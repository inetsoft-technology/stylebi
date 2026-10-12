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
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77510, the negation of a join under an OR must survive the production entry point
 * JDBCUtil.fixUniformSQLInfo (including its recursion into derived tables), the ANSI join
 * option, and a writeXML/parseXML round trip of the fixed tree.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  JDBCUtilFixUniformSQLInfoNotTest.TestBeans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JDBCUtilFixUniformSQLInfoNotTest {
   private static final String URL = "jdbc:derby:memory:bug77510b";
   private static final String SELECT = "select a.id, a.k, b.id, b.k from ";

   static Stream<Arguments> shapes() {
      List<String> texts = List.of(
         // OR deep inside nested AND groups
         SELECT + "a, b where a.k = 2 and (b.k > 0 and (a.k = 1 or not (a.id = b.k)))",
         // AND under OR under AND under OR
         SELECT + "a, b where a.k = 1 or (b.k = 2 and (a.k = 3 or not (a.id = b.k)))",
         // several negated joins
         SELECT + "a, b where not (a.id = b.k) or not (a.k = b.id)",
         SELECT + "a, b where a.id = b.k or not (a.k = b.id)",
         "select a.id, b.id, c.id from a, b, c where not (a.id = b.k) or not (b.id = c.k) " +
            "or not (a.k = c.id)",
         // a real join next to the negated one, the ANSI option moves only the real one to ON
         SELECT + "a, b where a.id = b.id and (a.k = 1 or not (a.k = b.k))",
         // left join variants
         SELECT + "a left join b on a.id = b.id where a.k = 1 or (b.k = 2 and not (a.k = b.id))",
         SELECT + "a left join b on a.id = b.id where not (a.k = b.id) or not (a.id = b.k)",
         // derived table, reached through the fixUniformSQLInfo recursion
         "select t.id, t.k, t.bk from (select a.id, a.k, b.k as bk from a, b " +
            "where a.k = 1 or not (a.id = b.k)) t");

      return texts.stream().flatMap(t -> Stream.of(Arguments.of(t, false), Arguments.of(t, true)));
   }

   @ParameterizedTest
   @MethodSource("shapes")
   void negationSurvivesFixUniformSQLInfo(String text, boolean ansi) throws Exception {
      JDBCDataSource xds = dataSource(ansi);
      UniformSQL sql = parse(text, xds);
      JDBCUtil.fixUniformSQLInfo(sql, XRepository.getRepository(), "u", xds);

      String generated = regenerate((UniformSQL) sql.clone());
      assertTrue(generated.contains("not ("), generated);
      assertSameRows(text, generated);

      // the fixed tree is what gets saved, parseXML does not re-parse it
      UniformSQL loaded = load(save(sql));
      loaded.setDataSource(xds);
      String reloaded = regenerate(loaded);
      assertEquals(generated, reloaded);
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection(URL + ";drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   private static JDBCDataSource dataSource(boolean ansi) {
      JDBCDataSource xds = new JDBCDataSource();
      xds.setName("bug77510");
      xds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      xds.setURL(URL);
      xds.setAnsiJoin(ansi);
      return xds;
   }

   private static UniformSQL parse(String text, JDBCDataSource xds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(xds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }

   // the tree is only exposed once the stored sql string is cleared
   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static Element save(UniformSQL sql) throws Exception {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return Tool.parseXML(new StringReader(buffer.toString())).getDocumentElement();
   }

   private static UniformSQL load(Element xml) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parseXML(xml);
      return sql;
   }

   private static void assertSameRows(String text, String generated) throws SQLException {
      Random random = new Random(77510);

      try(Connection conn = DriverManager.getConnection(URL + ";create=true")) {
         try(Statement stmt = conn.createStatement()) {
            for(String table : TABLES) {
               try {
                  stmt.executeUpdate("drop table " + table);
               }
               catch(SQLException ignore) {
                  // first run
               }

               stmt.executeUpdate("create table " + table + " (id int, k int)");
            }
         }

         for(int i = 0; i < 200; i++) {
            fillTables(conn, random);
            assertEquals(rows(conn, text), rows(conn, generated),
                         "dataset " + i + "\noriginal: " + text + "\ngenerated: " + generated);
         }
      }
   }

   private static final String[] TABLES = { "a", "b", "c" };

   private static void fillTables(Connection conn, Random random) throws SQLException {
      try(Statement stmt = conn.createStatement()) {
         for(String table : TABLES) {
            stmt.executeUpdate("delete from " + table);
         }
      }

      for(String table : TABLES) {
         try(PreparedStatement insert =
                conn.prepareStatement("insert into " + table + " values (?, ?)"))
         {
            int count = random.nextInt(5);

            for(int i = 0; i < count; i++) {
               for(int col = 1; col <= 2; col++) {
                  int value = random.nextInt(4);

                  if(value == 0) {
                     insert.setNull(col, Types.INTEGER);
                  }
                  else {
                     insert.setInt(col, value);
                  }
               }

               insert.addBatch();
            }

            insert.executeBatch();
         }
      }
   }

   // the result rows as a sorted multiset, by column name since fixUniformSQLInfo may
   // reorder the columns of a derived table
   private static List<String> rows(Connection conn, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(Statement stmt = conn.createStatement(); ResultSet result = stmt.executeQuery(query)) {
         int columns = result.getMetaData().getColumnCount();

         while(result.next()) {
            List<String> row = new ArrayList<>();
            Map<String, Integer> seen = new HashMap<>();

            for(int i = 1; i <= columns; i++) {
               // a.id and b.id share the label ID, number them in select order
               String label = result.getMetaData().getColumnLabel(i);
               int n = seen.merge(label, 1, Integer::sum);
               row.add(label + n + "=" + result.getObject(i));
            }

            Collections.sort(row);
            rows.add(String.join("|", row));
         }
      }

      Collections.sort(rows);
      return rows;
   }

   // JDBCDataSource's constructor needs the CredentialService bean, whose constructor is
   // package private
   @Configuration
   static class TestBeans {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      // fixUniformSQLInfo and the helper lookup ask Config for the database type
      @Bean
      public Config config() {
         Config config = mock(Config.class);
         when(config.getJDBCType(anyString())).thenReturn("Derby");
         return config;
      }

      // no metadata, fixUniformSQLInfo logs and continues as it does for a failed column fetch
      @Bean
      public XRepository repository() throws Exception {
         XRepository repository = mock(XRepository.class);
         when(repository.getMetaData(any(), any(), any(), anyBoolean(), any()))
            .thenReturn(new XNode());
         return repository;
      }
   }
}
