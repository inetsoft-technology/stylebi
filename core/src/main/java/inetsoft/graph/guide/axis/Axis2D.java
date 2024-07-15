/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.guide.axis;

import inetsoft.graph.GraphConstants;
import inetsoft.graph.VGraph;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.Scale;

import java.awt.*;
import java.awt.geom.*;

/**
 * This class provides the rendering of 2d axis.
 * @hidden
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class Axis2D extends DefaultAxis {
   /**
    * Default constructor.
    */
   public Axis2D() {
      super();
      setZIndex(GDefaults.AXIS2D_Z_INDEX);
   }

   /**
    * Create an axis for the specified scale.
    * @param scale the scale for this visual axis.
    */
   public Axis2D(Scale scale, VGraph vgraph) {
      super(scale, vgraph);
      setZIndex(GDefaults.AXIS2D_Z_INDEX);
   }

   /**
    * Create grid lines from this axis to the other axis.
    * @param axis the other axis to draw grid lines to.
    * @param asc true to create grid line in same order, otherwise create line
    * from the min to the max of the other axis, and so on.
    * @return true if grid lines are created.
    */
   @Override
   public boolean createGridLines(DefaultAxis axis, boolean asc) {
      if(!(axis instanceof Axis2D)) {
         return false;
      }

      int style = getScale().getAxisSpec().getGridStyle();

      removeAllGridLines();

      if(style == GraphConstants.NONE) {
         return false;
      }

      Axis2D axis0 = (Axis2D) axis;
      Point2D pos1 = new Point2D.Double(0, 0);
      Point2D pos2 = new Point2D.Double(getLength(), 0);

      pos1 = getScreenTransform().transform(pos1, null);
      pos2 = getScreenTransform().transform(pos2, null);

      boolean isH = pos1.getY() == pos2.getY();
      double[] ticks = getTickLocations(getLength());
      double[] ticks2 = axis0.getTickLocations(axis0.getLength());
      int cnt = Math.min(ticks.length, ticks2.length);

      if(!asc) {
         ticks2 = DefaultAxis.reverse(ticks);
      }

      for(int i = 0; i < cnt; i++) {
         Point2D loc1 = new Point2D.Double(ticks[i], 0);
         Point2D loc2 = new Point2D.Double(ticks2[i], 0);
         loc1 = getScreenTransform().transform(loc1, null);
         loc2 = axis0.getScreenTransform().transform(loc2, null);
         Point2D loc11 = new Point2D.Double(loc1.getX() + depth,
                                            loc1.getY() + depth);
         Point2D loc22 = new Point2D.Double(loc2.getX() + depth,
                                            loc2.getY() + depth);

         Line2D shape = new Line2D.Double(loc11, loc22);
         addGridLine(new GridLine(shape, this, GDefaults.GRIDLINE_Z_INDEX, true));
      }

      return true;
   }

   /**
    * Paint the visual object on the graphics.
    * @param g the graphics context to use for painting.
    */
   @Override
   public void paint(Graphics2D g) {
      if(getZIndex() == -1) {
         return;
      }

      if(getAxisLine() == null || !isLineVisible()) {
         super.paint(g);
         return;
      }

      Graphics2D g2 = (Graphics2D) g.create();
      Point2D pos1 = new Point2D.Double(0, 0);
      Point2D pos2 = new Point2D.Double(getLength(), 0);

      pos1 = getScreenTransform().transform(pos1, null);
      pos2 = getScreenTransform().transform(pos2, null);

      Point2D pos3 = new Point2D.Double(pos2.getX() + depth, pos2.getY()+depth);
      Point2D pos4 = new Point2D.Double(pos1.getX() + depth, pos1.getY()+depth);

      GeneralPath fill = new GeneralPath();
      fill.moveTo((float) pos1.getX(), (float) pos1.getY());
      fill.lineTo((float) pos2.getX(), (float) pos2.getY());
      fill.lineTo((float) pos3.getX(), (float) pos3.getY());
      fill.lineTo((float) pos4.getX(), (float) pos4.getY());
      fill.closePath();

      g2.setColor(FILL_COLOR);
      g2.fill(fill);

      g2.setColor(getLineColor());
      // drawn as coord border (wrong position if rescaled)
      //g2.draw(new Line2D.Double(pos1, pos4));
      g2.draw(new Line2D.Double(pos3, pos4));

      // leave the top/right border to the coord border to draw
      // g2.draw(new Line2D.Double(pos2, pos3));

      g2.dispose();
      super.paint(g);
   }

   /**
    * Set depth.
    */
   public void setDepth(double depth) {
      this.depth = depth;
   }

   /**
    * Get depth.
    * @return the depth.
    */
   public double getDepth() {
      return depth;
   }

   /**
    * Get the X location (chart coordinate) of the ticks.
    */
   private double[] getTickLocations(double width) {
      Scale scale = getScale();
      double[] ticks = scale.getTicks();

      for(int i = 0; i < ticks.length; i++) {
         if(scale instanceof CategoricalScale &&
            ((CategoricalScale) scale).isFill())
         {
            ticks[i] = (ticks[i] - scale.getMin() + 0.5) * width /
               (scale.getMax() - scale.getMin());
         }
         else {
            ticks[i] = (ticks[i] - scale.getMin()) * width /
               (scale.getMax() - scale.getMin());
         }
      }

      return ticks;
   }

   /**
    * Fill color for axis area.
    */
   public static final Color FILL_COLOR = new Color(237, 237, 237); //#ededed

   private double depth = 15;
}
