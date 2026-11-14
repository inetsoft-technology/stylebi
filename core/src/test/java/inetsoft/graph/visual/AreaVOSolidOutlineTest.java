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
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.AreaElement;
import inetsoft.graph.element.GraphElement;
import inetsoft.report.internal.Graphics2DWrapper;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A closed area's outline strokes at 1.2 times its fill alpha, or opaque when the element asks
 * for a solid outline.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AreaVOSolidOutlineTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aSolidOutlineStrokesAtOne() {
      AreaVO vo = closedArea(true);

      assertEquals(1, vo.outlineAlpha(), 1e-9);
      assertEquals(List.of(255), outlineAlphas(vo));
   }

   @Test
   void otherwiseTheOutlineIsTheFillAlphaTimes12() {
      AreaVO vo = closedArea(false);

      assertEquals(0.36, vo.outlineAlpha(), 1e-9);
      assertEquals(List.of(new Color(0, 0, 0, 0.36f).getAlpha()), outlineAlphas(vo));
   }

   @Test
   void theFlagIsOffByDefault() {
      assertFalse(new AreaElement().isSolidOutline());
   }

   @Test
   void theFlagIsPartOfTheContent() {
      AreaElement solid = new AreaElement();
      solid.setSolidOutline(true);

      assertFalse(solid.equalsContent(new AreaElement()));
      assertTrue(solid.equalsContent(solid.clone()));
   }

   // a closed area at 30%, plotted
   private static AreaVO closedArea(boolean solidOutline) {
      Object[][] rows = {
         { "x", "y" }, { 1.0, 3.0 }, { 2.0, 5.0 }, { 3.0, 2.0 }, { 4.0, 6.0 }, { 5.0, 4.0 } };
      EGraph graph = new EGraph();
      AreaElement elem = new AreaElement("x", "y");
      elem.setClosed(true);
      elem.setHint(GraphElement.HINT_ALPHA, 0.3);
      elem.setSolidOutline(solidOutline);
      graph.addElement(elem);
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 300);

      for(int i = 0; i < vg.getVisualCount(); i++) {
         if(vg.getVisual(i) instanceof AreaVO vo) {
            return vo;
         }
      }

      return fail("no AreaVO");
   }

   // the alpha of every stroke the paint draws; the fill is filled, not stroked
   private static List<Integer> outlineAlphas(AreaVO vo) {
      BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      List<Integer> alphas = new ArrayList<>();
      vo.paint(new Recording(g, alphas));
      g.dispose();
      return alphas;
   }

   // records the colour alpha of every draw, through every copy it makes
   private static final class Recording extends Graphics2DWrapper {
      Recording(Graphics2D g, List<Integer> alphas) {
         super(g, true);
         this.alphas = alphas;
      }

      @Override
      public Graphics create() {
         return new Recording((Graphics2D) g.create(), alphas);
      }

      @Override
      public void draw(Shape s) {
         alphas.add(getColor().getAlpha());
         super.draw(s);
      }

      private final List<Integer> alphas;
   }
}
