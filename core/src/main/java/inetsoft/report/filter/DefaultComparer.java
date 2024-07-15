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
package inetsoft.report.filter;

import inetsoft.report.Comparer;
import inetsoft.util.DefaultComparator;
import inetsoft.util.Tool;

/**
 * Object comparison interface. This comparer compares two objects as
 * numbers if they are both Number (Integer, Long, Double, ...). Otherwise
 * it performs a string comparison.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class DefaultComparer extends DefaultComparator implements Comparer {

   // ChrisS bug1405717099968 2014-8-26
   // Pass Tool.isCaseSensitive() to DefaultComparator()
   /**
    * Constructor.
    */
   public DefaultComparer() {
      super(Tool.isCaseSensitive());
   }

   protected DefaultComparer(boolean caseSensitive) {
      super(caseSensitive);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(double v1, double v2) {
      if(v1 == Tool.NULL_DOUBLE || v2 == Tool.NULL_DOUBLE) {
         return v1 == v2 ? 0 : (v1 == Tool.NULL_DOUBLE ? -getSign() : getSign());
      }

      double val = v1 - v2;

      if(val < NEGATIVE_DOUBLE_ERROR) {
         return -getSign();
      }
      else if(val > POSITIVE_DOUBLE_ERROR) {
         return getSign();
      }
      else {
         return 0;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(float v1, float v2) {
      if(v1 == Tool.NULL_FLOAT || v2 == Tool.NULL_FLOAT) {
         return v1 == v2 ? 0 : (v1 == Tool.NULL_FLOAT ? -getSign() : getSign());
      }

      float val = v1 - v2;

      if(val < NEGATIVE_FLOAT_ERROR) {
         return -getSign();
      }
      else if(val > POSITIVE_FLOAT_ERROR) {
         return getSign();
      }
      else {
         return 0;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(long v1, long v2) {
      if(v1 < v2) {
         return -getSign();
      }
      else if(v1 > v2) {
         return getSign();
      }
      else {
         return 0;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(int v1, int v2) {
      if(v1 < v2) {
         return -getSign();
      }
      else if(v1 > v2) {
         return getSign();
      }
      else {
         return 0;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(short v1, short v2) {
      if(v1 < v2) {
         return -getSign();
      }
      else if(v1 > v2) {
         return getSign();
      }
      else {
         return 0;
      }
   }
}
