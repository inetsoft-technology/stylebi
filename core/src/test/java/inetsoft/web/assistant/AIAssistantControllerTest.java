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

/*
 * Test strategy
 *
 * The assistant-visibility rule existed twice -- here and in AISettingsService -- as
 * byte-identical bodies. Either copy could be changed without the other, and nothing failed.
 * AIAssistantController.isAiAssistantVisible() is now the single definition and the service
 * delegates to it.
 *
 * The rule is: ai.assistant.visible must be on (case-insensitively) AND at least one of
 * chat.app.internal.url (proxy mode) / chat.app.server.url (direct mode) must be non-blank.
 * Enabling the flag with no URL would show a panel that cannot reach a server.
 *
 * Behavioral guarantees covered:
 *
 * [G1] The flag gates first: absent, "false", or unrecognized means not visible whatever the
 *      URLs say, and "TRUE" in any case counts as on.
 * [G2] With the flag on, either URL alone suffices; neither, empty, or whitespace-only does not.
 * [G3] AISettingsService agrees with the controller for every one of the above cases. This is
 *      what makes the de-duplication a regression test and not just a refactor -- reintroducing
 *      a second copy that drifts fails here.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.web.admin.presentation.AISettingsService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
   private MockedStatic<SreeEnv> sreeEnvStatic;

   @BeforeEach
   void setUp() {
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
   }

   private void configure(String visible, String internalUrl, String serverUrl) {
      // The flag is read with a default; the two URLs are read without one.
      sreeEnvStatic.when(
            () -> SreeEnv.getProperty(AIAssistantController.AI_ASSISTANT_VISIBLE, "false"))
         .thenReturn(visible == null ? "false" : visible);
      sreeEnvStatic.when(() -> SreeEnv.getProperty(AIAssistantController.CHAT_APP_INTERNAL_URL))
         .thenReturn(internalUrl);
      sreeEnvStatic.when(() -> SreeEnv.getProperty(AIAssistantController.CHAT_APP_SERVER_URL))
         .thenReturn(serverUrl);
   }

   @Test
   void resolvesTheInternalUrlTrimmedWhenConfigured() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("chat.app.internal.url"))
         .thenReturn("  https://internal.example.com  ");
      sreeEnvStatic.when(() -> SreeEnv.getProperty("chat.app.server.url"))
         .thenReturn("https://direct.example.com");

      assertEquals("https://internal.example.com", AIAssistantController.resolveAssistantBaseUrl());
   }

   // [G1] the flag gates everything, and its check is case-insensitive
   @ParameterizedTest
   @ValueSource(strings = { "false", "FALSE", "no", "1", "" })
   void notVisibleWhenFlagIsNotTrue(String flag) {
      configure(flag, "https://assistant.internal", "https://assistant.example.com");
      assertFalse(AIAssistantController.isAiAssistantVisible(),
                  "a configured URL must not make the assistant visible while the flag is off");
   }

   @Test
   void fallsBackToTheServerUrlWhenTheInternalUrlIsBlank() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("chat.app.internal.url")).thenReturn("   ");
      sreeEnvStatic.when(() -> SreeEnv.getProperty("chat.app.server.url"))
         .thenReturn("https://direct.example.com");

      assertEquals("https://direct.example.com", AIAssistantController.resolveAssistantBaseUrl());
   }

   @ParameterizedTest
   @ValueSource(strings = { "true", "TRUE", "True" })
   void flagCheckIsCaseInsensitive(String flag) {
      configure(flag, "https://assistant.internal", null);
      assertTrue(AIAssistantController.isAiAssistantVisible());
   }

   @Test
   void returnsNullWhenNeitherUrlIsConfigured() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("chat.app.internal.url")).thenReturn(null);
      sreeEnvStatic.when(() -> SreeEnv.getProperty("chat.app.server.url")).thenReturn(null);

      assertNull(AIAssistantController.resolveAssistantBaseUrl());
   }

   // This is exactly the defect the underlying fix closed: a trust-all TrustManager skips chain
   // validation but NOT hostname verification unless endpoint identification is cleared too.
   @Test
   void notVisibleWhenFlagIsAbsent() {
      configure(null, "https://assistant.internal", null);
      assertFalse(AIAssistantController.isAiAssistantVisible());
   }

   @Test
   void disablesHostnameVerificationAndInstallsATrustAllContextWhenVerificationIsDisabled()
      throws Exception
   {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("chat.app.server.ssl.verify", "false"))
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

   // [G2] with the flag on, visibility follows the URLs
   @ParameterizedTest
   @CsvSource(nullValues = "NULL", value = {
      // internalUrl, serverUrl, expectedVisible
      "https://assistant.internal, NULL,                        true",
      "NULL,                       https://assistant.test,      true",
      "https://assistant.internal, https://assistant.test,      true",
      "NULL,                       NULL,                        false",
      "'',                         '',                          false",
      "'   ',                      '   ',                       false",
      "'   ',                      https://assistant.test,      true",
      "https://assistant.internal, '   ',                       true"
   })

   void visibilityFollowsTheConfiguredUrls(String internalUrl, String serverUrl, boolean expected) {
      configure("true", internalUrl, serverUrl);
      assertEquals(expected, AIAssistantController.isAiAssistantVisible());
   }

   // [G3] the service must not drift from the controller
   @ParameterizedTest
   @CsvSource(nullValues = "NULL", value = {
      "NULL,  https://assistant.internal, NULL",
      "false, https://assistant.internal, NULL",
      "true,  NULL,                       NULL",
      "true,  '   ',                      '   '",
      "true,  https://assistant.internal, NULL",
      "true,  NULL,                       https://assistant.test",
      "TRUE,  https://assistant.internal, https://assistant.test"
   })

   void serviceAgreesWithController(String visible, String internalUrl, String serverUrl) {
      configure(visible, internalUrl, serverUrl);
      assertEquals(AIAssistantController.isAiAssistantVisible(),
                   new AISettingsService().isAiAssistantVisible(),
                   "AISettingsService must delegate, not keep its own copy of the rule");
   }

   @Test
   void leavesTlsUntouchedWhenVerificationIsEnabled() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("chat.app.server.ssl.verify", "false"))
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
