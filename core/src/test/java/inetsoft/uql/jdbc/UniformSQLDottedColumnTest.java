/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.uql.jdbc;

import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.ColumnCache;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.model.database.JoinModel;
import inetsoft.web.portal.model.database.QueryTableModel;
import inetsoft.web.portal.model.database.graph.TableJoinInfo;
import inetsoft.web.portal.model.database.events.EditQueryTableEvent;
import inetsoft.web.portal.model.database.events.RemoveGraphTableEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #80218. A column name can contain a dot (AA.BB..cc is the column ".cc" of the
 * schema qualified table AA.BB). The table qualifier of such a path was cut at the last
 * dot (AA.BB.), which is no table, so renaming or removing a table in the query graph
 * dropped the select, order by and group by columns and the join of the column, and the
 * graph attached its join to the node "AA.BB." instead of AA.BB.
 *
 * The edits go through QueryGraphModelService with a real QueryManagerService, as the
 * query graph does, with and without the table metadata.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, UniformSQLDottedColumnTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLDottedColumnTest {
   private static final String QUERY =
      "select AA.BB.NORM, AA.BB.\".cc\", CC.DD.ID from AA.BB, CC.DD " +
      "where AA.BB.\".cc\" = CC.DD.ID group by AA.BB.NORM, AA.BB.\".cc\", CC.DD.ID " +
      "order by AA.BB.\".cc\"";

   @Test
   void renameKeepsDottedColumnWithMetadata() throws Exception {
      assertKept(rename(QUERY, "AA.BB", "BB1", COLUMNS), "BB1", "AA.BB");
   }

   @Test
   void renameKeepsDottedColumnWithoutMetadata() throws Exception {
      assertKept(rename(QUERY, "AA.BB", "BB1", null), "BB1", "AA.BB");
   }

   // the table has its own alias, which is the old name that the columns carry
   @Test
   void renameCustomAliasKeepsDottedColumn() throws Exception {
      String query = "select a1.NORM, a1.\".cc\", CC.DD.ID from AA.BB a1, CC.DD " +
         "where a1.\".cc\" = CC.DD.ID group by a1.NORM, a1.\".cc\", CC.DD.ID order by a1.\".cc\"";

      for(String[] columns : new String[][] { COLUMNS, null }) {
         assertKept(rename(query, "a1", "a2", columns), "a2", "a1");
      }
   }

   // the other table of the join is renamed, the dotted column keeps its table
   @Test
   void renameOtherTableKeepsDottedColumn() throws Exception {
      for(String[] columns : new String[][] { COLUMNS, null }) {
         String generated = rename(QUERY, "CC.DD", "DD1", columns);
         assertTrue(generated.contains("DD1.ID"), generated);
         assertEquals(4, count(generated, "AA.BB..cc"), generated);
         assertTrue(generated.contains(" where AA.BB..cc = DD1.ID"), generated);
         assertTrue(generated.contains(" order by AA.BB..cc"), generated);
      }
   }

   @Test
   void removeUnrelatedTableKeepsDottedJoin() throws Exception {
      String query = "select AA.BB.NORM, AA.BB.\".cc\" from AA.BB, CC.DD, EE.FF " +
         "where AA.BB.\".cc\" = CC.DD.ID and EE.FF.ID > 1 order by AA.BB.\".cc\"";

      for(String[] columns : new String[][] { COLUMNS, null }) {
         String generated = remove(query, "EE.FF", columns);
         assertTrue(generated.contains(" where AA.BB..cc = CC.DD.ID"), generated);
         assertFalse(generated.contains("EE.FF"), generated);
         assertEquals(3, count(generated, "AA.BB..cc"), generated);
         assertTrue(generated.contains(" order by AA.BB..cc"), generated);
      }
   }

   // AA is a table, AA.BB is another one that is removed: the join of AA.BB.cc has no
   // table any more, and isn't taken for a column "BB.cc" of AA
   @Test
   void removedTableOfAPrefixTableDropsItsJoin() throws Exception {
      String query = "select AA.NORM from AA, AA.BB, CC.DD " +
         "where AA.BB.cc = CC.DD.ID and AA.NORM > 1";

      for(String[] columns : new String[][] { COLUMNS, null }) {
         String generated = remove(query, "AA.BB", columns);
         assertFalse(generated.contains("BB"), generated);
         assertFalse(generated.contains("CC.DD.ID"), generated);
         assertTrue(generated.contains("AA.NORM > 1"), generated);
      }
   }

   // normal, aliased and quoted columns are unchanged
   @Test
   void otherColumnsAreUnchanged() throws Exception {
      String generated = rename("select AA.BB.NORM, AA.BB.ID from AA.BB, CC.DD " +
                                   "where AA.BB.ID = CC.DD.ID order by AA.BB.NORM",
                                "AA.BB", "BB1", COLUMNS);
      assertEquals("select BB1.ID, BB1.NORM from AA.BB BB1, CC.DD where BB1.ID = CC.DD.ID " +
                      "order by BB1.NORM asc", generated);

      generated = rename("select a1.NORM, a1.ID from AA.BB a1, CC.DD where a1.ID = CC.DD.ID " +
                            "group by a1.NORM, a1.ID",
                         "a1", "a2", COLUMNS);
      assertEquals("select a2.ID, a2.NORM from AA.BB a2, CC.DD where a2.ID = CC.DD.ID " +
                      "group by a2.NORM, a2.ID", generated);

      generated = rename("select AA.BB.\"NORM\", AA.BB.ID from AA.BB, CC.DD where AA.BB.ID = CC.DD.ID",
                         "AA.BB", "BB1", null);
      assertEquals("select BB1.ID, BB1.\"NORM\" from AA.BB BB1, CC.DD where BB1.ID = CC.DD.ID",
                   generated);

      // the join of a removed table is still dropped
      generated = remove("select CC.DD.ID from AA.BB, CC.DD where AA.BB.ID = CC.DD.ID and CC.DD.ID > 1",
                         "AA.BB", null);
      assertEquals("select CC.DD.ID from CC.DD where CC.DD.ID > 1", generated);
   }

   // the join model has the table AA.BB, the node of the graph, not "AA.BB."
   @Test
   void graphJoinModelHasTheTableOfTheDottedColumn() throws Exception {
      Fixture fixture = new Fixture(QUERY, COLUMNS);
      Method method = QueryGraphModelService.class.getDeclaredMethod(
         "getTableJoinModels", UniformSQL.class, SelectTable.class, String.class);
      method.setAccessible(true);

      for(String name : new String[] { "AA.BB", "CC.DD" }) {
         @SuppressWarnings("unchecked")
         List<JoinModel> joins = (List<JoinModel>) method.invoke(
            fixture.graph, fixture.sql(), fixture.table(name), fixture.ds.getFullName());
         assertEquals(1, joins.size(), name);
         assertEquals("AA.BB", joins.get(0).getTable(), name);
         assertEquals("CC.DD", joins.get(0).getForeignTable(), name);
         assertTrue(joins.get(0).getColumn().endsWith(".cc"), joins.get(0).getColumn());
         assertEquals("ID", joins.get(0).getForeignColumn(), name);
      }
   }

   // a join of a dotted column is deleted from the Links tab
   @Test
   void deleteJoinOfDottedColumn() throws Exception {
      for(String[] columns : new String[][] { COLUMNS, null }) {
         Fixture fixture = new Fixture(QUERY, columns);
         TableJoinInfo info = new TableJoinInfo();
         info.setRuntimeId(RID);
         info.setSourceTable("AA.BB");
         info.setTargetTable("CC.DD");
         fixture.graph.deleteJoins(info);
         String generated = regenerate(fixture.sql());
         assertFalse(generated.contains(" where "), generated);
         assertTrue(generated.contains("from AA.BB, CC.DD"), generated);
      }
   }

   // the select, group by and order by columns and the join of the dotted column are kept
   // under the new table
   private static void assertKept(String generated, String to, String from) {
      assertFalse(generated.contains(from + "."), generated);
      assertEquals(4, count(generated, to + "..cc"), generated);
      assertTrue(generated.contains(" where " + to + "..cc = CC.DD.ID"), generated);
      assertTrue(generated.matches("select .*" + Pattern.quote(to + "..cc") + ".* from .*"), generated);
      assertTrue(generated.contains(" group by "), generated);
      assertTrue(generated.matches(".* group by .*" + Pattern.quote(to + "..cc") + ".*"), generated);
      assertTrue(generated.contains(" order by " + to + "..cc"), generated);
   }

   private static int count(String text, String part) {
      return text.split(Pattern.quote(part), -1).length - 1;
   }

   private static String rename(String query, String from, String to, String[] columns)
      throws Exception
   {
      Fixture fixture = new Fixture(query, columns);
      SelectTable table = fixture.table(from);
      QueryTableModel model = new QueryTableModel();
      model.setName(Tool.toString(table.getName()));
      model.setAlias(to);
      EditQueryTableEvent event = new EditQueryTableEvent();
      event.setId(RID);
      event.setOldName(table.getAlias());
      event.setTables(List.of(model));
      fixture.graph.editQueryTableProperties(event, null);
      return regenerate(fixture.sql());
   }

   private static String remove(String query, String name, String[] columns) throws Exception {
      Fixture fixture = new Fixture(query, columns);
      RemoveGraphTableEvent.RemoveTableInfo info = new RemoveGraphTableEvent.RemoveTableInfo();
      info.setFullName(Tool.toString(fixture.table(name).getName()));
      info.setTableName(info.getFullName());
      fixture.graph.removeTables(RID, List.of(info), null);
      return regenerate(fixture.sql());
   }

   private static final class Fixture {
      Fixture(String query, String[] columns) throws Exception {
         ds = dataSource();
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(ds);
         sql.parse(query, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), query);

         JDBCQuery jquery = new JDBCQuery();
         jquery.setDataSource(ds);
         jquery.setSQLDefinition(sql);
         runtimeQuery = new RuntimeQueryService.RuntimeXQuery(jquery, RID, ds.getFullName());

         RuntimeQueryService rqs = mock(RuntimeQueryService.class);
         when(rqs.getRuntimeQuery(RID)).thenReturn(runtimeQuery);
         DataSourceService dss = mock(DataSourceService.class);
         when(dss.getDataSource(any())).thenReturn(ds);
         QueryManagerService qms = new QueryManagerService(
            rqs, repository(columns), dss, mock(SecurityEngine.class), mock(ColumnCache.class));
         graph = new QueryGraphModelService(rqs, dss, qms, mock(DataSourceRegistry.class));
         // the graph opens the query with the table metadata
         qms.fixUniformSQLInfo(sql, ds, null);
      }

      UniformSQL sql() {
         return (UniformSQL) runtimeQuery.getQuery().getSQLDefinition();
      }

      SelectTable table(String name) {
         for(SelectTable table : sql().getSelectTable()) {
            if(name.equalsIgnoreCase(Tool.toString(table.getAlias()))) {
               return table;
            }
         }

         throw new AssertionError("no table " + name);
      }

      private final JDBCDataSource ds;
      private final RuntimeQueryService.RuntimeXQuery runtimeQuery;
      private final QueryGraphModelService graph;
   }

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      // the quoting of the column is written only with the column metadata
      return sql.getSQLString().replaceAll("\\s+", " ").trim().replace("\".cc\"", ".cc");
   }

   // the metadata of every table, or none
   private static XRepository repository(String[] columns) throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
         }

         if(columns == null) {
            return null;
         }

         XTypeNode result = new XTypeNode("Result");

         for(String column : columns) {
            result.addChild(XSchema.createPrimitiveType(column, Integer.class));
         }

         XTypeNode meta = new XTypeNode("meta");
         meta.addChild(result);
         return meta;
      });

      return repository;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setDriver("org.h2.Driver");
      ds.setURL("jdbc:h2:mem:x");
      ds.setName("ds80218_" + RUN + "_" + (++sources));
      ds.setRuntimeProductName("h2");
      ds.setProductVersion("10.0");
      return ds;
   }

   // a JDBCDataSource creates its credential when it is constructed, and
   // JDBCUtil.fixUniformSQLInfo looks up the driver type
   @Configuration
   static class Beans {
      @Bean
      Config config() {
         Config config = mock(Config.class);
         when(config.getJDBCType(any())).thenReturn("H2");
         return config;
      }

      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean())).thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }
   }

   private static final String RID = "rq-80218";
   private static final String[] COLUMNS = { ".cc", "NORM", "ID" };
   private static final long RUN = System.nanoTime();
   private static int sources;
}
