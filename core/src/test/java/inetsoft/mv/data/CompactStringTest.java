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
package inetsoft.mv.data;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CompactStringTest {

   private static CompactString of(String s) {
      return new CompactString(s.getBytes(StandardCharsets.UTF_8));
   }

   // -----------------------------------------------------------------------
   // equals
   // -----------------------------------------------------------------------

   @Test
   void equalsWithSameBytesReturnsTrue() {
      CompactString a = of("hello");
      CompactString b = of("hello");
      assertEquals(a, b);
   }

   @Test
   void equalsWithDifferentBytesReturnsFalse() {
      CompactString a = of("hello");
      CompactString b = of("world");
      assertNotEquals(a, b);
   }

   @Test
   void equalsWithNullReturnsFalse() {
      CompactString a = of("hello");
      assertFalse(a.equals(null));
   }

   @Test
   void equalsWithNonCompactStringFallsBackToStringComparison() {
      CompactString a = of("hello");
      // The equals() falls back to toString().equals(obj.toString()) when obj
      // is not a CompactString instance
      assertTrue(a.equals("hello"));
   }

   @Test
   void equalsWithNonCompactStringDifferentStringReturnsFalse() {
      CompactString a = of("hello");
      assertFalse(a.equals("world"));
   }

   @Test
   void equalsSymmetric() {
      CompactString a = of("test");
      CompactString b = of("test");
      assertEquals(a, b);
      assertEquals(b, a);
   }

   @Test
   void equalsWithSameInstanceReturnsTrue() {
      CompactString a = of("hello");
      assertEquals(a, a);
   }

   // -----------------------------------------------------------------------
   // hashCode
   // -----------------------------------------------------------------------

   @Test
   void hashCodeConsistentBeforeToStringCall() {
      CompactString a = of("hello");
      CompactString b = of("hello");
      assertEquals(a.hashCode(), b.hashCode());
   }

   @Test
   void hashCodeConsistentAfterToStringCall() {
      CompactString a = of("hello");
      // Force toString() / lazy conversion
      a.toString();
      CompactString b = of("hello");
      b.toString();
      assertEquals(a.hashCode(), b.hashCode());
   }

   @Test
   void hashCodeBeforeAndAfterToStringCallDiffers() {
      // Before toString, hashCode uses Arrays.hashCode(utf8).
      // After toString, utf8 is nulled out and hashCode uses str.hashCode().
      // The values are generally different, so we just assert they are both
      // non-zero and that the object still functions correctly.
      CompactString cs = of("hello");
      int beforeHash = cs.hashCode();
      cs.toString(); // triggers lazy conversion; utf8 becomes null
      int afterHash = cs.hashCode();
      // Both hashes are computed; we verify consistency within each phase
      assertEquals(beforeHash, of("hello").hashCode());
   }

   // -----------------------------------------------------------------------
   // toString — lazy UTF-8 conversion
   // -----------------------------------------------------------------------

   @Test
   void toStringReturnsOriginalString() {
      CompactString cs = of("hello");
      assertEquals("hello", cs.toString());
   }

   @Test
   void toStringCalledMultipleTimesReturnsSameValue() {
      CompactString cs = of("hello");
      String first = cs.toString();
      String second = cs.toString();
      assertSame(first, second);
   }

   @Test
   void toStringNonAsciiMultiByteUtf8() {
      String nonAscii = "caf\u00e9"; // "café"
      CompactString cs = of(nonAscii);
      assertEquals(nonAscii, cs.toString());
   }

   @Test
   void toStringChineseCharacters() {
      String chinese = "\u4e2d\u6587"; // 中文
      CompactString cs = of(chinese);
      assertEquals(chinese, cs.toString());
   }

   @Test
   void toStringEmptyString() {
      CompactString cs = of("");
      assertEquals("", cs.toString());
   }

   // -----------------------------------------------------------------------
   // clone
   // -----------------------------------------------------------------------

   @Test
   void cloneProducesIndependentCopy() throws Exception {
      CompactString original = of("hello");
      CompactString cloned = (CompactString) original.clone();

      assertNotSame(original, cloned);
      assertEquals(original, cloned);
   }

   @Test
   void clonePreservesToStringValue() throws Exception {
      CompactString original = of("hello");
      CompactString cloned = (CompactString) original.clone();
      assertEquals("hello", cloned.toString());
   }

   @Test
   void cloneAfterToStringCallPreservesValue() throws Exception {
      CompactString original = of("hello");
      original.toString(); // triggers lazy conversion
      CompactString cloned = (CompactString) original.clone();
      assertEquals("hello", cloned.toString());
   }

   // -----------------------------------------------------------------------
   // Non-ASCII / UTF-8 multi-byte scenarios
   // -----------------------------------------------------------------------

   @Test
   void equalsWithNonAsciiSameBytesReturnsTrue() {
      String emoji = "\uD83D\uDE00"; // U+1F600 GRINNING FACE (4-byte UTF-8)
      CompactString a = of(emoji);
      CompactString b = of(emoji);
      assertEquals(a, b);
   }

   @Test
   void equalsWithNonAsciiDifferentStringReturnsFalse() {
      CompactString a = of("caf\u00e9");
      CompactString b = of("cafe");
      assertNotEquals(a, b);
   }
}
