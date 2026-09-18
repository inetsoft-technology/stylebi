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
import inetsoft.uql.VariableTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WBT-007: a genuinely empty Excel sheet (no header row, no data) must fail
 * {@link ServerFileQuery#loadOutputColumns} with a clean, named exception, not a raw
 * {@link NullPointerException} -- {@link ServerFileQuery#getColumns()} returning {@code null} is
 * a documented, expected outcome ({@link inetsoft.uql.tabular.SelectableTabularQuery#getColumns()}),
 * not a bug in itself.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  ServerFileSpringTestSupport.ConfigBeanConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class ServerFileQueryEmptySheetTest {
   @TempDir
   File root;

   @Test
   void loadOutputColumnsThrowsCleanExceptionForEmptySheet_notNullPointerException() throws Exception {
      File book = new File(root, "book.xlsx");

      try(XSSFWorkbook wb = new XSSFWorkbook()) {
         wb.createSheet("Sheet3");

         try(FileOutputStream out = new FileOutputStream(book)) {
            wb.write(out);
         }
      }

      ServerFileQuery query = new ServerFileQuery();
      query.setFileFolder(book);
      query.setExcelSheet("Sheet3");
      query.setFirstRowHeader(true);

      assertNull(query.getColumns(), "an empty sheet must resolve to null columns, not throw");

      Exception ex = assertThrows(Exception.class,
         () -> query.loadOutputColumns(new VariableTable()));

      assertInstanceOf(IOException.class, ex,
         "an empty/headerless sheet must surface as a clean, named exception, not a raw NPE");
   }
}
