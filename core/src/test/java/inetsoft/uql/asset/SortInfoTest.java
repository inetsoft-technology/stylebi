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
package inetsoft.uql.asset;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SortInfoTest {

   private SortInfo sortInfo;

   @BeforeEach
   void setUp() {
      sortInfo = new SortInfo();
   }

   // ---- isEmpty / getSortCount ----

   @Test
   void emptyOnConstruction() {
      assertTrue(sortInfo.isEmpty());
      assertEquals(0, sortInfo.getSortCount());
   }

   // ---- addSort(SortRef) — append ----

   @Test
   void addSortAppendsRef() {
      SortRef ref = makeSort("col1", XConstants.SORT_ASC);
      sortInfo.addSort(ref);

      assertFalse(sortInfo.isEmpty());
      assertEquals(1, sortInfo.getSortCount());
      assertEquals("col1", sortInfo.getSort(0).getName());
   }

   @Test
   void addSortMultipleAppendsInOrder() {
      sortInfo.addSort(makeSort("a", XConstants.SORT_ASC));
      sortInfo.addSort(makeSort("b", XConstants.SORT_DESC));

      assertEquals(2, sortInfo.getSortCount());
      assertEquals("a", sortInfo.getSort(0).getName());
      assertEquals("b", sortInfo.getSort(1).getName());
   }

   // ---- addSort(int, SortRef) — insert at index ----

   @Test
   void addSortAtIndexInsertsAtCorrectPosition() {
      sortInfo.addSort(makeSort("first", XConstants.SORT_ASC));
      sortInfo.addSort(makeSort("third", XConstants.SORT_ASC));
      sortInfo.addSort(1, makeSort("second", XConstants.SORT_ASC));

      assertEquals(3, sortInfo.getSortCount());
      assertEquals("first",  sortInfo.getSort(0).getName());
      assertEquals("second", sortInfo.getSort(1).getName());
      assertEquals("third",  sortInfo.getSort(2).getName());
   }

   // ---- addSort duplicate (same DataRef) — reposition, not double-add ----

   @Test
   void addSortDuplicateDoesNotDoubleAdd() {
      AttributeRef attr = new AttributeRef("col1");
      SortRef ref1 = new SortRef(attr);
      ref1.setOrder(XConstants.SORT_ASC);
      sortInfo.addSort(ref1);

      // Add a second SortRef pointing to the same DataRef
      SortRef ref2 = new SortRef(attr);
      ref2.setOrder(XConstants.SORT_DESC);
      sortInfo.addSort(ref2);

      // The list uses indexOf which compares DataRef equality, so the existing
      // entry is repositioned (kept at same position) rather than a new one added.
      assertEquals(1, sortInfo.getSortCount());
   }

   // ---- removeSort(DataRef) ----

   @Test
   void removeSortByRefRemovesMatchingSort() {
      AttributeRef attr = new AttributeRef("col1");
      sortInfo.addSort(new SortRef(attr));
      sortInfo.addSort(makeSort("col2", XConstants.SORT_ASC));

      sortInfo.removeSort(attr);

      assertEquals(1, sortInfo.getSortCount());
      assertEquals("col2", sortInfo.getSort(0).getName());
   }

   @Test
   void removeSortByRefIsNoopWhenAbsent() {
      sortInfo.addSort(makeSort("col1", XConstants.SORT_ASC));
      sortInfo.removeSort(new AttributeRef("nonexistent"));

      assertEquals(1, sortInfo.getSortCount());
   }

   // ---- removeSort(int) ----

   @Test
   void removeSortByIndexRemovesCorrectElement() {
      sortInfo.addSort(makeSort("a", XConstants.SORT_ASC));
      sortInfo.addSort(makeSort("b", XConstants.SORT_ASC));
      sortInfo.addSort(makeSort("c", XConstants.SORT_ASC));

      sortInfo.removeSort(1);

      assertEquals(2, sortInfo.getSortCount());
      assertEquals("a", sortInfo.getSort(0).getName());
      assertEquals("c", sortInfo.getSort(1).getName());
   }

   // ---- getSort(DataRef) ----

   @Test
   void getSortByRefReturnsSortRef() {
      AttributeRef attr = new AttributeRef("col1");
      SortRef sort = new SortRef(attr);
      sort.setOrder(XConstants.SORT_DESC);
      sortInfo.addSort(sort);

      SortRef found = sortInfo.getSort(attr);
      assertNotNull(found);
      assertEquals(XConstants.SORT_DESC, found.getOrder());
   }

   @Test
   void getSortByRefReturnsNullWhenAbsent() {
      sortInfo.addSort(makeSort("col1", XConstants.SORT_ASC));
      assertNull(sortInfo.getSort(new AttributeRef("other")));
   }

   // ---- validate(ColumnSelection) ----

   @Test
   void validateKeepsRefsInColumnSelection() {
      AttributeRef attr = new AttributeRef("valid");
      sortInfo.addSort(new SortRef(attr));

      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(attr);

      sortInfo.validate(cs);

      assertEquals(1, sortInfo.getSortCount());
   }

   @Test
   void validateRemovesRefsNotInColumnSelection() {
      sortInfo.addSort(makeSort("gone", XConstants.SORT_ASC));

      ColumnSelection cs = new ColumnSelection();
      // "gone" not added to cs

      sortInfo.validate(cs);

      assertEquals(0, sortInfo.getSortCount());
   }

   @Test
   void validateKeepsValidAndRemovesInvalid() {
      AttributeRef validAttr = new AttributeRef("keep");
      sortInfo.addSort(new SortRef(validAttr));
      sortInfo.addSort(makeSort("remove", XConstants.SORT_ASC));

      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(validAttr);

      sortInfo.validate(cs);

      assertEquals(1, sortInfo.getSortCount());
      assertEquals("keep", sortInfo.getSort(0).getName());
   }

   // ---- getSorts() — returns array copy ----

   @Test
   void getSortsReturnsArrayCopyNotAffectingOriginal() {
      sortInfo.addSort(makeSort("col1", XConstants.SORT_ASC));
      sortInfo.addSort(makeSort("col2", XConstants.SORT_ASC));

      SortRef[] copy = sortInfo.getSorts();
      assertEquals(2, copy.length);

      // Null out the copy — original should be unaffected
      copy[0] = null;
      copy[1] = null;

      // The SortInfo internals should still have 2 sorts
      assertEquals(2, sortInfo.getSortCount());
      assertNotNull(sortInfo.getSort(0));
   }

   // ---- clone() — deep copy ----

   @Test
   void cloneProducesDifferentList() {
      sortInfo.addSort(makeSort("col1", XConstants.SORT_ASC));

      SortInfo cloned = (SortInfo) sortInfo.clone();
      assertNotNull(cloned);
      assertEquals(1, cloned.getSortCount());

      // Mutating clone should not affect original
      cloned.addSort(makeSort("col2", XConstants.SORT_ASC));
      assertEquals(1, sortInfo.getSortCount());
      assertEquals(2, cloned.getSortCount());
   }

   // ---- equals(Object) ----

   @Test
   void equalsReturnsTrueForSameSorts() {
      AttributeRef attr = new AttributeRef("col1");
      sortInfo.addSort(new SortRef(attr));

      SortInfo other = new SortInfo();
      other.addSort(new SortRef(attr));

      assertEquals(sortInfo, other);
   }

   @Test
   void equalsReturnsFalseForDifferentSorts() {
      sortInfo.addSort(makeSort("col1", XConstants.SORT_ASC));

      SortInfo other = new SortInfo();
      other.addSort(makeSort("col2", XConstants.SORT_ASC));

      assertNotEquals(sortInfo, other);
   }

   @Test
   void equalsReturnsFalseForNonSortInfo() {
      assertNotEquals(sortInfo, "not a SortInfo");
   }

   // ---- equalsContent(Object) ----

   @Test
   void equalsContentReturnsTrueWhenSortsMatch() {
      AttributeRef attr = new AttributeRef("col1");
      SortRef s1 = new SortRef(attr);
      s1.setOrder(XConstants.SORT_ASC);
      sortInfo.addSort(s1);

      SortInfo other = new SortInfo();
      SortRef s2 = new SortRef(attr);
      s2.setOrder(XConstants.SORT_ASC);
      other.addSort(s2);

      assertTrue(sortInfo.equalsContent(other));
   }

   @Test
   void equalsContentReturnsFalseWhenCountsDiffer() {
      sortInfo.addSort(makeSort("col1", XConstants.SORT_ASC));
      sortInfo.addSort(makeSort("col2", XConstants.SORT_ASC));

      SortInfo other = new SortInfo();
      other.addSort(makeSort("col1", XConstants.SORT_ASC));

      assertFalse(sortInfo.equalsContent(other));
   }

   @Test
   void equalsContentReturnsFalseWhenOrdersDiffer() {
      AttributeRef attr = new AttributeRef("col1");
      SortRef s1 = new SortRef(attr);
      s1.setOrder(XConstants.SORT_ASC);
      sortInfo.addSort(s1);

      SortInfo other = new SortInfo();
      SortRef s2 = new SortRef(attr);
      s2.setOrder(XConstants.SORT_DESC);
      other.addSort(s2);

      assertFalse(sortInfo.equalsContent(other));
   }

   // ---- helper ----

   private SortRef makeSort(String name, int order) {
      SortRef ref = new SortRef(new AttributeRef(name));
      ref.setOrder(order);
      return ref;
   }
}
