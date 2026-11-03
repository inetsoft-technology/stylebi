/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.assistant;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import javax.net.ssl.*;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Real TLS handshakes through the HttpClient built by {@code applyAssistantTls}, against a
 * self-signed certificate whose SAN does not list the host used to connect (Bug #80091).
 */
@Tag("core")
class AssistantTlsHandshakeTest {
   private static HttpsServer server;
   private static int port;
   private MockedStatic<SreeEnv> sreeEnvStatic;

   @BeforeAll
   static void startServer(@TempDir Path dir) throws Exception {
      Path ks = dir.resolve("ks.p12");
      String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
      Process p = new ProcessBuilder(keytool, "-genkeypair", "-alias", "t", "-keyalg", "RSA",
                                     "-keysize", "2048", "-dname", "CN=example.invalid",
                                     "-ext", "san=dns:example.invalid", "-storetype", "PKCS12",
                                     "-keystore", ks.toString(), "-storepass", "changeit",
                                     "-validity", "2")
         .redirectErrorStream(true).start();
      p.getInputStream().readAllBytes();
      assertEquals(0, p.waitFor());

      KeyStore store = KeyStore.getInstance("PKCS12");

      try(InputStream in = Files.newInputStream(ks)) {
         store.load(in, "changeit".toCharArray());
      }

      KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(store, "changeit".toCharArray());
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(kmf.getKeyManagers(), null, null);

      server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      server.setHttpsConfigurator(new HttpsConfigurator(ctx));
      server.createContext("/", ex -> {
         byte[] body = "ok".getBytes();
         ex.sendResponseHeaders(200, body.length);
         ex.getResponseBody().write(body);
         ex.close();
      });
      server.start();
      port = server.getAddress().getPort();
   }

   @AfterAll
   static void stopServer() {
      server.stop(0);
   }

   @BeforeEach
   void setUp() {
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
   }

   private HttpResponse<String> get(String host, String verify) throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("chat.app.server.ssl.verify", "false"))
         .thenReturn(verify);
      HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(
         java.time.Duration.ofSeconds(10));
      AIAssistantController.applyAssistantTls(builder, "test");
      return builder.build().send(
         HttpRequest.newBuilder(URI.create("https://" + host + ":" + port + "/"))
            .timeout(java.time.Duration.ofSeconds(10)).build(),
         HttpResponse.BodyHandlers.ofString());
   }

   @Test
   void verifyDisabledAcceptsCertificateWhoseSanDoesNotMatchTheHost() throws Exception {
      assertEquals("ok", get("localhost", "false").body());
   }

   @Test
   void verifyDisabledAcceptsCertificateWhoseSanDoesNotMatchAnIpHost() throws Exception {
      assertEquals("ok", get("127.0.0.1", "false").body());
   }

   @Test
   void verifyEnabledStillRejectsTheSelfSignedCertificate() {
      assertThrows(SSLException.class, () -> get("localhost", "true"));
   }
}
