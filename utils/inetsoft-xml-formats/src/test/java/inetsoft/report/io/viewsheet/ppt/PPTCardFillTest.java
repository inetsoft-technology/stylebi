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
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
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
 * A text box's fill blends against white, so the card tint is flattened over the sheet's page
 * before a modern card is written.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PPTCardFillTest {
   private PPTVSExporter exporter;

   @BeforeEach
   void setUp() throws Exception {
      PPTContext context = Mockito.mock(PPTContext.class);
      Mockito.when(context.getSlideShow()).thenReturn(new XMLSlideShow());
      exporter = new PPTVSExporter(context, new ByteArrayOutputStream());
      Field f = PPTVSExporter.class.getDeclaredField("coordinator");
      f.setAccessible(true);
      f.set(exporter, new PPTCoordinateHelper());
   }

   @Test
   void aModernTableOnALightSheetTakesTheTintOverThePage() {
      Viewsheet vs = modernSheet();
      assertEquals("F8F7F4", hex(
         vs.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH).getBackground()), "modern page");
      Color bg = preparedBackground(vs);

      assertEquals("ECEBE9", hex(bg));
      assertEquals(255, bg.getAlpha());
   }

   @Test
   void aBlackSheetIsTheBackdrop() {
      Viewsheet vs = modernSheet();
      vs.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH)
         .getUserDefinedFormat().setBackgroundValue("#000000");
      Color bg = preparedBackground(vs);

      assertEquals("1A1A1A", hex(bg));
      assertEquals(255, bg.getAlpha());
   }

   private static Viewsheet modernSheet() {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      vs.getVSAssemblyInfo().initDefaultFormat();
      return vs;
   }

   private Color preparedBackground(Viewsheet vs) {
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.initDefaultFormat();
      vs.addAssembly(table);
      VSCompositeFormat format = table.getVSAssemblyInfo().getFormat();
      assertTrue(format.isDefaultCardTint(), "seeded tint");

      exporter.prepareAssembly(table);
      return format.getBackground();
   }

   private static String hex(Color color) {
      return String.format("%06X", color.getRGB() & 0xFFFFFF);
   }
}
