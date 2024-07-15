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
package inetsoft.report.composition.graph.calc;

import inetsoft.report.filter.ProductFormula;
import inetsoft.util.NumberParserWrapper;

public class CompoundGrowthColumn extends RunningTotalColumn {
   public CompoundGrowthColumn(String field, String header) {
      super(field, header);
      setFormula(new CGProductFormula());
   }

   @Override
   public boolean isAsPercent() {
      return true;
   }

   private static class CGProductFormula extends ProductFormula {
      @Override
      public void addValue(Object v) {
         if(v == null) {
            return;
         }

         double dv = 0;

         try {
            dv = (v instanceof Number) ? ((Number) v).doubleValue() :
               NumberParserWrapper.getDouble(v.toString());
         }
         catch(NumberFormatException e) {
         }

         super.addValue(dv + 1);
      }

      @Override
      public void addValue(double v) {
         super.addValue(v + 1);
      }

      @Override
      public void addValue(double[] v) {
         if(v != null && v.length > 0) {
            super.addValue(v[0] + 1);
         }
      }

      @Override
      public void addValue(float v) {
         super.addValue(v + 1);
      }

      @Override
      public void addValue(long v) {
         super.addValue(v + 1);
      }

      @Override
      public void addValue(int v) {
         super.addValue(v + 1);
      }

      @Override
      public void addValue(short v) {
         super.addValue(v + 1);
      }

      @Override
      public Object getResult() {
         Object result = super.getResult();
         return result instanceof Number ?
            Double.valueOf(((Number) result).doubleValue() - 1) : result;
      }

      @Override
      public double getDoubleResult() {
         return super.getDoubleResult() - 1;
      }
   }
}
