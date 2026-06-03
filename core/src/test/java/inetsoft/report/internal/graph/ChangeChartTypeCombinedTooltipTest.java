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
package inetsoft.report.internal.graph;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A chart-type change must preserve the combined-tooltip design value across every
 * type switch, matching snap-tooltip. Rendering is gated by supportsCombinedTooltip(),
 * so a value retained on an unsupported type stays dormant and is restored when the
 * chart is switched back to a supporting type.
 */
@SreeHome
@Tag("core")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
class ChangeChartTypeCombinedTooltipTest {
   @Test
   void switchBetweenSupportingTypesKeepsCombinedTooltip() {
      assertPreserved(GraphTypes.CHART_LINE_STACK, GraphTypes.CHART_BAR_STACK);
      assertPreserved(GraphTypes.CHART_LINE, GraphTypes.CHART_BAR);
      assertPreserved(GraphTypes.CHART_LINE_STACK, GraphTypes.CHART_3D_BAR);
      assertPreserved(GraphTypes.CHART_LINE_STACK, GraphTypes.CHART_3D_BAR_STACK);
      assertPreserved(GraphTypes.CHART_LINE_STACK, GraphTypes.CHART_AREA_STACK);
      assertPreserved(GraphTypes.CHART_AREA_STACK, GraphTypes.CHART_BAR_STACK);
   }

   @Test
   void switchToUnsupportingTypeKeepsCombinedTooltip() {
      assertPreserved(GraphTypes.CHART_LINE_STACK, GraphTypes.CHART_PIE);
   }

   @Test
   void roundTripThroughUnsupportingTypeKeepsCombinedTooltip() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.setCombinedToolTipValue(true);

      new ChangeChartTypeProcessor(GraphTypes.CHART_BAR, GraphTypes.CHART_POINT, null, info).process();
      new ChangeChartTypeProcessor(GraphTypes.CHART_POINT, GraphTypes.CHART_LINE, null, info).process();

      assertTrue(info.getCombinedToolTipValue(),
                 "combined tooltip must survive bar -> point -> line");
   }

   private static void assertPreserved(int oldType, int newType) {
      assertTrue(changeType(oldType, newType),
                 "combined tooltip must survive " + oldType + " -> " + newType);
   }

   private static boolean changeType(int oldType, int newType) {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(oldType);
      info.setCombinedToolTipValue(true);

      new ChangeChartTypeProcessor(oldType, newType, null, info).process();

      return info.getCombinedToolTipValue();
   }
}
