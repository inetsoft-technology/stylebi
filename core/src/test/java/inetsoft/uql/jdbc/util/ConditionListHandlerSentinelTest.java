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
import org.junit.jupiter.params.provider.*;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The sentinel values NULL_VALUE, EMPTY_STRING and NULL_STRING in a condition pushed to SQL
 * must select the same rows as {@code Condition.evaluate}. Rows are compared on embedded Derby.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class,
                                  CredentialService.class,
                                  ConditionListHandlerSentinelTest.Cfg.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConditionListHandlerSentinelTest {
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

   // a null row, an empty string, the text "null", the sentinel names as text, and a plain value
   private static final String[] ROWS = {
      null, "", "null", "NULL_VALUE", "EMPTY_STRING", "NULL_STRING", "a"
   };

   // rows of the second table: wildcard, escape and injection candidates, plus case variants
   private static final String[] U_ROWS = {
      "abc", "a_c", "a?c", "ac", "50%", "500", "a_b", "ab", "a%b", "a\\b", "a!b", "a[b", "a]b",
      "A", "a", "b", "O'Brien", "x\"y", "'; DROP TABLE t;--"
   };

   private static Connection conn;

   @BeforeAll
   static void createTable() throws Exception {
      conn = DriverManager.getConnection("jdbc:derby:memory:sentinelcond;create=true");
      conn.createStatement().execute("create table t(v varchar(50))");
      conn.createStatement().execute("create table u(v varchar(50))");
      conn.createStatement().execute("create table w(v varchar(50))");

      for(String row : W_ROWS) {
         try(PreparedStatement ps = conn.prepareStatement("insert into w values(?)")) {
            ps.setString(1, row);
            ps.execute();
         }
      }

      for(String row : U_ROWS) {
         try(PreparedStatement ps = conn.prepareStatement("insert into u values(?)")) {
            ps.setString(1, row);
            ps.execute();
         }
      }

      for(String row : ROWS) {
         try(PreparedStatement ps = conn.prepareStatement("insert into t values(?)")) {
            ps.setString(1, row);
            ps.execute();
         }
      }
   }

   @AfterAll
   static void closeTable() throws Exception {
      conn.close();
   }

   private static Condition condition(int op, Object value) {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(op);
      cond.addValue(value);
      return cond;
   }

   private record Statement0(String sql, List<Object> parameters) {
   }

   private static Statement0 generate(Condition cond) throws Exception {
      return generate("t", cond, new VariableTable(), false);
   }

   private static Statement0 generate(String table, Condition cond, VariableTable vars,
                                      boolean replace)
      throws Exception
   {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("sentinelcond-derby");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:memory:sentinelcond");
      ds.setRuntimeProductName("derby");

      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.setParseSQL(true);

      synchronized(sql) {
         sql.setSQLString("select " + table + ".v from " + table);
         // bounded wait so a parse failure does not hang the whole surefire fork
         sql.wait(30000);
      }

      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef(table, "v"), cond, 0));

      if(replace) {
         // the real pipeline resolves the variables before the SQL is generated
         list.replaceVariables(vars);
      }

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

   private static Set<String> sqlRows(int op, Object value) throws Exception {
      return sqlRows(generate(condition(op, value)));
   }

   private static Set<String> sqlRows(Statement0 statement) throws Exception {
      Set<String> rows = new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));

      try(PreparedStatement ps = conn.prepareStatement(statement.sql())) {
         for(int i = 0; i < statement.parameters().size(); i++) {
            ps.setObject(i + 1, statement.parameters().get(i));
         }

         try(ResultSet rs = ps.executeQuery()) {
            while(rs.next()) {
               rows.add(rs.getString(1));
            }
         }
      }

      return rows;
   }

   private static Set<String> memoryRows(int op, Object value) {
      Condition cond = condition(op, value);
      Set<String> rows = new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));

      for(String row : ROWS) {
         if(cond.evaluate(row)) {
            rows.add(row);
         }
      }

      return rows;
   }

   private static Set<String> memoryRows(String[] data, Condition cond) {
      Set<String> rows = new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));

      for(String row : data) {
         if(cond.evaluate(row)) {
            rows.add(row);
         }
      }

      return rows;
   }

   private static Set<String> set(String... values) {
      Set<String> rows = new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));
      rows.addAll(Arrays.asList(values));
      return rows;
   }

   private static void check(Set<String> expected, int op, Object value) throws Exception {
      check(expected, expected, op, value);
   }

   private static void check(Set<String> expectedInMemory, Set<String> expectedInSql, int op,
                             Object value)
      throws Exception
   {
      assertEquals(expectedInMemory, memoryRows(op, value), "in memory");
      assertEquals(expectedInSql, sqlRows(op, value), "in SQL");
   }

   // pinned current behavior, #80963 item 7
   @Test
   void equalTo_nullValue_selectsNullRows() throws Exception {
      // the in-memory NULL operation treats an empty string as null too, SQL IS NULL does not
      check(set(null, ""), set((String) null), XCondition.EQUAL_TO,
            XConstants.CONDITION_NULL_VALUE);
   }

   @Test
   void equalTo_emptyString_selectsEmptyStringOnly() throws Exception {
      check(set(""), XCondition.EQUAL_TO, XConstants.CONDITION_EMPTY_STRING);
   }

   @Test
   void equalTo_nullString_selectsTheTextNull() throws Exception {
      check(set("null"), XCondition.EQUAL_TO, XConstants.CONDITION_NULL_STRING);
   }

   // pinned current behavior, #80963 item 7
   @Test
   void oneOf_singleNullValue_selectsNullRows() throws Exception {
      check(set(null, ""), set((String) null), XCondition.ONE_OF,
            new Object[] { XConstants.CONDITION_NULL_VALUE });
   }

   @Test
   void oneOf_singleEmptyString_selectsEmptyStringOnly() throws Exception {
      check(set(""), XCondition.ONE_OF, new Object[] { XConstants.CONDITION_EMPTY_STRING });
   }

   @Test
   void oneOf_singleNullString_selectsTheTextNull() throws Exception {
      check(set("null"), XCondition.ONE_OF, new Object[] { XConstants.CONDITION_NULL_STRING });
   }

   @Test
   void oneOf_sentinelAmongOtherValues_isComparedAsText() throws Exception {
      check(set("NULL_VALUE", "a"), XCondition.ONE_OF,
            new Object[] { "a", XConstants.CONDITION_NULL_VALUE });
   }

   // ---- differential rows: memory evaluation must select the same rows as the generated SQL ----

   private static Condition cond(int op, boolean caseSensitive, Object... values) {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(op);
      cond.setCaseSensitive(caseSensitive);

      for(Object value : values) {
         cond.addValue(value);
      }

      return cond;
   }

   private static void assertSame(String label, String[] data, String table,
                                  java.util.function.Supplier<Condition> factory)
      throws Exception
   {
      Set<String> memory = memoryRows(data, factory.get());
      Set<String> sql = sqlRows(generate(table, factory.get(), new VariableTable(), false));
      assertEquals(memory, sql, label + ": memory vs SQL");
   }

   @Disabled("mismatch C01 #81114")
   @DisplayName("C01 LIKE a_c memory == SQL")
   @Test
   void c01_likeUnderscore() throws Exception {
      assertSame("LIKE a_c", U_ROWS, "u", () -> cond(XCondition.LIKE, false, "a_c"));
   }

   @Disabled("mismatch C02 #81114")
   @DisplayName("C02 LIKE a?c memory == SQL")
   @Test
   void c02_likeQuestionMark() throws Exception {
      assertSame("LIKE a?c", U_ROWS, "u", () -> cond(XCondition.LIKE, false, "a?c"));
   }

   @DisplayName("C02 LIKE wildcards memory == SQL")
   @ParameterizedTest(name = "[{index}] LIKE ''{0}''")
   @ValueSource(strings = { "a%c", "%", "" })
   void c02_likeWildcards(String pattern) throws Exception {
      assertSame("LIKE " + pattern, U_ROWS, "u", () -> cond(XCondition.LIKE, false, pattern));
   }

   @DisplayName("C03 CONTAINS/STARTING_WITH with % and _ memory == SQL, literal match")
   @ParameterizedTest(name = "[{index}] op {0} value ''{1}''")
   @MethodSource("c03Cases")
   void c03_likeMetaCharacters(int op, String value, String[] literal) throws Exception {
      assertSame("op " + op + " " + value, U_ROWS, "u", () -> cond(op, true, value));
      assertEquals(set(literal), memoryRows(U_ROWS, cond(op, true, value)),
                   "literal match set");
   }

   static java.util.stream.Stream<Arguments> c03Cases() {
      return java.util.stream.Stream.of(
         Arguments.of(XCondition.CONTAINS, "%", new String[] { "50%", "a%b" }),
         Arguments.of(XCondition.CONTAINS, "_", new String[] { "a_c", "a_b" }),
         Arguments.of(XCondition.STARTING_WITH, "a%", new String[] { "a%b" }));
   }

   @DisplayName("C04 CONTAINS with escape-like characters memory == SQL")
   @ParameterizedTest(name = "[{index}] CONTAINS ''{0}''")
   @ValueSource(strings = { "\\", "!", "[", "]" })
   void c04_containsEscapeCharacters(String value) throws Exception {
      assertSame("CONTAINS " + value, U_ROWS, "u", () -> cond(XCondition.CONTAINS, true, value));
   }

   private static Condition varCondition(int op, VariableTable vars) {
      Condition cond = op == XCondition.BETWEEN ? cond(op, true, "$(v)", "$(v)") :
         cond(op, true, "$(v)");
      cond.replaceVariable(vars);
      return cond;
   }

   private static Condition rawVarCondition(int op) {
      return op == XCondition.BETWEEN ? cond(op, true, "$(v)", "$(v)") : cond(op, true, "$(v)");
   }

   @Disabled("mismatch C06 #81115")
   @DisplayName("C06 condition with a missing variable is ignored in memory and SQL")
   @ParameterizedTest(name = "[{index}] op {0}")
   @ValueSource(ints = { XCondition.EQUAL_TO, XCondition.ONE_OF, XCondition.BETWEEN,
                         XCondition.LIKE, XCondition.STARTING_WITH })
   void c06_missingVariable(int op) throws Exception {
      VariableTable vars = new VariableTable();
      Set<String> memory = memoryRows(ROWS, varCondition(op, vars));
      Statement0 st = generate("t", rawVarCondition(op), vars, true);
      assertEquals(set(ROWS), memory, "memory: ignored condition selects all rows");
      assertEquals(memory, sqlRows(st), "SQL rows == memory rows");
   }

   static java.util.stream.Stream<Arguments> nullArrays() {
      return java.util.stream.Stream.of(Arguments.of((Object) new Object[] { null, null }),
                                        Arguments.of((Object) new Object[0]),
                                        Arguments.of((Object) new Object[] { null }));
   }

   @Disabled("mismatch C07 #81115")
   @DisplayName("C07 ONE_OF with an all-null / empty array variable is ignored in memory and SQL")
   @ParameterizedTest(name = "[{index}]")
   @MethodSource("nullArrays")
   void c07_allNullArray(Object[] array) throws Exception {
      VariableTable vars = new VariableTable();
      vars.put("v", array);
      Set<String> memory = memoryRows(ROWS, varCondition(XCondition.ONE_OF, vars));
      Statement0 st = generate("t", rawVarCondition(XCondition.ONE_OF), vars, true);
      assertEquals(set(ROWS), memory, "memory");
      assertEquals(memory, sqlRows(st), "SQL");
   }

   static java.util.stream.Stream<Arguments> nullElementArrays() {
      return java.util.stream.Stream.of(Arguments.of((Object) new Object[] { "a", null }),
                                        Arguments.of((Object) new Object[] { null, "a" }));
   }

   @DisplayName("C08 ONE_OF with a null element among others memory == SQL == {a}")
   @ParameterizedTest(name = "[{index}]")
   @MethodSource("nullElementArrays")
   void c08_nullElement(Object[] array) throws Exception {
      VariableTable vars = new VariableTable();
      vars.put("v", array);
      Set<String> memory = memoryRows(ROWS, varCondition(XCondition.ONE_OF, vars));
      Statement0 st = generate("t", rawVarCondition(XCondition.ONE_OF), vars, true);
      assertEquals(set("a"), memory, "memory");
      assertEquals(memory, sqlRows(st), "SQL");
   }

   @Disabled("mismatch C09 #80963")
   @DisplayName("C09 BETWEEN NULL_VALUE..a memory == SQL")
   @Test
   void c09_betweenNullValue() throws Exception {
      assertSame("BETWEEN NULL_VALUE a", ROWS, "t",
                 () -> cond(XCondition.BETWEEN, true, XConstants.CONDITION_NULL_VALUE, "a"));
   }

   @DisplayName("C09 BETWEEN with an EMPTY_STRING bound memory == SQL")
   @Test
   void c09_betweenSentinel() throws Exception {
      assertSame("BETWEEN a EMPTY_STRING", ROWS, "t",
                 () -> cond(XCondition.BETWEEN, true, "a", XConstants.CONDITION_EMPTY_STRING));
   }

   @Disabled("mismatch C10 #81116")
   @DisplayName("C10 negated EQUAL_TO memory == SQL")
   @Test
   void c10_negatedEqualTo() throws Exception {
      assertSame("negated EQUAL_TO", ROWS, "t", () -> {
         Condition c = cond(XCondition.EQUAL_TO, true, "a");
         c.setNegated(true);
         return c;
      });
   }

   @DisplayName("C10 negated ONE_OF memory == SQL")
   @Test
   void c10_negated() throws Exception {
      assertSame("negated ONE_OF", ROWS, "t", () -> {
         Condition c = cond(XCondition.ONE_OF, true, (Object) new Object[] { "a" });
         c.setNegated(true);
         return c;
      });
   }

   static java.util.stream.Stream<Arguments> injectionCases() {
      java.util.List<Arguments> list = new ArrayList<>();

      for(int op : new int[] { XCondition.EQUAL_TO, XCondition.ONE_OF, XCondition.CONTAINS }) {
         for(String v : new String[] { "O'Brien", "x\"y", "a\\b", "'; DROP TABLE t;--" }) {
            list.add(Arguments.of(op, v));
         }
      }

      return list.stream();
   }

   @DisplayName("C15 values with quotes / SQL text are bound, each selects exactly its row")
   @ParameterizedTest(name = "[{index}] op {0} ''{1}''")
   @MethodSource("injectionCases")
   void c15_injection(int op, String value) throws Exception {
      java.util.function.Supplier<Condition> f = () -> op == XCondition.ONE_OF ?
         cond(op, true, (Object) new Object[] { value }) : cond(op, true, value);
      assertSame("injection " + value, U_ROWS, "u", f);
      Set<String> memory = memoryRows(U_ROWS, f.get());
      assertTrue(memory.contains(value), "own row selected: " + memory);

      assertEquals(set(value), memory);

      if(op != XCondition.CONTAINS) {
         Statement0 st = generate("u", f.get(), new VariableTable(), false);
         assertFalse(st.sql().contains(value),
                     "raw value must not be in the SQL text: " + st.sql());
      }
   }

   // LIKE: the value is spliced into the quoted literal and escaped by VarSQL (Bug #76822), so a
   // single quote appears doubled in the SQL text
   @DisplayName("C15 CONTAINS / STARTING_WITH with a single quote: doubled in the SQL text")
   @ParameterizedTest(name = "[{index}] op {0}")
   @ValueSource(ints = { XCondition.CONTAINS, XCondition.STARTING_WITH })
   void c15_likeQuoteDoubled(int op) throws Exception {
      Statement0 st = generate("u", cond(op, true, "O'Brien"), new VariableTable(), false);
      assertTrue(st.sql().contains("O''Brien"), st.sql());
      assertEquals(set("O'Brien"), sqlRows(st));
   }

   private static final String[] W_ROWS = {
      "a.c", "a+c", "a(c)", "a[b]", "a|c", "a^c", "a$c", "a{1}", "abc"
   };

   @DisplayName("C19 LIKE regex metacharacters are literal in memory and SQL")
   @ParameterizedTest(name = "[{index}] ''{0}''")
   @ValueSource(strings = { "a.c", "a+c", "a(c)", "a[b]", "a|c", "a^c", "a$c", "a{1}" })
   void c19_regexMetaCharacters(String pattern) throws Exception {
      assertSame("LIKE " + pattern, W_ROWS, "w", () -> cond(XCondition.LIKE, true, pattern));
      assertEquals(set(pattern), memoryRows(W_ROWS, cond(XCondition.LIKE, true, pattern)));
   }
}
