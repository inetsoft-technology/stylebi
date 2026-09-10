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
package inetsoft.report.io.viewsheet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("core")
class VSTableHelperTest {

   // Bug #76569 (VSC-003): a viewsheet assembly whose condition-script evaluation fails can
   // reach export/render with a null VSTableLens. getVisibleRowCount used to call
   // lens.getHeaderRowCount() unconditionally, NPE-ing and crashing the whole viewsheet's
   // export. This is a safety net only -- it does not address where the null lens comes from.
   @Test
   void getVisibleRowCountReturnsZeroForNullLens() {
      int[] result = new int[1];
      assertDoesNotThrow(() -> result[0] = VSTableHelper.getVisibleRowCount(null, null));
      assertEquals(0, result[0]);
   }
}
