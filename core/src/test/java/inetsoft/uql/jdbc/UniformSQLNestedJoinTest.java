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

import antlr.RecognitionException;
import inetsoft.test.*;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Bug #77434, UniformSQL keeps parsed joins only as XJoins without their order or
 * nesting, and the regenerated from clause picks its own join order. A join group on
 * the right side of an outer join, and a RIGHT or FULL join mixed with an inner or
 * cross join (from a join keyword or a where clause column join), are only accepted
 * when the generated sql has the same joins. #77475 generates parsed joins in text
 * order, which keeps most of them. The other queries fail the parse and keep the
 * original sql.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLNestedJoinTest {
   // an ANSI join data source, the sql of a query is generated with its data source
   private static JDBCDataSource ansiSource;
   // Oracle uses (+) joins unless the data source is set to ansi join
   private static JDBCDataSource oracleSource;
   private static JDBCDataSource oracleAnsiSource;

   @BeforeAll
   static void createDataSources() {
      CredentialService credentials = mock(CredentialService.class);
      when(credentials.createCredential(any(), anyBoolean()))
         .thenAnswer(inv -> new LocalPasswordCredential());

      try(MockedStatic<CredentialService> service = mockStatic(CredentialService.class)) {
         service.when(CredentialService::getInstance).thenReturn(credentials);
         ansiSource = createDataSource("h2", false);
         oracleSource = createDataSource("oracle", false);
         oracleAnsiSource = createDataSource("oracle", true);
      }
   }

   private static JDBCDataSource createDataSource(String product, boolean ansiJoin) {
      JDBCDataSource source = new JDBCDataSource();
      source.setName(product + (ansiJoin ? " ansi" : ""));
      source.setRuntimeProductName(product);
      source.setAnsiJoin(ansiJoin);
      // a known version, so getting the sql helper doesn't connect to the database
      source.setProductVersion("19.0");
      return source;
   }

   @ParameterizedTest
   @ValueSource(strings = {
      // non-join and missing inner ON and cross inner joins in the group, the generated
      // sql moves the group's condition or table out of the outer join
      "select * from a left join (b join c on c.k = 1) on a.id = b.id",
      "select * from a left join (b cross join c) on a.id = b.id",
      "select * from a left join b cross join c on a.id = b.id",
      // an inner ON that doesn't name its joined table c leaves c without a join (#77515)
      "select * from d left join (a left join b on a.id = b.id join c on a.k = b.k) " +
         "on c.id = d.id",
      "select * from d left join a left join b on a.id = b.id join c on a.k = b.k " +
         "on c.id = d.id",
      "select * from d right join (a left join b on a.id = b.id join c on a.k = b.k) " +
         "on c.id = d.id",
      "select * from d full join (a left join b on a.id = b.id join c on a.k = b.k) " +
         "on c.id = d.id"
   })
   void outerJoinOfNestedJoinFailsCleanly(String text) {
      assertRefused(text, "Unsupported nested join");
   }

   @ParameterizedTest
   @ValueSource(strings = {
      // an inner join ON with a non-join condition left of a right join
      "select * from a join b on b.k = 1 right join c on b.id = c.id",
      "select * from a join b on a.id = b.id and b.k = 1 right join c on b.id = c.id",
      "select * from (b join c on c.k = 1) right join a on a.id = b.id",
      // cross joins
      "select * from (a cross join b) right join c on b.id = c.id",
      // a subquery in the inner ON doesn't hide the inner join
      "select * from a join b on a.id = b.id and b.k in (select c.k from c) " +
         "join e on a.id = e.id right join d on b.id = d.id",
      "select * from a join b on b.k in (select c.k from c) right join d on b.id = d.id",
      // an inner ON that doesn't name its joined table c leaves c without a join (#77515)
      "select * from a left join b on a.id = b.id join c on a.k = b.k right join d on c.id = d.id",
      "select * from a left join b on a.id = b.id join c on a.k = b.k full join d on c.id = d.id",
      "select * from a join b on a.id = b.id join c on a.k = b.k right join d on c.id = d.id",
      "select * from a join c on a.k = 1 right join d on c.id = d.id, b",
      "select * from a left join b on a.id = b.id join c on 1 = 1 right join d on c.id = d.id",
      "select * from a left join b on a.id = b.id join c on a.k = b.k and a.id = b.k " +
         "right join d on c.id = d.id",
      "select * from (a left join b on a.id = b.id join c on a.k = b.k) right join d on c.id = d.id",
      "select * from (select a.id from a left join b on a.id = b.id join c on a.k = b.k " +
         "right join d on c.id = d.id) t"
   })
   void rightJoinWithInnerJoinFailsCleanly(String text) {
      assertRefused(text, "Unsupported RIGHT or FULL join");
   }

   @Test
   void correlatedRightJoinSubqueryParses() throws Exception {
      // the subquery's where join to the outer table a mixes an inner join with its right
      // join. The correlation stays in the subquery's where clause (#77480), so the
      // generated sql has the same joins, except for the (+) joins of oracle
      String text = "select a.id from a where exists (select 1 from b right join c " +
         "on b.id = c.id where c.id = a.id)";
      UniformSQL sql = parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals("select a.id from a where EXISTS ( select 1 from b RIGHT OUTER JOIN c " +
                   "ON b.id = c.id where c.id = a.id)", normalize(sql.getSQLString()));
      assertRefused(text, "Unsupported RIGHT or FULL join", oracleSource);
      assertRefused(text, "Unsupported RIGHT or FULL join", null);
   }

   @ParameterizedTest
   @ValueSource(strings = {
      // the generated sql has the same joins
      "select * from a join b on a.id = b.id right join c on b.id = c.id",
      "select * from a join b on a.id = b.id right join c on a.id = c.id",
      "select * from a inner join b on a.id = b.id full join c on b.id = c.id",
      "select * from (a join b on a.id = b.id) full join c on b.id = c.id",
      "select * from a full join b on a.id = b.id join c on b.id = c.id",
      "select * from a right join b on a.id = b.id join c on b.id = c.id",
      "select * from a join b on a.id = b.id join d on b.id = d.id right join c on a.id = c.id",
      "select * from (b join c on b.id = c.id) right join a on a.id = b.id",
      "select * from a join b using (id) right join c on b.id = c.id",
      // a top level inner join is the same as a where clause join
      "select * from a right join b on a.id = b.id, c where b.id = c.id",
      "select * from a full join b on a.id = b.id, c where b.id = c.id",
      "select * from a right join b on a.id = b.id, c join d on c.id = d.id",
      // a RIGHT join after a comma is refused (#77675, SQLHelperCommaJoinGroupTest)
      // sql generated for query editor joins
      "select * from (a RIGHT OUTER JOIN b ON a.id = b.id ) INNER JOIN c ON b.id = c.id",
      "select * from ((b INNER JOIN c ON b.id = c.id ) RIGHT OUTER JOIN a ON a.id = b.id ) " +
         "LEFT OUTER JOIN d ON a.id = d.id",
      // subqueries
      "select * from x where exists (select 1 from b join c on b.id = c.id " +
         "right join d on c.id = d.id)",
      "select * from a right join b on a.id = b.id where exists " +
         "(select 1 from c join d on c.id = d.id where c.id = a.id)",
      "select * from a right join b on a.id = b.id where b.id in " +
         "(select c.id from c join d on c.id = d.id)",
      "select * from (select a.id, b.k from a right join b on a.id = b.id) t join c on t.id = c.id",
      "select (select count(*) from c join d on c.id = d.id) as n from a right join b on a.id = b.id",
      // generated in text order (#77475), these used to be generated with other joins
      "select * from a join b on a.id = b.id join d on a.id = d.id right join c on b.id = c.id",
      "select * from a join (b join c on b.id = c.id) on a.id = b.id right join d on c.id = d.id",
      "select * from a right join b on a.id = b.id join c on a.id = c.id",
      "select * from a full join b on a.id = b.id join c on a.id = c.id",
      "select * from a right join b on a.id = b.id join c on b.id = c.id join d on d.id = a.id",
      "select * from a right join b on a.id = b.id, c where a.id = c.id",
      "select * from a right join b on a.id = b.id, c, d where a.id = c.id and c.id = d.id",
      "select * from a right join b on a.id = b.id, c join d on c.id = d.id where a.id = d.id",
      "select * from a left join b on a.id = b.id right join c on b.id = c.id where a.id = c.k",
      "select * from a left join b on a.id = b.id join c on c.id = a.id and a.k = b.k " +
         "right join d on c.id = d.id",
      "select * from x where exists (select 1 from b join c on b.id = c.id " +
         "join d on b.id = d.id right join e on c.id = e.id)",
      "select * from (select a.id from a right join b on a.id = b.id, c " +
         "where a.id = c.id) t",
      // a where clause comparison of the outer joined tables stays a where condition
      // (#77478), so it isn't a join
      "select * from a right join b on a.id = b.id where a.id = b.k",
      "select * from a right join b on a.id = b.id where a.id = b.id or a.k = 1",
      "select * from a right join b on a.id = b.id where b.k = 1 and not (a.id = b.k)",
      "select * from a right join b on a.id = b.id right join c on b.id = c.id where a.k = b.k",
      // a nested join on the right side of an outer join
      "select * from a left join (b join c on b.id = c.id) on a.id = b.id",
      "select * from a left join (b inner join c on b.id = c.id) on a.id = b.id",
      "select * from a left join ((b join c on b.id = c.id)) on a.id = b.id",
      "select * from a left join b join c on b.id = c.id on a.id = b.id",
      "select * from a left outer join (b join c on b.id = c.id) on b.id = a.id",
      "select * from a left join (b join c using (id)) on a.id = b.id",
      "select * from a right join (b join c on b.id = c.id) on a.id = b.id",
      "select * from a right join (b join c on b.id = c.id) on b.id = a.id",
      "select * from a full join (b join c on b.id = c.id) on a.id = b.id",
      "select * from a full outer join (b join c on b.id = c.id) on a.id = b.id",
      "select * from a left join (b left join c on b.id = c.id) on a.id = b.id",
      "select * from a left join (b left join c on b.id = c.id) on a.id = c.id",
      "select * from a left join (b left join c on b.id = c.id) on b.id = a.id",
      "select * from a right join (b left join c on c.id = b.id) on c.id = a.id",
      "select * from a left join b left join c on c.id = b.id on b.id = a.id",
      "select * from (a left join b on a.id = b.id) left join (c left join d on d.id = c.id) " +
         "on c.id = a.id",
      "select * from a left join (b right join c on b.id = c.id) on a.id = b.id",
      "select * from a left join b on a.id = b.id left join (c left join d on c.id = d.id) " +
         "on b.id = d.id",
      "select * from a left join b on a.id = b.id left join (c join d on c.id = d.id) " +
         "on b.id = c.id",
      "select * from a left join (b join c on b.id = c.id) on a.id = b.id " +
         "left join d on a.id = d.id",
      "select * from (a left join b on a.id = b.id) left join (c join d on c.id = d.id) " +
         "on a.id = c.id",
      "select * from (select a.id from a left join (b join c on b.id = c.id) " +
         "on a.id = b.id) t",
      "select * from x where exists (select 1 from a left join (b join c on b.id = c.id) " +
         "on a.id = b.id)",
      "select * from x where x.id in (select a.id from a left join (b join c on b.id = c.id) " +
         "on a.id = b.id)",
      "select a.id, t.id from a left join (select b.id from b left join " +
         "(c join d on c.id = d.id) on b.id = c.id) t on a.id = t.id",
      "select a.id from a join b on a.id = b.id where exists (select 1 from c left join " +
         "(d join b on d.id = b.id) on c.id = d.id)"
   })
   void rightJoinWithSameGeneratedJoinsParses(String text) throws Exception {
      UniformSQL sql = parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());

      String generated = normalize(sql.getSQLString());
      UniformSQL reparsed = parse(generated);
      assertEquals(UniformSQL.PARSE_SUCCESS, reparsed.getParseResult(), generated);
      assertEquals(sortJoinColumns(generated), sortJoinColumns(normalize(reparsed.getSQLString())));

      // the original and the generated sql return the same rows. Derby has no FULL join,
      // and the row comparison has the tables a to d
      if(text.startsWith("select * ") && !text.contains(" full ") &&
         !text.matches(".*\\b[ex]\\b.*"))
      {
         String columns = selectColumns(sql);
         assertEquals(0, SQLHelperWhereOrOuterJoinTest.RowCompare.diffCount(
            withColumns(text, columns), withColumns(generated, columns), 120), generated);
      }
   }

   // the id and k of each table of the from clause with a unique label, so that the rows
   // compare the same whatever the column order
   private static String selectColumns(UniformSQL sql) {
      List<String> columns = new ArrayList<>();

      for(int i = 0; i < sql.getTableCount(); i++) {
         String alias = sql.getTableAlias(i);
         columns.add(alias + ".id " + alias + "_id");

         if(!(sql.getSelectTable(i).getName() instanceof UniformSQL)) {
            columns.add(alias + ".k " + alias + "_k");
         }
      }

      return String.join(", ", columns);
   }

   private static String withColumns(String sql, String columns) {
      return sql.startsWith("select * ") ? "select " + columns + sql.substring(8) : sql;
   }

   @ParameterizedTest
   @ValueSource(strings = {
      // left and inner join chains
      "select * from a left join b on a.id = b.id join c on b.id = c.id",
      "select * from a left join b on a.id = b.id join c on c.k = 1",
      "select * from a left join b on a.id = b.id inner join c on a.id = c.id",
      "select * from a join b on a.id = b.id left join c on b.id = c.id",
      "select * from a left join b on a.id = b.id left join c on b.id = c.id",
      "select * from a left join b on a.id = b.id, c where a.id = c.id",
      // nested joins under an inner join, or on the left side
      "select * from a join (b join c on b.id = c.id) on a.id = b.id",
      "select * from a join (b left join c on b.id = c.id) on a.id = b.id",
      "select * from (a inner join b on a.id = b.id) left join c on b.id = c.id",
      // derived tables are a single table
      "select * from a left join (select id, k from b) t on a.id = t.id",
      "select * from a left join (select b.id from b join c on b.id = c.id) t on a.id = t.id",
      "select * from a right join (select c.id, c.k from c join d on c.id = d.id) t " +
         "on a.id = t.id",
      // right and full joins with no inner join
      "select * from a right join b on a.id = b.id",
      "select * from a full outer join b on a.id = b.id",
      "select * from a right join b on a.id = b.id right join c on b.id = c.id",
      "select * from a left join b on a.id = b.id right join c on b.id = c.id",
      "select * from a right join b on a.id = b.id where a.k = 1",
      // a right join in a subquery doesn't refuse the inner join of the query
      "select * from a join b on a.id = b.id where b.id in " +
         "(select c.id from c right join d on c.id = d.id)",
      "select * from a right join b on a.id = b.id where b.id in " +
         "(select c.id from c join d on c.id = d.id)"
   })
   void supportedJoinParses(String text) throws Exception {
      UniformSQL sql = parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertFalse(sql.isLossy());

      // the generated sql is accepted too
      String generated = normalize(sql.getSQLString());
      assertEquals(UniformSQL.PARSE_SUCCESS, parse(generated).getParseResult(), generated);

      UniformSQL processed = newSql(ansiSource);
      new SQLProcessor(processed).parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, processed.getParseResult());
   }

   @Test
   void naturalJoinWithRightJoinFailsCleanly() {
      // a natural join has no join condition. #77435 refuses any natural join with
      // its own message
      assertRefused("select * from a natural join b right join c on b.id = c.id", "Unsupported");
   }

   @Test
   void refusedQueryKeepsOriginalSql() {
      String text = "select * from a left join b on a.id = b.id join c on a.k = b.k " +
         "right join d on c.id = d.id";
      UniformSQL sql = newSql(ansiSource);
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
      assertEquals(text, sql.getSQLString());
   }

   @ParameterizedTest
   @ValueSource(strings = { "inner join", "join" })
   void innerJoinConditionMovesToWhere(String type) throws Exception {
      UniformSQL sql = parse("select a.x from a " + type + " b on a.id = b.id join c on c.k = 1");
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals("select a.x from a, b, c where a.id = b.id and c.k = 1",
                   normalize(sql.getSQLString()));
   }

   /**
    * Join models as the query editor builds them, each "table1.id op table2.id" link with
    * op =, *=, =* or *=*, over a chain, a star and a snowflake of 3 and 4 tables.
    */
   static List<String> editorJoinModels() {
      String[][] shapes = {
         { "a-b", "b-c" }, { "a-b", "a-c" },
         { "a-b", "b-c", "c-d" }, { "a-b", "a-c", "a-d" }, { "a-b", "b-c", "a-d" }
      };
      String[] ops = { "=", "*=", "=*", "*=*" };
      List<String> models = new ArrayList<>();

      for(String[] shape : shapes) {
         int n = (int) Math.pow(ops.length, shape.length);

         for(int i = 0; i < n; i++) {
            StringBuilder model = new StringBuilder();

            for(int j = 0, k = i; j < shape.length; j++, k /= ops.length) {
               model.append(j == 0 ? "" : ",").append(shape[j]).append(":").append(ops[k % ops.length]);
            }

            models.add(model.toString());
         }
      }

      return models;
   }

   @ParameterizedTest
   @MethodSource("editorJoinModels")
   void editorGeneratedSqlRoundTrips(String model) throws Exception {
      String tables = model.contains("d") ? "a, b, c, d" : "a, b, c";
      UniformSQL sql = parse("select a.id from " + tables);

      for(String link : model.split(",")) {
         String[] parts = link.split("[-:]");
         sql.addJoin(new XJoin(new XExpression(parts[0] + ".id", XExpression.FIELD),
                               new XExpression(parts[1] + ".id", XExpression.FIELD), parts[2]));
      }

      sql.clearSQLString();
      String generated = normalize(sql.getSQLString());
      UniformSQL reparsed = parse(generated);
      assertEquals(UniformSQL.PARSE_SUCCESS, reparsed.getParseResult(), generated);

      // the generated sql is the same, except that the outer join ON after a nested
      // inner join can have its columns in the other order (as before this change)
      String generated2 = normalize(reparsed.getSQLString());
      assertEquals(sortJoinColumns(generated), sortJoinColumns(generated2));
      assertEquals(generated2, normalize(parse(generated2).getSQLString()));
   }

   // put the columns of each "x.id = y.id" in name order
   private static String sortJoinColumns(String sql) {
      java.util.regex.Matcher matcher =
         java.util.regex.Pattern.compile("(\\w+\\.\\w+) = (\\w+\\.\\w+)").matcher(sql);
      StringBuilder result = new StringBuilder();

      while(matcher.find()) {
         String c1 = matcher.group(1);
         String c2 = matcher.group(2);
         matcher.appendReplacement(result, c1.compareTo(c2) <= 0 ? c1 + " = " + c2 : c2 + " = " + c1);
      }

      matcher.appendTail(result);
      return result.toString();
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "select * from a join b on a.id = b.id right join c on b.id = c.id",
      "select * from (a RIGHT OUTER JOIN b ON a.id = b.id ) INNER JOIN c ON b.id = c.id",
      "select * from ((b INNER JOIN c ON b.id = c.id ) RIGHT OUTER JOIN a ON a.id = b.id ) " +
         "LEFT OUTER JOIN d ON a.id = d.id",
      "select * from x where x.id in (select c.id from a join b on a.id = b.id " +
         "right join c on b.id = c.id)"
   })
   void rightJoinWithInnerJoinWithoutDataSourceFailsCleanly(String text) {
      // the sql helper that generates the sql later is unknown
      assertRefused(text, "Unsupported RIGHT or FULL join", null);
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "select * from a join b on a.id = b.id right join c on b.id = c.id",
      "select * from (a RIGHT OUTER JOIN b ON a.id = b.id ) INNER JOIN c ON b.id = c.id",
      // a subquery is generated with the data source of the outer query
      "select * from x where x.id in (select c.id from a join b on a.id = b.id " +
         "right join c on b.id = c.id)",
      "select * from x where exists (select 1 from b join c on b.id = c.id " +
         "right join d on c.id = d.id)",
      "select * from (select c.id from a join b on a.id = b.id right join c on b.id = c.id) t"
   })
   void rightJoinWithInnerJoinOnOracleFailsCleanly(String text) throws Exception {
      // Oracle without ansi join generates (+) joins, a.id = b.id and b.id (+)= c.id
      assertRefused(text, "Unsupported RIGHT or FULL join", oracleSource);

      UniformSQL sql = parse(text, oracleAnsiSource);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertFalse(normalize(sql.getSQLString()).contains("(+)"), sql.getSQLString());
   }

   @ParameterizedTest
   @ValueSource(strings = { "a-b:=*,b-c:=", "a-b:*=*,b-c:=", "a-b:*=,b-c:=,a-d:*=" })
   void editorSqlIsParsedWithItsDataSource(String model) throws Exception {
      // the query editor's sql pane and the worksheet sql dialog set the data source
      // before they parse the sql (QueryManagerService.parseSqlString,
      // SQLQueryDialogService.setUpTableWithSQLString)
      for(JDBCDataSource source : new JDBCDataSource[] { ansiSource, oracleSource, oracleAnsiSource }) {
         UniformSQL sql = parse("select a.id from " + (model.contains("d") ? "a, b, c, d" : "a, b, c"),
                                source);

         for(String link : model.split(",")) {
            String[] parts = link.split("[-:]");
            sql.addJoin(new XJoin(new XExpression(parts[0] + ".id", XExpression.FIELD),
                                  new XExpression(parts[1] + ".id", XExpression.FIELD), parts[2]));
         }

         sql.clearSQLString();
         String generated = sql.getSQLString();
         UniformSQL edited = newSql(source);
         new SQLProcessor(edited).parse(generated);
         assertEquals(UniformSQL.PARSE_SUCCESS, edited.getParseResult(),
                      source.getName() + ": " + generated);
      }
   }

   @ParameterizedTest
   @ValueSource(strings = {
      // each of these is generated with different results today, whatever the data source
      "select * from (a cross join b) right join c on b.id = c.id",
      "select * from a left join (b cross join c) on a.id = b.id",
      // #77515, c has no join of its own
      "select * from a left join b on a.id = b.id join c on a.k = b.k right join d on c.id = d.id",
      "select * from a left join b on a.id = b.id join c on a.k = b.k full join d on c.id = d.id",
      "select * from d left join (a left join b on a.id = b.id join c on a.k = b.k) " +
         "on c.id = d.id",
      "select * from a join b on a.id = b.id join c on a.k = b.k right join d on c.id = d.id",
      "select * from a where exists (select 1 from a left join b on a.id = b.id " +
         "join c on a.k = b.k right join d on c.id = d.id)"
   })
   void wrongJoinFailsWithEveryDataSource(String text) {
      for(JDBCDataSource source :
         new JDBCDataSource[] { null, ansiSource, oracleSource, oracleAnsiSource })
      {
         assertRefused(text, "Unsupported", source);
      }
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "select * from a join b on a.id = b.id left join c on b.id = c.id right join d on c.id = d.id",
      "select * from a right join (select c.id, c.k from c join d on c.id = d.id) t " +
         "on a.id = t.id join e on e.id = t.id"
   })
   void rightJoinWithSameGeneratedJoinsParsesWithAnsiSources(String text) throws Exception {
      for(JDBCDataSource source : new JDBCDataSource[] { ansiSource, oracleAnsiSource }) {
         UniformSQL sql = parse(text, source);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), source.getName());

         String generated = normalize(sql.getSQLString());
         assertEquals(generated, normalize(parse(generated, source).getSQLString()),
                      source.getName());
      }
   }

   private static void assertRefused(String text, String message) {
      assertRefused(text, message, ansiSource);
   }

   private static void assertRefused(String text, String message, JDBCDataSource source) {
      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text, source));
      assertTrue(ex.getMessage().contains(message), ex.getMessage());

      UniformSQL sql = newSql(source);
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text) throws Exception {
      return parse(text, ansiSource);
   }

   private static UniformSQL parse(String text, JDBCDataSource source) throws Exception {
      UniformSQL sql = newSql(source);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }

   private static UniformSQL newSql(JDBCDataSource source) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(source);
      return sql;
   }
}
