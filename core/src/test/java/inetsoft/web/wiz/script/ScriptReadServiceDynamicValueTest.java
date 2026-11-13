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
import inetsoft.uql.viewsheet.*;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptTargetInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@code list}/{@code read} for the draft-only dynamicValue kind (Bug #80459). */
@WizAgentTestSupport
class ScriptReadServiceDynamicValueTest {
   private final ScriptReadService service = new ScriptReadService();

   private static RuntimeViewsheet runtime() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      vs.addAssembly(new ChartVSAssembly(vs, "Chart1"));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   private static JoinSession session(EditorContext ctx) {
      return new JoinSession("TOK", "Viewsheet/foo", "alice~;~host-org", SheetType.VIEWSHEET, 0L,
         Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, ctx);
   }

   @Test
   void aDynamicValueGrantListsExactlyOneSyntheticPendingTarget() {
      List<ScriptTargetInfo> targets = service.list(runtime(), session(
         new EditorContext("dynamicValue", "Text1", "visible", null)));

      assertEquals(1, targets.size(), targets.toString());
      assertEquals("dynamicValue", targets.get(0).kind());
      assertEquals(Boolean.TRUE, targets.get(0).pending());
      assertEquals("Text1", targets.get(0).assembly());
      assertEquals("visible", targets.get(0).name());
   }

   @Test
   void theLabelUsesTheConcreteTypeOfTheAssemblyOnTheRuntime() {
      List<ScriptTargetInfo> text = service.list(runtime(), session(
         new EditorContext("dynamicValue", "Text1", "visible", null)));
      List<ScriptTargetInfo> chart = service.list(runtime(), session(
         new EditorContext("dynamicValue", "Chart1", "enabled", null)));

      assertTrue(text.get(0).label().contains("property of Text 'Text1'"), text.get(0).label());
      assertTrue(chart.get(0).label().contains("property of Chart 'Chart1'"),
                 chart.get(0).label());
   }

   @Test
   void anAssemblyDeletedSinceTheMintStillListsTheTargetWithoutAType() {
      List<ScriptTargetInfo> targets = service.list(runtime(), session(
         new EditorContext("dynamicValue", "Gone", "visible", null)));

      assertEquals(1, targets.size());
      assertTrue(targets.get(0).label().contains("property of 'Gone'"), targets.get(0).label());
   }

   @Test
   void anUnparsableGrantListsNothing() {
      assertTrue(service.list(runtime(), session(
         new EditorContext("dynamicValue", "Text1", "prop:visible", null))).isEmpty());
   }

   @Test
   void aWholeSheetSessionNeverListsIt() {
      List<ScriptTargetInfo> targets = service.list(runtime(), session(null));

      assertTrue(targets.stream().noneMatch(t -> t.kind().equals("dynamicValue")),
                 targets.toString());
   }

   @Test
   void anotherPaneGrantNeverListsIt() {
      for(EditorContext other : new EditorContext[] {
         new EditorContext("assemblyMain", "Text1", null, null),
         new EditorContext("vsConditionValue", "Text1", "filter", null) })
      {
         List<ScriptTargetInfo> targets = service.list(runtime(), session(other));

         assertTrue(targets.stream().noneMatch(t -> t.kind().equals("dynamicValue")),
                    other.kind() + ": " + targets);
      }
   }

   @Test
   void aConditionGrantStillListsItsOwnSyntheticTargetOnly() {
      List<ScriptTargetInfo> targets = service.list(runtime(), session(
         new EditorContext("vsConditionValue", "Chart1", "highlight:Hi", null)));

      assertEquals(1, targets.size(), targets.toString());
      assertEquals("vsConditionValue", targets.get(0).kind());
   }

   @Test
   void readReturnsAPendingEmptyDraftWithTheNote() throws Exception {
      ScriptInfo info = service.read(runtime(),
         ScriptTarget.of(ScriptTarget.Kind.DYNAMIC_VALUE, "Text1", "visible"));

      assertEquals("", info.text());
      assertEquals(Boolean.TRUE, info.pending());
      assertTrue(info.note().startsWith("Draft-only location"), info.note());
   }

   @Test
   void readOfADeletedAssemblyFailsLoud() throws Exception {
      ScriptTarget gone = ScriptTarget.of(ScriptTarget.Kind.DYNAMIC_VALUE, "Gone", "visible");

      PairingException ex = assertThrows(PairingException.class,
         () -> service.read(runtime(), gone));
      assertTrue(ex.getMessage().contains("Assembly not found: Gone"), ex.getMessage());
   }
}
