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
package inetsoft.report.script.viewsheet;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.report.script.viewsheet.ChartInfoDiff.Change;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #78759: which cached results an in-place edit of a chart's binding invalidates.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartInfoDiffTest {
   @Test
   void unchangedBindingIsNone() {
      assertEquals(Change.NONE, ChartInfoDiff.diff(info.clone(), info));
   }

   @Test
   void formulaChangeIsData() {
      VSChartInfo before = info.clone();
      ((VSAggregateRef) info.getYFields()[0]).setFormulaValue("Max");

      assertEquals(Change.DATA, ChartInfoDiff.diff(before, info));
   }

   @Test
   void removedFieldIsData() {
      VSChartInfo before = info.clone();
      info.removeYField(0);

      assertEquals(Change.DATA, ChartInfoDiff.diff(before, info));
   }

   @Test
   void visualChangeIsGraph() {
      VSChartInfo before = info.clone();
      info.setTooltipVisible(!info.isTooltipVisible());

      assertEquals(Change.GRAPH, ChartInfoDiff.diff(before, info));
   }

   private final VSChartInfo info = createInfo();

   private static VSChartInfo createInfo() {
      VSChartInfo info = new VSChartInfo();
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("NUM");
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      return info;
   }
}
