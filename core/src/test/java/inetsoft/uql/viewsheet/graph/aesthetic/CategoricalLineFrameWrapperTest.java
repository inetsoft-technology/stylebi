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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.CategoricalLineFrame;
import inetsoft.graph.aesthetic.GLine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.CoreTool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82047: the default line palette dropped its duplicate entry (31 to 30 entries). A chart
 * saved before that stores all 31 entries and must read back with the same look, the saved
 * length kept, instead of wrapping its last entry onto the first.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CategoricalLineFrameWrapperTest {
   private static final GLine[] CYCLE = {
      GLine.THIN_LINE, GLine.DOT_LINE, GLine.DASH_LINE, GLine.MEDIUM_DASH, GLine.LARGE_DASH
   };

   @Test
   void legacyPaletteWithUserEditsReadsBackUnchanged() throws Exception {
      GLine[] legacy = legacyPalette();
      legacy[3] = GLine.DOT_LINE;
      legacy[30] = GLine.DASH_LINE;

      CategoricalLineFrame frame = read(xml(styles(legacy)));
      assertPalette(legacy, frame);
      assertEquals(GLine.THIN_LINE, frame.getLine(0), "saved index 30 must not wrap onto index 0");
      assertEquals(GLine.LARGE_DASH, frame.getLine(10), "the legacy look is kept");

      CategoricalLineFrame again = read(write(frame));
      assertPalette(legacy, again);
   }

   @Test
   void emptyLinesKeepTheDefaultPalette() throws Exception {
      CategoricalLineFrame frame = read(xml(new String[0]));
      CategoricalLineFrame defaults = new CategoricalLineFrame();

      assertEquals(defaults.getLineCount(), frame.getLineCount());

      for(int i = 0; i < defaults.getLineCount(); i++) {
         assertEquals(defaults.getLine(i), frame.getLine(i), "index " + i);
      }
   }

   @Test
   void nonNumericEntryKeepsTheDefaultAtThatPosition() throws Exception {
      String[] styles = styles(legacyPalette());
      styles[5] = "x";
      styles[30] = "";

      CategoricalLineFrame frame = assertDoesNotThrow(() -> read(xml(styles)));

      assertEquals(31, frame.getLineCount());
      assertEquals(new CategoricalLineFrame().getLine(5), frame.getLine(5));
      assertEquals(GLine.THIN_LINE, frame.getLine(30), "beyond the default length the fallback is THIN_LINE");
      assertEquals(GLine.DOT_LINE, frame.getLine(1), "valid entries are still read");
   }

   /** The 31-entry default palette written by every chart saved before #82047. */
   private static GLine[] legacyPalette() {
      GLine[] lines = new GLine[31];

      for(int i = 0; i < 31; i++) {
         lines[i] = i < 10 ? CYCLE[i % 5] : CYCLE[(i - 1) % 5];
      }

      return lines;
   }

   private static String[] styles(GLine[] lines) {
      String[] styles = new String[lines.length];

      for(int i = 0; i < lines.length; i++) {
         styles[i] = String.valueOf(lines[i].getStyle());
      }

      return styles;
   }

   private static String xml(String[] styles) {
      StringBuilder xml = new StringBuilder(
         "<legendFrame classname=\"inetsoft.graph.aesthetic.CategoricalLineFrame\" changed=\"true\">");
      xml.append(styles.length == 0 ? "<lines/>" : "<lines>");

      for(int i = 0; i < styles.length; i++) {
         xml.append("<line index=\"").append(i).append("\" value=\"").append(styles[i]).append("\"></line>");
      }

      xml.append(styles.length == 0 ? "" : "</lines>").append("</legendFrame>");
      return xml.toString();
   }

   private static void assertPalette(GLine[] expected, CategoricalLineFrame frame) {
      assertEquals(expected.length, frame.getLineCount());

      for(int i = 0; i < expected.length; i++) {
         assertEquals(expected[i], frame.getLine(i), "index " + i);
      }
   }

   private static String write(CategoricalLineFrame frame) throws Exception {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      VisualFrameWrapper.wrap(frame).writeXML(writer);
      writer.flush();
      return buffer.toString();
   }

   private static CategoricalLineFrame read(String xml) throws Exception {
      Document doc = CoreTool.parseXML(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      VisualFrameWrapper wrapper = VisualFrameWrapper.createVisualFrame(doc.getDocumentElement());
      assertInstanceOf(CategoricalLineFrameWrapper.class, wrapper);
      return (CategoricalLineFrame) wrapper.getVisualFrame();
   }
}
