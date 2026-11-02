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
package inetsoft.web.wiz.pairing;

import inetsoft.web.wiz.WizControllerErrorHandler;
import inetsoft.web.wiz.script.ViewsheetAgentController;
import inetsoft.web.wiz.viewsheet.ViewsheetAssemblyAgentController;
import inetsoft.web.wiz.worksheet.WorksheetAgentController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.security.Principal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Bug #80140: the viewsheet, worksheet and script join endpoints (not covered by
 * {@link NullCodeJoinControllerTest}) must also answer 404 SESSION_EXPIRED, not 500, when the
 * pairing code is missing, and a missing code must not count toward the per-user lockout.
 */
@Tag("core")
class AllJoinEndpointsNullCodeTest {
   private MockMvc mvc;
   private Principal alice;

   @BeforeEach
   void setUp() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      SheetPairingService pairing = new SheetPairingService(() -> 1_000_000L);
      SheetSessionService sessions = new SheetSessionService(() -> 1_000_000L);
      SheetJoinService join = new SheetJoinService(pairing, sessions, feature,
         mock(SheetRuntimeAccess.class), mock(SheetAgentBroadcastService.class));
      alice = TestPrincipals.user("alice", "host-org");

      mvc = standaloneSetup(real(ViewsheetAssemblyAgentController.class, feature, join),
                            real(WorksheetAgentController.class, feature, join),
                            real(ViewsheetAgentController.class, feature, join))
         .setControllerAdvice(new WizControllerErrorHandler()).build();
   }

   private static <T> T real(Class<T> type, SheetAgentFeature feature, SheetJoinService join)
      throws Exception
   {
      T c = mock(type, Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
      set(c, type, "feature", feature);
      set(c, type, "joinService", join);
      return c;
   }

   private static void set(Object o, Class<?> type, String name, Object v) throws Exception {
      Field f = type.getDeclaredField(name);
      f.setAccessible(true);
      f.set(o, v);
   }

   private void expectNotFound(String url, String body) throws Exception {
      var req = post(url).principal(alice).accept(MediaType.APPLICATION_JSON);

      if(body != null) {
         req = req.contentType(MediaType.APPLICATION_JSON).content(body);
      }

      mvc.perform(req)
         .andExpect(status().isNotFound())
         .andExpect(jsonPath("$.errorCode").value("SESSION_EXPIRED"))
         .andExpect(jsonPath("$.error").value("Invalid or expired pairing code"));
   }

   @Test
   void viewsheetJoinWithoutCode() throws Exception {
      expectNotFound("/api/wiz/v1/agent/viewsheet/join", "{}");
      expectNotFound("/api/wiz/v1/agent/viewsheet/join", "{\"code\":null}");
   }

   @Test
   void worksheetJoinWithoutCode() throws Exception {
      expectNotFound("/api/wiz/v1/agent/worksheet/join", "{}");
      expectNotFound("/api/wiz/v1/agent/worksheet/join", "{\"code\":null}");
   }

   @Test
   void scriptJoinWithoutCodeParam() throws Exception {
      expectNotFound("/api/wiz/v1/agent/script/join", null);
   }

   @Test
   void missingCodesDoNotLockTheUserOut() throws Exception {
      for(int i = 0; i < 12; i++) {
         expectNotFound("/api/wiz/v1/agent/worksheet/join", "{}");
      }

      // an unknown (non-null) code is still a plain 404, not 429, so nothing was counted
      expectNotFound("/api/wiz/v1/agent/script/join?code=NOPE2345", null);
   }
}
