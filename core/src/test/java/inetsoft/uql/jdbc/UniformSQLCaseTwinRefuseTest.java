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
import inetsoft.uql.XRepository;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import inetsoft.util.Plugins;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77643. PostgreSQL folds an unquoted name to lower case and Snowflake and Exasol to
 * upper case, so "MixedCase" and MixedCase are two columns there. The parser quotes a name
 * written unquoted in its written case on these helpers, so both are stored as "MixedCase",
 * the regenerated sql names one column twice and returns other rows. A statement that writes
 * one name both ways, as a column, a qualifier, or a quoted select alias and a bare order/group
 * by name, is refused there and its sql runs as written. The names are compared in their exact
 * text, names that differ in case ("Id" and id) are other names and regenerate correctly.
 *
 * The refused shapes were checked against PostgreSQL 16 rows on t(id, x, "MixedCase",
 * mixedcase): the original and the regenerated sql of main return other rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLCaseTwinRefuseTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLCaseTwinRefuseTest {
   // a data source with a real driver and URL needs the credential service, and the JDBC
   // driver types of Config to pick its SQL helper
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      // the derby helper asks the repository for the database version
      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   private static final String[] FOLDING = { "postgresql", "snowflake", "exasol" };
   private static final String[] NOT_FOLDING = {
      "h2", "derby", "oracle", "mysql", "sql server"
   };

   /**
    * A column written quoted and unquoted in any clause or query level, also in two tables.
    */
   @ParameterizedTest
   @ValueSource(strings = {
      "select t.\"MixedCase\", t.MixedCase as b from t",
      "select \"MixedCase\", MixedCase as b from t",
      "select t.\"MixedCase\", u.MixedCase as b from t, u",
      "select \"MixedCase\", count(*) from t group by \"MixedCase\", MixedCase",
      "select \"MixedCase\" from t order by MixedCase desc",
      "select \"MixedCase\" as x from t group by MixedCase",
      "select \"MixedCase\" from t order by MixedCase + 1",
      "select t.id from t where \"MixedCase\" = 1 or MixedCase = 2",
      "select t.id from t join u on t.id = u.id and u.\"MixedCase\" = t.MixedCase",
      "select max(\"MixedCase\") from t group by id having max(MixedCase) > 1",
      "select \"MixedCase\" from t where id in (select id from u where MixedCase = 1)",
      "select id from (select MixedCase, id from t) q where q.\"MixedCase\" = 1",
      "select id, (select max(MixedCase) from u) as m, \"MixedCase\" from t",
      "select \"Year\", Year as b from t",
      "select a.id from a right join b on a.id = b.id join c on b.id = c.id " +
         "where a.\"MixedCase\" = c.MixedCase"
   })
   void columnTwinIsRefused(String text) throws Exception {
      assertRefused(text, FOLDING);
   }

   /**
    * A qualifier of a column written quoted and unquoted. The table names of the from clause
    * aren't compared, "Sales" sales is a table and its alias.
    */
   @ParameterizedTest
   @ValueSource(strings = {
      "select \"Tab\".x, Tab.y from \"Tab\", Tab",
      "select s.\"Mixed\".x, s.Mixed.y from s.\"Mixed\", s.Mixed",
      "select \"Tab\".x from \"Tab\" where Tab.y = 1"
   })
   void qualifierTwinIsRefused(String text) throws Exception {
      assertRefused(text, FOLDING);
   }

   /**
    * A quoted select alias and a bare unquoted order/group by name of the same text. The
    * database sorts by the column the unquoted name folds to, the regenerated "MixedCase"
    * sorts by the alias.
    */
   @ParameterizedTest
   @ValueSource(strings = {
      "select id, x as \"MixedCase\" from t order by MixedCase",
      "select id, coalesce(nickname, name) as \"Name\" from emp order by Name desc",
      "select x as \"MixedCase\", count(*) from t group by MixedCase",
      "select id, \"MixedCase\" = x from t order by MixedCase"
   })
   void quotedAliasTwinIsRefused(String text) throws Exception {
      assertRefused(text, FOLDING);
   }

   /**
    * A twin is a twin only where the database folds its unquoted name to another case.
    */
   @Test
   void twinOfTheFoldedCaseIsRefusedOnlyWhereItsFolded() throws Exception {
      assertRefused("select \"MIXEDCASE\", MIXEDCASE as b from t", "postgresql");
      assertRefused("select \"T\".x, T.y from \"T\", T", "postgresql");
      assertParsed("select \"MIXEDCASE\", MIXEDCASE as b from t", "snowflake", "exasol");
      assertRefused("select \"mixedcase\", mixedcase as b from t", "snowflake", "exasol");
      assertRefused("select \"t\".x, t.y from \"t\", t", "snowflake", "exasol");
      assertParsed("select \"mixedcase\", mixedcase as b from t", "postgresql");
      assertParsed("select \"t\".x, t.x from t", "postgresql");
      assertRefused("select id, k as \"A\" from t order by A", "postgresql");
      assertParsed("select id, k as \"A\" from t order by A", "snowflake", "exasol");
      assertRefused("select id, k as \"a\" from t order by a", "snowflake", "exasol");
      assertParsed("select id, k as \"a\" from t order by a", "postgresql");
      assertParsed("select name as \"NAME\" from emp order by NAME", "snowflake", "exasol");
      assertParsed("select name as \"name\" from emp order by name", "postgresql");
      // the same name on the database, a known over-refusal that costs only vpm
      assertRefused("select name as \"NAME\" from emp order by NAME", "postgresql");
      assertRefused("select name as \"name\" from emp order by name", "snowflake", "exasol");
   }

   /**
    * The regenerated sql that checks the join order is parsed without the check, snowflake and
    * exasol write order by "a" for alias A (Bug #77434).
    */
   @Test
   void joinOrderCheckIsNotRefused() throws Exception {
      assertParsed("select a.id as A from a right join b on a.id = b.id join c on b.id = c.id " +
                   "order by a", FOLDING);
   }

   /**
    * Names that differ in more than their quotes are other names, and a twin needs a helper
    * that folds names. A refused statement on these runs as written, which is right too, so
    * plausible sql isn't refused.
    */
   @ParameterizedTest
   @ValueSource(strings = {
      "select Name from t order by name",
      "select count(*) as Total from t order by total",
      "select c.\"Name\", p.id from \"Customers\" c join products p on p.cust = c.\"Id\"",
      "select c.\"Name\", p.name from \"Customers\" c join products p on p.cust = c.\"Id\"",
      "select \"Name\" as name, count(*) as n from \"Customers\" group by \"Name\" order by name",
      "select id, \"userId\" as userId from emp order by userId",
      "select \"userId\" as userId, count(*) from emp group by userId",
      "select \"MixedCase\" as MixedCase from t order by MixedCase, id",
      "select id, name as \"Name\" from emp order by name",
      "select id, firstname as \"FirstName\" from emp order by firstname",
      "select k as \"A\" from t order by a",
      "select k as \"K\" from t order by k + 1",
      "select firstname as \"FirstName\" from emp",
      "select \"FirstName\" as firstname from emp",
      "select sales.amount from \"Sales\" sales",
      "select \"MySchema\".\"Sales\".id from \"MySchema\".\"Sales\"",
      "select \"MixedCase\", mixedcase as b from t",
      "select \"NAME\", name as b from t",
      "select \"t\".*, t2.x from \"t\", t2",
      "select \"A\".id, a.id from \"A\", a where \"A\".id = a.id",
      "select id as A from t order by a",
      "select coalesce(nickname, name) as \"Name\" from emp order by \"Name\"",
      "select k as \"MixedCase\" from t order by 1",
      "select e.k as \"K\" from emp e order by e.K"
   })
   void otherNamesAreParsed(String text) throws Exception {
      assertParsed(text, FOLDING);
      assertParsed(text, NOT_FOLDING);
   }

   /**
    * An unquoted alias written in another case is the alias where the name is folded, so the
    * bare order by name of it isn't a column.
    */
   @Test
   void unquotedAliasOfTheFoldedCaseIsParsed() throws Exception {
      assertParsed("select \"id\" as ID from t order by id", "snowflake", "exasol");
      assertParsed("select \"ID\" as id from t order by ID", "postgresql");
   }

   /**
    * The sql of StyleBI written for the aliases on snowflake and exasol (order by "a" for alias
    * A) is parsed again, the order by name is quoted.
    */
   @Test
   void generatedSqlOfAnAliasIsParsed() throws Exception {
      for(String type : new String[] { "snowflake", "exasol" }) {
         UniformSQL sql = parse("select id as A from t order by a desc", dataSource(type));
         sql.clearSQLString();
         String generated = sql.getSQLString().replaceAll("\\s+", " ").trim();
         assertTrue(generated.contains("order by \"a\""), type + ": " + generated);
         assertParsed(generated, type);
      }
   }

   /**
    * Without the data source the folding is unknown, an expression is checked for its syntax
    * only, and a parser that is guessing doesn't run the check, so a twin is accepted there.
    */
   @Test
   void twinIsAcceptedWithoutTheFoldingOrForItsSyntax() throws Exception {
      String text = "select \"MixedCase\", MixedCase as b from t";
      UniformSQL sql = new UniformSQL();
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());

      assertTrue(XUtil.isSQLExpressionValid("(select \"MixedCase\" from t where MixedCase = 1)"));
      assertTrue(XUtil.isSQLExpressionValid("t.\"MixedCase\" = t.MixedCase"));

      for(String type : FOLDING) {
         UniformSQL guessed = new UniformSQL();
         guessed.setDataSource(dataSource(type));
         SQLParser parser = new SQLParser(new SQLLexer(new StringReader(text)));
         parser.getInputState().guessing = 1;
         parser.direct_select_stmt_n_rows(guessed);
      }

      // the condition rules of vpm and of a structured sql have no query
      new SQLParser(new SQLLexer(new StringReader("\"MixedCase\" = MixedCase")))
         .search_condition();
   }

   /**
    * A twin saved before the check (the sql string and parse result success) is lossy once its
    * data source is set, so it runs as written.
    */
   @Test
   void twinSavedBeforeTheCheckIsLossy() throws Exception {
      for(String text : new String[] {
         "select t.\"MixedCase\", t.MixedCase as b from t",
         "select \"MixedCase\", MixedCase as b from t",
         "select t.id from t where \"MixedCase\" = 1 or MixedCase = 2",
         "select id, x as \"MixedCase\" from t order by MixedCase" })
      {
         // the parse of main: without the folding the check doesn't run
         UniformSQL saved = new UniformSQL();
         saved.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
         saved.sqlstring = text;
         String xml = toXML(saved);
         assertTrue(xml.contains("parseResult=\"" + UniformSQL.PARSE_SUCCESS + "\""), xml);
         assertFalse(xml.contains("lossy="), xml);

         for(String type : FOLDING) {
            UniformSQL loaded = new UniformSQL();
            loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
            loaded.setDataSource(dataSource(type));
            assertTrue(loaded.isLossy(), type + ": " + text);
            assertEquals(text, loaded.getSQLString(), type);
         }
      }
   }

   /**
    * The names are folded in the root locale, i in turkish isn't the upper case of I.
    */
   @Test
   void turkishLocale() throws Exception {
      Locale old = Locale.getDefault();
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));

      try {
         assertParsed("select \"id\" as ID from t order by id", "snowflake", "exasol");
         assertParsed("select \"userId\" as USERID from emp order by userid", "postgresql");
         assertParsed("select \"ID\", ID as b from t", "snowflake", "exasol");
         assertParsed("select \"title\", title as b from t", "postgresql");
         assertRefused("select \"id\", id as b from t", "snowflake", "exasol");
         assertRefused("select \"Title\", Title as b from t", FOLDING);
         assertRefused("select id, x as \"Id\" from t order by Id", FOLDING);
      }
      finally {
         Locale.setDefault(old);
      }
   }

   private static void assertRefused(String text, String... types) throws Exception {
      for(String type : types) {
         JDBCDataSource ds = dataSource(type);
         RecognitionException ex = assertThrows(RecognitionException.class,
                                                () -> parse(text, ds), type + ": " + text);
         assertTrue(ex.getMessage().contains("written both quoted and unquoted"),
                    type + ": " + ex.getMessage());

         // the sql runs as written
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(ds);
         new SQLProcessor(sql).parse(text);
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), type + ": " + text);
         assertEquals(text, sql.getSQLString(), type);
         assertFalse(XUtil.isParsedSQL(sql), type);
         assertTrue(sql.isLossy(), type + ": " + text);
      }
   }

   private static void assertParsed(String text, String... types) throws Exception {
      for(String type : types) {
         UniformSQL sql = parse(text, dataSource(type));
         assertFalse(sql.isLossy(), type + ": " + text);
      }
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      return sql;
   }

   private static JDBCDataSource dataSource(String type) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77643_" + type);
      ds.setProductVersion("19.0");

      switch(type) {
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
      }
      case "snowflake" -> {
         ds.setDriver("net.snowflake.client.jdbc.SnowflakeDriver");
         ds.setURL("jdbc:snowflake://test.snowflakecomputing.com");
      }
      case "exasol" -> {
         ds.setDriver("com.exasol.jdbc.EXADriver");
         ds.setURL("jdbc:exa:localhost:8563");
      }
      case "h2" -> {
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:test");
      }
      case "derby" -> {
         ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
         ds.setURL("jdbc:derby:memory:test;create=true");
         ds.setProductVersion("10.17");
      }
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:test");
      }
      case "mysql" -> {
         ds.setDriver("com.mysql.cj.jdbc.Driver");
         ds.setURL("jdbc:mysql://localhost:3306/test");
      }
      case "sql server" -> {
         ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
         ds.setURL("jdbc:sqlserver://localhost:1433;databaseName=test");
      }
      default -> throw new IllegalArgumentException(type);
      }

      assertEquals(type, SQLHelper.getSQLHelper(ds).getSQLHelperType(), "helper for " + type);
      return ds;
   }

   private static String toXML(UniformSQL sql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return buffer.toString();
   }
}
