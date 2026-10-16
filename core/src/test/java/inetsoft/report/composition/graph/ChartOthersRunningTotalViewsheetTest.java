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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78468, at the chart level: X = type (Top 1 by Sum(wind), group others), year, name
 * and Y = Sum(wind) as a running sum broken by type. Inside the "Others" bucket the rows
 * must come out year-major (name ascending inside a year), and each running sum must be
 * the sum of the bucket's rows up to it. Before the fix the bucket came out name-major.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class ChartOthersRunningTotalViewsheetTest {
   @Test
   void runningSumInsideOthersFollowsTheGroupOrder() throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      ChartVSAssembly chart = new ChartVSAssembly(vs, CHART);
      chart.setSourceInfo(source);
      VSChartInfo info = chart.getVSChartInfo();

      VSChartDimensionRef type = dim("type");
      type.setRankingOptionValue(XCondition.TOP_N + "");
      type.setRankingNValue("1");
      type.setRankingColValue("Sum(wind)");
      type.setGroupOthersValue("true");
      info.addXField(type);
      info.addXField(dim("year"));
      info.addXField(dim("name"));

      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      RunningTotalCalc calc = new RunningTotalCalc();
      calc.setAggregate(XConstants.SUM_FORMULA);
      calc.setBreakBy("type");
      calc.setResetLevel(RunningTotalColumn.NONE);
      agg.setCalculator(calc);
      info.addYField(agg);
      vs.addAssembly(chart);

      VGraphPair pair = box.getVGraphPair(CHART, true, null);
      DataSet data = pair.getData();
      assertNotNull(data, "chart data");

      String rcol = "Running Sum type: Sum(wind)";
      assertTrue(data.indexOfHeader(rcol) >= 0, "running sum column " + rcol);

      List<Object[]> others = new ArrayList<>();

      for(int r = 0; r < data.getRowCount(); r++) {
         if("Others".equals(data.getData("type", r))) {
            others.add(new Object[] { data.getData("year", r), data.getData("name", r),
                                      data.getData("Sum(wind)", r), data.getData(rcol, r) });
         }
      }

      assertEquals(69, others.size(), "Others rows");
      double total = 0;

      for(int i = 0; i < others.size(); i++) {
         Object[] row = others.get(i);
         String where = "Others row " + i + " " + Tool.arrayToString(row);

         if(i > 0) {
            Object[] prev = others.get(i - 1);
            int byYear = Tool.compare(prev[0], row[0]);
            assertTrue(byYear < 0 || byYear == 0 && Tool.compare(prev[1], row[1]) < 0,
                       "year-major, then name: " + where);
         }

         total += ((Number) row[2]).doubleValue();
         assertEquals(total, ((Number) row[3]).doubleValue(), 1e-6, "running sum: " + where);
      }
   }

   private static VSChartDimensionRef dim(String col) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, col)));
      dim.setGroupColumnValue(col);
      dim.setOrder(XConstants.SORT_ASC);
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
   private static final String CHART = "ChartOthers";
}
