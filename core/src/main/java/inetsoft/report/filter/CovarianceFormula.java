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
package inetsoft.report.filter;

import inetsoft.uql.XConstants;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Vector;

/**
 * Calculate the covariance of two columns.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class CovarianceFormula implements Formula2, java.io.Serializable {
   /**
    * Constructor.
    */
   public CovarianceFormula() {
      super();
   }

   /**
    * Create a covariance formula.
    * @param col column number of the column to calculate covariance with.
    */
   public CovarianceFormula(int col) {
      this();
      this.col = col;
   }

   /**
    * Get the second column index. The value on the primary column and the
    * value on the second column are passed in as an object array with
    * two elements.
    */
   @Override
   public int[] getSecondaryColumns() {
      return new int[] {col};
   }

   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      xs.removeAllElements();
      ys.removeAllElements();
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double v) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Add double value to the formula.
    */
   @Override
   public void addValue(double[] v) {
      if(v == null || v.length < 2 ||
         v[0] == Tool.NULL_DOUBLE || v[1] == Tool.NULL_DOUBLE)
      {
         return;
      }

      xs.addElement(v[1]);
      ys.addElement(v[2]);
   }

   /**
    * Add a float value to the formula.
    */
   @Override
   public void addValue(float v) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
      if(v == null || !(v instanceof Object[])) {
         return;
      }

      try {
         Object[] pair = (Object[]) v;
         Number xV = (pair[0] instanceof Number) ? (Number) pair[0] :
            NumberParserWrapper.getDouble(pair[0] + "");
         Number yV = (pair[1] instanceof Number) ? (Number) pair[1] :
            NumberParserWrapper.getDouble(pair[1] + "");

         xs.addElement(xV);
         ys.addElement(yV);
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
      if(xs.size() == 0) {
         return def ? Double.valueOf(0) : null;
      }

      Double total = getRawResult();
      return total == null ? null : Double.valueOf(total.doubleValue() / xs.size());
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      if(xs.size() == 0) {
         return 0;
      }

      Double total = getRawResult();
      return total == null ? 0 : total.doubleValue() / xs.size();
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return xs.size() == 0;
   }

   @Override
   public Object clone() {
      CovarianceFormula cf = new CovarianceFormula(col);
      cf.def = def;

      for(int i = 0; i < xs.size(); i++) {
         Object[] ob = new Object[2];

         ob[0] = xs.elementAt(i);
         ob[1] = ys.elementAt(i);
         cf.addValue(ob);
      }

      return cf;
   }

   private Double getRawResult() {
      try {
         double xAvg = 0, yAvg = 0;

         for(int i = 0; i < xs.size(); i++) {
            xAvg += ((Number) xs.elementAt(i)).doubleValue();
            yAvg += ((Number) ys.elementAt(i)).doubleValue();
         }

         double x = 0;
         double y = 0;
         double temp = 0;
         double total = 0;

         for(int i = 0; i < xs.size(); i++) {
            x = ((Number) xs.elementAt(i)).doubleValue();
            y = ((Number) ys.elementAt(i)).doubleValue();
            temp += (x * xs.size() - xAvg) * (y * ys.size() - yAvg);
         }

         total = temp / (xs.size() * ys.size());

         return Double.valueOf(total);
      }
      catch(Throwable e) {
         LOG.error("Failed to get raw result", e);
         return null;
      }
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("Covariance");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.COVARIANCE_FORMULA;
   }

   @Override
   public String toString() {
      return super.toString() + "(" + col + ")";
   }

   @Override
   public Class getResultType() {
      return Double.class;
   }

   private Vector xs = new Vector();
   private Vector ys = new Vector();
   private int col; // 2nd column
   private boolean def;

   private static final Logger LOG =
      LoggerFactory.getLogger(CovarianceFormula.class);
}
