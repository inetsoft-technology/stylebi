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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.web.wiz.binding.BindableFieldsService;
import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.SheetAgentFeature;
import inetsoft.web.wiz.pairing.SheetSessionService;
import inetsoft.web.wiz.pairing.SheetType;
import inetsoft.web.wiz.script.model.PaneContext;
import inetsoft.web.wiz.script.model.ScriptContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #81445: every pane kind (and a whole-sheet session) is served the same read-only context,
 * through the real {@link PaneContextService}; an expired token is refused like everywhere else, and
 * the one rule that keeps a pane token narrow ({@code requireWholeSheetSession}) is unchanged.
 */
@Tag("core")
class PaneContextControllerTest {
   private static final String RUNTIME_ID = "Viewsheet/ctx";
   private final Principal agent = () -> "admin";
   private final RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
   private SheetSessionService sessions;
   private SheetAgentFeature feature;
   private ScriptEditService edit;
   private PaneContextController controller;

   private static JoinSession session(String token, EditorContext ctx) {
      return new JoinSession(token, RUNTIME_ID, "admin", SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
         JoinSession.ConnectionMode.PAIRED, null, null, ctx);
   }

   @BeforeEach
   void setUp() throws Exception {
      feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      sessions = mock(SheetSessionService.class);
      edit = mock(ScriptEditService.class);
      when(edit.resolve(any(), any())).thenReturn(rvs);

      ScriptContextService contextService = mock(ScriptContextService.class);
      when(contextService.context(rvs, null)).thenReturn(new ScriptContext(List.of(), List.of()));
      ScriptApiService api = mock(ScriptApiService.class);
      when(api.functionNames()).thenReturn(List.of("runQuery"));
      BindableFieldsService fields = mock(BindableFieldsService.class);
      when(fields.list(any(), any(), any())).thenReturn(List.of());

      controller = new PaneContextController(feature, sessions, edit,
         new PaneContextService(contextService, api, mock(ViewsheetService.class), fields));
   }

   @Test
   void everyPaneKindAndAWholeSheetSessionAreServed() throws Exception {
      String[][] grants = {
         {"assemblyMain", "Text1", null}, {"assemblyOnClick", "Text1", null},
         {"dynamicValue", "Text1", "visible"}, {"vsConditionValue", "Table1", "filter"},
         {"calcField", "Orders", "Margin"}, {"calcTableCell", "Calc1", "0,0"},
         {"viewsheetOnInit", null, null}, {"viewsheetOnLoad", null, null}};
      int n = 0;

      for(String[] g : grants) {
         String token = "TOK" + n++;
         when(sessions.resolve(eq(token), eq("admin"))).thenReturn(
            session(token, new EditorContext(g[0], g[1], g[2], null)));

         PaneContext ctx = controller.paneContext(token, agent);

         assertEquals(g[0], ctx.kind());
         assertEquals(List.of("runQuery"), ctx.functions());
         assertFalse(ctx.syntax().note().isBlank());
      }

      when(sessions.resolve(eq("WHOLE"), eq("admin"))).thenReturn(session("WHOLE", null));
      assertEquals("sheet", controller.paneContext("WHOLE", agent).kind());
   }

   @Test
   void anExpiredTokenIsRefusedAsExpired() throws Exception {
      when(edit.resolve(eq("GONE"), any())).thenThrow(new PairingException(
         PairingException.Kind.SESSION_EXPIRED, "Invalid or expired session: GONE"));

      PairingException ex =
         assertThrows(PairingException.class, () -> controller.paneContext("GONE", agent));

      assertEquals(PairingException.Kind.SESSION_EXPIRED, ex.getKind());
      assertEquals(HttpStatus.NOT_FOUND, controller.handlePairingException(ex).getStatusCode());
   }

   @Test
   void aTokenTheSessionServiceDoesNotKnowIsRefusedAsExpired() {
      when(sessions.resolve(eq("UNKNOWN"), eq("admin"))).thenReturn(null);

      PairingException ex =
         assertThrows(PairingException.class, () -> controller.paneContext("UNKNOWN", agent));

      assertEquals(PairingException.Kind.SESSION_EXPIRED, ex.getKind());
   }

   @Test
   void aDisabledFeatureIsForbidden() {
      when(feature.isEnabled()).thenReturn(false);

      ResponseStatusException ex =
         assertThrows(ResponseStatusException.class, () -> controller.paneContext("T", agent));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
   }

   @Test
   void thePaneTokenStaysRefusedOnWholeSheetWork() {
      JoinSession pane = session("T", new EditorContext("dynamicValue", "Text1", "visible", null));

      assertThrows(PairingException.class, () -> PaneScopeService.requireWholeSheetSession(pane));
   }
}
