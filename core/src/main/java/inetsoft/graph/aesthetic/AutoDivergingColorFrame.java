/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.graph.aesthetic;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.scale.*;
import inetsoft.graph.visual.ElementVO;

import java.awt.*;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * A Teal that draws the diverging ramp, centred on zero, when its data crosses zero. The decision
 * is made on every init, so a rebuilt scale is decided again.
 *
 * @version 15.0
 * @author InetSoft Technology
 */
public class AutoDivergingColorFrame extends TealColorFrame {
   /**
    * Create a frame with the field, legend spec, scale option and scale of a Teal.
    */
   public static AutoDivergingColorFrame from(TealColorFrame teal) {
      AutoDivergingColorFrame frame = new AutoDivergingColorFrame();
      frame.setField(teal.getField());
      frame.setLegendSpec(teal.getLegendSpec());
      frame.setScaleOption(teal.getScaleOption());

      if(teal.getScale() != null) {
         frame.setScale(teal.getScale().clone());
      }

      return frame;
   }

   /**
    * Check if the frame draws the diverging ramp.
    */
   public boolean isDiverging() {
      return diverging;
   }

   /**
    * Get the ramp position where a non-diverging frame starts.
    */
   public double getLowStop() {
      return lowStop;
   }

   /**
    * Set the ramp position where a non-diverging frame starts.
    */
   public void setLowStop(double lowStop) {
      this.lowStop = lowStop;
   }

   /**
    * Get the data whose field values decide the sign, or null.
    */
   public DataSet getSignData() {
      return signData;
   }

   /**
    * Set the data whose field values decide the sign, such as all data under brushing. The
    * scale keeps the range of the data the frame is initialised with.
    */
   public void setSignData(DataSet signData) {
      this.signData = signData;
   }

   @Override
   public void init(DataSet data) {
      super.init(data);
      Scale scale = getScale();
      boolean diverge = false;

      if(scale instanceof LinearScale && !(scale instanceof LogScale)) {
         double[] range = getSignRange(data, scale);
         diverge = range[0] < 0 && range[1] > 0;

         if(diverge) {
            LinearScale linear = (LinearScale) scale;
            double m = Math.max(Math.abs(scale.getMin()), Math.abs(scale.getMax()));
            // a reversed scale swaps what setMin and setMax address
            linear.setMin(linear.isReversed() ? m : -m);
            linear.setMax(linear.isReversed() ? -m : m);
         }
      }

      if(diverge != diverging || !colorsSet) {
         setColors(parse(diverge ? new VarianceColorFrame().getColorRamps()[0] :
                            getColorRamps()[0]));
         colorsSet = true;
      }

      diverging = diverge;
   }

   @Override
   public Color getColor(double ratio) {
      if(!diverging && lowStop > 0) {
         ratio = lowStop + (1 - lowStop) * ratio;
      }

      return super.getColor(ratio);
   }

   @Override
   public boolean equals(Object obj) {
      return super.equals(obj) && ((AutoDivergingColorFrame) obj).diverging == diverging &&
         Double.compare(((AutoDivergingColorFrame) obj).lowStop, lowStop) == 0;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), diverging, lowStop);
   }

   // the raw range of the field in the sign data, else of the all-data column, else the scale's
   private double[] getSignRange(DataSet data, Scale scale) {
      String field = getField();

      if(field != null && signData != null && signData.indexOfHeader(field) >= 0) {
         return getRawRange(signData, field);
      }

      String col = field == null ? null : ElementVO.ALL_PREFIX + field;

      if(data != null && col != null && data.indexOfHeader(col) >= 0) {
         return getRawRange(data, col);
      }

      double a = scale.getMin();
      double b = scale.getMax();
      return new double[] { Math.min(a, b), Math.max(a, b) };
   }

   private static double[] getRawRange(DataSet data, String col) {
      double min = Double.POSITIVE_INFINITY;
      double max = Double.NEGATIVE_INFINITY;

      for(int i = 0; i < data.getRowCount(); i++) {
         Object val = data.getData(col, i);

         if(val instanceof Number && !Double.isNaN(((Number) val).doubleValue())) {
            min = Math.min(min, ((Number) val).doubleValue());
            max = Math.max(max, ((Number) val).doubleValue());
         }
      }

      return new double[] { min, max };
   }

   private static Color[] parse(String s) {
      return IntStream.range(0, s.length() / 6)
         .mapToObj(i -> new Color(Integer.parseInt(s.substring(i * 6, (i + 1) * 6), 16)))
         .toArray(Color[]::new);
   }

   private boolean diverging = false;
   private boolean colorsSet = false;
   private double lowStop = 0;
   private transient DataSet signData; // render state, not part of equality
   private static final long serialVersionUID = 1L;
}
