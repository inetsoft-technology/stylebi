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

import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the two static helpers shared by {@link AIAssistantController} and
 * {@code HttpAssistantDocSearchGateway}: base-URL resolution and the trust-all TLS bypass.
 *
 * <p>Neither helper had test coverage before this class. In particular {@code applyAssistantTls}
 * fixed a real defect (a trust-all {@code X509TrustManager} alone skips certificate chain
 * validation but not hostname verification, unless {@code SSLParameters}' endpoint identification
 * algorithm is also cleared) with no regression test — this class is that regression test.</p>
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AIAssistantControllerTest {
   private MockedStatic<SreeEnv> sreeEnv;

   @BeforeEach
   void setUp() {
      sreeEnv = mockStatic(SreeEnv.class, withSettings().lenient());
   }

   @AfterEach
   void tearDown() {
      sreeEnv.close();
   }

   @Test
   void resolvesTheInternalUrlTrimmedWhenConfigured() {
      sreeEnv.when(() -> SreeEnv.getProperty("chat.app.internal.url"))
         .thenReturn("  https://internal.example.com  ");
      sreeEnv.when(() -> SreeEnv.getProperty("chat.app.server.url"))
         .thenReturn("https://direct.example.com");

      assertEquals("https://internal.example.com", AIAssistantController.resolveAssistantBaseUrl());
   }

   @Test
   void fallsBackToTheServerUrlWhenTheInternalUrlIsBlank() {
      sreeEnv.when(() -> SreeEnv.getProperty("chat.app.internal.url")).thenReturn("   ");
      sreeEnv.when(() -> SreeEnv.getProperty("chat.app.server.url"))
         .thenReturn("https://direct.example.com");

      assertEquals("https://direct.example.com", AIAssistantController.resolveAssistantBaseUrl());
   }

   @Test
   void returnsNullWhenNeitherUrlIsConfigured() {
      sreeEnv.when(() -> SreeEnv.getProperty("chat.app.internal.url")).thenReturn(null);
      sreeEnv.when(() -> SreeEnv.getProperty("chat.app.server.url")).thenReturn(null);

      assertNull(AIAssistantController.resolveAssistantBaseUrl());
   }

   // This is exactly the defect the underlying fix closed: a trust-all TrustManager skips chain
   // validation but NOT hostname verification unless endpoint identification is cleared too.
   @Test
   void disablesHostnameVerificationAndInstallsATrustAllContextWhenVerificationIsDisabled()
      throws Exception
   {
      sreeEnv.when(() -> SreeEnv.getProperty("chat.app.server.ssl.verify", "false"))
         .thenReturn("false");

      HttpClient.Builder builder = HttpClient.newBuilder();
      AIAssistantController.applyAssistantTls(builder, "doc search");
      HttpClient client = builder.build();

      assertEquals("", client.sslParameters().getEndpointIdentificationAlgorithm());
      // An untouched HttpClient.Builder's SSLContext is the JVM default (verified empirically:
      // HttpClient.newBuilder().build().sslContext() == SSLContext.getDefault()). A distinct
      // instance here confirms the trust-all context was actually installed, not just requested.
      assertNotSame(SSLContext.getDefault(), client.sslContext());
   }

   @Test
   void leavesTlsUntouchedWhenVerificationIsEnabled() throws Exception {
      sreeEnv.when(() -> SreeEnv.getProperty("chat.app.server.ssl.verify", "false"))
         .thenReturn("true");

      HttpClient.Builder builder = HttpClient.newBuilder();
      AIAssistantController.applyAssistantTls(builder, "doc search");
      HttpClient client = builder.build();

      // Observed empirically on an untouched builder (Temurin 21): the endpoint identification
      // algorithm defaults to null, not "HTTPS" or "". Asserting against that observed baseline
      // rather than an assumed one.
      assertNull(client.sslParameters().getEndpointIdentificationAlgorithm());
      assertSame(SSLContext.getDefault(), client.sslContext());
   }
}
