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

import inetsoft.report.composition.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for SheetRuntimeAccess.
 *
 * Mocks SheetDirectAccessor (not WorksheetEngine directly) to avoid triggering
 * WorksheetEngine's static initializers in a unit-test context.
 *
 * [WS: found]          worksheet branch returns RuntimeWorksheet and calls access(true)
 * [VS: found]          viewsheet branch returns RuntimeViewsheet and calls access(true)
 * [WS: missing]        null from getSheetDirect throws PairingException
 * [WS: wrong type]     wrong RuntimeSheet subtype throws PairingException
 * [VS: missing]        null from getSheetDirect (viewsheet) throws PairingException
 * [VS: wrong type]     wrong RuntimeSheet subtype (viewsheet) throws PairingException
 */
@Tag("core")
class SheetRuntimeAccessTest {

   private static final Principal ALICE = TestPrincipals.user("alice", "org");

   // ------------------------------------------------------------------ helpers

   private SheetRuntimeAccess access(SheetDirectAccessor wsMock, SheetDirectAccessor vsMock) {
      return new SheetRuntimeAccess(wsMock, vsMock);
   }

   // ------------------------------------------------------------------ worksheet branch

   @Test
   void worksheetBranchReturnsRuntimeWorksheet() throws Exception {
      SheetDirectAccessor wsMock = mock(SheetDirectAccessor.class);
      SheetDirectAccessor vsMock = mock(SheetDirectAccessor.class);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(wsMock.getSheetDirect("WS/123")).thenReturn(rws);

      RuntimeSheet result = access(wsMock, vsMock)
         .getSheetForPairing(SheetType.WORKSHEET, "WS/123", ALICE);

      assertSame(rws, result);
      verify(rws).access(true);
   }

   @Test
   void missingWorksheetThrowsPairingException() {
      SheetDirectAccessor wsMock = mock(SheetDirectAccessor.class);
      SheetDirectAccessor vsMock = mock(SheetDirectAccessor.class);
      when(wsMock.getSheetDirect(any())).thenReturn(null);

      assertThrows(PairingException.class,
         () -> access(wsMock, vsMock)
            .getSheetForPairing(SheetType.WORKSHEET, "WS/GONE", ALICE));
   }

   @Test
   void wrongTypeForWorksheetThrowsPairingException() {
      SheetDirectAccessor wsMock = mock(SheetDirectAccessor.class);
      SheetDirectAccessor vsMock = mock(SheetDirectAccessor.class);
      // return a ViewsheetRuntime when a WORKSHEET is requested
      RuntimeViewsheet wrongType = mock(RuntimeViewsheet.class);
      when(wsMock.getSheetDirect(any())).thenReturn(wrongType);

      assertThrows(PairingException.class,
         () -> access(wsMock, vsMock)
            .getSheetForPairing(SheetType.WORKSHEET, "WS/WRONG", ALICE));
   }

   // ------------------------------------------------------------------ viewsheet branch

   @Test
   void viewsheetBranchReturnsRuntimeViewsheet() throws Exception {
      SheetDirectAccessor wsMock = mock(SheetDirectAccessor.class);
      SheetDirectAccessor vsMock = mock(SheetDirectAccessor.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(vsMock.getSheetDirect("VS/456")).thenReturn(rvs);

      RuntimeSheet result = access(wsMock, vsMock)
         .getSheetForPairing(SheetType.VIEWSHEET, "VS/456", ALICE);

      assertSame(rvs, result);
      verify(rvs).access(true);
   }

   @Test
   void missingViewsheetThrowsPairingException() {
      SheetDirectAccessor wsMock = mock(SheetDirectAccessor.class);
      SheetDirectAccessor vsMock = mock(SheetDirectAccessor.class);
      when(vsMock.getSheetDirect(any())).thenReturn(null);

      assertThrows(PairingException.class,
         () -> access(wsMock, vsMock)
            .getSheetForPairing(SheetType.VIEWSHEET, "VS/GONE", ALICE));
   }

   @Test
   void wrongTypeForViewsheetThrowsPairingException() {
      SheetDirectAccessor wsMock = mock(SheetDirectAccessor.class);
      SheetDirectAccessor vsMock = mock(SheetDirectAccessor.class);
      // return a WorksheetRuntime when a VIEWSHEET is requested
      RuntimeWorksheet wrongType = mock(RuntimeWorksheet.class);
      when(vsMock.getSheetDirect(any())).thenReturn(wrongType);

      assertThrows(PairingException.class,
         () -> access(wsMock, vsMock)
            .getSheetForPairing(SheetType.VIEWSHEET, "VS/WRONG", ALICE));
   }
}
