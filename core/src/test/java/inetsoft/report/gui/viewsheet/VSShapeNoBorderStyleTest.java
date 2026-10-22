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
package inetsoft.report.gui.viewsheet;

import inetsoft.report.StyleConstants;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79148: a shape whose line style is None must not stroke its outline in
 * server-side renders (a zero-width Java2D stroke is a 1px hairline, not invisible).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSShapeNoBorderStyleTest {
   @Test
   void lineWithNoneStyleDrawsNothing() {
      assertEquals(0, opaque(line(StyleConstants.NO_BORDER, StyleConstants.NO_BORDER)));
   }

   @Test
   void lineWithThinStyleDrawsStroke() {
      assertTrue(opaque(line(StyleConstants.THIN_LINE, StyleConstants.NO_BORDER)) > 0);
   }

   @Test
   void lineWithNoneStyleStillDrawsArrow() {
      int arrowOnly = opaque(line(StyleConstants.NO_BORDER, StyleConstants.ARROW_LINE_1));
      int both = opaque(line(StyleConstants.THIN_LINE, StyleConstants.ARROW_LINE_1));
      assertTrue(arrowOnly > 0, "arrow must still be drawn");
      assertTrue(arrowOnly < both, "main stroke must not be drawn");
   }

   @Test
   void ovalWithNoneStyleDrawsNothing() {
      assertEquals(0, opaque(oval(StyleConstants.NO_BORDER)));
   }

   @Test
   void ovalWithThinStyleDrawsOutline() {
      assertTrue(opaque(oval(StyleConstants.THIN_LINE)) > 0);
   }

   private static BufferedImage line(int style, int endArrow) {
      Viewsheet vs = new Viewsheet();
      LineVSAssembly asm = new LineVSAssembly(vs, "Line1");
      vs.addAssembly(asm);
      LineVSAssemblyInfo info = (LineVSAssemblyInfo) asm.getVSAssemblyInfo();
      info.setPixelSize(new Dimension(400, 1));
      info.setStartPos(new Point(0, 0));
      info.setEndPos(new Point(400, 0));
      info.setLineStyle(style);
      info.setBeginArrowStyle(StyleConstants.NO_BORDER);
      info.setEndArrowStyle(endArrow);
      info.getFormat().getUserDefinedFormat().setForeground(Color.RED);
      VSLine shape = new VSLine(vs);
      shape.setAssemblyInfo(info);
      return (BufferedImage) shape.getImage(true);
   }

   private static BufferedImage oval(int style) {
      Viewsheet vs = new Viewsheet();
      OvalVSAssembly asm = new OvalVSAssembly(vs, "Oval1");
      OvalVSAssemblyInfo info = (OvalVSAssemblyInfo) asm.getVSAssemblyInfo();
      info.setPixelSize(new Dimension(100, 60));
      info.setLineStyle(style);
      info.getFormat().getUserDefinedFormat().setForeground(Color.RED);
      VSOval shape = new VSOval(vs);
      shape.setViewsheet(vs);
      shape.setAssemblyInfo(info);
      return (BufferedImage) shape.getImage(true);
   }

   private static int opaque(BufferedImage img) {
      int n = 0;

      for(int y = 0; y < img.getHeight(); y++) {
         for(int x = 0; x < img.getWidth(); x++) {
            if((img.getRGB(x, y) >>> 24) != 0) {
               n++;
            }
         }
      }

      return n;
   }
}
