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
import inetsoft.report.TableFilter;
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

         names.append(listed++ == 0 ? "" : ", ").append(columnName(inner, headerRows, col));
      }

      return List.of(cells + " cell(s) in column(s) " + names + " were cut to " + cap +
         " characters + '...' because this organization's max.cell.size is " + cap +
         "; raise max.cell.size to export them whole");
   }

   /**
    * Scans a viewsheet table assembly's lens (Bug #80182). A viewsheet table is cut at more than
    * one layer: the worksheet query layer wraps the base table in a
    * {@link TextSizeLimitTableLens}, and the viewsheet sandbox wraps the result again, so the
    * outermost layer's inner table already holds the cut text. The scan therefore follows the
    * {@link TableFilter} chain down to the deepest text-limit layer and reads that layer's inner
    * table, which still holds the uncut values. The base table may hold rows a viewsheet filter
    * later drops, so the warning names columns only, not a cell count.
    *
    * @param assemblyName name of the exported table assembly, shown in the warning.
    * @param lens         the lens the export writes (a viewsheet table lens).
    * @param cap          {@code Util.getOrganizationMaxCellSize()}; {@code <= 0} disables it.
    * @return zero or one human-readable warning.
    */
   public static List<String> scanViewsheetTable(String assemblyName, TableLens lens, int cap) {
      if(cap <= 0 || lens == null) {
         return List.of();
      }

      Set<String> columns = new LinkedHashSet<>();
      Set<TableLens> visited = Collections.newSetFromMap(new IdentityHashMap<>());
      TextSizeLimitTableLens deepest = null;
      TableLens current = lens;

      while(current != null && visited.add(current)) {
         if(current instanceof TextSizeLimitTableLens limited && limited.getTable() != null) {
            deepest = limited;
         }

         current = current instanceof TableFilter filter ? filter.getTable() : null;
      }

      // only the deepest layer: reading through an outer layer would run its own cut (and its
      // user message) again, and the deepest layer is the one whose inner table is uncut
      if(deepest != null) {
         collectCutColumns(deepest.getTable(), cap, columns);
      }

      if(columns.isEmpty()) {
         return List.of();
      }

      StringBuilder names = new StringBuilder();
      int listed = 0;

      for(String name : columns) {
         if(listed == MAX_LISTED_COLUMNS) {
            names.append(", ...");
            break;
         }

         names.append(listed++ == 0 ? "" : ", ").append(name);
      }

      return List.of("Text in column(s) " + names + " of table " + assemblyName +
         " was cut to " + cap + " characters + '...' because this organization's " +
         "max.cell.size is " + cap + "; raise max.cell.size to export it whole");
   }

   private static void collectCutColumns(TableLens inner, int cap, Set<String> columns) {
      int headerRows = inner.getHeaderRowCount();
      int colCount = inner.getColCount();
      Set<Integer> cutColumns = new TreeSet<>();

      for(int row = headerRows; inner.moreRows(row); row++) {
         for(int col = 0; col < colCount; col++) {
            if(!cutColumns.contains(col) && inner.getObject(row, col) instanceof String s &&
               s.length() > cap)
            {
               cutColumns.add(col);
            }
         }
      }

      for(int col : cutColumns) {
         columns.add(columnName(inner, headerRows, col));
      }
   }

   private static String columnName(TableLens inner, int headerRows, int col) {
      String name = headerRows > 0 ? Tool.toString(inner.getObject(0, col)) : null;
      name = name == null || name.isEmpty() ? "#" + (col + 1) : name;

      if(name.length() > MAX_COLUMN_NAME_LENGTH) {
         name = name.substring(0, MAX_COLUMN_NAME_LENGTH) + "...";
      }

      return name;
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
