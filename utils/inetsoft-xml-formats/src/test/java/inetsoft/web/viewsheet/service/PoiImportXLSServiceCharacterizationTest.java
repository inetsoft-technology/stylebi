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

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.FormTableLens;
import inetsoft.report.composition.FormTableRow;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.FormRef;
import inetsoft.uql.viewsheet.IntegerColumnOption;
import inetsoft.web.viewsheet.controller.table.BaseTableLoadDataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Pins what {@link PoiImportXLSService#updateViewsheet} does today (the Viewer's Import Excel
 * flow), written BEFORE it was refactored to delegate to {@code importWorkbook} so the refactor
 * has something to be held against. {@code git grep ImportXLS} in the test sources found no
 * test of this class (bug 80481, refutation objection 5).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PoiImportXLSServiceCharacterizationTest {
   private static final String[] HEADER = {"Region", "Amount"};

   @TempDir
   Path dir;

   private MockedStatic<VSEventUtil> eventUtil;
   private MockedStatic<BaseTableLoadDataService> loadData;

   @BeforeEach
   void stubViewerRefresh() {
      // the tail of updateViewsheet re-renders the table for the browser; not under test here
      eventUtil = Mockito.mockStatic(VSEventUtil.class);
      loadData = Mockito.mockStatic(BaseTableLoadDataService.class);
   }

   @AfterEach
   void release() {
      eventUtil.close();
      loadData.close();
   }

   private static Object[][] original() {
      return new Object[][] {{"East", 1}, {"West", 2}, {"North", 3}};
   }

   private Result run(Path file, String type, RuntimeViewsheet rvs) throws Exception {
      Result r = new Result();
      new PoiImportXLSService().updateViewsheet(
         file, type, rvs, "", mock(CommandDispatcher.class), mock(CoreLifecycleService.class),
         ExcelOfflineFixture.catalog(), r.assemblies, r.notInRange);
      return r;
   }

   @Test
   void editedAndTypedInRowsBecomePendingStates() throws Exception {
      // original has 3 rows; the edit changes row 2 and types a new row below them
      Object[][] sheetRows = {{"East", 1}, {"West", 20}, {"North", 3}, {"South", 4}};

      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();
      fx.writeTable("Orders", 2, 1, HEADER, original(), sheetRows);
      Path file = fx.save(dir, "edited.xlsx");

      FormTableLens lens = ExcelOfflineFixture.lens(HEADER, original());
      RuntimeViewsheet rvs = ExcelOfflineFixture.runtime(
         Map.of("Orders", lens), ExcelOfflineFixture.table("Orders", null));

      Result r = run(file, "xlsx", rvs);

      assertTrue(r.assemblies.isEmpty());
      assertTrue(r.notInRange.isEmpty());
      assertEquals(1, ExcelOfflineFixture.count(lens, FormTableRow.ADDED), "typed-in row");
      assertEquals(1, ExcelOfflineFixture.count(lens, FormTableRow.CHANGED), "edited row");
      // header row + the two untouched rows
      assertEquals(3, ExcelOfflineFixture.count(lens, FormTableRow.OLD));
      assertEquals(0, lens.rows(FormTableRow.DELETED).length);
   }

   /** Bug #74352's tolerance: no _Backup sheet means every named row is read as ADDED. */
   @Test
   void withoutBackupEveryRowBecomesAdded() throws Exception {
      ExcelOfflineFixture fx = new ExcelOfflineFixture();
      fx.writeTable("Orders", 2, 1, HEADER, original(), original());
      Path file = fx.save(dir, "nobackup.xlsx");

      FormTableLens lens = ExcelOfflineFixture.lens(HEADER, original());
      Result r = run(file, "xlsx", ExcelOfflineFixture.runtime(
         Map.of("Orders", lens), ExcelOfflineFixture.table("Orders", null)));

      assertEquals(3, ExcelOfflineFixture.count(lens, FormTableRow.ADDED));
      assertEquals(0, ExcelOfflineFixture.count(lens, FormTableRow.CHANGED));
      assertTrue(r.notInRange.isEmpty());
   }

   @Test
   void onlyTablesAbsentFromTheFileAreReportedNotFound() throws Exception {
      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();
      fx.writeTable("Orders", 2, 1, HEADER, original(), original());
      Path file = fx.save(dir, "notfound.xlsx");

      FormTableLens lens = ExcelOfflineFixture.lens(HEADER, original());
      Result r = run(file, "xlsx", ExcelOfflineFixture.runtime(
         Map.of("Orders", lens),
         ExcelOfflineFixture.table("Orders", null),
         ExcelOfflineFixture.table("Missing", null),
         ExcelOfflineFixture.chart("Chart1"),
         ExcelOfflineFixture.invisibleTable("Hidden")));

      // isAssemblyExist only tests TableVSAssembly (everything else "exists"), so the chart is
      // never listed; the invisible table is skipped silently
      assertEquals(List.of("Missing"), r.assemblies);
   }

   @Test
   void aColumnOptionViolationSkipsTheWholeTable() throws Exception {
      FormRef region = new FormRef();
      region.setDataRef(new AttributeRef(null, "Region"));
      FormRef amount = new FormRef();
      amount.setDataRef(new AttributeRef(null, "Amount"));
      amount.setOption(new IntegerColumnOption(10, 0, "0..10", true));
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(region);
      columns.addAttribute(amount);

      Object[][] sheet = {{"East", 1}, {"West", 99}, {"North", 3}};
      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();
      fx.writeTable("Orders", 2, 1, HEADER, original(), sheet);
      Path file = fx.save(dir, "range.xlsx");

      FormTableLens lens = ExcelOfflineFixture.lens(HEADER, original());
      Result r = run(file, "xlsx", ExcelOfflineFixture.runtime(
         Map.of("Orders", lens), ExcelOfflineFixture.table("Orders", columns)));

      assertEquals(Set.of("Orders"), r.notInRange);
      assertEquals(0, ExcelOfflineFixture.count(lens, FormTableRow.CHANGED));
      assertEquals(0, ExcelOfflineFixture.count(lens, FormTableRow.ADDED));
   }

   @Test
   void aWorkbookWithoutTheCurrentViewSheetThrowsFileNotFound() throws Exception {
      org.apache.poi.xssf.usermodel.XSSFWorkbook other = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
      other.createSheet("Sheet1");
      Path file = dir.resolve("other.xlsx");

      try(var out = java.nio.file.Files.newOutputStream(file)) {
         other.write(out);
      }

      assertThrows(FileNotFoundException.class, () -> run(
         file, "xlsx", ExcelOfflineFixture.runtime(Map.of(), ExcelOfflineFixture.table("Orders", null))));
   }

   @Test
   void aNonExcelFileThrowsFileNotFound() throws Exception {
      Path file = dir.resolve("junk.xlsx");
      java.nio.file.Files.writeString(file, "not a workbook");

      assertThrows(FileNotFoundException.class, () -> run(
         file, "xlsx", ExcelOfflineFixture.runtime(Map.of(), ExcelOfflineFixture.table("Orders", null))));
   }

   /** A table whose names are in the file but which has no Form lens is skipped without a word. */
   @Test
   void aNonFormTableIsSkippedSilently() throws Exception {
      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();
      fx.writeTable("Orders", 2, 1, HEADER, original(), original());
      Path file = fx.save(dir, "nonform.xlsx");

      Result r = run(file, "xlsx", ExcelOfflineFixture.runtime(
         Map.of(), ExcelOfflineFixture.table("Orders", null)));

      assertTrue(r.assemblies.isEmpty());
      assertTrue(r.notInRange.isEmpty());
   }

   private static final class Result {
      final List<String> assemblies = new ArrayList<>();
      final Set<String> notInRange = new HashSet<>();
   }
}
