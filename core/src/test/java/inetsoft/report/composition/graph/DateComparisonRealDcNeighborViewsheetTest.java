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
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
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
 * Bug #78446, the reporter's scenario with a real date comparison chart on the viewsheet (no
 * injected calc field): Chart2 has DC Year / Week To Date / For Each Week / Day, which
 * registers the WeekOfYear(D) runtime calc on the table. Chart1 (no DC, D at Week of Year),
 * a non-DC Week of Year crosstab and a DC crosstab on the same table keep working; Chart2
 * itself still renders with its calc field; clearing DC on Chart2 leaves Chart1 intact.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class DateComparisonRealDcNeighborViewsheetTest {
   @Test
   void realDcChartDoesNotBreakNeighborsOnSameTable() throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      String tname = source.getSource();

      CalculateRef dateCalc = new CalculateRef(true);
      dateCalc.setName("D");
      ExpressionRef dateExpr = new ExpressionRef(null, "D");
      dateExpr.setExpression("new Date(2021, 0, field['day'])");
      dateExpr.setDataType(XSchema.DATE);
      dateCalc.setDataRef(dateExpr);
      dateCalc.setSQL(false);
      vs.addCalcField(tname, dateCalc);

      List<Object> before = weeks(box, chart(vs, source, "Chart1", null), "WeekOfYear(D)");

      // Chart2: real DC chart, the reporter's config
      ChartVSAssembly chart2 = chart(vs, source, "Chart2", reporterDc());
      VGraphPair pair2 = assertDoesNotThrow(() -> box.getVGraphPair("Chart2", true, null));
      assertNotNull(pair2.getExpandedVGraph(), "Chart2 graph");
      assertTrue(chart2.getVSChartInfo().isAppliedDateComparison(), "Chart2 DC applied");
      assertTrue(dcCalcs(vs, tname).contains("WeekOfYear(D)"), "dc calcs " + dcCalcs(vs, tname));
      DataSet data2 = (DataSet) box.getData("Chart2");
      // (c) the DC chart's own query still evaluates its DC calc
      assertTrue(headers(data2).contains("WeekOfYear(D)"), "Chart2 headers " + headers(data2));
      assertTrue(data2.getRowCount() > 0);

      // Chart1 (no DC) re-executed after Chart2 registered its calc
      box.resetDataMap("Chart1");
      List<Object> after = weeks(box, (ChartVSAssembly) vs.getAssembly("Chart1"),
                                 "WeekOfYear(D)");
      assertEquals(before, after, "Chart1 week values");

      // (b) a non-DC crosstab at Week of Year, and a DC crosstab, on the same table
      TableLens plain = crosstab(box, vs, source, "XPlain", DateRangeRef.WEEK_OF_YEAR_PART, null);
      assertEquals("WeekOfYear(D)", plain.getObject(0, 0));

      for(int r = plain.getHeaderRowCount(); r < plain.getRowCount(); r++) {
         Object v = plain.getObject(r, 0);

         if(v instanceof Number) {
            int w = ((Number) v).intValue();
            assertTrue(w >= 1 && w <= 53, "XPlain week " + w);
         }
      }

      TableLens dcx = crosstab(box, vs, source, "XDc", XConstants.YEAR_DATE_GROUP, reporterDc());
      assertTrue(dcx.getRowCount() > dcx.getHeaderRowCount(), "XDc rows");

      // (a) a DC chart that also shows a plain D at Week of Year on color
      ChartVSAssembly chartA = chart(vs, source, "ChartA", yearToDateDc());
      VSChartDimensionRef colorDim = dateDim(new VSChartDimensionRef(),
                                             DateRangeRef.WEEK_OF_YEAR_PART);
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(colorDim);
      color.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      chartA.getVSChartInfo().setColorField(color);
      // DC replaces the color binding with its period color, so ChartA renders without a
      // Week of Year column and the calc collision cannot arise through the color field
      assertDoesNotThrow(() -> box.getVGraphPair("ChartA", true, null), "ChartA");
      assertTrue(chartA.getVSChartInfo().isAppliedDateComparison(), "ChartA DC applied");

      // clearing DC on Chart2: Chart1 and Chart2 still fine
      ((ChartVSAssemblyInfo) chart2.getVSAssemblyInfo()).setDateComparisonInfo(null);
      box.resetDataMap("Chart2");
      box.resetDataMap("Chart1");
      assertDoesNotThrow(() -> box.getVGraphPair("Chart2", true, null));
      assertFalse(chart2.getVSChartInfo().isAppliedDateComparison());
      assertEquals(before, weeks(box, (ChartVSAssembly) vs.getAssembly("Chart1"),
                                 "WeekOfYear(D)"), "Chart1 after clearing DC");
   }

   private static List<String> dcCalcs(Viewsheet vs, String tname) {
      List<String> list = new ArrayList<>();

      for(CalculateRef calc : vs.getCalcFields(tname)) {
         if(calc.isDcRuntime()) {
            list.add(calc.getName());
         }
      }

      return list;
   }

   private static ChartVSAssembly chart(Viewsheet vs, SourceInfo source, String name,
                                        DateComparisonInfo dc)
   {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.setSourceInfo((SourceInfo) source.clone());
      VSChartInfo info = chart.getVSChartInfo();
      info.addXField(dateDim(new VSChartDimensionRef(), dc == null ?
         DateRangeRef.WEEK_OF_YEAR_PART : XConstants.YEAR_DATE_GROUP));
      info.addYField(sumWind(new VSChartAggregateRef()));
      ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).setDateComparisonInfo(dc);
      vs.addAssembly(chart);
      return chart;
   }

   private static List<Object> weeks(ViewsheetSandbox box, ChartVSAssembly chart, String col)
      throws Exception
   {
      String name = chart.getAbsoluteName();
      VGraphPair pair = assertDoesNotThrow(() -> box.getVGraphPair(name, true, null), name);
      assertNotNull(pair.getExpandedVGraph(), name);
      DataSet data = (DataSet) box.getData(name);
      int idx = data.indexOfHeader(col);
      assertTrue(idx >= 0, name + " headers " + headers(data));
      assertFalse(headers(data).stream().anyMatch(h -> h.endsWith("_1")), name);
      List<Object> list = new ArrayList<>();

      for(int r = 0; r < data.getRowCount(); r++) {
         Object v = data.getData(idx, r);
         int w = ((Number) v).intValue();
         assertTrue(w >= 1 && w <= 53, name + " week " + w);
         list.add(v);
      }

      assertFalse(list.isEmpty(), name);
      return list;
   }

   private static TableLens crosstab(ViewsheetSandbox box, Viewsheet vs, SourceInfo source,
                                     String name, int level, DateComparisonInfo dc)
      throws Exception
   {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);
      crosstab.setSourceInfo((SourceInfo) source.clone());
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      cinfo.setDesignRowHeaders(new DataRef[] { dateDim(new VSDimensionRef(), level) });
      cinfo.setDesignAggregates(new DataRef[] { sumWind(new VSAggregateRef()) });
      ((CrosstabVSAssemblyInfo) crosstab.getVSAssemblyInfo()).setDateComparisonInfo(dc);
      vs.addAssembly(crosstab);
      TableLens lens = (TableLens) box.getData(name);
      assertNotNull(lens, name);
      lens.moreRows(Integer.MAX_VALUE);
      return lens;
   }

   private static DateComparisonInfo reporterDc() {
      return dc(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.WEEK_TO_DATE,
                DateComparisonInfo.DAY, XConstants.WEEK_DATE_GROUP);
   }

   private static DateComparisonInfo yearToDateDc() {
      return dc(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.YEAR_TO_DATE,
                DateComparisonInfo.MONTH, XConstants.YEAR_DATE_GROUP);
   }

   private static DateComparisonInfo dc(int period, int level, int granularity, int context) {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(period);
      periods.setPreCount(2);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue("2021-11-10");
      periods.setToDate(false);
      periods.setInclusive(true);
      dcInfo.setDateComparisonPeriods(periods);
      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(granularity);
      interval.setLevel(level);
      interval.setContextLevel(context);
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

   private static List<String> headers(DataSet data) {
      List<String> list = new ArrayList<>();

      for(int c = 0; c < data.getColCount(); c++) {
         list.add(data.getHeader(c));
      }

      return list;
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
