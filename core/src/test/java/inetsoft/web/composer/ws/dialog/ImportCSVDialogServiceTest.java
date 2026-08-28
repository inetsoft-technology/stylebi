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
package inetsoft.web.composer.ws.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.FileSystemService;
import inetsoft.util.MessageException;
import inetsoft.web.composer.model.ws.ImportCSVDialogModelValidator;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.service.BinaryTransferService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for isAboveMaxFileSize's per-file-type precedence (CSV -> csv.import.max
 * only; Excel -> excel.import.max, falling back to csv.import.max when unset) and for both
 * Long.parseLong call sites now catching a malformed property value instead of throwing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ImportCSVDialogServiceTest {
   private ImportCSVDialogService createService() {
      return new ImportCSVDialogService(mock(ViewsheetService.class), mock(VSLayoutService.class),
         mock(BinaryTransferService.class), mock(AssetDataCache.class),
         mock(FileSystemService.class));
   }

   private boolean invokeIsAboveMaxFileSize(ImportCSVDialogService service, File file,
                                             boolean csv) throws Exception
   {
      Method method = ImportCSVDialogService.class.getDeclaredMethod(
         "isAboveMaxFileSize", File.class, ImportCSVDialogModelValidator.Builder.class,
         boolean.class);
      method.setAccessible(true);

      try {
         return (boolean) method.invoke(service, file, null, csv);
      }
      catch(java.lang.reflect.InvocationTargetException e) {
         if(e.getCause() instanceof RuntimeException re) {
            throw re;
         }

         throw e;
      }
   }

   private static File fileOfLength(long length) {
      File file = mock(File.class);
      when(file.length()).thenReturn(length);
      return file;
   }

   @Test
   void csvNeverReadsExcelImportMax() throws Exception {
      ImportCSVDialogService service = createService();
      File file = fileOfLength(2000);

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         // Tiny enough that honouring it would reject this file; csv.import.max is left unset.
         sreeEnv.when(() -> SreeEnv.getProperty("excel.import.max")).thenReturn("1");
         sreeEnv.when(() -> SreeEnv.getProperty("csv.import.max")).thenReturn(null);

         assertFalse(invokeIsAboveMaxFileSize(service, file, true),
                     "CSV must not be capped by excel.import.max");
      }
   }

   @Test
   void excelFallsBackToCsvImportMaxWhenExcelImportMaxUnset() throws Exception {
      ImportCSVDialogService service = createService();
      File file = fileOfLength(2000);

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("excel.import.max")).thenReturn(null);
         sreeEnv.when(() -> SreeEnv.getProperty("csv.import.max")).thenReturn("100");

         assertThrows(MessageException.class,
                      () -> invokeIsAboveMaxFileSize(service, file, false),
                      "Excel must fall back to csv.import.max when excel.import.max is unset");
      }
   }

   @Test
   void malformedCsvImportMaxIsTreatedAsUnboundedForCsv() throws Exception {
      ImportCSVDialogService service = createService();
      File file = fileOfLength(2000);

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("excel.import.max")).thenReturn(null);
         sreeEnv.when(() -> SreeEnv.getProperty("csv.import.max")).thenReturn("not-a-number");

         assertFalse(invokeIsAboveMaxFileSize(service, file, true),
                     "a malformed csv.import.max must be caught and treated as unbounded, "
                     + "not thrown");
      }
   }

   /**
    * Regression guard for the second, independent Long.parseLong call in the "hint to use CSV
    * instead" branch (isAboveMaxFileSize's excelmax-hint text) - a malformed csv.import.max here
    * must not throw NumberFormatException even though excel.import.max (the value actually being
    * enforced) is well-formed and the file legitimately exceeds it.
    */
   @Test
   void malformedCsvImportMaxInHintBranchDoesNotThrowNumberFormatException() throws Exception {
      ImportCSVDialogService service = createService();
      File file = fileOfLength(2000);

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("excel.import.max")).thenReturn("100");
         sreeEnv.when(() -> SreeEnv.getProperty("csv.import.max")).thenReturn("not-a-number");

         // The file legitimately exceeds excel.import.max, so this must still throw - but as
         // MessageException (the documented behavior), never as a raw NumberFormatException
         // escaping from the hint branch's own parse of the malformed csv.import.max.
         assertThrows(MessageException.class,
                      () -> invokeIsAboveMaxFileSize(service, file, false));
      }
   }
}
