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

package inetsoft.web.wiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.TableLens;
import inetsoft.report.lens.TextSizeLimitTableLens;
import inetsoft.util.Tool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Detects string cells that the organization's {@code max.cell.size} cap cuts when a worksheet
 * table is written out (Bug #79596). The cap is applied by {@link TextSizeLimitTableLens}, the
 * outermost wrapper of every sandbox table lens, so a CSV export silently writes the first
 * {@code max.cell.size} characters plus "..." for any longer cell. The scan reads the wrapper's
 * inner table, which still holds the full value, so it needs no guess about a trailing "...".
 */
public final class ExportCellCapScanner {
   /** Name of the response header that carries the URL-encoded JSON array of warnings. */
   public static final String WARNINGS_HEADER = "X-Wiz-Export-Warnings";

   private static final int MAX_LISTED_COLUMNS = 10;
   private static final int MAX_COLUMN_NAME_LENGTH = 40;
   private static final int MAX_HEADER_LENGTH = 4000;
   private static final ObjectMapper MAPPER = new ObjectMapper();

   private ExportCellCapScanner() {
   }

   /**
    * Scans the rows an export will write (header rows excluded) for string cells longer than the
    * cap.
    *
    * @param lens    the lens the export writes; rows must already be materialized.
    * @param cap     {@code Util.getOrganizationMaxCellSize()}; a value {@code <= 0} disables the
    *                cap, so nothing is reported.
    * @return zero or one human-readable warning.
    */
   public static List<String> scan(TableLens lens, int cap) {
      if(cap <= 0 || !(lens instanceof TextSizeLimitTableLens limited) ||
         limited.getTable() == null)
      {
         return List.of();
      }

      TableLens inner = limited.getTable();
      int headerRows = inner.getHeaderRowCount();
      int rowCount = lens.getRowCount();
      int colCount = inner.getColCount();
      Set<Integer> columns = new LinkedHashSet<>();
      long cells = 0;

      for(int row = headerRows; row < rowCount; row++) {
         for(int col = 0; col < colCount; col++) {
            Object value = inner.getObject(row, col);

            if(value instanceof String s && s.length() > cap) {
               cells++;
               columns.add(col);
            }
         }
      }

      if(cells == 0) {
         return List.of();
      }

      StringBuilder names = new StringBuilder();
      int listed = 0;

      for(int col : columns) {
         if(listed == MAX_LISTED_COLUMNS) {
            names.append(", ...");
            break;
         }

         String name = headerRows > 0 ? Tool.toString(inner.getObject(0, col)) : null;
         name = name == null || name.isEmpty() ? "#" + (col + 1) : name;

         if(name.length() > MAX_COLUMN_NAME_LENGTH) {
            name = name.substring(0, MAX_COLUMN_NAME_LENGTH) + "...";
         }

         names.append(listed++ == 0 ? "" : ", ").append(name);
      }

      return List.of(cells + " cell(s) in column(s) " + names + " were cut to " + cap +
         " characters + '...' because this organization's max.cell.size is " + cap +
         "; raise max.cell.size to export them whole");
   }

   /**
    * Encodes warnings for {@link #WARNINGS_HEADER}: a URL-encoded JSON array, ASCII-safe and
    * kept under a size a servlet container accepts. Returns null when there is nothing to send.
    */
   public static String toHeaderValue(List<String> warnings) {
      if(warnings == null || warnings.isEmpty()) {
         return null;
      }

      try {
         String encoded = URLEncoder.encode(MAPPER.writeValueAsString(warnings),
            StandardCharsets.UTF_8).replace("+", "%20");

         if(encoded.length() > MAX_HEADER_LENGTH) {
            encoded = URLEncoder.encode(MAPPER.writeValueAsString(List.of(
               "Some cells were cut to this organization's max.cell.size; raise max.cell.size " +
               "to export them whole")), StandardCharsets.UTF_8).replace("+", "%20");
         }

         return encoded;
      }
      catch(JsonProcessingException e) {
         return null;
      }
   }
}
