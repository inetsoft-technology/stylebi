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
package inetsoft.report.io.viewsheet;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ContainerChildTopTest {
   private static final String[] CHILDREN = { "L1", "S1", "L2", "S2" };

   @Test
   void exportDrawsAMarkedContainersChildrenWhereTheBrowserStacksThem() {
      CurrentSelectionVSAssembly container = container(VizMark.MODERN_LIGHT);
      assertArrayEquals(new float[] { 128, 158, 228, 458 }, exportTops(container));
   }

   @Test
   void exportDrawsAnUnmarkedContainersChildrenAsBefore() {
      CurrentSelectionVSAssembly container = container(null);
      assertArrayEquals(new float[] { 98, 118, 178, 318 }, exportTops(container));
   }

   @Test
   void drawnHeightsAreUnchanged() {
      CurrentSelectionVSAssembly container = container(VizMark.MODERN_LIGHT);
      int[] heights = new int[CHILDREN.length];

      for(int i = 0; i < CHILDREN.length; i++) {
         heights[i] = CoordinateHelper.getAssemblySize(child(container, CHILDREN[i]), null).height;
      }

      assertArrayEquals(new int[] { 30, 70, 230, 30 }, heights);
   }

   // the legacy stacking ignores a slider's lane; out of scope, and its exports still read it
   @Test
   void anUnmarkedContainerKeepsItsLegacyStacking() {
      CurrentSelectionVSAssembly container = container(null);
      container.layout();
      assertArrayEquals(new int[] { 98, 118, 158, 298 }, storedTops(container));
   }

   @Test
   void theContainerOwnsTheRule() {
      CurrentSelectionVSAssembly container = container(VizMark.MODERN_LIGHT);
      float[] tops = new float[CHILDREN.length];

      for(int i = 0; i < CHILDREN.length; i++) {
         tops[i] = container.getChildTop(child(container, CHILDREN[i]), 38);
      }

      assertArrayEquals(new float[] { 128, 158, 228, 458 }, tops);
   }

   @Test
   void aMarkedContainerStoresTheStackItDraws() {
      CurrentSelectionVSAssembly container = container(VizMark.MODERN_LIGHT);
      container.layout();
      assertArrayEquals(new int[] { 128, 158, 228, 458 }, storedTops(container));
   }

   @Test
   void aMissingChildIsSkipped() {
      CurrentSelectionVSAssembly container = container(VizMark.MODERN_LIGHT);
      container.setAssemblies(new String[] { "L1", "Gone", "S1", "L2", "S2" });

      container.layout();

      assertArrayEquals(new int[] { 128, 158, 228, 458 }, storedTops(container));
   }

   private static float[] exportTops(CurrentSelectionVSAssembly container) {
      float[] tops = new float[CHILDREN.length];

      for(int i = 0; i < CHILDREN.length; i++) {
         tops[i] = CoordinateHelper.getContainerChildTop(
            container, child(container, CHILDREN[i]), 38, null);
      }

      return tops;
   }

   private static int[] storedTops(CurrentSelectionVSAssembly container) {
      int[] tops = new int[CHILDREN.length];

      for(int i = 0; i < CHILDREN.length; i++) {
         tops[i] = child(container, CHILDREN[i]).getPixelOffset().y;
      }

      return tops;
   }

   private static VSAssembly child(CurrentSelectionVSAssembly container, String name) {
      return (VSAssembly) container.getViewsheet().getAssembly(name);
   }

   // every mark is pinned, because construction takes the org gate's
   private static CurrentSelectionVSAssembly container(VizMark mark) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("comfortable");
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      vs.addAssembly(container);
      container.getVSAssemblyInfo().setVizMark(mark);
      container.getVSAssemblyInfo().setPixelOffset(new Point(418, 38));
      container.getVSAssemblyInfo().setPixelSize(new Dimension(300, 600));
      int lane = mark != null ? 30 : 20;

      list(vs, "L1", mark, SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE, lane);
      slider(vs, "S1", mark, false, 40);
      list(vs, "L2", mark, SelectionVSAssemblyInfo.LIST_SHOW_TYPE, mark != null ? 230 : 140);
      slider(vs, "S2", mark, true, lane);
      list(vs, "Outside1", null, SelectionVSAssemblyInfo.LIST_SHOW_TYPE, 120);
      list(vs, "Outside2", null, SelectionVSAssemblyInfo.LIST_SHOW_TYPE, 120);

      container.setAssemblies(CHILDREN);
      container.setShowCurrentSelectionValue(true);
      container.updateOutSelection();
      assertEquals(2, container.getOutSelectionTitles().length, "the two lists outside");
      return container;
   }

   private static void list(Viewsheet vs, String name, VizMark mark, int showType, int height) {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);
      vs.addAssembly(list);
      list.getSelectionListInfo().setVizMark(mark);
      list.getSelectionListInfo().setShowTypeValue(showType);
      list.getSelectionListInfo().setPixelOffset(new Point(0, 0));
      list.getSelectionListInfo().setPixelSize(new Dimension(300, height));
   }

   private static void slider(Viewsheet vs, String name, VizMark mark, boolean hidden, int height) {
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, name);
      vs.addAssembly(slider);
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) slider.getVSAssemblyInfo();
      info.setVizMark(mark);
      info.setHidden(hidden);
      info.setPixelOffset(new Point(0, 0));
      info.setPixelSize(new Dimension(300, height));
   }
}
