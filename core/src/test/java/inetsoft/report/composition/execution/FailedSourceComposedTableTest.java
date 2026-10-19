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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.CoreTool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78797. A worksheet table whose SQL fails at execution time yields a null lens in
 * RUNTIME_MODE (the cause is left in XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE). A composed
 * table over it (mirror, rotate, unpivot) is a live-data table, and AssetQuerySandbox,
 * RotatedQuery and UnpivotQuery dereferenced the null, so the caller got a raw
 * NullPointerException instead of the source's real cause.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  RowFetchFailureTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FailedSourceComposedTableTest {
   // the table does not exist, so the statement fails when it is executed
   private static final String FAILING = "select f.id, f.g from no_such_table_78797 f";

   @BeforeEach
   @AfterEach
   void clear() {
      XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.remove();
      CoreTool.clearUserMessage();
   }

   @Test
   void sourceItselfReturnsNullWithTheCause() throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "S1");

      assertNullLensWithCause(ws, "S1");
   }

   @Test
   void liveDataSourceReturnsNullWithTheCause() throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "S1").setLiveData(true);

      assertNullLensWithCause(ws, "S1");
   }

   @Test
   void mirrorOfFailingSourceReturnsNullWithTheCause() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly source = sqlTable(ws, "S1");
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M1", source);
      ws.addAssembly(mirror);
      mirror.setLiveData(true);
      init(ws, mirror);

      assertNullLensWithCause(ws, "M1");
   }

   @Test
   void rotateOfFailingSourceReturnsNullWithTheCause() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly source = sqlTable(ws, "S1");
      RotatedTableAssembly rotated = new RotatedTableAssembly(ws, "R1", source);
      ws.addAssembly(rotated);
      rotated.setLiveData(true);
      init(ws, rotated);

      assertNullLensWithCause(ws, "R1");
   }

   @Test
   void unpivotOfFailingSourceReturnsNullWithTheCause() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly source = sqlTable(ws, "S1");
      UnpivotTableAssembly unpivot = new UnpivotTableAssembly(ws, "U1", source);
      ws.addAssembly(unpivot);
      unpivot.setLiveData(true);
      init(ws, unpivot);

      assertNullLensWithCause(ws, "U1");
   }

   private static void assertNullLensWithCause(Worksheet ws, String name) throws Exception {
      XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.remove();
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      TableLens lens = box.getTableLens(name, AssetQuerySandbox.RUNTIME_MODE,
                                        new VariableTable());

      assertNull(lens, name + " must report the failure as a null lens, not throw");
      String cause = XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.get();
      assertNotNull(cause, "the source's failure cause was lost for " + name);
      assertTrue(cause.toLowerCase().contains("no_such_table_78797"), cause);
   }

   private static void init(Worksheet ws, TableAssembly table) throws Exception {
      table.update();
      AssetQuerySandbox init = new AssetQuerySandbox(ws);
      AssetEventUtil.initColumnSelection(init, table);
      init.dispose();
      CoreTool.clearUserMessage();
   }

   private static SQLBoundTableAssembly sqlTable(Worksheet ws, String name) throws Exception {
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class,
                                                        long.class);
      parse.setAccessible(true);
      parse.invoke(usql, FAILING, 0, 4000L);
      usql.setSQLString(FAILING, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), FAILING);

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("failedsource78797");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:memory:rowfetchfailure");
      ds.setRequireLogin(false);
      usql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setName(name);
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, name);
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);
      table.setSQLMergeable(true);

      ColumnSelection selection = new ColumnSelection();

      for(String column : new String[] { "id", "g" }) {
         ColumnRef ref = new ColumnRef(new AttributeRef(column));
         ref.setDataType(XSchema.INTEGER);
         selection.addAttribute(ref);
      }

      table.setColumnSelection(selection, false);
      table.setColumnSelection((ColumnSelection) selection.clone(), true);
      ws.addAssembly(table);
      return table;
   }
}
