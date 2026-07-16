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
package inetsoft.graph.element;

import inetsoft.graph.EGraph;
import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.GLine;
import inetsoft.graph.aesthetic.GShape;
import inetsoft.graph.aesthetic.StaticSizeFrame;
import inetsoft.graph.coord.RelationCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.geometry.RelationGeometry;
import inetsoft.graph.mxgraph.model.mxGeometry;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that RelationElement.mxLayout() populates getLayoutCenter() with the centroid of
 * laid-out node positions (post flipY + translate-to-origin), in the same coordinate space
 * the edge geometry will subsequently report. Required by RelationEdgeGeometry.getEdges()
 * to bend smooth edges toward a meaningful pull point.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RelationElementLayoutCenterTest {
   @Test
   void mxLayoutPopulatesLayoutCenterAsNodeCentroid_circular() {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "From", "To" },
         { "A",    "B"  },
         { "B",    "C"  },
         { "C",    "D"  },
         { "D",    "A"  },
      });

      RelationElement element = new RelationElement("From", "To");
      element.setAlgorithm(RelationElement.Algorithm.CIRCLE);
      // createGeometry / RelationGeometry.getSize dereference the edge and node size frames;
      // static frames keep those paths NPE-free without affecting layout positions.
      element.setSizeFrame(new StaticSizeFrame(5));
      element.setNodeSizeFrame(new StaticSizeFrame(5));

      EGraph egraph = new EGraph();
      egraph.addElement(element);

      RelationCoord coord = new RelationCoord();
      egraph.setCoordinate(coord);

      GGraph ggraph = egraph.createGGraph(coord, data);

      // GGraph builds geometries lazily on first access; force the layout pass so the
      // element's layoutCenter gets populated.
      List<RelationGeometry> nodes = new ArrayList<>();

      for(int i = 0; i < ggraph.getGeometryCount(); i++) {
         if(ggraph.getGeometry(i) instanceof RelationGeometry) {
            nodes.add((RelationGeometry) ggraph.getGeometry(i));
         }
      }

      Point2D layoutCenter = element.getLayoutCenter();
      assertNotNull(layoutCenter,
         "mxLayout must populate layoutCenter after a circular layout pass");

      assertEquals(4, nodes.size(), "expected one geometry per unique node (A,B,C,D)");

      double sumCx = 0, sumCy = 0;

      for(RelationGeometry n : nodes) {
         mxGeometry g = n.getMxCell().getGeometry();
         sumCx += g.getX() + g.getWidth() / 2.0;
         sumCy += g.getY() + g.getHeight() / 2.0;
      }

      double expectedCx = sumCx / nodes.size();
      double expectedCy = sumCy / nodes.size();

      assertEquals(expectedCx, layoutCenter.getX(), 1e-6,
         "layoutCenter x must equal the mean of node-centre x positions");
      assertEquals(expectedCy, layoutCenter.getY(), 1e-6,
         "layoutCenter y must equal the mean of node-centre y positions");
   }

   @Test
   void layoutCenterStartsNullBeforeLayoutRuns() {
      RelationElement element = new RelationElement("From", "To");
      assertNull(element.getLayoutCenter(),
         "layoutCenter must be null until mxLayout runs so consumers gate on it");
   }

   @Test
   void mxLayoutPopulatesLayoutRadiusAsDistanceToNodeCenter_circular() {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "From", "To" },
         { "A",    "B"  },
         { "B",    "C"  },
         { "C",    "A"  },
      });

      RelationElement element = new RelationElement("From", "To");
      element.setAlgorithm(RelationElement.Algorithm.CIRCLE);
      element.setSizeFrame(new StaticSizeFrame(5));
      element.setNodeSizeFrame(new StaticSizeFrame(5));

      EGraph egraph = new EGraph();
      egraph.addElement(element);
      RelationCoord coord = new RelationCoord();
      egraph.setCoordinate(coord);
      GGraph ggraph = egraph.createGGraph(coord, data);

      for(int i = 0; i < ggraph.getGeometryCount(); i++) {
         ggraph.getGeometry(i);
      }

      assertTrue(element.getLayoutRadius() > 0,
         "layoutRadius must be positive after a circular layout with multiple nodes");
   }

   @Test
   void singleNodeCircularLayoutProducesZeroRadius() {
      // A single node means layoutCenter == node center, so distance is 0 and no ring is drawn.
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "From", "To" },
         { "A",    "A"  },
      });

      RelationElement element = new RelationElement("From", "To");
      element.setAlgorithm(RelationElement.Algorithm.CIRCLE);
      element.setSizeFrame(new StaticSizeFrame(5));
      element.setNodeSizeFrame(new StaticSizeFrame(5));

      EGraph egraph = new EGraph();
      egraph.addElement(element);
      RelationCoord coord = new RelationCoord();
      egraph.setCoordinate(coord);
      GGraph ggraph = egraph.createGGraph(coord, data);

      for(int i = 0; i < ggraph.getGeometryCount(); i++) {
         ggraph.getGeometry(i);
      }

      assertEquals(0.0, element.getLayoutRadius(), 1e-6,
         "single-node circular layout must produce layoutRadius == 0 so no ring is added");
   }

   @Test
   void addShapeBorderRegistersFormInEGraphAfterLayout() {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "From", "To" },
         { "A",    "B"  },
         { "B",    "C"  },
         { "C",    "A"  },
      });

      RelationElement element = new RelationElement("From", "To");
      element.setAlgorithm(RelationElement.Algorithm.CIRCLE);
      element.setSizeFrame(new StaticSizeFrame(5));
      element.setNodeSizeFrame(new StaticSizeFrame(5));
      element.addShapeBorder(GShape.CIRCLE, Color.GRAY, GLine.MEDIUM_DASH);

      EGraph egraph = new EGraph();
      egraph.addElement(element);
      RelationCoord coord = new RelationCoord();
      egraph.setCoordinate(coord);
      GGraph ggraph = egraph.createGGraph(coord, data);

      for(int i = 0; i < ggraph.getGeometryCount(); i++) {
         ggraph.getGeometry(i);
      }

      assertEquals(1, ggraph.getEGraph().getFormCount(),
         "addShapeBorder must register exactly one BorderForm in the EGraph after layout");
   }

   @Test
   void mxLayoutSkipsCentroidForNonCircularAlgorithm() {
      // Smooth-edge curving only consumes layoutCenter for Algorithm.CIRCLE; other algorithms
      // (HIERARCHY/RADIAL/COMPACT_TREE/ORGANIC) skip the O(n) centroid pass and leave it null.
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "From", "To" },
         { "A",    "B"  },
         { "B",    "C"  },
         { "C",    "D"  },
      });

      RelationElement element = new RelationElement("From", "To");
      element.setAlgorithm(RelationElement.Algorithm.ORGANIC);
      element.setSizeFrame(new StaticSizeFrame(5));
      element.setNodeSizeFrame(new StaticSizeFrame(5));

      EGraph egraph = new EGraph();
      egraph.addElement(element);

      RelationCoord coord = new RelationCoord();
      egraph.setCoordinate(coord);

      GGraph ggraph = egraph.createGGraph(coord, data);

      // force the layout pass.
      for(int i = 0; i < ggraph.getGeometryCount(); i++) {
         ggraph.getGeometry(i);
      }

      assertNull(element.getLayoutCenter(),
         "non-CIRCLE algorithms must leave layoutCenter null so non-circular smooth-edge "
         + "guards short-circuit");
   }
}
