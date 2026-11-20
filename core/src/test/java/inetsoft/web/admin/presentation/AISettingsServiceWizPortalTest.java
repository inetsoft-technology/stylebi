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

package inetsoft.web.admin.presentation;

import inetsoft.sree.SreeEnv;
import inetsoft.util.MessageException;
import inetsoft.web.admin.presentation.model.PresentationAISettingsModel;
import inetsoft.web.portal.WizPortalEntry;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * The Wiz portal entry properties are edited through the AI Integration section of the EM
 * Presentation page: the URL is validated with the same rules the portal applies, before anything
 * is written.
 */
@Tag("core")
class AISettingsServiceWizPortalTest {
   private MockedStatic<SreeEnv> env;
   private Map<String, String> store;
   private final AISettingsService service = new AISettingsService();

   @BeforeEach
   void setUp() {
      store = new HashMap<>();
      env = Mockito.mockStatic(SreeEnv.class);
      env.when(() -> SreeEnv.getProperty(anyString()))
         .thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
      env.when(() -> SreeEnv.getProperty(anyString(), anyString()))
         .thenAnswer(inv -> store.getOrDefault(inv.<String>getArgument(0), inv.getArgument(1)));
      env.when(() -> SreeEnv.setProperty(anyString(), anyString()))
         .thenAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1)));
      env.when(() -> SreeEnv.remove(anyString()))
         .thenAnswer(inv -> store.remove(inv.<String>getArgument(0)));
   }

   @AfterEach
   void tearDown() {
      env.close();
   }

   private static PresentationAISettingsModel model(boolean visible, String url) {
      return PresentationAISettingsModel.builder()
         .aiAssistantVisible(false).wizPortalVisible(visible).wizPortalUrl(url).build();
   }

   @Test
   void validSettingsAreWrittenAndReadBack() throws Exception {
      service.setModel(model(true, "  https://wiz.example.com/  "));

      assertEquals("true", store.get(WizPortalEntry.WIZ_PORTAL_VISIBLE));
      assertEquals("https://wiz.example.com/", store.get(WizPortalEntry.WIZ_PORTAL_URL));

      PresentationAISettingsModel read = service.getModel();
      assertTrue(read.wizPortalVisible());
      assertEquals("https://wiz.example.com/", read.wizPortalUrl());
   }

   @Test
   void aRootRelativeUrlIsAccepted() throws Exception {
      service.setModel(model(true, "/wiz/"));
      assertEquals("/wiz/", store.get(WizPortalEntry.WIZ_PORTAL_URL));
   }

   @Test
   void anUnsafeUrlIsRejectedAndNothingIsWritten() {
      for(String bad : new String[] { "javascript:alert(1)", "//evil.example", "ftp://x.example",
                                      "https://user@evil.example/", "https://a b.example/" })
      {
         assertThrows(MessageException.class, () -> service.setModel(model(true, bad)), bad);
      }

      assertTrue(store.isEmpty(), "a rejected save must not change any property: " + store);
   }

   @Test
   void aBlankUrlRemovesTheProperty() throws Exception {
      service.setModel(model(true, "https://wiz.example.com/"));
      service.setModel(model(false, "  "));

      assertEquals("false", store.get(WizPortalEntry.WIZ_PORTAL_VISIBLE));
      assertFalse(store.containsKey(WizPortalEntry.WIZ_PORTAL_URL));
   }

   @Test
   void resetRemovesBothProperties() throws Exception {
      service.setModel(model(true, "https://wiz.example.com/"));
      service.resetSettings();

      assertFalse(store.containsKey(WizPortalEntry.WIZ_PORTAL_VISIBLE));
      assertFalse(store.containsKey(WizPortalEntry.WIZ_PORTAL_URL));
   }

   @Test
   void normalizeUrlAppliesThePortalRules() {
      assertEquals("https://wiz.example.com/", WizPortalEntry.normalizeUrl(" https://wiz.example.com/ "));
      assertNull(WizPortalEntry.normalizeUrl(null));
      assertNull(WizPortalEntry.normalizeUrl(""));
      assertNull(WizPortalEntry.normalizeUrl("javascript:alert(1)"));
   }
}
