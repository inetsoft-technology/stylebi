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
package inetsoft.uql.rest.datasource.xero;

import com.sun.net.httpserver.HttpServer;
import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.auth.RestAuthenticator;
import inetsoft.uql.rest.auth.RestAuthenticatorFactory;
import inetsoft.uql.rest.json.RestJsonDataSource;
import inetsoft.uql.tabular.HttpParameter;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82011: the xero-tenant-id header must be the tenant id of an organisation connection, not
 * the tenant id of whichever connection the Xero connections endpoint lists first. The real
 * authenticator gets the connections from a local HTTP server.
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class XeroOrganisationTenantTest {
   @BeforeAll
   static void startServer() throws IOException {
      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      server.createContext("/connections", exchange -> {
         final byte[] bytes = connections.getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "application/json");
         exchange.sendResponseHeaders(200, bytes.length);

         try(OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
         }
      });
      server.start();
   }

   @AfterAll
   static void stopServer() {
      server.stop(0);
   }

   @BeforeEach
   void createClient() {
      client = HttpAsyncClients.createDefault();
      client.start();
   }

   @AfterEach
   void closeClient() throws IOException {
      client.close();
   }

   @Test
   void organisationIsUsedWhenAPracticeIsListedFirst() throws Exception {
      assertEquals("org-2", getTenantId(
         "[{\"tenantId\":\"practice-1\",\"tenantType\":\"PRACTICE\"}," +
         "{\"tenantId\":\"org-2\",\"tenantType\":\"ORGANISATION\"}]"));
   }

   @Test
   void firstOrganisationIsStillUsedWhenItIsListedFirst() throws Exception {
      assertEquals("org-1", getTenantId(
         "[{\"tenantId\":\"org-1\",\"tenantType\":\"ORGANISATION\"}," +
         "{\"tenantId\":\"practice-2\",\"tenantType\":\"PRACTICE\"}]"));
   }

   @Test
   void tenantTypeBeforeTenantId() throws Exception {
      assertEquals("org-2", getTenantId(
         "[{\"tenantType\":\"PRACTICE\",\"tenantId\":\"practice-1\"}," +
         "{\"tenantType\":\"ORGANISATION\",\"tenantId\":\"org-2\"}]"));
   }

   @Test
   void prettyPrintedConnections() throws Exception {
      assertEquals("org-2", getTenantId(
         "[\n" +
         "  {\n" +
         "    \"id\": \"conn-1\",\n" +
         "    \"tenantId\": \"practice-1\",\n" +
         "    \"tenantName\": \"Practice\",\n" +
         "    \"tenantType\": \"PRACTICE\"\n" +
         "  },\n" +
         "  {\n" +
         "    \"id\": \"conn-2\",\n" +
         "    \"tenantId\": \"org-2\",\n" +
         "    \"tenantName\": \"Org\",\n" +
         "    \"tenantType\": \"ORGANISATION\"\n" +
         "  }\n" +
         "]"));
   }

   @Test
   void organisationNameWithBracesAmongSeveralOrganisations() throws Exception {
      assertEquals("org-2", getTenantId(
         "[{\"tenantId\":\"practice-1\",\"tenantName\":\"Practice\",\"tenantType\":\"PRACTICE\"}," +
         "{\"tenantId\":\"org-2\",\"tenantName\":\"Acme {NZ} Ltd\",\"tenantType\":\"ORGANISATION\"}," +
         "{\"tenantId\":\"org-3\",\"tenantName\":\"Other\",\"tenantType\":\"ORGANISATION\"}]"));
   }

   @Test
   void firstTenantIsUsedWhenNoConnectionHasATenantType() throws Exception {
      assertEquals("tenant-1", getTenantId(
         "[{\"tenantId\":\"tenant-1\"},{\"tenantId\":\"tenant-2\"}]"));
   }

   @Test
   void onlyAPracticeIsReportedClearly() {
      final IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> getTenantId("[{\"tenantId\":\"practice-1\",\"tenantType\":\"PRACTICE\"}]"));
      assertTrue(e.getMessage().contains("No Xero organisation is connected"), e.getMessage());
      assertTrue(e.getMessage().contains("PRACTICE"), e.getMessage());
      assertFalse(e.getMessage().contains("practice-1"), e.getMessage());
   }

   @Test
   void noConnectionIsReportedClearly() {
      final IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> getTenantId("[]"));
      assertTrue(e.getMessage().contains("No Xero organisation is connected"), e.getMessage());
   }

   @Test
   void tenantTypeIsMatchedIgnoringCaseAndAnOrganisationWithoutATenantIdIsSkipped()
      throws Exception
   {
      assertEquals("org-3", getTenantId(
         "[{\"tenantId\":\"practice-1\",\"tenantType\":\"PRACTICE\"}," +
         "{\"tenantId\":\"\",\"tenantType\":\"ORGANISATION\"}," +
         "{\"tenantId\":\"org-3\",\"tenantType\":\"Organisation\"}]"));
   }

   @Test
   void genericTwoStepSourceStillUsesTheFirstMatchOfItsTokenPattern() throws Exception {
      connections =
         "[{\"tenantId\":\"practice-1\",\"tenantType\":\"PRACTICE\"}," +
         "{\"tenantId\":\"org-2\",\"tenantType\":\"ORGANISATION\"}]";
      final RestJsonDataSource ds = new RestJsonDataSource();
      ds.setAuthType(AuthType.TWO_STEP);
      ds.setAuthURL("http://127.0.0.1:" + server.getAddress().getPort() + "/connections");
      ds.setTokenPattern("\"tenantId\":\"(.*?)\"");
      ds.setQueryHttpParameters(new HttpParameter[] {
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("tenant")
            .value("{{token}}")
            .build()
      });

      final RestAuthenticator authenticator = RestAuthenticatorFactory.createFrom(ds, client);
      final HttpGet request = new HttpGet("https://example.com/api");
      authenticator.authenticateRequest(request, HttpClientContext.create());

      final Header header = request.getFirstHeader("tenant");
      assertNotNull(header);
      assertEquals("practice-1", header.getValue());
   }

   private String getTenantId(String body) throws Exception {
      connections = body;
      final LocalXeroDataSource ds = new LocalXeroDataSource(
         "http://127.0.0.1:" + server.getAddress().getPort() + "/connections");
      ds.setAccessToken("access-token");

      final RestAuthenticator authenticator = RestAuthenticatorFactory.createFrom(ds, client);
      final HttpGet request = new HttpGet("https://api.xero.com/api.xro/2.0/Invoices");
      authenticator.authenticateRequest(request, HttpClientContext.create());

      final Header header = request.getFirstHeader("xero-tenant-id");
      assertNotNull(header);
      return header.getValue();
   }

   private static final class LocalXeroDataSource extends XeroDataSource {
      LocalXeroDataSource(String authURL) {
         this.authURL = authURL;
      }

      @Override
      public String getAuthURL() {
         return authURL;
      }

      private final String authURL;
   }

   private static HttpServer server;
   private static volatile String connections = "[]";
   private CloseableHttpAsyncClient client;
}
