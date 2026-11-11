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
package inetsoft.graph.guide.legend;

import inetsoft.graph.EGraph;
import inetsoft.graph.LegendSpec;
import inetsoft.graph.aesthetic.LinearSizeFrame;
import inetsoft.graph.data.DefaultDataSet;
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
 * The size legend's symbol colours follow the legend spec when it sets them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SizeLegendSymbolTest {
   // the batik SVG support is not on the test classpath; seed a stub and restore the cache after
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
   void anUnsetSpecKeepsTheOldColours() {
      LegendItem item = firstItem(new LegendSpec());
      assertEquals(new Color(0x759595), item.getSymbolColor());
      assertEquals(new Color(0xA3A3A3), boxEdge(item));
   }

   @Test
   void aSetFallbackColoursTheSymbol() {
      LegendSpec spec = new LegendSpec();
      spec.setSymbolFallbackColor(new Color(0x6A685F));
      assertEquals(new Color(0x6A685F), firstItem(spec).getSymbolColor());
   }

   @Test
   void aSetBorderDrawsTheBox() {
      LegendSpec spec = new LegendSpec();
      spec.setSymbolBorderColor(new Color(0xD6D2C8));
      assertEquals(new Color(0xD6D2C8), boxEdge(firstItem(spec)));
   }

   @Test
   void theNewColoursAreCloned() {
      LegendSpec spec = new LegendSpec();
      spec.setSymbolFallbackColor(Color.RED);
      spec.setSymbolBorderColor(Color.BLUE);
      LegendSpec copy = spec.clone();
      assertEquals(Color.RED, copy.getSymbolFallbackColor());
      assertEquals(Color.BLUE, copy.getSymbolBorderColor());
      assertEquals(spec, copy);
      copy.setSymbolBorderColor(Color.GREEN);
      assertNotEquals(spec, copy);
   }

   private static LegendItem firstItem(LegendSpec spec) {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "Total" }, { 1.0 }, { 5.0 }, { 10.0 } });
      LinearSizeFrame frame = new LinearSizeFrame("Total");
      frame.init(data);
      frame.setLegendSpec(spec);
      SizeLegend legend = new SizeLegend(frame, new EGraph());
      LegendItem last = null;

      for(int i = 0; i < legend.getVisualCount(); i++) {
         if(legend.getVisual(i) instanceof LegendItem) {
            last = (LegendItem) legend.getVisual(i);
         }
      }

      assertNotNull(last, "premise: the legend has items");
      return last;
   }

   // the colour of the box's left edge, painted at the symbol's origin
   private static Color boxEdge(LegendItem item) {
      BufferedImage img = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();

      try {
         item.paintSymbol(g, 10, 10);
      }
      finally {
         g.dispose();
      }

      int size = item.getSymbolSize();
      return new Color(img.getRGB(10, 10 + size / 2), true);
   }
}
