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
package inetsoft.report.internal.binding;

import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.DataRefWrapper;

/**
 * BindingTool, utility methods operating binding attr.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class BindingTool {
   /**
    * Fake column prefix.
    */
   public static final String FAKE_PREFIX = "__Fake__";

    /**
    * Get field name.
    */
   public static String getFieldName(Field f) {
      String name = getPureField(f).toString();
      int idx = name.indexOf("::");
      name = idx >= 0 ? name.substring(idx + 2) : name;
      return name;
   }

   /**
    * Get data ref name.
    */
   public static String getFieldName(DataRef f) {
      DataRef ref = getPureField(f);
      String name = ref.getName();

      if(ref instanceof AliasDataRef) {
         ref = ((AliasDataRef) ref).getDataRef();
         name = ref.getName();
      }

      int idx = name.indexOf("::");
      name = idx >= 0 ? name.substring(idx + 2) : name;
      return name;
   }

   /**
    * Get a field without aggreate and group.
    */
   public static Field getPureField(Field field) {
      if(field instanceof GroupField) {
         return ((GroupField) field).getField();
      }

      if(field instanceof AggregateField) {
         return ((AggregateField) field).getField();
      }

      return field;
   }

   /**
    * Get a data ref without aggreate and group.
    */
   public static DataRef getPureField(DataRef ref) {
      if(ref instanceof Field) {
         return getPureField((Field) ref);
      }

      return DataRefWrapper.getBaseDataRef(ref);
   }

   /**
    * Check the group field is fake.
    */
   public static boolean isFakeField(DataRef field) {
      return isFakeField(field, false);
   }

   /**
    * Check the group field is fake.
    */
   public static boolean isFakeField(DataRef field, boolean isEmbedded) {
      if(isEmbedded && field.getName().startsWith(FAKE_PREFIX)) {
         return true;
      }

      while(field != null) {
         if(field instanceof FormulaField && ((FormulaField) field).isFake()) {
            return true;
         }

         field = field instanceof DataRefWrapper ?
            ((DataRefWrapper) field).getDataRef() : null;
      }

      return false;
   }

   /**
    * Get formula string used for the aggregate field.
    */
   public static String getFormulaString(String func) {
      if(func == null) {
         return func;
      }

      int idx;

      if((idx = func.indexOf('<')) >= 0) {
         func = func.substring(0, idx);
      }

      if((idx = func.indexOf('(')) >= 0) {
         func = func.substring(0, idx);
      }

      return func;
   }

   /**
    * Get formula type.
    */
   public static String getSecondFormula(String formula) {
      if(formula == null) {
         return null;
      }

      int perIdx = formula.indexOf('(');

      if(perIdx > 0) {
         int perEIdx = formula.lastIndexOf(')');
         return formula.substring(perIdx + 1, perEIdx);
      }

      return null;
   }
}
