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
package inetsoft.web.composer.vs.dialog;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.ChartInfo.TooltipStyle;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The tooltip radios have no AUTO option, so the dialog preselects what AUTO resolves to. Saving
 * that preselection unchanged must keep AUTO, or the chart is pinned to the look its mark gives
 * today and stops following a later Modernize or Revert.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartPropertyDialogTooltipStyleTest {
   private static VSChartInfo autoChart(boolean modern) {
      VSChartInfo info = new VSChartInfo();
      info.setOwnerModern(modern);
      return info;
   }

   @Test
   void untouchedModernChoiceKeepsAuto() {
      VSChartInfo info = autoChart(true);

      ChartPropertyDialogService.applyTooltipStyle(info, TooltipStyle.CARD);

      assertEquals(TooltipStyle.AUTO, info.getTooltipStyleValue());
   }

   @Test
   void untouchedClassicChoiceKeepsAuto() {
      VSChartInfo info = autoChart(false);

      ChartPropertyDialogService.applyTooltipStyle(info, TooltipStyle.DEFAULT);

      assertEquals(TooltipStyle.AUTO, info.getTooltipStyleValue());
   }

   @Test
   void changedChoiceIsSaved() {
      VSChartInfo info = autoChart(true);

      ChartPropertyDialogService.applyTooltipStyle(info, TooltipStyle.DEFAULT);

      assertEquals(TooltipStyle.DEFAULT, info.getTooltipStyleValue());
   }

   @Test
   void explicitStyleIsReplacedEvenWhenItMatchesTheMark() {
      VSChartInfo info = autoChart(true);
      info.setTooltipStyleValue(TooltipStyle.DEFAULT);

      ChartPropertyDialogService.applyTooltipStyle(info, TooltipStyle.CARD);

      assertEquals(TooltipStyle.CARD, info.getTooltipStyleValue());
   }
}
