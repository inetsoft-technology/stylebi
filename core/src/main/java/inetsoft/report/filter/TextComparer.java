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

import inetsoft.report.Comparer;
import inetsoft.util.Tool;

import java.text.Collator;

/**
 * Text string comparison. A collator can be specified to perform
 * locale specific comparison.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TextComparer implements Comparer {
   public TextComparer(Collator collator, boolean forceToString) {
      this(collator);
      this.forceToString = forceToString;
   }

   /**
    * The collator is used to perform the comparison of two strings.
    */
   public TextComparer(Collator collator) {
      super();
      caseSensitive = Tool.isCaseSensitive();

      // for case sensitive, to use collator could not return proper result,
      // so here we disable collator if user requires case sensitive comparison
      if(caseSensitive) {
         collator = null;
      }

      this.collator = collator;
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
    * {@inheritDoc}
    */
   @Override
   public int compare(Object v1, Object v2) {
      if(forceToString) {
         v1 = v1 != null ? v1.toString() : null;
         v2 = v2 != null ? v2.toString() : null;
      }

      if(collator != null) {
         try {
            return collator.compare((String) v1, (String) v2);
         }
         catch(Exception ex) {
            // @by larryl, the TextComparer is used to sort string, but since
            // column types defaults to string if it's unknown, we need to handle
            // the case when the values are not strings
         }
      }

      return Tool.compare(v1, v2, caseSensitive, true);
   }

   /**
    * {@inheritDoc}
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
    * {@inheritDoc}
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
    * {@inheritDoc}
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
    * {@inheritDoc}
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
    * {@inheritDoc}
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

   private boolean caseSensitive;
   private transient Collator collator;
   private boolean forceToString;
}
