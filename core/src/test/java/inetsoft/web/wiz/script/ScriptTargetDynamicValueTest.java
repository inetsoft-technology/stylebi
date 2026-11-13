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

import inetsoft.web.wiz.pairing.PairingException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code dynamicValue} kind: addressing, grammar and id round trip (Bug #80459). */
@Tag("core")
class ScriptTargetDynamicValueTest {
   private static final ScriptTarget.Kind KIND = ScriptTarget.Kind.DYNAMIC_VALUE;

   @Test
   void wireNameRoundTrips() throws Exception {
      assertEquals("dynamicValue", KIND.wireName());
      assertSame(KIND, ScriptTarget.Kind.fromWire("dynamicValue"));
      assertEquals(ScriptTarget.Location.DYNAMIC_VALUE, KIND.location());
   }

   @Test
   void requiresAPaneSessionAndIsDraftOnly() {
      assertTrue(KIND.requiresPaneSession());
      assertTrue(KIND.isDraftOnly());
   }

   @Test
   void isDraftOnlyIsTrueForExactlyTheTwoDraftKinds() {
      Set<ScriptTarget.Kind> draft = EnumSet.noneOf(ScriptTarget.Kind.class);

      for(ScriptTarget.Kind k : ScriptTarget.Kind.values()) {
         if(k.isDraftOnly()) {
            draft.add(k);
         }
      }

      assertEquals(EnumSet.of(ScriptTarget.Kind.VS_CONDITION_VALUE, ScriptTarget.Kind.DYNAMIC_VALUE),
                   draft);
   }

   @Test
   void everyDraftOnlyKindRequiresAPaneSession() {
      for(ScriptTarget.Kind k : ScriptTarget.Kind.values()) {
         if(k.isDraftOnly()) {
            assertTrue(k.requiresPaneSession(), k.wireName());
         }
      }
   }

   @Test
   void acceptsAssemblyAndEveryNamespaceOfName() throws Exception {
      for(String name : new String[] { "visible", "rangeValues[0]", "source:measure",
                                       "binding:x[2].sort.sortByField", "tableOption:rowTotals",
                                       "dateComparison:customPeriods[0].start",
                                       "chartTarget:band.fromLabel", "chartRegion:title/y2/title" })
      {
         ScriptTarget t = ScriptTarget.of(KIND, "Text1", name);

         assertEquals("Text1", t.assemblyName());
         assertEquals(name, t.name());
         assertEquals("dynamicValue:Text1:" + name, t.toString());
      }
   }

   @Test
   void requiresAnAssembly() {
      for(String bad : new String[] { null, "", "  " }) {
         PairingException ex = assertThrows(PairingException.class,
            () -> ScriptTarget.of(KIND, bad, "visible"), String.valueOf(bad));

         assertTrue(ex.getMessage().contains("kind 'dynamicValue' requires 'assembly'"),
                    ex.getMessage());
      }
   }

   @Test
   void requiresAParseableNameAndSaysWhy() {
      for(String bad : new String[] { null, "", "  ", "prop:visible", "a b", "format:x",
                                      "unknown:x", "rangeValues[x]" })
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> ScriptTarget.of(KIND, "Text1", bad), String.valueOf(bad));

         assertTrue(ex.getMessage().contains("kind 'dynamicValue' requires a valid 'name'"),
                    ex.getMessage());
      }

      PairingException prefix = assertThrows(PairingException.class,
         () -> ScriptTarget.of(KIND, "Text1", "prop:visible"));
      assertTrue(prefix.getMessage().contains("drop the 'prop:' prefix"), prefix.getMessage());
   }

   @Test
   void idRoundTripsWithBracketsColonsAndSlashesInTheName() throws Exception {
      for(String name : new String[] { "dateComparison:customPeriods[0].start",
                                       "chartRegion:legend/2/title", "binding:x[10].ranking.n",
                                       "rangeValues[3]" })
      {
         ScriptTarget t = ScriptTarget.of(KIND, "Chart|1", name);
         ScriptTarget back = ScriptTarget.fromId(t.id());

         assertSame(KIND, back.kind());
         assertEquals("Chart|1", back.assemblyName());
         assertEquals(name, back.name());
      }
   }

   @Test
   void resolveAcceptsKindAssemblyName() throws Exception {
      ScriptTarget t = ScriptTarget.resolve(null, null, "dynamicValue", "Text1", "visible", null);

      assertSame(KIND, t.kind());
      assertEquals("visible", t.name());
   }

   @Test
   void legacyParseNeverLearnsIt() {
      assertThrows(PairingException.class, () -> ScriptTarget.parse("dynamicValue:Text1:visible"));
   }

   @Test
   void aNameOnAnotherKindStillListsEveryKindThatTakesOne() {
      PairingException ex = assertThrows(PairingException.class,
         () -> ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN, "Table1", "x"));

      assertTrue(ex.getMessage().contains("dynamicValue"), ex.getMessage());
      assertTrue(ex.getMessage().contains("vsConditionValue"), ex.getMessage());
   }

   @Test
   void grammarListsTheKindAsSupported() {
      assertTrue(ScriptGrammar.supportedKinds().contains("dynamicValue"));
   }
}
