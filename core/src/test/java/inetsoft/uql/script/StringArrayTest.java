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

import inetsoft.test.*;
import inetsoft.util.script.graal.ScriptScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
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
      assertEquals(3, stringArray.getMember("length"));
      assertNull(stringArray.getMember("nonexistent"));
   }

   @Test
   void testGetIndexedProperty() {
      assertEquals("one", stringArray.getArrayElement(0));
      assertEquals("two", stringArray.getArrayElement(1));
      assertEquals("three", stringArray.getArrayElement(2));
      assertNull(stringArray.getArrayElement(3));
   }

   @Test
   void testGetArraySize() {
      assertEquals(3, stringArray.getArraySize());
   }

   @Test
   void testHasNamedProperty() {
      assertTrue(stringArray.hasMember("length"));
      assertFalse(stringArray.hasMember("nonexistent"));
   }

   @Test
   void testPutNamedProperty() {
      stringArray.putMember("length", 2);
      assertEquals(2, stringArray.getMember("length"));
      assertEquals(2, stringArray.getMemberKeys().length - 1); // Verify size reduction

      stringArray.putMember("length", 5);
      assertEquals(5, stringArray.getMember("length"));
      assertEquals(5, stringArray.getMemberKeys().length - 1); // Verify size increase
   }

   @Test
   void testRemoveNamedProperty() {
      // "length" cannot be removed; removeMember always returns false (no-op)
      assertFalse(stringArray.removeMember("length"));
      assertTrue(stringArray.hasMember("length"));
   }

   @Test
   void testGetAndSetParentScope() {
      assertNull(stringArray.getParentScope());

      ScriptScope mockScope = mock(ScriptScope.class);
      stringArray.setParentScope(mockScope);
      assertEquals(mockScope, stringArray.getParentScope());
   }

   @Test
   void testGetMemberKeys() {
      Object[] ids = stringArray.getMemberKeys();
      assertEquals(4, ids.length); // 3 elements + "length"
      assertEquals("length", ids[3]);
   }

   @Test
   void testUnwrap() {
      Object unwrapped = stringArray.unwrap();
      assertTrue(unwrapped instanceof String[]);
      assertArrayEquals(new String[]{ "one", "two", "three" }, (String[]) unwrapped);
   }

   @Test
   void testToString() {
      assertEquals("[one, two, three]", stringArray.toString());
   }
}
