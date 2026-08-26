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
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.viewsheet.model.LayoutModel;
import inetsoft.web.wiz.viewsheet.model.ViewsheetModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class ViewsheetAssemblyAgentControllerTest {
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

   /**
    * The join response must carry the session's editorContext through -- otherwise a dropped
    * context doesn't surface as an error, it reads as an ordinary whole-sheet session,
    * indistinguishable from a legitimate toolbar mint, on the exact route an agent reads.
    */
   @Test
   void joinReturnsTheSessionsEditorContext() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      EditorContext ctx = new EditorContext("viewsheetOnLoad", null, null, null);
      JoinSession session = new JoinSession("tok-1", "Viewsheet/vs-1", "alice",
         SheetType.VIEWSHEET, 0L, 1000L, JoinSession.ConnectionMode.PAIRED, "sock-1",
         "alice", ctx);

      SheetJoinService joinService = mock(SheetJoinService.class);
      Principal agent = principal();
      when(joinService.join(eq("ABCD2345"), eq(agent))).thenReturn(session);

      ViewsheetAssemblyAgentController controller = controllerWithJoinService(feature, joinService);

      ViewsheetAssemblyAgentController.JoinResponse resp =
         controller.join(new ViewsheetAssemblyAgentController.JoinRequest("ABCD2345"), agent);

      assertEquals(ctx, resp.editorContext());
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

   /**
    * Task 1 (layout implementation plan, Phase 0): {@code LayoutSessionService} caches a preview
    * clone per session token, and this is the one real detach hook every wiz viewsheet session
    * already closes through -- so a layout clone must be flushed here rather than through a
    * second, parallel cleanup path.
    */
   @Test
   void detachDisposesTheLayoutSessionCacheForTheClosedToken() {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      SheetSessionService sessionService = mock(SheetSessionService.class);
      when(sessionService.resolve(anyString(), anyString()))
         .thenReturn(mock(JoinSession.class));
      LayoutSessionService layoutSessionService = mock(LayoutSessionService.class);

      ViewsheetAssemblyAgentController controller =
         controllerWith(feature, mock(ViewsheetSessionService.class),
                        mock(ViewsheetReadService.class), sessionService, layoutSessionService);

      controller.detach("my-token", principal());

      verify(layoutSessionService).disposeAll("my-token");
   }

   /**
    * A refused detach (not the caller's own session) must not dispose anything -- naming a
    * token that resolves to someone else's session should not let an outsider flush a layout
    * clone they never opened.
    */
   @Test
   void detachRefusalDoesNotDisposeTheLayoutSessionCache() {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      SheetSessionService sessionService = mock(SheetSessionService.class);
      when(sessionService.resolve(anyString(), anyString())).thenReturn(null);
      LayoutSessionService layoutSessionService = mock(LayoutSessionService.class);

      ViewsheetAssemblyAgentController controller =
         controllerWith(feature, mock(ViewsheetSessionService.class),
                        mock(ViewsheetReadService.class), sessionService, layoutSessionService);

      controller.detach("someone-elses-token", principal());

      verify(layoutSessionService, never()).disposeAll(anyString());
   }

   /**
    * {@code list_viewsheet_properties} / {@code get_viewsheet_properties} /
    * {@code set_viewsheet_properties} delegate straight through to {@link SheetPropertyService},
    * exactly as the assembly property trio delegates to {@link AssemblyPropertyService} — no
    * assembly name in the request, since the target is the sheet itself.
    */
   @Test
   void listViewsheetPropertiesDelegatesToTheService() throws Exception {
      SheetPropertyService propertyService = mock(SheetPropertyService.class);
      Map<String, Object> expected = Map.of("properties", List.of());
      when(propertyService.list(eq("tok"), any(Principal.class))).thenReturn(expected);

      ViewsheetAssemblyAgentController controller = controllerWith(propertyService);

      assertSame(expected, controller.listViewsheetProperties("tok", principal()));
   }

   @Test
   void getViewsheetPropertiesDelegatesToTheService() throws Exception {
      SheetPropertyService propertyService = mock(SheetPropertyService.class);
      Map<String, Object> expected = Map.of("desc", "hello");
      when(propertyService.get(eq("tok"), any(Principal.class), eq(false))).thenReturn(expected);

      ViewsheetAssemblyAgentController controller = controllerWith(propertyService);

      assertSame(expected, controller.getViewsheetProperties("tok", false, principal()));
   }

   @Test
   void setViewsheetPropertiesDelegatesToTheService() throws Exception {
      SheetPropertyService propertyService = mock(SheetPropertyService.class);
      ViewsheetAssemblyAgentController controller = controllerWith(propertyService);
      Map<String, Object> patch = Map.of("desc", "new");

      controller.setViewsheetProperties(
         "tok", new ViewsheetAssemblyAgentController.ViewsheetPropertyPatchRequest(patch),
         "link", principal());

      verify(propertyService).set(eq("tok"), any(Principal.class), eq(patch), eq("link"));
   }

   /**
    * {@code list_hyperlink_targets} — the query params are forwarded exactly as received and the
    * service's return value passes through unchanged, matching every other read endpoint in this
    * file.
    */
   @Test
   void listHyperlinkTargetsDelegatesToTheService() throws Exception {
      AssemblyHyperlinkService hyperlinkService = mock(AssemblyHyperlinkService.class);
      Map<String, Object> expected = Map.of("targets", List.of(), "truncated", false);
      when(hyperlinkService.listLinkTargets(eq("tok"), any(Principal.class), eq("Reports"),
                                            eq("detail"), eq(50)))
         .thenReturn(expected);

      ViewsheetAssemblyAgentController controller = controllerWith(hyperlinkService);

      assertSame(expected, controller.listHyperlinkTargets(
         "tok", "Reports", "detail", 50, principal()));
   }

   /**
    * A refused patch (a bad key, or the {@code vsScriptPane} refusal) must surface as the same
    * named-field {@code IllegalArgumentException} the global {@code WizControllerErrorHandler}
    * turns into a 400 — never a bare 500 that reads as a server bug.
    */
   @Test
   void aRefusedPatchPropagatesTheNamedFieldError() throws Exception {
      SheetPropertyService propertyService = mock(SheetPropertyService.class);
      doThrow(new IllegalArgumentException("'vsScriptPane' is not settable ... use update_script."))
         .when(propertyService).set(anyString(), any(Principal.class), anyMap(), anyString());

      ViewsheetAssemblyAgentController controller = controllerWith(propertyService);
      Map<String, Object> patch = Map.of("vsScriptPane", Map.of());

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         controller.setViewsheetProperties(
            "tok", new ViewsheetAssemblyAgentController.ViewsheetPropertyPatchRequest(patch),
            "", principal()));

      assertTrue(thrown.getMessage().contains("update_script"));
   }

   /**
    * {@code sheetType} is reported by {@link SheetOpenService} through the {@link JoinSession} it
    * returns, never inferred by the controller — a plugin that guesses the runtime type files the
    * session under the wrong key and overwrites a live one while reporting success.
    */
   @Test
   void openBaseWorksheetReturnsTheJoinShapeWithSheetTypeReported() throws Exception {
      SheetOpenService openService = mock(SheetOpenService.class);
      when(openService.openBaseWorksheet(eq("tok-vs"), any()))
         .thenReturn(new JoinSession("tok-ws", "ws-runtime-1", "alice", SheetType.WORKSHEET,
                                     0L, 1000L, JoinSession.ConnectionMode.PAIRED, "sock-1",
                                     "alice", null));

      ViewsheetAssemblyAgentController controller = controllerWith(openService);

      var response = controller.openBaseWorksheet("tok-vs", principal());

      assertEquals("tok-ws", response.sessionToken());
      assertEquals("ws-runtime-1", response.runtimeId());
      // Reported by the server, never inferred: a plugin that guesses the runtime type files the
      // session under the wrong key and overwrites a live one while reporting success.
      assertEquals("worksheet", response.sheetType());
   }

   /**
    * {@code openBaseWorksheet} is the second {@code JoinResponse} construction site in this
    * file (the first is {@link #join}) -- easy to miss because the file's other
    * {@code openBaseWorksheet} test above stubs a {@code null} editorContext, which a
    * hardcoded {@code null} in the controller would satisfy just as well. Stubbing a non-null
    * context here means only a real echo of {@code session.editorContext()} can pass.
    */
   @Test
   void openBaseWorksheetReturnsTheSessionsEditorContext() throws Exception {
      SheetOpenService openService = mock(SheetOpenService.class);
      EditorContext ctx = new EditorContext("assemblyMain", "Chart1", null, null);
      when(openService.openBaseWorksheet(eq("tok-vs"), any()))
         .thenReturn(new JoinSession("tok-ws", "ws-runtime-1", "alice", SheetType.WORKSHEET,
                                     0L, 1000L, JoinSession.ConnectionMode.PAIRED, "sock-1",
                                     "alice", ctx));

      ViewsheetAssemblyAgentController controller = controllerWith(openService);

      var response = controller.openBaseWorksheet("tok-vs", principal());

      assertEquals(ctx, response.editorContext());
   }

   /** Feature enabled, only {@code openService} wired -- for the open_base_worksheet test. */
   private static ViewsheetAssemblyAgentController controllerWith(SheetOpenService openService) {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      return new ViewsheetAssemblyAgentController(feature, mock(SheetJoinService.class),
                                          mock(SheetSessionService.class),
                                          mock(ViewsheetSessionService.class),
                                          mock(ViewsheetReadService.class),
                                          mock(ViewsheetEditService.class),
                                          mock(ViewsheetFormatService.class),
                                          mock(inetsoft.web.wiz.script.ScriptImageService.class),
                                          mock(AssemblyPropertyService.class),
                                          mock(SheetPropertyService.class),
                                          mock(AssemblyHyperlinkService.class),
                                          mock(ChartElementService.class),
                                          mock(ChartRegionPropertyService.class),
                                          mock(AssemblyConditionService.class),
                                          mock(AssemblyHighlightService.class),
                                          mock(DateComparisonService.class),
                                          mock(AssemblyConvertService.class),
                                          mock(SelectionRuntimeService.class),
                                          mock(CalendarDisplayService.class),
                                          mock(InputValueService.class),
                                          mock(inetsoft.analytic.composition.ViewsheetService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          openService,
                                          mock(LayoutSessionService.class),
                                          mock(LayoutReadService.class),
                                          mock(PrintDeviceLayoutPropertyService.class),
                                          mock(LayoutMutationService.class),
                                          mock(LayoutUndoService.class));
   }

   private static ViewsheetAssemblyAgentController controllerWith(SheetAgentFeature feature,
                                                          ViewsheetSessionService sessions,
                                                          ViewsheetReadService reader)
   {
      return controllerWith(feature, sessions, reader, mock(SheetSessionService.class));
   }

   /** Feature as given, only {@code joinService} wired -- for the successful-join test. */
   private static ViewsheetAssemblyAgentController controllerWithJoinService(
      SheetAgentFeature feature, SheetJoinService joinService)
   {
      return new ViewsheetAssemblyAgentController(feature, joinService,
                                          mock(SheetSessionService.class),
                                          mock(ViewsheetSessionService.class),
                                          mock(ViewsheetReadService.class),
                                          mock(ViewsheetEditService.class),
                                          mock(ViewsheetFormatService.class),
                                          mock(inetsoft.web.wiz.script.ScriptImageService.class),
                                          mock(AssemblyPropertyService.class),
                                          mock(SheetPropertyService.class),
                                          mock(AssemblyHyperlinkService.class),
                                          mock(ChartElementService.class),
                                          mock(ChartRegionPropertyService.class),
                                          mock(AssemblyConditionService.class),
                                          mock(AssemblyHighlightService.class),
                                          mock(DateComparisonService.class),
                                          mock(AssemblyConvertService.class),
                                          mock(SelectionRuntimeService.class),
                                          mock(CalendarDisplayService.class),
                                          mock(InputValueService.class),
                                          mock(inetsoft.analytic.composition.ViewsheetService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(SheetOpenService.class),
                                          mock(LayoutSessionService.class),
                                          mock(LayoutReadService.class),
                                          mock(PrintDeviceLayoutPropertyService.class),
                                          mock(LayoutMutationService.class),
                                          mock(LayoutUndoService.class));
   }

   private static ViewsheetAssemblyAgentController controllerWith(SheetAgentFeature feature,
                                                          ViewsheetSessionService sessions,
                                                          ViewsheetReadService reader,
                                                          SheetSessionService sessionService)
   {
      return controllerWith(feature, sessions, reader, sessionService,
                            mock(LayoutSessionService.class));
   }

   /** Overload that exposes {@code layoutSessionService} -- for the detach/disposeAll tests. */
   private static ViewsheetAssemblyAgentController controllerWith(SheetAgentFeature feature,
                                                          ViewsheetSessionService sessions,
                                                          ViewsheetReadService reader,
                                                          SheetSessionService sessionService,
                                                          LayoutSessionService layoutSessionService)
   {
      return new ViewsheetAssemblyAgentController(feature, mock(SheetJoinService.class),
                                          sessionService, sessions, reader,
                                          mock(ViewsheetEditService.class),
                                          mock(ViewsheetFormatService.class),
                                          mock(inetsoft.web.wiz.script.ScriptImageService.class),
                                          mock(AssemblyPropertyService.class),
                                          mock(SheetPropertyService.class),
                                          mock(AssemblyHyperlinkService.class),
                                          mock(ChartElementService.class),
                                          mock(ChartRegionPropertyService.class),
                                          mock(AssemblyConditionService.class),
                                          mock(AssemblyHighlightService.class),
                                          mock(DateComparisonService.class),
                                          mock(AssemblyConvertService.class),
                                          mock(SelectionRuntimeService.class),
                                          mock(CalendarDisplayService.class),
                                          mock(InputValueService.class),
                                          mock(inetsoft.analytic.composition.ViewsheetService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(SheetOpenService.class),
                                          layoutSessionService,
                                          mock(LayoutReadService.class),
                                          mock(PrintDeviceLayoutPropertyService.class),
                                          mock(LayoutMutationService.class),
                                          mock(LayoutUndoService.class));
   }

   /** Feature enabled, only {@code propertyService} wired -- for the property-trio tests. */
   private static ViewsheetAssemblyAgentController controllerWith(
      SheetPropertyService propertyService)
   {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      return new ViewsheetAssemblyAgentController(feature, mock(SheetJoinService.class),
                                          mock(SheetSessionService.class),
                                          mock(ViewsheetSessionService.class),
                                          mock(ViewsheetReadService.class),
                                          mock(ViewsheetEditService.class),
                                          mock(ViewsheetFormatService.class),
                                          mock(inetsoft.web.wiz.script.ScriptImageService.class),
                                          mock(AssemblyPropertyService.class),
                                          propertyService,
                                          mock(AssemblyHyperlinkService.class),
                                          mock(ChartElementService.class),
                                          mock(ChartRegionPropertyService.class),
                                          mock(AssemblyConditionService.class),
                                          mock(AssemblyHighlightService.class),
                                          mock(DateComparisonService.class),
                                          mock(AssemblyConvertService.class),
                                          mock(SelectionRuntimeService.class),
                                          mock(CalendarDisplayService.class),
                                          mock(InputValueService.class),
                                          mock(inetsoft.analytic.composition.ViewsheetService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(SheetOpenService.class),
                                          mock(LayoutSessionService.class),
                                          mock(LayoutReadService.class),
                                          mock(PrintDeviceLayoutPropertyService.class),
                                          mock(LayoutMutationService.class),
                                          mock(LayoutUndoService.class));
   }

   /** Feature enabled, only {@code hyperlinkService} wired -- for the hyperlink-targets test. */
   private static ViewsheetAssemblyAgentController controllerWith(
      AssemblyHyperlinkService hyperlinkService)
   {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      return new ViewsheetAssemblyAgentController(feature, mock(SheetJoinService.class),
                                          mock(SheetSessionService.class),
                                          mock(ViewsheetSessionService.class),
                                          mock(ViewsheetReadService.class),
                                          mock(ViewsheetEditService.class),
                                          mock(ViewsheetFormatService.class),
                                          mock(inetsoft.web.wiz.script.ScriptImageService.class),
                                          mock(AssemblyPropertyService.class),
                                          mock(SheetPropertyService.class),
                                          hyperlinkService,
                                          mock(ChartElementService.class),
                                          mock(ChartRegionPropertyService.class),
                                          mock(AssemblyConditionService.class),
                                          mock(AssemblyHighlightService.class),
                                          mock(DateComparisonService.class),
                                          mock(AssemblyConvertService.class),
                                          mock(SelectionRuntimeService.class),
                                          mock(CalendarDisplayService.class),
                                          mock(InputValueService.class),
                                          mock(inetsoft.analytic.composition.ViewsheetService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(SheetOpenService.class),
                                          mock(LayoutSessionService.class),
                                          mock(LayoutReadService.class),
                                          mock(PrintDeviceLayoutPropertyService.class),
                                          mock(LayoutMutationService.class),
                                          mock(LayoutUndoService.class));
   }

   /**
    * Regression for the highlight header-cell defect: omitting {@code row}/{@code col} must
    * reach {@link AssemblyHighlightService} as a {@code null} Region, not
    * {@code Region(0, 0, ...)}. A non-null Region there skips the service's fall-forward to the
    * first data cell, so every call that named no location landed on a table's header — which
    * has no highlightable fields — and was refused. An explicit {@code row}/{@code col} of 0 must
    * still arrive as a real Region: the caller asked for that cell, and second-guessing it would
    * highlight somewhere they did not request.
    */
   @Test
   void listHighlightsPassesANullRegionWhenNoLocationWasNamed() throws Exception {
      AssemblyHighlightService highlightService = mock(AssemblyHighlightService.class);
      ViewsheetAssemblyAgentController controller = controllerWith(highlightService);

      controller.listHighlights("tok", "Table1", null, null, null, principal());

      verify(highlightService).list(eq("tok"), any(Principal.class), eq("Table1"),
                                    isNull());
   }

   @Test
   void listHighlightsPassesARealRegionWhenALocationWasNamed() throws Exception {
      AssemblyHighlightService highlightService = mock(AssemblyHighlightService.class);
      ViewsheetAssemblyAgentController controller = controllerWith(highlightService);

      controller.listHighlights("tok", "Table1", 1, 1, null, principal());

      verify(highlightService).list(eq("tok"), any(Principal.class), eq("Table1"),
                                    eq(new AssemblyHighlightService.Region(1, 1, null, false,
                                                                          false)));
   }

   /**
    * {@code colName} is a standalone address, not a qualifier on {@code row}/{@code col} — a
    * chart has no rows or columns, so picking one of its measures to highlight is addressed by
    * {@code colName} alone, with {@code row}/{@code col} both omitted. An earlier version of
    * {@code highlightRegion} checked only {@code row}/{@code col} for null and dropped a
    * {@code colName}-only address into the "no location named" branch, silently losing it.
    */
   @Test
   void listHighlightsPassesARealRegionWhenOnlyColNameWasNamed() throws Exception {
      AssemblyHighlightService highlightService = mock(AssemblyHighlightService.class);
      ViewsheetAssemblyAgentController controller = controllerWith(highlightService);

      controller.listHighlights("tok", "Chart1", null, null, "Sum(Sales)", principal());

      verify(highlightService).list(eq("tok"), any(Principal.class), eq("Chart1"),
                                    eq(new AssemblyHighlightService.Region(null, null,
                                                                          "Sum(Sales)", false,
                                                                          false)));
   }

   /** Same signal, reached through {@code HighlightRequest.region()} for set/delete. */
   @Test
   void setHighlightPassesANullRegionWhenNoLocationWasNamed() throws Exception {
      AssemblyHighlightService highlightService = mock(AssemblyHighlightService.class);
      ViewsheetAssemblyAgentController controller = controllerWith(highlightService);
      var request = new ViewsheetAssemblyAgentController.HighlightRequest(
         "Table1", null, null, null, "HighRevenue", null, "#FFDDDD", List.of(), false, false);

      controller.setHighlight("tok", request, "", principal());

      verify(highlightService).set(eq("tok"), any(Principal.class), eq("Table1"), isNull(),
                                   any(), eq(false), eq(""));
   }

   @Test
   void setHighlightPassesARealRegionWhenALocationWasNamed() throws Exception {
      AssemblyHighlightService highlightService = mock(AssemblyHighlightService.class);
      ViewsheetAssemblyAgentController controller = controllerWith(highlightService);
      var request = new ViewsheetAssemblyAgentController.HighlightRequest(
         "Table1", 0, 0, null, "HighRevenue", null, "#FFDDDD", List.of(), false, false);

      controller.setHighlight("tok", request, "", principal());

      verify(highlightService).set(eq("tok"), any(Principal.class), eq("Table1"),
                                   eq(new AssemblyHighlightService.Region(0, 0, null, false,
                                                                          false)),
                                   any(), eq(false), eq(""));
   }

   /** Feature enabled, only {@code highlightService} wired -- for the highlight-region tests. */
   private static ViewsheetAssemblyAgentController controllerWith(
      AssemblyHighlightService highlightService)
   {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      return new ViewsheetAssemblyAgentController(feature, mock(SheetJoinService.class),
                                          mock(SheetSessionService.class),
                                          mock(ViewsheetSessionService.class),
                                          mock(ViewsheetReadService.class),
                                          mock(ViewsheetEditService.class),
                                          mock(ViewsheetFormatService.class),
                                          mock(inetsoft.web.wiz.script.ScriptImageService.class),
                                          mock(AssemblyPropertyService.class),
                                          mock(SheetPropertyService.class),
                                          mock(AssemblyHyperlinkService.class),
                                          mock(ChartElementService.class),
                                          mock(ChartRegionPropertyService.class),
                                          mock(AssemblyConditionService.class),
                                          highlightService,
                                          mock(DateComparisonService.class),
                                          mock(AssemblyConvertService.class),
                                          mock(SelectionRuntimeService.class),
                                          mock(CalendarDisplayService.class),
                                          mock(InputValueService.class),
                                          mock(inetsoft.analytic.composition.ViewsheetService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(SheetOpenService.class),
                                          mock(LayoutSessionService.class),
                                          mock(LayoutReadService.class),
                                          mock(PrintDeviceLayoutPropertyService.class),
                                          mock(LayoutMutationService.class),
                                          mock(LayoutUndoService.class));
   }

   // ---------------------------------------------------------------------------
   // Task 6 (layout implementation plan) -- wiring assertions for the eight new
   // layout endpoints. Each test only checks that the right service is called with the
   // right arguments for the right path; every hazard/validation behavior is already
   // covered by that service's own unit tests (Tasks 1-5).
   // ---------------------------------------------------------------------------

   @Test
   void listLayoutsDelegatesToTheReadService() throws Exception {
      LayoutReadService readService = mock(LayoutReadService.class);
      Map<String, Object> expected = Map.of("layouts", List.of());
      when(readService.list(eq("tok"), any(Principal.class))).thenReturn(expected);

      ViewsheetAssemblyAgentController controller = controllerWithLayoutServices(
         readService, mock(PrintDeviceLayoutPropertyService.class),
         mock(LayoutMutationService.class), mock(LayoutUndoService.class));

      assertSame(expected, controller.listLayouts("tok", principal()));
   }

   @Test
   void getLayoutDelegatesToTheReadService() throws Exception {
      LayoutReadService readService = mock(LayoutReadService.class);
      LayoutModel expected =
         new LayoutModel("Print Layout", "print", null, null, null, List.of());
      when(readService.get(eq("tok"), any(Principal.class), eq("Print Layout")))
         .thenReturn(expected);

      ViewsheetAssemblyAgentController controller = controllerWithLayoutServices(
         readService, mock(PrintDeviceLayoutPropertyService.class),
         mock(LayoutMutationService.class), mock(LayoutUndoService.class));

      assertSame(expected, controller.getLayout("tok", "Print Layout", principal()));
   }

   @Test
   void setPrintLayoutDelegatesToThePropertyService() throws Exception {
      PrintDeviceLayoutPropertyService propertyService =
         mock(PrintDeviceLayoutPropertyService.class);
      Map<String, Object> patch = Map.of("scaleFont", 1.0);

      ViewsheetAssemblyAgentController controller = controllerWithLayoutServices(
         mock(LayoutReadService.class), propertyService, mock(LayoutMutationService.class),
         mock(LayoutUndoService.class));

      controller.setPrintLayout("tok",
         new ViewsheetAssemblyAgentController.LayoutPrintPatchRequest(patch), "link",
         principal());

      verify(propertyService).setPrintLayout(eq("tok"), any(Principal.class), eq(patch),
                                             eq("link"));
   }

   @Test
   void manageDeviceLayoutDelegatesToThePropertyService() throws Exception {
      PrintDeviceLayoutPropertyService propertyService =
         mock(PrintDeviceLayoutPropertyService.class);
      Map<String, Object> patch = Map.of("name", "Phone");

      ViewsheetAssemblyAgentController controller = controllerWithLayoutServices(
         mock(LayoutReadService.class), propertyService, mock(LayoutMutationService.class),
         mock(LayoutUndoService.class));

      controller.manageDeviceLayout("tok",
         new ViewsheetAssemblyAgentController.LayoutDevicePatchRequest("create", patch), "link",
         principal());

      verify(propertyService).manageDeviceLayout(eq("tok"), any(Principal.class), eq("create"),
                                                 eq(patch), eq("link"));
   }

   @Test
   void editLayoutObjectsDelegatesToTheMutationServiceWithTheContentRegion() throws Exception {
      LayoutMutationService mutationService = mock(LayoutMutationService.class);
      Map<String, Object> expected = Map.of("requiresConfirmation", false);
      List<Map<String, Object>> objects = List.of(Map.of("name", "Text1"));
      when(mutationService.editObjects(eq("tok"), any(Principal.class), eq("Print Layout"),
                                       eq("move_resize"), eq(VSLayoutService.CONTENT),
                                       eq(objects), eq(false)))
         .thenReturn(expected);

      ViewsheetAssemblyAgentController controller = controllerWithLayoutServices(
         mock(LayoutReadService.class), mock(PrintDeviceLayoutPropertyService.class),
         mutationService, mock(LayoutUndoService.class));

      Map<String, Object> result = controller.editLayoutObjects("tok",
         new ViewsheetAssemblyAgentController.LayoutObjectsRequest(
            "Print Layout", "move_resize", objects, null),
         principal());

      assertSame(expected, result);
   }

   @Test
   void setLayoutTableOptionsDelegatesToTheMutationServiceWithTheContentRegion() throws Exception {
      LayoutMutationService mutationService = mock(LayoutMutationService.class);

      ViewsheetAssemblyAgentController controller = controllerWithLayoutServices(
         mock(LayoutReadService.class), mock(PrintDeviceLayoutPropertyService.class),
         mutationService, mock(LayoutUndoService.class));

      controller.setLayoutTableOptions("tok",
         new ViewsheetAssemblyAgentController.LayoutTableOptionsRequest(
            "Print Layout", "Table1", 1),
         principal());

      verify(mutationService).setTableLayoutOptions(eq("tok"), any(Principal.class),
                                                    eq("Print Layout"), eq("Table1"),
                                                    eq(VSLayoutService.CONTENT), eq(1));
   }

   @Test
   void layoutUndoDelegatesToTheUndoService() throws Exception {
      LayoutUndoService undoService = mock(LayoutUndoService.class);
      Map<String, Object> expected = Map.of("applied", true);
      when(undoService.layoutUndo(eq("tok"), any(Principal.class), eq("Print Layout")))
         .thenReturn(expected);

      ViewsheetAssemblyAgentController controller = controllerWithLayoutServices(
         mock(LayoutReadService.class), mock(PrintDeviceLayoutPropertyService.class),
         mock(LayoutMutationService.class), undoService);

      Map<String, Object> result = controller.layoutUndo("tok",
         new ViewsheetAssemblyAgentController.LayoutUndoRedoRequest("Print Layout"), principal());

      assertSame(expected, result);
   }

   @Test
   void layoutRedoDelegatesToTheUndoService() throws Exception {
      LayoutUndoService undoService = mock(LayoutUndoService.class);
      Map<String, Object> expected = Map.of("applied", true);
      when(undoService.layoutRedo(eq("tok"), any(Principal.class), eq("Print Layout")))
         .thenReturn(expected);

      ViewsheetAssemblyAgentController controller = controllerWithLayoutServices(
         mock(LayoutReadService.class), mock(PrintDeviceLayoutPropertyService.class),
         mock(LayoutMutationService.class), undoService);

      Map<String, Object> result = controller.layoutRedo("tok",
         new ViewsheetAssemblyAgentController.LayoutUndoRedoRequest("Print Layout"), principal());

      assertSame(expected, result);
   }

   /** Feature enabled, only the four Task 6 layout services wired -- for the layout-endpoint tests. */
   private static ViewsheetAssemblyAgentController controllerWithLayoutServices(
      LayoutReadService layoutReadService,
      PrintDeviceLayoutPropertyService printDeviceLayoutPropertyService,
      LayoutMutationService layoutMutationService, LayoutUndoService layoutUndoService)
   {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      return new ViewsheetAssemblyAgentController(feature, mock(SheetJoinService.class),
                                          mock(SheetSessionService.class),
                                          mock(ViewsheetSessionService.class),
                                          mock(ViewsheetReadService.class),
                                          mock(ViewsheetEditService.class),
                                          mock(ViewsheetFormatService.class),
                                          mock(inetsoft.web.wiz.script.ScriptImageService.class),
                                          mock(AssemblyPropertyService.class),
                                          mock(SheetPropertyService.class),
                                          mock(AssemblyHyperlinkService.class),
                                          mock(ChartElementService.class),
                                          mock(ChartRegionPropertyService.class),
                                          mock(AssemblyConditionService.class),
                                          mock(AssemblyHighlightService.class),
                                          mock(DateComparisonService.class),
                                          mock(AssemblyConvertService.class),
                                          mock(SelectionRuntimeService.class),
                                          mock(CalendarDisplayService.class),
                                          mock(InputValueService.class),
                                          mock(inetsoft.analytic.composition.ViewsheetService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(SheetOpenService.class),
                                          mock(LayoutSessionService.class),
                                          layoutReadService, printDeviceLayoutPropertyService,
                                          layoutMutationService, layoutUndoService);
   }

   private static Principal principal() {
      return () -> "admin";
   }

   // ---------------------------------------------------------------------------
   // Whole-branch review finding 1 (CRITICAL) -- a pane-scoped code is not a
   // whole-sheet write handle
   // ---------------------------------------------------------------------------

   /**
    * Every endpoint on this controller resolves its session through
    * {@link ViewsheetSessionService#requireSession}, and none of them takes a
    * {@code ScriptTarget} to check a pane grant against. Before this guard, a code minted from
    * one chart's Script tab reached all of them -- the edit dispatcher, the condition and
    * highlight writers, the converts -- with the grant's {@code editorContext} simply unread.
    *
    * <p>Driven through a REAL {@link ViewsheetSessionService} over a REAL
    * {@link SheetSessionService} holding a genuinely pane-scoped session: mocking the session
    * service (as every other test in this class does) would assert nothing here, because the
    * refusal lives inside it.
    */
   @Test
   void aPaneScopedSessionIsRefusedOnThisWholeSheetSurface() {
      SheetSessionService store = new SheetSessionService();
      JoinSession pane = store.open("Viewsheet/vs-1", "admin", SheetType.VIEWSHEET, "sock-1",
                                    "admin",
                                    new EditorContext("calcField", "Query1", "Margin", null));

      ViewsheetReadService reader = mock(ViewsheetReadService.class);
      ViewsheetAssemblyAgentController controller =
         controllerWith(featureOn(), realSessions(store), reader);

      PairingException thrown = assertThrows(PairingException.class,
         () -> controller.model(pane.sessionToken(), principal()));

      assertTrue(thrown.getMessage().contains("scoped to one script location"),
                 thrown.getMessage());
      // Names WHERE it is bound, so the agent can tell this from an expired session.
      assertTrue(thrown.getMessage().contains("Query1.Margin"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Connect to Claude"), thrown.getMessage());
      verifyNoInteractions(reader);
   }

   /**
    * The regression that would be worse than the bug. Whole-sheet toolbar pairing is in use by
    * other agents today; a session with no {@code editorContext} must pass straight through.
    */
   @Test
   void aWholeSheetToolbarSessionStillReachesTheSameEndpoint() throws Exception {
      SheetSessionService store = new SheetSessionService();
      JoinSession toolbar = store.open("Viewsheet/vs-1", "admin", SheetType.VIEWSHEET, "sock-1",
                                       "admin", null);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/vs-1"),
                                            any(Principal.class))).thenReturn(rvs);

      ViewsheetSessionService sessions = new ViewsheetSessionService(
         store, runtimeAccess, mock(SheetAgentBroadcastService.class));
      ViewsheetReadService reader = mock(ViewsheetReadService.class);
      ViewsheetModel expected = new ViewsheetModel("vs1", List.of());
      when(reader.read(rvs)).thenReturn(expected);

      ViewsheetAssemblyAgentController controller = controllerWith(featureOn(), sessions, reader);

      assertSame(expected, controller.model(toolbar.sessionToken(), principal()));
   }

   private static SheetAgentFeature featureOn() {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      return feature;
   }

   /** A real {@link ViewsheetSessionService} over {@code store}; runtime access is never reached. */
   private static ViewsheetSessionService realSessions(SheetSessionService store) {
      return new ViewsheetSessionService(store, mock(SheetRuntimeAccess.class),
                                         mock(SheetAgentBroadcastService.class));
   }

}
