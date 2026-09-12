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

import inetsoft.sree.security.IdentityID;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VSBookmark}.
 */
class VSBookmarkTest {

   private static final String USER = "testUser";
   private static final IdentityID USER_ID = new IdentityID(USER, null);

   @Test
   void removeBookmark_systemBookmarks_canBeRemoved() {
      VSBookmark bookmark = new VSBookmark("test-vs-id", USER_ID);
      bookmark.setBookmarkData(VSBookmark.HOME_BOOKMARK, new byte[]{ 1, 2, 3 });
      bookmark.setBookmarkData(VSBookmark.INITIAL_STATE, new byte[]{ 4, 5, 6 });

      bookmark.removeBookmark(VSBookmark.HOME_BOOKMARK);
      bookmark.removeBookmark(VSBookmark.INITIAL_STATE);

      assertNull(bookmark.getBookmarkData(VSBookmark.HOME_BOOKMARK));
      assertNull(bookmark.getBookmarkData(VSBookmark.INITIAL_STATE));
   }

   @Test
   void removeBookmark_userBookmark_isRemoved() {
      VSBookmark bookmark = new VSBookmark("test-vs-id", USER_ID);
      bookmark.setBookmarkData("MyBookmark", new byte[]{ 1, 2, 3 });

      bookmark.removeBookmark("MyBookmark");

      assertNull(bookmark.getBookmarkData("MyBookmark"));
   }

   @Test
   void mergeFrom_importsNewBookmarks() {
      VSBookmark existing = new VSBookmark("test-vs-id", USER_ID);
      existing.setBookmarkData("OldBookmark", new byte[]{ 1 });

      VSBookmark imported = new VSBookmark("test-vs-id", USER_ID);
      imported.setBookmarkData("NewBookmark", new byte[]{ 2 });

      existing.mergeFrom(imported);

      assertNotNull(existing.getBookmarkData("OldBookmark"),
         "Existing bookmarks must be preserved");
      assertNotNull(existing.getBookmarkData("NewBookmark"),
         "Imported bookmarks must be added");
   }

   @Test
   void mergeFrom_importedOverwritesConflicting() {
      byte[] originalData = new byte[]{ 1, 2, 3 };
      byte[] importedData = new byte[]{ 9, 8, 7 };

      VSBookmark existing = new VSBookmark("test-vs-id", USER_ID);
      existing.setBookmarkData("SharedBookmark", originalData);

      VSBookmark imported = new VSBookmark("test-vs-id", USER_ID);
      imported.setBookmarkData("SharedBookmark", importedData);

      existing.mergeFrom(imported);

      assertArrayEquals(importedData, (byte[]) existing.getBookmarkData("SharedBookmark"),
         "Imported bookmark data must overwrite existing");
   }

   @Test
   void mergeFrom_defaultBookmark_keptFromExistingWhenSet() {
      VSBookmark existing = new VSBookmark("test-vs-id", USER_ID);
      VSBookmark.DefaultBookmark existingDefault = new VSBookmark.DefaultBookmark("ExistingDefault", USER_ID);
      existing.setDefaultBookmark(existingDefault);

      VSBookmark imported = new VSBookmark("test-vs-id", USER_ID);
      VSBookmark.DefaultBookmark importedDefault = new VSBookmark.DefaultBookmark("ImportedDefault", USER_ID);
      imported.setDefaultBookmark(importedDefault);

      existing.mergeFrom(imported);

      assertEquals("ExistingDefault", existing.getDefaultBookmark().getName(),
         "Existing default bookmark must be kept when already set");
   }

   @Test
   void mergeFrom_defaultBookmark_importedWhenExistingIsNull() {
      VSBookmark existing = new VSBookmark("test-vs-id", USER_ID);
      // No default bookmark set on existing

      VSBookmark imported = new VSBookmark("test-vs-id", USER_ID);
      VSBookmark.DefaultBookmark importedDefault = new VSBookmark.DefaultBookmark("ImportedDefault", USER_ID);
      imported.setDefaultBookmark(importedDefault);

      existing.mergeFrom(imported);

      assertNotNull(existing.getDefaultBookmark(),
         "Imported default bookmark must be used when existing has none");
      assertEquals("ImportedDefault", existing.getDefaultBookmark().getName());
   }

   @Test
   void mergeFrom_nullImported_throwsNPE() {
      VSBookmark existing = new VSBookmark("test-vs-id", USER_ID);
      assertThrows(NullPointerException.class, () -> existing.mergeFrom(null));
   }

   @Test
   void defaultBookmark_isStaticNested_canBeConstructedWithoutOuterInstance() {
      // This test verifies DefaultBookmark is a static nested class, not an inner class.
      // If it were an inner class, this line would require an outer instance.
      VSBookmark.DefaultBookmark db = new VSBookmark.DefaultBookmark("myBookmark", USER_ID);
      assertEquals("myBookmark", db.getName());
      assertEquals(USER_ID, db.getOwner());
   }

   // ---- getIncompatibilities tests ----

   @Test
   void getIncompatibilities_homeAndInitialState_alwaysEmpty() {
      VSBookmark bm = new VSBookmark("test-vs-id", USER_ID);
      Viewsheet vs = new Viewsheet();

      assertTrue(bm.getIncompatibilities(VSBookmark.HOME_BOOKMARK, vs).isEmpty(),
         "Home bookmark is annotation-only and must never be reported as structurally incompatible");
      assertTrue(bm.getIncompatibilities(VSBookmark.INITIAL_STATE, vs).isEmpty(),
         "INITIAL_STATE is a runtime-only mirror of Home and must never be reported as incompatible");
   }

   @Test
   void getIncompatibilities_missingBookmarkName_returnsEmpty() {
      VSBookmark bm = new VSBookmark("test-vs-id", USER_ID);
      Viewsheet vs = new Viewsheet();

      VSBookmark.BookmarkIncompatibility result = bm.getIncompatibilities("NonExistent", vs);

      assertTrue(result.isEmpty());
      assertTrue(result.getMissingAssemblies().isEmpty());
      assertTrue(result.getTypeChanges().isEmpty());
      assertFalse(result.isParseError());
   }

   @Test
   void getIncompatibilities_assemblyRemovedFromViewsheet_reportedAsMissing() {
      VSBookmark bm = new VSBookmark("test-vs-id", USER_ID);
      // Stored state references "DeletedChart" which no longer exists in the viewsheet.
      String xml = "<state>" +
         "<assembly class=\"inetsoft.uql.viewsheet.ChartVSAssembly\">" +
         "<name>DeletedChart</name></assembly>" +
         "</state>";
      bm.setBookmarkData("MyBookmark", xml.getBytes(StandardCharsets.UTF_8));

      VSBookmark.BookmarkIncompatibility result =
         bm.getIncompatibilities("MyBookmark", new Viewsheet());

      assertFalse(result.isParseError());
      assertEquals(1, result.getMissingAssemblies().size());
      assertEquals("DeletedChart", result.getMissingAssemblies().get(0));
      assertTrue(result.getTypeChanges().isEmpty());
   }

   @Test
   void getIncompatibilities_assemblyClassChanged_reportedAsTypeChange() {
      Viewsheet vs = new Viewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "Widget1");
      vs.addAssembly(text);

      VSBookmark bm = new VSBookmark("test-vs-id", USER_ID);
      // Stored state claims Widget1 was a ChartVSAssembly (different from current TextVSAssembly).
      String xml = "<state>" +
         "<assembly class=\"inetsoft.uql.viewsheet.ChartVSAssembly\">" +
         "<name>Widget1</name></assembly>" +
         "</state>";
      bm.setBookmarkData("MyBookmark", xml.getBytes(StandardCharsets.UTF_8));

      VSBookmark.BookmarkIncompatibility result = bm.getIncompatibilities("MyBookmark", vs);

      assertFalse(result.isParseError());
      assertTrue(result.getMissingAssemblies().isEmpty());
      assertEquals(1, result.getTypeChanges().size());
      assertEquals("Widget1", result.getTypeChanges().get(0));
   }

   @Test
   void getIncompatibilities_malformedXml_setsParseError() {
      VSBookmark bm = new VSBookmark("test-vs-id", USER_ID);
      bm.setBookmarkData("MyBookmark", "<<not valid xml>>".getBytes(StandardCharsets.UTF_8));

      VSBookmark.BookmarkIncompatibility result =
         bm.getIncompatibilities("MyBookmark", new Viewsheet());

      assertTrue(result.isParseError());
      assertFalse(result.isEmpty());
   }
}
