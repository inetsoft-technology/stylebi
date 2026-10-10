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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78260: a table whose data failed to load has no lens, and the CSV writer dereferenced it,
 * failing the whole export with an opaque 500. It is written as an empty file instead.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CSVVSExporterNullLensTest {
   @Test
   void tableWithNoLensIsWrittenAsAnEmptyFile() throws Exception {
      CSVVSExporter exporter = new CSVVSExporter();
      TableVSAssembly table = mock(TableVSAssembly.class);
      when(table.getAbsoluteName()).thenReturn("Table1");

      exporter.writeTable(table, null);

      assertEmptyFile(exporter);
   }

   @Test
   void crosstabWithNoLensIsWrittenAsAnEmptyFile() throws Exception {
      CSVVSExporter exporter = new CSVVSExporter();
      CrosstabVSAssembly crosstab = mock(CrosstabVSAssembly.class);
      when(crosstab.getAbsoluteName()).thenReturn("Crosstab1");

      exporter.writeCrosstab(crosstab, null);

      assertEmptyFile(exporter);
   }

   @Test
   void calcTableWithNoLensIsWrittenAsAnEmptyFile() throws Exception {
      CSVVSExporter exporter = new CSVVSExporter();
      CalcTableVSAssembly calc = mock(CalcTableVSAssembly.class);
      when(calc.getAbsoluteName()).thenReturn("Calc1");

      exporter.writeCalcTable(calc, null);

      assertEmptyFile(exporter);
   }

   private static void assertEmptyFile(CSVVSExporter exporter) throws Exception {
      Field field = CSVVSExporter.class.getDeclaredField("resultFiles");
      field.setAccessible(true);
      List<?> files = (List<?>) field.get(exporter);

      assertEquals(1, files.size());
      File file = (File) files.get(0);
      assertNotNull(file);
      assertTrue(file.exists());
      assertEquals(0, file.length());
      file.delete();
   }
}
