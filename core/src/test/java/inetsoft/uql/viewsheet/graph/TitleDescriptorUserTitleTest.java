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
package inetsoft.uql.viewsheet.graph;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.util.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The axis title's author flag: the custom text is the author's. Derived from "has text" when a
 * file predates it, and written only when the derivation would read it wrong.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TitleDescriptorUserTitleTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void anOldFilesTypedTitleIsTheAuthors() throws Exception {
      TitleDescriptor d = new TitleDescriptor();
      d.setTitleValue("Revenue");
      String written = xml(d);
      String old = oldFile(written);
      assertNotEquals(written, old, "premise: the text without the flag wrote it");

      TitleDescriptor loaded = parse(old);

      assertEquals("Revenue", loaded.getTitleValue());
      assertTrue(loaded.isUserTitle(), "an old file's text is the author's");
   }

   @Test
   void aSystemTitleStaysNoOpinionThroughASave() throws Exception {
      TitleDescriptor d = new TitleDescriptor();
      d.setTitleValue("Frequency");
      assertFalse(d.isUserTitle(), "premise: setting the text stamps nothing");
      String written = xml(d);

      assertTrue(written.contains("userTitle=\"false\""), written);
      assertFalse(parse(written).isUserTitle());
   }

   @Test
   void aTitleWithoutTextWritesNoFlag() throws Exception {
      String written = xml(new TitleDescriptor());

      assertFalse(written.contains("userTitle"), written);
      assertFalse(parse(written).isUserTitle());
   }

   @Test
   void anAuthorsTitleKeepsItsFlagThroughASave() throws Exception {
      TitleDescriptor d = new TitleDescriptor();
      d.setTitleValue("Revenue");
      d.setUserTitle(true);
      String written = xml(d);

      assertFalse(written.contains("userTitle"), "the derivation reads it right: " + written);
      assertTrue(parse(written).isUserTitle());
   }

   @Test
   void aCopyKeepsTheFlagAndTheFlagIsContent() {
      TitleDescriptor authored = new TitleDescriptor();
      authored.setTitleValue("Revenue");
      authored.setUserTitle(true);
      TitleDescriptor system = new TitleDescriptor();
      system.setTitleValue("Revenue");

      assertTrue(((TitleDescriptor) authored.clone()).isUserTitle());
      assertFalse(authored.equalsContent(system));
      assertTrue(authored.equalsContent(authored.clone()));
   }

   @Test
   void typingATitleMarksItTheAuthors() throws Exception {
      TitleDescriptor d = new TitleDescriptor();
      d.setAuthorTitleValue("Revenue");

      assertTrue(d.isUserTitle());
      String written = xml(d);
      assertFalse(written.contains("userTitle"), "the flag is derived: " + written);
      assertTrue(parse(written).isUserTitle());
   }

   @Test
   void typingOverASeededHiddenTitleShowsIt() {
      TitleDescriptor d = seededHidden();

      d.setAuthorTitleValue("Revenue");

      assertTrue(d.isVisible());
      assertTrue(d.hasVisibleUserValue());
   }

   @Test
   void typingNeverShowsATitleTheAuthorHid() {
      TitleDescriptor d = seededHidden();
      d.setVisible(false);

      d.setAuthorTitleValue("Revenue");

      assertFalse(d.isVisible());
   }

   @Test
   void typingOnAClassicTitleWritesNoVisibility() {
      TitleDescriptor d = new TitleDescriptor();
      d.setVisible(true, CompositeValue.Type.DEFAULT);

      d.setAuthorTitleValue("Revenue");

      assertTrue(d.isVisible());
      assertFalse(d.hasVisibleUserValue());
   }

   @Test
   void aStylesheetHiddenTitleStaysHidden() {
      TitleDescriptor d = seededHidden();
      d.setVisible(false, CompositeValue.Type.CSS);

      d.setAuthorTitleValue("Revenue");

      assertFalse(d.isVisible());
      assertFalse(d.hasVisibleUserValue());
   }

   @Test
   void clearingTheTextClearsTheFlag() {
      TitleDescriptor d = seededHidden();
      d.setAuthorTitleValue("Revenue");
      assertTrue(d.isUserTitle(), "premise");

      d.setAuthorTitleValue("");

      assertFalse(d.isUserTitle());
   }

   @Test
   void emptyTextShowsNothing() {
      TitleDescriptor d = seededHidden();

      d.setAuthorTitleValue("");
      d.setAuthorTitle(null);

      assertFalse(d.isVisible());
      assertFalse(d.hasVisibleUserValue());
   }

   @Test
   void scriptTextShowsASeededHiddenTitle() {
      TitleDescriptor d = seededHidden();

      d.setAuthorTitle("abc");

      assertEquals("abc", d.getTitle());
      assertNull(d.getTitleValue(), "a script writes the runtime text only");
      assertTrue(d.isVisible());
   }

   @Test
   void maxModeFollowsVisibilityUnderModern() {
      VizContext modern = VizContext.of(VizMark.MODERN_LIGHT);
      TitleDescriptor d = new TitleDescriptor();
      d.setVisible(false, CompositeValue.Type.DEFAULT);

      assertFalse(d.isMaxModeVisible(modern), "modern follows the hidden title");
      assertTrue(d.isMaxModeVisible(VizContext.LEGACY), "classic reads the stored max-mode value");

      d.setVisible(true);
      assertTrue(d.isMaxModeVisible(modern), "an author's show");

      d.setMaxModeVisible(false);
      assertFalse(d.isMaxModeVisible(modern), "a stored false is the author's, modern");
      assertFalse(d.isMaxModeVisible(VizContext.LEGACY), "a stored false is the author's, classic");
   }

   // as the modern seed leaves a title its type does not show
   private static TitleDescriptor seededHidden() {
      TitleDescriptor d = new TitleDescriptor();
      d.setVisible(false, CompositeValue.Type.DEFAULT);
      return d;
   }

   // the bytes an older build wrote: no flag attribute
   private static String oldFile(String xml) {
      return xml.replaceAll(" userTitle=\"[a-z]*\"", "");
   }

   private static String xml(TitleDescriptor d) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      d.writeXML(writer);
      writer.flush();
      return out.toString();
   }

   private static TitleDescriptor parse(String xml) throws Exception {
      TitleDescriptor d = new TitleDescriptor();
      d.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return d;
   }
}
