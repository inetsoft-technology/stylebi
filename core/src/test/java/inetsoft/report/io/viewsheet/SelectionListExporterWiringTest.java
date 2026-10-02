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
package inetsoft.report.io.viewsheet;

import inetsoft.report.io.ArabicTextUtil;
import inetsoft.report.io.viewsheet.html.HTMLVSExporter;
import inetsoft.report.io.viewsheet.pdf.PDFVSExporter;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.awt.*;
import java.io.ByteArrayOutputStream;

import static org.mockito.Mockito.*;

/**
 * Each card format lays a selection list's rows out against its own card inset. The list helper
 * resolves the inset through the exporter it is handed, so an exporter that builds the helper
 * without handing itself over leaves every list flush to its border while trees inset. SVG is
 * not here: its exporter needs Batik, which only the xml-formats module carries.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionListExporterWiringTest {
   // the PDF printer reshapes every string through ArabicTextUtil, whose only implementation lives
   // in the xml-formats module; a mock that finds no Arabic leaves the text as it is
   @BeforeAll
   static void stubArabicText() {
      ReflectionTestUtils.setField(ArabicTextUtil.class, "instance", mock(ArabicTextUtil.class));
   }

   @AfterAll
   static void restoreArabicText() {
      ReflectionTestUtils.setField(ArabicTextUtil.class, "instance", null);
   }

   @Test
   void pdfResolvesAListsInsetThroughItself() {
      assertListResolvesItsInset(
         spy(new PDFVSExporter(null, null, null, null, new ByteArrayOutputStream())));
   }

   @Test
   void htmlResolvesAListsInsetThroughItself() {
      assertListResolvesItsInset(spy(new HTMLVSExporter(new ByteArrayOutputStream())));
   }

   private static void assertListResolvesItsInset(AbstractVSExporter exporter) {
      SelectionListVSAssembly list = markedList();

      exporter.prepareAssembly(list);
      exporter.writeSelectionList(list);

      verify(exporter, atLeastOnce()).getSelectionCardInset(list.getVSAssemblyInfo());
   }

   // a comfortable list after its seed: 132 x 202 with a 16px inset and five values
   private static SelectionListVSAssembly markedList() {
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "SelectionList1");
      vs.addAssembly(list);
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) list.getVSAssemblyInfo();
      info.setPixelOffset(new Point(40, 60));
      info.setPixelSize(new Dimension(132, 202));
      info.setPadding(new Insets(16, 16, 16, 16));
      info.setTitleVisibleValue(true);
      SelectionList values = new SelectionList();

      for(String label : new String[] { "Business", "Educational", "Games", "Graphics", "Hardware" }) {
         SelectionValue value = new SelectionValue(label, label);
         value.setFormat(new VSCompositeFormat());
         values.addSelectionValue(value);
      }

      info.setSelectionList(values);
      return list;
   }
}
