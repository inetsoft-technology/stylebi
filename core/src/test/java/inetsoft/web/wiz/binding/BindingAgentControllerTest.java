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
package inetsoft.web.wiz.binding;

import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("core")
class BindingAgentControllerTest {
   @Test
   void fieldsRefusesWhenTheFeatureIsDisabled() {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(false);

      assertThrows(ResponseStatusException.class,
                   () -> controllerWith(feature, mock(ViewsheetSessionService.class),
                                        mock(BindableFieldsService.class))
                      .fields("tok", null, principal()));
   }

   @Test
   void joinRefusesWhenTheFeatureIsDisabled() {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(false);

      assertThrows(ResponseStatusException.class,
                   () -> controllerWith(feature, mock(ViewsheetSessionService.class),
                                        mock(BindableFieldsService.class))
                      .join(new BindingAgentController.JoinRequest("ABCD2345"), principal()));
   }

   @Test
   void fieldsListsTheDiscoveredTablesForTheSessionRuntime() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.runtimeId(eq("tok"), any(Principal.class))).thenReturn("rt1");
      BindableFieldsService fields = mock(BindableFieldsService.class);
      when(fields.list(eq("rt1"), any(), any(Principal.class))).thenReturn(List.of());

      assertTrue(controllerWith(feature, sessions, fields)
                    .fields("tok", null, principal()).isEmpty());
      verify(fields).list(eq("rt1"), any(), any(Principal.class));
   }

   private static BindingAgentController controllerWith(SheetAgentFeature feature,
                                                        ViewsheetSessionService sessions,
                                                        BindableFieldsService fields)
   {
      return new BindingAgentController(feature, mock(SheetJoinService.class),
                                        mock(SheetSessionService.class), sessions, fields,
                                        mock(BindingReadService.class),
                                        mock(ChartBindingService.class),
                                        mock(ChartAestheticService.class),
                                        mock(TableBindingService.class),
                                        mock(CalcTableService.class));
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
