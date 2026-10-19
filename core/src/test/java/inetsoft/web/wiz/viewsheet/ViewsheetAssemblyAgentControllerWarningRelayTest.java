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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.web.wiz.pairing.SheetAgentFeature;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78406: each wiz write endpoint that re-runs a table returns the warnings its service
 * produced as {@code {"warnings": [...]}} and otherwise answers exactly as it did before (an empty
 * 200, or the same map without a {@code warnings} key).
 */
@Tag("core")
class ViewsheetAssemblyAgentControllerWarningRelayTest {
   private static final List<String> WARNED = List.of("Script execution error in assembly: T1");

   /** Bug #78439: the chart element, plot-size and region-property writes relay the warnings. */
   @Test
   void chartWritesRelayTheWarnings() throws Exception {
      List<String> chartWarned = List.of("Chart 'Chart1' cannot be generated: x");
      ChartElementService elements = mock(ChartElementService.class);
      when(elements.setVisibility(eq("tok"), any(), eq("Chart1"), eq("axis"), eq("Region"),
                                  eq(false), eq(false), anyString())).thenReturn(chartWarned);
      when(elements.resizePlot(eq("tok"), any(), eq("Chart1"), eq(2.0), eq(false), eq(false),
                               anyString())).thenReturn(chartWarned);
      ChartRegionPropertyService regions = mock(ChartRegionPropertyService.class);
      when(regions.set(eq("tok"), any(), eq("Chart1"), eq("axis"), eq("y"), eq(null), any(),
                       anyString())).thenReturn(chartWarned);
      ViewsheetAssemblyAgentController controller = controller(Map.of(
         ChartElementService.class, elements, ChartRegionPropertyService.class, regions));
      Map<String, Object> expected = Map.of("warnings", chartWarned);

      assertEquals(expected, controller.setChartElementVisibility(
         "tok", new ViewsheetAssemblyAgentController.ElementVisibilityRequest(
            "Chart1", "axis", "Region", false, false), "", principal()));
      assertEquals(expected, controller.resizeChartPlot(
         "tok", new ViewsheetAssemblyAgentController.PlotResizeRequest("Chart1", 2.0, false, false),
         "", principal()));
      assertEquals(expected, controller.setChartRegionProperties(
         "tok", new ViewsheetAssemblyAgentController.RegionPropertiesRequest(
            "Chart1", "axis", "y", null, Map.of("showAxisLine", false)), "", principal()));
   }

   @Test
   void chartWritesAnswerAnEmptyBodyWhenNothingWarned() throws Exception {
      ChartElementService elements = mock(ChartElementService.class);
      when(elements.setVisibility(anyString(), any(), anyString(), anyString(), any(),
                                  org.mockito.ArgumentMatchers.anyBoolean(),
                                  org.mockito.ArgumentMatchers.anyBoolean(), anyString()))
         .thenReturn(List.of());
      ViewsheetAssemblyAgentController controller =
         controller(ChartElementService.class, elements);

      assertNull(controller.setChartElementVisibility(
         "tok", new ViewsheetAssemblyAgentController.ElementVisibilityRequest(
            "Chart1", "axis", "Region", false, false), "", principal()));
   }

   /** Bug #78406 round 3: clear_date_comparison used to answer an empty 200 whatever happened. */
   @Test
   void clearDateComparisonRelaysTheWarnings() throws Exception {
      DateComparisonService comparisons = mock(DateComparisonService.class);
      when(comparisons.clear(eq("tok"), any(), eq("Xt1"), anyString())).thenReturn(WARNED);
      ViewsheetAssemblyAgentController controller =
         controller(DateComparisonService.class, comparisons);

      assertEquals(Map.of("warnings", WARNED),
                   controller.clearDateComparison("tok", dateComparisonRequest("Xt1"), "",
                                                  principal()));
   }

   @Test
   void clearDateComparisonAnswersAnEmptyBodyWhenClean() throws Exception {
      DateComparisonService comparisons = mock(DateComparisonService.class);
      when(comparisons.clear(eq("tok"), any(), eq("Xt1"), anyString())).thenReturn(List.of());
      ViewsheetAssemblyAgentController controller =
         controller(DateComparisonService.class, comparisons);

      assertNull(controller.clearDateComparison("tok", dateComparisonRequest("Xt1"), "",
                                                principal()));
   }

   /** Bug #78406 round 4: set_column_options used to answer an empty 200 whatever happened. */
   @Test
   void setColumnOptionsRelaysTheWarnings() throws Exception {
      ColumnOptionService options = mock(ColumnOptionService.class);
      when(options.set(eq("tok"), any(), eq("Form1"), eq(0), eq(false), any(), any(),
                       anyString())).thenReturn(WARNED);
      ViewsheetAssemblyAgentController controller =
         controller(ColumnOptionService.class, options);

      assertEquals(Map.of("warnings", WARNED),
                   controller.setColumnOptions("tok", columnOptionsRequest(), "", principal()));
   }

   @Test
   void setColumnOptionsAnswersAnEmptyBodyWhenClean() throws Exception {
      ColumnOptionService options = mock(ColumnOptionService.class);
      when(options.set(eq("tok"), any(), eq("Form1"), eq(0), eq(false), any(), any(),
                       anyString())).thenReturn(List.of());
      ViewsheetAssemblyAgentController controller =
         controller(ColumnOptionService.class, options);

      assertNull(controller.setColumnOptions("tok", columnOptionsRequest(), "", principal()));
   }

   private static ViewsheetAssemblyAgentController.ColumnOptionsPatchRequest
      columnOptionsRequest()
   {
      return new ViewsheetAssemblyAgentController.ColumnOptionsPatchRequest(
         "Form1", 0, false, null, null);
   }

   private static ViewsheetAssemblyAgentController.DateComparisonRequest dateComparisonRequest(
      String assembly)
   {
      return new ViewsheetAssemblyAgentController.DateComparisonRequest(
         assembly, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null);
   }

   @Test
   void gotoBookmarkRelaysTheWarnings() throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.mutate(anyString(), any(), any())).thenReturn(WARNED);

      assertEquals(Map.of("warnings", WARNED),
                   controller(sessions).gotoBookmark(
                      "tok", new ViewsheetAssemblyAgentController.GotoBookmarkRequest("bm", null),
                      "", principal()));
   }

   @Test
   void gotoBookmarkAnswersAnEmptyBodyWhenClean() throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.mutate(anyString(), any(), any())).thenReturn(List.of());

      assertNull(controller(sessions).gotoBookmark(
         "tok", new ViewsheetAssemblyAgentController.GotoBookmarkRequest("bm", null), "",
         principal()));
   }

   @Test
   void setViewsheetPropertiesRelaysTheWarnings() throws Exception {
      SheetPropertyService service = mock(SheetPropertyService.class);
      Map<String, Object> patch = Map.of("desc", "x");
      when(service.set(eq("tok"), any(), eq(patch), anyString())).thenReturn(WARNED);

      assertEquals(Map.of("warnings", WARNED),
                   controller(SheetPropertyService.class, service).setViewsheetProperties(
                      "tok", new ViewsheetAssemblyAgentController.ViewsheetPropertyPatchRequest(patch),
                      "", principal()));
   }

   @Test
   void setViewsheetPropertiesAnswersAnEmptyBodyWhenClean() throws Exception {
      SheetPropertyService service = mock(SheetPropertyService.class);
      Map<String, Object> patch = Map.of("desc", "x");
      when(service.set(eq("tok"), any(), eq(patch), anyString())).thenReturn(List.of());

      assertNull(controller(SheetPropertyService.class, service).setViewsheetProperties(
         "tok", new ViewsheetAssemblyAgentController.ViewsheetPropertyPatchRequest(patch), "",
         principal()));
   }

   @Test
   void setDataSourceRelaysTheWarningsOfTheClearMode() throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(eq("tok"), any())).thenReturn(mock(RuntimeViewsheet.class));
      SheetPropertyService service = mock(SheetPropertyService.class);
      when(service.setDataSource(eq("tok"), any(), eq(null), anyString())).thenReturn(WARNED);
      ViewsheetAssemblyAgentController controller = controller(
         Map.of(ViewsheetSessionService.class, sessions, SheetPropertyService.class, service));

      assertEquals(Map.of("warnings", WARNED), controller.setDataSource(
         "tok", new ViewsheetAssemblyAgentController.SetDataSourceRequest(
            true, null, null, null, null, null), "", principal()));
   }

   @Test
   void setDataSourceAnswersAnEmptyBodyWhenClean() throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(eq("tok"), any())).thenReturn(mock(RuntimeViewsheet.class));
      SheetPropertyService service = mock(SheetPropertyService.class);
      when(service.setDataSource(eq("tok"), any(), eq(null), anyString())).thenReturn(List.of());
      ViewsheetAssemblyAgentController controller = controller(
         Map.of(ViewsheetSessionService.class, sessions, SheetPropertyService.class, service));

      assertNull(controller.setDataSource(
         "tok", new ViewsheetAssemblyAgentController.SetDataSourceRequest(
            true, null, null, null, null, null), "", principal()));
   }

   @Test
   void setAssemblyPropertiesRelaysTheWarnings() throws Exception {
      AssemblyPropertyService service = mock(AssemblyPropertyService.class);
      Map<String, Object> patch = Map.of("title", "x");
      when(service.set(eq("tok"), any(), eq("Table1"), eq(patch), anyString()))
         .thenReturn(WARNED);

      assertEquals(Map.of("warnings", WARNED),
                   controller(AssemblyPropertyService.class, service).setAssemblyProperties(
                      "tok", new ViewsheetAssemblyAgentController.PropertyPatchRequest(
                         "Table1", patch), "", principal()));
   }

   @Test
   void setAssemblyPropertiesAnswersAnEmptyBodyWhenClean() throws Exception {
      AssemblyPropertyService service = mock(AssemblyPropertyService.class);
      Map<String, Object> patch = Map.of("title", "x");
      when(service.set(eq("tok"), any(), eq("Table1"), eq(patch), anyString()))
         .thenReturn(List.of());

      assertNull(controller(AssemblyPropertyService.class, service).setAssemblyProperties(
         "tok", new ViewsheetAssemblyAgentController.PropertyPatchRequest("Table1", patch), "",
         principal()));
   }

   @Test
   void editRelaysTheWarningsAlone() throws Exception {
      ViewsheetEditService edits = mock(ViewsheetEditService.class);
      when(edits.apply(eq("tok"), any(), any(), anyString()))
         .thenReturn(new ViewsheetEditService.EditResult(null, WARNED));

      assertEquals(Map.of("warnings", WARNED),
                   controller(ViewsheetEditService.class, edits).edit(
                      "tok", editRequest("refresh_viewsheet"), "", principal()));
   }

   @Test
   void editRelaysTheNewNameAndTheWarnings() throws Exception {
      ViewsheetEditService edits = mock(ViewsheetEditService.class);
      when(edits.apply(eq("tok"), any(), any(), anyString()))
         .thenReturn(new ViewsheetEditService.EditResult("Table1", WARNED));

      assertEquals(Map.of("assembly", "Table1", "warnings", WARNED),
                   controller(ViewsheetEditService.class, edits).edit(
                      "tok", editRequest("add"), "", principal()));
   }

   @Test
   void editKeepsItsPreviousShapesWhenClean() throws Exception {
      ViewsheetEditService edits = mock(ViewsheetEditService.class);
      ViewsheetAssemblyAgentController controller = controller(ViewsheetEditService.class, edits);

      when(edits.apply(eq("tok"), any(), any(), anyString()))
         .thenReturn(new ViewsheetEditService.EditResult("Text1", List.of()));
      assertEquals(Map.of("assembly", "Text1"),
                   controller.edit("tok", editRequest("add"), "", principal()));

      when(edits.apply(eq("tok"), any(), any(), anyString()))
         .thenReturn(new ViewsheetEditService.EditResult(null, List.of()));
      assertNull(controller.edit("tok", editRequest("move"), "", principal()));
   }

   @Test
   void setPrintLayoutRelaysTheWarnings() throws Exception {
      PrintDeviceLayoutPropertyService service = mock(PrintDeviceLayoutPropertyService.class);
      Map<String, Object> patch = Map.of("scaleFont", 1.0);
      when(service.setPrintLayout(eq("tok"), any(), eq(patch), anyString())).thenReturn(WARNED);
      ViewsheetAssemblyAgentController controller =
         controller(PrintDeviceLayoutPropertyService.class, service);

      assertEquals(Map.of("warnings", WARNED), controller.setPrintLayout(
         "tok", new ViewsheetAssemblyAgentController.LayoutPrintPatchRequest(patch), "",
         principal()));

      when(service.setPrintLayout(eq("tok"), any(), eq(patch), anyString()))
         .thenReturn(List.of());
      assertNull(controller.setPrintLayout(
         "tok", new ViewsheetAssemblyAgentController.LayoutPrintPatchRequest(patch), "",
         principal()));
   }

   @Test
   void manageDeviceLayoutRelaysTheWarnings() throws Exception {
      PrintDeviceLayoutPropertyService service = mock(PrintDeviceLayoutPropertyService.class);
      Map<String, Object> patch = Map.of("mobileOnly", true);
      when(service.manageDeviceLayout(eq("tok"), any(), eq("update"), eq(patch), anyString()))
         .thenReturn(WARNED);
      ViewsheetAssemblyAgentController controller =
         controller(PrintDeviceLayoutPropertyService.class, service);

      assertEquals(Map.of("warnings", WARNED), controller.manageDeviceLayout(
         "tok", new ViewsheetAssemblyAgentController.LayoutDevicePatchRequest("update", patch),
         "", principal()));

      when(service.manageDeviceLayout(eq("tok"), any(), eq("update"), eq(patch), anyString()))
         .thenReturn(List.of());
      assertNull(controller.manageDeviceLayout(
         "tok", new ViewsheetAssemblyAgentController.LayoutDevicePatchRequest("update", patch),
         "", principal()));
   }

   /** The clear result is a record, so the warnings must stay out of its JSON when empty. */
   @Test
   void theClearPrintLayoutResultCarriesWarningsOnlyWhenThereAreSome() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      Map<?, ?> clean = mapper.readValue(mapper.writeValueAsString(
         new PrintDeviceLayoutPropertyService.ClearPrintLayoutResult(true, 3)), Map.class);
      Map<?, ?> warned = mapper.readValue(mapper.writeValueAsString(
         new PrintDeviceLayoutPropertyService.ClearPrintLayoutResult(true, 3, WARNED)),
         Map.class);

      assertEquals(Map.of("cleared", true, "discardedObjects", 3), clean);
      assertEquals(Map.of("cleared", true, "discardedObjects", 3, "warnings", WARNED), warned);
   }

   @Test
   void setValuesRelaysTheWarningsOfTheParameterWrite() throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.mutate(anyString(), any(), any())).thenReturn(WARNED);
      ParameterValueService service = new ParameterValueService(sessions, null, null);

      Map<String, Object> result =
         service.setValues("tok", principal(), Map.of("p", List.of("v")), "");

      assertEquals(WARNED, result.get("warnings"));
      assertEquals(true, result.get("ok"));
   }

   @Test
   void setValuesHasNoWarningsKeyWhenClean() throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.mutate(anyString(), any(), any())).thenReturn(List.of());
      ParameterValueService service = new ParameterValueService(sessions, null, null);

      assertFalse(service.setValues("tok", principal(), Map.of("p", List.of("v")), "")
                     .containsKey("warnings"));
   }

   @Test
   void setMaxModeRelaysTheWarningsAndKeepsItsShapeWhenClean() throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      AssemblyMaxModeService service = new AssemblyMaxModeService(sessions, null, null, null);

      when(sessions.mutate(anyString(), any(), any())).thenReturn(WARNED);
      assertEquals(WARNED, service.setMaxMode("tok", principal(), "T1", false, null, null, "")
                              .get("warnings"));

      when(sessions.mutate(anyString(), any(), any())).thenReturn(List.of());
      assertFalse(service.setMaxMode("tok", principal(), "T1", false, null, null, "")
                     .containsKey("warnings"));
   }

   private static EditRequest editRequest(String op) {
      return new EditRequest(op, null, null, null, null, null, null, null, null, null, null,
                             null, null, null);
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private static ViewsheetAssemblyAgentController controller(ViewsheetSessionService sessions) {
      return controller(ViewsheetSessionService.class, sessions);
   }

   private static <T> ViewsheetAssemblyAgentController controller(Class<T> type, T override) {
      return controller(Map.of(type, override));
   }

   /** Every collaborator is a mock unless it is named in {@code overrides}. */
   private static ViewsheetAssemblyAgentController controller(Map<Class<?>, Object> overrides) {
      try {
         Constructor<?> constructor =
            ViewsheetAssemblyAgentController.class.getConstructors()[0];
         Class<?>[] types = constructor.getParameterTypes();
         Object[] args = new Object[types.length];

         for(int i = 0; i < types.length; i++) {
            args[i] = overrides.containsKey(types[i]) ? overrides.get(types[i])
               : types[i].isInterface() || !types[i].isPrimitive() ? mock(types[i]) : null;
         }

         for(int i = 0; i < types.length; i++) {
            if(types[i] == SheetAgentFeature.class) {
               when(((SheetAgentFeature) args[i]).isEnabled()).thenReturn(true);
            }
         }

         return (ViewsheetAssemblyAgentController) constructor.newInstance(args);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }
}
