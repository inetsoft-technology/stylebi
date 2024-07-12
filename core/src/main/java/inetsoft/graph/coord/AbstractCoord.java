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

import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.SubDataSet;
import inetsoft.graph.geo.GeoDataSet;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.internal.ICoordinate;
import inetsoft.graph.scale.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.Map;

/**
 * This is the base class that implements the common methods of Coordinate.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class AbstractCoord implements ICoordinate {
   /**
    * Get all axes in this coordinate.
    *
    * @param recursive true to include axes in nested coordinates.
    */
   public abstract Axis[] getAxes(boolean recursive);

   /**
    * This method is called after VGraph.layout has finished. It should release any
    * cached information used by layout to free up memory.
    */
   public void layoutCompleted() {
      sharedScales.clear();
      coordCache.clear();
   }

   /**
    * Set size for laying out graph. This is used for calculating layout and constraints
    * based on size. For example, geo map may need to calculate the range of lat/lon allowed
    * to be valid with web map services.
    */
   @TernMethod
   public void setLayoutSize(Dimension2D size) {
      this.asize = size;
   }

   /**
    * Get size for laying out legend and axis.
    */
   @TernMethod
   public Dimension2D getLayoutSize() {
      return asize;
   }

   /**
    * Get the bounds for the coordinate area.
    */
   @Override
   @TernMethod
   public Rectangle2D getCoordBounds() {
      return cbounds;
   }

   /**
    * Set the bounds for the coordinate area.
    */
   @Override
   @TernMethod
   public void setCoordBounds(Rectangle2D bounds) {
      this.cbounds = bounds;

      // need to recalculate size
      for(Axis axis : getAxes(true)) {
         axis.invalidate();
      }
   }

   /**
    * Check if the axis label should be shown.
    *
    * @param axis the axis constant defined in Coordinate.
    *
    * @hidden
    */
   @Override
   public boolean isAxisLabelVisible(int axis) {
      return (axisVis & (1 << axis)) != 0;
   }

   /**
    * Set if the axis label should be shown.
    * This is called by graph engine. External caller should use AxisSpec to
    * control the visibility.
    *
    * @param axis the axis constant defined in Coordinate.
    *
    * @hidden
    */
   @Override
   public void setAxisLabelVisible(int axis, boolean vis) {
      if(vis) {
         axisVis = axisVis | (1 << axis);
      }
      else {
         axisVis = axisVis & ~(1 << axis);
      }
   }

   /**
    * Check if the axis tick should be shown.
    *
    * @param axis the axis constant defined in Coordinate.
    *
    * @hidden
    */
   @Override
   public boolean isAxisTickVisible(int axis) {
      return (tickVis & (1 << axis)) != 0;
   }

   /**
    * Set if the axis tick should be shown.
    * This is called by graph engine. External caller should use AxisSpec to
    * control the visibility.
    *
    * @param axis the axis constant defined in Coordinate.
    *
    * @hidden
    */
   @Override
   public void setAxisTickVisible(int axis, boolean vis) {
      if(vis) {
         tickVis = tickVis | (1 << axis);
      }
      else {
         tickVis = tickVis & ~(1 << axis);
      }
   }

   /**
    * @hidden
    */
   @Override
   public void copyAxisVisibility(ICoordinate coord) {
      if(coord instanceof AbstractCoord) {
         this.axisVis = ((AbstractCoord) coord).axisVis;
         this.tickVis = ((AbstractCoord) coord).tickVis;
      }
   }

   /**
    * @hidden
    */
   protected void addSharedScale(Scale scale, DataSet xset) {
      if(scale instanceof CategoricalScale || scale instanceof TimeScale) {
         Object key = getScaleKey(scale, xset);

         if(key != null) {
            sharedScales.put(key, scale);
         }
      }
   }

   /**
    * @hidden
    */
   protected Scale getSharedScale(Scale scale, DataSet xset) {
      Object key = getScaleKey(scale, xset);
      return key != null ? sharedScales.get(key) : null;
   }

   private static String getScaleKey(Scale scale, DataSet dset) {
      Object dataKey = null;

      if(dset instanceof GeoDataSet) {
         dset = ((GeoDataSet) dset).getDataSet();
      }

      if(dset instanceof SubDataSet) {
         dataKey = ((SubDataSet) dset).getConditionKey();
      }

      return dataKey == null ? null
         : scale.getClass() + "." + Arrays.toString(scale.getFields()) + dataKey;
   }

   /**
    * A cached that can be used by processing for this coordinate. It's cleared at the end
    * of layout (layoutCompleted).
    * @hidden
    */
   public Map getCoordCache() {
      return coordCache;
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone coordinates", ex);
      }

      return null;
   }

   private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
      in.defaultReadObject();
      sharedScales = new Object2ObjectOpenHashMap<>();
      coordCache = new Object2ObjectOpenHashMap();
   }

   private static final long serialVersionUID = 1L;

   private int axisVis = 0xFFFFFF; // axis label visibility
   private int tickVis = 0xFFFFFF; // axis tick visibility
   private Rectangle2D cbounds; // coordinate bounds
   private transient Map<Object, Scale> sharedScales = new Object2ObjectOpenHashMap<>();
   private transient Map coordCache = new Object2ObjectOpenHashMap();
   private transient Dimension2D asize; // available size

   private static final Logger LOG = LoggerFactory.getLogger(AbstractCoord.class);
}
