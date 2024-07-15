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
package inetsoft.util.xml;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VersionControlComparatorsTests {

   @Test
   public void testStringComparator() {
      ObjectOpenHashSet<String> allRows = new ObjectOpenHashSet<>();

      allRows.add("c1");
      allRows.add("a4");
      allRows.add("a1");
      allRows.add("b1");
      allRows.add("b12");

      List<String> list = allRows.stream()
         .sorted(VersionControlComparators.string)
         .collect(Collectors.toList());

      assertEquals(list.get(0), "a1");
      assertEquals(list.get(1), "a4");
      assertEquals(list.get(2), "b1");
   }

   @Test
   public void testSortStringKeyMap() {
      Map<String, Object> map = new HashMap<>();

      map.put("b", new Object());
      map.put("a3", new Object());
      map.put("a12", new Object());
      map.put("a1", new Object());

      List<Map.Entry<String, Object>> entries = VersionControlComparators.sortStringKeyMap(map);

      assertEquals(entries.get(0).getKey(), "a1");
      assertEquals(entries.get(1).getKey(), "a12");
      assertEquals(entries.get(2).getKey(), "a3");
      assertEquals(entries.get(3).getKey(), "b");
   }

   @Test
   public void testSortComparableKeyMap() {
      Map<TestComparableBean, Object> map = new HashMap<>();

      map.put(new TestComparableBean("b"), new Object());
      map.put(new TestComparableBean("a3"), new Object());
      map.put(new TestComparableBean("a12"), new Object());
      map.put(new TestComparableBean("a1"), new Object());

      List<Map.Entry<TestComparableBean, Object>> entries
         = VersionControlComparators.sortComparableKeyMap(map);

      assertEquals(entries.get(0).getKey().getLabel(), "a1");
      assertEquals(entries.get(1).getKey().getLabel(), "a12");
      assertEquals(entries.get(2).getKey().getLabel(), "a3");
      assertEquals(entries.get(3).getKey().getLabel(), "b");

   }

   private static class TestComparableBean implements Comparable<TestComparableBean> {

      public TestComparableBean(String label) {
         this.label = label;
      }

      @Override
      public int compareTo(VersionControlComparatorsTests.TestComparableBean o) {
         return this.label.compareTo(o.label);
      }

      public String getLabel() {
         return label;
      }

      public void setLabel(String label) {
         this.label = label;
      }

      private String label;
   }
}
