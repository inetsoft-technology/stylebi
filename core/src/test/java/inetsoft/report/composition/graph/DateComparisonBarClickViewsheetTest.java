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
import inetsoft.report.TableLens;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.DCMergeDatePartFilter;
import inetsoft.test.*;
import inetsoft.test.dc.DcInMemoryViewsheetFixture;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
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
 * Bug #78446: a click on a bar of the reporter's date comparison chart (Year period, Week To
 * Date, For Each Week, Day granularity) reaches the services as the click string
 * {@code Sum(wind)^INDEX:<row>}, which is resolved through VSDataSet.getFieldValue. The
 * DayOfWeek(D) cell of such a chart is a DCMergeDatePartFilter.MergePartCell (week part and day
 * part). Its equivalence cell recomputed the week from the period start (124 for 2021, 125 for
 * 2020), and the selection then held two contradictory WeekOfYear(D) values, so brush, data tip
 * and flyover filtered every target to nothing.
 * <p>
 * Fixture: an in-memory runtime viewsheet ({@link DcInMemoryViewsheetFixture#openRuntime}) with
 * one row per day of 2019 to 2021 (D, wind, name), so the chart has bars in several periods and
 * weeks. Each case clicks the first bar of 2020 and of 2021 and checks the targets show that
 * bar's own Sum(wind).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  DcInMemoryViewsheetFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DateComparisonBarClickViewsheetTest {
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
    * VSChartBrushService: brushing a bar filters chart and crosstab targets to that bar, in
    * the current and in a previous period.
    */
   @Test
   void barClickBrushFiltersTargetsInEveryPeriod() throws Exception {
      Fixture f = new Fixture();

      for(int year : YEARS) {
         Bar bar = f.bar(year);
         VSChartBrushEvent event = new VSChartBrushEvent();
         event.setChartName(DC_CHART);
         event.setSelected(bar.click());
         new VSChartBrushService(lifecycle(), f.service(), null)
            .eventHandler(f.rvs.getID(), event, "", ThreadContext.getPrincipal(),
                          mock(CommandDispatcher.class));

         for(String target : TARGETS) {
            f.box.resetDataMap(target);
            assertEquals(bar.value, total(f.box, target), 0.001,
                         "brush " + bar + " on " + target);
         }

         f.chart2.setBrushSelection(null);
         f.resetTargets();
      }
   }

   /**
    * VSDataTipService: a data tip view of a bar shows that bar, in the current and in a
    * previous period.
    */
   @Test
   void barClickDataTipFiltersTipViewInEveryPeriod() throws Exception {
      Fixture f = new Fixture();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) f.chart2.getVSAssemblyInfo();
      info.setTipOptionValue(TipVSAssemblyInfo.VIEWTIP_OPTION);
      info.setTipViewValue("ChartYear");

      for(int year : YEARS) {
         Bar bar = f.bar(year);
         OpenDataTipEvent event = new OpenDataTipEvent();
         setField(event, "name", "ChartYear");
         setField(event, "parent", DC_CHART);
         setField(event, "conds", bar.click());
         new VSDataTipService(lifecycle(), f.service())
            .applyDataTip(f.rvs.getID(), event, ThreadContext.getPrincipal(), "",
                          mock(CommandDispatcher.class));

         VSAssembly tip = (VSAssembly) f.vs.getAssembly("ChartYear");
         f.box.resetDataMap("ChartYear");
         assertEquals(bar.value, total(f.box, "ChartYear"), 0.001,
                      "data tip " + bar + " condition " + tip.getTipConditionList());
         tip.setTipConditionList(null);
         f.box.resetDataMap("ChartYear");
      }
   }

   /**
    * VSChartFlyoverService: flyover views of a bar show that bar, in the current and in a
    * previous period.
    */
   @Test
   void barClickFlyoverFiltersViewsInEveryPeriod() throws Exception {
      Fixture f = new Fixture();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) f.chart2.getVSAssemblyInfo();
      info.setFlyoverViewsValue(TARGETS);

      for(int year : YEARS) {
         Bar bar = f.bar(year);
         VSChartFlyoverEvent event = new VSChartFlyoverEvent();
         event.setChartName(DC_CHART);
         setField(event, "conditions", bar.click());
         new VSChartFlyoverService(lifecycle(), f.service(), null)
            .eventHandler(f.rvs.getID(), event, "", ThreadContext.getPrincipal(),
                          mock(CommandDispatcher.class));

         for(String target : TARGETS) {
            VSAssembly view = (VSAssembly) f.vs.getAssembly(target);
            f.box.resetDataMap(target);
            assertEquals(bar.value, total(f.box, target), 0.001,
                         "flyover " + bar + " on " + target + " condition " +
                            view.getTipConditionList());
            view.setTipConditionList(null);
         }

         f.resetTargets();
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

   private static double total(ViewsheetSandbox box, String name) throws Exception {
      Object data = box.getData(name);
      double sum = 0;

      if(data instanceof DataSet dset) {
         int col = dset.indexOfHeader("Sum(wind)");
         assertTrue(col >= 0, name + " has no Sum(wind)");

         for(int r = 0; r < dset.getRowCount(); r++) {
            Object v = dset.getData(col, r);
            sum += v instanceof Number ? ((Number) v).doubleValue() : 0;
         }
      }
      else if(data instanceof TableLens lens) {
         lens.moreRows(Integer.MAX_VALUE);
         int col = lens.getColCount() - 1;

         for(int r = lens.getHeaderRowCount(); r < lens.getRowCount(); r++) {
            Object v = lens.getObject(r, col);
            sum += v instanceof Number ? ((Number) v).doubleValue() : 0;
         }
      }
      else {
         fail(name + " data " + data);
      }

      return sum;
   }

   /**
    * A bar of the date comparison chart: its row in the chart's data set and its value.
    */
   private record Bar(int row, int year, Object part, double value) {
      String click() {
         return "Sum(wind)^INDEX:" + row;
      }

      @Override
      public String toString() {
         return year + " " + part + " (row " + row + ", " + value + ")";
      }
   }

   /**
    * The reporter's date comparison chart (Chart2) and the non date comparison targets.
    */
   private final class Fixture {
      final RuntimeViewsheet rvs;
      final ViewsheetSandbox box;
      final Viewsheet vs;
      final ChartVSAssembly chart2;

      Fixture() throws Exception {
         List<Object[]> rows = new ArrayList<>();

         for(LocalDate day = LocalDate.of(2019, 1, 1); day.isBefore(LocalDate.of(2022, 1, 1));
             day = day.plusDays(1))
         {
            rows.add(new Object[] { java.sql.Date.valueOf(day),
                                    DcInMemoryViewsheetFixture.dayValue(day),
                                    "n" + day.getDayOfYear() % 3 });
         }

         DcInMemoryViewsheetFixture.RuntimeSheet sheet = dcFixture.openRuntime(
            new String[] { "D", "wind", "name" },
            new String[] { XSchema.DATE, XSchema.INTEGER, XSchema.STRING }, rows);
         rvs = sheet.rvs();
         box = sheet.box();
         vs = sheet.vs();
         SourceInfo source = sheet.source();

         chart2 = chart(source, DC_CHART, XConstants.YEAR_DATE_GROUP, reporterDc());
         chart(source, "ChartYear", XConstants.YEAR_DATE_GROUP, null);
         chart(source, "ChartWeek", DateRangeRef.WEEK_OF_YEAR_PART, null);
         crosstab(source, "XName", nameDim(new VSDimensionRef()));
         assertDoesNotThrow(() -> box.getVGraphPair(DC_CHART, true, null), DC_CHART);
         assertTrue(chart2.getVSChartInfo().isAppliedDateComparison(), "Chart2 DC applied");
      }

      /**
       * The first bar of a period, read from the data set the click string indexes (the real
       * size vgraph's data set, as ChartVSSelectionUtil.getVSSelection reads it).
       */
      Bar bar(int year) throws Exception {
         VGraphPair pair = box.getVGraphPair(DC_CHART, true, null);
         DataSet data = pair.getRealSizeVGraph().getCoordinate().getDataSet();
         assertInstanceOf(VSDataSet.class, data);
         int yearCol = data.indexOfHeader("YearOfWeek(D)");
         int partCol = data.indexOfHeader("DayOfWeek(D)");
         assertTrue(yearCol >= 0 && partCol >= 0, "Chart2 headers");
         Calendar cal = new GregorianCalendar();

         for(int r = 0; r < data.getRowCount(); r++) {
            cal.setTime((Date) data.getData(yearCol, r));
            Object part = data.getData(partCol, r);

            if(cal.get(Calendar.YEAR) == year) {
               assertInstanceOf(DCMergeDatePartFilter.MergePartCell.class, part,
                                "Chart2 week and day part cell");
               double value = ((Number) data.getData("Sum(wind)", r)).doubleValue();
               assertTrue(value > 0, "bar value");
               return new Bar(r, year, part, value);
            }
         }

         return fail("no Chart2 bar in " + year);
      }

      ViewsheetService service() throws Exception {
         ViewsheetService service = mock(ViewsheetService.class);
         when(service.getViewsheet(eq(rvs.getID()), any())).thenReturn(rvs);
         return service;
      }

      void resetTargets() {
         for(String target : TARGETS) {
            box.resetDataMap(target);
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

      private void crosstab(SourceInfo source, String name, DataRef dim) {
         CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);
         crosstab.setSourceInfo((SourceInfo) source.clone());
         VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
         cinfo.setDesignRowHeaders(new DataRef[] { dim });
         cinfo.setDesignAggregates(new DataRef[] { sumWind(new VSAggregateRef()) });
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

   private static final String DC_CHART = "Chart2";
   // 2021 is the current period (week part equivalence 124), 2020 a previous one (125)
   private static final int[] YEARS = { 2020, 2021 };
   private static final String[] TARGETS = { "ChartYear", "ChartWeek", "XName" };
}
