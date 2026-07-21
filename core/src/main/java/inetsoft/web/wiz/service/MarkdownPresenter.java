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
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
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

      Graphics2D g2 = g instanceof Graphics2D ? (Graphics2D) g : null;

      for(Line line : layout.lines) {
         if(line.top + line.height <= top || line.top >= bottom) {
            continue;
         }

         float baseline = y + line.top - bufy + line.ascent;

         if(line.marker != null) {
            drawSegment(g, g2, line.marker.font, line.marker.color, line.marker.text, x, baseline);
         }

         // Coalesce consecutive same-style words into one draw call (joined by real spaces) so
         // inter-word spacing is laid out from the font's own glyph advances. Rendering goes through
         // drawGlyphVector, which paints glyph outlines at those exact advances — bypassing the
         // PDF text engine's char-spacing reconciliation (PDFPrinter caps per-char stretch at 0.8,
         // so drawString renders derived bold/italic fonts narrower than their metrics report, and
         // that error accumulates into a visible gap at each style boundary). Because both layout
         // and paint measure with the same canonical FontRenderContext (FRC), positions match the
         // rendered output exactly on every device.
         float cx = x + line.startX;
         List<Run> runs = line.runs;
         int i = 0;

         while(i < runs.size()) {
            Run first = runs.get(i);
            StringBuilder segment = new StringBuilder();

            if(first.spaceBefore) {
               segment.append(' ');
            }

            segment.append(first.text);
            int j = i + 1;

            while(j < runs.size() && runs.get(j).font.equals(first.font)
                  && runs.get(j).color.equals(first.color))
            {
               if(runs.get(j).spaceBefore) {
                  segment.append(' ');
               }

               segment.append(runs.get(j).text);
               j++;
            }

            cx += drawSegment(g, g2, first.font, first.color, segment.toString(), cx, baseline);
            i = j;
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
         boolean heading = block.type() == MarkdownModel.BlockType.HEADING;
         boolean bullet = block.type() == MarkdownModel.BlockType.BULLET;
         int size = heading ? bodySize + Math.max(1, 5 - block.level()) : bodySize;
         int textX = bullet ? BULLET_INDENT : 0;
         Run marker = bullet ? new Run("•", deriveFont(true, false, bodySize), accentColor, false) : null;

         // Flatten spans into styled words; spaceBefore marks a single inter-word gap.
         List<Run> words = new ArrayList<>();
         boolean firstWord = true;

         for(MarkdownModel.Span span : block.spans()) {
            Font font = deriveFont(heading || span.bold(), span.italic(), size);
            Color color = heading ? accentColor : bodyColor;

            for(String word : splitWords(span.text())) {
               words.add(new Run(word, font, color, !firstWord));
               firstWord = false;
            }
         }

         y = wrapBlock(lines, words, marker, textX, width, y);
      }

      return new Layout(lines, y);
   }

   /** Greedy word-wrap into lines. Positions are resolved at paint time from the device metrics;
    *  here we only decide line breaks (using approximate layout metrics) and per-run spacing. */
   private int wrapBlock(List<Line> lines, List<Run> words, Run marker, int textX, int width, int y) {
      List<Run> cur = new ArrayList<>();
      float cx = textX;
      int ascent = 0;
      int height = 0;
      boolean markerOnThisLine = marker != null;

      if(markerOnThisLine) {
         FontMetrics mfm = metrics(marker.font);
         ascent = mfm.getAscent();
         height = mfm.getHeight();
      }

      for(Run word : words) {
         FontMetrics fm = metrics(word.font);
         float wordW = advance(word.font, word.text);
         boolean spaceBefore = word.spaceBefore && !cur.isEmpty();
         float spaceW = spaceBefore ? advance(word.font, " ") : 0;

         if(!cur.isEmpty() && cx + spaceW + wordW > width) {
            lines.add(new Line(y, height, ascent, textX, markerOnThisLine ? marker : null, cur));
            y += height;
            cur = new ArrayList<>();
            cx = textX;
            ascent = 0;
            height = 0;
            markerOnThisLine = false;
            spaceBefore = false;
            spaceW = 0;
         }

         cur.add(new Run(word.text, word.font, word.color, spaceBefore));
         cx += spaceW + wordW;
         ascent = Math.max(ascent, fm.getAscent());
         height = Math.max(height, fm.getHeight());
      }

      if(!cur.isEmpty()) {
         lines.add(new Line(y, height, ascent, textX, markerOnThisLine ? marker : null, cur));
         y += height;
      }

      return y;
   }

   /**
    * Draw one styled segment at the baseline and return its advance width. Uses drawGlyphVector so
    * the rendered glyph positions match the advances used for layout ({@link #advance}); falls back
    * to drawString on a non-Graphics2D device.
    */
   private float drawSegment(Graphics g, Graphics2D g2, Font font, Color color, String text,
                             float x, float baseline)
   {
      g.setColor(color);

      if(g2 != null) {
         GlyphVector gv = font.createGlyphVector(FRC, text);
         g2.drawGlyphVector(gv, x, baseline);
         return (float) gv.getGlyphPosition(gv.getNumGlyphs()).getX();
      }

      g.setFont(font);
      g.drawString(text, Math.round(x), Math.round(baseline));
      return advance(font, text);
   }

   /** Horizontal advance of a string from the font's own glyph metrics (device-independent). */
   private float advance(Font font, String text) {
      GlyphVector gv = font.createGlyphVector(FRC, text);
      return (float) gv.getGlyphPosition(gv.getNumGlyphs()).getX();
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

   private record Run(String text, Font font, Color color, boolean spaceBefore) {
   }

   private record Line(int top, int height, int ascent, int startX, Run marker, List<Run> runs) {
   }

   private record Layout(List<Line> lines, int height) {
   }

   private static final int DEFAULT_WIDTH = 400;
   private static final int BLOCK_GAP = 6;
   private static final int BULLET_INDENT = 16;

   /** Canonical render context used for BOTH layout measurement and glyph-vector painting, so
    *  positions are identical and device-independent (antialias + fractional metrics on). */
   private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

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
