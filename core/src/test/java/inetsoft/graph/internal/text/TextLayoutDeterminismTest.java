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
package inetsoft.graph.internal.text;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.DefaultTextFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.visual.VOText;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The overlap resolution in TextLayoutManager must give the same result every time the same
 * chart is laid out. FreeHelper.processOverlapped nudges labels that share a center X or Y by a
 * small random amount, and the nudges steer which way each label moves. When they came from
 * Math.random(), every render of a chart with overlapping equal-value bar labels put the labels
 * at different positions, and now and then a different label was hidden. (82196)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TextLayoutDeterminismTest {
   @Test
   void overlappingBarLabelsAreLaidOutTheSameOnEveryRender() {
      List<VOText> first = layoutBars();

      // premise: the labels are wider than the bar spacing, so neighbours overlap and the
      // layout has to move (and may hide) some of them
      assertEquals(CATEGORIES, first.size());
      double spacing = first.get(1).getElementVO().getBounds().getCenterX() -
         first.get(0).getElementVO().getBounds().getCenterX();
      double labelWidth = first.get(0).getBounds().getWidth();
      assertTrue(spacing < labelWidth,
                 "labels must overlap: spacing " + spacing + ", label width " + labelWidth);

      String expected = fingerprint(first);

      for(int i = 1; i < RENDERS; i++) {
         assertEquals(expected, fingerprint(layoutBars()),
                      "render " + i + " laid out the labels differently from render 0");
      }
   }

   /**
    * One line per label: its bar, z-index (negative when hidden) and final position.
    */
   private static String fingerprint(List<VOText> texts) {
      StringBuilder sb = new StringBuilder();

      for(VOText text : texts) {
         Rectangle2D bounds = text.getBounds();
         sb.append("bar x=").append(text.getElementVO().getBounds().getCenterX())
            .append(" z=").append(text.getZIndex())
            .append(" label x=").append(bounds.getX())
            .append(" y=").append(bounds.getY())
            .append('\n');
      }

      return sb.toString();
   }

   /**
    * Lay out a bar chart of 24 categories with the same value, so every value label sits at the
    * same Y and the labels overlap their neighbours.
    */
   private static List<VOText> layoutBars() {
      Object[][] rows = new Object[CATEGORIES + 1][];
      rows[0] = new Object[] { "State", "Sales", "Label" };

      for(int i = 0; i < CATEGORIES; i++) {
         rows[i + 1] = new Object[] { "S" + (char) ('A' + i), 30.0, "-1,250" };
      }

      IntervalElement elem = new IntervalElement("State", "Sales");
      elem.setTextFrame(new DefaultTextFrame("Label"));
      elem.setLabelPlacement(GraphConstants.CENTER);

      LinearScale scale = new LinearScale("Sales");
      scale.setMin(0);
      scale.setMax(100);

      EGraph graph = new EGraph();
      graph.addElement(elem);
      graph.setCoordinate(new RectCoord(new CategoricalScale("State"), scale));

      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 240);
      List<VOText> texts = new ArrayList<>();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof VOText text) {
            texts.add(text);
         }
      }

      return texts;
   }

   private static final int CATEGORIES = 24;
   private static final int RENDERS = 5;
}
