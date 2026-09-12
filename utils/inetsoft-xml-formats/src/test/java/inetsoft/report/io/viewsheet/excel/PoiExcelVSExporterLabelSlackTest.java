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
package inetsoft.report.io.viewsheet.excel;

import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PoiExcelVSExporter#getExcelFontScale(Font)}, which drives the extra width a
 * LEFT input label is given because the workbook carries the font in points and Excel lays
 * those out at 96 dpi, so the label draws wider than the pixel-based split reserved for it.
 *
 * <p>Only the scale is exercised here, not getExcelLabelWidthSlack(): measuring text goes
 * through Common.stringWidth(), which reads SreeEnv properties and so needs a bootstrapped
 * server this module's tests do not have (the same reason {@link PoiExcelVSExporterTest} mocks
 * its assembly infos).</p>
 */
class PoiExcelVSExporterLabelSlackTest {
   private static Font font(int pixelSize) {
      return new Font("Dialog", Font.PLAIN, pixelSize);
   }

   @Test
   void largeFontIsDrawnWiderThanItsPixelSize() {
      assertTrue(PoiExcelVSExporter.getExcelFontScale(font(24)) > 1,
         "a 24px label goes into the workbook as points and is drawn back at 96 dpi");
   }

   /**
    * translateFontStyle() raises anything under 9pt to 9pt when isAdjust is set, which is how
    * applyFormat() writes the label font. Without modelling that floor a small label gets no
    * compensation at all while Excel draws it half again as wide, and with word wrap off it
    * runs straight into the widget.
    */
   @Test
   void smallFontIsClampedToTheMinimumPointSize() {
      // 8px -> 7pt by the px-to-pt rate, floored to 9pt, drawn at 12px
      assertEquals(12.0 / 8, PoiExcelVSExporter.getExcelFontScale(font(8)), 0.001);
      assertTrue(PoiExcelVSExporter.getExcelFontScale(font(8)) >
                    PoiExcelVSExporter.getExcelFontScale(font(24)),
         "the floor hits small fonts hardest, so they need the most compensation");
   }

   @Test
   void degenerateFontsNeedNoCompensation() {
      assertEquals(1, PoiExcelVSExporter.getExcelFontScale(null), 0.001);
      assertEquals(1, PoiExcelVSExporter.getExcelFontScale(font(0)), 0.001);
   }

   @Test
   void scaleIsNeverBelowOne() {
      for(int size = 1; size <= 96; size++) {
         assertTrue(PoiExcelVSExporter.getExcelFontScale(font(size)) >= 1,
            "size " + size + " must never shrink the reserved label width");
      }
   }
}
