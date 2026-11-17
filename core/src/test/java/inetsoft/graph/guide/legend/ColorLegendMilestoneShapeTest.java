/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.graph.EGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.element.PointElement;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.graphics.SVGSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * A color legend for a Gantt milestone must draw the milestone's shape (a diamond) instead of
 * the default square swatch, so the legend matches the mark on the chart.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ColorLegendMilestoneShapeTest {
   private static final int X = 10;
   private static final int Y = 10;

   // Batik is not on the core test classpath, but GShape.paint asks SVGSupport whether the
   // graphics is SVG. Seed a stub (not SVG) and restore the cache after.
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

   @Test
   void milestoneColorLegendDrawsMilestoneShape() {
      CategoricalColorFrame color = colorFrame();
      PointElement point = milestone(color);
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement());
      graph.addElement(point);

      LegendItem item = new ColorLegend(color, graph).createLegendItem("a", "a");
      BufferedImage img = paintSymbol(item);

      assertEquals(0, alpha(img, X + 3, Y + 2), "diamond leaves the symbol corner empty");
      assertNotEquals(0, alpha(img, X + 1 + item.getSymbolSize() / 2, Y + item.getSymbolSize() / 2),
                      "diamond fills the symbol center");
   }

   @Test
   void colorLegendWithoutMilestoneKeepsSquare() {
      CategoricalColorFrame color = colorFrame();
      IntervalElement bar = new IntervalElement();
      bar.setColorFrame(color);
      EGraph graph = new EGraph();
      graph.addElement(bar);

      LegendItem item = new ColorLegend(color, graph).createLegendItem("a", "a");
      BufferedImage img = paintSymbol(item);

      assertNotEquals(0, alpha(img, X + 1, Y + 1), "square fills the symbol corner");
   }

   @Test
   void colorFrameSharedWithBarKeepsSquare() {
      CategoricalColorFrame color = colorFrame();
      IntervalElement bar = new IntervalElement();
      bar.setColorFrame(color);
      EGraph graph = new EGraph();
      graph.addElement(bar);
      graph.addElement(milestone(color));

      LegendItem item = new ColorLegend(color, graph).createLegendItem("a", "a");
      BufferedImage img = paintSymbol(item);

      assertNotEquals(0, alpha(img, X + 1, Y + 1),
                      "one legend for bars and milestones keeps the neutral square");
   }

   private static CategoricalColorFrame colorFrame() {
      CategoricalColorFrame frame = new CategoricalColorFrame("Status");
      frame.init(new DefaultDataSet(new Object[][]{ { "Status" }, { "a" } }));
      return frame;
   }

   private static PointElement milestone(ColorFrame color) {
      PointElement point = new PointElement();
      point.setColorFrame(color);
      point.setShapeFrame(new StaticShapeFrame(GShape.FILLED_DIAMOND));
      point.setHint(PointElement.HINT_GANTT_MILESTONE, "true");
      return point;
   }

   private static BufferedImage paintSymbol(LegendItem item) {
      BufferedImage img = new BufferedImage(60, 40, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();

      try {
         item.paintSymbol(g, X, Y);
      }
      finally {
         g.dispose();
      }

      return img;
   }

   private static int alpha(BufferedImage img, int x, int y) {
      return (img.getRGB(x, y) >>> 24) & 0xff;
   }
}
