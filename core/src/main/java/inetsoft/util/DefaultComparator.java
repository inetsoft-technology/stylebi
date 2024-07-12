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
package inetsoft.util;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;

/**
 * Object comparison interface. This comparer compares two objects as
 * numbers if they are both Number (Integer, Long, Double, ...). Otherwise
 * it performs a string comparison.
 *
 * @version 10.1, 12/9/2008
 * @author InetSoft Technology Corp
 */
public class DefaultComparator implements Comparator, Cloneable, Serializable {
   /**
    * Constructor.
    */
   public DefaultComparator() {
      super();
   }

   /**
    * Create a comparator.
    */
   public DefaultComparator(boolean caseSensitive) {
      this.caseSensitive = caseSensitive;
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
   public int compare(Object v1, Object v2) {
      return sign * CoreTool.compare(v1, v2, caseSensitive, parseNumber);
   }

   /**
    * If negate is set to true, the comparison result is reversed.
    * This is the same as adding a logic 'Not' to the comparison.
    */
   public void setNegate(boolean neg) {
      sign = neg ? -1 : 1;
   }

   /**
    * Check if the comparison result is negated.
    */
   public boolean isNegate() {
      return sign < 0;
   }

   /**
    * Sets whether the comparison is case sensitive for strings.
    */
   public void setCaseSensitive(boolean caseSensitive) {
      this.caseSensitive = caseSensitive;
   }

   /**
    * Determines if the comparison is case sensitive for strings.
    */
   public boolean isCaseSensitive() {
      return caseSensitive;
   }

   /**
    * Check if string should be parsed as number if a mixed type of number and string are compared.
    */
   public boolean isParseNumber() {
      return parseNumber;
   }

   /**
    * Set if string should be parsed as number if a mixed type of number and string are compared.
    */
   public void setParseNumber(boolean parseNumber) {
      this.parseNumber = parseNumber;
   }

   /**
    * Return 1 for ascending and -1 for descending.
    */
   protected int getSign() {
      return sign;
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         // impossible
      }

      return null;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      DefaultComparator that = (DefaultComparator) o;
      return sign == that.sign && caseSensitive == that.caseSensitive;
   }

   @Override
   public int hashCode() {
      return Objects.hash(sign, caseSensitive);
   }

   @Override
   public String toString() {
      return super.toString() + "(" + sign + "," + caseSensitive + ")";
   }

   private int sign = 1;
   private boolean caseSensitive = true;
   private boolean parseNumber = true;
}
