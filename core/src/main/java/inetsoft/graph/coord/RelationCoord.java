/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.coord;

import inetsoft.graph.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DataSetIndex;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.scale.Scale;

import java.awt.geom.*;
import java.util.Map;

/**
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public class RelationCoord extends Coordinate {
   public RelationCoord() {
   }

   @Override
   public Point2D getPosition(double[] tuple) {
      return new Point2D.Double(0, 0);
   }

   @Override
   public double getIntervalSize(double interval) {
      return 0;
   }

   @Override
   public void createAxis(VGraph vgraph) {
   }

   /**
    * Set the screen transformation to fit the graph to the specified output
    * size.
    * @param x horizontal position.
    * @param y vertical position.
    * @param w width to scale to.
    * @param h height to scale to.
    */
   @Override
   public void fit(double x, double y, double w, double h) {
      AffineTransform c2 = getScaledCoordTransform(x, y, w, h);

      VGraph vgraph = getVGraph();
      double plotw = w - 1; // circle has anti-aliasing shade, avoid cutting it off
      double ploth = h - 1;
      double plotx = x;
      double ploty = y;

      if(vgraph != null) {
         // vgraph transform
         AffineTransform trans = new AffineTransform();

         trans.translate(plotx, ploty);
         //trans.scale(plotw / GWIDTH, ploth / GHEIGHT);
         vgraph.concat(trans, true);

         AffineTransform trans2 = new AffineTransform();
         /* the position is flipped in RelationElement.flipY() so doesn't need to be done here.
         // flip tree so the root is at top
         trans2.scale(1, -1);
         trans2.translate(GAP, -(h - GAP)); // leave a little gap on sides
         */
         trans2.translate(GAP, GAP); // leave a little gap on sides

         Rectangle2D allBounds = getElementBounds(vgraph, true);
         Rectangle2D selfBounds = getElementBounds(vgraph, false);

         final double w2 = w - GAP * 2;
         final double h2 = h - GAP * 2;
         double ratio = 1;

         // scale if can't fit
         if(h2 > 0 && allBounds != null &&
            (allBounds.getWidth() > w2 || allBounds.getHeight() > h2))
         {
            ratio = Math.min(w2 / allBounds.getWidth(), h2 / allBounds.getHeight());
         }

         // center nodes
         if(selfBounds != null && !selfBounds.isEmpty()) {
            double diffW = w2 - selfBounds.getWidth() * ratio;
            double diffH = h2 - selfBounds.getHeight() * ratio;
            double scaledY = selfBounds.getY() * ratio;
            boolean facet = getParentCoordinate() != null;

            // center if extra space
            if(diffW >= 0) {
               trans2.translate(diffW / 2, 0);
            }

            if(diffH >= 0) {
               trans2.translate(0, -(scaledY - y + (facet ? ploty : 0)) + diffH / 2);
            }
         }

         if(ratio != 1) {
            trans2.scale(ratio, ratio);
         }

         vgraph.concat(trans2, true);

         // set the plot bounds before c2 transformation so the
         // getElementBounds can get the right boundary
         vgraph.setPlotBounds(new Rectangle2D.Double(plotx, ploty, plotw, ploth));

         vgraph.concat(c2, false);
         vgraph.setPlotBounds(getBounds(plotx, ploty, plotw, ploth, c2));
      }

      layoutText(vgraph, true);
   }

   @Override
   VGraph getScaledVGraph() {
      return getVGraph();
   }

   private Rectangle2D getVisualObjectBounds() {
      VGraph vgraph = getVGraph();
      Rectangle2D allBounds = null;

      if(needLayout) {
         layoutText(vgraph, false);
         needLayout = false;
      }

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Rectangle2D bounds = vgraph.getVisual(i).getBounds();
         allBounds = allBounds == null ? bounds : allBounds.createUnion(bounds);
      }

      return allBounds;
   }

   @Override
   public int getDimCount() {
      return 0;
   }

   @Override
   public Scale[] getScales() {
      return new Scale[0];
   }

   @Override
   public void createSubGGraph(DataSet rset, DataSetIndex ridx, DataSet dset,
                               EGraph graph, GGraph ggraph, FacetCoord facet, Map xmap, Map ymap,
                               int hint)
   {
      // do nothing
   }

   /**
    * @hidden
    */
   @Override
   public Axis[] getAxes(boolean recursive) {
      return new Axis[0];
   }

   /**
    * @hidden
    */
   @Override
   public double getUnitMinWidth() {
      Rectangle2D box = getVisualObjectBounds();
      // preferred size should be unscaled size
      double scaleX = Math.abs(getVGraph().getScreenTransform().getScaleX());
      return box != null ? (box.getMaxX() - box.getX()) / scaleX + GAP * 2 : 100;
   }

   /**
    * @hidden
    */
   @Override
   public double getUnitMinHeight() {
      Rectangle2D box = getVisualObjectBounds();
      // preferred size should be unscaled size
      double scaleY = Math.abs(getVGraph().getScreenTransform().getScaleY());
      return box != null ? (box.getMaxY() - box.getY()) / scaleY + GAP * 2 : 100;
   }

   /**
    * @hidden
    */
   @Override
   public double getUnitPreferredWidth() {
      return getUnitMinWidth();
   }

   /**
    * Get unit preferred height.
    * @hidden
    */
   @Override
   public double getUnitPreferredHeight() {
      return getUnitMinHeight();
   }

   @Override
   public Object clone(boolean srange) {
      return clone();
   }

   private static final int GAP = 4;
   private static final long serialVersionUID = 1L;
   private boolean needLayout = true;
}
