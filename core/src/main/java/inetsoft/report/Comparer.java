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
package inetsoft.report;

import java.util.Comparator;

/**
 * Object comparison interface. This interface is used by some classes
 * to define a custom comparison ordering.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface Comparer extends java.io.Serializable, Comparator {
   /**
    * Negative double error.
    */
   public static final double NEGATIVE_DOUBLE_ERROR = -0.0000001d;
   /**
    * Positive double error.
    */
   public static final double POSITIVE_DOUBLE_ERROR = 0.0000001d;
   /**
    * Negative float error.
    */
   public static final float NEGATIVE_FLOAT_ERROR = -0.000001f;
   /**
    * Positive float error.
    */
   public static final float POSITIVE_FLOAT_ERROR = 0.000001f;

   /**
    * This method should return &gt; 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or &lt; 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return &lt; 0, 0, or &gt; 0 for v1&lt;v2, v1==v2, or v1&gt;v2.
    */
   @Override
   public int compare(Object v1, Object v2);

   /**
    * This method should return &gt; 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or &lt; 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return &lt; 0, 0, or &gt; 0 for v1&lt;v2, v1==v2, or v1&gt;v2.
    */
   public int compare(double v1, double v2);

   /**
    * This method should return &gt; 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or &lt; 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return &lt; 0, 0, or &gt; 0 for v1&lt;v2, v1==v2, or v1&gt;v2.
    */
   public int compare(float v1, float v2);

   /**
    * This method should return &gt; 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or &lt; 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return &lt; 0, 0, or &gt; 0 for v1&lt;v2, v1==v2, or v1&gt;v2.
    */
   public int compare(long v1, long v2);

   /**
    * This method should return &gt; 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or &lt; 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return &lt; 0, 0, or &gt; 0 for v1&lt;v2, v1==v2, or v1&gt;v2.
    */
   public int compare(int v1, int v2);

   /**
    * This method should return &gt; 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or &lt; 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return &lt; 0, 0, or &gt; 0 for v1&lt;v2, v1==v2, or v1&gt;v2.
    */
   public int compare(short v1, short v2);
}
