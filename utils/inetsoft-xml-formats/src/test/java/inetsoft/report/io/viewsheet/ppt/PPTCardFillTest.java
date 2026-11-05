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
import inetsoft.uql.viewsheet.internal.VSTableStructureDefaults;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A text box's fill blends against white, so the card tint is flattened over the sheet's page
 * before a modern card is written, and a modern cell's translucent fill over that card.
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

   // a text box's fill blends against white, so a translucent stripe is flattened over the card

   @Test
   void aLightModernZebraRowIsFlattenedOverTheCard() {
      assertEquals("E2E1E0", zebraFill(VizMark.MODERN_LIGHT), "the stripe over ECEBE9");
   }

   @Test
   void aDarkModernZebraRowIsFlattenedOverTheDarkCard() {
      assertEquals("37363A", zebraFill(VizMark.MODERN_DARK), "the stripe over 313034");
   }

   @Test
   void aLegacyTranslucentCellFillBlendsAgainstWhiteAsBefore() {
      assertEquals("F2F2F2", zebraFill(null));
   }

   /** One body row carrying the zebra stripe as the table lens resolves it. */
   private String zebraFill(VizMark mark) {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setVizMark(mark);
      vs.getVSAssemblyInfo().initDefaultFormat();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.initDefaultFormat();
      vs.addAssembly(table);
      exporter.prepareAssembly(table);

      Color zebra = VSTableStructureDefaults.zebraBackground(
         VizContext.of(mark == null ? VizMark.MODERN_LIGHT : mark));
      VSCompositeFormat cell = new VSCompositeFormat();
      cell.getUserDefinedFormat().setBackground(zebra);
      cell.getUserDefinedFormat().setAlpha(Math.round(zebra.getAlpha() * 100 / 255f));
      assertEquals(zebra.getAlpha(), cell.getBackground().getAlpha(), "precondition: the stripe");

      XSLFSlide slide = new XMLSlideShow().createSlide();
      PPTTableHelper helper = new PPTTableHelper(slide, new PPTCoordinateHelper(), vs, table);
      helper.writeTableCell(0, 0, new Dimension(1, 1), new Rectangle2D.Double(0, 0, 80, 20),
                            0, 0, cell, "x", "x", null, table.getVSAssemblyInfo().getFormat(),
                            new Rectangle(0, 0, 1, 1), null);

      XSLFTextBox box = (XSLFTextBox) slide.getShapes().get(0);
      return hex(box.getFillColor());
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
