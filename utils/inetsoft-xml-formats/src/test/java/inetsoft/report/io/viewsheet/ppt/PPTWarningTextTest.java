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
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #79684: the query row-limit warning written to PowerPoint was cut off at the end of its
 * fixed 220pt box. The box must be at least as wide as the text.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PPTWarningTextTest {
   private XMLSlideShow show;
   private XSLFSlide slide;
   private PPTVSExporter exporter;

   @BeforeEach
   void setUp() throws Exception {
      show = new XMLSlideShow();
      slide = show.createSlide();
      PPTContext context = Mockito.mock(PPTContext.class);
      Mockito.when(context.getSlideShow()).thenReturn(show);
      exporter = new PPTVSExporter(context, new ByteArrayOutputStream());
      PPTCoordinateHelper coordinator = new PPTCoordinateHelper();
      coordinator.setViewsheet(new Viewsheet());
      set("slide", slide);
      set("coordinator", coordinator);
   }

   @Test
   void longWarningIsNotCutOff() {
      String warning = "The length of the table has been truncated to 7 rows of data.";
      VSCompositeFormat format = new VSCompositeFormat();
      format.getUserDefinedFormat().setForeground(Color.RED);
      // a wide font stands in for fonts whose metrics make the message longer than 220pt
      format.getUserDefinedFormat().setFont(new Font("Dialog", Font.PLAIN, 20));
      exporter.writeWarningText(new Assembly[0], warning, format);

      XSLFTextShape box = (XSLFTextShape) slide.getShapes().get(0);
      assertEquals(warning, box.getText());
      assertTrue(box.getAnchor().getWidth() > 220,
                 "box should grow past 220pt, was " + box.getAnchor().getWidth());
   }

   @Test
   void shortWarningKeepsDefaultSize() {
      exporter.writeWarningText(new Assembly[0], "Short", new VSCompositeFormat());
      XSLFTextShape box = (XSLFTextShape) slide.getShapes().get(0);
      assertEquals("Short", box.getText());
      assertEquals(220, box.getAnchor().getWidth(), 0.5);
   }

   private void set(String name, Object value) throws Exception {
      Field f = PPTVSExporter.class.getDeclaredField(name);
      f.setAccessible(true);
      f.set(exporter, value);
   }
}
