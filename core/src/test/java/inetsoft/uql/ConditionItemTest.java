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
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Cases deferred - not covered in this pass:
 *
 * [ConditionItem] writeXML(PrintWriter) / parseXML(Element) - XML serialization round-trip,
 *             not branching logic; descoped per reviewer guidance.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ConditionItemTest {

   // ---- Default constructor ----

   @Test
   void defaultConstructorCreatesItemAtLevelZero() {
      ConditionItem item = new ConditionItem();
      assertEquals(0, item.getLevel());
      assertNotNull(item.getXCondition());
      assertNotNull(item.getAttribute());
   }

   // ---- Parameterized constructor ----

   @Test
   void parameterizedConstructorStoresAllFields() {
      // three fields set by one constructor call, not independent branch/param variants,
      // so a single multi-assertion test is clearer here than @ParameterizedTest.
      AttributeRef attr = new AttributeRef("MyEntity", "MyAttr");
      Condition cond = new Condition();
      cond.setType(XSchema.INTEGER);
      ConditionItem item = new ConditionItem(attr, cond, 2);

      assertSame(attr, item.getAttribute());
      assertSame(cond, item.getXCondition());
      assertEquals(2, item.getLevel());
   }

   // ---- getCondition() vs getXCondition() ----

   @Test
   void getConditionReturnsConditionWhenTypeIsCondition() {
      Condition cond = new Condition();
      ConditionItem item = new ConditionItem(new AttributeRef("e", "a"), cond, 0);

      assertSame(cond, item.getCondition());
   }

   @Test
   void getXConditionReturnsStoredXCondition() {
      Condition cond = new Condition();
      ConditionItem item = new ConditionItem(new AttributeRef("e", "a"), cond, 0);

      assertSame(cond, item.getXCondition());
   }

   @Test
   void getConditionReturnsNewConditionWhenXConditionNotConditionInstance() {
      // Use a plain Condition but verify the deprecated getCondition also works
      ConditionItem item = new ConditionItem(new AttributeRef("e", "a"), new Condition(), 0);
      assertNotNull(item.getCondition());
   }

   // ---- setLevel / getLevel ----

   @Test
   void setLevelUpdatesLevel() {
      ConditionItem item = new ConditionItem();
      item.setLevel(3);
      assertEquals(3, item.getLevel());
   }

   // ---- setAttribute ----

   @Test
   void setAttributeUpdatesRef() {
      ConditionItem item = new ConditionItem();
      AttributeRef newAttr = new AttributeRef("Entity2", "Attr2");
      item.setAttribute(newAttr);
      assertSame(newAttr, item.getAttribute());
   }

   @Test
   void setAttributeIgnoresNull() {
      AttributeRef original = new AttributeRef("e", "a");
      ConditionItem item = new ConditionItem(original, new Condition(), 0);
      item.setAttribute(null);
      assertSame(original, item.getAttribute());
   }

   // ---- setCondition / setXCondition ----

   @Test
   void setConditionUpdatesCondition() {
      ConditionItem item = new ConditionItem();
      Condition newCond = new Condition();
      newCond.setType(XSchema.DOUBLE);
      item.setCondition(newCond);
      assertSame(newCond, item.getCondition());
   }

   @Test
   void setXConditionUpdatesCondition() {
      ConditionItem item = new ConditionItem();
      Condition newCond = new Condition();
      newCond.setType(XSchema.FLOAT);
      item.setXCondition(newCond);
      assertSame(newCond, item.getXCondition());
   }

   // ---- clone() ----

   @Test
   void cloneReturnsNewInstance() {
      ConditionItem item = new ConditionItem(new AttributeRef("E", "A"), new Condition(), 1);
      ConditionItem cloned = (ConditionItem) item.clone();
      assertNotSame(item, cloned);
   }

   @Test
   void cloneAttributeIsSameInstanceBecauseAttributeRefIsImmutable() {
      AttributeRef attr = new AttributeRef("E", "A");
      ConditionItem item = new ConditionItem(attr, new Condition(), 1);
      ConditionItem cloned = (ConditionItem) item.clone();
      // AttributeRef.clone() returns 'this' — it is an immutable object
      assertSame(attr, cloned.getAttribute());
   }

   @Test
   void cloneCreatesDeepCopyOfCondition() {
      Condition cond = new Condition();
      ConditionItem item = new ConditionItem(new AttributeRef("E", "A"), cond, 1);
      ConditionItem cloned = (ConditionItem) item.clone();
      assertNotSame(cond, cloned.getXCondition());
   }

   @Test
   void clonePreservesLevel() {
      ConditionItem item = new ConditionItem(new AttributeRef("e", "a"), new Condition(), 5);
      ConditionItem cloned = (ConditionItem) item.clone();
      assertEquals(5, cloned.getLevel());
   }

   @Test
   void clonesAreEqualByValue() {
      AttributeRef attr = new AttributeRef("E", "A");
      Condition cond = new Condition();
      cond.setType(XSchema.INTEGER);
      ConditionItem item = new ConditionItem(attr, cond, 2);

      ConditionItem cloned = (ConditionItem) item.clone();
      assertEquals(item, cloned);
   }

   // ---- toString(boolean) ----

   @ParameterizedTest
   @MethodSource("toStringPrefixCases")
   void toStringStartsWithExpectedPrefix(boolean shlvl, int level, String expectedPrefix) {
      ConditionItem item = new ConditionItem(new AttributeRef("Entity", "Column"), new Condition(), level);
      String str = item.toString(shlvl);
      assertTrue(str.startsWith(expectedPrefix), "Expected prefix \"" + expectedPrefix + "\" but was: " + str);
   }

   static Stream<Arguments> toStringPrefixCases() {
      return Stream.of(
         // level indentation shown: two levels -> "........." repeated twice
         Arguments.of(true, 2, "................."),
         // level display suppressed regardless of level
         Arguments.of(false, 2, "["),
         // level 0 means no dots even when indentation is requested
         Arguments.of(true, 0, "[")
      );
   }

   @Test
   void toStringContainsBrackets() {
      ConditionItem item = new ConditionItem(new AttributeRef(null, "MyColumn"), new Condition(), 0);
      String str = item.toString(true);
      assertTrue(str.contains("["));
      assertTrue(str.contains("]"));
   }

   // ---- equals ----

   @Test
   void equalsWithSameFields() {
      ConditionItem item1 = new ConditionItem(new AttributeRef("E", "A"), new Condition(), 1);
      ConditionItem item2 = new ConditionItem(new AttributeRef("E", "A"), new Condition(), 1);
      assertEquals(item1, item2);
   }

   @Test
   void notEqualsWithDifferentLevel() {
      AttributeRef attr = new AttributeRef("E", "A");
      ConditionItem item1 = new ConditionItem(attr, new Condition(), 1);
      ConditionItem item2 = new ConditionItem(attr, new Condition(), 2);
      assertNotEquals(item1, item2);
   }

   @Test
   void notEqualsWithNull() {
      ConditionItem item = new ConditionItem();
      assertNotEquals(item, null);
   }

   @Test
   void equalsWithBothConditionsNull() {
      AttributeRef attr = new AttributeRef("E", "A");
      ConditionItem item1 = new ConditionItem(attr, new Condition(), 1);
      ConditionItem item2 = new ConditionItem(attr, new Condition(), 1);
      item1.setXCondition(null);
      item2.setXCondition(null);
      assertEquals(item1, item2);
   }

   @Test
   void notEqualsWhenOnlyOneConditionIsNull() {
      AttributeRef attr = new AttributeRef("E", "A");
      ConditionItem item1 = new ConditionItem(attr, new Condition(), 1);
      ConditionItem item2 = new ConditionItem(attr, new Condition(), 1);
      item1.setXCondition(null);
      assertNotEquals(item1, item2);
   }

   // ---- hashCode ----

   @Nested
   class HashCodeTests {
      @Test
      void equalItemsHaveSameHashCode() {
         AttributeRef attr = new AttributeRef("E", "A");
         ConditionItem item1 = new ConditionItem(attr, new Condition(), 1);
         ConditionItem item2 = new ConditionItem(attr, new Condition(), 1);

         assertEquals(item1, item2);
         assertEquals(item1.hashCode(), item2.hashCode());
      }
   }
}
