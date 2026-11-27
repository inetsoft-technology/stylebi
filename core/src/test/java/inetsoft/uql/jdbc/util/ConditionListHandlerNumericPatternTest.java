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
package inetsoft.uql.jdbc.util;

import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82491: a like, starting with or contains value that is not a number (e.g. 2%) on a
 * numeric column was sent to the database as LIKE '0' (or '0%', '%0%'). It now generates the
 * always-false "col <> col", so no row matches and the negated condition matches the non-null
 * rows, as in memory. Rows are checked on embedded Derby.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class,
                                  CredentialService.class,
                                  ConditionListHandlerNumericPatternTest.Cfg.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConditionListHandlerNumericPatternTest {
   @Configuration
   static class Cfg {
      @Bean
      XRepository repository() {
         return Mockito.mock(XRepository.class);
      }

      @Bean
      inetsoft.uql.service.DataSourceRegistry dataSourceRegistry() {
         return Mockito.mock(inetsoft.uql.service.DataSourceRegistry.class);
      }
   }

   private static final Integer[] ROWS = { null, 0, 2, 20, 21, 11 };
   private static final Long[] REPORTER_L = { 2L, 12L, 20L, 25L, 102L, null, 0L, 21L, 11L, 1L, 32L };
   private static final Double[] REPORTER_D =
      { 1d, 10d, 129d, 1.5d, 21d, null, 0d, 12.5d, 120d, 2d, 129.5d };
   private static Connection conn;

   @BeforeAll
   static void createTable() throws Exception {
      conn = DriverManager.getConnection("jdbc:derby:memory:numpattern;create=true");
      conn.createStatement().execute("create table n(i int)");
      // the reporter's long (as bigint) and double columns
      conn.createStatement().execute("create table r(l bigint, d double)");

      for(int i = 0; i < REPORTER_L.length; i++) {
         try(PreparedStatement ps = conn.prepareStatement("insert into r values(?, ?)")) {
            ps.setObject(1, REPORTER_L[i], Types.BIGINT);
            ps.setObject(2, REPORTER_D[i], Types.DOUBLE);
            ps.execute();
         }
      }

      for(Integer row : ROWS) {
         try(PreparedStatement ps = conn.prepareStatement("insert into n values(?)")) {
            ps.setObject(1, row, Types.INTEGER);
            ps.execute();
         }
      }
   }

   @AfterAll
   static void closeTable() throws Exception {
      conn.close();
   }

   @ParameterizedTest
   @CsvSource({
      "LIKE, 2%", "LIKE, _1", "LIKE, 12%", "LIKE, abc",
      "STARTING_WITH, abc", "STARTING_WITH, 2%", "CONTAINS, abc", "CONTAINS, _1"
   })
   void nonNumericPatternMatchesNoRow(String opName, String value) throws Exception {
      int op = (Integer) XCondition.class.getField(opName).get(null);
      Statement0 stmt = generate(condition(op, value, false));

      assertFalse(stmt.sql().toUpperCase().contains("LIKE"), stmt.sql());
      assertTrue(stmt.sql().contains("<>"), stmt.sql());
      assertEquals(List.of(), sqlRows(stmt));

      Statement0 negated = generate(condition(op, value, true));
      assertEquals(List.of(0, 2, 11, 20, 21), sqlRows(negated), negated.sql());
   }

   @Test
   void numberStillUsesLike() throws Exception {
      Statement0 stmt = generate(condition(XCondition.LIKE, "2", false));

      assertTrue(stmt.sql().toUpperCase().contains("LIKE"), stmt.sql());
      assertFalse(stmt.sql().contains("<>"), stmt.sql());
   }

   /**
    * The reporter's patterns on a long and a double column, also after the condition is saved
    * and reloaded (the save wrote "0" before the fix, so the 0 row matched).
    */
   @ParameterizedTest
   @CsvSource({
      "l, long, LIKE, 2%", "l, long, LIKE, _1", "l, long, CONTAINS, 2%",
      "d, double, LIKE, 12%", "d, double, LIKE, 2%", "d, double, STARTING_WITH, _1"
   })
   void reporterLongAndDoubleColumnsMatchNoRow(String col, String type, String opName,
                                               String value) throws Exception
   {
      int op = (Integer) XCondition.class.getField(opName).get(null);

      for(boolean saved : new boolean[] { false, true }) {
         Condition cond = condition(type, op, value, false);
         cond = saved ? roundTrip(cond) : cond;
         Statement0 stmt = generate(cond, "r", col);

         assertFalse(stmt.sql().toUpperCase().contains("LIKE"), stmt.sql());
         assertEquals(List.of(), sqlRows(stmt), "saved " + saved + ": " + stmt.sql());

         Condition negated = condition(type, op, value, true);
         negated = saved ? roundTrip(negated) : negated;
         Statement0 nstmt = generate(negated, "r", col);
         assertEquals(10, sqlRows(nstmt).size(), "saved " + saved + ": " + nstmt.sql());
      }
   }

   private static Condition roundTrip(Condition cond) throws Exception {
      java.io.StringWriter sw = new java.io.StringWriter();
      java.io.PrintWriter pw = new java.io.PrintWriter(sw);
      cond.writeXML(pw);
      pw.flush();
      org.w3c.dom.Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
         .newDocumentBuilder().parse(new java.io.ByteArrayInputStream(
            sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      Condition loaded = new Condition();
      loaded.parseXML(doc.getDocumentElement());
      assertEquals(cond.getValues().get(0), loaded.getValues().get(0), sw.toString());
      return loaded;
   }

   private static Condition condition(int op, String value, boolean negated) {
      return condition(XSchema.INTEGER, op, value, negated);
   }

   private static Condition condition(String type, int op, String value, boolean negated) {
      Condition cond = new Condition(type);
      cond.setOperation(op);
      cond.addValue(value);
      cond.setNegated(negated);
      return cond;
   }

   private record Statement0(String sql, List<Object> parameters) {
   }

   private static Statement0 generate(Condition cond) throws Exception {
      return generate(cond, "n", "i");
   }

   private static Statement0 generate(Condition cond, String table, String col)
      throws Exception
   {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("numpattern-derby");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:memory:numpattern");
      ds.setRuntimeProductName("derby");

      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.setParseSQL(true);

      synchronized(sql) {
         sql.setSQLString("select " + table + "." + col + " from " + table);
         // bounded wait so a parse failure does not hang the whole surefire fork
         sql.wait(30000);
      }

      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef(table, col), cond, 0));
      VariableTable vars = new VariableTable();
      XFilterNode node = new ConditionListHandler().createXFilterNode(list, sql, vars);
      sql.setWhere(node);
      sql.clearSQLString();

      VarSQL varsql = new VarSQL();
      varsql.setBackslashIsEscapeChar(SQLHelper.getSQLHelper(sql).isBackslashEscapeChar());
      varsql.setSQLType(VarSQL.SQLType.STATEMENT);
      String text = varsql.replaceVariables(sql.getSQLString(), vars)
         .replaceAll("\\s+", " ").trim();
      return new Statement0(text, varsql.getParameterValues());
   }

   private static List<Object> sqlRows(Statement0 statement) throws Exception {
      List<Object> rows = new ArrayList<>();

      try(PreparedStatement ps = conn.prepareStatement(statement.sql())) {
         for(int i = 0; i < statement.parameters().size(); i++) {
            ps.setObject(i + 1, statement.parameters().get(i));
         }

         try(ResultSet rs = ps.executeQuery()) {
            while(rs.next()) {
               rows.add(rs.getObject(1));
            }
         }
      }

      rows.sort(Comparator.nullsFirst(Comparator.<Object, Double>comparing(v -> ((Number) v).doubleValue())));
      return rows;
   }
}
