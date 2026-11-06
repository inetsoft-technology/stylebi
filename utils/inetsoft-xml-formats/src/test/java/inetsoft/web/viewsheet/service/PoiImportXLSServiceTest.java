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
import inetsoft.web.viewsheet.service.ImportXLSInspection.TableCheck;
import inetsoft.web.viewsheet.service.ImportXLSResult.Outcome;
import inetsoft.web.viewsheet.service.ImportXLSResult.TableResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug 80481. {@link PoiImportXLSService#inspect} and {@link PoiImportXLSService#importWorkbook}:
 * the pre-check and the per-table result the wiz agent endpoint is built on.
 *
 * <p>The workbooks are written by {@link ExcelOfflineFixture}: production code names the cells,
 * the table layout is the fixture's reading of the offline exporter (see its javadoc). Not run:
 * the real exporter over a real runtime viewsheet.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PoiImportXLSServiceTest {
   private static final String[] HEADER = {"Region", "Amount"};

   @TempDir
   Path dir;

   private MockedStatic<VSEventUtil> eventUtil;
   private MockedStatic<BaseTableLoadDataService> loadData;
   private final PoiImportXLSService service = new PoiImportXLSService();

   @BeforeEach
   void stubViewerRefresh() {
      eventUtil = Mockito.mockStatic(VSEventUtil.class);
      loadData = Mockito.mockStatic(BaseTableLoadDataService.class);
   }

   @AfterEach
   void release() {
      eventUtil.close();
      loadData.close();
   }

   private static Object[][] original() {
      return new Object[][] {{"East", 1}, {"West", 2}, {"North", 3}, {"South", 4}};
   }

   private ImportXLSResult importFile(Path file, RuntimeViewsheet rvs) throws Exception {
      return service.importWorkbook(file, "xlsx", rvs, "", mock(CommandDispatcher.class),
                                    mock(CoreLifecycleService.class),
                                    ExcelOfflineFixture.catalog());
   }

   private ImportXLSInspection inspect(Path file, RuntimeViewsheet rvs) throws Exception {
      return service.inspect(file, "xlsx", rvs, ExcelOfflineFixture.catalog());
   }

   private static TableResult table(ImportXLSResult result, String name) {
      return result.tables().stream().filter(t -> t.assembly().equals(name)).findFirst()
         .orElseThrow(() -> new AssertionError("no result for " + name + ": " + result.tables()));
   }

   private static TableCheck check(ImportXLSInspection inspection, String name) {
      return inspection.tables().stream().filter(t -> t.name().equals(name)).findFirst()
         .orElseThrow(() -> new AssertionError("no check for " + name));
   }

   // --- importWorkbook ---------------------------------------------------------------------

   @Test
   void everyKindOfEditIsCountedPerTable() throws Exception {
      Object[][] o = original();
      // East untouched, West edited, North deleted, South untouched, one row typed in
      Object[][] sheet = {o[0], {"West", 20}, null, o[3], {"Central", 5}};

      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();
      fx.writeTable("Orders", 2, 1, HEADER, o, sheet);
      Path file = fx.save(dir, "round-trip.xlsx");

      FormTableLens lens = ExcelOfflineFixture.lens(HEADER, o);
      ImportXLSResult result = importFile(file, ExcelOfflineFixture.runtime(
         Map.of("Orders", lens), ExcelOfflineFixture.table("Orders", null)));

      TableResult orders = table(result, "Orders");
      assertEquals(Outcome.IMPORTED, orders.outcome());
      assertEquals(1, orders.added());
      assertEquals(1, orders.changed());
      assertEquals(2, orders.unchanged());
      assertEquals(1, orders.deleted());
      assertEquals(4, orders.rowCount(), "East, West, South, Central; North is pending delete");

      // the result is the lens's own state, not a second opinion
      assertEquals(1, ExcelOfflineFixture.count(lens, FormTableRow.ADDED));
      assertEquals(1, ExcelOfflineFixture.count(lens, FormTableRow.CHANGED));
      assertEquals(1, lens.rows(FormTableRow.DELETED).length);
      assertTrue(result.notFound().isEmpty());
      assertTrue(result.notInRange().isEmpty());
   }

   @Test
   void aTableTheFileLacksIsNotFoundAndLeftAlone() throws Exception {
      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();
      fx.writeTable("Orders", 2, 1, HEADER, original(), original());
      Path file = fx.save(dir, "one-table.xlsx");

      FormTableLens orders = ExcelOfflineFixture.lens(HEADER, original());
      FormTableLens other = ExcelOfflineFixture.lens(HEADER, original());
      ImportXLSResult result = importFile(file, ExcelOfflineFixture.runtime(
         Map.of("Orders", orders, "Other", other),
         ExcelOfflineFixture.table("Orders", null), ExcelOfflineFixture.table("Other", null)));

      assertEquals(Outcome.NOT_IN_FILE, table(result, "Other").outcome());
      assertEquals(List.of("Other"), result.notFound());
      assertFalse(other.isChanged());
   }

   @Test
   void aNonTableAssemblyNeverShowsUpInNotFound() throws Exception {
      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();
      fx.writeTable("Orders", 2, 1, HEADER, original(), original());
      Path file = fx.save(dir, "chart.xlsx");

      ImportXLSResult result = importFile(file, ExcelOfflineFixture.runtime(
         Map.of("Orders", ExcelOfflineFixture.lens(HEADER, original())),
         ExcelOfflineFixture.table("Orders", null), ExcelOfflineFixture.chart("Chart1")));

      assertTrue(result.notFound().isEmpty());
      assertEquals(1, result.tables().size());
   }

   @Test
   void aTableInTheFileWithoutAFormLensIsNotAFormTable() throws Exception {
      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();
      fx.writeTable("Orders", 2, 1, HEADER, original(), original());
      Path file = fx.save(dir, "nonform.xlsx");

      ImportXLSResult result = importFile(file, ExcelOfflineFixture.runtime(
         Map.of(), ExcelOfflineFixture.table("Orders", null)));

      assertEquals(Outcome.NOT_A_FORM_TABLE, table(result, "Orders").outcome());
   }

   @Test
   void aHiddenFormTableIsReportedNotSilentlySkipped() throws Exception {
      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();
      fx.writeTable("Orders", 2, 1, HEADER, original(), original());
      Path file = fx.save(dir, "hidden.xlsx");

      ImportXLSResult result = importFile(file, ExcelOfflineFixture.runtime(
         Map.of("Orders", ExcelOfflineFixture.lens(HEADER, original())),
         ExcelOfflineFixture.table("Orders", null), ExcelOfflineFixture.invisibleTable("Hidden")));

      assertEquals(Outcome.SKIPPED_INVISIBLE, table(result, "Hidden").outcome());
   }

   @Test
   void aColumnOptionViolationIsNotInRangeAndLeavesTheTableAlone() throws Exception {
      FormRef region = new FormRef();
      region.setDataRef(new AttributeRef(null, "Region"));
      FormRef amount = new FormRef();
      amount.setDataRef(new AttributeRef(null, "Amount"));
      amount.setOption(new IntegerColumnOption(10, 0, "0..10", true));
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(region);
      columns.addAttribute(amount);

      Object[][] o = original();
      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();
      fx.writeTable("Orders", 2, 1, HEADER, o, new Object[][] {o[0], {"West", 99}, o[2], o[3]});
      Path file = fx.save(dir, "range.xlsx");

      FormTableLens lens = ExcelOfflineFixture.lens(HEADER, o);
      ImportXLSResult result = importFile(file, ExcelOfflineFixture.runtime(
         Map.of("Orders", lens), ExcelOfflineFixture.table("Orders", columns)));

      assertEquals(Outcome.NOT_IN_RANGE, table(result, "Orders").outcome());
      assertEquals(Set.of("Orders"), result.notInRange());
      assertFalse(lens.isChanged());
   }

   @Test
   void theFileCanBeDeletedAfterwardsEvenOnWindows() throws Exception {
      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();
      fx.writeTable("Orders", 2, 1, HEADER, original(), original());
      Path file = fx.save(dir, "delete-me.xlsx");
      RuntimeViewsheet rvs = ExcelOfflineFixture.runtime(
         Map.of("Orders", ExcelOfflineFixture.lens(HEADER, original())),
         ExcelOfflineFixture.table("Orders", null));

      inspect(file, rvs);
      Files.delete(file);

      Path second = fx.save(dir, "delete-me-too.xlsx");
      importFile(second, rvs);
      Files.delete(second);
   }

   @Test
   void aWorkbookWithoutTheCurrentViewSheetCannotBeImported() throws Exception {
      Path file = dir.resolve("other.xlsx");

      try(XSSFWorkbook other = new XSSFWorkbook(); var out = Files.newOutputStream(file)) {
         other.createSheet("Sheet1");
         other.write(out);
      }

      RuntimeViewsheet rvs = ExcelOfflineFixture.runtime(
         Map.of(), ExcelOfflineFixture.table("Orders", null));
      assertThrows(FileNotFoundException.class, () -> importFile(file, rvs));
   }

   // --- inspect ----------------------------------------------------------------------------

   @Test
   void inspectReportsAnUnreadableFileWithoutThrowing() throws Exception {
      Path file = dir.resolve("junk.xlsx");
      Files.writeString(file, "not a workbook");

      ImportXLSInspection inspection = inspect(file, ExcelOfflineFixture.runtime(Map.of()));

      assertFalse(inspection.readable());
      assertFalse(inspection.hasCurrentView());
      assertTrue(inspection.tables().isEmpty());
   }

   @Test
   void inspectSeparatesAMissingCurrentViewFromAMissingBackup() throws Exception {
      Path noView = dir.resolve("noview.xlsx");

      try(XSSFWorkbook other = new XSSFWorkbook(); var out = Files.newOutputStream(noView)) {
         other.createSheet("Sheet1");
         other.write(out);
      }

      ImportXLSInspection a = inspect(noView, ExcelOfflineFixture.runtime(Map.of()));
      assertTrue(a.readable());
      assertFalse(a.hasCurrentView());

      ExcelOfflineFixture fx = new ExcelOfflineFixture();
      fx.writeTable("Orders", 2, 1, HEADER, original(), original());
      ImportXLSInspection b = inspect(fx.save(dir, "nobackup.xlsx"), ExcelOfflineFixture.runtime(
         Map.of("Orders", ExcelOfflineFixture.lens(HEADER, original())),
         ExcelOfflineFixture.table("Orders", null)));
      assertTrue(b.hasCurrentView());
      assertFalse(b.hasBackup());
      TableCheck orders = check(b, "Orders");
      assertEquals(4, orders.namedRows());
      assertEquals(4, orders.unbackedRows(), "no backup sheet: every named row would become ADDED");
   }

   /**
    * The workbook-level "has a _Backup sheet" is not enough: the sheet is created for any export of
    * "Current View", but gets rows only for tables that had a Form lens at export time.
    */
   @Test
   void theBackupCheckIsPerTable() throws Exception {
      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();
      fx.writeTable("Orders", 2, 1, HEADER, original(), original());
      fx.writeTableWithoutBackupRows("Returns", 2, 6, HEADER, original());
      Path file = fx.save(dir, "per-table.xlsx");

      ImportXLSInspection inspection = inspect(file, ExcelOfflineFixture.runtime(
         Map.of("Orders", ExcelOfflineFixture.lens(HEADER, original()),
                "Returns", ExcelOfflineFixture.lens(HEADER, original())),
         ExcelOfflineFixture.table("Orders", null), ExcelOfflineFixture.table("Returns", null)));

      assertTrue(inspection.hasBackup(), "the sheet exists");
      assertEquals(0, check(inspection, "Orders").unbackedRows());
      assertEquals(4, check(inspection, "Orders").namedRows());
      assertEquals(4, check(inspection, "Returns").unbackedRows());
   }

   @Test
   void inspectMeasuresTheFilesColumnsAgainstTheLiveTable() throws Exception {
      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();
      fx.writeTable("Orders", 2, 1, HEADER, original(), original());
      Path file = fx.save(dir, "columns.xlsx");

      String[] wider = {"Region", "Amount", "Owner"};
      Object[][] widerRows = {{"East", 1, "a"}};
      ImportXLSInspection same = inspect(file, ExcelOfflineFixture.runtime(
         Map.of("Orders", ExcelOfflineFixture.lens(HEADER, original())),
         ExcelOfflineFixture.table("Orders", null)));
      ImportXLSInspection changed = inspect(file, ExcelOfflineFixture.runtime(
         Map.of("Orders", ExcelOfflineFixture.lens(wider, widerRows)),
         ExcelOfflineFixture.table("Orders", null)));

      assertEquals(4, check(same, "Orders").dataRows());
      assertEquals(4, check(same, "Orders").lensRows());
      assertEquals(2, check(same, "Orders").fileColumns());
      assertFalse(check(same, "Orders").columnCountDiffers());
      assertEquals(2, check(changed, "Orders").fileColumns());
      assertEquals(3, check(changed, "Orders").lensColumns());
      assertTrue(check(changed, "Orders").columnCountDiffers());
   }

   @Test
   void inspectReportsHiddenAndNonFormTablesAndChangesNothing() throws Exception {
      Object[][] o = original();
      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();
      fx.writeTable("Orders", 2, 1, HEADER, o, new Object[][] {o[0], {"West", 20}, o[2], o[3]});
      Path file = fx.save(dir, "inspect.xlsx");

      FormTableLens lens = ExcelOfflineFixture.lens(HEADER, o);
      RuntimeViewsheet rvs = ExcelOfflineFixture.runtime(
         Map.of("Orders", lens), ExcelOfflineFixture.table("Orders", null),
         ExcelOfflineFixture.table("Plain", null), ExcelOfflineFixture.invisibleTable("Hidden"));

      ImportXLSInspection inspection = inspect(file, rvs);

      assertTrue(check(inspection, "Orders").formTable());
      assertTrue(check(inspection, "Orders").inFile());
      assertFalse(check(inspection, "Plain").formTable());
      assertFalse(check(inspection, "Plain").inFile());
      assertFalse(check(inspection, "Hidden").visible());
      assertFalse(lens.isChanged(), "inspect never touches the table");

      // and it consumed nothing: the import that follows still sees the backup
      ImportXLSResult result = importFile(file, rvs);
      assertEquals(1, table(result, "Orders").changed());
      assertEquals(0, table(result, "Orders").added());
   }
}
