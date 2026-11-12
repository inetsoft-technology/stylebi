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

import inetsoft.util.script.ScriptEnv;
import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptTargetInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@WizAgentTestSupport
class VsConditionValueTargetsTest {
   private static ScriptTarget target(String assembly, String name) throws Exception {
      return ScriptTarget.of(ScriptTarget.Kind.VS_CONDITION_VALUE, assembly, name);
   }

   @Test
   void theSyntheticTargetIsPendingAndRoundTripsItsId() throws Exception {
      ScriptTargetInfo info = VsConditionValueTargets.targetInfo(
         new EditorContext("vsConditionValue", "Table1", "filter", null));

      assertEquals("vsConditionValue", info.kind());
      assertEquals("Table1", info.assembly());
      assertEquals("filter", info.name());
      assertEquals(Boolean.TRUE, info.pending());
      assertFalse(info.hasScript());
      assertEquals("viewsheet", info.hostSheet());
      assertTrue(info.label().contains("filter condition"), info.label());
      assertTrue(info.label().contains("draft-only"), info.label());

      ScriptTarget back = ScriptTarget.fromId(info.id());
      assertEquals(ScriptTarget.Kind.VS_CONDITION_VALUE, back.kind());
      assertEquals("filter", back.name());
   }

   @Test
   void aHighlightTargetNamesTheHighlight() {
      ScriptTargetInfo info = VsConditionValueTargets.targetInfo(
         new EditorContext("vsConditionValue", "Chart1", "highlight:HighSales", null));

      assertTrue(info.label().contains("highlight 'HighSales'"), info.label());
   }

   @Test
   void anUnparsableGrantYieldsNoTarget() {
      assertNull(VsConditionValueTargets.targetInfo(
         new EditorContext("vsConditionValue", "Table1", "bogus", null)));
   }

   @Test
   void readIsAnEmptyPendingDraft() throws Exception {
      ScriptInfo filter = VsConditionValueTargets.read(target("Table1", "filter"));
      ScriptInfo highlight = VsConditionValueTargets.read(target("Chart1", "highlight:Hi"));

      assertEquals("", filter.text());
      assertEquals(Boolean.TRUE, filter.pending());
      assertTrue(filter.note().contains("Draft-only"), filter.note());
      assertTrue(filter.note().contains("filter condition of 'Table1'"), filter.note());
      assertTrue(filter.note().contains("NOT an empty expression"), filter.note());
      assertTrue(highlight.note().contains("highlight 'Hi' of 'Chart1'"), highlight.note());
      assertTrue(highlight.note().contains("Highlight dialog"), highlight.note());
   }

   /** Parse only: checkFunction is the one engine call, nothing is executed. */
   @Test
   void syntaxProblemParsesWithoutExecuting() throws Exception {
      ScriptEnv env = mock(ScriptEnv.class);
      doThrow(new Exception("Syntax error in viewsheet condition: unexpected end"))
         .when(env).checkFunction("viewsheet condition", "1 +");

      assertNull(VsConditionValueTargets.syntaxProblem(env, "parameter.x > 1"));
      assertTrue(VsConditionValueTargets.syntaxProblem(env, "1 +").contains("Syntax error"));
      verify(env).checkFunction("viewsheet condition", "parameter.x > 1");
      verify(env).checkFunction("viewsheet condition", "1 +");
      verifyNoMoreInteractions(env);
   }

   @Test
   void syntaxProblemToleratesAnEngineThatIsNotInitialised() throws Exception {
      ScriptEnv env = mock(ScriptEnv.class);
      doThrow(new IllegalStateException("Engine not initialized"))
         .when(env).checkFunction(any(), any());

      assertNull(VsConditionValueTargets.syntaxProblem(env, "1 +"));
      assertNull(VsConditionValueTargets.syntaxProblem(null, "1 +"));
   }
}
