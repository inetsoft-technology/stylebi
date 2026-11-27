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
import inetsoft.uql.viewsheet.VSAssembly;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82418: the per-assembly Max Rows of a freehand (calc) table.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalcTableVSAssemblyInfoMaxRowsTest {
   @Test
   void defaultIsUnlimitedAndNotWritten() throws Exception {
      CalcTableVSAssemblyInfo info = new CalcTableVSAssemblyInfo();

      assertEquals(0, info.getMaxRows());
      assertFalse(toXml(info).contains("maxRow="));
      assertEquals(0, roundTrip(info).getMaxRows());
   }

   @Test
   void maxRowsSurvivesXmlRoundTrip() throws Exception {
      CalcTableVSAssemblyInfo info = new CalcTableVSAssemblyInfo();
      info.setMaxRows(25);

      assertTrue(toXml(info).contains("maxRow=\"25\""));
      assertEquals(25, roundTrip(info).getMaxRows());
   }

   @Test
   void deepCopyCarriesMaxRowsAndReportsInputDataChanged() {
      CalcTableVSAssemblyInfo source = new CalcTableVSAssemblyInfo();
      source.setMaxRows(7);
      CalcTableVSAssemblyInfo target = new CalcTableVSAssemblyInfo();

      int hint = target.copyInfo(source, true);

      assertEquals(7, target.getMaxRows());
      assertTrue((hint & VSAssembly.INPUT_DATA_CHANGED) != 0);
      assertEquals(0, target.copyInfo(source, true) & VSAssembly.INPUT_DATA_CHANGED);
   }

   private static String toXml(CalcTableVSAssemblyInfo info) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      info.writeXML(pw);
      pw.flush();
      return sw.toString().trim();
   }

   private static CalcTableVSAssemblyInfo roundTrip(CalcTableVSAssemblyInfo info)
      throws Exception
   {
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new InputSource(new StringReader(toXml(info))));
      CalcTableVSAssemblyInfo restored = new CalcTableVSAssemblyInfo();
      restored.parseXML(doc.getDocumentElement());
      return restored;
   }
}
