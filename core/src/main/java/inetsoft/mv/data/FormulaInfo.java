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
package inetsoft.mv.data;

import inetsoft.report.filter.Formula;
import inetsoft.util.Tool;

import java.io.Serializable;

/**
 * FormulaInfo stores formula and column indexes.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public abstract class FormulaInfo implements Cloneable, Serializable {
   // for serialization
   protected FormulaInfo() {
   }

   /**
    * Create an instance of FormulaInfo.
    */
   protected FormulaInfo(Formula formula, int[] cols) {
      this.formula = formula;
      this.cols = cols;
   }

   /**
    * Create a formula info.
    */
   public static FormulaInfo create(Formula formula, int[] cols) {
      if(cols.length == 1) {
         return new FormulaInfo1(formula, cols);
      }
      else {
         return new FormulaInfo2(formula, cols);
      }
   }

   /**
    * Add double values to the formula.
    */
   public abstract void addValue(double[] values);

   /**
    * Add Object values to the formula.
    */
   public abstract void addValue(Object[] values);

   /**
    * Clone this formula info.
    */
   @Override
   public final Object clone() {
      FormulaInfo info = null;

      try {
         info = (FormulaInfo) super.clone();
         info.formula = (Formula) formula.clone();
      }
      catch(Exception ex) {
         // ignore it
      }

      return info;
   }

   /**
    * Return the formula which is within this FormulaInfo
    */
   public Formula getFormula() {
      return formula;
   }

   /**
    * Determine if the supplied column number is part of this FormulaInfo
    */
   public boolean isCol(int c) {
      for(int col: this.cols) {
         if(col == c) {
            return true;
         }
      }

      return false;
   }

   public String toString() {
      return formula.getDisplayName() + "(" + Tool.arrayToString(cols) + ")";
   }

   /**
    * FormulaInfo contains the formula requires one measure.
    */
   public static final class FormulaInfo1 extends FormulaInfo {
      // for serialization
      FormulaInfo1() {
      }

      FormulaInfo1(Formula formula, int[] cols) {
         super(formula, cols);
         col = cols[0];
      }

      @Override
      public final void addValue(double[] values) {
         formula.addValue(values[col]);
      }

      @Override
      public final void addValue(Object[] values) {
         formula.addValue(values[col]);
      }

      int col = 0;
   }

   /**
    * FormulaInfo contains the formula requires multiple measures.
    */
   public static final class FormulaInfo2 extends FormulaInfo {
      // for serialization
      FormulaInfo2() {
      }

      FormulaInfo2(Formula formula, int[] cols) {
         super(formula, cols);
         arr = new double[cols.length];
      }

      @Override
      public final void addValue(double[] values) {
         for(int i = 0; i < cols.length; i++) {
            arr[i] = values[cols[i]];
         }

         formula.addValue(arr);
      }

      @Override
      public final void addValue(Object[] values) {
         Object[] objs = new Object[cols.length];

         for(int i = 0; i < cols.length; i++) {
            objs[i] = values[cols[i]];
         }

         formula.addValue(objs);
      }

      private double[] arr;
   }

   protected Formula formula;
   protected int[] cols;
}
