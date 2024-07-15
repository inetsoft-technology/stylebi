/*
 * inetsoft-r - StyleBI is a business intelligence web application.
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
package com.inetsoft.connectors.script;

import inetsoft.report.script.TableArray;
import inetsoft.uql.table.XSwappableTable;
import org.rosuda.REngine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class REXPDecoder {
   public Object decode(REXP expr)  {
      try {
         if(expr == null) {
            return null;
         }

         if(expr.isReference()) {
            expr = ((REXPReference) expr).resolve();
         }

         if(expr.isNull()) {
            return null;
         }

         if(expr.isInteger()) {
            return decodeInteger(expr);
         }

         if(expr.isNumeric()) {
            return decodeDouble(expr);
         }

         if(expr.isLogical()) {
            return decodeLogical(expr);
         }

         if(expr.isString()) {
            return decodeString(expr);
         }

         if(expr.isFactor()) {
            return decodeFactor(expr);
         }

         if(expr.isList()) {
            return decodeList(expr);
         }

         LOG.warn("Unable to convert R type {} to JavaScript\n{}", expr.getClass().getSimpleName(), expr);
         return null;
      }
      catch(REXPMismatchException e) {
         throw new RuntimeException("Failed to convert R expression", e);
      }
   }

   private Object decodeInteger(REXP expr) throws REXPMismatchException {
      if(expr.length() == 1) {
         return expr.asInteger();
      }
      else {
         return expr.asIntegers();
      }
   }

   private Object decodeDouble(REXP expr) throws REXPMismatchException {
      if(expr.length() == 1) {
         return expr.asDouble();
      }
      else {
         return expr.asDoubles();
      }
   }

   private Object decodeLogical(REXP expr) throws REXPMismatchException {
      boolean[] value = ((REXPLogical) expr).isTRUE();

      if(expr.length() == 1) {
         return value[0];
      }
      return value;
   }

   private Object decodeString(REXP expr) throws REXPMismatchException {
      if(expr.length() == 1) {
         return expr.asString();
      }
      else {
         return expr.asStrings();
      }
   }

   private Object decodeFactor(REXP expr) throws REXPMismatchException {
      return new RFactorScriptable(expr.asFactor());
   }

   private Object decodeList(REXP expr) throws REXPMismatchException {
      RList list = expr.asList();
      List<String> headers = new ArrayList<>();
      List<Object> columns = new ArrayList<>();
      int rowCount = Integer.MAX_VALUE;

      for(String name : list.keys()) {
         Object column = list.at(name).asNativeJavaObject();

         if(column != null && column.getClass().isArray()) {
            headers.add(name);
            columns.add(column);
            rowCount = Math.min(rowCount, Array.getLength(column));
         }
      }

      int colCount = headers.size();
      XSwappableTable table = new XSwappableTable(colCount, true);
      table.addRow(headers.toArray(new Object[0]));

      for(int r = 0; r < rowCount; r++) {
         Object[] row = new Object[colCount];

         for(int c = 0; c < colCount; c++) {
            row[c] = Array.get(columns.get(c), r);
         }

         table.addRow(row);
      }

      table.complete();
      return new TableArray(table);
   }

   private static final Logger LOG = LoggerFactory.getLogger(REXPDecoder.class);
}
