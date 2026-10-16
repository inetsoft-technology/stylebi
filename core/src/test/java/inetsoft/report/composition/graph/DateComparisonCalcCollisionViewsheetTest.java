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
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.test.*;
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
 * Bug #78446: a date comparison chart on a table registers a viewsheet-wide runtime calc
 * field WeekOfYear(D) for the week-of-year part (Year period, Week To Date, For Each = Week).
 * Its name equals the full name of a Week of Year dimension. A non-DC chart or crosstab on the
 * same table bound to D at Week of Year must not pick up that calc field and must keep its own
 * WeekOfYear(D) column: same header (no "_1"), same week values, and the chart renders.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class DateComparisonCalcCollisionViewsheetTest {
   @Test
   void weekOfYearChartIsUnaffectedByDateComparisonCalcOnSameTable() throws Exception {
      checkWeekOfYearAssemblies(false);
   }

   /**
    * Same, with a hidden column on the worksheet table, so its public column selection differs
    * from the private one (the general unused calc field cleanup skips such a table).
    */
   @Test
   void weekOfYearChartIsUnaffectedWhenWorksheetTableHasHiddenColumn() throws Exception {
      checkWeekOfYearAssemblies(true);
   }

   /**
    * A selection (e.g. an adhoc filter on a DC part dimension) bound to the DC calc field
    * keeps the calc field in its query.
    */
   @Test
   void selectionBoundToDateComparisonCalcKeepsIt() throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      String tname = source.getSource();
      addDateCalc(vs, tname);
      registerDateComparisonCalc(vs, tname);

      SelectionListVSAssembly selection = new SelectionListVSAssembly(vs, "WeekList");
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) selection.getInfo();
      info.setTableName(tname);
      ColumnRef column = new ColumnRef(new AttributeRef(null, "WeekOfYear(D)"));
      column.setDataType(XSchema.INTEGER);
      info.setDataRef(column);
      vs.addAssembly(selection);

      box.lockWrite();

      try {
         box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
      }
      finally {
         box.unlockWrite();
      }

      SelectionList list = selection.getSelectionList();
      assertNotNull(list, "WeekList selection list");
      assertTrue(list.getSelectionValueCount() > 1,
                 "WeekList values " + list.getSelectionValueCount());

      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         String value = list.getSelectionValue(i).getValue();
         assertNotNull(value);
         assertTrue(value.matches("[0-9]+"), "week value " + value);
      }

      // a non-DC chart on the same table is filtered by the selection on the DC calc field
      ChartVSAssembly chart = new ChartVSAssembly(vs, "TypeChart");
      chart.setSourceInfo((SourceInfo) source.clone());
      VSChartDimensionRef type =
         new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
      type.setGroupColumnValue("type");
      chart.getVSChartInfo().addXField(type);
      chart.getVSChartInfo().addYField(sumWind());
      vs.addAssembly(chart);
      double total = sumWind(box, "TypeChart");
      assertTrue(total > 0, "TypeChart total " + total);

      String selected = list.getSelectionValue(0).getValue();
      box.lockWrite();

      try {
         SelectionList slist = new SelectionList();
         SelectionValue value = new SelectionValue(selected, selected);
         value.setState(SelectionValue.STATE_SELECTED);
         slist.setSelectionValues(new SelectionValue[] { value });
         int hint = selection.setStateSelectionList(slist);
         box.processChange(selection.getAbsoluteName(), hint, new ChangedAssemblyList());
      }
      finally {
         box.unlockWrite();
      }

      box.resetDataMap("TypeChart");
      double filtered = assertDoesNotThrow(() -> sumWind(box, "TypeChart"), "TypeChart");
      assertTrue(filtered > 0 && filtered < total,
                 "TypeChart filtered by week " + selected + ": " + filtered + " of " + total);
   }

   private static double sumWind(ViewsheetSandbox box, String name) throws Exception {
      DataSet data = (DataSet) box.getData(name);
      assertNotNull(data, name + " data");
      int col = data.indexOfHeader("Sum(wind)");
      assertTrue(col >= 0, name + " has Sum(wind)");
      double sum = 0;

      for(int r = 0; r < data.getRowCount(); r++) {
         Object v = data.getData(col, r);
         sum += v instanceof Number ? ((Number) v).doubleValue() : 0;
      }

      return sum;
   }

   private void checkWeekOfYearAssemblies(boolean hideColumn) throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      String tname = source.getSource();

      // a date column to bind at Week of Year (the fixture has no date column)
      CalculateRef dateCalc = new CalculateRef(true);
      dateCalc.setName("D");
      ExpressionRef dateExpr = new ExpressionRef(null, "D");
      dateExpr.setExpression("new Date(2021, 0, field['day'])");
      dateExpr.setDataType(XSchema.DATE);
      dateCalc.setDataRef(dateExpr);
      dateCalc.setSQL(false);
      vs.addCalcField(tname, dateCalc);

      if(hideColumn) {
         hideWorksheetColumn(box, vs, tname);
      }

      // baseline, before any date comparison calc exists on the table
      Columns before = weekOfYearChart(box, vs, source, "ChartBefore");
      Columns xbefore = weekOfYearCrosstab(box, vs, source, "CrosstabBefore");
      assertTrue(before.headers.contains("WeekOfYear(D)"), "headers " + before.headers);
      assertFalse(before.weeks.isEmpty());

      for(Object week : before.weeks) {
         int value = ((Number) week).intValue();
         assertTrue(value >= 1 && value <= 53, "week of year " + value);
      }

      // the reporter's DC config, run through the real ChartDcProcessor on the same table
      registerDateComparisonCalc(vs, tname);
      List<String> dcCalcs = new ArrayList<>();

      for(CalculateRef calc : vs.getCalcFields(tname)) {
         if(calc.isDcRuntime()) {
            dcCalcs.add(calc.getName());
         }
      }

      // the DC calc field has the same name as the Week of Year dimension
      assertEquals(List.of("WeekOfYear(D)"), dcCalcs);

      Columns after = weekOfYearChart(box, vs, source, "ChartAfter");
      assertEquals(before.headers, after.headers);
      assertFalse(after.headers.stream().anyMatch(h -> h.endsWith("_1")),
                  "headers " + after.headers);
      assertEquals(before.weeks, after.weeks);

      Columns xafter = weekOfYearCrosstab(box, vs, source, "CrosstabAfter");
      assertEquals(xbefore.headers, xafter.headers);
      assertEquals(xbefore.weeks, xafter.weeks);
   }

   private static Columns weekOfYearCrosstab(ViewsheetSandbox box, Viewsheet vs,
                                             SourceInfo source, String name)
      throws Exception
   {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);
      crosstab.setSourceInfo((SourceInfo) source.clone());
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      VSDimensionRef dim = new VSDimensionRef();
      AttributeRef attr = new AttributeRef(null, "D");
      attr.setDataType(XSchema.DATE);
      ColumnRef column = new ColumnRef(attr);
      column.setDataType(XSchema.DATE);
      dim.setDataRef(column);
      dim.setGroupColumnValue("D");
      dim.setDataType(XSchema.DATE);
      dim.setDateLevelValue(DateRangeRef.WEEK_OF_YEAR_PART + "");
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      cinfo.setDesignRowHeaders(new DataRef[] { dim });
      cinfo.setDesignAggregates(new DataRef[] { agg });
      vs.addAssembly(crosstab);

      TableLens lens = (TableLens) box.getData(name);
      assertNotNull(lens, name + " data");
      lens.moreRows(Integer.MAX_VALUE);
      Columns columns = new Columns();

      for(int c = 0; c < lens.getColCount(); c++) {
         columns.headers.add(String.valueOf(lens.getObject(0, c)));
      }

      assertEquals("WeekOfYear(D)", columns.headers.get(0), name + " headers " + columns.headers);

      for(int r = lens.getHeaderRowCount(); lens.moreRows(r); r++) {
         columns.weeks.add(lens.getObject(r, 0));
         columns.weeks.add(lens.getObject(r, 1));
      }

      assertTrue(columns.weeks.size() > 2, name + " rows");
      return columns;
   }

   private static void addDateCalc(Viewsheet vs, String tname) {
      CalculateRef dateCalc = new CalculateRef(true);
      dateCalc.setName("D");
      ExpressionRef dateExpr = new ExpressionRef(null, "D");
      dateExpr.setExpression("new Date(2021, 0, field['day'])");
      dateExpr.setDataType(XSchema.DATE);
      dateCalc.setDataRef(dateExpr);
      dateCalc.setSQL(false);
      vs.addCalcField(tname, dateCalc);
   }

   // hide one column not used by the assemblies (e.g. a helper/ID column)
   private static void hideWorksheetColumn(ViewsheetSandbox box, Viewsheet vs, String tname) {
      for(Worksheet ws : new Worksheet[] {
         vs.getBaseWorksheet(), box.getAssetQuerySandbox().getWorksheet() })
      {
         TableAssembly table = (TableAssembly) ws.getAssembly(tname);
         ColumnSelection priv = table.getColumnSelection(false);

         for(int i = 0; i < priv.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) priv.getAttribute(i);

            if("name".equals(column.getAttribute())) {
               column.setVisible(false);
            }
         }

         table.resetColumnSelection();
         assertNotEquals(table.getColumnSelection(false), table.getColumnSelection(true),
                         "fixture: the hidden column makes public != private");
      }
   }

   private static void registerDateComparisonCalc(Viewsheet vs, String tname) {
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
      dcInfo.setComparisonOption(DateComparisonInfo.CHANGE);

      VSChartDimensionRef dateDim = dateDim(XConstants.YEAR_DATE_GROUP);
      VSChartInfo info = new VSChartInfo();
      info.setRTXFields(new ChartRef[] { dateDim });
      info.setRTYFields(new ChartRef[] { sumWind() });

      new ChartDcProcessor(info, dcInfo).process(tname, vs);
   }

   private static Columns weekOfYearChart(ViewsheetSandbox box, Viewsheet vs, SourceInfo source,
                                          String name)
      throws Exception
   {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.setSourceInfo((SourceInfo) source.clone());
      VSChartInfo info = chart.getVSChartInfo();
      info.addXField(dateDim(DateRangeRef.WEEK_OF_YEAR_PART));
      info.addYField(sumWind());
      vs.addAssembly(chart);

      VGraphPair pair = assertDoesNotThrow(() -> box.getVGraphPair(name, true, null), name);
      assertNotNull(pair.getExpandedVGraph(), name + " has a graph");

      DataSet data = (DataSet) box.getData(name);
      assertNotNull(data, name + " data");
      Columns columns = new Columns();

      for(int c = 0; c < data.getColCount(); c++) {
         columns.headers.add(data.getHeader(c));
      }

      int col = data.indexOfHeader("WeekOfYear(D)");
      assertTrue(col >= 0, name + " headers " + columns.headers);

      for(int r = 0; r < data.getRowCount(); r++) {
         columns.weeks.add(data.getData(col, r));
      }

      return columns;
   }

   private static VSChartDimensionRef dateDim(int level) {
      AttributeRef attr = new AttributeRef(null, "D");
      attr.setDataType(XSchema.DATE);
      ColumnRef column = new ColumnRef(attr);
      column.setDataType(XSchema.DATE);
      VSChartDimensionRef dim = new VSChartDimensionRef(column);
      dim.setGroupColumnValue("D");
      dim.setDataType(XSchema.DATE);
      dim.setDateLevelValue(level + "");
      return dim;
   }

   private static VSChartAggregateRef sumWind() {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      return agg;
   }

   private static final class Columns {
      final List<String> headers = new ArrayList<>();
      final List<Object> weeks = new ArrayList<>();
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
}
