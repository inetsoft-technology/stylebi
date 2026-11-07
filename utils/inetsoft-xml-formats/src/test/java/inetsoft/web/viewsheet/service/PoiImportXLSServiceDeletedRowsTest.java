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
import inetsoft.web.viewsheet.controller.table.BaseTableLoadDataService;
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

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Bug 80481. How a row deleted in the Excel file is found again in the {@code _Backup} sheet.
 *
 * <p>Two defects of {@code PoiImportXLSService.getDeleteRows}, both reproduced through the
 * pre-existing {@code updateViewsheet} entry point:
 * <ul>
 * <li>it matched backup names with {@code startsWith(<table name>)}, so a table whose name is a
 * prefix of another's ({@code Orders} / {@code OrdersByRegion}, {@code Table1} / {@code Table10})
 * took the other table's deleted rows as its own;</li>
 * <li>it required the backup name to end in {@code _TR}, but the offline exporter writes the backup
 * copy of every row name with the {@code _1} suffix {@code getNextCellName} adds to a name the main
 * sheet already used, so no deleted row of a real export was ever found.</li>
 * </ul>
 * </p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PoiImportXLSServiceDeletedRowsTest {
   private static final String[] HEADER = {"Region", "Amount"};

   @TempDir
   Path dir;

   private MockedStatic<VSEventUtil> eventUtil;
   private MockedStatic<BaseTableLoadDataService> loadData;

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

   private static Object[][] rows(String... names) {
      Object[][] rows = new Object[names.length][];

      for(int i = 0; i < names.length; i++) {
         rows[i] = new Object[] {names[i], i + 1};
      }

      return rows;
   }

   private void importInto(Path file, RuntimeViewsheet rvs) throws Exception {
      new PoiImportXLSService().updateViewsheet(
         file, "xlsx", rvs, "", mock(CommandDispatcher.class), mock(CoreLifecycleService.class),
         ExcelOfflineFixture.catalog(), new ArrayList<>(), new HashSet<>());
   }

   /** A real export reserves the deleted row's name in the main sheet, so the backup's gets _1. */
   @Test
   void aRowDeletedFromARealExportIsFound() throws Exception {
      Object[][] original = rows("East", "West", "North");
      Object[][] sheet = {original[0], null, original[2]};

      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();
      fx.writeTable("Orders", 2, 1, HEADER, original, sheet);
      Path file = fx.save(dir, "deleted.xlsx");

      FormTableLens lens = ExcelOfflineFixture.lens(HEADER, original);
      importInto(file, ExcelOfflineFixture.runtime(
         Map.of("Orders", lens), ExcelOfflineFixture.table("Orders", null)));

      FormTableRow[] deleted = lens.rows(FormTableRow.DELETED);
      assertEquals(1, deleted.length);
      assertEquals("West", deleted[0].get(0));
      assertEquals(0, ExcelOfflineFixture.count(lens, FormTableRow.CHANGED));
   }

   /** Orders is read first, while OrdersByRegion's backup names are still in the shared map. */
   @Test
   void aPrefixNamedTableDoesNotTakeAnotherTablesDeletedRow() throws Exception {
      assertPrefixPairKeepsDeletesApart(false);
   }

   @Test
   void aPrefixNamedTableDoesNotTakeAnotherTablesDeletedRowInLegacyBackupNames() throws Exception {
      assertPrefixPairKeepsDeletesApart(true);
   }

   /**
    * Orders_1's row names (Orders_1_row_state_TR_1) and Orders' (Orders_row_state_TR_1) can be
    * told apart only by counting the numbers after the table name. Orders_1 is read first, while
    * Orders' backup names are still in the shared map.
    */
   @Test
   void aTableNamedLikeAnotherPlusASuffixDoesNotTakeItsDeletedRow() throws Exception {
      Object[][] orders = rows("East", "West");
      Object[][] suffixed = rows("Alpha", "Beta");

      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();
      fx.writeTable("Orders_1", 2, 1, HEADER, suffixed, suffixed);
      // West is deleted from Orders only
      fx.writeTable("Orders", 2, 6, HEADER, orders, new Object[][] {orders[0], null});
      Path file = fx.save(dir, "suffix.xlsx");

      FormTableLens suffixedLens = ExcelOfflineFixture.lens(HEADER, suffixed);
      FormTableLens ordersLens = ExcelOfflineFixture.lens(HEADER, orders);
      importInto(file, ExcelOfflineFixture.runtime(
         Map.of("Orders_1", suffixedLens, "Orders", ordersLens),
         ExcelOfflineFixture.table("Orders_1", null),
         ExcelOfflineFixture.table("Orders", null)));

      assertEquals(0, suffixedLens.rows(FormTableRow.DELETED).length,
                   "Orders_1 lost no row; it must not inherit Orders'");
      FormTableRow[] deleted = ordersLens.rows(FormTableRow.DELETED);
      assertEquals(1, deleted.length);
      assertEquals("West", deleted[0].get(0));
   }

   private void assertPrefixPairKeepsDeletesApart(boolean legacyNames) throws Exception {
      Object[][] orders = rows("East", "West");
      Object[][] byRegion = rows("Alpha", "Beta", "Gamma");

      ExcelOfflineFixture fx = new ExcelOfflineFixture().withBackupSheet();

      if(legacyNames) {
         fx.withLegacyBackupNames();
      }

      fx.writeTable("Orders", 2, 1, HEADER, orders, orders);
      // Beta is deleted from OrdersByRegion only
      fx.writeTable("OrdersByRegion", 2, 6, HEADER, byRegion,
                    new Object[][] {byRegion[0], null, byRegion[2]});
      Path file = fx.save(dir, "prefix.xlsx");

      FormTableLens ordersLens = ExcelOfflineFixture.lens(HEADER, orders);
      FormTableLens regionLens = ExcelOfflineFixture.lens(HEADER, byRegion);
      importInto(file, ExcelOfflineFixture.runtime(
         Map.of("Orders", ordersLens, "OrdersByRegion", regionLens),
         ExcelOfflineFixture.table("Orders", null),
         ExcelOfflineFixture.table("OrdersByRegion", null)));

      assertEquals(0, ordersLens.rows(FormTableRow.DELETED).length,
                   "Orders lost no row; it must not inherit OrdersByRegion's");
      FormTableRow[] deleted = regionLens.rows(FormTableRow.DELETED);
      assertEquals(1, deleted.length);
      assertEquals("Beta", deleted[0].get(0));
   }
}
