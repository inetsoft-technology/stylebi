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
package inetsoft.uql.viewsheet.graph;

import inetsoft.util.Tool;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
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
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PlotDescriptorTextLayoutTest {

   @Test
   void textLayoutRoundTripsXml() throws Exception {
      PlotDescriptor desc = new PlotDescriptor();
      TextLayout layout = new TextLayout();
      TextLayoutRow row = new TextLayoutRow();
      row.addItem(TextLayoutItem.ofField(0));
      row.addItem(TextLayoutItem.ofStatic(": "));
      row.addItem(TextLayoutItem.ofField(1));
      layout.addRow(row);
      desc.setTextLayout(layout);

      PlotDescriptor parsed = roundTrip(desc);

      assertEquals(layout, parsed.getTextLayout());
   }

   @Test
   void absentTextLayoutDeserializesNull() throws Exception {
      PlotDescriptor desc = new PlotDescriptor();
      // textLayout is not set — must survive round-trip as null
      PlotDescriptor parsed = roundTrip(desc);
      assertNull(parsed.getTextLayout());
   }

   @Test
   void cloneDeepCopiesTextLayout() {
      PlotDescriptor desc = new PlotDescriptor();
      TextLayout layout = new TextLayout();
      TextLayoutRow row = new TextLayoutRow();
      row.addItem(TextLayoutItem.ofField(0));
      layout.addRow(row);
      desc.setTextLayout(layout);

      PlotDescriptor copy = (PlotDescriptor) desc.clone();
      copy.getTextLayout().getRows().clear();

      assertEquals(1, desc.getTextLayout().getRows().size(),
         "original layout must not be affected by clone mutation");
   }

   @Test
   void equalsContentReflectsTextLayout() {
      PlotDescriptor a = new PlotDescriptor();
      PlotDescriptor b = new PlotDescriptor();

      TextLayout layout = new TextLayout();
      TextLayoutRow row = new TextLayoutRow();
      row.addItem(TextLayoutItem.ofField(0));
      layout.addRow(row);
      a.setTextLayout(layout);

      assertFalse(a.equalsContent(b), "differs when one has textLayout and other does not");

      b.setTextLayout(layout.clone());
      assertTrue(a.equalsContent(b), "equal when both have equal textLayouts");
   }

   private static PlotDescriptor roundTrip(PlotDescriptor source) throws Exception {
      StringWriter sw = new StringWriter();
      try(PrintWriter pw = new PrintWriter(sw)) {
         source.writeXML(pw);
      }
      Document doc = Tool.parseXML(new StringReader(sw.toString()));
      PlotDescriptor parsed = new PlotDescriptor();
      parsed.parseXML(doc.getDocumentElement());
      return parsed;
   }
}
