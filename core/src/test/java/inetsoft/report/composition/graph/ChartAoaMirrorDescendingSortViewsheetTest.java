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
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.calc.RunningTotalCalc;
import inetsoft.report.composition.graph.calc.RunningTotalColumn;
import inetsoft.test.*;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78498 (S-16): X = type, year (descending), name and Y = Sum(wind) as a running sum
 * broken by type, on the embedded (not source-mergeable) Hurricane data of EmbeddedVS1.
 * A ranking or a percentage formula makes the chart query aggregate in a mirror (AOA). The
 * mirror had no sort info, so every group defaulted to ascending: the rows came out year
 * ascending while the axis stayed descending, and the running sum ran against the axis
 * (ANA-2015 got the bucket total 616812 instead of 995).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class ChartAoaMirrorDescendingSortViewsheetTest {
   @Test
   void topNKeepsDescendingInnerLevel() throws Exception {
      DataSet data = runChart("ChartTop1", type -> {
         type.setRankingOptionValue(XCondition.TOP_N + "");
         type.setRankingNValue("1");
         type.setRankingColValue("Sum(wind)");
      }, false);

      assertTypeAxis(data, "NAMED");
      assertRunningSumFollowsDescendingYears(data);
      assertEquals(995, runningSum(data, "NAMED", 2015, "ANA-2015"), 1e-6, "ANA-2015");
   }

   @Test
   void topNWithOthersKeepsDescendingInnerLevel() throws Exception {
      DataSet data = runChart("ChartTop1Others", type -> {
         type.setRankingOptionValue(XCondition.TOP_N + "");
         type.setRankingNValue("1");
         type.setRankingColValue("Sum(wind)");
         type.setGroupOthersValue("true");
      }, false);

      assertTypeAxis(data, "NAMED", "Others");
      assertRunningSumFollowsDescendingYears(data);
      assertEquals(995, runningSum(data, "NAMED", 2015, "ANA-2015"), 1e-6, "ANA-2015");
   }

   @Test
   void bottomNWithOthersKeepsAxisAndDescendingInnerLevel() throws Exception {
      DataSet data = runChart("ChartBottom1Others", type -> {
         type.setRankingOptionValue(XCondition.BOTTOM_N + "");
         type.setRankingNValue("1");
         type.setRankingColValue("Sum(wind)");
         type.setGroupOthersValue("true");
      }, false);

      // the dataset row order may put Others first, the axis keeps it last
      assertTypeAxis(data, "UNNAMED", "Others");
      assertRunningSumFollowsDescendingYears(data);
      assertEquals(995, runningSum(data, "Others", 2015, "ANA-2015"), 1e-6, "ANA-2015");
   }

   @Test
   void percentageKeepsDescendingInnerLevel() throws Exception {
      DataSet data = runChart("ChartPercent", type -> {}, true);

      assertRunningSumFollowsDescendingYears(data);
   }

   private DataSet runChart(String name, java.util.function.Consumer<VSChartDimensionRef> rank,
                            boolean percent)
      throws Exception
   {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.setSourceInfo(source);
      VSChartInfo info = chart.getVSChartInfo();

      VSChartDimensionRef type = dim("type", XConstants.SORT_ASC);
      rank.accept(type);
      info.addXField(type);
      info.addXField(dim("year", XConstants.SORT_DESC));
      info.addXField(dim("name", XConstants.SORT_ASC));

      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");

      if(percent) {
         agg.setPercentageOptionValue(XConstants.PERCENTAGE_OF_GRANDTOTAL + "");
      }

      RunningTotalCalc calc = new RunningTotalCalc();
      calc.setAggregate(XConstants.SUM_FORMULA);
      calc.setBreakBy("type");
      calc.setResetLevel(RunningTotalColumn.NONE);
      agg.setCalculator(calc);
      info.addYField(agg);
      vs.addAssembly(chart);

      VGraphPair pair = box.getVGraphPair(name, true, null);
      DataSet data = pair.getData();
      assertNotNull(data, "chart data");
      assertTrue(data.indexOfHeader(RCOL) >= 0, "running sum column " + RCOL);
      Comparator<Object> years = (Comparator<Object>) data.getComparator("year");
      assertTrue(years.compare(1986, 1987) > 0, "year axis descending");
      return data;
   }

   /**
    * Each type's running sum must accumulate along the axis order: year descending, then
    * name ascending.
    */
   private static void assertRunningSumFollowsDescendingYears(DataSet data) {
      Map<Object, List<Object[]>> byType = new LinkedHashMap<>();

      for(int r = 0; r < data.getRowCount(); r++) {
         byType.computeIfAbsent(data.getData("type", r), k -> new ArrayList<>())
            .add(new Object[] { data.getData("year", r), data.getData("name", r),
                                data.getData("Sum(wind)", r), data.getData(RCOL, r) });
      }

      assertFalse(byType.isEmpty(), "chart rows");

      for(Map.Entry<Object, List<Object[]>> e : byType.entrySet()) {
         List<Object[]> rows = e.getValue();
         rows.sort((a, b) -> {
            int c = -Tool.compare(a[0], b[0]);
            return c != 0 ? c : Tool.compare(a[1], b[1]);
         });

         double total = 0;

         for(Object[] row : rows) {
            total += ((Number) row[2]).doubleValue();
            assertEquals(total, ((Number) row[3]).doubleValue(), Math.abs(total) * 1e-9 + 1e-9,
                         "running sum of " + e.getKey() + " " + Tool.arrayToString(row));
         }
      }
   }

   private static double runningSum(DataSet data, Object type, Object year, Object name) {
      for(int r = 0; r < data.getRowCount(); r++) {
         if(Tool.equals(type, data.getData("type", r)) &&
            Tool.equals(year, data.getData("year", r)) &&
            Tool.equals(name, data.getData("name", r)))
         {
            return ((Number) data.getData(RCOL, r)).doubleValue();
         }
      }

      fail("no row " + type + "/" + year + "/" + name);
      return 0;
   }

   /**
    * The type axis order is the order of the dataset comparator, not the row order.
    */
   private static void assertTypeAxis(DataSet data, Object... expected) {
      Set<Object> types = new LinkedHashSet<>();

      for(int r = 0; r < data.getRowCount(); r++) {
         types.add(data.getData("type", r));
      }

      List<Object> axis = new ArrayList<>(types);
      axis.sort((Comparator<Object>) data.getComparator("type"));
      assertEquals(Arrays.asList(expected), axis, "type axis");
   }

   private static VSChartDimensionRef dim(String col, int order) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, col)));
      dim.setGroupColumnValue(col);
      dim.setOrder(order);
      return dim;
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
   private static final String RCOL = "Running Sum type: Sum(wind)";
}
