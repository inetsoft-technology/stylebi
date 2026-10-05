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

import com.microsoft.graph.httpcore.AuthenticationHandler;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MessageListener;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.service.ClearDataSourceCacheEvent;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.util.credential.CloudResourceOwnerPasswordCredentials;
import okhttp3.*;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #77730: the ROPC credential of SharePoint Online could not hold the tokens, so the token
 * that the authenticator handed to the Graph SDK was always null and every request was sent with
 * no Authorization header. Each request also made a password grant and saved the data source,
 * which cleared the data source cache of the cluster. The registry, the repository and the Graph
 * SDK's AuthenticationHandler are the real ones; only the token endpoint is mocked.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SharepointTokenSaveTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
class SharepointTokenReuseTest {
   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private String securityInitializer;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      grants.clear();
      responses.clear();
      cacheEvents.set(0);
      cacheListener = event -> {
         if(event.getMessage() instanceof ClearDataSourceCacheEvent) {
            cacheEvents.incrementAndGet();
         }
      };
      Cluster.getInstance().addMessageListener(cacheListener);
   }

   @AfterEach
   void tearDown() {
      Cluster.getInstance().removeMessageListener(cacheListener);
   }

   // token state none, then valid: one password grant, the token is sent, and is reused by the
   // next request and by the next query without saving the data source again
   @Test
   void localTokenIsSentAndReused() throws Exception {
      registry.setDataSource(source("spDs"), false);
      cacheEvents.set(0);
      tokenResponse(200, token("tok-1", "R1"));

      SharepointOnlineDataSource runtime = runtime("spDs");
      assertEquals("Bearer tok-1", send(runtime, true));
      assertEquals("Bearer tok-1", send(runtime, true));
      assertEquals(List.of("password"), grants);
      assertEquals(1, cacheEvents.get(), "the tokens are saved once");

      registry.clearCache();
      SharepointOnlineDataSource stored = (SharepointOnlineDataSource) registry.getDataSource("spDs");
      assertEquals("tok-1", stored.getAccessToken());
      assertEquals("R1", stored.getRefreshToken());
      assertEquals(runtime.getTokenExpires(), stored.getTokenExpires());
      cacheEvents.set(0);

      // the next query
      assertEquals("Bearer tok-1", send(runtime("spDs"), true));
      assertEquals(List.of("password"), grants);
      assertEquals(0, cacheEvents.get(), "a valid token is not saved again");
   }

   // token state expired with a refresh token: the refresh token is used, not the password
   @Test
   void expiredTokenIsRefreshed() throws Exception {
      registry.setDataSource(expired(source("spDs"), "R1"), false);
      tokenResponse(200, token("tok-2", "R2"));

      assertEquals("Bearer tok-2", send(runtime("spDs"), true));
      assertEquals(List.of("refresh_token"), grants);
      assertTrue(bodies.get(0).contains("refresh_token=R1"), bodies.get(0));

      registry.clearCache();
      SharepointOnlineDataSource stored = (SharepointOnlineDataSource) registry.getDataSource("spDs");
      assertEquals("tok-2", stored.getAccessToken());
      assertEquals("R2", stored.getRefreshToken());
      assertTrue(stored.getTokenExpires().isAfter(Instant.now()));
   }

   // a refresh response without a new refresh token keeps the old one
   @Test
   void refreshTokenIsKeptIfNotReplaced() throws Exception {
      registry.setDataSource(expired(source("spDs"), "R1"), false);
      tokenResponse(200, token("tok-2", null));

      SharepointOnlineDataSource runtime = runtime("spDs");
      assertEquals("Bearer tok-2", send(runtime, true));
      assertEquals("R1", runtime.getRefreshToken());
   }

   // token state expired without a refresh token: the stale token is not reused
   @Test
   void expiredTokenWithoutRefreshTokenSignsInAgain() throws Exception {
      registry.setDataSource(expired(source("spDs"), null), false);
      tokenResponse(200, token("tok-2", "R2"));

      assertEquals("Bearer tok-2", send(runtime("spDs"), true));
      assertEquals(List.of("password"), grants);
   }

   // token state refresh rejected: the password is used instead
   @Test
   void rejectedRefreshSignsInAgain() throws Exception {
      registry.setDataSource(expired(source("spDs"), "revoked"), false);
      tokenResponse(400, "{\"error\":\"invalid_grant\"}");
      tokenResponse(200, token("tok-3", "R3"));

      SharepointOnlineDataSource runtime = runtime("spDs");
      assertEquals("Bearer tok-3", send(runtime, true));
      assertEquals(List.of("refresh_token", "password"), grants);
      assertEquals("R3", runtime.getRefreshToken());
   }

   // entry point test connection: the token is sent but the data source is not saved
   @Test
   void testConnectionSendsTokenWithoutSaving() throws Exception {
      registry.setDataSource(source("spDs"), false);
      cacheEvents.set(0);
      tokenResponse(200, token("tok-1", "R1"));

      SharepointOnlineDataSource runtime = runtime("spDs");
      assertEquals("Bearer tok-1", send(runtime, false));
      assertEquals("Bearer tok-1", send(runtime, false));
      assertEquals(List.of("password"), grants);
      assertEquals(0, cacheEvents.get());

      registry.clearCache();
      assertNull(((SharepointOnlineDataSource) registry.getDataSource("spDs")).getAccessToken());
   }

   // credential mode cloud: the tokens are kept by the runtime instance and sent, but they are
   // not saved, since the data source stores only the id of the credential (Bug #77699)
   @Test
   void cloudTokenIsSentAndNotSaved() throws Exception {
      SharepointOnlineDataSource ds = new SharepointOnlineDataSource();
      ds.setName("spCloud");
      CloudResourceOwnerPasswordCredentials credential = new CloudResourceOwnerPasswordCredentials();
      credential.setId("secret-id");
      credential.setUser("alice");
      credential.setPassword("password");
      credential.setClientId("client");
      credential.setClientSecret("secret");
      credential.setTenantId("acme");
      ds.setCredential(credential);
      registry.setDataSource(ds, false);
      cacheEvents.set(0);
      tokenResponse(200, token("tok-1", "R1"));

      // the stored definition holds only the id of the secret, which can't be fetched in the
      // test, so the query runs on a clone of the definition with the values of the secret
      SharepointOnlineDataSource runtime = (SharepointOnlineDataSource) ds.clone();
      assertEquals("Bearer tok-1", send(runtime, true));
      assertEquals("Bearer tok-1", send(runtime, true));
      assertEquals(List.of("password"), grants);
      assertEquals("tok-1", runtime.getAccessToken());
      assertEquals(0, cacheEvents.get(), "a cloud credential is not saved");

      registry.clearCache();
      SharepointOnlineDataSource stored =
         (SharepointOnlineDataSource) registry.getDataSource("spCloud");
      assertNull(stored.getAccessToken());
      assertNull(stored.getTokenExpires());
   }

   // the variables of the credential are replaced for a query: tokens of the stored definition
   // are not used for another account, and those of the query are not saved onto it, the stored
   // definition is not written at all
   @Test
   void tokensAreNotSharedAcrossAccounts() throws Exception {
      SharepointOnlineDataSource ds = source("spDs");
      ds.setUser("$(user)");
      ds.setAccessToken("tok-template");
      ds.setRefreshToken("R-template");
      ds.setTokenExpires(Instant.now().plus(1, ChronoUnit.HOURS));
      registry.setDataSource(ds, false);
      tokenResponse(200, token("tok-alice", "R-alice"));

      SharepointOnlineDataSource runtime = runtime("spDs");
      VariableTable vars = new VariableTable();
      vars.put("user", "alice");
      TabularUtil.replaceVariables(runtime, vars);
      cacheEvents.set(0);

      assertEquals("Bearer tok-alice", send(runtime, true));
      assertEquals(List.of("password"), grants);
      assertTrue(bodies.get(0).contains("username=alice"), bodies.get(0));
      // nothing is saved, so the stored definition is not written and no cache is cleared
      assertEquals(0, cacheEvents.get(), "the stored definition is not written");

      registry.clearCache();
      SharepointOnlineDataSource stored = (SharepointOnlineDataSource) registry.getDataSource("spDs");
      assertEquals("$(user)", stored.getUser());
      assertEquals("tok-template", stored.getAccessToken());
      assertEquals("R-template", stored.getRefreshToken());
   }

   // changing the account discards the tokens, an unchanged value keeps them
   @Test
   void changingTheAccountClearsTokens() {
      SharepointOnlineDataSource ds = source("spDs");
      ds.setAccessToken("tok-1");
      ds.setRefreshToken("R1");
      ds.setTokenExpires(Instant.now().plus(1, ChronoUnit.HOURS));

      ds.setUser("alice");
      ds.setTenantId("acme");
      assertEquals("tok-1", ds.getAccessToken());

      ds.setUser("bob");
      assertNull(ds.getAccessToken());
      assertNull(ds.getRefreshToken());
      assertNull(ds.getTokenExpires());
   }

   /**
    * Sends a Graph request through the Graph SDK's AuthenticationHandler.
    *
    * @return the Authorization header that was sent.
    */
   private String send(SharepointOnlineDataSource ds, boolean saveTokens) throws Exception {
      CloseableHttpClient http = mock(CloseableHttpClient.class);
      ArgumentCaptor<ClassicHttpRequest> request = ArgumentCaptor.forClass(ClassicHttpRequest.class);
      when(http.execute(request.capture(), any(HttpClientResponseHandler.class)))
         .thenAnswer(inv -> {
            ClassicHttpRequest post = inv.getArgument(0);
            String body = EntityUtils.toString(((org.apache.hc.core5.http.HttpEntityContainer) post)
                                                  .getEntity());
            bodies.add(body);
            grants.add(body.contains("grant_type=refresh_token") ? "refresh_token" : "password");
            Object[] next = responses.poll();
            BasicClassicHttpResponse response = new BasicClassicHttpResponse((Integer) next[0]);
            response.setEntity(new StringEntity((String) next[1], ContentType.APPLICATION_JSON));
            return inv.<HttpClientResponseHandler<?>>getArgument(1).handleResponse(response);
         });

      Request graphRequest = new Request.Builder()
         .url(new URL("https://graph.microsoft.com/v1.0/sites/root")).build();
      Interceptor.Chain chain = mock(Interceptor.Chain.class);
      when(chain.request()).thenReturn(graphRequest);
      ArgumentCaptor<Request> sent = ArgumentCaptor.forClass(Request.class);
      when(chain.proceed(sent.capture())).thenAnswer(inv -> new Response.Builder()
         .request(inv.getArgument(0)).protocol(Protocol.HTTP_1_1).code(200).message("OK")
         .body(ResponseBody.create("{}", MediaType.get("application/json"))).build());

      try(MockedStatic<HttpClients> clients = mockStatic(HttpClients.class)) {
         clients.when(HttpClients::createDefault).thenReturn(http);
         new AuthenticationHandler(new SharepointAuthenticator(ds, saveTokens)).intercept(chain);
      }

      return sent.getValue().header("Authorization");
   }

   private SharepointOnlineDataSource runtime(String name) {
      // TabularHandler.execute runs the query on a clone of the stored data source
      return (SharepointOnlineDataSource) registry.getDataSource(name).clone();
   }

   private void tokenResponse(int code, String body) {
      responses.add(new Object[] { code, body });
   }

   private static String token(String access, String refresh) {
      return "{\"token_type\":\"Bearer\",\"expires_in\":3600,\"access_token\":\"" + access + "\"" +
         (refresh == null ? "" : ",\"refresh_token\":\"" + refresh + "\"") + "}";
   }

   private static SharepointOnlineDataSource expired(SharepointOnlineDataSource ds, String refresh) {
      ds.setAccessToken("tok-old");
      ds.setRefreshToken(refresh);
      ds.setTokenExpires(Instant.now().minus(1, ChronoUnit.MINUTES));
      return ds;
   }

   private static SharepointOnlineDataSource source(String name) {
      SharepointOnlineDataSource ds = new SharepointOnlineDataSource();
      ds.setName(name);
      ds.setUser("alice");
      ds.setPassword("password");
      ds.setClientId("client");
      ds.setTenantId("acme");
      ds.setClientSecret("secret");
      return ds;
   }

   private final List<String> grants = new ArrayList<>();
   private final List<String> bodies = new ArrayList<>();
   private final Deque<Object[]> responses = new ArrayDeque<>();
   private final AtomicInteger cacheEvents = new AtomicInteger();
   private MessageListener cacheListener;
}
