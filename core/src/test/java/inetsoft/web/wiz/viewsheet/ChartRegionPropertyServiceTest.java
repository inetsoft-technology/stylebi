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
import inetsoft.web.composer.vs.dialog.RegionPropertyDialogService;
import inetsoft.web.graph.model.dialog.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Chart sub-element properties: axis, legend and title.
 *
 * <p>These are the three dialogs the assembly property registry could not reach, because they are
 * scoped by a <b>region</b> within the chart rather than by the assembly alone — an axis is
 * identified by its type and field, a legend by its index, a title by which title it is.
 */
@Tag("core")
class ChartRegionPropertyServiceTest {
   @Test
   void listsTheAxisPropertiesWithTheirCurrentValues() throws Exception {
      Harness h = harness();
      AxisPropertyDialogModel model = axisModel();
      model.getAxisLinePaneModel().setShowAxisLine(true);
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(model);

      Map<String, Object> listed = h.service.list("tok", principal(), "Chart1", "axis", "y", null);

      assertEquals("axis", listed.get("region"));
      @SuppressWarnings("unchecked")
      java.util.List<Map<String, Object>> props =
         (java.util.List<Map<String, Object>>) listed.get("properties");
      assertTrue(props.stream().anyMatch(p -> "showAxisLine".equals(p.get("name"))),
                 "the axis line properties must be discoverable by name");
   }

   @Test
   void writesAnAxisPropertyThroughTheWholePatchRule() throws Exception {
      Harness h = harness();
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(axisModel());

      h.service.set("tok", principal(), "Chart1", "axis", "y", null,
                    Map.of("showAxisLine", false, "logarithmicScale", true), "");

      ArgumentCaptor<AxisPropertyDialogModel> captor =
         ArgumentCaptor.forClass(AxisPropertyDialogModel.class);
      verify(h.regions).setAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyInt(),
                                                   any(), captor.capture(), anyString(),
                                                   any(Principal.class), any());
      assertFalse(captor.getValue().getAxisLinePaneModel().isShowAxisLine());
      assertTrue(captor.getValue().getAxisLinePaneModel().isLogarithmicScale());
   }

   /** One bad key rejects the whole patch, exactly as set_assembly_properties does. */
   @Test
   void refusesTheWholePatchWhenOneKeyIsUnknown() throws Exception {
      Harness h = harness();
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(axisModel());

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", "axis", "y", null,
                             Map.of("showAxisLine", false, "nonsense", 1), ""));

      assertTrue(thrown.getMessage().contains("nonsense"));
      verify(h.regions, never()).setAxisPropertyDialogModel(anyString(), anyString(), anyString(),
                                                            anyInt(), any(), any(), anyString(),
                                                            any(Principal.class), any());
   }

   @Test
   void writesALegendPropertyAddressedByIndex() throws Exception {
      Harness h = harness();
      when(h.regions.getLegendFormatDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(Principal.class)))
         .thenReturn(legendModel());

      h.service.set("tok", principal(), "Chart1", "legend", "0", null,
                    Map.of("visible", false), "");

      ArgumentCaptor<LegendFormatDialogModel> captor =
         ArgumentCaptor.forClass(LegendFormatDialogModel.class);
      verify(h.regions).setLegendFormatDialogModel(anyString(), anyString(), anyInt(),
                                                   captor.capture(), anyString(),
                                                   any(Principal.class), any());
      assertFalse(captor.getValue().getLegendFormatGeneralPaneModel().isVisible());
   }

   @Test
   void writesATitleProperty() throws Exception {
      Harness h = harness();
      when(h.regions.getTitleFormatDialogModel(anyString(), anyString(), anyString(), anyString(),
                                               any(Principal.class)))
         .thenReturn(titleModel());

      h.service.set("tok", principal(), "Chart1", "title", "y", null,
                    Map.of("title", "Revenue"), "");

      ArgumentCaptor<TitleFormatDialogModel> captor =
         ArgumentCaptor.forClass(TitleFormatDialogModel.class);
      verify(h.regions).setTitleFormatDialogModel(anyString(), anyString(), anyString(),
                                                  captor.capture(), anyString(),
                                                  any(Principal.class), any());
      assertEquals("Revenue", captor.getValue().getTitleFormatPaneModel().getTitle());
   }

   @Test
   void refusesAnUnknownRegionNamingTheThree() {
      Harness h = harness();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.list("tok", principal(), "Chart1", "plot", "y", null));
      assertTrue(thrown.getMessage().contains("plot"));
      assertTrue(thrown.getMessage().contains("axis"));
      assertTrue(thrown.getMessage().contains("legend"));
   }

   /**
    * The composer service parses the legend index while READING the model, so a non-numeric
    * target surfaced as a raw {@code For input string: "..."} from inside StyleBI before the
    * write-side guard was reached. Validating the target up front is what makes the message
    * useful. Found live, not by unit test.
    */
   @Test
   void refusesANonNumericLegendTargetBeforeTouchingTheComposerService() throws Exception {
      Harness h = harness();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.list("tok", principal(), "Chart1", "legend", "notanumber", null));

      assertTrue(thrown.getMessage().contains("0-based index"));
      assertTrue(thrown.getMessage().contains("notanumber"));
      verify(h.regions, never()).getLegendFormatDialogModel(anyString(), anyString(), anyString(),
                                                            anyString(), any(Principal.class));
   }

   /**
    * An axis target that names no axis was accepted in silence on the read path — {@code "PAID"}
    * and {@code "zzzznonsense"} both returned the same full, plausible property list as
    * {@code "y"} — and blew up with {@code NullPointerException: axisArea is null} on the write
    * path. Both come from {@code ChartRegionHandler.getAxisArea} returning null for an unrecognised
    * type.
    *
    * <p>A column name is the specific wrong value worth predicting: {@code list_chart_elements}
    * describes an axis target as a column name (true for element visibility, not for this tool),
    * so an agent following it lands here. This tool takes the column in {@code field}, so the
    * message says so rather than only listing the valid types. Found live on local-1196.
    */
   @Test
   void refusesAnAxisTargetThatNamesNoAxis() {
      Harness h = harness();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.list("tok", principal(), "Chart1", "axis", "PAID", null));

      assertTrue(thrown.getMessage().contains("PAID"));
      assertTrue(thrown.getMessage().contains("y2"), "the message must list the valid axis types");
      assertTrue(thrown.getMessage().contains("field"),
                 "a column name belongs in 'field' — say so, since that is the likely mistake");
   }

   /** The long forms the composer itself uses are equally valid and must not be refused. */
   @Test
   void acceptsTheLongAxisFormsToo() throws Exception {
      Harness h = harness();
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(axisModel());

      Map<String, Object> listed =
         h.service.list("tok", principal(), "Chart1", "axis", "left_y_axis", null);

      assertEquals("left_y_axis", listed.get("target"));
   }

   /** Forgiving where the intent is unambiguous: case is normalised rather than refused. */
   @Test
   void normalisesAxisTargetCase() throws Exception {
      Harness h = harness();
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(axisModel());

      Map<String, Object> listed = h.service.list("tok", principal(), "Chart1", "axis", "Y2", null);

      assertEquals("y2", listed.get("target"));
   }

   @Test
   void refusesAMissingTarget() {
      Harness h = harness();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.list("tok", principal(), "Chart1", "axis", "  ", null));
      assertTrue(thrown.getMessage().contains("target"));
   }

   /**
    * The composer service returns a fully-populated dialog model; a bare {@code new} leaves its
    * panes null, and PropertyPath deliberately refuses to instantiate an absent intermediate
    * (a null pane means "this property does not apply here"). So the fixtures populate them.
    */
   private static AxisPropertyDialogModel axisModel() {
      AxisPropertyDialogModel model = new AxisPropertyDialogModel();
      model.setAxisLinePaneModel(new AxisLinePaneModel());
      model.setAxisLabelPaneModel(new AxisLabelPaneModel());
      return model;
   }

   private static LegendFormatDialogModel legendModel() {
      LegendFormatDialogModel model = new LegendFormatDialogModel();
      model.setLegendFormatGeneralPaneModel(new LegendFormatGeneralPaneModel());
      return model;
   }

   private static TitleFormatDialogModel titleModel() {
      TitleFormatDialogModel model = new TitleFormatDialogModel();
      model.setTitleFormatPaneModel(new TitleFormatPaneModel());
      return model;
   }

   private record Harness(ChartRegionPropertyService service, RegionPropertyDialogService regions) {}

   private static Harness harness() {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);

      try {
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         when(sessions.runtimeId(anyString(), any(Principal.class))).thenReturn("rt1");
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      RegionPropertyDialogService regions = mock(RegionPropertyDialogService.class);
      return new Harness(new ChartRegionPropertyService(sessions, regions), regions);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
