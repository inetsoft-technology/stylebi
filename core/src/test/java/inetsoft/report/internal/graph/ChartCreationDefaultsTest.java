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
package inetsoft.report.internal.graph;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.asset.SNamedGroupInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The seven creation-time rules, each gated on the chart's mark. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartCreationDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void newDateLevelIsMonthOnAMarkedAxis() {
      for(String type : new String[] { XSchema.DATE, XSchema.TIME_INSTANT }) {
         assertEquals(DateRangeRef.MONTH_INTERVAL,
                      ChartCreationDefaults.newDateLevel(type, true, modern()));
         assertEquals(DateRangeRef.YEAR_INTERVAL,
                      ChartCreationDefaults.newDateLevel(type, false, modern()));
         assertEquals(DateRangeRef.YEAR_INTERVAL,
                      ChartCreationDefaults.newDateLevel(type, true, VizContext.LEGACY));
      }

      assertEquals(DateRangeRef.HOUR_INTERVAL,
                   ChartCreationDefaults.newDateLevel(XSchema.TIME, true, modern()));
   }

   @Test
   void pickingAreaGivesTheStackedType() {
      assertEquals(GraphTypes.CHART_AREA_STACK, ChartCreationDefaults.stackedAreaType(
         GraphTypes.CHART_BAR, GraphTypes.CHART_AREA, modern()));
      assertEquals(GraphTypes.CHART_STEP_AREA_STACK, ChartCreationDefaults.stackedAreaType(
         GraphTypes.CHART_LINE, GraphTypes.CHART_STEP_AREA, modern()));
      assertEquals(GraphTypes.CHART_AREA_STACK, ChartCreationDefaults.stackedAreaType(
         GraphTypes.CHART_AUTO, GraphTypes.CHART_AREA, modern()));
      assertEquals(GraphTypes.CHART_AREA, ChartCreationDefaults.stackedAreaType(
         GraphTypes.CHART_AREA_STACK, GraphTypes.CHART_AREA, modern()));
      assertEquals(GraphTypes.CHART_AREA, ChartCreationDefaults.stackedAreaType(
         GraphTypes.CHART_STEP_AREA, GraphTypes.CHART_AREA, modern()));
      assertEquals(GraphTypes.CHART_AREA, ChartCreationDefaults.stackedAreaType(
         GraphTypes.CHART_BAR, GraphTypes.CHART_AREA, VizContext.LEGACY));
      assertEquals(GraphTypes.CHART_BAR, ChartCreationDefaults.stackedCounterpart(
         GraphTypes.CHART_BAR));
   }

   @Test
   void aMarkedCandleRefusesAColourDimension() {
      ChartInfo candle = new CandleVSChartInfo();
      candle.setChartType(GraphTypes.CHART_CANDLE);
      ChartInfo stock = new StockVSChartInfo();
      stock.setChartType(GraphTypes.CHART_STOCK);
      int color = ChartConstants.DROP_REGION_COLOR;

      assertTrue(ChartCreationDefaults.refusesColorDimension(candle, color, dim("a"), modern()));
      assertFalse(ChartCreationDefaults.refusesColorDimension(candle, color, sum("m"), modern()));
      assertFalse(ChartCreationDefaults.refusesColorDimension(
         candle, ChartConstants.DROP_REGION_SHAPE, dim("a"), modern()));
      assertFalse(ChartCreationDefaults.refusesColorDimension(stock, color, dim("a"), modern()));
      assertFalse(ChartCreationDefaults.refusesColorDimension(
         candle, color, dim("a"), VizContext.LEGACY));
   }

   @Test
   void aPieSortsItsSliceLargestFirst() {
      VSChartDimensionRef segment = dim("Segment");
      VSChartInfo info = pie(segment, true);

      ChartCreationDefaults.applyPieOrder(info, modern());

      assertEquals(XConstants.SORT_VALUE_DESC, segment.getOrder());
      assertEquals("Sum(Sales)", segment.getSortByColValue());
   }

   @Test
   void aPieSliceOnShapeSortsWhenColorIsEmpty() {
      VSChartDimensionRef segment = dim("Segment");
      VSChartInfo info = pie(segment, false);

      ChartCreationDefaults.applyPieOrder(info, modern());

      assertEquals(XConstants.SORT_VALUE_DESC, segment.getOrder());
      assertEquals("Sum(Sales)", segment.getSortByColValue());
   }

   @Test
   void aPieKeepsAnAuthorSortADateAndNoMeasure() {
      VSChartDimensionRef desc = dim("Segment");
      desc.setOrder(XConstants.SORT_DESC);
      ChartCreationDefaults.applyPieOrder(pie(desc, true), modern());
      assertEquals(XConstants.SORT_DESC, desc.getOrder());

      VSChartDimensionRef specific = dim("Segment");
      specific.setOrder(XConstants.SORT_SPECIFIC);
      specific.setManualOrderList(new ArrayList<>(Arrays.asList("a", "b")));
      ChartCreationDefaults.applyPieOrder(pie(specific, true), modern());
      assertEquals(XConstants.SORT_SPECIFIC, specific.getOrder());

      VSChartDimensionRef date = dim("When");
      date.setDataType(XSchema.DATE);
      ChartCreationDefaults.applyPieOrder(pie(date, true), modern());
      assertEquals(XConstants.SORT_ASC, date.getOrder());

      VSChartDimensionRef noMeasure = dim("Segment");
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_PIE);
      info.setColorField(aesthetic(noMeasure));
      ChartCreationDefaults.applyPieOrder(info, modern());
      assertEquals(XConstants.SORT_ASC, noMeasure.getOrder());

      VSChartDimensionRef legacy = dim("Segment");
      ChartCreationDefaults.applyPieOrder(pie(legacy, true), VizContext.LEGACY);
      assertEquals(XConstants.SORT_ASC, legacy.getOrder());
   }

   @Test
   void aMultiStylePieSortsEachSliceByItsOwnMeasure() {
      VSChartAggregateRef sales = sum("Sales");
      VSChartAggregateRef cost = sum("Cost");
      sales.setChartType(GraphTypes.CHART_PIE);
      cost.setChartType(GraphTypes.CHART_PIE);
      VSChartDimensionRef segment = dim("Segment");
      VSChartDimensionRef region = dim("Region");
      sales.setColorField(aesthetic(segment));
      cost.setColorField(aesthetic(region));

      VSChartInfo info = new DefaultVSChartInfo();
      info.setMultiStyles(true);
      info.addYField(sales);
      info.addYField(cost);

      ChartCreationDefaults.applyPieOrder(info, modern());

      assertEquals("Sum(Sales)", segment.getSortByColValue());
      assertEquals("Sum(Cost)", region.getSortByColValue());
      assertEquals(XConstants.SORT_VALUE_DESC, segment.getOrder());
      assertEquals(XConstants.SORT_VALUE_DESC, region.getOrder());
   }

   @Test
   void aWaterfallSavesTheSourceOrder() {
      VSChartDimensionRef item = dim("Item");
      VSChartInfo info = waterfall(item, false);

      ChartCreationDefaults.applyWaterfallOrder(info, modern(), reader(
         "Revenue", "Returns", "Cost of goods"));

      assertEquals(XConstants.SORT_SPECIFIC, item.getOrder());
      assertEquals(Arrays.asList("Revenue", "Returns", "Cost of goods"),
                   item.getManualOrderList());
   }

   @Test
   void anInvertedWaterfallOrdersItsYCategory() {
      VSChartDimensionRef item = dim("Item");
      VSChartInfo info = waterfall(item, true);
      assertTrue(info.isInvertedGraph(), "premise: Y holds the category");

      ChartCreationDefaults.applyWaterfallOrder(info, modern(), reader("a", "b"));

      assertEquals(XConstants.SORT_SPECIFIC, item.getOrder());
      assertEquals(Arrays.asList("a", "b"), item.getManualOrderList());
   }

   @Test
   void anUnreadableSourceKeepsTheOrder() {
      VSChartDimensionRef item = dim("Item");
      ChartCreationDefaults.applyWaterfallOrder(waterfall(item, false), modern(), d -> null);
      assertEquals(XConstants.SORT_ASC, item.getOrder());
      assertTrue(item.getManualOrderList() == null || item.getManualOrderList().isEmpty());

      VSChartDimensionRef date = dim("When");
      date.setDataType(XSchema.DATE);
      ChartCreationDefaults.applyWaterfallOrder(waterfall(date, false), modern(), reader("a"));
      assertEquals(XConstants.SORT_ASC, date.getOrder());

      VSChartDimensionRef desc = dim("Item");
      desc.setOrder(XConstants.SORT_DESC);
      ChartCreationDefaults.applyWaterfallOrder(waterfall(desc, false), modern(), reader("a"));
      assertEquals(XConstants.SORT_DESC, desc.getOrder());

      VSChartDimensionRef group = dim("Item");
      SNamedGroupInfo named = new SNamedGroupInfo();
      named.setGroupValue("g", new ArrayList<>(Arrays.asList("a", "b")));
      group.setNamedGroupInfo(named);
      ChartCreationDefaults.applyWaterfallOrder(waterfall(group, false), modern(), reader("a"));
      assertEquals(XConstants.SORT_ASC, group.getOrder());

      VSChartDimensionRef none = dim("Item");
      ChartCreationDefaults.applyWaterfallOrder(
         waterfall(none, false), modern(), ChartCreationDefaults.SourceOrder.NONE);
      assertEquals(XConstants.SORT_ASC, none.getOrder());
   }

   @Test
   void aTooLongSourceKeepsTheOrder() {
      VSChartDimensionRef item = dim("Item");
      String[] values = new String[ChartCreationDefaults.MAX_ORDERED_VALUES + 1];

      for(int i = 0; i < values.length; i++) {
         values[i] = "v" + i;
      }

      ChartCreationDefaults.applyWaterfallOrder(waterfall(item, false), modern(), reader(values));

      assertEquals(XConstants.SORT_ASC, item.getOrder());
   }

   @Test
   void aTreemapColoursByItsTopLevel() {
      for(int type : new int[] { GraphTypes.CHART_TREEMAP, GraphTypes.CHART_SUNBURST,
                                 GraphTypes.CHART_ICICLE, GraphTypes.CHART_CIRCLE_PACKING })
      {
         VSChartDimensionRef division = dim("Division");
         VSChartInfo info = new DefaultVSChartInfo();
         info.setChartType(type);
         info.addGroupField(division);
         info.addGroupField(dim("Department"));

         ChartCreationDefaults.applyTopLevelColor(info, modern());

         assertNotNull(info.getColorField());
         assertEquals("Division", info.getColorField().getDataRef().getName());
         assertNotSame(division, info.getColorField().getDataRef());
         assertInstanceOf(CategoricalColorFrame.class, info.getColorField().getVisualFrame());
      }
   }

   @Test
   void aTreemapKeepsAColourItAlreadyHas() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_TREEMAP);
      info.addGroupField(dim("Division"));
      VSChartAggregateRef margin = sum("Margin");
      AestheticRef color = aesthetic(margin);
      info.setColorField(color);

      ChartCreationDefaults.applyTopLevelColor(info, modern());

      assertSame(color, info.getColorField());

      VSChartInfo legacy = new DefaultVSChartInfo();
      legacy.setChartType(GraphTypes.CHART_TREEMAP);
      legacy.addGroupField(dim("Division"));
      ChartCreationDefaults.applyTopLevelColor(legacy, VizContext.LEGACY);
      assertNull(legacy.getColorField());
   }

   @Test
   void aMarimekkoColoursByItsBreakBy() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_MEKKO);
      info.addXField(dim("Region"));
      info.addGroupField(dim("Product"));

      ChartCreationDefaults.applyBreakByColor(info, modern());

      assertEquals("Product", info.getColorField().getDataRef().getName());

      VSChartInfo none = new DefaultVSChartInfo();
      none.setChartType(GraphTypes.CHART_MEKKO);
      none.addXField(dim("Region"));
      ChartCreationDefaults.applyBreakByColor(none, modern());
      assertNull(none.getColorField());
   }

   @Test
   void aCandleColourDimensionBecomesAPanel() {
      ChartInfo info = new CandleVSChartInfo();
      info.setChartType(GraphTypes.CHART_CANDLE);
      info.addYField(dim("Market"));
      info.setColorField(aesthetic(dim("Ticker")));

      ChartCreationDefaults.moveColorDimensionToPanels(info, modern());

      assertEquals(2, info.getYFieldCount());
      assertEquals("Market", info.getYField(0).getName());
      assertEquals("Ticker", info.getYField(1).getName());
      assertNull(info.getColorField());

      ChartInfo measure = new CandleVSChartInfo();
      measure.setChartType(GraphTypes.CHART_CANDLE);
      AestheticRef color = aesthetic(sum("m"));
      measure.setColorField(color);
      ChartCreationDefaults.moveColorDimensionToPanels(measure, modern());
      assertSame(color, measure.getColorField());

      ChartInfo legacy = new CandleVSChartInfo();
      legacy.setChartType(GraphTypes.CHART_CANDLE);
      legacy.setColorField(aesthetic(dim("Ticker")));
      ChartCreationDefaults.moveColorDimensionToPanels(legacy, VizContext.LEGACY);
      assertNotNull(legacy.getColorField());
      assertEquals(0, legacy.getYFieldCount());
   }

   @Test
   void enteringAPieSortsButPieToDonutDoesNot() {
      VSChartDimensionRef segment = dim("Segment");
      VSChartInfo info = pie(segment, true);

      ChartCreationDefaults.afterTypeChange(info, GraphTypes.CHART_PIE, GraphTypes.CHART_DONUT,
                                            modern(), SO_NONE);
      assertEquals(XConstants.SORT_ASC, segment.getOrder());

      ChartCreationDefaults.afterTypeChange(info, GraphTypes.CHART_BAR, GraphTypes.CHART_PIE,
                                            modern(), SO_NONE);
      assertEquals(XConstants.SORT_VALUE_DESC, segment.getOrder());
   }

   @Test
   void enteringATreemapColoursButTreemapToSunburstDoesNot() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_SUNBURST);
      info.addGroupField(dim("Division"));

      ChartCreationDefaults.afterTypeChange(info, GraphTypes.CHART_TREEMAP,
                                            GraphTypes.CHART_SUNBURST, modern(), SO_NONE);
      assertNull(info.getColorField());

      ChartCreationDefaults.afterTypeChange(info, GraphTypes.CHART_BAR,
                                            GraphTypes.CHART_SUNBURST, modern(), SO_NONE);
      assertEquals("Division", info.getColorField().getDataRef().getName());
   }

   @Test
   void enteringAMarimekkoColoursItsBreakBy() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_MEKKO);
      info.addXField(dim("Region"));
      info.addGroupField(dim("Product"));

      ChartCreationDefaults.afterTypeChange(info, GraphTypes.CHART_BAR, GraphTypes.CHART_MEKKO,
                                            modern(), SO_NONE);
      assertEquals("Product", info.getColorField().getDataRef().getName());
   }

   @Test
   void enteringACandleMakesPanels() {
      ChartInfo info = new CandleVSChartInfo();
      info.setChartType(GraphTypes.CHART_CANDLE);
      info.setColorField(aesthetic(dim("Ticker")));

      ChartCreationDefaults.afterTypeChange(info, GraphTypes.CHART_BAR, GraphTypes.CHART_CANDLE,
                                            modern(), SO_NONE);

      assertNull(info.getColorField());
      assertEquals("Ticker", info.getYField(0).getName());
   }

   @Test
   void enteringAWaterfallSavesTheOrder() {
      VSChartDimensionRef item = dim("Item");
      VSChartInfo info = waterfall(item, false);

      ChartCreationDefaults.afterTypeChange(info, GraphTypes.CHART_BAR,
                                            GraphTypes.CHART_WATERFALL, modern(),
                                            reader("a", "b"));
      assertEquals(Arrays.asList("a", "b"), item.getManualOrderList());

      VSChartDimensionRef again = dim("Item");
      ChartCreationDefaults.afterTypeChange(waterfall(again, false), GraphTypes.CHART_WATERFALL,
                                            GraphTypes.CHART_WATERFALL, modern(), reader("a"));
      assertEquals(XConstants.SORT_ASC, again.getOrder());
   }

   @Test
   void aSliceDroppedOnColorSortsButOneDroppedOnTextDoesNot() {
      VSChartDimensionRef onColor = dim("Segment");
      ChartCreationDefaults.afterDrop(pie(onColor, true), ChartConstants.DROP_REGION_COLOR,
                                      Arrays.asList(onColor), modern(), SO_NONE);
      assertEquals(XConstants.SORT_VALUE_DESC, onColor.getOrder());

      VSChartDimensionRef onText = dim("Segment");
      ChartCreationDefaults.afterDrop(pie(onText, true), ChartConstants.DROP_REGION_TEXT,
                                      Arrays.asList(onText), modern(), SO_NONE);
      assertEquals(XConstants.SORT_ASC, onText.getOrder());
   }

   @Test
   void aMeasureDroppedOnAPieSortsItsSlice() {
      VSChartDimensionRef segment = dim("Segment");
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_PIE);
      info.setColorField(aesthetic(segment));

      ChartCreationDefaults.afterDrop(info, ChartConstants.DROP_REGION_COLOR,
                                      Arrays.asList(segment), modern(), SO_NONE);
      assertEquals(XConstants.SORT_ASC, segment.getOrder());

      VSChartAggregateRef sales = sum("Sales");
      info.addYField(sales);
      ChartCreationDefaults.afterDrop(info, ChartConstants.DROP_REGION_Y,
                                      Arrays.asList(sales), modern(), SO_NONE);
      assertEquals(XConstants.SORT_VALUE_DESC, segment.getOrder());
      assertEquals("Sum(Sales)", segment.getSortByColValue());
   }

   @Test
   void aMultiStylePieAggregateTriggersThePieOrder() {
      VSChartAggregateRef sales = sum("Sales");
      sales.setChartType(GraphTypes.CHART_PIE);
      VSChartDimensionRef segment = dim("Segment");
      sales.setColorField(aesthetic(segment));
      VSChartInfo info = new DefaultVSChartInfo();
      info.setMultiStyles(true);
      info.addYField(sales);

      ChartCreationDefaults.afterDrop(info, ChartConstants.DROP_REGION_Y,
                                      Arrays.asList(sales), modern(), SO_NONE);

      assertEquals(XConstants.SORT_VALUE_DESC, segment.getOrder());
   }

   @Test
   void anAuthorSortSurvivesALaterDrop() {
      VSChartDimensionRef segment = dim("Segment");
      segment.setOrder(XConstants.SORT_DESC);
      VSChartInfo info = pie(segment, true);
      VSChartAggregateRef cost = sum("Cost");
      info.addYField(cost);
      ChartCreationDefaults.afterDrop(info, ChartConstants.DROP_REGION_Y,
                                      Arrays.asList(cost), modern(), SO_NONE);
      assertEquals(XConstants.SORT_DESC, segment.getOrder());

      VSChartDimensionRef item = dim("Item");
      item.setOrder(XConstants.SORT_DESC);
      VSChartInfo wf = waterfall(item, false);
      VSChartDimensionRef other = dim("Other");
      wf.addXField(other);
      ChartCreationDefaults.afterDrop(wf, ChartConstants.DROP_REGION_X,
                                      Arrays.asList(other), modern(), reader("a", "b"));
      assertEquals(XConstants.SORT_DESC, item.getOrder());
   }

   @Test
   void aClearedColourStaysClearedOnASecondLevel() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_TREEMAP);
      VSChartDimensionRef division = dim("Division");
      info.addGroupField(division);
      ChartCreationDefaults.afterDrop(info, ChartConstants.DROP_REGION_GROUP,
                                      Arrays.asList(division), modern(), SO_NONE);
      assertNotNull(info.getColorField());

      info.setColorField(null);
      VSChartDimensionRef department = dim("Department");
      info.addGroupField(department);
      ChartCreationDefaults.afterDrop(info, ChartConstants.DROP_REGION_GROUP,
                                      Arrays.asList(department), modern(), SO_NONE);
      assertNull(info.getColorField());
   }

   @Test
   void aBreakByDropColoursAMarimekko() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_MEKKO);
      info.addXField(dim("Region"));
      VSChartDimensionRef product = dim("Product");
      info.addGroupField(product);

      ChartCreationDefaults.afterDrop(info, ChartConstants.DROP_REGION_GROUP,
                                      Arrays.asList(product), modern(), SO_NONE);

      assertEquals("Product", info.getColorField().getDataRef().getName());
   }

   @Test
   void aWaterfallCategoryDropSavesTheOrder() {
      VSChartDimensionRef item = dim("Item");
      VSChartInfo info = waterfall(item, false);

      ChartCreationDefaults.afterDrop(info, ChartConstants.DROP_REGION_X,
                                      Arrays.asList(item), modern(), reader("a", "b"));

      assertEquals(XConstants.SORT_SPECIFIC, item.getOrder());
      assertEquals(Arrays.asList("a", "b"), item.getManualOrderList());
   }

   @Test
   void aMeasureDropOnAWaterfallReadsNothing() {
      VSChartDimensionRef item = dim("Item");
      VSChartInfo info = waterfall(item, false);
      VSChartAggregateRef cost = sum("Cost");
      info.addYField(cost);

      ChartCreationDefaults.afterDrop(info, ChartConstants.DROP_REGION_Y,
                                      Arrays.asList(cost), modern(),
                                      d -> { throw new AssertionError("read"); });

      assertEquals(XConstants.SORT_ASC, item.getOrder());
   }

   @Test
   void anUnnamedDropRegionAppliesNothing() {
      VSChartDimensionRef item = dim("Item");
      ChartCreationDefaults.afterDrop(waterfall(item, false), ChartConstants.DROP_REGION_TEXT,
                                      Arrays.asList(item), modern(), reader("a"));
      assertEquals(XConstants.SORT_ASC, item.getOrder());
   }

   @Test
   void theWizardAppliesThePieAndWaterfallOrders() {
      VSChartDimensionRef segment = dim("Segment");
      ChartCreationDefaults.afterWizard(pie(segment, true), modern(), SO_NONE);
      assertEquals(XConstants.SORT_VALUE_DESC, segment.getOrder());

      VSChartDimensionRef item = dim("Item");
      ChartCreationDefaults.afterWizard(waterfall(item, false), modern(), reader("a", "b"));
      assertEquals(Arrays.asList("a", "b"), item.getManualOrderList());
   }

   @Test
   void theResetCopyRulesSkipTheDateLevel() {
      VSChartDimensionRef date = dim("Date");
      date.setDataType(XSchema.DATE);
      date.setDateLevel(DateRangeRef.YEAR_INTERVAL);
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_AREA);
      info.addXField(date);
      info.addYField(sum("Sales"));

      ChartCreationDefaults.forResetCopy(info, modern(), SO_NONE);

      assertEquals(GraphTypes.CHART_AREA_STACK, info.getChartType());
      assertEquals(DateRangeRef.YEAR_INTERVAL, date.getDateLevel());
   }

   @Test
   void theResetCopyStacksMultiStyleAreasAndAppliesTheRules() {
      VSChartAggregateRef sales = sum("Sales");
      sales.setChartType(GraphTypes.CHART_STEP_AREA);
      VSChartAggregateRef cost = sum("Cost");
      cost.setChartType(GraphTypes.CHART_PIE);
      VSChartDimensionRef segment = dim("Segment");
      cost.setColorField(aesthetic(segment));
      VSChartInfo info = new DefaultVSChartInfo();
      info.setMultiStyles(true);
      info.addYField(sales);
      info.addYField(cost);

      ChartCreationDefaults.forResetCopy(info, modern(), SO_NONE);

      assertEquals(GraphTypes.CHART_STEP_AREA_STACK, sales.getChartType());
      assertEquals(GraphTypes.CHART_PIE, cost.getChartType());
      assertEquals(XConstants.SORT_VALUE_DESC, segment.getOrder());

      VSChartInfo treemap = new DefaultVSChartInfo();
      treemap.setChartType(GraphTypes.CHART_TREEMAP);
      treemap.addGroupField(dim("Division"));
      ChartCreationDefaults.forResetCopy(treemap, modern(), SO_NONE);
      assertNotNull(treemap.getColorField());

      ChartInfo candle = new CandleVSChartInfo();
      candle.setChartType(GraphTypes.CHART_CANDLE);
      candle.setColorField(aesthetic(dim("Ticker")));
      ChartCreationDefaults.forResetCopy(candle, modern(), SO_NONE);
      assertNull(candle.getColorField());
   }

   @Test
   void everyEntryIsANoOpOnAClassicChart() {
      VSChartDimensionRef segment = dim("Segment");
      VSChartInfo pie = pie(segment, true);

      VSChartDimensionRef item = dim("Item");
      VSChartInfo wf = waterfall(item, false);

      VSChartInfo tree = new DefaultVSChartInfo();
      tree.setChartType(GraphTypes.CHART_TREEMAP);
      VSChartDimensionRef division = dim("Division");
      tree.addGroupField(division);

      VSChartInfo mekko = new DefaultVSChartInfo();
      mekko.setChartType(GraphTypes.CHART_MEKKO);
      mekko.addXField(dim("Region"));
      VSChartDimensionRef product = dim("Product");
      mekko.addGroupField(product);

      ChartInfo candle = new CandleVSChartInfo();
      candle.setChartType(GraphTypes.CHART_CANDLE);
      candle.setColorField(aesthetic(dim("Ticker")));

      VSChartInfo area = new DefaultVSChartInfo();
      area.setChartType(GraphTypes.CHART_AREA);
      area.addXField(dim("Region"));
      area.addYField(sum("Sales"));

      ChartInfo[] charts = { pie, wf, tree, mekko, candle, area };
      DataRef[] landed = { segment, item, division, product, null, null };
      int[] types = { GraphTypes.CHART_PIE, GraphTypes.CHART_WATERFALL,
                      GraphTypes.CHART_TREEMAP, GraphTypes.CHART_MEKKO,
                      GraphTypes.CHART_CANDLE, GraphTypes.CHART_AREA };
      int[] regions = { ChartConstants.DROP_REGION_COLOR, ChartConstants.DROP_REGION_X,
                        ChartConstants.DROP_REGION_GROUP, ChartConstants.DROP_REGION_GROUP,
                        ChartConstants.DROP_REGION_COLOR, ChartConstants.DROP_REGION_X };

      for(int i = 0; i < charts.length; i++) {
         String before = xml(charts[i]);
         ChartCreationDefaults.afterTypeChange(
            charts[i], GraphTypes.CHART_BAR, types[i], VizContext.LEGACY, reader("a", "b"));
         ChartCreationDefaults.afterDrop(
            charts[i], regions[i],
            landed[i] == null ? new ArrayList<DataRef>() : Arrays.asList(landed[i]),
            VizContext.LEGACY, reader("a", "b"));
         ChartCreationDefaults.afterWizard(charts[i], VizContext.LEGACY, reader("a", "b"));
         ChartCreationDefaults.forResetCopy(charts[i], VizContext.LEGACY, reader("a", "b"));
         assertEquals(before, xml(charts[i]), "chart " + i);
      }
   }

   private static String xml(ChartInfo info) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      ((VSChartInfo) info).writeXML(writer);
      writer.flush();
      return out.toString();
   }

   private static final ChartCreationDefaults.SourceOrder SO_NONE =
      ChartCreationDefaults.SourceOrder.NONE;

   private static VizContext modern() {
      return VizContext.of(VizMark.MODERN_LIGHT);
   }

   private static ChartCreationDefaults.SourceOrder reader(String... values) {
      return d -> new ArrayList<Object>(Arrays.asList(values));
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

   private static AestheticRef aesthetic(inetsoft.uql.erm.DataRef ref) {
      AestheticRef aes = new VSAestheticRef();
      aes.setDataRef(ref);
      return aes;
   }

   private static VSChartInfo pie(VSChartDimensionRef slice, boolean onColor) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_PIE);
      info.addYField(sum("Sales"));

      if(onColor) {
         info.setColorField(aesthetic(slice));
      }
      else {
         info.setShapeField(aesthetic(slice));
      }

      return info;
   }

   private static VSChartInfo waterfall(VSChartDimensionRef item, boolean inverted) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_WATERFALL);

      if(inverted) {
         info.addYField(item);
         info.addXField(sum("Amount"));
      }
      else {
         info.addXField(item);
         info.addYField(sum("Amount"));
      }

      return info;
   }
}
