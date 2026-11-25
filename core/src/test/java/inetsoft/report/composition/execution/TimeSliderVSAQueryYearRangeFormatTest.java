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

package inetsoft.report.composition.execution;

import inetsoft.mv.*;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.text.Format;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82320: with an MV, a range slider's table is a two-row embedded table built from the
 * MV date column's min/max and meta. A "yyyy" meta was always dropped there (#53446), so a
 * slider bound to a year level date range column showed "2002 Jan" with an MV and "2002"
 * without one. The yyyy is kept only when the bound column resolves, through the V_ mirror
 * and the worksheet tables below it, to a year level date range column.
 *
 * The slider table is built the way the server does: the selection table S_ is a copy of the
 * bound worksheet table, the real {@code getTableAssembly} gets its V_ mirror through
 * {@code VSAQuery.getVSTableAssembly}, and the bound copy is made with
 * {@code ViewsheetSandbox.copyBoundTable}. Only the RuntimeMV and the MV definition are stubbed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
               TimeSliderVSAQueryYearRangeFormatTest.MVManagerConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TimeSliderVSAQueryYearRangeFormatTest {
   private Worksheet ws;
   private Viewsheet vs;
   private TimeSliderVSQueryHolder last;

   @BeforeEach
   void setUp() {
      ws = new Worksheet();

      EmbeddedTableAssembly orders = new EmbeddedTableAssembly(ws, ORDERS);
      orders.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.DATE },
         new Object[][] { { "ORDER_DATE" }, { date(2002, 2, 5) }, { date(2003, 6, 1) },
                          { date(2005, 0, 20) } }));
      ColumnSelection columns = orders.getColumnSelection(false);
      columns.addAttribute(dateRangeColumn("range", DateRangeRef.YEAR_INTERVAL, null));
      columns.addAttribute(dateRangeColumn("yrange", DateRangeRef.YEAR_INTERVAL, "YearAlias"));
      columns.addAttribute(dateRangeColumn("mrange", DateRangeRef.MONTH_INTERVAL, null));
      orders.resetColumnSelection();
      ws.addAssembly(orders);

      vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
   }

   /** The reported case: a slider bound to a year level date range column keeps yyyy. */
   @Test
   void yearDateRangeColumnKeepsYyyyMeta() throws Exception {
      assertYyyyKept(ORDERS, "range");
   }

   /** A plain date column: the yyyy meta is still dropped, as before. */
   @Test
   void plainDateColumnDropsYyyyMeta() throws Exception {
      assertYyyyDropped(ORDERS, "ORDER_DATE");
   }

   /** A month level date range column never had yyyy as its own format: still dropped. */
   @Test
   void monthDateRangeColumnDropsYyyyMeta() throws Exception {
      assertYyyyDropped(ORDERS, "mrange");
   }

   /** A worksheet mirror over the table with the year range column: found one table down. */
   @Test
   void mirrorOverYearDateRangeColumnKeepsYyyyMeta() throws Exception {
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "Q", null, false,
         (TableAssembly) ws.getAssembly(ORDERS));
      ws.addAssembly(mirror);

      assertYyyyKept("Q", "range");
   }

   /** A date VS calc field (#53446): no date range on the chain, still dropped. */
   @Test
   void dateCalcFieldDropsYyyyMeta() throws Exception {
      ExpressionRef exp = new ExpressionRef(null, "calcDate");
      exp.setExpression("field['ORDER_DATE']");
      exp.setDataType(XSchema.DATE);
      CalculateRef calc = new CalculateRef(true);
      calc.setDataRef(exp);
      calc.setDataType(XSchema.DATE);
      vs.addCalcField(ORDERS, calc);

      assertYyyyDropped(ORDERS, "calcDate");
   }

   /** A year level date range column with an alias: the mirror attribute is the alias. */
   @Test
   void aliasedYearDateRangeColumnKeepsYyyyMeta() throws Exception {
      assertYyyyKept(ORDERS, "YearAlias");
   }

   /**
    * A worksheet table grouped by year, built as the aggregate dialog does: the grouped
    * column is replaced by a ColumnRef(DateRangeRef YEAR) and the group is on it.
    */
   @Test
   void yearGroupedAggregateTableKeepsYyyyMeta() throws Exception {
      MirrorTableAssembly grouped = new MirrorTableAssembly(ws, "G", null, false,
         (TableAssembly) ws.getAssembly(ORDERS));
      ColumnSelection columns = grouped.getColumnSelection(false);
      ColumnRef dateCol = (ColumnRef) columns.getAttribute("ORDER_DATE");
      int idx = columns.indexOfAttribute(dateCol);
      DateRangeRef dateRef = new DateRangeRef(
         DateRangeRef.getName(dateCol.getAttribute(), DateRangeRef.YEAR_INTERVAL),
         dateCol.getDataRef(), DateRangeRef.YEAR_INTERVAL);
      dateRef.setOriginalType(XSchema.DATE);
      ColumnRef groupCol = new ColumnRef(dateRef);
      groupCol.setDataType(XSchema.DATE);
      columns.setAttribute(idx, groupCol);
      grouped.setColumnSelection(columns, false);

      AggregateInfo ainfo = new AggregateInfo();
      GroupRef group = new GroupRef(groupCol);
      group.setDateGroup(DateRangeRef.YEAR_INTERVAL);
      ainfo.addGroup(group);
      grouped.setAggregateInfo(ainfo);
      ws.addAssembly(grouped);

      assertYyyyKept("G", dateRef.getAttribute());
   }

   /** A meta other than yyyy was never dropped and still is not. */
   @Test
   void nonYyyyMetaKeptOnPlainDateColumn() throws Exception {
      XEmbeddedTable data = runSlider(ORDERS, "ORDER_DATE", meta("MMM yyyy"));
      assertNotNull(data.getXMetaInfo("max"));
      assertEquals("MMM yyyy", data.getXMetaInfo("max").getXFormatInfo().getFormatSpec());
   }

   /**
    * The reported symptom: with the MV, the labels of a month range slider bound to a year
    * level date range column read "2002" ... "2005" (as without the MV), not "2002 Jan".
    */
   @Test
   void yearDateRangeColumnLabelsAreYears() throws Exception {
      List<String> labels = sliderLabels(runSlider(ORDERS, "range", meta("yyyy")));

      assertEquals("2002", labels.get(0));
      assertEquals("2005", labels.get(labels.size() - 1));
   }

   /** A plain date column still gets the month label pattern, as before. */
   @Test
   void plainDateColumnLabelsKeepMonthPattern() throws Exception {
      List<String> labels = sliderLabels(runSlider(ORDERS, "ORDER_DATE", meta("yyyy")));
      Format month = Tool.createDateFormat(TimeSliderVSAssembly.LABEL_MONTH_PATTERN, locale());

      assertEquals(month.format(date(2002, 0, 1)), labels.get(0));
      assertEquals(month.format(date(2005, 0, 1)), labels.get(labels.size() - 1));
   }

   /**
    * Label the slider as getData does: the default format of the min/max table (built from
    * the column meta as AbstractTableLens does) becomes the slider default format, and the
    * data is {min, max + 1 month, max}.
    */
   private List<String> sliderLabels(XEmbeddedTable data) throws Exception {
      XMetaInfo info = data.getXMetaInfo("max");
      Format dfmt = info == null || info.getXFormatInfo() == null ? null :
         TableFormat.getFormat(info.getXFormatInfo().getFormat(),
                               info.getXFormatInfo().getFormatSpec(), locale());
      last.slider.setDefaultFormat(dfmt);

      Date max = (Date) data.getObject(1, 0);
      Calendar cal = new GregorianCalendar();
      cal.setTime(max);
      cal.add(Calendar.MONTH, 1);
      last.query.refreshSelectionValue(new Object[] { data.getObject(1, 1), cal.getTime(), max });

      SelectionList slist = last.slider.getSelectionList();
      List<String> labels = new ArrayList<>();

      for(int i = 0; i < slist.getSelectionValueCount(); i++) {
         SelectionValue value = slist.getSelectionValue(i);

         if(!(value instanceof SelectionValue.UpperExclusiveEndValue)) {
            labels.add(value.getLabel());
         }
      }

      assertFalse(labels.isEmpty(), "slider labels");
      return labels;
   }

   private static Locale locale() {
      Locale locale = Catalog.getCatalog().getLocale();
      return locale == null ? Locale.getDefault() : locale;
   }

   private void assertYyyyKept(String table, String column) throws Exception {
      XEmbeddedTable data = runSlider(table, column, meta("yyyy"));

      for(String header : new String[] { "max", "min" }) {
         XMetaInfo info = data.getXMetaInfo(header);
         assertNotNull(info, header + " meta of " + table + "." + column);
         assertEquals("yyyy", info.getXFormatInfo().getFormatSpec());
      }
   }

   private void assertYyyyDropped(String table, String column) throws Exception {
      XEmbeddedTable data = runSlider(table, column, meta("yyyy"));
      assertNull(data.getXMetaInfo("max"), "max meta of " + table + "." + column);
      assertNull(data.getXMetaInfo("min"), "min meta of " + table + "." + column);
   }

   /**
    * Run the real TimeSliderVSAQuery.getTableAssembly for a range slider (range type
    * month) bound to table.column, with an MV whose date column carries the meta.
    */
   private XEmbeddedTable runSlider(String table, String column, XMetaInfo meta)
      throws Exception
   {
      // the selection table, as Viewsheet.createSelectionTable makes it
      TableAssembly stable = (TableAssembly) ((TableAssembly) ws.getAssembly(table))
         .copyAssembly(Assembly.SELECTION + table);
      stable.setPreRuntimeConditionList(null);
      stable.setPostRuntimeConditionList(null);
      ws.addAssembly(stable);

      TimeSliderVSAssembly slider = new TimeSliderVSAssembly();
      ((TimeSliderVSAssemblyInfo) slider.getVSAssemblyInfo()).setName(SLIDER);
      slider.setTableName(table);
      SingleTimeInfo tinfo = new SingleTimeInfo();
      tinfo.setRangeTypeValue(TimeInfo.MONTH);
      ColumnRef bound = new ColumnRef(new AttributeRef(null, column));
      bound.setDataType(XSchema.DATE);
      tinfo.setDataRef(bound);
      slider.setTimeInfo(tinfo);
      vs.addAssembly(slider);

      RuntimeMV rmv = mock(RuntimeMV.class);
      when(rmv.getMV()).thenReturn(MV_NAME);

      AssetQuerySandbox wbox = mock(AssetQuerySandbox.class);
      when(wbox.getVariableTable()).thenReturn(new VariableTable());

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(vs);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getAssetQuerySandbox()).thenReturn(wbox);
      when(box.getBoundTable(any(), anyString(), anyBoolean())).thenAnswer(inv -> {
         TableAssembly vtable = inv.getArgument(0);
         TableAssembly ntable =
            ViewsheetSandbox.copyBoundTable(vtable, vtable.getName() + "_" + SLIDER);
         ws.addAssembly(ntable);
         ntable.setRuntimeMV(rmv);
         return ntable;
      });

      DateMVColumn dateCol = mock(DateMVColumn.class);
      when(dateCol.getMin()).thenReturn(date(2002, 0, 1));
      when(dateCol.getMax()).thenReturn(date(2005, 0, 1));
      when(dateCol.getXMetaInfo()).thenReturn(meta);

      MVDef def = mock(MVDef.class);
      when(def.update()).thenReturn(def);
      when(def.getColumn(startsWith("Day("), anyBoolean(), anyBoolean())).thenReturn(dateCol);

      MVManager mgr = MVManager.getManager();
      reset(mgr);
      when(mgr.get(MV_NAME)).thenReturn(def);

      TimeSliderVSAQuery query = new TimeSliderVSAQuery(box, SLIDER);
      Method getTableAssembly = TimeSliderVSAQuery.class.getDeclaredMethod("getTableAssembly");
      getTableAssembly.setAccessible(true);
      Object result = getTableAssembly.invoke(query);
      last = new TimeSliderVSQueryHolder(query, slider);

      assertInstanceOf(DataTableAssembly.class, result,
                       "MV branch must return the embedded min/max table");
      XEmbeddedTable data = (XEmbeddedTable) ((DataTableAssembly) result).getData();
      assertEquals(date(2005, 0, 1), data.getObject(1, 0));
      assertEquals(date(2002, 0, 1), data.getObject(1, 1));
      return data;
   }

   private static ColumnRef dateRangeColumn(String name, int option, String alias) {
      DateRangeRef range = new DateRangeRef(name, new AttributeRef(null, "ORDER_DATE"), option);
      range.setOriginalType(XSchema.DATE);
      ColumnRef column = new ColumnRef(range);
      column.setDataType(XSchema.DATE);

      if(alias != null) {
         column.setAlias(alias);
      }

      return column;
   }

   private static XMetaInfo meta(String spec) {
      XMetaInfo meta = new XMetaInfo();
      meta.setXFormatInfo(new XFormatInfo(TableFormat.DATE_FORMAT, spec));
      return meta;
   }

   private static Date date(int year, int month, int day) {
      return new Timestamp(new java.util.GregorianCalendar(year, month, day).getTimeInMillis());
   }

   private record TimeSliderVSQueryHolder(TimeSliderVSAQuery query, TimeSliderVSAssembly slider) {
   }

   @Configuration
   static class MVManagerConfiguration {
      @Bean
      MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   private static final String ORDERS = "ORDERS1";
   private static final String SLIDER = "RangeSlider1";
   private static final String MV_NAME = "mv1";
}
