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
package inetsoft.uql.onedrive;

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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77699: a OneDrive query runs on a clone of the data source whose $(var) templates have
 * been replaced with the values of the query. The Client ID is kept in the credential. After a
 * token refresh the data source saved that clone, and the clone shared its credential with the
 * instance cached by the registry, so the value of the query replaced the stored template. The
 * registry and the repository are the real ones; the query runs the steps of
 * TabularHandler.execute.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, OneDriveTokenSaveTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
class OneDriveTokenSaveTest {
   private static final String CLIENT_ID = "$(clientId)";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private String securityInitializer;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
   }

   // the refreshed tokens are stored and the template of the credential is kept, in the cache
   // and in storage
   @Test
   void refreshedTokensAreSavedOntoTheStoredDefinition() throws Exception {
      registry.setDataSource(source("odDs"), false);

      // TabularHandler.execute: clone the data source and replace its variables
      OneDriveDataSource runtime = (OneDriveDataSource) registry.getDataSource("odDs").clone();
      VariableTable vars = new VariableTable();
      vars.put("clientId", "acme-client");
      TabularUtil.replaceVariables(runtime, vars);
      assertEquals("acme-client", runtime.getClientId());

      OneDriveDataSource cached = (OneDriveDataSource) registry.getDataSource("odDs");
      assertEquals(CLIENT_ID, cached.getClientId());

      try(MockedStatic<AuthorizationClient> client = mockStatic(AuthorizationClient.class)) {
         client.when(() -> AuthorizationClient.refresh(
            any(), any(), any(), any(), any(), any(), anyBoolean(), any())).thenReturn(tokens());
         runtime.refreshTokens();

         // the refresh request is made with the value of the query
         client.verify(() -> AuthorizationClient.refresh(
            any(), eq("R"), eq("acme-client"), any(), any(), any(), anyBoolean(), any()));
      }

      assertEquals("tok-B", runtime.getAccessToken());

      cached = (OneDriveDataSource) registry.getDataSource("odDs");
      assertEquals("tok-B", cached.getAccessToken());
      assertEquals(CLIENT_ID, cached.getClientId());

      registry.clearCache();
      OneDriveDataSource stored = (OneDriveDataSource) registry.getDataSource("odDs");
      assertEquals("tok-B", stored.getAccessToken());
      assertEquals("R2", stored.getRefreshToken());
      assertEquals(CLIENT_ID, stored.getClientId());
      assertEquals("secret", stored.getClientSecret());
   }

   private static Tokens tokens() {
      return Tokens.builder().accessToken("tok-B").refreshToken("R2")
         .issued(System.currentTimeMillis())
         .expiration(System.currentTimeMillis() + 3_600_000L).build();
   }

   private static OneDriveDataSource source(String name) {
      OneDriveDataSource ds = new OneDriveDataSource();
      ds.setName(name);
      ds.setClientId(CLIENT_ID);
      ds.setClientSecret("secret");
      ds.setAccessToken("tok-A");
      ds.setRefreshToken("R");
      ds.setTokenExpiration(System.currentTimeMillis() - 60_000L);
      return ds;
   }

   // the beans that a save through the real XEngine and DataSourceRegistry needs
   @Configuration
   static class Beans {
      // the OneDrive plugin is not installed in the test JVM, map its type to the class
      @Bean
      public Config config() throws Exception {
         Config config = mock(Config.class);
         when(config.getDataSourceClass(OneDriveDataSource.TYPE))
            .thenReturn(OneDriveDataSource.class.getName());
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
