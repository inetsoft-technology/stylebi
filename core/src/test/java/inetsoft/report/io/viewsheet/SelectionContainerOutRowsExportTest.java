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
package inetsoft.report.io.viewsheet;

import inetsoft.report.io.viewsheet.html.HTMLCoordinateHelper;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CurrentSelectionVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A container showing the current selection draws one collapsed row per selection outside it,
 * and stacks its children below them. A marked container's rows follow the density (30 at
 * comfortable), so the stacking and every format's drawing must use that height too, or the
 * children overlap the rows. An unmarked container keeps 20.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionContainerOutRowsExportTest {
   // two outside selections: the child moves down by two rows of 30
   @Test
   void aMarkedContainersChildrenStackBelowItsDensityOutRows() {
      assertEquals(60, childTop(VizMark.MODERN_LIGHT, true) - childTop(VizMark.MODERN_LIGHT, false));
   }

   @Test
   void anUnmarkedContainersChildrenStackBelowTwentyPixelRowsAsBefore() {
      assertEquals(40, childTop(null, true) - childTop(null, false));
   }

   @Test
   void htmlDrawsAMarkedContainersOutRowsAtTheDensityHeight() {
      assertEquals(List.of(30.0, 30.0), htmlOutRowHeights(VizMark.MODERN_LIGHT));
   }

   @Test
   void htmlDrawsAnUnmarkedContainersOutRowsAtTwentyAsBefore() {
      assertEquals(List.of(20.0, 20.0), htmlOutRowHeights(null));
   }

   // html also moves each child's offset to just below the rows it drew
   @Test
   void htmlPlacesAMarkedContainersChildrenBelowItsDensityOutRows() {
      assertEquals(60, htmlChildOffset(VizMark.MODERN_LIGHT, true) -
                       htmlChildOffset(VizMark.MODERN_LIGHT, false));
   }

   @Test
   void htmlPlacesAnUnmarkedContainersChildrenBelowTwentyPixelRowsAsBefore() {
      assertEquals(40, htmlChildOffset(null, true) - htmlChildOffset(null, false));
   }

   private static int htmlChildOffset(VizMark mark, boolean showCurrent) {
      CurrentSelectionVSAssembly container = container(mark, showCurrent);
      HTMLCoordinateHelper helper = new HTMLCoordinateHelper();
      helper.setViewsheet(container.getViewsheet());
      helper.writeCurrentSelection(new StringWriter(),
                                   (CurrentSelectionVSAssemblyInfo) container.getVSAssemblyInfo(),
                                   container.getViewsheet());
      return container.getViewsheet().getAssembly("SelectionList2").getPixelOffset().y;
   }

   private static float childTop(VizMark mark, boolean showCurrent) {
      CurrentSelectionVSAssembly container = container(mark, showCurrent);
      VSAssembly child = (VSAssembly) container.getViewsheet().getAssembly("SelectionList2");
      return CoordinateHelper.getContainerChildTop(container, child, 38, null);
   }

   // an out row is the div writeOutTitle opens, immediately followed by its flex box
   private static List<Double> htmlOutRowHeights(VizMark mark) {
      CurrentSelectionVSAssembly container = container(mark, true);
      HTMLCoordinateHelper helper = new HTMLCoordinateHelper();
      helper.setViewsheet(container.getViewsheet());
      StringWriter out = new StringWriter();
      helper.writeCurrentSelection(out, (CurrentSelectionVSAssemblyInfo) container.getVSAssemblyInfo(),
                                   container.getViewsheet());
      Matcher m = Pattern.compile(
         "<div style='[^']*height:([0-9.]+)px[^']*'> <div style='width:100%;height:100%;display:flex")
         .matcher(out.toString());
      List<Double> heights = new ArrayList<>();

      while(m.find()) {
         heights.add(Double.parseDouble(m.group(1)));
      }

      return heights;
   }

   private static CurrentSelectionVSAssembly container(VizMark mark, boolean showCurrent) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("comfortable");
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      vs.addAssembly(container);
      container.getVSAssemblyInfo().setPixelOffset(new Point(418, 38));
      container.getVSAssemblyInfo().setPixelSize(new Dimension(300, 240));
      container.getVSAssemblyInfo().setVizMark(mark);
      list(vs, "SelectionList2", new Point(418, 58));
      list(vs, "SelectionList1", new Point(38, 38));
      list(vs, "SelectionList5", new Point(40, 320));
      container.setAssemblies(new String[] { "SelectionList2" });
      container.setShowCurrentSelectionValue(showCurrent);
      container.updateOutSelection();

      if(showCurrent) {
         assertEquals(2, container.getOutSelectionTitles().length, "the two lists outside");
      }

      return container;
   }

   private static void list(Viewsheet vs, String name, Point pos) {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);
      vs.addAssembly(list);
      list.getSelectionListInfo().setPixelOffset(pos);
      list.getSelectionListInfo().setPixelSize(new Dimension(300, 150));
   }
}
