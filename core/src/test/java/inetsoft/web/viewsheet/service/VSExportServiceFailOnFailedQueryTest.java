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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.FileFormatInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.function.BiConsumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The failed-query export guard is opt-in on the exporter and rides {@link CSVConfig}. The wiz
 * agent export route goes through {@link VSExportService}, which must hand the flag to the
 * exporter it builds the same way ExportControllerService does (bug #80075 problem 5).
 *
 * <p>This proves the exporter is armed; it does not run a real failing query through the
 * exporter (that is covered by AbstractVSExporterFailedQueryTest and the live vsc-035 case).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSExportServiceFailOnFailedQueryTest {
   private static CSVVSExporter writeWith(CSVConfig csvConfig) throws Throwable {
      CSVVSExporter exporter = mock(CSVVSExporter.class);
      // a runtime viewsheet with no sandbox makes the export return right after the exporter is
      // built and configured, which is all this test needs
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      VSExportService service = mock(VSExportService.class, CALLS_REAL_METHODS);

      Method write = VSExportService.class.getDeclaredMethod(
         "writeViewsheetExport", RuntimeViewsheet.class, java.io.OutputStream.class,
         Principal.class, int.class, boolean.class, boolean.class, boolean.class, boolean.class,
         boolean.class, String[].class, boolean.class, CSVConfig.class, java.io.File.class,
         BiConsumer.class, boolean.class, boolean.class);
      write.setAccessible(true);

      try(MockedStatic<AbstractVSExporter> factory =
             mockStatic(AbstractVSExporter.class, CALLS_REAL_METHODS))
      {
         factory.when(() -> AbstractVSExporter.getVSExporter(
            anyInt(), any(), any(), anyBoolean(), any())).thenReturn(exporter);

         try {
            write.invoke(service, rvs, new ByteArrayOutputStream(), mock(Principal.class),
                         FileFormatInfo.EXPORT_TYPE_CSV, false, false, true, false, true,
                         new String[0], false, csvConfig, null, null, false, false);
         }
         catch(InvocationTargetException e) {
            throw e.getCause();
         }
      }

      return exporter;
   }

   @Test
   void aFlaggedConfigArmsTheExporter() throws Throwable {
      CSVConfig config = new CSVConfig();
      config.setFailOnFailedQuery(true);

      CSVVSExporter exporter = writeWith(config);

      verify(exporter).setFailOnFailedQuery(true);
   }

   @Test
   void anUnflaggedConfigLeavesTheExporterUnarmed() throws Throwable {
      CSVVSExporter exporter = writeWith(new CSVConfig());

      verify(exporter, never()).setFailOnFailedQuery(anyBoolean());
   }

   @Test
   void noConfigLeavesTheExporterUnarmed() throws Throwable {
      CSVVSExporter exporter = writeWith(null);

      verify(exporter, never()).setFailOnFailedQuery(anyBoolean());
   }
}
