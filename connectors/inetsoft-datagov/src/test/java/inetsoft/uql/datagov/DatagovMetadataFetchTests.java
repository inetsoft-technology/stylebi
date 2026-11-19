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
package inetsoft.uql.datagov;

import com.sun.net.httpserver.HttpServer;
import inetsoft.test.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wire-level coverage for {@link DatagovRuntime#getDiscoveryMetadata}/
 * {@link DatagovRuntime#getSiteMetadata}, against a real local loopback HTTP server rather than a
 * mock -- mocking those two methods (as {@code DatagovCatalogTest} does to isolate
 * {@link DatagovCatalog}'s own logic) cannot catch a bug IN them, such as an accidental
 * {@code setDoOutput(true)} or a missing/wrong {@code Authorization} header, because mocking
 * replaces the method entirely. Mirrors the reasoning behind
 * {@code ElasticCatalogTests.metadataEndpointsAreReadWithGet}, using
 * {@code com.sun.net.httpserver.HttpServer} instead of testcontainers since there is no equivalent
 * "spin up a real Socrata site" option available in this environment.
 *
 * <p>{@code getDiscoveryMetadata} targets Socrata's centrally-hosted Discovery API
 * ({@code api.us.socrata.com}), a different host than whatever a data source's own {@code user}/
 * {@code password} authenticate against, so it must never send credentials -- configured or not.
 * {@code getSiteMetadata} targets the SAME host {@code runQuery} does, so it applies the same
 * Basic-Auth-if-configured behavior {@code DatagovRuntime.getConnection} already applies.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, DatagovMetadataFetchTests.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class DatagovMetadataFetchTests {
   private HttpServer server;
   private String baseUrl;
   private volatile String capturedMethod;
   private volatile String capturedAuthHeader;
   private volatile boolean capturedHadBody;

   @BeforeEach
   void startServer() throws IOException {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/", exchange -> {
         capturedMethod = exchange.getRequestMethod();
         capturedAuthHeader = exchange.getRequestHeaders().getFirst("Authorization");
         capturedHadBody = exchange.getRequestBody().read() != -1;

         byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "application/json");
         exchange.sendResponseHeaders(200, response.length);

         try(OutputStream out = exchange.getResponseBody()) {
            out.write(response);
         }
      });
      server.start();
      baseUrl = "http://localhost:" + server.getAddress().getPort();
   }

   @AfterEach
   void stopServer() {
      server.stop(0);
   }

   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   @Test
   void discoveryApiNeverSendsCredentials_evenWhenConfigured() throws Exception {
      DatagovDataSource ds = new DatagovDataSource();
      ds.setUser("someuser");
      ds.setPassword("somepass");

      // getDiscoveryMetadata takes no data source at all -- it cannot send ds's credentials even
      // if it wanted to. This test exists to pin that shape as deliberate, not to prove a
      // parameter is unused.
      DatagovRuntime.getDiscoveryMetadata(baseUrl + "/api/catalog/v1?domains=data.example.us");

      assertEquals("GET", capturedMethod);
      assertNull(capturedAuthHeader, "the Discovery API is a different host than the site the " +
         "operator's credentials authenticate against, so it must never receive them");
      assertFalse(capturedHadBody, "a metadata read must never write a request body");
   }

   @Test
   void discoveryApiNeverSendsCredentials_whenNoneConfigured() throws Exception {
      DatagovRuntime.getDiscoveryMetadata(baseUrl + "/api/catalog/v1?domains=data.example.us");

      assertEquals("GET", capturedMethod);
      assertNull(capturedAuthHeader);
      assertFalse(capturedHadBody);
   }

   // Bug #81826: a large site's catalog is megabytes of JSON; the connector asks for gzip and
   // must decode it, and a page cut off mid-stream must be retried instead of failing the listing.

   @Test
   void discoveryApiAsksForGzipAndDecodesIt() throws Exception {
      String json = "{\"resultSetSize\":0,\"results\":[]}";
      java.util.concurrent.atomic.AtomicReference<String> acceptEncoding =
         new java.util.concurrent.atomic.AtomicReference<>();
      server.createContext("/gz", exchange -> {
         acceptEncoding.set(exchange.getRequestHeaders().getFirst("Accept-Encoding"));
         java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();

         try(java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(buf)) {
            gz.write(json.getBytes(StandardCharsets.UTF_8));
         }

         exchange.getResponseHeaders().add("Content-Encoding", "gzip");
         exchange.sendResponseHeaders(200, buf.size());

         try(OutputStream out = exchange.getResponseBody()) {
            out.write(buf.toByteArray());
         }
      });

      assertEquals(json, DatagovRuntime.getDiscoveryMetadata(baseUrl + "/gz"));
      assertTrue(String.valueOf(acceptEncoding.get()).contains("gzip"), acceptEncoding.get());
   }

   @Test
   void discoveryApiRetriesAPageCutOffMidStream() throws Exception {
      String json = "{\"resultSetSize\":0,\"results\":[]}";
      java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
      server.createContext("/cut", exchange -> {
         if(calls.incrementAndGet() == 1) {
            // Declares more bytes than it sends, then drops the connection: Premature EOF.
            exchange.sendResponseHeaders(200, 1000);
            exchange.getResponseBody().write("{\"res".getBytes(StandardCharsets.UTF_8));

            try {
               exchange.close();
            }
            catch(Exception ignore) {
            }

            return;
         }

         byte[] body = json.getBytes(StandardCharsets.UTF_8);
         exchange.sendResponseHeaders(200, body.length);

         try(OutputStream out = exchange.getResponseBody()) {
            out.write(body);
         }
      });

      assertEquals(json, DatagovRuntime.getDiscoveryMetadata(baseUrl + "/cut"));
      assertEquals(2, calls.get(), "the cut page is retried once, not the whole listing");
   }

   @Test
   void discoveryApiNamesTheInterruptionWhenEveryAttemptIsCut() {
      java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
      server.createContext("/alwayscut", exchange -> {
         calls.incrementAndGet();
         exchange.sendResponseHeaders(200, 1000);
         exchange.getResponseBody().write("{\"res".getBytes(StandardCharsets.UTF_8));

         try {
            exchange.close();
         }
         catch(Exception ignore) {
         }
      });

      Exception thrown = assertThrows(Exception.class,
         () -> DatagovRuntime.getDiscoveryMetadata(baseUrl + "/alwayscut"));

      assertEquals(3, calls.get());
      assertTrue(thrown.getMessage().contains("interrupted"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("3 attempts"), thrown.getMessage());
   }


   /**
    * A raw socket server that answers each request with a chunked 200 and, for the first
    * {@code cutFirst} requests, drops the connection mid-body with no terminating chunk -- the
    * exact "Premature EOF (ChunkedInputStream.fastRead)" shape of bug #81826. HttpServer cannot
    * produce that, it always terminates a chunked body cleanly.
    */
   private static java.net.ServerSocket chunkedServer(String body, int cutFirst,
      java.util.concurrent.atomic.AtomicInteger calls) throws IOException
   {
      java.net.ServerSocket ss =
         new java.net.ServerSocket(0, 5, java.net.InetAddress.getLoopbackAddress());
      Thread t = new Thread(() -> {
         try {
            while(!ss.isClosed()) {
               try(java.net.Socket s = ss.accept()) {
                  java.io.BufferedReader in = new java.io.BufferedReader(
                     new java.io.InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                  String line;

                  while((line = in.readLine()) != null && !line.isEmpty()) {
                  }

                  boolean cut = calls.incrementAndGet() <= cutFirst;
                  OutputStream out = s.getOutputStream();
                  out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                     "Transfer-Encoding: chunked\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                  String part = cut ? body.substring(0, body.length() / 2) : body;
                  out.write((Integer.toHexString(part.length()) + "\r\n" + part + "\r\n")
                     .getBytes(StandardCharsets.UTF_8));

                  if(!cut) {
                     out.write("0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                  }

                  out.flush();
               }
            }
         }
         catch(IOException ignore) {
         }
      });
      t.setDaemon(true);
      t.start();
      return ss;
   }

   @Test
   void discoveryApiRetriesAChunkedPageCutOffMidStream() throws Exception {
      String json = "{\"resultSetSize\":0,\"results\":[]}";
      java.util.concurrent.atomic.AtomicInteger calls =
         new java.util.concurrent.atomic.AtomicInteger();

      try(java.net.ServerSocket ss = chunkedServer(json, 1, calls)) {
         assertEquals(json, DatagovRuntime.getDiscoveryMetadata(
            "http://127.0.0.1:" + ss.getLocalPort() + "/p"));
      }

      assertEquals(2, calls.get(), "the cut page is retried once, not the whole listing");
   }

   @Test
   void discoveryApiNamesTheInterruptionWhenEveryChunkedAttemptIsCut() throws Exception {
      java.util.concurrent.atomic.AtomicInteger calls =
         new java.util.concurrent.atomic.AtomicInteger();

      try(java.net.ServerSocket ss =
         chunkedServer("{\"resultSetSize\":0,\"results\":[]}", 99, calls))
      {
         Exception thrown = assertThrows(Exception.class, () -> DatagovRuntime.getDiscoveryMetadata(
            "http://127.0.0.1:" + ss.getLocalPort() + "/p"));
         assertTrue(thrown.getMessage().contains("interrupted"), thrown.getMessage());
         assertTrue(thrown.getMessage().contains("3 attempts"), thrown.getMessage());
      }

      assertEquals(3, calls.get());
   }

   @Test
   void discoveryApiDoesNotRetryAnHttpErrorStatus() {
      java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
      server.createContext("/err", exchange -> {
         calls.incrementAndGet();
         byte[] body = "nope".getBytes(StandardCharsets.UTF_8);
         exchange.sendResponseHeaders(404, body.length);

         try(OutputStream out = exchange.getResponseBody()) {
            out.write(body);
         }
      });

      Exception thrown = assertThrows(Exception.class,
         () -> DatagovRuntime.getDiscoveryMetadata(baseUrl + "/err"));

      assertEquals(1, calls.get());
      assertTrue(thrown.getMessage().contains("404"), thrown.getMessage());
   }

   @Test
   void siteMetadataSendsBasicAuthWhenConfigured() throws Exception {
      DatagovDataSource ds = new DatagovDataSource();
      ds.setUser("someuser");
      ds.setPassword("somepass");

      DatagovRuntime.getSiteMetadata(ds, baseUrl + "/api/views/erm2-nwe9/rows.json?max_rows=0");

      assertEquals("GET", capturedMethod);
      assertFalse(capturedHadBody, "a metadata read must never write a request body");
      String expected = "Basic " + Base64.getEncoder()
         .encodeToString("someuser:somepass".getBytes(StandardCharsets.UTF_8));
      assertEquals(expected, capturedAuthHeader);
   }

   @Test
   void siteMetadataSendsNoAuthWhenNotConfigured() throws Exception {
      DatagovDataSource ds = new DatagovDataSource();

      DatagovRuntime.getSiteMetadata(ds, baseUrl + "/api/views/erm2-nwe9/rows.json?max_rows=0");

      assertEquals("GET", capturedMethod);
      assertNull(capturedAuthHeader);
      assertFalse(capturedHadBody);
   }

   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         // A real LocalPasswordCredential, not a Mockito mock: this suite needs setUser/
         // setPassword/getUser/getPassword to actually hold state so the Basic-Auth assertions
         // above are checking real credential values, not a mock's default nulls. A fresh
         // instance per call keeps each DatagovDataSource's credential independent.
         when(credentialService.createCredential(CredentialType.PASSWORD))
            .thenAnswer(invocation -> new LocalPasswordCredential());
         when(credentialService.createCredential(CredentialType.PASSWORD, false))
            .thenAnswer(invocation -> new LocalPasswordCredential());
         when(credentialService.createCredential(CredentialType.PASSWORD, true))
            .thenAnswer(invocation -> new LocalPasswordCredential());
         return credentialService;
      }
   }
}
