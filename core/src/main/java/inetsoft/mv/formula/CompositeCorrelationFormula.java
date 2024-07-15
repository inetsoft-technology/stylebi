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
package inetsoft.mv.formula;

import inetsoft.report.filter.Formula2;
import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;
/**
 * Calculate the correlation.
 *
 * @author InetSoft Technology Corp
 */
public class CompositeCorrelationFormula implements Formula2 {
   /**
    * Constructor.
    */
   public CompositeCorrelationFormula() {
      super();
   }

   /**
    * Create a correlation formula.
    * @param cols secondary columns used for this formula.
    */
   public CompositeCorrelationFormula(int[] cols) {
      this();
      this.cols = cols;
   }

   /**
    * Get the secondary columns
    */
   @Override
   public int[] getSecondaryColumns() {
      return cols;
   }

   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      sumwt = 0;
      cntx = 0;
      sumx = 0;
      sumy = 0;
      sumsqx = 0;
      cnty = 0;
      sumsqy = 0;
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
      if(v.length < 7) {
      	 // very special case, might be self correlation
         if(v.length == 4) {
            sumwt += v[0];
            sumx += v[1];
            sumy += v[1];
            cntx += v[2];
            sumsqx += v[3];
            cnty += v[2];
            sumsqy += v[3];
         }

         return;
      }

      sumwt += v[0];
      sumx += v[1];
      sumy += v[2];
      cntx += v[3];
      sumsqx += v[4];
      cnty += v[5];
      sumsqy += v[6];
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

      double osumwt = sumwt;
      int ocntx = cntx;
      double osumx = sumx;
      double osumy = sumy;
      double osumsqx = sumsqx;
      int ocnty = cnty;
      double osumsqy = sumsqy;

      try {
         Object[] data = (Object[]) v;
         // result = SIGMA(xV * yV) / SIGMA(yV);
         Number val1 = (data[0] instanceof Number) ?
            (Number) data[0] : Double.parseDouble(data[0] + "");
         Number val2 = (data[1] instanceof Number) ?
            (Number) data[1] : Double.parseDouble(data[1] + "");
         Number val3 = (data[2] instanceof Number) ?
            (Number) data[2] : Double.parseDouble(data[2] + "");
         Number val4 = (data[3] instanceof Number) ?
            (Number) data[3] : Double.parseDouble(data[3] + "");
         Number val5 = (data[4] instanceof Number) ?
            (Number) data[4] : Double.parseDouble(data[4] + "");
         Number val6 = (data[5] instanceof Number) ?
            (Number) data[5] : Double.parseDouble(data[5] + "");
         Number val7 = (data[6] instanceof Number) ?
            (Number) data[6] : Double.parseDouble(data[6] + "");

         if(val1 == null || val2 == null || val3 == null || val4 == null ||
            val5 == null || val6 == null || val7 == null)
         {
            return;
         }

         sumwt += val1.doubleValue();
         sumx += val2.doubleValue();
         sumy += val3.doubleValue();
         cntx += val4.intValue();
         sumsqx += val5.doubleValue();
         cnty += val6.intValue();
         sumsqy += val7.doubleValue();
      }
      catch(Exception e) {
         sumwt = osumwt;
         cntx = ocntx;
         sumx = osumx;
         sumy = osumy;
         sumsqx = osumsqx;
         cnty = ocnty;
         sumsqy = osumsqy;
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
      if(cntx == 0 || cnty == 0) {
         return def ? Double.valueOf(0) : null;
      }

      try {
         double variancex = ((cntx * sumsqx) - (sumx * sumx)) /
            (cntx * cntx);
         double sdevx = Math.sqrt(variancex);
         double variancey = ((cnty * sumsqy) - (sumy * sumy)) /
            (cnty * cnty);
         double sdevy = Math.sqrt(variancey);
         double covar = (sumwt / cntx) - ((sumx * sumy) / (cntx * cntx));

         if(sdevx == 0 || sdevy == 0) {
            return null;
         }

         return Double.valueOf(covar / (sdevx * sdevy));
      }
      catch(Throwable e) {
         return null;
      }
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      if(cntx == 0 || cnty == 0) {
         return 0;
      }

      try {
         double variancex = ((cntx * sumsqx) - (sumx * sumx)) /
            (cntx * cntx);
         double sdevx = Math.sqrt(variancex);
         double variancey = ((cnty * sumsqy) - (sumy * sumy)) /
            (cnty * cnty);
         double sdevy = Math.sqrt(variancey);
         double covar = (sumwt / cntx) - ((sumx * sumy) / (cntx * cntx));

         if(sdevx == 0 || sdevy == 0) {
            return 0;
         }

         return covar / (sdevx * sdevy);
      }
      catch(Throwable e) {
         return 0;
      }
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return cntx == 0 || cnty == 0;
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(CloneNotSupportedException ex) {
         return this;
      }
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("CorrelationFormula");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.CORRELATION_FORMULA;
   }

   @Override
   public Class getResultType() {
      return Double.class;
   }

   private int[] cols;
   private double sumwt = 0;
   private int cntx = 0;
   private double sumx = 0;
   private double sumy = 0;
   private double sumsqx = 0;
   private int cnty = 0;
   private double sumsqy = 0;

   private boolean def = false;
}
