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
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.BoundTableAssembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for SheetPairingService.
 *
 * [Mint]           mint returns a non-null, non-empty 8-char code
 * [Peek]           peek returns the grant for a valid code without consuming it
 * [Peek: repeat]   peek can be called multiple times for the same code
 * [Consume: once]  consume removes the grant (single-use)
 * [Consume: null]  consume on unknown code returns null
 * [Expired: peek]  peek returns null for expired grants
 * [Expired: consume] consume returns null for expired grants
 * [Code: format]   code uses only allowed alphabet characters
 * [EditorContext: none]      mint with a null editorContext works exactly as a toolbar mint
 * [EditorContext: found]     mint accepts an editorContext naming an assembly the runtime has
 * [EditorContext: missing]   mint refuses an editorContext naming an assembly the runtime
 *                            does not have, at mint time
 * [EditorContext: kind]      mint refuses an editorContext with a blank kind
 * [EditorContext: calc ok]   mint accepts a calcField editorContext whose table+name exist
 *                            (table aliased from 'assembly', matching the real browser wiring)
 * [EditorContext: calc miss] mint refuses a calcField editorContext naming a field the
 *                            runtime does not have
 * [EditorContext: worksheet] the same assembly check applies to worksheet runtimes
 * [EditorContext: worksheet ok] mint accepts an editorContext naming a table the worksheet
 *                            runtime has (positive path for the WORKSHEET arm)
 * [EditorContext: unknown kind] mint refuses an editorContext with an unrecognized kind
 * [EditorContext: table]     a calcField editorContext can name its table via 'table' directly,
 *                            not only via the 'assembly' alias
 * [EditorContext: precedence] an explicit 'table' wins over 'assembly' when both are given
 * [EditorContext: IDOR]      mint against a runtimeId the caller does not own fails identically
 *                            whether or not the named assembly exists -- the failure must never
 *                            become an oracle for what exists on someone else's open sheet
 */
/*
 * @WizAgentTestSupport (which itself carries @Tag("core")) replaces a bare @Tag: the
 * worksheet-column validation added by whole-branch review finding 3 is asserted against a REAL
 * ColumnSelection holding a REAL ExpressionRef, and ExpressionRef's static initializer reaches
 * SreeEnv. Mocking those two instead would have made the test assert only that its own stubs
 * agree with each other -- ColumnRef.getName()/ExpressionRef.getName() are the exact lookups the
 * validation performs.
 */
@WizAgentTestSupport
class SheetPairingServiceTest {

   private final long FIXED_NOW = 1_000_000L;

   private SheetPairingService serviceAt(long now) {
      return new SheetPairingService(() -> now);
   }

   /** A service whose VIEWSHEET runtime lookup resolves "vs-1" to the given RuntimeViewsheet. */
   private SheetPairingService serviceWithViewsheetRuntime(RuntimeViewsheet rvs) {
      return new SheetPairingService(() -> FIXED_NOW, runtimeId -> null,
                                     runtimeId -> "vs-1".equals(runtimeId) ? rvs : null);
   }

   /** A service whose WORKSHEET runtime lookup resolves "ws-1" to the given RuntimeWorksheet. */
   private SheetPairingService serviceWithWorksheetRuntime(RuntimeWorksheet rws) {
      return new SheetPairingService(() -> FIXED_NOW,
                                     runtimeId -> "ws-1".equals(runtimeId) ? rws : null,
                                     runtimeId -> null);
   }

   @Test
   void mintReturnsEightCharCode() throws PairingException {
      SheetPairingService svc = serviceAt(FIXED_NOW);
      String code = svc.mint("rt-1", "alice~;~org", "sock-1", null, SheetType.WORKSHEET, null);
      assertNotNull(code);
      assertEquals(8, code.length());
   }

   @Test
   void mintedCodeUsesAllowedAlphabet() throws PairingException {
      SheetPairingService svc = serviceAt(FIXED_NOW);
      String code = svc.mint("rt-1", "alice~;~org", "sock-1", null, SheetType.WORKSHEET, null);
      for (char c : code.toCharArray()) {
         assertTrue(SheetPairingService.ALPHABET.indexOf(c) >= 0,
                    "Unexpected char: " + c);
      }
   }

   @Test
   void peekReturnsGrantForValidCode() throws PairingException {
      SheetPairingService svc = serviceAt(FIXED_NOW);
      String code = svc.mint("rt-2", "bob~;~org", "sock-2", null, SheetType.VIEWSHEET, null);
      PairingGrant grant = svc.peek(code);
      assertNotNull(grant);
      assertEquals("rt-2", grant.runtimeId());
      assertEquals("bob~;~org", grant.ownerIdentity());
      assertEquals(SheetType.VIEWSHEET, grant.sheetType());
   }

   @Test
   void peekDoesNotConsumeGrant() throws PairingException {
      SheetPairingService svc = serviceAt(FIXED_NOW);
      String code = svc.mint("rt-3", "carol~;~org", "sock-3", null, SheetType.WORKSHEET, null);
      svc.peek(code);
      assertNotNull(svc.peek(code), "peek should be non-destructive");
   }

   @Test
   void consumeRemovesGrant() throws PairingException {
      SheetPairingService svc = serviceAt(FIXED_NOW);
      String code = svc.mint("rt-4", "dave~;~org", "sock-4", null, SheetType.WORKSHEET, null);
      PairingGrant first = svc.consume(code);
      assertNotNull(first);
      assertNull(svc.consume(code), "second consume must return null (single-use)");
   }

   @Test
   void consumeUnknownCodeReturnsNull() {
      SheetPairingService svc = serviceAt(FIXED_NOW);
      assertNull(svc.consume("XXXXXXXX"));
   }

   @Test
   void peekReturnsNullForExpiredGrant() throws PairingException {
      long mintTime = FIXED_NOW;
      SheetPairingService svc = serviceAt(mintTime);
      String code = svc.mint("rt-5", "eve~;~org", "sock-5", null, SheetType.WORKSHEET, null);
      // advance clock beyond TTL
      SheetPairingService svcLater = new SheetPairingService(
         () -> mintTime + SheetPairingService.TTL_MILLIS + 1, svc);
      assertNull(svcLater.peek(code));
   }

   @Test
   void consumeReturnsNullForExpiredGrant() throws PairingException {
      long mintTime = FIXED_NOW;
      SheetPairingService svc = serviceAt(mintTime);
      String code = svc.mint("rt-6", "frank~;~org", "sock-6", null, SheetType.WORKSHEET, null);
      SheetPairingService svcLater = new SheetPairingService(
         () -> mintTime + SheetPairingService.TTL_MILLIS + 1, svc);
      assertNull(svcLater.consume(code));
   }

   // ------------------------------------------------------------------ editorContext validation

   @Test
   void mintsWithoutAnEditorContextForAToolbarCode() throws PairingException {
      SheetPairingService svc = serviceAt(FIXED_NOW);
      String code = svc.mint("vs-1", "user", "sock-1", "user", SheetType.VIEWSHEET, null);
      assertNull(svc.peek(code).editorContext());
   }

   @Test
   void refusesAnEditorContextNamingAnAssemblyTheRuntimeDoesNotHave() {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssembly("NoSuchChart")).thenReturn(null);
      SheetPairingService svc = serviceWithViewsheetRuntime(rvs);
      EditorContext ctx = new EditorContext("assemblyMain", "NoSuchChart", null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.mint("vs-1", "user", "sock-1", "user", SheetType.VIEWSHEET, ctx));
      assertTrue(ex.getMessage().contains("NoSuchChart"),
                 "message must name what was asked for: " + ex.getMessage());
   }

   @Test
   void mintsWhenTheEditorContextNamesAnAssemblyTheRuntimeHas() throws PairingException {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssembly("Chart1")).thenReturn(mock(VSAssembly.class));
      SheetPairingService svc = serviceWithViewsheetRuntime(rvs);
      EditorContext ctx = new EditorContext("assemblyMain", "Chart1", null, null);

      String code = svc.mint("vs-1", "user", "sock-1", "user", SheetType.VIEWSHEET, ctx);

      assertEquals(ctx, svc.peek(code).editorContext());
   }

   @Test
   void refusesAnEditorContextWithABlankKind() {
      SheetPairingService svc = serviceAt(FIXED_NOW);
      EditorContext ctx = new EditorContext("  ", null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.mint("vs-1", "user", "sock-1", "user", SheetType.VIEWSHEET, ctx));
      assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind());
   }

   @Test
   void refusesACalcFieldEditorContextNamingAFieldTheRuntimeDoesNotHave() {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getCalcField("Query1", "NoSuchField")).thenReturn(null);
      SheetPairingService svc = serviceWithViewsheetRuntime(rvs);
      // The current browser wiring sends the owning table in 'assembly', not 'table'.
      EditorContext ctx = new EditorContext("calcField", "Query1", "NoSuchField", null);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.mint("vs-1", "user", "sock-1", "user", SheetType.VIEWSHEET, ctx));
      assertTrue(ex.getMessage().contains("NoSuchField"),
                 "message must name what was asked for: " + ex.getMessage());
   }

   @Test
   void mintsACalcFieldEditorContextWhoseFieldExists_tableAliasedFromAssembly()
      throws PairingException
   {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getCalcField("Query1", "Margin"))
         .thenReturn(mock(CalculateRef.class));
      SheetPairingService svc = serviceWithViewsheetRuntime(rvs);
      EditorContext ctx = new EditorContext("calcField", "Query1", "Margin", null);

      String code = svc.mint("vs-1", "user", "sock-1", "user", SheetType.VIEWSHEET, ctx);

      assertEquals(ctx, svc.peek(code).editorContext());
   }

   @Test
   void refusesAnEditorContextNamingATableTheWorksheetRuntimeDoesNotHave() {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      Worksheet ws = mock(Worksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(ws.getAssembly("NoSuchTable")).thenReturn(null);
      SheetPairingService svc = serviceWithWorksheetRuntime(rws);
      EditorContext ctx = new EditorContext("worksheetExpression", "NoSuchTable", "Calc1", null);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.mint("ws-1", "user", "sock-1", "user", SheetType.WORKSHEET, ctx));
      assertTrue(ex.getMessage().contains("NoSuchTable"),
                 "message must name what was asked for: " + ex.getMessage());
   }

   /**
    * FIXTURE STRENGTHENED, not weakened, by whole-branch review finding 3. This test previously
    * stubbed only {@code ws.getAssembly("Query1")} to a bare {@link Assembly} and asserted the
    * mint succeeded -- which is exactly the false success the fix removes: a
    * {@code worksheetExpression} grant is addressed by (table, FIELD), so an assembly that exists
    * proves nothing about the column the grant names. The intent of the test is unchanged
    * ("mints when the runtime has what the context names"); what the runtime must now actually
    * have is the column.
    */
   @Test
   void mintsWhenTheEditorContextNamesATableTheWorksheetRuntimeHas() throws PairingException {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      Worksheet ws = mock(Worksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(ws.getAssembly("Query1")).thenReturn(tableWithExpressionColumn("Calc1"));
      SheetPairingService svc = serviceWithWorksheetRuntime(rws);
      EditorContext ctx = new EditorContext("worksheetExpression", "Query1", "Calc1", null);

      String code = svc.mint("ws-1", "user", "sock-1", "user", SheetType.WORKSHEET, ctx);

      assertEquals(ctx, svc.peek(code).editorContext());
   }

   // ---------------------------------------------------------------------------
   // Whole-branch review finding 3 -- a mint that can match nothing must not succeed
   // ---------------------------------------------------------------------------

   /**
    * The reachable case: "new expression column" opens the formula editor with no
    * {@code formulaName}, so the mint carries no {@code name}. Before this, the mint SUCCEEDED
    * and {@code status} reported a healthy pane-scoped session -- and then
    * {@code PaneScopeService.matchesGrant} compared the grant's null name against every target's
    * real one and refused all of them with {@code 'Query1.null'}.
    */
   @Test
   void refusesAWorksheetExpressionContextThatCarriesNoName() {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      Worksheet ws = mock(Worksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(ws.getAssembly("Query1")).thenReturn(tableWithExpressionColumn("Calc1"));
      SheetPairingService svc = serviceWithWorksheetRuntime(rws);
      EditorContext ctx = new EditorContext("worksheetExpression", "Query1", null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.mint("ws-1", "user", "sock-1", "user", SheetType.WORKSHEET, ctx));

      assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind());
      assertTrue(ex.getMessage().contains("'name'"), ex.getMessage());
   }

   /** Same rule for the other worksheet kind -- both are addressed by (table, field). */
   @Test
   void refusesAWorksheetConditionContextThatCarriesNoName() {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      Worksheet ws = mock(Worksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(ws.getAssembly("Query1")).thenReturn(tableWithColumn("Amount"));
      SheetPairingService svc = serviceWithWorksheetRuntime(rws);
      EditorContext ctx = new EditorContext("worksheetCondition", "Query1", null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.mint("ws-1", "user", "sock-1", "user", SheetType.WORKSHEET, ctx));

      assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind());
      assertTrue(ex.getMessage().contains("'name'"), ex.getMessage());
   }

   /**
    * The name must be VERIFIED, not merely present -- mirroring the calcField branch, which has
    * always checked the field exists rather than that a field name was supplied.
    */
   @Test
   void refusesAWorksheetExpressionContextNamingAColumnTheTableDoesNotHave() {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      Worksheet ws = mock(Worksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(ws.getAssembly("Query1")).thenReturn(tableWithExpressionColumn("Calc1"));
      SheetPairingService svc = serviceWithWorksheetRuntime(rws);
      EditorContext ctx = new EditorContext("worksheetExpression", "Query1", "NoSuchColumn", null);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.mint("ws-1", "user", "sock-1", "user", SheetType.WORKSHEET, ctx));

      assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind());
      assertTrue(ex.getMessage().contains("NoSuchColumn"),
                 "message must name what was asked for: " + ex.getMessage());
   }

   /**
    * A condition is checked against ANY column, not expression columns only: adding a condition
    * to a field that has none yet is legitimate, so requiring an ExpressionRef here would refuse
    * the ordinary case.
    */
   @Test
   void mintsAWorksheetConditionContextWhoseColumnExists() throws PairingException {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      Worksheet ws = mock(Worksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(ws.getAssembly("Query1")).thenReturn(tableWithColumn("Amount"));
      SheetPairingService svc = serviceWithWorksheetRuntime(rws);
      EditorContext ctx = new EditorContext("worksheetCondition", "Query1", "Amount", null);

      String code = svc.mint("ws-1", "user", "sock-1", "user", SheetType.WORKSHEET, ctx);

      assertEquals(ctx, svc.peek(code).editorContext());
   }

   /**
    * A REAL {@link TableAssembly} holding exactly one EXPRESSION column named {@code name}.
    *
    * <p>Real domain objects, not mocks, for two reasons: {@code TableAssembly.getColumnSelection}
    * is final and cannot be stubbed at all, and {@code ColumnRef.getName()}/
    * {@code ExpressionRef.getName()} are the exact lookups the validation performs -- stubbing
    * them would prove only that the test's own stubs agree with each other.
    */
   private static TableAssembly tableWithExpressionColumn(String name) {
      ExpressionRef er = new ExpressionRef();
      er.setName(name);

      BoundTableAssembly t = new BoundTableAssembly(new Worksheet(), "Query1");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(er));
      t.setColumnSelection(cs, false);
      return t;
   }

   /** A REAL {@link TableAssembly} holding exactly one ordinary column named {@code name}. */
   private static TableAssembly tableWithColumn(String name) {
      return TestWorksheets.nonEmbeddedTableWithColumns(new Worksheet(), "Query1", name);
   }

   @Test
   void refusesAnEditorContextWithAnUnrecognizedKind() {
      SheetPairingService svc = serviceAt(FIXED_NOW);
      EditorContext ctx = new EditorContext("asssemblyMain", "Chart1", null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.mint("vs-1", "user", "sock-1", "user", SheetType.VIEWSHEET, ctx));
      assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind());
      assertTrue(ex.getMessage().contains("asssemblyMain"),
                 "message must name the bad value: " + ex.getMessage());
   }

   @Test
   void mintsACalcFieldEditorContextUsingTheTableFieldDirectly() throws PairingException {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getCalcField("Query1", "Margin")).thenReturn(mock(CalculateRef.class));
      SheetPairingService svc = serviceWithViewsheetRuntime(rvs);
      // 'assembly' is null here -- only the record's own 'table' field is set.
      EditorContext ctx = new EditorContext("calcField", null, "Margin", "Query1");

      String code = svc.mint("vs-1", "user", "sock-1", "user", SheetType.VIEWSHEET, ctx);

      assertEquals(ctx, svc.peek(code).editorContext());
   }

   /**
    * Pins the precedence explicitly, with both fields set to DIFFERENT tables: only 'table' is
    * stubbed to resolve the calc field. Inverted alias/fallback logic (checking 'assembly'
    * first) would look up the un-stubbed decoy table, get null back from the mock, and fail --
    * catching a regression that {@link #mintsACalcFieldEditorContextUsingTheTableFieldDirectly}
    * and the alias-path tests (which never set both fields at once) cannot.
    */
   @Test
   void explicitTableTakesPrecedenceOverTheAssemblyAlias() throws PairingException {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getCalcField("RealTable", "Margin")).thenReturn(mock(CalculateRef.class));
      // "DecoyTable" deliberately left unstubbed -- getCalcField returns null for it.
      SheetPairingService svc = serviceWithViewsheetRuntime(rvs);
      EditorContext ctx = new EditorContext("calcField", "DecoyTable", "Margin", "RealTable");

      String code = svc.mint("vs-1", "user", "sock-1", "user", SheetType.VIEWSHEET, ctx);

      assertEquals(ctx, svc.peek(code).editorContext());
   }

   /**
    * The core IDOR/information-disclosure guard: minting against a runtime the caller does not
    * own must fail identically regardless of whether the named assembly exists. A test that only
    * checked "a foreign runtime is refused" would still pass if the failure differed (or if
    * assembly existence were checked first) -- this pins that the two outcomes are
    * indistinguishable, and that neither message leaks the assembly name.
    */
   @Test
   void foreignRuntimeFailsIdenticallyRegardlessOfWhetherTheNamedAssemblyExists() {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      Principal bob = TestPrincipals.user("bob", "host-org");
      when(rvs.getUser()).thenReturn(bob);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssembly("RealChart")).thenReturn(mock(VSAssembly.class));
      when(vs.getAssembly("FakeChart")).thenReturn(null);
      SheetPairingService svc = serviceWithViewsheetRuntime(rvs);

      String aliceKey = new IdentityID("alice", "host-org").convertToKey();
      EditorContext namesARealAssembly = new EditorContext("assemblyMain", "RealChart", null, null);
      EditorContext namesAFakeAssembly = new EditorContext("assemblyMain", "FakeChart", null, null);

      PairingException exReal = assertThrows(PairingException.class,
         () -> svc.mint("vs-1", aliceKey, "sock-1", "alice", SheetType.VIEWSHEET, namesARealAssembly));
      PairingException exFake = assertThrows(PairingException.class,
         () -> svc.mint("vs-1", aliceKey, "sock-1", "alice", SheetType.VIEWSHEET, namesAFakeAssembly));

      assertEquals(exReal.getKind(), exFake.getKind());
      assertEquals(exReal.getMessage(), exFake.getMessage());
      assertFalse(exReal.getMessage().contains("RealChart"));
      assertFalse(exReal.getMessage().contains("FakeChart"));
   }
}
