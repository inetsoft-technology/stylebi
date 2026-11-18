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

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.jdbc.XBinaryCondition;
import inetsoft.uql.jdbc.XExpression;
import inetsoft.util.Tool;
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

   // Bug #79417. The source database's LIKE case sensitivity (condition attribute) overrides
   // the global setting; without the attribute the global setting applies as before.
   @Test
   void likeFamilyFollowsAttributeOverGlobalSetting() {
      for(boolean global : new boolean[] { false, true }) {
         withGlobalCaseSensitive(global, () -> {
            for(String op : new String[] { "CONTAINS", "STARTSWITH", "LIKE" }) {
               String pattern = "LIKE".equals(op) ? "%a%" : "STARTSWITH".equals(op) ? "t" : "a";
               // attribute true: exact case
               assertEquals(matches(op, pattern, true), fixValues(op, pattern, "true"), op);
               // attribute false: folded
               assertEquals(matches(op, pattern, false), fixValues(op, pattern, "false"), op);
               // absent: the global setting
               assertEquals(matches(op, pattern, global), fixValues(op, pattern, null), op);
            }
         });
      }
   }

   @Test
   void containsAttributeTrueKeepsUpperCaseOnlyValues() {
      withGlobalCaseSensitive(false, () -> {
         assertEquals(List.of("banana"), fixValues("CONTAINS", "a", "true"));
         assertEquals(List.of("MeToo AppServer", "True Action", "banana"),
                      fixValues("CONTAINS", "a", null));
      });
   }

   private static List<String> fixValues(String op, String pattern, String attr) {
      XDimDictionary dict = new XDimDictionary();
      dict.setDataType(String.class);

      for(String value : new String[] { "True Action", "MeToo AppServer", "banana", "xyz" }) {
         dict.addValue(value);
      }

      dict.complete();
      XBinaryCondition cond = new XBinaryCondition(
         new XExpression("col", XExpression.FIELD),
         new XExpression(pattern, XExpression.VALUE), op);

      if(attr != null) {
         cond.setAttribute(MVConditionListHandler.LIKE_CASE_SENSITIVE, attr);
      }

      dict.fixFilter(cond);
      List<String> result = new ArrayList<>();

      for(Object idx : (Object[]) cond.getExpression2().getValue()) {
         result.add(dict.getValue(Integer.parseInt((String) idx)).toString());
      }

      // order independent
      result.sort(null);
      return result;
   }

   // independent expectation over the 4 values
   private static List<String> matches(String op, String pattern, boolean cs) {
      List<String> result = new ArrayList<>();

      for(String value : new String[] { "True Action", "MeToo AppServer", "banana", "xyz" }) {
         String v = cs ? value : value.toLowerCase();
         String p = cs ? pattern : pattern.toLowerCase();
         boolean match = "CONTAINS".equals(op) ? v.contains(p) :
            "STARTSWITH".equals(op) ? v.startsWith(p) : v.contains(p.replace("%", ""));

         if(match) {
            result.add(value);
         }
      }

      result.sort(null);
      return result;
   }

   private static void withGlobalCaseSensitive(boolean cs, Runnable run) {
      String old = SreeEnv.getProperty("string.compare.casesensitive");
      SreeEnv.setProperty("string.compare.casesensitive", "" + cs);
      Tool.invalidateCaseSensitive();

      try {
         run.run();
      }
      finally {
         if(old == null) {
            SreeEnv.remove("string.compare.casesensitive");
         }
         else {
            SreeEnv.setProperty("string.compare.casesensitive", old);
         }

         Tool.invalidateCaseSensitive();
      }
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
