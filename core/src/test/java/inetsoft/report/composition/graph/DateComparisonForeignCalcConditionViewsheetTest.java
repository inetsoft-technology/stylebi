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
import inetsoft.report.TableLens;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
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
 * Bug #78446: a date comparison chart registers the runtime calc field WeekOfYear(D) on its
 * table, and the calc field is appended to the query of every assembly on that table. Its name
 * equals the name of a plain D at Week of Year dimension, so the two same-named columns must not
 * be confused by:
 * <ul>
 *    <li>a non date comparison chart grouping on its own week column (ChartVSAQuery);</li>
 *    <li>a brush, data tip or flyover condition from the date comparison chart/crosstab, which
 *        is on the calc field and must filter every target, with or without its own week
 *        column (VSAQuery.replaceRef);</li>
 *    <li>a plain week condition from a non date comparison chart/crosstab, which must stay on
 *        the date range (VSUtil.fixCondition, VSAQuery.fixConditionColumn).</li>
 * </ul>
 * Fixture: EmbeddedVS1, calc D = new Date(2021, 0, field['day']), Sum(wind) = 653732 in total.
 * The date comparison chart's first bar is in its calc week 42 (Sum(wind) of the calc week 42 is
 * 33020, which falls in plain weeks 15 and 16), and the bar itself is 205. The plain week 4 is
 * 8250.
 * <p>
 * Tagged slow: the full target matrix takes about 38 s alone. One fast case per site is in
 * {@link DateComparisonForeignCalcConditionCoreViewsheetTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("slow")
@Tag("integration")
class DateComparisonForeignCalcConditionViewsheetTest {
   /**
    * Aggregated and detail Week of Year charts next to the date comparison chart group on
    * their own week column (the original "Column not found: WeekOfYear(D)").
    */
   @Test
   void weekOfYearChartsKeepTheirOwnWeekColumn() throws Exception {
      Fixture f = new Fixture(false);
      checkWeekCharts(f);
   }

   /**
    * Same with a hidden column on the worksheet table (public != private column selection).
    */
   @Test
   void weekOfYearChartsKeepTheirOwnWeekColumnWithHiddenWorksheetColumn() throws Exception {
      Fixture f = new Fixture(true);
      checkWeekCharts(f);
   }

   private static void checkWeekCharts(Fixture f) throws Exception {
      DataSet week = chartData(f.box, "ChartWeek");
      assertEquals(List.of("WeekOfYear(D)", "Sum(wind)"), headers(week));
      assertEquals(FIXTURE_WEEKS, week.getRowCount(), "ChartWeek rows");
      assertEquals(1, ((Number) week.getData("WeekOfYear(D)", 0)).intValue());
      assertEquals(3180.0, ((Number) week.getData("Sum(wind)", 0)).doubleValue(), 0.001);
      assertEquals(PLAIN_WEEK_4_TOTAL,
                   ((Number) week.getData("Sum(wind)", 3)).doubleValue(), 0.001);
      assertEquals(UNFILTERED_TOTAL, total(f.box, "ChartWeek"), 0.001);

      DataSet detail = chartData(f.box, "ChartDetail");
      List<String> headers = headers(detail);
      assertTrue(headers.contains("WeekOfYear(D)"), "ChartDetail headers " + headers);
      assertFalse(headers.stream().anyMatch(h -> h.endsWith("_1")),
                  "ChartDetail headers " + headers);
      Set<Integer> weeks = new TreeSet<>();

      for(int r = 0; r < detail.getRowCount(); r++) {
         weeks.add(((Number) detail.getData("WeekOfYear(D)", r)).intValue());
      }

      assertEquals(FIXTURE_WEEKS, weeks.size(), "ChartDetail weeks " + weeks);
      assertEquals(1, (int) ((TreeSet<Integer>) weeks).first(), "ChartDetail weeks " + weeks);
      assertEquals(UNFILTERED_TOTAL, total(f.box, "XWeek"), 0.001);
      assertEquals(UNFILTERED_TOTAL, total(f.box, "ChartYear"), 0.001);

      f.box.resetDataMap(TABLE);
      TableLens table = (TableLens) f.box.getData(TABLE);
      table.moreRows(Integer.MAX_VALUE);
      List<Object> theaders = new ArrayList<>();

      for(int c = 0; c < table.getColCount(); c++) {
         theaders.add(table.getObject(0, c));
      }

      // a plain table on the source does not show the date comparison calc field
      assertFalse(theaders.contains(WEEK), TABLE + " headers " + theaders);
      assertFalse(theaders.stream().anyMatch(h -> String.valueOf(h).endsWith("_1")),
                  TABLE + " headers " + theaders);
      assertTrue(theaders.contains("wind"), TABLE + " headers " + theaders);
   }

   /**
    * Brush on the calc week only of the date comparison chart: every target is filtered on the
    * calc week (the reporter's "brush ignored" for Region targets and "wrong week" for Week of
    * Year targets).
    */
   @Test
   void dcWeekBrushFiltersEveryTarget() throws Exception {
      Fixture f = new Fixture(false);
      brush(f, f.chart2, weekSelection(DC_WEEK));

      for(String target : ALL_TARGETS) {
         assertEquals(DC_WEEK_TOTAL, total(f.box, target), 0.001, target);
      }

      // the brushed calc week is plain weeks 15 and 16, not the plain week 42
      assertEquals(List.of(15, 16), weekRows(f.box, "XWeek"));
      assertEquals(List.of(15, 16), weekRows(f.box, "ChartWeek"));
   }

   /**
    * Brush on the date comparison chart's first bar (all of its dimensions).
    */
   @Test
   void dcBarBrushFiltersEveryTarget() throws Exception {
      Fixture f = new Fixture(false);
      VSDataSet data = (VSDataSet) f.box.getData("Chart2");
      VSSelection selection = new VSSelection();
      VSPoint point = new VSPoint();

      for(String header : headers(data)) {
         if(!"Sum(wind)".equals(header)) {
            point.addValue(new VSFieldValue(header, String.valueOf(data.getData(header, 0))));
         }
      }

      selection.addPoint(point);
      DateComparisonUtil.fixDatePartSelection(f.chart2, data, selection);
      brush(f, f.chart2, selection);

      for(String target : ALL_TARGETS) {
         assertEquals(DC_BAR_TOTAL, total(f.box, target), 0.001, target);
      }
   }

   /**
    * Data tip / flyover from the date comparison chart's calc week. The condition is built from
    * the selection as VSUtil.fixCondition builds it for a chart and then goes through
    * VSUtil.fixCondition.
    */
   @Test
   void dcChartTipFiltersEveryTarget() throws Exception {
      Fixture f = new Fixture(false);

      for(String target : ALL_TARGETS) {
         chartTip(f, target, f.chart2, weekSelection(DC_WEEK));
      }

      for(String target : ALL_TARGETS) {
         assertEquals(DC_WEEK_TOTAL, total(f.box, target), 0.001, target);
      }
   }

   /**
    * Flyover from the first data cell of a date comparison crosstab (the reporter's flyover
    * source), built by VSUtil.getConditionList + VSUtil.fixCondition as
    * BaseTableFlyoverService does.
    */
   @Test
   void dcCrosstabFlyoverFiltersEveryTarget() throws Exception {
      Fixture f = new Fixture(false);
      TableLens xdc = (TableLens) f.box.getData("XDc");
      xdc.moreRows(Integer.MAX_VALUE);
      String cell = xdc.getHeaderRowCount() + "X" + (xdc.getColCount() - 1);
      assertEquals(DC_BAR_TOTAL,
                   ((Number) xdc.getObject(xdc.getHeaderRowCount(), xdc.getColCount() - 1))
                      .doubleValue(), 0.001, "XDc first cell");
      ConditionList conds = VSUtil.getConditionList(f.rvs, f.vs.getAssembly("XDc"), cell);

      for(String target : ALL_TARGETS) {
         tip(f, target, conds, "XDc", cell);
      }

      for(String target : ALL_TARGETS) {
         assertEquals(DC_BAR_TOTAL, total(f.box, target), 0.001, target);
      }
   }

   /**
    * Data tip from a non date comparison Week of Year chart: the plain week stays a plain
    * week on every target.
    */
   @Test
   void plainChartTipFiltersOnPlainWeek() throws Exception {
      Fixture f = new Fixture(false);

      ChartVSAssembly source = (ChartVSAssembly) f.vs.getAssembly("ChartWeek");

      for(String target : PLAIN_SOURCE_TARGETS) {
         chartTip(f, target, source, weekSelection(4));
      }

      for(String target : PLAIN_SOURCE_TARGETS) {
         assertEquals(PLAIN_WEEK_4_TOTAL, total(f.box, target), 0.001, target);
      }
   }

   /**
    * Flyover from a non date comparison Week of Year crosstab cell (week 4).
    */
   @Test
   void plainCrosstabFlyoverFiltersOnPlainWeek() throws Exception {
      Fixture f = new Fixture(false);
      ConditionList conds = VSUtil.getConditionList(f.rvs, f.vs.getAssembly("XWeek"), "4X1");

      for(String target : PLAIN_SOURCE_TARGETS) {
         tip(f, target, conds, "XWeek", "4X1");
      }

      for(String target : PLAIN_SOURCE_TARGETS) {
         assertEquals(PLAIN_WEEK_4_TOTAL, total(f.box, target), 0.001, target);
      }
   }

   /**
    * Ordinary brushing from a non date comparison Week of Year chart while a date comparison
    * chart is on the same table.
    */
   @Test
   void plainChartBrushFiltersOnPlainWeek() throws Exception {
      Fixture f = new Fixture(false);
      ChartVSAssembly source = (ChartVSAssembly) f.vs.getAssembly("ChartWeek");
      brush(f, source, weekSelection(4));

      for(String target : PLAIN_SOURCE_TARGETS) {
         assertEquals(PLAIN_WEEK_4_TOTAL, total(f.box, target), 0.001, target);
      }
   }

   /**
    * A selection list bound to the date comparison calc field filters a Week of Year chart on
    * the same table on the calc week (#6816 left this with the original "Column not found").
    */
   @Test
   void selectionOnDcCalcFiltersWeekOfYearChart() throws Exception {
      Fixture f = new Fixture(false);
      SelectionListVSAssembly selection = new SelectionListVSAssembly(f.vs, "WeekList");
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) selection.getInfo();
      info.setTableName(f.tname);
      ColumnRef column = new ColumnRef(new AttributeRef(null, WEEK));
      column.setDataType(XSchema.INTEGER);
      info.setDataRef(column);
      f.vs.addAssembly(selection);

      f.box.lockWrite();

      try {
         f.box.reset(null, f.vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
         SelectionList slist = new SelectionList();
         SelectionValue value =
            new SelectionValue(String.valueOf(DC_WEEK), String.valueOf(DC_WEEK));
         value.setState(SelectionValue.STATE_SELECTED);
         slist.setSelectionValues(new SelectionValue[] { value });
         int hint = selection.setStateSelectionList(slist);
         f.box.processChange(selection.getAbsoluteName(), hint, new ChangedAssemblyList());
      }
      finally {
         f.box.unlockWrite();
      }

      for(String target : new String[] { "ChartWeek", "XWeek", "ChartYear", "XName" }) {
         f.box.resetDataMap(target);
         assertEquals(DC_WEEK_TOTAL, total(f.box, target), 0.001, target);
      }

      assertEquals(List.of(15, 16), weekRows(f.box, "ChartWeek"));
   }

   private static void brush(Fixture f, ChartVSAssembly chart, VSSelection selection) {
      chart.setBrushSelection(selection);

      for(String target : ALL_TARGETS) {
         if(!target.equals(chart.getName())) {
            f.box.resetDataMap(target);
         }
      }
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
      tip(f, target, conds, chart.getName(), null);
   }

   private static void tip(Fixture f, String target, ConditionList conds, String source,
                           String value) throws Exception
   {
      VSAssembly tip = (VSAssembly) f.vs.getAssembly(target);
      Object result = VSUtil.fixCondition(f.rvs, tip, conds, source, value);
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
      Object data = assertDoesNotThrow(() -> box.getData(name), name);
      double sum = 0;

      if(data instanceof DataSet) {
         DataSet dset = (DataSet) data;
         int col = dset.indexOfHeader("Sum(wind)");
         assertTrue(col >= 0, name + " headers " + headers(dset));

         for(int r = 0; r < dset.getRowCount(); r++) {
            Object v = dset.getData(col, r);
            sum += v instanceof Number ? ((Number) v).doubleValue() : 0;
         }
      }
      else {
         TableLens lens = (TableLens) data;
         assertNotNull(lens, name);
         lens.moreRows(Integer.MAX_VALUE);
         int col = lens.getColCount() - 1;

         for(int r = lens.getHeaderRowCount(); r < lens.getRowCount(); r++) {
            Object v = lens.getObject(r, col);
            sum += v instanceof Number ? ((Number) v).doubleValue() : 0;
         }
      }

      return sum;
   }

   private static List<Integer> weekRows(ViewsheetSandbox box, String name) throws Exception {
      Object data = box.getData(name);
      List<Integer> weeks = new ArrayList<>();

      if(data instanceof DataSet) {
         DataSet dset = (DataSet) data;
         int col = dset.indexOfHeader(WEEK);
         assertTrue(col >= 0, name + " headers " + headers(dset));

         for(int r = 0; r < dset.getRowCount(); r++) {
            weeks.add(((Number) dset.getData(col, r)).intValue());
         }
      }
      else {
         TableLens lens = (TableLens) data;
         lens.moreRows(Integer.MAX_VALUE);
         assertEquals(WEEK, lens.getObject(0, 0), name + " header");

         for(int r = lens.getHeaderRowCount(); r < lens.getRowCount(); r++) {
            if(lens.getObject(r, 0) instanceof Number) {
               weeks.add(((Number) lens.getObject(r, 0)).intValue());
            }
         }
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
    * The reporter's date comparison chart (Chart2) and non date comparison neighbors on the
    * same table.
    */
   private final class Fixture {
      final RuntimeViewsheet rvs;
      final ViewsheetSandbox box;
      final Viewsheet vs;
      final String tname;
      final ChartVSAssembly chart2;

      Fixture(boolean hideColumn) throws Exception {
         rvs = vsResource.getRuntimeViewsheet();
         box = rvs.getViewsheetSandbox().orElseThrow();
         vs = box.getViewsheet();
         SourceInfo source =
            (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
         tname = source.getSource();

         if(hideColumn) {
            hideWorksheetColumn();
         }

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
         ChartVSAssembly name = new ChartVSAssembly(vs, "ChartName");
         name.setSourceInfo((SourceInfo) source.clone());
         name.getVSChartInfo().addXField(nameDim(new VSChartDimensionRef()));
         name.getVSChartInfo().addYField(sumWind(new VSChartAggregateRef()));
         vs.addAssembly(name);
         ChartVSAssembly detail =
            chart(source, "ChartDetail", DateRangeRef.WEEK_OF_YEAR_PART, null);
         ((VSChartAggregateRef) detail.getVSChartInfo().getYField(0)).setFormulaValue("None");

         crosstab(source, "XWeek", dateDim(new VSDimensionRef(),
                                           DateRangeRef.WEEK_OF_YEAR_PART), null);
         crosstab(source, "XWeek2", dateDim(new VSDimensionRef(),
                                            DateRangeRef.WEEK_OF_YEAR_PART), null);
         crosstab(source, "XYear", dateDim(new VSDimensionRef(),
                                           XConstants.YEAR_DATE_GROUP), null);
         crosstab(source, "XName", nameDim(new VSDimensionRef()), null);
         crosstab(source, "XDc", dateDim(new VSDimensionRef(), XConstants.YEAR_DATE_GROUP),
                  reporterDc());

         for(String target : ALL_TARGETS) {
            if(target.startsWith("Chart")) {
               assertDoesNotThrow(() -> box.getVGraphPair(target, true, null), target);
            }

            assertEquals(UNFILTERED_TOTAL, total(box, target), 0.001, target + " unfiltered");
         }
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

      private void crosstab(SourceInfo source, String name, DataRef dim, DateComparisonInfo dc) {
         CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);
         crosstab.setSourceInfo((SourceInfo) source.clone());
         VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
         cinfo.setDesignRowHeaders(new DataRef[] { dim });
         cinfo.setDesignAggregates(new DataRef[] { sumWind(new VSAggregateRef()) });
         ((CrosstabVSAssemblyInfo) crosstab.getVSAssemblyInfo()).setDateComparisonInfo(dc);
         vs.addAssembly(crosstab);
      }

      private void hideWorksheetColumn() {
         for(Worksheet ws : new Worksheet[] {
            vs.getBaseWorksheet(), box.getAssetQuerySandbox().getWorksheet() })
         {
            TableAssembly table = (TableAssembly) ws.getAssembly(tname);
            ColumnSelection priv = table.getColumnSelection(false);

            for(int i = 0; i < priv.getAttributeCount(); i++) {
               ColumnRef column = (ColumnRef) priv.getAttribute(i);

               if("type".equals(column.getAttribute())) {
                  column.setVisible(false);
               }
            }

            table.resetColumnSelection();
            assertNotEquals(table.getColumnSelection(false), table.getColumnSelection(true),
                            "fixture: the hidden column makes public != private");
         }
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

   private static <T extends VSDimensionRef> T nameDim(T dim) {
      AttributeRef attr = new AttributeRef(null, "name");
      attr.setDataType(XSchema.STRING);
      ColumnRef column = new ColumnRef(attr);
      column.setDataType(XSchema.STRING);
      dim.setDataRef(column);
      dim.setGroupColumnValue("name");
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
   // targets of a condition from the date comparison chart/crosstab
   private static final String[] ALL_TARGETS = {
      "ChartWeek", "ChartWeek2", "ChartYear", "ChartName",
      "XWeek", "XWeek2", "XYear", "XName" };
   // targets of a condition from ChartWeek / XWeek
   private static final String[] PLAIN_SOURCE_TARGETS = {
      "ChartWeek2", "XWeek2", "ChartYear", "ChartName", "XYear", "XName" };
   private static final double UNFILTERED_TOTAL = 653732.0;
   // D covers the plain weeks 1 to 40
   private static final int FIXTURE_WEEKS = 40;
   private static final int DC_WEEK = 42;
   private static final double DC_WEEK_TOTAL = 33020.0;
   private static final double DC_BAR_TOTAL = 205.0;
   private static final double PLAIN_WEEK_4_TOTAL = 8250.0;
}
