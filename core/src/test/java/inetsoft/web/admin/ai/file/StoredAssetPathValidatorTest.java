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
package inetsoft.web.admin.ai.file;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The load-bearing security test for Track 1 (01-design.md section 3.3/6.5): {@code
 * DataSpace.sanitizePathComponent} never rejects a {@code ".."} segment, so {@link
 * StoredAssetPathValidator} is the only layer that does.
 */
@Tag("core")
class StoredAssetPathValidatorTest {
   @Test
   void requirePathAcceptsALegitimateNestedPath() {
      assertEquals("scripts/lib/utils.js",
                   StoredAssetPathValidator.requirePath("scripts/lib/utils.js", "path"));
   }

   @Test
   void requirePathAcceptsABackslashSeparatedPathAndNormalizesToForwardSlash() {
      assertEquals("scripts/lib/utils.js",
                   StoredAssetPathValidator.requirePath("scripts\\lib\\utils.js", "path"));
   }

   @ParameterizedTest
   @ValueSource(strings = {"/", ".", "", "//"})
   void requirePathTreatsRootAliasesAsEmptyOrRejectsThem(String raw) {
      if(raw.equals("//")) {
         assertThrows(IllegalArgumentException.class,
                      () -> StoredAssetPathValidator.requirePath(raw, "path"));
      }
      else {
         assertEquals("", StoredAssetPathValidator.requirePath(raw, "path"));
      }
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "../etc/passwd", "..\\..\\windows\\system32", "scripts/../../../etc/passwd",
      "a/../b", "a/b/..", ".."
   })
   void requirePathRejectsADotDotSegmentAnywhere(String raw) {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> StoredAssetPathValidator.requirePath(raw, "path"));
      assertTrue(ex.getMessage().contains("path"), ex.getMessage());
   }

   @Test
   void requirePathRejectsALeadingSlash() {
      assertThrows(IllegalArgumentException.class,
                   () -> StoredAssetPathValidator.requirePath("/etc/passwd", "path"));
   }

   @Test
   void requirePathRejectsATrailingSlash() {
      assertThrows(IllegalArgumentException.class,
                   () -> StoredAssetPathValidator.requirePath("scripts/lib/", "path"));
   }

   @Test
   void requirePathRejectsAnEmptySegment() {
      assertThrows(IllegalArgumentException.class,
                   () -> StoredAssetPathValidator.requirePath("scripts//lib.js", "path"));
   }

   @Test
   void requirePathRejectsADriveLetterSegment() {
      assertThrows(IllegalArgumentException.class,
                   () -> StoredAssetPathValidator.requirePath("C:\\Windows", "path"));
      assertThrows(IllegalArgumentException.class,
                   () -> StoredAssetPathValidator.requirePath("C:/Windows", "path"));
   }

   @Test
   void requirePathRejectsATildePrefixedSegment() {
      assertThrows(IllegalArgumentException.class,
                   () -> StoredAssetPathValidator.requirePath("~/secrets.txt", "path"));
   }

   @Test
   void requirePathRejectsANulByte() {
      assertThrows(IllegalArgumentException.class,
                   () -> StoredAssetPathValidator.requirePath("scripts/a\u0000b.js", "path"));
   }

   @Test
   void requirePathRejectsNull() {
      assertThrows(IllegalArgumentException.class,
                   () -> StoredAssetPathValidator.requirePath(null, "path"));
   }

   /** False-positive guard: a legitimate filename containing literal ".." as a SUBSTRING, not a
    * whole segment, must NOT be rejected. */
   @Test
   void requirePathDoesNotFalsePositiveOnADotDotSubstring() {
      assertEquals("report..v2.xml",
                   StoredAssetPathValidator.requirePath("report..v2.xml", "path"));
      assertEquals("a..b/c.txt", StoredAssetPathValidator.requirePath("a..b/c.txt", "path"));
   }

   @Test
   void requireSiblingNameAcceptsABareName() {
      assertEquals("new-name.js", StoredAssetPathValidator.requireSiblingName("new-name.js", "newName"));
   }

   @Test
   void requireSiblingNameRejectsAPathSeparator() {
      assertThrows(IllegalArgumentException.class,
                   () -> StoredAssetPathValidator.requireSiblingName("a/b.js", "newName"));
      assertThrows(IllegalArgumentException.class,
                   () -> StoredAssetPathValidator.requireSiblingName("a\\b.js", "newName"));
   }

   @Test
   void requireSiblingNameRejectsDotDot() {
      assertThrows(IllegalArgumentException.class,
                   () -> StoredAssetPathValidator.requireSiblingName("..", "newName"));
   }

   @Test
   void requireNotRootRejectsTheEmptyPath() {
      assertThrows(IllegalArgumentException.class,
                   () -> StoredAssetPathValidator.requireNotRoot("", "path"));
   }

   @Test
   void requireNotRootAcceptsANonRootPath() {
      assertDoesNotThrow(() -> StoredAssetPathValidator.requireNotRoot("scripts/a.js", "path"));
   }
}
