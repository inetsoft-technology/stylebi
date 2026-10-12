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

import inetsoft.sree.internal.cluster.ignite.IgniteUtils;
import inetsoft.test.*;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import inetsoft.web.portal.controller.database.RuntimeQueryService.RuntimeXQuery;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.*;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.*;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77858, the query editor keeps a parsed query in an Ignite cache
 * ({@code RuntimeQueryService}, a {@link RuntimeXQuery}), which the binary marshaller writes on
 * put and reads on get even on one node. The values recording the quoting of a column or alias
 * and the sql generated for a run must survive that round trip, at the top level and in a
 * derived table or a where subquery.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, JDBCSelectionIgniteMarshalTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JDBCSelectionIgniteMarshalTest {
   @BeforeAll
   static void startIgnite() throws Exception {
      int discoPort = freePort();
      int commPort = freePort();
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(Collections.singletonList("127.0.0.1:" + discoPort));
      TcpDiscoverySpi disco = new TcpDiscoverySpi();
      disco.setLocalAddress("127.0.0.1");
      disco.setLocalPort(discoPort);
      disco.setLocalPortRange(0);
      disco.setIpFinder(ipFinder);
      TcpCommunicationSpi comm = new TcpCommunicationSpi();
      comm.setLocalAddress("127.0.0.1");
      comm.setLocalPort(commPort);
      comm.setLocalPortRange(0);

      workDir = Files.createTempDirectory("ignite-77858");
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("bug77858-" + UUID.randomUUID());
      config.setWorkDirectory(workDir.toString());
      config.setLocalHost("127.0.0.1");
      config.setDiscoverySpi(disco);
      config.setCommunicationSpi(comm);
      config.setMetricsLogFrequency(0);
      config.setPeerClassLoadingEnabled(false);
      // as IgniteCluster configures it
      IgniteUtils.configBinaryTypes(config);
      ignite = Ignition.start(config);

      // as IgniteCluster.getCacheConfiguration creates the RuntimeQueryService cache
      CacheConfiguration<String, RuntimeXQuery> cacheConfig = new CacheConfiguration<>("bug77858");
      cacheConfig.setCacheMode(CacheMode.PARTITIONED);
      cacheConfig.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
      cacheConfig.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);
      cache = ignite.getOrCreateCache(cacheConfig);
   }

   @AfterAll
   static void stopIgnite() throws Exception {
      if(ignite != null) {
         ignite.close();
      }

      if(workDir != null) {
         try(var paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
         }
      }
   }

   @Test
   void quotedColumnsSurvive() throws Exception {
      // the reported query: get failed with "Can not set final ... ColumnQuote.column"
      UniformSQL sql = roundTrip(parse("select \"CATEGORY_ID\", \"CATEGORY_NAME\" from \"CATEGORIES\""));
      JDBCSelection selection = (JDBCSelection) sql.getSelection();
      assertTrue(selection.isQuoted(0));
      assertTrue(selection.isQuoted(1));
      assertEquals("select \"CATEGORY_ID\", \"CATEGORY_NAME\" from \"CATEGORIES\"", regenerate(sql));

      sql = roundTrip(parse("select c.\"CATEGORY_ID\", c.\"Mixed Case\" from \"CATEGORIES\" c " +
                            "where c.\"CATEGORY_ID\" = 1 order by c.\"Mixed Case\""));
      selection = (JDBCSelection) sql.getSelection();
      assertTrue(selection.isQuoted("c.CATEGORY_ID"));
      assertEquals("Mixed Case", selection.getQuotedColumn("c.Mixed Case"));
   }

   @Test
   void quotedAliasesSurvive() throws Exception {
      // put failed with "can't get field offset on a record class ... AliasQuote.quoted"
      UniformSQL sql = roundTrip(parse("select CATEGORY_ID as \"Id\", CATEGORY_NAME as nm from CATEGORIES"));
      JDBCSelection selection = (JDBCSelection) sql.getSelection();
      assertEquals(Boolean.TRUE, selection.isAliasQuoted(0));
      assertEquals(Boolean.FALSE, selection.isAliasQuoted(1));
   }

   @Test
   void derivedTableSurvives() throws Exception {
      // a quoted column in the derived table failed at put
      UniformSQL sql = roundTrip(parse("select d.\"CITY\" from (select c.\"CUSTOMER_ID\", c.\"CITY\" " +
                                       "from \"CUSTOMERS\" c) d"));
      UniformSQL derived = (UniformSQL) sql.getSelectTable(0).getName();
      JDBCSelection selection = (JDBCSelection) derived.getSelection();
      assertTrue(selection.isQuoted("c.CUSTOMER_ID"));
      assertTrue(selection.isQuoted("c.CITY"));
      assertTrue(((JDBCSelection) sql.getSelection()).isQuoted("d.CITY"));
   }

   @Test
   void subqueriesSurvive() throws Exception {
      // the regenerated sql keeps the quotes recorded in the subqueries
      String regenerated = regenerate(roundTrip(parse(
         "select o.\"ORDER_ID\", (select max(p.\"PRICE\") from \"PRODUCTS\" p) as \"Max\" " +
         "from \"ORDERS\" o where o.\"CUSTOMER_ID\" in " +
         "(select c.\"CUSTOMER_ID\" from \"CUSTOMERS\" c where c.\"CITY\" = 'X')")));
      assertTrue(regenerated.contains("p.\"PRICE\""), regenerated);
      assertTrue(regenerated.contains("select c.\"CUSTOMER_ID\" from \"CUSTOMERS\" c"), regenerated);
   }

   @Test
   void runSqlSurvives() throws Exception {
      UniformSQL sql = parse("select CATEGORY_ID, CATEGORY_NAME from CATEGORIES order by CATEGORY_NAME");
      ((JDBCSelection) sql.getSelection()).setColumnSQL(1, "upper(CATEGORY_NAME)");
      sql.setOrderBySQL(0, "lower(CATEGORY_NAME)");
      UniformSQL loaded = roundTrip(sql);

      assertEquals("upper(CATEGORY_NAME)", ((JDBCSelection) loaded.getSelection()).getColumnSQL(1));
      assertNull(((JDBCSelection) loaded.getSelection()).getColumnSQL(0));
      assertEquals("lower(CATEGORY_NAME)", loaded.getOrderBySQL(0));
   }

   @Test
   void storedQuerySurvives() throws Exception {
      // a saved query opened in the editor restores the quoting from its xml
      UniformSQL saved = parse("select c.\"CATEGORY_ID\" as \"Id\", \"CATEGORY_NAME\" from \"CATEGORIES\" c");
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         saved.writeXML(writer);
      }

      UniformSQL sql = new UniformSQL();
      sql.parseXML(Tool.parseXML(new StringReader(buffer.toString())).getDocumentElement());
      JDBCSelection selection = (JDBCSelection) sql.getSelection();
      assertTrue(selection.isQuoted(0));
      assertEquals(Boolean.TRUE, selection.isAliasQuoted(0));

      UniformSQL loaded = roundTrip(sql);
      assertTrue(((JDBCSelection) loaded.getSelection()).isQuoted(0));
      assertTrue(((JDBCSelection) loaded.getSelection()).isQuoted(1));
      assertEquals(Boolean.TRUE, ((JDBCSelection) loaded.getSelection()).isAliasQuoted(0));
   }

   /**
    * Ignite 2.x can't marshal a record, so a record type must not be declared by a field of the
    * parsed query classes, including the element types of their collections and maps.
    */
   @Test
   void noRecordTypeInQueryFields() {
      for(Class<?> cls : new Class<?>[] { UniformSQL.class, JDBCSelection.class }) {
         for(Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for(Field field : c.getDeclaredFields()) {
               if(!Modifier.isStatic(field.getModifiers()) &&
                  !Modifier.isTransient(field.getModifiers()))
               {
                  assertNoRecord(field.getGenericType(), c.getName() + "." + field.getName());
               }
            }
         }
      }
   }

   private static void assertNoRecord(Type type, String path) {
      if(type instanceof Class<?> cls) {
         assertFalse(cls.isRecord(), path + " declares the record " + cls.getName());
      }
      else if(type instanceof ParameterizedType ptype) {
         assertNoRecord(ptype.getRawType(), path);

         for(Type arg : ptype.getActualTypeArguments()) {
            assertNoRecord(arg, path);
         }
      }
      else if(type instanceof GenericArrayType atype) {
         assertNoRecord(atype.getGenericComponentType(), path);
      }
   }

   /**
    * Put the query in the cache and get it back, as RuntimeQueryService.saveRuntimeQuery and
    * getRuntimeQuery do, and check that it equals the original.
    */
   private static UniformSQL roundTrip(UniformSQL sql) {
      JDBCQuery query = new JDBCQuery();
      query.setSQLDefinition(sql);
      String id = UUID.randomUUID().toString();
      cache.put(id, new RuntimeXQuery(query, id, "ds"));
      RuntimeXQuery restored = cache.get(id);
      assertNotSame(query, restored.getQuery());

      UniformSQL loaded = (UniformSQL) restored.getQuery().getSQLDefinition();
      assertEquals(sql.getSelection(), loaded.getSelection());
      assertEquals(sql.getSelection().hashCode(), loaded.getSelection().hashCode());
      assertEquals(regenerate((UniformSQL) sql.clone()), regenerate((UniformSQL) loaded.clone()));
      return loaded;
   }

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      return sql;
   }

   private static int freePort() throws Exception {
      try(ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }

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

   private static Ignite ignite;
   private static IgniteCache<String, RuntimeXQuery> cache;
   private static Path workDir;
}
