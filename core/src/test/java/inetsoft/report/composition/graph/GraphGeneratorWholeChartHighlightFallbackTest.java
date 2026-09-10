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

import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug 76558 / VSH-003 (the same underlying defect as bug 76522 / DCG-001): a colName-less chart
 * mark highlight is written by {@code HighlightDialogService.updateHighlights} to the whole-chart
 * level ({@code ChartInfo.setHighlightGroup()}), not to any individual measure ref. Before this
 * fix, {@code GraphGenerator.getHighlightRefs(ChartRef...)} unconditionally counted every
 * axis-bound measure as "already covered" even when that measure carried no highlight of its own,
 * so {@code applyHighlight}'s whole-chart fallback (only reachable when its ref array is empty)
 * was never taken whenever any measure was bound -- the common case.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GraphGeneratorWholeChartHighlightFallbackTest {
   @Test
   void wholeChartForegroundHighlightRecolorsAMarkWhenNoMeasureCarriesItsOwnHighlight() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);

      VSChartDimensionRef region = new VSChartDimensionRef(new AttributeRef("REGION"));
      VSChartAggregateRef revenue = new VSChartAggregateRef();
      revenue.setDataRef(new AttributeRef("REVENUE"));
      revenue.setFormula(AggregateFormula.SUM);

      info.setRTXFields(new ChartRef[]{ region });
      info.setRTYFields(new ChartRef[]{ revenue });

      // the exact repro shape: the measure bound on the chart carries no highlight of its own.
      assertNull(revenue.getHighlightGroup());

      // mirrors the write side (HighlightDialogService.java, ~line 633): a colName-less,
      // non-text chart highlight lands at the whole-chart level, keyed by no particular column.
      Condition cond = new Condition();
      cond.addValue("USA East");
      cond.setOperation(XCondition.EQUAL_TO);
      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(new AttributeRef("REGION"), cond, 0));

      TextHighlight rule = new TextHighlight();
      rule.setName("TopRegionBarForeground");
      rule.setConditionGroup(conds);
      rule.setForeground(Color.RED);
      rule.setBackground(Color.BLUE);

      HighlightGroup wholeChartGroup = new HighlightGroup();
      wholeChartGroup.addHighlight("TopRegionBarForeground", rule);
      info.setHighlightGroup(wholeChartGroup);

      GraphGenerator gen = mock(GraphGenerator.class, CALLS_REAL_METHODS);
      gen.info = info;

      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "REGION", "SUM(REVENUE)" },
         { "USA East", 100.0 },
         { "USA West", 50.0 },
      });

      ColorFrame frame = gen.applyHighlight(null, data);

      assertNotNull(frame, "the whole-chart highlight fallback must now be reachable and " +
                    "produce a color frame -- before the fix this was null and the bar never " +
                    "recolored");
      assertEquals(Color.RED, frame.getColor(data, "SUM(REVENUE)", 0),
                   "foreground must recolor the matching row's mark");
      assertNotEquals(Color.BLUE, frame.getColor(data, "SUM(REVENUE)", 0),
                       "background must never affect a chart mark's color -- HLColorFrame.getColor" +
                       " only ever reads getForeground(), by design, independent of this fix");
      assertNull(frame.getColor(data, "SUM(REVENUE)", 1),
                 "USA West does not match the rule's condition and must stay uncolored");
   }
}
