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
package inetsoft.uql.path;

import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.io.StringReader;
import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80136: the javacc-generated lexer throws {@code TokenMgrError} (a {@link Error}, not an
 * {@link Exception}) when an input cannot be tokenized at all. {@code ConditionExpression.parse},
 * {@code XNodePath.parse} and {@code XSelection.parse} only caught {@code Exception}, so a
 * lexically malformed input escaped as an unchecked {@code Error} instead of the documented
 * {@link ParseException}.
 */
@Tag("core")
class ParserEscapeTest {
   @Test
   void conditionExpressionUnterminatedStringThrowsParseException() {
      assertThrows(ParseException.class, () -> ConditionExpression.parse("a = 'x"));
   }

   @Test
   void conditionExpressionStrayHashThrowsParseException() {
      assertThrows(ParseException.class, () -> ConditionExpression.parse("a = #"));
   }

   @Test
   void xNodePathUnterminatedStringThrowsParseException() {
      assertThrows(ParseException.class, () -> XNodePath.parse("a.'b"));
   }

   @Test
   void xSelectionInvalidConvertToTypeThrowsParseException() {
      assertThrows(ParseException.class, () -> XSelection.parse("select a to foo"));
   }

   @Test
   void xSelectionStrayTildeThrowsParseException() {
      assertThrows(ParseException.class, () -> XSelection.parse("select ~"));
   }

   // control cases: already-correct grammar-level failures must keep throwing ParseException
   @Test
   void conditionExpressionGrammarErrorStillThrowsParseException() {
      assertThrows(ParseException.class, () -> ConditionExpression.parse("a =="));
   }

   @Test
   void xNodePathGrammarErrorStillThrowsParseException() {
      assertThrows(ParseException.class, () -> XNodePath.parse("a[b = 1"));
   }

   @Test
   void xSelectionGrammarErrorStillThrowsParseException() {
      assertThrows(ParseException.class, () -> XSelection.parse("selec a"));
   }

   @Test
   void xNodePathParseXmlSwallowsMalformedConditionInstead() throws Exception {
      Element root = Tool.parseXML(new StringReader(
         "<path><pathnode><name>foo</name><condition>a = 'x</condition></pathnode></path>"))
         .getDocumentElement();

      XNodePath path = new XNodePath();
      assertDoesNotThrow(() -> path.parseXML(root));
      assertEquals(1, path.getPathNodeCount());
   }
}
