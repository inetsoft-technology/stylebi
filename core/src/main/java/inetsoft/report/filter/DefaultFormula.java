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

import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default formula.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class DefaultFormula implements Formula {
   /**
    * Create a default formula.
    */
   public DefaultFormula() {
      super();

      reset();
   }

   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      dv = Double.MIN_VALUE;
      fv = Float.MIN_VALUE;
      lv = Long.MIN_VALUE;
      iv = Integer.MIN_VALUE;
      sinited = false;
   }

   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
      this.v = v;
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double v) {
      this.dv = v;
   }

   /**
    * Add double values to the formula.
    */
   @Override
   public void addValue(double[] v) {
      this.dv = v[0];
   }

   /**
    * Add a float value to the formula.
    */
   @Override
   public void addValue(float v) {
      this.fv = v;
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
      this.lv = v;
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
      this.iv = v;
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
      if(v != Tool.NULL_SHORT) {
         this.sv = v;
         this.sinited = true;
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
      if(v != null) {
         return v;
      }
      else if(dv != Double.MIN_VALUE) {
         return dv;
      }
      else if(fv != Float.MIN_VALUE) {
         return fv;
      }
      else if(lv != Long.MIN_VALUE) {
         return lv;
      }
      else if(iv != Integer.MIN_VALUE) {
         return iv;
      }
      else if(sinited) {
         return sv;
      }

      return null;
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      if(dv != Double.MIN_VALUE) {
         return dv;
      }

      return 0;
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return dv == Double.MIN_VALUE;
   }

   /**
    * Clone this formula. This may or may not copy the values from this
    * formula.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone formula", ex);
      }

      return null;
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("Null");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.NONE_FORMULA;
   }

   private boolean def;
   private Object v;
   private double dv;
   private float fv;
   private long lv;
   private int iv;
   private short sv;
   private boolean sinited;

   private static final Logger LOG =
      LoggerFactory.getLogger(DefaultFormula.class);
}
