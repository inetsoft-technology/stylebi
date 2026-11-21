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
package inetsoft.web.wiz.controller;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class WizDataModelRevisionTest {
   private static Map<String, Object> sample() {
      Map<String, Object> inner = new LinkedHashMap<>();
      inner.put("table", "ORDERS");
      inner.put("column", "ID");
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("name", "销售视图 \"v\"\n");
      m.put("joins", new ArrayList<>(List.of(inner, Map.of("a", 1))));
      m.put("priority", 3);
      return m;
   }

   @Test
   void theRevisionIsLowercaseHexSha256() {
      assertTrue(WizDataModelRevision.of(sample()).matches("[0-9a-f]{64}"));
   }

   @Test
   void keyOrderDoesNotMatterAtAnyLevel() {
      Map<String, Object> a = sample();
      Map<String, Object> b = new TreeMap<>(Comparator.reverseOrder());
      b.putAll(a);
      Map<String, Object> inner = new TreeMap<>(Comparator.reverseOrder());
      inner.put("table", "ORDERS");
      inner.put("column", "ID");
      b.put("joins", new ArrayList<>(List.of(inner, Map.of("a", 1))));

      assertEquals(WizDataModelRevision.of(a), WizDataModelRevision.of(b));
   }

   @Test
   void listOrderMatters() {
      Map<String, Object> b = sample();
      Collections.reverse((List<?>) b.get("joins"));

      assertNotEquals(WizDataModelRevision.of(sample()), WizDataModelRevision.of(b));
   }

   @Test
   void nullMembersAndTheRevisionKeyAreIgnored() {
      Map<String, Object> b = sample();
      b.put("description", null);
      b.put("revision", "anything");

      assertEquals(WizDataModelRevision.of(sample()), WizDataModelRevision.of(b));
   }

   @Test
   void anyRealChangeChangesIt() {
      String base = WizDataModelRevision.of(sample());
      Map<String, Object> text = sample();
      text.put("name", "销售视图 \"w\"\n");
      Map<String, Object> number = sample();
      number.put("priority", 4);
      Map<String, Object> added = sample();
      added.put("description", "x");

      assertNotEquals(base, WizDataModelRevision.of(text));
      assertNotEquals(base, WizDataModelRevision.of(number));
      assertNotEquals(base, WizDataModelRevision.of(added));
   }

   @Test
   void numbersAreWrittenOneWay() {
      String a = WizDataModelRevision.of(new HashMap<>(Map.of("n", 3)));

      assertEquals(a, WizDataModelRevision.of(new HashMap<>(Map.of("n", 3.0))));
      assertEquals(a, WizDataModelRevision.of(new HashMap<>(Map.of("n", 3L))));
   }

   @Test
   void aValueThatLooksLikeStructureCannotCollideWithStructure() {
      Map<String, Object> text = new HashMap<>(Map.of("a", "b\",\"c\":\"d"));
      Map<String, Object> two = new LinkedHashMap<>();
      two.put("a", "b");
      two.put("c", "d");

      assertNotEquals(WizDataModelRevision.of(text), WizDataModelRevision.of(two));
   }
}
