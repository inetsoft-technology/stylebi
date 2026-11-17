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
package inetsoft.web.wiz.pairing;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The hyperlink region grammar of the {@code vsHyperlinkExpression} kind (Bug #80459). */
@Tag("core")
class HyperlinkRegionNameTest {
   @Test
   void everyFormParsesAndKeepsItsComponents() {
      HyperlinkRegionName link = HyperlinkRegionName.parse("link");
      assertEquals("link", link.form());
      assertNull(link.column());

      assertEquals("title", HyperlinkRegionName.parse("title").form());
      assertEquals("emptyPlot", HyperlinkRegionName.parse("emptyPlot").form());

      HyperlinkRegionName cell = HyperlinkRegionName.parse("cell[2,1]");
      assertEquals("cell", cell.form());
      assertEquals(2, cell.row());
      assertEquals(1, cell.col());

      HyperlinkRegionName field = HyperlinkRegionName.parse("field[Sales]");
      assertEquals("field", field.form());
      assertEquals("Sales", field.column());

      assertEquals("axis", HyperlinkRegionName.parse("axis[Region]").form());
      assertEquals("text", HyperlinkRegionName.parse("text[Sales]").form());
   }

   @Test
   void wireIsTheExactInverseOfParse() {
      for(String name : new String[] { "link", "title", "emptyPlot", "cell[0,0]", "cell[12,34]",
                                       "field[Total Sales]", "axis[a]b]", "text[x[y]z]" })
      {
         assertEquals(name, HyperlinkRegionName.parse(name).wire(), name);
      }
   }

   @Test
   void aColumnMayHoldSpacesAndBracketsAndIsEverythingBetweenTheFirstAndLastBracket() {
      assertEquals("Total Sales", HyperlinkRegionName.parse("field[Total Sales]").column());
      assertEquals("a]b", HyperlinkRegionName.parse("field[a]b]").column());
      assertEquals("a[b", HyperlinkRegionName.parse("field[a[b]").column());
      assertEquals("  padded  ", HyperlinkRegionName.parse("field[  padded  ]").column());
      assertEquals("Sum(Total)", HyperlinkRegionName.parse("axis[Sum(Total)]").column());
      assertEquals("x,y|z\\w", HyperlinkRegionName.parse("text[x,y|z\\w]").column());
   }

   @Test
   void cellIndexesAreStrictBaseTenWithoutLeadingZeros() {
      assertNotNull(HyperlinkRegionName.parse("cell[0,0]"));
      assertNotNull(HyperlinkRegionName.parse("cell[999999,999999]"));

      for(String bad : new String[] { "cell[01,0]", "cell[0,00]", "cell[1000000,0]",
                                      "cell[0,1000000]", "cell[-1,0]", "cell[a,b]", "cell[1]",
                                      "cell[1,2,3]", "cell[ 1,2]", "cell[1, 2]", "cell[]",
                                      "cell[1,2", "cell1,2]" })
      {
         assertNotNull(HyperlinkRegionName.problem(bad), bad);
         assertNull(HyperlinkRegionName.parse(bad), bad);
      }
   }

   @Test
   void aBlankOrMissingNameIsRefused() {
      for(String bad : new String[] { null, "", "   " }) {
         assertTrue(HyperlinkRegionName.problem(bad).contains("name is required"), bad);
      }
   }

   @Test
   void anEmptyOrBlankColumnIsRefused() {
      for(String bad : new String[] { "field[]", "axis[   ]", "text[]" }) {
         assertTrue(HyperlinkRegionName.problem(bad).contains("needs a column name"), bad);
      }
   }

   @Test
   void aColumnOverTwoHundredCharactersIsRefusedAndTwoHundredIsAccepted() {
      assertNull(HyperlinkRegionName.problem("field[" + "c".repeat(200) + "]"));

      String problem = HyperlinkRegionName.problem("field[" + "c".repeat(201) + "]");
      assertNotNull(problem);
      assertTrue(problem.contains("200"), problem);
   }

   @Test
   void aNameOverTheOverallLengthIsRefused() {
      String problem = HyperlinkRegionName.problem("field[" + "c".repeat(300) + "]");

      assertNotNull(problem);
      assertTrue(problem.contains("260"), problem);
   }

   @Test
   void controlCharactersInTheColumnAreRefused() {
      for(char c : new char[] { '\n', '\r', '\t', 0, 0x1f, 0x7f }) {
         assertEquals("the column name must not contain control characters",
                      HyperlinkRegionName.problem("field[a" + c + "b]"), "char " + (int) c);
      }
   }

   @Test
   void theFormWordIsCaseSensitiveSoThereIsOneSpellingPerTarget() {
      for(String bad : new String[] { "Link", "LINK", "Title", "emptyplot", "EmptyPlot",
                                      "Cell[1,2]", "Field[x]", "AXIS[x]", "Text[x]" })
      {
         assertNotNull(HyperlinkRegionName.problem(bad), bad);
         assertNull(HyperlinkRegionName.parse(bad), bad);
      }
   }

   @Test
   void unknownFormsAndMalformedShapesAreRefusedWithTheGrammarInTheMessage() {
      for(String bad : new String[] { "bogus", "bogus[x]", "link[x]", "title[x]", "field", "field[",
                                      "field]x[", "field[x]y", "cell", "legend[x]", "link ",
                                      " link", "[x]" })
      {
         String problem = HyperlinkRegionName.problem(bad);

         assertNotNull(problem, bad);
         assertNull(HyperlinkRegionName.parse(bad), bad);
      }

      assertTrue(HyperlinkRegionName.problem("bogus").contains("emptyPlot"));
      assertTrue(HyperlinkRegionName.problem("bogus[x]").contains("unknown hyperlink region"));
   }

   @Test
   void parseAgreesWithProblemOnEveryInput() {
      for(String name : new String[] { "link", "cell[1,2]", "field[a]", "bad", "cell[1]", "", null,
                                       "field[]" })
      {
         assertEquals(HyperlinkRegionName.problem(name) == null,
                      HyperlinkRegionName.parse(name) != null, String.valueOf(name));
      }
   }

   @Test
   void typesOfFollowsTheGetHyperlinkDescription() {
      assertEquals(Set.of("text", "image", "gauge"), HyperlinkRegionName.typesOf("link"));
      assertEquals(Set.of("chart"), HyperlinkRegionName.typesOf("title"));
      assertEquals(Set.of("chart"), HyperlinkRegionName.typesOf("emptyPlot"));
      assertEquals(Set.of("chart"), HyperlinkRegionName.typesOf("field"));
      assertEquals(Set.of("chart"), HyperlinkRegionName.typesOf("axis"));
      assertEquals(Set.of("chart"), HyperlinkRegionName.typesOf("text"));
      assertEquals(Set.of("table", "crosstab", "calctable"), HyperlinkRegionName.typesOf("cell"));
      assertEquals(Set.of(), HyperlinkRegionName.typesOf("bogus"));
      assertEquals(Set.of(), HyperlinkRegionName.typesOf(null));
   }

   @Test
   void typesOfNameIsEmptyForAMalformedName() {
      assertEquals(Set.of("table", "crosstab", "calctable"),
                   HyperlinkRegionName.typesOfName("cell[1,2]"));
      assertEquals(Set.of(), HyperlinkRegionName.typesOfName("cell[1]"));
      assertEquals(Set.of(), HyperlinkRegionName.typesOfName(null));
   }
}
