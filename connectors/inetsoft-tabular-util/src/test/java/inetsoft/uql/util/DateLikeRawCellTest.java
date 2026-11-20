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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class DateLikeRawCellTest {
   // Bug #81945: reads cells through the table's own XTable API (no XNodeTable) incl. nulls and a swapped list
   static String json(String... v) {
      StringBuilder b = new StringBuilder("[");
      for(int i = 0; i < v.length; i++) {
         b.append(i == 0 ? "" : ",").append("{\"col\":").append(v[i] == null ? "null" : "\"" + v[i] + "\"").append("}");
      }
      return b.append("]").toString();
   }

   // raw XTable API on the table itself (not XNodeTable)
   static List<Object> raw(XTableNode t) {
      List<Object> l = new ArrayList<>();
      while(t.next()) { l.add(t.getObject(0)); }
      return l;
   }

   static XTableNode[] tables(String... v) {
      JsonTable j = new JsonTable(json(v), 0);
      ExpandedJsonTable e = new ExpandedJsonTable("[{\"values\":" + json(v) + "}]", Integer.MAX_VALUE);
      HeadersAndRowsJsonTable h = new HeadersAndRowsJsonTable("headers", "rows");
      StringBuilder rows = new StringBuilder();
      for(int i = 0; i < v.length; i++) {
         rows.append(i == 0 ? "" : ",").append("[").append(v[i] == null ? "null" : "\"" + v[i] + "\"").append("]");
      }
      h.load("{\"headers\":[\"col\"],\"rows\":[" + rows + "]}");
      return new XTableNode[] { j, e, h };
   }

   @Test
   void stringColumnsRaw() {
      String[][] cases = {
         {"foo", "4/9/2030", "bar"}, {"4/9/2030", "foo", "bar"}, {"foo", "bar", "4/9/2030"},
         {"4/9/2030", "foo", "5/1/2030", "bar"}, {"2030-04-09T10:11:12Z", "x"}, {"x", "2030-04-09T10:11:12.123Z"},
         {"10:11:12", "x"}, {"2030-04-09T10:11:12", "1:02:03", "x"}, {"4/9/2030", null, "foo"}, {null, "foo", "4/9/2030"},
         {"4/9/2030", "5/1/2030", "2030-04-09T10:11:12", "z"}
      };
      for(String[] c : cases) {
         for(XTableNode t : tables(c)) {
            assertEquals(String.class, t.getType(0), t.getClass().getSimpleName() + Arrays.toString(c));
            assertEquals(Arrays.asList(c), raw(t), t.getClass().getSimpleName() + Arrays.toString(c));
         }
      }
   }

   @Test
   void dateColumnsRaw() {
      String[][] cases = {{"4/9/2030"}, {"4/9/2030", "5/1/2030"}, {"4/9/2030", null, "5/1/2030"}};
      for(String[] c : cases) {
         for(XTableNode t : tables(c)) {
            assertEquals(java.sql.Date.class, t.getType(0), Arrays.toString(c));
            for(Object o : raw(t)) {
               if(o != null) {
                  assertEquals(java.sql.Date.class, o.getClass());
               }
            }
         }
      }
      for(XTableNode t : tables("2030-04-09T10:11:12Z", "2030-04-09T10:11:13Z")) {
         assertEquals(java.sql.Timestamp.class, t.getType(0));
         for(Object o : raw(t)) { assertEquals(java.sql.Timestamp.class, o.getClass()); }
      }
   }

   @Test
   void manyRowsSwapped() {
      String[] v = new String[30000];
      Arrays.fill(v, "4/9/2030");
      v[29999] = "foo";
      JsonTable j = new JsonTable(json(v), 0);
      assertEquals(String.class, j.getType(0));
      int n = 0;
      while(j.next()) {
         Object o = j.getObject(0);
         assertEquals(v[n++], o);
      }
      assertEquals(30000, n);
   }
}
