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

import com.inetsoft.build.tern.*;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.*;

import java.awt.geom.Point2D;

/**
 * A parallel coord consists of a set of parallel vertical axes that
 * plot data points on the axes.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=ParallelCoord")
public class ParallelCoord extends AbstractParallelCoord {
   /**
    * Default constructor.
    */
   public ParallelCoord() {
      pscale = new ParallelScale();
   }

   /**
    * Create a parallel coord.
    */
   @TernConstructor
   public ParallelCoord(Scale... scales) {
      this();
      setScales(scales);
   }

   @TernMethod
   public Scale[] getParallelScales() {
      return scales;
   }

   /**
    * Set the scales for this coord.
    */
   @TernMethod
   public void setScales(Scale... scales) {
      this.scales = scales;

      if(scales.length < 2) {
         throw new RuntimeException(
            GTool.getString("viewer.viewsheet.alert.radar"));
      }

      pscale.initValues();
   }

   /**
    * Map a tuple (from logic coordinate space) to the chart coordinate space.
    * @param tuple the tuple in logic space (scaled values).
    */
   @TernMethod
   @Override
   public Point2D getPosition(double[] tuple) {
      Scale[] scales = getScales();
      double x = Double.NaN;
      double y = Double.NaN;
      double w = GWIDTH / scales.length;

      if(scales.length > tuple.length) {
         throw new RuntimeException("Element not supported by ParallelCoord.");
      }

      for(int i = 0; i < scales.length; i++) {
         if(!Double.isNaN(tuple[i])) {
            y = getPosition(scales[i], tuple[i], GHEIGHT);
            x = (i + 0.5) * w;
            break;
         }
      }

      return new Point2D.Double(x, y);
   }

   /**
    * Get the number of dimensions in this coordinate.
    */
   @Override
   @TernMethod
   public int getDimCount() {
      return getScales().length;
   }

   /**
    * Get the scale for the axes labels.
    */
   @Override
   @TernMethod
   public Scale getAxisLabelScale() {
      return pscale;
   }

   /**
    * Scale used to add labels to the parallel axes.
    */
   private class ParallelScale extends CategoricalScale {
      public ParallelScale() {
         super("_Parallel_Label_");
         getAxisSpec().setLineVisible(false);
         getAxisSpec().setTickVisible(false);
      }

      public void initValues() {
         Scale[] scales = getScales();
         Object[] values = new Object[scales.length];

         for(int i = 0; i < values.length; i++) {
            values[i] = scales[i].getFields()[0];
         }

         init(values);
      }

      @Override
      public double[] getTicks() {
         double[] ticks = new double[getValues().length];

         for(int i = 0; i < ticks.length; i++) {
            ticks[i] = i + 0.5;
         }

         return ticks;
      }

      @Override
      public double getMax() {
         return getValues().length;
      }
   }

   @Override
   public ParallelCoord clone(boolean srange) {
      ParallelCoord coord = (ParallelCoord) super.clone(srange);

      coord.pscale = (ParallelCoord.ParallelScale) pscale.clone();
      coord.pscale.setAxisSpec(pscale.getAxisSpec());
      coord.scales = scales.clone();

      for(int i = 0; i < scales.length; i++) {
         if(scales[i] instanceof LinearScale) {
            coord.scales[i] = ((LinearScale) scales[i]).clone(srange);
         }
         else {
            coord.scales[i] = scales[i].clone();
         }
      }

      return coord;
   }

   private transient ParallelScale pscale;
   private Scale[] scales = {};
   private static final long serialVersionUID = 1L;
}
