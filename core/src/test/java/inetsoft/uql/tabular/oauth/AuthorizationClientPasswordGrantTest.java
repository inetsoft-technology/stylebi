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
package inetsoft.uql.tabular.oauth;

/*
 * Bug #78243: the token URI of a password grant is sent by the client, so the server only posts
 * the credentials to an HTTP(S) token URI and never follows a redirect to another address.
 */

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class AuthorizationClientPasswordGrantTest {
   private HttpServer server;
   private String base;
   private final List<String> requests = new CopyOnWriteArrayList<>();

   @BeforeEach
   void setUp() throws IOException {
      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      server.createContext("/token", exchange -> {
         requests.add(exchange.getRequestMethod() + " /token " +
                         new String(exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
         byte[] body = "{\"access_token\":\"access\",\"expires_in\":3600}"
            .getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "application/json");
         exchange.sendResponseHeaders(200, body.length);

         try(OutputStream out = exchange.getResponseBody()) {
            out.write(body);
         }
      });
      server.createContext("/redirect307", exchange -> redirect(exchange, 307));
      server.createContext("/redirect302", exchange -> redirect(exchange, 302));
      server.start();
      base = "http://" + server.getAddress().getHostString() + ":" +
         server.getAddress().getPort();
   }

   @AfterEach
   void tearDown() {
      server.stop(0);
   }

   @Test
   void postsCredentialsToHttpTokenUri() {
      Tokens tokens = AuthorizationClient.doPasswordGrantAuth(
         "user", "secret", null, null, null, base + "/token");

      assertEquals("access", tokens.accessToken());
      assertEquals(1, requests.size());
      assertTrue(requests.get(0).startsWith("POST /token "));
      assertTrue(requests.get(0).contains("password=secret"));
   }

   @Test
   void temporaryRedirectIsNotFollowed() {
      RuntimeException ex = assertThrows(
         RuntimeException.class,
         () -> AuthorizationClient.doPasswordGrantAuth(
            "user", "secret", null, null, null, base + "/redirect307"));

      assertTrue(ex.getMessage().contains("307"), ex.getMessage());
      assertTrue(requests.isEmpty(), "the credentials must not be posted to the redirect target");
   }

   @Test
   void foundRedirectIsNotFollowed() {
      RuntimeException ex = assertThrows(
         RuntimeException.class,
         () -> AuthorizationClient.doPasswordGrantAuth(
            "user", "secret", null, null, null, base + "/redirect302"));

      assertTrue(ex.getMessage().contains("302"), ex.getMessage());
      assertTrue(requests.isEmpty(), "the redirect target must not be requested");
   }

   @Test
   void nonHttpTokenUriIsRejected() {
      for(String uri : List.of("file:///etc/passwd", "ftp://127.0.0.1/token",
                               "jar:file:/tmp/a.jar!/token", "localhost:8080/token"))
      {
         assertThrows(IllegalArgumentException.class,
                      () -> AuthorizationClient.doPasswordGrantAuth(
                         "user", "secret", null, null, null, uri), uri);
      }
   }

   private void redirect(com.sun.net.httpserver.HttpExchange exchange, int code)
      throws IOException
   {
      exchange.getRequestBody().readAllBytes();
      exchange.getResponseHeaders().add("Location", base + "/token");
      exchange.sendResponseHeaders(code, -1);
      exchange.close();
   }
}
