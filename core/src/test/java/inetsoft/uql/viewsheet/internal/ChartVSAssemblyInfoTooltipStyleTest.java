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
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A chart with no explicit tooltip style takes the card tooltip when its own mark is modern and
 * the classic one when it is unmarked, whatever the org gate says.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartVSAssemblyInfoTooltipStyleTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
   }

   @Test
   void markedChartTakesCardWithGateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      assertEquals(ChartInfo.TooltipStyle.CARD, info.getVSChartInfo().getTooltipStyle());
   }

   @Test
   void unmarkedChartTakesClassicWithGateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      assertEquals(ChartInfo.TooltipStyle.DEFAULT, info.getVSChartInfo().getTooltipStyle());
   }

   @Test
   void revertedChartTakesClassic() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      assertEquals(ChartInfo.TooltipStyle.CARD, info.getVSChartInfo().getTooltipStyle());

      info.setVizMark(null);

      assertEquals(ChartInfo.TooltipStyle.DEFAULT, info.getVSChartInfo().getTooltipStyle());
   }

   @Test
   void replacedChartInfoFollowsTheMark() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      info.setVSChartInfo(new VSChartInfo());

      assertEquals(ChartInfo.TooltipStyle.CARD, info.getVSChartInfo().getTooltipStyle());
   }
}
