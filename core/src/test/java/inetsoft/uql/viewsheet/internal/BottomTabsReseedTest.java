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
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.TabVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Point;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Modernize, Revert and the dashboard's mode switch re-seed a selection list's size and title lane,
 * so a child of a bottom-tabs container has to be put back on its strip afterwards.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BottomTabsReseedTest {
   private static final int STRIP = 722;

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
   }

   private static void gate(boolean on) {
      SreeEnv.setProperty("viewsheet.modernVisualization", Boolean.toString(on));
   }

   // a legacy sheet at a pinned tier, so the compact sizes below do not depend on the org's
   private static Viewsheet legacySheet() {
      gate(false);
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("compact");
      return vs;
   }

   private static SelectionListVSAssemblyInfo legacyList(Viewsheet vs, String name) {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);
      vs.addAssembly(list);
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) list.getVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(null);
      info.setPixelSize(new Dimension(100, 120));
      return info;
   }

   private static TabVSAssemblyInfo tabs(Viewsheet vs, boolean bottom, String... children) {
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      tabInfo.setPixelOffset(new Point(78, STRIP));
      tabInfo.setPixelSize(new Dimension(400, 24));
      tabInfo.setBottomTabsValue(bottom);
      vs.addAssembly(tab);
      tab.setAssemblies(children);
      return tabInfo;
   }

   @Test
   void modernizeKeepsABottomTabsListOnTheStrip() {
      Viewsheet vs = legacySheet();
      SelectionListVSAssemblyInfo list = legacyList(vs, "SelA");
      list.setPixelOffset(new Point(78, STRIP - 120));
      tabs(vs, true, "SelA");

      gate(true);
      VizModernizeUtil.modernize(vs);

      assertEquals(new Dimension(124, 170), list.getPixelSize(), "premise: the size rule ran");
      assertEquals(new Point(78, STRIP - 170), list.getPixelOffset());
   }

   @Test
   void revertKeepsABottomTabsListOnTheStrip() {
      Viewsheet vs = legacySheet();
      SelectionListVSAssemblyInfo list = legacyList(vs, "SelA");
      tabs(vs, true, "SelA");
      gate(true);
      VizModernizeUtil.modernize(vs);
      list.setPixelOffset(new Point(78, STRIP - 170));

      VizModernizeUtil.revert(vs);

      assertEquals(new Dimension(100, 120), list.getPixelSize(), "premise: the size rule ran");
      assertEquals(new Point(78, STRIP - 120), list.getPixelOffset());
   }

   // the per-dashboard switch in the viewsheet property dialog
   @Test
   void aDashboardModeSwitchKeepsABottomTabsListOnTheStrip() {
      Viewsheet vs = legacySheet();
      SelectionListVSAssemblyInfo list = legacyList(vs, "SelA");
      list.setPixelOffset(new Point(78, STRIP - 120));
      tabs(vs, true, "SelA");

      VizModernizeUtil.applyMark(vs, VizMark.MODERN_LIGHT);

      assertEquals(new Dimension(124, 170), list.getPixelSize(), "premise: the size rule ran");
      assertEquals(new Point(78, STRIP - 170), list.getPixelOffset());
   }

   // a dropdown stands on the strip by its title lane, which Modernize moves while its box stays
   @Test
   void modernizeKeepsABottomTabsDropdownOnTheStrip() {
      Viewsheet vs = legacySheet();
      SelectionListVSAssemblyInfo list = legacyList(vs, "SelA");
      list.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      list.setPixelSize(new Dimension(100, 20));
      int legacyLane = list.getTitleHeight();
      list.setPixelOffset(new Point(78, STRIP - legacyLane));
      tabs(vs, true, "SelA");

      gate(true);
      VizModernizeUtil.modernize(vs);

      assertNotEquals(legacyLane, list.getTitleHeight(), "premise: the lane moved");
      assertEquals(new Point(78, STRIP - list.getTitleHeight()), list.getPixelOffset());
   }

   // top tabs hang their children below the strip, so a resize there needs no move
   @Test
   void modernizeLeavesATopTabsChildWhereItIs() {
      Viewsheet vs = legacySheet();
      SelectionListVSAssemblyInfo list = legacyList(vs, "SelA");
      list.setPixelOffset(new Point(78, STRIP + 24));
      tabs(vs, false, "SelA");

      gate(true);
      VizModernizeUtil.modernize(vs);

      assertEquals(new Dimension(124, 170), list.getPixelSize(), "premise: the size rule ran");
      assertEquals(new Point(78, STRIP + 24), list.getPixelOffset());
   }

   // nothing to modernize means nothing moves, even a child an author left off the strip
   @Test
   void aModernizeWithNothingToDoMovesNothing() {
      Viewsheet vs = legacySheet();
      SelectionListVSAssemblyInfo list = legacyList(vs, "SelA");
      tabs(vs, true, "SelA");
      gate(true);
      VizModernizeUtil.modernize(vs);
      list.setPixelOffset(new Point(78, 300));

      assertEquals(0, VizModernizeUtil.modernize(vs), "premise: nothing was unmarked");
      assertEquals(new Point(78, 300), list.getPixelOffset());
   }
}
