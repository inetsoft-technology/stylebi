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
package inetsoft.graph.coord;

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.StaticSizeFrame;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.RelationElement;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.AffineTransform;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the horizontal-mode special-casing in {@link RelationCoord#getUnitMinWidth()} and
 * {@link RelationCoord#getUnitMinHeight()}. Horizontal width skips back-divide only when
 * faceted (so each facet cell's depth-direction X isn't inflated by 1/scaleX); non-faceted
 * horizontal back-divides so the natural width is reported and the V-scrollable expand step
 * gives the tree a wide enough plot to render at full scale. Height uses natural mxcell bounds
 * in horizontal mode to avoid label-amplified 1/scaleY inflation in faceted layouts.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RelationCoordMinSizeTest {
   @Test
   void facetedHorizontalSkipsBackDivideVerticalDoesNot() {
      // In a facet cell, scaleX is shrunk per row; back-dividing the depth-direction width
      // would inflate X. The horizontal-faceted branch skips back-divide so the cell's min
      // width stays sane. Simulate the facet by wiring a parent coordinate.
      RelationCoord coord = treeCoord(false);
      RelationElement elem = (RelationElement) coord.getVGraph().getEGraph().getElement(0);
      coord.setParentCoordinate(new FacetCoord());

      coord.getVGraph().concat(AffineTransform.getScaleInstance(0.5, 0.5), true);

      elem.setHorizontal(true);
      double horizMin = coord.getUnitMinWidth();

      elem.setHorizontal(false);
      double vertMin = coord.getUnitMinWidth();

      assertTrue(vertMin > horizMin * 1.5,
         "faceted-horizontal must skip back-divide while vertical back-divides "
            + "(horiz=" + horizMin + ", vert=" + vertMin + "); regression: faceted horizontal "
            + "back-divides and X inflates spuriously");
   }

   @Test
   void nonFacetedHorizontalBackDividesWidth() {
      // Non-faceted: back-divide so the reported width is the natural unscaled width including
      // labels. This is what the V-scrollable expand step needs to give the tree a wide enough
      // plot to render at full scale - otherwise uniform fit shrinks it and leaves vertical
      // slack inside the expanded scroll region.
      RelationCoord coord = treeCoord(false);
      RelationElement elem = (RelationElement) coord.getVGraph().getEGraph().getElement(0);
      assertNull(coord.getParentCoordinate(), "test fixture must be non-faceted for this guard");

      coord.getVGraph().concat(AffineTransform.getScaleInstance(0.5, 0.5), true);

      elem.setHorizontal(true);
      double horizMin = coord.getUnitMinWidth();

      elem.setHorizontal(false);
      double vertMin = coord.getUnitMinWidth();

      assertEquals(vertMin, horizMin, 0.5,
         "non-faceted horizontal must back-divide like vertical (horiz=" + horizMin
            + ", vert=" + vertMin + "); regression: horizontal skips back-divide and reports "
            + "the scaled visual width instead of the natural width");
   }

   @Test
   void heightInHorizontalModeIsInvariantToScreenScale() {
      // Horizontal mode reads the natural mxgraph layout bounds directly, so artificial
      // changes to the screen transform must not affect the reported height. Vertical mode
      // still goes through visual bounds + back-divide and so is scale-sensitive.
      RelationCoord coord = treeCoord(false);
      RelationElement elem = (RelationElement) coord.getVGraph().getEGraph().getElement(0);

      elem.setHorizontal(true);
      double horizBefore = coord.getUnitMinHeight();
      coord.getVGraph().concat(AffineTransform.getScaleInstance(0.5, 0.05), true);
      double horizAfter = coord.getUnitMinHeight();

      assertEquals(horizBefore, horizAfter, 0.5,
         "horizontal-mode unitMinHeight must be invariant to screen-transform scaling "
            + "(before=" + horizBefore + ", after=" + horizAfter + ")");
   }

   private static RelationCoord treeCoord(boolean horizontal) {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "From", "To"  },
         { "R",    "A"   },
         { "R",    "B"   },
         { "R",    "C"   },
         { "A",    "A1"  },
         { "B",    "B1"  },
         { "C",    "C1"  },
      });

      RelationElement element = new RelationElement("From", "To");
      element.setAlgorithm(RelationElement.Algorithm.COMPACT_TREE);
      element.setHorizontal(horizontal);
      element.setSizeFrame(new StaticSizeFrame(5));
      element.setNodeSizeFrame(new StaticSizeFrame(5));

      EGraph egraph = new EGraph();
      egraph.addElement(element);
      RelationCoord coord = new RelationCoord();
      egraph.setCoordinate(coord);

      // plotAndLayout runs the full pipeline (createVGraph + layout) so coord.getVGraph()
      // is wired up and visuals have bounds — what the production chart-engine sees.
      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(data, 0, 0, 800, 600);
      assertNotNull(vgraph, "Plotter.plotAndLayout must produce a vgraph for the test fixture");
      return coord;
   }
}
