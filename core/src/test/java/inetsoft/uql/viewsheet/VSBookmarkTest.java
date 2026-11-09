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
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;
import inetsoft.util.Encoder;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VSBookmark}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
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

   // ---- Bug #80536: password state of a text input inside an embedded viewsheet ----

   private static final String SECRET = "Zq7secretVXP080536";
   private static final String VISIBLE = "Zq7visibleVXP080536";

   private static TextInputVSAssembly addInput(Viewsheet vs, String name, boolean password,
                                               String value)
   {
      TextInputVSAssembly assembly = new TextInputVSAssembly(vs, name);
      TextInputVSAssemblyInfo info = (TextInputVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setColumnOption(password ? new PasswordColumnOption() : new TextColumnOption());
      info.setDataType(XSchema.STRING);
      info.setPixelSize(new Dimension(100, 20));
      assembly.setSelectedObject(value);
      vs.addAssembly(assembly);
      return assembly;
   }

   private static Viewsheet addEmbedded(Viewsheet parent, String name) {
      Viewsheet child = new Viewsheet();
      child.setViewsheet(parent);
      child.getVSAssemblyInfo().setName(name);
      parent.addAssembly(child);
      return child;
   }

   private static byte[] stateBytes(Viewsheet vs) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      vs.writeState(writer, false);
      writer.flush();
      return out.toString().getBytes(StandardCharsets.UTF_8);
   }

   private static String scrub(Viewsheet vs) {
      byte[] bytes = stateBytes(vs);
      return new String(VSBookmark.removePasswordState(bytes, vs), StandardCharsets.UTF_8);
   }

   @Test
   void removePasswordState_embeddedPasswordIsBlankedAndSiblingsKept() {
      Viewsheet root = new Viewsheet();
      addInput(root, "HostPwd", true, SECRET + "host");
      Viewsheet child = addEmbedded(root, "Viewsheet1");
      addInput(child, "ChildPwd", true, SECRET + "child");
      addInput(child, "ChildPlain", false, VISIBLE);

      String xml = scrub(root);

      assertFalse(xml.contains(SECRET), xml);
      assertTrue(xml.contains(VISIBLE), xml);
   }

   @Test
   void removePasswordState_rootPasswordAndEmbeddedTextShareName_embeddedTextKept() {
      Viewsheet root = new Viewsheet();
      addInput(root, "Shared", true, SECRET);
      Viewsheet child = addEmbedded(root, "Viewsheet1");
      addInput(child, "Shared", false, VISIBLE);

      String xml = scrub(root);

      assertFalse(xml.contains(SECRET), xml);
      assertTrue(xml.contains(VISIBLE), xml);
   }

   @Test
   void removePasswordState_rootTextAndEmbeddedPasswordShareName_embeddedPasswordBlanked() {
      Viewsheet root = new Viewsheet();
      addInput(root, "Shared", false, VISIBLE);
      Viewsheet child = addEmbedded(root, "Viewsheet1");
      addInput(child, "Shared", true, SECRET);

      String xml = scrub(root);

      assertFalse(xml.contains(SECRET), xml);
      assertTrue(xml.contains(VISIBLE), xml);
   }

   @Test
   void removePasswordState_twoLevelsOfEmbedding() {
      Viewsheet root = new Viewsheet();
      Viewsheet outer = addEmbedded(root, "Viewsheet1");
      Viewsheet inner = addEmbedded(outer, "Viewsheet2");
      addInput(outer, "OuterPwd", true, SECRET + "outer");
      addInput(inner, "InnerPwd", true, SECRET + "inner");
      addInput(inner, "InnerPlain", false, VISIBLE);

      String xml = scrub(root);

      assertFalse(xml.contains(SECRET), xml);
      assertTrue(xml.contains(VISIBLE), xml);
   }

   @Test
   void removePasswordState_sameChildNameInTwoEmbeddedViewsheets() {
      Viewsheet root = new Viewsheet();
      Viewsheet first = addEmbedded(root, "Viewsheet1");
      Viewsheet second = addEmbedded(root, "Viewsheet2");
      addInput(first, "Input", true, SECRET);
      addInput(second, "Input", false, VISIBLE);

      String xml = scrub(root);

      assertFalse(xml.contains(SECRET), xml);
      assertTrue(xml.contains(VISIBLE), xml);
   }

   @Test
   void removePasswordState_noPasswordReturnsSameBytes() {
      Viewsheet root = new Viewsheet();
      Viewsheet child = addEmbedded(root, "Viewsheet1");
      addInput(root, "Plain", false, VISIBLE);
      addInput(child, "ChildPlain", false, VISIBLE);
      byte[] bytes = stateBytes(root);

      assertSame(bytes, VSBookmark.removePasswordState(bytes, root));
   }

   @Test
   void removePasswordState_unbalancedStateFailsClosed() {
      Viewsheet root = new Viewsheet();
      addInput(root, "Pwd", true, SECRET);
      String xml = new String(stateBytes(root), StandardCharsets.UTF_8) + "</assembly>";

      String out = new String(
         VSBookmark.removePasswordState(xml.getBytes(StandardCharsets.UTF_8), root),
         StandardCharsets.UTF_8);

      assertFalse(out.contains(SECRET), out);
   }

   @Test
   void writeXML_snapshotScrubsEmbeddedPasswordFromBookmark() {
      Viewsheet root = new Viewsheet();
      Viewsheet child = addEmbedded(root, "Viewsheet1");
      addInput(child, "ChildPwd", true, SECRET);
      addInput(child, "ChildPlain", false, VISIBLE);
      VSBookmark bookmark = new VSBookmark();
      bookmark.addBookmark("b1", root, VSBookmarkInfo.PRIVATE, false, false);
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      bookmark.writeXML(writer, root);
      writer.flush();
      Matcher matcher = Pattern.compile("<value><!\\[CDATA\\[([0-9a-fA-F]*)\\]\\]></value>")
         .matcher(out.toString());
      StringBuilder decoded = new StringBuilder();

      while(matcher.find()) {
         decoded.append(new String(Encoder.decodeAsciiHex(matcher.group(1)),
                                   StandardCharsets.UTF_8));
      }

      assertFalse(decoded.toString().contains(SECRET), decoded.toString());
      assertTrue(decoded.toString().contains(VISIBLE), decoded.toString());
   }
}
