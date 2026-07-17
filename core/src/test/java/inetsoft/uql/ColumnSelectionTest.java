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

import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Intent vs implementation suspects
 *
 * [Suspect 1] setAttribute(idx, attr) no-ops if attr already exists anywhere in the
 *             selection (contains check), not only at idx.
 *             Conclusion (do not fix): preserves exclusive uniqueness (same as addAttribute);
 *             allowing the replace would create duplicates. Not frontend-reproducible.
 */

/*
 * Cases deferred - not covered in this pass:
 *
 * [ColumnSelection] writeXML(PrintWriter) / parseXML(Element) / toString() - serialization
 *             and display-format output, not branching logic; descoped per reviewer guidance.
 */
@Tag("core")
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

   // ---- getAttribute cube-ref matching (bug1304592646321) ----

   @Test
   void getAttributeCubeRefMatchesAfterStrippingBrackets() {
      AttributeRef ref = new AttributeRef("Cube", "[Revenue]");
      ref.setRefType(DataRef.CUBE);
      selection.addAttribute(ref);

      DataRef found = selection.getAttribute("Revenue", null, true);
      assertSame(ref, found);
   }

   @Test
   void getAttributeCubeRefFallsBackToColumnRefCaption() {
      AttributeRef inner = new AttributeRef("Cube", "[RevenueRaw]");
      inner.setRefType(DataRef.CUBE);
      ColumnRef ref = new ColumnRef(inner);
      ref.setCaption("Revenue Caption");
      selection.addAttribute(ref);

      // neither the raw name nor the bracket-stripped attribute match; only the caption does
      DataRef found = selection.getAttribute("Revenue Caption", null, true);
      assertSame(ref, found);
   }

   // ---- getAttribute with dot-notation parsing ----

   @ParameterizedTest
   @MethodSource("dotNotationCases")
   void getAttributeWithDotNotationEntityParsed(AttributeRef ref, String lookupName) {
      selection.addAttribute(ref);
      assertNotNull(selection.getAttribute(lookupName));
   }

   static Stream<Arguments> dotNotationCases() {
      return Stream.of(
         // single dot: entity parsed from the substring before the last '.'
         Arguments.of(new AttributeRef("MyEntity", "MyAttr"), "MyEntity.MyAttr"),
         // entity itself contains a dot: full "entity.attribute" name is looked up
         Arguments.of(new AttributeRef("a.b", "c"), "a.b.c")
      );
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

   @ParameterizedTest
   @ValueSource(booleans = {true, false})
   void cloneHasSameAttributeCount(boolean shallow) {
      selection.addAttribute(new AttributeRef("E", "A"));
      ColumnSelection cloned = selection.clone(shallow);
      assertEquals(1, cloned.getAttributeCount());
   }

   @ParameterizedTest
   @ValueSource(booleans = {true, false})
   void cloneKeepsSameRefInstanceRegardlessOfShallowFlag(boolean shallow) {
      // AttributeRef.clone() returns 'this' — it is immutable, so shallow vs deep clone(false)
      // (which calls drefin.clone()) make no observable difference for this ref type.
      AttributeRef ref = new AttributeRef("E", "A");
      selection.addAttribute(ref);
      ColumnSelection cloned = selection.clone(shallow);
      assertSame(ref, cloned.getAttribute(0));
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

   @Test
   void equalSelectionsHaveSameHashCode() {
      ColumnSelection a = new ColumnSelection();
      ColumnSelection b = new ColumnSelection();
      a.addAttribute(new AttributeRef("E", "A"));
      b.addAttribute(new AttributeRef("E", "A"));
      assertEquals(a.hashCode(), b.hashCode());
   }

   // ---- equals(Object, boolean strict) ----

   @Test
   void equalsNonStrictDelegatesToEquals() {
      ColumnSelection a = new ColumnSelection();
      ColumnSelection b = new ColumnSelection();
      a.addAttribute(new AttributeRef("E", "A"));
      b.addAttribute(new AttributeRef("E", "A"));
      assertTrue(a.equals(b, false));
   }

   @Test
   void equalsStrictComparesEachRefStrictly() {
      ColumnSelection a = new ColumnSelection();
      ColumnSelection b = new ColumnSelection();
      a.addAttribute(new AttributeRef("E", "A"));
      b.addAttribute(new AttributeRef("E", "A"));
      assertTrue(a.equals(b, true));
   }

   @Test
   void equalsStrictFalseWhenSizesDiffer() {
      ColumnSelection a = new ColumnSelection();
      ColumnSelection b = new ColumnSelection();
      a.addAttribute(new AttributeRef("E", "A"));
      a.addAttribute(new AttributeRef("E", "B"));
      b.addAttribute(new AttributeRef("E", "A"));
      assertFalse(a.equals(b, true));
   }

   @Test
   void equalsStrictReturnsFalseInsteadOfThrowingForNonColumnSelectionArgument() {
      // equals(obj, true) casts obj to ColumnSelection without an instanceof check first;
      // the resulting ClassCastException is swallowed by the catch(Exception) and turned
      // into a plain "not equal" instead of propagating.
      ColumnSelection a = new ColumnSelection();
      a.addAttribute(new AttributeRef("E", "A"));
      assertFalse(a.equals("not a ColumnSelection", true));
   }

   // ---- constructor from existing List<DataRef> ----

   @Test
   void constructorFromListSeedsAttributes() {
      AttributeRef a = new AttributeRef("E", "A");
      AttributeRef b = new AttributeRef("E", "B");
      ColumnSelection fromList = new ColumnSelection(List.of(a, b));

      assertEquals(2, fromList.getAttributeCount());
      assertSame(a, fromList.getAttribute(0));
      assertSame(b, fromList.getAttribute(1));
   }

   // ---- removeAttributes(ColumnSelection) / clear ----

   @Nested
   class BulkRemovalTests {
      @Test
      void removeAttributesRemovesCommonEntries() {
         AttributeRef a = new AttributeRef("E", "A");
         AttributeRef b = new AttributeRef("E", "B");
         selection.addAttribute(a);
         selection.addAttribute(b);

         ColumnSelection toRemove = new ColumnSelection();
         toRemove.addAttribute(a);

         selection.removeAttributes(toRemove);

         assertEquals(1, selection.getAttributeCount());
         assertTrue(selection.containsAttribute(b));
      }

      @Test
      void clearRemovesAllAttributes() {
         selection.addAttribute(new AttributeRef("E", "A"));
         selection.addAttribute(new AttributeRef("E", "B"));
         selection.clear();
         assertTrue(selection.isEmpty());
      }
   }

   // ---- addAttribute(int index, DataRef, exclusive) / addAttribute(int index, DataRef) ----

   @Nested
   class IndexedAddAttributeTests {
      @Test
      void addAttributeAtIndexInsertsBeforeExistingElement() {
         selection.addAttribute(new AttributeRef("E", "A"));
         AttributeRef inserted = new AttributeRef("E", "B");
         selection.addAttribute(0, inserted, true);

         assertEquals(2, selection.getAttributeCount());
         assertSame(inserted, selection.getAttribute(0));
      }

      @Test
      void addAttributeAtOutOfBoundsIndexAppendsInstead() {
         selection.addAttribute(new AttributeRef("E", "A"));
         AttributeRef appended = new AttributeRef("E", "B");
         selection.addAttribute(99, appended, true);

         assertEquals(2, selection.getAttributeCount());
         assertSame(appended, selection.getAttribute(1));
      }

      @Test
      void addAttributeAtIndexExclusiveSkipsDuplicate() {
         AttributeRef ref = new AttributeRef("E", "A");
         selection.addAttribute(ref);
         selection.addAttribute(0, ref, true);
         assertEquals(1, selection.getAttributeCount());
      }

      @Test
      void addAttributeAtIndexNonExclusiveAllowsDuplicate() {
         AttributeRef ref = new AttributeRef("E", "A");
         selection.addAttribute(ref);
         selection.addAttribute(0, ref, false);
         assertEquals(2, selection.getAttributeCount());
      }

      @Test
      void addAttributeAtIndexWithoutExclusiveFlagIsAlwaysExclusive() {
         // via: addAttribute(int, DataRef) -> addAttribute(int, DataRef, exclusive)
         AttributeRef ref = new AttributeRef("E", "A");
         selection.addAttribute(ref);
         selection.addAttribute(0, ref);
         assertEquals(1, selection.getAttributeCount());
      }
   }

   // ---- indexOfAttribute ----

   @Nested
   class IndexOfAttributeTests {
      @Test
      void indexOfAttributeReturnsPosition() {
         selection.addAttribute(new AttributeRef("E", "A"));
         AttributeRef target = new AttributeRef("E", "B");
         selection.addAttribute(target);
         assertEquals(1, selection.indexOfAttribute(target));
      }

      @Test
      void indexOfAttributeReturnsNegativeOneWhenAbsent() {
         selection.addAttribute(new AttributeRef("E", "A"));
         assertEquals(-1, selection.indexOfAttribute(new AttributeRef("E", "X")));
      }
   }

   // ---- getAttributes() [Enumeration snapshot] ----

   @Nested
   class GetAttributesEnumerationTests {
      @Test
      void getAttributesEnumeratesAllStoredRefs() {
         selection.addAttribute(new AttributeRef("E", "A"));
         selection.addAttribute(new AttributeRef("E", "B"));

         Enumeration<DataRef> e = selection.getAttributes();
         int count = 0;

         while(e.hasMoreElements()) {
            e.nextElement();
            count++;
         }

         assertEquals(2, count);
      }

      @Test
      void getAttributesIsSnapshotUnaffectedByLaterAdd() {
         selection.addAttribute(new AttributeRef("E", "A"));
         Enumeration<DataRef> e = selection.getAttributes();
         selection.addAttribute(new AttributeRef("E", "B"));

         int count = 0;

         while(e.hasMoreElements()) {
            e.nextElement();
            count++;
         }

         assertEquals(1, count);
      }
   }

   // ---- setAttribute(int, DataRef) ----

   @Nested
   class SetAttributeTests {
      @Test
      void setAttributeReplacesRefAtIndex() {
         selection.addAttribute(new AttributeRef("E", "A"));
         AttributeRef replacement = new AttributeRef("E", "B");
         selection.setAttribute(0, replacement);
         assertSame(replacement, selection.getAttribute(0));
      }
   }

   // ---- sortBy ----

   @Nested
   class SortByTests {
      @Test
      void sortByOrdersAttributesUsingComparator() {
         AttributeRef b = new AttributeRef("E", "B");
         AttributeRef a = new AttributeRef("E", "A");
         selection.addAttribute(b);
         selection.addAttribute(a);

         selection.sortBy(Comparator.comparing(DataRef::getAttribute));

         assertSame(a, selection.getAttribute(0));
         assertSame(b, selection.getAttribute(1));
      }
   }

   // ---- getHiddenColumnCount ----

   @Nested
   class GetHiddenColumnCountTests {
      @Test
      void countsOnlyInvisibleColumnRefs() {
         ColumnRef visible = new ColumnRef(new AttributeRef("E", "A"));
         ColumnRef hidden = new ColumnRef(new AttributeRef("E", "B"));
         hidden.setVisible(false);

         selection.addAttribute(visible);
         selection.addAttribute(hidden);
         selection.addAttribute(new AttributeRef("E", "C")); // not a ColumnRef, ignored

         assertEquals(1, selection.getHiddenColumnCount());
      }

      @Test
      void zeroWhenNoColumnRefsAreHidden() {
         selection.addAttribute(new ColumnRef(new AttributeRef("E", "A")));
         assertEquals(0, selection.getHiddenColumnCount());
      }
   }
}
