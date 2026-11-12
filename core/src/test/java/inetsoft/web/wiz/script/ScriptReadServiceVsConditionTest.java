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
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptTargetInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@code list}/{@code read} for the draft-only vsConditionValue kind (Bug #80459). */
@WizAgentTestSupport
class ScriptReadServiceVsConditionTest {
   private final ScriptReadService service = new ScriptReadService();

   private static RuntimeViewsheet runtime() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   private static JoinSession session(EditorContext ctx) {
      return new JoinSession("TOK", "Viewsheet/foo", "alice~;~host-org", SheetType.VIEWSHEET, 0L,
         Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, ctx);
   }

   @Test
   void aConditionValueGrantListsExactlyOneSyntheticPendingTarget() {
      List<ScriptTargetInfo> targets = service.list(runtime(), session(
         new EditorContext("vsConditionValue", "Table1", "filter", null)));

      assertEquals(1, targets.size(), targets.toString());
      assertEquals("vsConditionValue", targets.get(0).kind());
      assertEquals(Boolean.TRUE, targets.get(0).pending());
      assertEquals("filter", targets.get(0).name());
   }

   @Test
   void aWholeSheetSessionNeverListsIt() {
      List<ScriptTargetInfo> targets = service.list(runtime(), session(null));

      assertTrue(targets.stream().noneMatch(t -> t.kind().equals("vsConditionValue")),
                 targets.toString());
   }

   @Test
   void anotherPaneGrantNeverListsIt() {
      List<ScriptTargetInfo> targets = service.list(runtime(), session(
         new EditorContext("assemblyMain", "Table1", null, null)));

      assertTrue(targets.stream().noneMatch(t -> t.kind().equals("vsConditionValue")),
                 targets.toString());
   }

   @Test
   void readReturnsAPendingEmptyDraft() throws Exception {
      ScriptInfo info = service.read(runtime(),
         ScriptTarget.of(ScriptTarget.Kind.VS_CONDITION_VALUE, "Table1", "filter"));

      assertEquals("", info.text());
      assertEquals(Boolean.TRUE, info.pending());
      assertNotNull(info.note());
   }

   @Test
   void readOfADeletedAssemblyFailsLoud() throws Exception {
      ScriptTarget gone =
         ScriptTarget.of(ScriptTarget.Kind.VS_CONDITION_VALUE, "Gone", "filter");

      PairingException ex = assertThrows(PairingException.class,
         () -> service.read(runtime(), gone));
      assertTrue(ex.getMessage().contains("Assembly not found: Gone"), ex.getMessage());
   }
}
