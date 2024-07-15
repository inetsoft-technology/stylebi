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
package com.inetsoft.connectors.script;

import inetsoft.util.Tool;
import inetsoft.util.script.ScriptUtil;
import org.rosuda.REngine.*;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Date;

public class REXPEncoder {
   public REXP encode(Object value) {
      value = ScriptUtil.unwrap(value);

      if(value == null) {
         return new REXPNull();
      }

      if(isInteger(value)) {
         return new REXPInteger(((Number) value).intValue());
      }

      if(isNumeric(value)) {
         return new REXPDouble(((Number) value).doubleValue());
      }

      if(value instanceof Boolean) {
         return new REXPLogical((Boolean) value);
      }

      if(Tool.isDateClass(value.getClass())) {
         return new REXPString(formatDate((Date) value));
      }

      if(value instanceof String) {
         return new REXPString((String) value);
      }

      if(value.getClass().isArray()) {
         return encodeArray(value);
      }

      throw new IllegalArgumentException(
         "Cannot assign a value of type " + value.getClass().getName() + " in R");
   }

   private boolean isInteger(Object value) {
      return (value instanceof Integer) || (value instanceof Short) || (value instanceof Byte);
   }

   private boolean isNumeric(Object value) {
      return (value instanceof Double) || (value instanceof Float) || (value instanceof Long);
   }

   private String formatDate(Date date) {
      return date == null ? "" : date.toInstant().toString();
   }

   private REXP encodeArray(Object value) {
      int length = Array.getLength(value);

      if(length == 0) {
         return new REXPDouble(new double[0]);
      }

      Object element = null;

      for(int i = 0; i < length && element == null; i++) {
         element = Array.get(value, i);
      }

      if(element == null) {
         String[] values = new String[length];
         Arrays.fill(values, "");
         return new REXPString(values);
      }

      if(isInteger(element)) {
         int[] values = new int[length];

         for(int i = 0; i < length; i++) {
            Number number = (Number) Array.get(value, i);
            values[i] = number == null ? 0 : number.intValue();
         }

         return new REXPInteger(values);
      }

      if(isNumeric(element)) {
         double[] values = new double[length];

         for(int i = 0; i < length; i++) {
            Number number = (Number) Array.get(value, i);
            values[i] = number == null ? 0 : number.doubleValue();
         }

         return new REXPDouble(values);
      }

      if(element instanceof Boolean) {
         boolean[] values = new boolean[length];

         for(int i = 0; i < length; i++) {
            values[i] = Boolean.TRUE.equals(Array.get(value, i));
         }

         return new REXPLogical(values);
      }

      if(Tool.isDateClass(element.getClass())) {
         String[] values = new String[length];

         for(int i = 0; i < length; i++) {
            values[i] = formatDate((Date) Array.get(value, i));
         }

         return new REXPString(values);
      }

      if(element instanceof String) {
         String[] values = new String[length];

         for(int i = 0; i < length; i++) {
            String string = (String) Array.get(value, i);
            values[i] = string == null ? "" : string;
         }

         return new REXPString(values);
      }

      throw new IllegalArgumentException(
         "Cannot assign a value of type " + element.getClass().getName() + " in R");
   }
}
