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
package inetsoft.report;

import org.junit.jupiter.api.Test;

import java.awt.geom.RoundRectangle2D;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for PDFPrinter.doRoundRect to ensure rounded rectangles
 * are not incorrectly rendered as ellipses.
 */
class PDFPrinterRoundRectTest {

   /**
    * Arc dimensions that are >= half the bar size but < full size should
    * produce a rounded rectangle (cubic curves), not an ellipse.
    * This was the bug: doRoundRect used doArc when aw >= width/2.
    */
   @Test
   void largeArc_producesRoundedRectNotEllipse() {
      // aw=20 >= old threshold (width/2=20), but < width=40 — NOT an ellipse
      String content = renderRoundRect(0, 0, 40, 20, 20, 20);
      // doRoundRect produces exactly 4 cubic curves (one per corner)
      int curveCount = countOccurrences(content, " c\n");
      assertEquals(4, curveCount,
                   "Rounded rect should have exactly 4 cubic curves (one per corner)");
      assertTrue(content.contains(" l\n"),
                 "Should contain line 'l' operators for edges between curves");
   }

   /**
    * When arcs fully cover both dimensions, the shape IS a true ellipse
    * and should use doArc (which produces != 4 curve segments).
    */
   @Test
   void trueEllipse_producesArc() {
      // aw=40 >= width=40 AND ah=20 >= height=20 — true ellipse
      String content = renderRoundRect(0, 0, 40, 20, 40, 20);
      int curveCount = countOccurrences(content, " c\n");
      assertTrue(curveCount > 4,
                 "Ellipse via doArc should produce many more than 4 curves");
   }

   /**
    * Normal small arcs should produce a standard rounded rectangle.
    */
   @Test
   void normalArc_producesRoundedRect() {
      String content = renderRoundRect(0, 0, 100, 40, 10, 10);
      int curveCount = countOccurrences(content, " c\n");
      assertEquals(4, curveCount,
                   "Normal rounded rect should have exactly 4 cubic curves");
   }

   private String renderRoundRect(double x, double y, double w, double h,
                                  double arcW, double arcH)
   {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PDFPrinter printer = new PDFPrinter(baos);
      // disable compression so PDF stream content is readable for assertions
      printer.setCompressText(false);

      RoundRectangle2D rect = new RoundRectangle2D.Double(x, y, w, h, arcW, arcH);
      printer.fill(rect);
      printer.dispose();

      return baos.toString(StandardCharsets.UTF_8);
   }

   private int countOccurrences(String text, String sub) {
      int count = 0;
      int idx = 0;

      while((idx = text.indexOf(sub, idx)) != -1) {
         count++;
         idx += sub.length();
      }

      return count;
   }
}
