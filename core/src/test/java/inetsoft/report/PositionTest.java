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
package inetsoft.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionTest {

   // ---- Default constructor ----

   @Test
   void defaultConstructorCreatesZeroX() {
      Position pos = new Position();
      assertEquals(0f, pos.x);
   }

   @Test
   void defaultConstructorCreatesZeroY() {
      Position pos = new Position();
      assertEquals(0f, pos.y);
   }

   // ---- Copy constructor ----

   @Test
   void copyConstructorCopiesX() {
      Position original = new Position(3.5f, 7.25f);
      Position copy = new Position(original);
      assertEquals(3.5f, copy.x);
   }

   @Test
   void copyConstructorCopiesY() {
      Position original = new Position(3.5f, 7.25f);
      Position copy = new Position(original);
      assertEquals(7.25f, copy.y);
   }

   @Test
   void copyConstructorProducesIndependentObject() {
      Position original = new Position(1f, 2f);
      Position copy = new Position(original);
      copy.x = 99f;
      assertEquals(1f, original.x);
   }

   // ---- Float constructor ----

   @Test
   void floatConstructorSetsX() {
      Position pos = new Position(1.5f, 2.5f);
      assertEquals(1.5f, pos.x);
   }

   @Test
   void floatConstructorSetsY() {
      Position pos = new Position(1.5f, 2.5f);
      assertEquals(2.5f, pos.y);
   }

   @Test
   void floatConstructorWithZero() {
      Position pos = new Position(0f, 0f);
      assertEquals(0f, pos.x);
      assertEquals(0f, pos.y);
   }

   @Test
   void floatConstructorWithNegativeX() {
      Position pos = new Position(-1.0f, -2.0f);
      assertEquals(-1.0f, pos.x);
   }

   @Test
   void floatConstructorWithNegativeY() {
      Position pos = new Position(-1.0f, -2.0f);
      assertEquals(-2.0f, pos.y);
   }

   // ---- Double constructor ----

   @Test
   void doubleConstructorCastsXToFloat() {
      Position pos = new Position(1.5, 2.75);
      assertEquals((float) 1.5, pos.x);
   }

   @Test
   void doubleConstructorCastsYToFloat() {
      Position pos = new Position(1.5, 2.75);
      assertEquals((float) 2.75, pos.y);
   }

   @Test
   void doubleConstructorWithLargeValues() {
      Position pos = new Position(100.0, 200.0);
      assertEquals(100.0f, pos.x);
      assertEquals(200.0f, pos.y);
   }

   // ---- equals ----

   @Test
   void equalPositionsAreEqual() {
      Position a = new Position(1f, 2f);
      Position b = new Position(1f, 2f);
      assertEquals(a, b);
   }

   @Test
   void differentXIsNotEqual() {
      Position a = new Position(1f, 2f);
      Position b = new Position(9f, 2f);
      assertNotEquals(a, b);
   }

   @Test
   void differentYIsNotEqual() {
      Position a = new Position(1f, 2f);
      Position b = new Position(1f, 9f);
      assertNotEquals(a, b);
   }

   @Test
   void nullIsNotEqual() {
      Position pos = new Position(1f, 2f);
      assertFalse(pos.equals(null));
   }

   @Test
   void differentTypeIsNotEqual() {
      Position pos = new Position(1f, 2f);
      assertFalse(pos.equals("not a position"));
   }

   @Test
   void zeroPositionsAreEqual() {
      assertEquals(new Position(), new Position());
   }

   // ---- clone ----

   @Test
   void cloneReturnsNewInstance() {
      Position pos = new Position(3f, 4f);
      Object cloned = pos.clone();
      assertNotSame(pos, cloned);
   }

   @Test
   void clonePreservesX() {
      Position pos = new Position(3f, 4f);
      Position cloned = (Position) pos.clone();
      assertEquals(3f, cloned.x);
   }

   @Test
   void clonePreservesY() {
      Position pos = new Position(3f, 4f);
      Position cloned = (Position) pos.clone();
      assertEquals(4f, cloned.y);
   }

   @Test
   void cloneIsIndependent() {
      Position pos = new Position(3f, 4f);
      Position cloned = (Position) pos.clone();
      cloned.x = 100f;
      assertEquals(3f, pos.x);
   }

   @Test
   void cloneEqualsOriginal() {
      Position pos = new Position(5f, 10f);
      assertEquals(pos, pos.clone());
   }

   // ---- toString ----

   @Test
   void toStringContainsX() {
      Position pos = new Position(1.5f, 2.5f);
      assertTrue(pos.toString().contains("1.5"), "toString should contain x value");
   }

   @Test
   void toStringContainsY() {
      Position pos = new Position(1.5f, 2.5f);
      assertTrue(pos.toString().contains("2.5"), "toString should contain y value");
   }

   @Test
   void toStringStartsWithPosition() {
      Position pos = new Position(0f, 0f);
      assertTrue(pos.toString().startsWith("Position["));
   }
}
