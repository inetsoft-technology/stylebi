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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for tab child geometry in the scale-to-screen path: {@code VSEventUtil.applyTabScale}
 * and the list-input overlap rescale in {@code VSEventUtil.handleOverlapping}.
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

   /**
    * Bug #76407: at runtime the unselected sibling isn't hidden -- tab selection is
    * resolved by Viewsheet.isVisible(assembly, mode), not assembly.isVisible() -- and it
    * shares the radio button's area. When the radio button precedes it in the viewsheet,
    * handleOverlapping used to rescale the radio button generically (vertical scale =
    * scaleRatio.x), overriding applyTabScale and overlapping the bottom tab bar.
    */
   @Test
   void unselectedSiblingDoesNotRescaleBottomTabChild() throws Exception {
      Viewsheet vs = createRuntimeTabViewsheet(true);
      applyScale(vs, 3.97, 2.16);

      assertEquals(RADIO_HEIGHT, scaledSize(vs, "RadioButton1").height,
                   "tab child must keep the height applyTabScale gave it");
      assertEquals(scaledTop(vs, "Tab1"),
                   scaledTop(vs, "RadioButton1") + RADIO_HEIGHT,
                   "radio button must end flush with the tab bar");
   }

   /**
    * Top-tabs counterpart of {@link #unselectedSiblingDoesNotRescaleBottomTabChild}.
    */
   @Test
   void unselectedSiblingDoesNotRescaleTopTabChild() throws Exception {
      Viewsheet vs = createRuntimeTabViewsheet(false);
      applyScale(vs, 3.97, 2.16);

      assertEquals(RADIO_HEIGHT, scaledSize(vs, "RadioButton1").height,
                   "tab child must keep the height applyTabScale gave it");
      assertEquals(scaledTop(vs, "Tab1") + TAB_HEIGHT, scaledTop(vs, "RadioButton1"),
                   "radio button must start flush below the tab bar");
   }

   /**
    * Inputs in the same tab page are shown together, so a real overlap between them
    * still gets the overlap rescale (#57656); only pages of one tab are exempt.
    */
   @Test
   void overlappingListInputsInSameTabPageStillRescale() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setScaleToScreen(true);

      RadioButtonVSAssembly radioA = new RadioButtonVSAssembly(vs, "RadioButtonA");
      radioA.setPixelOffset(new Point(160, 290));
      radioA.setPixelSize(new Dimension(200, RADIO_HEIGHT));

      RadioButtonVSAssembly radioB = new RadioButtonVSAssembly(vs, "RadioButtonB");
      radioB.setPixelOffset(new Point(160, 304));
      radioB.setPixelSize(new Dimension(200, RADIO_HEIGHT));

      GroupContainerVSAssembly group = new GroupContainerVSAssembly(vs, "Group1");
      group.setPixelOffset(new Point(160, 290));
      group.setPixelSize(new Dimension(200, 54));

      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      tab.setPixelOffset(new Point(160, 344));
      tab.setPixelSize(new Dimension(200, TAB_HEIGHT));
      ((TabVSAssemblyInfo) tab.getInfo()).setBottomTabsValue(true);

      vs.addAssembly(radioA);
      vs.addAssembly(radioB);
      vs.addAssembly(group);
      vs.addAssembly(tab);
      group.setAssemblies(new String[]{ "RadioButtonA", "RadioButtonB" });
      tab.setAssemblies(new String[]{ "Group1" });
      ((TabVSAssemblyInfo) tab.getInfo()).setSelectedValue("Group1");

      applyScale(vs, 3.97, 2.16);

      // without the overlap rescale a nested input is its natural height plus the
      // tab bar slack (Bug #20141)
      int unscaled = (int) Math.floor(RADIO_HEIGHT + (TAB_HEIGHT * 2.16 - TAB_HEIGHT));
      assertTrue(scaledSize(vs, "RadioButtonA").height > unscaled,
                 "overlapping input in the same page must still be rescaled");
      assertTrue(scaledSize(vs, "RadioButtonB").height > unscaled,
                 "overlapping input in the same page must still be rescaled");
   }

   /**
    * Bug #76407: a radio button inside a group that is itself a tab child must not be
    * inflated because it overlaps the group's unselected sibling tab.
    */
   @Test
   void unselectedSiblingDoesNotRescaleListInputNestedInTabChild() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setScaleToScreen(true);

      RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, "RadioButton1");
      radio.setPixelOffset(new Point(160, 304));
      radio.setPixelSize(new Dimension(200, RADIO_HEIGHT));

      GroupContainerVSAssembly group = new GroupContainerVSAssembly(vs, "Group1");
      group.setPixelOffset(new Point(160, 304));
      group.setPixelSize(new Dimension(200, RADIO_HEIGHT));

      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "Gauge1");
      gauge.setPixelOffset(new Point(160, 204));
      gauge.setPixelSize(new Dimension(140, GAUGE_HEIGHT));

      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      tab.setPixelOffset(new Point(160, 344));
      tab.setPixelSize(new Dimension(200, TAB_HEIGHT));
      ((TabVSAssemblyInfo) tab.getInfo()).setBottomTabsValue(true);

      vs.addAssembly(radio);
      vs.addAssembly(group);
      vs.addAssembly(gauge);
      vs.addAssembly(tab);
      group.setAssemblies(new String[]{ "RadioButton1" });
      tab.setAssemblies(new String[]{ "Group1", "Gauge1" });
      ((TabVSAssemblyInfo) tab.getInfo()).setSelectedValue("Group1");

      applyScale(vs, 3.97, 2.16);

      // a child nested below a tab child still absorbs the tab bar slack (Bug #20141),
      // but must not get the overlap rescale's vertical scaleRatio.x
      int expected = (int) Math.floor(RADIO_HEIGHT + (TAB_HEIGHT * 2.16 - TAB_HEIGHT));
      assertEquals(expected, scaledSize(vs, "RadioButton1").height,
                   "nested tab child must not be rescaled for the overlap");
   }

   /**
    * Bug #76407, top tabs: an input in a group page sits flush under the tab bar. The tab
    * is its grandparent, not its direct container, and touching edges count as an overlap,
    * so it must be excluded as an ancestor or the input is rescaled over the tab bar.
    */
   @Test
   void tabAncestorDoesNotRescaleListInputNestedInTopTabPage() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setScaleToScreen(true);

      RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, "RadioButton1");
      radio.setPixelOffset(new Point(160, 344 + TAB_HEIGHT));
      radio.setPixelSize(new Dimension(200, RADIO_HEIGHT));

      GroupContainerVSAssembly group = new GroupContainerVSAssembly(vs, "Group1");
      group.setPixelOffset(new Point(160, 344 + TAB_HEIGHT));
      group.setPixelSize(new Dimension(200, RADIO_HEIGHT));

      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      tab.setPixelOffset(new Point(160, 344));
      tab.setPixelSize(new Dimension(200, TAB_HEIGHT));

      vs.addAssembly(radio);
      vs.addAssembly(group);
      vs.addAssembly(tab);
      group.setAssemblies(new String[]{ "RadioButton1" });
      tab.setAssemblies(new String[]{ "Group1" });
      ((TabVSAssemblyInfo) tab.getInfo()).setSelectedValue("Group1");

      applyScale(vs, 3.97, 2.16);

      int expected = (int) Math.floor(RADIO_HEIGHT + (TAB_HEIGHT * 2.16 - TAB_HEIGHT));
      assertEquals(expected, scaledSize(vs, "RadioButton1").height,
                   "nested input must not be rescaled against its own tab");
   }

   /**
    * Inputs in two different tabs can both be shown, so a real overlap between them
    * still gets the overlap rescale; only pages of one tab are exempt.
    */
   @Test
   void overlappingListInputsInDifferentTabsStillRescale() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setScaleToScreen(true);

      RadioButtonVSAssembly radioA = new RadioButtonVSAssembly(vs, "RadioButtonA");
      radioA.setPixelOffset(new Point(160, 304));
      radioA.setPixelSize(new Dimension(200, RADIO_HEIGHT));

      RadioButtonVSAssembly radioB = new RadioButtonVSAssembly(vs, "RadioButtonB");
      radioB.setPixelOffset(new Point(260, 304));
      radioB.setPixelSize(new Dimension(200, RADIO_HEIGHT));

      TabVSAssembly tabA = new TabVSAssembly(vs, "TabA");
      tabA.setPixelOffset(new Point(160, 344));
      tabA.setPixelSize(new Dimension(200, TAB_HEIGHT));
      ((TabVSAssemblyInfo) tabA.getInfo()).setBottomTabsValue(true);

      TabVSAssembly tabB = new TabVSAssembly(vs, "TabB");
      tabB.setPixelOffset(new Point(260, 344));
      tabB.setPixelSize(new Dimension(200, TAB_HEIGHT));
      ((TabVSAssemblyInfo) tabB.getInfo()).setBottomTabsValue(true);

      vs.addAssembly(radioA);
      vs.addAssembly(radioB);
      vs.addAssembly(tabA);
      vs.addAssembly(tabB);
      tabA.setAssemblies(new String[]{ "RadioButtonA" });
      tabB.setAssemblies(new String[]{ "RadioButtonB" });

      applyScale(vs, 3.97, 2.16);

      assertTrue(scaledSize(vs, "RadioButtonA").height > RADIO_HEIGHT,
                 "overlapping input in another tab must still be rescaled");
      assertTrue(scaledSize(vs, "RadioButtonB").height > RADIO_HEIGHT,
                 "overlapping input in another tab must still be rescaled");
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

   /**
    * Runtime shape of the reported asset: RadioButton1 is the selected tab and precedes
    * Gauge1 in the viewsheet, and the unselected Gauge1 is not explicitly hidden.
    */
   private Viewsheet createRuntimeTabViewsheet(boolean bottomTabs) {
      Viewsheet vs = new Viewsheet();
      addRuntimeTab(vs, bottomTabs);
      return vs;
   }

   private void addRuntimeTab(Viewsheet vs, boolean bottomTabs) {
      vs.getViewsheetInfo().setScaleToScreen(true);

      RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, "RadioButton1");
      radio.setPixelOffset(new Point(160, bottomTabs ? 304 : 344 + TAB_HEIGHT));
      radio.setPixelSize(new Dimension(200, RADIO_HEIGHT));

      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "Gauge1");
      gauge.setPixelOffset(new Point(160, bottomTabs ? 204 : 344 + TAB_HEIGHT));
      gauge.setPixelSize(new Dimension(140, GAUGE_HEIGHT));

      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      tab.setPixelOffset(new Point(160, 344));
      tab.setPixelSize(new Dimension(200, TAB_HEIGHT));
      ((TabVSAssemblyInfo) tab.getInfo()).setBottomTabsValue(bottomTabs);

      vs.addAssembly(radio);
      vs.addAssembly(gauge);
      vs.addAssembly(tab);
      tab.setAssemblies(new String[]{ "RadioButton1", "Gauge1" });
      ((TabVSAssemblyInfo) tab.getInfo()).setSelectedValue("RadioButton1");
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
