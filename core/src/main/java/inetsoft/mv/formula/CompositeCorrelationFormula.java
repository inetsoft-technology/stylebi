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
package inetsoft.mv.formula;

import inetsoft.report.filter.Formula2;
import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
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
      cnt = 0;
      sumx = 0;
      sumy = 0;
      sumsqx = 0;
      sumsqy = 0;
   }

   @Override
   public void addValue(double v) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Add the sub-aggregates of a sub-group, in the order of
    * AggregateFormula.CORRELATION.getSubAggregates(): SUMWT(x, y), PAIRSUM(x, y),
    * PAIRSUM(y, x), PAIRCOUNT(x, y), PAIRSUMSQ(x, y), PAIRSUMSQ(y, x), all over the rows
    * where both x and y are not null (Bug #82375). For a correlation of a column with itself
    * the duplicates are left out (SUMWT, PAIRSUM, PAIRCOUNT, PAIRSUMSQ). A null sub-aggregate
    * (Tool.NULL_DOUBLE, no row in the sub-group) adds 0.
    */
   @Override
   public void addValue(double[] v) {
      if(v == null) {
         return;
      }

      if(v.length == 4) {
         add(v[0], v[1], v[1], v[2], v[3], v[3]);
      }
      else if(v.length == 6) {
         add(v[0], v[1], v[2], v[3], v[4], v[5]);
      }
   }

   private void add(double wt, double x, double y, double n, double sqx, double sqy) {
      sumwt += value(wt);
      sumx += value(x);
      sumy += value(y);
      cnt += value(n);
      sumsqx += value(sqx);
      sumsqy += value(sqy);
   }

   private static double value(double val) {
      return val == Tool.NULL_DOUBLE ? 0 : val;
   }

   @Override
   public void addValue(float v) {
      throw new RuntimeException("Unsupported method called!");
   }

   @Override
   public void addValue(long v) {
      throw new RuntimeException("Unsupported method called!");
   }

   @Override
   public void addValue(int v) {
      throw new RuntimeException("Unsupported method called!");
   }

   @Override
   public void addValue(short v) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Add the sub-aggregates as objects, in the same order as addValue(double[]).
    */
   @Override
   public void addValue(Object v) {
      if(!(v instanceof Object[] data)) {
         return;
      }

      try {
         double[] vals = new double[data.length];

         for(int i = 0; i < data.length; i++) {
            vals[i] = data[i] == null ? Tool.NULL_DOUBLE : (data[i] instanceof Number)
               ? ((Number) data[i]).doubleValue() : Double.parseDouble(data[i] + "");
         }

         addValue(vals);
      }
      catch(NumberFormatException ignore) {
         // a sub-aggregate that is not a number, the sub-group is skipped
      }
   }

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
    * Get the formula result. No result (no rows, a single pair, a constant column, i.e. a zero
    * correlation denominator) is null, never NaN; with the default result option only the
    * no-row case returns 0, as before (Bug #81182).
    */
   @Override
   public Object getResult() {
      if(cnt <= 0) {
         return def ? Double.valueOf(0) : null;
      }

      double result = correlation();
      return Double.isNaN(result) ? null : Double.valueOf(result);
   }

   /**
    * Get the formula double result. 0 when there is no result, see {@link #isNull()}.
    */
   @Override
   public double getDoubleResult() {
      double result = correlation();
      return Double.isNaN(result) ? 0 : result;
   }

   /**
    * Check if the result is null, i.e. there is no correlation to report.
    */
   @Override
   public boolean isNull() {
      return cnt <= 0 || Double.isNaN(correlation());
   }

   /**
    * The single place the correlation is computed, shared by getResult(), getDoubleResult() and
    * isNull() so they always agree.
    *
    * @return the correlation, or NaN when there is none (no rows, or a zero variance of x or y).
    */
   private double correlation() {
      if(cnt <= 0) {
         return Double.NaN;
      }

      // n * sum(v^2) - sum(v)^2 is the variance times n^2. For a constant column of inexact
      // doubles both terms are equal up to rounding, so the difference is noise of either sign
      // (sqrt of it would be NaN or a spurious ~1e-9 correlation). A difference no larger than
      // ZERO_VARIANCE_TOLERANCE of the term it is cancelled from carries no information
      // (that is ~4500 ulps of the double precision) and is treated as zero variance.
      // Data with a standard deviation above ~1e-6 of its mean is not affected.
      double scalex = cnt * sumsqx;
      double scaley = cnt * sumsqy;
      double varnumx = scalex - (sumx * sumx);
      double varnumy = scaley - (sumy * sumy);

      if(!(varnumx > ZERO_VARIANCE_TOLERANCE * scalex) ||
         !(varnumy > ZERO_VARIANCE_TOLERANCE * scaley))
      {
         return Double.NaN;
      }

      double sdevx = Math.sqrt(varnumx / (cnt * cnt));
      double sdevy = Math.sqrt(varnumy / (cnt * cnt));
      double covar = (sumwt / cnt) - ((sumx * sumy) / (cnt * cnt));
      double result = covar / (sdevx * sdevy);

      return Double.isInfinite(result) ? Double.NaN : result;
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
   // count of the pairs (the same for x and y), a double so that the square does not overflow
   private double cnt = 0;
   private double sumx = 0;
   private double sumy = 0;
   private double sumsqx = 0;
   private double sumsqy = 0;

   private boolean def = false;

   // relative size below which n*sum(v^2) - sum(v)^2 is rounding noise, see correlation()
   private static final double ZERO_VARIANCE_TOLERANCE = 1.0E-12;
}
