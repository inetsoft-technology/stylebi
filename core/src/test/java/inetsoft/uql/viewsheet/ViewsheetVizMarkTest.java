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
package inetsoft.uql.viewsheet;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
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
class ViewsheetVizMarkTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   @Test
   void aNewSheetTakesTheGate() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(VizMark.MODERN_LIGHT, new Viewsheet().getVSAssemblyInfo().getVizMark());

      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(VizMark.MODERN_DARK, new Viewsheet().getVSAssemblyInfo().getVizMark());
   }

   @Test
   void aNewSheetUnderAClosedGateIsUnmarked() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertNull(new Viewsheet().getVSAssemblyInfo().getVizMark());
   }

   @Test
   void aLegacySheetLoadedUnderAnOpenGateStaysUnmarked() throws Exception {
      // the case the whole design turns on: opening old content in a modern product must not claim it
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      String legacy = toXml(new Viewsheet());

      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      Viewsheet loaded = parse(legacy);

      assertNull(loaded.getVSAssemblyInfo().getVizMark(),
                 "a sheet saved before the mark existed must load unmarked, gate or no gate");
   }

   @Test
   void aMarkedSheetLoadedUnderAClosedGateStaysMarked() throws Exception {
      // the mirror: the mark comes from the file, not from the live gate
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      String modern = toXml(new Viewsheet());

      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Viewsheet loaded = parse(modern);

      assertEquals(VizMark.MODERN_LIGHT, loaded.getVSAssemblyInfo().getVizMark());
   }

   @Test
   void aLegacyDashboardsSavedXmlHasNoVizMarkAnywhere() {
      // covers the whole sheet, not just its own info - no vizMark attribute anywhere in the file
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));

      String xml = toXml(vs);

      assertFalse(xml.contains("vizMark"),
                  "a legacy dashboard's saved XML must carry no vizMark attribute anywhere");
   }

   private static String toXml(Viewsheet vs) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      vs.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static Viewsheet parse(String xml) throws Exception {
      Document doc = Tool.parseXML(new StringReader(xml));
      Viewsheet vs = new Viewsheet();
      vs.parseXML(doc.getDocumentElement());
      return vs;
   }
}
