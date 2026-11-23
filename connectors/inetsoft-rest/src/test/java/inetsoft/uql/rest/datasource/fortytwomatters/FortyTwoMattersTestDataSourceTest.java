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
package inetsoft.uql.rest.datasource.fortytwomatters;

import com.sun.net.httpserver.HttpServer;
import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Test Data Source button of 42matters. The data source's URL is fixed, so a subclass that
 * points at a local server is used; the runtime only reads {@code getURL()}.
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class FortyTwoMattersTestDataSourceTest {
   static class LocalSource extends FortyTwoMattersDataSource {
      LocalSource(String url) {
         this.url = url;
      }

      @Override
      public String getURL() {
         return url;
      }

      private final String url;
   }

   @BeforeEach
   void startServer() throws Exception {
      hits.set(0);
      lastPath = null;
      lastQuery = null;
      status = 200;
      body = "{\"status\":\"OK\"}";
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
         hits.incrementAndGet();
         lastPath = exchange.getRequestURI().getPath();
         lastQuery = exchange.getRequestURI().getRawQuery();
         byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
         exchange.sendResponseHeaders(status, bytes.length);

         try(OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
         }
      });
      server.start();
   }

   @AfterEach
   void stopServer() {
      server.stop(0);
   }

   private LocalSource source(String token) {
      LocalSource ds = new LocalSource("http://127.0.0.1:" + server.getAddress().getPort());
      ds.setName("42m-" + System.nanoTime());
      ds.setAccessToken(token);
      return ds;
   }

   private void test(LocalSource ds) throws Exception {
      new FortyTwoMattersRuntime().testDataSource(ds, new VariableTable());
   }

   @Test
   void callsTheAccountEndpointWithTheTokenAsAQueryParameter() throws Exception {
      test(source("tok123"));
      assertEquals(1, hits.get());
      assertEquals("/v2.0/account.json", lastPath);
      assertEquals("access_token=tok123", lastQuery);
   }

   @Test
   void tokenWithSpecialCharactersIsUrlEncoded() throws Exception {
      String token = "a&b=c+d%e f#g/h?ié";
      test(source(token));
      assertEquals(1, hits.get());
      assertEquals(1, lastQuery.split("&").length, lastQuery);
      assertEquals(token, URLDecoder.decode(lastQuery.substring("access_token=".length()),
                                            StandardCharsets.UTF_8));
   }

   @Test
   void accountStatusOtherThanOkFails() {
      body = "{\"status\":\"SUSPENDED\"}";
      Exception e = assertThrows(Exception.class, () -> test(source("t")));
      assertTrue(e.getMessage().contains("SUSPENDED"), e.getMessage());
   }

   @Test
   void missingStatusFails() {
      body = "{}";
      assertThrows(Exception.class, () -> test(source("t")));
   }

   @Test
   void httpErrorFailsWithTheStatusCode() {
      status = 401;
      body = "{\"status\":\"OK\"}";
      IOException e = assertThrows(IOException.class, () -> test(source("t")));
      assertTrue(e.getMessage().contains("401"), e.getMessage());
   }

   @Test
   void emptyTokenFailsWithoutARequest() {
      assertThrows(IllegalStateException.class, () -> test(source("")));
      assertEquals(0, hits.get());
   }

   private HttpServer server;
   private final AtomicInteger hits = new AtomicInteger();
   private volatile String lastPath;
   private volatile String lastQuery;
   private volatile int status = 200;
   private volatile String body;
}
