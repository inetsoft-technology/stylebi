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
package inetsoft.report.io.viewsheet;

import inetsoft.test.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;
import inetsoft.util.Encoder;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80310: a Snapshot (.vso) export wrote the value of a password text input in clear text
 * (state_selectedObject, text and value of the assembly, and the state bytes of every bookmark).
 * The value is now left out of a snapshot, so the input reopens empty, and only there.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TextInputPasswordSnapshotTest {
   private static final String SECRET = "Zq7secretVXP080310";
   private static final String VISIBLE = "Zq7visibleVXP080310";

   private static TextInputVSAssembly add(Viewsheet vs, String name, boolean password,
                                          String type, Object value)
   {
      TextInputVSAssembly assembly = new TextInputVSAssembly(vs, name);
      TextInputVSAssemblyInfo info = (TextInputVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setColumnOption(password ? new PasswordColumnOption() : new TextColumnOption());
      info.setDataType(type);
      info.setPixelSize(new Dimension(100, 20));
      assembly.setSelectedObject(value);
      vs.addAssembly(assembly);
      return assembly;
   }

   private static String xml(TextInputVSAssembly assembly) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      assembly.writeXML(writer);
      writer.flush();
      return out.toString();
   }

   private static String state(Viewsheet vs, boolean runtime) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      vs.writeState(writer, runtime);
      writer.flush();
      return out.toString();
   }

   /**
    * The content of the &lt;bookmark&gt; values, hex decoded, as the importer would read it.
    */
   private static String bookmarkContent(VSBookmark bookmark, Viewsheet snapshotVs) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      bookmark.writeXML(writer, snapshotVs);
      writer.flush();
      String xml = out.toString();
      Matcher matcher = Pattern.compile("<value><!\\[CDATA\\[([0-9a-fA-F]*)\\]\\]></value>")
         .matcher(xml);
      StringBuilder decoded = new StringBuilder(xml);

      while(matcher.find()) {
         decoded.append(new String(Encoder.decodeAsciiHex(matcher.group(1)),
                                   StandardCharsets.UTF_8));
      }

      return decoded.toString();
   }

   private static VSBookmark bookmarkOf(Viewsheet vs) {
      VSBookmark bookmark = new VSBookmark();
      bookmark.addBookmark("b1", vs, VSBookmarkInfo.PRIVATE, false, false);
      return bookmark;
   }

   @Test
   void passwordValueIsLeftOutOfSnapshotAssemblyXml() {
      Viewsheet vs = new Viewsheet();
      TextInputVSAssembly assembly = add(vs, "Pwd", true, XSchema.STRING, SECRET);
      vs.setSnapshotExport(true);
      String xml = xml(assembly);

      assertFalse(xml.contains(SECRET), xml);
      assertFalse(xml.contains("<value"), xml);
      assertFalse(xml.contains("<text>"), xml);
      // the state element is kept, so the parser sees what an empty input writes
      assertTrue(xml.contains("<state_selectedObject"), xml);
   }

   @Test
   void passwordValueIsKeptWithoutSnapshotFlag() {
      Viewsheet vs = new Viewsheet();
      TextInputVSAssembly assembly = add(vs, "Pwd", true, XSchema.STRING, SECRET);
      String xml = xml(assembly);

      assertEquals(3, count(xml, SECRET), xml);
      assertTrue(state(vs, true).contains(SECRET));
      assertTrue(state(vs, false).contains(SECRET));
   }

   @Test
   void textInputValueIsKeptInSnapshot() {
      Viewsheet vs = new Viewsheet();
      TextInputVSAssembly assembly = add(vs, "Plain", false, XSchema.STRING, VISIBLE);
      vs.setSnapshotExport(true);

      assertEquals(3, count(xml(assembly), VISIBLE));
   }

   @Test
   void passwordSnapshotReloadsEmptyForEveryDataType() throws Exception {
      String[] types = { XSchema.STRING, XSchema.INTEGER, XSchema.DOUBLE, XSchema.DATE,
                         XSchema.BOOLEAN, XSchema.TIME_INSTANT };

      for(String type : types) {
         Viewsheet vs = new Viewsheet();
         TextInputVSAssembly assembly = add(vs, "Pwd", true, type, sample(type));
         vs.setSnapshotExport(true);

         Element elem = Tool.parseXML(new StringReader(xml(assembly))).getDocumentElement();
         TextInputVSAssembly loaded = new TextInputVSAssembly(new Viewsheet(), "Pwd");
         ((TextInputVSAssemblyInfo) loaded.getVSAssemblyInfo()).setDataType(type);
         loaded.setSelectedObject("stale");
         loaded.parseState(elem);
         assertNull(loaded.getSelectedObject(), type);

         loaded.parseXML(elem);
         assertNull(loaded.getSelectedObject(), type);
      }
   }

   @Test
   void passwordIsScrubbedFromSnapshotBookmark() {
      Viewsheet vs = new Viewsheet();
      add(vs, "Pwd", true, XSchema.STRING, SECRET);
      add(vs, "Plain", false, XSchema.STRING, VISIBLE);
      VSBookmark bookmark = bookmarkOf(vs);

      // the bookmark bytes hold the password as it was saved, hex encoded in the export
      assertTrue(bookmarkContent(bookmark, null).contains(SECRET));

      String content = bookmarkContent(bookmark, vs);
      assertFalse(content.contains(SECRET), content);
      assertTrue(content.contains(VISIBLE), content);
   }

   @Test
   void snapshotBookmarkReloadsPasswordEmptyAndKeepsOtherState() throws Exception {
      Viewsheet vs = new Viewsheet();
      add(vs, "Pwd", true, XSchema.STRING, SECRET);
      add(vs, "Plain", false, XSchema.STRING, VISIBLE);
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      bookmarkOf(vs).writeXML(writer, vs);
      writer.flush();

      Element root = Tool.parseXML(new StringReader(out.toString())).getDocumentElement();
      Element value = Tool.getChildNodeByTagName(
         Tool.getChildNodeByTagName(root, "bookmark"), "value");
      byte[] bytes = Encoder.decodeAsciiHex(Tool.getValue(value));

      Viewsheet target = new Viewsheet();
      TextInputVSAssembly pwd = add(target, "Pwd", true, XSchema.STRING, "stale");
      TextInputVSAssembly plain = add(target, "Plain", false, XSchema.STRING, "stale");
      Element state = Tool.parseXML(new ByteArrayInputStream(bytes)).getDocumentElement();
      target.parseState(state, true);

      assertNull(pwd.getSelectedObject());
      assertEquals(VISIBLE, plain.getSelectedObject());
   }

   @Test
   void snapshotFlagIsOffByDefaultAndSettable() {
      Viewsheet vs = new Viewsheet();
      assertFalse(vs.isSnapshotExport());
      vs.setSnapshotExport(true);
      assertTrue(vs.isSnapshotExport());
   }

   private static Object sample(String type) {
      switch(type) {
      case XSchema.INTEGER:
         return 5;
      case XSchema.DOUBLE:
         return 1.5;
      case XSchema.DATE:
         return new java.sql.Date(86400000L * 365);
      case XSchema.BOOLEAN:
         return Boolean.TRUE;
      case XSchema.TIME_INSTANT:
         return new java.sql.Timestamp(86400000L * 365);
      default:
         return SECRET;
      }
   }

   private static int count(String text, String find) {
      int n = 0;

      for(int i = text.indexOf(find); i >= 0; i = text.indexOf(find, i + 1)) {
         n++;
      }

      return n;
   }
}
