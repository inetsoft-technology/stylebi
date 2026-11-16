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

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.DefaultVariableAssembly;
import inetsoft.uql.asset.SQLBoundTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.script.ScriptTarget;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptTargetInfo;
import inetsoft.web.wiz.script.model.ScriptWriteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@code worksheetVariableDefault} and {@code worksheetSqlQuery} through
 * {@link WorksheetScriptService} (Bug #80459). Both are draft-only: nothing on the worksheet is
 * ever written, the text is pushed into a dialog the user has open.
 *
 * <p>The SQL kind is permission-sensitive. The Free Form SQL check is the overridable seam
 * {@code freeFormSqlAllowed}; every test that proves a refusal also proves it came BEFORE the
 * lookup or the push (a spy on the edit service and the broadcaster), because a refusal that
 * runs after the work leaks the work.
 */
@WizAgentTestSupport
class WorksheetScriptServiceDraftKindsTest {
   private static final Principal OWNER = TestPrincipals.user("alice", "host-org");
   private static final String SQL_TEXT = "select a, b from t where c = $(v) and d = '=x'";

   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private final WorksheetEditService editService = mock(WorksheetEditService.class);
   private final WorksheetAgentController worksheetController =
      mock(WorksheetAgentController.class);
   private final SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

   /** The principals the Free Form SQL seam was asked about, in order. */
   private final List<Principal> asked = new ArrayList<>();
   private boolean sqlAllowed = true;
   private boolean sqlSeamForbidden = false;

   private final WorksheetScriptService service =
      new WorksheetScriptService(editService, worksheetController, broadcast) {
         @Override
         boolean freeFormSqlAllowed(Principal user) {
            if(sqlSeamForbidden) {
               throw new AssertionError("only worksheetSqlQuery is gated by Free Form SQL");
            }

            asked.add(user);
            return sqlAllowed;
         }

         // The script engine is not initialised in unit tests: "1 +" is told apart by hand.
         @Override
         String syntaxProblem(String text) {
            return text.trim().endsWith("+") ? "unexpected end of input" : null;
         }
      };

   private RuntimeWorksheet rws;
   private Worksheet ws;

   @BeforeEach
   void setUp() throws Exception {
      ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, "Var1"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "Embedded1", "price", "cost"));
      ws.addAssembly(sqlTable("SQL Query1", SQL_TEXT));

      rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getUser()).thenReturn(OWNER);
      when(editService.resolve("TOK", agent)).thenReturn(rws);
      when(broadcast.pushScriptRefresh(any(), any(), any(), any(), any(), any(), any()))
         .thenReturn(true);
   }

   private SQLBoundTableAssembly sqlTable(String name, String sql) {
      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, name);
      UniformSQL usql = new UniformSQL();
      usql.setSQLString(sql, false);
      JDBCQuery query = new JDBCQuery();
      query.setSQLDefinition(usql);
      ((SQLBoundTableAssemblyInfo) table.getInfo()).setQuery(query);
      return table;
   }

   // ---------------------------------------------------------------------------
   // Fixtures
   // ---------------------------------------------------------------------------

   private static JoinSession session(EditorContext ctx, SheetType type) {
      return new JoinSession("TOK", "Worksheet/ws-1", "alice~;~host-org", type, 0L,
         Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, ctx);
   }

   private static JoinSession sessionScopedTo(EditorContext ctx) {
      return session(ctx, SheetType.WORKSHEET);
   }

   private static EditorContext variable(String name, boolean pending) {
      return new EditorContext("worksheetVariableDefault", name, null, null, pending);
   }

   private static EditorContext sql(String table, boolean pending) {
      return new EditorContext("worksheetSqlQuery", table, null, null, pending);
   }

   private static ScriptTarget variableTarget(String name) throws Exception {
      return ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_VARIABLE_DEFAULT, name);
   }

   private static ScriptTarget sqlTarget(String table) throws Exception {
      return ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_SQL_QUERY, table);
   }

   private void assertNothingPushed() {
      verify(broadcast, never()).pushScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                    any());
   }

   private void assertWorksheetUntouched() throws Exception {
      verifyNoInteractions(worksheetController);
      verify(broadcast, never()).broadcastScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                         any());
      verify(broadcast, never()).broadcastRefresh(any(), any(), any(), any());
   }

   private static PairingException denied(org.junit.jupiter.api.function.Executable action) {
      PairingException ex = assertThrows(PairingException.class, action);

      assertEquals(PairingException.Kind.USER_MISMATCH, ex.getKind(), ex.getMessage());
      assertEquals(SheetPairingService.FREE_FORM_SQL_DENIED, ex.getMessage());
      return ex;
   }

   // ---------------------------------------------------------------------------
   // worksheetVariableDefault
   // ---------------------------------------------------------------------------

   @Test
   void aVariableReadIsAnEmptyPendingDraftAndNeverLooksAtTheWorksheet() throws Exception {
      ScriptInfo info = service.read(sessionScopedTo(variable("Var1", false)), agent,
                                     variableTarget("Var1"));

      assertEquals("", info.text());
      assertEquals(Boolean.TRUE, info.pending());
      assertTrue(info.enabled());
      assertEquals("worksheetVariableDefault:Var1", info.target());
      assertTrue(info.note().contains("Draft-only location: the Expression default value of " +
                                      "worksheet variable 'Var1'"), info.note());
      assertTrue(info.note().contains("NOT an empty expression and NOT a failed update_script"),
                 info.note());
      assertTrue(info.note().contains("read_worksheet_model"), info.note());
      verifyNoInteractions(editService);
   }

   @Test
   void aNewVariableReadSaysItDoesNotExistUntilOk() throws Exception {
      ScriptInfo info = service.read(sessionScopedTo(variable("NewVar", true)), agent,
                                     variableTarget("NewVar"));

      assertTrue(info.note().contains("the NEW variable 'NewVar', which does not exist until " +
                                      "OK"), info.note());
   }

   @Test
   void aVariableWritePushesTheStrippedTextAndNeverTouchesTheWorksheet() throws Exception {
      ScriptWriteResult result = service.write(sessionScopedTo(variable("Var1", false)), agent,
                                               variableTarget("Var1"), "= 1 > 0");

      verify(broadcast).pushScriptRefresh(rws, "Worksheet/ws-1", agent, "worksheetVariableDefault",
                                          "Var1", null, "1 > 0");
      assertTrue(result.ok());
      assertEquals(Boolean.TRUE, result.pending());
      assertNull(result.warning());
      assertTrue(result.note().contains("Nothing is applied"), result.note());
      assertTrue(result.note().contains("worksheet variable 'Var1'"), result.note());
      assertTrue(result.note().endsWith(" A leading '=' was removed: the dialog's editor holds " +
                                        "the expression without it."), result.note());
      assertWorksheetUntouched();
   }

   @Test
   void aTextWithoutALeadingEqualsIsPushedVerbatimWithoutTheExtraSentence() throws Exception {
      ScriptWriteResult result = service.write(sessionScopedTo(variable("Var1", false)), agent,
                                               variableTarget("Var1"), "1 + 1");

      verify(broadcast).pushScriptRefresh(rws, "Worksheet/ws-1", agent, "worksheetVariableDefault",
                                          "Var1", null, "1 + 1");
      assertFalse(result.note().contains("leading '='"), result.note());
   }

   @Test
   void aNewVariableWriteSaysTheVariableDoesNotExistYet() throws Exception {
      ScriptWriteResult result = service.write(sessionScopedTo(variable("NewVar", true)), agent,
                                               variableTarget("NewVar"), "7");

      assertTrue(result.note().contains("the NEW variable 'NewVar'"), result.note());
      assertTrue(result.note().contains("the variable does not exist until then"),
                 result.note());
   }

   @Test
   void aVariableWriteIsRefusedBeforeAnyPushForBlankDoubleEqualsAndSyntaxErrors()
      throws Exception
   {
      for(String bad : new String[] { "", "   ", "=", " = ", "==x", "1 +", "= 1 +" }) {
         PairingException ex = assertThrows(PairingException.class,
            () -> service.write(sessionScopedTo(variable("Var1", false)), agent,
                                variableTarget("Var1"), bad), bad);

         assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind(), ex.getMessage());
      }

      PairingException nul = assertThrows(PairingException.class,
         () -> service.write(sessionScopedTo(variable("Var1", false)), agent,
                             variableTarget("Var1"), null));
      assertTrue(nul.getMessage().contains("text is required."), nul.getMessage());
      assertNothingPushed();
      assertWorksheetUntouched();
   }

   @Test
   void aVariableWriteFailsLoudWhenTheDialogCannotBeReached() throws Exception {
      when(broadcast.pushScriptRefresh(any(), any(), any(), any(), any(), any(), any()))
         .thenReturn(false);

      PairingException ex = assertThrows(PairingException.class,
         () -> service.write(sessionScopedTo(variable("Var1", false)), agent,
                             variableTarget("Var1"), "1"));

      assertTrue(ex.getMessage().contains("is not reachable (closed or disconnected), so " +
                                          "nothing was pushed"), ex.getMessage());
      assertTrue(ex.getMessage().contains("variable 'Var1'"), ex.getMessage());
      assertWorksheetUntouched();
   }

   @Test
   void aVariableWriteStampsTheSocketSessionOntoTheRuntimeOnlyWhenItHasNone() throws Exception {
      JoinSession stamped = new JoinSession("TOK", "Worksheet/ws-1", "alice~;~host-org",
         SheetType.WORKSHEET, 0L, Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, "sock-1",
         "alice", variable("Var1", false));

      service.write(stamped, agent, variableTarget("Var1"), "1");

      verify(rws).setSocketSessionId("sock-1");
      verify(rws).setSocketUserName("alice");
   }

   @Test
   void theVariableKindIsNotGatedByFreeFormSql() throws Exception {
      sqlSeamForbidden = true;
      sqlAllowed = false;

      service.read(sessionScopedTo(variable("Var1", false)), agent, variableTarget("Var1"));
      service.write(sessionScopedTo(variable("Var1", false)), agent, variableTarget("Var1"), "1");
      assertEquals(1, service.list(rws, sessionScopedTo(variable("Var1", false))).size());
   }

   @Test
   void theVariableListHasOneDraftTargetFromTheGrantAndSkipsTheTableEnumeration() {
      List<ScriptTargetInfo> targets = service.list(rws, sessionScopedTo(variable("Var1", false)));

      assertEquals(1, targets.size());
      ScriptTargetInfo t = targets.get(0);
      assertEquals("worksheetVariableDefault", t.kind());
      assertEquals("Var1", t.assembly());
      assertNull(t.name());
      assertEquals("Expression of the default value of worksheet variable 'Var1' (draft-only: " +
                   "the value is in the user's open Variable Properties dialog)", t.label());
      assertEquals("when the variable's value is requested, once the user applies the dialog",
                   t.runsWhen());
      assertFalse(t.hasScript());
      assertTrue(t.enabled());
      assertEquals("worksheetVariableDefault:Var1", t.enableScope());
      assertEquals("worksheet", t.hostSheet());
      assertEquals(Boolean.TRUE, t.pending());
      verify(rws, never()).getWorksheet();
   }

   @Test
   void aVariableGrantIsRefusedAnotherVariableAndTheSqlKindOfTheSameName() throws Exception {
      JoinSession s = sessionScopedTo(variable("Var1", false));

      PairingException other = assertThrows(PairingException.class,
         () -> service.write(s, agent, variableTarget("Var2"), "1"));
      assertTrue(other.getMessage().contains("This session is paired to the default-value " +
                                             "expression of worksheet variable 'Var1'"),
                 other.getMessage());

      assertThrows(PairingException.class, () -> service.read(s, agent, sqlTarget("Var1")));
      assertThrows(PairingException.class, () -> service.write(s, agent, sqlTarget("Var1"), "x"));
      assertNothingPushed();
      assertTrue(asked.isEmpty(), "a refused scope never consults the permission");
   }

   // ---------------------------------------------------------------------------
   // worksheetSqlQuery: read
   // ---------------------------------------------------------------------------

   @Test
   void anExistingSqlTableReadReturnsTheExactAppliedSql() throws Exception {
      ScriptInfo info = service.read(sessionScopedTo(sql("SQL Query1", false)), agent,
                                     sqlTarget("SQL Query1"));

      assertEquals(SQL_TEXT, info.text());
      assertEquals(Boolean.FALSE, info.pending(), "the text is real, read from the worksheet");
      assertTrue(info.enabled());
      assertEquals("worksheetSqlQuery:SQL Query1", info.target());
      assertTrue(info.note().contains("This is the SQL last applied to query table " +
                                      "'SQL Query1'"), info.note());
      assertTrue(info.note().contains("Text typed in the open dialog and not yet applied (OK) " +
                                      "is not visible here"), info.note());
      assertTrue(info.note().contains("update_script pushes a DRAFT"), info.note());
      assertEquals(List.of(agent), asked, "the read is gated on the agent principal, once");
   }

   @Test
   void aReadReturnsTheSqlByteForByte() throws Exception {
      String exact = "  SELECT *\r\n  FROM \"t\"  \n";
      ws.addAssembly(sqlTable("Exact", exact));

      assertEquals(exact, service.read(sessionScopedTo(sql("Exact", false)), agent,
                                       sqlTarget("Exact")).text());
   }

   @Test
   void aDeniedUserIsRefusedBeforeAnyTableLookup() throws Exception {
      sqlAllowed = false;

      denied(() -> service.read(sessionScopedTo(sql("SQL Query1", false)), agent,
                                sqlTarget("SQL Query1")));
      verifyNoInteractions(editService);
      verify(rws, never()).getWorksheet();
      assertEquals(List.of(agent), asked);
   }

   /** The probe test: a denied user learns nothing about which tables exist. */
   @Test
   void aDeniedReadGivesTheSameMessageForARealAMissingAndANonSqlTable() throws Exception {
      sqlAllowed = false;
      String reference = denied(() -> service.read(sessionScopedTo(sql("SQL Query1", false)),
                                                    agent, sqlTarget("SQL Query1"))).getMessage();

      for(String table : new String[] { "Nope", "Embedded1", "Var1" }) {
         PairingException ex = assertThrows(PairingException.class,
            () -> service.read(sessionScopedTo(sql(table, false)), agent, sqlTarget(table)),
            table);

         assertEquals(PairingException.Kind.USER_MISMATCH, ex.getKind(), table);
         assertEquals(reference, ex.getMessage(), table);
      }

      PairingException pending = assertThrows(PairingException.class,
         () -> service.read(sessionScopedTo(sql("Brand New", true)), agent, sqlTarget("Brand New")));
      assertEquals(reference, pending.getMessage());
      verifyNoInteractions(editService);
   }

   @Test
   void aNonSqlTableAndAMissingTableAreRefusedForAnAllowedUser() throws Exception {
      for(String table : new String[] { "Embedded1", "Var1", "Nope" }) {
         PairingException ex = assertThrows(PairingException.class,
            () -> service.read(sessionScopedTo(sql(table, false)), agent, sqlTarget(table)),
            table);

         assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind(), table);
         assertTrue(ex.getMessage().startsWith("Not a SQL-bound table: " + table), ex.getMessage());
      }
   }

   @Test
   void aNewQueryReadsEmptyAndPendingAndSaysNothingIsAppliedYet() throws Exception {
      ScriptInfo info = service.read(sessionScopedTo(sql("SQL Query2", true)), agent,
                                     sqlTarget("SQL Query2"));

      assertEquals("", info.text());
      assertEquals(Boolean.TRUE, info.pending());
      assertTrue(info.note().contains("NEW query table 'SQL Query2'"), info.note());
      assertTrue(info.note().contains("no SQL is applied yet"), info.note());
      assertTrue(info.note().contains("not yet applied (OK) is not visible here"), info.note());
      assertEquals(List.of(agent), asked, "even a pending read is gated");
      verifyNoInteractions(editService);
   }

   @Test
   void aTableWithNoSqlDefinitionOrNoQueryReadsEmptyWithoutAnNpe() throws Exception {
      SQLBoundTableAssembly noDefinition = new SQLBoundTableAssembly(ws, "NoDefinition");
      ((SQLBoundTableAssemblyInfo) noDefinition.getInfo()).setQuery(new JDBCQuery());
      ws.addAssembly(noDefinition);
      SQLBoundTableAssembly noQuery = new SQLBoundTableAssembly(ws, "NoQuery");
      ((SQLBoundTableAssemblyInfo) noQuery.getInfo()).setQuery(null);
      ws.addAssembly(noQuery);

      for(String table : new String[] { "NoDefinition", "NoQuery" }) {
         ScriptInfo info = service.read(sessionScopedTo(sql(table, false)), agent,
                                        sqlTarget(table));

         assertEquals("", info.text(), table);
         assertEquals(Boolean.FALSE, info.pending(), table);
      }
   }

   @Test
   void aSqlReadWithoutARuntimeFailsAsNotASqlTable() throws Exception {
      when(editService.resolve("TOK", agent)).thenReturn(null);

      PairingException ex = assertThrows(PairingException.class,
         () -> service.read(sessionScopedTo(sql("SQL Query1", false)), agent,
                            sqlTarget("SQL Query1")));
      assertTrue(ex.getMessage().startsWith("Not a SQL-bound table"), ex.getMessage());
   }

   // ---------------------------------------------------------------------------
   // worksheetSqlQuery: write
   // ---------------------------------------------------------------------------

   @Test
   void anAllowedWritePushesTheTextByteIdenticalAndNeverTouchesTheWorksheet() throws Exception {
      String text = "  =select 1 -- not an expression\n  from dual where x = $(a)  ";

      ScriptWriteResult result = service.write(sessionScopedTo(sql("SQL Query1", false)), agent,
                                               sqlTarget("SQL Query1"), text);

      verify(broadcast).pushScriptRefresh(rws, "Worksheet/ws-1", agent, "worksheetSqlQuery",
                                          "SQL Query1", null, text);
      assertTrue(result.ok());
      assertEquals(Boolean.TRUE, result.pending());
      assertNull(result.warning());
      assertTrue(result.note().startsWith("Pushed to the open Database Query dialog as a draft " +
                                          "of the SQL for 'SQL Query1'"), result.note());
      assertTrue(result.note().contains("Nothing is applied until the user clicks OK"),
                 result.note());
      assertTrue(result.note().contains("Parse Now"), result.note());
      assertTrue(result.note().contains("with the user's own permissions"), result.note());
      assertWorksheetUntouched();
      assertEquals(List.of(agent), asked);
   }

   @Test
   void aDeniedWriteIsRefusedBeforeAnyPushAndBeforeAnyLookup() throws Exception {
      sqlAllowed = false;

      denied(() -> service.write(sessionScopedTo(sql("SQL Query1", false)), agent,
                                 sqlTarget("SQL Query1"), "select 1"));

      assertNothingPushed();
      verifyNoInteractions(editService);
      assertWorksheetUntouched();
   }

   @Test
   void aDeniedWriteIsRefusedBeforeTheTextIsValidated() throws Exception {
      sqlAllowed = false;

      for(String text : new String[] { null, "", "   ", "select 1" }) {
         denied(() -> service.write(sessionScopedTo(sql("SQL Query1", false)), agent,
                                    sqlTarget("SQL Query1"), text));
      }

      assertNothingPushed();
   }

   @Test
   void aBlankOrNullSqlTextIsRefusedAndNothingIsPushed() throws Exception {
      for(String bad : new String[] { "", "   ", "\n\t " }) {
         PairingException ex = assertThrows(PairingException.class,
            () -> service.write(sessionScopedTo(sql("SQL Query1", false)), agent,
                                sqlTarget("SQL Query1"), bad), bad);

         assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind());
         assertTrue(ex.getMessage().contains("Nothing was pushed"), ex.getMessage());
      }

      PairingException nul = assertThrows(PairingException.class,
         () -> service.write(sessionScopedTo(sql("SQL Query1", false)), agent,
                             sqlTarget("SQL Query1"), null));
      assertTrue(nul.getMessage().contains("text is required."), nul.getMessage());
      assertNothingPushed();
   }

   @Test
   void aSqlTextIsNeverSyntaxCheckedAsJavaScript() throws Exception {
      // The seam above flags a trailing '+': SQL must not be run through it.
      service.write(sessionScopedTo(sql("SQL Query1", false)), agent, sqlTarget("SQL Query1"),
                    "select a +");

      verify(broadcast).pushScriptRefresh(rws, "Worksheet/ws-1", agent, "worksheetSqlQuery",
                                          "SQL Query1", null, "select a +");
   }

   @Test
   void aSqlWriteFailsLoudWhenTheDialogCannotBeReached() throws Exception {
      when(broadcast.pushScriptRefresh(any(), any(), any(), any(), any(), any(), any()))
         .thenReturn(false);

      PairingException ex = assertThrows(PairingException.class,
         () -> service.write(sessionScopedTo(sql("SQL Query1", false)), agent,
                             sqlTarget("SQL Query1"), "select 1"));

      assertTrue(ex.getMessage().contains("The Database Query dialog for 'SQL Query1' is not " +
                                          "reachable (closed or disconnected), so nothing was " +
                                          "pushed"), ex.getMessage());
      assertWorksheetUntouched();
   }

   @Test
   void aSqlWriteWithoutARuntimeIsRefusedAsExpired() throws Exception {
      when(editService.resolve("TOK", agent)).thenReturn(null);

      PairingException ex = assertThrows(PairingException.class,
         () -> service.write(sessionScopedTo(sql("SQL Query1", false)), agent,
                             sqlTarget("SQL Query1"), "select 1"));
      assertEquals(PairingException.Kind.SESSION_EXPIRED, ex.getKind(), ex.getMessage());
      assertNothingPushed();
   }

   @Test
   void otherFieldsSentAlongsideTheTextAreRefused() throws Exception {
      assertThrows(PairingException.class,
         () -> service.write(sessionScopedTo(sql("SQL Query1", false)), agent,
                             sqlTarget("SQL Query1"), "select 1", Map.of("type", "string")));
      assertThrows(PairingException.class,
         () -> service.write(sessionScopedTo(variable("Var1", false)), agent,
                             variableTarget("Var1"), "1", Map.of("name", "Var2")));
      assertNothingPushed();
   }

   // ---------------------------------------------------------------------------
   // worksheetSqlQuery: a permission revoked after mint
   // ---------------------------------------------------------------------------

   @Test
   void aRevocationAfterMintIsHonouredOnTheNextWriteReadAndList() throws Exception {
      JoinSession s = sessionScopedTo(sql("SQL Query1", false));

      service.write(s, agent, sqlTarget("SQL Query1"), "select 1");
      assertEquals(SQL_TEXT, service.read(s, agent, sqlTarget("SQL Query1")).text());
      assertEquals(1, service.list(rws, s).size());
      verify(broadcast, times(1)).pushScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                     any());

      sqlAllowed = false;
      clearInvocations(editService);

      denied(() -> service.write(s, agent, sqlTarget("SQL Query1"), "select 2"));
      denied(() -> service.read(s, agent, sqlTarget("SQL Query1")));
      assertTrue(service.list(rws, s).isEmpty());

      verify(broadcast, times(1)).pushScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                     any());
      verifyNoInteractions(editService);
   }

   // ---------------------------------------------------------------------------
   // worksheetSqlQuery: list
   // ---------------------------------------------------------------------------

   @Test
   void theSqlListHasOneDraftTargetFromTheGrantAndAsksTheRuntimeOwner() {
      Principal other = TestPrincipals.user("bob", "host-org");
      when(rws.getUser()).thenReturn(other);

      List<ScriptTargetInfo> targets = service.list(rws, sessionScopedTo(sql("SQL Query1", false)));

      assertEquals(1, targets.size());
      ScriptTargetInfo t = targets.get(0);
      assertEquals("worksheetSqlQuery", t.kind());
      assertEquals("SQL Query1", t.assembly());
      assertNull(t.name());
      assertTrue(t.label().startsWith("SQL of query table 'SQL Query1' (draft-only: update_script " +
                                      "pushes into the user's open Database Query dialog; " +
                                      "free-form SQL;"), t.label());
      assertTrue(t.label().contains("not text typed in the open dialog and not yet applied " +
                                    "with OK"), t.label());
      assertEquals("each time 'SQL Query1' is queried, once the user clicks OK", t.runsWhen());
      assertEquals("worksheetSqlQuery:SQL Query1", t.enableScope());
      assertEquals("worksheet", t.hostSheet());
      assertEquals(Boolean.TRUE, t.pending());
      assertEquals(List.of(other), asked, "list asks about rws.getUser(), not the agent");
      verify(rws, never()).getWorksheet();
   }

   @Test
   void aDeniedListIsEmptyAndANullRuntimeOrOwnerIsDenied() {
      sqlAllowed = false;
      assertTrue(service.list(rws, sessionScopedTo(sql("SQL Query1", false))).isEmpty());

      sqlAllowed = true;
      assertTrue(service.list(null, sessionScopedTo(sql("SQL Query1", false))).isEmpty());
   }

   @Test
   void aNullOwnerReachesTheSeamAsNullSoItCanFailClosed() {
      when(rws.getUser()).thenReturn(null);
      sqlAllowed = false;

      assertTrue(service.list(rws, sessionScopedTo(sql("SQL Query1", false))).isEmpty());
      assertEquals(1, asked.size());
      assertNull(asked.get(0));
   }

   @Test
   void theProductionSeamDeniesWhenNoSecurityEngineAnswers() {
      WorksheetScriptService real =
         new WorksheetScriptService(editService, worksheetController, broadcast);

      assertFalse(real.freeFormSqlAllowed(OWNER));
      assertFalse(real.freeFormSqlAllowed(null));
   }

   @Test
   void aWholeSheetSessionStillListsTheTablesAndNoDraftTarget() {
      List<ScriptTargetInfo> targets = service.list(rws, sessionScopedTo(null));

      assertTrue(targets.stream().noneMatch(t -> "worksheetSqlQuery".equals(t.kind()) ||
                                                 "worksheetVariableDefault".equals(t.kind())));
      assertTrue(asked.isEmpty());
   }

   // ---------------------------------------------------------------------------
   // sessions that must never reach the two kinds
   // ---------------------------------------------------------------------------

   @Test
   void aWholeSheetSessionIsRefusedReadAndWriteOfBothKinds() throws Exception {
      JoinSession whole = sessionScopedTo(null);

      for(ScriptTarget t : new ScriptTarget[] { variableTarget("Var1"), sqlTarget("SQL Query1") }) {
         PairingException read = assertThrows(PairingException.class,
            () -> service.read(whole, agent, t), t.toString());
         PairingException write = assertThrows(PairingException.class,
            () -> service.write(whole, agent, t, "x"), t.toString());

         assertTrue(read.getMessage().contains("has no name at sheet level"), read.getMessage());
         assertTrue(write.getMessage().contains("Connect to Claude"), write.getMessage());
      }

      assertNothingPushed();
      verifyNoInteractions(editService);
      assertTrue(asked.isEmpty(), "the whole-sheet refusal comes before the permission");
   }

   @Test
   void aViewsheetSessionIsRefused() throws Exception {
      JoinSession vs = session(sql("SQL Query1", false), SheetType.VIEWSHEET);

      PairingException ex = assertThrows(PairingException.class,
         () -> service.write(vs, agent, sqlTarget("SQL Query1"), "select 1"));
      assertTrue(ex.getMessage().contains("require a session joined to a worksheet runtime"),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("worksheetSqlQuery"), ex.getMessage());
      assertThrows(PairingException.class,
         () -> service.read(vs, agent, sqlTarget("SQL Query1")));
      assertThrows(PairingException.class,
         () -> service.write(session(variable("Var1", false), SheetType.VIEWSHEET), agent,
                             variableTarget("Var1"), "1"));

      assertNothingPushed();
      verifyNoInteractions(editService);
      assertTrue(asked.isEmpty());
   }

   @Test
   void aSqlGrantIsRefusedAnotherTableAndAnotherKind() throws Exception {
      JoinSession s = sessionScopedTo(sql("SQL Query1", false));

      PairingException other = assertThrows(PairingException.class,
         () -> service.write(s, agent, sqlTarget("Embedded1"), "select 1"));
      assertTrue(other.getMessage().contains("This session is paired to the SQL of worksheet " +
                                             "query table 'SQL Query1'"), other.getMessage());

      assertThrows(PairingException.class, () -> service.read(s, agent, variableTarget("SQL Query1")));
      assertThrows(PairingException.class, () -> service.write(s, agent,
         ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_CONDITION_VALUE, "SQL Query1", "price"), "1"));
      assertNothingPushed();
      assertTrue(asked.isEmpty(), "a refused scope never consults the permission");
      verifyNoInteractions(worksheetController);
   }

   @Test
   void aWorksheetConditionValueGrantCannotReachTheSqlKind() throws Exception {
      JoinSession s = sessionScopedTo(
         new EditorContext("worksheetConditionValue", "SQL Query1", "price", null));

      assertThrows(PairingException.class, () -> service.read(s, agent, sqlTarget("SQL Query1")));
      assertThrows(PairingException.class,
         () -> service.write(s, agent, sqlTarget("SQL Query1"), "select 1"));
      assertNothingPushed();
      assertTrue(asked.isEmpty());
   }
}
