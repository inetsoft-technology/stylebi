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
package inetsoft.uql.viewsheet.internal;

import org.junit.jupiter.api.*;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class VSTableStructureDefaultsTest {
   // pins the modern structure palette; the values are overlaid onto the Default Style clone in
   // DataVSAQuery, so a change here is an export-visible change and must be intentional
   @Test
   void gridlineColorValue() {
      assertEquals(0xE8E5DE, rgb(VSTableStructureDefaults.gridlineColor()));
   }

   @Test
   void headerBackgroundValue() {
      assertEquals(0xF1EFEA, rgb(VSTableStructureDefaults.headerBackground()));
   }

   @Test
   void headerForegroundValue() {
      assertEquals(0x6A685F, rgb(VSTableStructureDefaults.headerForeground()));
   }

   @Test
   void totalBackgroundValue() {
      assertEquals(0xE9E4DA, rgb(VSTableStructureDefaults.totalBackground()));
   }

   private static int rgb(Color c) {
      return c.getRGB() & 0xFFFFFF;
   }
}
