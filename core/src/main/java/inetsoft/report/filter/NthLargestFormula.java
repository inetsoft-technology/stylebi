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
import inetsoft.sree.SreeEnv;
import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.jnumbers.NumberParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Vector;

/**
 * Calculate the nth largest value in a column.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class NthLargestFormula implements Formula, java.io.Serializable {
   /**
    * Create a formula to get the nth largest value.
    * @param n this value starts at 1 for the largest value.
    */
   public NthLargestFormula(int n) {
      if(n <= 0) {
         n = 1;
         LOG.info("The N of {} is invalid, use 1 as default.", getDisplayName());
      }

      this.n = n;
   }

   // Needed to avoid Kyro error "Class cannot be created (missing no-arg constructor):"
   public NthLargestFormula() {
   }

   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      vs.removeAllElements();
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double v) {
      Double val = v == Tool.NULL_DOUBLE ? null : v;
      addValue(val);
   }

   /**
    * Add double values to the formula.
    */
   @Override
   public void addValue(double[] v) {
      Double val = v[0] == Tool.NULL_DOUBLE ? null : v[0];
      addValue(val);
   }

   /**
    * Add a float value to the formula.
    */
   @Override
   public void addValue(float v) {
      Float val = v == Tool.NULL_FLOAT ? null : v;
      addValue(val);
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
      Long val = v == Tool.NULL_LONG ? null : v;
      addValue(val);
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
      Integer val = v == Tool.NULL_INTEGER ? null : v;
      addValue(val);
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
      Short val = v == Tool.NULL_SHORT ? null : v;
      addValue(val);
   }

   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
      try {
         for(int i = vs.size() - 1; i >= 0; i--) {
            Object iV = vs.elementAt(i);

            if(compare(v, iV) == 0) {	// val == iV
               v = null;
               break;
            }
            else if(compare(v, iV) < 0) {  // v less than iV
               vs.insertElementAt(v, i + 1);
               v = null;
               break;
            }
         }

         if(v != null) {
            vs.insertElementAt(v, 0);
         }

         if(vs.size() > n) {
            vs.setSize(n);
         }
      }
      catch(NumberFormatException e) {
      }
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
      if(vs.size() == 0) {
         return def ? 0 : null;
      }

      return (vs.size() >= n) ? vs.elementAt(n - 1) :
         (def ? 0 : null);
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      if(vs.size() == 0) {
         return 0;
      }

      if(vs.size() >= n) {
         Object obj = vs.elementAt(n - 1);

         if(obj instanceof Number) {
            return ((Number) obj).doubleValue();
         }
         else if(obj instanceof String) {
            try {
               return NumberParser.getDouble((String) obj);
            }
            catch(Exception exp) {
            }
         }
      }

      return 0;
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return vs.size() == 0;
   }

   @Override
   public Object clone() {
      NthLargestFormula nlf = createNthFormula(n);
      nlf.def = def;

      for(int i = 0; i < vs.size(); i++) {
         nlf.addValue(vs.elementAt(i));
      }

      return nlf;
   }

   protected NthLargestFormula createNthFormula(int n) {
      return (new NthLargestFormula(n));
   }

   /**
    * Compare two values and return <0, 0, or >0 if the first value is less
    * than, equal to, or greater than the second value.
    */
   int compare(Object v1, Object v2) {
      if(v1 == null) { // null < everything
         return -1;
      }

      if(comp == null) {
         if(v1 instanceof Date) {
            comp = new DateComparer();
         }
         else {
            comp = ImmutableDefaultComparer.getInstance();
         }
      }

      // @by larryl, if both values are number string, use numeric comparison.
      // This is not completely consistent with the logic of comparing using
      // the actual type, but is probably more user expects. This is especially
      // true when formula is used in a fixed table, where all values are
      // string even if user entered a number
      if(v1 instanceof String && v2 instanceof String && parseNum) {
         try {
            v1 = NumberParser.getDouble((String) v1);
            v2 = NumberParser.getDouble((String) v2);
         }
         catch(Exception ex) {
         }
      }

      return comp.compare(v1, v2);
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("NthLargest");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.NTHLARGEST_FORMULA;
   }

   private boolean parseNum = "true".equals(SreeEnv.getProperty("formula.parse.number", "true"));
   private Vector vs = new Vector();
   private Comparer comp = null;
   private int n;
   private boolean def;

   private static final Logger LOG = LoggerFactory.getLogger(NthLargestFormula.class);
}
