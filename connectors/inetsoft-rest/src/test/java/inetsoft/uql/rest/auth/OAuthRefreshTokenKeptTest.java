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
package inetsoft.uql.rest.auth;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.BlobStorageManager;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.ConnectionPoolFactory;
import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.rest.datasource.adobeanalytics.AdobeAnalyticsDataSource;
import inetsoft.uql.rest.datasource.constantcontact.ConstantContactDataSource;
import inetsoft.uql.rest.datasource.googlecal.GoogleCalendarDataSource;
import inetsoft.uql.rest.datasource.keap.KeapDataSource;
import inetsoft.uql.rest.datasource.quickbooks.QuickbooksReportsDataSource;
import inetsoft.uql.rest.datasource.remedyforce.RemedyforceDataSource;
import inetsoft.uql.rest.datasource.twitter.TwitterDataSource;
import inetsoft.uql.rest.json.RestJsonDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.tabular.oauth.AuthorizationClient;
import inetsoft.uql.tabular.oauth.OAuthDataSource;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.uql.util.Config;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.credential.CredentialService;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Bug #81957: a refresh whose result has no refresh token must leave the data source able to
 * refresh again. Drives the real refresh paths (AbstractRestDataSource.refreshTokens and
 * OAuthAuthenticator.authenticateRequest) with a mocked token service, through the real
 * DataSourceRegistry and XEngine, then expires the token again and checks that a second refresh
 * is attempted, from the runtime instance and from the stored definition. The hosted service is
 * not called, so whether it omits the refresh token is not shown here.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, OAuthRefreshTokenKeptTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
class OAuthRefreshTokenKeptTest {
   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   @Autowired
   private String securityInitializer;

   private final List<String> storedNames = new ArrayList<>();

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
   }

   @AfterEach
   void tearDown() {
      for(String name : storedNames) {
         try {
            registry.removeDataSource(name);
         }
         catch(Exception ignore) {
            // best effort
         }
      }
   }

   private static Map<String, Supplier<AbstractRestDataSource<?>>> sources() {
      Map<String, Supplier<AbstractRestDataSource<?>>> map = new LinkedHashMap<>();
      map.put("Twitter", TwitterDataSource::new);
      map.put("Keap", KeapDataSource::new);
      map.put("ConstantContact", ConstantContactDataSource::new);
      map.put("AdobeAnalytics", AdobeAnalyticsDataSource::new);
      map.put("QuickbooksReports", QuickbooksReportsDataSource::new);
      map.put("Remedyforce", RemedyforceDataSource::new);
      map.put("GoogleCalendar", GoogleCalendarDataSource::new);
      map.put("PlainBaseInheritor", RestJsonDataSource::new);
      return map;
   }

   private AbstractRestDataSource<?> store(String label, boolean cred, String rt) throws Exception {
      AbstractRestDataSource<?> ds = sources().get(label).get();
      ds.setName("keptRt" + label + (cred ? "C" : "F"));
      ds.setClientId("client");
      ds.setClientSecret("secret");

      if(!cred) {
         ds.setCredential(null);
      }

      ds.setAccessToken("AT-A");
      ds.setRefreshToken(rt);
      ds.setTokenExpiration(System.currentTimeMillis() - 60_000L);
      registry.setDataSource(ds, false);
      storedNames.add(ds.getName());
      return ds;
   }

   private AbstractRestDataSource<?> reload(AbstractRestDataSource<?> ds) {
      registry.clearCache();
      AbstractRestDataSource<?> s = (AbstractRestDataSource<?>) registry.getDataSource(ds.getName());
      assertNotNull(s, ds.getName());
      return s;
   }

   private static boolean tokenValid(AbstractRestDataSource<?> ds) throws Exception {
      Method m = AbstractRestDataSource.class.getDeclaredMethod("isTokenValid");
      m.setAccessible(true);
      return (Boolean) m.invoke(ds);
   }

   private static void refresh(AbstractRestDataSource<?> ds) throws Exception {
      Method m = AbstractRestDataSource.class.getDeclaredMethod("refreshTokens");
      m.setAccessible(true);
      m.invoke(ds);
   }

   private static Tokens result(String rt) {
      long now = System.currentTimeMillis();
      return Tokens.builder().accessToken("AT-B").refreshToken(rt).issued(now)
         .expiration(now + 3_600_000L).properties(Map.of("realmId", "1")).build();
   }

   private static void expire(AbstractRestDataSource<?> ds) {
      ds.setTokenExpiration(System.currentTimeMillis() - 60_000L);
   }

   // AbstractRestDataSource.refreshTokens, the path of the query execution
   @Test
   void dataSourceRefreshWithoutRefreshTokenCanRefreshAgain() throws Exception {
      for(String label : sources().keySet()) {
         // a data source reloaded from the registry gets its default credential back, so the
         // field is only meaningful for Twitter, which has no refresh token credential
         for(boolean cred : new boolean[] { true }) {
            for(String x : new String[] { null, "", "R2" }) {
               String msg = label + (cred ? " cred" : " field") + " x='" + x + "'";
               String expected = x == null || x.isEmpty() ? "R1" : x;
               AbstractRestDataSource<?> def = store(label, cred, "R1");
               AbstractRestDataSource<?> runtime = (AbstractRestDataSource<?>) def.clone();
               assertFalse(tokenValid(runtime), "before " + msg);

               try(MockedStatic<AuthorizationClient> client =
                      Mockito.mockStatic(AuthorizationClient.class))
               {
                  client.when(() -> AuthorizationClient.refresh(
                     any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                     .thenReturn(result(x));
                  refresh(runtime);

                  assertEquals("AT-B", runtime.getAccessToken(), "runtime AT " + msg);
                  assertEquals(expected, runtime.getRefreshToken(), "runtime RT " + msg);
                  AbstractRestDataSource<?> s = reload(def);
                  assertEquals("AT-B", s.getAccessToken(), "stored AT " + msg);
                  assertEquals(expected, s.getRefreshToken(), "stored RT " + msg);

                  // the token expires again: both copies still treat it as refreshable
                  expire(runtime);
                  expire(s);
                  assertFalse(tokenValid(runtime), "runtime valid " + msg);
                  assertFalse(tokenValid(s), "stored valid " + msg);
                  refresh(s);
                  client.verify(() -> AuthorizationClient.refresh(
                     any(), any(), any(), any(), any(), any(), anyBoolean(), any()), times(2));
               }
            }
         }
      }
   }

   // OAuthAuthenticator.authenticateRequest, the path of the sources that use the authenticator
   @Test
   @SuppressWarnings({ "unchecked", "rawtypes" })
   void authenticatorRefreshWithoutRefreshTokenKeepsTheStoredOne() throws Exception {
      int count = 0;

      for(String label : sources().keySet()) {
         AbstractRestDataSource<?> probe = sources().get(label).get();

         if(!(probe instanceof OAuthDataSource)) {
            continue;
         }

         count++;

         for(String x : new String[] { null, "", "R2" }) {
            String msg = label + " x='" + x + "'";
            String expected = x == null || x.isEmpty() ? "R1" : x;
            AbstractRestDataSource<?> def = store(label, true, "R1");
            AbstractRestDataSource<?> runtime = (AbstractRestDataSource<?>) def.clone();

            try(MockedStatic<AuthorizationClient> client =
                   Mockito.mockStatic(AuthorizationClient.class))
            {
               client.when(() -> AuthorizationClient.refreshTokens(any(OAuthDataSource.class),
                                                                   anyBoolean()))
                  .thenReturn(result(x));
               new OAuthAuthenticator(runtime)
                  .authenticateRequest(new HttpGet("http://localhost/"), HttpClientContext.create());
            }

            assertEquals("AT-B", runtime.getAccessToken(), "runtime AT " + msg);
            assertEquals(expected, runtime.getRefreshToken(), "runtime RT " + msg);
            AbstractRestDataSource<?> s = reload(def);
            assertEquals("AT-B", s.getAccessToken(), "stored AT " + msg);
            assertEquals(expected, s.getRefreshToken(), "stored RT " + msg);
            expire(s);
            assertFalse(tokenValid(s), "stored valid " + msg);
         }
      }

      assertTrue(count >= 1, "no source uses the authenticator");
   }

   // the beans that a save through the real XEngine and DataSourceRegistry needs
   @Configuration
   static class Beans {
      // the REST plugin is not installed in the test JVM, map its types to the classes
      @Bean
      public Config config() throws Exception {
         Config config = mock(Config.class);

         for(Supplier<AbstractRestDataSource<?>> supplier : sources().values()) {
            AbstractRestDataSource<?> ds = supplier.get();
            when(config.getDataSourceClass(ds.getType())).thenReturn(ds.getClass().getName());
         }

         when(config.getClass(anyString(), anyString()))
            .thenAnswer(inv -> Class.forName(inv.<String>getArgument(1)));
         return config;
      }

      @Bean
      public IndexedStorage indexedStorage(BlobStorageManager blobStorageManager) {
         return new BlobIndexedStorage(blobStorageManager);
      }

      @Bean
      public DataSourceRegistry dataSourceRegistry(IndexedStorage indexedStorage, Config config,
                                                   Cluster cluster) throws Exception
      {
         return new DataSourceRegistry(indexedStorage, config, cluster);
      }

      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         return mock(ConnectionPoolFactory.class);
      }

      @Bean
      public XRepository xRepository(Cluster cluster, Config config,
                                     DataSourceRegistry dataSourceRegistry,
                                     ConnectionPoolFactory connectionPoolFactory)
      {
         return new XEngine(cluster, config, dataSourceRegistry, connectionPoolFactory);
      }

      // the storage of the data sources needs the security provider
      @Bean
      public String securityInitializer(SecurityEngine securityEngine) {
         securityEngine.init();
         return "initialized";
      }

      @Bean
      public RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }

      // the constructors of these beans are package private
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }

      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }
}
