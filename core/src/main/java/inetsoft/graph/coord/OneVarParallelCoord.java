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

import inetsoft.graph.data.DataSet;
import inetsoft.graph.scale.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.*;

/**
 * A parallel coord that creates axes from a single var by grouping values on a separate
 * dimension.
 *
 * @version 13.4
 * @author InetSoft Technology Corp
 */
public class OneVarParallelCoord extends AbstractParallelCoord {
   /**
    * Default constructor.
    */
   public OneVarParallelCoord() {
   }

   /**
    * Create a parallel coord.
    */
   public OneVarParallelCoord(String var, String dim, Scale varScale) {
      this();
      this.pscale = new MultiScale(var, dim, varScale);
   }

   @Override
   public void init(DataSet dset) {
      this.pscale.init(dset);

      if(pscale instanceof MultiScale) {
         subscales = ((MultiScale) pscale).createScales(dset);
      }
   }

   @Override
   public Scale[] getParallelScales() {
      return subscales;
   }

   @Override
   public Scale[] getScales() {
      Scale[] arr = new Scale[subscales.length + 1];
      arr[0] = pscale;
      System.arraycopy(subscales, 0, arr, 1, subscales.length);
      return arr;
   }

   @Override
   public Point2D getPosition(double[] tuple) {
      Scale[] scales = this.subscales;
      double w = GWIDTH / scales.length;
      int idx = (int) tuple[tuple.length - 2];

      if(idx >= scales.length) {
         throw new RuntimeException("Element not supported by ParallelCoord: " +
                                       idx + " >= " + scales.length);
      }
      else if(idx < 0) {
         LOG.info("Parallel scale index is -1: " + Arrays.toString(tuple));
         return null;
      }

      double y = getPosition(scales[idx], tuple[tuple.length - 1], GHEIGHT);
      double x = (idx + 0.5) * w;

      return new Point2D.Double(x, y);
   }

   @Override
   public int getDimCount() {
      return 1;
   }

   /**
    * Get the scale for the axes labels.
    */
   @Override
   public Scale getAxisLabelScale() {
      return pscale;
   }

   @Override
   public OneVarParallelCoord clone(boolean srange) {
      OneVarParallelCoord coord = (OneVarParallelCoord) super.clone(srange);

      coord.pscale = pscale.clone();
      coord.pscale.setAxisSpec(pscale.getAxisSpec());
      coord.subscales = subscales.clone();

      for(int i = 0; i < subscales.length; i++) {
         if(subscales[i] instanceof LinearScale) {
            coord.subscales[i] = ((LinearScale) subscales[i]).clone(srange);
         }
         else {
            coord.subscales[i] = subscales[i].clone();
         }
      }

      return coord;
   }

   private static class MultiScale extends CategoricalScale  {
      public MultiScale(String var, String dim, Scale varScale) {
         super(dim);
         this.var = var;
         this.varScale = varScale;
      }

      public LinearScale[] createScales(DataSet data) {
         super.init(data);
         Map<Object, LinearScale> scales = new HashMap<>();

         for(Object dimVal : getValues()) {
            if(dimVal == null && (getScaleOption() & Scale.NO_NULL) != 0) {
               continue;
            }

            Scale scale = varScale instanceof LinearScale
               ? ((LinearScale) varScale).copyLinearScale() : new LinearScale(var);
            scales.put(dimVal, (LinearScale) scale);
         }

         for(Object dimVal : scales.keySet()) {
            // should share scale for parallel axes
            //Map<String, Object> conds = new HashMap<>();
            //conds.put(getFields()[0], dimVal);
            //scales.get(dimVal).init(new SubDataSet(data, conds));
            scales.get(dimVal).init(data);
         }

         return scales.values().toArray(new LinearScale[0]);
      }

      private String var;
      private Scale varScale;
   }

   private transient Scale pscale;
   private transient Scale[] subscales;
   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(OneVarParallelCoord.class);
}
