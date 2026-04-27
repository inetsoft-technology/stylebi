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
package inetsoft.uql.viewsheet;

import org.junit.jupiter.api.Test;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.*;
import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class CellRefTest {

   // ── equals ────────────────────────────────────────────────────────────

   @Test
   void equalsSameColAndRow() {
      CellRef a = new CellRef("colA", 3);
      CellRef b = new CellRef("colA", 3);
      assertEquals(a, b);
   }

   @Test
   void equalsDifferentColIsFalse() {
      CellRef a = new CellRef("colA", 3);
      CellRef b = new CellRef("colB", 3);
      assertNotEquals(a, b);
   }

   @Test
   void equalsDifferentRowIsFalse() {
      CellRef a = new CellRef("colA", 3);
      CellRef b = new CellRef("colA", 4);
      assertNotEquals(a, b);
   }

   @Test
   void equalsNullReturnsFalse() {
      CellRef a = new CellRef("colA", 3);
      assertFalse(a.equals(null));
   }

   @Test
   void equalsWrongTypeReturnsFalse() {
      CellRef a = new CellRef("colA", 3);
      assertFalse(a.equals("colA"));
   }

   // ── hashCode ──────────────────────────────────────────────────────────

   @Test
   void hashCodeIsConsistentAcrossCalls() {
      CellRef ref = new CellRef("colA", 5);
      assertEquals(ref.hashCode(), ref.hashCode());
   }

   @Test
   void equalObjectsHaveSameHashCode() {
      CellRef a = new CellRef("myCol", 7);
      CellRef b = new CellRef("myCol", 7);
      assertEquals(a.hashCode(), b.hashCode());
   }

   // ── clone ─────────────────────────────────────────────────────────────

   @Test
   void cloneProducesIndependentCopy() {
      CellRef original = new CellRef("colX", 10);
      CellRef cloned = (CellRef) original.clone();

      assertNotNull(cloned);
      assertNotSame(original, cloned);
      assertEquals(original.getCol(), cloned.getCol());
      assertEquals(original.getRow(), cloned.getRow());
   }

   @Test
   void cloneMutationDoesNotAffectOriginal() {
      CellRef original = new CellRef("colX", 10);
      CellRef cloned = (CellRef) original.clone();

      cloned.setCol("otherCol");
      cloned.setRow(99);

      assertEquals("colX", original.getCol());
      assertEquals(10, original.getRow());
   }

   // ── XML round-trip ────────────────────────────────────────────────────

   @Test
   void xmlRoundTripPreservesColAndRow() throws Exception {
      CellRef original = new CellRef("myColumn", 42);

      // Write XML
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      original.writeXML(pw);
      pw.flush();

      // Parse the written XML
      String xml = sw.toString().trim();
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(new InputSource(new StringReader(xml)));
      Element elem = doc.getDocumentElement();

      // Restore
      CellRef restored = new CellRef();
      restored.parseXML(elem);

      assertEquals("myColumn", restored.getCol());
      assertEquals(42, restored.getRow());
   }

   @Test
   void xmlRoundTripWithNegativeRow() throws Exception {
      CellRef original = new CellRef("headerCol", -1);

      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      original.writeXML(pw);
      pw.flush();

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(new InputSource(new StringReader(sw.toString().trim())));
      Element elem = doc.getDocumentElement();

      CellRef restored = new CellRef();
      restored.parseXML(elem);

      assertEquals("headerCol", restored.getCol());
      assertEquals(-1, restored.getRow());
   }
}
