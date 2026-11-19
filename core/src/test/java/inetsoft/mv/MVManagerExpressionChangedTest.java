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
package inetsoft.mv;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #81422, a viewsheet must not read an MV built from an expression column of the worksheet
 * that has been edited since, and the MV must not be removed on the query path. Bug #81366, the
 * same for any other edit of the table the MV was built from, and for a sub or association MV.
 */
@Tag("core")
class MVManagerExpressionChangedTest {
   @BeforeEach
   void setUp() {
      manager = mock(MVManager.class, CALLS_REAL_METHODS);
      worksheet = mock(Worksheet.class);
      vs = mock(Viewsheet.class);
      when(vs.getBaseWorksheet()).thenReturn(worksheet);
      vbox = mock(ViewsheetSandbox.class);
      // a temporary viewsheet returns the found mv without creating one
      entry = mock(AssetEntry.class);
      when(entry.isWorksheet()).thenReturn(false);
      when(entry.getScope()).thenReturn(AssetRepository.TEMPORARY_SCOPE);
      mv = mock(MVDef.class);
      when(mv.isSuccess()).thenReturn(true);
      when(mv.getName()).thenReturn("mv1");
      doReturn(mv).when(manager).findMV(any(), any(), any(), any(), any());
      doReturn(null).when(manager).getSubMV(any(), any(), any(), any());
   }

   @Test
   void editedExpressionColumnSkipsTheMVWithoutRemovingIt() {
      when(mv.isStale(worksheet)).thenReturn(true);

      assertNull(findRuntimeMV());
      verify(manager, never()).remove(any(MVDef.class), anyString());
   }

   @Test
   void unchangedExpressionColumnKeepsTheMV() {
      when(mv.isStale(worksheet)).thenReturn(false);

      RuntimeMV rmv = findRuntimeMV();

      assertNotNull(rmv);
      assertEquals("mv1", rmv.getMV());
   }

   @Test
   void tableEditedOtherThanAnExpressionSkipsTheMVWithoutRemovingIt() {
      // a filter, a join, an aggregate: the expression is unchanged
      when(mv.isExpressionChanged(worksheet)).thenReturn(false);
      when(mv.isDefinitionChanged(worksheet)).thenReturn(true);
      when(mv.isStale(worksheet)).thenCallRealMethod();

      assertNull(findRuntimeMV());
      verify(manager, never()).remove(any(MVDef.class), anyString());
   }

   @Test
   void editedTableSkipsTheSubMV() {
      MVDef smv = mock(MVDef.class);
      when(smv.isSuccess()).thenReturn(true);
      when(smv.getName()).thenReturn("smv1");
      doReturn(smv).when(manager).getSubMV(any(), any(), any(), any());
      when(mv.isStale(worksheet)).thenReturn(true);
      when(smv.isStale(worksheet)).thenReturn(true);

      assertNull(findRuntimeMV(), "a stale sub mv must not be read either");
      verify(manager, never()).remove(any(MVDef.class), anyString());
   }

   @Test
   void unchangedTableKeepsTheSubMV() {
      MVDef smv = mock(MVDef.class);
      when(smv.isSuccess()).thenReturn(true);
      when(smv.getName()).thenReturn("smv1");
      doReturn(smv).when(manager).getSubMV(any(), any(), any(), any());
      when(mv.isStale(worksheet)).thenReturn(true);
      when(smv.isStale(worksheet)).thenReturn(false);

      assertNotNull(findRuntimeMV());
   }

   private RuntimeMV findRuntimeMV() {
      return manager.findRuntimeMV(entry, vs, "Table1", "ORDERS", mock(XPrincipal.class), vbox,
                                   true, true);
   }

   private MVManager manager;
   private Worksheet worksheet;
   private Viewsheet vs;
   private ViewsheetSandbox vbox;
   private AssetEntry entry;
   private MVDef mv;
}
