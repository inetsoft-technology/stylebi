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
package inetsoft.report.io.viewsheet.html;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTML draws a selection container's children where the viewer does. With its title hidden, a
 * marked container stacks them from its top: a list is not moved up a second time, the bottom
 * child keeps its height, and a range slider moves with the lists. An unmarked container keeps
 * the lane its export has always left.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HTMLHiddenContainerTitleTest {
   // the viewer draws Category 38-68, Customer 68-218 and Product 218-248 in the 210px container
   @Test
   void aMarkedContainersListIsWrittenWhereTheViewerDrawsIt() {
      assertArrayEquals(new double[] { 68, 150 }, box(list(VizMark.MODERN_LIGHT, false, "Customer")));
   }

   @Test
   void aMarkedContainersBottomListKeepsItsHeight() {
      assertArrayEquals(new double[] { 218, 30 }, box(list(VizMark.MODERN_LIGHT, false, "Product")));
   }

   // clipped under the 20px lane, then moved up by it, as before
   @Test
   void anUnmarkedContainersListsAreMovedUpAsBefore() {
      assertArrayEquals(new double[] { 58, 150 }, box(list(null, false, "Customer")));
      assertArrayEquals(new double[] { 208, 20 }, box(list(null, false, "Product")));
   }

   @Test
   void aVisibleTitleKeepsTheListsUnderItAsBefore() {
      assertArrayEquals(new double[] { 98, 150 }, box(list(VizMark.MODERN_LIGHT, true, "Customer")));
   }

   // the viewer draws Category 58-88 and the collapsed slider 88-118
   @Test
   void aMarkedContainersSliderMovesUpWithTheLists() {
      assertArrayEquals(new double[] { 88, 30 }, box(slider(VizMark.MODERN_LIGHT, false)));
   }

   @Test
   void anUnmarkedContainersSliderStaysWhereItWas() {
      assertArrayEquals(new double[] { 98, 20 }, box(slider(null, false)));
   }

   @Test
   void aVisibleTitleKeepsTheSliderUnderItAsBefore() {
      assertArrayEquals(new double[] { 118, 30 }, box(slider(VizMark.MODERN_LIGHT, true)));
   }

   private static String list(VizMark mark, boolean titleVisible, String name) {
      CurrentSelectionVSAssembly container = container(mark, titleVisible, 38, 210);
      Viewsheet vs = container.getViewsheet();
      member(vs, "Category", mark, SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE, 150);
      member(vs, "Customer", mark, SelectionVSAssemblyInfo.LIST_SHOW_TYPE, 150);
      member(vs, "Product", mark, SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE, 150);
      container.setAssemblies(new String[] { "Category", "Customer", "Product" });
      SelectionListVSAssembly list = (SelectionListVSAssembly) vs.getAssembly(name);
      HTMLSelectionListHelper helper = new HTMLSelectionListHelper(coordinates(vs), vs, list);
      helper.setExporter(new HTMLVSExporter(new ByteArrayOutputStream()));
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      helper.write(writer, list);
      writer.flush();
      return out.toString();
   }

   private static String slider(VizMark mark, boolean titleVisible) {
      CurrentSelectionVSAssembly container = container(mark, titleVisible, 58, 240);
      Viewsheet vs = container.getViewsheet();
      member(vs, "Category", mark, SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE, 150);
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "Slider");
      vs.addAssembly(slider);
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) slider.getVSAssemblyInfo();
      info.setVizMark(mark);
      info.setHidden(true);
      info.setTitleHeightValue(20);
      info.setPixelOffset(new Point(418, 0));
      info.setPixelSize(new Dimension(300, 30));
      container.setAssemblies(new String[] { "Category", "Slider" });
      StringWriter out = new StringWriter();
      coordinates(vs).writeTimeSlider(out, slider, null, 0);
      return out.toString();
   }

   private static CurrentSelectionVSAssembly container(VizMark mark, boolean titleVisible, int top,
                                                       int height)
   {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("comfortable");
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      vs.addAssembly(container);
      CurrentSelectionVSAssemblyInfo info = (CurrentSelectionVSAssemblyInfo) container.getVSAssemblyInfo();
      info.setPixelOffset(new Point(418, top));
      info.setPixelSize(new Dimension(300, height));
      info.setTitleHeightValue(20);
      info.setTitleVisibleValue(titleVisible);
      info.setVizMark(mark);
      container.setShowCurrentSelectionValue(false);
      return container;
   }

   private static void member(Viewsheet vs, String name, VizMark mark, int showType, int height) {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);
      vs.addAssembly(list);
      SelectionListVSAssemblyInfo info = list.getSelectionListInfo();
      info.setVizMark(mark);
      info.setShowTypeValue(showType);
      info.setTitleHeightValue(20);
      info.setPixelOffset(new Point(418, 0));
      info.setPixelSize(new Dimension(300, height));
   }

   // the top and height of the first absolute box written
   private static double[] box(String html) {
      Matcher m = Pattern.compile("position:absolute;left:[0-9.]+px;top:([0-9.]+)px;" +
                                  "width:[0-9.]+px;height:([0-9.]+)px;").matcher(html);
      assertTrue(m.find(), html);
      return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
   }

   private static HTMLCoordinateHelper coordinates(Viewsheet vs) {
      HTMLCoordinateHelper helper = new HTMLCoordinateHelper();
      helper.setViewsheet(vs);
      return helper;
   }
}
