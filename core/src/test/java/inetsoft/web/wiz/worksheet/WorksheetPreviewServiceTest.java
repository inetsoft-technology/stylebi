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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@WizAgentTestSupport
class WorksheetPreviewServiceTest {

   private final WorksheetPreviewService service = new WorksheetPreviewService();

   // ---------------------------------------------------------------------------
   // Helpers
   // ---------------------------------------------------------------------------

   private static RuntimeWorksheet rws(AssetQuerySandbox box) {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      return rws;
   }

   /**
    * Creates a mock TableLens with the given headers and rows.
    * Row 0 = headers, rows 1..n = data.
    */
   private static TableLens lens(String[] headers, Object[][] data) throws Exception {
      TableLens lens = mock(TableLens.class);
      when(lens.getColCount()).thenReturn(headers.length);

      for(int col = 0; col < headers.length; col++) {
         when(lens.getObject(0, col)).thenReturn(headers[col]);
      }

      int totalRows = data.length; // data rows only (row 0 is header)
      for(int row = 0; row < data.length; row++) {
         when(lens.moreRows(row + 1)).thenReturn(true);
         for(int col = 0; col < data[row].length; col++) {
            when(lens.getObject(row + 1, col)).thenReturn(data[row][col]);
         }
      }
      // moreRows returns false beyond the last row
      when(lens.moreRows(totalRows + 1)).thenReturn(false);

      return lens;
   }

   // ---------------------------------------------------------------------------
   // Tests
   // ---------------------------------------------------------------------------

   @Test
   void throwsWhenSandboxIsNull() {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getAssetQuerySandbox()).thenReturn(null);

      assertThrows(PairingException.class,
                   () -> service.preview(rws, "T", 10));
   }

   @Test
   void throwsWhenGetTableLensReturnsNull() throws Exception {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(null);

      assertThrows(PairingException.class,
                   () -> service.preview(rws(box), "T", 10));
   }

   @Test
   void throwsWhenGetTableLensThrows() throws Exception {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt()))
         .thenThrow(new RuntimeException("query failed"));

      assertThrows(PairingException.class,
                   () -> service.preview(rws(box), "T", 10));
   }

   @Test
   void returnsEmptyListWhenNoDataRows() throws Exception {
      TableLens emptyLens = mock(TableLens.class);
      when(emptyLens.getColCount()).thenReturn(2);
      when(emptyLens.getObject(0, 0)).thenReturn("a");
      when(emptyLens.getObject(0, 1)).thenReturn("b");
      when(emptyLens.moreRows(1)).thenReturn(false);

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(emptyLens);

      List<Map<String, Object>> rows = service.preview(rws(box), "T", 10);
      assertTrue(rows.isEmpty());
   }

   @Test
   void returnsRowsWithColumnNamesAsKeys() throws Exception {
      TableLens l = lens(
         new String[]{"name", "age"},
         new Object[][]{{"Alice", 30}, {"Bob", 25}}
      );
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(l);

      List<Map<String, Object>> rows = service.preview(rws(box), "T", 10);

      assertEquals(2, rows.size());
      assertEquals("Alice", rows.get(0).get("name"));
      assertEquals(30, rows.get(0).get("age"));
      assertEquals("Bob", rows.get(1).get("name"));
   }

   @Test
   void respectsLimitAndDoesNotExceedIt() throws Exception {
      TableLens l = lens(
         new String[]{"x"},
         new Object[][]{{"r1"}, {"r2"}, {"r3"}, {"r4"}, {"r5"}}
      );
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(l);

      List<Map<String, Object>> rows = service.preview(rws(box), "T", 3);
      assertEquals(3, rows.size());
   }

   @Test
   void usesFallbackHeaderNameWhenObjectIsNull() throws Exception {
      TableLens l = mock(TableLens.class);
      when(l.getColCount()).thenReturn(1);
      when(l.getObject(0, 0)).thenReturn(null); // null header
      when(l.getObject(1, 0)).thenReturn("value");
      when(l.moreRows(1)).thenReturn(true);
      when(l.moreRows(2)).thenReturn(false);

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(anyString(), anyInt())).thenReturn(l);

      List<Map<String, Object>> rows = service.preview(rws(box), "T", 10);
      assertEquals(1, rows.size());
      assertTrue(rows.get(0).containsKey("col0"), "should use fallback key 'col0'");
   }
}
