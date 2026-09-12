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
package inetsoft.uql.jdbc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XFieldTest {

   // ── Constructor overloads ──────────────────────────────────────────────

   @Test
   void singleArgConstructorSetsAliasAndName() {
      XField f = new XField("myField");
      assertEquals("myField", f.getAlias());
      assertEquals("myField", f.getName());
   }

   @Test
   void singleArgConstructorHasEmptyTableAndStringType() {
      XField f = new XField("myField");
      assertEquals("", f.getTable());
      assertEquals(XField.STRING_TYPE, f.getType());
   }

   @Test
   void twoArgConstructorSetsDifferentAliasAndName() {
      XField f = new XField("alias", "columnName");
      assertEquals("alias", f.getAlias());
      assertEquals("columnName", f.getName());
      assertEquals("", f.getTable());
      assertEquals(XField.STRING_TYPE, f.getType());
   }

   @Test
   void fiveArgConstructorSetsAllFields() {
      XField f = new XField("al", "col", "myTable", "integer", "physTable");
      assertEquals("al", f.getAlias());
      assertEquals("col", f.getName());
      assertEquals("myTable", f.getTable());
      assertEquals("integer", f.getType());
      assertEquals("physTable", f.getPhysicalTable());
   }

   // ── equals ────────────────────────────────────────────────────────────

   @Test
   void equalsSameFourFieldsIsTrue() {
      XField a = new XField("alias", "name", "table", "string");
      XField b = new XField("alias", "name", "table", "string");
      assertEquals(a, b);
   }

   @Test
   void equalsDifferentAlias() {
      XField a = new XField("alias1", "name", "table", "string");
      XField b = new XField("alias2", "name", "table", "string");
      assertNotEquals(a, b);
   }

   @Test
   void equalsDifferentTable() {
      XField a = new XField("alias", "name", "tableA", "string");
      XField b = new XField("alias", "name", "tableB", "string");
      assertNotEquals(a, b);
   }

   @Test
   void equalsDifferentType() {
      XField a = new XField("alias", "name", "table", "string");
      XField b = new XField("alias", "name", "table", "integer");
      assertNotEquals(a, b);
   }

   @Test
   void equalsNullReturnsFalse() {
      XField a = new XField("alias");
      // XField.equals casts directly — passing null causes NPE inside equals;
      // verify via the cast-safe approach: null XField cast → xf != null check
      assertFalse(a.equals(null));
   }

   // ── hashCode ──────────────────────────────────────────────────────────

   @Test
   void hashCodeIsConsistentAcrossMultipleCalls() {
      XField f = new XField("al", "name", "tbl", "string");
      int h1 = f.hashCode();
      int h2 = f.hashCode();
      assertEquals(h1, h2);
   }

   // ── clone ─────────────────────────────────────────────────────────────

   @Test
   void cloneProducesIndependentCopy() {
      XField original = new XField("al", "col", "tbl", "string");
      XField cloned = (XField) original.clone();

      assertNotNull(cloned);
      assertNotSame(original, cloned);

      assertEquals(original.getAlias(), cloned.getAlias());
      assertEquals(original.getName(), cloned.getName());
      assertEquals(original.getTable(), cloned.getTable());
      assertEquals(original.getType(), cloned.getType());
   }

   @Test
   void cloneMutationDoesNotAffectOriginal() {
      XField original = new XField("al", "col", "tbl", "string");
      XField cloned = (XField) original.clone();

      cloned.setAlias("newAlias");
      cloned.setTable("newTable");

      assertEquals("al", original.getAlias());
      assertEquals("tbl", original.getTable());
   }
}
