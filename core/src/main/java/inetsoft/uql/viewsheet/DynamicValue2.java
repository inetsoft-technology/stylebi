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
package inetsoft.uql.viewsheet;

import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * Dynamic value, comtains a design time string value and a runtime value.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class DynamicValue2 extends DynamicValue {
   /**
    * Constructor.
    */
   public DynamicValue2() {
      super();
   }

   /**
    * Constructor.
    * @param dvalue the specified design time value.
    */
   public DynamicValue2(String dvalue) {
      super(dvalue);
   }

   /**
    * Constructor.
    * @param dvalue the specified design time value.
    * @param dtype the specified data type.
    */
   public DynamicValue2(String dvalue, String dtype) {
      super(dvalue, dtype);
   }

   /**
    * Constructor.
    * @param dvalue the specified design time value.
    * @param dtype the specified data type.
    * @param restriction the specified restriction.
    */
   public DynamicValue2(String dvalue, String dtype, Object[] restriction) {
      super(dvalue, dtype, restriction);
   }

   /**
    * Constructor.
    * @param dvalue the specified design time value.
    * @param dtype the specified data type.
    * @param restriction the specified restriction.
    * @param rname the names of the values. The name can be returned by
    * variable or script, and it is used to lookup the value.
    */
   public DynamicValue2(String dvalue, String dtype, int[] restriction,
                        String[] rnames) {
      super(dvalue, dtype, restriction, rnames);
   }

   /**
    * Get int value.
    * @param design <tt>true</tt> if get design time value, <tt>false</tt>
    * if get run time value.
    * @param def the default value should be returned. It is also returned when the
    * value is not an integer, e.g. an unevaluated variable or script, or junk.
    * @return the int value.
    */
   public int getIntValue(boolean design, int def) {
      String str = getStringRepresentation(design, def);

      try {
         return Integer.parseInt(str);
      }
      catch(NumberFormatException ex) {
         // accept an integral double within int range, e.g. "50.0" from a script result
         try {
            double dval = Double.parseDouble(str);

            if(dval == Math.rint(dval) && dval >= Integer.MIN_VALUE &&
               dval <= Integer.MAX_VALUE)
            {
               return (int) dval;
            }
         }
         catch(NumberFormatException ignore) {
            // not a number, fall back to the default below
         }

         LOG.debug("Invalid int value \"{}\", using default {}", str, def);
         return def;
      }
   }

   /**
    * Get double value.
    * @param design <tt>true</tt> if get design time value, <tt>false</tt>
    * if get run time value.
    * @param def the default value should be returned. It is also returned when the
    * value is not a number, e.g. an unevaluated variable or script, or junk.
    * @return the double value.
    */
   public double getDoubleValue(boolean design, double def) {
      String str = getStringRepresentation(design, def);

      try {
         return Double.parseDouble(str);
      }
      catch(NumberFormatException ex) {
         LOG.debug("Invalid double value \"{}\", using default {}", str, def);
         return def;
      }
   }

   /**
    * Get boolean value.
    * @param design <tt>true</tt> if get design time value, <tt>false</tt>
    * if get run time value.
    * @param def the default value should be returned.
    * @return the boolean value.
    */
   public boolean getBooleanValue(boolean design, boolean def) {
      return Boolean.valueOf(getStringRepresentation(design, def));
   }

   /**
    * Get string value.
    * @param design <tt>true</tt> if get design time value, <tt>false</tt>
    * if get run time value.
    * @param def the default value should be returned.
    * @return the string value.
    */
   public String getStringValue(boolean design, String def) {
      return getStringRepresentation(design, def);
   }

   /**
    * Get color value.
    * @param design <tt>true</tt> if get design time value, <tt>false</tt>
    * if get run time value.
    * @param def the default value should be returned.
    * @return the color Object.
    */
   public Color getColorValue(boolean design, Color def) {
      Color color = Tool.getColorData(design ? getDValue() : getRValue());

      return color == null ? def : color;
   }

   /**
    * Get string representation of a value.
    */
   private String getStringRepresentation(boolean design, Object def) {
      Object value = design ? getDValue() : getRValue();
      String valueStr = value == null ? "" : value + "";
      valueStr = valueStr.trim();

      return valueStr.length() > 0 ? valueStr : def + "";
   }

   private static final Logger LOG = LoggerFactory.getLogger(DynamicValue2.class);
}