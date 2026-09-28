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
package inetsoft.report.io.viewsheet.pdf;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VsToReportConverter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Print layout reads a table's card inset through the PDF exporter's resolver, the one C1's
 * PDF export reads.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PDFPrintLayoutCardInsetTest {
   @Test
   void thePrintLayoutConverterReadsThePdfExportersInset() throws Exception {
      VsToReportConverter converter =
         new PDFVSExporter(null, null, null, null, new ByteArrayOutputStream())
            .createReportConverter(null);

      assertEquals(new Insets(16, 12, 8, 4), cardInset(converter, padded(new Insets(16, 12, 8, 4))));
   }

   @Test
   void aConverterWithoutAResolverPrintsTablesUninset() throws Exception {
      VsToReportConverter converter = new VsToReportConverter(null, null, null, null, null);

      assertEquals(new Insets(0, 0, 0, 0), cardInset(converter, padded(new Insets(16, 12, 8, 4))));
   }

   private static Insets cardInset(VsToReportConverter converter, TableDataVSAssemblyInfo info)
      throws Exception
   {
      Method method =
         VsToReportConverter.class.getDeclaredMethod("getCardInset", TableDataVSAssemblyInfo.class);
      method.setAccessible(true);
      return (Insets) method.invoke(converter, info);
   }

   private static TableVSAssemblyInfo padded(Insets padding) {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setPadding(padding);
      return info;
   }
}
