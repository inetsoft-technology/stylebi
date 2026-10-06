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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.TableArray;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.stall.LockStallException;
import inetsoft.util.swap.LostSwapFile;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * #77123: a lock stall (FAIL stall mode) while a viewsheet table's data is fetched for a
 * script must reach the script. It must not read as a missing table, which TableArray reads
 * as an empty table ({@code Crosstab1.data.length == 0}) and which the scriptable cached
 * until its cache was cleared.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableDataVSAScriptableStallTest {
   private ViewsheetSandbox box;
   private Viewsheet vs;

   @BeforeEach
   void setUp() {
      vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");

      TableVSAssembly table = new TableVSAssembly();
      table.getVSAssemblyInfo().setName("Table1");
      vs.addAssembly(table);

      CrosstabVSAssembly crosstab = new CrosstabVSAssembly();
      crosstab.getVSAssemblyInfo().setName("Crosstab1");
      vs.addAssembly(crosstab);

      box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(vs);
   }

   /**
    * initTableArray: a stalled view table fetch read as no table, so {@code Table1.data}
    * was undefined and the script failed with a misleading TypeError.
    */
   @Test
   void stalledViewTableFetchThrowsTheStall() throws Exception {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      when(box.getVSTableLens("Table1", false)).thenThrow(new RuntimeException("query", stall));
      TableVSAScriptable table = new TableVSAScriptable(box);
      table.setAssembly("Table1");

      assertSame(stall, assertThrows(LockStallException.class, () -> table.getMember("data")));
   }

   /**
    * A failure that is not a stall still reads as no table, as before.
    */
   @Test
   void failedViewTableFetchStillReadsAsNoTable() throws Exception {
      when(box.getVSTableLens("Table1", false)).thenThrow(new RuntimeException("boom"));
      TableVSAScriptable table = new TableVSAScriptable(box);
      table.setAssembly("Table1");

      assertFalse(table.getMember("data") instanceof TableArray);
   }

   /**
    * getTable0: a stalled crosstab data fetch was cached as an empty data array, so
    * {@code Crosstab1.data.length} stayed 0 after the stall was gone.
    */
   @Test
   void stalledCrosstabDataFetchThrowsTheStallAndIsNotCached() throws Exception {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      when(box.getVSTableLens("Crosstab1", false)).thenReturn(new VSTableLens(lens()));
      when(box.getTableData("Crosstab1")).thenThrow(stall).thenReturn(lens());
      CrosstabVSAScriptable crosstab = new CrosstabVSAScriptable(box);
      crosstab.setAssembly("Crosstab1");

      assertSame(stall, assertThrows(LockStallException.class,
         () -> crosstab.getMember("data")));

      // the stall is gone: the data is fetched again, not read from a cached empty array
      TableArray data = (TableArray) crosstab.getMember("data");
      assertEquals(3, data.getMember("length"));
   }

   @Test
   void failedCrosstabDataFetchStillReadsAsEmpty() throws Exception {
      when(box.getVSTableLens("Crosstab1", false)).thenReturn(new VSTableLens(lens()));
      when(box.getTableData("Crosstab1")).thenThrow(new RuntimeException("boom"));
      CrosstabVSAScriptable crosstab = new CrosstabVSAScriptable(box);
      crosstab.setAssembly("Crosstab1");

      TableArray data = (TableArray) crosstab.getMember("data");
      assertEquals(0, data.getMember("length"));
   }

   @Test
   void crosstabDataReadsTheTable() throws Exception {
      when(box.getVSTableLens("Crosstab1", false)).thenReturn(new VSTableLens(lens()));
      when(box.getTableData("Crosstab1")).thenReturn(lens());
      CrosstabVSAScriptable crosstab = new CrosstabVSAScriptable(box);
      crosstab.setAssembly("Crosstab1");

      TableArray data = (TableArray) crosstab.getMember("data");
      assertEquals(3, data.getMember("length"));
   }

   /**
    * A stall while a dirty (cleared) table is fetched again must leave the table dirty, so
    * the next read fetches it instead of reading the old table.
    */
   @Test
   void stalledRefetchOfAClearedTableKeepsItDirty() throws Exception {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      when(box.getVSTableLens("Table1", false))
         .thenReturn(new VSTableLens(lens()))
         .thenThrow(stall)
         .thenReturn(new VSTableLens(new DefaultTableLens(new Object[][] {
            { "a", "b" }, { 1, 2 } })));
      when(box.getTableData("Table1")).thenReturn(lens());
      TableVSAScriptable table = new TableVSAScriptable(box);
      table.setAssembly("Table1");

      assertEquals(3, table.getMember("row"));
      table.clearCache();

      assertSame(stall, assertThrows(LockStallException.class, () -> table.getMember("row")));
      assertEquals(2, table.getMember("row"));
   }

   /**
    * After stalled re-fetches of a cleared table, the first good fetch is read and clears the
    * dirty flag: later reads use it and do not fetch again (no endless re-fetch).
    */
   @Test
   void goodFetchAfterStalledRefetchesClearsTheDirtyFlag() throws Exception {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      when(box.getVSTableLens("Table1", false))
         .thenReturn(new VSTableLens(lens()))
         .thenThrow(stall)
         .thenThrow(new RuntimeException("wrapped", stall))
         .thenReturn(new VSTableLens(new DefaultTableLens(new Object[][] {
            { "a", "b" }, { 1, 2 } })));
      when(box.getTableData("Table1")).thenReturn(lens());
      TableVSAScriptable table = new TableVSAScriptable(box);
      table.setAssembly("Table1");

      assertEquals(3, table.getMember("row"));
      table.clearCache();

      assertSame(stall, assertThrows(LockStallException.class, () -> table.getMember("row")));
      assertSame(stall, assertThrows(LockStallException.class, () -> table.getMember("row")));
      assertEquals(2, table.getMember("row"));
      assertEquals(2, table.getMember("row"));
      assertEquals(2, table.getMember("row"));
      verify(box, times(4)).getVSTableLens("Table1", false);
   }

   private static DefaultTableLens lens() {
      return new DefaultTableLens(new Object[][] { { "a", "b" }, { 1, 2 }, { 3, 4 } });
   }

   /**
    * #77910: a lost swap file while a view table is fetched must reach the script, and a
    * cleared table must stay dirty so the next read fetches it again.
    */
   @Test
   void lostSwapFileRefetchOfAClearedTableThrowsAndKeepsItDirty() throws Exception {
      try(LostSwapFile lost = new LostSwapFile()) {
         when(box.getVSTableLens("Table1", false))
            .thenReturn(new VSTableLens(lens()))
            .thenAnswer(inv -> {
               lost.read();
               return null;
            })
            .thenReturn(new VSTableLens(new DefaultTableLens(new Object[][] {
               { "a", "b" }, { 1, 2 } })));
         when(box.getTableData("Table1")).thenReturn(lens());
         TableVSAScriptable table = new TableVSAScriptable(box);
         table.setAssembly("Table1");

         assertEquals(3, table.getMember("row"));
         table.clearCache();

         assertEquals(lost.getFile(), assertThrows(SwapFileReadException.class,
            () -> table.getMember("row")).getFile());
         assertEquals(2, table.getMember("row"));
      }
   }

   /**
    * #77910: a lost swap file while crosstab data is fetched must reach the script and not
    * be cached as an empty data array.
    */
   @Test
   void lostSwapFileCrosstabDataFetchThrowsAndIsNotCached() throws Exception {
      try(LostSwapFile lost = new LostSwapFile()) {
         when(box.getVSTableLens("Crosstab1", false)).thenReturn(new VSTableLens(lens()));
         when(box.getTableData("Crosstab1"))
            .thenAnswer(inv -> {
               lost.read();
               return null;
            })
            .thenReturn(lens());
         CrosstabVSAScriptable crosstab = new CrosstabVSAScriptable(box);
         crosstab.setAssembly("Crosstab1");

         assertEquals(lost.getFile(), assertThrows(SwapFileReadException.class,
            () -> crosstab.getMember("data")).getFile());

         TableArray data = (TableArray) crosstab.getMember("data");
         assertEquals(3, data.getMember("length"));
      }
   }
}
