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

import inetsoft.uql.XTable;

/**
 * Formula agent adds one value at a table cell to a formula properly.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
abstract class FormulaAgent implements java.io.Serializable {
   /**
    * Get the formula agent.
    * @param cls the specified class.
    * @param primitive <tt>true</tt> if primitive agent is recommended.
    * @return the created formula agent.
    */
   public static final FormulaAgent getAgent(Class cls, boolean primitive) {
      if(!primitive || cls == null) {
         return new ObjectFormulaAgent();
      }
      else if(Double.class.isAssignableFrom(cls)) {
         return new DoubleFormulaAgent();
      }
      else if(Float.class.isAssignableFrom(cls)) {
         return new FloatFormulaAgent();
      }
      else if(Long.class.isAssignableFrom(cls)) {
         return new LongFormulaAgent();
      }
      else if(Integer.class.isAssignableFrom(cls)) {
         return new IntFormulaAgent();
      }
      else if(Short.class.isAssignableFrom(cls)) {
         return new ShortFormulaAgent();
      }
      else {
         return new ObjectFormulaAgent();
      }
   }

   /**
    * Add one value at a cell to formula.
    * @param formula the specified formula object.
    * @param table the specified table.
    * @param r the specified row index.
    * @param c the specified column index.
    */
   public abstract void add(Formula formula, XTable table, int r, int c);

   /**
    * Object formula agent.
    */
   public static final class ObjectFormulaAgent extends FormulaAgent {
      @Override
      public void add(Formula formula, XTable table, int r, int c) {
         formula.addValue(table.getObject(r, c));
      }
   }

   /**
    * Double formula agent.
    */
   public static final class DoubleFormulaAgent extends FormulaAgent {
      @Override
      public void add(Formula formula, XTable table, int r, int c) {
         if(table.getObject(r, c) != null) {
            formula.addValue(table.getDouble(r, c));
         }
      }
   }

   /**
    * Float formula agent.
    */
   public static final class FloatFormulaAgent extends FormulaAgent {
      @Override
      public void add(Formula formula, XTable table, int r, int c) {
         if(table.getObject(r, c) != null) {
            formula.addValue(table.getFloat(r, c));
         }
      }
   }

   /**
    * Long formula agent.
    */
   public static final class LongFormulaAgent extends FormulaAgent {
      @Override
      public void add(Formula formula, XTable table, int r, int c) {
         if(table.getObject(r, c) != null) {
            formula.addValue(table.getLong(r, c));
         }
      }
   }

   /**
    * Int formula agent.
    */
   public static final class IntFormulaAgent extends FormulaAgent {
      @Override
      public void add(Formula formula, XTable table, int r, int c) {
         if(table.getObject(r, c) != null) {
            formula.addValue(table.getInt(r, c));
         }
      }
   }

   /**
    * Short formula agent.
    */
   public static final class ShortFormulaAgent extends FormulaAgent {
      @Override
      public void add(Formula formula, XTable table, int r, int c) {
         if(table.getObject(r, c) != null) {
            formula.addValue(table.getShort(r, c));
         }
      }
   }
}
