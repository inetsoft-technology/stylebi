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
package inetsoft.uql.sharepoint;

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
import inetsoft.uql.util.Config;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.credential.CredentialService;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77699: a SharePoint query runs on a clone of the data source whose $(var) templates have
 * been replaced with the values of the query. The User, Password, Client ID, Tenant ID and Client
 * Secret are kept in the credential. After it obtained a token the authenticator saved that
 * clone, and the clone shared its credential with the instance cached by the registry, so the
 * values of the query replaced the stored templates. The registry and the repository are the
 * real ones; the query runs the steps of TabularHandler.execute.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SharepointTokenSaveTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
class SharepointTokenSaveTest {
   private static final String USER = "$(user)";
   private static final String TENANT = "$(tenant)";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private String securityInitializer;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
   }

   // the token expiration is stored and the templates of the credential are kept, in the cache
   // and in storage
   @Test
   void tokensAreSavedOntoTheStoredDefinition() throws Exception {
      registry.setDataSource(source("spDs"), false);

      // TabularHandler.execute: clone the data source and replace its variables
      SharepointOnlineDataSource runtime =
         (SharepointOnlineDataSource) registry.getDataSource("spDs").clone();
      VariableTable vars = new VariableTable();
      vars.put("user", "alice");
      vars.put("tenant", "acme");
      TabularUtil.replaceVariables(runtime, vars);
      assertEquals("alice", runtime.getUser());
      assertEquals("acme", runtime.getTenantId());

      SharepointOnlineDataSource cached =
         (SharepointOnlineDataSource) registry.getDataSource("spDs");
      assertEquals(USER, cached.getUser());
      assertEquals(TENANT, cached.getTenantId());

      CloseableHttpClient http = mock(CloseableHttpClient.class);
      ArgumentCaptor<ClassicHttpRequest> request = ArgumentCaptor.forClass(ClassicHttpRequest.class);
      when(http.execute(request.capture(), any(HttpClientResponseHandler.class)))
         .thenAnswer(inv -> inv.<HttpClientResponseHandler<?>>getArgument(1)
            .handleResponse(tokenResponse()));

      try(MockedStatic<HttpClients> clients = mockStatic(HttpClients.class)) {
         clients.when(HttpClients::createDefault).thenReturn(http);
         new SharepointAuthenticator(runtime, true)
            .getAuthorizationTokenAsync(new URL("https://graph.microsoft.com/v1.0/sites"))
            .get();
      }

      // the token request is made with the values of the query
      HttpPost post = (HttpPost) request.getValue();
      assertEquals("https://login.microsoftonline.com/acme/oauth2/v2.0/token",
                   post.getUri().toString());
      assertTrue(EntityUtils.toString(post.getEntity()).contains("username=alice"));
      assertNotNull(runtime.getTokenExpires());

      cached = (SharepointOnlineDataSource) registry.getDataSource("spDs");
      assertEquals(runtime.getTokenExpires(), cached.getTokenExpires());
      assertEquals(USER, cached.getUser());
      assertEquals(TENANT, cached.getTenantId());

      registry.clearCache();
      SharepointOnlineDataSource stored =
         (SharepointOnlineDataSource) registry.getDataSource("spDs");
      assertEquals(runtime.getTokenExpires(), stored.getTokenExpires());
      assertEquals(USER, stored.getUser());
      assertEquals(TENANT, stored.getTenantId());
      assertEquals("password", stored.getPassword());
   }

   private static BasicClassicHttpResponse tokenResponse() {
      BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
      response.setEntity(new StringEntity(
         "{\"token_type\":\"Bearer\",\"expires_in\":3600," +
            "\"access_token\":\"tok-B\",\"refresh_token\":\"R2\"}",
         ContentType.APPLICATION_JSON));
      return response;
   }

   private static SharepointOnlineDataSource source(String name) {
      SharepointOnlineDataSource ds = new SharepointOnlineDataSource();
      ds.setName(name);
      ds.setUser(USER);
      ds.setPassword("password");
      ds.setClientId("client");
      ds.setTenantId(TENANT);
      ds.setClientSecret("secret");
      return ds;
   }

   // the beans that a save through the real XEngine and DataSourceRegistry needs
   @Configuration
   static class Beans {
      // the SharePoint plugin is not installed in the test JVM, map its type to the class
      @Bean
      public Config config() throws Exception {
         Config config = mock(Config.class);
         when(config.getDataSourceClass(SharepointOnlineDataSource.TYPE))
            .thenReturn(SharepointOnlineDataSource.class.getName());
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
