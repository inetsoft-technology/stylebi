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
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.composer.vs.dialog.RegionPropertyDialogService;
import inetsoft.web.graph.model.dialog.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;
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

   /**
    * A categorical (dimension) axis given a linear-only property used to be silently accepted:
    * {@code getAxisPropertyDialogModel} always asked for area index 0, which
    * {@code ChartRegionHandler.createAxisPropertyDialogModel} used to <em>infer</em> linearity
    * from whichever leaf area happened to sort first on screen rather than from the bound field,
    * so an ordinary bottom x-axis came back {@code isLinear: true} regardless of what was bound
    * to it. Live evidence: {@code minimum:"5"} on a year-grouped date dimension was persisted and
    * rendered as a fabricated numeric axis, corrupting the chart (2026-09-02). This checks
    * linearity independently, off the actual bound ref, before the buggy area-index path is ever
    * reached.
    */
   @Test
   void refusesLinearOnlyAxisPropertiesOnADimensionAxis() {
      Harness h = harness();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", "axis", "x", null,
                             Map.of("minimum", "5", "reverse", true), ""));

      assertTrue(thrown.getMessage().contains("minimum"));
      assertTrue(thrown.getMessage().contains("reverse"));
      assertTrue(thrown.getMessage().contains("'x'"));
      verifyNoInteractions(h.regions);
   }

   /** The same categorical axis must still accept properties that apply regardless of type. */
   @Test
   void stillAcceptsNonLinearAxisPropertiesOnADimensionAxis() throws Exception {
      Harness h = harness();
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(axisModel());

      h.service.set("tok", principal(), "Chart1", "axis", "x", null,
                    Map.of("showAxisLine", false, "ignoreNull", true), "");

      verify(h.regions).setAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyInt(),
                                                   any(), any(), anyString(), any(Principal.class),
                                                   any());
   }

   /** The genuinely linear y-axis (a real measure, per the default harness) must be unaffected. */
   @Test
   void stillAcceptsLinearOnlyAxisPropertiesOnAMeasureAxis() throws Exception {
      Harness h = harness();
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(axisModel());

      h.service.set("tok", principal(), "Chart1", "axis", "y", null,
                    Map.of("minimum", "5", "reverse", true), "");

      verify(h.regions).setAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyInt(),
                                                   any(), any(), anyString(), any(Principal.class),
                                                   any());
   }

   /**
    * A repair-review catch, live 2026-09-02: the first cut of the linearity check asked "does
    * ANY field on this shelf happen to be a measure" instead of resolving the specific field this
    * call addresses. On a shelf carrying both a dimension and a measure of the same axis type —
    * exactly the case {@code vocabulary()}'s own note describes ("to address one of several axes
    * of the same type... pass the column name as 'field'") — that let a write aimed at the
    * dimension slip through because a measure happened to sit on the same shelf, reopening the
    * exact corruption this fix exists to close.
    */
   @Test
   void refusesLinearOnlyKeysOnADimensionFieldEvenWhenTheShelfAlsoHasAMeasure() {
      VSChartDimensionRef dimension = mock(VSChartDimensionRef.class);
      when(dimension.getFullName()).thenReturn("Year(ORDER_DATE)");
      when(dimension.getName()).thenReturn("ORDER_DATE");
      VSChartAggregateRef measure = mock(VSChartAggregateRef.class);
      when(measure.isSecondaryY()).thenReturn(false);
      when(measure.getFullName()).thenReturn("Sum(Total)");

      Harness h = harness(mixedShelfViewsheet(new ChartRef[] { dimension, measure }));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", "axis", "x", "Year(ORDER_DATE)",
                             Map.of("minimum", "5"), ""));

      assertTrue(thrown.getMessage().contains("minimum"));
      verifyNoInteractions(h.regions);
   }

   /** The measure on that same mixed shelf must still be reachable via its own 'field'. */
   @Test
   void stillAcceptsLinearOnlyKeysOnTheMeasureFieldOfAMixedShelf() throws Exception {
      VSChartDimensionRef dimension = mock(VSChartDimensionRef.class);
      when(dimension.getFullName()).thenReturn("Year(ORDER_DATE)");
      VSChartAggregateRef measure = mock(VSChartAggregateRef.class);
      when(measure.isSecondaryY()).thenReturn(false);
      when(measure.getFullName()).thenReturn("Sum(Total)");
      when(measure.getName()).thenReturn("Sum(Total)");

      Harness h = harness(mixedShelfViewsheet(new ChartRef[] { dimension, measure }));
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(axisModel());

      h.service.set("tok", principal(), "Chart1", "axis", "x", "Sum(Total)",
                    Map.of("minimum", "5"), "");

      verify(h.regions).setAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyInt(),
                                                   any(), any(), anyString(), any(Principal.class),
                                                   any());
   }

   /**
    * A repair-review catch, live 2026-09-02: the first cut only required
    * {@code isSecondaryY()} on the SECONDARY side ({@code !secondary || isSecondaryY()}), which
    * degrades to "any measure at all" on the PRIMARY side. A chart whose only y-shelf measure is
    * itself flagged secondary must still refuse a write to the primary 'y' — that primary axis is
    * a grid-line carrier mirroring the real (secondary) one, per
    * {@code ChartRegionResolver}'s own documentation, not a genuine linear axis of its own.
    */
   @Test
   void refusesLinearOnlyKeysOnAPrimaryAxisWhoseOnlyMeasureIsFlaggedSecondary() {
      VSChartAggregateRef onlySecondary = mock(VSChartAggregateRef.class);
      when(onlySecondary.isSecondaryY()).thenReturn(true);
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.getXFields()).thenReturn(new ChartRef[] { mock(VSChartDimensionRef.class) });
      when(info.getYFields()).thenReturn(new ChartRef[] { onlySecondary });
      when(info.isInvertedGraph()).thenReturn(false);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(chart);

      Harness h = harness(vs);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", "axis", "y", null,
                             Map.of("minimum", "5"), ""));

      assertTrue(thrown.getMessage().contains("minimum"));
      verifyNoInteractions(h.regions);
   }

   /** L4 finding G3-3: the legend Scale tab (logarithmic/reverse/includeZero) had no tool alias. */
   @Test
   void writesLegendScaleProperties() throws Exception {
      Harness h = harness();
      when(h.regions.getLegendFormatDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(Principal.class)))
         .thenReturn(legendModel());

      h.service.set("tok", principal(), "Chart1", "legend", "0", null,
                    Map.of("logarithmicScale", true, "reverse", true, "includeZero", true), "");

      ArgumentCaptor<LegendFormatDialogModel> captor =
         ArgumentCaptor.forClass(LegendFormatDialogModel.class);
      verify(h.regions).setLegendFormatDialogModel(anyString(), anyString(), anyInt(),
                                                   captor.capture(), anyString(),
                                                   any(Principal.class), any());
      LegendScalePaneModel scale = captor.getValue().getLegendScalePaneModel();
      assertTrue(scale.isLogarithmic());
      assertTrue(scale.isReverse());
      assertTrue(scale.isIncludeZero());
   }

   /** L4 finding G3-5: the Title Properties dialog's Rotation fieldset had no tool alias. */
   @Test
   void writesTitleRotation() throws Exception {
      Harness h = harness();
      when(h.regions.getTitleFormatDialogModel(anyString(), anyString(), anyString(), anyString(),
                                               any(Principal.class)))
         .thenReturn(titleModel());

      h.service.set("tok", principal(), "Chart1", "title", "y", null, Map.of("rotation", "-90"), "");

      ArgumentCaptor<TitleFormatDialogModel> captor =
         ArgumentCaptor.forClass(TitleFormatDialogModel.class);
      verify(h.regions).setTitleFormatDialogModel(anyString(), anyString(), anyString(),
                                                  captor.capture(), anyString(),
                                                  any(Principal.class), any());
      assertEquals("-90",
                   captor.getValue().getTitleFormatPaneModel().getRotationRadioGroupModel()
                      .getRotation());
   }

   /**
    * The title's rotation does not accept "auto" -- unlike the axis label's, its own persist step
    * calls {@code Float.parseFloat} on whatever arrives with no "auto" branch at all, which would
    * otherwise surface as a raw {@code NumberFormatException} instead of a named refusal.
    */
   @Test
   void refusesAutoRotationOnATitle() {
      Harness h = harness();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", "title", "y", null,
                             Map.of("rotation", "auto"), ""));

      assertTrue(thrown.getMessage().contains("rotation"));
      verifyNoInteractions(h.regions);
   }

   /** L4 finding G3-6: the axis Label tab's Rotation fieldset had no tool alias. */
   @Test
   void writesAxisLabelRotation() throws Exception {
      Harness h = harness();
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(axisModel());

      h.service.set("tok", principal(), "Chart1", "axis", "y", null, Map.of("rotation", "45"), "");

      ArgumentCaptor<AxisPropertyDialogModel> captor =
         ArgumentCaptor.forClass(AxisPropertyDialogModel.class);
      verify(h.regions).setAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyInt(),
                                                   any(), captor.capture(), anyString(),
                                                   any(Principal.class), any());
      assertEquals("45",
                   captor.getValue().getAxisLabelPaneModel().getRotationRadioGroupModel()
                      .getRotation());
   }

   /** Unlike the title, the axis label genuinely offers "auto" -- and normalizes its case. */
   @Test
   void writesAutoRotationOnAnAxisCaseInsensitively() throws Exception {
      Harness h = harness();
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(axisModel());

      h.service.set("tok", principal(), "Chart1", "axis", "y", null, Map.of("rotation", "AUTO"), "");

      ArgumentCaptor<AxisPropertyDialogModel> captor =
         ArgumentCaptor.forClass(AxisPropertyDialogModel.class);
      verify(h.regions).setAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyInt(),
                                                   any(), captor.capture(), anyString(),
                                                   any(Principal.class), any());
      assertEquals("auto",
                   captor.getValue().getAxisLabelPaneModel().getRotationRadioGroupModel()
                      .getRotation(),
                   "AxisPropertyDialogModel matches \"auto\" case-sensitively, so the canonical " +
                   "lowercase form must reach it, not the caller's original casing");
   }

   @Test
   void refusesARotationOutsideEitherDomain() {
      Harness h = harness();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", "axis", "y", null,
                             Map.of("rotation", "30"), ""));

      assertTrue(thrown.getMessage().contains("30"));
      verifyNoInteractions(h.regions);
   }

   /** L4 finding G3-4: the legend Alias tab (per-value label overrides) had no tool alias. */
   @Test
   void writesLegendAliases() throws Exception {
      Harness h = harness();
      when(h.regions.getLegendFormatDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(Principal.class)))
         .thenReturn(legendModel());

      h.service.set("tok", principal(), "Chart1", "legend", "0", null,
                    Map.of("aliases", List.of(
                       Map.of("value", "2022", "alias", "FY22"),
                       Map.of("value", "2023", "alias", "FY23"))),
                    "");

      ArgumentCaptor<LegendFormatDialogModel> captor =
         ArgumentCaptor.forClass(LegendFormatDialogModel.class);
      verify(h.regions).setLegendFormatDialogModel(anyString(), anyString(), anyInt(),
                                                   captor.capture(), anyString(),
                                                   any(Principal.class), any());
      ModelAlias[] written = captor.getValue().getAliasPaneModel().getAliasList();
      assertEquals(2, written.length);
      assertEquals("2022", written[0].getValue());
      assertEquals("FY22", written[0].getAlias());
      assertEquals("2023", written[1].getValue());
      assertEquals("FY23", written[1].getAlias());
   }

   /** L4 finding G3-7: the axis Alias tab (per-value label overrides) had no tool alias. */
   @Test
   void writesAxisAliases() throws Exception {
      Harness h = harness();
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(axisModel());

      h.service.set("tok", principal(), "Chart1", "axis", "y", null,
                    Map.of("aliases", List.of(Map.of("value", "West", "alias", "Region West"))),
                    "");

      ArgumentCaptor<AxisPropertyDialogModel> captor =
         ArgumentCaptor.forClass(AxisPropertyDialogModel.class);
      verify(h.regions).setAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyInt(),
                                                   any(), captor.capture(), anyString(),
                                                   any(Principal.class), any());
      ModelAlias[] written = captor.getValue().getAliasPaneModel().getAliasList();
      assertEquals(1, written.length);
      assertEquals("West", written[0].getValue());
      assertEquals("Region West", written[0].getAlias());
      assertEquals("West", written[0].getLabel(), "label defaults to the value when omitted");
   }

   /**
    * The bug this test exists to pin, found live 2026-09-02 by this audit's own promotion pass
    * on a real year-grouped axis: {@code AxisPropertyDialogModel.updateAxisPropertyDialogModel}
    * calls {@code axisDesc.setLabelAlias(value, alias)} with WHATEVER value this service hands
    * it, unconditionally -- it does not itself match against the real items. A caller supplying
    * the display text ({@code "2022"}) rather than the real underlying value (a date-grouped
    * axis's real value is a full timestamp, e.g. {@code "2022-01-01 00:00:00"}) used to have
    * that alias silently stored under a key nothing ever reads back -- {@code ok:true}, chart
    * unchanged. This asserts the value actually written is the REAL one, resolved from the
    * axis's own current alias list (mirroring what list_chart_region_properties already reports),
    * not the caller's display-text guess.
    */
   @Test
   void resolvesAnAxisAliasValueSuppliedAsItsDisplayLabel() throws Exception {
      Harness h = harness();
      AxisPropertyDialogModel model = axisModel();
      model.getAliasPaneModel().setAliasList(new ModelAlias[] {
         new ModelAlias("2022", "2022-01-01 00:00:00", "2022"),
         new ModelAlias("2023", "2023-01-01 00:00:00", "2023"),
      });
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(model);

      h.service.set("tok", principal(), "Chart1", "axis", "x", null,
                    Map.of("aliases", List.of(Map.of("value", "2022", "alias", "FY22"))), "");

      ArgumentCaptor<AxisPropertyDialogModel> captor =
         ArgumentCaptor.forClass(AxisPropertyDialogModel.class);
      verify(h.regions).setAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyInt(),
                                                   any(), captor.capture(), anyString(),
                                                   any(Principal.class), any());
      ModelAlias written = captor.getValue().getAliasPaneModel().getAliasList()[0];
      assertEquals("2022-01-01 00:00:00", written.getValue(),
                   "the REAL value must be written, not the caller's display-text guess -- " +
                   "AxisPropertyDialogModel keys setLabelAlias on whatever value arrives here " +
                   "with no matching of its own");
      assertEquals("FY22", written.getAlias());
   }

   /** Supplying the real value directly (not the label) must also resolve, unchanged. */
   @Test
   void resolvesAnAxisAliasValueSuppliedAsTheRealValue() throws Exception {
      Harness h = harness();
      AxisPropertyDialogModel model = axisModel();
      model.getAliasPaneModel().setAliasList(new ModelAlias[] {
         new ModelAlias("2022", "2022-01-01 00:00:00", "2022"),
      });
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(model);

      h.service.set("tok", principal(), "Chart1", "axis", "x", null,
                    Map.of("aliases",
                           List.of(Map.of("value", "2022-01-01 00:00:00", "alias", "FY22"))),
                    "");

      ArgumentCaptor<AxisPropertyDialogModel> captor =
         ArgumentCaptor.forClass(AxisPropertyDialogModel.class);
      verify(h.regions).setAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyInt(),
                                                   any(), captor.capture(), anyString(),
                                                   any(Principal.class), any());
      assertEquals("2022-01-01 00:00:00",
                   captor.getValue().getAliasPaneModel().getAliasList()[0].getValue());
   }

   /**
    * A value matching neither the real value nor the label used to reach the same silent-no-op
    * shape resolvesAnAxisAliasValueSuppliedAsItsDisplayLabel pins -- refused here instead, naming
    * what the axis actually has, so the caller can retry with a value that will actually work.
    */
   @Test
   void refusesAnAxisAliasValueMatchingNeitherRealValueNorLabel() throws Exception {
      Harness h = harness();
      AxisPropertyDialogModel model = axisModel();
      model.getAliasPaneModel().setAliasList(new ModelAlias[] {
         new ModelAlias("2022", "2022-01-01 00:00:00", "2022"),
      });
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(model);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", "axis", "x", null,
                             Map.of("aliases", List.of(Map.of("value", "9999", "alias", "FY99"))),
                             ""));

      assertTrue(thrown.getMessage().contains("9999"));
      assertTrue(thrown.getMessage().contains("2022"));
      verify(h.regions, never()).setAxisPropertyDialogModel(anyString(), anyString(), anyString(),
                                                            anyInt(), any(), any(), anyString(),
                                                            any(Principal.class), any());
   }

   @Test
   void refusesAnAliasEntryMissingAlias() {
      Harness h = harness();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", "legend", "0", null,
                             Map.of("aliases", List.of(Map.of("value", "2022"))), ""));

      assertTrue(thrown.getMessage().contains("alias"));
      verifyNoInteractions(h.regions);
   }

   @Test
   void refusesANonArrayAliasesValue() {
      Harness h = harness();

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", "legend", "0", null,
                             Map.of("aliases", "not-an-array"), ""));

      assertTrue(thrown.getMessage().contains("array"));
      verifyNoInteractions(h.regions);
   }

   private static Viewsheet mixedShelfViewsheet(ChartRef[] xFields) {
      VSChartAggregateRef y = mock(VSChartAggregateRef.class);
      when(y.isSecondaryY()).thenReturn(false);
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.getXFields()).thenReturn(xFields);
      when(info.getYFields()).thenReturn(new ChartRef[] { y });
      when(info.isInvertedGraph()).thenReturn(false);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(chart);
      return vs;
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

   /**
    * The legend's {@code title} property must land on {@code titleValue} — the field
    * {@code legend-format-general-pane.component.html} actually binds
    * ({@code [(value)]="model.titleValue"}; {@code title} is only the read-only {@code origValue}
    * placeholder). Aliasing it onto {@code title} used to return {@code ok:true} and change
    * nothing, since {@code LegendFormatDialogModel.updateLegendFormatDialogModel} only ever reads
    * {@code getTitleValue()}. Found live 2026-09-02.
    */
   @Test
   void writesLegendTitleOntoTheEditableTitleValueField() throws Exception {
      Harness h = harness();
      when(h.regions.getLegendFormatDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(Principal.class)))
         .thenReturn(legendModel());

      h.service.set("tok", principal(), "Chart1", "legend", "0", null,
                    Map.of("title", "Revenue by Year"), "");

      ArgumentCaptor<LegendFormatDialogModel> captor =
         ArgumentCaptor.forClass(LegendFormatDialogModel.class);
      verify(h.regions).setLegendFormatDialogModel(anyString(), anyString(), anyInt(),
                                                   captor.capture(), anyString(),
                                                   any(Principal.class), any());
      assertEquals("Revenue by Year",
                   captor.getValue().getLegendFormatGeneralPaneModel().getTitleValue());
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

   // ── an axis the chart does not have ───────────────────────────────────────

   /**
    * <b>The phantom axis.</b> {@code ChartArea} builds all four axis areas unconditionally, so
    * {@code ChartRegionHandler.getAxisArea} hands back an empty shell for {@code y2} on a chart
    * with no secondary measure — and this service used to describe it with the full y1 property
    * list. The read is guarded as well as the write, because the read is what talked the caller
    * into the write.
    */
   @Test
   void refusesToDescribeAnAxisTheChartDoesNotHave() {
      Harness h = harness(false);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.list("tok", principal(), "Chart1", "axis", "y2", null));

      assertTrue(thrown.getMessage().contains("y2"));
      assertTrue(thrown.getMessage().contains("x, y"), "name the axes it does have");
   }

   @Test
   void refusesToWriteToAnAxisTheChartDoesNotHave() throws Exception {
      Harness h = harness(false);

      assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", "axis", "y2", null,
                             Map.of("logarithmicScale", true), ""));

      verify(h.regions, never()).setAxisPropertyDialogModel(
         anyString(), anyString(), anyString(), anyInt(), any(), any(), anyString(),
         any(Principal.class), any());
   }

   /** The long alias must not be a way around the check — it reaches the same axis. */
   @Test
   void refusesTheLongAliasForAPhantomAxisToo() {
      Harness h = harness(false);

      assertThrows(
         IllegalArgumentException.class,
         () -> h.service.list("tok", principal(), "Chart1", "axis", "right_y_axis", null));
   }

   /** An axis title for an axis that is not there is the same fiction, one region over. */
   @Test
   void refusesATitleForAnAxisTheChartDoesNotHave() {
      Harness h = harness(false);

      assertThrows(
         IllegalArgumentException.class,
         () -> h.service.list("tok", principal(), "Chart1", "title", "y2", null));
   }

   /**
    * <b>Found live, 2026-08-20.</b> {@code region: "title", target: "chart"} returned a raw HTTP
    * 500: {@code TitlesDescriptor} holds only x/x2/y/y2 descriptors, so
    * {@code ChartRegionHandler.getTitleArea} returns null for "chart" and
    * {@code RegionPropertyDialogService} dereferences it. The chart's own title is an assembly
    * property, not a region — so this is a refusal that names where the title actually lives,
    * against the same clean-refusal standard the rest of this surface meets.
    */
   @Test
   void refusesTheChartTitleAndSaysWhereItActuallyLives() {
      Harness h = harness(false);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.list("tok", principal(), "Chart1", "title", "chart", null));

      assertTrue(thrown.getMessage().contains("set_assembly_properties"),
                 "a refusal has to name the tool that does work");
   }

   /**
    * <b>Found live 2026-08-20.</b> A y2 write landed on the primary axis, because
    * {@code ChartRegionHandler.getChartRef} does not know the short {@code "y2"} form while
    * {@code getAxisArea} does — so the area resolved, the descriptor did not, and the chain fell
    * back to the descriptor shared with the primary axis. Both the read and the write have to
    * send the long form, or they disagree with each other.
    */
   @Test
   void sendsTheLongAxisFormForY2SoTheDescriptorResolves() throws Exception {
      Harness h = harness();
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(axisModel());

      Map<String, Object> listed =
         h.service.list("tok", principal(), "Chart1", "axis", "y2", null);

      verify(h.regions).getAxisPropertyDialogModel(anyString(), anyString(), eq("right_y_axis"),
                                                   anyString(), any(), anyString(),
                                                   any(Principal.class));
      assertEquals("y2", listed.get("target"), "the caller's own vocabulary comes back unchanged");
   }

   @Test
   void theWriteSendsTheSameLongFormAsTheRead() throws Exception {
      Harness h = harness();
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(axisModel());

      h.service.set("tok", principal(), "Chart1", "axis", "y2", null,
                    Map.of("showAxisLabel", false), "");

      verify(h.regions).setAxisPropertyDialogModel(anyString(), anyString(), eq("right_y_axis"),
                                                   anyInt(), any(), any(), anyString(),
                                                   any(Principal.class), any());
   }

   /** The primary forms are already understood downstream and must not be rewritten. */
   @Test
   void leavesThePrimaryAxisFormsAlone() throws Exception {
      Harness h = harness();
      when(h.regions.getAxisPropertyDialogModel(anyString(), anyString(), anyString(), anyString(),
                                                any(), anyString(), any(Principal.class)))
         .thenReturn(axisModel());

      h.service.list("tok", principal(), "Chart1", "axis", "y", null);

      verify(h.regions).getAxisPropertyDialogModel(anyString(), anyString(), eq("y"), anyString(),
                                                   any(), anyString(), any(Principal.class));
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
      AxisLabelPaneModel labelPaneModel = new AxisLabelPaneModel();
      labelPaneModel.setRotationRadioGroupModel(new RotationRadioGroupModel());
      model.setAxisLabelPaneModel(labelPaneModel);
      model.setAliasPaneModel(new AliasPaneModel());
      return model;
   }

   private static LegendFormatDialogModel legendModel() {
      LegendFormatDialogModel model = new LegendFormatDialogModel();
      model.setLegendFormatGeneralPaneModel(new LegendFormatGeneralPaneModel());
      model.setLegendScalePaneModel(new LegendScalePaneModel());
      model.setAliasPaneModel(new AliasPaneModel());
      return model;
   }

   private static TitleFormatDialogModel titleModel() {
      TitleFormatDialogModel model = new TitleFormatDialogModel();
      TitleFormatPaneModel paneModel = new TitleFormatPaneModel();
      paneModel.setRotationRadioGroupModel(new RotationRadioGroupModel());
      model.setTitleFormatPaneModel(paneModel);
      return model;
   }

   private record Harness(ChartRegionPropertyService service, RegionPropertyDialogService regions) {}

   private static Harness harness() {
      // The default chart carries a secondary measure, so every pre-existing case that addresses
      // y2 is still addressing an axis that exists. Pass false for the phantom-axis cases.
      return harness(true);
   }

   private static Harness harness(boolean secondaryAxis) {
      return harness(viewsheet(secondaryAxis));
   }

   private static Harness harness(Viewsheet vs) {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      try {
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         when(sessions.runtimeId(anyString(), any(Principal.class))).thenReturn("rt1");
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
         doAnswer(invocation -> {
            ViewsheetSessionService.Read<?> read = invocation.getArgument(2);
            return read.run(rvs, "rt1", null);
         }).when(sessions).read(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      RegionPropertyDialogService regions = mock(RegionPropertyDialogService.class);
      return new Harness(new ChartRegionPropertyService(sessions, regions), regions);
   }

   /**
    * A chart bound with a dimension on x and one or two measures on y. A mocked runtime has no
    * sandbox, so the axis resolver falls back to the binding — which is the point: these tests
    * pin what the binding implies, and the laid-out graph is the live case's business.
    */
   private static Viewsheet viewsheet(boolean secondaryAxis) {
      // Built before any stubbing: mocking inside a when(...) argument is nested stubbing.
      VSChartAggregateRef primary = mock(VSChartAggregateRef.class);
      when(primary.isSecondaryY()).thenReturn(false);
      ChartRef[] y = new ChartRef[] { primary };

      if(secondaryAxis) {
         VSChartAggregateRef secondary = mock(VSChartAggregateRef.class);
         when(secondary.isSecondaryY()).thenReturn(true);
         y = new ChartRef[] { primary, secondary };
      }

      ChartRef[] x = new ChartRef[] { mock(VSChartDimensionRef.class) };
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.getXFields()).thenReturn(x);
      when(info.getYFields()).thenReturn(y);
      when(info.isInvertedGraph()).thenReturn(false);

      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(chart);
      return vs;
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
