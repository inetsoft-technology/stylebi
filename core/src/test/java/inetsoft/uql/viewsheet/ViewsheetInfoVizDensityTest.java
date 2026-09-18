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
import inetsoft.uql.viewsheet.internal.VSDensityDefaults;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

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
class ViewsheetInfoVizDensityTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void anUnsetSheetWritesNoAttribute() {
      assertFalse(writeXml(new ViewsheetInfo()).contains("vizDensity"),
                  "an existing file must stay byte-identical until an author sets it");
   }

   @Test
   void aSetSheetRoundTrips() throws Exception {
      ViewsheetInfo info = new ViewsheetInfo();
      info.setVizDensity("comfortable");

      assertEquals("comfortable", reparse(info).getVizDensity());
   }

   @Test
   void anAbsentAttributeParsesAsNull() throws Exception {
      assertNull(reparse(new ViewsheetInfo()).getVizDensity());
   }

   @Test
   void anEmptyValueClearsTheField() {
      ViewsheetInfo info = new ViewsheetInfo();
      info.setVizDensity("compact");
      info.setVizDensity("");

      assertNull(info.getVizDensity(), "empty means inherit, same as null");
   }

   @Test
   void theSheetValueOverridesTheOrg() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      ViewsheetInfo info = new ViewsheetInfo();
      info.setVizDensity("dense");

      assertEquals("dense", VSDensityDefaults.mode(info));
   }

   @Test
   void anUnsetSheetFallsBackToTheOrg() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");

      assertEquals("comfortable", VSDensityDefaults.mode(new ViewsheetInfo()));
   }

   @Test
   void aNullSheetFallsBackToTheOrg() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");

      assertEquals("comfortable", VSDensityDefaults.mode((ViewsheetInfo) null));
   }

   @Test
   void anUnsetSheetInAnUnsetOrgTakesTheShippedDefault() {
      assertEquals("compact", VSDensityDefaults.mode(new ViewsheetInfo()));
   }

   @Test
   void anUnrecognizedSheetValueClampsToDense() {
      ViewsheetInfo info = new ViewsheetInfo();
      info.setVizDensity("roomy");

      assertEquals("dense", VSDensityDefaults.mode(info));
   }

   private String writeXml(ViewsheetInfo info) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      info.writeXML(writer);
      writer.flush();
      return buffer.toString();
   }

   private ViewsheetInfo reparse(ViewsheetInfo info) throws Exception {
      Element elem = Tool.parseXML(new StringReader(writeXml(info))).getDocumentElement();
      ViewsheetInfo parsed = new ViewsheetInfo();
      parsed.parseXML(elem);
      return parsed;
   }
}
