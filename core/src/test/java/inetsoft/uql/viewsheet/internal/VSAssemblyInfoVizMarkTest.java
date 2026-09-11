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
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSAssemblyInfoVizMarkTest {
   @Test
   void newInfoIsUnmarked() {
      assertNull(new TextVSAssemblyInfo().getVizMark());
   }

   @Test
   void aMarkSurvivesARoundTrip() throws Exception {
      assertEquals(VizMark.MODERN_LIGHT, roundTrip(VizMark.MODERN_LIGHT).getVizMark(),
                   "a light mark must survive a save and load");
      assertEquals(VizMark.MODERN_DARK, roundTrip(VizMark.MODERN_DARK).getVizMark(),
                   "a dark mark must survive a save and load");
   }

   @Test
   void anUnmarkedInfoWritesNoAttribute() throws Exception {
      assertFalse(toXml(new TextVSAssemblyInfo()).contains("vizMark"),
                  "an unmarked assembly must add nothing to the file format");
   }

   @Test
   void parsingAFileWithNoMarkClearsAStampedField() throws Exception {
      // the load path constructs before it parses, so a constructor stamp must not survive a file
      // that carries no mark - this is what keeps legacy content unmarked
      String legacy = toXml(new TextVSAssemblyInfo());

      TextVSAssemblyInfo stamped = new TextVSAssemblyInfo();
      stamped.setVizMark(VizMark.MODERN_DARK);
      parseInto(stamped, legacy);

      assertNull(stamped.getVizMark(),
                 "an absent attribute must clear the field, not leave it alone");
   }

   @Test
   void copyViewInfoCarriesTheMark() {
      TextVSAssemblyInfo source = new TextVSAssemblyInfo();
      source.setVizMark(VizMark.MODERN_DARK);
      TextVSAssemblyInfo target = new TextVSAssemblyInfo();

      target.copyViewInfo(source, true);

      assertEquals(VizMark.MODERN_DARK, target.getVizMark(),
                   "changing an object's type must not silently modernize a legacy assembly");
   }

   @Test
   void copyViewInfoCarriesTheAbsenceOfAMark() {
      TextVSAssemblyInfo source = new TextVSAssemblyInfo();
      TextVSAssemblyInfo target = new TextVSAssemblyInfo();
      target.setVizMark(VizMark.MODERN_LIGHT);

      target.copyViewInfo(source, true);

      assertNull(target.getVizMark(), "the copy carries the source's state in both directions");
   }

   /** Write an info to XML and parse it into a fresh one. */
   private static TextVSAssemblyInfo roundTrip(VizMark mark) throws Exception {
      TextVSAssemblyInfo written = new TextVSAssemblyInfo();
      written.setVizMark(mark);

      TextVSAssemblyInfo parsed = new TextVSAssemblyInfo();
      parseInto(parsed, toXml(written));

      return parsed;
   }

   private static String toXml(VSAssemblyInfo info) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static void parseInto(VSAssemblyInfo info, String xml) throws Exception {
      Document doc = Tool.parseXML(new StringReader(xml));
      info.parseXML(doc.getDocumentElement());
   }
}
