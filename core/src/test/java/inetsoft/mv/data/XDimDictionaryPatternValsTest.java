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
package inetsoft.mv.data;

import inetsoft.test.*;
import inetsoft.uql.jdbc.XBinaryCondition;
import inetsoft.uql.jdbc.XExpression;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CONTAINS/STARTSWITH/LIKE conditions are rewritten by {@link XDimDictionary#fixFilter} into an
 * IN list of dictionary indexes, in descending index order. The list is built by appending, so
 * these tests pin the order and that null entries never match.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class XDimDictionaryPatternValsTest {
   @Test
   void containsReturnsMatchesInDescendingIndexOrder() {
      XDimDictionary dict = dictionary();
      assertDescending(dict, "CONTAINS", "an", dictVal -> dictVal.contains("an"));
   }

   @Test
   void startsWithReturnsMatchesInDescendingIndexOrder() {
      XDimDictionary dict = dictionary();
      assertDescending(dict, "STARTSWITH", "b", dictVal -> dictVal.startsWith("b"));
   }

   @Test
   void likeReturnsMatchesInDescendingIndexOrder() {
      XDimDictionary dict = dictionary();
      Pattern p = Pattern.compile("a.*a");
      assertDescending(dict, "LIKE", "%a%a%", dictVal -> p.matcher(dictVal).find());
   }

   @Test
   void noMatchReturnsEmptyArray() {
      Object[] vals = fix(dictionary(), "CONTAINS", "zzz");
      assertEquals(0, vals.length);
   }

   private static XDimDictionary dictionary() {
      XDimDictionary dict = new XDimDictionary();
      dict.setDataType(String.class);
      String[] values = { "banana", "apple", null, "bandana", "cherry", "papaya", "blueberry",
                          "avocado", "banana", "mango" };

      for(String value : values) {
         dict.addValue(value);
      }

      dict.complete();
      return dict;
   }

   private static void assertDescending(XDimDictionary dict, String op, String pattern,
                                        Predicate<String> match)
   {
      List<String> expected = new ArrayList<>();

      // independently of the implementation: matching indexes from the highest down
      for(int i = dict.size() - 1; i >= 0; i--) {
         Object v = dict.getValue(i);

         if(v != null && match.test(v.toString())) {
            expected.add("" + i);
         }
      }

      assertTrue(expected.size() > 2, "test data should produce several matches");

      Object[] actual = fix(dict, op, pattern);
      assertEquals(expected, List.of(actual));
   }

   private static Object[] fix(XDimDictionary dict, String op, String pattern) {
      XBinaryCondition cond = new XBinaryCondition(
         new XExpression("col", XExpression.FIELD),
         new XExpression(pattern, XExpression.VALUE), op);
      dict.fixFilter(cond);
      assertEquals("IN", cond.getOp());
      return (Object[]) cond.getExpression2().getValue();
   }
}
