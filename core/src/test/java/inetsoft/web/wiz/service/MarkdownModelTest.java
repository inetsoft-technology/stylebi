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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class MarkdownModelTest {
   @Test
   void classifiesHeadingsBulletsAndParagraphs() {
      List<MarkdownModel.Block> blocks = MarkdownModel.parse(
         "## Premium share\n\nPremium items carry the business.\n- point one\n- point two");

      assertEquals(4, blocks.size());
      assertEquals(MarkdownModel.BlockType.HEADING, blocks.get(0).type());
      assertEquals(2, blocks.get(0).level());
      assertEquals("Premium share", blocks.get(0).plainText());
      assertEquals(MarkdownModel.BlockType.PARAGRAPH, blocks.get(1).type());
      assertEquals(MarkdownModel.BlockType.BULLET, blocks.get(2).type());
      assertEquals("point one", blocks.get(2).plainText());
      assertEquals(MarkdownModel.BlockType.BULLET, blocks.get(3).type());
   }

   @Test
   void mergesConsecutiveProseLinesIntoOneParagraph() {
      List<MarkdownModel.Block> blocks = MarkdownModel.parse("line one\nline two\n\nsecond para");
      assertEquals(2, blocks.size());
      assertEquals("line one line two", blocks.get(0).plainText());
      assertEquals("second para", blocks.get(1).plainText());
   }

   @Test
   void parsesInlineBoldAndItalicSpans() {
      List<MarkdownModel.Span> spans = MarkdownModel.parseInline("**Premium** drives ~69% of *all* revenue");
      // bold "Premium" | " drives ~69% of " | italic "all" | " revenue"
      assertEquals("Premium", spans.get(0).text());
      assertTrue(spans.get(0).bold());
      assertFalse(spans.get(0).italic());
      assertTrue(spans.stream().anyMatch(s -> s.italic() && s.text().equals("all")));
      assertTrue(spans.stream().anyMatch(s -> !s.bold() && !s.italic() && s.text().contains("drives")));
      // reassembled plain text loses no characters
      assertEquals("Premium drives ~69% of all revenue",
         spans.stream().map(MarkdownModel.Span::text).reduce("", String::concat));
   }

   @Test
   void handlesItalicNestedInsideBold() {
      List<MarkdownModel.Span> spans = MarkdownModel.parseInline("**not the same order as *share*.**");
      // no raw markers survive
      assertFalse(spans.stream().anyMatch(s -> s.text().contains("*")), "markers consumed");
      // "share" is bold AND italic; the rest is bold-only
      assertTrue(spans.stream().anyMatch(s -> s.text().equals("share") && s.bold() && s.italic()));
      assertTrue(spans.stream().anyMatch(s -> s.text().contains("not the same order") && s.bold() && !s.italic()));
      assertEquals("not the same order as share.",
         spans.stream().map(MarkdownModel.Span::text).reduce("", String::concat));
   }

   @Test
   void doesNotItalicizeIntraWordUnderscores() {
      List<MarkdownModel.Span> spans = MarkdownModel.parseInline("group by price_band and premium_pct");
      assertEquals(1, spans.size(), "intra-word underscores are literal, not italic markers");
      assertFalse(spans.get(0).italic());
      assertEquals("group by price_band and premium_pct", spans.get(0).text());
   }

   @Test
   void emptyAndNullAreSafe() {
      assertTrue(MarkdownModel.parse(null).isEmpty());
      assertTrue(MarkdownModel.parse("").isEmpty());
      assertTrue(MarkdownModel.parse("   \n  \n").isEmpty());
   }
}
