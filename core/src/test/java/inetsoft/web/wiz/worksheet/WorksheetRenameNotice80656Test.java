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

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.uql.asset.sync.DependencyTransformer;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80656: an in-place worksheet save that renamed a table or column must be detectable
 * without any stored dependent (an unsaved connected dashboard is never in the dependency
 * index), and must stay undetected for a plain save, a second save and a rename that was undone.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetRenameNotice80656Test {
   private static final String ID = "1^128^__NULL__^ws80656^host-org";

   private MockedStatic<DependencyTool> dependencies;

   @BeforeEach
   void noStoredDependents() {
      // an unsaved dashboard is in no dependency index; the table-rename branch only looks it up
      dependencies = Mockito.mockStatic(DependencyTool.class);
      dependencies.when(() -> DependencyTool.getDependencies(Mockito.anyString()))
         .thenReturn(List.of());
   }

   @AfterEach
   void closeMock() {
      dependencies.close();
   }

   private static BoundTableAssembly table(Worksheet ws, String name, String oldName,
                                           String col, String oldCol)
   {
      BoundTableAssembly t = new BoundTableAssembly(ws, name);
      ColumnRef ref = new ColumnRef(new AttributeRef(null, col));
      ref.setOldName(oldCol);
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(ref);
      t.setColumnSelection(cs, false);
      t.setOldName(oldName);
      ws.addAssembly(t);
      return t;
   }

   @Test
   void plainSaveRenamesNothing() {
      Worksheet ws = new Worksheet();
      table(ws, "ORDERS", "ORDERS", "ID", "ID");
      assertTrue(WorksheetRenameNotice.pendingRenames(ws, ID).isEmpty());
   }

   @Test
   void tableRenameIsPending() {
      Worksheet ws = new Worksheet();
      table(ws, "ORDERS2", "ORDERS", "ID", "ID");
      List<String> r = WorksheetRenameNotice.pendingRenames(ws, ID);
      assertEquals(List.of("table ORDERS -> ORDERS2"), r);
   }

   @Test
   void columnRenameIsPendingWithTableNameUnchanged() {
      Worksheet ws = new Worksheet();
      table(ws, "ORDERS", "ORDERS", "ID2", "ID");
      List<String> r = WorksheetRenameNotice.pendingRenames(ws, ID);
      assertEquals(1, r.size());
      assertTrue(r.get(0).startsWith("column ") && r.get(0).contains("ID -> ID2"), r.get(0));
   }

   @Test
   void renameThenRenameBackIsNotPending() {
      Worksheet ws = new Worksheet();
      table(ws, "ORDERS", "ORDERS", "ID", "ID");
      assertTrue(WorksheetRenameNotice.pendingRenames(ws, ID).isEmpty());
   }

   @Test
   void secondSaveAfterMarkersResetIsSilent() {
      Worksheet ws = new Worksheet();
      AbstractTableAssembly t = table(ws, "ORDERS2", "ORDERS", "ID2", "ID");
      assertEquals(2, WorksheetRenameNotice.pendingRenames(ws, ID).size());
      // what SaveWorksheetService.initWorksheetOldName does after every successful save
      t.setOldName(t.getName());
      DependencyTransformer.initTableColumnOldNames(t, false);
      assertTrue(WorksheetRenameNotice.pendingRenames(ws, ID).isEmpty());
   }

   @Test
   void sessionDecision() {
      List<String> none = List.of();
      List<String> renamed = List.of("table A -> B");
      // design session: reported after a cascade, or after a rename nothing received
      assertTrue(WorksheetRenameNotice.keepsSessionAndReports(true, true, none));
      assertTrue(WorksheetRenameNotice.keepsSessionAndReports(true, false, renamed));
      // plain save, no rename: silent
      assertFalse(WorksheetRenameNotice.keepsSessionAndReports(true, false, none));
      // viewer / preview / binding-editor runtimes: always the silent reset
      assertFalse(WorksheetRenameNotice.keepsSessionAndReports(false, true, renamed));
      assertFalse(WorksheetRenameNotice.keepsSessionAndReports(false, false, renamed));
   }

   @Test
   void warningNamesSessionsRenamesAndRebindTools() {
      String w = WorksheetRenameNotice.staleWarning(
         List.of("rvs-1"), List.of("table ORDERS -> ORDERS2"));
      assertTrue(w.contains("rvs-1"));
      assertTrue(w.contains("table ORDERS -> ORDERS2"));
      assertTrue(w.contains("set_table_source") && w.contains("set_selection_source"));
      assertFalse(WorksheetRenameNotice.staleWarning(List.of("rvs-1"), List.of())
         .contains("This save renamed"));
   }
}
