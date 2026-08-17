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
package inetsoft.web.wiz.script;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ViewsheetAgentController}'s {@code /script/join} endpoint.
 *
 * <p>There was previously no controller-level test for the script join endpoint at all --
 * {@code editorContext} threading (Task 5, G2) was verified only at the
 * {@code SheetJoinService.join} seam ({@code SheetJoinServiceTest}), never through this
 * controller's own {@link ViewsheetAgentController.JoinResponse}. This file closes that gap.
 */
@Tag("core")
class ViewsheetAgentControllerTest {

   @Test
   void joinRefusesWhenTheFeatureIsDisabled() {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(false);

      ViewsheetAgentController controller = controllerWith(feature, mock(SheetJoinService.class));

      assertThrows(ResponseStatusException.class,
                   () -> controller.join("CODE", principal()));
   }

   /**
    * The join response must carry the session's editorContext through -- this is what lets a
    * pane-scoped ("Connect to Claude" from a script pane) session be told apart, on the wire,
    * from a whole-sheet toolbar session. Dropping it here doesn't surface as an error; it reads
    * as an ordinary whole-sheet session, indistinguishable from a legitimate toolbar mint.
    */
   @Test
   void joinReturnsTheSessionsEditorContext() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      EditorContext ctx = new EditorContext("assemblyOnClick", "Chart1", null, null);
      JoinSession session = new JoinSession("TOK-1", "Viewsheet/vs-1", "alice~;~host-org",
         SheetType.VIEWSHEET, 0L, Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED,
         null, null, ctx);

      SheetJoinService joinService = mock(SheetJoinService.class);
      Principal agent = principal();
      when(joinService.join(eq("CODE"), eq(agent))).thenReturn(session);

      ViewsheetAgentController controller = controllerWith(feature, joinService);

      ViewsheetAgentController.JoinResponse resp = controller.join("CODE", agent);

      assertEquals("TOK-1", resp.sessionToken());
      assertEquals(ctx, resp.editorContext());
   }

   @Test
   void joinReturnsNullEditorContextForAWholeSheetSession() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      JoinSession session = new JoinSession("TOK-2", "Viewsheet/vs-2", "alice~;~host-org",
         SheetType.VIEWSHEET, 0L, Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED,
         null, null, null);

      SheetJoinService joinService = mock(SheetJoinService.class);
      Principal agent = principal();
      when(joinService.join(eq("CODE"), eq(agent))).thenReturn(session);

      ViewsheetAgentController controller = controllerWith(feature, joinService);

      ViewsheetAgentController.JoinResponse resp = controller.join("CODE", agent);

      assertNull(resp.editorContext());
   }

   private static ViewsheetAgentController controllerWith(SheetAgentFeature feature,
                                                          SheetJoinService joinService)
   {
      return new ViewsheetAgentController(feature, joinService,
         mock(SheetSessionService.class), mock(ScriptEditService.class),
         mock(ScriptReadService.class), mock(ScriptExecuteService.class),
         mock(ScriptContextService.class), mock(ScriptApiService.class),
         mock(ScriptImageService.class), mock(ViewsheetService.class),
         mock(SheetAgentBroadcastService.class));
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
