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
import inetsoft.uql.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.jdbc.util.VarSQL;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Plugins;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77667. The parser and the SQL helpers folded identifiers and dialect names with the
 * default locale. Under a Turkish or Azerbaijani default locale, i becomes İ (U+0130) and I
 * becomes ı (U+0131), so:
 * <ul>
 * <li>Oracle select columns were regenerated as İTEMS.İD,</li>
 * <li>an Oracle alias that differs only in case was quoted ("id"),</li>
 * <li>a postgresql/snowflake ORDER BY on an unquoted name was dropped,</li>
 * <li>an upper case IN $(var) list was cut to its first value,</li>
 * <li>informix, ingres and clickhouse lost their helper,</li>
 * <li>identifiers named like keywords with an I were not quoted,</li>
 * <li>a USING join named in two cases failed to parse.</li>
 * </ul>
 * Every case runs under en-US too, which must give the same result (the control).
 *
 * Core's surefire argLine pins en-US, so each test sets the default locale itself.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  UniformSQLDefaultLocaleFoldTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLDefaultLocaleFoldTest {
   @Configuration
   @Import(CredentialService.class)
   static class JdbcConfig {
      @Bean
      public inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      // the oracle helper asks the repository for the product version
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @BeforeEach
   void saveLocale() {
      defaultLocale = Locale.getDefault();
   }

   @AfterEach
   void restoreLocale() {
      Locale.setDefault(defaultLocale);
   }

   // the reported shape
   @ParameterizedTest
   @ValueSource(strings = { "en-US", "tr-TR", "az-AZ" })
   void oracleSelectColumnsFoldToAsciiUpperCase(String locale) throws Exception {
      Locale.setDefault(Locale.forLanguageTag(locale));

      assertRegenerates("oracle", "select items.id from items", "select ITEMS.ID from items");
      assertRegenerates("oracle", "select i.price, i.name from sa.items i where i.id = 1",
                        "select I.NAME, I.PRICE from sa.items i where i.id = 1");
      // T.İD didn't match ID, so the alias was quoted
      assertRegenerates("oracle", "select t.id as ID from t", "select T.ID as ID from t");
   }

   // a query saved under the locale stores the ascii name
   @ParameterizedTest
   @ValueSource(strings = { "en-US", "tr-TR", "az-AZ" })
   void oracleSavedSelectionHasAsciiNames(String locale) throws Exception {
      Locale.setDefault(Locale.forLanguageTag(locale));
      UniformSQL usql = parsed("oracle", "select items.id from items");
      String xml = toXML(usql);

      assertFalse(xml.contains("İ"), xml);

      Locale.setDefault(Locale.US);
      UniformSQL loaded = new UniformSQL();
      loaded.setDataSource(dataSource("oracle"));
      loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      assertEquals("select ITEMS.ID from items", regenerate(loaded));
   }

   // SQLHelper compared the lower-cased column and alias, ID became ıd and didn't match id
   @ParameterizedTest
   @ValueSource(strings = { "en-US", "tr-TR", "az-AZ" })
   void oracleAliasDifferingInCaseIsNotQuoted(String locale) throws Exception {
      Locale.setDefault(Locale.forLanguageTag(locale));

      assertRegenerates("oracle", "select t.ID as id from t", "select T.ID as id from t");
   }

   // UniformSQL.foldName folded the ORDER BY name with the locale, it then matched no field
   // and was dropped
   @ParameterizedTest
   @ValueSource(strings = { "en-US", "tr-TR", "az-AZ" })
   void unquotedOrderByIsKept(String locale) throws Exception {
      Locale.setDefault(Locale.forLanguageTag(locale));

      assertEquals("select \"t\".\"id\" as \"x\" from \"t\" order by \"t\".\"id\" asc",
                   regenerate(fixed("postgresql", "select t.id as x from t order by ID", "id")));
      assertEquals("select \"id\" as \"ida\" from \"t\" order by \"id\" asc",
                   regenerate(fixed("postgresql", "select id as \"ida\" from t order by IDA",
                                    "id")));
      assertEquals("select \"id\" as IDA from \"t\" order by \"id\" asc",
                   regenerate(fixed("snowflake", "select id as \"IDA\" from t order by ida",
                                    "id")));
   }

   // VarSQL looked for "in" in the lower-cased text, IN became ın
   @ParameterizedTest
   @ValueSource(strings = { "en-US", "tr-TR", "az-AZ" })
   void upperCaseInListKeepsAllValues(String locale) throws Exception {
      Locale.setDefault(Locale.forLanguageTag(locale));
      String sql = replaceVariables("select X from T77667 where X IN $(v)", "a", "b");

      assertEquals("select X from T77667 where X IN ( 'a' , 'b' )", sql);

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table T77667");
         }
         catch(SQLException ignore) {
            // first run
         }

         stmt.executeUpdate("create table T77667 (X VARCHAR(10))");
         stmt.executeUpdate("insert into T77667 values ('a'), ('b'), ('c')");
         assertEquals(List.of("a", "b"), column(stmt, sql + " order by X"));
         assertEquals(column(stmt, "select X from T77667 where X IN ('a', 'b') order by X"),
                      column(stmt, sql + " order by X"));
      }
   }

   // the product name was lower-cased with the locale, informix became ınformix
   @ParameterizedTest
   @ValueSource(strings = { "en-US", "tr-TR", "az-AZ" })
   void dialectHelperIsSelected(String locale) throws Exception {
      Locale.setDefault(Locale.forLanguageTag(locale));

      // the driver is in config.xml
      assertHelper("informix", InformixSQLHelper.class);
      assertHelper("ingres", IngresHelper.class);
      // the driver isn't, the type comes from the database type string
      assertHelper("clickhouse", ClickhouseHelper.class);

      // an odbc data source names its product
      JDBCDataSource odbc = dataSource("odbc");
      odbc.setProductName("Informix Dynamic Server");
      assertEquals("informix", SQLHelper.getProductName(odbc));
      assertInstanceOf(InformixSQLHelper.class, SQLHelper.getSQLHelper(odbc));
   }

   // XUtil.isSpecial lower-cased the name before looking up the keyword, INDEX became ındex
   @ParameterizedTest
   @ValueSource(strings = { "en-US", "tr-TR", "az-AZ" })
   void keywordIdentifierIsQuoted(String locale) throws Exception {
      Locale.setDefault(Locale.forLanguageTag(locale));
      SQLHelper h2 = SQLHelper.getSQLHelper(dataSource("h2"));
      SQLHelper oracle = SQLHelper.getSQLHelper(dataSource("oracle"));

      assertEquals("\"INDEX\"", XUtil.quoteAlias("INDEX", h2));
      assertEquals("\"DISTINCT\"", XUtil.quoteAlias("DISTINCT", h2));
      assertTrue(XUtil.isSpecial("LIMIT", oracle));
      assertTrue(XUtil.isSpecial("INDEX", oracle));

      // İ lower-cases to two chars, the scan must still reach the last char
      assertTrue(XUtil.isSpecial("MİKTAR$", h2));
      assertEquals("\"MİKTAR$\"", XUtil.quoteAlias("MİKTAR$", h2));
      assertTrue(XUtil.isSpecial("SİPARİŞ#", oracle));
   }

   // the grammar keyed USING columns with the locale, ID and id became different keys
   @ParameterizedTest
   @ValueSource(strings = { "en-US", "tr-TR", "az-AZ" })
   void usingJoinNamedInTwoCasesParses(String locale) throws Exception {
      Locale.setDefault(Locale.forLanguageTag(locale));
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(dataSource("h2"));
      new SQLProcessor(usql).parse("select * from a join b using (ID) join c using (id)");

      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult());
      assertTrue(XUtil.isParsedSQL(usql));
      assertEquals("[a, b, c]", Arrays.toString(XUtil.getTables(usql)));
      assertEquals("select * from a, b, c where a.ID = b.ID and b.id = c.id", regenerate(usql));
   }

   // parse, regenerate, and check the regenerated sql re-parses to itself
   private static void assertRegenerates(String type, String sql, String expected)
      throws Exception
   {
      String generated = regenerate(parsed(type, sql));
      assertEquals(expected, generated, sql);
      assertEquals(generated, regenerate(parsed(type, generated)), generated);
   }

   private static void assertHelper(String type, Class<? extends SQLHelper> expected) {
      JDBCDataSource ds = dataSource(type);
      assertInstanceOf(expected, SQLHelper.getSQLHelper(ds), type);
      assertEquals(type, SQLHelper.getProductName(ds), type);
   }

   private static UniformSQL parsed(String type, String sql) throws Exception {
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(dataSource(type));
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
      assertFalse(usql.isLossy(), sql);
      return usql;
   }

   // as the query editor and worksheet paths prepare a query, with the table metadata
   private static UniformSQL fixed(String type, String sql, String... columns)
      throws Exception
   {
      UniformSQL usql = parsed(type, sql);
      JDBCUtil.fixUniformSQLInfo(usql, repository(columns), null, usql.getDataSource());
      return usql;
   }

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

   private static String replaceVariables(String sql, Object... values) {
      VariableTable vars = new VariableTable();
      vars.put("v", values);
      VarSQL vsql = new VarSQL();
      vsql.setSQLType(VarSQL.SQLType.STRING);
      return vsql.replaceVariables(sql, vars).replaceAll("\\s+", " ").trim();
   }

   private static List<String> column(Statement stmt, String sql) throws SQLException {
      List<String> values = new ArrayList<>();

      try(ResultSet rs = stmt.executeQuery(sql)) {
         while(rs.next()) {
            values.add(rs.getString(1));
         }
      }

      return values;
   }

   private static EmbeddedDataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName("memory:bug77667");
      ds.setCreateDatabase("create");
      return ds;
   }

   private static String regenerate(UniformSQL usql) {
      usql.clearSQLString();
      return usql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static String toXML(UniformSQL usql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         usql.writeXML(writer);
      }

      return buffer.toString();
   }

   // a new data source each time: the runtime product name and the table metadata are cached
   // on it, by object and by name
   private static JDBCDataSource dataSource(String type) {
      String[] spec = switch(type) {
         case "h2" -> new String[] { "org.h2.Driver", "jdbc:h2:mem:x" };
         case "oracle" -> new String[] { "oracle.jdbc.OracleDriver",
                                         "jdbc:oracle:thin:@localhost:1521:x" };
         case "postgresql" -> new String[] { "org.postgresql.Driver",
                                             "jdbc:postgresql://localhost/db" };
         case "snowflake" -> new String[] { "net.snowflake.client.jdbc.SnowflakeDriver",
                                            "jdbc:snowflake://x.snowflakecomputing.com" };
         case "informix" -> new String[] { "com.informix.jdbc.IfxDriver",
                                           "jdbc:informix-sqli://localhost:9088/db:INFORMIXSERVER=ids" };
         case "ingres" -> new String[] { "com.ingres.jdbc.IngresDriver",
                                         "jdbc:ingres://localhost:II7/db" };
         case "clickhouse" -> new String[] { "com.clickhouse.jdbc.ClickHouseDriver",
                                             "jdbc:clickhouse://localhost:8123/db" };
         case "odbc" -> new String[] { "sun.jdbc.odbc.JdbcOdbcDriver", "jdbc:odbc:x" };
         default -> throw new IllegalArgumentException(type);
      };

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77667" + type + "_" + (++sources));
      ds.setDriver(spec[0]);
      ds.setURL(spec[1]);
      // otherwise the oracle helper asks the repository for it
      ds.setProductVersion("19.0");
      ds.setRequireLogin(false);
      ds.setAnsiJoin("snowflake".equals(type));
      return ds;
   }

   private static int sources;
   private Locale defaultLocale;
}
