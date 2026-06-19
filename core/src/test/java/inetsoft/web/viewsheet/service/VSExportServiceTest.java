/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class VSExportServiceTest {
   @ParameterizedTest
   @ValueSource(strings = {"xlsx", "xls", "pptx", "ppt", "pdf", "vso", "html", "png", "csv",
                           "PDF", "Xlsx"})
   void supportedTypesAreAccepted(String ext) {
      assertTrue(VSExportService.isSupportedExportType(ext));
   }

   @ParameterizedTest
   @ValueSource(strings = {"excel", "data", "xml", "json", "svg", "txt", "powerpoint", ""})
   void unsupportedTypesAreRejected(String ext) {
      assertFalse(VSExportService.isSupportedExportType(ext));
   }

   @Test
   void nullTypeIsRejected() {
      assertFalse(VSExportService.isSupportedExportType(null));
   }

   @Test
   void getFormatNumberRejectsUnsupportedTypeWithoutNpe() {
      assertThrows(RuntimeException.class, () -> VSExportService.getFormatNumberFromExtension("excel"));
      assertThrows(RuntimeException.class, () -> VSExportService.getFormatNumberFromExtension(null));
   }
}
