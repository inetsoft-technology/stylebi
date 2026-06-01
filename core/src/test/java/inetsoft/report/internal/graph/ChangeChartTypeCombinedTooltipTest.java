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
 * A chart-type change must keep the combined-tooltip design value for any type
 * that still supports it (line/area/bar) and drop it for types that do not.
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
   void switchToUnsupportingTypeClearsCombinedTooltip() {
      assertCleared(GraphTypes.CHART_LINE_STACK, GraphTypes.CHART_PIE);
   }

   private static void assertPreserved(int oldType, int newType) {
      assertTrue(changeType(oldType, newType),
                 "combined tooltip must survive " + oldType + " -> " + newType);
   }

   private static void assertCleared(int oldType, int newType) {
      assertFalse(changeType(oldType, newType),
                  "combined tooltip must be cleared for " + oldType + " -> " + newType);
   }

   private static boolean changeType(int oldType, int newType) {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(oldType);
      info.setCombinedToolTipValue(true);

      new ChangeChartTypeProcessor(oldType, newType, null, info).process();

      return info.getCombinedToolTipValue();
   }
}
