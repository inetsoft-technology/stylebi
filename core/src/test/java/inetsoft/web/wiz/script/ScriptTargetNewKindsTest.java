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

import static org.junit.jupiter.api.Assertions.*;

/**
 * The three draft-only dialog kinds added last in Bug #80459 ({@code worksheetVariableDefault},
 * {@code vsHyperlinkExpression}, {@code worksheetSqlQuery}): addressing, grammar and id round trip.
 */
@Tag("core")
class ScriptTargetNewKindsTest {
   private static final ScriptTarget.Kind VARIABLE = ScriptTarget.Kind.WORKSHEET_VARIABLE_DEFAULT;
   private static final ScriptTarget.Kind HYPERLINK = ScriptTarget.Kind.VS_HYPERLINK_EXPRESSION;
   private static final ScriptTarget.Kind SQL = ScriptTarget.Kind.WORKSHEET_SQL_QUERY;
   private static final ScriptTarget.Kind[] KINDS = { VARIABLE, HYPERLINK, SQL };

   @Test
   void wireNamesAndLocationsRoundTrip() {
      assertEquals("worksheetVariableDefault", VARIABLE.wireName());
      assertEquals("vsHyperlinkExpression", HYPERLINK.wireName());
      assertEquals("worksheetSqlQuery", SQL.wireName());

      for(ScriptTarget.Kind k : KINDS) {
         assertDoesNotThrow(() -> assertSame(k, ScriptTarget.Kind.fromWire(k.wireName())));
         assertNotNull(k.location(), k.wireName());
      }

      assertEquals(ScriptTarget.Location.WORKSHEET_VARIABLE_DEFAULT, VARIABLE.location());
      assertEquals(ScriptTarget.Location.VS_HYPERLINK_EXPRESSION, HYPERLINK.location());
      assertEquals(ScriptTarget.Location.WORKSHEET_SQL_QUERY, SQL.location());
   }

   @Test
   void everyOneIsDraftOnlyAndRequiresAPaneSession() {
      for(ScriptTarget.Kind k : KINDS) {
         assertTrue(k.isDraftOnly(), k.wireName());
         assertTrue(k.requiresPaneSession(), k.wireName());
      }
   }

   @Test
   void theEnumNamesTheKindsInTheUnknownKindAndGrammarLists() {
      PairingException ex = assertThrows(PairingException.class,
         () -> ScriptTarget.Kind.fromWire("worksheetSqlQueries"));

      for(ScriptTarget.Kind k : KINDS) {
         assertTrue(ex.getMessage().contains(k.wireName()), ex.getMessage());
         assertTrue(ScriptGrammar.supportedKinds().contains(k.wireName()), k.wireName());
      }
   }

   // ---- worksheetVariableDefault and worksheetSqlQuery: assembly only -------------------------

   @Test
   void assemblyOnlyKindsCarryTheNameAndNoSecondComponent() throws Exception {
      for(ScriptTarget.Kind k : new ScriptTarget.Kind[] { VARIABLE, SQL }) {
         ScriptTarget t = ScriptTarget.of(k, "Var 1");

         assertEquals("Var 1", t.assemblyName());
         assertNull(t.name());
         assertEquals(k.wireName() + ":Var 1", t.toString());
      }
   }

   @Test
   void assemblyOnlyKindsRequireTheAssemblyAndNameTheKindInTheError() {
      for(ScriptTarget.Kind k : new ScriptTarget.Kind[] { VARIABLE, SQL }) {
         for(String bad : new String[] { null, "", "  " }) {
            PairingException ex = assertThrows(PairingException.class,
               () -> ScriptTarget.of(k, bad), k + " " + bad);

            assertTrue(ex.getMessage().contains("kind '" + k.wireName() + "' requires 'assembly'"),
                       ex.getMessage());
         }
      }
   }

   @Test
   void assemblyOnlyKindsRefuseANameAndListTheKindsThatTakeOne() {
      for(ScriptTarget.Kind k : new ScriptTarget.Kind[] { VARIABLE, SQL }) {
         PairingException ex = assertThrows(PairingException.class,
            () -> ScriptTarget.of(k, "X", "some"), k.wireName());

         assertTrue(ex.getMessage().contains("kind '" + k.wireName() + "' takes no 'name'"),
                    ex.getMessage());
         assertTrue(ex.getMessage().contains("vsHyperlinkExpression"), ex.getMessage());
         assertFalse(ex.getMessage().contains("worksheetSqlQuery, ") ||
                     ex.getMessage().contains(", worksheetSqlQuery") ||
                     ex.getMessage().contains("worksheetVariableDefault, ") ||
                     ex.getMessage().contains(", worksheetVariableDefault"),
                     "an assembly-only kind is not in the NAMED list: " + ex.getMessage());
      }
   }

   @Test
   void aBlankNameOnAnAssemblyOnlyKindIsTreatedAsNoName() throws Exception {
      assertNull(ScriptTarget.of(VARIABLE, "X", "  ").name());
      assertNull(ScriptTarget.of(SQL, "X", "").name());
   }

   // ---- vsHyperlinkExpression: assembly + region name -----------------------------------------

   @Test
   void hyperlinkCarriesTheAssemblyAndEveryRegionForm() throws Exception {
      for(String name : new String[] { "link", "title", "emptyPlot", "cell[2,1]",
                                       "field[Total Sales]", "axis[a]b]", "text[x]" })
      {
         ScriptTarget t = ScriptTarget.of(HYPERLINK, "Text1", name);

         assertEquals("Text1", t.assemblyName());
         assertEquals(name, t.name());
         assertEquals("vsHyperlinkExpression:Text1:" + name, t.toString());
      }
   }

   @Test
   void hyperlinkRequiresAnAssembly() {
      for(String bad : new String[] { null, "", "  " }) {
         PairingException ex = assertThrows(PairingException.class,
            () -> ScriptTarget.of(HYPERLINK, bad, "link"), String.valueOf(bad));

         assertTrue(ex.getMessage().contains("kind 'vsHyperlinkExpression' requires 'assembly'"),
                    ex.getMessage());
      }
   }

   @Test
   void hyperlinkRequiresAWellFormedRegionAndSaysWhy() {
      for(String bad : new String[] { null, "", "  ", "Link", "cell[1]", "cell[01,2]", "field[]",
                                      "bogus", "field[" + "c".repeat(201) + "]" })
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> ScriptTarget.of(HYPERLINK, "Text1", bad), String.valueOf(bad));

         assertTrue(ex.getMessage().contains("kind 'vsHyperlinkExpression' requires a valid " +
                                             "'name'"), ex.getMessage());
      }
   }

   @Test
   void hyperlinkIsListedAmongTheKindsThatTakeAName() {
      PairingException ex = assertThrows(PairingException.class,
         () -> ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN, "Table1", "x"));

      assertTrue(ex.getMessage().contains("vsHyperlinkExpression"), ex.getMessage());
   }

   // ---- ids -----------------------------------------------------------------------------------

   @Test
   void idRoundTripsIncludingPipesAndBackslashesInTheNames() throws Exception {
      for(ScriptTarget.Kind k : new ScriptTarget.Kind[] { VARIABLE, SQL }) {
         for(String assembly : new String[] { "Var1", "a|b", "a\\b", "a\\|b", "SQL Query1" }) {
            ScriptTarget back = ScriptTarget.fromId(ScriptTarget.of(k, assembly).id());

            assertSame(k, back.kind());
            assertEquals(assembly, back.assemblyName());
            assertNull(back.name());
         }
      }

      for(String name : new String[] { "link", "cell[1,2]", "field[a|b]", "field[a\\b]",
                                       "axis[x\\|y]", "text[p]q]", "field[Total Sales]" })
      {
         ScriptTarget t = ScriptTarget.of(HYPERLINK, "Chart|1", name);
         ScriptTarget back = ScriptTarget.fromId(t.id());

         assertSame(HYPERLINK, back.kind());
         assertEquals("Chart|1", back.assemblyName());
         assertEquals(name, back.name());
      }
   }

   @Test
   void aForgedIdWithABadRegionOrANameOnAnAssemblyKindIsRefused() {
      assertThrows(PairingException.class, () -> ScriptTarget.fromId(forged(
         "vsHyperlinkExpression", "Text1", "cell[1]")));
      assertThrows(PairingException.class, () -> ScriptTarget.fromId(forged(
         "worksheetSqlQuery", "T", "extra")));
   }

   private static String forged(String kind, String assembly, String name) {
      return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
         (kind + "|" + assembly + "|" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
   }

   @Test
   void resolveAcceptsKindAssemblyNameAndTheIdDialect() throws Exception {
      ScriptTarget v = ScriptTarget.resolve(null, null, "worksheetVariableDefault", "Var1", null,
                                            null);
      assertSame(VARIABLE, v.kind());
      assertEquals("Var1", v.assemblyName());

      ScriptTarget h = ScriptTarget.resolve(null, null, "vsHyperlinkExpression", "Table1",
                                            "cell[2,1]", null);
      assertSame(HYPERLINK, h.kind());
      assertEquals("cell[2,1]", h.name());

      assertSame(SQL, ScriptTarget.resolve(null, ScriptTarget.of(SQL, "Q1").id(), null, null,
                                           null, null).kind());
   }

   @Test
   void legacyParseNeverLearnsThem() {
      for(ScriptTarget.Kind k : KINDS) {
         assertThrows(PairingException.class,
            () -> ScriptTarget.parse(k.wireName() + ":Text1:link"), k.wireName());
      }
   }
}
