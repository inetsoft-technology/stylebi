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
