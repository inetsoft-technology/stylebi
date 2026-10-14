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
package inetsoft.report.composition.graph;

import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78446: one fast case for each site that must not confuse the date comparison runtime
 * calc field WeekOfYear(D) with a same-named plain D at Week of Year column. The full target
 * matrix (every chart/crosstab target, full-bar brush, crosstab flyover, selection list) is in
 * the slow {@link DateComparisonForeignCalcConditionViewsheetTest}.
 * <p>
 * Fixture: EmbeddedVS1, calc D = new Date(2021, 0, field['day']), Sum(wind) = 653732 in total.
 * The date comparison chart's calc week 42 is 33020 (plain weeks 15 and 16); the plain week 4
 * is 8250.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class DateComparisonForeignCalcConditionCoreViewsheetTest {
   /**
    * ChartVSAQuery align loop: aggregated and detail Week of Year charts next to the date
    * comparison chart group on their own week column (the original "Column not found").
    */
   @Test
   void weekOfYearChartsKeepTheirOwnWeekColumn() throws Exception {
      Fixture f = new Fixture();
      DataSet week = chartData(f.box, "ChartWeek");
      assertEquals(List.of(WEEK, "Sum(wind)"), headers(week));
      assertEquals(FIXTURE_WEEKS, week.getRowCount(), "ChartWeek rows");
      assertEquals(1, ((Number) week.getData(WEEK, 0)).intValue());
      assertEquals(PLAIN_WEEK_4_TOTAL, ((Number) week.getData("Sum(wind)", 3)).doubleValue(),
                   0.001);

      DataSet detail = chartData(f.box, "ChartDetail");
      List<String> headers = headers(detail);
      assertTrue(headers.contains(WEEK), "ChartDetail headers " + headers);
      assertFalse(headers.stream().anyMatch(h -> h.endsWith("_1")),
                  "ChartDetail headers " + headers);
   }

   /**
    * VSAQuery.replaceRef: a data tip on the date comparison chart's calc week filters a Week
    * of Year target on the calc week, not on its own same-named plain week.
    */
   @Test
   void dcChartTipFiltersWeekOfYearTargetOnCalcWeek() throws Exception {
      Fixture f = new Fixture();
      chartTip(f, "ChartWeek", f.chart2, weekSelection(DC_WEEK));
      assertEquals(DC_WEEK_TOTAL, total(f.box, "ChartWeek"), 0.001);
      assertEquals(List.of(15, 16), weekRows(f.box, "ChartWeek"));
   }

   /**
    * VSUtil.fixCondition: a data tip from a plain Week of Year chart stays on the plain week
    * on another Week of Year target.
    */
   @Test
   void plainChartTipFiltersWeekOfYearTargetOnPlainWeek() throws Exception {
      Fixture f = new Fixture();
      ChartVSAssembly source = (ChartVSAssembly) f.vs.getAssembly("ChartWeek");
      chartTip(f, "ChartWeek2", source, weekSelection(4));
      assertEquals(PLAIN_WEEK_4_TOTAL, total(f.box, "ChartWeek2"), 0.001);
   }

   /**
    * VSAQuery.fixConditionColumn: a brush from a plain Week of Year chart filters a Year
    * target on the plain week, not on the same-named calc pass-through.
    */
   @Test
   void plainChartBrushFiltersYearTargetOnPlainWeek() throws Exception {
      Fixture f = new Fixture();
      ChartVSAssembly source = (ChartVSAssembly) f.vs.getAssembly("ChartWeek");
      source.setBrushSelection(weekSelection(4));
      f.box.resetDataMap("ChartYear");
      assertEquals(PLAIN_WEEK_4_TOTAL, total(f.box, "ChartYear"), 0.001);
   }

   private static VSSelection weekSelection(int week) {
      VSSelection selection = new VSSelection();
      VSPoint point = new VSPoint();
      point.addValue(new VSFieldValue(WEEK, String.valueOf(week)));
      selection.addPoint(point);
      return selection;
   }

   /**
    * Set the data tip condition of a chart point on the target, built like
    * VSUtil.fixCondition builds it for a chart source.
    */
   private static void chartTip(Fixture f, String target, ChartVSAssembly chart,
                                VSSelection selection) throws Exception
   {
      VSAssembly tip = (VSAssembly) f.vs.getAssembly(target);
      TableAssembly tobj = tip.getWorksheet().getVSTableAssembly(tip.getTableName());
      tobj = (TableAssembly) f.box.getBoundTable(tobj, target, false).clone();
      VSAQuery.appendCalcField(tobj, f.tname, true, f.vs);
      ConditionList conds = chart.getConditionList(selection, tobj.getColumnSelection(false));
      conds = VSAQuery.replaceGroupValues(conds, chart);
      Object result = VSUtil.fixCondition(f.rvs, tip, conds, chart.getName(), null);
      assertTrue(result instanceof ConditionList, target + " tip condition " + result);
      tip.setTipConditionList((ConditionList) result);
      f.box.resetDataMap(target);
   }

   private static DataSet chartData(ViewsheetSandbox box, String name) throws Exception {
      VGraphPair pair = assertDoesNotThrow(() -> box.getVGraphPair(name, true, null), name);
      assertNotNull(pair.getExpandedVGraph(), name);
      return (DataSet) box.getData(name);
   }

   private static double total(ViewsheetSandbox box, String name) throws Exception {
      DataSet dset = (DataSet) assertDoesNotThrow(() -> box.getData(name), name);
      int col = dset.indexOfHeader("Sum(wind)");
      assertTrue(col >= 0, name + " headers " + headers(dset));
      double sum = 0;

      for(int r = 0; r < dset.getRowCount(); r++) {
         Object v = dset.getData(col, r);
         sum += v instanceof Number ? ((Number) v).doubleValue() : 0;
      }

      return sum;
   }

   private static List<Integer> weekRows(ViewsheetSandbox box, String name) throws Exception {
      DataSet dset = (DataSet) box.getData(name);
      int col = dset.indexOfHeader(WEEK);
      assertTrue(col >= 0, name + " headers " + headers(dset));
      List<Integer> weeks = new ArrayList<>();

      for(int r = 0; r < dset.getRowCount(); r++) {
         weeks.add(((Number) dset.getData(col, r)).intValue());
      }

      return weeks;
   }

   private static List<String> headers(DataSet data) {
      List<String> list = new ArrayList<>();

      for(int c = 0; c < data.getColCount(); c++) {
         list.add(data.getHeader(c));
      }

      return list;
   }

   /**
    * The reporter's date comparison chart (Chart2) and the non date comparison charts on the
    * same table that the four cases need.
    */
   private final class Fixture {
      final RuntimeViewsheet rvs;
      final ViewsheetSandbox box;
      final Viewsheet vs;
      final String tname;
      final ChartVSAssembly chart2;

      Fixture() throws Exception {
         rvs = vsResource.getRuntimeViewsheet();
         box = rvs.getViewsheetSandbox().orElseThrow();
         vs = box.getViewsheet();
         SourceInfo source =
            (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
         tname = source.getSource();

         CalculateRef dateCalc = new CalculateRef(true);
         dateCalc.setName("D");
         ExpressionRef dateExpr = new ExpressionRef(null, "D");
         dateExpr.setExpression("new Date(2021, 0, field['day'])");
         dateExpr.setDataType(XSchema.DATE);
         dateCalc.setDataRef(dateExpr);
         dateCalc.setSQL(false);
         vs.addCalcField(tname, dateCalc);

         chart2 = chart(source, "Chart2", XConstants.YEAR_DATE_GROUP, reporterDc());
         assertDoesNotThrow(() -> box.getVGraphPair("Chart2", true, null), "Chart2");
         assertTrue(chart2.getVSChartInfo().isAppliedDateComparison(), "Chart2 DC applied");
         DataSet data2 = (DataSet) box.getData("Chart2");
         assertEquals(DC_WEEK, ((Number) data2.getData(WEEK, 0)).intValue(), "Chart2 week");

         chart(source, "ChartWeek", DateRangeRef.WEEK_OF_YEAR_PART, null);
         chart(source, "ChartWeek2", DateRangeRef.WEEK_OF_YEAR_PART, null);
         chart(source, "ChartYear", XConstants.YEAR_DATE_GROUP, null);
         ChartVSAssembly detail =
            chart(source, "ChartDetail", DateRangeRef.WEEK_OF_YEAR_PART, null);
         ((VSChartAggregateRef) detail.getVSChartInfo().getYField(0)).setFormulaValue("None");
      }

      private ChartVSAssembly chart(SourceInfo source, String name, int level,
                                    DateComparisonInfo dc)
      {
         ChartVSAssembly chart = new ChartVSAssembly(vs, name);
         chart.setSourceInfo((SourceInfo) source.clone());
         VSChartInfo info = chart.getVSChartInfo();
         info.addXField(dateDim(new VSChartDimensionRef(), level));
         info.addYField(sumWind(new VSChartAggregateRef()));
         ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).setDateComparisonInfo(dc);
         vs.addAssembly(chart);
         return chart;
      }
   }

   private static DateComparisonInfo reporterDc() {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(XConstants.YEAR_DATE_GROUP);
      periods.setPreCount(2);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue("2021-11-10");
      periods.setToDate(false);
      periods.setInclusive(true);
      dcInfo.setDateComparisonPeriods(periods);
      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(DateComparisonInfo.DAY);
      interval.setLevel(DateComparisonInfo.WEEK_TO_DATE);
      interval.setContextLevel(XConstants.WEEK_DATE_GROUP);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(DateComparisonInfo.VALUE);
      return dcInfo;
   }

   private static <T extends VSDimensionRef> T dateDim(T dim, int level) {
      AttributeRef attr = new AttributeRef(null, "D");
      attr.setDataType(XSchema.DATE);
      ColumnRef column = new ColumnRef(attr);
      column.setDataType(XSchema.DATE);
      dim.setDataRef(column);
      dim.setGroupColumnValue("D");
      dim.setDataType(XSchema.DATE);
      dim.setDateLevelValue(level + "");
      return dim;
   }

   private static <T extends VSAggregateRef> T sumWind(T agg) {
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      return agg;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private static final String TABLE = "TableView1";
   private static final String WEEK = "WeekOfYear(D)";
   // D covers the plain weeks 1 to 40
   private static final int FIXTURE_WEEKS = 40;
   private static final int DC_WEEK = 42;
   private static final double DC_WEEK_TOTAL = 33020.0;
   private static final double PLAIN_WEEK_4_TOTAL = 8250.0;
}
