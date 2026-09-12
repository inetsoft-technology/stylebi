/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.analytic.composition.event;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for bottom-tab child geometry in the scale-to-screen path
 * ({@code VSEventUtil.applyTabScale}).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSEventUtilTabScaleTest {
   /**
    * Bug #76022: the tab bar's height is not scaled, so its slack is handed to the
    * children. A radio button never scales vertically
    * ({@code InputVSAssemblyInfo.getSizeScale} returns y=1), so adding the slack
    * inflated its box and left the rendered content floating above the tab bar.
    */
   @Test
   void fixedHeightChildKeepsNaturalHeightWhenScalingUp() throws Exception {
      Viewsheet vs = createTabViewsheet("Gauge1");
      applyScale(vs, 1.0, 1.5);

      assertEquals(RADIO_HEIGHT, scaledSize(vs, "RadioButton1").height,
                   "fixed-height child must not absorb the tab bar slack");
      assertEquals(scaledTop(vs, "Tab1"),
                   scaledTop(vs, "RadioButton1") + RADIO_HEIGHT,
                   "radio button must end flush with the tab bar");
   }

   /**
    * Mirror case: with a shrinking ratio the slack is negative, so the box used
    * to be shorter than the content and the radios overlapped the tab bar.
    */
   @Test
   void fixedHeightChildKeepsNaturalHeightWhenScalingDown() throws Exception {
      Viewsheet vs = createTabViewsheet("Gauge1");
      applyScale(vs, 1.0, 0.5);

      assertEquals(RADIO_HEIGHT, scaledSize(vs, "RadioButton1").height,
                   "fixed-height child must not absorb the tab bar slack");
      assertEquals(scaledTop(vs, "Tab1"),
                   scaledTop(vs, "RadioButton1") + RADIO_HEIGHT,
                   "radio button must end flush with the tab bar");
   }

   /**
    * A child that does scale vertically still absorbs the slack, so the tab
    * group's total scaled extent is unchanged.
    */
   @Test
   void scalingChildStillAbsorbsTabBarSlack() throws Exception {
      Viewsheet vs = createTabViewsheet("RadioButton1");
      applyScale(vs, 1.0, 1.5);

      int expected = (int) Math.floor(GAUGE_HEIGHT * 1.5 + (TAB_HEIGHT * 1.5 - TAB_HEIGHT));
      assertEquals(expected, scaledSize(vs, "Gauge1").height,
                   "vertically scaling child keeps the tab bar slack");
      assertEquals(scaledTop(vs, "Tab1"),
                   scaledTop(vs, "Gauge1") + expected,
                   "gauge must end flush with the tab bar");
   }

   private void applyScale(Viewsheet vs, double rx, double ry) throws Exception {
      ViewsheetSandbox box = Mockito.mock(ViewsheetSandbox.class);
      VSEventUtil.applyScale(vs, new Point2D.Double(rx, ry), true, null, 375, 667, box);
   }

   /**
    * Geometry of the asset from the report: a bottom-tabs container with a gauge
    * (140px, scales vertically) and a radio button (40px, fixed height), both
    * design-time flush with the 24px tab bar.
    *
    * @param hidden the unselected tab child, invisible at runtime
    */
   private Viewsheet createTabViewsheet(String hidden) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setScaleToScreen(true);

      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "Gauge1");
      gauge.setPixelOffset(new Point(160, 204));
      gauge.setPixelSize(new Dimension(140, GAUGE_HEIGHT));

      RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, "RadioButton1");
      radio.setPixelOffset(new Point(160, 304));
      radio.setPixelSize(new Dimension(200, RADIO_HEIGHT));

      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      tab.setPixelOffset(new Point(160, 344));
      tab.setPixelSize(new Dimension(200, TAB_HEIGHT));
      ((TabVSAssemblyInfo) tab.getInfo()).setBottomTabsValue(true);

      vs.addAssembly(gauge);
      vs.addAssembly(radio);
      vs.addAssembly(tab);
      tab.setAssemblies(new String[]{ "Gauge1", "RadioButton1" });
      ((VSAssembly) vs.getAssembly(hidden)).getVSAssemblyInfo().setVisible("hide");

      return vs;
   }

   private int scaledTop(Viewsheet vs, String name) {
      return ((VSAssembly) vs.getAssembly(name)).getVSAssemblyInfo()
         .getLayoutPosition(true).y;
   }

   private Dimension scaledSize(Viewsheet vs, String name) {
      return ((VSAssembly) vs.getAssembly(name)).getVSAssemblyInfo().getLayoutSize(true);
   }

   private static final int TAB_HEIGHT = 24;
   private static final int GAUGE_HEIGHT = 140;
   private static final int RADIO_HEIGHT = 40;
}
