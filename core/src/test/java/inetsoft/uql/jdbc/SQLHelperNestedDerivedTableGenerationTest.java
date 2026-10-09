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
import inetsoft.uql.XRepository;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.IntFunction;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78119. SQLHelper generated the SQL of every FROM derived table four times per
 * generation of its parent (generateSentence, fixTableAliases, the table clause name and the
 * written subquery text), plus once per column reference resolved past it next to a joined
 * table, and once more per join side with ANSI joins. Nothing cached the text, so one
 * generation took about 4^depth (up to 10^depth for ANSI joins) and a query with 12 nested
 * derived tables held a request thread for minutes. Each derived table is now generated once
 * per generation of its parent.
 * <p>
 * The generated SQL must not change: {@link #sameSqlAsBefore()} compares it, byte for byte,
 * with the SQL the old generator wrote (captured before the change in
 * SQLHelperNestedDerivedTableGenerationTest-main.txt), for each shape, helper, ANSI option and
 * row limit, on the first and on a second generation of the same parse, and after an XML
 * save and load. Only the timestamp in the alias of a row limit subquery is masked.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  SQLHelperNestedDerivedTableGenerationTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperNestedDerivedTableGenerationTest {
   private static final String EXPECTED = "SQLHelperNestedDerivedTableGenerationTest-main.txt";
   private static final Duration BUDGET = Duration.ofSeconds(5);

   @Configuration
   static class JdbcConfig {
      // JDBCDataSource's constructor needs CredentialService, whose constructor is
      // package-private.
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      // DerbyHelper asks the repository for the product version
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   // product name -> the helper class it must load
   private static final String[][] HELPERS = {
      { "unknowndb", "SQLHelper" }, { "postgresql", "PostgreSQLHelper" },
      { "mysql", "MySQLHelper" }, { "oracle", "OracleSQLHelper" },
      { "sql server", "SQLServerHelper" }, { "clickhouse", "ClickhouseHelper" },
      { "databricks", "DatabricksHelper" }, { "h2", "H2Helper" }, { "db2", "DB2SQLHelper" },
      { "snowflake", "SnowflakeHelper" },
   };

   // the nesting shapes of the report and of the regression note, at depth d: each level
   // holds the next one as a derived table
   private static final Map<String, IntFunction<String>> NESTED = new LinkedHashMap<>();

   static {
      NESTED.put("plain", d -> nest(d, "select * from \"ORDERS\"",
         (x, i) -> "select * from (" + x + ") x" + i));
      NESTED.put("join", d -> nest(d, "select o.ID, o.AMT from \"ORDERS\" o",
         (x, i) -> "select x" + i + ".ID, x" + i + ".AMT from (" + x + ") x" + i +
            " join \"ORDERS\" o" + i + " on x" + i + ".ID = o" + i + ".ID"));
      NESTED.put("left", d -> nest(d, "select o.ID, o.AMT from \"ORDERS\" o",
         (x, i) -> "select x" + i + ".ID, x" + i + ".AMT from \"ORDERS\" o" + i +
            " left join (" + x + ") x" + i + " on o" + i + ".ID = x" + i + ".ID"));
      NESTED.put("comma", d -> nest(d, "select o.ID, o.AMT from \"ORDERS\" o",
         (x, i) -> "select x" + i + ".ID, x" + i + ".AMT from (" + x + ") x" + i +
            ", (select * from \"ORDERS\") y" + i + " where x" + i + ".ID = y" + i + ".ID"));
   }

   // other derived table shapes for the comparison with the old generator
   private static final String[][] OTHER = {
      // unaliased derived tables: the alias is the sql text
      { "unaliased", "select * from (select * from (select o.ID from \"ORDERS\" o))" },
      { "mixedCase", "select x1.\"Amt\", x1.id from (select o.\"Amt\", o.id from \"Orders\" o " +
         "where o.id > 1) x1 order by x1.id" },
      { "aggregate", "select x1.total, x1.ID from (select o.ID, sum(o.AMT) total from " +
         "\"ORDERS\" o group by o.ID) x1 where x1.total > 10 order by x1.total" },
      { "columnAlias", "select x1.a, c.NAME from (select o.ID a, o.CID cid from \"ORDERS\" o) " +
         "x1 inner join CUSTOMERS c on x1.cid = c.ID where x1.a > 1 order by x1.a" },
      { "twoDerived", "select x1.ID, x2.NAME from (select o.ID, o.CID from \"ORDERS\" o) x1 " +
         "left outer join (select c.ID, c.NAME from CUSTOMERS c) x2 on x1.CID = x2.ID" },
      { "rightJoin", "select x1.ID, c.NAME from (select o.ID, o.CID from \"ORDERS\" o) x1 " +
         "right outer join CUSTOMERS c on x1.CID = c.ID" },
      { "schema", "select x1.ID from (select o.ID from SALES.\"ORDERS\" o) x1" },
   };

   // map key access, parsed only with a ClickHouse or Databricks source
   private static final String[][] MAP_KEY = {
      { "mapKey", "select x1.ID from (select t.ID, t.m['k'] from t) x1" },
      { "mapKeyAlias", "select x1.v from (select t.m['k'] as v from t) x1" },
   };

   // limits: none, an input limit of a query with vpm conditions, an output limit
   private static final String[] LIMITS = { "none", "vpm", "out" };

   /**
    * The generated sql is the same as the old generator's, byte for byte, on the first
    * generation of a parse, on a second one (the derived tables then have the data source
    * and the parent of the first), and on the parse saved to XML and loaded.
    */
   @Test
   void sameSqlAsBefore() throws Exception {
      Map<String, String> actual = generateAll();


      Map<String, String> expected = readExpected();

      assertEquals(expected.keySet(), actual.keySet());
      List<String> diffs = new ArrayList<>();

      for(Map.Entry<String, String> e : expected.entrySet()) {
         if(!e.getValue().equals(actual.get(e.getKey()))) {
            diffs.add(e.getKey() + "\n  main: " + e.getValue() + "\n  now:  " +
                         escape(actual.get(e.getKey())));
         }
      }

      assertTrue(diffs.isEmpty(), diffs.size() + " differences:\n" + String.join("\n", diffs));
   }

   /**
    * The generated sql of a nested shape parses back to the same generated sql.
    */
   @ParameterizedTest(name = "{0} {1} ansi={2}")
   @CsvSource({
      "plain,postgresql,false", "plain,mysql,true", "join,postgresql,true",
      "join,oracle,false", "left,postgresql,false", "left,unknowndb,true",
      "comma,sql server,false", "comma,h2,true",
   })
   void roundTrip(String shape, String product, boolean ansi) throws Exception {
      JDBCDataSource ds = dataSource(product, ansi);
      String generated = generate(parse(NESTED.get(shape).apply(4), ds));

      assertEquals(generated, generate(parse(generated, ds)), shape + " " + product);
   }

   /**
    * Each derived table is generated once per generation of its parent, so a generation is
    * linear in the nesting depth. The old generator needed hours at these depths.
    */
   @ParameterizedTest(name = "{0} {1} ansi={2}")
   @CsvSource({
      "plain,postgresql,false", "plain,postgresql,true", "plain,unknowndb,false",
      "plain,unknowndb,true", "join,postgresql,false", "join,postgresql,true",
      "join,unknowndb,false", "join,unknowndb,true", "left,postgresql,false",
      "left,postgresql,true", "left,unknowndb,false", "left,unknowndb,true",
      "comma,postgresql,false", "comma,postgresql,true", "comma,unknowndb,false",
      "comma,unknowndb,true",
   })
   void generationIsLinearInDepth(String shape, String product, boolean ansi) throws Exception {
      JDBCDataSource ds = dataSource(product, ansi);
      // warm up
      generate(parse(NESTED.get(shape).apply(3), ds));

      for(int depth : new int[] { 12, 20 }) {
         UniformSQL sql = parse(NESTED.get(shape).apply(depth), ds);
         String generated = assertTimeoutPreemptively(BUDGET, () -> {
            // the first generation, a second one, and one of a clone as preview does
            String text = generate(sql);
            assertEquals(text, generate(sql));
            assertEquals(text, generate(sql.clone()));
            return text;
         }, () -> shape + " at depth " + depth + " took over " + BUDGET.toMillis() + " ms");

         // every level is written
         for(int i = 1; i <= depth; i++) {
            assertTrue(generated.matches("(?s).*\\bx" + i + "\\b.*"),
                       shape + " " + depth + ": x" + i + " in " + generated);
         }

         assertEquals(depth + 1 + (shape.equals("comma") ? depth : 0),
                      generated.split("(?i)\\bselect\\b", -1).length - 1, generated);
      }
   }

   private static Map<String, String> generateAll() throws Exception {
      Map<String, String> result = new LinkedHashMap<>();

      for(String[] helper : HELPERS) {
         for(boolean ansi : new boolean[] { false, true }) {
            JDBCDataSource ds = dataSource(helper[0], ansi);
            assertEquals(helper[1], SQLHelper.getSQLHelper(ds).getClass().getSimpleName());
            List<String[]> shapes = new ArrayList<>();

            for(Map.Entry<String, IntFunction<String>> e : NESTED.entrySet()) {
               shapes.add(new String[] { e.getKey(), e.getValue().apply(3) });
            }

            shapes.addAll(Arrays.asList(OTHER));

            if(helper[0].equals("clickhouse") || helper[0].equals("databricks")) {
               shapes.addAll(Arrays.asList(MAP_KEY));
            }

            for(String[] shape : shapes) {
               for(String limit : LIMITS) {
                  String key = helper[0] + "|ansi=" + ansi + "|" + shape[0] + "|" + limit;
                  String[] texts = generate(shape[1], ds, limit);
                  result.put(key + "|1", escape(texts[0]));
                  result.put(key + "|2", escape(texts[1]));
                  result.put(key + "|xml", escape(texts[2]));
               }
            }
         }
      }

      return result;
   }

   // the first and a second generation of a parse, and the generation of the parse saved
   // to XML and loaded again, or the parse failure
   private static String[] generate(String text, JDBCDataSource ds, String limit) {
      try {
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(ds);
         sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);

         if(sql.getParseResult() != UniformSQL.PARSE_SUCCESS || sql.isLossy()) {
            String failed = "NOT PARSED " + sql.getParseResult() + " lossy=" + sql.isLossy();
            return new String[] { failed, failed, failed };
         }

         setLimit(sql, limit);
         sql.clearSQLString();
         String first = sql.getSQLString();
         sql.clearSQLString();
         String second = sql.getSQLString();

         StringWriter buffer = new StringWriter();

         try(PrintWriter writer = new PrintWriter(buffer)) {
            sql.writeXML(writer);
         }

         UniformSQL loaded = new UniformSQL();
         loaded.parseXML(Tool.parseXML(new StringReader(buffer.toString())).getDocumentElement());
         loaded.setDataSource(ds);
         setLimit(loaded, limit);
         loaded.clearSQLString();
         return new String[] { first, second, loaded.getSQLString() };
      }
      catch(Exception ex) {
         String failed = "ERROR " + ex.getClass().getName();
         return new String[] { failed, failed, failed };
      }
   }

   private static void setLimit(UniformSQL sql, String limit) {
      if(limit.equals("vpm")) {
         sql.setVPMCondition(true);
         sql.setHint(UniformSQL.HINT_INPUT_MAXROWS, "5");
      }
      else if(limit.equals("out")) {
         sql.setHint(UniformSQL.HINT_OUTPUT_MAXROWS, "7");
      }
   }

   private static Map<String, String> readExpected() throws IOException {
      Map<String, String> expected = new LinkedHashMap<>();

      try(InputStream in = SQLHelperNestedDerivedTableGenerationTest.class
         .getResourceAsStream(EXPECTED))
      {
         assertNotNull(in, EXPECTED);
         BufferedReader reader =
            new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
         String line;

         while((line = reader.readLine()) != null) {
            if(!line.isEmpty()) {
               int tab = line.indexOf('\t');
               expected.put(line.substring(0, tab), line.substring(tab + 1));
            }
         }
      }

      return expected;
   }

   // one line per sql in the expected file. The alias of the output row limit (and of a
   // limited table subquery) holds a timestamp, which is replaced by N.
   private static String escape(String text) {
      return TIMESTAMP.matcher(text).replaceAll("$1N").replace("\\", "\\\\")
         .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
   }

   private static final Pattern TIMESTAMP = Pattern.compile("\\b(inner|t)[0-9]{8,}");

   private static String nest(int depth, String leaf, LevelWrapper wrapper) {
      String sql = leaf;

      for(int i = depth; i >= 1; i--) {
         sql = wrapper.wrap(sql, i);
      }

      return sql;
   }

   private interface LevelWrapper {
      String wrap(String inner, int level);
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.clearSQLString();
      return sql;
   }

   private static String generate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static JDBCDataSource dataSource(String product, boolean ansi) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug78119-" + product + "-" + ansi);
      String[] driver = switch(product) {
         case "postgresql" -> new String[] { "org.postgresql.Driver",
                                             "jdbc:postgresql://localhost:5432/test" };
         case "mysql" -> new String[] { "com.mysql.cj.jdbc.Driver",
                                        "jdbc:mysql://localhost:3306/test" };
         case "oracle" -> new String[] { "oracle.jdbc.OracleDriver",
                                         "jdbc:oracle:thin:@localhost:1521:test" };
         case "sql server" -> new String[] { "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                                             "jdbc:sqlserver://localhost:1433;databaseName=test" };
         case "clickhouse" -> new String[] { "com.clickhouse.jdbc.ClickHouseDriver",
                                             "jdbc:clickhouse://localhost:8123/test" };
         case "databricks" -> new String[] { "com.databricks.client.jdbc.Driver",
                                             "jdbc:databricks://localhost:443/default" };
         case "h2" -> new String[] { "org.h2.Driver", "jdbc:h2:mem:test" };
         case "db2" -> new String[] { "com.ibm.db2.jcc.DB2Driver",
                                      "jdbc:db2://localhost:50000/test" };
         case "snowflake" -> new String[] { "net.snowflake.client.jdbc.SnowflakeDriver",
                                            "jdbc:snowflake://test.snowflakecomputing.com" };
         default -> new String[] { "jdbc.Driver" + product, "jdbc:x:" + product };
      };
      ds.setDriver(driver[0]);
      ds.setURL(driver[1]);
      ds.setRuntimeProductName(product);
      // otherwise the mysql and oracle helpers ask the repository for it
      ds.setProductVersion("10.0");
      ds.setAnsiJoin(ansi);
      return ds;
   }
}
