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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DataSetFilter;
import inetsoft.report.TableLens;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.DataMap;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.DCMergeDatePartFilter.MergePartCell;
import inetsoft.test.*;
import inetsoft.test.dc.DcInMemoryViewsheetFixture;
import inetsoft.test.dc.DcPeriods;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.ThreadContext;
import inetsoft.web.viewsheet.controller.VSDataTipService;
import inetsoft.web.viewsheet.controller.chart.VSChartBrushService;
import inetsoft.web.viewsheet.controller.chart.VSChartFlyoverService;
import inetsoft.web.viewsheet.event.OpenDataTipEvent;
import inetsoft.web.viewsheet.event.chart.VSChartBrushEvent;
import inetsoft.web.viewsheet.event.chart.VSChartFlyoverEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78446: a brush, data tip or flyover from a plain Week of Year chart into a date
 * comparison chart or crosstab on the same table. The date comparison (Year period, Week To
 * Date, For Each Week, Day granularity) registers a runtime calc field WeekOfYear(D) on the
 * table, which holds month*10 + week of month, not the plain week. The plain week condition
 * was applied through a hidden date range column of the same name, which took over the date
 * comparison's own week group: the filtered data lost the calc column and its merged
 * (week-day) cells, the brushed layer was empty and the background showed the calc week codes.
 * <p>
 * Fixture: an in-memory runtime viewsheet ({@link DcInMemoryViewsheetFixture#openRuntime}) with
 * one row per day from January 1 to March 11 of 2019, 2020 and 2021, inside the date comparison
 * window. Expected values are summed from the source rows, not from the date comparison output.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  DcInMemoryViewsheetFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DateComparisonPlainWeekTargetViewsheetTest {
   private final DcInMemoryViewsheetFixture dcFixture = new DcInMemoryViewsheetFixture();

   @BeforeEach
   void setUp() {
      dcFixture.setUp();
   }

   @AfterEach
   void tearDown() {
      dcFixture.tearDown();
   }

   /**
    * VSChartBrushService on a plain week bar: the date comparison chart keeps the unbrushed
    * values as background and brushes the days of that week, merged like the unbrushed data;
    * the date comparison crosstab shows those days merged.
    */
   @Test
   void plainWeekBrushKeepsDateComparisonLayout() throws Exception {
      Fixture f = new Fixture();

      for(int week : WEEKS) {
         VSChartBrushEvent event = new VSChartBrushEvent();
         event.setChartName(SOURCE);
         event.setSelected(f.click(week));
         new VSChartBrushService(lifecycle(), f.service(), null)
            .eventHandler(f.rvs.getID(), event, "", ThreadContext.getPrincipal(),
                          mock(CommandDispatcher.class));
         assertNotNull(f.source.getBrushSelection(), "brush selection of week " + week);
         f.resetTargets();

         f.assertBrushedChart(week);
         f.assertMergedRows(f.box.getData(DC_CROSSTAB), f.expected(week), "brush " + week);

         f.source.setBrushSelection(null);
         f.resetTargets();
      }
   }

   /**
    * A plain week that has no data, but is a valid week code of the calc field (13 = third
    * week of January), brushes nothing: the condition is not applied to the calc field.
    */
   @Test
   void plainWeekWithoutDataBrushesNothing() throws Exception {
      Fixture f = new Fixture();
      assertTrue(f.expected(EMPTY_WEEK).isEmpty(), "fixture: no data in week " + EMPTY_WEEK);
      VSSelection selection = new VSSelection();
      VSPoint point = new VSPoint();
      point.addValue(new VSFieldValue(WEEK, String.valueOf(EMPTY_WEEK)));
      selection.addPoint(point);
      f.source.setBrushSelection(selection);
      f.resetTargets();

      f.assertBrushedChart(EMPTY_WEEK);
      f.assertMergedRows(f.box.getData(DC_CROSSTAB), Map.of(), "brush " + EMPTY_WEEK);
   }

   /**
    * VSDataTipService on a plain week bar: a date comparison chart or crosstab tip view shows
    * the days of that week merged, and its own tip condition is not changed by the query.
    */
   @Test
   void plainWeekDataTipKeepsDateComparisonLayout() throws Exception {
      Fixture f = new Fixture();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) f.source.getVSAssemblyInfo();
      info.setTipOptionValue(TipVSAssemblyInfo.VIEWTIP_OPTION);

      for(String target : TARGETS) {
         info.setTipViewValue(target);

         for(int week : WEEKS) {
            OpenDataTipEvent event = new OpenDataTipEvent();
            setField(event, "name", target);
            setField(event, "parent", SOURCE);
            setField(event, "conds", f.click(week));
            new VSDataTipService(lifecycle(), f.service())
               .applyDataTip(f.rvs.getID(), event, ThreadContext.getPrincipal(), "",
                             mock(CommandDispatcher.class));

            f.assertTipView(target, week, "data tip " + week);
         }
      }
   }

   /**
    * VSChartFlyoverService on a plain week bar: date comparison flyover views show the days of
    * that week merged, and their own tip conditions are not changed by the query.
    */
   @Test
   void plainWeekFlyoverKeepsDateComparisonLayout() throws Exception {
      Fixture f = new Fixture();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) f.source.getVSAssemblyInfo();
      info.setFlyoverViewsValue(TARGETS);

      for(int week : WEEKS) {
         VSChartFlyoverEvent event = new VSChartFlyoverEvent();
         event.setChartName(SOURCE);
         setField(event, "conditions", f.click(week));
         new VSChartFlyoverService(lifecycle(), f.service(), null)
            .eventHandler(f.rvs.getID(), event, "", ThreadContext.getPrincipal(),
                          mock(CommandDispatcher.class));

         for(String target : TARGETS) {
            f.assertTipView(target, week, "flyover " + week);
         }
      }
   }

   private static CoreLifecycleService lifecycle() {
      CoreLifecycleService lifecycle = mock(CoreLifecycleService.class);
      when(lifecycle.createList(anyBoolean(), any(), any(), any()))
         .thenAnswer(inv -> new ChangedAssemblyList(false));
      return lifecycle;
   }

   private static void setField(Object obj, String name, Object value) throws Exception {
      Field field = obj.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(obj, value);
   }

   private static List<String> headers(DataSet data) {
      List<String> list = new ArrayList<>();

      for(int c = 0; c < data.getColCount(); c++) {
         list.add(data.getHeader(c));
      }

      return list;
   }

   private static int year(Object date) {
      assertInstanceOf(Date.class, date, "YearOfWeek(D) value");
      Calendar cal = new GregorianCalendar();
      cal.setTime((Date) date);
      return cal.get(Calendar.YEAR);
   }

   /**
    * The key of a date comparison row: the year and the merged week-day cell, as text.
    */
   private static String mergedKey(Object year, Object part, String msg) {
      assertInstanceOf(MergePartCell.class, part, msg + ": DayOfWeek(D) is merged");
      return year(year) + " " + part;
   }

   /**
    * The year and day of week of a merged week-day cell, for the expected values.
    */
   private static String dayKey(Object year, Object part) {
      return year(year) + "/" + ((MergePartCell) part).getOriginalData();
   }

   private static double number(Object value) {
      return value instanceof Number ? ((Number) value).doubleValue() : 0;
   }

   /**
    * The reporter's date comparison chart (Chart2), the same date comparison on a crosstab
    * (XDc) and the plain Week of Year chart that brushes them.
    */
   private final class Fixture {
      final RuntimeViewsheet rvs;
      final ViewsheetSandbox box;
      final Viewsheet vs;
      final ChartVSAssembly source;
      // all data of the date comparison chart: merged key -> Sum(wind)
      final Map<String, Double> allData;
      final List<String> allHeaders;
      // the day parts (Sunday = 1) in the date comparison window
      final Set<Object> dcDays = new HashSet<>();
      // source rows: [date, wind]
      final List<Object[]> rows = new ArrayList<>();

      Fixture() throws Exception {
         List<Object[]> sheetRows = new ArrayList<>();

         for(int year = 2019; year <= 2021; year++) {
            for(int offset = 0; offset < 70; offset++) {
               LocalDate day = LocalDate.of(year, 1, 1).plusDays(offset);
               int wind = DcInMemoryViewsheetFixture.dayValue(day);
               sheetRows.add(new Object[] { java.sql.Date.valueOf(day), wind });
               rows.add(new Object[] { DcPeriods.toDate(day), wind });
            }
         }

         DcInMemoryViewsheetFixture.RuntimeSheet sheet = dcFixture.openRuntime(
            new String[] { "D", "wind" }, new String[] { XSchema.DATE, XSchema.INTEGER },
            sheetRows);
         rvs = sheet.rvs();
         box = sheet.box();
         vs = sheet.vs();
         SourceInfo sinfo = sheet.source();

         ChartVSAssembly chart2 = chart(sinfo, DC_CHART, XConstants.YEAR_DATE_GROUP, reporterDc());
         source = chart(sinfo, SOURCE, DateRangeRef.WEEK_OF_YEAR_PART, null);
         crosstab(sinfo, DC_CROSSTAB, reporterDc());
         assertDoesNotThrow(() -> box.getVGraphPair(DC_CHART, true, null), DC_CHART);
         assertTrue(chart2.getVSChartInfo().isAppliedDateComparison(), "Chart2 DC applied");

         DataSet all = (DataSet) box.getData(DC_CHART);
         allHeaders = headers(all);
         assertTrue(allHeaders.contains(WEEK), "Chart2 headers " + allHeaders);
         allData = new HashMap<>();

         for(int r = 0; r < all.getRowCount(); r++) {
            Object part = all.getData(DAY, r);
            allData.put(mergedKey(all.getData(YEAR, r), part, "Chart2 all data"),
                        number(all.getData(SUM, r)));
            dcDays.add(((MergePartCell) part).getOriginalData());
         }
      }

      /**
       * The expected rows of the date comparison targets filtered on a plain week: year/day of
       * week -> Sum(wind), summed from the source rows of that week in the comparison window.
       */
      Map<String, Double> expected(int week) {
         Map<String, Double> map = new HashMap<>();
         Calendar cal = new GregorianCalendar();

         for(Object[] row : rows) {
            Date date = (Date) row[0];
            Object dow = DateRangeRef.getData(DateRangeRef.DAY_OF_WEEK_PART, date);

            if(((Number) DateRangeRef.getData(DateRangeRef.WEEK_OF_YEAR_PART, date)).intValue()
               == week && dcDays.contains(dow))
            {
               cal.setTime(date);
               map.merge(cal.get(Calendar.YEAR) + "/" + dow, number(row[1]), Double::sum);
            }
         }

         return map;
      }

      /**
       * The click string of the source chart's bar of a plain week.
       */
      String click(int week) throws Exception {
         box.clearGraph(SOURCE);
         VGraphPair pair = box.getVGraphPair(SOURCE, true, null);
         DataSet data = pair.getRealSizeVGraph().getCoordinate().getDataSet();
         int col = data.indexOfHeader(WEEK);
         assertTrue(col >= 0, SOURCE + " headers " + headers(data));

         for(int r = 0; r < data.getRowCount(); r++) {
            if(((Number) data.getData(col, r)).intValue() == week) {
               assertFalse(expected(week).isEmpty(), "fixture: data in week " + week);
               return SUM + "^INDEX:" + r;
            }
         }

         return fail("no " + SOURCE + " bar of week " + week);
      }

      ViewsheetService service() throws Exception {
         ViewsheetService service = mock(ViewsheetService.class);
         when(service.getViewsheet(eq(rvs.getID()), any())).thenReturn(rvs);
         return service;
      }

      void resetTargets() {
         for(String target : TARGETS) {
            box.resetDataMap(target);
            box.clearGraph(target);
         }
      }

      /**
       * Chart2 brushed on a plain week: the brushed data has the all-data layout and the
       * week's days, and the rendered data set keeps the unbrushed values as background.
       */
      void assertBrushedChart(int week) throws Exception {
         String msg = "Chart2 brush " + week;
         Map<String, Double> expected = expected(week);
         DataSet brushed = (DataSet) box.getData(DC_CHART, true, DataMap.NORMAL);
         assertEquals(allHeaders, headers(brushed), msg + " headers");
         assertEquals(allData, merged((DataSet) box.getData(DC_CHART, true, DataMap.ZOOM),
                                      msg + " all data"), msg + " all data");
         Map<String, Double> rows = assertMergedRows(brushed, expected, msg);

         for(Map.Entry<String, Double> e : rows.entrySet()) {
            assertEquals(allData.get(e.getKey()), e.getValue(), 0.001,
                         msg + " brushed value of " + e.getKey());
         }

         VGraphPair pair = box.getVGraphPair(DC_CHART, true, null);
         BrushDataSet bset = brushDataSet(pair.getRealSizeVGraph().getCoordinate().getDataSet());
         assertNotNull(bset, msg + ": rendered data is brushed");
         int brow = bset.getBaseRowCount();
         assertEquals(rows.size(), brow, msg + " brushed rows");
         String allSum = BrushDataSet.ALL_HEADER_PREFIX + SUM;
         Map<String, Double> background = new HashMap<>();

         for(int r = brow; r < bset.getRowCount(); r++) {
            background.put(mergedKey(bset.getData(YEAR, r), bset.getData(DAY, r), msg),
                           number(bset.getData(allSum, r)));
         }

         assertEquals(allData, background, msg + " background");
      }

      /**
       * Check the rows of a date comparison target are merged and are the expected days.
       * @return merged key -> Sum(wind).
       */
      Map<String, Double> assertMergedRows(Object data, Map<String, Double> expected, String msg) {
         Map<String, Double> merged = new HashMap<>();
         Map<String, Double> days = new HashMap<>();

         if(data instanceof DataSet dset) {
            assertTrue(dset.indexOfHeader(WEEK) >= 0, msg + " headers " + headers(dset));

            for(int r = 0; r < dset.getRowCount(); r++) {
               Object year = dset.getData(YEAR, r);
               Object part = dset.getData(DAY, r);
               double value = number(dset.getData(SUM, r));
               merged.put(mergedKey(year, part, msg), value);
               days.merge(dayKey(year, part), value, Double::sum);
            }
         }
         else if(data instanceof TableLens lens) {
            lens.moreRows(Integer.MAX_VALUE);
            List<Object> lheaders = new ArrayList<>();

            for(int c = 0; c < lens.getColCount(); c++) {
               lheaders.add(lens.getObject(0, c));
            }

            int yearCol = lheaders.indexOf(YEAR);
            int dayCol = lheaders.indexOf(DAY);
            assertTrue(yearCol >= 0 && dayCol >= 0, msg + " headers " + lheaders);
            int sumCol = lens.getColCount() - 1;

            for(int r = lens.getHeaderRowCount(); r < lens.getRowCount(); r++) {
               Object year = lens.getObject(r, yearCol);
               Object part = lens.getObject(r, dayCol);
               double value = number(lens.getObject(r, sumCol));
               merged.put(mergedKey(year, part, msg), value);
               days.merge(dayKey(year, part), value, Double::sum);
            }
         }
         else {
            fail(msg + " data " + data);
         }

         assertEquals(expected, days, msg + " rows " + merged.keySet());
         return merged;
      }

      /**
       * Check a tip/flyover view filtered on a plain week, and that the query did not change
       * the view's own tip condition.
       */
      void assertTipView(String target, int week, String msg) throws Exception {
         VSAssembly view = (VSAssembly) vs.getAssembly(target);
         ConditionList cond = view.getTipConditionList() == null ? null :
            view.getTipConditionList().getConditionList();
         assertNotNull(cond, msg + " " + target + " tip condition");
         String before = cond.toString();
         box.resetDataMap(target);
         box.clearGraph(target);
         assertMergedRows(box.getData(target), expected(week), msg + " " + target);
         assertEquals(before, cond.toString(), msg + " " + target + " tip condition changed");
         assertEquals(WEEK, cond.getConditionItem(0).getAttribute().getName(),
                      msg + " " + target + " tip condition column");
         view.setTipConditionList(null);
         box.resetDataMap(target);
         box.clearGraph(target);
      }

      private Map<String, Double> merged(DataSet dset, String msg) {
         Map<String, Double> map = new HashMap<>();

         for(int r = 0; r < dset.getRowCount(); r++) {
            map.put(mergedKey(dset.getData(YEAR, r), dset.getData(DAY, r), msg),
                    number(dset.getData(SUM, r)));
         }

         return map;
      }

      private BrushDataSet brushDataSet(DataSet data) {
         while(data != null && !(data instanceof BrushDataSet)) {
            data = data instanceof DataSetFilter ? ((DataSetFilter) data).getDataSet() : null;
         }

         return (BrushDataSet) data;
      }

      private ChartVSAssembly chart(SourceInfo sinfo, String name, int level,
                                    DateComparisonInfo dc)
      {
         ChartVSAssembly chart = new ChartVSAssembly(vs, name);
         chart.setSourceInfo((SourceInfo) sinfo.clone());
         VSChartInfo info = chart.getVSChartInfo();
         info.addXField(dateDim(new VSChartDimensionRef(), level));
         info.addYField(sumWind(new VSChartAggregateRef()));
         ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).setDateComparisonInfo(dc);
         vs.addAssembly(chart);
         return chart;
      }

      private void crosstab(SourceInfo sinfo, String name, DateComparisonInfo dc) {
         CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);
         crosstab.setSourceInfo((SourceInfo) sinfo.clone());
         VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
         cinfo.setDesignRowHeaders(new inetsoft.uql.erm.DataRef[] {
            dateDim(new VSDimensionRef(), XConstants.YEAR_DATE_GROUP) });
         cinfo.setDesignAggregates(new inetsoft.uql.erm.DataRef[] {
            sumWind(new VSAggregateRef()) });
         ((CrosstabVSAssemblyInfo) crosstab.getVSAssemblyInfo()).setDateComparisonInfo(dc);
         vs.addAssembly(crosstab);
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

   private static final String SOURCE = "ChartWeek";
   private static final String DC_CHART = "Chart2";
   private static final String DC_CROSSTAB = "XDc";
   private static final String[] TARGETS = { DC_CHART, DC_CROSSTAB };
   private static final String WEEK = "WeekOfYear(D)";
   private static final String YEAR = "YearOfWeek(D)";
   private static final String DAY = "DayOfWeek(D)";
   private static final String SUM = "Sum(wind)";
   // the reporter's week 4 (calc week 13), and week 11, which is calc week 32 in 2019/2020 and
   // 31 in 2021
   private static final int[] WEEKS = { 4, 11 };
   // a plain week with no data that is a calc week code of the fixture (January week 3)
   private static final int EMPTY_WEEK = 13;
}
