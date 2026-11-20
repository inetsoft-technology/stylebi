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
package inetsoft.web.binding.handler;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.binding.dnd.*;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.model.graph.OriginalDescriptor;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.viewsheet.handler.VSDrillHandler;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The creation-time defaults a binding editor or chart view drop applies to a marked chart. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSChartBindingHandlerCreationDefaultsTest {
   @BeforeEach
   void setup() {
      chartRefService = mock(ChartRefModelFactoryService.class);
      dndHandler = new ChartDndHandler(chartRefService);
      handler = new VSChartBindingHandler(dndHandler, mock(VSDrillHandler.class),
                                          mock(VSChartDataHandler.class),
                                          mock(AnalyticRepository.class));
      viewsheet = new Viewsheet();
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      dispatcher = mock(CommandDispatcher.class);
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aDateOnTheXShelfOfAMarkedChartGroupsByMonth() throws Exception {
      ChartVSAssembly chart = chart(bar(), true);

      drop(chart, column("Date", XSchema.DATE), shelf("2", 0));

      assertEquals(DateRangeRef.MONTH_INTERVAL, level(chart.getVSChartInfo().getXField(0)));
   }

   @Test
   void aDateOnColorKeepsYear() throws Exception {
      ChartVSAssembly chart = chart(bar(), true);

      drop(chart, column("Date", XSchema.DATE), aesthetic("6"));

      AestheticRef color = chart.getVSChartInfo().getColorField();
      assertNotNull(color, "Color is empty");
      assertEquals(DateRangeRef.YEAR_INTERVAL, level(color.getDataRef()));
   }

   @Test
   void aDateOnAClassicChartKeepsYear() throws Exception {
      ChartVSAssembly chart = chart(bar(), false);

      drop(chart, column("Date", XSchema.DATE), shelf("2", 0));

      assertEquals(DateRangeRef.YEAR_INTERVAL, level(chart.getVSChartInfo().getXField(0)));
   }

   @Test
   void aSecondDropOfTheSameDateTakesTheNextLevel() throws Exception {
      ChartVSAssembly chart = chart(bar(), true);

      drop(chart, column("Date", XSchema.DATE), shelf("2", 0));
      drop(chart, column("Date", XSchema.DATE), shelf("2", 1));

      VSChartInfo info = chart.getVSChartInfo();
      assertEquals(DateRangeRef.MONTH_INTERVAL, level(info.getXField(0)));
      assertEquals(DateRangeRef.DAY_OF_MONTH_PART, level(info.getXField(1)));
   }

   @Test
   void aSliceOnAMarkedPieSortsLargestFirst() throws Exception {
      ChartVSAssembly chart = chart(pie(), true);

      drop(chart, column("Segment", XSchema.STRING), aesthetic("6"));

      VSDimensionRef slice = colorDimension(chart);
      assertEquals("Segment", slice.getName());
      assertEquals(XConstants.SORT_VALUE_DESC, slice.getOrder());
      assertEquals("Sum(Sales)", slice.getSortByColValue());
   }

   // the plot body hands the field to an aesthetic, and the rule sees that aesthetic
   @Test
   void aSliceDroppedOnAMarkedPiesPlotSortsLargestFirst() throws Exception {
      ChartVSAssembly chart = chart(pie(), true);

      drop(chart, column("Segment", XSchema.STRING), view("1"));

      VSDimensionRef slice = colorDimension(chart);
      assertEquals(XConstants.SORT_VALUE_DESC, slice.getOrder());
      assertEquals("Sum(Sales)", slice.getSortByColValue());
   }

   // a measure beside one on X loses its formula, and the slice sorts by the name it is left with
   @Test
   void aPieSortsByTheMeasureNameTheDropLeaves() throws Exception {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_PIE);
      info.addXField(sum("Profit"));
      VSChartDimensionRef segment = dim("Segment");
      info.setColorField(aestheticRef(segment));
      ChartVSAssembly chart = chart(info, true);

      drop(chart, column("Sales", XSchema.DOUBLE), shelf("3", 0));

      assertEquals("Sales", chart.getVSChartInfo().getYField(0).getFullName());
      assertEquals(XConstants.SORT_VALUE_DESC, segment.getOrder());
      assertEquals("Sales", segment.getSortByColValue());
   }

   @Test
   void aFirstTreeLevelOnAMarkedTreemapAlsoColours() throws Exception {
      ChartVSAssembly chart = chart(treemap(), true);

      drop(chart, column("Division", XSchema.STRING), shelf("14", 0));

      assertEquals("Division", chart.getVSChartInfo().getGroupField(0).getName());
      assertEquals("Division", colorDimension(chart).getName());
   }

   @Test
   void aBreakByOnAMarkedMarimekkoAlsoColours() throws Exception {
      ChartVSAssembly chart = chart(mekko(), true);

      drop(chart, column("Product", XSchema.STRING), shelf("14", 0));

      assertEquals("Product", colorDimension(chart).getName());
   }

   @Test
   void classicDropsApplyNoDefault() throws Exception {
      ChartVSAssembly pie = chart(pie(), false);
      drop(pie, column("Segment", XSchema.STRING), aesthetic("6"));
      VSDimensionRef slice = colorDimension(pie);
      assertEquals("Segment", slice.getName());
      assertEquals(XConstants.SORT_ASC, slice.getOrder());

      ChartVSAssembly treemap = chart(treemap(), false);
      drop(treemap, column("Division", XSchema.STRING), shelf("14", 0));
      assertEquals("Division", treemap.getVSChartInfo().getGroupField(0).getName());
      assertNull(treemap.getVSChartInfo().getColorField());

      ChartVSAssembly mekko = chart(mekko(), false);
      drop(mekko, column("Product", XSchema.STRING), shelf("14", 0));
      assertEquals("Product", mekko.getVSChartInfo().getGroupField(0).getName());
      assertNull(mekko.getVSChartInfo().getColorField());
   }

   // a drop type that is not a number places nothing and applies no rule
   @Test
   void aDropTypeThatIsNotANumberAppliesNothing() throws Exception {
      VSChartInfo info = pie();
      VSChartDimensionRef segment = dim("Segment");
      info.setColorField(aestheticRef(segment));
      ChartVSAssembly chart = chart(info, true);

      drop(chart, column("Region", XSchema.STRING), shelf("plot", 0));

      assertSame(segment, chart.getVSChartInfo().getColorField().getDataRef());
      assertEquals(XConstants.SORT_ASC, segment.getOrder());
   }

   @Test
   void aDimensionFromTheTreeOnAMarkedCandlesColorIsRefused() throws Exception {
      ChartVSAssembly chart = chart(candle(dim("Day")), true);

      drop(chart, column("Ticker", XSchema.STRING), aesthetic("6"));

      VSChartInfo info = chart.getVSChartInfo();
      assertNull(info.getColorField());
      assertEquals(1, info.getXFieldCount());
      assertEquals(0, info.getYFieldCount());
   }

   @Test
   void aDimensionMovedFromXToAMarkedCandlesColorIsRefused() throws Exception {
      ChartVSAssembly chart = chart(candle(dim("Day"), dim("Ticker")), true);
      VSChartInfo info = chart.getVSChartInfo();
      ChartRef ticker = info.getXField(1);
      // the binding editor reuses the dragged field, as pasteChartRef does
      when(chartRefService.pasteChartRef(any(), any())).thenReturn(ticker);

      handler.addRemoveColumns(rvs, chart, fromX(1), aesthetic("6"));

      assertNull(info.getColorField());
      assertEquals(2, info.getXFieldCount());
      assertSame(ticker, info.getXField(1));
   }

   @Test
   void aDimensionDroppedOnAMarkedCandlesPlotIsRefused() throws Exception {
      ChartVSAssembly chart = chart(candle(dim("Day")), true);
      VSChartInfo info = chart.getVSChartInfo();
      assertEquals(ChartConstants.DROP_REGION_COLOR,
                   dndHandler.getAestheticDropType(dim("Ticker"), info));

      drop(chart, column("Ticker", XSchema.STRING), view("1"));

      assertNull(info.getColorField());
      assertNull(info.getShapeField());
      assertNull(info.getTextField());
   }

   @Test
   void aMeasureOnAMarkedCandlesColorIsAccepted() throws Exception {
      ChartVSAssembly chart = chart(candle(dim("Day")), true);

      drop(chart, column("Volume", XSchema.DOUBLE), aesthetic("6"));

      AestheticRef color = chart.getVSChartInfo().getColorField();
      assertNotNull(color, "Color is empty");
      assertEquals("Volume", color.getDataRef().getName());
      assertInstanceOf(VSChartAggregateRef.class, color.getDataRef());
   }

   private void drop(ChartVSAssembly chart, AssetEntry entry, BindingDropTarget target)
      throws Exception
   {
      handler.addColumns(rvs, chart, new AssetEntry[] { entry }, target, dispatcher, "/link");
   }

   private ChartVSAssembly chart(VSChartInfo info, boolean marked) {
      ChartVSAssembly chart = new ChartVSAssembly(viewsheet, "Chart1");
      chart.setVSChartInfo(info);
      chart.setChartDescriptor(new ChartDescriptor());
      // set either way: a new assembly inherits the sheet's mark, stamped from the org gate
      chart.getVSAssemblyInfo().setVizMark(marked ? VizMark.MODERN_LIGHT : null);
      return chart;
   }

   // a column of the Orders table as the binding tree sends it
   private static AssetEntry column(String name, String dtype) {
      AssetEntry entry = new AssetEntry(AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.COLUMN,
                                        "Orders/" + name, null);
      entry.setProperty("assembly", "Orders");
      entry.setProperty("dtype", dtype);
      return entry;
   }

   private static BindingDropTarget shelf(String type, int index) {
      BindingDropTarget target = new BindingDropTarget();
      target.setDropType(type);
      target.setDropIndex(index);
      return target;
   }

   private static ChartAestheticDropTarget aesthetic(String type) {
      ChartAestheticDropTarget target = new ChartAestheticDropTarget();
      target.setDropType(type);
      return target;
   }

   private static ChartViewDropTarget view(String type) {
      ChartViewDropTarget target = new ChartViewDropTarget();
      target.setDropType(type);
      return target;
   }

   // the field at the index of X, dragged from the X shelf
   private static ChartTransfer fromX(int index) {
      OriginalDescriptor original = new OriginalDescriptor(OriginalDescriptor.X_AXIS);
      original.setIndex(index);
      ChartRefModel model = mock(ChartRefModel.class);
      when(model.getOriginal()).thenReturn(original);
      ChartTransfer transfer = new ChartTransfer();
      transfer.setDragType(String.valueOf(ChartConstants.DROP_REGION_X));
      transfer.setRefs(Collections.singletonList(model));
      return transfer;
   }

   private static int level(DataRef ref) {
      assertInstanceOf(VSChartDimensionRef.class, ref);
      return Integer.parseInt(((VSChartDimensionRef) ref).getDateLevelValue());
   }

   private static VSDimensionRef colorDimension(ChartVSAssembly chart) {
      AestheticRef color = chart.getVSChartInfo().getColorField();
      assertNotNull(color, "Color is empty");
      DataRef ref = color.getDataRef();
      assertInstanceOf(VSDimensionRef.class, ref);
      return (VSDimensionRef) ref;
   }

   private static VSChartInfo bar() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.addYField(sum("Sales"));
      return info;
   }

   private static VSChartInfo pie() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_PIE);
      info.addYField(sum("Sales"));
      return info;
   }

   private static VSChartInfo treemap() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_TREEMAP);
      return info;
   }

   private static VSChartInfo mekko() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_MEKKO);
      info.addXField(dim("Region"));
      info.addYField(sum("Sales"));
      return info;
   }

   private static VSChartInfo candle(VSChartDimensionRef... xs) {
      VSChartInfo info = new CandleVSChartInfo();
      info.setChartType(GraphTypes.CHART_CANDLE);
      Arrays.stream(xs).forEach(info::addXField);
      return info;
   }

   private static AestheticRef aestheticRef(DataRef ref) {
      AestheticRef aes = new VSAestheticRef();
      aes.setDataRef(ref);
      return aes;
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef ref = new VSChartDimensionRef(new AttributeRef(null, name));
      ref.setDataType(XSchema.STRING);
      ref.setGroupColumnValue(name);
      return ref;
   }

   private static VSChartAggregateRef sum(String name) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(new AttributeRef(null, name));
      ref.setColumnValue(name);
      ref.setFormula(AggregateFormula.SUM);
      return ref;
   }

   private ChartRefModelFactoryService chartRefService;
   private ChartDndHandler dndHandler;
   private VSChartBindingHandler handler;
   private Viewsheet viewsheet;
   private RuntimeViewsheet rvs;
   private CommandDispatcher dispatcher;
}
