/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import inetsoft.uql.XTableNode;
import inetsoft.util.CoreTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79640: a column whose values are different Java Number classes (e.g. MongoDB Int32 and
 * Double, Jackson IntNode and DoubleNode) must widen to a numeric type, not collapse to String.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class JsonTableNumericWideningTest {

   private static Map<String, Object> rec(Object v) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("v", v);
      return m;
   }

   private static List<Object> records(Object... values) {
      List<Object> list = new ArrayList<>();

      for(Object v : values) {
         list.add(rec(v));
      }

      return list;
   }

   private static List<Object> cells(XTableNode t, int col) {
      List<Object> out = new ArrayList<>();
      t.rewind();

      while(t.next()) {
         out.add(t.getObject(col));
      }

      return out;
   }

   private static void assertAll(Class<?> cls, List<Object> cells) {
      for(Object c : cells) {
         if(c != null) {
            assertEquals(cls, c.getClass(), cells.toString());
         }
      }
   }

   private static JsonTable table(Object... values) {
      return new JsonTable(records(values), 0);
   }

   @Test
   void intAndDoubleWidenToDouble() {
      JsonTable t = table(1, 2.5, 3, null);
      assertEquals(Double.class, t.getType(0));
      assertEquals(CoreTool.DOUBLE, t.getColumnType("v"));
      assertEquals(Arrays.asList(1.0, 2.5, 3.0, null), cells(t, 0));
      assertAll(Double.class, cells(t, 0));
   }

   @Test
   void leadingNullsThenIntThenDouble() {
      JsonTable t = table(null, null, 7, 1.5);
      assertEquals(Double.class, t.getType(0));
      assertAll(Double.class, cells(t, 0));
   }

   @Test
   void intAndLongWidenToLong() {
      JsonTable t = table(1, 5_000_000_000L, 3);
      assertEquals(Long.class, t.getType(0));
      assertEquals(Arrays.asList(1L, 5_000_000_000L, 3L), cells(t, 0));
      assertAll(Long.class, cells(t, 0));
   }

   @Test
   void longAndDoubleWidenToDouble() {
      JsonTable t = table(5L, 2.5);
      assertEquals(Double.class, t.getType(0));
      assertAll(Double.class, cells(t, 0));
   }

   @Test
   void bigDecimalAndIntWidenToDouble() {
      JsonTable t = table(new BigDecimal("1.25"), 4);
      assertEquals(Double.class, t.getType(0));
      assertEquals(Arrays.asList(1.25, 4.0), cells(t, 0));
   }

   @Test
   void numberAndStringStaysString() {
      JsonTable t = table(1, "abc");
      assertEquals(String.class, t.getType(0));
      assertEquals(CoreTool.STRING, t.getColumnType("v"));
   }

   @Test
   void numberAndBooleanStaysString() {
      JsonTable t = table(1, true);
      assertEquals(String.class, t.getType(0));
   }

   @Test
   void headersAndRowsWidens() {
      Map<String, Object> json = new HashMap<>();
      json.put("headers", Arrays.asList("a", "b"));
      json.put("rows", Arrays.asList(Arrays.asList(1, 1), Arrays.asList(2.5, "x"), Arrays.asList(3, 4)));
      HeadersAndRowsJsonTable t = new HeadersAndRowsJsonTable("headers", "rows");
      t.load(json);

      assertEquals(Double.class, t.getType(0));
      assertEquals(String.class, t.getType(1));
      assertAll(Double.class, cells(t, 0));
   }

   @Test
   void expandedWidens() {
      ExpandedJsonTable t = new ExpandedJsonTable(records(1, 2.5, 3), Integer.MAX_VALUE);
      assertEquals(Double.class, t.getType(0));
      assertAll(Double.class, cells(t, 0));
   }

   @Test
   void expandedIntAndLongWidenToLong() {
      ExpandedJsonTable t = new ExpandedJsonTable(records(1, 5_000_000_000L), Integer.MAX_VALUE);
      assertEquals(Long.class, t.getType(0));
      assertAll(Long.class, cells(t, 0));
   }

   @Test
   void expandedNumberAndStringStaysString() {
      ExpandedJsonTable t = new ExpandedJsonTable(records(1, "abc"), Integer.MAX_VALUE);
      assertEquals(String.class, t.getType(0));
   }

   @Test
   void expandedMetadataTypeWidensWithData() {
      ExpandedJsonTable t = new ExpandedJsonTable((String) null);
      t.setJsonMetadata(records(1));
      t.load(records(2.5, 3));

      assertEquals(Double.class, t.getType(0));
      assertAll(Double.class, cells(t, 0));
   }

   @Test
   void distillerWidensNumericLeaves() {
      Map<String, Object> shape = shapeOf(records(1, 2.5));
      assertEquals(CoreTool.DOUBLE, ((Map<?, ?>) ((List<?>) shape.get("")).get(0)).get("v"));
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> shapeOf(Object json) {
      Object shape = JsonShapeDistiller.distill(json).getShape();
      Map<String, Object> wrap = new HashMap<>();
      wrap.put("", shape);
      return wrap;
   }

   @Test
   void distillerIntAndLongWidenToLong() {
      Map<String, Object> shape = shapeOf(records(1, 5_000_000_000L));
      assertEquals(CoreTool.LONG, ((Map<?, ?>) ((List<?>) shape.get("")).get(0)).get("v"));
   }

   @Test
   void distillerNumberAndStringStaysString() {
      Map<String, Object> shape = shapeOf(records(1, "x"));
      assertEquals(CoreTool.STRING, ((Map<?, ?>) ((List<?>) shape.get("")).get(0)).get("v"));
   }
}
