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
package inetsoft.util;

import inetsoft.report.Comparer;

import java.util.Date;

/**
 * DataComparer compares two objects.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class DataComparer {
   /**
    * Ignored values.
    */
   public static final int IGNORED = Integer.MIN_VALUE + 1;

   /**
    * String comparer.
    */
   public static final Comparer STRING_COMPARER = new StringComparer();
   /**
    * Date comparer.
    */
   public static final Comparer DATE_COMPARER = new DateComparer();
   /**
    * Number comparer.
    */
   public static final Comparer NUM_COMPARER = new NumberComparer();
   /**
    * Boolean comparer.
    */
   public static final Comparer BOOL_COMPARER = new BooleanComparer();
   /**
    * Default comparer.
    */
   public static final Comparer DEFAULT_COMPARER = new DefaultComparer();
   /**
    * Char comparer.
    */
   public static final Comparer CHAR_COMPARER = new CharacterComparer();

   /**
    * This method should return > 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or < 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return < 0, 0, or > 0 for v1 < v2, v1 == v2, or v1 > v2.
    */
   public static final int compare(double v1, double v2) {
      if(v1 == Tool.NULL_DOUBLE || v2 == Tool.NULL_DOUBLE) {
         return v1 == v2 ? 0 : (v1 == Tool.NULL_DOUBLE ? -1 : 1);
      }

      double val = v1 - v2;

      if(val < NumberComparer.NEGATIVE_ERROR) {
         return -1;
      }
      else if(val > NumberComparer.POSITIVE_ERROR) {
         return 1;
      }
      else {
         return 0;
      }
   }

   /**
    * Get the data comparer according to a class.
    * @param type the specified class.
    * @return the corresponding data comparer.
    */
   public static final Comparer getDataComparer(Class type) {
      if(type == String.class) {
         return STRING_COMPARER;
      }
      else if(type == Character.class) {
         return CHAR_COMPARER;
      }
      else if(type == Boolean.class) {
         return BOOL_COMPARER;
      }
      else if(Number.class.isAssignableFrom(type)) {
         if(type == java.math.BigDecimal.class) {
            return NUM_COMPARER;
         }
         else if(type == java.math.BigInteger.class) {
            return NUM_COMPARER;
         }
         else if(type == Double.class) {
            return NUM_COMPARER;
         }
         else if(type == Float.class) {
            return NUM_COMPARER;
         }
         else if(type == Long.class) {
            return NUM_COMPARER;
         }
         else if(type == Short.class) {
            return NUM_COMPARER;
         }
         else if(type == Byte.class) {
            return NUM_COMPARER;
         }
         else {
            return NUM_COMPARER;
         }
      }
      else if(java.sql.Date.class.isAssignableFrom(type)) {
         return DATE_COMPARER;
      }
      else if(java.sql.Time.class.isAssignableFrom(type)) {
         return DATE_COMPARER;
      }
      else if(java.sql.Timestamp.class.isAssignableFrom(type)) {
         return DATE_COMPARER;
      }
      else if(java.util.Date.class.isAssignableFrom(type)) {
         return DATE_COMPARER;
      }
      else {
         return DEFAULT_COMPARER;
      }
   }

   /**
    * Default comparer.
    */
   public static final class DefaultComparer implements Comparer {
      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(Object v1, Object v2) {
         return IGNORED;
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(double v1, double v2) {
         return IGNORED;
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(float v1, float v2) {
         return IGNORED;
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(long v1, long v2) {
         return IGNORED;
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(int v1, int v2) {
         return IGNORED;
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(short v1, short v2) {
         return IGNORED;
      }
   }

   /**
    * String comparer.
    */
   public static final class StringComparer implements Comparer {
      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(Object v1, Object v2) {
         if(v1 == null) {
            return v2 == null ? 0 : -1;
         }
         else if(v2 == null) {
            return 1;
         }

         String obj1 = v1.toString().trim();
         String obj2 = v2.toString().trim();

         if(Tool.isCaseSensitive()) {
            return obj1.compareTo(obj2);
         }
         else {
            obj1 = obj1.toLowerCase();
            obj2 = obj2.toLowerCase();

            return obj1.compareTo(obj2);
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(double v1, double v2) {
         if(v1 == Tool.NULL_DOUBLE || v2 == Tool.NULL_DOUBLE) {
            return v1 == v2 ? 0 : (v1 == Tool.NULL_DOUBLE ? -1 : 1);
         }

         double val = v1 - v2;

         if(val < NEGATIVE_DOUBLE_ERROR) {
            return -1;
         }
         else if(val > POSITIVE_DOUBLE_ERROR) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(float v1, float v2) {
         if(v1 == Tool.NULL_FLOAT || v2 == Tool.NULL_FLOAT) {
            return v1 == v2 ? 0 : (v1 == Tool.NULL_FLOAT ? -1 : 1);
         }

         float val = v1 - v2;

         if(val < NEGATIVE_FLOAT_ERROR) {
            return -1;
         }
         else if(val > POSITIVE_FLOAT_ERROR) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(long v1, long v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(int v1, int v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(short v1, short v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }
   }

   /**
    * Number comparer.
    */
   public static final class NumberComparer implements Comparer {
      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(Object v1, Object v2) {
         if(v1 == null) {
            return v2 == null ? 0 : -1;
         }
         else if(v2 == null) {
            return 1;
         }

         String type = Tool.DOUBLE;
         boolean dbl = true;

         if(v1 instanceof Long || v2 instanceof Long) {
            type = Tool.LONG;
            dbl = false;
         }

         Number obj1 = v1 instanceof Number ? (Number) v1 :
            (Number) Tool.getData(type, v1);
         Number obj2 = v2 instanceof Number ? (Number) v2 :
            (Number) Tool.getData(type, v2);
         int res = 0;

         if(dbl) {
            Double d1 = Double.valueOf(obj1.doubleValue());
            Double d2 = Double.valueOf(obj2.doubleValue());
            res = d1.compareTo(d2);
         }
         else {
            Long d1 = Long.valueOf(obj1.longValue());
            Long d2 = Long.valueOf(obj2.longValue());
            res = d1.compareTo(d2);
         }

         if(res < 0) {
            return -1;
         }
         else if(res > 0) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(double v1, double v2) {
         if(v1 == Tool.NULL_DOUBLE || v2 == Tool.NULL_DOUBLE) {
            return v1 == v2 ? 0 : (v1 == Tool.NULL_DOUBLE ? -1 : 1);
         }

         double val = v1 - v2;

         if(val < NEGATIVE_DOUBLE_ERROR) {
            return -1;
         }
         else if(val > POSITIVE_DOUBLE_ERROR) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(float v1, float v2) {
         if(v1 == Tool.NULL_FLOAT || v2 == Tool.NULL_FLOAT) {
            return v1 == v2 ? 0 : (v1 == Tool.NULL_FLOAT ? -1 : 1);
         }

         float val = v1 - v2;

         if(val < NEGATIVE_FLOAT_ERROR) {
            return -1;
         }
         else if(val > POSITIVE_FLOAT_ERROR) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(long v1, long v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(int v1, int v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(short v1, short v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }

      static final double NEGATIVE_ERROR = -0.000001;
      static final double POSITIVE_ERROR = 0.000001;
   }

   /**
    * Date comparer.
    */
   public static final class DateComparer implements Comparer {
      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(Object v1, Object v2) {
         if(v1 == null) {
            return v2 == null ? 0 : -1;
         }
         else if(v2 == null) {
            return 1;
         }

         Date obj1 = (Date) v1;
         Date obj2 = (Date) v2;
         long t1 = obj1.getTime();
         long t2 = obj2.getTime();

         if(t1 == t2) {
            return 0;
         }

         return t1 > t2 ?  1 : -1;
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(double v1, double v2) {
         if(v1 == Tool.NULL_DOUBLE || v2 == Tool.NULL_DOUBLE) {
            return v1 == v2 ? 0 : (v1 == Tool.NULL_DOUBLE ? -1 : 1);
         }

         double val = v1 - v2;

         if(val < NEGATIVE_DOUBLE_ERROR) {
            return -1;
         }
         else if(val > POSITIVE_DOUBLE_ERROR) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(float v1, float v2) {
         if(v1 == Tool.NULL_FLOAT || v2 == Tool.NULL_FLOAT) {
            return v1 == v2 ? 0 : (v1 == Tool.NULL_FLOAT ? -1 : 1);
         }

         float val = v1 - v2;

         if(val < NEGATIVE_FLOAT_ERROR) {
            return -1;
         }
         else if(val > POSITIVE_FLOAT_ERROR) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(long v1, long v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(int v1, int v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(short v1, short v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }
   }

   /**
    * Boolean comparer.
    */
   public static final class BooleanComparer implements Comparer {
      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(Object v1, Object v2) {
         if(v1 == null) {
            return v2 == null ? 0 : -1;
         }
         else if(v2 == null) {
            return 1;
         }

         Boolean obj1 = (Boolean) v1;
         Boolean obj2 = (Boolean) v2;

         if(obj1.booleanValue() == obj2.booleanValue()) {
            return 0;
         }

         return obj1.booleanValue() ? 1 : -1;
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(double v1, double v2) {
         if(v1 == Tool.NULL_DOUBLE || v2 == Tool.NULL_DOUBLE) {
            return v1 == v2 ? 0 : (v1 == Tool.NULL_DOUBLE ? -1 : 1);
         }

         double val = v1 - v2;

         if(val < NEGATIVE_DOUBLE_ERROR) {
            return -1;
         }
         else if(val > POSITIVE_DOUBLE_ERROR) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(float v1, float v2) {
         if(v1 == Tool.NULL_FLOAT || v2 == Tool.NULL_FLOAT) {
            return v1 == v2 ? 0 : (v1 == Tool.NULL_FLOAT ? -1 : 1);
         }

         float val = v1 - v2;

         if(val < NEGATIVE_FLOAT_ERROR) {
            return -1;
         }
         else if(val > POSITIVE_FLOAT_ERROR) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(long v1, long v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(int v1, int v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(short v1, short v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }
   }

   /**
    * Character comparer.
    */
   public static final class CharacterComparer implements Comparer {
      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(Object v1, Object v2) {
         String s1 = v1 == null ? null : v1.toString().trim();
         String s2 = v2 == null ? null : v2.toString().trim();

         if(s1 == null) {
            return s2 == null ? 0 : -1;
         }
         else if(s2 == null) {
            return 1;
         }

         if(Tool.isCaseSensitive()) {
            return s1.compareTo(s2);
         }
         else {
            s1 = s1.toLowerCase();
            s2 = s2.toLowerCase();

            return s1.compareTo(s2);
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(double v1, double v2) {
         if(v1 == Tool.NULL_DOUBLE || v2 == Tool.NULL_DOUBLE) {
            return v1 == v2 ? 0 : (v1 == Tool.NULL_DOUBLE ? -1 : 1);
         }

         double val = v1 - v2;

         if(val < NEGATIVE_DOUBLE_ERROR) {
            return -1;
         }
         else if(val > POSITIVE_DOUBLE_ERROR) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(float v1, float v2) {
         if(v1 == Tool.NULL_FLOAT || v2 == Tool.NULL_FLOAT) {
            return v1 == v2 ? 0 : (v1 == Tool.NULL_FLOAT ? -1 : 1);
         }

         float val = v1 - v2;

         if(val < NEGATIVE_FLOAT_ERROR) {
            return -1;
         }
         else if(val > POSITIVE_FLOAT_ERROR) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(long v1, long v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(int v1, int v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }

      /**
       * This method should return > 0 if v1 is greater than v2, 0 if
       * v1 is equal to v2, or < 0 if v1 is less than v2.
       * It must handle null values for the comparison values.
       * @param v1 comparison value.
       * @param v2 comparison value.
       * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
       */
      @Override
      public int compare(short v1, short v2) {
         if(v1 < v2) {
            return -1;
         }
         else if(v1 > v2) {
            return 1;
         }
         else {
            return 0;
         }
      }
   }
}
