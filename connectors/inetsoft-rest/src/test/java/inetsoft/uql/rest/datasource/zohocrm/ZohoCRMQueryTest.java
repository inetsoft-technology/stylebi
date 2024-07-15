/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.datasource.zohocrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import inetsoft.test.EndpointsSource;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.json.RestJsonRuntime;
import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.web.util.UriComponentsBuilder;

import java.awt.*;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SreeHome
class ZohoCRMQueryTest {

   private static final String clientId = System.getenv("ZOHOCRM_CLIENT_ID");
   private static final String clientSecret = System.getenv("ZOHOCRM_CLIENT_SECRET");
   private static final String scope = "ZohoCRM.modules.all,ZohoCRM.users.all,ZohoCRM.org.all,ZohoCRM.settings.all,ZohoCRM.notifications.read";
   private static final String accountDomain = System.getenv("ZOHOCRM_ACCOUNT_DOMAIN");
   private static String accessToken;
   private static String refreshToken;
   private static long tokenExpiration;
   private static String apiDomain;

   @BeforeAll
   static void authorize() throws Exception {
      if(!Desktop.isDesktopSupported()) {
         throw new Exception("Tests requiring OAuth must not be run headless");
      }

      HttpServer server = HttpServer.create(new InetSocketAddress(18888), 0);
      String authorizationCode;

      try {
         CompletableFuture<String> future = new CompletableFuture<>();
         server.createContext("/", httpExchange -> {
            URI uri = httpExchange.getRequestURI();
            String query = uri.getQuery();

            if(query != null && !query.isEmpty()) {
               if(query.charAt(0) == '?') {
                  query = query.substring(1);
               }

               for(String pair : query.split("&")) {
                  int index = pair.indexOf('=');

                  if(index >= 0) {
                     String name = pair.substring(0, index);
                     String value = pair.substring(index + 1);

                     if("code".equals(name)) {
                        future.complete(value);
                        break;
                     }
                  }
               }
            }

            byte[] response = "You can close this window now.".getBytes(StandardCharsets.UTF_8);
            httpExchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            httpExchange.sendResponseHeaders(200, response.length);

            try(OutputStream out = httpExchange.getResponseBody()) {
               out.write(response);
            }
         });
         server.start();

         URI uri = UriComponentsBuilder.fromUri(URI.create("https://accounts.zoho.com/oauth/v2/auth"))
            .queryParam("scope", scope)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", "http://localhost:18888/oauth")
            .queryParam("response_type", "code")
            .queryParam("access_type", "offline")
            .build(new Object[0]);
         Desktop.getDesktop().browse(uri);
         authorizationCode = future.get(1L, TimeUnit.MINUTES);
      }
      finally {
         server.stop(2);
      }

      HttpPost request = new HttpPost(accountDomain + "/oauth/v2/token");
      List<NameValuePair> form = Arrays.asList(
         new BasicNameValuePair("grant_type", "authorization_code"),
         new BasicNameValuePair("client_id", clientId),
         new BasicNameValuePair("client_secret", clientSecret),
         new BasicNameValuePair("redirect_uri", "http://localhost:18888/oauth"),
         new BasicNameValuePair("code", authorizationCode));
      UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, Consts.UTF_8);
      request.setEntity(entity);

      try(CloseableHttpClient client = HttpClients.createDefault();
          CloseableHttpResponse response = client.execute(request))
      {
         int status = response.getStatusLine().getStatusCode();

         if(status == 200) {
            String content = EntityUtils.toString(response.getEntity());
            System.err.println(content);
            JsonNode json = new ObjectMapper().readTree(content);
            accessToken = json.get("access_token").asText();
            apiDomain = json.get("api_domain").asText();
            Duration duration = Duration.of(json.get("expires_in").asLong(), ChronoUnit.SECONDS);
            Instant instant = Instant.now().plus(duration);
            tokenExpiration = instant.toEpochMilli();

            if(json.has("refresh_token")) {
               refreshToken = json.get("refresh_token").asText();
            }
         }
         else {
            throw new Exception(
               "Failed to get access token [" + status + "]: " +
                  response.getStatusLine().getReasonPhrase());
         }
      }
   }

   @ParameterizedTest(name = "{0}")
   @EndpointsSource(dataSource = ZohoCRMDataSource.class, query = ZohoCRMQuery.class)
   @Tag("endpoints")
   void testRunQuery(@SuppressWarnings("unused") String endpoint, ZohoCRMQuery query) {
      ZohoCRMDataSource dataSource = (ZohoCRMDataSource) query.getDataSource();
      dataSource.setClientId(clientId);
      dataSource.setClientSecret(clientSecret);
      dataSource.setAccountDomain(accountDomain);
      dataSource.setAccessToken(accessToken);
      dataSource.setRefreshToken(refreshToken);
      dataSource.setTokenExpiration(tokenExpiration);
      dataSource.setURL(apiDomain);

      RestJsonRuntime runtime = new RestJsonRuntime();
      XTableNode results = runtime.runQuery(query, new VariableTable());
      assertNotNull(results);
      int rowCount = 0;

      while(results.next()) {
         ++rowCount;
      }

      assertTrue(rowCount > 0);
      accessToken = dataSource.getAccessToken();
      refreshToken = dataSource.getRefreshToken();
      tokenExpiration = dataSource.getTokenExpiration();
      apiDomain = dataSource.getURL();
   }
}
