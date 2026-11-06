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
package inetsoft.web.wiz.pairing;

import inetsoft.report.TableLayout;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Mint-time validation of the calcTableCell editorContext (Bug #80459). */
@Tag("core")
class SheetPairingServiceCalcCellTest {
   private static final long NOW = 1_000_000L;

   private static SheetPairingService serviceWith(VSAssembly assembly, TableLayout layout) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      if(assembly instanceof CalcTableVSAssembly calc) {
         CalcTableVSAssemblyInfo info = mock(CalcTableVSAssemblyInfo.class);
         when(info.getTableLayout()).thenReturn(layout);
         when(calc.getInfo()).thenReturn(info);
      }

      when(vs.getAssembly("Calc1")).thenReturn(assembly);
      return new SheetPairingService(() -> NOW, id -> null, id -> "vs-1".equals(id) ? rvs : null);
   }

   private static TableLayout grid(int rows, int cols) {
      TableLayout layout = mock(TableLayout.class);
      when(layout.getRowCount()).thenReturn(rows);
      when(layout.getColCount()).thenReturn(cols);
      return layout;
   }

   private static String mint(SheetPairingService svc, String assembly, String name)
      throws PairingException
   {
      return svc.mint("vs-1", "user", "sock", "user", SheetType.VIEWSHEET,
                      new EditorContext("calcTableCell", assembly, name, null));
   }

   @Test
   void mintsACellInsideTheGrid() throws Exception {
      SheetPairingService svc = serviceWith(mock(CalcTableVSAssembly.class), grid(3, 3));

      String code = mint(svc, "Calc1", "2,1");

      assertEquals("2,1", svc.peek(code).editorContext().name());
   }

   @Test
   void refusesAMalformedCellName() {
      SheetPairingService svc = serviceWith(mock(CalcTableVSAssembly.class), grid(3, 3));

      for(String bad : new String[] { "x", "1", "1,a", "1,2,3", " 1,2", "-1,0", null, "" }) {
         PairingException ex = assertThrows(PairingException.class, () -> mint(svc, "Calc1", bad),
                                            String.valueOf(bad));
         assertTrue(ex.getMessage().contains("row,col"), ex.getMessage());
      }
   }

   @Test
   void refusesABlankAssemblyAnUnknownAssemblyAndANonCalcTable() {
      SheetPairingService svc = serviceWith(mock(VSAssembly.class), null);

      assertThrows(PairingException.class, () -> mint(svc, "", "0,0"));
      assertThrows(PairingException.class, () -> mint(svc, "Nope", "0,0"));
      assertThrows(PairingException.class, () -> mint(svc, "Calc1", "0,0"));
   }

   @Test
   void refusesACellOutsideTheGrid() {
      SheetPairingService svc = serviceWith(mock(CalcTableVSAssembly.class), grid(3, 3));

      PairingException ex = assertThrows(PairingException.class, () -> mint(svc, "Calc1", "3,0"));
      assertTrue(ex.getMessage().contains("outside the grid"), ex.getMessage());
   }

   @Test
   void refusesACellAMergeHasSwallowedButAcceptsTheAnchor() throws Exception {
      TableLayout layout = grid(1, 2);
      when(layout.getSpan(0, 0)).thenReturn(new Dimension(2, 1));
      SheetPairingService svc = serviceWith(mock(CalcTableVSAssembly.class), layout);

      PairingException ex = assertThrows(PairingException.class, () -> mint(svc, "Calc1", "0,1"));
      assertTrue(ex.getMessage().contains("merged into [0,0]"), ex.getMessage());
      assertNotNull(mint(svc, "Calc1", "0,0"));
   }

   @Test
   void aWrongOwnerStillGetsTheSameExpiredShapeWithNoExistenceOracle() {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getUser()).thenReturn(() -> "someone~;~host-org");
      SheetPairingService svc = new SheetPairingService(() -> NOW, id -> null, id -> rvs);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.mint("vs-1", "alice~;~host-org", "s", "alice", SheetType.VIEWSHEET,
                        new EditorContext("calcTableCell", "Nope", "0,0", null)));

      assertEquals(PairingException.Kind.SESSION_EXPIRED, ex.getKind());
   }
}
