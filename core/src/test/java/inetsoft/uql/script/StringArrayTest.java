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
package inetsoft.uql.script;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class StringArrayTest {

   private StringArray stringArray;

   @BeforeEach
   void setUp() {
      String[] initialArray = { "one", "two", "three" };
      stringArray = new StringArray("TestArray", initialArray);
   }

   @Test
   void testGetClassName() {
      assertEquals("TestArray", stringArray.getClassName());
   }

   @Test
   void testGetNamedProperty() {
      assertEquals(3, stringArray.get("length", null));
      assertEquals(Undefined.instance, stringArray.get("nonexistent", null));
   }

   @Test
   void testGetIndexedProperty() {
      assertEquals("one", stringArray.get(0, null));
      assertEquals("two", stringArray.get(1, null));
      assertEquals("three", stringArray.get(2, null));
      assertEquals(Undefined.instance, stringArray.get(3, null));
   }

   @Test
   void testHasNamedProperty() {
      assertTrue(stringArray.has("length", null));
      assertFalse(stringArray.has("nonexistent", null));
   }

   @Test
   void testHasIndexedProperty() {
      assertTrue(stringArray.has(0, null));
      assertTrue(stringArray.has(2, null));
      assertFalse(stringArray.has(3, null));
   }

   @Test
   void testPutNamedProperty() {
      stringArray.put("length", null, 2);
      assertEquals(2, stringArray.get("length", null));
      assertEquals(2, stringArray.getIds().length - 1); // Verify size reduction

      stringArray.put("length", null, 5);
      assertEquals(5, stringArray.get("length", null));
      assertEquals(5, stringArray.getIds().length - 1); // Verify size increase
   }

   @Test
   void testPutIndexedProperty() {
      stringArray.put(1, null, "updated");
      assertEquals("updated", stringArray.get(1, null));

      stringArray.put(3, null, "new");
      assertEquals(Undefined.instance, stringArray.get(3, null)); // Out of bounds
   }

   @Test
   void testDeleteNamedProperty() {
      stringArray.delete("length");
      assertTrue(stringArray.has("length", null)); // "length" cannot be deleted
   }

   @Test
   void testDeleteIndexedProperty() {
      stringArray.delete(1);
      stringArray.delete(4); // Out of bounds
      assertEquals("three", stringArray.get(1, null));
      assertEquals(2, stringArray.get("length", null)); // Verify size reduction
   }

   @Test
   void testGetPrototypeAndParentScope() {
      assertNull(stringArray.getPrototype());
      assertNull(stringArray.getParentScope());

      Scriptable mockScriptable = mock(Scriptable.class);

      stringArray.setPrototype(mockScriptable);
      stringArray.setParentScope(mockScriptable);

      assertEquals(mockScriptable, stringArray.getPrototype());
      assertEquals(mockScriptable, stringArray.getParentScope());
   }

   @Test
   void testGetIds() {
      Object[] ids = stringArray.getIds();
      assertEquals(4, ids.length); // 3 elements + "length"
      assertEquals("length", ids[3]);
   }

   @Test
   void testGetDefaultValue() {
      assertEquals(Arrays.asList("one", "two", "three"), stringArray.getDefaultValue(null));
   }

   @Test
   void testHasInstance() {
      assertFalse(stringArray.hasInstance(null));
   }

   @Test
   void testUnwrap() {
      Object unwrapped = stringArray.unwrap();
      assertTrue(unwrapped instanceof String[]);
      assertArrayEquals(new String[]{ "one", "two", "three" }, (String[]) unwrapped);
   }
}