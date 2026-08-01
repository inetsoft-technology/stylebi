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
package inetsoft.web.wiz.service;

import inetsoft.report.StyleConstants;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.MirrorTableAssembly;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TitledVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizDashboardFilterBuilderTest {
   private final WizDashboardFilterBuilder builder = new WizDashboardFilterBuilder();

   @Test
   void categoricalFieldMakesASelectionList() {
      Viewsheet vs = new Viewsheet();
      // A field on NO table binds to nothing -> skipped, but the control TYPE is still decided
      // by data type. Assert via a package-visible helper the builder exposes for the type choice:
      AbstractSelectionVSAssembly a = builder.createControlForType(vs, "string", column("Region", "string"));
      assertTrue(a instanceof SelectionListVSAssembly);
   }

   @Test
   void dateAndNumericFieldsMakeRangeSliders() {
      Viewsheet vs = new Viewsheet();
      assertTrue(builder.createControlForType(vs, "date", column("OrderDate", "date")) instanceof TimeSliderVSAssembly);
      assertTrue(builder.createControlForType(vs, "integer", column("Qty", "integer")) instanceof TimeSliderVSAssembly);
   }

   @Test
   void theSharedFilterFactoryStaysInListModeForOtherCallers() {
      // createControlForType delegates to AddFilterService.createFilterAssembly, which the
      // non-dashboard add-filter flow also uses. Dashboard compaction must NOT leak into it.
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly a =
         (SelectionListVSAssembly) builder.createControlForType(vs, "string", column("Region", "string"));
      assertEquals(SelectionVSAssemblyInfo.LIST_SHOW_TYPE, a.getSelectionListInfo().getShowType(),
         "the shared factory must keep StyleBI's default list rendering");
   }

   @Test
   void sharedBarCategoricalFilterRendersAsADropdownNotATallList() {
      // THE BUG: the shared bar reserves FILTER_CONTROL_HEIGHT - FILTER_LABEL_HEIGHT (44px) for a
      // control. In list mode a SelectionList draws a title row plus ~20px item rows inside that,
      // leaving exactly ONE item visible in a scroll area -- unusable for choosing among, say, 141
      // customer names, which is precisely what the FK-label filter feature puts on this bar.
      // Dropdown mode was applied to the per-chart path only; the shared bar was missed.
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "SO", "res_partner_name"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "C");
      boundToTable(chart, "SO");
      vs.addAssembly(chart);

      builder.build(vs, ws, List.of(
         new WizDashboardFilterBuilder.FilterRequest("res_partner_name", "string", "Customer")), 0);

      SelectionListVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof SelectionListVSAssembly)
         .map(a -> (SelectionListVSAssembly) a)
         .findFirst().orElseThrow();

      assertEquals(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE, control.getSelectionListInfo().getShowType(),
         "a shared-bar categorical filter must render as a dropdown, not a multi-row list");
   }

   @Test
   void sharedBarDropdownTitleRowFillsTheHeightTheBarReserves() {
      // A dropdown draws ONLY its title row and ignores the rest of the assembly's pixel height, so
      // reserving more than it draws leaves the difference as a gap in the filter bar. Pin the row
      // to the height the bar actually reserves rather than relying on two constants matching.
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "SO", "res_partner_name"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "C");
      boundToTable(chart, "SO");
      vs.addAssembly(chart);

      WizDashboardFilterBuilder.FilterResult result = builder.build(vs, ws, List.of(
         new WizDashboardFilterBuilder.FilterRequest("res_partner_name", "string", "Customer")), 0);

      SelectionListVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof SelectionListVSAssembly)
         .map(a -> (SelectionListVSAssembly) a)
         .findFirst().orElseThrow();
      int reserved = result.placements().stream()
         .filter(pl -> pl.assemblyName().equals(control.getName()))
         .findFirst().orElseThrow().size().height;

      assertEquals(reserved, control.getSelectionListInfo().getTitleHeightValue(),
         "the dropdown's title row must fill exactly the height the bar reserved for it");
   }

   @Test
   void sharedBarDropdownTitleIsVerticallyCentredNotPinnedToTheTopOfItsRow() {
      // THE BUG (live screenshot): the dropdown's title text sat hard against the TOP of its 60px
      // row with an obvious empty band beneath it. Nothing in this path calls initDefaultFormat(),
      // which is the only thing that seeds a TITLEPATH format (with H_LEFT|V_CENTER); with no entry
      // the title inherits the OBJECT format's default alignment, VSFormat.ALIGN = H_LEFT|V_TOP,
      // which reaches the client as align-items:flex-start on the title cell.
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "SO", "res_partner_name"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "C");
      boundToTable(chart, "SO");
      vs.addAssembly(chart);

      builder.build(vs, ws, List.of(
         new WizDashboardFilterBuilder.FilterRequest("res_partner_name", "string", "Customer")), 0);

      VSCompositeFormat titleFormat = dropdown(vs).getVSAssemblyInfo().getFormatInfo()
         .getFormat(VSAssemblyInfo.TITLEPATH);
      assertNotNull(titleFormat, "the dropdown needs its own TITLEPATH format to align at all");
      // The DESIGN value is the one that survives the composed dashboard being saved and reopened.
      assertTrue((titleFormat.getAlignmentValue() & StyleConstants.V_CENTER) != 0,
                 "the dropdown's title must be vertically centred in the row the bar reserves");
      assertTrue((titleFormat.getAlignment() & StyleConstants.V_CENTER) != 0,
                 "...and the effective (runtime) alignment must agree");
      // V_TOP is what it inherited before the fix; asserting its ABSENCE is what distinguishes
      // "centred" from "both bits set", which fixAlignment would happily store.
      assertEquals(0, titleFormat.getAlignmentValue() & StyleConstants.V_TOP);
      // Horizontal alignment must be carried along: fixAlignment keeps the horizontal and vertical
      // bits independently, so writing V_CENTER alone would zero H_LEFT and centre the text
      // horizontally too.
      assertTrue((titleFormat.getAlignmentValue() & StyleConstants.H_LEFT) != 0,
                 "the title must stay left-aligned horizontally");
   }

   @Test
   void sharedBarDropdownPopupHasAnOpaqueBackgroundThatSurvivesAReload() {
      // THE BUG (live screenshot): the OPEN dropdown list painted transparently on top of the chart
      // behind it -- its items overlapped chart content and were unreadable. The popup has no
      // background of its own (.selection-list-body carries none); the whole assembly is painted by
      // the single [style.background-color]="model.objectFormat.background" binding on .vs-object,
      // and that was unset because nothing here calls initDefaultFormat().
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "SO", "res_partner_name"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "C");
      boundToTable(chart, "SO");
      vs.addAssembly(chart);

      builder.build(vs, ws, List.of(
         new WizDashboardFilterBuilder.FilterRequest("res_partner_name", "string", "Customer")), 0);

      VSCompositeFormat objectFormat = dropdown(vs).getVSAssemblyInfo().getFormat();
      // Assert through the getter that SURVIVES a save/reload -- the design "...Value" layer. The
      // plain setter's isBackgroundDefined flag does not survive the round-trip (see
      // applyCardFormat), so a background set only that way looks right here and vanishes for the
      // user; reading getBackgroundValue is what makes this test able to tell the difference.
      assertEquals("#ffffff",
                   objectFormat.getUserDefinedFormat().getBackgroundValue(),
                   "the popup needs an opaque background that survives save/reload");
      assertTrue(objectFormat.getUserDefinedFormat().isBackgroundValueDefined(),
                 "...and the value must be flagged defined, or the composite getter ignores it");
      // The effective (composite) getter is what actually renders.
      assertEquals(java.awt.Color.WHITE, objectFormat.getBackground());
      // A floating list needs an outline too, or it bleeds into the chart it covers.
      assertEquals(inetsoft.report.StyleConstants.THIN_LINE, objectFormat.getBorders().top);
      assertEquals(inetsoft.report.StyleConstants.THIN_LINE, objectFormat.getBorders().bottom);
      assertNotNull(objectFormat.getBorderColors());
   }

   @Test
   void sharedBarDropdownGetsNoCaptionBecauseItDrawsItsOwnTitleRow() {
      // THE BUG (live screenshot): "Res Partner Name" appeared TWICE -- once as the caption assembly
      // above the control and again as the dropdown's own title row. The caption exists for the
      // TimeSlider, which renders no title at all; a dropdown does, so for it the caption is a
      // duplicate.
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "SO", "res_partner_name"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "C");
      boundToTable(chart, "SO");
      vs.addAssembly(chart);

      WizDashboardFilterBuilder.FilterResult result = builder.build(vs, ws, List.of(
         new WizDashboardFilterBuilder.FilterRequest("res_partner_name", "string", "Customer")), 0);

      SelectionListVSAssembly control = dropdown(vs);
      assertNull(vs.getAssembly("wizFilterLabel_" + control.getName()),
                 "a dropdown draws its own title -- a caption assembly would print the label twice");
      assertTrue(result.placements().stream().noneMatch(pl -> pl.assemblyName().startsWith("wizFilterLabel_")),
                 "and no caption placement may be returned either");
      // The label still reaches the user -- through the control's OWN title row.
      assertEquals("Customer", ((TitledVSAssemblyInfo) control.getVSAssemblyInfo()).getTitleValue());
   }

   @Test
   void sharedBarRangeSliderStillGetsACaptionBecauseItRendersNoTitleOfItsOwn() {
      // The other half of the caption rule: vs-range-slider.component.html gates its whole title
      // header on isInSelectionContainer(), so a standalone slider on the bar shows a bare "6..216"
      // with nothing naming the column. Removing the caption for EVERY control would strip its only
      // label.
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "SO", "partner_id"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "C");
      boundToTable(chart, "SO");
      vs.addAssembly(chart);

      builder.build(vs, ws, List.of(
         new WizDashboardFilterBuilder.FilterRequest("partner_id", "integer", "Customer")), 0);

      assertNotNull(vs.getAssembly("wizFilterLabel_" + control(vs).getName()),
                    "a range slider renders no title, so it still needs its caption");
   }

   @Test
   void bothSharedBarControlTypesFillTheSameBandHeightSoTheBarStaysEven() {
      // A dropdown has no caption and a slider does, so without compensating the two would occupy
      // different total heights and the bar would step by 16px between adjacent controls of
      // different types. The dropdown grows its own row by exactly the caption's height instead.
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "SO", "res_partner_name", "partner_id"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "C");
      boundToTable(chart, "SO");
      vs.addAssembly(chart);

      WizDashboardFilterBuilder.FilterResult result = builder.build(vs, ws, List.of(
         new WizDashboardFilterBuilder.FilterRequest("res_partner_name", "string", "Customer"),
         new WizDashboardFilterBuilder.FilterRequest("partner_id", "integer", "Customer Id")), 0);

      SelectionListVSAssembly list = dropdown(vs);
      VSAssembly slider = (VSAssembly) java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof TimeSliderVSAssembly).findFirst().orElseThrow();
      VSAssembly caption = (VSAssembly) vs.getAssembly("wizFilterLabel_" + slider.getName());
      assertNotNull(caption);

      int dropdownTop = list.getPixelOffset().y;
      int dropdownBottom = dropdownTop + list.getPixelSize().height;
      int sliderBandTop = caption.getPixelOffset().y;
      int sliderBandBottom = slider.getPixelOffset().y + slider.getPixelSize().height;

      assertEquals(sliderBandTop, dropdownTop, "both control types start at the same band top");
      assertEquals(sliderBandBottom, dropdownBottom, "and end at the same band bottom");
      // The dropdown's title row still fills exactly what was reserved for it (no gap), now that
      // "what was reserved" is the whole band rather than the band minus a caption.
      int reserved = result.placements().stream()
         .filter(pl -> pl.assemblyName().equals(list.getName()))
         .findFirst().orElseThrow().size().height;
      assertEquals(reserved, list.getSelectionListInfo().getTitleHeightValue());
   }

   private static SelectionListVSAssembly dropdown(Viewsheet vs) {
      return java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof SelectionListVSAssembly)
         .map(a -> (SelectionListVSAssembly) a)
         .findFirst().orElseThrow();
   }

   @Test
   void perChartCategoricalFilterRendersAsADropdownNotATallList() {
      // A per-chart filter sits INSIDE its chart's tile, so a multi-row checkbox list steals
      // height from the chart itself. Dropdown mode collapses it to a single title row
      // (SelectionListVSAssemblyInfo#getSizeScale pins the Y scale to 1 in that mode).
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name"));

      Viewsheet vs = new Viewsheet();

      WizDashboardFilterBuilder.FilterControlPlacement placement = builder.buildPerChart(vs, ws, 100, 200, 640, 40,
         new WizDashboardFilterBuilder.FilterRequest("category_name", "string", "Category"), "CHART_FINAL", null);

      assertNotNull(placement);
      SelectionListVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof SelectionListVSAssembly)
         .map(a -> (SelectionListVSAssembly) a)
         .findFirst().orElseThrow();
      assertEquals(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE, control.getSelectionListInfo().getShowType(),
         "a per-chart categorical filter must render as a dropdown, not a multi-row list");
   }

   @Test
   void perChartDropdownTitleRowFillsTheReservedHeightSoNoGapShowsAboveTheChart() {
      // A dropdown draws ONLY its title row (default AssetUtil.defh = 20), ignoring the rest of the
      // assembly's pixel height. Reserving more than the row draws leaves the difference as a visible
      // gap between the filter and the chart it belongs to, breaking the single-enclosing-card look
      // applyGroupedCardStyle creates. Pin the title row to the reserved height so the two agree by
      // construction rather than by two magic numbers happening to match.
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name"));

      Viewsheet vs = new Viewsheet();

      builder.buildPerChart(vs, ws, 100, 200, 640, 28,
         new WizDashboardFilterBuilder.FilterRequest("category_name", "string", "Category"), "CHART_FINAL", null);

      SelectionListVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof SelectionListVSAssembly)
         .map(a -> (SelectionListVSAssembly) a)
         .findFirst().orElseThrow();
      // The DESIGN value is the one that survives save/reopen, which is how a composed dashboard is used.
      assertEquals(28, control.getSelectionListInfo().getTitleHeightValue(),
         "the dropdown's title row must fill the reserved height exactly, or the leftover shows as a gap");
   }

   @Test
   void perChartRangeSliderIsLeftAloneByTheDropdownTreatment() {
      // TimeSlider has no show type -- it is already a single-row control. The dropdown
      // treatment must not touch it (or throw when it is the control that was created).
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "order_qty"));

      Viewsheet vs = new Viewsheet();

      WizDashboardFilterBuilder.FilterControlPlacement placement = builder.buildPerChart(vs, ws, 0, 0, 640, 40,
         new WizDashboardFilterBuilder.FilterRequest("order_qty", "integer", "Qty"), "CHART_FINAL", null);

      assertNotNull(placement);
      assertTrue(java.util.Arrays.stream(vs.getAssemblies()).anyMatch(a -> a instanceof TimeSliderVSAssembly),
         "a numeric per-chart filter must still be a range slider");
   }

   @Test
   void mergedChartTableNamesCollectsOnlyChartAssembliesOwnTables() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly radar = new ChartVSAssembly(vs, "RadarChart");
      boundToTable(radar, "RADAR_FINAL");
      vs.addAssembly(radar);
      ChartVSAssembly mr = new ChartVSAssembly(vs, "MrChart");
      boundToTable(mr, "MR_FINAL");
      vs.addAssembly(mr);
      // A non-chart assembly must not contribute a table name, even though it also implements
      // BindableVSAssembly -- only ChartVSAssembly is considered (see the method's javadoc).
      TextVSAssembly text = new TextVSAssembly(vs, "Title");
      vs.addAssembly(text);

      List<String> names = builder.mergedChartTableNames(vs);

      assertEquals(List.of("RADAR_FINAL", "MR_FINAL"), names);
   }

   @Test
   void buildBindsOnlyToTheChartsOwnTableNotAnUnrelatedTableSharingTheColumn() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name", "norm_revenue"));
      // Simulates the shared global-aggregate table upstream of a chart's normalization step:
      // it also exposes category_name, but no chart is bound to it directly, so it must never
      // receive the filter (that was the root cause of the live "No data" regression: binding a
      // filter to this kind of table cascades into the aggregate it feeds).
      ws.addAssembly(physicalTable(ws, "GLOBAL_STATS", "category_name", "min_revenue", "max_revenue"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "RadarChart");
      boundToTable(chart, "CHART_FINAL");
      vs.addAssembly(chart);

      WizDashboardFilterBuilder.FilterResult result = builder.build(
         vs, ws, List.of(new WizDashboardFilterBuilder.FilterRequest("category_name", "string", "Category")),
         WizDashboardService.CANVAS_MARGIN);

      assertEquals(List.of("category_name"), result.applied());
      assertTrue(result.skipped().isEmpty());

      AbstractSelectionVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof AbstractSelectionVSAssembly)
         .map(a -> (AbstractSelectionVSAssembly) a)
         .findFirst().orElseThrow();
      assertEquals(List.of("CHART_FINAL"), control.getTableNames(),
         "must bind only to the chart's own table, never GLOBAL_STATS");
   }

   @Test
   void adjacentSharedBarControlsAreSeparatedByAGap() {
      // Observed live: two range sliders butted edge-to-edge read as ONE double-ended slider, with no
      // visual cue where one filter stopped and the next began -- the stride equalled the control
      // width exactly.
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "SO", "state", "region"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "C");
      boundToTable(chart, "SO");
      vs.addAssembly(chart);

      WizDashboardFilterBuilder.FilterResult result = builder.build(vs, ws, List.of(
         new WizDashboardFilterBuilder.FilterRequest("state", "string", "State"),
         new WizDashboardFilterBuilder.FilterRequest("region", "string", "Region")), 0);

      // Placements now carry a caption per control as well, so compare CONTROL placements only.
      var ctls = result.placements().stream()
         .filter(pl -> !pl.assemblyName().startsWith("wizFilterLabel_")).toList();
      assertEquals(2, ctls.size(), "both controls should be placed");
      int firstX = ctls.get(0).position().x;
      int secondX = ctls.get(1).position().x;
      int width = ctls.get(0).size().width;
      assertTrue(secondX > firstX + width,
         "adjacent controls must not touch: second x=" + secondX + " should exceed first x=" + firstX
            + " plus width=" + width);
   }

   @Test
   void aSharedBarControlCarriesItsLabelAsTheTitleValue() {
      // Asserts ONLY what is actually true and load-bearing: the label reaches the control's title
      // value. Deliberately does NOT assert visibility or height -- both are already true/non-zero by
      // default, so such assertions pass with and without any change and prove nothing. The observed
      // missing title is NOT explained by anything assertable here; see the note in
      // WizDashboardFilterBuilder.build for what has been ruled out.
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "SO", "partner_id"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "C");
      boundToTable(chart, "SO");
      vs.addAssembly(chart);

      WizDashboardFilterBuilder.FilterResult result = builder.build(vs, ws, List.of(
         new WizDashboardFilterBuilder.FilterRequest("partner_id", "integer", "Customer")), 0);

      // A CAPTION assembly carries the label, because a standalone range slider renders no title of
      // its own (vs-range-slider.component.html gates the whole title header on
      // isInSelectionContainer()). Server-side titleVisible/titleHeight were all ruled out by test.
      VSAssembly caption = (VSAssembly) vs.getAssembly("wizFilterLabel_" + control(vs).getName());
      assertNotNull(caption, "every labelled control needs a caption assembly");
      assertEquals("Customer", ((TextVSAssemblyInfo) caption.getVSAssemblyInfo()).getTextValue());

      // ...and the caption is carried into the layout placements, or it is hidden whenever a layout
      // tier is selected (the same trap the filter-bar band rectangles documented).
      assertTrue(result.placements().stream().anyMatch(pl -> pl.assemblyName().equals(caption.getName())),
                 "the caption must appear in the returned placements");

      // The caption sits ABOVE the control, and the pair still fits the band the service reserves.
      java.awt.Point captionPos = result.placements().stream()
         .filter(pl -> pl.assemblyName().equals(caption.getName())).findFirst().orElseThrow().position();
      WizDashboardFilterBuilder.FilterControlPlacement ctl = result.placements().stream()
         .filter(pl -> !pl.assemblyName().startsWith("wizFilterLabel_")).findFirst().orElseThrow();
      assertTrue(ctl.position().y > captionPos.y, "control must sit below its caption");
      assertTrue(ctl.position().y + ctl.size().height
                    <= captionPos.y + WizDashboardService.FILTER_BAR_ROW_HEIGHT,
                 "caption + control must fit the reserved filter-bar row");
   }

   private static VSAssembly control(Viewsheet vs) {
      return java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof AbstractSelectionVSAssembly)
         .map(a -> (VSAssembly) a)
         .findFirst().orElseThrow();
   }

   @Test
   void preAggregationFilterBindsToTheRawSourceTableNotTheChartsFinalTable() {
      // A column (order `state`) present on the RAW source but NOT on the chart's aggregated final
      // table. Post-aggregation (default) can't reach it -> skipped. Pre-aggregation binds to the
      // raw source (a WHERE before the group-by) so the aggregate re-computes over the subset.
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "SO_RAW", "date_order", "amount_total", "state"));
      // The chart's own final (aggregated) table: carries only the grouped dim + measure, no state.
      ws.addAssembly(physicalTable(ws, "SO_REVENUE_BY_QTR", "Quarter(date_order)", "total_revenue"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "RevenueChart");
      boundToTable(chart, "SO_REVENUE_BY_QTR");
      vs.addAssembly(chart);

      // Post-aggregation (default): state isn't on the chart's final table -> skipped, nothing added.
      WizDashboardFilterBuilder.FilterResult post = builder.build(
         vs, ws, List.of(new WizDashboardFilterBuilder.FilterRequest("state", "string", "Status")),
         WizDashboardService.CANVAS_MARGIN);
      assertEquals(List.of("state"), post.skipped(), "post-agg can't reach a column not on the final table");
      assertTrue(post.applied().isEmpty());

      // Pre-aggregation: binds to the raw source table that carries state.
      WizDashboardFilterBuilder.FilterResult pre = builder.build(
         vs, ws, List.of(new WizDashboardFilterBuilder.FilterRequest("state", "string", "Status", true)),
         WizDashboardService.CANVAS_MARGIN);
      assertEquals(List.of("state"), pre.applied());
      assertTrue(pre.skipped().isEmpty());

      AbstractSelectionVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof AbstractSelectionVSAssembly)
         .map(a -> (AbstractSelectionVSAssembly) a)
         .findFirst().orElseThrow();
      assertEquals(List.of("SO_RAW"), control.getTableNames(),
         "pre-aggregation must bind to the raw source table carrying the column");
   }

   @Test
   void buildReturnsThePlacementOfEachAppliedControl() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "RadarChart");
      boundToTable(chart, "CHART_FINAL");
      vs.addAssembly(chart);

      WizDashboardFilterBuilder.FilterResult result = builder.build(
         vs, ws, List.of(new WizDashboardFilterBuilder.FilterRequest("category_name", "string", "Category")),
         WizDashboardService.CANVAS_MARGIN);

      // A string field is a DROPDOWN, which draws its own title row -- so exactly ONE placement, the
      // control itself, sitting at the passed origin. (It used to be two: the control plus a caption,
      // which printed the label twice.) A LABELLED SLIDER still returns two -- see
      // aSharedBarControlCarriesItsLabelAsTheTitleValue.
      assertEquals(1, result.placements().size());
      assertTrue(result.placements().stream().allMatch(pl -> pl.assemblyName() != null));
      assertEquals(new java.awt.Point(WizDashboardService.CANVAS_MARGIN, WizDashboardService.CANVAS_MARGIN),
         result.placements().get(0).position());
   }

   @Test
   void firstControlIsOffsetByTheCanvasMarginNotFlushAgainstTheEdge() {
      Worksheet ws = new Worksheet();
      // Both control types, because what "the top of the bar" IS differs between them: a dropdown
      // draws its own title so IT sits at the origin, while a slider's caption does.
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name", "order_qty"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "RadarChart");
      boundToTable(chart, "CHART_FINAL");
      vs.addAssembly(chart);

      builder.build(vs, ws, List.of(
            new WizDashboardFilterBuilder.FilterRequest("category_name", "string", "Category"),
            new WizDashboardFilterBuilder.FilterRequest("order_qty", "integer", "Qty")),
         WizDashboardService.CANVAS_MARGIN);

      SelectionListVSAssembly list = dropdown(vs);
      assertEquals(WizDashboardService.CANVAS_MARGIN, list.getPixelOffset().x,
         "must sit at the passed startX (the merged charts' left edge)");
      assertEquals(WizDashboardService.CANVAS_MARGIN, list.getPixelOffset().y,
         "a dropdown has no caption above it, so IT is what must clear the canvas's top edge");

      VSAssembly slider = (VSAssembly) java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof TimeSliderVSAssembly).findFirst().orElseThrow();
      // The slider sits BELOW its caption, so the caption is what must clear the top edge --
      // same intent, one row up (see FILTER_LABEL_HEIGHT for why a caption exists at all).
      assertTrue(slider.getPixelOffset().y > WizDashboardService.CANVAS_MARGIN,
         "the slider sits below its caption");
      var captionAssembly = vs.getAssembly("wizFilterLabel_" + slider.getName());
      assertNotNull(captionAssembly, "the slider must have a caption above it");
      assertEquals(WizDashboardService.CANVAS_MARGIN, ((VSAssembly) captionAssembly).getPixelOffset().y,
         "the bar must not sit flush against the canvas's top edge");
   }

   @Test
   void buildFilterBarBandAddsATintedBandAndAThinDividerSpanningTheGivenWidth() {
      Viewsheet vs = new Viewsheet();

      List<WizDashboardFilterBuilder.FilterControlPlacement> placements =
         builder.buildFilterBarBand(vs, 48, 12, 1800, 124);

      // Two rectangles: the band (full height) and a thin divider along its bottom edge.
      assertEquals(2, placements.size());
      assertEquals(new java.awt.Point(48, 12), placements.get(0).position());
      assertEquals(new java.awt.Dimension(1800, 124), placements.get(0).size(), "band spans the given width x height");
      assertEquals(new java.awt.Point(48, 12 + 124 - 2), placements.get(1).position(),
         "divider sits along the band's bottom edge");
      assertEquals(new java.awt.Dimension(1800, 2), placements.get(1).size(), "divider is a 2px-tall full-width bar");

      // Both are borderless rectangles rendered as a solid background fill.
      long rectangles = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof RectangleVSAssembly).count();
      assertEquals(2, rectangles);

      for(inetsoft.uql.asset.Assembly a : vs.getAssemblies()) {
         if(a instanceof RectangleVSAssembly rect) {
            inetsoft.uql.viewsheet.VSFormat fmt = rect.getVSAssemblyInfo().getFormat().getUserDefinedFormat();
            assertNotNull(fmt.getBackground(), "rectangle must carry a fill background so it renders");
         }
      }
   }

   @Test
   void buildPerChartBindsOnlyToTheSpecifiedTableEvenWhenAnotherTableExposesTheSameColumn() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "product_name"));
      // A DIFFERENT table also exposes "product_name" -- buildPerChart must never match it,
      // since only ONE table (the caller-specified chartTableName) is ever a candidate.
      ws.addAssembly(physicalTable(ws, "OTHER_CHART_FINAL", "product_name"));

      Viewsheet vs = new Viewsheet();

      WizDashboardFilterBuilder.FilterControlPlacement placement = builder.buildPerChart(vs, ws, 100, 200, 640, 120,
         new WizDashboardFilterBuilder.FilterRequest("product_name", "string", "Product"), "CHART_FINAL", null);

      assertNotNull(placement);
      assertEquals(new java.awt.Point(100, 200), placement.position());
      assertEquals(new java.awt.Dimension(640, 120), placement.size(),
         "the control must span the chart's width and the reserved header height");

      AbstractSelectionVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof AbstractSelectionVSAssembly)
         .map(a -> (AbstractSelectionVSAssembly) a)
         .findFirst().orElseThrow();
      assertEquals(List.of("CHART_FINAL"), control.getTableNames(),
         "must bind only to the specified chart table, never any other table exposing the same column");
      assertEquals(100, control.getPixelOffset().x);
      assertEquals(200, control.getPixelOffset().y);
      assertEquals(640, control.getPixelSize().width);
      assertEquals(120, control.getPixelSize().height);
   }

   @Test
   void perChartPreAggregationFilterBindsToTheChartsRawSourceNotItsFinalTable() {
      // The chart is a revenue-by-quarter aggregate: its final table (a mirror over the raw source,
      // exactly what the wiz merge stacks) carries only the grouped dim + measure. `state` lives
      // only on the RAW source, so post-aggregation cannot reach it at all -- pre-aggregation binds
      // there instead (a WHERE before the group-by) so the aggregate re-computes over the subset.
      Worksheet ws = new Worksheet();
      TableAssembly raw = physicalTable(ws, "SO_RAW", "date_order", "amount_total", "state");
      ws.addAssembly(raw);
      ws.addAssembly(mirrorTable(ws, "SO_REVENUE_BY_QTR", raw, "Quarter(date_order)", "total_revenue"));

      Viewsheet vs = new Viewsheet();

      WizDashboardFilterBuilder.FilterControlPlacement placement = builder.buildPerChart(vs, ws, 100, 200, 640, 28,
         new WizDashboardFilterBuilder.FilterRequest("state", "string", "State", true),
         "SO_REVENUE_BY_QTR", null);

      assertNotNull(placement, "pre-aggregation must reach a column that never survives to the final table");

      AbstractSelectionVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof AbstractSelectionVSAssembly)
         .map(a -> (AbstractSelectionVSAssembly) a)
         .findFirst().orElseThrow();
      assertEquals(List.of("SO_RAW"), control.getTableNames(),
         "pre-aggregation must bind to the chart's raw source table, not its aggregated final table");
   }

   @Test
   void perChartPostAggregationStillBindsToTheFinalTableWithTheFlagFalseOrAbsent() {
      // Regression guard for the default: with preAggregation false -- and with the flag simply
      // absent (the 3-arg compatibility constructor) -- binding is byte-for-byte the old behavior:
      // the chart's own FINAL table, and a column that only exists on the raw source is skipped.
      Worksheet ws = new Worksheet();
      TableAssembly raw = physicalTable(ws, "SO_RAW", "date_order", "amount_total", "state");
      ws.addAssembly(raw);
      ws.addAssembly(mirrorTable(ws, "SO_REVENUE_BY_QTR", raw, "Quarter(date_order)", "total_revenue"));

      for(WizDashboardFilterBuilder.FilterRequest req : List.of(
         new WizDashboardFilterBuilder.FilterRequest("total_revenue", "string", "Revenue", false),
         new WizDashboardFilterBuilder.FilterRequest("total_revenue", "string", "Revenue")))
      {
         Viewsheet vs = new Viewsheet();
         assertNotNull(builder.buildPerChart(vs, ws, 0, 0, 640, 28, req, "SO_REVENUE_BY_QTR", null));

         AbstractSelectionVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
            .filter(a -> a instanceof AbstractSelectionVSAssembly)
            .map(a -> (AbstractSelectionVSAssembly) a)
            .findFirst().orElseThrow();
         assertEquals(List.of("SO_REVENUE_BY_QTR"), control.getTableNames(),
            "post-aggregation must stay bound to the chart's final table");
      }

      // And post-aggregation still cannot reach a raw-source-only column -- unchanged.
      Viewsheet vs = new Viewsheet();
      assertNull(builder.buildPerChart(vs, ws, 0, 0, 640, 28,
         new WizDashboardFilterBuilder.FilterRequest("state", "string", "State"), "SO_REVENUE_BY_QTR", null),
         "post-aggregation must not reach a column absent from the final table");
      assertEquals(0, vs.getAssemblies().length);
   }

   @Test
   void perChartPreAggregationForOneChartNeverBindsToAnotherChartsRawSourceSharingTheColumnName() {
      // THE isolation property this whole feature rests on. Two charts, each an aggregate over its
      // OWN raw source, and BOTH raw sources expose a column called `state`. A per-chart
      // pre-aggregation filter for chart A must bind to A_RAW only -- never B_RAW -- so chart B's
      // structural safety (it could be a window/global-ratio chart a subset WHERE would collapse)
      // is irrelevant to whether A's filter can be offered. That is what removes the conservative
      // "every chart sharing the column name must be safe" veto the shared-bar path needs.
      Worksheet ws = new Worksheet();
      TableAssembly aRaw = physicalTable(ws, "A_RAW", "date_order", "amount_total", "state");
      TableAssembly bRaw = physicalTable(ws, "B_RAW", "date_order", "amount_total", "state");
      ws.addAssembly(aRaw);
      ws.addAssembly(bRaw);
      ws.addAssembly(mirrorTable(ws, "A_FINAL", aRaw, "Quarter(date_order)", "total_revenue"));
      ws.addAssembly(mirrorTable(ws, "B_FINAL", bRaw, "Quarter(date_order)", "revenue_share"));

      // The shared-bar resolver has no chart scope: it reaches BOTH raw sources from this same
      // worksheet, which is precisely why its caller must veto on the least-safe chart.
      assertEquals(List.of("A_RAW", "B_RAW"),
         AddFilterService.findColumnMatchingRootTables(ws, "state"),
         "the shared-bar resolver is board-wide -- the premise of the veto this test removes");

      Viewsheet vs = new Viewsheet();

      assertNotNull(builder.buildPerChart(vs, ws, 0, 0, 640, 28,
         new WizDashboardFilterBuilder.FilterRequest("state", "string", "State", true), "A_FINAL", null));

      AbstractSelectionVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof AbstractSelectionVSAssembly)
         .map(a -> (AbstractSelectionVSAssembly) a)
         .findFirst().orElseThrow();
      assertEquals(List.of("A_RAW"), control.getTableNames(),
         "chart A's pre-aggregation filter must bind ONLY to A's own root table, never B_RAW");
   }

   @Test
   void perChartPreAggregationIsSkippedWhenTheColumnLivesOnlyOnAnUnrelatedChartsSource() {
      // The chart-scoped lookup finds nothing (the column is on another chart's raw source, not
      // reachable from this chart's table) -> the request is SKIPPED, never bound to the wrong
      // table. Binding a pre-aggregation control to a table this chart doesn't read would filter
      // nothing here while silently changing whatever else reads that table.
      Worksheet ws = new Worksheet();
      TableAssembly aRaw = physicalTable(ws, "A_RAW", "date_order", "amount_total");
      ws.addAssembly(aRaw);
      ws.addAssembly(mirrorTable(ws, "A_FINAL", aRaw, "Quarter(date_order)", "total_revenue"));
      // `state` exists only here, on an unrelated chart's source.
      ws.addAssembly(physicalTable(ws, "B_RAW", "state"));

      Viewsheet vs = new Viewsheet();

      assertNull(builder.buildPerChart(vs, ws, 0, 0, 640, 28,
         new WizDashboardFilterBuilder.FilterRequest("state", "string", "State", true), "A_FINAL", null),
         "a column only on an unrelated chart's source must be skipped, not mis-bound");
      assertEquals(0, vs.getAssemblies().length, "no control may be added when nothing matched");
   }

   @Test
   @org.junit.jupiter.api.Timeout(30)
   void perChartPreAggregationTerminatesOnASelfReferentialDependencyGraph() {
      // A malformed worksheet (a mirror whose base is itself) must not hang the dashboard compose:
      // the walk's visited set terminates it. Nothing is reachable, so nothing binds.
      Worksheet ws = new Worksheet();
      MirrorTableAssembly cyclic = mirrorTable(ws, "CYCLE", physicalTable(ws, "SO_RAW", "state"), "state");
      cyclic.setTableAssemblies(new TableAssembly[]{ cyclic });
      ws.addAssembly(cyclic);

      Viewsheet vs = new Viewsheet();

      assertNull(builder.buildPerChart(vs, ws, 0, 0, 640, 28,
         new WizDashboardFilterBuilder.FilterRequest("state", "string", "State", true), "CYCLE", null));
      assertEquals(0, vs.getAssemblies().length);
   }

   @Test
   void buildPerChartReturnsNullWhenSkipped() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name"));

      Viewsheet vs = new Viewsheet();

      WizDashboardFilterBuilder.FilterControlPlacement placement = builder.buildPerChart(vs, ws, 0, 0, 640, 120,
         new WizDashboardFilterBuilder.FilterRequest("product_name", "string", "Product"), "CHART_FINAL", null);

      assertNull(placement);
      assertEquals(0, vs.getAssemblies().length, "no control should be added when the field isn't on the table");
   }

   @Test
   void buildPerChartWithAnUnresolvableChartAssemblyNameStillCreatesTheControl() {
      // A null/unresolvable chartAssemblyName must never block the filter control itself from
      // being created -- only the visual-grouping styling is skipped.
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name"));

      Viewsheet vs = new Viewsheet();

      WizDashboardFilterBuilder.FilterControlPlacement placement = builder.buildPerChart(vs, ws, 0, 0, 640, 120,
         new WizDashboardFilterBuilder.FilterRequest("category_name", "string", "Category"),
         "CHART_FINAL", "NoSuchAssembly");

      assertNotNull(placement);
      assertEquals(1, vs.getAssemblies().length);
   }

   @Test
   void buildPerChartStylesTheControlAndItsChartAsOneGroupedCardWhenTheChartResolves() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "RadarChart");
      boundToTable(chart, "CHART_FINAL");
      vs.addAssembly(chart);

      builder.buildPerChart(vs, ws, 100, 200, 640, 120,
         new WizDashboardFilterBuilder.FilterRequest("category_name", "string", "Category"),
         "CHART_FINAL", "RadarChart");

      AbstractSelectionVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof AbstractSelectionVSAssembly)
         .map(a -> (AbstractSelectionVSAssembly) a)
         .findFirst().orElseThrow();

      inetsoft.uql.viewsheet.VSFormat controlFormat = control.getVSAssemblyInfo().getFormat().getUserDefinedFormat();
      inetsoft.uql.viewsheet.VSFormat chartFormat = chart.getVSAssemblyInfo().getFormat().getUserDefinedFormat();

      assertNotNull(controlFormat.getBackground());
      assertEquals(controlFormat.getBackground(), chartFormat.getBackground(),
         "control and chart must share the same card background so they read as one unit");

      // The control's bottom border and the chart's top border abut directly -- both must be
      // borderless there so there's no doubled seam line between them.
      assertEquals(inetsoft.report.StyleConstants.NO_BORDER, controlFormat.getBorders().bottom);
      assertEquals(inetsoft.report.StyleConstants.NO_BORDER, chartFormat.getBorders().top);
      // The OUTER edges of the pair are bordered.
      assertEquals(inetsoft.report.StyleConstants.THIN_LINE, controlFormat.getBorders().top);
      assertEquals(inetsoft.report.StyleConstants.THIN_LINE, chartFormat.getBorders().bottom);
   }

   // ChartVSAssembly.setTableName(String) silently no-ops when getSourceInfo() is still null (it
   // builds a local SourceInfo but never calls setSourceInfo(...) to store it back) -- harmless
   // in production, where a chart's SourceInfo is always initialized before setTableName is
   // ever called, but a real gap for a bare `new ChartVSAssembly(...)` test fixture. Set the
   // SourceInfo directly instead of relying on setTableName here.
   private static void boundToTable(ChartVSAssembly chart, String tableName) {
      SourceInfo source = new SourceInfo(SourceInfo.ASSET, null, tableName);
      chart.setSourceInfo(source);
   }

   private static PhysicalBoundTableAssembly physicalTable(Worksheet ws, String assemblyName, String... columns) {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, assemblyName);
      SourceInfo si = new SourceInfo(SourceInfo.PHYSICAL_TABLE, "postgres", "public." + assemblyName);
      table.setSourceInfo(si);
      table.setColumnSelection(columnSelection(columns), false);
      return table;
   }

   // A wiz-merged chart's own final table is a MirrorTableAssembly stacked over the shared physical
   // base (see WsMergeService) -- the shape the pre-aggregation walk has to traverse. Its own column
   // selection is set explicitly to the AGGREGATE OUTPUT columns, so the raw columns are reachable
   // only through getDependeds (which a mirror answers with the table it mirrors), never directly.
   private static MirrorTableAssembly mirrorTable(Worksheet ws, String assemblyName,
                                                  TableAssembly base, String... columns)
   {
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, assemblyName, base);
      mirror.setColumnSelection(columnSelection(columns), false);
      return mirror;
   }

   private static ColumnSelection columnSelection(String... columns) {
      ColumnSelection cs = new ColumnSelection();

      for(String name : columns) {
         AttributeRef ref = new AttributeRef(null, name);
         ref.setDataType(XSchema.STRING);
         ColumnRef col = new ColumnRef(ref);
         col.setDataType(XSchema.STRING);
         cs.addAttribute(col);
      }

      return cs;
   }

   // Helper mirrors AddFilterService.buildColumnRef (AttributeRef + ColumnRef with dataType).
   // NOTE: ColumnRef lives in inetsoft.uql.asset (not inetsoft.uql.erm as the task brief sketch
   // assumed) — confirmed against AddFilterService's imports in Step 1.
   private static inetsoft.uql.erm.DataRef column(String name, String dtype) {
      inetsoft.uql.erm.AttributeRef attr = new inetsoft.uql.erm.AttributeRef(name);
      attr.setDataType(dtype);
      inetsoft.uql.asset.ColumnRef col = new inetsoft.uql.asset.ColumnRef(attr);
      col.setDataType(dtype);
      return col;
   }
}
