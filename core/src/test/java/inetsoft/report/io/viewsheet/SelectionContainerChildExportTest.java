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

import inetsoft.report.io.viewsheet.html.HTMLVSExporter;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * At match layout a selection container's list child is clipped at the container's bottom. A
 * marked child is measured from where the container draws it - under the container title and the
 * children above it, where the viewer stacks it too - not from its stored offset, which is an old
 * stacking the container no longer draws. An unmarked child keeps the stored-offset clip. The
 * same holds for whether the child is exported at all: a marked one is left out only when the
 * container draws it past its bottom.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionContainerChildExportTest {
   // the container ends at 38 + 240 = 278; the child is drawn at 38 + 20 + 20 = 78, so 200 fits
   @Test
   void aMarkedListChildKeepsTheHeightTheViewerGivesIt() {
      assertEquals(150, preparedHeight(VizMark.MODERN_LIGHT, 150, true));
   }

   @Test
   void aMarkedListChildIsClippedAtTheContainerFromWhereItIsDrawn() {
      assertEquals(200, preparedHeight(VizMark.MODERN_LIGHT, 250, true));
   }

   // the stored offset 208 leaves 278 - 208 = 70, as it always did
   @Test
   void anUnmarkedListChildIsClippedFromItsStoredOffsetAsBefore() {
      assertEquals(70, preparedHeight(null, 150, true));
   }

   @Test
   void expandLeavesAMarkedListChildAlone() {
      assertEquals(250, preparedHeight(VizMark.MODERN_LIGHT, 250, false));
   }

   // stored at y 288, 250 into the 240px container, but drawn at 38 + 20 + 20 = 78
   @Test
   void aMarkedListChildIsExportedWhereTheContainerDrawsIt() {
      assertTrue(exported(VizMark.MODERN_LIGHT, 288, true));
   }

   @Test
   void anUnmarkedListChildIsJudgedByItsStoredOffsetAsBefore() {
      assertFalse(exported(null, 288, true));
   }

   // under an expanded 300px list it is drawn at 38 + 20 + 300 = 358, past the container's bottom
   @Test
   void aMarkedListChildDrawnPastTheContainerIsLeftOut() {
      assertFalse(exported(VizMark.MODERN_LIGHT, 100, false));
   }

   private static int preparedHeight(VizMark mark, int childHeight, boolean match) {
      SelectionListVSAssembly customer = customer(mark, 208, childHeight, true);
      AbstractVSExporter exporter = new HTMLVSExporter(new ByteArrayOutputStream());
      exporter.setMatchLayout(match);
      exporter.prepareAssembly(customer);
      return customer.getPixelSize().height;
   }

   private static boolean exported(VizMark mark, int storedY, boolean categoryDropdown) {
      SelectionListVSAssembly customer = customer(mark, storedY, 150, categoryDropdown);
      AbstractVSExporter exporter = new HTMLVSExporter(new ByteArrayOutputStream());
      exporter.viewsheet = customer.getViewsheet();
      exporter.setMatchLayout(true);
      return exporter.needExport(customer);
   }

   // a container at (418, 38), 300 x 240 with a 20px title, holding the Category list above Customer
   private static SelectionListVSAssembly customer(VizMark mark, int storedY, int height,
                                                   boolean categoryDropdown)
   {
      Viewsheet vs = new Viewsheet();
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      vs.addAssembly(container);
      container.getVSAssemblyInfo().setPixelOffset(new Point(418, 38));
      container.getVSAssemblyInfo().setPixelSize(new Dimension(300, 240));
      container.setShowCurrentSelectionValue(false);
      ((CurrentSelectionVSAssemblyInfo) container.getVSAssemblyInfo()).setTitleHeightValue(20);
      // a new assembly takes the gate's mark, and a marked title follows the density; unmarked,
      // the container's and the dropdown's titles keep the stored 20 whatever the density is
      container.getVSAssemblyInfo().setVizMark(null);

      SelectionListVSAssembly category = child(vs, "SelectionList2", new Point(418, 58),
                                               categoryDropdown ? 150 : 300);

      if(categoryDropdown) {
         category.getSelectionListInfo().setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      }

      category.getVSAssemblyInfo().setVizMark(null);
      SelectionListVSAssembly customer = child(vs, "SelectionList3", new Point(418, storedY), height);
      customer.getVSAssemblyInfo().setVizMark(mark);
      container.setAssemblies(new String[] { "SelectionList2", "SelectionList3" });
      return customer;
   }

   private static SelectionListVSAssembly child(Viewsheet vs, String name, Point pos, int height) {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);
      vs.addAssembly(list);
      SelectionListVSAssemblyInfo info = list.getSelectionListInfo();
      info.setPixelOffset(pos);
      info.setPixelSize(new Dimension(300, height));
      info.setTitleHeightValue(20);
      return list;
   }
}
