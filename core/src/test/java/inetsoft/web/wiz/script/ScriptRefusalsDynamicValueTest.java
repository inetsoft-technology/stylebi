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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * execute / live-run / scripting context / enable / a misrouted write must all refuse the
 * draft-only dynamicValue kind explicitly. Without the guards a blank read falls into
 * {@code executeScript}'s "nothing to execute" early return, which answers ok:true for something
 * that never ran (Bug #80459).
 */
@WizAgentTestSupport
class ScriptRefusalsDynamicValueTest {
   private static RuntimeViewsheet runtime() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   private static ScriptTarget target() throws Exception {
      return ScriptTarget.of(ScriptTarget.Kind.DYNAMIC_VALUE, "Text1", "visible");
   }

   private static final String EXECUTE_MESSAGE =
      "'dynamicValue:Text1:visible': a dynamic value expression is not a runnable script. It is " +
      "a draft in the user's open formula dialog; use update_script to push text into it.";

   @Test
   void dryRunRefusesInsteadOfAnsweringNothingToExecute() throws Exception {
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.dryRun(runtime(), target()));
      assertEquals(EXECUTE_MESSAGE, ex.getMessage());
   }

   @Test
   void runLiveRefuses() throws Exception {
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.runLive(runtime(), target(), true));
      assertEquals(EXECUTE_MESSAGE, ex.getMessage());
   }

   @Test
   void requireRunnableRefusesByKindWithTheKindSpecificMessage() throws Exception {
      assertEquals(EXECUTE_MESSAGE, assertThrows(PairingException.class,
         () -> ScriptExecuteService.requireRunnable(target())).getMessage());

      // The merged vsConditionValue wording is untouched by the isDraftOnly refactor.
      ScriptTarget condition =
         ScriptTarget.of(ScriptTarget.Kind.VS_CONDITION_VALUE, "Text1", "filter");
      assertEquals("'vsConditionValue:Text1:filter': a condition expression is not a runnable " +
                   "script. It is a draft in the user's open formula dialog; use update_script " +
                   "to push text into it.",
                   assertThrows(PairingException.class,
                      () -> ScriptExecuteService.requireRunnable(condition)).getMessage());

      ScriptExecuteService.requireRunnable(ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN,
                                                           "Text1"));
   }

   @Test
   void contextRefusesInsteadOfAnsweringTheAssemblyContext() throws Exception {
      ScriptContextService svc = new ScriptContextService(mock(VSScriptableService.class)) {
         @Override
         List<AssemblyContext> describeAssemblies(RuntimeViewsheet rvs) {
            return List.of(new AssemblyContext("Text1", "text", true, List.of("text"), "T"));
         }
      };

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.context(runtime(), target()));
      assertEquals("'dynamicValue:Text1:visible' has no scripting context: it is a dynamic " +
                   "value draft, not a script.", ex.getMessage());
   }

   @Test
   void enableAndAMisroutedWriteRefuse() throws Exception {
      ScriptEditService svc = new ScriptEditService(mock(SheetSessionService.class),
         mock(SheetRuntimeAccess.class), mock(SheetAgentBroadcastService.class));

      PairingException enable = assertThrows(PairingException.class,
         () -> svc.setEnabled(runtime(), target(), true));
      assertTrue(enable.getMessage().contains("a dynamic value has no enable flag"),
                 enable.getMessage());

      PairingException write = assertThrows(PairingException.class,
         () -> svc.write(runtime(), TestPrincipals.user("alice", "host-org"), target(), "1"));
      assertTrue(write.getMessage().contains("draft-only target"), write.getMessage());
   }
}
