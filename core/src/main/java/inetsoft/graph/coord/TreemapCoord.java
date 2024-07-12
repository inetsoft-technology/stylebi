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
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.Scale;

import java.awt.geom.*;
import java.util.Map;

/**
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public class TreemapCoord extends Coordinate {
   /**
    * This controls the layout of shapes inside the coordinate space. SQUARE will use the largest
    * square space (same width and height), without altering the aspect ratio of shapes.
    * FILL_X will stretch shapes horizontally to fill the width, and FILL will fill
    * both width and height.
    */
   public enum Layout { SQUARE, FILL_X, FILL };

   public TreemapCoord() {
   }

   /**
    * Set whether the tree is drawn as circular shape.
    */
   public void setLayout(Layout layout) {
      this.layout = layout;
   }

   /**
    * Check whether the tree is drawn as circular shape.
    */
   public Layout isLayout() {
      return layout;
   }

   /**
    * Get the padding space on the sides.
    */
   public int getPadding() {
      return padding;
   }

   /**
    * Set the padding space on the sides.
    */
   public void setPadding(int padding) {
      this.padding = padding;
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
      double xfactor = GTool.getScaleFactor(c2, 0);
      double yfactor = GTool.getScaleFactor(c2, 90);
      double plotw = w - 1; // avoid cutting of borders
      double ploth = h - 1;
      double plotx = x;
      double ploty = y;

      if(vgraph != null) {
         // vgraph transform
         AffineTransform trans = new AffineTransform();

         if(layout == Layout.SQUARE || layout == Layout.FILL_X && h > w) {
            double d = Math.min(plotw, ploth) - padding;
            trans.translate(plotx + padding / 2.0, ploty + padding / 2.0);

            if(plotw > ploth) {
               trans.translate((plotw - ploth) / 2, 0);
            }
            else {
               trans.translate(0, (ploth - plotw) / 2);
            }

            trans.scale(d / GWIDTH, d / GHEIGHT);
         }
         else {
            trans.translate(plotx + padding / 2.0, ploty + padding / 2.0);
            trans.scale((plotw - padding) / GWIDTH, (ploth - padding) / GHEIGHT);
         }

         vgraph.concat(trans, true);

         // set the plot bounds before c2 transformation so the
         // getElementBounds can get the right boundary
         vgraph.setPlotBounds(new Rectangle2D.Double(plotx, ploty, plotw,ploth));

         vgraph.concat(c2, false);
         vgraph.setPlotBounds(getBounds(plotx, ploty, plotw, ploth, c2));
      }

      layoutText(vgraph, true);
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
      return 100;
   }

   /**
    * @hidden
    */
   @Override
   public double getUnitMinHeight() {
      return 100;
   }

   /**
    * @hidden
    */
   @Override
   public double getUnitPreferredWidth() {
      return 100;
   }

   /**
    * Get unit preferred height.
    * @hidden
    */
   @Override
   public double getUnitPreferredHeight() {
      return 100;
   }

   @Override
   public Object clone(boolean srange) {
      return clone();
   }

   private Layout layout = Layout.FILL;
   private int padding = 0;
   private static final long serialVersionUID = 1L;
}
