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

import antlr.Token;
import inetsoft.test.*;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParserTokenTypes;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.StringReader;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77818, the sql lexer dropped a latin-1 letter (U+00C0..U+00FF and ª µ º) of an unquoted
 * identifier: IDENT took the ascii letters and the characters from U+0100 up, and in filter mode
 * a character no rule matches is skipped. So über was read as ber, Größe as the column Gr with
 * the alias e, and from Kundenübersicht as the table Kunden with the alias bersicht, which
 * returned the rows of another table. The parse succeeded and wasn't lossy, so the regenerated
 * sql ran. The latin-1 letters are now identifier characters, and the middle dot (l·l) may
 * continue one. The latin-1 symbols (× ÷ and the no-break space) are not.
 * <p>
 * On Oracle a plain select column is upper cased as Oracle folds it, per code point (Bug #77821),
 * Größe is GRÖßE. Derby folds ß to SS on both sides, so its rows can't check that; Oracle 23
 * Free (AL32UTF8) is the reference, measured for the bug: every Oracle output asserted here
 * returned the rows of the sql as written there. There is no Oracle in CI.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, UniformSQLLatin1IdentifierTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLLatin1IdentifierTest {
   // a latin-1 letter at the start, in the middle and at the end of a name
   private static final String[] NAMES = {
      "über", "Größe", "année", "año", "Café", "Nº", "a·b", "xÿ", "µx", "ªx", "Ärger", "øre"
   };
   private static final String[] HELPERS = { null, "h2", "mysql" };

   /**
    * The lexer reads each name as one identifier. Was ber, Gr e, ann e, a o, Caf, N, a b, x, x,
    * x, rger, re.
    */
   @Test
   void latin1LetterIsAnIdentifierCharacter() throws Exception {
      for(String name : NAMES) {
         assertEquals(List.of("IDENT " + name), tokens(name), name);
      }

      assertEquals(List.of("IDENT T", "DOT .", "IDENT über"), tokens("T.über"));
      assertEquals(List.of("IDENT Kundenübersicht"), tokens("Kundenübersicht"));
      assertEquals(List.of("SPIDENT_VAR $(über)"), tokens("$(über)"));
   }

   /**
    * The latin-1 operators and the no-break space still separate two names (they are dropped
    * in filter mode, as before), and the middle dot doesn't start a name.
    */
   @Test
   void latin1SymbolIsNotAnIdentifierCharacter() throws Exception {
      for(String separator : new String[] { "×", "÷", " " }) {
         assertEquals(List.of("IDENT a", "IDENT b"), tokens("a" + separator + "b"), separator);
      }

      assertEquals(List.of("IDENT a"), tokens("·a"));
   }

   /**
    * Every name in every clause regenerates as written on the helpers that don't fold it. Was
    * select ber from T, sum(T.ber), Gr as e etc.
    */
   @Test
   void nameIsRegeneratedAsWritten() throws Exception {
      for(String helper : HELPERS) {
         for(String name : NAMES) {
            String label = helper + " " + name;
            assertRegenerates(helper, "select " + name + " from T", label);
            assertRegenerates(helper, "select T." + name + " from T", label);
            assertRegenerates(helper, "select sum(T." + name + ") from T", label);
            assertRegenerates(helper, "select T.id from T where T." + name + " = 1", label);
            assertRegenerates(helper, "select T." + name + ", count(*) from T group by T." + name, label);
            assertRegenerates(helper, "select T.id from T order by T." + name + " asc", label);
            assertRegenerates(helper, "select " + name + ".id from T " + name, label);
         }
      }
   }

   /**
    * The reported table, was from Kunden bersicht: the table Kunden with the alias bersicht.
    */
   @Test
   void tableNameIsOneTable() throws Exception {
      for(String helper : HELPERS) {
         UniformSQL sql = parse(helper, "select x from Kundenübersicht");
         assertEquals("[Kundenübersicht]", Arrays.toString(XUtil.getTables(sql)), helper);
         assertEquals("select x from Kundenübersicht", regenerate(sql), helper);
      }
   }

   /**
    * A quoted name was read intact before, and is the same.
    */
   @Test
   void quotedNameIsUnchanged() throws Exception {
      for(String helper : new String[] { null, "h2" }) {
         assertRegenerates(helper, "select \"über\" from T", helper);
         assertRegenerates(helper, "select T.\"Größe\" from T", helper);
      }
   }

   /**
    * A variable name may have a latin-1 letter. Was the lexer error expecting ')', found 'ü'.
    */
   @Test
   void variableName() throws Exception {
      assertTrue(XUtil.isSQLExpressionValid("x = $(über)"));
      UniformSQL sql = parse(null, "select x from T where y = $(über)");
      assertTrue(regenerate(sql).contains("$(über)"), regenerate(sql));
   }

   /**
    * Without the metadata of the table, an unqualified order by name resolves to the select
    * column (#77639), which needs the name read as IDENT reads it. Without the matching change
    * of the UniformSQL pattern, the order by was dropped. (A name with a character that isn't a
    * letter, a·b as a＿b, isn't a qualified name to XUtil.isQualifiedName, and isn't resolved.)
    */
   @Test
   void unqualifiedOrderByWithoutMetadataIsKept() throws Exception {
      for(String name : new String[] { "über", "Größe", "Café", "Nº", "xÿ" }) {
         String generated = fixed("h2", "select t.id, t." + name + " from t order by " + name);
         assertTrue(generated.endsWith("order by t." + name + " asc"), generated);
      }
   }

   /**
    * On Oracle a plain select column is upper cased as Oracle folds it (per code point, #77821),
    * and an aggregate of one column is emitted as written, unquoted, which Oracle folds the same
    * way. With the metadata the column is the catalog name.
    */
   @Test
   void oracleFoldsAsOracle() throws Exception {
      String[][] cases = {
         // written, as oracle stores it
         { "über", "ÜBER" }, { "Größe", "GRÖßE" }, { "Straße", "STRAßE" }, { "µx", "ΜX" }, { "xÿ", "XŸ" },
         { "Café", "CAFÉ" }, { "Nº", "Nº" }, { "année", "ANNÉE" }
      };

      for(String key : new String[] { "oracle", "oracle-ansi" }) {
         for(String[] c : cases) {
            String label = key + " " + c[0];
            assertEquals("select T." + c[1] + " from T", generate(key, "select t." + c[0] + " from T"), label);
            assertEquals("select sum(T." + c[0] + ") from T", generate(key, "select sum(T." + c[0] + ") from T"),
                         label);
            assertEquals("select t." + c[1] + " from T t",
                         generate(key, "select t." + c[0] + " from T t", "ID", c[1]), label);
            assertEquals("select sum(t." + c[1] + ") from T t",
                         generate(key, "select sum(t." + c[0] + ") from T t", "ID", c[1]), label);
         }

         // not STRASSE (ORA-00904), the group by is kept as written and oracle folds it the same
         assertEquals("select STRAßE, count(*) from T group by Straße",
                      generate(key, "select Straße, count(*) from T group by Straße"), key);
         assertEquals("select T.ID, sum(T.über) from T group by T.id order by sum(T.über) asc",
                      generate(key, "select T.id, sum(T.über) from T group by T.id order by sum(T.über)"), key);
         assertEquals("select count(distinct T.Größe) from T",
                      generate(key, "select count(distinct T.Größe) from T"), key);
         // a quoted name isn't folded
         assertEquals("select T.\"über\" from T", generate(key, "select T.\"über\" from T"), key);
      }
   }

   /**
    * Surefire pins en_US, the output is the same in a turkish locale.
    */
   @Test
   void turkishLocale() throws Exception {
      List<String> queries = new ArrayList<>();

      for(String name : NAMES) {
         queries.add("select T." + name + ", sum(T.id" + ") from T where T." + name + " = 1 group by T." + name);
         queries.add("select sum(t." + name + ") from T t");
      }

      List<String> expected = generateAll(queries);
      Locale locale = Locale.getDefault();

      try {
         Locale.setDefault(Locale.forLanguageTag("tr-TR"));
         assertEquals(expected, generateAll(queries));
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   private static List<String> generateAll(List<String> queries) throws Exception {
      List<String> list = new ArrayList<>();

      for(String key : new String[] { null, "h2", "oracle", "oracle-ansi" }) {
         for(String query : queries) {
            list.add(generate(key, query));
         }
      }

      return list;
   }

   /**
    * The regenerated sql returns the rows of the sql as written on Derby. Was the rows of Kunden
    * for from Kundenübersicht, and "Column 'BER' is not in any table" for über.
    */
   @Test
   void rowsMatchOnDerby() throws Exception {
      String[] queries = {
         "select x from Kundenübersicht",
         "select k.x from Kundenübersicht k",
         "select über, Café, Nº from T",
         "select sum(T.über), max(T.Café) from T",
         "select T.über, count(*) from T group by T.über order by T.über",
         "select t.Café from T t where t.über = 2",
         "select x from T where Nº = 3",
         "select ä.über from T ä order by ä.über desc",
      };

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77818;create=true");
          Statement stmt = conn.createStatement())
      {
         stmt.execute("create table Kunden (x int)");
         stmt.execute("insert into Kunden values (1)");
         stmt.execute("create table Kundenübersicht (x int)");
         stmt.execute("insert into Kundenübersicht values (2)");
         stmt.execute("create table T (x int, über int, Café int, Nº int)");
         stmt.execute("insert into T values (1, 1, 10, 3), (2, 2, 20, 4), (3, 2, 30, 3)");

         for(String key : new String[] { null, "h2", "oracle", "oracle-ansi" }) {
            for(String query : queries) {
               List<String> expected = rows(stmt, query);
               String generated = generate(key, query);
               assertEquals(expected, rows(stmt, generated), key + ": " + query + " -> " + generated);
            }
         }
      }
      finally {
         try {
            DriverManager.getConnection("jdbc:derby:memory:bug77818;drop=true");
         }
         catch(SQLException ignore) {
            // a successful drop is reported as an exception
         }
      }
   }

   // the rows, with the values of each row sorted (oracle reorders the select list)
   private static List<String> rows(Statement stmt, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(ResultSet rs = executeQuery(stmt, query)) {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            List<String> row = new ArrayList<>();

            for(int i = 1; i <= count; i++) {
               row.add(String.valueOf(rs.getObject(i)));
            }

            Collections.sort(row);
            rows.add(row.toString());
         }
      }

      return rows;
   }

   private static ResultSet executeQuery(Statement stmt, String query) throws SQLException {
      try {
         return stmt.executeQuery(query);
      }
      catch(SQLException ex) {
         throw new SQLException(query, ex);
      }
   }

   // the tokens of the sql lexer, as type and text
   private static List<String> tokens(String text) throws Exception {
      SQLLexer lexer = new SQLLexer(new StringReader(text));
      List<String> tokens = new ArrayList<>();

      for(Token token = lexer.nextToken(); token.getType() != Token.EOF_TYPE; token = lexer.nextToken()) {
         String type = switch(token.getType()) {
            case SQLParserTokenTypes.IDENT -> "IDENT";
            case SQLParserTokenTypes.SPIDENT_VAR -> "SPIDENT_VAR";
            case SQLParserTokenTypes.DOT -> "DOT";
            default -> String.valueOf(token.getType());
         };

         tokens.add(type + " " + token.getText());
      }

      return tokens;
   }

   // parse, regenerate, and check the regenerated sql re-parses to itself
   private static void assertRegenerates(String key, String query, String label) throws Exception {
      String generated = generate(key, query);
      assertEquals(query, generated, label);
      assertEquals(generated, generate(key, generated), label);
   }

   // the sql regenerated after the metadata step with the columns of every table, or after
   // the parse if there are no columns
   private static String generate(String key, String query, String... columns) throws Exception {
      UniformSQL sql = parse(key, query);

      if(columns.length > 0) {
         JDBCUtil.fixUniformSQLInfo(sql, repository(columns), null, sql.getDataSource());
      }

      return regenerate(sql);
   }

   // the sql regenerated after the metadata step with no table columns (the column fetch
   // failed), as the query editor and a worksheet sql table run it
   private static String fixed(String key, String query) throws Exception {
      UniformSQL sql = parse(key, query);
      JDBCUtil.fixUniformSQLInfo(sql, repository(), null, sql.getDataSource());
      return regenerate(sql);
   }

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String key, String text) throws Exception {
      UniformSQL sql = new UniformSQL();

      if(key != null) {
         sql.setDataSource(source(key));
      }

      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      sql.setSQLString(text, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      return sql;
   }

   // the table metadata is cached by data source name, also in the sree home of earlier test
   // runs, use a new name each time
   private static JDBCDataSource source(String key) {
      String[] spec = switch(key) {
         case "h2" -> new String[] { "org.h2.Driver", "jdbc:h2:mem:x", "h2" };
         case "mysql" -> new String[] { "com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost/db", "mysql" };
         case "oracle", "oracle-ansi" -> new String[] { "oracle.jdbc.OracleDriver",
                                                        "jdbc:oracle:thin:@localhost:1521:x", "oracle" };
         default -> throw new IllegalArgumentException(key);
      };

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77818" + key + RUN + "_" + (++sources));
      ds.setDriver(spec[0]);
      ds.setURL(spec[1]);
      ds.setRuntimeProductName(spec[2]);
      // otherwise the mysql and oracle helpers ask the repository for it
      ds.setProductVersion("10.0");
      ds.setAnsiJoin(key.endsWith("-ansi"));
      return ds;
   }

   private static int sources;
   private static final String RUN = Long.toString(System.nanoTime(), 36);

   // the column metadata comes from the repository
   private static XRepository repository(String... columns) throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
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

   // a JDBCDataSource creates its credential when it is constructed, and
   // JDBCUtil.fixUniformSQLInfo looks up the driver type
   @Configuration
   static class Beans {
      @Bean
      Config config() {
         Config config = mock(Config.class);
         when(config.getJDBCType(any())).thenAnswer(inv -> {
            String driver = String.valueOf((Object) inv.getArgument(0));
            return driver.contains("oracle") ? "oracle" : driver.contains("mysql") ? "mysql" : "H2";
         });
         return config;
      }

      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean())).thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }
   }
}
