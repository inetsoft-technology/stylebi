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
package inetsoft.uql.odata;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.BlobStorageManager;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.ConnectionPoolFactory;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.uql.tabular.oauth.AuthorizationClient;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.uql.util.Config;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.credential.CredentialService;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.olingo.client.core.http.DefaultHttpClientFactory;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77699: a query of an OData data source runs on a clone of the data source whose $(var)
 * templates have been replaced with the values of the query. After a token refresh the client
 * factories saved that clone, which wrote the values of the query over the stored templates. The
 * clone also shared its credential with the instance cached by the registry, so the templates
 * kept in the credential, e.g. the scope, were rewritten in the cache without any save. The
 * registry, the repository and the client factories are the real ones; the query runs the steps
 * of TabularHandler.execute.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, ODataTokenSaveTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@SuppressWarnings("deprecation")
class ODataTokenSaveTest {
   private static final String URL = "http://127.0.0.1:1/$(tenant)/svc";
   private static final String SCOPE = "$(scope)";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private String securityInitializer;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
   }

   // the refreshed tokens are stored and the templates are kept, in the cache and in storage
   @Test
   void refreshedTokensAreSavedOntoTheStoredDefinition() throws Exception {
      registry.setDataSource(source("rfDs"), false);
      ODataDataSource runtime = prepare("rfDs");

      // replacing the variables of the query's clone does not change the cached instance
      ODataDataSource cached = (ODataDataSource) registry.getDataSource("rfDs");
      assertNotSame(cached.getCredential(), runtime.getCredential());
      assertEquals(SCOPE, cached.getOdataScope());
      assertEquals(URL, cached.getURL());

      HttpGet request = new HttpGet("http://127.0.0.1:1/acme/svc/Items");

      try(MockedStatic<AuthorizationClient> client = mockStatic(AuthorizationClient.class)) {
         client.when(() -> AuthorizationClient.refreshTokens(any(), anyBoolean()))
            .thenReturn(tokens());
         intercept(new ODataHttpClientFactory(runtime, true), request);
      }

      assertEquals("Bearer tok-B", request.getFirstHeader("Authorization").getValue());
      assertEquals("http://127.0.0.1:1/acme/svc", runtime.getURL());
      assertEquals("read.all", runtime.getOdataScope());

      cached = (ODataDataSource) registry.getDataSource("rfDs");
      assertEquals("tok-B", cached.getAccessToken());
      assertEquals(SCOPE, cached.getOdataScope());
      assertEquals(URL, cached.getURL());

      registry.clearCache();
      ODataDataSource stored = (ODataDataSource) registry.getDataSource("rfDs");
      assertEquals("tok-B", stored.getAccessToken());
      assertEquals("R2", stored.getRefreshToken());
      assertEquals(SCOPE, stored.getOdataScope());
      assertEquals(URL, stored.getURL());
   }

   // the password grant factory saves the same way
   @Test
   void passwordGrantTokensAreSavedOntoTheStoredDefinition() throws Exception {
      registry.setDataSource(source("pwDs"), false);
      ODataDataSource runtime = prepare("pwDs");

      try(MockedStatic<AuthorizationClient> client = mockStatic(AuthorizationClient.class)) {
         client.when(() -> AuthorizationClient.refreshPasswordGrantToken(any()))
            .thenReturn(tokens());
         intercept(new OAuthPasswordGrantClientFactory(runtime, true),
                   new HttpGet("http://127.0.0.1:1/acme/svc/Items"));
      }

      registry.clearCache();
      ODataDataSource stored = (ODataDataSource) registry.getDataSource("pwDs");
      assertEquals("tok-B", stored.getAccessToken());
      assertEquals(SCOPE, stored.getOdataScope());
      assertEquals(URL, stored.getURL());
   }

   // e.g. a Test in the data source editor before the data source is saved: a token refresh
   // does not create the data source
   @Test
   void dataSourceThatIsNotStoredIsNotCreated() throws Exception {
      ODataDataSource unsaved = source("unsavedDs");
      HttpGet request = new HttpGet("http://127.0.0.1:1/acme/svc/Items");

      try(MockedStatic<AuthorizationClient> client = mockStatic(AuthorizationClient.class)) {
         client.when(() -> AuthorizationClient.refreshTokens(any(), anyBoolean()))
            .thenReturn(tokens());
         intercept(new ODataHttpClientFactory(unsaved, true), request);
      }

      assertEquals("Bearer tok-B", request.getFirstHeader("Authorization").getValue());
      registry.clearCache();
      assertNull(registry.getDataSource("unsavedDs"));
   }

   // TabularHandler.execute: clone the data source and replace its variables
   private ODataDataSource prepare(String name) {
      ODataDataSource runtime = (ODataDataSource) registry.getDataSource(name).clone();
      VariableTable vars = new VariableTable();
      vars.put("tenant", "acme");
      vars.put("scope", "read.all");
      TabularUtil.replaceVariables(runtime, vars);
      assertEquals("read.all", runtime.getOdataScope());
      return runtime;
   }

   // runs the request interceptor that the factory adds to the client
   private static void intercept(DefaultHttpClientFactory factory, HttpGet request)
      throws Exception
   {
      DefaultHttpClient client = factory.create(HttpMethod.GET, URI.create(request.getURI().toString()));
      HttpRequestInterceptor interceptor =
         client.getRequestInterceptor(client.getRequestInterceptorCount() - 1);
      interceptor.process(request, new BasicHttpContext());
   }

   private static Tokens tokens() {
      return Tokens.builder().accessToken("tok-B").refreshToken("R2")
         .issued(System.currentTimeMillis())
         .expiration(System.currentTimeMillis() + 3_600_000L).build();
   }

   private static ODataDataSource source(String name) {
      ODataDataSource ds = new ODataDataSource();
      ds.setName(name);
      ds.setURL(URL);
      ds.setUser("user");
      ds.setPassword("password");
      ds.setOdataClientId("client");
      ds.setOdataClientSecret("secret");
      ds.setOdataTokenUri("http://127.0.0.1:1/token");
      ds.setOdataScope(SCOPE);
      ds.setAccessToken("tok-A");
      ds.setRefreshToken("R");
      ds.setTokenExpiration(System.currentTimeMillis() - 60_000L);
      return ds;
   }

   // the beans that a save through the real XEngine and DataSourceRegistry needs
   @Configuration
   static class Beans {
      // the OData plugin is not installed in the test JVM, map its type to the class
      @Bean
      public Config config() throws Exception {
         Config config = mock(Config.class);
         when(config.getDataSourceClass(ODataDataSource.TYPE))
            .thenReturn(ODataDataSource.class.getName());
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
