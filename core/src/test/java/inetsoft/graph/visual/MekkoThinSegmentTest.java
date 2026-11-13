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
package inetsoft.graph.visual;

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.coord.MekkoCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.MekkoElement;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A modern marimekko segment too thin to survive its separators draws as a 1px line in its own
 * colour.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MekkoThinSegmentTest {
   @Test
   void aThinSegmentShowsItsColour() {
      Chart chart = chart(true);
      Color expected = ((inetsoft.graph.geometry.MekkoGeometry) chart.thin.getGeometry()).getColor(0);
      Rectangle2D box = chart.thin.getBounds();

      assertTrue(box.getHeight() > 0 && box.getHeight() < 2, "premise: a sub-2px segment " + box);
      assertColor(expected, chart.image.getRGB((int) box.getCenterX(), (int) box.getCenterY()));
      assertColor(expected, chart.image.getRGB((int) (box.getX() + 2), (int) box.getCenterY()));
      assertEquals(chart.thinZ + 1, chart.thin.getZIndex(), "painted over the neighbours");
   }

   @Test
   void classicThinSegmentUnchanged() {
      Chart chart = chart(false);
      Rectangle2D box = chart.thin.getBounds();

      assertEquals(Color.WHITE.getRGB(),
                   chart.image.getRGB((int) box.getCenterX(), (int) box.getCenterY()) | 0xFF000000,
                   "the separator covers the segment");
      assertEquals(chart.thinZ, chart.thin.getZIndex());
   }

   private static void assertColor(Color expected, int rgb) {
      Color actual = new Color(rgb, true);
      assertEquals(expected.getRed(), actual.getRed(), 8, "red " + actual);
      assertEquals(expected.getGreen(), actual.getGreen(), 8, "green " + actual);
      assertEquals(expected.getBlue(), actual.getBlue(), 8, "blue " + actual);
   }

   private static Chart chart(boolean thinLine) {
      Object[][] rows = {
         { "Col", "Seg", "Val" },
         { "A", "big", 1000 }, { "A", "thin", 2 }, { "A", "mid", 1000 },
         { "B", "big", 1000 }, { "B", "thin", 1000 }, { "B", "mid", 1000 }
      };
      EGraph graph = new EGraph();
      MekkoElement elem = new MekkoElement("Col", "Seg", "Val");
      elem.setColorFrame(new CategoricalColorFrame("Seg"));
      elem.setThinSegmentLine(thinLine);
      graph.addElement(elem);
      graph.setCoordinate(new MekkoCoord("Col", "Val"));
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 240);
      List<MekkoVO> vos = new ArrayList<>();
      MekkoVO thin = null;

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof MekkoVO vo) {
            vos.add(vo);

            if(thin == null || vo.getBounds().getHeight() < thin.getBounds().getHeight()) {
               thin = vo;
            }
         }
      }

      // the segments alone, in paint order (the axes need vector graphics support)
      Collections.sort(vos);

      BufferedImage image = new BufferedImage(400, 240, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, 400, 240);
      vos.forEach(vo -> vo.paint(g));
      g.dispose();

      return new Chart(thin, image, inetsoft.graph.internal.GDefaults.VO_Z_INDEX);
   }

   private record Chart(MekkoVO thin, BufferedImage image, int thinZ) {
   }
}
