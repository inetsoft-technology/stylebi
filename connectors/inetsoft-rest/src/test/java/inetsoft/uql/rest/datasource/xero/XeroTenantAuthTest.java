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
import inetsoft.test.*;
import inetsoft.uql.rest.auth.RestAuthenticator;
import inetsoft.uql.rest.auth.RestAuthenticatorFactory;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Xero needs the tenant id of the organisation in the xero-tenant-id header. The data source
 * asks GET /connections (Xero OAuth 2.0 connections API) with the bearer token, reads the first
 * tenantId from the response and puts it in the header of the real request.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XeroTenantAuthTest {
   static class LocalXero extends XeroDataSource {
      LocalXero(String authUrl) {
         this.authUrl = authUrl;
      }

      @Override
      public String getAuthURL() {
         return authUrl;
      }

      private final String authUrl;
   }

   @BeforeEach
   void start() throws Exception {
      client = HttpAsyncClients.createDefault();
      client.start();
      authHeaders.clear();
      status = 200;
      body = "[{\"id\":\"c1\",\"tenantId\":\"tenant-111\",\"tenantType\":\"ORGANISATION\"}]";
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/connections", exchange -> {
         authHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
         byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
         exchange.sendResponseHeaders(status, bytes.length);

         try(OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
         }
      });
      server.start();
   }

   @AfterEach
   void stop() throws IOException {
      server.stop(0);
      client.close();
   }

   private RestAuthenticator authenticator() {
      LocalXero ds = new LocalXero("http://127.0.0.1:" + server.getAddress().getPort() +
                                   "/connections");
      ds.setName("xero-" + System.nanoTime());
      ds.setAccessToken("xr-access");
      ds.setRefreshToken("xr-refresh");
      ds.setTokenExpiration(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
      return RestAuthenticatorFactory.createFrom(ds, client);
   }

   private HttpGet authenticated() throws Exception {
      HttpGet request = new HttpGet("http://127.0.0.1:" + server.getAddress().getPort() + "/api");
      authenticator().authenticateRequest(request, HttpClientContext.create());
      return request;
   }

   @Test
   void tenantIdFromTheConnectionsResponseIsSentWithTheBearerToken() throws Exception {
      HttpGet request = authenticated();

      assertEquals(List.of("Bearer xr-access"), authHeaders);
      assertEquals("tenant-111", request.getFirstHeader("xero-tenant-id").getValue());
      assertEquals("Bearer xr-access", request.getFirstHeader("Authorization").getValue());
   }

   @Test
   void theFirstOfSeveralOrganisationsIsUsed() throws Exception {
      body = "[{\"tenantId\":\"tenant-111\"},{\"tenantId\":\"tenant-222\"}]";
      assertEquals("tenant-111", authenticated().getFirstHeader("xero-tenant-id").getValue());
   }

   @Test
   void failedConnectionsRequestIsReported() {
      status = 401;
      body = "{}";
      IOException e = assertThrows(IOException.class, this::authenticated);
      assertTrue(e.getMessage().contains("Authentication request was not successful"),
                 e.getMessage());
   }

   @Test
   void responseWithoutATenantIdIsReportedWithTheBody() {
      body = "[]";
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, this::authenticated);
      assertTrue(e.getMessage().contains("[]"), e.getMessage());
   }

   @Test
   @Disabled("Bug #82011: the first tenant id is used whatever its type; enable when it is fixed")
   void firstConnectionThatIsNotAnOrganisationIsSkipped() throws Exception {
      body = "[{\"tenantId\":\"practice-1\",\"tenantType\":\"PRACTICE\"}," +
         "{\"tenantId\":\"org-2\",\"tenantType\":\"ORGANISATION\"}]";
      assertEquals("org-2", authenticated().getFirstHeader("xero-tenant-id").getValue());
   }

   private CloseableHttpAsyncClient client;
   private HttpServer server;
   private final List<String> authHeaders = new ArrayList<>();
   private volatile int status = 200;
   private volatile String body;
}
