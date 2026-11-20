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

package inetsoft.web.admin.ai.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.SreeEnv;
import inetsoft.web.admin.presentation.AISettingsService;
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
 * An admin-chat patch of the {@code ai} sub-model that does not mention the Wiz portal fields must
 * leave them as they were. It runs the same read, {@link PresentationJson#merge} and
 * {@link PresentationJson#toModel} steps as {@code PresentationChangePlanService}, then hands the
 * proposed model to the real {@link AISettingsService#setModel}, the write the apply step makes.
 */
@Tag("core")
class PresentationAiPatchKeepsWizPortalTest {
   private MockedStatic<SreeEnv> env;
   private Map<String, String> store;

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

   @Test
   void aPatchThatOmitsTheWizFieldsKeepsTheirValues() throws Exception {
      AISettingsService service = new AISettingsService();
      store.put(WizPortalEntry.WIZ_PORTAL_VISIBLE, "true");
      store.put(WizPortalEntry.WIZ_PORTAL_URL, "https://wiz.example.com/");
      store.put("ai.assistant.visible", "false");

      JsonNode current = PresentationJson.toNode(service.getModel());
      JsonNode spec = new ObjectMapper().readTree("{\"aiAssistantVisible\": true}");
      JsonNode merged = PresentationJson.merge(current, spec);
      PresentationAISettingsModel proposed = (PresentationAISettingsModel)
         PresentationJson.toModel(merged, PresentationSubModel.AI.modelClass());
      service.setModel(proposed);

      assertEquals("true", store.get("ai.assistant.visible"));
      assertEquals("true", store.get(WizPortalEntry.WIZ_PORTAL_VISIBLE));
      assertEquals("https://wiz.example.com/", store.get(WizPortalEntry.WIZ_PORTAL_URL));
   }

   @Test
   void aPatchOnAnUnconfiguredInstallDoesNotInventWizValues() throws Exception {
      AISettingsService service = new AISettingsService();

      JsonNode current = PresentationJson.toNode(service.getModel());
      JsonNode merged = PresentationJson.merge(
         current, new ObjectMapper().readTree("{\"aiAssistantVisible\": true}"));
      service.setModel((PresentationAISettingsModel)
         PresentationJson.toModel(merged, PresentationSubModel.AI.modelClass()));

      assertEquals("false", store.get(WizPortalEntry.WIZ_PORTAL_VISIBLE));
      assertFalse(store.containsKey(WizPortalEntry.WIZ_PORTAL_URL));
   }

   @Test
   void aPatchCanSetTheWizFields() throws Exception {
      AISettingsService service = new AISettingsService();

      JsonNode current = PresentationJson.toNode(service.getModel());
      JsonNode spec = new ObjectMapper().readTree(
         "{\"wizPortalVisible\": true, \"wizPortalUrl\": \"/wiz/\"}");
      service.setModel((PresentationAISettingsModel) PresentationJson.toModel(
         PresentationJson.merge(current, spec), PresentationSubModel.AI.modelClass()));

      assertEquals("true", store.get(WizPortalEntry.WIZ_PORTAL_VISIBLE));
      assertEquals("/wiz/", store.get(WizPortalEntry.WIZ_PORTAL_URL));
   }
}
