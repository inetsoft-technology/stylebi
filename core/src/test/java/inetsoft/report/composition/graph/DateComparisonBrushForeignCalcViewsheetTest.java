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
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
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
 * Bug #78446: a brush or data tip on the date comparison runtime calc field WeekOfYear(D) of a
 * date comparison chart must filter every other assembly on the same table on the calc field:
 * a crosstab grouped by an unrelated column must not drop the condition, and a crosstab with
 * its own plain D at Week of Year dimension must not bind the condition to that same-named
 * date range column.
 * <p>
 * Reporter's scenario: a DC chart (Year period, Previous 2 Years, End 2021-11-10, Week To
 * Date interval, granularity Day, context Week) is brushed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class DateComparisonBrushForeignCalcViewsheetTest {
   /**
    * (a) the reporter's exact scenario, (b) the silent-drop case (a target with no
    * independently-bound same-named dimension), (c) the wrong-match case (a target that
    * independently binds the same-named plain date-level dimension) - all exercised by a single
    * brush, since ViewsheetSandbox.getBrushingChart() automatically propagates a chart's brush to
    * every other assembly on the same table.
    */
   @Test
   void brushAppliesConsistentlyToBothKindsOfNeighbor() throws Exception {
      Fixture f = new Fixture();

      // unfiltered baseline: both targets aggregate the same table, so their grand totals agree
      double nameBefore = sumLastColumn(f.box, "NameCrosstab");
      double weekBefore = sumLastColumn(f.box, "WeekCrosstab");
      assertEquals(UNFILTERED_TOTAL, nameBefore, "name crosstab baseline");
      assertEquals(UNFILTERED_TOTAL, weekBefore, "week crosstab baseline");

      // brush Chart2 (DC chart) on one of its own real points - the reporter's one-click repro
      VSSelection selection = new VSSelection();
      VSPoint point = new VSPoint();
      point.addValue(new VSFieldValue(WEEK_CALC, String.valueOf(f.brushWeek)));
      selection.addPoint(point);
      f.chart2.setBrushSelection(selection);

      assertSame(f.chart2, f.box.getBrushingChart("NameCrosstab"),
                "NameCrosstab should automatically pick up Chart2's brush");
      assertSame(f.chart2, f.box.getBrushingChart("WeekCrosstab"),
                "WeekCrosstab should automatically pick up Chart2's brush");

      f.box.resetDataMap("NameCrosstab");
      f.box.resetDataMap("WeekCrosstab");

      double nameAfter = sumLastColumn(f.box, "NameCrosstab");
      double weekAfter = sumLastColumn(f.box, "WeekCrosstab");

      // (b) silent drop: the brush must have a real, non-trivial effect, not the unfiltered total
      assertNotEquals(UNFILTERED_TOTAL, nameAfter,
                      "NameCrosstab (no colliding column): brush must not be silently dropped");
      // (c) wrong match: both targets filter the exact same condition on the exact same table,
      // so - whether or not a target happens to also bind its own same-named plain dimension -
      // they must agree on the filtered grand total
      assertEquals(nameAfter, weekAfter, 0.001,
                  "NameCrosstab and WeekCrosstab must agree on the brushed total");
      assertEquals(EXPECTED_BRUSHED_TOTAL, nameAfter, 0.001, "NameCrosstab brushed total");
      assertEquals(EXPECTED_BRUSHED_TOTAL, weekAfter, 0.001, "WeekCrosstab brushed total");

      // the original collision must not reopen: WeekCrosstab's own Week Of Year header must
      // still read "WeekOfYear(D)", never a "_1"-suffixed duplicate, and its own grouped values
      // must still be valid week numbers
      TableLens weekLens = (TableLens) f.box.getData("WeekCrosstab");
      weekLens.moreRows(Integer.MAX_VALUE);
      assertEquals(WEEK_CALC, weekLens.getObject(0, 0), "WeekCrosstab header must not collide");

      for(int r = weekLens.getHeaderRowCount(); r < weekLens.getRowCount(); r++) {
         Object w = weekLens.getObject(r, 0);

         if(w instanceof Number) {
            int wi = ((Number) w).intValue();
            assertTrue(wi >= 1 && wi <= 53, "WeekCrosstab week value " + wi);
         }
      }

      // Chart2 itself (the DC-owning chart) still renders with its own calc column
      f.box.resetDataMap("Chart2");
      DataSet chart2After = (DataSet) f.box.getData("Chart2");
      assertTrue(chart2After.indexOfHeader(WEEK_CALC) >= 0,
                "Chart2 must keep its own calc header");
      assertEquals(f.chart2RowCount, chart2After.getRowCount(), "Chart2 row count unaffected");
   }

   /**
    * (d) flyover / data tip coverage: VSChartFlyoverService.applyCondition and
    * VSDataTipService.applyDataTip attach their condition with
    * vsAssembly.setTipConditionList(conds), which VSAQuery.applySharedCondition merges into
    * the target's runtime condition the same way it merges a brush condition.
    */
   @Test
   void dataTipAppliesTheSameWayAsBrush() throws Exception {
      Fixture f = new Fixture();

      double nameBefore = sumLastColumn(f.box, "NameCrosstab");
      double weekBefore = sumLastColumn(f.box, "WeekCrosstab");
      assertEquals(UNFILTERED_TOTAL, nameBefore, "name crosstab baseline");
      assertEquals(UNFILTERED_TOTAL, weekBefore, "week crosstab baseline");

      f.nameCrosstab.setTipConditionList(weekEqualsCondition(f.brushWeek));
      f.weekCrosstab.setTipConditionList(weekEqualsCondition(f.brushWeek));

      f.box.resetDataMap("NameCrosstab");
      f.box.resetDataMap("WeekCrosstab");

      double nameAfter = sumLastColumn(f.box, "NameCrosstab");
      double weekAfter = sumLastColumn(f.box, "WeekCrosstab");

      assertNotEquals(UNFILTERED_TOTAL, nameAfter,
                      "NameCrosstab (no colliding column): data tip must not be silently dropped");
      assertEquals(nameAfter, weekAfter, 0.001,
                  "NameCrosstab and WeekCrosstab must agree on the data-tip-filtered total");
      assertEquals(EXPECTED_BRUSHED_TOTAL, nameAfter, 0.001, "NameCrosstab data-tip total");
      assertEquals(EXPECTED_BRUSHED_TOTAL, weekAfter, 0.001, "WeekCrosstab data-tip total");

      TableLens weekLens = (TableLens) f.box.getData("WeekCrosstab");
      weekLens.moreRows(Integer.MAX_VALUE);
      assertEquals(WEEK_CALC, weekLens.getObject(0, 0), "WeekCrosstab header must not collide");
   }

   private static ConditionList weekEqualsCondition(int week) {
      AttributeRef attr = new AttributeRef(null, WEEK_CALC);
      attr.setDataType(XSchema.INTEGER);
      ColumnRef column = new ColumnRef(attr);
      column.setDataType(XSchema.INTEGER);
      Condition cond = new Condition(XSchema.INTEGER);
      cond.addValue(week);
      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(column, cond, 0));
      return conds;
   }

   private static double sumLastColumn(ViewsheetSandbox box, String name) throws Exception {
      TableLens lens = (TableLens) box.getData(name);
      lens.moreRows(Integer.MAX_VALUE);
      double total = 0;

      for(int r = lens.getHeaderRowCount(); r < lens.getRowCount(); r++) {
         Object v = lens.getObject(r, lens.getColCount() - 1);

         if(v instanceof Number) {
            total += ((Number) v).doubleValue();
         }
      }

      return total;
   }

   /**
    * Builds the reporter's scenario once per test: a real DC chart (Chart2) that registers the
    * WeekOfYear(D) runtime calc on the table, a crosstab grouped by an unrelated column
    * ("NameCrosstab", the silent-drop case) and a crosstab that independently binds its own
    * plain Week Of Year dimension on the same date column ("WeekCrosstab", the wrong-match
    * case). brushWeek is the real week value of one of Chart2's own rows, not an arbitrary
    * number, so the test brushes a point the chart could actually show.
    */
   private final class Fixture {
      final ViewsheetSandbox box;
      final Viewsheet vs;
      final ChartVSAssembly chart2;
      final int brushWeek;
      final int chart2RowCount;
      final CrosstabVSAssembly nameCrosstab;
      final CrosstabVSAssembly weekCrosstab;

      Fixture() throws Exception {
         box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
         vs = box.getViewsheet();
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

         chart2 = new ChartVSAssembly(vs, "Chart2");
         chart2.setSourceInfo((SourceInfo) source.clone());
         VSChartInfo info = chart2.getVSChartInfo();
         info.addXField(dateDim(new VSChartDimensionRef(), XConstants.YEAR_DATE_GROUP));
         info.addYField(sumWind(new VSChartAggregateRef()));
         ((ChartVSAssemblyInfo) chart2.getVSAssemblyInfo()).setDateComparisonInfo(reporterDc());
         vs.addAssembly(chart2);

         box.getVGraphPair("Chart2", true, null);
         assertTrue(chart2.getVSChartInfo().isAppliedDateComparison(), "Chart2 DC applied");
         DataSet chart2Data = (DataSet) box.getData("Chart2");
         int weekIdx = chart2Data.indexOfHeader(WEEK_CALC);
         assertTrue(weekIdx >= 0, "Chart2 must compute " + WEEK_CALC);
         assertTrue(chart2Data.getRowCount() > 0, "Chart2 must have rows to brush");
         brushWeek = ((Number) chart2Data.getData(weekIdx, 0)).intValue();
         chart2RowCount = chart2Data.getRowCount();

         nameCrosstab = new CrosstabVSAssembly(vs, "NameCrosstab");
         nameCrosstab.setSourceInfo((SourceInfo) source.clone());
         VSCrosstabInfo nameInfo = nameCrosstab.getVSCrosstabInfo();
         VSDimensionRef nameDim = new VSDimensionRef();
         AttributeRef nameAttr = new AttributeRef(null, "name");
         nameAttr.setDataType(XSchema.STRING);
         ColumnRef nameCol = new ColumnRef(nameAttr);
         nameCol.setDataType(XSchema.STRING);
         nameDim.setDataRef(nameCol);
         nameDim.setGroupColumnValue("name");
         nameInfo.setDesignRowHeaders(new DataRef[] { nameDim });
         nameInfo.setDesignAggregates(new DataRef[] { sumWind(new VSAggregateRef()) });
         vs.addAssembly(nameCrosstab);

         weekCrosstab = new CrosstabVSAssembly(vs, "WeekCrosstab");
         weekCrosstab.setSourceInfo((SourceInfo) source.clone());
         VSCrosstabInfo weekInfo = weekCrosstab.getVSCrosstabInfo();
         weekInfo.setDesignRowHeaders(
            new DataRef[] { dateDim(new VSDimensionRef(), DateRangeRef.WEEK_OF_YEAR_PART) });
         weekInfo.setDesignAggregates(new DataRef[] { sumWind(new VSAggregateRef()) });
         vs.addAssembly(weekCrosstab);
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
   private static final String WEEK_CALC = "WeekOfYear(D)";
   // grand total of Sum(wind) over the EmbeddedVS1 TableView1 fixture, unfiltered
   private static final double UNFILTERED_TOTAL = 653732.0;
   // deterministic for this fixture: the DC chart's end date (2021-11-10) and Week-To-Date
   // config always resolve WeekOfYear(D) to 42 for its own data, and Sum(wind) filtered to
   // just that DC-calc week across the whole table is always 33020.0
   private static final double EXPECTED_BRUSHED_TOTAL = 33020.0;
}
