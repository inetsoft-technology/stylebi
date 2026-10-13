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

import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
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
 * Bug #78446: date comparison charts and crosstabs whose visible dimension is itself a DC
 * runtime calc field (Year period + Year To Date + Day granularity gives DayOfYear; Quarter
 * period + Quarter To Date + Month granularity gives MonthOfQuarter) are executed end to end.
 * The internal DCPart_ marker of the calc name must not reach the axis titles/labels, the
 * tooltip caption or the crosstab header, and the DC part values must stay numeric.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class DateComparisonVisibleCalcPartViewsheetTest {
   @Test
   void dcChartAndCrosstabWithVisibleCalcPartShowNoMarker() throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      String tname = source.getSource();

      // a date column (the fixture has no date column)
      CalculateRef dateCalc = new CalculateRef(true);
      dateCalc.setName("D");
      ExpressionRef dateExpr = new ExpressionRef(null, "D");
      dateExpr.setExpression("new Date(2021, 0, field['day'])");
      dateExpr.setDataType(XSchema.DATE);
      dateCalc.setDataRef(dateExpr);
      dateCalc.setSQL(false);
      vs.addCalcField(tname, dateCalc);

      checkChart(box, vs, source, "DcDayOfYear", XConstants.YEAR_DATE_GROUP,
                 DateComparisonInfo.YEAR_TO_DATE, DateComparisonInfo.DAY,
                 DateComparisonUtil.DC_CALC_DAY_OF_YEAR);
      checkChart(box, vs, source, "DcMonthOfQuarter", XConstants.QUARTER_DATE_GROUP,
                 DateComparisonInfo.QUARTER_TO_DATE, DateComparisonInfo.MONTH,
                 DateComparisonUtil.DC_CALC_MONTH_OF_QUARTER);
      checkCrosstab(box, vs, source, "XDayOfYear", XConstants.YEAR_DATE_GROUP,
                    DateComparisonInfo.YEAR_TO_DATE, DateComparisonInfo.DAY,
                    DateComparisonUtil.DC_CALC_DAY_OF_YEAR);
      checkCrosstab(box, vs, source, "XMonthOfQuarter", XConstants.QUARTER_DATE_GROUP,
                    DateComparisonInfo.QUARTER_TO_DATE, DateComparisonInfo.MONTH,
                    DateComparisonUtil.DC_CALC_MONTH_OF_QUARTER);
      assertTrue(problems.isEmpty(), "marker shown: " + problems);
   }

   private void checkChart(ViewsheetSandbox box, Viewsheet vs, SourceInfo source,
                                  String name, int period, int interval, int granularity,
                                  String part)
      throws Exception
   {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.setSourceInfo((SourceInfo) source.clone());
      VSChartInfo info = chart.getVSChartInfo();
      info.addXField(dateDim(new VSChartDimensionRef(), period));
      info.addYField(sumWind(new VSChartAggregateRef()));
      ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo())
         .setDateComparisonInfo(dcInfo(period, interval, granularity));
      vs.addAssembly(chart);

      VGraphPair pair = assertDoesNotThrow(() -> box.getVGraphPair(name, true, null), name);
      VGraph graph = pair.getExpandedVGraph();
      assertNotNull(graph, name + " has a graph");

      String calcName = DateComparisonUtil.getDCCalcPartName(part, "D");
      VSChartInfo rinfo = chart.getVSChartInfo();
      ChartRef partRef = null;

      for(ChartRef ref : rinfo.getRTXFields()) {
         if(calcName.equals(ref.getFullName())) {
            partRef = ref;
         }
      }

      assertNotNull(partRef, name + " rt x " + Arrays.toString(rinfo.getRTXFields()));
      // axis title / tooltip caption of the visible DC part dimension
      String caption = GraphUtil.getCaption(partRef);

      if(!(part + "(D)").equals(caption)) {
         problems.add(name + " caption " + caption);
      }

      // the data set keeps the internal name, with numeric part values
      DataSet data = (DataSet) box.getData(name);
      int col = data.indexOfHeader(calcName);
      assertTrue(col >= 0, name + " headers " + headers(data));
      assertTrue(data.getRowCount() > 0, name + " rows");

      for(int r = 0; r < data.getRowCount(); r++) {
         Object v = data.getData(col, r);
         assertTrue(v == null || v instanceof Number, name + " part value " + v);
      }

      List<String> texts = new ArrayList<>();
      collect(texts, graph.getXTitle());
      collect(texts, graph.getX2Title());
      collect(texts, graph.getYTitle());
      collect(texts, graph.getY2Title());

      for(int i = 0; i < graph.getAxisCount(); i++) {
         Axis axis = graph.getAxis(i);

         for(VLabel label : axis.getLabels()) {
            collect(texts, label);
         }
      }

      assertFalse(texts.isEmpty(), name + " has visible texts");

      if(texts.stream().anyMatch(t -> t.contains(DateComparisonUtil.DC_CALC_PART_PREFIX))) {
         problems.add(name + " visible texts " + texts);
      }
   }

   private void checkCrosstab(ViewsheetSandbox box, Viewsheet vs, SourceInfo source,
                                     String name, int period, int interval, int granularity,
                                     String part)
      throws Exception
   {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);
      crosstab.setSourceInfo((SourceInfo) source.clone());
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      cinfo.setDesignRowHeaders(new DataRef[] { dateDim(new VSDimensionRef(), period) });
      cinfo.setDesignAggregates(new DataRef[] { sumWind(new VSAggregateRef()) });
      ((CrosstabVSAssemblyInfo) crosstab.getVSAssemblyInfo())
         .setDateComparisonInfo(dcInfo(period, interval, granularity));
      vs.addAssembly(crosstab);

      TableLens lens = (TableLens) box.getData(name);
      assertNotNull(lens, name + " data");
      lens.moreRows(Integer.MAX_VALUE);
      List<String> header = new ArrayList<>();

      for(int r = 0; r < lens.getHeaderRowCount(); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            header.add(String.valueOf(lens.getObject(r, c)));
         }
      }

      if(!header.contains(part + "(D)") ||
         header.stream().anyMatch(t -> t.contains(DateComparisonUtil.DC_CALC_PART_PREFIX)))
      {
         problems.add(name + " header " + header);
      }

      assertTrue(lens.getRowCount() > lens.getHeaderRowCount(), name + " rows");
   }

   private static DateComparisonInfo dcInfo(int period, int intervalLevel, int granularity) {
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
      interval.setLevel(intervalLevel);
      interval.setContextLevel(period);
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

   private static void collect(List<String> texts, VLabel label) {
      if(label != null && label.getText() != null) {
         texts.add(label.getText());
      }
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

   private final List<String> problems = new ArrayList<>();
   private static final String TABLE = "TableView1";
}
