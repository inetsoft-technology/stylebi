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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
import inetsoft.uql.rest.datasource.keap.KeapDataSource;
import inetsoft.uql.rest.datasource.zohocrm.ZohoCRMDataSource;
import inetsoft.uql.rest.json.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.tabular.HttpParameter;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.uql.tabular.oauth.AuthorizationClient;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.uql.util.Config;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.credential.CredentialService;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77692: a query of a REST data source with OAuth runs on a clone of the data source whose
 * $(var) templates have been replaced with the values of the query. The authenticator used to
 * save that clone on every request, which wrote the values of the first query over the stored
 * templates. It must save only after a token refresh, and only the tokens, onto the stored
 * definition. The registry, the repository, the runtime and the authenticator are the real ones;
 * the query runs the steps of TabularHandler.execute.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, OAuthTokenSaveTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
class OAuthTokenSaveTest {
   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private String securityInitializer;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
         requests.add(exchange.getRequestURI().getPath() + " X-Tenant=" +
                         exchange.getRequestHeaders().getFirst("X-Tenant") + " " +
                         exchange.getRequestHeaders().getFirst("Authorization"));
         respond(exchange);
      });
      server.start();
      base = "http://127.0.0.1:" + server.getAddress().getPort();
   }

   @AfterEach
   void tearDown() {
      if(server != null) {
         server.stop(0);
      }
   }

   // the templates stay, each query requests its own value, an empty value is not written
   @Test
   void variablesInTheUrlAndHeaderStayTemplates() throws Exception {
      registry.setDataSource(source("varDs", validExpiration()), false);
      long modified = storedLastModified("varDs");

      runQuery("varDs", vars("acme"));
      assertTemplates("varDs");
      runQuery("varDs", vars("globex"));
      assertTemplates("varDs");
      runQuery("varDs", new VariableTable());
      assertTemplates("varDs");

      assertEquals(List.of("/api/acme/users X-Tenant=acme Bearer tok-A",
                           "/api/globex/users X-Tenant=globex Bearer tok-A",
                           "/api/users X-Tenant= Bearer tok-A"),
                   requests);
      // without a refresh nothing is written, a write sets the last modified time
      assertEquals(modified, storedLastModified("varDs"));
   }

   // a data source without variables is not written either
   @Test
   void noWriteWithoutARefresh() throws Exception {
      RestJsonDataSource plain = source("plainDs", validExpiration());
      plain.setURL(base + "/api/fixed");
      plain.setQueryHttpParameters(new HttpParameter[0]);
      registry.setDataSource(plain, false);
      long modified = storedLastModified("plainDs");

      runQuery("plainDs", new VariableTable());
      runQuery("plainDs", new VariableTable());

      assertEquals(2, requests.size());
      assertEquals(modified, storedLastModified("plainDs"));
   }

   // after a refresh, the stored definition gets the new tokens and keeps its templates, and the
   // request uses the new token
   @Test
   void refreshedTokensAreSavedOntoTheStoredDefinition() throws Exception {
      registry.setDataSource(source("rfDs", System.currentTimeMillis() - 60_000L), false);
      long expiration = System.currentTimeMillis() + 3_600_000L;
      Tokens tokens = Tokens.builder().accessToken("tok-B").refreshToken("R2")
         .issued(System.currentTimeMillis()).expiration(expiration).build();

      RestJsonDataSource runtime = prepare("rfDs", new RestJsonQuery(), vars("acme"));
      HttpGet request = new HttpGet(runtime.getURL() + "/users");

      // the authenticator is called on this thread, the static mock is local to the thread
      try(MockedStatic<AuthorizationClient> client = mockStatic(AuthorizationClient.class);
          CloseableHttpAsyncClient http = HttpAsyncClients.createDefault())
      {
         client.when(() -> AuthorizationClient.refreshTokens(any(), anyBoolean()))
            .thenReturn(tokens);
         RestAuthenticatorFactory.createFrom(runtime, http)
            .authenticateRequest(request, HttpClientContext.create());
      }

      // the current request uses the new token
      assertEquals("Bearer tok-B", request.getFirstHeader("Authorization").getValue());
      assertEquals(base + "/api/acme", runtime.getURL());
      RestJsonDataSource stored = assertTemplates("rfDs");
      assertEquals("tok-B", stored.getAccessToken());
      assertEquals("R2", stored.getRefreshToken());
      assertEquals(expiration, stored.getTokenExpiration());
   }

   // a connector that overrides updateTokens: the stored definition is updated by its own
   // updateTokens, which computes the expiration from the issue time
   @Test
   void refreshUsesTheUpdateTokensOfTheConnector() throws Exception {
      KeapDataSource keap = new KeapDataSource();
      keap.setName("keapDs");
      keap.setClientId("client");
      keap.setClientSecret("secret");
      keap.setAccessToken("tok-A");
      keap.setRefreshToken("R");
      keap.setTokenExpiration(System.currentTimeMillis() - 60_000L);
      registry.setDataSource(keap, false);
      long issued = System.currentTimeMillis();
      Tokens tokens = Tokens.builder().accessToken("tok-B").refreshToken("R2")
         .issued(issued).expiration(0L).build();
      KeapDataSource runtime = (KeapDataSource) registry.getDataSource("keapDs").clone();

      try(MockedStatic<AuthorizationClient> client = mockStatic(AuthorizationClient.class)) {
         client.when(() -> AuthorizationClient.refresh(any(), any(), any(), any(), any(), any(),
                                                       anyBoolean(), any()))
            .thenReturn(tokens);
         runtime.getQueryHttpParameters();
      }

      registry.clearCache();
      KeapDataSource stored = (KeapDataSource) registry.getDataSource("keapDs");
      assertEquals("tok-B", stored.getAccessToken());
      assertEquals("R2", stored.getRefreshToken());
      assertEquals(issued + TimeUnit.HOURS.toMillis(24), stored.getTokenExpiration());
   }

   // AbstractRestDataSource.refreshTokens saves onto the stored definition: a variable that was
   // replaced on the query-time clone keeps its template in the stored definition
   @Test
   void refreshTokensKeepsTheTemplates() throws Exception {
      KeapDataSource keap = new KeapDataSource();
      keap.setName("keapVarDs");
      keap.setClientId("$(appkey)");
      keap.setClientSecret("secret");
      keap.setAccessToken("tok-A");
      keap.setRefreshToken("R");
      keap.setTokenExpiration(System.currentTimeMillis() - 60_000L);
      registry.setDataSource(keap, false);
      Tokens tokens = Tokens.builder().accessToken("tok-B").refreshToken("R2")
         .issued(System.currentTimeMillis()).expiration(0L).build();
      KeapDataSource runtime = (KeapDataSource) registry.getDataSource("keapVarDs").clone();
      VariableTable vars = new VariableTable();
      vars.put("appkey", "acme-key");
      TabularUtil.replaceVariables(runtime, vars);
      assertEquals("acme-key", runtime.getClientId());

      try(MockedStatic<AuthorizationClient> client = mockStatic(AuthorizationClient.class)) {
         client.when(() -> AuthorizationClient.refresh(any(), any(), any(), any(), any(), any(),
                                                       anyBoolean(), any()))
            .thenReturn(tokens);
         runtime.getQueryHttpParameters();
      }

      registry.clearCache();
      KeapDataSource stored = (KeapDataSource) registry.getDataSource("keapVarDs");
      assertEquals("tok-B", stored.getAccessToken());
      assertEquals("R2", stored.getRefreshToken());
      assertEquals("$(appkey)", stored.getClientId());
   }

   // ZohoCRM refreshes through its own token request and saves through the helper: the stored
   // definition gets the new tokens and the API domain, and keeps the account domain template
   @Test
   void zohoRefreshKeepsTheTemplates() throws Exception {
      server.createContext("/zoho/oauth/v2/token", exchange -> {
         byte[] bytes = ("{\"access_token\":\"tok-Z\",\"refresh_token\":\"R2\"," +
            "\"api_domain\":\"" + base + "/zapi\",\"expires_in\":3600}")
            .getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "application/json");
         exchange.sendResponseHeaders(200, bytes.length);

         try(OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
         }
      });
      ZohoCRMDataSource zoho = new ZohoCRMDataSource();
      zoho.setName("zohoVarDs");
      zoho.setClientId("client");
      zoho.setClientSecret("secret");
      zoho.setAccountDomain(base + "/$(region)");
      zoho.setAccessToken("tok-A");
      zoho.setRefreshToken("R");
      zoho.setTokenExpiration(System.currentTimeMillis() - 60_000L);
      registry.setDataSource(zoho, false);
      ZohoCRMDataSource runtime = (ZohoCRMDataSource) registry.getDataSource("zohoVarDs").clone();
      VariableTable vars = new VariableTable();
      vars.put("region", "zoho");
      TabularUtil.replaceVariables(runtime, vars);
      assertEquals(base + "/zoho", runtime.getAccountDomain());

      runtime.getQueryHttpParameters();

      // the runtime instance uses the new token
      assertEquals("tok-Z", runtime.getAccessToken());
      registry.clearCache();
      ZohoCRMDataSource stored = (ZohoCRMDataSource) registry.getDataSource("zohoVarDs");
      assertEquals("tok-Z", stored.getAccessToken());
      assertEquals("R2", stored.getRefreshToken());
      assertEquals(base + "/zapi", stored.getURL());
      assertTrue(stored.getTokenExpiration() > System.currentTimeMillis());
      assertEquals(base + "/$(region)", stored.getAccountDomain());
   }

   // a URL property with checkEnvVariables resolves $(prop) from the system properties at query
   // time, the resolved value is not written to the stored definition, with or without a refresh
   @Test
   void environmentValuesAreNotSaved() throws Exception {
      String prop = "bug77692.env.path";
      System.setProperty(prop, "envvalue");

      try {
         RestJsonDataSource ds = source("envDs", validExpiration());
         ds.setURL(base + "/$(" + prop + ")");
         ds.setQueryHttpParameters(new HttpParameter[0]);
         registry.setDataSource(ds, false);

         runQuery("envDs", new VariableTable());
         assertEquals(List.of("/envvalue/users X-Tenant=null Bearer tok-A"), requests);
         registry.clearCache();
         assertEquals(base + "/$(" + prop + ")", registry.getDataSource("envDs") instanceof
            RestJsonDataSource stored ? stored.getURL() : null);

         // now with an expired token and a refresh
         RestJsonDataSource expired = (RestJsonDataSource) registry.getDataSource("envDs").clone();
         expired.setTokenExpiration(System.currentTimeMillis() - 60_000L);
         registry.setDataSource(expired, false);
         RestJsonDataSource runtime = prepare("envDs", new RestJsonQuery(), new VariableTable());
         assertEquals(base + "/envvalue", runtime.getURL());
         Tokens tokens = Tokens.builder().accessToken("tok-B").refreshToken("R2")
            .issued(System.currentTimeMillis()).expiration(validExpiration()).build();

         try(MockedStatic<AuthorizationClient> client = mockStatic(AuthorizationClient.class);
             CloseableHttpAsyncClient http = HttpAsyncClients.createDefault())
         {
            client.when(() -> AuthorizationClient.refreshTokens(any(), anyBoolean()))
               .thenReturn(tokens);
            RestAuthenticatorFactory.createFrom(runtime, http)
               .authenticateRequest(new HttpGet(runtime.getURL()), HttpClientContext.create());
         }

         registry.clearCache();
         RestJsonDataSource stored = (RestJsonDataSource) registry.getDataSource("envDs");
         assertEquals("tok-B", stored.getAccessToken());
         assertEquals(base + "/$(" + prop + ")", stored.getURL());
      }
      finally {
         System.clearProperty(prop);
      }
   }

   private void runQuery(String name, VariableTable vars) throws Exception {
      // TabularHandler.execute: clone the query and the data source, fill the missing
      // variables, replace the variables, run
      RestJsonQuery query = new RestJsonQuery();
      query.setSuffix("/users");
      prepare(name, query, vars);
      new RestJsonRuntime().runQuery(query, vars);
   }

   private RestJsonDataSource prepare(String name, RestJsonQuery query, VariableTable vars)
      throws Exception
   {
      RestJsonDataSource xds = (RestJsonDataSource) registry.getDataSource(name).clone();
      query.setDataSource(xds);
      TabularUtil.fillNullVariablesWithEmptyString(new RestJsonRuntime(), query, vars);
      TabularUtil.replaceVariables(xds, vars);
      TabularUtil.replaceVariables(query, vars);
      return xds;
   }

   private RestJsonDataSource assertTemplates(String name) {
      registry.clearCache();
      RestJsonDataSource stored = (RestJsonDataSource) registry.getDataSource(name);
      assertEquals(base + "/api/$(tenant)", stored.getURL());
      assertEquals("$(tenant)", stored.getQueryHttpParameters()[0].getValue());
      return stored;
   }

   private long storedLastModified(String name) {
      registry.clearCache();
      return registry.getDataSource(name).getLastModified();
   }

   private RestJsonDataSource source(String name, long expiration) {
      RestJsonDataSource ds = new RestJsonDataSource();
      ds.setName(name);
      ds.setURL(base + "/api/$(tenant)");
      ds.setAuthType(AuthType.OAUTH);
      ds.setClientId("client");
      ds.setClientSecret("secret");
      ds.setTokenUri(base + "/token");
      ds.setAccessToken("tok-A");
      ds.setRefreshToken("R");
      ds.setTokenExpiration(expiration);
      ds.setQueryHttpParameters(new HttpParameter[] {
         HttpParameter.builder().type(HttpParameter.ParameterType.HEADER)
            .name("X-Tenant").value("$(tenant)").build()
      });
      return ds;
   }

   private static long validExpiration() {
      return System.currentTimeMillis() + 3_600_000L;
   }

   private static VariableTable vars(String tenant) {
      VariableTable vars = new VariableTable();
      vars.put("tenant", tenant);
      return vars;
   }

   private static void respond(HttpExchange exchange) throws IOException {
      byte[] bytes = "[]".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);

      try(OutputStream output = exchange.getResponseBody()) {
         output.write(bytes);
      }
   }

   private HttpServer server;
   private String base;
   private final List<String> requests = new CopyOnWriteArrayList<>();

   // the beans that a save through the real XEngine and DataSourceRegistry needs
   @Configuration
   static class Beans {
      // the REST plugin is not installed in the test JVM, map its types to the classes
      @Bean
      public Config config() throws Exception {
         Config config = mock(Config.class);
         when(config.getDataSourceClass(RestJsonDataSource.TYPE))
            .thenReturn(RestJsonDataSource.class.getName());
         when(config.getDataSourceClass("Rest.InfusionSoft"))
            .thenReturn(KeapDataSource.class.getName());
         when(config.getDataSourceClass("Rest.ZohoCRM"))
            .thenReturn(ZohoCRMDataSource.class.getName());
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
