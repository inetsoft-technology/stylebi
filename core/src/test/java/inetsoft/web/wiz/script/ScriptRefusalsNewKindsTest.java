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
package inetsoft.web.wiz.script;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.VSScriptableService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.script.model.AssemblyContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * execute / live-run / scripting context / enable / a misrouted write must each refuse the three
 * last draft kinds of Bug #80459 explicitly. {@code write} and {@code setEnabled} are switch
 * STATEMENTS over {@link ScriptTarget.Location} with no {@code default}: a Location they do not
 * name silently does nothing and reports success. The proof is that every refusal below throws
 * and that nothing on the runtime moved.
 */
@WizAgentTestSupport
class ScriptRefusalsNewKindsTest {
   private static RuntimeViewsheet runtime() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   private static ScriptTarget[] targets() throws Exception {
      return new ScriptTarget[] {
         ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_VARIABLE_DEFAULT, "Var1"),
         ScriptTarget.of(ScriptTarget.Kind.VS_HYPERLINK_EXPRESSION, "Text1", "link"),
         ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_SQL_QUERY, "Q1") };
   }

   private static final String[] EXECUTE_WORDS = {
      "a worksheet variable default expression is not a runnable script",
      "a hyperlink expression is not a runnable script",
      "a worksheet SQL query text is not a runnable script" };

   private static final String[] CONTEXT_WORDS = {
      "a worksheet variable default draft", "a hyperlink expression draft",
      "a worksheet SQL draft" };

   @Test
   void requireRunnableRefusesEachWithItsOwnWording() throws Exception {
      ScriptTarget[] targets = targets();

      for(int i = 0; i < targets.length; i++) {
         ScriptTarget t = targets[i];
         PairingException ex = assertThrows(PairingException.class,
            () -> ScriptExecuteService.requireRunnable(t), t.toString());

         assertTrue(ex.getMessage().contains(EXECUTE_WORDS[i]), ex.getMessage());
         assertTrue(ex.getMessage().contains("use update_script to push text into it"),
                    ex.getMessage());
      }
   }

   @Test
   void theSqlRefusalNamesTheDatabaseQueryDialogTheOthersTheFormulaDialog() throws Exception {
      ScriptTarget[] targets = targets();

      assertTrue(assertThrows(PairingException.class,
         () -> ScriptExecuteService.requireRunnable(targets[2])).getMessage()
         .contains("in the user's open Database Query dialog"));
      assertTrue(assertThrows(PairingException.class,
         () -> ScriptExecuteService.requireRunnable(targets[0])).getMessage()
         .contains("in the user's open formula dialog"));
      assertTrue(assertThrows(PairingException.class,
         () -> ScriptExecuteService.requireRunnable(targets[1])).getMessage()
         .contains("in the user's open formula dialog"));
   }

   @Test
   void dryRunAndRunLiveRefuseInsteadOfAnsweringNothingToExecute() throws Exception {
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      for(ScriptTarget t : targets()) {
         assertThrows(PairingException.class, () -> svc.dryRun(runtime(), t), t.toString());
         assertThrows(PairingException.class, () -> svc.runLive(runtime(), t, true),
                      t.toString());
      }
   }

   @Test
   void contextRefusesInsteadOfAnsweringTheAssemblyContext() throws Exception {
      ScriptContextService svc = new ScriptContextService(mock(VSScriptableService.class)) {
         @Override
         List<AssemblyContext> describeAssemblies(RuntimeViewsheet rvs) {
            return List.of(new AssemblyContext("Text1", "text", true, List.of("text"), "T"));
         }
      };
      ScriptTarget[] targets = targets();

      for(int i = 0; i < targets.length; i++) {
         ScriptTarget t = targets[i];
         PairingException ex = assertThrows(PairingException.class,
            () -> svc.context(runtime(), t), t.toString());

         assertEquals("'" + t + "' has no scripting context: it is " + CONTEXT_WORDS[i] +
                      ", not a script.", ex.getMessage());
      }
   }

   /** The statement-switch trap: a misrouted write or enable must throw, not report success. */
   @Test
   void writeAndSetEnabledDoNotSilentlySucceed() throws Exception {
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      ScriptEditService svc = new ScriptEditService(mock(SheetSessionService.class),
         mock(SheetRuntimeAccess.class), broadcast);

      for(ScriptTarget t : targets()) {
         RuntimeViewsheet rvs = runtime();

         PairingException write = assertThrows(PairingException.class,
            () -> svc.write(rvs, TestPrincipals.user("alice", "host-org"), t, "x"), t.toString());
         assertTrue(write.getMessage().contains("target"), write.getMessage());
         assertTrue(write.getMessage().contains(t.kind().wireName()), write.getMessage());

         PairingException enable = assertThrows(PairingException.class,
            () -> svc.setEnabled(rvs, t, true), t.toString());
         assertTrue(enable.getMessage().contains("enable flag"), enable.getMessage());

         verify(rvs, never()).bumpWriteRevision();
         verify(rvs, never()).addCheckpoint(any());
      }

      verifyNoInteractions(broadcast);
   }

   @Test
   void flagSiblingsOfTheKindsAreEmpty() throws Exception {
      ScriptEditService svc = new ScriptEditService(mock(SheetSessionService.class),
         mock(SheetRuntimeAccess.class), mock(SheetAgentBroadcastService.class));

      for(ScriptTarget t : targets()) {
         assertTrue(svc.flagSiblings(runtime(), t, true).isEmpty(), t.toString());
      }
   }

   @Test
   void theViewsheetReadAndWriteDraftRefuseTheWorksheetKinds() throws Exception {
      ScriptEditService svc = new ScriptEditService(mock(SheetSessionService.class),
         mock(SheetRuntimeAccess.class), mock(SheetAgentBroadcastService.class));

      for(ScriptTarget t : new ScriptTarget[] { targets()[0], targets()[2] }) {
         assertThrows(PairingException.class, () -> new ScriptReadService().read(runtime(), t));
         assertThrows(PairingException.class,
            () -> svc.writeDraft(runtime(), TestPrincipals.user("alice", "host-org"), t, "x"));
      }
   }
}
