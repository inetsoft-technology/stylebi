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

package inetsoft.uql.rest.datasource;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.EndpointsTestConfig;
import inetsoft.test.ScriptedHttpHandler;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.RestJsonDataSource;
import inetsoft.uql.rest.json.RestJsonQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The response cache of the REST connector, which is on only while "rest.cache.timeout.millis" is
 * set. It keeps the answer of a request under a key made of the URL, the parameters, the data
 * source's HTTP parameters and the body. The tests ask what the key leaves out: a request with other
 * credentials, or with another method, must not be served the answer to somebody else's request.
 * Every test uses its own URL, because the cache is shared by the whole JVM.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
class ResponseCacheReplayTest {
   @BeforeEach
   void turnTheCacheOn() throws Exception {
      try {
         forgetTheTimeout();
         SreeEnv.setProperty(TIMEOUT, "600000");
      }
      catch(Exception | Error e) {
         // @AfterEach still runs when this fails, but make sure nothing stays set
         SreeEnv.remove(TIMEOUT);
         throw e;
      }
   }

   @AfterEach
   void turnTheCacheOff() throws Exception {
      // the cache would stay on for every other class that runs in this JVM
      forgetTheTimeout();
   }

   @Test
   void theSameRequestIsAnsweredFromTheCache() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("same.test", "[{\"id\":1}]");

      XTableNode first = run(query("https://same.test", "/items"), http);
      XTableNode second = run(query("https://same.test", "/items"), http);

      assertNotNull(first);
      assertNotNull(second);
      assertEquals(1, http.requests().size(), http.requests().toString());
      assertTrue(second.next(), "the cached response gave no rows");
   }

   @Test
   void aDifferentUrlOrParameterIsNotAnsweredFromTheCache() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("other.test", "[{\"id\":1}]");

      run(query("https://other.test", "/items?page=1"), http);
      run(query("https://other.test", "/items?page=2"), http);
      run(query("https://other.test", "/things?page=1"), http);

      assertEquals(3, http.requests().size(), http.requests().toString());
   }

   @Test
   void aRequestWithOtherCredentialsIsNotAnsweredWithTheCachedResponseOfSomeoneElse()
      throws Exception
   {
      // the Authorization header is added by the HttpHandler, after the key was made
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("creds.test", "[{\"id\":1}]");

      run(basicQuery("https://creds.test", "/items", "alice"), http);
      run(basicQuery("https://creds.test", "/items", "bob"), http);

      assertEquals(2, http.requests().size(),
                   "bob was served the cached response that was made for alice: " +
                   http.requests());
   }

   @Test
   void aPostIsNotAnsweredWithTheCachedResponseOfAGet() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("method.test", "[{\"id\":1}]");
      RestJsonQuery post = query("https://method.test", "/items");
      post.setRequestType("POST");
      post.setContentType("application/json");

      run(query("https://method.test", "/items"), http);
      run(post, http);

      assertEquals(2, http.requests().size(),
                   "the POST was served the cached response of the GET: " + http.requests());
   }

   /**
    * Clears the property and the parsed value that SreeEnv keeps for it: {@code getLong} keeps the
    * first value it parsed, so a later change of the property is not seen (Bug #81932).
    */
   private static void forgetTheTimeout() throws Exception {
      SreeEnv.remove(TIMEOUT);

      try {
         Field field = SreeEnv.class.getDeclaredField("cache");
         field.setAccessible(true);
         ((Map<?, ?>) field.get(null)).remove(TIMEOUT);
      }
      catch(NoSuchFieldException | ClassCastException e) {
         // SreeEnv keeps its own copy of the property, so the cache cannot be switched off without it
         throw new AssertionError("SreeEnv no longer has a private static Map field 'cache'; " +
                                  "update forgetTheTimeout(), which resets the REST response " +
                                  "cache timeout (see Bug #81932)", e);
      }
   }

   private static RestJsonQuery basicQuery(String url, String suffix, String user) {
      RestJsonQuery query = query(url, suffix);
      RestJsonDataSource dataSource = (RestJsonDataSource) query.getDataSource();
      dataSource.setAuthType(AuthType.BASIC);
      dataSource.setUser(user);
      dataSource.setPassword("secret");
      return query;
   }

   private static RestJsonQuery query(String url, String suffix) {
      RestJsonDataSource dataSource = new RestJsonDataSource();
      dataSource.setURL(url);
      RestJsonQuery query = new RestJsonQuery();
      query.setDataSource(dataSource);
      query.setSuffix(suffix);
      query.setJsonPath("$");
      return query;
   }

   private static XTableNode run(RestJsonQuery query, ScriptedHttpHandler http) {
      XTableNode table = new ScriptedHttpHandler.RestJsonReplayRuntime(http)
         .runQuery(query, new VariableTable());
      http.assertNoFailures();
      return table;
   }

   private static final String TIMEOUT = "rest.cache.timeout.millis";
}
