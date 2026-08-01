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
package inetsoft.web.wiz.service;

import inetsoft.uql.XNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the PrimaryKey attribute type mismatch.
 *
 * JDBCHandler writes the attribute as a BOOLEAN (JDBCHandler:3179,
 * {@code node.setAttribute("PrimaryKey", isPrimary)}), and XNode.setAttribute stores the value as an
 * Object without coercing it. MetadataApiService used to read it with
 * {@code "true".equals(getAttribute("PrimaryKey"))} — a String compared to a Boolean, which is
 * ALWAYS false. Every column was therefore reported as a non-key and {@code dataset.primary_key}
 * was null for every table on every datasource, which silently disabled every downstream feature
 * gated on a known primary key.
 *
 * The boolean case below is the one that reproduces the bug; it fails against the old
 * string-comparison form.
 */
@Tag("core")
class MetadataApiServicePrimaryKeyTest {
   private static boolean isPK(Object attrValue) throws Exception {
      XNode node = new XNode("some_column");

      if(attrValue != null) {
         node.setAttribute("PrimaryKey", attrValue);
      }

      Method m = MetadataApiService.class
         .getDeclaredMethod("isPrimaryKeyColumn", XNode.class);
      m.setAccessible(true);
      return (Boolean) m.invoke(null, node);
   }

   @Test
   void booleanTrueIsAPrimaryKey() throws Exception {
      // THE BUG: this is exactly what JDBCHandler writes, and it used to evaluate to false.
      assertTrue(isPK(Boolean.TRUE));
   }

   @Test
   void booleanFalseIsNotAPrimaryKey() throws Exception {
      assertFalse(isPK(Boolean.FALSE));
   }

   @Test
   void stringTrueIsStillAccepted() throws Exception {
      // An XNode round-tripped through XML carries its attributes as text.
      assertTrue(isPK("true"));
      assertTrue(isPK("TRUE"));
   }

   @Test
   void stringFalseAndOtherValuesAreNotPrimaryKeys() throws Exception {
      assertFalse(isPK("false"));
      assertFalse(isPK("yes"));
      assertFalse(isPK(1));
   }

   @Test
   void aMissingAttributeIsNotAPrimaryKey() throws Exception {
      assertFalse(isPK(null));
   }
}
