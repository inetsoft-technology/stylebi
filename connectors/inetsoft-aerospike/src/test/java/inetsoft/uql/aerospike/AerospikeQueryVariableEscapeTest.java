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
package inetsoft.uql.aerospike;

import inetsoft.uql.VariableTable;
import inetsoft.uql.tabular.TabularUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #78594: a variable spliced into the AerospikeQuery query text is escaped for the
 * dialect's string literal (unknown dialect: quote and backslash doubling) instead of being URL-encoded.
 * Runs the same substitution TabularHandler.execute and TabularTableAssembly
 * apply, on the real query bean.
 */
@Tag("core")
class AerospikeQueryVariableEscapeTest {
   private static final String QUOTED = "SELECT * FROM t WHERE name = '$(p)'";
   private static final String PREFIX = "SELECT * FROM t WHERE name = ";

   @Test
   void quoteBreakOut_staysInsideLiteral() {
      assertEquals(PREFIX + "'x''OR(1)IN(1)OR'''", replace(QUOTED, "x'OR(1)IN(1)OR'"));
   }

   @Test
   void apostrophe_isEscapedNotLeftBare() {
      assertEquals(PREFIX + "'O''Brien'", replace(QUOTED, "O'Brien"));
   }

   @Test
   void backslashQuoteBreakOut_staysInsideLiteral() {
      assertEquals(PREFIX + "'x\\\\''/**/OR/**/1=1--'", replace(QUOTED, "x\\'/**/OR/**/1=1--"));
   }

   @Test
   void spaceEqualsAmpersand_areNotPercentEncoded() {
      assertEquals(PREFIX + "'a b=c&d'", replace(QUOTED, "a b=c&d"));
   }

   @Test
   void unquotedPlaceholder_becomesLiteral() {
      assertEquals("SELECT * FROM t WHERE id =  '1/**/OR/**/(1)IN(1)'",
                   replace("SELECT * FROM t WHERE id = $(p)", "1/**/OR/**/(1)IN(1)"));
   }

   @Test
   void arrayInList_isListOfEscapedLiterals() {
      assertEquals("SELECT * FROM t WHERE name IN ( " + "'a''b' , 'c d'" + " )",
                   replace("SELECT * FROM t WHERE name IN ($(p))", new String[] { "a'b", "c d" }));
   }

   private static String replace(String template, Object value) {
      AerospikeQuery query = new AerospikeQuery();
      query.setQueryString(template);
      VariableTable vars = new VariableTable();
      vars.put("p", value);
      TabularUtil.replaceVariables(query, vars);
      return query.getQueryString();
   }
}
