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

import inetsoft.test.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77501, a bare quoted identifier ("x y", `x y`, "MixedCase") keeps its quotes in every
 * clause when the SQL is regenerated from the parsed {@link UniformSQL}: the sybase
 * {@code alias = "col"} select item, GROUP BY, ORDER BY, WHERE, HAVING and ON, and inside
 * function arguments, CASE, CAST and parentheses. The select list was fixed by #77408.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, UniformSQLQuotedGroupOrderTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLQuotedGroupOrderTest {
   @Test
   void groupByKeepsQuotes() throws Exception {
      assertEquals("select count(*), \"x y\" from t group by \"x y\"",
                   regenerate("select \"x y\", count(*) from t group by \"x y\""));
      // not in the select list
      assertEquals("select count(*) from t group by \"x y\"",
                   regenerate("select count(*) from t group by \"x y\""));
      // backtick and a case-sensitive name
      String generated = regenerate("select `a b`, \"MixedCase\", count(*) from t group by `a b`, \"MixedCase\"");
      assertTrue(generated.endsWith("group by \"a b\", \"MixedCase\""), generated);
   }

   @Test
   void orderByKeepsQuotes() throws Exception {
      assertEquals("select \"x y\" from t order by \"x y\" asc",
                   regenerate("select \"x y\" from t order by \"x y\""));
      // not in the select list
      assertEquals("select a from t order by \"MixedCase\" desc, a asc",
                   regenerate("select a from t order by \"MixedCase\" desc, a"));
      String generated = regenerate("select `a b`, \"MixedCase\" from t order by `a b`, \"MixedCase\"");
      assertTrue(generated.endsWith("order by \"a b\" asc, \"MixedCase\" asc"), generated);
   }

   @Test
   void aliasOfQuotedColumnKeepsQuotes() throws Exception {
      // the alias is generated as its quoted column
      assertEquals("select \"x y\" as z from t order by \"x y\" asc",
                   regenerate("select \"x y\" as z from t order by z"));
      assertEquals("select count(*), \"x y\" as z from t group by \"x y\" order by \"x y\" desc",
                   regenerate("select \"x y\" as z, count(*) from t group by z order by z desc"));

      // postgresql keeps the alias, and a quoted alias is generated as its quoted column
      JDBCDataSource pg = dataSource("org.postgresql.Driver", "jdbc:postgresql://localhost/db", "postgresql");
      String generated = regenerate(parse("select \"x y\" as z, count(*) from t group by z order by z desc", pg));
      assertTrue(generated.endsWith("group by \"z\" order by \"z\" desc"), generated);
      generated = regenerate(parse(generated, pg));
      assertTrue(generated.endsWith("group by \"x y\" order by \"x y\" desc"), generated);
   }

   @Test
   void sybaseAliasFormKeepsQuotes() throws Exception {
      UniformSQL sql = parse("select a = \"x y\" from t", sybase());
      JDBCSelection selection = (JDBCSelection) sql.getSelection();

      assertEquals("x y", selection.getColumn(0));
      assertEquals("a", selection.getAlias(0));
      assertTrue(selection.isQuoted("x y"));
      assertEquals("select \"x y\" as a from t", regenerate(sql));
   }

   @Test
   void conditionsKeepQuotes() throws Exception {
      assertEquals("select a from t where \"MixedCase\" = 1",
                   regenerate("select a from t where \"MixedCase\" = 1"));
      assertEquals("select * from t where \"x y\" is null and \"x y\" BETWEEN 1 and 2",
                   regenerate("select * from t where \"x y\" is null and \"x y\" between 1 and 2"));
      String generated = regenerate("select \"x y\", count(*) from t group by \"x y\" having \"x y\" > 1");
      assertTrue(generated.endsWith("group by \"x y\" having \"x y\" > 1"), generated);
      // an inner join ON is regenerated as a where condition
      generated = regenerate("select * from t join u on \"x y\" = u.id");
      assertTrue(generated.endsWith("where \"x y\" = u.id"), generated);
      // the where clause next to an outer join
      generated = regenerate("select * from t left join u on t.id = u.id where `x y` = 1");
      assertTrue(generated.endsWith("where \"x y\" = 1"), generated);
   }

   @Test
   void expressionOperandsKeepQuotes() throws Exception {
      assertEquals("select coalesce(\"x y\",0) from t", regenerate("select coalesce(\"x y\", 0) from t"));
      assertEquals("select nullif(\"x y\",0) from t", regenerate("select nullif(\"x y\", 0) from t"));
      assertEquals("select cast(\"x y\" as int) from t", regenerate("select cast(\"x y\" as int) from t"));
      assertEquals("select case when \"x y\" = 1 then 1 else 0 END from t",
                   regenerate("select case when \"x y\" = 1 then 1 else 0 end from t"));
      assertEquals("select case \"x y\" when 1 then 1 END from t",
                   regenerate("select case \"x y\" when 1 then 1 end from t"));
      assertEquals("select * from t where (\"x y\") = 1", regenerate("select * from t where (\"x y\") = 1"));
      assertEquals("select a from t order by (\"x y\") asc", regenerate("select a from t order by (\"x y\")"));
      assertEquals("select * from t where coalesce(\"MixedCase\",0) = 1",
                   regenerate("select * from t where coalesce(\"MixedCase\",0) = 1"));
   }

   @Test
   void unquotedAndQualifiedNamesUnchanged() throws Exception {
      assertEquals("select MixedCase from t where MixedCase = 1 group by MixedCase order by MixedCase asc",
                   regenerate("select MixedCase from t where MixedCase = 1 group by MixedCase " +
                              "order by MixedCase"));
      assertEquals("select t.\"x y\" from t where t.\"x y\" = 1 group by t.\"x y\" order by t.\"x y\" asc",
                   regenerate("select t.\"x y\" from t where t.\"x y\" = 1 group by t.\"x y\" " +
                              "order by t.\"x y\""));
      assertEquals("select \"x y\"+1, count(*) from t group by \"x y\"+1 order by 1 asc",
                   regenerate("select \"x y\"+1, count(*) from t group by \"x y\"+1 order by 1"));
      assertEquals("select * from t where a = 'x y' and upper(\"x y\") = 'A'",
                   regenerate("select * from t where a = 'x y' and upper(\"x y\") = 'A'"));
   }

   @Test
   void dialectQuoteIsUsed() throws Exception {
      String[][] dialects = {
         { "com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost/db", "mysql", "MySQLHelper", "`" },
         { "org.postgresql.Driver", "jdbc:postgresql://localhost/db", "postgresql", "PostgreSQLHelper", "\"" },
         { "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:db", "oracle", "OracleSQLHelper", "\"" },
         { "com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver://localhost", "sql server",
           "SQLServerHelper", "\"" },
         { "net.snowflake.client.jdbc.SnowflakeDriver", "jdbc:snowflake://localhost", "snowflake",
           "SnowflakeHelper", "\"" },
      };

      for(String[] dialect : dialects) {
         UniformSQL sql = parse("select a, count(*) from t where `MixedCase` = 1 group by \"MixedCase\" " +
                                "order by \"x y\"", dataSource(dialect[0], dialect[1], dialect[2]));
         assertEquals(dialect[3], SQLHelper.getSQLHelper(sql).getClass().getSimpleName());
         String generated = regenerate(sql);
         String q = dialect[4];

         // no double quotes on a dialect that quotes the names it knows
         assertFalse(generated.contains(q + q), generated);
         assertTrue(generated.contains("where " + q + "MixedCase" + q + " = 1"), generated);
         assertTrue(generated.endsWith("group by " + q + "MixedCase" + q + " order by " + q + "x y" + q + " asc"),
                    generated);
      }
   }

   @Test
   void metadataResolvedNamesKeepQuotes() throws Exception {
      // after JDBCUtil.fixUniformSQLInfo the names are qualified by their table
      UniformSQL sql = parse("select \"MixedCase\", count(*) from t where \"MixedCase\" = 1 and " +
                             "\"x y\" > 0 group by \"MixedCase\" having \"MixedCase\" > 1 " +
                             "order by \"MixedCase\"");
      resolve(sql, "t", "MixedCase", "x y", "a");
      String generated = regenerate(sql);

      assertEquals("select count(*), t.\"MixedCase\" from t where t.\"MixedCase\" = 1 and t.\"x y\" > 0 " +
                   "group by t.\"MixedCase\" having \"MixedCase\" > 1 order by t.\"MixedCase\" asc",
                   generated);

      // postgresql stores the table quoted, only the column segment is quoted
      sql = parse("select a from t where \"MixedCase\" = 1 group by \"x y\"",
                  dataSource("org.postgresql.Driver", "jdbc:postgresql://localhost/db", "postgresql"));
      resolve(sql, "t", "MixedCase", "x y", "a");
      generated = regenerate(sql);
      assertTrue(generated.endsWith("from \"t\" where t.\"MixedCase\" = 1 group by t.\"x y\""), generated);
   }

   @Test
   void namesResolvedByFixUniformSQLInfoKeepQuotes() throws Exception {
      // the column metadata comes from the repository. SQLTypes.getQualifiedName logs an error
      // that it can't get the root meta-data from XRepository.getRepository(), then goes on
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
         }

         XTypeNode result = new XTypeNode("Result");

         for(String column : new String[] { "MixedCase", "x y", "a" }) {
            result.addChild(XSchema.createPrimitiveType(column, Integer.class));
         }

         XTypeNode meta = new XTypeNode("meta");
         meta.addChild(result);
         return meta;
      });

      JDBCDataSource ds = dataSource("org.h2.Driver", "jdbc:h2:mem:bug77501", "h2");
      UniformSQL sql = parse("select \"MixedCase\", count(*) from t where \"MixedCase\" = 1 " +
                             "group by \"MixedCase\" order by \"x y\"", ds);
      JDBCUtil.fixUniformSQLInfo(sql, repository, null, ds);
      String generated = regenerate(sql);

      assertEquals("H2Helper", SQLHelper.getSQLHelper(sql).getClass().getSimpleName());
      assertEquals("t.MixedCase", sql.getSelection().getColumn(0), "not resolved: " + generated);
      assertEquals("select count(*), t.\"MixedCase\" from t where t.\"MixedCase\" = 1 " +
                   "group by t.\"MixedCase\" order by t.\"x y\" asc", generated);
   }

   @Test
   void quotedFlagsSurviveXmlRoundTrip() throws Exception {
      UniformSQL sql = parse("select a = \"x y\", count(*) from t where \"MixedCase\" = 1 " +
                             "group by \"x y\" having \"x y\" > 1 order by \"MixedCase\" desc", sybase());
      String expected = regenerate(sql);
      UniformSQL loaded = reload(sql);

      assertEquals(expected, regenerate(loaded));
      assertTrue(loaded.isQuotedField("x y"));
      assertTrue(loaded.isQuotedField("MixedCase"));
      assertFalse(loaded.isQuotedField("a"));
   }

   @Test
   void oldXmlWithoutFlagsIsUnquoted() throws Exception {
      UniformSQL sql = parse("select a, count(*) from t where \"MixedCase\" = 1 group by \"x y\" " +
                             "order by \"MixedCase\"");
      String xml = toXML(sql).replace(" quoted=\"true\"", "").replace(" quote=\"1\"", "");
      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      loaded.clearSQLString();

      assertEquals("select a, count(*) from t where MixedCase = 1 group by x y order by MixedCase asc",
                   normalize(loaded.getSQLString()));
   }

   @Test
   void reservedWordsBracketsAndSubqueriesKeepQuotes() throws Exception {
      assertEquals("select count(*), \"order\" from t group by \"order\" order by \"order\" asc",
                   regenerate("select \"order\", count(*) from t group by \"order\" order by \"order\""));
      assertEquals("select \"user\" from t where \"user\" = 'a' order by \"select\" asc",
                   regenerate("select \"user\" from t where \"user\" = 'a' order by \"select\""));
      assertEquals("select \"x y\" from t group by \"x y\" order by \"x y\" asc",
                   regenerate("select [x y] from t group by [x y] order by [x y]"));

      String query = "select \"x y\" from t where \"x y\" in (select \"x y\" from u) and " +
         "exists (select 1 from u where u.k = \"x y\")";
      assertEquals("select \"x y\" from t where \"x y\" IN ( select \"x y\" from u) and " +
                   "EXISTS ( select 1 from u where u.k = \"x y\")", regenerate(query));
      assertEquals("select `x y` from t where `x y` IN ( select `x y` from u) and " +
                   "EXISTS ( select 1 from u where u.k = `x y`)",
                   regenerate(parse(query, dataSource("com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost/db",
                                                      "mysql"))));
   }

   @Test
   void unparsedExpressionsAndCopiesKeepTheirQuoting() throws Exception {
      // expressions built in code (worksheet, VPM conditions) never set the flag
      UniformSQL sql = parse("select a from t");
      sql.setWhere(new XBinaryCondition(new XExpression("x y", XExpression.FIELD),
                                        new XExpression("1", XExpression.VALUE), "="));
      sql.setOrderBy("MixedCase", "asc");
      sql.setGroupBy(new Object[] { "x y" });
      assertEquals("select a from t where x y = 1 group by x y order by MixedCase asc", regenerate(sql));

      sql = parse("select \"x y\", count(*) from t where \"MixedCase\" = 1 group by \"x y\" " +
                  "order by \"MixedCase\"");
      String expected = "select count(*), \"x y\" from t where \"MixedCase\" = 1 group by \"x y\" " +
         "order by \"MixedCase\" asc";
      UniformSQL copy = new UniformSQL();
      copy.read(sql);

      assertEquals(expected, regenerate((UniformSQL) sql.clone()));
      assertEquals(expected, regenerate(copy));
   }

   @Test
   void windowClauseKeepsQuotes() throws Exception {
      String query = "select row_number() over (partition by \"x y\", a order by \"MixedCase\" desc, `a b`) " +
         "as r from t";
      assertEquals("select row_number() over ( partition by \"x y\", a order by \"MixedCase\" desc ,`a b` asc) " +
                   "as r from t", regenerate(query));

      // the window text is kept as written, postgresql also quotes the names it knows
      String generated = regenerate(parse(query, dataSource("org.postgresql.Driver",
                                                            "jdbc:postgresql://localhost/db", "postgresql")));
      assertTrue(generated.contains("over ( partition by \"x y\", \"a\" order by \"MixedCase\" desc ,`a b` asc)"),
                 generated);
   }

   @Test
   void malformedQuoteAttributeIsUnquoted() throws Exception {
      UniformSQL sql = parse("select a from t where \"MixedCase\" = 1 and \"x y\" = 2");
      String xml = toXML(sql).replaceFirst(" quote=\"1\"", " quote=\"x\"")
         .replaceFirst(" quote=\"1\"", " quote=\"7\"");
      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());

      assertEquals("select a from t where MixedCase = 1 and x y = 2", regenerate(loaded));
   }

   @Test
   void regeneratedSqlRegeneratesToItself() throws Exception {
      String[] queries = {
         "select coalesce(`x y`, 0), case when \"x y\" = 1 then 1 end from t where (\"x y\") = 1",
         "select \"x y\" as z from t order by z",
         "select count(*) from t group by \"x y\", a",
         "select row_number() over (partition by \"x y\" order by \"MixedCase\") as r from t",
         "select a from t where \"x y\" in (\"MixedCase\", 2)",
      };

      for(String query : queries) {
         String generated = regenerate(query);
         assertEquals(generated, regenerate(generated), query);
      }

      // the alias form is parsed on a T-SQL data source only (#77785)
      String query = "select a = \"x y\", count(*) from t where \"MixedCase\" = 1 group by \"x y\" " +
         "having \"x y\" > 1 order by \"MixedCase\" desc";
      String generated = regenerate(parse(query, sybase()));
      assertEquals(generated, regenerate(parse(generated, sybase())), query);
   }

   @Test
   void mixedCaseRowsMatchOnDerby() throws Exception {
      String[] queries = {
         "select a from t where \"MixedCase\" = 1",
         "select a from t order by \"MixedCase\" desc, a",
         "select \"MixedCase\", count(*) from t group by \"MixedCase\" order by 1",
         "select \"MixedCase\" as m, max(a) from t group by \"MixedCase\" having \"MixedCase\" > 1",
         "select coalesce(\"MixedCase\", 0) from t where (\"MixedCase\") = 2",
      };

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77501;create=true");
          Statement stmt = conn.createStatement())
      {
         // MixedCase and MIXEDCASE are different columns, an unquoted MixedCase is MIXEDCASE
         stmt.execute("create table t (\"MixedCase\" int, MIXEDCASE int, a int)");
         stmt.execute("insert into t values (1, 10, 1), (2, 10, 2), (1, 20, 3)");

         for(String query : queries) {
            String generated = regenerate(query);
            assertEquals(rows(stmt, query), rows(stmt, generated), query + " -> " + generated);
         }
      }
      finally {
         try {
            DriverManager.getConnection("jdbc:derby:memory:bug77501;drop=true");
         }
         catch(SQLException ignore) {
            // a successful drop is reported as an exception
         }
      }
   }

   private static List<List<Object>> rows(Statement stmt, String query) throws SQLException {
      List<List<Object>> rows = new ArrayList<>();

      try(ResultSet rs = stmt.executeQuery(query)) {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            List<Object> row = new ArrayList<>();

            for(int i = 1; i <= count; i++) {
               row.add(rs.getObject(i));
            }

            rows.add(row);
         }
      }

      return rows;
   }

   // the metadata step of JDBCUtil.fixUniformSQLInfo, with the columns of one table
   private static void resolve(UniformSQL sql, String table, String... columns) {
      for(String column : columns) {
         sql.addField(new XField(column, column, table, XSchema.INTEGER));
      }

      JDBCUtil.fixSelectionInfo(sql);
      JDBCUtil.expandAsterisk(sql);
      JDBCUtil.fixWhereInfo(sql);
      sql.syncTable();
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

   private static JDBCDataSource dataSource(String driver, String url, String product) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77501");
      ds.setDriver(driver);
      ds.setURL(url);
      ds.setRuntimeProductName(product);
      // otherwise the mysql and oracle helpers ask the repository for it
      ds.setProductVersion("10.0");
      return ds;
   }

   // select a = b is the T-SQL alias form, parsed only for SQL Server and Sybase (#77785)
   private static JDBCDataSource sybase() {
      JDBCDataSource ds = dataSource("net.sourceforge.jtds.jdbc.Driver", "jdbc:jtds:sybase://localhost/db", "sybase");
      assertEquals("sybase", SQLHelper.getSQLHelper(ds).getSQLHelperType());
      return ds;
   }

   private static UniformSQL reload(UniformSQL sql) throws Exception {
      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(toXML(sql))).getDocumentElement());
      return loaded;
   }

   private static String toXML(UniformSQL sql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return buffer.toString();
   }

   private static String regenerate(String text) throws Exception {
      return regenerate(parse(text));
   }

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   // the generated sql is pretty-printed (and its columns sorted)
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text) throws Exception {
      return parse(text, null);
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();

      if(ds != null) {
         sql.setDataSource(ds);
      }

      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      return sql;
   }
}
