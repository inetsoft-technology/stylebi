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
import static org.mockito.ArgumentMatchers.anyString;
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
      when(joinService.join(eq("ABCD2345"), eq(agent)))
         .thenReturn(new SheetJoinService.JoinOutcome(session, null));

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
                                        mock(SelectionBindingService.class),
                                        mock(CalcFieldAgentService.class),
                                        mock(SheetAgentBroadcastService.class));
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
                                        mock(SelectionBindingService.class),
                                        mock(CalcFieldAgentService.class),
                                        mock(SheetAgentBroadcastService.class));
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
         mock(SelectionBindingService.class), mock(CalcFieldAgentService.class),
         mock(SheetAgentBroadcastService.class));

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
         mock(SelectionBindingService.class), mock(CalcFieldAgentService.class),
         mock(SheetAgentBroadcastService.class));

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
         mock(SelectionBindingService.class), mock(CalcFieldAgentService.class),
         mock(SheetAgentBroadcastService.class));

      BindingAgentController.AestheticFieldRequest request =
         new BindingAgentController.AestheticFieldRequest(
            "Chart1", "color", new FieldRef("Region", "dimension", null, null, null), "Sales");

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> controller.setAestheticField("tok", request, "", principal()));

      assertTrue(thrown.getMessage().contains("Sales"), thrown.getMessage());
      verifyNoInteractions(aestheticService);
   }

   /**
    * Drives a real (not mocked) {@code TableBindingService} through the controller endpoint,
    * unlike the mocked-service tests below. {@code resolveSourceTable} falls back to {@code null}
    * -- rather than throwing -- when the bindable-fields listing itself fails to read (a
    * different fault than an inconclusive listing); this confirms that in that case the write
    * still reaches {@code TableBindingService#setShelf} with no source, and it is that method's
    * own guard which refuses it, not merely {@code resolveSourceTable}'s.
    */
   @Test
   void setTableFieldsRefusesWhenAssemblyHasNoSource() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.runtimeId(eq("tok"), any(Principal.class))).thenReturn("rt1");

      inetsoft.uql.viewsheet.CrosstabVSAssembly assembly =
         mock(inetsoft.uql.viewsheet.CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(null);
      inetsoft.uql.viewsheet.Viewsheet vs = mock(inetsoft.uql.viewsheet.Viewsheet.class);
      when(vs.getAssembly("Crosstab1")).thenReturn(assembly);
      inetsoft.report.composition.RuntimeViewsheet rvs =
         mock(inetsoft.report.composition.RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      inetsoft.web.binding.service.VSBindingService binding =
         mock(inetsoft.web.binding.service.VSBindingService.class);
      when(binding.createModel(assembly))
         .thenReturn(new inetsoft.web.binding.model.table.CrosstabBindingModel());

      TableBindingService tableService = new TableBindingService(
         sessions, binding, mock(inetsoft.web.binding.controller.VSBindingModelService.class),
         mock(inetsoft.web.binding.service.DataRefModelFactoryService.class));

      // A listing failure -- not merely an inconclusive listing -- is what reaches this guard:
      // resolveSourceTable's own try/catch turns a read failure into a null source rather than a
      // thrown refusal, so the write proceeds into TableBindingService with nothing established.
      BindableFieldsService fields = mock(BindableFieldsService.class);
      when(fields.list(eq("rt1"), eq("Crosstab1"), any(Principal.class)))
         .thenThrow(new RuntimeException("tree read failed"));

      BindingAgentController controller = new BindingAgentController(
         feature, mock(SheetJoinService.class), mock(SheetSessionService.class), sessions, fields,
         mock(BindingReadService.class), mock(ChartBindingService.class),
         mock(ChartAestheticAgentService.class), tableService, mock(CalcTableService.class),
         mock(SelectionBindingService.class), mock(CalcFieldAgentService.class),
         mock(SheetAgentBroadcastService.class));

      BindingAgentController.TableShelfRequest request =
         new BindingAgentController.TableShelfRequest(
            "Crosstab1", "rows", List.of(new FieldRef("Region", "dimension", null, null, null)),
            null);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> controller.setTableFields("tok", request, principal()));

      assertTrue(thrown.getMessage().contains("Crosstab1"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("set_table_source"), thrown.getMessage());
   }

   // ---------------------------------------------------------------------------
   // Bug #76350, PCB-004: set_table_fields/add_table_field never resolved or established a
   // source for a sourceless crosstab/table -- unlike the three chart endpoints above, which all
   // call resolveSourceTable. Reported ok:true and rendered empty, with no error naming the
   // missing source.
   // ---------------------------------------------------------------------------

   @Test
   void setTableFieldsRefusesATableTheListingDoesNotHave() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.runtimeId(eq("tok"), any(Principal.class))).thenReturn("rt1");
      BindableFieldsService fields = mock(BindableFieldsService.class);
      when(fields.list(eq("rt1"), eq("Crosstab1"), any(Principal.class))).thenReturn(List.of());
      TableBindingService tableService = mock(TableBindingService.class);

      BindingAgentController controller = new BindingAgentController(
         feature, mock(SheetJoinService.class), mock(SheetSessionService.class), sessions, fields,
         mock(BindingReadService.class), mock(ChartBindingService.class),
         mock(ChartAestheticAgentService.class), tableService, mock(CalcTableService.class),
         mock(SelectionBindingService.class), mock(CalcFieldAgentService.class),
         mock(SheetAgentBroadcastService.class));

      BindingAgentController.TableShelfRequest request = new BindingAgentController.TableShelfRequest(
         "Crosstab1", "rows", List.of(new FieldRef("Region", "dimension", null, null, null)),
         "Sales");

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> controller.setTableFields("tok", request, principal()));

      assertTrue(thrown.getMessage().contains("Sales"), thrown.getMessage());
      verifyNoInteractions(tableService);
   }

   /** @see #setTableFieldsRefusesATableTheListingDoesNotHave -- add_table_field shares the gap. */
   @Test
   void addTableFieldRefusesATableTheListingDoesNotHave() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.runtimeId(eq("tok"), any(Principal.class))).thenReturn("rt1");
      BindableFieldsService fields = mock(BindableFieldsService.class);
      when(fields.list(eq("rt1"), eq("Crosstab1"), any(Principal.class))).thenReturn(List.of());
      TableBindingService tableService = mock(TableBindingService.class);

      BindingAgentController controller = new BindingAgentController(
         feature, mock(SheetJoinService.class), mock(SheetSessionService.class), sessions, fields,
         mock(BindingReadService.class), mock(ChartBindingService.class),
         mock(ChartAestheticAgentService.class), tableService, mock(CalcTableService.class),
         mock(SelectionBindingService.class), mock(CalcFieldAgentService.class),
         mock(SheetAgentBroadcastService.class));

      BindingAgentController.TableFieldRequest request = new BindingAgentController.TableFieldRequest(
         "Crosstab1", "rows", new FieldRef("Region", "dimension", null, null, null), null, "Sales");

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> controller.addTableField("tok", request, principal()));

      assertTrue(thrown.getMessage().contains("Sales"), thrown.getMessage());
      verifyNoInteractions(tableService);
   }

   /**
    * The other half of the fix: when the listing resolves a single unambiguous table, that table
    * is forwarded to the service to establish as the assembly's source -- not just validated and
    * discarded, which is what left the assembly sourceless before this fix.
    */
   @Test
   void setTableFieldsForwardsTheResolvedSourceToTheService() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.runtimeId(eq("tok"), any(Principal.class))).thenReturn("rt1");
      BindableFieldsService fields = mock(BindableFieldsService.class);
      when(fields.list(eq("rt1"), eq("Crosstab1"), any(Principal.class))).thenReturn(List.of(
         new inetsoft.web.wiz.binding.model.BindableTable("ORDERS", null, List.of(
            new inetsoft.web.wiz.binding.model.BindableField("Region", "string", "dimension")))));
      TableBindingService tableService = mock(TableBindingService.class);

      BindingAgentController controller = new BindingAgentController(
         feature, mock(SheetJoinService.class), mock(SheetSessionService.class), sessions, fields,
         mock(BindingReadService.class), mock(ChartBindingService.class),
         mock(ChartAestheticAgentService.class), tableService, mock(CalcTableService.class),
         mock(SelectionBindingService.class), mock(CalcFieldAgentService.class),
         mock(SheetAgentBroadcastService.class));

      BindingAgentController.TableShelfRequest request = new BindingAgentController.TableShelfRequest(
         "Crosstab1", "rows", List.of(new FieldRef("Region", "dimension", null, null, null)),
         null);

      controller.setTableFields("tok", request, principal());

      verify(tableService).setShelf(eq("tok"), any(Principal.class), eq("Crosstab1"), eq("rows"),
                                    eq(request.fields()), eq("ORDERS"));
   }

   // ---------------------------------------------------------------------------
   // detach -- C2(a): must notify the tab bar the agent is no longer attached
   // ---------------------------------------------------------------------------

   @Test
   void detachNotifiesTabBar() {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      SheetSessionService sessionService = mock(SheetSessionService.class);
      JoinSession session = mock(JoinSession.class);
      when(sessionService.resolve(anyString(), anyString())).thenReturn(session);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      BindingAgentController controller = new BindingAgentController(
         feature, mock(SheetJoinService.class), sessionService,
         mock(ViewsheetSessionService.class), mock(BindableFieldsService.class),
         mock(BindingReadService.class), mock(ChartBindingService.class),
         mock(ChartAestheticAgentService.class), mock(TableBindingService.class),
         mock(CalcTableService.class), mock(SelectionBindingService.class),
         mock(CalcFieldAgentService.class), broadcast);

      controller.detach("my-token", principal());

      verify(broadcast).sendAgentInactive(session);
   }

   @Test
   void detachRefusalDoesNotNotifyTabBar() {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      SheetSessionService sessionService = mock(SheetSessionService.class);
      when(sessionService.resolve(anyString(), anyString())).thenReturn(null);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      BindingAgentController controller = new BindingAgentController(
         feature, mock(SheetJoinService.class), sessionService,
         mock(ViewsheetSessionService.class), mock(BindableFieldsService.class),
         mock(BindingReadService.class), mock(ChartBindingService.class),
         mock(ChartAestheticAgentService.class), mock(TableBindingService.class),
         mock(CalcTableService.class), mock(SelectionBindingService.class),
         mock(CalcFieldAgentService.class), broadcast);

      controller.detach("someone-elses-token", principal());

      verify(broadcast, never()).sendAgentInactive(any());
   }
}
