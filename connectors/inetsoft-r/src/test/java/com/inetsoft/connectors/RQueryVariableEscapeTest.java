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

import java.util.ArrayList;
import java.util.List;
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

   // ---- comments and template backslashes (review round 1) -------------------

   @Test
   void script_apostropheInHashComment_payloadStaysInsideLiteral() {
      String value = ";system(intToUtf8(c(105,100)));y<-";

      assertEquals("# don't filter blanks\ndf <- subset(d, name == '" + value + "'); df",
                   script("# don't filter blanks\ndf <- subset(d, name == '$(p)'); df", value));
      assertEquals("# don't filter blanks\ndf <- subset(d, name == 'a b'); df",
                   script("# don't filter blanks\ndf <- subset(d, name == '$(p)'); df", "a b"));
   }

   @Test
   void script_doubleMinus_isNotAComment() {
      assertEquals("x <- 5 --1; y <- 'v'", script("x <- 5 --1; y <- '$(p)'", "v"));
   }

   @Test
   void script_placeholderInHashComment_isNotReplacedOrListed() {
      assertEquals("# old: $(q)\ny <- 'v'", script("# old: $(q)\ny <- '$(p)'", "v"));

      RQuery query = new RQuery();
      query.setScript("# $(q)\ny <- '$(p)'");
      query.setPreExecute("// $(r)\nvar n = '$(s)';");
      List<String> names = new ArrayList<>();
      TabularUtil.findVariables(query).forEach(v -> names.add(v.getName()));
      assertTrue(names.contains("p"), names.toString());
      assertTrue(names.contains("s"), names.toString());
      assertFalse(names.contains("q"), names.toString());
      assertFalse(names.contains("r"), names.toString());
   }

   @Test
   void script_rStringWithBackslashesAndRegex_isUnchanged() {
      String template = "x <- 'a\\nb'; y <- gsub('\\\\.', '', z)";
      assertEquals(template, script(template, "v"));
   }

   @Test
   void preAndPost_backslashes_arePreservedByteForByte() {
      String noVar = "var s = 'a\\nb'; var r = /\\d+/; var t = 'it\\'s';";
      assertEquals(noVar, pre(noVar, "v"));
      assertEquals(noVar, post(noVar, "v"));

      String path = "var s = 'C:\\\\temp\\\\' + '$(p)';";
      assertEquals("var s = 'C:\\\\temp\\\\' + '\\u0076';", pre(path, "v"));
      assertEquals("var s = 'C:\\\\temp\\\\' + '\\u0076';", post(path, "v"));
   }

   @Test
   void preAndPost_apostropheInComments_keepsValueInsideLiteral() {
      for(String comment : new String[] { "// don't do this\n", "/* don't do this */ " }) {
         String expected = comment + "var n = '\\u0061 b';";
         assertEquals(expected, pre(comment + "var n = '$(p)';", "a b"));
         assertEquals(expected, post(comment + "var n = '$(p)';", "a b"));
      }
   }

   @Test
   void preAndPost_decrementBeforePlaceholder_isReplaced() {
      assertEquals("while(i--) n = '\\u0076';", pre("while(i--) n = '$(p)';", "v"));
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
