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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, defensive parser that turns the markdown authored for a saved visualization's
 * "insights" (and a board's recap) into a small block/span model for styled rendering in the
 * board exports — headers, bullets, and paragraphs, each carrying inline bold/italic spans.
 *
 * <p>Like {@link MarkdownPlainText} this is a regex transform, not a full CommonMark parser: it
 * handles the constructs the insights actually use (hash headers; dash/star/plus bullets; bold
 * via double-star or double-underscore; italic via single-star or single-underscore) and never
 * throws on malformed input. Intra-word underscores (e.g. price_band) are intentionally NOT
 * treated as italics -- the underscore emphasis pattern requires word boundaries.</p>
 */
public final class MarkdownModel {
   private MarkdownModel() {
   }

   public enum BlockType { HEADING, BULLET, PARAGRAPH }

   /** An inline run of text with its emphasis flags. */
   public record Span(String text, boolean bold, boolean italic) {
   }

   /** A block-level element. {@code level} is the heading depth (1-6) or 0 otherwise. */
   public record Block(BlockType type, int level, List<Span> spans) {
      public String plainText() {
         StringBuilder sb = new StringBuilder();

         for(Span s : spans) {
            sb.append(s.text());
         }

         return sb.toString();
      }
   }

   /** Parse markdown into a flat list of blocks. Consecutive non-blank prose lines merge into
    *  one paragraph; a blank line, header, or bullet ends the current paragraph. */
   public static List<Block> parse(String markdown) {
      List<Block> blocks = new ArrayList<>();

      if(markdown == null) {
         return blocks;
      }

      String[] lines = markdown.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
      StringBuilder para = new StringBuilder();

      for(String raw : lines) {
         String line = raw.strip();

         if(line.isEmpty()) {
            flushParagraph(para, blocks);
            continue;
         }

         Matcher header = HEADER.matcher(line);
         Matcher bullet = BULLET.matcher(line);

         if(header.find()) {
            flushParagraph(para, blocks);
            blocks.add(new Block(BlockType.HEADING, header.group(1).length(),
                                  parseInline(line.substring(header.end()))));
         }
         else if(bullet.find()) {
            flushParagraph(para, blocks);
            blocks.add(new Block(BlockType.BULLET, 1, parseInline(line.substring(bullet.end()))));
         }
         else {
            if(para.length() > 0) {
               para.append(' ');
            }

            para.append(line);
         }
      }

      flushParagraph(para, blocks);
      return blocks;
   }

   private static void flushParagraph(StringBuilder para, List<Block> blocks) {
      if(para.length() > 0) {
         blocks.add(new Block(BlockType.PARAGRAPH, 0, parseInline(para.toString())));
         para.setLength(0);
      }
   }

   /** Split a line into bold/italic/plain spans (handles one level of nesting, e.g. bold > italic). */
   static List<Span> parseInline(String text) {
      return parseInline(text, false, false);
   }

   private static List<Span> parseInline(String text, boolean inheritBold, boolean inheritItalic) {
      List<Span> spans = new ArrayList<>();

      if(text.isEmpty()) {
         return spans;
      }

      Matcher m = INLINE.matcher(text);
      int last = 0;

      while(m.find()) {
         if(m.start() > last) {
            spans.add(new Span(text.substring(last, m.start()), inheritBold, inheritItalic));
         }

         // Recurse into the matched content so nested emphasis (bold containing italic, etc.) is
         // preserved rather than left as literal ** / * inside the outer span.
         if(m.group(1) != null) {
            spans.addAll(parseInline(m.group(1), true, inheritItalic));
         }
         else if(m.group(2) != null) {
            spans.addAll(parseInline(m.group(2), true, inheritItalic));
         }
         else if(m.group(3) != null) {
            spans.addAll(parseInline(m.group(3), inheritBold, true));
         }
         else if(m.group(4) != null) {
            spans.addAll(parseInline(m.group(4), inheritBold, true));
         }

         last = m.end();
      }

      if(last < text.length()) {
         spans.add(new Span(text.substring(last), inheritBold, inheritItalic));
      }

      if(spans.isEmpty()) {
         spans.add(new Span(text, inheritBold, inheritItalic));
      }

      return spans;
   }

   private static final Pattern HEADER = Pattern.compile("^(#{1,6})\\s+");
   private static final Pattern BULLET = Pattern.compile("^[-*+]\\s+");
   // Order matters: bold (** / __) before italic (* / _). Italic markers require word boundaries
   // so intra-word underscores/stars (price_band, a*b) are left as literal text.
   private static final Pattern INLINE = Pattern.compile(
      "\\*\\*(.+?)\\*\\*" +
      "|__(.+?)__" +
      "|(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![\\w])" +
      "|(?<![\\w_])_(?!\\s)(.+?)(?<!\\s)_(?![\\w])");
}
