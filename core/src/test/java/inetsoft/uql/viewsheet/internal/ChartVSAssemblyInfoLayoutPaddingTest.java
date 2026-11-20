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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * getLayoutPadding is the padding a chart is laid out, drawn, positioned and exported with. It is
 * the stored padding, plus extra top space for a marked chart whose title is hidden, so the top
 * y-axis label that rises above the first gridline does not eat the top padding.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartVSAssemblyInfoLayoutPaddingTest {
   private ChartVSAssemblyInfo newChart(VizMark mark, boolean titleVisible) {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(mark);
      info.setTitleVisible(titleVisible);
      info.setPadding(new Insets(4, 5, 6, 7));
      return info;
   }

   @Test
   void aMarkedChartWithAHiddenTitleGetsExtraTopSpace() {
      ChartVSAssemblyInfo info = newChart(VizMark.MODERN_LIGHT, false);

      Insets layout = info.getLayoutPadding();

      assertEquals(new Insets(10, 5, 6, 7), layout,
                   "only the top grows, and by the label overhang");
      assertEquals(new Insets(4, 5, 6, 7), info.getPadding(),
                   "the stored padding is what the dialog shows, so it must not change");
   }

   @Test
   void aMarkedChartWithAVisibleTitleIsUnchanged() {
      ChartVSAssemblyInfo info = newChart(VizMark.MODERN_LIGHT, true);

      assertEquals(new Insets(4, 5, 6, 7), info.getLayoutPadding(),
                   "the title absorbs the overhang");
   }

   @Test
   void anUnmarkedChartIsUnchangedEvenWithAHiddenTitle() {
      ChartVSAssemblyInfo info = newChart(null, false);

      assertEquals(new Insets(4, 5, 6, 7), info.getLayoutPadding(),
                   "classic charts keep their layout");
   }

   @Test
   void theLayoutPaddingIsACopy() {
      ChartVSAssemblyInfo info = newChart(null, true);

      Insets layout = info.getLayoutPadding();
      layout.top = 99;

      assertNotSame(info.getPadding(), layout);
      assertEquals(4, info.getPadding().top, "changing the copy must not change the stored padding");
      assertEquals(4, info.getLayoutPadding().top);
   }

   @Test
   void noStoredPaddingMeansNoLayoutPadding() {
      ChartVSAssemblyInfo info = newChart(VizMark.MODERN_LIGHT, false);
      info.setPadding(null);

      assertNull(info.getLayoutPadding());
   }
}
