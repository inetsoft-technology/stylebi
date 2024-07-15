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
package inetsoft.report.filter;

import inetsoft.report.StyleConstants;
import inetsoft.uql.XConstants;
import inetsoft.util.*;
import inetsoft.util.swap.XDoubleList;

/**
 * Calculate the sample variance.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class VarianceFormula implements PercentageFormula {
   public VarianceFormula() {
   }

   VarianceFormula(boolean population) {
      sample = population ? 0 : 1;
   }

   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      list.clear();
   }

   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
      if(v == null) {
         return;
      }

      try {
         double dval = (v instanceof Number) ? ((Number) v).doubleValue()
            : NumberParserWrapper.getDouble(v.toString());
         list.add(dval);
      }
      catch(NumberFormatException e) {
      }
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double v) {
      if(v == Tool.NULL_DOUBLE) {
         return;
      }

      list.add(v);
   }

   /**
    * Add double values to the formula.
    */
   @Override
   public void addValue(double[] v) {
      if(v[0] == Tool.NULL_DOUBLE) {
         return;
      }

      list.add(v[0]);
   }

   /**
    * Add a float value to the formula.
    */
   @Override
   public void addValue(float v) {
      if(v == Tool.NULL_FLOAT) {
         return;
      }

      list.add(v);
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
      if(v == Tool.NULL_LONG) {
         return;
      }

      list.add(v);
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
      if(v == Tool.NULL_INTEGER) {
         return;
      }

      list.add(v);
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
      if(v == Tool.NULL_SHORT) {
         return;
      }

      list.add(v);
   }

   /**
    * Get the formula result.
    */
   @Override
   public Object getResult() {
      if(list.size() < 1) {
         return def ? Double.valueOf(0) : null;
      }

      double douResult = getRawResult();

      if(percentageType != 0 && XConstants.VARIANCE_FORMULA.equals(getName())) {
         return getPercentage(douResult);
      }

      return douResult;
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      double douResult = getRawResult();

      if(percentageType != 0 && XConstants.VARIANCE_FORMULA.equals(getName())) {
         Double percentage = getPercentage(douResult);
         return percentage == null ? 0 : percentage.doubleValue();
      }

      return douResult;
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return list.size() == 0;
   }

   /**
    * Get the raw formula result.
    */
   protected double getRawResult() {
      double n, rTotal = 0;
      double[] varr = list.getArray();
      int count = list.size();

      if(count <= 1) {
         return 0;
      }

      for(int i = 0; i < count; i++) {
         rTotal += varr[i];
      }

      double avg = rTotal / count;
      rTotal = 0;

      for(int i = 0; i < count; i++) {
         n = varr[i] - avg;
         rTotal += n * n;
      }

      return rTotal / (count - sample);
   }

   /**
    * Set the default result option of this formula.
    * @param def <tt>true</tt> to use the default value of a formula if no
    * result, <tt>false</tt> to just return null.
    */
   @Override
   public void setDefaultResult(boolean def) {
      this.def = def;
   }

   /**
    * Get the default result option of this formula.
    * @return <tt>true</tt> to use the default value of a formula if no
    * result, <tt>false</tt> to just return null.
    */
   @Override
   public boolean isDefaultResult() {
      return def;
   }

   /**
    * Get percentage.
    */
   public Double getPercentage(double dou) {
      double totalNum = 0;

      try {
         Object total = getTotal();

         totalNum =(total instanceof Number) ? ((Number) total).doubleValue() :
            NumberParserWrapper.getDouble(total.toString());

         if(totalNum <= 0) {
            return null;
         }

         return Double.valueOf(dou / totalNum);
      }
      catch(Exception e) {
         return null;
      }
   }

   @Override
   public Object clone() {
      VarianceFormula vf = createVarianceFormula();
      vf.def = def;

      for(int i = 0; i < list.size(); i++) {
         vf.addValue(list.get(i));
      }

      vf.setPercentageType(getPercentageType());
      vf.setTotal(total);

      return vf;
   }

   protected VarianceFormula createVarianceFormula() {
      return new VarianceFormula();
   }

   /**
    * Get percentage type.
    */
   @Override
   public int getPercentageType() {
      return percentageType;
   }

   /**
    * Set percentage type.
    * three types: StyleConstants.PERCENTAGE_NONE,
    *              StyleConstants.PERCENTAGE_OF_GROUP,
    *              StyleConstants.PERCENTAGE_OF_GRANDTOTAL.
    */
   @Override
   public void setPercentageType(int percentageType) {
      this.percentageType = (short) percentageType;
   }

   /**
    * Set the total used to calculate percentage.
    * if percentage type is PERCENTAGE_NONE, it is ineffective to
    * invoke the method.
    */
   @Override
   public void setTotal(Object total) {
      this.total = total;
   }

   /**
    * Get the total used to calculate percentage.
    */
   public Object getTotal() {
      return total;
   }

   /**
    * Get the original formula result without percentage.
    */
   @Override
   public Object getOriginalResult() {
      int perType = getPercentageType();
      setPercentageType(StyleConstants.PERCENTAGE_NONE);
      Object oresult = getResult();
      setPercentageType(perType);

      return oresult;
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("Variance");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.VARIANCE_FORMULA;
   }

   @Override
   public Class getResultType() {
      return Double.class;
   }

   private short percentageType = (short) StyleConstants.PERCENTAGE_NONE;
   private Object total = null;
   private XDoubleList list = new XDoubleList();
   private int sample = 1;
   private boolean def;
}
