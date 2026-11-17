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
import inetsoft.uql.asset.SQLBoundTableAssembly;
import inetsoft.uql.asset.Worksheet;
import org.junit.jupiter.api.Test;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mint-time validation of the {@code worksheetSqlQuery} editorContext, and above all its Free Form
 * SQL gate (Bug #80459). The gate is checked FIRST, before any assembly lookup, so a user without
 * the permission cannot probe table names: a real and a missing table get the SAME message. The
 * permission is injected through the {@code freeFormSqlAllowed} seam, so no security engine is
 * needed.
 */
@WizAgentTestSupport
class SheetPairingServiceSqlQueryTest {
   private static final long NOW = 1_000_000L;
   private static final Principal OWNER = TestPrincipals.user("alice", "host-org");
   private static final String OWNER_IDENTITY = "alice~;~host-org";

   /** The principals the gate was asked about, in order. */
   private final List<Principal> asked = new ArrayList<>();

   private RuntimeWorksheet runtime(Principal user) {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new SQLBoundTableAssembly(ws, "SQL Query1"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "Embedded1", "price"));
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getUser()).thenReturn(user);
      return rws;
   }

   private SheetPairingService service(RuntimeWorksheet rws, Predicate<Principal> allowed) {
      // The viewsheet accessor answers for the same id so a wrong sheetType reaches the validator.
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Principal owner = rws == null ? null : rws.getUser();
      when(rvs.getUser()).thenReturn(owner);
      return new SheetPairingService(() -> NOW, id -> "ws-1".equals(id) ? rws : null,
                                     id -> rws == null || !"ws-1".equals(id) ? null : rvs) {
         @Override
         boolean freeFormSqlAllowed(Principal user) {
            asked.add(user);
            return allowed.test(user);
         }
      };
   }

   private SheetPairingService allowed() {
      return service(runtime(OWNER), user -> true);
   }

   private SheetPairingService denied() {
      return service(runtime(OWNER), user -> false);
   }

   private static String mint(SheetPairingService svc, String assembly, String name,
                              boolean pending)
      throws PairingException
   {
      return svc.mint("ws-1", OWNER_IDENTITY, "sock", "alice", SheetType.WORKSHEET,
                      new EditorContext("worksheetSqlQuery", assembly, name, null, pending));
   }

   private static PairingException refused(SheetPairingService svc, String assembly, String name,
                                           boolean pending, PairingException.Kind kind,
                                           String fragment)
   {
      PairingException ex = assertThrows(PairingException.class,
         () -> mint(svc, assembly, name, pending), assembly + "/" + name + "/" + pending);
      assertEquals(kind, ex.getKind(), ex.getMessage());
      assertTrue(ex.getMessage().contains(fragment), ex.getMessage());
      return ex;
   }

   // ---- the kind and the allowed paths --------------------------------------------------------

   @Test
   void theKindIsRecognizedAndListedInTheUnknownKindMessage() {
      assertTrue(SheetPairingService.RECOGNIZED_KINDS.contains("worksheetSqlQuery"));
      assertEquals("worksheetSqlQuery", EditorContext.WORKSHEET_SQL_QUERY);

      PairingException ex = assertThrows(PairingException.class,
         () -> allowed().mint("ws-1", OWNER_IDENTITY, "sock", "alice", SheetType.WORKSHEET,
            new EditorContext("worksheetSqlQueries", "SQL Query1", null, null)));
      assertTrue(ex.getMessage().contains("worksheetSqlQuery"), ex.getMessage());
   }

   @Test
   void anAllowedUserMintsAnExistingSqlTableAndKeepsTheContextVerbatim() throws Exception {
      SheetPairingService svc = allowed();

      EditorContext ctx = svc.peek(mint(svc, "SQL Query1", null, false)).editorContext();
      assertEquals("worksheetSqlQuery", ctx.kind());
      assertEquals("SQL Query1", ctx.assembly());
      assertNull(ctx.name());
      assertFalse(ctx.pending());
      assertEquals(List.of(OWNER), asked, "the gate is asked about the runtime owner, once");
   }

   @Test
   void anAllowedUserMintsANewQueryWhoseNameIsUnused() throws Exception {
      SheetPairingService svc = allowed();

      EditorContext ctx = svc.peek(mint(svc, "SQL Query2", null, true)).editorContext();
      assertEquals("SQL Query2", ctx.assembly());
      assertTrue(ctx.pending());
   }

   // ---- the gate ------------------------------------------------------------------------------

   @Test
   void aDeniedUserIsRefusedWithThePermissionTextAndUserMismatch() {
      PairingException ex = refused(denied(), "SQL Query1", null, false,
         PairingException.Kind.USER_MISMATCH, "You do not have permission to edit free-form SQL");

      assertEquals(SheetPairingService.FREE_FORM_SQL_DENIED, ex.getMessage());
      assertTrue(ex.getMessage().contains("Visual Composer > Free Form SQL"), ex.getMessage());
   }

   /**
    * The probe test: before the lookup, a denied user asking for a real table, a missing table, a
    * non-SQL table and a new query's name gets ONE message, so the refusal tells nothing about
    * what exists on the worksheet.
    */
   @Test
   void aDeniedUserGetsTheSameMessageForARealAndAMissingTable() {
      SheetPairingService svc = denied();
      String reference = refused(svc, "SQL Query1", null, false,
         PairingException.Kind.USER_MISMATCH, "free-form SQL").getMessage();

      for(Object[] c : new Object[][] { { "Nope", false }, { "Embedded1", false },
                                        { "SQL Query1", true }, { "Brand New", true },
                                        { "", false }, { null, false } })
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> mint(svc, (String) c[0], null, (Boolean) c[1]), String.valueOf(c[0]));

         assertEquals(PairingException.Kind.USER_MISMATCH, ex.getKind(), String.valueOf(c[0]));
         assertEquals(reference, ex.getMessage(), String.valueOf(c[0]));
      }
   }

   @Test
   void aDeniedUserIsRefusedBeforeEvenTheSheetTypeAndNameAreLookedAt() {
      SheetPairingService svc = denied();

      // A name on a nameless kind, and a viewsheet sheet type, would each be INVALID_ARGUMENT for
      // an allowed user; the denied user must not learn that either.
      PairingException name = refused(svc, "SQL Query1", "extra", false,
         PairingException.Kind.USER_MISMATCH, "free-form SQL");
      assertEquals(SheetPairingService.FREE_FORM_SQL_DENIED, name.getMessage());

      PairingException type = assertThrows(PairingException.class,
         () -> svc.mint("ws-1", OWNER_IDENTITY, "sock", "alice", SheetType.VIEWSHEET,
            new EditorContext("worksheetSqlQuery", "SQL Query1", null, null)));
      assertEquals(PairingException.Kind.USER_MISMATCH, type.getKind(), type.getMessage());
   }

   @Test
   void theProductionSeamDeniesWhenNoSecurityEngineAnswers() {
      SheetPairingService svc = new SheetPairingService(() -> NOW,
         id -> runtime(OWNER), id -> null);

      // No security engine is initialised in a unit test: the real check must answer false.
      assertFalse(svc.freeFormSqlAllowed(OWNER));
      assertFalse(SheetPairingService.isFreeFormSqlAllowed(OWNER));
      PairingException ex = assertThrows(PairingException.class,
         () -> mint(svc, "SQL Query1", null, false));
      assertEquals(PairingException.Kind.USER_MISMATCH, ex.getKind(), ex.getMessage());
   }

   @Test
   void aRuntimeWithNoOwnerIsDeniedFailClosedAndTheGateSeesNull() {
      SheetPairingService svc = service(runtime(null), user -> user != null);

      PairingException ex = refused(svc, "SQL Query1", null, false,
         PairingException.Kind.USER_MISMATCH, "free-form SQL");

      assertEquals(SheetPairingService.FREE_FORM_SQL_DENIED, ex.getMessage());
      assertEquals(1, asked.size());
      assertNull(asked.get(0));
   }

   @Test
   void theProductionCheckDeniesANullUser() {
      assertFalse(SheetPairingService.isFreeFormSqlAllowed(null));
   }

   @Test
   void theGateIsAskedAboutTheRuntimeOwnerNotTheCallerIdentityString() throws Exception {
      Principal other = TestPrincipals.user("bob", "host-org");
      SheetPairingService svc = service(runtime(other), user -> true);

      // alice minting against bob's runtime fails the ownership check first, and the gate is
      // never reached for a runtime that is not hers.
      PairingException ex = assertThrows(PairingException.class,
         () -> mint(svc, "SQL Query1", null, false));
      assertEquals(PairingException.Kind.SESSION_EXPIRED, ex.getKind(), ex.getMessage());
      assertTrue(asked.isEmpty(), "the ownership check runs before the permission gate");
   }

   // ---- an allowed user: the lookups ----------------------------------------------------------

   @Test
   void refusesANonSqlTableAndAMissingTable() {
      SheetPairingService svc = allowed();

      refused(svc, "Embedded1", null, false, PairingException.Kind.INVALID_ARGUMENT,
              "Not a SQL-bound table: Embedded1");
      refused(svc, "Nope", null, false, PairingException.Kind.INVALID_ARGUMENT,
              "Not a SQL-bound table: Nope");
   }

   @Test
   void aPendingNameAlreadyUsedByAnyAssemblyIsRefused() {
      SheetPairingService svc = allowed();

      refused(svc, "SQL Query1", null, true, PairingException.Kind.INVALID_ARGUMENT,
              "Name 'SQL Query1' is already used by an assembly of this worksheet");
      refused(svc, "Embedded1", null, true, PairingException.Kind.INVALID_ARGUMENT,
              "Name 'Embedded1' is already used");
   }

   @Test
   void refusesANameABlankAssemblyAndAViewsheetSheetType() {
      SheetPairingService svc = allowed();

      refused(svc, "SQL Query1", "extra", false, PairingException.Kind.INVALID_ARGUMENT,
              "takes no 'name'");
      refused(svc, "", null, false, PairingException.Kind.INVALID_ARGUMENT, "requires 'assembly'");
      refused(svc, null, null, true, PairingException.Kind.INVALID_ARGUMENT, "requires 'assembly'");

      PairingException type = assertThrows(PairingException.class,
         () -> svc.mint("ws-1", OWNER_IDENTITY, "sock", "alice", SheetType.VIEWSHEET,
            new EditorContext("worksheetSqlQuery", "SQL Query1", null, null)));
      assertEquals(PairingException.Kind.INVALID_ARGUMENT, type.getKind(), type.getMessage());
      assertEquals("editorContext kind 'worksheetSqlQuery' requires a worksheet runtime",
                   type.getMessage());
   }

   @Test
   void aViewsheetRuntimeIsRefusedAsNotAWorksheet() {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getUser()).thenReturn(OWNER);
      SheetPairingService svc = new SheetPairingService(() -> NOW, id -> rvs, id -> null) {
         @Override
         boolean freeFormSqlAllowed(Principal user) {
            return true;
         }
      };

      PairingException ex = assertThrows(PairingException.class,
         () -> mint(svc, "SQL Query1", null, false));
      assertEquals("editorContext kind 'worksheetSqlQuery' requires a worksheet runtime",
                   ex.getMessage());
   }

   @Test
   void aMissingRuntimeIsRefusedAsExpiredAndNeverReachesTheGate() {
      SheetPairingService svc = service(null, user -> true);

      PairingException ex = assertThrows(PairingException.class,
         () -> mint(svc, "SQL Query1", null, false));
      assertEquals(PairingException.Kind.SESSION_EXPIRED, ex.getKind(), ex.getMessage());
      assertTrue(asked.isEmpty());
   }

   /** One spelling of "no such runtime": a foreign owner learns nothing about the worksheet. */
   @Test
   void aForeignOwnerGivesTheSameMessageAsAMissingRuntime() {
      RuntimeWorksheet foreign = runtime(TestPrincipals.user("someone", "host-org"));
      SheetPairingService wrongOwner =
         new SheetPairingService(() -> NOW, id -> foreign, id -> null);
      SheetPairingService missing = new SheetPairingService(() -> NOW, id -> null, id -> null);

      for(String assembly : new String[] { "Nope", "SQL Query1", "" }) {
         EditorContext ctx = new EditorContext("worksheetSqlQuery", assembly, null, null);
         PairingException mismatch = assertThrows(PairingException.class,
            () -> wrongOwner.mint("ws-1", OWNER_IDENTITY, "s", "alice", SheetType.WORKSHEET,
                                  ctx));
         PairingException gone = assertThrows(PairingException.class,
            () -> missing.mint("ws-1", OWNER_IDENTITY, "s", "alice", SheetType.WORKSHEET, ctx));

         assertEquals(PairingException.Kind.SESSION_EXPIRED, mismatch.getKind(), assembly);
         assertEquals(gone.getMessage(), mismatch.getMessage(), assembly);
      }
   }

   // ---- retarget ------------------------------------------------------------------------------

   /** Follow Focus's {@code retarget} reuses exactly this check, gate included. */
   @Test
   void theRetargetCheckAcceptsForAnAllowedUserAndIsGatedForADeniedOne() throws Exception {
      EditorContext ok = new EditorContext("worksheetSqlQuery", "SQL Query1", null, null);

      allowed().validateEditorContext(SheetType.WORKSHEET, "ws-1", OWNER_IDENTITY, ok);

      PairingException gated = assertThrows(PairingException.class,
         () -> denied().validateEditorContext(SheetType.WORKSHEET, "ws-1", OWNER_IDENTITY, ok));
      assertEquals(PairingException.Kind.USER_MISMATCH, gated.getKind(), gated.getMessage());
      assertEquals(SheetPairingService.FREE_FORM_SQL_DENIED, gated.getMessage());

      PairingException probe = assertThrows(PairingException.class,
         () -> denied().validateEditorContext(SheetType.WORKSHEET, "ws-1", OWNER_IDENTITY,
            new EditorContext("worksheetSqlQuery", "Nope", null, null)));
      assertEquals(gated.getMessage(), probe.getMessage());

      assertThrows(PairingException.class,
         () -> allowed().validateEditorContext(SheetType.WORKSHEET, "ws-1", OWNER_IDENTITY,
            new EditorContext("worksheetSqlQuery", "Nope", null, null)));
   }

   /** The other kinds are NOT gated by Free Form SQL: a denied user can still pair a variable. */
   @Test
   void theGateDoesNotAffectTheOtherKinds() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "Query1", "price"));
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      SheetPairingService other = new SheetPairingService(() -> NOW, id -> rws, id -> null) {
         @Override
         boolean freeFormSqlAllowed(Principal user) {
            throw new AssertionError("only worksheetSqlQuery is gated");
         }
      };

      assertNotNull(other.mint("ws-1", OWNER_IDENTITY, "s", "alice", SheetType.WORKSHEET,
         new EditorContext("worksheetVariableDefault", "NewVar", null, null, true)));
      assertNotNull(other.mint("ws-1", OWNER_IDENTITY, "s", "alice", SheetType.WORKSHEET,
         new EditorContext("worksheetCondition", "Query1", "price", null)));
   }

   /**
    * The real seam against an engine that behaves like production: a principal tagged wiz=true
    * (every agent/JWT principal, see WizServiceAuthenticationFilter and Redmine #76630) bypasses
    * the check, a plain one is really checked. The mint judges the runtime OWNER (the real
    * browser-session principal), so a denied owner is refused.
    */
   @Test
   void theRealSeamChecksTheRuntimeOwnerWhichIsNotWizTagged() throws Exception {
      SecurityEngine engine = mock(SecurityEngine.class);
      when(engine.checkPermission(any(), eq(ResourceType.FREE_FORM_SQL), eq("*"),
                                  eq(ResourceAction.ACCESS))).thenReturn(false);
      SheetPairingService svc = new SheetPairingService(() -> NOW,
         id -> runtime(OWNER), id -> null);

      try(MockedStatic<SecurityEngine> security = mockStatic(SecurityEngine.class)) {
         security.when(SecurityEngine::getSecurity).thenReturn(engine);

         PairingException ex = assertThrows(PairingException.class,
            () -> mint(svc, "SQL Query1", null, false));
         assertEquals(PairingException.Kind.USER_MISMATCH, ex.getKind(), ex.getMessage());
         verify(engine).checkPermission(OWNER, ResourceType.FREE_FORM_SQL, "*",
                                        ResourceAction.ACCESS);
      }
   }
}
