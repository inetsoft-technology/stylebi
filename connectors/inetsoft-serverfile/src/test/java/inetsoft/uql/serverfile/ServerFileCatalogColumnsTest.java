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
import inetsoft.uql.tabular.TabularColumn;
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
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers charter A4 (declared header, not sampling; {@code columnsMayBeIncomplete == false}) and
 * A8's {@code sampleable} flag. See {@link ServerFileSpringTestSupport}'s class javadoc for why
 * reading a real workbook's header row needs the full server-shaped Spring test context, not just
 * a mocked {@code Config} bean.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  ServerFileSpringTestSupport.ConfigBeanConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class ServerFileCatalogColumnsTest {
   @TempDir
   File root;

   private ServerFileDataSource ds;

   @BeforeEach
   void setUp() {
      ds = new ServerFileDataSource();
      ds.setName("columns-test-ds");
      ds.setFile(root);
   }

   @Test
   void csvColumnsComeFromTheDeclaredHeader_inOrder_notInferredFromDataShape() throws Exception {
      // "1" as a header name would be indistinguishable from a data row if columns were ever
      // inferred from the first row's SHAPE instead of read as a declared header.
      Files.writeString(new File(root, "data.csv").toPath(), "1,name,amount\nfoo,bar,42\n");

      TabularDatasetSchema schema = ServerFileCatalog.describeDataset(ds, "data.csv");

      assertEquals(List.of("1", "name", "amount"), names(schema));
      assertFalse(schema.columnsMayBeIncomplete());
   }

   @Test
   void xlsxColumnsComeFromTheDeclaredHeader_inOrder() throws Exception {
      try(XSSFWorkbook wb = new XSSFWorkbook()) {
         Sheet sheet = wb.createSheet("Sheet1");
         Row header = sheet.createRow(0);
         header.createCell(0).setCellValue("1");
         header.createCell(1).setCellValue("region");
         Row data = sheet.createRow(1);
         data.createCell(0).setCellValue("x");
         data.createCell(1).setCellValue("y");

         try(FileOutputStream out = new FileOutputStream(new File(root, "data.xlsx"))) {
            wb.write(out);
         }
      }

      TabularDatasetSchema schema = ServerFileCatalog.describeDataset(ds, "data.xlsx");

      assertEquals(List.of("1", "region"), names(schema));
      assertFalse(schema.columnsMayBeIncomplete());
   }

   @Test
   void sampleableIsTrue_aLocalFileIsCheapToReReadForSampleRows() throws Exception {
      Files.writeString(new File(root, "data.csv").toPath(), "a,b\n1,2\n");
      TabularDatasetSchema schema = ServerFileCatalog.describeDataset(ds, "data.csv");
      assertTrue(schema.sampleable());
   }

   private static List<String> names(TabularDatasetSchema schema) {
      return schema.columns().stream().map(TabularColumn::name).collect(Collectors.toList());
   }
}
