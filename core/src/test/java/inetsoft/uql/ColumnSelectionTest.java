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
package inetsoft.uql;

import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColumnSelectionTest {

   private ColumnSelection selection;

   @BeforeEach
   void setUp() {
      selection = new ColumnSelection();
   }

   // ---- addAttribute / getAttributeCount / isEmpty ----

   @Test
   void emptyOnConstruction() {
      assertTrue(selection.isEmpty());
      assertEquals(0, selection.getAttributeCount());
   }

   @Test
   void addAttributeIncreasesCount() {
      selection.addAttribute(new AttributeRef("E", "A"));
      assertEquals(1, selection.getAttributeCount());
   }

   @Test
   void addAttributeExclusiveDoesNotDuplicate() {
      AttributeRef ref = new AttributeRef("E", "A");
      selection.addAttribute(ref);
      selection.addAttribute(ref);
      assertEquals(1, selection.getAttributeCount());
   }

   @Test
   void addAttributeNonExclusiveAllowsDuplicates() {
      AttributeRef ref = new AttributeRef("E", "A");
      selection.addAttribute(ref, false);
      selection.addAttribute(ref, false);
      assertEquals(2, selection.getAttributeCount());
   }

   // ---- getAttribute(String name) — simple lookup ----

   @Test
   void getAttributeByNameReturnsMatchingRef() {
      AttributeRef ref = new AttributeRef("Entity", "Column");
      selection.addAttribute(ref);
      DataRef found = selection.getAttribute(ref.getName());
      assertNotNull(found);
      assertEquals(ref, found);
   }

   @Test
   void getAttributeByNameReturnsNullWhenNotFound() {
      selection.addAttribute(new AttributeRef("E", "A"));
      assertNull(selection.getAttribute("NonExistent"));
   }

   @Test
   void getAttributeByNameNullReturnsNull() {
      selection.addAttribute(new AttributeRef("E", "A"));
      assertNull(selection.getAttribute((String) null));
   }

   @Test
   void getAttributeBySimpleNameWithNoEntity() {
      AttributeRef ref = new AttributeRef(null, "ColumnName");
      selection.addAttribute(ref);
      DataRef found = selection.getAttribute("ColumnName");
      assertNotNull(found);
      assertEquals(ref, found);
   }

   // ---- getAttribute(String name, String entity, boolean fuzz) ----

   @Test
   void getAttributeWithExactNameMatch() {
      AttributeRef ref = new AttributeRef("Sales", "Revenue");
      selection.addAttribute(ref);
      DataRef found = selection.getAttribute("Sales.Revenue", "Sales", false);
      assertNotNull(found);
   }

   @Test
   void getAttributeWithNullEntityName() {
      AttributeRef ref = new AttributeRef(null, "Column");
      selection.addAttribute(ref);
      DataRef found = selection.getAttribute("Column", null, true);
      assertNotNull(found);
   }

   @Test
   void getAttributeByAttributeNameAlone() {
      AttributeRef ref = new AttributeRef("Entity", "AttrName");
      selection.addAttribute(ref);
      // When ref is "Entity.AttrName" and we search for just "AttrName",
      // the second loop matches dr.getAttribute().equals(name)
      DataRef found = selection.getAttribute("AttrName", null, true);
      assertNotNull(found);
   }

   @Test
   void getAttributeFuzzMatchStripsEntityPrefix() {
      AttributeRef ref = new AttributeRef("Sales", "Revenue");
      selection.addAttribute(ref);
      // Fuzzy: name starts with entity, strip it to match by attribute only
      DataRef found = selection.getAttribute("Sales.Revenue", "Sales", true);
      assertNotNull(found);
   }

   // ---- getAttribute with dot-notation parsing ----

   @Test
   void getAttributeWithDotNotationEntityParsed() {
      AttributeRef ref = new AttributeRef("MyEntity", "MyAttr");
      selection.addAttribute(ref);
      DataRef found = selection.getAttribute("MyEntity.MyAttr");
      assertNotNull(found);
   }

   @Test
   void getAttributeWithMultipleDotsWalksBack() {
      AttributeRef ref = new AttributeRef("a.b", "c");
      selection.addAttribute(ref);
      DataRef found = selection.getAttribute("a.b.c");
      assertNotNull(found);
   }

   // ---- findAttribute ----

   @Test
   void findAttributeReturnsStoredRef() {
      AttributeRef ref = new AttributeRef("E", "A");
      selection.addAttribute(ref);
      DataRef found = selection.findAttribute(new AttributeRef("E", "A"));
      assertNotNull(found);
      assertEquals(ref, found);
   }

   @Test
   void findAttributeReturnsNullWhenNotFound() {
      selection.addAttribute(new AttributeRef("E", "A"));
      assertNull(selection.findAttribute(new AttributeRef("E", "X")));
   }

   @Test
   void findAttributeWithNullReturnsNull() {
      selection.addAttribute(new AttributeRef("E", "A"));
      assertNull(selection.findAttribute(null));
   }

   // ---- clone(boolean shallow) ----

   @Test
   void cloneShallowHasSameCount() {
      AttributeRef ref = new AttributeRef("E", "A");
      selection.addAttribute(ref);
      ColumnSelection shallow = selection.clone(true);
      assertEquals(1, shallow.getAttributeCount());
   }

   @Test
   void cloneShallowSharesSameRefInstances() {
      AttributeRef ref = new AttributeRef("E", "A");
      selection.addAttribute(ref);
      ColumnSelection shallow = selection.clone(true);
      assertSame(ref, shallow.getAttribute(0));
   }

   @Test
   void cloneDeepHasSameCount() {
      AttributeRef ref = new AttributeRef("E", "A");
      selection.addAttribute(ref);
      ColumnSelection deep = selection.clone(false);
      assertEquals(1, deep.getAttributeCount());
   }

   @Test
   void cloneDeepReturnsSameRefInstanceBecauseAttributeRefIsImmutable() {
      // AttributeRef.clone() returns 'this' — it is an immutable object.
      // ColumnSelection.clone(false) calls drefin.clone(), but gets back the same instance.
      AttributeRef ref = new AttributeRef("E", "A");
      selection.addAttribute(ref);
      ColumnSelection deep = selection.clone(false);
      assertSame(ref, deep.getAttribute(0));
      assertEquals(ref, deep.getAttribute(0));
   }

   @Test
   void cloneDefaultReturnsSameRefInstanceBecauseAttributeRefIsImmutable() {
      // clone() delegates to clone(false) — same immutable-ref behaviour.
      AttributeRef ref = new AttributeRef("E", "A");
      selection.addAttribute(ref);
      ColumnSelection cloned = selection.clone();
      assertSame(ref, cloned.getAttribute(0));
   }

   @Test
   void cloneDoesNotShareUnderlyingList() {
      selection.addAttribute(new AttributeRef("E", "A"));
      ColumnSelection cloned = selection.clone(true);
      cloned.addAttribute(new AttributeRef("E", "B"));

      assertEquals(1, selection.getAttributeCount());
      assertEquals(2, cloned.getAttributeCount());
   }

   // ---- containsAttribute ----

   @Test
   void containsAttributeTrueWhenPresent() {
      AttributeRef ref = new AttributeRef("E", "A");
      selection.addAttribute(ref);
      assertTrue(selection.containsAttribute(ref));
   }

   @Test
   void containsAttributeFalseWhenAbsent() {
      selection.addAttribute(new AttributeRef("E", "A"));
      assertFalse(selection.containsAttribute(new AttributeRef("E", "B")));
   }

   // ---- removeAttribute ----

   @Test
   void removeAttributeByRefDecreasesCount() {
      AttributeRef ref = new AttributeRef("E", "A");
      selection.addAttribute(ref);
      selection.removeAttribute(ref);
      assertEquals(0, selection.getAttributeCount());
   }

   @Test
   void removeAttributeByIndexDecreasesCount() {
      selection.addAttribute(new AttributeRef("E", "A"));
      selection.addAttribute(new AttributeRef("E", "B"));
      selection.removeAttribute(0);
      assertEquals(1, selection.getAttributeCount());
   }

   // ---- property store ----

   @Test
   void setAndGetProperty() {
      selection.setProperty("key", "value");
      assertEquals("value", selection.getProperty("key"));
   }

   @Test
   void setPropertyNullRemovesProperty() {
      selection.setProperty("key", "value");
      selection.setProperty("key", null);
      assertNull(selection.getProperty("key"));
   }

   // ---- equals ----

   @Test
   void equalSelectionsWithSameAttributes() {
      ColumnSelection a = new ColumnSelection();
      ColumnSelection b = new ColumnSelection();
      a.addAttribute(new AttributeRef("E", "A"));
      b.addAttribute(new AttributeRef("E", "A"));
      assertEquals(a, b);
   }

   @Test
   void notEqualWithDifferentAttributes() {
      ColumnSelection a = new ColumnSelection();
      ColumnSelection b = new ColumnSelection();
      a.addAttribute(new AttributeRef("E", "A"));
      b.addAttribute(new AttributeRef("E", "B"));
      assertNotEquals(a, b);
   }
}
