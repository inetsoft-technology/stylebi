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
package inetsoft.uql;

import inetsoft.test.*;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82491: a like, starting with or contains value that is not a number (e.g. 2%) on a
 * numeric column was turned into 0 by {@link Condition#getValue(int)}, so sql and the mv
 * filtered on LIKE 0 and a save wrote "0" in place of the pattern. Such a value is now kept
 * as is and matches no row (the negated condition matches the non-null values).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConditionNumericPatternTest {
   private static final Object[] INTS = { 0, 2, 20, 25, 21, 11, 12, 102 };
   private static final Object[] LONGS = { 0L, 2L, 20L, 25L, 21L, 11L, 12L, 102L };
   private static final Object[] DOUBLES = { 0d, 0.0d, 12d, 12.5d, 120d, 129.5d, 2d, 21d };

   @ParameterizedTest
   @CsvSource({
      "integer, 2%", "integer, _1", "integer, 12%", "long, 2%", "long, _1",
      "double, 12%", "double, 2%", "float, 1_", "short, 2%", "byte, 2%", "integer, abc"
   })
   void getValueKeepsTheNonNumericPattern(String type, String value) {
      for(int op : new int[] { XCondition.LIKE, XCondition.STARTING_WITH,
                               XCondition.CONTAINS })
      {
         Condition cond = condition(type, op, value);
         assertEquals(value, cond.getValue(0), type + " op " + op);
      }
   }

   @Test
   void getValueStillConvertsANumber() {
      assertEquals(2, condition(XSchema.INTEGER, XCondition.LIKE, "2").getValue(0));
      // as before, a number is converted with the column type
      assertEquals(2, condition(XSchema.INTEGER, XCondition.LIKE, "2.0").getValue(0));
      assertEquals(2L, condition(XSchema.LONG, XCondition.CONTAINS, "2").getValue(0));
      assertEquals(12.5d, condition(XSchema.DOUBLE, XCondition.STARTING_WITH, "12.5")
         .getValue(0));
   }

   @Test
   void otherOperatorsAndTypesUnchanged() {
      // only like, starting with and contains keep the value: equal to still falls back to 0
      assertEquals(0, condition(XSchema.INTEGER, XCondition.EQUAL_TO, "2%").getValue(0));
      assertEquals("2%", condition(XSchema.STRING, XCondition.LIKE, "2%").getValue(0));
   }

   @ParameterizedTest
   @CsvSource({ "integer, 2%", "integer, _1", "integer, 12%", "long, 2%", "double, 12%" })
   void inMemoryNonNumericPatternMatchesNoRow(String type, String value) throws Exception {
      Object[] data = data(type);

      for(int op : new int[] { XCondition.LIKE, XCondition.STARTING_WITH,
                               XCondition.CONTAINS })
      {
         AssetCondition cond = condition(type, op, value);
         assertEquals(List.of(), matches(cond, data), type + " op " + op);

         // a save keeps the pattern, so the reloaded condition matches no row either (the
         // value was saved as 0, which then matched the 0 row of an integer column)
         AssetCondition loaded = roundTrip(cond);
         assertEquals(value, loaded.getValues().get(0));
         assertEquals(value, loaded.getValue(0));
         assertEquals(List.of(), matches(loaded, data), type + " op " + op + " reloaded");
      }
   }

   @Test
   void negatedNonNumericPatternMatchesTheNonNullValues() {
      AssetCondition cond = condition(XSchema.INTEGER, XCondition.LIKE, "2%");
      cond.setNegated(true);

      assertEquals(Arrays.asList(INTS), matches(cond, INTS));
      // null never matches, as in sql
      assertFalse(cond.evaluate(null));
   }

   @Test
   void saveWritesThePattern() throws Exception {
      AssetCondition cond = condition(XSchema.DOUBLE, XCondition.LIKE, "12%");
      String xml = toXML(cond);
      AssetCondition loaded = roundTrip(cond);

      assertEquals("12%", loaded.getValue(0), xml);
   }

   @Test
   void numericPatternStillMatchesTheValue() {
      assertEquals(List.of(2), matches(condition(XSchema.INTEGER, XCondition.LIKE, "2"), INTS));
      assertEquals(List.of(2, 20, 25, 21),
                   matches(condition(XSchema.INTEGER, XCondition.STARTING_WITH, "2"), INTS));
   }

   private static AssetCondition condition(String type, int op, String value) {
      AssetCondition cond = new AssetCondition();
      List<Object> vals = new ArrayList<>();
      vals.add(value);
      cond.setValues(vals);
      cond.setOperation(op);
      cond.setType(type);
      return cond;
   }

   private static Object[] data(String type) {
      return XSchema.LONG.equals(type) ? LONGS : XSchema.DOUBLE.equals(type) ? DOUBLES : INTS;
   }

   private static List<Object> matches(Condition cond, Object[] data) {
      List<Object> rows = new ArrayList<>();

      for(Object val : data) {
         if(cond.evaluate(val)) {
            rows.add(val);
         }
      }

      return rows;
   }

   private static String toXML(AssetCondition cond) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      cond.writeXML(pw);
      pw.flush();
      return sw.toString();
   }

   private static AssetCondition roundTrip(AssetCondition cond) throws Exception {
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(toXML(cond).getBytes(StandardCharsets.UTF_8)));
      AssetCondition loaded = new AssetCondition();
      loaded.parseXML(doc.getDocumentElement());
      return loaded;
   }
}
