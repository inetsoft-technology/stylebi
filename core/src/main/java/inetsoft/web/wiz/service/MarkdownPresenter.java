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

import inetsoft.report.ReportElement;
import inetsoft.report.internal.ExpandablePresenter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link ExpandablePresenter} that renders markdown ({@link MarkdownModel}) directly to a
 * report graphics context — headers, bullet lists, and paragraphs with inline bold/italic runs —
 * so board exports (and anything else that paints a Presenter) keep formatting instead of
 * flattening it. Wraps text to the paint width, computes its own preferred height, and paints a
 * vertical slice (for page breaks) the same way {@link inetsoft.report.painter.HTMLPresenter} does.
 */
public class MarkdownPresenter implements ExpandablePresenter {
   public MarkdownPresenter() {
   }

   public void setBodyColor(Color bodyColor) {
      this.bodyColor = bodyColor;
   }

   public void setAccentColor(Color accentColor) {
      this.accentColor = accentColor;
   }

   @Override
   public boolean isPresenterOf(Class type) {
      return true;
   }

   @Override
   public boolean isPresenterOf(Object obj) {
      return obj != null;
   }

   @Override
   public boolean isFill() {
      return true;
   }

   @Override
   public void setFont(Font font) {
      if(font != null) {
         this.baseFont = font;
         this.layoutCache = null;
      }
   }

   @Override
   public void setBackground(Color bg) {
      this.background = bg;
   }

   @Override
   public String getDisplayName() {
      return "Markdown";
   }

   @Override
   public boolean isRawDataRequired() {
      return false;
   }

   @Override
   public Dimension getPreferredSize(Object v) {
      return getPreferredSize(v, DEFAULT_WIDTH);
   }

   @Override
   public Dimension getPreferredSize(Object v, float width) {
      int w = Math.max(1, (int) width);
      return new Dimension(w, layout(v, w).height);
   }

   @Override
   public float getHeightAdjustment(Object obj, ReportElement elem, Dimension pd,
                                    float starty, float bufw, float bufh)
   {
      // If everything up to the intended break already fits, no adjustment.
      if(obj == null || bufh <= 0 || starty + bufh >= pd.height) {
         return 0;
      }

      // If a line straddles the intended break, pull the break back to that line's top so no line
      // is cut in half across a page boundary.
      Layout layout = layout(obj, (int) bufw);
      float breakAt = starty + bufh;

      for(Line line : layout.lines) {
         if(line.top < breakAt && line.top + line.height > breakAt) {
            return breakAt - line.top;
         }
      }

      return 0;
   }

   @Override
   public void paint(Graphics g, Object v, int x, int y, int w, int h) {
      paint(g, v, x, y, w, h, 0, -1);
   }

   @Override
   public void paint(Graphics g, Object v, int x, int y, int w, int h, float bufy, float bufh) {
      Layout layout = layout(v, w);
      Shape oclip = g.getClip();
      Font ofont = g.getFont();
      Color ocolor = g.getColor();

      if(background != null) {
         g.setColor(background);
         g.fillRect(x, y, w, h);
      }

      g.clipRect(x, y, w, h);
      float top = bufy;
      float bottom = bufh < 0 ? Float.MAX_VALUE : bufy + bufh;

      for(Line line : layout.lines) {
         if(line.top + line.height <= top || line.top >= bottom) {
            continue;
         }

         int baseline = (int) (y + line.top - bufy + line.ascent);

         for(Run run : line.runs) {
            g.setFont(run.font);
            g.setColor(run.color);
            g.drawString(run.text, x + run.x, baseline);
         }
      }

      g.setClip(oclip);
      g.setFont(ofont);
      g.setColor(ocolor);
   }

   // ------------------------------------------------------------------------
   // Layout
   // ------------------------------------------------------------------------

   private Layout layout(Object v, int width) {
      String md = v == null ? "" : v.toString();

      if(layoutCache != null && width == cacheWidth && md.equals(cacheValue)) {
         return layoutCache;
      }

      Layout layout = buildLayout(md, width);
      cacheValue = md;
      cacheWidth = width;
      layoutCache = layout;
      return layout;
   }

   private Layout buildLayout(String md, int width) {
      List<Line> lines = new ArrayList<>();
      int y = 0;
      boolean firstBlock = true;

      for(MarkdownModel.Block block : MarkdownModel.parse(md)) {
         if(!firstBlock) {
            y += BLOCK_GAP;
         }

         firstBlock = false;

         int bodySize = baseFont.getSize();
         int indent = block.type() == MarkdownModel.BlockType.BULLET ? BULLET_INDENT : 0;
         List<Token> tokens = new ArrayList<>();

         if(block.type() == MarkdownModel.BlockType.BULLET) {
            // Hanging "•" marker at the block's left edge; text starts at the indent.
            tokens.add(new Token("•", deriveFont(true, false, bodySize), accentColor, 0));
         }

         int headerSize = bodySize + Math.max(1, 5 - block.level());
         boolean heading = block.type() == MarkdownModel.BlockType.HEADING;

         for(MarkdownModel.Span span : block.spans()) {
            Font font = deriveFont(heading || span.bold(), span.italic(), heading ? headerSize : bodySize);
            Color color = heading ? accentColor : bodyColor;

            for(String word : splitWords(span.text())) {
               tokens.add(new Token(word, font, color, indent));
            }
         }

         y = layoutTokens(lines, tokens, width, indent, y);
      }

      return new Layout(lines, y);
   }

   /** Greedy word-wrap of styled tokens into positioned lines; returns the y past the block. */
   private int layoutTokens(List<Line> lines, List<Token> tokens, int width, int wrapX, int y) {
      List<Run> runs = new ArrayList<>();
      int curX = 0;
      int lineAscent = 0;
      int lineHeight = 0;
      boolean lineStarted = false;

      for(Token token : tokens) {
         FontMetrics fm = metrics(token.font);
         int wordW = fm.stringWidth(token.text);
         boolean bulletMarker = token.wrapX == 0 && !lineStarted && "•".equals(token.text);

         int startX = bulletMarker ? 0 : token.wrapX;

         if(!lineStarted) {
            curX = startX;
         }
         else {
            int spaceW = metrics(token.font).charWidth(' ');

            if(curX + spaceW + wordW > width) {
               // wrap
               lines.add(new Line(y, lineHeight, lineAscent, runs));
               y += lineHeight;
               runs = new ArrayList<>();
               curX = wrapX;
               lineAscent = 0;
               lineHeight = 0;
               lineStarted = false;
            }
            else {
               curX += spaceW;
            }
         }

         runs.add(new Run(token.text, token.font, token.color, curX));
         curX += wordW;
         lineAscent = Math.max(lineAscent, fm.getAscent());
         lineHeight = Math.max(lineHeight, fm.getHeight());
         lineStarted = true;
      }

      if(lineStarted) {
         lines.add(new Line(y, lineHeight, lineAscent, runs));
         y += lineHeight;
      }

      return y;
   }

   private static List<String> splitWords(String text) {
      List<String> words = new ArrayList<>();

      for(String w : text.split("\\s+")) {
         if(!w.isEmpty()) {
            words.add(w);
         }
      }

      return words;
   }

   private Font deriveFont(boolean bold, boolean italic, int size) {
      int style = (bold ? Font.BOLD : 0) | (italic ? Font.ITALIC : 0);
      return baseFont.deriveFont(style, size);
   }

   private FontMetrics metrics(Font font) {
      return metricsCache.computeIfAbsent(font, f -> {
         if(measuring == null) {
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            measuring = img.createGraphics();
         }

         return measuring.getFontMetrics(f);
      });
   }

   private record Token(String text, Font font, Color color, int wrapX) {
   }

   private record Run(String text, Font font, Color color, int x) {
   }

   private record Line(int top, int height, int ascent, List<Run> runs) {
   }

   private record Layout(List<Line> lines, int height) {
   }

   private static final int DEFAULT_WIDTH = 400;
   private static final int BLOCK_GAP = 6;
   private static final int BULLET_INDENT = 16;

   private Font baseFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
   private Color background;
   private Color bodyColor = new Color(0x2B2B2B);
   private Color accentColor = new Color(0x3B6EA5);

   private transient String cacheValue;
   private transient int cacheWidth = -1;
   private transient Layout layoutCache;
   private transient Graphics2D measuring;
   private final transient Map<Font, FontMetrics> metricsCache = new HashMap<>();
}
