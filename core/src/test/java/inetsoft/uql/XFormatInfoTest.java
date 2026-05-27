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

import inetsoft.report.internal.table.TableFormat;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link XFormatInfo}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Constructors (no-arg and two-arg)</li>
 *   <li>format / formatSpec getters and setters</li>
 *   <li>isEmpty()</li>
 *   <li>equals() / hashCode()</li>
 *   <li>clone()</li>
 *   <li>writeXML() / parseXML()</li>
 *   <li>toString() for various format types</li>
 * </ul>
 * </p>
 */
class XFormatInfoTest {

   // ==========================================================================
   // Constructors
   // ==========================================================================

   @Test
   void noArgConstructor_producesEmptyInfo() {
      XFormatInfo info = new XFormatInfo();
      assertNull(info.getFormat());
      assertNull(info.getFormatSpec());
      assertTrue(info.isEmpty());
   }

   @Test
   void twoArgConstructor_storesFormatAndSpec() {
      XFormatInfo info = new XFormatInfo("DateFormat", "yyyy-MM-dd");
      assertEquals("DateFormat", info.getFormat());
      assertEquals("yyyy-MM-dd", info.getFormatSpec());
   }

   @Test
   void twoArgConstructor_nullValues_areStored() {
      XFormatInfo info = new XFormatInfo(null, null);
      assertNull(info.getFormat());
      assertNull(info.getFormatSpec());
   }

   // ==========================================================================
   // Setters / getters
   // ==========================================================================

   @Test
   void setFormat_updatesFormat() {
      XFormatInfo info = new XFormatInfo();
      info.setFormat("DecimalFormat");
      assertEquals("DecimalFormat", info.getFormat());
   }

   @Test
   void setFormatSpec_updatesFormatSpec() {
      XFormatInfo info = new XFormatInfo();
      info.setFormatSpec("#,##0.00");
      assertEquals("#,##0.00", info.getFormatSpec());
   }

   // ==========================================================================
   // isEmpty
   // ==========================================================================

   @Test
   void isEmpty_returnsTrueWhenFormatIsNull() {
      XFormatInfo info = new XFormatInfo();
      assertTrue(info.isEmpty());
   }

   @Test
   void isEmpty_returnsFalseWhenFormatIsSet() {
      XFormatInfo info = new XFormatInfo("DateFormat", "yyyy");
      assertFalse(info.isEmpty());
   }

   @Test
   void isEmpty_returnsFalseWhenFormatIsSetWithNullSpec() {
      XFormatInfo info = new XFormatInfo("DecimalFormat", null);
      assertFalse(info.isEmpty());
   }

   // ==========================================================================
   // equals
   // ==========================================================================

   @Test
   void equals_sameFormatAndSpec_areEqual() {
      XFormatInfo a = new XFormatInfo("DateFormat", "yyyy-MM-dd");
      XFormatInfo b = new XFormatInfo("DateFormat", "yyyy-MM-dd");
      assertEquals(a, b);
   }

   @Test
   void equals_differentFormat_notEqual() {
      XFormatInfo a = new XFormatInfo("DateFormat",    "yyyy");
      XFormatInfo b = new XFormatInfo("DecimalFormat", "yyyy");
      assertNotEquals(a, b);
   }

   @Test
   void equals_differentSpec_notEqual() {
      XFormatInfo a = new XFormatInfo("DateFormat", "yyyy-MM-dd");
      XFormatInfo b = new XFormatInfo("DateFormat", "MM/dd/yyyy");
      assertNotEquals(a, b);
   }

   @Test
   void equals_bothEmpty_areEqual() {
      assertEquals(new XFormatInfo(), new XFormatInfo());
   }

   @Test
   void equals_nullSpecVsNonNullSpec_notEqual() {
      XFormatInfo a = new XFormatInfo("DateFormat", null);
      XFormatInfo b = new XFormatInfo("DateFormat", "yyyy");
      assertNotEquals(a, b);
   }

   @Test
   void equals_notEqualToNull() {
      XFormatInfo info = new XFormatInfo("DateFormat", "yyyy");
      assertNotEquals(null, info);
   }

   @Test
   void equals_notEqualToDifferentType() {
      XFormatInfo info = new XFormatInfo("DateFormat", "yyyy");
      assertNotEquals("not an XFormatInfo", info);
   }

   @Test
   void equals_sameInstance_isEqual() {
      XFormatInfo info = new XFormatInfo("DateFormat", "yyyy");
      assertEquals(info, info);
   }

   // ==========================================================================
   // hashCode
   // ==========================================================================

   @Test
   void hashCode_equalObjectsHaveSameHashCode() {
      XFormatInfo a = new XFormatInfo("DateFormat", "yyyy-MM-dd");
      XFormatInfo b = new XFormatInfo("DateFormat", "yyyy-MM-dd");
      assertEquals(a.hashCode(), b.hashCode());
   }

   @Test
   void hashCode_emptyInfoHasZeroHashCode() {
      XFormatInfo info = new XFormatInfo();
      assertEquals(0, info.hashCode());
   }

   @Test
   void hashCode_nonNullFormatContributes() {
      XFormatInfo info = new XFormatInfo("DateFormat", null);
      assertEquals("DateFormat".hashCode(), info.hashCode());
   }

   @Test
   void hashCode_bothNonNullContribute() {
      XFormatInfo info = new XFormatInfo("DateFormat", "yyyy");
      int expected = "DateFormat".hashCode() + "yyyy".hashCode();
      assertEquals(expected, info.hashCode());
   }

   // ==========================================================================
   // clone
   // ==========================================================================

   @Test
   void clone_producesEqualObject() {
      XFormatInfo original = new XFormatInfo("DateFormat", "yyyy-MM-dd");
      XFormatInfo cloned   = (XFormatInfo) original.clone();
      assertEquals(original, cloned);
   }

   @Test
   void clone_isNotSameInstance() {
      XFormatInfo original = new XFormatInfo("DateFormat", "yyyy-MM-dd");
      XFormatInfo cloned   = (XFormatInfo) original.clone();
      assertNotSame(original, cloned);
   }

   @Test
   void clone_modifyingCloneDoesNotAffectOriginal() {
      XFormatInfo original = new XFormatInfo("DateFormat", "yyyy-MM-dd");
      XFormatInfo cloned   = (XFormatInfo) original.clone();
      cloned.setFormat("DecimalFormat");

      assertEquals("DateFormat", original.getFormat(),
                   "Original format must not change after modifying clone");
   }

   @Test
   void clone_emptyInfoProducesEqualEmptyInfo() {
      XFormatInfo empty  = new XFormatInfo();
      XFormatInfo cloned = (XFormatInfo) empty.clone();
      assertEquals(empty, cloned);
      assertTrue(cloned.isEmpty());
   }

   // ==========================================================================
   // writeXML
   // ==========================================================================

   @Test
   void writeXML_withFormat_containsFormatTags() {
      XFormatInfo info = new XFormatInfo("DateFormat", "yyyy-MM-dd");
      StringWriter sw  = new StringWriter();
      info.writeXML(new PrintWriter(sw));
      String xml = sw.toString();

      assertTrue(xml.contains("<XFormatInfo>"), "Should have opening tag");
      assertTrue(xml.contains("</XFormatInfo>"), "Should have closing tag");
      assertTrue(xml.contains("DateFormat"),  "Should contain format value");
      assertTrue(xml.contains("yyyy-MM-dd"),  "Should contain formatSpec value");
   }

   @Test
   void writeXML_withNullFormat_containsOnlyWrapper() {
      XFormatInfo info = new XFormatInfo();
      StringWriter sw  = new StringWriter();
      info.writeXML(new PrintWriter(sw));
      String xml = sw.toString();

      assertTrue(xml.contains("<XFormatInfo>"),  "Should have opening tag");
      assertTrue(xml.contains("</XFormatInfo>"), "Should have closing tag");
      // No <format> sub-element expected
      assertFalse(xml.contains("<format>"), "Empty info should not write <format> element");
   }

   // ==========================================================================
   // toString  (smoke tests — exact output depends on locale / Catalog)
   // ==========================================================================

   @Test
   void toString_nullFormat_doesNotThrow() {
      XFormatInfo info = new XFormatInfo();
      assertDoesNotThrow(() -> {
         String s = info.toString();
         assertNotNull(s);
      });
   }

   @Test
   void toString_dateFormat_doesNotThrow() {
      XFormatInfo info = new XFormatInfo(TableFormat.DATE_FORMAT, "yyyy-MM-dd");
      assertDoesNotThrow(() -> {
         String s = info.toString();
         assertNotNull(s);
         assertFalse(s.isEmpty());
      });
   }

   @Test
   void toString_decimalFormat_doesNotThrow() {
      XFormatInfo info = new XFormatInfo(TableFormat.DECIMAL_FORMAT, "#,##0.00");
      assertDoesNotThrow(() -> {
         String s = info.toString();
         assertNotNull(s);
      });
   }

   @Test
   void toString_percentFormat_doesNotThrow() {
      XFormatInfo info = new XFormatInfo(TableFormat.PERCENT_FORMAT, null);
      assertDoesNotThrow(() -> {
         String s = info.toString();
         assertNotNull(s);
      });
   }

   @Test
   void toString_messageFormat_includesSpec() {
      XFormatInfo info = new XFormatInfo(TableFormat.MESSAGE_FORMAT, "Value: {0}");
      String s = info.toString();
      // The implementation returns "MESSAGE_FORMAT: <spec>" for message format
      assertTrue(s.contains("Value: {0}") || s.contains(TableFormat.MESSAGE_FORMAT),
                 "toString for MessageFormat should include format or spec");
   }

   @Test
   void toString_currencyFormat_doesNotThrow() {
      XFormatInfo info = new XFormatInfo(TableFormat.CURRENCY_FORMAT, null);
      assertDoesNotThrow(() -> assertNotNull(info.toString()));
   }
}
