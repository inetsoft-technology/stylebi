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
import inetsoft.sree.SreeEnv;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.ChoiceFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;

/*
 * XFormatInfo - single-pass
 *
 * Risk-first coverage:
 *   [Risk 3] - toString() NPE on malformed/unrecoverable format spec (see Suspect 1)
 *   [Risk 2] - Format-derived constructor, writeXML/parseXML round trip (including the
 *              null-formatSpec literal-"null" mechanism), toString() branch coverage
 *              (duration, decimal percent-spec, unrecognized format), equals()/hashCode()
 *              content contract, clone() independence, isEmpty(), format/formatSpec
 *              getters and setters
 */

/*
 * Intent vs implementation suspects
 *
 * [Suspect 1] toString() calls fmt.format(...) in every branch without null-checking fmt first.
 *             TableFormat.getFormat() silently swallows any exception during Format
 *             construction (malformed custom format spec, transient env failure, etc.) and
 *             returns null in that case instead of throwing.
 *             XFormatInfo.java:160-192 (toString), TableFormat.java:278-283 (swallowed catch)
 *             Conclusion (Bug): reachable via a user-entered unparseable custom format spec
 *             (e.g. an invalid decimal pattern) -> NPE instead of the intended graceful
 *             fallback to Catalog "none".
 *
 * [Suspect 2] writeXML()/parseXML() null-formatSpec handling looked asymmetric during analysis
 *             (writeXML always writes a formatSpec tag, even for a null spec; parseXML
 *             special-cases the literal string "null").
 *             Conclusion (do not fix): not a bug - confirmed by the round-trip tests below to
 *             be a deliberate, consistent round-trip mechanism.
 */
@Tag("core")
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
      // TableFormat.getFormat() reads SreeEnv.getProperty("format.number.round") for this
      // branch; mock it so this stays [unit]-tier instead of depending on a real Spring
      // ApplicationContext being available (see Suspect 1: fmt-null-NPE below).
      try(MockedStatic<SreeEnv> sreeEnv = Mockito.mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty(anyString())).thenReturn(null);

         XFormatInfo info = new XFormatInfo(TableFormat.DECIMAL_FORMAT, "#,##0.00");
         assertDoesNotThrow(() -> {
            String s = info.toString();
            assertNotNull(s);
         });
      }
   }

   @Test
   void toString_percentFormat_doesNotThrow() {
      // TableFormat.getFormat() reads SreeEnv.getProperty("format.percent.round") for this
      // branch; mock it for the same reason as toString_decimalFormat_doesNotThrow above.
      try(MockedStatic<SreeEnv> sreeEnv = Mockito.mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty(anyString())).thenReturn(null);

         XFormatInfo info = new XFormatInfo(TableFormat.PERCENT_FORMAT, null);
         assertDoesNotThrow(() -> {
            String s = info.toString();
            assertNotNull(s);
         });
      }
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

   @Test
   void toString_durationFormat_doesNotThrow() {
      XFormatInfo info = new XFormatInfo(TableFormat.DURATION_FORMAT, null);
      assertDoesNotThrow(() -> {
         String s = info.toString();
         assertNotNull(s);
      });
   }

   @Test
   void toString_durationFormatPadNon_doesNotThrow() {
      XFormatInfo info = new XFormatInfo(TableFormat.DURATION_FORMAT_PAD_NON, null);
      assertDoesNotThrow(() -> {
         String s = info.toString();
         assertNotNull(s);
      });
   }

   @Test
   void toString_decimalFormatPercentLikeSpec_usesSmallSampleValue() {
      // Covers the branch that samples fmt.format(1) instead of fmt.format(100000) for these
      // two specific percent-like decimal specs. Mocked for the same reason as
      // toString_decimalFormat_doesNotThrow above.
      try(MockedStatic<SreeEnv> sreeEnv = Mockito.mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty(anyString())).thenReturn(null);

         XFormatInfo info = new XFormatInfo(TableFormat.DECIMAL_FORMAT, "##.00%");
         assertDoesNotThrow(() -> {
            String s = info.toString();
            assertNotNull(s);
         });
      }
   }

   @Test
   void toString_unrecognizedFormat_fallsBackToNone() {
      XFormatInfo info = new XFormatInfo("SomeUnknownFormatType", null);
      assertEquals(Catalog.getCatalog().getString("none"), info.toString());
   }

   @Test
   // Suspect 1 / toString() NPEs instead of falling back to "none" when TableFormat.getFormat()
   // returns null for an unparseable custom format spec / XFormatInfo.java:160-192 /
   // Fix: null-check fmt in each branch before calling fmt.format(...), falling back to
   // Catalog "none" (matching the format == null case) when fmt is null
   @Disabled("Suspect 1: toString() throws NPE on a malformed custom decimal format spec "
      + "instead of falling back to Catalog \"none\" - XFormatInfo.java:160-192")
   void toString_malformedDecimalSpec_npesInsteadOfFallingBackToNone() {
      try(MockedStatic<SreeEnv> sreeEnv = Mockito.mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty(anyString())).thenReturn(null);

         // Unterminated quote reliably makes DecimalFormat/ExtendedDecimalFormat's constructor
         // throw IllegalArgumentException, which TableFormat.getFormat() silently swallows,
         // leaving fmt == null.
         XFormatInfo info = new XFormatInfo(TableFormat.DECIMAL_FORMAT, "'unterminated");

         assertDoesNotThrow(() -> info.toString(),
            "toString() should fall back to \"none\" instead of NPEing on a malformed spec");
      }
   }

   // ==========================================================================
   // Format-derived constructor
   // ==========================================================================

   @Test
   void formatConstructor_decimalFormat_derivesFormatAndSpec() {
      DecimalFormat fmt = new DecimalFormat("#,##0.00");
      XFormatInfo info = new XFormatInfo(fmt);

      assertEquals(TableFormat.DECIMAL_FORMAT, info.getFormat());
      assertEquals("#,##0.00", info.getFormatSpec());
   }

   @Test
   void formatConstructor_dateFormat_derivesFormatAndSpec() {
      SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
      XFormatInfo info = new XFormatInfo(fmt);

      assertEquals(TableFormat.DATE_FORMAT, info.getFormat());
      assertEquals("yyyy-MM-dd", info.getFormatSpec());
   }

   @Test
   void formatConstructor_unrecognizedFormatType_leavesFormatNull() {
      // ChoiceFormat is neither SimpleDateFormat, DecimalFormat, nor MessageFormat, so
      // TableFormat.setFormat() does not recognize it and format/formatSpec stay null.
      ChoiceFormat fmt = new ChoiceFormat(new double[]{0, 1}, new String[]{"a", "b"});
      XFormatInfo info = new XFormatInfo(fmt);

      assertNull(info.getFormat());
      assertNull(info.getFormatSpec());
   }

   // ==========================================================================
   // writeXML / parseXML round trip
   // ==========================================================================

   @Test
   void roundTrip_preservesFormatAndSpec() throws Exception {
      XFormatInfo original = new XFormatInfo("DateFormat", "yyyy-MM-dd");

      XFormatInfo restored = writeThenParse(original);

      assertEquals(original, restored);
   }

   @Test
   void roundTrip_nullFormatSpecWithNonNullFormat_parsesBackToNull() throws Exception {
      // writeXML() writes the literal text "null" for a null formatSpec (Java string
      // concatenation of `null`), and parseXML() specifically converts that literal string
      // back to a real null - this is a deliberate round-trip mechanism, not a defect.
      XFormatInfo original = new XFormatInfo("DecimalFormat", null);

      XFormatInfo restored = writeThenParse(original);

      assertEquals("DecimalFormat", restored.getFormat());
      assertNull(restored.getFormatSpec(),
         "Literal \"null\" written for a null formatSpec should parse back to a real null");
   }

   @Test
   void roundTrip_emptyInfo_parsesBackToNullFormat() throws Exception {
      XFormatInfo original = new XFormatInfo();

      XFormatInfo restored = writeThenParse(original);

      assertNull(restored.getFormat());
      assertNull(restored.getFormatSpec());
   }

   private XFormatInfo writeThenParse(XFormatInfo original) throws Exception {
      StringWriter sw = new StringWriter();
      original.writeXML(new PrintWriter(sw));

      Document doc = Tool.parseXML(new StringReader(sw.toString()));
      Element root = doc.getDocumentElement();

      XFormatInfo restored = new XFormatInfo();
      restored.parseXML(root);
      return restored;
   }
}
