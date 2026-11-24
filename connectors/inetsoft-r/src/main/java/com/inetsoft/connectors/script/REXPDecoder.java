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

import inetsoft.report.script.TableArray;
import inetsoft.uql.table.XSwappableTable;
import org.rosuda.REngine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

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
      Object[][] cells = decodeFrame(expr.asList());
      int colCount = cells[0].length;
      XSwappableTable table = new XSwappableTable(colCount, true);

      for(Object[] row : cells) {
         table.addRow(row);
      }

      table.complete();
      return new TableArray(table);
   }

   /**
    * Converts an R data frame to cells. The first row holds the column names, the following
    * rows the data. Column values keep their R type (Date, POSIXct, logical, factor) and NA is
    * null.
    */
   static Object[][] decodeFrame(RList list) throws REXPMismatchException {
      int colCount = list.size();
      String[] headers = columnNames(list);
      Object[][] columns = new Object[colCount][];
      int rowCount = -1;

      for(int c = 0; c < colCount; c++) {
         columns[c] = decodeColumn(headers[c], list.at(c));

         if(rowCount < 0) {
            rowCount = columns[c].length;
         }
         else if(rowCount != columns[c].length) {
            throw new IllegalStateException(
               "Columns of the R result have different lengths: column '" + headers[0] +
               "' has " + rowCount + " values, column '" + headers[c] + "' has " +
               columns[c].length + " values. Return a data frame with equal-length columns.");
         }
      }

      rowCount = Math.max(rowCount, 0);
      Object[][] cells = new Object[rowCount + 1][];
      cells[0] = headers;

      for(int r = 0; r < rowCount; r++) {
         Object[] row = new Object[colCount];

         for(int c = 0; c < colCount; c++) {
            row[c] = columns[c][r];
         }

         cells[r + 1] = row;
      }

      return cells;
   }

   /**
    * Column names by position. Missing names become V1, V2...; duplicates get a numeric suffix.
    */
   static String[] columnNames(RList list) {
      int count = list.size();
      String[] names = new String[count];
      Set<String> used = new HashSet<>();

      for(int i = 0; i < count; i++) {
         String name = list.keyAt(i);

         if(name == null || name.isEmpty()) {
            name = "V" + (i + 1);
         }

         String unique = name;

         for(int n = 1; !used.add(unique); n++) {
            unique = name + "." + n;
         }

         names[i] = unique;
      }

      return names;
   }

   /**
    * Converts one column to per-row values. NA is checked before any class conversion.
    */
   static Object[] decodeColumn(String name, REXP col) throws REXPMismatchException {
      if(col == null || col.isNull()) {
         return new Object[0];
      }

      if(col.isList()) {
         throw new IllegalStateException(
            "Column '" + name + "' of the R data frame is a list column, which is not " +
            "supported. Convert it with as.character() or drop it.");
      }

      if(col.inherits("factor") && col.hasAttribute("levels") && col.isInteger()) {
         String[] levels = col.getAttribute("levels").asStrings();
         int[] codes = col.asIntegers();
         Object[] values = new Object[codes.length];

         for(int i = 0; i < codes.length; i++) {
            values[i] = codes[i] == REXPInteger.NA || codes[i] < 1 || codes[i] > levels.length ?
               null : levels[codes[i] - 1];
         }

         return values;
      }

      if(col.inherits("Date") && col.isNumeric()) {
         Double[] days = numbers(col, true);
         Object[] values = new Object[days.length];

         for(int i = 0; i < days.length; i++) {
            if(days[i] != null) {
               values[i] = java.sql.Date.valueOf(
                  LocalDate.ofEpochDay((long) Math.floor(days[i])));
            }
         }

         return values;
      }

      if(col.inherits("POSIXct") && col.isNumeric()) {
         Double[] seconds = numbers(col, true);
         Object[] values = new Object[seconds.length];

         for(int i = 0; i < seconds.length; i++) {
            if(seconds[i] != null) {
               values[i] = new Timestamp(Math.round(seconds[i] * 1000));
            }
         }

         return values;
      }

      if(col.isLogical()) {
         byte[] raw = ((REXPLogical) col).asBytes();
         Object[] values = new Object[raw.length];

         for(int i = 0; i < raw.length; i++) {
            values[i] = raw[i] == REXPLogical.NA ? null : (Object) Boolean.valueOf(raw[i] != 0);
         }

         return values;
      }

      if(col.isInteger()) {
         int[] ints = col.asIntegers();
         Object[] values = new Object[ints.length];

         for(int i = 0; i < ints.length; i++) {
            values[i] = ints[i] == REXPInteger.NA ? null : (Object) ints[i];
         }

         return values;
      }

      if(col.isNumeric()) {
         return numbers(col, false);
      }

      if(col.isString()) {
         return col.asStrings();
      }

      if(col instanceof REXPRaw) {
         byte[] bytes = ((REXPRaw) col).asBytes();
         Object[] values = new Object[bytes.length];

         for(int i = 0; i < bytes.length; i++) {
            values[i] = bytes[i] & 0xFF;
         }

         return values;
      }

      throw new IllegalStateException(
         "Column '" + name + "' of the R data frame has unsupported type " +
         col.getClass().getSimpleName() + ".");
   }

   /**
    * Numeric values with NA as null. For date-like columns NaN and Inf are also null.
    */
   private static Double[] numbers(REXP col, boolean finiteOnly) throws REXPMismatchException {
      Double[] values;

      if(col.isInteger()) {
         int[] ints = col.asIntegers();
         values = new Double[ints.length];

         for(int i = 0; i < ints.length; i++) {
            values[i] = ints[i] == REXPInteger.NA ? null : (double) ints[i];
         }
      }
      else {
         double[] doubles = col.asDoubles();
         values = new Double[doubles.length];

         for(int i = 0; i < doubles.length; i++) {
            if(REXPDouble.isNA(doubles[i]) ||
               finiteOnly && (Double.isNaN(doubles[i]) || Double.isInfinite(doubles[i])))
            {
               values[i] = null;
            }
            else {
               values[i] = doubles[i];
            }
         }
      }

      return values;
   }

   private static final Logger LOG = LoggerFactory.getLogger(REXPDecoder.class);
}
