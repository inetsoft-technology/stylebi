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
package com.inetsoft.connectors;

import inetsoft.uql.VariableTable;
import inetsoft.uql.tabular.TabularUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78757: a variable spliced into the R script is escaped for an R string literal
 * (backslash escapes) and one spliced into the pre/post-execution JavaScript is escaped
 * with the JSON style (punctuation as unicode escapes), instead of being URL-encoded, which
 * left a quote in the value free to end the literal. Runs the same substitution
 * TabularHandler.execute and TabularTableAssembly apply, on the real query bean.
 */
@Tag("core")
class RQueryVariableEscapeTest {
   private static final String R_QUOTED = "df <- subset(d, name == '$(p)'); df";

   @Test
   void script_quoteInValue_staysInsideLiteral() {
      assertEquals("df <- subset(d, name == 'a\\'); x <- (\\''); df",
                   script(R_QUOTED, "a'); x <- ('"));
   }

   @Test
   void script_apostrophe_isEscaped() {
      assertEquals("df <- subset(d, name == 'O\\'Brien'); df", script(R_QUOTED, "O'Brien"));
   }

   @Test
   void script_doubleQuotedLiteral_isEscaped() {
      assertEquals("x <- \"a\\\"b\"", script("x <- \"$(p)\"", "a\"b"));
   }

   @Test
   void script_trailingBackslash_cannotEscapeClosingQuote() {
      assertEquals("x <- 'a\\\\'", script("x <- '$(p)'", "a\\"));
   }

   @Test
   void script_spaceEqualsAmpersand_areNotPercentEncoded() {
      assertEquals("df <- subset(d, name == 'a b=c&d'); df", script(R_QUOTED, "a b=c&d"));
   }

   @Test
   void script_backtickName_valueCannotEndName() {
      assertEquals("d$`ab`", script("d$`$(p)`", "a`b"));
   }

   @Test
   void script_unquotedPlaceholder_becomesLiteral() {
      assertEquals("x <-  'a\\'b'",script("x <- $(p)", "a'b"));
   }

   @Test
   void script_unquotedNumericString_staysRaw() {
      assertEquals("head(d,  5 )", script("head(d, $(p))", "5"));
   }

   @Test
   void script_templateBackslashes_arePreservedByteForByte() {
      String template = "x <- 'it\\'s'; y <- \"C:\\\\temp\"; z <- '$(p)'";
      assertEquals(template.replace("$(p)", "v"), script(template, "v"));
   }

   @Test
   void preAndPost_singleQuotedValue_staysInsideLiteral() {
      String expected = "var n = '\\u0061\\u0027\\u003b x \\u003d 1\\u003b \\u0027';";

      assertEquals(expected, pre("var n = '$(p)';", "a'; x = 1; '"));
      assertEquals(expected, post("var n = '$(p)';", "a'; x = 1; '"));
   }

   @Test
   void preAndPost_apostrophe_isEscaped() {
      assertEquals("var n = '\\u004f\\u0027Brien';", pre("var n = '$(p)';", "O'Brien"));
      assertEquals("var n = '\\u004f\\u0027Brien';", post("var n = '$(p)';", "O'Brien"));
   }

   @Test
   void preAndPost_doubleQuotedValue_staysInsideLiteral() {
      assertEquals("var n = \"\\u0061\\u0022b\";", pre("var n = \"$(p)\";", "a\"b"));
      assertEquals("var n = \"\\u0061\\u0022b\";", post("var n = \"$(p)\";", "a\"b"));
   }

   @Test
   void preAndPost_templateLiteral_interpolationIsNotFormed() {
      String template = "var n = `$(p)`;";

      for(String out : new String[] { pre(template, "a${X}b`"), post(template, "a${X}b`") }) {
         assertFalse(out.contains("${"), out);
         assertEquals("var n = ` '\\u0061\\u0024\\u007bX\\u007db\\u0060' `;", out);
      }
   }

   @Test
   void preAndPost_numberValue_staysANumber() {
      assertEquals("var n =  5 ;", pre("var n = $(p);", 5));
      assertEquals("var n =  5 ;", post("var n = $(p);", 5));
   }

   private static String script(String template, Object value) {
      return replace(RQuery::setScript, RQuery::getScript, template, value);
   }

   private static String pre(String template, Object value) {
      return replace(RQuery::setPreExecute, RQuery::getPreExecute, template, value);
   }

   private static String post(String template, Object value) {
      return replace(RQuery::setPostExecute, RQuery::getPostExecute, template, value);
   }

   private static String replace(BiConsumer<RQuery, String> setter, Function<RQuery, String> getter,
                                 String template, Object value)
   {
      RQuery query = new RQuery();
      setter.accept(query, template);
      VariableTable vars = new VariableTable();
      vars.put("p", value);
      TabularUtil.replaceVariables(query, vars);
      return getter.apply(query);
   }
}
