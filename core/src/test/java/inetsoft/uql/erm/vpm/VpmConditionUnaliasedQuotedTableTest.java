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
package inetsoft.uql.erm.vpm;

import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.util.XUtil;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77861, a VPM condition column of a query table without an alias was qualified with the
 * table name as the parser stores it, quoted on PostgreSQL and Snowflake ("ORDERS") or for a
 * keyword ("user"), and its column was left unquoted ("ORDERS".EMPLOYEE_ID), since the helper
 * doesn't quote a column after a quoted qualifier as it quotes the column of an alias
 * (o."EMPLOYEE_ID"). PostgreSQL folded the column (column orders.employee_id does not exist).
 * The column of a table without an alias is now quoted as the column of an alias.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class,
                                  CredentialService.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VpmConditionUnaliasedQuotedTableTest {
   private static final String DEFAULT = "default";
   private static final String POSTGRESQL = "postgresql";
   private static final String SNOWFLAKE = "snowflake";
   private static final String H2 = "h2";
   private static final String SQLSERVER = "sqlserver";

   @Test
   void reportedQueryQuotesTheConditionColumn() throws Exception {
      VpmCondition cond = condition("ORDERS", t -> new XBinaryCondition(
         field(t, "EMPLOYEE_ID"), value("2"), "="));
      String text = "select \"ORDER_ID\" from \"ORDERS\" where \"ORDERS\".\"PAID\" = 1";

      assertEquals("\"ORDERS\".\"EMPLOYEE_ID\" = 2", apply(POSTGRESQL, text, cond));
      assertEquals("select \"ORDER_ID\" from \"ORDERS\" where \"ORDERS\".\"PAID\" = 1 " +
                   "and (\"ORDERS\".\"EMPLOYEE_ID\" = 2)",
                   applySentence(POSTGRESQL, text, cond));
   }

   // the condition column also selected, qualified, isn't quoted again by the outer query
   @Test
   void selectedQualifiedColumnIsNotQuotedAgain() throws Exception {
      VpmCondition cond = condition("ORDERS", t -> new XBinaryCondition(
         field(t, "EMPLOYEE_ID"), value("2"), "="));

      for(String text : new String[] {
         "select \"ORDERS\".\"ORDER_ID\", \"ORDERS\".\"EMPLOYEE_ID\" from \"ORDERS\" " +
            "where \"ORDERS\".\"PAID\" = 1",
         "select \"ORDER_ID\", \"EMPLOYEE_ID\" from \"ORDERS\" where \"ORDERS\".\"PAID\" = 1",
         "select ORDERS.ORDER_ID, ORDERS.EMPLOYEE_ID from ORDERS where ORDERS.PAID = 1" })
      {
         String sentence = applySentence(POSTGRESQL, text, cond);
         assertTrue(sentence.endsWith(" and (\"ORDERS\".\"EMPLOYEE_ID\" = 2)"), sentence);
         assertFalse(sentence.contains("\"\""), sentence);
      }
   }

   // a GUI field with a quote in its name is quoted with the quote escaped. The field of an
   // alias isn't quoted (o.A"B), as the helper doesn't quote a field with a quote in a VPM
   @Test
   void fieldWithQuoteIsQuoted() throws Exception {
      VpmCondition cond = condition("ORDERS", t -> new XBinaryCondition(
         field(t, "A\"B"), value("2"), "="));

      assertEquals("\"ORDERS\".\"A\"\"B\" = 2",
                   apply(POSTGRESQL, "select \"ORDER_ID\" from \"ORDERS\"", cond));
      assertEquals("o.A\"B = 2",
                   apply(POSTGRESQL, "select o.\"ORDER_ID\" from \"ORDERS\" o", cond));
   }

   // a column with a doubled quote in an expression is quoted as for an alias
   @Test
   void expressionColumnWithQuoteIsQuotedAsAliased() throws Exception {
      VpmCondition cond = condition("ORDERS", t -> new XBinaryCondition(
         expression(t + ".\"A\"\"B\""), value("2"), "="));

      assertEquals("\"ORDERS\".\"A\"\"B\" = 2",
                   apply(POSTGRESQL, "select \"ORDER_ID\" from \"ORDERS\"", cond));
      assertEquals("o.\"A\"\"B\" = 2",
                   apply(POSTGRESQL, "select o.\"ORDER_ID\" from \"ORDERS\" o", cond));
   }

   // a column of the same table read with and without an alias
   @Test
   void eachOccurrenceIsQuotedAsAliased() throws Exception {
      VpmCondition cond = condition("ORDERS", t -> new XBinaryCondition(
         field(t, "EMPLOYEE_ID"), value("2"), "="));
      String text = "select x.\"ORDER_ID\" from \"public\".\"ORDERS\" x, \"ORDERS\" " +
         "where x.\"ORDER_ID\" = \"ORDERS\".\"ORDER_ID\"";

      assertEquals("(x.\"EMPLOYEE_ID\" = 2) and (\"ORDERS\".\"EMPLOYEE_ID\" = 2)",
                   apply(POSTGRESQL, text, cond, false));
   }

   // the column of a table without an alias is quoted as the column of an alias, so a VPM
   // column whose case is not the column's case fails as it fails for an alias
   @Test
   void caseMismatchedColumnIsQuotedAsAliased() throws Exception {
      VpmCondition cond = condition("orders", t -> new XBinaryCondition(
         field(t, "EMPLOYEE_ID"), value("2"), "="));

      assertEquals("\"orders\".\"EMPLOYEE_ID\" = 2",
                   apply(POSTGRESQL, "select id from orders", cond));
      assertEquals("o.\"EMPLOYEE_ID\" = 2", apply(POSTGRESQL, "select o.id from orders o", cond));
   }

   // a condition script keeps its text for a table without an alias
   @Test
   void scriptConditionIsUnchanged() throws Exception {
      VpmCondition cond = new VpmCondition("cond");
      cond.setType(VpmCondition.TABLE);
      cond.setTable("ORDERS");
      cond.setScript("\"ORDERS.EMPLOYEE_ID = 2\"");

      assertEquals("ORDERS.EMPLOYEE_ID = 2",
                   apply(POSTGRESQL, "select \"ORDER_ID\" from \"ORDERS\"", cond));
   }

   // the query table without an alias, the same query with the alias o, and the condition table
   static Stream<Arguments> queries() {
      return Stream.of(
         Arguments.of(POSTGRESQL, "select \"ORDER_ID\" from \"ORDERS\"",
                      "select o.\"ORDER_ID\" from \"ORDERS\" o", "ORDERS"),
         Arguments.of(POSTGRESQL, "select ORDER_ID from ORDERS",
                      "select o.ORDER_ID from ORDERS o", "ORDERS"),
         Arguments.of(POSTGRESQL, "select \"ORDER_ID\" from \"public\".\"ORDERS\"",
                      "select o.\"ORDER_ID\" from \"public\".\"ORDERS\" o", "ORDERS"),
         Arguments.of(POSTGRESQL, "select \"ORDER_ID\" from \"public\".\"ORDERS\"",
                      "select o.\"ORDER_ID\" from \"public\".\"ORDERS\" o", "public.ORDERS"),
         Arguments.of(POSTGRESQL, "select id from orders", "select o.id from orders o",
                      "orders"),
         Arguments.of(SNOWFLAKE, "select \"ORDER_ID\" from \"ORDERS\"",
                      "select o.\"ORDER_ID\" from \"ORDERS\" o", "ORDERS"),
         Arguments.of(SNOWFLAKE, "select ORDER_ID from ORDERS",
                      "select o.ORDER_ID from ORDERS o", "ORDERS"),
         Arguments.of(DEFAULT, "select id from \"user\"", "select o.id from \"user\" o", "user"),
         Arguments.of(DEFAULT, "select id from \"public\".ORDERS",
                      "select o.id from \"public\".ORDERS o", "public.ORDERS"),
         Arguments.of(H2, "select id from \"user\"", "select o.id from \"user\" o", "user"),
         Arguments.of(H2, "select id from ORDERS", "select o.id from ORDERS o", "ORDERS"),
         Arguments.of(SQLSERVER, "select id from \"user\"", "select o.id from \"user\" o",
                      "user"),
         Arguments.of(SQLSERVER, "select id from ORDERS", "select o.id from ORDERS o",
                      "ORDERS"));
   }

   // a column name for a GUI field, and as written in an expression
   private static final String[][] COLUMNS = {
      { "EMPLOYEE_ID", "EMPLOYEE_ID" },
      { "employee_id", "employee_id" },
      { "Employee Id", "\"Employee Id\"" },
      { "user", "\"user\"" },
      { "amount$", "amount$" },
      { "emp#no", "\"emp#no\"" },
      { "\"A.B\"", "\"A.B\"" },
   };

   @ParameterizedTest(name = "{0}: {1} ({3})")
   @MethodSource("queries")
   void columnIsQuotedAsAliased(String helper, String text, String aliasedText, String condTable)
      throws Exception
   {
      String qualifier = XUtil.getTables(parse(helper, text))[0];

      for(String[] column : COLUMNS) {
         String name = column[0];
         String written = column[1];
         List<VpmCondition> conds = new ArrayList<>();
         // field = value
         conds.add(condition(condTable, t -> new XBinaryCondition(
            field(t, name), value("2"), "=")));
         // field in an expression list
         conds.add(condition(condTable, t -> new XBinaryCondition(
            field(t, name), new XExpression("(1, 2)", XExpression.EXPRESSION), "IN")));
         // field = an expression with columns
         conds.add(condition(condTable, t -> new XBinaryCondition(
            field(t, "CUSTOMER_ID"), expression(t + "." + written + " + 1"), "=")));
         // field = field
         conds.add(condition(condTable, t -> new XBinaryCondition(
            field(t, "CUSTOMER_ID"), field(t, name), "=")));
         // field between values
         conds.add(condition(condTable, t -> new XTrinaryCondition(
            field(t, name), value("1"), value("5"), "BETWEEN")));
         // field is null
         conds.add(condition(condTable, t -> new XUnaryCondition(field(t, name), "IS NULL")));
         // an expression = value, and an expression between expressions
         conds.add(condition(condTable, t -> new XBinaryCondition(
            expression(t + "." + written), value("2"), "=")));
         conds.add(condition(condTable, t -> new XTrinaryCondition(
            expression(t + "." + written), expression(t + ".CUSTOMER_ID"),
            expression(t + ".CUSTOMER_ID + 5"), "BETWEEN")));

         for(VpmCondition cond : conds) {
            String aliased = apply(helper, aliasedText, cond);
            String expected = Pattern.compile("(?<![\\w$\"])o\\.").matcher(aliased)
               .replaceAll(Matcher.quoteReplacement(qualifier + "."));

            assertEquals(expected, apply(helper, text, cond),
                         name + " in " + cond.getCondition() + ", aliased: " + aliased);
         }
      }
   }

   private static VpmCondition condition(String table, Function<String, XFilterNode> node) {
      VpmCondition cond = new VpmCondition("cond");
      cond.setType(VpmCondition.TABLE);
      cond.setTable(table);
      cond.setCondition(node.apply(table));
      return cond;
   }

   private static XExpression field(String table, String column) {
      return new XExpression(table + "." + column, XExpression.FIELD);
   }

   private static XExpression value(String value) {
      return new XExpression(value, XExpression.VALUE);
   }

   private static XExpression expression(String exp) {
      return new XExpression(exp, XExpression.EXPRESSION);
   }

   private static String apply(String helper, String text, VpmCondition cond) throws Exception {
      return apply(helper, text, cond, true);
   }

   /**
    * Evaluate the condition for a parsed query the way VpmUtil.applyConditions does.
    */
   private static String apply(String helper, String text, VpmCondition cond, boolean unwrap)
      throws Exception
   {
      UniformSQL sql = parse(helper, text);
      String where = evaluate(sql, cond);
      return unwrap && where.startsWith("(") && where.endsWith(")") ?
         where.substring(1, where.length() - 1) : where;
   }

   /**
    * Add the condition to a parsed query the way VpmUtil.applyConditions does, and generate
    * the query.
    */
   private static String applySentence(String helper, String text, VpmCondition cond)
      throws Exception
   {
      UniformSQL sql = parse(helper, text);
      String where = evaluate(sql, cond);
      sql.setSQLString(null);
      sql.combineWhereByAnd(new XExpressionCondition(
         new XExpression(where, XExpression.EXPRESSION)));
      return sql.toString().replaceAll("\\s+", " ").trim();
   }

   private static String evaluate(UniformSQL sql, VpmCondition cond) throws Exception {
      String[] tables = XUtil.getTables(sql);
      String[] taliases = new String[tables.length];
      Map<String, Integer> counts = new HashMap<>();

      for(int i = 0; i < tables.length; i++) {
         int count = counts.getOrDefault(tables[i], 0);
         taliases[i] = XUtil.getTableAlias(sql, tables[i], count);
         taliases[i] = taliases[i] == null ? tables[i] : taliases[i];
         counts.put(tables[i], count + 1);
      }

      VariableTable vars = new VariableTable();
      XPrincipal user = new XPrincipal(new IdentityID("viewer", null));
      String where = cond.evaluate(null, tables, taliases, new String[0],
                                   sql.getDataSource(), vars, user, false);
      assertNotNull(where);
      return where;
   }

   private static UniformSQL parse(String helper, String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(dataSource(helper));
      sql.setParseSQL(true);

      // the parse is asynchronous
      synchronized(sql) {
         sql.setSQLString(text);
         sql.wait();
      }

      assertTrue(XUtil.isParsedSQL(sql), text);
      assertEquals(helperClass(helper), SQLHelper.getSQLHelper(sql).getClass());
      return sql;
   }

   private static Class<?> helperClass(String helper) {
      return switch(helper) {
         case POSTGRESQL -> PostgreSQLHelper.class;
         case SNOWFLAKE -> SnowflakeHelper.class;
         case H2 -> H2Helper.class;
         case SQLSERVER -> SQLServerHelper.class;
         default -> SQLHelper.class;
      };
   }

   private static JDBCDataSource dataSource(String helper) {
      if(DEFAULT.equals(helper)) {
         return null;
      }

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77861-" + helper);

      switch(helper) {
      case POSTGRESQL -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
         ds.setRuntimeProductName("postgresql");
      }
      case SNOWFLAKE -> {
         ds.setDriver("net.snowflake.client.jdbc.SnowflakeDriver");
         ds.setURL("jdbc:snowflake://account.snowflakecomputing.com");
         ds.setRuntimeProductName("snowflake");
      }
      case H2 -> {
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:test");
         ds.setRuntimeProductName("h2");
      }
      case SQLSERVER -> {
         ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
         ds.setURL("jdbc:sqlserver://localhost:1433;databaseName=db0");
         ds.setRuntimeProductName("sql server");
      }
      default -> throw new IllegalArgumentException(helper);
      }

      return ds;
   }
}
