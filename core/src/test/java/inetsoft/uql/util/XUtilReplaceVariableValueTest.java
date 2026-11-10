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
package inetsoft.uql.util;

import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XUtil.replaceVariable: {@code $(name)} placeholders in a string. The date formats are covered
 * by XUtilReplaceVariableTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XUtilReplaceVariableValueTest {
   private static String replaceEncoded(String str, boolean encode, Object... nameAndValues) {
      VariableTable vars = new VariableTable();

      for(int i = 0; i < nameAndValues.length; i += 2) {
         vars.put((String) nameAndValues[i], nameAndValues[i + 1]);
      }

      return XUtil.replaceVariable(str, vars, encode);
   }

   private static String replace(String str, Object... nameAndValues) {
      return replaceEncoded(str, false, nameAndValues);
   }

   @Nested
   class Placeholders {
      @Test
      void nullString_staysNull() {
         assertNull(XUtil.replaceVariable(null, new VariableTable()));
      }

      @Test
      void value_replacesThePlaceholderInsideText() {
         assertEquals("region = West;", replace("region = $(r);", "r", "West"));
      }

      @Test
      void severalPlaceholders_areAllReplaced() {
         assertEquals("1-2-1", replace("$(a)-$(b)-$(a)", "a", 1, "b", 2));
      }

      @Test
      void numbers_useToString() {
         assertEquals("15", replace("$(n)", "n", 15));
         assertEquals("3000000000", replace("$(n)", "n", 3000000000L));
         assertEquals("1.5", replace("$(n)", "n", 1.5));
      }

      @Test
      void missingVariable_keepsThePlaceholder() {
         assertEquals("x = $(missing)", replace("x = $(missing)"));
      }

      @Test
      void missingVariable_doesNotStopLaterPlaceholders() {
         assertEquals("$(missing) and 2", replace("$(missing) and $(b)", "b", 2));
      }

      @Test
      void dollarBraceSyntax_isNotRecognized() {
         assertEquals("${a}", replace("${a}", "a", 1));
      }

      @Test
      void textWithoutPlaceholder_isUnchanged() {
         assertEquals("plain", replace("plain", "a", 1));
      }

      @Test
      void unclosedPlaceholder_isUnchanged() {
         assertEquals("$(a", replace("$(a", "a", 1));
      }

      @Test
      void emptyPlaceholder_stopsTheScan() {
         // $() is not a placeholder and ends the loop, the placeholders after it stay
         assertEquals("$() $(a)", replace("$() $(a)", "a", 1));
      }

      @Test
      void valueContainingAPlaceholder_isNotExpandedAgain() {
         assertEquals("$(b) 2", replace("$(a) $(b)", "a", "$(b)", "b", 2));
      }

      @Test
      void valueWithQuoteAndParenthesis_isInsertedAsIs() {
         assertEquals("name = 'O'Brien (x)'", replace("name = '$(n)'", "n", "O'Brien (x)"));
      }
   }

   @Nested
   class ArrayValues {
      @Test
      void elements_areJoinedWithCommas() {
         assertEquals("a,b,c", replace("$(x)", "x", new Object[] { "a", "b", "c" }));
      }

      @Test
      void singleElement_isTheElement() {
         assertEquals("a", replace("$(x)", "x", new Object[] { "a" }));
      }

      @Test
      void numberElements_useToString() {
         assertEquals("1,3000000000", replace("$(x)", "x", new Object[] { 1, 3000000000L }));
      }

      @Test
      void primitiveArray_isJoinedToo() {
         assertEquals("1,2", replace("$(x)", "x", new int[] { 1, 2 }));
      }

      @Test
      void emptyArray_keepsThePlaceholder() {
         VariableTable vars = new VariableTable() {
            @Override
            public Object get(String name) {
               return new Object[0];
            }
         };

         assertEquals("$(x)", XUtil.replaceVariable("$(x)", vars));
      }
   }

   @Nested
   class Encoding {
      @Test
      void withoutEncoding_valueIsInsertedAsIs() {
         assertEquals("a b&c", replaceEncoded("$(x)", false, "x", "a b&c"));
      }

      @Test
      void scalar_isUrlEncoded() {
         String encoded = replaceEncoded("$(x)", true, "x", "a b&c");

         assertFalse(encoded.contains(" "));
         assertFalse(encoded.contains("&"));
         assertTrue(encoded.startsWith("a"));
      }

      @Test
      void array_encodesEachElement() {
         assertEquals("a,b,c%20d", replaceEncoded("$(x)", true, "x", new Object[] { "a,b", "c d" }));
      }
   }

   @Nested
   class Dates {
      private final java.util.Date date =
         new GregorianCalendar(2024, Calendar.MARCH, 15, 10, 30, 0).getTime();

      @Test
      void dateWithoutFormat_usesToString() {
         assertEquals(date.toString(), replace("$(d)", "d", date));
      }

      @Test
      void formatForANonDateValue_isIgnored() {
         VariableTable vars = new VariableTable();
         vars.put("d", "2024-03-15");
         vars.putFormat("d", "MM/dd/yyyy");

         assertEquals("2024-03-15", XUtil.replaceVariable("$(d)", vars));
      }

      @Test
      void invalidFormat_fallsBackToToString() {
         VariableTable vars = new VariableTable();
         vars.put("d", date);
         vars.putFormat("d", "invalid pattern {");

         assertEquals(date.toString(), XUtil.replaceVariable("$(d)", vars));
      }

      @Test
      void format_dropsTheTimeOfDay() {
         VariableTable vars = new VariableTable();
         vars.put("d", date);
         vars.putFormat("d", "yyyy/MM/dd");

         assertEquals("2024/03/15", XUtil.replaceVariable("$(d)", vars));
      }
   }
}
