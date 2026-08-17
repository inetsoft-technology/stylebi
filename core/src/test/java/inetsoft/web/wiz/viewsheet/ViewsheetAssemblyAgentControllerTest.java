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
                                     "alice"));

      ViewsheetAssemblyAgentController controller = controllerWith(openService);

      var response = controller.openBaseWorksheet("tok-vs", principal());

      assertEquals("tok-ws", response.sessionToken());
      assertEquals("ws-runtime-1", response.runtimeId());
      // Reported by the server, never inferred: a plugin that guesses the runtime type files the
      // session under the wrong key and overwrites a live one while reporting success.
      assertEquals("worksheet", response.sheetType());
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
                                          mock(inetsoft.analytic.composition.ViewsheetService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          openService);
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
                                          mock(SheetPropertyService.class),
                                          mock(AssemblyHyperlinkService.class),
                                          mock(ChartElementService.class),
                                          mock(ChartRegionPropertyService.class),
                                          mock(AssemblyConditionService.class),
                                          mock(AssemblyHighlightService.class),
                                          mock(DateComparisonService.class),
                                          mock(inetsoft.analytic.composition.ViewsheetService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(SheetOpenService.class));
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
                                          mock(inetsoft.analytic.composition.ViewsheetService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(SheetOpenService.class));
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
                                          mock(inetsoft.analytic.composition.ViewsheetService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(SheetOpenService.class));
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
