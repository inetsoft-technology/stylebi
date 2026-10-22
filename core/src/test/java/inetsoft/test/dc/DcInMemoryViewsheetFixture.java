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
package inetsoft.test.dc;

import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.execution.*;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XConstants;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonInfo;
import inetsoft.util.ThreadContext;
import inetsoft.util.script.graal.pool.PoolConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A Date Comparison chart over an in-memory table, run through the real chart pipeline
 * ({@link ViewsheetSandbox#getVGraphPair}: the DC query conditions, ChartDcProcessor and
 * GraphGenerator), without opening an asset or reading a data file. The table is an
 * {@link EmbeddedTableAssembly} with a real date column and one row per day, so the query,
 * grouping and Change calculation are the production ones and no script calc field is needed.
 *
 * <p>A test class registers the beans with
 * {@code @ContextConfiguration(classes = { BaseTestConfiguration.class,
 * SwapperTestConfiguration.class, LibManagerTestConfiguration.class,
 * PluginsTestConfiguration.class, DcInMemoryViewsheetFixture.Config.class }, ...)}, and calls
 * {@link #setUp()} / {@link #tearDown()} from {@code @BeforeEach} / {@code @AfterEach}.</p>
 */
public final class DcInMemoryViewsheetFixture {
   public static final String CHART = "Chart1";
   public static final String TABLE = "all date";

   /** The beans the chart pipeline reaches that the base test configuration lacks. */
   @Configuration
   public static class Config {
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

   /** A pseudo-random 1..997 for a day, with no pattern between years or periods. */
   public static int dayValue(LocalDate day) {
      long x = day.toEpochDay() * 0x9E3779B97F4A7C15L;
      x = (x ^ (x >>> 32)) * 0xBF58476D1CE4E5B9L;
      x ^= x >>> 29;

      return 1 + (int) ((x >>> 1) % 997);
   }

   /** Run as the system user, with the script pool off, and restore both in tearDown(). */
   public void setUp() {
      AssetDataCache.getCache().clearCache();
      oldPool = SreeEnv.getProperty(PoolConfig.ENABLED);
      SreeEnv.setProperty(PoolConfig.ENABLED, "false");
      oldPrincipal = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false));
   }

   public void tearDown() {
      for(ViewsheetSandbox box : boxes) {
         box.dispose();
      }

      boxes.clear();
      ThreadContext.setPrincipal(oldPrincipal);

      if(oldPool == null) {
         SreeEnv.remove(PoolConfig.ENABLED);
      }
      else {
         SreeEnv.setProperty(PoolConfig.ENABLED, oldPool);
      }

      AssetDataCache.getCache().clearCache();
   }

   /** An open sheet over the in-memory table: add assemblies to {@code vs} and read them from {@code box}. */
   public record Sheet(Viewsheet vs, ViewsheetSandbox box, SourceInfo source) {
   }

   /** A {@link Sheet} that is also a runtime viewsheet, for the services that look it up by id. */
   public record RuntimeSheet(RuntimeViewsheet rvs, ViewsheetSandbox box, Viewsheet vs,
                              SourceInfo source)
   {
   }

   /**
    * A viewsheet over the in-memory table {@link #TABLE} of a Date column and an Integer
    * column.
    *
    * @param dateColumn  the name of the date column.
    * @param valueColumn the name of the integer column.
    * @param rows        the data rows, a {@code java.sql.Date} and an Integer each.
    */
   public Sheet open(String dateColumn, String valueColumn, List<Object[]> rows) throws Exception {
      Worksheet ws = worksheet(new String[] { dateColumn, valueColumn },
                               new String[] { XSchema.DATE, XSchema.INTEGER }, rows);
      Viewsheet vs = viewsheet(ws);
      ViewsheetSandbox box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null,
                                                  false, entry());
      useWorksheet(box, ws);
      boxes.add(box);

      return new Sheet(vs, box, new SourceInfo(XSourceInfo.ASSET, null, TABLE));
   }

   /**
    * A runtime viewsheet over the in-memory table {@link #TABLE} with the given columns, with
    * the id {@code "dc-in-memory"} that a mocked {@code ViewsheetService} can answer to.
    *
    * @param names the column names.
    * @param types the column types, {@code XSchema.*}.
    * @param rows  the data rows.
    */
   public RuntimeSheet openRuntime(String[] names, String[] types, List<Object[]> rows)
      throws Exception
   {
      Worksheet ws = worksheet(names, types, rows);
      Viewsheet vs = viewsheet(ws);
      AssetEntry entry = entry();
      entry.setProperty("viewer", "true");
      RuntimeViewsheet rvs = new RuntimeViewsheet(
         entry, vs, ThreadContext.getPrincipal(), mock(AssetRepository.class),
         mock(WorksheetService.class), null, true);
      rvs.setID("dc-in-memory");
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      useWorksheet(box, ws);
      boxes.add(box);

      return new RuntimeSheet(rvs, box, rvs.getViewsheet(),
                              new SourceInfo(XSourceInfo.ASSET, null, TABLE));
   }

   private static Worksheet worksheet(String[] names, String[] types, List<Object[]> rows) {
      Worksheet ws = new Worksheet();
      List<Object[]> all = new ArrayList<>();
      all.add(names);
      all.addAll(rows);

      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, TABLE);
      table.setEmbeddedData(new XEmbeddedTable(types, all.toArray(new Object[0][])));
      ws.addAssembly(table);

      return ws;
   }

   /** Viewsheet has no public setter for its worksheet, and the sandbox none for its query sandbox. */
   private static Viewsheet viewsheet(Worksheet ws) throws Exception {
      Viewsheet vs = new Viewsheet();
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);

      return vs;
   }

   private static AssetEntry entry() {
      return new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/DcInMemoryViewsheetFixture",
         null, OrganizationManager.getInstance().getCurrentOrgID());
   }

   private static void useWorksheet(ViewsheetSandbox box, Worksheet ws) throws Exception {
      Field wbox = ViewsheetSandbox.class.getDeclaredField("wbox");
      wbox.setAccessible(true);
      wbox.set(box, new AssetQuerySandbox(ws));
   }

   /**
    * A Sum(Paid) bar chart with the date on X at {@code dateLevel}
    * ({@code XConstants.*_DATE_GROUP}), over one row per day from {@code from} to
    * {@code toExclusive}, comparing as {@code dc} says.
    *
    * @param value the Paid of a day.
    */
   public VGraphPair chart(DateComparisonInfo dc, int dateLevel, LocalDate from,
                           LocalDate toExclusive, ToIntFunction<LocalDate> value)
      throws Exception
   {
      List<Object[]> rows = new ArrayList<>();

      for(LocalDate day = from; day.isBefore(toExclusive); day = day.plusDays(1)) {
         rows.add(new Object[] { java.sql.Date.valueOf(day), value.applyAsInt(day) });
      }

      Sheet sheet = open("Date", "Paid", rows);
      Viewsheet vs = sheet.vs();
      ChartVSAssembly chart = new ChartVSAssembly(vs, CHART);
      chart.setSourceInfo((SourceInfo) sheet.source().clone());
      VSChartInfo info = chart.getVSChartInfo();
      ColumnRef date = new ColumnRef(new AttributeRef(null, "Date"));
      date.setDataType(XSchema.DATE);
      VSChartDimensionRef dim = new VSChartDimensionRef(date);
      dim.setGroupColumnValue("Date");
      dim.setDateLevelValue(dateLevel + "");
      dim.setTimeSeries(true);
      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("Paid");
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      info.setChartType(GraphTypes.CHART_BAR);
      info.setRTChartType(GraphTypes.CHART_BAR);

      ChartVSAssemblyInfo chartInfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      chartInfo.setDateComparisonEnabled(true);
      chartInfo.setDateComparisonInfo(dc);
      vs.addAssembly(chart);

      return sheet.box().getVGraphPair(CHART);
   }

   /**
    * The data of a crosstab with the date on the rows at {@code dateLevel} and the Sum of the
    * value column, under a Date Comparison, with no totals. The first row is the header, then a
    * row per period and part: the period date, the part columns, the value.
    *
    * @param name the name of the assembly to add to the sheet.
    */
   public TableLens crosstab(Sheet sheet, String name, DateComparisonInfo dc, String dateColumn,
                             String valueColumn, int dateLevel)
      throws Exception
   {
      Viewsheet vs = sheet.vs();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);
      crosstab.setSourceInfo((SourceInfo) sheet.source().clone());
      ColumnRef date = new ColumnRef(new AttributeRef(null, dateColumn));
      date.setDataType(XSchema.DATE);
      VSDimensionRef dim = new VSDimensionRef();
      dim.setDataRef(date);
      dim.setGroupColumnValue(dateColumn);
      dim.setDataType(XSchema.DATE);
      dim.setDateLevelValue(dateLevel + "");
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue(valueColumn);
      agg.setFormulaValue("Sum");
      crosstab.getVSCrosstabInfo().setDesignRowHeaders(new DataRef[] { dim });
      crosstab.getVSCrosstabInfo().setDesignAggregates(new DataRef[] { agg });
      crosstab.getVSCrosstabInfo().setCalculateTotalValue(false);
      ((CrosstabVSAssemblyInfo) crosstab.getVSAssemblyInfo()).setDateComparisonInfo(dc);
      vs.addAssembly(crosstab);
      TableLens data = (TableLens) sheet.box().getData(name);
      data.moreRows(Integer.MAX_VALUE);

      return data;
   }

   /** The chart over one row per day with a Paid of 1, the date on X at year level. */
   public VGraphPair yearChart(DateComparisonInfo dc, LocalDate from, LocalDate toExclusive)
      throws Exception
   {
      return chart(dc, XConstants.YEAR_DATE_GROUP, from, toExclusive, day -> 1);
   }

   private Principal oldPrincipal;
   private String oldPool;
   private final List<ViewsheetSandbox> boxes = new ArrayList<>();
}
