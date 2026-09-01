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

import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import inetsoft.web.wiz.binding.model.ChartTypeState;

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

   /**
    * The join response must carry the session's editorContext through -- otherwise a dropped
    * context doesn't surface as an error, it reads as an ordinary whole-sheet session,
    * indistinguishable from a legitimate toolbar mint, on the exact route an agent reads.
    */
   @Test
   void joinReturnsTheSessionsEditorContext() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      EditorContext ctx = new EditorContext("assemblyMain", "Chart1", null, null);
      JoinSession session = new JoinSession("TOK-1", "Viewsheet/vs-1", "alice~;~host-org",
         SheetType.VIEWSHEET, 0L, Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED,
         null, null, ctx);

      SheetJoinService joinService = mock(SheetJoinService.class);
      Principal agent = principal();
      when(joinService.join(eq("ABCD2345"), eq(agent))).thenReturn(session);

      BindingAgentController controller = controllerWith(feature, joinService,
         mock(ViewsheetSessionService.class), mock(BindableFieldsService.class));

      BindingAgentController.JoinResponse resp =
         controller.join(new BindingAgentController.JoinRequest("ABCD2345"), agent);

      assertEquals(ctx, resp.editorContext());
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
      return controllerWith(feature, mock(SheetJoinService.class), sessions, fields);
   }

   private static BindingAgentController controllerWith(SheetAgentFeature feature,
                                                        SheetJoinService joinService,
                                                        ViewsheetSessionService sessions,
                                                        BindableFieldsService fields)
   {
      return new BindingAgentController(feature, joinService,
                                        mock(SheetSessionService.class), sessions, fields,
                                        mock(BindingReadService.class),
                                        mock(ChartBindingService.class),
                                        mock(ChartAestheticAgentService.class),
                                        mock(TableBindingService.class),
                                        mock(CalcTableService.class),
                                        mock(SelectionBindingService.class));
   }

   private static BindingAgentController controllerWith(SheetAgentFeature feature,
                                                        ChartBindingService chartService)
   {
      return new BindingAgentController(feature, mock(SheetJoinService.class),
                                        mock(SheetSessionService.class),
                                        mock(ViewsheetSessionService.class),
                                        mock(BindableFieldsService.class),
                                        mock(BindingReadService.class),
                                        chartService,
                                        mock(ChartAestheticAgentService.class),
                                        mock(TableBindingService.class),
                                        mock(CalcTableService.class),
                                        mock(SelectionBindingService.class));
   }

   private static Principal principal() {
      return () -> "admin";
   }

   // ---------------------------------------------------------------------------
   // Whole-branch review finding 1 (CRITICAL) -- a pane-scoped code is not a
   // whole-sheet write handle
   // ---------------------------------------------------------------------------

   /**
    * ~25 mutating endpoints on this controller resolve the same session token -- shelves, chart
    * type, aesthetics, table binding, calc layout -- and not one of them takes a
    * {@code ScriptTarget} to check a pane grant against. Before this guard, a code minted from
    * one chart's Script tab could rebind any assembly on the sheet.
    *
    * <p>Driven through a REAL {@link ViewsheetSessionService} over a REAL
    * {@link SheetSessionService}: the refusal lives inside that service (the single resolution
    * every endpoint here funnels through), so a mocked one would assert nothing.
    *
    * <p>{@code fields} is the endpoint used because it resolves via
    * {@link ViewsheetSessionService#runtimeId} -- the READ-looking path that hands a runtime id
    * straight to services that write, and therefore the one where "it's only a read" is least
    * true.
    */
   @Test
   void aPaneScopedSessionIsRefusedOnThisWholeSheetSurface() {
      SheetSessionService store = new SheetSessionService();
      JoinSession pane = store.open("Viewsheet/vs-1", "admin", SheetType.VIEWSHEET, "sock-1",
                                    "admin",
                                    new EditorContext("assemblyMain", "Chart1", null, null));

      BindableFieldsService fields = mock(BindableFieldsService.class);
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      BindingAgentController controller = controllerWith(feature, realSessions(store), fields);

      PairingException thrown = assertThrows(PairingException.class,
         () -> controller.fields(pane.sessionToken(), null, principal()));

      assertTrue(thrown.getMessage().contains("scoped to one script location"),
                 thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Chart1"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Connect to Claude"), thrown.getMessage());
      verifyNoInteractions(fields);
   }

   /**
    * The regression that would be worse than the bug: whole-sheet toolbar pairing is in use by
    * other agents today and must be untouched.
    */
   @Test
   void aWholeSheetToolbarSessionStillReachesTheSameEndpoint() throws Exception {
      SheetSessionService store = new SheetSessionService();
      JoinSession toolbar = store.open("Viewsheet/vs-1", "admin", SheetType.VIEWSHEET, "sock-1",
                                       "admin", null);

      BindableFieldsService fields = mock(BindableFieldsService.class);
      when(fields.list(eq("Viewsheet/vs-1"), any(), any(Principal.class))).thenReturn(List.of());

      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      BindingAgentController controller = controllerWith(feature, realSessions(store), fields);

      assertTrue(controller.fields(toolbar.sessionToken(), null, principal()).isEmpty());
      verify(fields).list(eq("Viewsheet/vs-1"), any(), any(Principal.class));
   }

   /** A real {@link ViewsheetSessionService} over {@code store}. */
   private static ViewsheetSessionService realSessions(SheetSessionService store) {
      return new ViewsheetSessionService(store, mock(SheetRuntimeAccess.class),
                                         mock(SheetAgentBroadcastService.class));
   }

   // ---------------------------------------------------------------------------
   // GET chart/type. The mapping itself was untested while both downstream tiers
   // got route tests, and the read is the half a caller reaches first.
   // ---------------------------------------------------------------------------

   @Test
   void chartTypeRefusesWhenTheFeatureIsDisabled() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(false);
      ChartBindingService chartService = mock(ChartBindingService.class);

      assertThrows(ResponseStatusException.class,
                   () -> controllerWith(feature, chartService).chartType("tok", "Chart1",
                                                                        principal()));
      verifyNoInteractions(chartService);
   }

   @Test
   void chartTypeReturnsWhatTheServiceRead() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      ChartTypeState state =
         new ChartTypeState("Chart1", 1, 5, true, true, false);
      ChartBindingService chartService = mock(ChartBindingService.class);
      when(chartService.readChartType(eq("tok"), any(), eq("Chart1"))).thenReturn(state);

      ChartTypeState read =
         controllerWith(feature, chartService).chartType("tok", "Chart1", principal());

      assertSame(state, read, "the controller is a passthrough; it must not rebuild the state");
   }

   // ---------------------------------------------------------------------------
   // Bug #76350, PCB-002: set_chart_shelf silently accepted a bind to a table the viewsheet
   // cannot see yet (unsaved), reported success, then crashed later on render -- while
   // set_chart_source correctly refused the identical case. BindableColumns.requireSource's own
   // unit tests (BindableColumnsTest) cover the fixed logic directly; this drives it end to end
   // through the controller endpoint an agent actually calls.
   // ---------------------------------------------------------------------------

   @Test
   void setChartShelfRefusesATableTheListingDoesNotHaveInsteadOfSilentlyDroppingTheSource()
      throws Exception
   {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.runtimeId(eq("tok"), any(Principal.class))).thenReturn("rt1");
      BindableFieldsService fields = mock(BindableFieldsService.class);

      // The shape an unsaved table produces: a successful listing that is genuinely empty, not a
      // read failure -- see BindableColumnsTest for why those two used to be conflated.
      when(fields.list(eq("rt1"), eq("Chart1"), any(Principal.class))).thenReturn(List.of());
      ChartBindingService chartService = mock(ChartBindingService.class);

      BindingAgentController controller = new BindingAgentController(
         feature, mock(SheetJoinService.class), mock(SheetSessionService.class), sessions, fields,
         mock(BindingReadService.class), chartService, mock(ChartAestheticAgentService.class),
         mock(TableBindingService.class), mock(CalcTableService.class),
         mock(SelectionBindingService.class));

      BindingAgentController.ShelfRequest request = new BindingAgentController.ShelfRequest(
         "Chart1", "x", List.of(new FieldRef("Region", "dimension", null, null, null)), "Sales");

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> controller.setChartShelf("tok", request, "", principal()));

      assertTrue(thrown.getMessage().contains("Sales"), thrown.getMessage());
      verifyNoInteractions(chartService);
   }

   /** Same fix, same underlying resolveSourceTable call -- confirming the blast radius is 3
    *  tools, not 1: set_chart_single_shelf shares the identical gap set_chart_shelf had. */
   @Test
   void setChartSingleShelfRefusesATableTheListingDoesNotHave() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.runtimeId(eq("tok"), any(Principal.class))).thenReturn("rt1");
      BindableFieldsService fields = mock(BindableFieldsService.class);
      when(fields.list(eq("rt1"), eq("Chart1"), any(Principal.class))).thenReturn(List.of());
      ChartBindingService chartService = mock(ChartBindingService.class);

      BindingAgentController controller = new BindingAgentController(
         feature, mock(SheetJoinService.class), mock(SheetSessionService.class), sessions, fields,
         mock(BindingReadService.class), chartService, mock(ChartAestheticAgentService.class),
         mock(TableBindingService.class), mock(CalcTableService.class),
         mock(SelectionBindingService.class));

      BindingAgentController.SingleShelfRequest request = new BindingAgentController.SingleShelfRequest(
         "Chart1", "close", new FieldRef("Price", "measure", "Sum", null, null), "Sales");

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> controller.setChartSingleShelf("tok", request, "", principal()));

      assertTrue(thrown.getMessage().contains("Sales"), thrown.getMessage());
      verifyNoInteractions(chartService);
   }

   /** @see #setChartSingleShelfRefusesATableTheListingDoesNotHave -- the third of the 3 tools. */
   @Test
   void setAestheticFieldRefusesATableTheListingDoesNotHave() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.runtimeId(eq("tok"), any(Principal.class))).thenReturn("rt1");
      BindableFieldsService fields = mock(BindableFieldsService.class);
      when(fields.list(eq("rt1"), eq("Chart1"), any(Principal.class))).thenReturn(List.of());
      ChartAestheticAgentService aestheticService = mock(ChartAestheticAgentService.class);

      BindingAgentController controller = new BindingAgentController(
         feature, mock(SheetJoinService.class), mock(SheetSessionService.class), sessions, fields,
         mock(BindingReadService.class), mock(ChartBindingService.class), aestheticService,
         mock(TableBindingService.class), mock(CalcTableService.class),
         mock(SelectionBindingService.class));

      BindingAgentController.AestheticFieldRequest request =
         new BindingAgentController.AestheticFieldRequest(
            "Chart1", "color", new FieldRef("Region", "dimension", null, null, null), "Sales");

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> controller.setAestheticField("tok", request, "", principal()));

      assertTrue(thrown.getMessage().contains("Sales"), thrown.getMessage());
      verifyNoInteractions(aestheticService);
   }
}
