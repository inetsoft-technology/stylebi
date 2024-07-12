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
package inetsoft.graph.internal;

import inetsoft.graph.VGraph;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.scale.Scale;

import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.function.Predicate;

/**
 * ICoordinate, the object defines the common functions for coordinate.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public interface ICoordinate extends Cloneable, Serializable {
   /**
    * Top axis.
    */
   public static final int TOP_AXIS = 0;
   /**
    * Left axis.
    */
   public static final int LEFT_AXIS = 1;
   /**
    * Bottom axis.
    */
   public static final int BOTTOM_AXIS = 2;
   /**
    * Right axis.
    */
   public static final int RIGHT_AXIS = 4;

   /**
    * Left most.
    * @hidden
    */
   public static final int LEFT_MOST = 1;
   /**
    * Right most.
    * @hidden
    */
   public static final int RIGHT_MOST = 2;
   /**
    * Top most.
    * @hidden
    */
   public static final int TOP_MOST = 4;
   /**
    * Bottom most.
    * @hidden
    */
   public static final int BOTTOM_MOST = 8;
   /**
    * All most.
    * @hidden
    */
   public static final int ALL_MOST =
      LEFT_MOST | RIGHT_MOST | TOP_MOST | BOTTOM_MOST;

   /**
    * Get all axes at specified position, e.g. TOP_AXIS.
    * @hidden
    */
   public DefaultAxis[] getAxesAt(int axis);

   /**
    * Check if any axis matches the predicate. This is a stream-like version of getAxesAt().
    */
   public boolean anyAxisAt(int axis, Predicate<Axis> func);

   /**
    * Get the parent vgraph.
    */
   public VGraph getVGraph();

   /**
    * Set the parent vgraph.
    */
   public void setVGraph(VGraph graph);

   /**
    * Check if the axis label should be shown.
    * @hidden
    */
   public boolean isAxisLabelVisible(int axis);

   /**
    * Set if the axis label should be shown.
    * @hidden
    */
   public void setAxisLabelVisible(int axis, boolean vis);

   /**
    * Copy axis label/tick visibility.
    * @hidden
    */
   public void copyAxisVisibility(ICoordinate coord);

   /**
    * Check if the axis tick should be shown.
    * @hidden
    */
   public boolean isAxisTickVisible(int axis);

   /**
    * Set if the axis tick should be shown.
    * @hidden
    */
   public void setAxisTickVisible(int axis, boolean vis);

   /**
    * Get the number of dimensions in this coordinate.
    */
   public int getDimCount();

   /**
    * Get the scales used in the coordinate.
    */
   public Scale[] getScales();

   /**
    * Get unit min width.
    */
   public double getUnitMinWidth();

   /**
    * Get unit min height.
    */
   public double getUnitMinHeight();

   /**
    * Get unit preferred width.
    */
   public double getUnitPreferredWidth();

   /**
    * Get unit preferred height.
    */
   public double getUnitPreferredHeight();

   /**
    * Get top axis min height.
    * @hidden
    */
   public double getAxisMinSize(int axis);

   /**
    * Get top axis preferred height.
    * @hidden
    */
   public double getAxisPreferredSize(int axis);

   /**
    * Get the height of top axis.
    * @hidden
    */
   public double getAxisSize(int axis);

   /**
    * Set the height of top axis.
    * @hidden
    */
   public void setAxisSize(int axis, double val);

   /**
    * Get the bounds for the coordinate area.
    */
   public Rectangle2D getCoordBounds();

   /**
    * Set the bounds for the coordinate area.
    */
   public void setCoordBounds(Rectangle2D bounds);

   /**
    * Make a copy of this object.
    * @return an Object that is a copy of this object.
    */
   public Object clone();

   /**
    * Check if the VGraph needs to be re-generated with the coordinate bounds set.
    */
   default boolean requiresReplot() {
      return false;
   }
}
