/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.rest.json;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.test.*;
import inetsoft.uql.rest.*;
import inetsoft.uql.rest.auth.*;
import inetsoft.uql.rest.xml.RestXMLDataSource;
import inetsoft.uql.rest.xml.RestXMLQuery;
import inetsoft.uql.tabular.HttpParameter;
import inetsoft.util.ThreadContext;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81931: the key of the cached REST responses must include everything that selects the
 * response, i.e. the credentials applied by the authenticator, the request method and content
 * type and the query type, and must not contain secrets in clear.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, RestResponseCacheKeyTest.CredentialConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class RestResponseCacheKeyTest {
   @BeforeEach
   void setUp() throws Exception {
      oldPrincipal = ThreadContext.getContextPrincipal();
      forgetTheTimeout();
      // the cache files survive between runs, so every test uses its own url
      url = "http://host/" + UUID.randomUUID();
   }

   @AfterEach
   void tearDown() throws Exception {
      ThreadContext.setContextPrincipal(oldPrincipal);
      // the cache would stay on for every other class that runs in this JVM
      forgetTheTimeout();
   }

   @Test
   void identicalRequestsHaveTheSameKey() {
      Consumer<RestJsonDataSource> config = ds -> {
         ds.setAuthType(AuthType.BASIC);
         ds.setUser("alice");
         ds.setPassword("alice-password");
         ds.setQueryHttpParameters(new HttpParameter[] { secretHeader("secret-value") });
      };

      assertEquals(key(jsonQuery(config)), key(jsonQuery(config)));
   }

   @Test
   void basicCredentialsAreInTheKey() {
      assertNotEquals(key(jsonQuery(basic("alice", "pw"))), key(jsonQuery(basic("bob", "pw"))));
      assertNotEquals(key(jsonQuery(basic("alice", "pw1"))), key(jsonQuery(basic("alice", "pw2"))));
   }

   @Test
   void requestMethodIsInTheKey() {
      RestJsonQuery get = jsonQuery(basic("alice", "pw"));
      RestJsonQuery post = jsonQuery(basic("alice", "pw"));
      post.setRequestType("POST");

      assertNotEquals(key(get), key(post));
   }

   @Test
   void postContentTypeIsInTheKey() {
      RestJsonQuery json = jsonQuery(ds -> {});
      json.setRequestType("POST");
      json.setContentType("application/json");
      RestJsonQuery form = jsonQuery(ds -> {});
      form.setRequestType("POST");
      form.setContentType("application/x-www-form-urlencoded");

      assertNotEquals(key(json), key(form));
   }

   @Test
   void queryTypeIsInTheKey() {
      RestXMLDataSource xmlDataSource = new RestXMLDataSource();
      xmlDataSource.setURL(url);
      RestXMLQuery xml = new RestXMLQuery();
      xml.setDataSource(xmlDataSource);

      assertNotEquals(key(jsonQuery(ds -> {})), key(xml),
         "a json and an xml query are sent with a different Accept header");
   }

   @Test
   void twoStepConfigurationIsInTheKey() {
      Consumer<RestJsonDataSource> base = ds -> {
         ds.setAuthType(AuthType.TWO_STEP);
         ds.setAuthURL("http://auth/token");
         ds.setAuthMethod(AuthMethod.POST);
         ds.setContentType("application/json");
         ds.setBody("{\"user\": \"alice\"}");
         ds.setTokenPattern("\"token\": \"(.*)\"");
      };

      String alice = key(jsonQuery(base));
      assertEquals(alice, key(jsonQuery(base)));
      assertNotEquals(alice, key(jsonQuery(base.andThen(ds -> ds.setBody("{\"user\": \"bob\"}")))));
      assertNotEquals(alice, key(jsonQuery(base.andThen(ds -> ds.setAuthURL("http://other/token")))));
      assertNotEquals(alice, key(jsonQuery(base.andThen(
         ds -> ds.setAuthenticationHttpParameters(
            new HttpParameter[] { secretHeader("bob-api-key") })))));
   }

   @Test
   void oauthTokensAreInTheKey() {
      Consumer<RestJsonDataSource> base = ds -> {
         ds.setAuthType(AuthType.OAUTH);
         ds.setClientId("client");
         ds.setTokenUri("http://auth/token");
         ds.setAccessToken("alice-token");
      };

      assertNotEquals(key(jsonQuery(base)),
                      key(jsonQuery(base.andThen(ds -> ds.setAccessToken("bob-token")))));
   }

   @Test
   void clientCredentialsAreInTheKey() {
      Consumer<RestJsonDataSource> base = ds -> {
         ds.setAuthType(AuthType.OAUTH_CLIENT_CREDENTIALS);
         ds.setClientId("client");
         ds.setClientSecret("secret1");
         ds.setTokenUri("http://auth/token");
      };

      assertNotEquals(key(jsonQuery(base)),
                      key(jsonQuery(base.andThen(ds -> ds.setClientSecret("secret2")))));
      assertNotEquals(key(jsonQuery(base)),
                      key(jsonQuery(base.andThen(ds -> ds.setScope("other-scope")))));
   }

   @Test
   void kerberosImpersonatedIdentityIsInTheKey() {
      Consumer<RestJsonDataSource> base = ds -> {
         ds.setAuthType(AuthType.KERBEROS);
         ds.setServicePrincipalName("HTTP/host");
         ds.setConstrainedDelegation(true);
         ds.setImpersonationType(KerberosImpersonationType.PRINCIPAL);
      };

      ThreadContext.setContextPrincipal(principal("user1"));
      String user1 = key(jsonQuery(base));
      ThreadContext.setContextPrincipal(principal("user2"));
      String user2 = key(jsonQuery(base));

      assertNotEquals(user1, user2);
   }

   @Test
   void authenticationTypeIsInTheKey() {
      Set<String> keys = new HashSet<>();

      for(AuthType type : AuthType.values()) {
         keys.add(key(jsonQuery(ds -> ds.setAuthType(type))));
      }

      assertEquals(AuthType.values().length, keys.size());
   }

   @Test
   void keyHasNoSecretsInClear() {
      RestJsonQuery basic = jsonQuery(ds -> {
         ds.setAuthType(AuthType.BASIC);
         ds.setUser("alice");
         ds.setPassword("alice-password");
         ds.setQueryHttpParameters(new HttpParameter[] { secretHeader("header-secret") });
      });
      RestJsonQuery client = jsonQuery(ds -> {
         ds.setAuthType(AuthType.OAUTH_CLIENT_CREDENTIALS);
         ds.setClientId("client");
         ds.setClientSecret("client-secret");
         ds.setTokenUri("http://auth/token");
      });
      RestJsonQuery oauth = jsonQuery(ds -> {
         ds.setAuthType(AuthType.OAUTH);
         ds.setAccessToken("access-token");
      });

      for(String secret : List.of("alice-password", "header-secret")) {
         assertFalse(key(basic).contains(secret), secret);
      }

      assertFalse(key(client).contains("client-secret"));
      assertFalse(key(oauth).contains("access-token"));
   }

   @Test
   void responseIsNotSharedBetweenCredentials() throws Exception {
      SreeEnv.setProperty(CACHE_TIMEOUT, "600000");
      CountingHttpHandler handler = new CountingHttpHandler();

      Object alice = execute(jsonQuery(basic("alice", "pw")), handler);
      Object bob = execute(jsonQuery(basic("bob", "pw")), handler);

      assertEquals(2, handler.calls, "bob's request must not be answered from alice's response");
      assertNotEquals(alice, bob);
   }

   @Test
   void responseIsNotSharedBetweenMethods() throws Exception {
      SreeEnv.setProperty(CACHE_TIMEOUT, "600000");
      CountingHttpHandler handler = new CountingHttpHandler();
      RestJsonQuery post = jsonQuery(basic("alice", "pw"));
      post.setRequestType("POST");

      execute(jsonQuery(basic("alice", "pw")), handler);
      execute(post, handler);

      assertEquals(2, handler.calls, "a POST must not be answered from a GET response");
   }

   @Test
   void identicalRequestIsAnsweredFromTheCache() throws Exception {
      SreeEnv.setProperty(CACHE_TIMEOUT, "600000");
      CountingHttpHandler handler = new CountingHttpHandler();

      Object first = execute(jsonQuery(basic("alice", "pw")), handler);
      Object second = execute(jsonQuery(basic("alice", "pw")), handler);

      assertEquals(1, handler.calls);
      assertEquals(first, second);
   }

   private Object execute(RestJsonQuery query, IHttpHandler handler) throws Exception {
      JsonUnpagedRestDataIteratorStrategy strategy = new JsonUnpagedRestDataIteratorStrategy(
         query, new JsonTransformer(), handler, new RestErrorHandler());
      assertTrue(strategy.hasNext());
      return String.valueOf(strategy.next());
   }

   private RestJsonQuery jsonQuery(Consumer<RestJsonDataSource> config) {
      RestJsonDataSource ds = new RestJsonDataSource();
      ds.setName("Rest1");
      ds.setURL(url);
      config.accept(ds);
      RestJsonQuery query = new RestJsonQuery();
      query.setDataSource(ds);
      return query;
   }

   private static Consumer<RestJsonDataSource> basic(String user, String password) {
      return ds -> {
         ds.setAuthType(AuthType.BASIC);
         ds.setUser(user);
         ds.setPassword(password);
      };
   }

   /**
    * Clears the property, so the response cache is off unless a test turns it on.
    */
   private static void forgetTheTimeout() {
      SreeEnv.remove(CACHE_TIMEOUT);
   }

   private static String key(AbstractRestQuery query) {
      return RestRequest.fromQuery(query).key();
   }

   private static HttpParameter secretHeader(String value) {
      return HttpParameter.builder()
         .type(HttpParameter.ParameterType.HEADER)
         .name("X-Api-Key")
         .value(value)
         .secret(true)
         .build();
   }

   private static Principal principal(String name) {
      return new SRPrincipal(
         new IdentityID(name, "orgA"), new IdentityID[0], new String[0], "orgA", 0L);
   }

   /**
    * Answers every request with a different response, so that a response served from the
    * cache can be told apart from a new one.
    */
   private static final class CountingHttpHandler implements IHttpHandler {
      @Override
      public HttpResponse executeRequest(RestRequest request) {
         calls++;
         return new TestHttpResponse("{\"call\": " + calls + "}");
      }

      @Override
      public void close() {
         // no-op
      }

      private int calls;
   }

   /**
    * Creates real credentials, so that the user, password, client and token properties of the
    * data sources are kept.
    */
   @Configuration
   static class CredentialConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(
            eq(CredentialType.PASSWORD_OAUTH2_WITH_FLAGS), anyBoolean()))
            .thenAnswer(invocation -> new LocalPasswordAndOAuth2WithFlagCredentialsGrant());
         when(credentialService.createCredential(CredentialType.PASSWORD_OAUTH2_WITH_FLAGS))
            .thenAnswer(invocation -> new LocalPasswordAndOAuth2WithFlagCredentialsGrant());
         return credentialService;
      }
   }

   private Principal oldPrincipal;
   private String url;

   private static final String CACHE_TIMEOUT = "rest.cache.timeout.millis";
}
