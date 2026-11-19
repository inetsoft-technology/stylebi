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
import inetsoft.graph.aesthetic.StaticSizeFrame;
import inetsoft.graph.coord.RelationCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.RelationElement;
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

import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A rounded relation node reports the corner fraction it draws with, so the client outline
 * of the node matches its corner.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RelationVOEffectiveCornerTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedNodeReportsItsFlooredCorner() {
      RelationVO node = node(true);

      assertEquals(2.0 / 23, node.getEffectiveCornerRadius(), 1e-9);
      assertEquals(BarVO.flooredCornerFraction(0.08, 23), node.getEffectiveCornerRadius(), 1e-9);
   }

   @Test
   void aClassicNodeReportsItsOwnFraction() {
      assertEquals(0.08, node(false).getEffectiveCornerRadius(), 0);
   }

   // a 33x23 node drawn at its layout size
   private static RelationVO node(boolean cornerFloor) {
      RelationElement elem = new RelationElement("From", "To");
      elem.setAlgorithm(RelationElement.Algorithm.COMPACT_TREE);
      // an inside label adds 4px across at size 1
      elem.setNodeWidth(29);
      elem.setNodeHeight(23);
      elem.setSizeFrame(new StaticSizeFrame(1));
      elem.setNodeSizeFrame(new StaticSizeFrame(1));
      elem.setNodeCornerRadius(0.08);
      elem.setCornerFloor(cornerFloor);
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      egraph.setCoordinate(new RelationCoord());
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "From", "To" }, { "R", "A" }, { "R", "B" } });

      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(data, 0, 0, 376, 190);
      assertNotNull(vgraph);

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof RelationVO vo) {
            Rectangle2D b = vo.getBounds();
            assertEquals(33, b.getWidth(), 1e-9, "premise: node width");
            assertEquals(23, b.getHeight(), 1e-9, "premise: node height");
            return vo;
         }
      }

      return fail("premise: a node");
   }
}
