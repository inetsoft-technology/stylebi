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
 * Guards the asymmetric back-divide in {@link RelationCoord#getUnitMinWidth()}: vertical
 * mode back-divides by scaleX (so post-fit calls recover natural width); horizontal mode
 * skips the back-divide (X is depth, scaleX is Y-binding-driven, dividing would over-report
 * X and force the chart canvas to widen unnecessarily).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RelationCoordMinSizeTest {
   @Test
   void horizontalModeSkipsBackDivideVerticalDoesNot() {
      // Lay out one tree, then toggle the horizontal flag on the same coord and observe
      // how getUnitMinWidth's formula reacts to a non-1 scaleX. Vertical mode back-divides
      // and so reports ~2× horizontal's value when scaleX=0.5.
      RelationCoord coord = treeCoord(false);
      RelationElement elem = (RelationElement) coord.getVGraph().getEGraph().getElement(0);

      coord.getVGraph().concat(AffineTransform.getScaleInstance(0.5, 0.5), true);

      elem.setHorizontal(true);
      double horizMin = coord.getUnitMinWidth();

      elem.setHorizontal(false);
      double vertMin = coord.getUnitMinWidth();

      assertTrue(vertMin > horizMin * 1.5,
         "vertical-mode back-divide should give a notably larger min than horizontal mode "
            + "(horiz=" + horizMin + ", vert=" + vertMin + "); regression: horizontal also "
            + "back-divides and X inflates spuriously");
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
