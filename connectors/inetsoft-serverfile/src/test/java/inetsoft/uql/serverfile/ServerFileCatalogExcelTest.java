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
package inetsoft.uql.serverfile;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.tabular.TabularCatalog;
import inetsoft.uql.tabular.TabularColumn;
import inetsoft.uql.tabular.TabularDatasetRef;
import inetsoft.uql.tabular.TabularDatasetSchema;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers charter A3 (option C's Excel id grammar). Reading a real workbook's ROWS (not just its
 * sheet names) builds an {@code XSwappableTable}, which needs the full server-shaped Spring test
 * context {@link ServerFileSpringTestSupport}'s class javadoc explains -- the same
 * {@code BaseTestConfiguration}/{@code SwapperTestConfiguration} shape {@code ODataCatalogCacheTest}
 * and others already use, plus this run's own {@code Config} bean.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  ServerFileSpringTestSupport.ConfigBeanConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class ServerFileCatalogExcelTest {
   @TempDir
   File root;

   private ServerFileDataSource ds;

   @BeforeEach
   void setUp() {
      ds = new ServerFileDataSource();
      ds.setName("excel-test-ds");
      ds.setFile(root);
   }

   @Test
   void multiSheetWorkbookYieldsOneIdPerSheet_andDistinctSchemas() throws Exception {
      writeWorkbook(new File(root, "book.xlsx"),
         sheet("Q1", "id", "amount"),
         sheet("Q2", "region", "total", "notes"),
         sheet("Q3", "customer"));

      TabularCatalog catalog = ServerFileCatalog.listDatasets(ds);
      List<String> ids = catalog.datasets().stream().map(TabularDatasetRef::id).sorted()
         .collect(Collectors.toList());
      assertEquals(List.of("book.xlsx#Q1", "book.xlsx#Q2", "book.xlsx#Q3"), ids);

      // A3-3: distinct ids must describe DISTINCT schemas -- an implementation that resolves
      // every id to sheet 1 would still pass a test that only checked "each id resolves".
      assertEquals(List.of("id", "amount"), names(ServerFileCatalog.describeDataset(ds, "book.xlsx#Q1")));
      assertEquals(List.of("region", "total", "notes"),
         names(ServerFileCatalog.describeDataset(ds, "book.xlsx#Q2")));
      assertEquals(List.of("customer"), names(ServerFileCatalog.describeDataset(ds, "book.xlsx#Q3")));
   }

   @Test
   void singleSheetWorkbookYieldsExactlyOneId_noSuffix() throws Exception {
      writeWorkbook(new File(root, "single.xlsx"), sheet("Sheet1", "a", "b"));

      List<String> ids = ServerFileCatalog.listDatasets(ds).datasets().stream()
         .map(TabularDatasetRef::id).collect(Collectors.toList());
      assertEquals(List.of("single.xlsx"), ids);

      assertEquals(List.of("a", "b"), names(ServerFileCatalog.describeDataset(ds, "single.xlsx")));
   }

   @Test
   void aRelativePathContainingHashStillDecodesCorrectly_lastHashSplit() throws Exception {
      File sub = new File(root, "sub#folder");
      assertTrue(sub.mkdirs());
      writeWorkbook(new File(sub, "report.xlsx"), sheet("Data", "x"), sheet("Extra", "y"));

      List<String> ids = ServerFileCatalog.listDatasets(ds).datasets().stream()
         .map(TabularDatasetRef::id).sorted().collect(Collectors.toList());
      // R1-1: the path's own '#' is percent-escaped (%23) before the sheet separator is appended,
      // so lastIndexOf('#') on the full id always finds the genuine separator, never a '#' that
      // was really part of the directory name.
      assertEquals(List.of("sub%23folder/report.xlsx#Data", "sub%23folder/report.xlsx#Extra"), ids);

      // decodeId must split on the LAST unescaped '#' -- the file's own relative path contains
      // one (now escaped), and a first-'#' split would wrongly cut the path in half instead of
      // isolating the sheet name.
      TabularDatasetSchema schema =
         ServerFileCatalog.describeDataset(ds, "sub%23folder/report.xlsx#Data");
      assertEquals(List.of("x"), names(schema));
   }

   // R1-1, manifestation 1: an ORDINARY, unforced file whose own name contains '#' must still
   // round-trip and resolve correctly -- not merely fail loudly. Before the path-escaping fix,
   // this file's bare id ("sales#2026.csv") decoded as path "sales" + sheet "2026.csv" and
   // describeDataset threw "has no file at 'sales'" for a completely normal file.
   @Test
   void aBareHashContainingNonExcelPathRoundTripsAndResolves() throws Exception {
      java.nio.file.Files.writeString(new File(root, "sales#2026.csv").toPath(), "region\nEast\n");

      List<String> ids = ServerFileCatalog.listDatasets(ds).datasets().stream()
         .map(TabularDatasetRef::id).collect(Collectors.toList());
      assertEquals(List.of("sales%232026.csv"), ids);

      TabularDatasetSchema schema = ServerFileCatalog.describeDataset(ds, "sales%232026.csv");
      assertEquals(List.of("region"), names(schema));
   }

   // R1-1, manifestation 2: a multi-sheet workbook's sheet suffix must never collide with an
   // unrelated plain file's own bare id, even when the plain file's name is EXACTLY what the
   // workbook's "<path>#<sheet>" encoding would have produced unescaped. Before the fix,
   // "book.xlsx" (sheet "Sheet1.csv") and a plain file literally named "book.xlsx#Sheet1.csv"
   // both encoded to the identical string "book.xlsx#Sheet1.csv" -- a duplicate id that aborts
   // the whole catalog (TabularCatalogService's uniqueness check).
   @Test
   void aWorkbookSheetSuffixNeverCollidesWithAPlainFilesOwnBareId() throws Exception {
      writeWorkbook(new File(root, "book.xlsx"), sheet("Sheet1.csv", "a"), sheet("Other", "b"));
      java.nio.file.Files.writeString(
         new File(root, "book.xlsx#Sheet1.csv").toPath(), "collision\nx\n");

      List<String> ids = ServerFileCatalog.listDatasets(ds).datasets().stream()
         .map(TabularDatasetRef::id).sorted().collect(Collectors.toList());

      // No duplicates, and both targets independently resolve to what they actually are.
      assertEquals(ids.size(), ids.stream().distinct().count(), "duplicate dataset id: " + ids);
      assertEquals(
         List.of("book.xlsx#Other", "book.xlsx#Sheet1.csv", "book.xlsx%23Sheet1.csv"),
         ids);

      assertEquals(List.of("a"), names(ServerFileCatalog.describeDataset(ds, "book.xlsx#Sheet1.csv")));
      assertEquals(List.of("collision"),
         names(ServerFileCatalog.describeDataset(ds, "book.xlsx%23Sheet1.csv")));
   }

   private static List<String> names(TabularDatasetSchema schema) {
      return schema.columns().stream().map(TabularColumn::name).collect(Collectors.toList());
   }

   private record SheetSpec(String name, String[] headers) {}

   private static SheetSpec sheet(String name, String... headers) {
      return new SheetSpec(name, headers);
   }

   private static void writeWorkbook(File file, SheetSpec... sheets) throws Exception {
      try(XSSFWorkbook wb = new XSSFWorkbook()) {
         for(SheetSpec spec : sheets) {
            Sheet sheet = wb.createSheet(spec.name());
            Row header = sheet.createRow(0);

            for(int i = 0; i < spec.headers().length; i++) {
               header.createCell(i).setCellValue(spec.headers()[i]);
            }

            Row data = sheet.createRow(1);

            for(int i = 0; i < spec.headers().length; i++) {
               data.createCell(i).setCellValue("v" + i);
            }
         }

         try(FileOutputStream out = new FileOutputStream(file)) {
            wb.write(out);
         }
      }
   }
}
