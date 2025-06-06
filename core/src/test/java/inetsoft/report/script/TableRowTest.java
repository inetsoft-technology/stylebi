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

package inetsoft.report.script;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.viewsheet.TableVSAScriptable;
import net.bytebuddy.implementation.bytecode.StackSize;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Scriptable;

import java.awt.*;
import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class TableRowTest {
   private TableRow tableRow;

   /**
    * test tablerow with get methord.   such as setBackground and getBackground
    */
   @Test
   void  testWithBackground() {
      tableRow = new TableRow(defaultTableLens, 1, "Background", Color.class);

      assertEquals(1, tableRow.getRow());
      assertTrue(tableRow.has("length", null));
      assertEquals(3, tableRow.get("length", null));

      assertTrue(tableRow.has(1, null));
      assertArrayEquals(new Object[]{ "name", "id", "date", "length" }, tableRow.getIds());
      assertEquals("[index]", tableRow.getDisplaySuffix());
      assertEquals("[]", tableRow.getSuffix());
   }

   /**
    * test tableRow with is property method, such as setLineWrap and isLineWrap
    */
   @Test
   void testWithOthers() {
      tableRow = new TableRow(defaultTableLens, 1, "LineWrap", boolean.class);

      assertTrue(tableRow.has("name", null));
      assertFalse(tableRow.has("test", null));

      //check no parent, but put a didn't existed column and value
      tableRow.put("name2", mock(TableVSAScriptable.class), "test");
      assertEquals(Scriptable.NOT_FOUND, tableRow.get("name2", null));

      TableRow row1 = (TableRow)tableRow.get(-1, null);
      assertArrayEquals(new Object[]{ "name", "id", "date", "length" }, row1.getIds());

      TableRow.TableCol tableCol = new TableRow.TableCol();
      assertEquals("null[0]", tableCol.toString());
   }

   Object[][] objData = new Object[][]{
      {"name", "id", "date"},
      {"a", 1, new Date(2021 - 1900, 0, 1)},
      {"c", 2, new Date(2023 - 1900, 5, 15)},
      {"a", 3, new Date(2025 - 1900, 11, 31)},
      {"b", 2, new Date(2026 - 1900, 9, 20)}
   };

   private DefaultTableLens defaultTableLens = new DefaultTableLens(objData);
}
