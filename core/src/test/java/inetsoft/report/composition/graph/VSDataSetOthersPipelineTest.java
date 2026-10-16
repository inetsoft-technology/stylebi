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

import inetsoft.graph.*;
import inetsoft.graph.data.CalcColumn;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.visual.ElementVO;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.report.composition.graph.calc.ValueOfColumn;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.filter.SortOrder;
import inetsoft.report.filter.SummaryFilter;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78469: the rows of a real Top N + Group Others summary with Sort None/Original, plotted
 * as bars, draw every bar in its own axis slot, and Value of Previous of the first value is empty.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSDataSetOthersPipelineTest {
   private static final String[] STATES = {
      "AK", "AZ", "CA", "CO", "FL", "GA", "IL", "MA", "NJ", "NY", "OH", "PA", "TX", "WA", "WI" };

   static Stream<Arguments> rankings() {
      return Stream.of(
         Arguments.of(XConstants.SORT_NONE, XCondition.TOP_N),
         Arguments.of(XConstants.SORT_ORIGINAL, XCondition.TOP_N),
         Arguments.of(XConstants.SORT_NONE, XCondition.BOTTOM_N),
         Arguments.of(XConstants.SORT_ORIGINAL, XCondition.BOTTOM_N));
   }

   @ParameterizedTest
   @MethodSource("rankings")
   void topTenWithOthersDrawsElevenSeparateBars(int order, int ranking) {
      VSDataSet data = summarize(order, ranking, 10);
      assertEquals(11, data.getRowCount());
      assertEquals("Others", data.getData("State", 10));

      // the chart generator puts a categorical scale on each dimension, as here
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("State", data.getHeader(1)));
      graph.setScale("State", new CategoricalScale("State"));
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 800, 400);

      Map<Object, Rectangle2D> bars = new HashMap<>();
      collectBars(vgraph, data, bars);
      assertEquals(11, bars.size(), bars.keySet().toString());

      // the bars are drawn in data order, one slot each, so the next bar is one slot further
      double slot = bars.get(data.getData("State", 1)).getCenterX() -
         bars.get(data.getData("State", 0)).getCenterX();
      assertTrue(slot > bars.get(data.getData("State", 0)).getWidth(), "bars share a slot");

      for(int r = 0; r < 10; r++) {
         Rectangle2D bar = bars.get(data.getData("State", r));
         Rectangle2D next = bars.get(data.getData("State", r + 1));
         assertEquals(slot, next.getCenterX() - bar.getCenterX(), 0.5,
                      data.getData("State", r + 1) + " is not in the next slot: " + bars);
      }
   }

   @ParameterizedTest
   @MethodSource("rankings")
   void topFiveValueOfPreviousOfFirstValueIsEmpty(int order, int ranking) {
      VSDataSet data = summarize(order, ranking, 5);
      assertEquals(6, data.getRowCount());

      ValueOfColumn column = new ValueOfColumn(data.getHeader(1), "prev");
      column.setChangeType(ValueOfCalc.PREVIOUS);
      column.setDim("State");

      assertEquals(CalcColumn.INVALID, column.calculate(data, 0, false, false));

      for(int i = 1; i < data.getRowCount(); i++) {
         assertEquals(data.getData(data.getHeader(1), i - 1),
                      column.calculate(data, i, false, false), data.getData("State", i) + "");
      }
   }

   // the rows are not grouped, and the sums are not in name order
   private static VSDataSet summarize(int order, int ranking, int n) {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "State", "Num" });

      for(int pass = 0; pass < 2; pass++) {
         for(int i = 0; i < STATES.length; i++) {
            int k = (i * 7) % STATES.length;
            rows.add(new Object[] { STATES[k], ((k * 4) % STATES.length + 1) * 10 + pass });
         }
      }

      // the summary groups adjacent rows, as the query sorts them by the group column
      SummaryFilter summary = new SummaryFilter(
         new SortFilter(new DefaultTableLens(rows.toArray(new Object[0][])),
                        new int[] { 0 }, new boolean[] { true }),
         new int[] { 0 }, new int[] { 1 }, new SumFormula(), null);
      summary.setGroupOrder(0, new SortOrder(order));
      summary.setTopN(0, 0, n, ranking == XCondition.BOTTOM_N, false, true);

      VSDimensionRef ref = new VSDimensionRef();
      ref.setDataRef(new AttributeRef("State"));
      ref.setOrder(order);
      ref.setRankingOptionValue(ranking + "");
      ref.setRankingNValue(n + "");
      ref.setGroupOthersValue("true");

      return new VSDataSet(summary, new VSDataRef[] { ref });
   }

   private static void collectBars(VContainer container, VSDataSet data,
                                   Map<Object, Rectangle2D> bars)
   {
      for(int i = 0; i < container.getVisualCount(); i++) {
         Object visual = container.getVisual(i);

         if(visual instanceof ElementVO vo) {
            Object state = data.getData("State", vo.getSubRowIndex());
            bars.put(state, vo.getBounds());
         }
         else if(visual instanceof VContainer child) {
            collectBars(child, data, bars);
         }
      }
   }
}
