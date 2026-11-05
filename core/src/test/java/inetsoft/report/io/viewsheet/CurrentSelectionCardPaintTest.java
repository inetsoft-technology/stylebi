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

import inetsoft.report.io.viewsheet.pdf.PDFCoordinateHelper;
import inetsoft.report.io.viewsheet.pdf.PDFCurrentSelectionHelper;
import inetsoft.report.io.viewsheet.svg.SVGCoordinateHelper;
import inetsoft.report.io.viewsheet.svg.SVGCurrentSelectionHelper;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.CurrentSelectionVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CurrentSelectionVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A selection container's fill is painted once, as its object background. Its title redraw
 * repaints only the frame, so a translucent card is not painted twice in PDF or SVG (and PNG).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CurrentSelectionCardPaintTest {
   @Test
   void pdfPaintsAModernContainersFillOnce() {
      CurrentSelectionVSAssemblyInfo info = container(VizMark.MODERN_LIGHT);
      List<Boolean> fills = new ArrayList<>();
      new PDFCurrentSelectionHelper(new PDFCoordinateHelper(new ByteArrayOutputStream())) {
         @Override
         protected void drawTextBox(String value, Rectangle2D bounds, VSCompositeFormat format,
                                    Insets padding)
         {
            record(fills, info, format, true);
         }

         @Override
         protected void drawTextBox(String value, Rectangle2D bounds, VSCompositeFormat format,
                                    boolean needBG, Insets padding)
         {
            record(fills, info, format, needBG);
         }

         @Override
         protected void writeSelections(CurrentSelectionVSAssemblyInfo info) {
         }
      }.write((CurrentSelectionVSAssembly) info.getViewsheet().getAssembly("Container1"));

      assertEquals(List.of(true, false), fills, "one fill, then a frame-only redraw");
   }

   @Test
   void svgPaintsAModernContainersFillOnce() {
      CurrentSelectionVSAssemblyInfo info = container(VizMark.MODERN_LIGHT);
      List<Boolean> fills = new ArrayList<>();
      // Batik is not on this classpath, and the recorder replaces every draw
      new SVGCurrentSelectionHelper(mock(SVGCoordinateHelper.class)) {
         @Override
         protected void drawTextBox(String value, Rectangle2D bounds, VSCompositeFormat format,
                                    Insets padding)
         {
            record(fills, info, format, true);
         }

         @Override
         protected void drawTextBox(String value, Rectangle2D bounds, VSCompositeFormat format,
                                    boolean needBG, Insets padding)
         {
            record(fills, info, format, needBG);
         }

         @Override
         protected void writeSelections(CurrentSelectionVSAssemblyInfo info) {
         }
      }.write((CurrentSelectionVSAssembly) info.getViewsheet().getAssembly("Container1"));

      assertEquals(List.of(true, false), fills, "one fill, then a frame-only redraw");
   }

   // only draws with the container's own object format count; the title draws its own format
   private static void record(List<Boolean> fills, CurrentSelectionVSAssemblyInfo info,
                              VSCompositeFormat format, boolean paintsFill)
   {
      if(format == info.getFormat()) {
         fills.add(paintsFill);
      }
   }

   private static CurrentSelectionVSAssemblyInfo container(VizMark mark) {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setVizMark(mark);
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "Container1");
      container.initDefaultFormat();
      vs.addAssembly(container);
      CurrentSelectionVSAssemblyInfo info =
         (CurrentSelectionVSAssemblyInfo) container.getVSAssemblyInfo();
      info.setPixelOffset(new Point(0, 0));
      info.setPixelSize(new Dimension(200, 300));
      info.setTitleVisibleValue(true);
      assertTrue(info.getFormat().isDefaultCardTint(), "precondition: a modern card");
      return info;
   }
}
