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
package inetsoft.analytic.composition;

import inetsoft.report.StyleConstants;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.uql.viewsheet.VSFormat;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

class VSCSSUtilTest {

   // -----------------------------------------------------------------------
   // getForegroundColor
   // -----------------------------------------------------------------------

   @Test
   void getForegroundColorWithRedReturnsHexString() {
      assertEquals("#ff0000", VSCSSUtil.getForegroundColor(Color.RED));
   }

   @Test
   void getForegroundColorWithBlueReturnsHexString() {
      assertEquals("#0000ff", VSCSSUtil.getForegroundColor(Color.BLUE));
   }

   @Test
   void getForegroundColorWithWhiteReturnsHexString() {
      assertEquals("#ffffff", VSCSSUtil.getForegroundColor(Color.WHITE));
   }

   @Test
   void getForegroundColorWithBlackReturnsHexString() {
      assertEquals("#000000", VSCSSUtil.getForegroundColor(Color.BLACK));
   }

   @Test
   void getForegroundColorWithNullReturnsEmptyString() {
      assertEquals("", VSCSSUtil.getForegroundColor(null));
   }

   @Test
   void getForegroundColorCustomColorReturnsLowercaseHex() {
      Color c = new Color(0x12, 0x34, 0x56);
      assertEquals("#123456", VSCSSUtil.getForegroundColor(c));
   }

   // -----------------------------------------------------------------------
   // getBackgroundColor
   // -----------------------------------------------------------------------

   @Test
   void getBackgroundColorWithGreenReturnsHexString() {
      assertEquals("#00ff00", VSCSSUtil.getBackgroundColor(Color.GREEN));
   }

   @Test
   void getBackgroundColorWithNullReturnsEmptyString() {
      assertEquals("", VSCSSUtil.getBackgroundColor(null));
   }

   @Test
   void getBackgroundColorWithBlackReturnsHexString() {
      assertEquals("#000000", VSCSSUtil.getBackgroundColor(Color.BLACK));
   }

   // -----------------------------------------------------------------------
   // getBackgroundRGBA(Color)
   // -----------------------------------------------------------------------

   @Test
   void getBackgroundRGBAWithNullReturnsEmptyString() {
      assertEquals("", VSCSSUtil.getBackgroundRGBA((Color) null));
   }

   @Test
   void getBackgroundRGBAWithFullyOpaqueColorReturnsRgbaString() {
      // Color with alpha=255: 255/255f = 1.0
      Color c = new Color(255, 0, 0, 255);
      String result = VSCSSUtil.getBackgroundRGBA(c);
      assertEquals("rgba(255,0,0,1.0)", result);
   }

   @Test
   void getBackgroundRGBAWithSemiTransparentColor() {
      // alpha = 128 => 128/255f
      Color c = new Color(0, 128, 64, 128);
      String result = VSCSSUtil.getBackgroundRGBA(c);
      assertTrue(result.startsWith("rgba(0,128,64,"));
   }

   @Test
   void getBackgroundRGBAWithZeroAlphaColorReturnsZeroAlpha() {
      // alpha = 0 stays as 0 (special case in code: if alpha != 0 then /255f, else keep 0)
      Color c = new Color(0, 0, 0, 0);
      String result = VSCSSUtil.getBackgroundRGBA(c);
      assertEquals("rgba(0,0,0,0.0)", result);
   }

   // -----------------------------------------------------------------------
   // getAlpha(XVSFormat)
   // -----------------------------------------------------------------------

   @Test
   void getAlphaReturns100PercentAsOne() {
      VSFormat fmt = new VSFormat();
      fmt.setAlpha(100);
      assertEquals(1.0f, VSCSSUtil.getAlpha(fmt), 0.0001f);
   }

   @Test
   void getAlphaReturns50PercentAsPointFive() {
      VSFormat fmt = new VSFormat();
      fmt.setAlpha(50);
      assertEquals(0.5f, VSCSSUtil.getAlpha(fmt), 0.0001f);
   }

   @Test
   void getAlphaReturns0PercentAsZero() {
      VSFormat fmt = new VSFormat();
      fmt.setAlpha(0);
      assertEquals(0.0f, VSCSSUtil.getAlpha(fmt), 0.0001f);
   }

   // -----------------------------------------------------------------------
   // getBorderStyle(int)
   // -----------------------------------------------------------------------

   @Test
   void getBorderStyleThinLineReturnsSolid() {
      assertEquals("solid ", VSCSSUtil.getBorderStyle(StyleConstants.THIN_LINE));
   }

   @Test
   void getBorderStyleMediumLineReturnsSolid() {
      assertEquals("solid ", VSCSSUtil.getBorderStyle(StyleConstants.MEDIUM_LINE));
   }

   @Test
   void getBorderStyleThickLineReturnsSolid() {
      assertEquals("solid ", VSCSSUtil.getBorderStyle(StyleConstants.THICK_LINE));
   }

   @Test
   void getBorderStyleDoubleLineReturnsDouble() {
      assertEquals("double ", VSCSSUtil.getBorderStyle(StyleConstants.DOUBLE_LINE));
   }

   @Test
   void getBorderStyleRaised3DReturnsRidge() {
      assertEquals("ridge ", VSCSSUtil.getBorderStyle(StyleConstants.RAISED_3D));
   }

   @Test
   void getBorderStyleLowered3DReturnsGroove() {
      assertEquals("groove ", VSCSSUtil.getBorderStyle(StyleConstants.LOWERED_3D));
   }

   @Test
   void getBorderStyleDouble3DRaisedReturnsOutset() {
      assertEquals("outset ", VSCSSUtil.getBorderStyle(StyleConstants.DOUBLE_3D_RAISED));
   }

   @Test
   void getBorderStyleDouble3DLoweredReturnsInset() {
      assertEquals("inset ", VSCSSUtil.getBorderStyle(StyleConstants.DOUBLE_3D_LOWERED));
   }

   @Test
   void getBorderStyleDotLineReturnsDotted() {
      assertEquals("dotted ", VSCSSUtil.getBorderStyle(StyleConstants.DOT_LINE));
   }

   @Test
   void getBorderStyleDashLineReturnsDashed() {
      assertEquals("dashed ", VSCSSUtil.getBorderStyle(StyleConstants.DASH_LINE));
   }

   @Test
   void getBorderStyleMediumDashReturnsDashed() {
      assertEquals("dashed ", VSCSSUtil.getBorderStyle(StyleConstants.MEDIUM_DASH));
   }

   @Test
   void getBorderStyleLargeDashReturnsDashed() {
      assertEquals("dashed ", VSCSSUtil.getBorderStyle(StyleConstants.LARGE_DASH));
   }

   @Test
   void getBorderStyleNoBorderReturnsNone() {
      assertEquals("none ", VSCSSUtil.getBorderStyle(StyleConstants.NO_BORDER));
   }

   @Test
   void getBorderStyleUnknownValueReturnsSolid() {
      // default case
      assertEquals("solid ", VSCSSUtil.getBorderStyle(9999));
   }

   // -----------------------------------------------------------------------
   // getBorder(XVSFormat, String)
   // -----------------------------------------------------------------------

   @Test
   void getBorderWithNullBordersReturnsNull() {
      VSFormat fmt = new VSFormat();
      // fmt.getBorders() returns null by default
      assertNull(VSCSSUtil.getBorder(fmt, "top"));
   }

   @Test
   void getBorderTopReturnsStringWithStyleAndColor() {
      VSFormat fmt = new VSFormat();
      Insets borders = new Insets(
         StyleConstants.THIN_LINE,
         StyleConstants.THIN_LINE,
         StyleConstants.THIN_LINE,
         StyleConstants.THIN_LINE);
      fmt.setBorders(borders);
      Color black = Color.BLACK;
      fmt.setBorderColors(new BorderColors(black, black, black, black));

      String result = VSCSSUtil.getBorder(fmt, "top");
      assertNotNull(result);
      assertTrue(result.contains("solid"));
   }

   @Test
   void getBorderBottomReturnsStringWithStyleAndColor() {
      VSFormat fmt = new VSFormat();
      Insets borders = new Insets(
         StyleConstants.THIN_LINE,
         StyleConstants.THIN_LINE,
         StyleConstants.THIN_LINE,
         StyleConstants.THIN_LINE);
      fmt.setBorders(borders);
      fmt.setBorderColors(new BorderColors(Color.BLACK, Color.RED, Color.BLACK, Color.BLACK));

      String result = VSCSSUtil.getBorder(fmt, "bottom");
      assertNotNull(result);
      // bottom color is red => should contain "ff0000"
      assertTrue(result.contains("ff0000"));
   }

   @Test
   void getBorderLeftReturnsStringWithStyleAndColor() {
      VSFormat fmt = new VSFormat();
      Insets borders = new Insets(
         StyleConstants.THIN_LINE,
         StyleConstants.THIN_LINE,
         StyleConstants.THIN_LINE,
         StyleConstants.THIN_LINE);
      fmt.setBorders(borders);
      fmt.setBorderColors(new BorderColors(Color.BLACK, Color.BLACK, Color.BLUE, Color.BLACK));

      String result = VSCSSUtil.getBorder(fmt, "left");
      assertNotNull(result);
      // left color is blue => "0000ff"
      assertTrue(result.contains("0000ff"));
   }

   @Test
   void getBorderRightReturnsStringWithStyleAndColor() {
      VSFormat fmt = new VSFormat();
      Insets borders = new Insets(
         StyleConstants.THIN_LINE,
         StyleConstants.THIN_LINE,
         StyleConstants.THIN_LINE,
         StyleConstants.THIN_LINE);
      fmt.setBorders(borders);
      fmt.setBorderColors(new BorderColors(Color.BLACK, Color.BLACK, Color.BLACK, Color.GREEN));

      String result = VSCSSUtil.getBorder(fmt, "right");
      assertNotNull(result);
      // right color is green => "00ff00"
      assertTrue(result.contains("00ff00"));
   }

   @Test
   void getBorderWithNullBorderColorsReturnsEmptyString() {
      VSFormat fmt = new VSFormat();
      Insets borders = new Insets(
         StyleConstants.THIN_LINE,
         StyleConstants.THIN_LINE,
         StyleConstants.THIN_LINE,
         StyleConstants.THIN_LINE);
      fmt.setBorders(borders);
      // No border colors set

      String result = VSCSSUtil.getBorder(fmt, "top");
      assertEquals("", result);
   }

   // -----------------------------------------------------------------------
   // gethAlign / getvAlign
   // -----------------------------------------------------------------------

   @Test
   void gethAlignHLeft() {
      VSFormat fmt = new VSFormat();
      fmt.setAlignment(StyleConstants.H_LEFT);
      assertEquals("left", VSCSSUtil.gethAlign(fmt));
   }

   @Test
   void gethAlignHCenter() {
      VSFormat fmt = new VSFormat();
      fmt.setAlignment(StyleConstants.H_CENTER);
      assertEquals("center", VSCSSUtil.gethAlign(fmt));
   }

   @Test
   void gethAlignHRight() {
      VSFormat fmt = new VSFormat();
      fmt.setAlignment(StyleConstants.H_RIGHT);
      assertEquals("right", VSCSSUtil.gethAlign(fmt));
   }

   @Test
   void gethAlignHCurrencyIsStrippedByVSFormat() {
      // VSFormat.fixAlignment() only preserves H_LEFT|H_CENTER|H_RIGHT and vertical bits.
      // H_CURRENCY (128) is stripped to 0, so gethAlign returns "" (no recognised alignment).
      VSFormat fmt = new VSFormat();
      fmt.setAlignment(StyleConstants.H_CURRENCY);
      assertEquals("", VSCSSUtil.gethAlign(fmt));
   }

   @Test
   void getvAlignVTop() {
      VSFormat fmt = new VSFormat();
      fmt.setAlignment(StyleConstants.V_TOP);
      assertEquals("top", VSCSSUtil.getvAlign(fmt));
   }

   @Test
   void getvAlignVCenter() {
      VSFormat fmt = new VSFormat();
      fmt.setAlignment(StyleConstants.V_CENTER);
      assertEquals("middle", VSCSSUtil.getvAlign(fmt));
   }

   @Test
   void getvAlignVBottom() {
      VSFormat fmt = new VSFormat();
      fmt.setAlignment(StyleConstants.V_BOTTOM);
      assertEquals("bottom", VSCSSUtil.getvAlign(fmt));
   }

   @Test
   void getvAlignNoAlignmentReturnsBaseline() {
      VSFormat fmt = new VSFormat();
      fmt.setAlignment(0);
      // no V_ bits set => returns "baseline"
      assertEquals("baseline", VSCSSUtil.getvAlign(fmt));
   }

   // -----------------------------------------------------------------------
   // getForeground / getBackground via XVSFormat
   // -----------------------------------------------------------------------

   @Test
   void getForegroundViaFormatWithColorReturnsHexString() {
      VSFormat fmt = new VSFormat();
      fmt.setForeground(Color.RED);
      assertEquals("#ff0000", VSCSSUtil.getForeground(fmt));
   }

   @Test
   void getBackgroundViaFormatWithColorReturnsHexString() {
      VSFormat fmt = new VSFormat();
      fmt.setBackground(Color.BLUE);
      assertEquals("#0000ff", VSCSSUtil.getBackground(fmt));
   }
}
