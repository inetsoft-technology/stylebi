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
package inetsoft.graph.guide.legend;

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.guide.VLabel;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.graphics.SVGSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Bug #79398: with legend Row Spacing top != bottom, each label must stay vertically centered on
 * its symbol and inside its own row (it used to shift by bottom - top and reach into the title).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LegendItemLabelAlignmentTest {
   // Batik (BatikSVGSupport) is not on the core test classpath, but GTool.drawString asks
   // SVGSupport whether the graphics is SVG. Seed a stub (not SVG) and restore the cache after.
   private SVGSupport savedSvgSupport;

   @BeforeEach
   void stubSvgSupport() {
      savedSvgSupport = SVGSupport._CACHE[0];
      SVGSupport._CACHE[0] = mock(SVGSupport.class);
   }

   @AfterEach
   void restoreSvgSupport() {
      SVGSupport._CACHE[0] = savedSvgSupport;
   }

   static Stream<Arguments> cases() {
      return Stream.of("color", "line").flatMap(kind -> Stream.of(
         Arguments.of(kind, null),
         Arguments.of(kind, new Insets(0, 0, 10, 0)),
         Arguments.of(kind, new Insets(10, 0, 0, 0)),
         Arguments.of(kind, new Insets(3, 0, 7, 0))));
   }

   @ParameterizedTest(name = "{0} padding={1}")
   @MethodSource("cases")
   void labelCenteredOnSymbolAndInsideRow(String kind, Insets padding) {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{ { "Year" }, { "2025" } });
      VisualFrame frame = "line".equals(kind) ? new CategoricalLineFrame("Year")
         : new CategoricalColorFrame("Year");
      frame.init(data);
      frame.getLegendSpec().setPadding(padding);

      double[] symbolY = { Double.NaN };
      LegendItem item = "line".equals(kind)
         ? new LineLegendItem("2025", "2025", frame) {
              @Override
              protected void paintSymbol(Graphics2D g, double x, double y) {
                 symbolY[0] = y;
                 super.paintSymbol(g, x, y);
              }
           }
         : new ColorLegendItem("2025", "2025", frame) {
              @Override
              protected void paintSymbol(Graphics2D g, double x, double y) {
                 symbolY[0] = y;
                 super.paintSymbol(g, x, y);
              }
           };

      double rowY = 10;
      double rowH = item.getPreferredHeight();
      item.setBounds(0, rowY, 120, rowH);
      item.layout();

      BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();

      try {
         item.paint(g);
      }
      finally {
         g.dispose();
      }

      assertTrue(!Double.isNaN(symbolY[0]), "paintSymbol was not called");
      // both ColorLegendItem (box) and LineLegendItem (line at y + size/2) center on y + size/2
      double symbolCenter = symbolY[0] + item.getSymbolSize() / 2.0;

      VLabel label = (VLabel) item.getVisual(0);
      double boxBottom = label.getPosition().getY();
      double boxTop = boxBottom + label.getSize().getHeight();
      double boxCenter = (boxBottom + boxTop) / 2;
      Rectangle2D text = label.getTransformedBounds().getBounds2D();

      assertEquals(symbolCenter, boxCenter, 1.0,
         "label box center vs symbol center, row [" + rowY + "," + (rowY + rowH) + "]");
      assertEquals(symbolCenter, text.getCenterY(), 1.0, "label text center vs symbol center");
      assertTrue(boxBottom >= rowY - 0.01 && boxTop <= rowY + rowH + 0.01,
         "label box [" + boxBottom + "," + boxTop + "] leaves row [" + rowY + ","
            + (rowY + rowH) + "]");
   }
}
