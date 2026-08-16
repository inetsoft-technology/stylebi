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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.viewsheet.model.ViewsheetModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("core")
class ViewsheetAgentControllerTest {
   @Test
   void joinRefusesWhenTheFeatureIsDisabled() {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(false);

      ViewsheetAssemblyAgentController controller = controllerWith(feature,
                                                           mock(ViewsheetSessionService.class),
                                                           mock(ViewsheetReadService.class));

      assertThrows(ResponseStatusException.class, () ->
         controller.join(new ViewsheetAssemblyAgentController.JoinRequest("ABCD2345"), principal()));
   }

   @Test
   void modelReturnsTheReadServiceResult() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      ViewsheetReadService reader = mock(ViewsheetReadService.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      ViewsheetModel expected = new ViewsheetModel("vs1", List.of());
      when(sessions.resolve(eq("tok"), any(Principal.class))).thenReturn(rvs);
      when(reader.read(rvs)).thenReturn(expected);

      ViewsheetAssemblyAgentController controller = controllerWith(feature, sessions, reader);

      assertSame(expected, controller.model("tok", principal()));
   }

   /**
    * detach took the session token and nothing else, so any authenticated caller holding or
    * guessing another user's token could terminate their pairing session. Every other endpoint
    * binds the token to the caller through {@code sessions.resolve(token, user)}; this one
    * accepted {@code Principal} and ignored it. The script controller — which this was clearly
    * copied from — resolves first and only closes on a hit.
    */
   @Test
   void detachRefusesASessionThatIsNotTheCallersOwn() {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      SheetSessionService sessionService = mock(SheetSessionService.class);
      when(sessionService.resolve(anyString(), anyString())).thenReturn(null);

      ViewsheetAssemblyAgentController controller =
         controllerWith(feature, mock(ViewsheetSessionService.class),
                        mock(ViewsheetReadService.class), sessionService);

      controller.detach("someone-elses-token", principal());

      verify(sessionService, never()).close(anyString());
   }

   @Test
   void detachClosesTheCallersOwnSession() {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      SheetSessionService sessionService = mock(SheetSessionService.class);
      when(sessionService.resolve(anyString(), anyString()))
         .thenReturn(mock(JoinSession.class));

      ViewsheetAssemblyAgentController controller =
         controllerWith(feature, mock(ViewsheetSessionService.class),
                        mock(ViewsheetReadService.class), sessionService);

      controller.detach("my-token", principal());

      verify(sessionService).close("my-token");
   }

   private static ViewsheetAssemblyAgentController controllerWith(SheetAgentFeature feature,
                                                          ViewsheetSessionService sessions,
                                                          ViewsheetReadService reader)
   {
      return controllerWith(feature, sessions, reader, mock(SheetSessionService.class));
   }

   private static ViewsheetAssemblyAgentController controllerWith(SheetAgentFeature feature,
                                                          ViewsheetSessionService sessions,
                                                          ViewsheetReadService reader,
                                                          SheetSessionService sessionService)
   {
      return new ViewsheetAssemblyAgentController(feature, mock(SheetJoinService.class),
                                          sessionService, sessions, reader,
                                          mock(ViewsheetEditService.class),
                                          mock(ViewsheetFormatService.class),
                                          mock(inetsoft.web.wiz.script.ScriptImageService.class),
                                          mock(AssemblyPropertyService.class),
                                          mock(AssemblyHyperlinkService.class),
                                          mock(ChartElementService.class),
                                          mock(ChartRegionPropertyService.class),
                                          mock(AssemblyConditionService.class),
                                          mock(AssemblyHighlightService.class),
                                          mock(DateComparisonService.class),
                                          mock(inetsoft.analytic.composition.ViewsheetService.class),
                                          mock(SheetAgentBroadcastService.class));
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
