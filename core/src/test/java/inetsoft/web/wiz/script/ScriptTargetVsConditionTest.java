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

import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.PairingException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code vsConditionValue} kind: addressing, grammar and id round trip (Bug #80459). */
@Tag("core")
class ScriptTargetVsConditionTest {
   private static final ScriptTarget.Kind KIND = ScriptTarget.Kind.VS_CONDITION_VALUE;

   @Test
   void wireNameRoundTrips() throws Exception {
      assertEquals("vsConditionValue", KIND.wireName());
      assertSame(KIND, ScriptTarget.Kind.fromWire("vsConditionValue"));
      assertEquals(ScriptTarget.Location.VS_CONDITION_VALUE, KIND.location());
      assertTrue(KIND.requiresPaneSession());
   }

   @Test
   void acceptsFilterAndHighlightNames() throws Exception {
      ScriptTarget f = ScriptTarget.of(KIND, "Table1", "filter");
      ScriptTarget h = ScriptTarget.of(KIND, "Chart1", "highlight:High|Sales:x");

      assertEquals("Table1", f.assemblyName());
      assertEquals("filter", f.name());
      assertEquals("highlight:High|Sales:x", h.name());
      assertEquals("vsConditionValue:Table1:filter", f.toString());
   }

   @Test
   void requiresAssemblyAndAValidName() {
      PairingException noAssembly = assertThrows(PairingException.class,
         () -> ScriptTarget.of(KIND, " ", "filter"));
      assertTrue(noAssembly.getMessage().contains("kind 'vsConditionValue' requires 'assembly'"),
                 noAssembly.getMessage());

      for(String bad : new String[] { null, "", "  ", "Filter2", "highlight:", "highlight:  ",
                                      "highlight", "filter:x" })
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> ScriptTarget.of(KIND, "Table1", bad), String.valueOf(bad));
         assertTrue(ex.getMessage().contains("kind 'vsConditionValue' requires 'name'") &&
                    ex.getMessage().contains("\"filter\" or \"highlight:<highlight name>\""),
                    ex.getMessage());
      }
   }

   @Test
   void idRoundTripsWithSeparatorsInTheHighlightName() throws Exception {
      ScriptTarget h = ScriptTarget.of(KIND, "Chart|1", "highlight:a|b\\c");
      ScriptTarget back = ScriptTarget.fromId(h.id());

      assertSame(KIND, back.kind());
      assertEquals("Chart|1", back.assemblyName());
      assertEquals("highlight:a|b\\c", back.name());
   }

   @Test
   void resolveAcceptsKindAssemblyName() throws Exception {
      ScriptTarget t =
         ScriptTarget.resolve(null, null, "vsConditionValue", "Table1", "filter", null);

      assertSame(KIND, t.kind());
   }

   @Test
   void legacyParseNeverLearnsIt() {
      assertThrows(PairingException.class,
                   () -> ScriptTarget.parse("vsConditionValue:Table1:filter"));
   }

   @Test
   void aNameOnAnotherKindStillListsEveryKindThatTakesOne() {
      PairingException ex = assertThrows(PairingException.class,
         () -> ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN, "Table1", "x"));

      assertTrue(ex.getMessage().contains("vsConditionValue"), ex.getMessage());
      assertTrue(ex.getMessage().contains("calcField"), ex.getMessage());
   }

   @Test
   void grammarListsTheKindAsSupported() {
      assertTrue(ScriptGrammar.supportedKinds().contains("vsConditionValue"));
   }

   @Test
   void editorContextNameGrammar() {
      EditorContext.VsConditionScope f = EditorContext.parseVsConditionName("filter");
      EditorContext.VsConditionScope h = EditorContext.parseVsConditionName("highlight:A:b");

      assertFalse(f.highlight());
      assertNull(f.highlightName());
      assertTrue(h.highlight());
      assertEquals("A:b", h.highlightName());
      assertEquals("highlight:A:b", EditorContext.vsHighlightName("A:b"));

      for(String bad : new String[] { null, "", "Filter", "highlight:", "highlight: ", "x" }) {
         assertNull(EditorContext.parseVsConditionName(bad), String.valueOf(bad));
      }
   }
}
