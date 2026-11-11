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
 * The axis line's author flag: derived from the line when a file predates it, written only when
 * the derivation would read it wrong.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AxisDescriptorLineFlagTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void anOldFilesLineOffIsTheAuthors() throws Exception {
      AxisDescriptor d = new AxisDescriptor();
      d.setLineVisible(false);

      AxisDescriptor loaded = parse(oldFile(d));

      assertFalse(loaded.isLineVisible());
      assertTrue(loaded.isUserLineVisible(), "no classic creation wrote a line off");
   }

   @Test
   void anOldFilesLineOnIsNoOpinion() throws Exception {
      AxisDescriptor d = new AxisDescriptor();
      d.setLineVisible(true);

      AxisDescriptor loaded = parse(oldFile(d));

      assertTrue(loaded.isLineVisible());
      assertFalse(loaded.isUserLineVisible());
   }

   @Test
   void aPresentFlagIsReadAsItIs() throws Exception {
      AxisDescriptor seeded = parse("<axisDescriptor lineVisible=\"false\" userLineVisible=\"false\"/>");
      assertFalse(seeded.isLineVisible());
      assertFalse(seeded.isUserLineVisible(), "a stored false beats the derivation");

      AxisDescriptor authored = parse("<axisDescriptor lineVisible=\"true\" userLineVisible=\"true\"/>");
      assertTrue(authored.isLineVisible());
      assertTrue(authored.isUserLineVisible(), "a stored true beats the derivation");
   }

   @Test
   void aNewRadarsLabelAxisWritesNoFlag() throws Exception {
      AxisDescriptor label = new RadarVSChartInfo().getLabelAxisDescriptor();
      String written = xml(label);

      assertFalse(written.contains("userLineVisible"), written);
      assertEquals(label.isUserLineVisible(), parse(written).isUserLineVisible(),
                   "memory matches the reloaded state");
   }

   @Test
   void anUneditedOldFileWritesWhatItAlwaysWrote() throws Exception {
      assertFalse(xml(new AxisDescriptor()).contains("userLineVisible"),
                  "an unedited classic descriptor writes no flag");

      for(boolean line : new boolean[] { true, false }) {
         AxisDescriptor d = new AxisDescriptor();
         d.setLineVisible(line);
         String old = oldFile(d);

         String rewritten = xml(parse(old));

         assertFalse(rewritten.contains("userLineVisible"), "line " + line + ": " + rewritten);
         assertEquals(old, rewritten, "line " + line);
      }
   }

   @Test
   void aSeededLineOffKeepsItsFlagThroughASave() throws Exception {
      AxisDescriptor d = new AxisDescriptor();
      d.setLineVisible(false);
      String written = xml(d);

      assertTrue(written.contains("userLineVisible=\"false\""), written);
      AxisDescriptor loaded = parse(written);
      assertFalse(loaded.isLineVisible());
      assertFalse(loaded.isUserLineVisible(), "a seeded off stays the seed's");
   }

   @Test
   void anAuthorsLineOnIsWritten() throws Exception {
      AxisDescriptor d = new AxisDescriptor();
      d.setAuthorLineVisible(true);
      String written = xml(d);

      assertTrue(written.contains("userLineVisible=\"true\""), written);
      AxisDescriptor loaded = parse(written);
      assertTrue(loaded.isLineVisible());
      assertTrue(loaded.isUserLineVisible());
   }

   @Test
   void aCopyKeepsTheFlag() {
      AxisDescriptor d = new AxisDescriptor();
      d.setAuthorLineVisible(false);

      AxisDescriptor copy = d.clone();

      assertFalse(copy.isLineVisible());
      assertTrue(copy.isUserLineVisible());
   }

   @Test
   void theFlagIsPartOfTheContent() {
      // a content copy skips descriptors it finds equal, so a flag-only change would be lost
      AxisDescriptor authored = new AxisDescriptor();
      authored.setAuthorLineVisible(false);
      AxisDescriptor seeded = new AxisDescriptor();
      seeded.setLineVisible(false);

      assertFalse(authored.equalsContent(seeded));
      assertTrue(authored.equalsContent(authored.clone()));
   }

   @Test
   void maxModeFollowsTheLineUnderModern() {
      AxisDescriptor d = new AxisDescriptor();
      d.setLineVisible(false);
      assertFalse(d.isMaxModeLineVisible(VizContext.of(VizMark.MODERN_LIGHT)));
      assertTrue(d.isMaxModeLineVisible(VizContext.LEGACY), "classic reads the stored max-mode value");
      d.setLineVisible(true);
      d.setMaxModeLineVisible(false);
      assertFalse(d.isMaxModeLineVisible(VizContext.of(VizMark.MODERN_LIGHT)), "a stored false is the author's");
   }

   // the bytes an older build wrote: no flag attribute
   private static String oldFile(AxisDescriptor d) {
      return xml(d).replaceAll(" userLineVisible=\"[a-z]*\"", "");
   }

   private static String xml(AxisDescriptor d) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      d.writeXML(writer);
      writer.flush();
      return out.toString();
   }

   private static AxisDescriptor parse(String xml) throws Exception {
      AxisDescriptor d = new AxisDescriptor();
      d.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return d;
   }
}
