/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #82506: the delimited type detection window is shared by the preview and the import.
 */
@Tag("core")
class ImportCSVDialogServiceTypeRowsTest {
   @Test
   void noRowLimitScansWholeFile() {
      assertEquals(Integer.MAX_VALUE, ImportCSVDialogService.getCSVTypeRows(0));
      assertEquals(Integer.MAX_VALUE, ImportCSVDialogService.getCSVTypeRows(-1));
   }

   @Test
   void rowLimitKeepsMinimumWindow() {
      assertEquals(50000, ImportCSVDialogService.getCSVTypeRows(101));
      assertEquals(50000, ImportCSVDialogService.getCSVTypeRows(10000));
      assertEquals(80000, ImportCSVDialogService.getCSVTypeRows(80000));
   }
}
