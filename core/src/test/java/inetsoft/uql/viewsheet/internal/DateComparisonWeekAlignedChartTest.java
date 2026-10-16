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
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.EGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.mv.MVManager;
import inetsoft.report.composition.execution.*;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.ThreadContext;
import inetsoft.util.script.graal.pool.PoolConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78454 through the real chart pipeline ({@link ViewsheetSandbox#getVGraphPair}: the DC
 * query conditions, ChartDcProcessor's full-week period/part columns and
 * GraphGenerator's applyDateRange() hide selector) for the bug's chart: Year(Date) x Sum(Paid),
 * Previous 2 Years, Week to Date for each week, granularity Week, End Date Saturday 2021-01-02
 * (its week starts 2020-12-27, so 2020 is the current full-week year). Every compared year must
 * plot all of its weeks; only the extra prior year fetched for Change is hidden.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  DateComparisonWeekAlignedChartTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DateComparisonWeekAlignedChartTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   @BeforeEach
   void setUp() {
      AssetDataCache.getCache().clearCache();
      SreeEnv.setProperty(PoolConfig.ENABLED, "false");
      oldPrincipal = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false));
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setPrincipal(oldPrincipal);
      SreeEnv.remove(PoolConfig.ENABLED);
      AssetDataCache.getCache().clearCache();
   }

   /** Used to plot 53 weeks: the 2018-12-30 week of 2018 plus 2019. */
   @Test
   void valuePlotsTwoFullYears() throws Exception {
      assertEquals(Map.of("2018-01-01", 52, "2019-01-01", 52),
                   visibleWeeksPerYear(chart(DateComparisonInfo.VALUE, false)));
   }

   /** Used to show only 2019: the hide threshold was the calendar-anchored 2019-01-01. */
   @Test
   void changeHidesOnlyTheExtraPriorYear() throws Exception {
      assertEquals(Map.of("2018-01-01", 52, "2019-01-01", 52),
                   visibleWeeksPerYear(chart(DateComparisonInfo.CHANGE, false)));
   }

   /**
    * The toDate query was already week-anchored (2017, 2018 and 2019 up to week 51), but the
    * calendar-anchored threshold hid 2018 too.
    */
   @Test
   void toDateChangeHidesOnlyTheExtraPriorYear() throws Exception {
      assertEquals(Map.of("2018-01-01", 51, "2019-01-01", 51),
                   visibleWeeksPerYear(chart(DateComparisonInfo.CHANGE, true)));
   }

   /** The number of plotted (not hidden) rows, one per week, per full-week year. */
   private static Map<String, Integer> visibleWeeksPerYear(VGraphPair pair) {
      DataSet data = pair.getData();
      EGraph graph = pair.getEGraph();
      assertNotNull(data);
      assertNotNull(graph);
      GraphtDataSelector selector = graph.getElementCount() > 0 ?
         graph.getElement(0).getGraphDataSelector() : null;
      String yearCol = "YearOfWeek(Date)";
      assertTrue(data.indexOfHeader(yearCol) >= 0, "the period column is the full-week year");

      SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
      Map<String, Integer> counts = new TreeMap<>();

      for(int r = 0; r < data.getRowCount(); r++) {
         if(selector == null || selector.accept(data, r, null)) {
            counts.merge(fmt.format((Date) data.getData(yearCol, r)), 1, Integer::sum);
         }
      }

      return counts;
   }

   private static VGraphPair chart(int option, boolean toDate) throws Exception {
      Worksheet ws = new Worksheet();
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Date", "Paid" });
      Calendar cal = new GregorianCalendar();
      cal.clear();
      cal.set(2016, Calendar.JANUARY, 1);

      while(cal.get(Calendar.YEAR) < 2022) {
         rows.add(new Object[] { new java.sql.Date(cal.getTimeInMillis()), 1 });
         cal.add(Calendar.DATE, 1);
      }

      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "all date");
      table.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.DATE, XSchema.INTEGER },
                                               rows.toArray(new Object[0][])));
      ws.addAssembly(table);

      Viewsheet vs = new Viewsheet();
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);

      ChartVSAssembly chart = new ChartVSAssembly(vs, CHART);
      chart.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "all date"));
      VSChartInfo info = chart.getVSChartInfo();
      ColumnRef date = new ColumnRef(new AttributeRef(null, "Date"));
      date.setDataType(XSchema.DATE);
      VSChartDimensionRef dim = new VSChartDimensionRef(date);
      dim.setGroupColumnValue("Date");
      dim.setDateLevelValue(XConstants.YEAR_DATE_GROUP + "");
      dim.setTimeSeries(true);
      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("Paid");
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      info.setChartType(GraphTypes.CHART_BAR);
      info.setRTChartType(GraphTypes.CHART_BAR);

      StandardPeriods periods = new StandardPeriods();
      periods.setPreCount(2);
      periods.setDateLevel(XConstants.YEAR_DATE_GROUP);
      periods.setToDate(toDate);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue("2021-01-02");
      periods.setInclusive(false);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevel(DateComparisonInfo.WEEK_TO_DATE);
      interval.setGranularity(DateComparisonInfo.WEEK);
      interval.setContextLevel(XConstants.WEEK_DATE_GROUP);
      interval.setEndDayAsToDate(true);
      interval.setInclusive(true);

      DateComparisonInfo dc = new DateComparisonInfo();
      dc.setDateComparisonPeriods(periods);
      dc.setDateComparisonInterval(interval);
      dc.setComparisonOption(option);
      ChartVSAssemblyInfo chartInfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      chartInfo.setDateComparisonEnabled(true);
      chartInfo.setDateComparisonInfo(dc);
      vs.addAssembly(chart);

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "test/DateComparisonWeekAlignedChartTest", null,
         OrganizationManager.getInstance().getCurrentOrgID());
      ViewsheetSandbox box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null,
                                                  false, entry);
      Field wbox = ViewsheetSandbox.class.getDeclaredField("wbox");
      wbox.setAccessible(true);
      wbox.set(box, new AssetQuerySandbox(ws));

      return box.getVGraphPair(CHART);
   }

   private static final String CHART = "Chart1";
   private Principal oldPrincipal;
}
