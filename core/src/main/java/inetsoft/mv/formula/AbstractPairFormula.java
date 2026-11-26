/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import inetsoft.util.Tool;

/**
 * A sub-aggregate of a two-column formula (covariance, correlation) computed over the rows
 * where both columns are not null, so that all the sub-aggregates of the formula range over
 * the same rows (Bug #82375). The value of the first column is aggregated, the second column
 * only selects the rows.
 *
 * @author InetSoft Technology Corp
 */
public abstract class AbstractPairFormula implements Formula2, java.io.Serializable {
   protected AbstractPairFormula() {
      super();
   }

   protected AbstractPairFormula(int col) {
      this();
      this.col = col;
   }

   /**
    * Add the value of the first column of a row where both columns are not null.
    */
   protected abstract void add(double x);

   @Override
   public int[] getSecondaryColumns() {
      return new int[] {col};
   }

   @Override
   public void reset() {
      total = 0;
      cnt = 0;
   }

   @Override
   public void addValue(double v) {
      throw new RuntimeException("Unsupported method called!");
   }

   @Override
   public void addValue(double[] v) {
      if(v == null || v.length < 2 || v[0] == Tool.NULL_DOUBLE || v[1] == Tool.NULL_DOUBLE) {
         return;
      }

      cnt++;
      add(v[0]);
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

   @Override
   public void addValue(Object v) {
      if(!(v instanceof Object[] pair) || pair.length < 2 || pair[0] == null ||
         pair[1] == null)
      {
         return;
      }

      try {
         double x = (pair[0] instanceof Number) ? ((Number) pair[0]).doubleValue()
            : Double.parseDouble(pair[0] + "");
         cnt++;
         add(x);
      }
      catch(NumberFormatException ignore) {
         // not a number, skipped as SumWTFormula does
      }
   }

   @Override
   public void setDefaultResult(boolean def) {
      this.def = def;
   }

   @Override
   public boolean isDefaultResult() {
      return def;
   }

   /**
    * The result is null only when no row has both values, not when the sum is 0, so that a
    * group whose values add up to 0 is not taken as having no rows.
    */
   @Override
   public Object getResult() {
      return cnt > 0 ? Double.valueOf(total) : (def ? Double.valueOf(0) : null);
   }

   @Override
   public double getDoubleResult() {
      return total;
   }

   @Override
   public boolean isNull() {
      return cnt == 0;
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

   @Override
   public String getDisplayName() {
      return getName();
   }

   @Override
   public Class getResultType() {
      return Double.class;
   }

   protected double total = 0;
   private int cnt = 0;
   private int col; // 2nd column
   private boolean def;
}
