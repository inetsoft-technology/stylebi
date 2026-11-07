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
package inetsoft.web.viewsheet.service;

import inetsoft.report.composition.FormTableLens;
import inetsoft.report.composition.FormTableRow;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.excel.ExcelContext;
import inetsoft.report.io.viewsheet.excel.OfflineExcelVSExporter;
import inetsoft.report.io.viewsheet.excel.PoiExcelVSUtil;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Catalog;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.mockito.Mockito.when;

/**
 * Builds a "Current View" + "Current View_Backup" workbook the way the offline Excel export
 * lays it out, and a mocked runtime viewsheet whose Form tables are real {@link FormTableLens}es.
 *
 * <p>The cell names are produced by the production code: the row/header names go through
 * {@link OfflineExcelVSExporter#getNextCellName} (so the backup copy of a name gets the exporter's
 * {@code _1} suffix) and {@link PoiExcelVSUtil#setCellName} (which rewrites '.' and '\' to '_').
 * What is NOT run is the exporter's table traversal -- the assembly infos are mocked, as in the
 * other tests of this module -- so the layout (header row, first data row, one cell per column) is
 * this fixture's reading of {@code OfflineExcelTableHelper.writeData}.</p>
 */
final class ExcelOfflineFixture {
   static final String CURRENT_VIEW = "Current View";

   final XSSFWorkbook book = new XSSFWorkbook();
   final Sheet main = book.createSheet(CURRENT_VIEW);
   private final OfflineExcelVSExporter exporter =
      new OfflineExcelVSExporter(Mockito.mock(ExcelContext.class), new ByteArrayOutputStream());
   private Sheet backup;
   private boolean legacyBackupNames;

   /**
    * Do not reserve the name of a row the user later deleted, so its backup name keeps the plain
    * {@code _TR} ending instead of the exporter's dedupe suffix. Only the pre-fix importer matched
    * that shape.
    */
   ExcelOfflineFixture withLegacyBackupNames() {
      legacyBackupNames = true;
      return this;
   }

   ExcelOfflineFixture withBackupSheet() {
      backup = book.createSheet(CURRENT_VIEW + "_Backup");
      book.setSheetHidden(book.getSheetIndex(backup), true);
      return this;
   }

   /**
    * Writes one table to the main sheet (and its original to the backup sheet when there is one).
    * Row {@code i} of a rows array is lens row {@code i + 1}.
    *
    * @param original the rows as the lens held them at export time
    * @param edited   the rows as they stand in the main sheet; {@code null} entries are rows the
    *                 user deleted (no cell, no name, no gap); an entry whose index is >= the
    *                 original's size is a typed-in row (unnamed)
    */
   void writeTable(String name, int top, int left, String[] header, Object[][] original,
                   Object[][] edited)
   {
      writeSheetTable(main, name, top, left, header, original, edited);

      if(backup != null) {
         writeSheetTable(backup, name, top, left, header, original, original);
      }
   }

   /**
    * Like {@link #writeTable} but the backup sheet gets nothing for this table: what an export made
    * while the table had no Form lens (a Design-tab pairing) looks like -- the sheet exists, this
    * table's rows are not in it.
    */
   void writeTableWithoutBackupRows(String name, int top, int left, String[] header,
                                    Object[][] rows)
   {
      writeSheetTable(main, name, top, left, header, rows, rows);
   }

   private void writeSheetTable(Sheet sheet, String name, int top, int left, String[] header,
                                Object[][] original, Object[][] rows)
   {
      Row hrow = PoiExcelVSUtil.getRow(top, sheet);

      for(int c = 0; c < header.length; c++) {
         hrow.createCell(left + c).setCellValue(header[c]);
      }

      PoiExcelVSUtil.setCellName(top, left, exporter.getNextCellName(name + "_Header"), sheet);

      int out = top + 1;

      for(int i = 0; i < rows.length; i++) {
         if(rows[i] == null) {
            // the export wrote this row (and so reserved its name) before the user deleted it
            if(!legacyBackupNames) {
               exporter.getNextCellName(name + "." + (i + 1) + "\\" + 1 + "_TR");
            }

            continue;
         }

         Row row = PoiExcelVSUtil.getRow(out, sheet);

         for(int c = 0; c < rows[i].length; c++) {
            put(row.createCell(left + c), rows[i][c]);
         }

         if(i < original.length) {
            // OfflineExcelTableHelper: name + "." + irow + "\\" + state + "_TR"; a row that was
            // not changed at export time has state OLD (1)
            PoiExcelVSUtil.setCellName(
               out, left, exporter.getNextCellName(name + "." + (i + 1) + "\\" + 1 + "_TR"), sheet);
         }

         out++;
      }
   }

   private static void put(Cell cell, Object value) {
      if(value instanceof Number n) {
         cell.setCellValue(n.doubleValue());
      }
      else if(value != null) {
         cell.setCellValue(String.valueOf(value));
      }
   }

   Path save(Path dir, String fileName) throws IOException {
      Path file = dir.resolve(fileName);

      try(var out = Files.newOutputStream(file)) {
         book.write(out);
      }

      return file;
   }

   /** A real lens over {@code header} + {@code rows}. */
   static FormTableLens lens(String[] header, Object[][] rows) {
      Object[][] all = new Object[rows.length + 1][];
      all[0] = header;
      System.arraycopy(rows, 0, all, 1, rows.length);
      DefaultTableLens base = new DefaultTableLens(all);
      base.setHeaderRowCount(1);
      return new FormTableLens(base);
   }

   static TableVSAssembly table(String name, ColumnSelection columns) {
      TableVSAssemblyInfo info = Mockito.mock(TableVSAssemblyInfo.class);
      when(info.isVisible(true)).thenReturn(true);
      when(info.isForm()).thenReturn(true);
      when(info.getAbsoluteName()).thenReturn(name);
      when(info.getColumnSelection()).thenReturn(columns == null ? new ColumnSelection() : columns);
      TableVSAssembly table = Mockito.mock(TableVSAssembly.class);
      when(table.getAbsoluteName()).thenReturn(name);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      return table;
   }

   static VSAssembly invisibleTable(String name) {
      TableVSAssemblyInfo info = Mockito.mock(TableVSAssemblyInfo.class);
      when(info.isVisible(true)).thenReturn(false);
      when(info.isForm()).thenReturn(true);
      TableVSAssembly table = Mockito.mock(TableVSAssembly.class);
      when(table.getAbsoluteName()).thenReturn(name);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      return table;
   }

   static VSAssembly chart(String name) {
      ChartVSAssembly chart = Mockito.mock(ChartVSAssembly.class);
      VSAssemblyInfo info = Mockito.mock(VSAssemblyInfo.class);
      when(info.isVisible(true)).thenReturn(true);
      when(chart.getAbsoluteName()).thenReturn(name);
      when(chart.getVSAssemblyInfo()).thenReturn(info);
      return chart;
   }

   /** A runtime viewsheet whose sandbox returns {@code lenses} by assembly name. */
   static RuntimeViewsheet runtime(Map<String, FormTableLens> lenses, Assembly... assemblies)
      throws Exception
   {
      Viewsheet vs = Mockito.mock(Viewsheet.class);
      when(vs.getAssemblies()).thenReturn(assemblies);
      ViewsheetSandbox box = Mockito.mock(ViewsheetSandbox.class);

      for(Map.Entry<String, FormTableLens> e : lenses.entrySet()) {
         when(box.getFormTableLens(e.getKey())).thenReturn(e.getValue());
      }

      RuntimeViewsheet rvs = Mockito.mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(rvs.isRuntime()).thenReturn(true);
      return rvs;
   }

   static Catalog catalog() {
      Catalog catalog = Mockito.mock(Catalog.class);
      when(catalog.getString("Current View")).thenReturn(CURRENT_VIEW);
      return catalog;
   }

   static int count(FormTableLens lens, int state) {
      int n = 0;

      for(FormTableRow r : lens.rows()) {
         if(r.getRowState() == state) {
            n++;
         }
      }

      return n;
   }
}
