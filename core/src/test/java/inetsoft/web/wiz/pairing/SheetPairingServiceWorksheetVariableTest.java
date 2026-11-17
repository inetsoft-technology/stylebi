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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.DefaultVariableAssembly;
import inetsoft.uql.asset.Worksheet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Mint-time validation of the {@code worksheetVariableDefault} editorContext (Bug #80459). */
@WizAgentTestSupport
class SheetPairingServiceWorksheetVariableTest {
   private static final long NOW = 1_000_000L;

   private static SheetPairingService service() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, "Var1"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "Query1", "price", "cost"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "Var3", "price"));
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      return new SheetPairingService(() -> NOW, id -> "ws-1".equals(id) ? rws : null, id -> null);
   }

   private static String mint(SheetPairingService svc, String assembly, String name,
                              boolean pending)
      throws PairingException
   {
      return svc.mint("ws-1", "user", "sock", "user", SheetType.WORKSHEET,
                      new EditorContext("worksheetVariableDefault", assembly, name, null,
                                        pending));
   }

   private static PairingException refused(SheetPairingService svc, String assembly, String name,
                                           boolean pending, String fragment)
   {
      PairingException ex = assertThrows(PairingException.class,
         () -> mint(svc, assembly, name, pending), assembly + "/" + name + "/" + pending);
      assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind(), ex.getMessage());
      assertTrue(ex.getMessage().contains(fragment), ex.getMessage());
      return ex;
   }

   @Test
   void theKindIsRecognizedAndListedInTheUnknownKindMessage() throws Exception {
      assertTrue(SheetPairingService.RECOGNIZED_KINDS.contains("worksheetVariableDefault"));
      assertEquals("worksheetVariableDefault", EditorContext.WORKSHEET_VARIABLE_DEFAULT);

      PairingException ex = assertThrows(PairingException.class,
         () -> service().mint("ws-1", "user", "sock", "user", SheetType.WORKSHEET,
            new EditorContext("worksheetVariableDefaults", "Var1", null, null)));
      assertTrue(ex.getMessage().contains("worksheetVariableDefault"), ex.getMessage());
   }

   @Test
   void mintsAnExistingVariableAndKeepsTheContextVerbatim() throws Exception {
      SheetPairingService svc = service();

      EditorContext ctx = svc.peek(mint(svc, "Var1", null, false)).editorContext();
      assertEquals("worksheetVariableDefault", ctx.kind());
      assertEquals("Var1", ctx.assembly());
      assertNull(ctx.name());
      assertFalse(ctx.pending());
   }

   @Test
   void mintsANewVariableWhoseNameIsUnused() throws Exception {
      SheetPairingService svc = service();

      EditorContext ctx = svc.peek(mint(svc, "NewVar", null, true)).editorContext();
      assertEquals("NewVar", ctx.assembly());
      assertTrue(ctx.pending());
   }

   @Test
   void aBlankNameIsAcceptedAsNoName() throws Exception {
      SheetPairingService svc = service();

      assertNotNull(mint(svc, "Var1", "", false));
      assertNotNull(mint(svc, "Var1", "   ", false));
   }

   @Test
   void refusesANameBecauseAVariableHasExactlyOneDefault() throws Exception {
      refused(service(), "Var1", "default", false, "takes no 'name'");
   }

   @Test
   void refusesABlankAndMissingAssembly() throws Exception {
      SheetPairingService svc = service();

      refused(svc, "", null, false, "requires 'assembly'");
      refused(svc, null, null, false, "requires 'assembly'");
      refused(svc, "  ", null, true, "requires 'assembly'");
   }

   @Test
   void refusesAMissingVariable() throws Exception {
      refused(service(), "Nope", null, false, "Variable not found: Nope");
   }

   @Test
   void refusesATableNamedLikeTheVariableAsNotAVariable() throws Exception {
      refused(service(), "Var3", null, false, "Variable not found: Var3");
      refused(service(), "Query1", null, false, "Variable not found: Query1");
   }

   @Test
   void aPendingNameAlreadyUsedByAnyAssemblyIsRefused() throws Exception {
      SheetPairingService svc = service();

      refused(svc, "Var1", null, true, "Name 'Var1' is already used by an assembly of this " +
              "worksheet");
      refused(svc, "Query1", null, true, "Name 'Query1' is already used");
   }

   @Test
   void refusesAViewsheetSheetTypeAndAViewsheetRuntime() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      SheetPairingService svc = new SheetPairingService(() -> NOW, id -> null, id -> rvs);

      PairingException type = assertThrows(PairingException.class,
         () -> svc.mint("ws-1", "user", "sock", "user", SheetType.VIEWSHEET,
            new EditorContext("worksheetVariableDefault", "Var1", null, null)));
      assertEquals(PairingException.Kind.INVALID_ARGUMENT, type.getKind(), type.getMessage());
      assertEquals("editorContext kind 'worksheetVariableDefault' requires a worksheet runtime",
                   type.getMessage());

      SheetPairingService viewsheetRuntime =
         new SheetPairingService(() -> NOW, id -> rvs, id -> rvs);
      PairingException runtime = assertThrows(PairingException.class,
         () -> viewsheetRuntime.mint("ws-1", "user", "sock", "user", SheetType.WORKSHEET,
            new EditorContext("worksheetVariableDefault", "Var1", null, null)));
      assertEquals("editorContext kind 'worksheetVariableDefault' requires a worksheet runtime",
                   runtime.getMessage());
   }

   @Test
   void aMissingRuntimeIsRefusedAsExpired() {
      SheetPairingService svc = new SheetPairingService(() -> NOW, id -> null, id -> null);

      PairingException ex = assertThrows(PairingException.class,
         () -> mint(svc, "Var1", null, false));
      assertEquals(PairingException.Kind.SESSION_EXPIRED, ex.getKind(), ex.getMessage());
   }

   /** One spelling of "no such runtime": a foreign owner learns nothing about the worksheet. */
   @Test
   void aForeignOwnerGivesTheSameMessageAsAMissingRuntime() {
      RuntimeWorksheet other = mock(RuntimeWorksheet.class);
      when(other.getUser()).thenReturn(() -> "someone~;~host-org");
      SheetPairingService foreign = new SheetPairingService(() -> NOW, id -> other, id -> null);
      SheetPairingService missing = new SheetPairingService(() -> NOW, id -> null, id -> null);

      for(String assembly : new String[] { "Nope", "Var1", "" }) {
         EditorContext ctx = new EditorContext("worksheetVariableDefault", assembly, null, null);
         PairingException mismatch = assertThrows(PairingException.class,
            () -> foreign.mint("ws-1", "alice~;~host-org", "s", "alice", SheetType.WORKSHEET,
                               ctx));
         PairingException gone = assertThrows(PairingException.class,
            () -> missing.mint("ws-1", "alice~;~host-org", "s", "alice", SheetType.WORKSHEET,
                               ctx));

         assertEquals(PairingException.Kind.SESSION_EXPIRED, mismatch.getKind(), assembly);
         assertEquals(gone.getMessage(), mismatch.getMessage(), assembly);
      }
   }

   @Test
   void theRetargetCheckAcceptsAndRefusesTheSameWay() throws Exception {
      SheetPairingService svc = service();

      svc.validateEditorContext(SheetType.WORKSHEET, "ws-1", "user",
         new EditorContext("worksheetVariableDefault", "Var1", null, null));
      assertThrows(PairingException.class, () -> svc.validateEditorContext(
         SheetType.WORKSHEET, "ws-1", "user",
         new EditorContext("worksheetVariableDefault", "Nope", null, null)));
   }
}
