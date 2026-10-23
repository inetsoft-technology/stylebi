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
package inetsoft.report.io.viewsheet.ppt;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #78672: a Text component's padding must reach the PowerPoint text box as insets (in
 * points, converted from pixels like the bounds), not be dropped in favor of the 1pt default.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PPTTextPaddingTest {
   private static final double EMU = 12700;

   private XMLSlideShow show;
   private XSLFSlide slide;
   private PPTVSExporter exporter;
   private Viewsheet vs;

   @BeforeEach
   void setUp() throws Exception {
      show = new XMLSlideShow();
      slide = show.createSlide();
      PPTContext context = Mockito.mock(PPTContext.class);
      Mockito.when(context.getSlideShow()).thenReturn(show);
      exporter = new PPTVSExporter(context, new ByteArrayOutputStream());
      vs = new Viewsheet();
      PPTCoordinateHelper coordinator = new PPTCoordinateHelper();
      coordinator.setViewsheet(vs);
      set("slide", slide);
      set("coordinator", coordinator);
   }

   @Test
   void topPaddingBecomesTopInsetInPoints() throws Exception {
      assertInsets(new Insets(40, 0, 0, 0), 30, 0, 0, 0);
   }

   @Test
   void uniformPaddingBecomesInsetsInPoints() throws Exception {
      assertInsets(new Insets(10, 10, 10, 10), 7.5, 7.5, 7.5, 7.5);
   }

   @Test
   void noPaddingKeepsOnePointInsets() throws Exception {
      assertInsets(new Insets(0, 0, 0, 0), 1, 1, 1, 1);
   }

   private void assertInsets(Insets padding, double top, double left, double bottom,
                             double right) throws Exception
   {
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      TextVSAssemblyInfo info = (TextVSAssemblyInfo) text.getVSAssemblyInfo();
      info.setPadding(padding);
      info.setPixelSize(new Dimension(300, 80));
      vs.addAssembly(text);

      exporter.writeText(text, "hello");

      String xml = slide.getXmlObject().toString();
      assertAttr(xml, "tIns", top);
      assertAttr(xml, "lIns", left);
      assertAttr(xml, "bIns", bottom);
      assertAttr(xml, "rIns", right);
   }

   private static void assertAttr(String xml, String name, double points) {
      assertEquals(true, xml.contains(name + "=\"" + Math.round(points * EMU) + "\""),
                   name + " should be " + points + "pt in " + xml);
   }

   private void set(String name, Object value) throws Exception {
      Field f = PPTVSExporter.class.getDeclaredField(name);
      f.setAccessible(true);
      f.set(exporter, value);
   }
}
