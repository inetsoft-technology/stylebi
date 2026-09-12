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
package inetsoft.uql.viewsheet.vslayout;

import inetsoft.graph.internal.DimensionD;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * PrintInfo's no-arg constructor never used to initialize the private DimensionD size field,
 * and getSize() dereferenced it with no null-check, so getSize() threw an unconditional NPE on
 * any PrintInfo that was default-constructed (or XML-parsed without width/height attributes)
 * and never had setSize(...) called on it. This crashed set_print_layout/manage_device_layout
 * on every call, since ViewsheetPropertyDialogService.getViewsheetInfo() calls getSize() as the
 * first step of its read-merge-write cycle.
 */
class PrintInfoTest {
   @Test
   @Tag("core")
   void getSize_freshlyConstructed_returnsDefaultInsteadOfThrowing() {
      PrintInfo info = new PrintInfo();

      DimensionD size = assertDoesNotThrow(info::getSize);

      assertNotNull(size);
      assertTrue(size.getWidth() > 0);
      assertTrue(size.getHeight() > 0);
   }

   @Test
   @Tag("core")
   void clone_freshlyConstructed_doesNotThrow() {
      PrintInfo info = new PrintInfo();

      assertDoesNotThrow(info::clone);
   }

   @Test
   @Tag("core")
   void clone_nullSize_doesNotThrow() {
      PrintInfo info = new PrintInfo();
      info.setSize(null);

      // clone() itself catches any exception and returns null, so a null size NPE here
      // wouldn't surface as a thrown exception -- assert the return value instead.
      Object clone = assertDoesNotThrow(info::clone);

      assertNotNull(clone);
   }
}
