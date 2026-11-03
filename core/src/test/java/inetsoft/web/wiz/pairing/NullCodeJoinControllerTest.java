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
import inetsoft.web.wiz.binding.*;
import inetsoft.web.wiz.viewsheet.ViewsheetFormatService;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Bug #80140: a join/peek request that carries no pairing code must answer 404 SESSION_EXPIRED
 * (like an unknown code), not 500 "unexpected server error" from a NullPointerException in the
 * grant map. Real {@link SheetJoinService}, real {@link SheetPairingService} and the real
 * {@link WizControllerErrorHandler}, so the 500-vs-404 outcome is the one the app produces.
 */
@Tag("core")
class NullCodeJoinControllerTest {
   private MockMvc mvc;
   private Principal alice;

   @BeforeEach
   void setUp() {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      SheetPairingService pairing = new SheetPairingService(() -> 1_000_000L);
      SheetSessionService sessions = new SheetSessionService(() -> 1_000_000L);
      SheetJoinService join = new SheetJoinService(pairing, sessions, feature,
         mock(SheetRuntimeAccess.class), mock(SheetAgentBroadcastService.class));
      alice = TestPrincipals.user("alice", "host-org");

      BindingAgentController binding = new BindingAgentController(feature, join,
         sessions, mock(ViewsheetSessionService.class), mock(BindableFieldsService.class),
         mock(BindingReadService.class), mock(ChartBindingService.class),
         mock(ChartAestheticAgentService.class), mock(TableBindingService.class),
         mock(CalcTableService.class), mock(SelectionBindingService.class),
         mock(CalcFieldAgentService.class), mock(ViewsheetFormatService.class),
         mock(SheetAgentBroadcastService.class));
      SheetPairingController pairingController =
         new SheetPairingController(pairing, sessions, feature,
                                    mock(SheetAgentBroadcastService.class), join, false);

      mvc = standaloneSetup(binding, pairingController)
         .setControllerAdvice(new WizControllerErrorHandler()).build();
   }

   @Test
   void bindingJoinWithEmptyBodyIsNotFound() throws Exception {
      mvc.perform(post("/api/wiz/v1/agent/binding/join").principal(alice).accept(MediaType.APPLICATION_JSON)
                     .contentType(MediaType.APPLICATION_JSON).content("{}"))
         .andExpect(status().isNotFound())
         .andExpect(jsonPath("$.errorCode").value("SESSION_EXPIRED"))
         .andExpect(jsonPath("$.error").value("Invalid or expired pairing code"));
   }

   @Test
   void bindingJoinWithNullCodeIsNotFound() throws Exception {
      mvc.perform(post("/api/wiz/v1/agent/binding/join").principal(alice).accept(MediaType.APPLICATION_JSON)
                     .contentType(MediaType.APPLICATION_JSON).content("{\"code\":null}"))
         .andExpect(status().isNotFound())
         .andExpect(jsonPath("$.errorCode").value("SESSION_EXPIRED"));
   }

   @Test
   void bindingJoinWithBlankCodeStillNotFound() throws Exception {
      mvc.perform(post("/api/wiz/v1/agent/binding/join").principal(alice).accept(MediaType.APPLICATION_JSON)
                     .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"  \"}"))
         .andExpect(status().isNotFound())
         .andExpect(jsonPath("$.errorCode").value("SESSION_EXPIRED"));
   }

   @Test
   void peekWithoutCodeParamIsNotFound() throws Exception {
      mvc.perform(get("/api/wiz/v1/agent/pairing/peek").principal(alice)
                    .accept(MediaType.APPLICATION_JSON))
         .andExpect(status().isNotFound())
         .andExpect(jsonPath("$.errorCode").value("SESSION_EXPIRED"));
   }
}
