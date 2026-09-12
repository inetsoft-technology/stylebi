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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
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

   // [G1] the flag gates everything, and its check is case-insensitive
   @ParameterizedTest
   @ValueSource(strings = { "false", "FALSE", "no", "1", "" })
   void notVisibleWhenFlagIsNotTrue(String flag) {
      configure(flag, "https://assistant.internal", "https://assistant.example.com");
      assertFalse(AIAssistantController.isAiAssistantVisible(),
                  "a configured URL must not make the assistant visible while the flag is off");
   }

   @ParameterizedTest
   @ValueSource(strings = { "true", "TRUE", "True" })
   void flagCheckIsCaseInsensitive(String flag) {
      configure(flag, "https://assistant.internal", null);
      assertTrue(AIAssistantController.isAiAssistantVisible());
   }

   @Test
   void notVisibleWhenFlagIsAbsent() {
      configure(null, "https://assistant.internal", null);
      assertFalse(AIAssistantController.isAiAssistantVisible());
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
}
