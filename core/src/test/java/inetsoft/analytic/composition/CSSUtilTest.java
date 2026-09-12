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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

class CSSUtilTest {

   // -----------------------------------------------------------------------
   // getCSS — null format
   // -----------------------------------------------------------------------

   @Test
   void getCSSWithNullFormatReturnsNull() {
      assertNull(CSSUtil.getCSS(null));
   }

   // -----------------------------------------------------------------------
   // getCSS — produces a non-null ObjectNode for a fresh format
   // -----------------------------------------------------------------------

   @Test
   void getCSSWithEmptyFormatReturnsNonNullNode() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      ObjectNode css = CSSUtil.getCSS(fmt);
      assertNotNull(css);
   }

   @Test
   void getCSSContainsOpacityKey() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      ObjectNode css = CSSUtil.getCSS(fmt);
      // getBackgroundAlpha always sets "opacity" to ""
      assertTrue(css.has("opacity"));
      assertEquals("", css.get("opacity").asText());
   }

   // -----------------------------------------------------------------------
   // getBackground
   // -----------------------------------------------------------------------

   @Test
   void getBackgroundWithColorSetsRGBABackgroundKey() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      // Default alpha is 100, so alpha = 100/100f = 1.0
      fmt.getUserDefinedFormat().setBackground(new Color(255, 0, 0));

      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getBackground(css, fmt);

      assertTrue(css.has("background"));
      // alpha = 100 / 100f = 1.0; color = red (255, 0, 0)
      assertEquals("rgba(255,0,0,1.0)", css.get("background").asText());
   }

   @Test
   void getBackgroundWithNoColorDoesNotSetBackgroundKey() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getBackground(css, fmt);
      assertFalse(css.has("background"));
   }

   // -----------------------------------------------------------------------
   // getForeground
   // -----------------------------------------------------------------------

   @Test
   void getForegroundWithColorSetsColorKey() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setForeground(new Color(0, 0, 255));

      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getForeground(css, fmt);

      assertTrue(css.has("color"));
      assertEquals("#0000ff", css.get("color").asText());
   }

   @Test
   void getForegroundDefaultFormatSetsBlackColor() {
      // VSFormat defaults fg = Color.BLACK and fgval = DynamicValue("0", COLOR),
      // so a fresh VSCompositeFormat always has foreground = black.
      VSCompositeFormat fmt = new VSCompositeFormat();
      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getForeground(css, fmt);
      assertTrue(css.has("color"));
      assertEquals("#000000", css.get("color").asText());
   }

   @Test
   void getForegroundRedColorHTMLString() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setForeground(Color.RED);

      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getForeground(css, fmt);

      assertEquals("#ff0000", css.get("color").asText());
   }

   // -----------------------------------------------------------------------
   // getFontStyle
   // -----------------------------------------------------------------------

   @Test
   void getFontStyleWithNullFontDoesNothing() {
      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getFontStyle(css, (Font) null);
      assertFalse(css.has("font-family"));
   }

   @Test
   void getFontStyleBoldFont() {
      Font font = new Font("Arial", Font.BOLD, 12);
      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getFontStyle(css, font);

      assertEquals("bold", css.get("font-weight").asText());
      assertEquals("normal", css.get("font-style").asText());
   }

   @Test
   void getFontStyleItalicFont() {
      Font font = new Font("Arial", Font.ITALIC, 12);
      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getFontStyle(css, font);

      assertEquals("normal", css.get("font-weight").asText());
      assertEquals("italic", css.get("font-style").asText());
   }

   @Test
   void getFontStyleBoldItalicFont() {
      Font font = new Font("Arial", Font.BOLD | Font.ITALIC, 12);
      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getFontStyle(css, font);

      assertEquals("bold", css.get("font-weight").asText());
      assertEquals("italic", css.get("font-style").asText());
   }

   @Test
   void getFontStylePlainFont() {
      Font font = new Font("Arial", Font.PLAIN, 14);
      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getFontStyle(css, font);

      assertEquals("normal", css.get("font-weight").asText());
      assertEquals("normal", css.get("font-style").asText());
      assertEquals("14px", css.get("font-size").asText());
   }

   @Test
   void getFontStyleUnderlineStyleFont() {
      // StyleFont.UNDERLINE = 0x10
      Font font = new StyleFont("Arial", StyleFont.UNDERLINE, 12);
      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getFontStyle(css, font);

      assertTrue(css.has("text-decoration"));
      assertTrue(css.get("text-decoration").asText().contains("underline"));
   }

   @Test
   void getFontStyleStrikethroughFont() {
      // StyleFont.STRIKETHROUGH = 0x20
      Font font = new StyleFont("Arial", StyleFont.STRIKETHROUGH, 12);
      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getFontStyle(css, font);

      assertTrue(css.has("text-decoration"));
      assertTrue(css.get("text-decoration").asText().contains("line-through"));
   }

   @Test
   void getFontStyleUnderlineAndStrikethroughFont() {
      Font font = new StyleFont("Arial", StyleFont.UNDERLINE | StyleFont.STRIKETHROUGH, 12);
      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getFontStyle(css, font);

      assertTrue(css.has("text-decoration"));
      String decoration = css.get("text-decoration").asText();
      assertTrue(decoration.contains("underline"));
      assertTrue(decoration.contains("line-through"));
   }

   @Test
   void getFontStyleNoDecorationDoesNotSetTextDecoration() {
      Font font = new Font("Arial", Font.PLAIN, 12);
      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getFontStyle(css, font);

      assertFalse(css.has("text-decoration"));
   }

   @Test
   void getFontStyleFontSizeInPixels() {
      Font font = new Font("Arial", Font.PLAIN, 18);
      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getFontStyle(css, font);

      assertEquals("18px", css.get("font-size").asText());
   }

   // -----------------------------------------------------------------------
   // getAlignment
   // -----------------------------------------------------------------------

   @Test
   void getAlignmentHCenter() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setAlignment(StyleConstants.H_CENTER);

      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getAlignment(css, fmt);

      assertEquals("center", css.get("text-align").asText());
   }

   @Test
   void getAlignmentHLeft() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setAlignment(StyleConstants.H_LEFT);

      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getAlignment(css, fmt);

      assertEquals("left", css.get("text-align").asText());
   }

   @Test
   void getAlignmentHRight() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setAlignment(StyleConstants.H_RIGHT);

      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getAlignment(css, fmt);

      assertEquals("right", css.get("text-align").asText());
   }

   @Test
   void getAlignmentHCurrencyIsStrippedByVSFormat() {
      // VSFormat.fixAlignment() only preserves H_LEFT|H_CENTER|H_RIGHT and vertical bits.
      // H_CURRENCY (128) is stripped to 0, so no text-align is produced.
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setAlignment(StyleConstants.H_CURRENCY);

      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getAlignment(css, fmt);

      assertNull(css.get("text-align"));
   }

   @Test
   void getAlignmentVTop() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setAlignment(StyleConstants.V_TOP);

      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getAlignment(css, fmt);

      assertEquals("top", css.get("vertical-align").asText());
   }

   @Test
   void getAlignmentVCenter() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setAlignment(StyleConstants.V_CENTER);

      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getAlignment(css, fmt);

      assertEquals("middle", css.get("vertical-align").asText());
   }

   @Test
   void getAlignmentVBottom() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setAlignment(StyleConstants.V_BOTTOM);

      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getAlignment(css, fmt);

      assertEquals("bottom", css.get("vertical-align").asText());
   }

   @Test
   void getAlignmentCombinedHAndV() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setAlignment(StyleConstants.H_CENTER | StyleConstants.V_TOP);

      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getAlignment(css, fmt);

      assertEquals("center", css.get("text-align").asText());
      assertEquals("top", css.get("vertical-align").asText());
   }

   // -----------------------------------------------------------------------
   // getWrapping
   // -----------------------------------------------------------------------

   @Test
   void getWrappingTrueSetsWordWrapKeys() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setWrapping(true);

      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getWrapping(css, fmt);

      assertEquals("normal", css.get("white-space").asText());
      assertEquals("break-word", css.get("word-wrap").asText());
      assertEquals("hidden", css.get("overflow").asText());
   }

   @Test
   void getWrappingFalseSetsNoWrap() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setWrapping(false);

      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getWrapping(css, fmt);

      assertEquals("nowrap", css.get("white-space").asText());
   }

   // -----------------------------------------------------------------------
   // getBackgroundAlpha
   // -----------------------------------------------------------------------

   @Test
   void getBackgroundAlphaSetsOpacityToEmptyString() {
      VSCompositeFormat fmt = new VSCompositeFormat();
      ObjectNode css = new ObjectMapper().createObjectNode();
      CSSUtil.getBackgroundAlpha(css, fmt);
      assertEquals("", css.get("opacity").asText());
   }
}
