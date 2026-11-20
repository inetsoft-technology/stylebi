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

package inetsoft.uql.rest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.EndpointsTestConfig;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.json.RestJsonDataSource;
import inetsoft.uql.rest.json.RestJsonQuery;
import inetsoft.uql.rest.json.RestJsonRuntime;
import inetsoft.uql.tabular.HttpParameter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the real {@link HttpHandler} against a server on the loopback interface, which the
 * scripted handler of the replay tests bypasses: the headers, parameters and body that really go
 * out, and what the handler does with a response that is not successful.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
class HttpHandlerTest {
   @BeforeEach
   void startServer() throws IOException {
      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      server.createContext("/", exchange -> {
         received.add(describe(exchange));
         handler.accept(exchange);
      });
      server.start();
   }

   @AfterEach
   void stopServer() {
      server.stop(0);
   }

   @Test
   void aGetSendsTheDefaultHeadersAndReturnsTheBodyAndHeaders() throws Exception {
      respond(200, "{\"ok\":true}", "X-Total", "7");

      try(HttpHandler http = new HttpHandler()) {
         HttpResponse response = http.executeRequest(RestRequest.fromQuery(query("/things?a=1")));

         assertEquals(200, response.getResponseStatusCode());
         assertEquals("{\"ok\":true}", read(response.getResponseBodyAsStream()));
         assertEquals("7", response.getResponseHeaderValue("X-Total"));
      }

      Received request = received.get(0);
      assertEquals("GET", request.method());
      assertEquals("/things?a=1", request.target());
      assertEquals("Mozilla/4.76", request.header("User-Agent"));
      assertEquals("application/json", request.header("Accept"));
   }

   @Test
   void theAcceptHeaderIsTheOneTheHandlerWasCreatedWith() throws Exception {
      respond(200, "<a/>");

      try(HttpHandler http = new HttpHandler("application/xml")) {
         http.executeRequest(RestRequest.fromQuery(query("/x")));
      }

      assertEquals("application/xml", received.get(0).header("Accept"));
   }

   @Test
   void dataSourceHeadersAndQueryParametersAreSent() throws Exception {
      respond(200, "{}");
      RestJsonQuery query = query("/things");
      ((RestJsonDataSource) query.getDataSource()).setQueryHttpParameters(new HttpParameter[] {
         HttpParameter.builder().type(HttpParameter.ParameterType.HEADER)
            .name("X-Api-Key").value("secret").build(),
         HttpParameter.builder().type(HttpParameter.ParameterType.QUERY)
            .name("lang").value("en").build() });

      try(HttpHandler http = new HttpHandler()) {
         http.executeRequest(RestRequest.fromQuery(query));
      }

      assertEquals("secret", received.get(0).header("X-Api-Key"));
      assertTrue(received.get(0).target().contains("lang=en"), received.get(0).target());
   }

   @Test
   void aPostSendsItsBodyAndContentType() throws Exception {
      respond(200, "[]");
      RestJsonQuery query = query("/search");
      query.setRequestType("POST");
      query.setContentType("application/json");
      query.setRequestBody("{\"q\":\"x\"}");

      try(HttpHandler http = new HttpHandler()) {
         http.executeRequest(RestRequest.fromQuery(query));
      }

      Received request = received.get(0);
      assertEquals("POST", request.method());
      assertEquals("{\"q\":\"x\"}", request.body());
      assertTrue(request.header("Content-Type").startsWith("application/json"),
                 request.header("Content-Type"));
   }

   @Test
   void aPostWithoutABodySendsAnEmptyOne() throws Exception {
      respond(200, "[]");
      RestJsonQuery query = query("/search");
      query.setRequestType("POST");
      query.setContentType("application/json");

      try(HttpHandler http = new HttpHandler()) {
         http.executeRequest(RestRequest.fromQuery(query));
      }

      assertEquals("", received.get(0).body());
   }

   @Test
   void aServerErrorIsReportedWithItsStatusAndBody() throws Exception {
      respond(500, "database is down");

      try(HttpHandler http = new HttpHandler()) {
         RestResponseException e = assertThrows(RestResponseException.class,
            () -> http.executeRequest(RestRequest.fromQuery(query("/things"))));

         assertEquals(500, e.getResponse().getResponseStatusCode());
         assertTrue(e.getMessage().contains("500"), e.getMessage());
         assertTrue(e.getMessage().contains("/things"), e.getMessage());
         assertTrue(e.getMessage().contains("database is down"), e.getMessage());
      }
   }

   @Test
   void everyStatusOutsideTwoHundredIsAnError() throws Exception {
      try(HttpHandler http = new HttpHandler()) {
         // 300 is the first status past the successful range; the one below 200 (1xx) cannot be
         // sent by this server, so that end of the range is not tested
         for(int status : new int[] { 300, 400, 401, 403, 404, 429, 503 }) {
            respond(status, "no");
            RestResponseException e = assertThrows(RestResponseException.class,
               () -> http.executeRequest(RestRequest.fromQuery(query("/things"))), "" + status);
            assertEquals(status, e.getResponse().getResponseStatusCode());
         }
      }
   }

   @Test
   void everyTwoHundredStatusIsSuccessful() throws Exception {
      try(HttpHandler http = new HttpHandler()) {
         for(int status : new int[] { 200, 201, 202 }) {
            respond(status, "{}");
            assertEquals(status, http.executeRequest(RestRequest.fromQuery(query("/things")))
               .getResponseStatusCode());
         }
      }
   }

   @Test
   void aServerThatIsNotListeningFailsWithAnIoException() throws Exception {
      RestJsonQuery query = query("/things");
      server.stop(0);

      try(HttpHandler http = new HttpHandler()) {
         assertThrows(IOException.class, () -> http.executeRequest(RestRequest.fromQuery(query)));
      }

      // start another one so that the stop in the @AfterEach has something to stop
      startServer();
   }

   @Test
   void aWholeQueryRunsThroughTheRealHandlerAndRuntime() throws Exception {
      respond(200, "{\"data\":[{\"id\":1},{\"id\":2}]}");
      RestJsonQuery query = query("/items");
      query.setJsonPath("$.data");

      XTableNode table = new RestJsonRuntime().runQuery(query, new VariableTable());

      assertNotNull(table);
      int rows = 0;

      while(table.next()) {
         rows++;
      }

      assertEquals(2, rows);
      assertEquals("/items", received.get(0).target());
   }

   @Test
   void aQueryAgainstAFailingServerReturnsNoRowsInsteadOfThrowing() throws Exception {
      respond(503, "busy");
      RestJsonQuery query = query("/items");

      XTableNode table = new RestJsonRuntime().runQuery(query, new VariableTable());

      assertTrue(table == null || !table.next());
   }

   private RestJsonQuery query(String suffix) {
      RestJsonDataSource dataSource = new RestJsonDataSource();
      dataSource.setURL("http://127.0.0.1:" + server.getAddress().getPort());
      RestJsonQuery query = new RestJsonQuery();
      query.setDataSource(dataSource);
      query.setSuffix(suffix);
      query.setJsonPath("$");
      return query;
   }

   private void respond(int status, String body, String... headers) {
      handler = exchange -> {
         try {
            for(int i = 0; i < headers.length; i += 2) {
               exchange.getResponseHeaders().add(headers[i], headers[i + 1]);
            }

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
         }
         catch(IOException e) {
            throw new IllegalStateException(e);
         }
         finally {
            exchange.close();
         }
      };
   }

   private static Received describe(HttpExchange exchange) {
      Map<String, String> headers = new HashMap<>();
      exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));

      try {
         String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
         return new Received(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                             headers, body);
      }
      catch(IOException e) {
         throw new IllegalStateException(e);
      }
   }

   private static String read(InputStream input) throws IOException {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
   }

   private record Received(String method, String target, Map<String, String> headers,
                           String body)
   {
      String header(String name) {
         return headers.get(name.toLowerCase());
      }
   }

   private HttpServer server;
   private volatile Consumer<HttpExchange> handler = HttpExchange::close;
   private final List<Received> received = new CopyOnWriteArrayList<>();
}
