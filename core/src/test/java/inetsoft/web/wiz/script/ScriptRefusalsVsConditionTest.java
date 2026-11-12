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
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.VSScriptableService;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.script.model.AssemblyContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * execute / live-run / scripting context must refuse the draft-only vsConditionValue kind
 * explicitly. Without the guards a blank read falls into {@code executeScript}'s "nothing to
 * execute" early return, which answers ok:true for something that never ran (Bug #80459).
 */
@WizAgentTestSupport
class ScriptRefusalsVsConditionTest {
   private static RuntimeViewsheet runtime() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   private static ScriptTarget target() throws Exception {
      return ScriptTarget.of(ScriptTarget.Kind.VS_CONDITION_VALUE, "Table1", "filter");
   }

   @Test
   void dryRunRefusesInsteadOfAnsweringNothingToExecute() throws Exception {
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.dryRun(runtime(), target()));
      assertTrue(ex.getMessage().contains("a condition expression is not a runnable script"),
                 ex.getMessage());
   }

   @Test
   void runLiveRefuses() throws Exception {
      ScriptExecuteService svc = new ScriptExecuteService(new ScriptReadService());

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.runLive(runtime(), target(), true));
      assertTrue(ex.getMessage().contains("a condition expression is not a runnable script"),
                 ex.getMessage());
   }

   @Test
   void contextRefusesInsteadOfAnsweringTheAssemblyContext() throws Exception {
      ScriptContextService svc = new ScriptContextService(mock(VSScriptableService.class)) {
         @Override
         List<AssemblyContext> describeAssemblies(RuntimeViewsheet rvs) {
            return List.of(new AssemblyContext("Table1", "table", true, List.of("table"), "T"));
         }
      };

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.context(runtime(), target()));
      assertTrue(ex.getMessage().contains("has no scripting context"), ex.getMessage());
   }
}
