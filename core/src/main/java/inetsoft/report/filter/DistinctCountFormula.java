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
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.util.*;

/**
 * Count the distinct occurrence of items. Same values are only counted
 * once.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class DistinctCountFormula implements PercentageFormula {
   private static final Double ZERO_DOUBLE = 0.0;
   private static final Float ZERO_FLOAT = 0.0F;

   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      set.clear();
   }

   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
      if(v == null) {
         return;
      }

      set.add(normalize(v));
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double v) {
      if(v == Tool.NULL_DOUBLE) {
         return;
      }

      // 0.0 == -0.0 is true, so this maps -0.0 onto 0.0 without a branch on the sign bit
      set.add(v == 0 ? 0.0 : v);
   }

   /**
    * Add double values to the formula.
    */
   @Override
   public void addValue(double[] v) {
      if(v != null && v.length > 0) {
         addValue(v[0]);
      }
   }

   /**
    * Add a float value to the formula.
    */
   @Override
   public void addValue(float v) {
      if(v == Tool.NULL_FLOAT) {
         return;
      }

      set.add(v == 0 ? 0.0F : v);
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
      if(v == Tool.NULL_LONG) {
         return;
      }

      set.add(v);
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
      if(v == Tool.NULL_INTEGER) {
         return;
      }

      set.add(v);
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
      if(v == Tool.NULL_SHORT) {
         return;
      }

      set.add(v);
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
    * Get the formula result.
    */
   @Override
   public Object getResult() {
      int douResult = set.size();

      if(percentageType != 0 && total instanceof Number) {
         double totalNum = ((Number) total).doubleValue();

         if(totalNum == 0) {
            return def ? (double) 0 : null;
         }

         return douResult / totalNum;
      }

      return douResult;
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      double douResult = set.size();

      if((percentageType != 0) && (total instanceof Number)) {
         double totalNum = ((Number) total).doubleValue();

         if(totalNum == 0) {
            return 0;
         }

         return douResult / totalNum;
      }

      return douResult;
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return false;
   }

   @Override
   public Object clone() {
      try {
         DistinctCountFormula dcf = (DistinctCountFormula) super.clone();
         dcf.set = Collections.newSetFromMap(new Hashtable<>());
         dcf.set.addAll(set);
         return dcf;
      }
      catch(CloneNotSupportedException ex) {
         return this;
      }
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
      return Catalog.getCatalog().getString("DistinctCount");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.DISTINCTCOUNT_FORMULA;
   }

   @Override
   public Class<Integer> getResultType() {
      return Integer.class;
   }

   /**
    * Map a value to the key that represents it in the set, so values that are equal by
    * value are one key regardless of Java equals() asymmetries or insertion order:
    * -0.0 and 0.0 (Double.equals/Float.equals distinguish them), and a Timestamp and a
    * Date of the same instant (Timestamp.equals(Date) is false, Date.equals(Timestamp) is
    * true). Everything else, including strings and ordinary numbers, is returned as is.
    */
   private static Object normalize(Object v) {
      Class<?> cls = v.getClass();

      if(cls == String.class || cls == Integer.class || cls == Long.class) {
         return v;
      }

      if(cls == Double.class) {
         return ((Double) v).doubleValue() == 0 ? ZERO_DOUBLE : v;
      }

      if(cls == Float.class) {
         return ((Float) v).floatValue() == 0 ? ZERO_FLOAT : v;
      }

      if(v instanceof java.sql.Timestamp) {
         java.sql.Timestamp ts = (java.sql.Timestamp) v;
         int subMillis = ts.getNanos() % 1_000_000;
         // whole-millisecond timestamps equal the plain Date of that instant
         return subMillis == 0 ? new Date(ts.getTime()) : new SubMillisKey(ts.getTime(), subMillis);
      }

      return v;
   }

   /**
    * Key of a Timestamp with sub-millisecond precision. It is distinct from every Date
    * and from Timestamps of other nanos, and symmetric in equals.
    */
   private static final class SubMillisKey implements java.io.Serializable {
      SubMillisKey(long time, int nanos) {
         this.time = time;
         this.nanos = nanos;
      }

      @Override
      public boolean equals(Object obj) {
         return obj instanceof SubMillisKey && ((SubMillisKey) obj).time == time &&
            ((SubMillisKey) obj).nanos == nanos;
      }

      @Override
      public int hashCode() {
         return Long.hashCode(time) * 31 + nanos;
      }

      private final long time;
      private final int nanos;
   }

   private short percentageType = (short) StyleConstants.PERCENTAGE_NONE;
   private Object total = null;
   private Set<Object> set = Collections.newSetFromMap(new Hashtable<>());
   private boolean def;
}
