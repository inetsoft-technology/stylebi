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
package inetsoft.uql.util;

import antlr.SemanticException;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.vpm.VpmCondition;
import inetsoft.uql.jdbc.XBinaryCondition;
import inetsoft.uql.jdbc.XExpression;
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.composer.ws.dialog.ExpressionDialogService;
import inetsoft.web.portal.controller.database.LogicalModelController;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.portal.model.database.StringWrapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.StringReader;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77493, a scalar subquery builds a UniformSQL even when the parse only checks an
 * expression, so a join or GROUP BY the model refuses made a valid expression invalid in
 * the SQL expression checks (calc field, query expression column, logical model expression,
 * worksheet sql expression), and VpmCondition left the table of a VPM expression unaliased.
 * The checks must judge the syntax only, and must still report a syntax error by its token.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLExpressionValidityRefusalTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLExpressionValidityRefusalTest {
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   // scalar subqueries that are valid sql but hold a construct the model refuses
   private static final String[] REFUSED = {
      "(select max(b.x) from b natural join c)",
      "(select max(b.x) from b left join c on 1 = 1)",
      "(select max(b.x) from b left join c on b.id = c.id and b.k > 1)",
      "(select max(b.x) from b left join c on id = id2)",
      "(select max(a.x) from a left join b on a.id = z.id)",
      "(select max(a.x) from a, b where a.id *= z.id)",
      "(select max(a.x) from a join (b join c on b.id = c.id) using (id))",
      "(select count(*) from a left join b)",
      "(select max(b.x) from b union join c on b.id = c.id)",
      "(select max(b.k) from b group by rollup(b.k))",
      "(select max(b.k) from b group by cube(b.k, b.j))",
      "(select max(b.k) from b group by grouping sets ((b.k), (b.j)))",
      "(select max(b.k) from b group by (), b.k)",
   };

   @ParameterizedTest
   @ValueSource(strings = {
      "(select max(b.x) from b natural join c)",
      "(select max(b.x) from b left join c on 1 = 1)",
      "(select max(b.x) from b left join c on b.id = c.id and b.k > 1)",
      "(select max(b.x) from b left join c on id = id2)",
      "(select max(a.x) from a left join b on a.id = z.id)",
      "(select max(a.x) from a, b where a.id *= z.id)",
      "(select max(a.x) from a join (b join c on b.id = c.id) using (id))",
      "(select count(*) from a left join b)",
      "(select max(b.x) from b union join c on b.id = c.id)",
      "(select max(b.k) from b group by rollup(b.k))",
      "(select max(b.k) from b group by cube(b.k, b.j))",
      "(select max(b.k) from b group by grouping sets ((b.k), (b.j)))",
      "(select max(b.k) from b group by (), b.k)",
      "a.x + (select max(b.x) from b natural join c where b.id = a.id)",
      "coalesce((select max(b.k) from b group by rollup(b.k)), 0)",
      // TOP failed with a NullPointerException outside a statement parse
      "(select top 1 b.x from b)",
      // a derived column list, which #77494 refuses
      "(select max(p) from (select 1 x) t(p))",
      // controls with no refusal
      "(select max(b.x) from b left join c on b.id = c.id)",
      "(select max(b.k) from b group by b.k)",
   })
   void validExpression(String exp) throws Exception {
      assertTrue(XUtil.isSQLExpressionValid(exp), exp);
      assertTrue(queryService().checkExpression(exp), exp);
      assertNull(logicalModelController().checkExpressionSQL(wrap(exp)), exp);
      expressionService().check(exp, null, true);
   }

   // the cases above fail a normal parse, so they do test the refusals
   @Test
   void normalParseRefuses() {
      for(String exp : REFUSED) {
         assertThrows(SemanticException.class, () -> parse(exp), exp);
      }
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "(select from)",
      "(select a from b where)",
      "(select max(b.x) from b left join c on)",
      "(select max(b.x) from b left join c on 1 = 1 where)",
      "(select max(b.x) from b natural join c where)",
      "(select max(b.x) from b union join c on)",
      "(select max(b.k) from b group by rollup(b.k)",
      "(select max(b.k) from b group by rollup(b.k) where)",
      // the END check is a predicate that throws a SemanticException
      "case when a = 1 then 2 else 3",
      "max(a.x",
   })
   void invalidExpression(String exp) {
      assertFalse(XUtil.isSQLExpressionValid(exp), exp);
      assertFalse(queryService().checkExpression(exp), exp);
      assertNotNull(logicalModelController().checkExpressionSQL(wrap(exp)), exp);
      assertThrows(Exception.class, () -> expressionService().check(exp, null, true), exp);
   }

   // the error of a syntax error still names the token, not a memoized guessing failure
   @Test
   void syntaxErrorNamesToken() {
      String exp = "(select from)";
      StringWrapper result = logicalModelController().checkExpressionSQL(wrap(exp));
      assertNotNull(result);
      assertTrue(result.getBody().contains("unexpected token"), result.getBody());

      Exception ex = assertThrows(Exception.class,
                                  () -> expressionService().check(exp, null, true));
      assertTrue(ex.getMessage().contains("unexpected token"), ex.getMessage());

      ex = assertThrows(Exception.class, () -> XUtil.parseSQLExpressionSyntax(exp));
      assertTrue(ex.getMessage().contains("unexpected token"), ex.getMessage());

      // a missing END keeps the error of the normal parse
      ex = assertThrows(Exception.class,
                        () -> XUtil.parseSQLExpressionSyntax("case when a = 1 then 2 else 3"));
      assertInstanceOf(SemanticException.class, ex);
   }

   // a VPM expression that holds a refused subquery gets its table replaced by the alias
   @Test
   void vpmConditionRewritesTable() throws Exception {
      String sub = "(select max(r.x) from r left join c on 1 = 1 where r.id = orders.id)";
      String cond = vpm(sub);
      assertTrue(cond.contains("r.id = o.id"), cond);
      assertFalse(cond.contains("orders.id"), cond);

      // control: the same rewrite as a subquery the model can hold
      String control = vpm("(select max(r.x) from r left join c on r.id = c.id " +
                              "where r.id = orders.id)");
      assertTrue(control.contains("r.id = o.id"), control);

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:vpm77493;create=true");
          Statement stmt = conn.createStatement())
      {
         stmt.execute("create table orders(id int, region int)");
         stmt.execute("create table r(id int, x int)");
         stmt.execute("create table c(id int)");
         stmt.execute("insert into orders values (1, 10), (2, 20)");
         stmt.execute("insert into r values (1, 10), (2, 99)");
         stmt.execute("insert into c values (1)");

         List<Integer> rows = new ArrayList<>();

         try(ResultSet rs = stmt.executeQuery("select o.id from orders o where " + cond)) {
            while(rs.next()) {
               rows.add(rs.getInt(1));
            }
         }

         assertEquals(List.of(1), rows, cond);
      }
   }

   private static String vpm(String exp) throws Exception {
      VpmCondition cond = new VpmCondition("c1");
      cond.setTable("orders");
      cond.setCondition(new XBinaryCondition(new XExpression("orders.region", XExpression.FIELD),
         new XExpression(exp, XExpression.EXPRESSION), "="));
      return cond.evaluate(null, new String[] { "orders" }, new String[] { "o" },
                           new String[] { "region", "id" }, null, new VariableTable(), null,
                           false);
   }

   private static void parse(String exp) throws Exception {
      new SQLParser(new SQLLexer(new StringReader(exp))).value_exp();
   }

   private static StringWrapper wrap(String exp) {
      StringWrapper wrapper = new StringWrapper();
      wrapper.setBody(exp);
      return wrapper;
   }

   private static QueryManagerService queryService() {
      return new QueryManagerService(null, null, null, null, null);
   }

   private static LogicalModelController logicalModelController() {
      return new LogicalModelController(null, null, null, null, null, null);
   }

   private static ExpressionDialogService expressionService() {
      return new ExpressionDialogService(null, null, null);
   }
}
