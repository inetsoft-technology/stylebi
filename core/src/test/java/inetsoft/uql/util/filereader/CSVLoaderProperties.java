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
package inetsoft.uql.util.filereader;

import inetsoft.uql.schema.XSchema;
import inetsoft.uql.table.XSwappableTable;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Invariants of delimited file import ({@link CSVLoader#readCSV}, as called by the worksheet
 * CSV import dialog), shared by {@code CSVLoaderSeedTest} and the enterprise fuzzer
 * ({@code test/fuzzer}).
 * <p>
 * An input is the raw file content. {@link #check(byte[])} loads it with type detection on,
 * with and without quote removal, and verifies that:
 * <ol>
 *    <li>loading never throws;</li>
 *    <li>there are no more data rows than lines in the file;</li>
 *    <li>every detected column type has a matching value class: each non-null value in a
 *        numeric column is a {@link Number}, in a date column a {@link Date}, in a boolean
 *        column a {@link Boolean} and in a string column a {@link String}.</li>
 * </ol>
 */
public final class CSVLoaderProperties {
   private CSVLoaderProperties() {
   }

   public static void check(byte[] content) throws Exception {
      if(content.length > MAX_INPUT) {
         return;
      }

      File file = File.createTempFile("csv-fuzz", ".csv");

      try {
         Files.write(file.toPath(), content);
         load(file, content, true);
         load(file, content, false);
      }
      finally {
         Files.deleteIfExists(file.toPath());
      }
   }

   private static void load(File file, byte[] content, boolean removeQuote) {
      String where = "removeQuote=" + removeQuote;
      List<String> types = new ArrayList<>();
      XSwappableTable table;

      try {
         table = CSVLoader.readCSV(file, "UTF-8", removeQuote, ",", true, false,
                                   new HashMap<>(), types, true, null, 50000, 0, -1,
                                   new DateParseInfo());
      }
      catch(Throwable ex) {
         throw new AssertionError("Loading (" + where + ") threw " + ex, ex);
      }

      try {
         table.moreRows(Integer.MAX_VALUE);
         int rows = table.getRowCount() - 1;
         int lines = lineCount(content);

         if(rows > lines) {
            throw new AssertionError("Loaded " + rows + " data rows from " + lines +
                                     " lines (" + where + ")");
         }

         for(int c = 0; c < table.getColCount(); c++) {
            String type = c < types.size() ? types.get(c) : null;

            for(int r = 1; r <= rows; r++) {
               Object value = table.getObject(r, c);

               if(value != null && !matches(type, value)) {
                  throw new AssertionError(
                     "Column " + c + " was detected as " + type + " but row " + r + " holds " +
                     value.getClass().getName() + " \"" + value + "\" (" + where + ")");
               }
            }
         }
      }
      finally {
         table.dispose();
      }
   }

   private static boolean matches(String type, Object value) {
      if(type == null) {
         return true;
      }
      else if(XSchema.isNumericType(type)) {
         return value instanceof Number;
      }
      else if(XSchema.isDateType(type)) {
         return value instanceof Date;
      }
      else if(XSchema.BOOLEAN.equals(type)) {
         return value instanceof Boolean;
      }
      else if(XSchema.STRING.equals(type)) {
         return value instanceof String;
      }

      return true;
   }

   private static int lineCount(byte[] content) {
      int lines = 1;

      for(byte b : content) {
         if(b == '\n' || b == '\r') {
            lines++;
         }
      }

      return lines;
   }

   public static final int MAX_INPUT = 4000;
}
