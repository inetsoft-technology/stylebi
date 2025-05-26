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

import inetsoft.report.TableFilter;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Scriptable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TableHighlightedArrayTest {
   private TableHighlightedArray tableHighlightedArray;
   private TableHighlightAttr.HighlightTableLens mockHighlightLens;

   @BeforeEach
   void setUp() {
      mockHighlightLens = mock(TableHighlightAttr.HighlightTableLens.class);
      when(mockHighlightLens.getHighlightNames()).thenReturn(List.of("highlight1", "highlight2"));
      when(mockHighlightLens.isHighlighted("highlight1")).thenReturn(true);
   }

   @Test
   void testInitWithNullTable() {
      // test init with null table
      XTable mockTable = mock(XTable.class);
      tableHighlightedArray = new TableHighlightedArray(mockTable);
      Object[] ids = tableHighlightedArray.getIds();

      assertNotNull(ids);
      assertEquals(0, ids.length);

      //test init with TableFilter which include HighlightTableLens
      TableFilter mockTableFilter = mock(TableFilter.class);
      when(mockTableFilter.getTable()).thenReturn(mockHighlightLens);

      tableHighlightedArray = new TableHighlightedArray(mockTableFilter);
      assertArrayEquals(new Object[] {"highlight1", "highlight2"}, tableHighlightedArray.getIds());
   }

   @Test
   void testOtherGets() {
      tableHighlightedArray = new TableHighlightedArray(mockHighlightLens);

      assertTrue(tableHighlightedArray.has("highlight2", null));
      assertTrue((boolean)tableHighlightedArray.get("highlight1", null));
      assertEquals(Scriptable.NOT_FOUND, tableHighlightedArray.get("highlight0", null));

      assertEquals("[highlightname]", tableHighlightedArray.getDisplaySuffix());
      assertEquals("[]", tableHighlightedArray.getSuffix());
      assertEquals("Highlighted", tableHighlightedArray.getClassName());
   }
}
