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
package inetsoft.web.wiz.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class MarkdownPlainTextTest {
   @Test
   void stripsHeaders() {
      assertEquals("Title\nBody text", MarkdownPlainText.strip("# Title\nBody text"));
      assertEquals("Subheading", MarkdownPlainText.strip("### Subheading"));
   }

   @Test
   void stripsBoldAndItalicMarkers() {
      assertEquals("This is bold text", MarkdownPlainText.strip("This is **bold** text"));
      assertEquals("This is bold text", MarkdownPlainText.strip("This is __bold__ text"));
      assertEquals("This is italic text", MarkdownPlainText.strip("This is *italic* text"));
      assertEquals("This is italic text", MarkdownPlainText.strip("This is _italic_ text"));
   }

   @Test
   void normalizesBulletsToDotMarker() {
      assertEquals("• item one\n• item two", MarkdownPlainText.strip("- item one\n* item two"));
   }

   @Test
   void preservesBlankLinesAsParagraphBreaks() {
      assertEquals("Para one\n\nPara two", MarkdownPlainText.strip("Para one\n\nPara two"));
   }

   @Test
   void nullInputReturnsEmptyString() {
      assertEquals("", MarkdownPlainText.strip(null));
   }

   @Test
   void malformedMarkdownNeverThrows() {
      assertDoesNotThrow(() -> MarkdownPlainText.strip("**unclosed bold"));
      assertDoesNotThrow(() -> MarkdownPlainText.strip("# "));
      assertDoesNotThrow(() -> MarkdownPlainText.strip("***"));
      assertDoesNotThrow(() -> MarkdownPlainText.strip("- \n- \n-"));
   }
}
