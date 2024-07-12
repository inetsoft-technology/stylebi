/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.internal;

import inetsoft.report.*;
import inetsoft.report.internal.png.PNGEncoder;
import inetsoft.report.io.ArabicTextUtil;
import inetsoft.report.pdf.FontManager;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.XConstants;
import inetsoft.util.Tool;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.*;
import java.io.*;
import java.lang.reflect.Method;
import java.math.RoundingMode;
import java.text.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class contains the common functions used in the inetsoft.report
 * package. It is for internal implementation purpose only, and is exposed
 * as a public class sorely for the reason of lack of support for certain
 * class dependencies by the Java compiler.
 * <p>
 * DO NOT USE THIS CLASS!!!
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Common extends Util {
   /**
    * Get the the line adjustment. For integer based coordinate system,
    * this is always 0. For fraction coordinate system, this is half
    * of a one point line width.
    */
   public static float getLineAdjustment(Graphics g) {
      return g2d.getLineAdjustment(g);
   }

   /**
    * Check if the graphics is a Graphics2D object.
    */
   public static boolean isGraphics2D(Graphics g) {
      return g2d.isGraphics2D(g);
   }

   /**
    * Get a Graphics2D object from the provided graphics context.
    *
    * @param g the graphics context.
    *
    * @return a Graphics2D object.
    */
   public static Graphics2D getGraphics2D(Graphics g) {
      if(g instanceof Graphics2D) {
         return (Graphics2D) g;
      }

      try {
         if(g.getClass().getName().
            equals("sun.awt.windows.WPrintGraphicsWrapper"))
         {
            // cache getTarget method object for performance
            if(getTarget == null) {
               getTarget = g.getClass().getMethod("getTarget", new Class[] {});
            }

            return (Graphics2D) getTarget.invoke(g, new Object[] {});
         }
      }
      catch(Exception exc) {
         // ignore, this class doesn't exist in jdk 1.4
         LOG.warn("Failed to dereference graphics wrapper", exc);
      }

      throw new RuntimeException("Can not get Graphics2D from: " + g);
   }

   /**
    * Check if the runtime is Java2.
    */
   public static boolean isJava2() {
      return g2d.isJava2();
   }

   /**
    * Check if the runtime is Java JDK 1.4.
    */
   public static boolean isJDK14() {
      try {
         Class.forName("java.awt.KeyboardFocusManager");
         return true;
      }
      catch(Throwable e) {
         return false;
      }
   }

   /**
    * Draw a plain 1 pt line.
    */
   public static void drawLine(Graphics g, double x1, double y1, double x2,
                               double y2) {
      g2d.drawLine(g, x1, y1, x2, y2);
   }

   /**
    * Draw a styled line.
    */
   public static void drawLine(Graphics g, double x1, double y1, double x2,
                               double y2, int style) {
      g2d.drawLine(g, x1, y1, x2, y2, style);
   }

   /**
    * Draw a styled polyline.
    */
   public static void drawPolyline(Graphics g, float[] xs, float[] ys, int n,
                                   int style) {
      g2d.drawPolyline(g, xs, ys, n, style);
   }

   /**
    * Draw a plain polyline.
    */
   public static void drawPolygon(Graphics g, Polygon polygon) {
      g2d.drawPolygon(g, polygon);
   }

   /**
    * Draw a styled polyline.
    */
   public static void drawPolygon(Graphics g, Polygon polygon, int style) {
      g2d.drawPolygon(g, polygon, style);
   }

   /**
    * Draw a plain oval.
    */
   public static void drawOval(Graphics g, float x, float y, float w, float h) {
      g2d.drawOval(g, x, y, w, h);
   }

   /**
    * Draw a styled oval.
    */
   public static void drawOval(Graphics g, float x, float y, float w, float h,
                        int style) {
      g2d.drawOval(g, x, y, w, h, style);
   }

   /**
    * Draw a horizontal line according to the style.
    * The styles are defined in the StyleConstants.
    * @param g graphics content.
    * @param y y coordinate.
    * @param x1 leftside x coordinate.
    * @param x2 rightside x coordinate.
    * @param style line style.
    * @param top the line style of left-top vertical line.
    * @param bottom the line style of the right-bottom vertical line.
    */
   public static void drawHLine(Graphics g, float y, float x1, float x2,
                                int style, int top, int bottom) {
      if(x1 == x2) {
         return;
      }

      g2d.drawHLine(g, y, x1, x2, style, top, bottom);
   }

   /**
    * Draw a vertical line according to the style.
    * The styles are defined in the StyleConstants.
    * @param g graphics content.
    * @param x x coordinate.
    * @param y1 upper y coordinate.
    * @param y2 bottom y coordinate.
    * @param style line style.
    * @param left the line style of left-top vertical line.
    * @param right the line style of the right-bottom vertical line.
    */
   public static void drawVLine(Graphics g, float x, float y1, float y2,
                                int style, int left, int right) {
      if(y1 == y2) {
         return;
      }

      g2d.drawVLine(g, x, y1, y2, style, left, right);
   }

   /**
    * Draw an arc with the specified line style.
    */
   public static void drawArc(Graphics g, float x, float y, float w, float h,
                              float startAngle, float angle, int style) {
      g2d.drawArc(g, x, y, w, h, startAngle, angle, style);
   }

   /**
    * Draw an arc with the plain line style.
    */
   public static void drawArc(Graphics g, float x, float y, float w, float h,
                              float startAngle, float angle) {
      g2d.drawArc(g, x, y, w, h, startAngle, angle);
   }

   /**
    * Fill a rectanglar area.
    * @param x left x coordinate.
    * @param y upper y coordinate.
    * @param w rectangle width.
    * @param h rectangle height.
    */
   public static void fillRect(Graphics g, float x, float y, float w, float h) {
      g2d.fillRect(g, x, y, w, h);
   }

   /**
    * Fill an arc with the specified fill pattern.
    */
   public static void fillArc(Graphics g, float x, float y, float w, float h,
                              float startAngle, float angle, Object fill) {
      g2d.fillArc(g, x, y, w, h, startAngle, angle, fill);
   }

   /**
    * Fill an area with the specified fill pattern.
    * @param area shape to fill.
    * @param fill fill pattern, a Color or Paint (JDK1.2 only) object.
    */
   public static void fill(Graphics g, Shape area, Object fill) {
      g2d.fill(g, area, fill);
   }

   /**
    * Set the clip area.
    * @param g graphics context.
    * @param box clipping bounds.
    */
   public static void setClip(Graphics g, Bounds box) {
      g2d.setClip(g, box);
   }

   /**
    * Clip the current clip area.
    * @param g graphics context.
    * @param box clipping bounds.
    */
   public static void clipRect(Graphics g, Bounds box) {
      g2d.clipRect(g, box);
   }

   /**
    * Draw image at the specified location and size.
    * @param g graphics context.
    * @param img image object.
    * @param x x coordinate.
    * @param y y coordinate.
    * @param w image target width.
    * @param h image target height.
    * @param observer image observer.
    */
   public static void drawImage(Graphics g, Image img, float x, float y,
                                float w, float h, ImageObserver observer) {
      g2d.drawImage(g, img, x, y, w, h, observer);
   }

   /**
    * Rotate the graphics coordinate.
    */
   public static void rotate(Graphics g, double angle) {
      g2d.rotate(g, angle);
   }

   /**
    * Paint a painter at the graphics location. The painter is scaled
    * properly to fit in the area.
    * @param g graphics context.
    * @param x graphics x coordinate.
    * @param y graphics y coordinate.
    * @param w graphics area width.
    * @param h graphics area width.
    * @param painter painter object to print.
    * @param sx painter x coordinate.
    * @param sy painter y coordinate.
    * @param sw painter width.
    * @param sh painter height.
    * @param clipw clip width.
    * @param cliph clip height.
    * @param fg foreground color.
    * @param bg background color.
    * @param bufy already consumed height.
    * @param bufh available paintable height in graphics.
    */
   public static void paint(Graphics g, float x, float y, float w, float h,
                            Painter painter, float sx, float sy, float sw,
                            float sh, float clipw, float cliph,
                            Color fg, Color bg, float bufy, float bufh) {
      g2d.paint(g, x, y, w, h, painter, sx, sy, sw, sh, clipw, cliph, fg, bg,
                bufy, bufh);
   }

   /**
    * Calculate the font height. This is more accurate than FontMetrics.getHeight as it avoids integer rounding.
    * @param font font object.
    * @return font height in pixels.
    */
   public static float getHeight(Font font) {
      return g2d.getHeight(font);
   }

   /**
    * Get the font ascent value.
    * @param fm font metrics.
    * @return font ascent.
    */
   public static float getAscent(FontMetrics fm) {
      return g2d.getAscent(fm);
   }

   /**
    * Get the font ascent value.
    * @param fn font.
    * @return font ascent.
    */
   public static float getAscent(Font fn) {
      return g2d.getAscent(fn);
   }

   /**
    * Get the font descent value.
    * @param fn font.
    * @return font descent.
    */
   public static float getDescent(Font fn) {
      return g2d.getDescent(fn);
   }

   /**
    * Return all font names supported in this environment.
    * @return all available fonts.
    */
   public static String[] getAllFonts() {
      return g2d.getAllFonts();
   }

   private static final Hashtable fnNameCache = new Hashtable(50);
   private static Font lastFnForName = null;
   private static String lastFnName = null;
   /**
    * Get the font face name.
    */
   public static synchronized String getFontName(Font fn) {
      String fnName;

      if(fn.equals(lastFnForName)) {
         return lastFnName;
      }

      lastFnForName = fn;

      if((lastFnName = (String) fnNameCache.get(fn)) != null) {
         return lastFnName;
      }

      if(fnNameCache.size() > 50) {
         fnNameCache.clear();
      }

      lastFnName = g2d.getFontName(fn);
      fnNameCache.put(fn, lastFnName);

      return lastFnName;
   }

   /**
    * Get the postscript font name.
    */
   public static String getPSName(Font fn) {
      return g2d.getPSName(fn);
   }

   /**
    * Paint the painter by rotating it 90 degrees clockwise.
    */
   public static void paintRotate(Painter painter, Graphics g, float x,
                                  float y, float w, float h, int rotation) {
      g2d.paintRotate(painter, g, x, y, w, h, rotation);
   }

   /**
    * This method is called before a page is printed for any initialization.
    */
   public static void startPage(Graphics g, StylePage page) {
      g2d.startPage(g, page);
   }

   /**
    * Calculate the width of the string. This is the same as the
    * FontMetrics.stringWidth() for regular font. If the font is StyleFont,
    * the width is adjusted.
    * @param str string text.
    * @param fn font.
    */
   public static final float stringWidth(String str, Font fn) {
      int len = str == null ? 0 : str.length();

      if(len == 0) {
         return 0;
      }

      return stringWidth(str, 0, len, len, fn, Common.getFractionalFontMetrics(fn));
   }

   /**
    * Calculate the width of the string. This is the same as the
    * FontMetrics.stringWidth() for regular font. If the font is StyleFont,
    * the width is adjusted.
    * @param str string text.
    * @param fn font.
    * @param fm font metrics.
    */
   public static final float stringWidth(String str, Font fn, FontMetrics fm) {
      int len = str == null ? 0 : str.length();

      if(len == 0) {
         return 0;
      }

      return stringWidth(str, 0, len, len, fn, fm);
   }

   /**
    * Calculate the width of the string. This is the same as the
    * FontMetrics.stringWidth() for regular font. If the font is StyleFont,
    * the width is adjusted.
    * @param str string text.
    * @param fn font.
    * @param fm font metrics.
    */
   public static final float stringWidth(String str, int from, int to, int len,
                                         Font fn, FontMetrics fm) {
      if(from >= to) {
         return 0;
      }

      boolean total = from == 0 && to == len;

      if(fn instanceof StyleFont) {
         StyleFont font = (StyleFont) fn;
         int style = font.getStyle();

         if((style & StyleFont.SUPERSCRIPT) != 0 ||
            (style & StyleFont.SUBSCRIPT) != 0)
         {
            fn = font = new StyleFont(font.getName(), style,
               font.getSize() * 2 / 3);
            fm = Common.getFractionalFontMetrics(font);
         }

         if((style & StyleFont.SMALLCAPS) != 0) {
            Font smallcap = new Font(font.getName(),
                                     font.getStyle() & StyleFont.AWT_FONT_MASK,
                                     font.getSize() - 2);
            FontMetrics smallfm = Common.getFractionalFontMetrics(smallcap);
            float w = 0;

            for(int i = from; i < to; i++) {
               char c = str.charAt(i);

               if(Character.isLowerCase(c)) {
                  w += smallfm.charWidth(Character.toUpperCase(c));
               }
               else {
                  w += fm.charWidth(c);
               }
            }

            // @by larryl, it seems the charWidth does not accord for the part
            // that sticks out in italic fonts. We add a couple of points to
            // account for the mismatch
            if((style & Font.ITALIC) != 0) {
               w += 2;
            }

            return w;
         }
         else if((style & StyleFont.ALLCAPS) != 0) {
            if(!total) {
               str = str.substring(from, to);
            }

            return g2d.stringWidth(str.toUpperCase(), fn, fm);
         }
         /**
           @by mikec, the fix here seems not correct, it will result in the
           string width calculation error for CJK characters. And will result
           in a different print result with 8.0 version.
           Not sure what the original fix was for, so comment it out, if
           something happen in future, will return here and have a deep check.
         */
         /*
          else {
            float w = 0;

            for(int i = from; i < to; i++) {
               char c = str.charAt(i);
               // @by jamesx fix the charWidth() problem when using a western
               // font for CJK characters.
               // According to different kind of i18n requirements
               // the range here for "wide charachers" and algorithms
               // could be changed.
               // the current range supports CJK code range, including
               // Hangul Jamo range: 1100-11FF
               // CJK related radical/symbol/char ranges: 2E80-4DFF
               // and Hangul Syllables: AC00-D7AF
               int u_idx = (int) c;

               if((u_idx >= 0x1100 && u_idx <= 0x11FF) ||
                  (u_idx >= 0x2E80 && u_idx <= 0x9FFF) ||
                  (u_idx >= 0xAC00 && u_idx <= 0xD7AF))
               {
                  w += font.getSize();
               }
               else {
                  w += fm.charWidth(c);
               }
            }

            return w;
         }
         */
      }

      if(!total) {
         str = str.substring(from, to);
      }

      return g2d.stringWidth(str, fn, fm);
   }

   /**
    * Draw a string. This function supports the additional font styles
    * defined in the StyleFont class. It's used for text justification.
    * @param g graphics content.
    * @param str string text.
    * @param x x coordinate.
    * @param y y coordinate.
    * @param w width of the string area.
    */
   public static void drawString(Graphics g, String str, float x, float y,
                                 float w) {
      if(str == null || str.length() == 0) {
         return;
      }

      // if no space, draw as normal
      if(str.indexOf(' ') < 0) {
         drawString(g, str, x, y);
         return;
      }

      // @by mikec, remove the trailing space when justify.
      String[] words = Tool.split(str, ' ');
      {
         int lastword = -1;

         for(int i = words.length - 1; i >= 0; i--) {
            if(words[i].length() == 0) {
               lastword = i;
            }
            else {
               break;
            }
         }

         if(lastword != -1) {
            String[] temp = new String[lastword];
            System.arraycopy(words, 0, temp, 0, lastword);

            words = temp;
         }
      }

      Font fn = g.getFont();
      FontMetrics fm = Common.getFractionalFontMetrics(fn);

      float wdw = 0.0f; // word width

      int leadingspacecount = 0;
      boolean leadingspace = true;

      float[] wordlens = new float[words.length];

      for(int i = 0; i < words.length; i++) {
         if(words[i].length() == 0) {
            words[i] = " ";

            if(leadingspace) {
               leadingspacecount++;
            }
         }
         else {
            leadingspace = false;
         }

         wordlens[i] = stringWidth(words[i], fn, fm);
         wdw = wdw + wordlens[i];
      }

      final int underline = ((fn instanceof StyleFont) &&
         (fn.getStyle() & StyleFont.UNDERLINE) != 0) ?
         ((StyleFont) fn).getUnderlineStyle() : NO_BORDER;

      final int strikeline = ((fn instanceof StyleFont) &&
         (fn.getStyle() & StyleFont.STRIKETHROUGH) != 0) ?
         ((StyleFont) fn).getStrikelineStyle() : NO_BORDER;

      final float bot = y + Common.getDescent(fn);

      final float mid = (strikeline == NO_BORDER) ? -1 :
         y - getAscent(fn) + getHeight(fn) / 2 -
            getLineWidth(((StyleFont)fn).getStrikelineStyle()) / 2;

      // double is necessary otherwise the roundup loses too much precision
      final double adj = (w - wdw) / (words.length - leadingspacecount - 1.0);
      double xx = x;

      leadingspace = true;

      for(int i = 0; i < words.length; i++) {
         double adj2 = adj;

         if(leadingspace && words[i].equals(" ")) {
            adj2 = 0.0;
         }
         else {
            leadingspace = false;
         }

         drawString(g, words[i], (float) xx, y);

         xx += wordlens[i];

         // draw underline if any, not the last gap
         if(underline != 0 && i < words.length - 1) {
            drawHLine(g, bot, (float) xx, (float) (xx + adj2), underline,
                      NO_BORDER, NO_BORDER);
         }

         if(strikeline != 0 && i < words.length - 1) {
            drawHLine(g, mid, (float) xx, (float) (xx + adj2), strikeline,
                      NO_BORDER, NO_BORDER);
         }

         xx += adj2;
      }
   }

   /**
    * Draw a string. This function supports the additional font styles
    * defined in the StyleFont class.
    * @param g graphics content.
    * @param str string text.
    * @param x x coordinate.
    * @param y y coordinate of text baseline position.
    */
   public static void drawString(Graphics g, String str, float x, float y) {
      // ignore empty string
      if(str == null || str.length() == 0) {
         return;
      }

      if(g.getClass().getName().equals("inetsoft.xml.org.apache.batik.svggen.SVGGraphics2D")) {
         str = processBidi(str);
      }

      if(g.getFont() instanceof StyleFont) {
         StyleFont font = (StyleFont) g.getFont();
         FontMetrics fm = Common.getFractionalFontMetrics(font);
         int style = font.getStyle();
         // @by joec, stringWidth(...) already takes into account whether the
         // font is super/subscript. So determine the required line length
         // before redefining the font's size.
         float lineLength = stringWidth(str, font, fm);
         Font ofont = font;

         if((style & StyleFont.SUPERSCRIPT) != 0) {
            y = y - getAscent(fm) / 3;
            style = style & (~StyleFont.SUPERSCRIPT);
            font = new StyleFont(font.getName(), style, font.getSize() * 2 / 3,
                                 font.getUnderlineStyle(),
                                 font.getStrikelineStyle());
            fm = Common.getFractionalFontMetrics(font);
            g.setFont(font);
         }
         else if((style & StyleFont.SUBSCRIPT) != 0) {
            y = y + fm.getDescent() / 3;
            style = style & (~StyleFont.SUBSCRIPT);
            font = new StyleFont(font.getName(), style, font.getSize() * 2 / 3,
                                 font.getUnderlineStyle(),
                                 font.getStrikelineStyle());
            fm = Common.getFractionalFontMetrics(font);
            g.setFont(font);
         }

         drawStringCase(g, str, x, y);
         g.setFont(ofont);

         if((style & StyleFont.STRIKETHROUGH) != 0) {
            float mid = y - getAscent(font) + getHeight(font) / 2 -
               getLineWidth(font.getStrikelineStyle()) / 2;

            drawHLine(g, mid, x, x + lineLength, font.getStrikelineStyle(),
                      NO_BORDER, NO_BORDER);
         }

         if(!g.getClass().isAnnotationPresent(UnderlineSupported.class) &&
            (style & StyleFont.UNDERLINE) != 0)
         {
            float bot = y + getDescent(font);

            drawHLine(g, bot, x, x + lineLength, font.getUnderlineStyle(),
                      NO_BORDER, NO_BORDER);
         }
      }
      else {
         g2d.drawString(g, str, x, y);
      }
   }

   /**
    * Draw the string. This function handles the SHADOW, SMALLCAPS, and
    * ALLCAPS styles.
    * @param g graphics content.
    * @param str string text.
    * @param x x coordinate.
    * @param y y coordinate.
    */
   static void drawStringCase(Graphics g, String str, float x, float y) {
      StyleFont font = (StyleFont) g.getFont();
      int style = font.getStyle();

      if((style & StyleFont.SMALLCAPS) != 0) {
         FontMetrics fm = Common.getFractionalFontMetrics(font);
         Font smallcap = new Font(font.getName(),
                                  font.getStyle() & StyleFont.AWT_FONT_MASK,
                                  font.getSize() - 2);
         FontMetrics smallfm = Common.getFractionalFontMetrics(smallcap);

         for(int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            if(Character.isLowerCase(c)) {
               c = Character.toUpperCase(c);

               g.setFont(smallcap);

               if((style & StyleFont.SHADOW) != 0) {
                  Color clr = g.getColor();

                  g.setColor(Tool.brighter(clr));
                  g2d.drawString(g, "" + c, x + 1, y + 1);
                  g.setColor(clr);
               }

               g2d.drawString(g, "" + c, x, y);
               x += smallfm.charWidth(c);
            }
            else {
               g.setFont(font);

               if((style & StyleFont.SHADOW) != 0) {
                  Color clr = g.getColor();

                  g.setColor(Tool.brighter(clr));
                  g2d.drawString(g, "" + c, x + 1, y + 1);
                  g.setColor(clr);
               }

               g2d.drawString(g, "" + c, x, y);
               x += fm.charWidth(c);
            }
         }

         g.setFont(font);
      }
      else {
         if((style & StyleFont.ALLCAPS) != 0) {
            str = str.toUpperCase();
         }

         if((style & StyleFont.SHADOW) != 0) {
            Color c = g.getColor();

            g.setColor(Tool.brighter(c));
            g2d.drawString(g, str, x + 1, y + 1);
            g.setColor(c);
         }

         g2d.drawString(g, str, x, y);
      }
   }

   private static class FnFm {
      public FnFm(Font fn, FontMetrics fm) {
         this.fn = fn;
         this.fm = fm;
      }

      public Font fn;
      public FontMetrics fm;
   }

   static final Map<Font, FontMetrics> fmcache = new ConcurrentHashMap<>();
   private static final Map<Font, FontMetrics> fractionalFMCache = new ConcurrentHashMap<>();
   private static FnFm lastFnFm = null;
   /**
    * Get a font metrics for a font. A cache of font metrics is maintained
    * to avoid creating a new font metrics everytime.
    * @param fn font.
    * @return font metrics.
    */
   public static FontMetrics getFontMetrics(final Font fn) {
      FnFm fnfm = Common.lastFnFm;

      if(fnfm != null && fnfm.fn.equals(fn)) {
         return fnfm.fm;
      }

      FontMetrics fm0 = fmcache.computeIfAbsent(fn, k -> {
         FontMetrics fm = null;

         if(fn.getName().endsWith("-Acro")) {
            fm = FontManager.getFontManager().getFontMetrics(fn);
         }
         else if(SreeEnv.getProperty("font.metrics.source").
            equals("awt"))
         {
            fm = Tool.getToolkit().getFontMetrics(fn);
         }
         else {
            fm = FontManager.getFontManager().getFontMetrics(fn);
         }

         return fm;
      });

      lastFnFm = new FnFm(fn, fm0);
      return fm0;
   }

   /**
    * Get the fractional font metrics for a font. A cache of font metrics is maintained
    * to avoid creating a new font metrics every time.
    * @param font the font to get the fractional font metrics of.
    * @return font metrics.
    */
   public static FontMetrics getFractionalFontMetrics(Font font) {
      return fractionalFMCache.computeIfAbsent(font, k -> {
         final BufferedImage dummyImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
         final Graphics2D g = ((Graphics2D) dummyImg.getGraphics());
         g.setFont(font);
         g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
         FontMetrics fm = g.getFontMetrics();
         g.dispose();
         return fm;
      });
   }

   /**
    * Write an image to an output stream in JPEG format.
    */
   public static void writeJPEG(Image img, OutputStream output) {
      g2d.writeJPEG(img, output);
   }

   /**
    * Write an image to an output stream in PNG format.
    */
   public static void writePNG(Image img, OutputStream output) {
      writePNG(img, output,
              SreeEnv.getProperty("image.png.alpha").equals("true"));
   }

   /**
    * Write an image to an output stream in PNG format.
    */
   public static void writePNG(Image img, OutputStream output, boolean alpha) {
      // try JAI first, if does not work, use our own PNG generator
      try {
         /*
         RenderedImage im = (RenderedImage) img;
         PNGEncodeParam params = PNGEncodeParam.getDefaultEncodeParam(im);
         ImageEncoder encoder =
            ImageCodec.createImageEncoder("PNG", output, params);

         if(encoder != null) {
            encoder.encode(im);
            return;
         }
         */

         // or JDK 1.4
         RenderedImage img2 = (RenderedImage) img;
         ImageIO.write(img2, "png", output);
         return;
      }
      catch(Exception ex) {
      }

      // this is very memory intensive, should use Java image IO
      PNGEncoder png = new PNGEncoder(img, alpha);

      try {
         png.encode(output);
      }
      catch(IOException ioe) {
         if(ioe.getMessage() != null &&
            ioe.getMessage().indexOf("ClientAbortException") < 0)
         {
            LOG.error("Failed to encode image", ioe);
         }
      }
   }

   /**
    *  Write an image to an output stream in SVG format.
    */
   public static void writeSVG(MetaImage img, OutputStream out) {
      if(!img.isSVGImage()) {
         throw new RuntimeException("The format of the image should be svg");
      }

      try {
         ImageIO.write((RenderedImage) img.getImage(img.getWidth(null), img.getHeight(null)), "png", out);
      }
      catch(IOException e) {
         LOG.error("Failed to write SVG image", e);
      }
   }

   /**
    * Write an image to an output stream.
    * @return the suffix the image.
    */
   public static String writeImage(Image img, OutputStream output) {
      String imageType = SreeEnv.getProperty("image.type");

      return writeImage(img, output, imageType);
   }

   /**
    * Write an image to an output stream.
    * @return the suffix the image.
    */
   public static String writeImage(Image img, OutputStream output,
                                   String imageType) {
      if(img instanceof MetaImage) {
         InputStream input = ((MetaImage) img).getInputStream();

         if(input != null &&
            !("gif".equalsIgnoreCase(((MetaImage) img).getSuffix()) &&
            imageType.equalsIgnoreCase("jpeg")) &&
            !((MetaImage) img).isSVGImage())
         {
            try {
               Tool.copyTo(input, output);
            }
            catch(Exception ex) {
               LOG.error("Failed to write image", ex);
            }
            finally {
               IOUtils.closeQuietly(input);
            }

            return ((MetaImage) img).getSuffix();
         }
      }

      if(imageType != null && imageType.equalsIgnoreCase("jpeg")) {
         writeJPEG(img, output);
         return "jpg";
      }
      else if(img instanceof MetaImage && ((MetaImage) img).isSVGImage()) {
         writeSVG((MetaImage) img, output);
         return "svg";
      }
      else {
         writePNG(img, output);
         return "png";
      }
   }

   /**
    * Paint a text string. The text is wrapped around the line if a line
    * is longer than the bound. The text is adjusted for the specified
    * alignment inside the bound.
    *
    * @param g graphics object.
    * @param str text string.
    * @param bound the area the text will be painted.
    * @param align alignment flag.
    * @param wrap true to wrap the lines.
    * @param justify true to justify the right edge of lines.
    * @param dot decimal points for the currency alignment calculation.
    * @param clipline true to clip at line boundary.
    */
   public static void paintText(Graphics g, String str, Bounds bound,
                                int align, boolean wrap, boolean justify,
                                int spacing, int dot, boolean clipline) {
      Bounds nbound = new Bounds();
      Vector<Float> lineoff = new Vector<>(2);
      Font fn = g.getFont();
      FontMetrics fm = getFractionalFontMetrics(fn);
      float rmax = (dot == 0) ? 0f : stringWidth("." + Tool.getChars('9', dot), fn, fm);
      Vector<int[]> lines = processText(str, bound, align, wrap, fn, nbound, lineoff,
                                 spacing, fm, rmax);
      paintText(g, str, lines, bound, nbound, justify, lineoff, spacing, clipline);
   }

   /**
    * Get the target string height.
    * @param fn        the painting font.
    * @param str       text string.
    * @param bound     the area the text will be painted.
    * @param align     alignment flag.
    * @param wrap      true to wrap the lines.
    * @param spacing   line spacing.
    * @param dot       decimal points for the currency alignment calculation.
    * @return
    */
   public static float getStringHeight(Font fn, String str, Bounds bound, int align,
                                       boolean wrap, int spacing, int dot)
   {
      Bounds nbound = new Bounds();
      Vector lineoff = new Vector(2);
      FontMetrics fm = getFontMetrics(fn);
      float rmax = (dot == 0) ? 0f :
         stringWidth("." + Tool.getChars('9', dot), fn, fm);
      Vector lines = processText(str, bound, align, wrap, fn, nbound, lineoff,
         spacing, fm, rmax);
      return getHeight(fm.getFont()) * lines.size() + spacing * (lines.size() - 1);
   }

   /**
    * Paint a text string. The text is wrapped around the line if a line
    * is longer than the bound. The text is adjusted for the specified
    * alignment inside the bound.
    *
    * @param g graphics object.
    * @param str text string.
    * @param bound the area the text will be painted.
    * @param align alignment flag.
    * @param wrap true to wrap the lines.
    * @param justify true to justify the right edge of lines
    */
   public static void paintText(Graphics g, String str, Bounds bound,
                                int align, boolean wrap, boolean justify, int spacing) {
      paintText(g, str, bound, align, wrap, justify, spacing, 0, true);
   }

   /**
    * Paint text lines.
    *
    * @param g graphics object.
    * @param lines text lines offset {begin idx, end idx}.
    * @param bound the outer bound of the area text can be painted on.
    * @param nbound the area the text will be painted.
    * @param justify true to justify the right edge of lines
    * @param lineoff line offset from the left of the box.
    * @param spacing line spacing.
    * @param clipline true to clip at line boundary.
    */
   public static void paintText(Graphics g, String str, Vector<int[]> lines,
                                Bounds bound, Bounds nbound, boolean justify,
                                Vector<Float> lineoff, int spacing, boolean clipline)
   {
      Shape clip = g.getClip();
      clipRect(g, new Bounds(nbound.x, nbound.y, nbound.width, nbound.height));

      Font font = g.getFont();
      // draw text strings
      float fnH = getHeight(font);
      float ascent = getAscent(font);
      float descent = fnH - ascent;
      float clipDescent = clipline ? descent : descent - fnH;
      float y = nbound.y + ascent;
      float inc = fnH + spacing;
      Rectangle2D clipbox = g.getClip().getBounds2D();

      // @by mikec, if the height is negative, some content will not be draw
      // especially when pdf printer paint rotated text painter in 90 degrees
      // see bug1209114842706
      if(clipbox.getHeight() < 0) {
         clipbox.setRect(clipbox.getX(), clipbox.getY(), clipbox.getWidth(), -clipbox.getHeight());
      }

      // @by peterx, change clipbox.y to bound.y and
      // clipbox.height to bound.height. fix bug1074302425774.
      // If designepane doesnot active and another window move on the
      // painter paintable element of the designpane, for example:
      // TextBoxElement. the clipbox.y often is greater than bound.y,
      // then the value of 'y - ascent' will be less than clipbox.y.
      // Method drawString(...) will not be invoked.
      // @by larryl, can't just use bounds otherwise the clipping info
      // would be lost and the bottom line may be split in two pages.
      double minY = Math.min(clipbox.getY(), bound.y);
      clipbox.setRect(clipbox.getX(), minY, clipbox.getWidth(),
                      clipbox.getY() + clipbox.getHeight() - minY);

      for(int k = 0; k < lines.size(); k++, y += inc) {
         // @by larryl, avoid line being cut in the middle
         // 0.1F values are to account for floating-point rounding errors.
         if((y - ascent) - bound.y < -0.1F) {
            continue;
         }
         else if((y + clipDescent) - clipbox.getMaxY() > 0.1F && k > 0) {
            break;
         }

         boolean endOfParagraph = false;
         float x = nbound.x;

         if(lineoff != null && k < lineoff.size()) {
            x += lineoff.elementAt(k);
         }

         int[] offsets = lines.elementAt(k);
         String line = str.substring(offsets[0], offsets[1]);
         line = line.replace('\t', ' ');

         if(str.length() > offsets[1] && str.charAt(offsets[1]) == '\n') {
            endOfParagraph = true;
         }

         if(justify && k < lines.size() - 1 && !endOfParagraph) {
            drawString(g, line, x, y, Math.min(nbound.width, nbound.width + nbound.x - x));
         }
         else {
            drawString(g, line, x, y);
         }
      }

      g.setClip(clip);
   }

   /**
    * Process the text for newlines, text wrapping, and alignment.
    * @param str text string.
    * @param bound the bound to paint the text in.
    * @param align alignment.
    * @param fm font metrics for printing.
    * @param outbound the adjusted bound to paint the text in.
    * @param lineoff line offset
    * @return Vector of int[] {line offset, line end index}.
    */
   protected static Vector<int[]> processCurrencyText(String str, Bounds bound,
                                                      int align, Font fn,
                                                      Bounds outbound, Vector<Float> lineoff,
                                                      int spacing, FontMetrics fm,
                                                      float right)
   {
      // cut the right string
      Vector<int[]> lines = new Vector<>(2);
      Size psize = new Size();

      int dot = str.lastIndexOf('.');
      float w = stringWidth(str, fn, fm);
      float rw = 0;
      int offset = 0;

      if(dot != -1) {
         rw = stringWidth(str.substring(dot), fn, fm);
      }

      w = w + Math.max(0, right - rw);

      str = Tool.trimEnd(str);
      lineoff.addElement(w);
      psize.width = Math.max(psize.width, w);
      lines.addElement(new int[] {offset, offset + str.length()});

      psize.height = getHeight(fm.getFont()) * lines.size() +
         spacing * (lines.size() - 1);

      // adjust for alignment
      Bounds nbound = Common.alignCell(bound, psize, align);

      // don't chop off top
      outbound.x = nbound.x;
      outbound.y = nbound.y +
         Math.max((nbound.height - psize.height) / 2, 0);
      outbound.width = nbound.width;
      outbound.height = Math.min(psize.height,
         bound.y + bound.height - outbound.y);

      // adjust line offset
      for(int i = 0; i < lineoff.size(); i++) {
         w = lineoff.elementAt(i);
         lineoff.setElementAt(nbound.width - w, i);
      }

      return lines;
   }

   /**
    * Process the text for newlines, text wrapping, and alignment.
    * @param str text string.
    * @param bound the bound to paint the text in.
    * @param align alignment.
    * @param wrap true to wrap the text.
    * @param fm font metrics for printing.
    * @param outbound the adjusted bound to paint the text in.
    * @param lineoff line offset
    * @return Vector of int[] {line offset, line end index}.
    */
   public static Vector<int[]> processText(String str, Bounds bound, int align,
                                           boolean wrap, Font fn, Bounds outbound,
                                           Vector<Float> lineoff, int spacing,
                                           FontMetrics fm, float right)
   {
      lineoff.removeAllElements();

      if(bound.width <= 0 || bound.height <= 0) {
         return new Vector<>(1);
      }

      if(align > 0 && (align & StyleConstants.H_CURRENCY) != 0) {
         return processCurrencyText(str, bound, align, fn, outbound, lineoff, spacing, fm, right);
      }

      Vector<int[]> lines = new Vector<>(2);
      // @by mikec, use the print bound as a base width, otherwise a paragraph's
      // width will be determined only by the longest text line, and have nothing
      // to do width the print bound, which is not correct.
      Size psize = new Size(bound.width, 0);
      int offset = 0;

      // calculate the size of the text lines
      while(str != null) {
         int idx = str.indexOf('\n');

         if(wrap) {
            // support small font, customer bug bug1321349627049
            int mch = fm.charWidth('.'); //Math.max(fm.charWidth('.'), 4);
            int eidx = Math.min((idx >= 0) ? idx : str.length(),
                                (int) (bound.width / (mch * 0.5)));
            String s = str.substring(0, eidx);
            float lw = stringWidth(s, fn, fm);
            int breakpoint = idx >= 0 ? eidx + 1 : eidx; // exclude '\n'

            // wrap line
            if(lw > bound.width) {
               eidx--; // eidx is inclusive in the loop

               // find the breakpoint
               int sidx = s.length() / (fm.charWidth('X') * 2);
               int mid;

               do {
                  mid = (eidx + sidx) / 2;
                  lw = stringWidth(s.substring(0, mid + 1), fn, fm);

                  if(lw < bound.width) {
                     sidx = mid + 1;
                  }
                  else if(lw > bound.width) {
                     eidx = mid;
                  }
                  else {
                     eidx = mid;
                     break;
                  }
               }
               while(eidx > sidx);

               // breakpoint is at point where the string [0..breakpoint)
               // is < bound.width
               if(lw <= bound.width) {
                  breakpoint = mid + 1;
               }
               else {
                  breakpoint = mid;
               }

               eidx = breakpoint - 1; // eidx is inclusive now

               // work backward to find the first whitespace to break
               // the line
               if(eidx < str.length() - 1 &&
                  !Character.isWhitespace(str.charAt(eidx + 1)))
               {
                  // @see comments in Util.breakLine
                  boolean nextBreak = Util.isBreakSymbol(str.charAt(eidx + 1));

                  for(; eidx > 0; eidx--) {
                     char value = str.charAt(eidx);

                     if(Character.isWhitespace(value)) {
                        break;
                     }

                     // @by billh, fix customer bug bug1305311000060
                     // support euro symbol properly when break line
                     if(isInCJKCharacters(value) && value != GROUP_SYMBOL &&
                        value != SUMMARY_SYMBOL && value != 8364)
                     {
                        if(nextBreak) {
                           eidx--;
                        }

                        break;
                     }
                  }
               }
               else {
                  // found white space but don't want to skip the next char
                  eidx = -1;
               }

               // if no white space is found, use the breakpoint
               if(eidx <= 0) {
                  // @by larryl, the breakpoint could be 2 chars wider than the
                  // bounds at this point. This loop makes sure the wrapped line
                  // can indeed fit in the bound
                  while(true) {
                     eidx = breakpoint - 1;
                     s = str.substring(0, eidx + 1);
                     lw = stringWidth(s, fn, fm);

                     if(breakpoint > 2 && lw > bound.width) {
                        breakpoint--;
                        continue;
                     }

                     break;
                  }
               }
               // found white space
               else {
                  breakpoint = eidx + 1; // position after the white space

                  //maybe not break at white space, in this case should
                  //not exclude it.
                  if(str.charAt(eidx) <= 255) {
                     eidx--; // don't include the white space
                  }

                  // add 1 since eidx is inclusive
                  s = Tool.trimEnd(str.substring(0, eidx + 1));
                  // need to get the string width again since it may have
                  // changed since last stringWidth()
                  lw = stringWidth(s, fn, fm);
               }

               eidx++; // eidx is not inclusive outside the loop
            }

            lines.addElement(new int[] {offset, offset + s.length()});
            lineoff.addElement(lw);
            psize.width = Math.max(psize.width, lw);

            if(eidx >= str.length()) {
               break;
            }

            // elimite infinite loop if the eidx is 0 by doing a max
            int lineidx = Math.max(breakpoint, 1);

            offset += lineidx;
            str = str.substring(lineidx);

            // skip leading white space
            while(str.length() > 0 && ' ' == str.charAt(0)) {
               str = str.substring(1);
               offset++;
            }
         }
         else if(idx >= 0) {
            String s = str.substring(0, idx);
            float lw = stringWidth(s, fn, fm);

            lineoff.addElement(lw);
            psize.width = Math.max(psize.width, lw);
            str = str.substring(idx + 1);

            lines.addElement(new int[] {offset, offset + s.length()});
            offset += idx + 1;
         }
         else {
            float lw = stringWidth(str, fn, fm);

            lineoff.addElement(lw);
            psize.width = Math.max(psize.width, lw);

            lines.addElement(new int[] {offset, offset + str.length()});
            break;
         }
      }

      psize.height = getHeight(fm.getFont()) * lines.size() +
         spacing * (lines.size() - 1);

      // adjust for alignment
      Bounds nbound = Common.alignCell(bound, psize, align);

      // don't chop off top
      outbound.x = nbound.x;
      outbound.y = nbound.y + Math.max((nbound.height - psize.height) / 2, 0);
      outbound.width = nbound.width;
      outbound.height = Math.min(psize.height,
         bound.y + bound.height - outbound.y);

      // adjust line offset
      for(int i = 0; i < lineoff.size(); i++) {
         float w = lineoff.elementAt(i);

         // on html client, LEFT takes precedence over CENTER and RIGHT. do the same here.
         if((align & H_LEFT) != 0) {
            lineoff.setElementAt((float) 0, i);
         }
         else if((align & H_CENTER) != 0) {
            lineoff.setElementAt((nbound.width - w) / 2, i);
         }
         else if((align & H_RIGHT) != 0 && nbound.width != w) {
            // @by mikec, fix the line off of text.
            // @see bug1135784206363
            lineoff.setElementAt(nbound.width - w - 1, i);
         }
         else {
            lineoff.setElementAt((float) 0, i);
         }
      }

      return lines;
   }

   /**
    * Check if the character is in CJK characters.
    */
   public static boolean isInCJKCharacters(char c) {
      // The CJK Characters ranges in unicode as following
      // Please check http://www.alanwood.net/unicode/fontsbyrange.html or
      // http://jrgraphix.net/research/unicode_blocks.php
      // Bopomofo 3100 - 312F
      // Bopomofo Extended 31A0 - 31BF
      // CJK Compatibility 3300 - 33FF
      // CJK Compatibility Forms FE30 - FE4F
      // CJK Compatibility Ideographs F900 - FAFF
      // CJK Compatibility Ideographs Supplement 2F800 - 2FA1F
      // CJK Radicals Supplement 2E80 - 2EFF
      // CJK Strokes U+31C0 \u2043 U+31EF
      // CJK Symbols and Punctuation 3000 - 303F
      // CJK Unified Ideographs 4E00 - 9FFF
      // CJK Unified Ideographs Extension A 3400 - 4DBF
      // CJK Unified Ideographs Extension B 20000 - 2A6DF
      // CJK Unified Ideographs Extension C U+2A700 \u2043 U+2B73F
      // CJK Unified Ideographs Extension D U+2B740 \u2043 U+2B81F
      // Enclosed CJK Letters and Months 3200 - 32FF
      // Enclosed Ideographic Supplement U+1F200 \u2043 U+1F2FF
      // Hangul Compatibility Jamo 3130 - 318F
      // Hangul Jamo 1100 - 11FF
      // Hangul Jamo Extended-A U+A960 \u2043 U+A97F
      // Hangul Jamo Extended-b U+D7B0 \u2043 U+D7FF
      // Hangul Syllables AC00 - D7AF
      // Hiragana 3040 - 309F
      // Ideographic Description Characters 2FF0 - 2FFF
      // Kana Supplement U+1B000 \u2043 U+1B0FF
      // Kanbun 3190 - 319F
      // KangXi Radicals 2F00 - 2FDF
      // Katakana 30A0 - 30FF
      // Katakana Phonetic Extensions 31F0 - 31FF
      // Vertical Forms U+FE10 \u2043 U+FE1F
      if((c >= 0x3100 && c <= 0x312F) || (c >= 0x31A0 && c <= 0x31BF) ||
         (c >= 0x3300 && c <= 0x33FF) || (c >= 0xFE30 && c <= 0xFE4F) ||
         (c >= 0xF900 && c <= 0xFAFF) || (c >= 0x2F800 && c <= 0x2FA1F) ||
         (c >= 0x2E80 && c <= 0x2EFF) || (c >= 0x31C0 && c <= 0x31EF) ||
         (c >= 0x3000 && c <= 0x303F) || (c >= 0x4E00 && c <= 0x9FFF) ||
         (c >= 0x3400 && c <= 0x4DBF) || (c >= 0x20000 && c <= 0x2A6DF) ||
         (c >= 0x2A700 && c <= 0x2B73F) || (c >= 0x2B740 && c <= 0x2B81F) ||
         (c >= 0x3200 && c <= 0x32FF) || (c >= 0x1F200 && c <= 0x1F2FF) ||
         (c >= 0x3130 && c <= 0x318F) || (c >= 0x1100 && c <= 0x11FF) ||
         (c >= 0xA960 && c <= 0xA97F) || (c >= 0xD7B0 && c <= 0xD7FF) ||
         (c >= 0xAC00 && c <= 0xD7AF) || (c >= 0x3040 && c <= 0x309F) ||
         (c >= 0x2FF0 && c <= 0x2FFF) || (c >= 0x1B000 && c <= 0x1B0FF) ||
         (c >= 0x3190 && c <= 0x319F) || (c >= 0x2F00 && c <= 0x2FDF) ||
         (c >= 0x30A0 && c <= 0x30FF) || (c >= 0x31F0 && c <= 0x31FF) ||
         (c >= 0xFE10 && c <= 0xFE1F))
      {
         return true;
      }

      return false;
   }

   /**
    * Process the text for newlines, text wrapping, and alignment.
    * @param str text string.
    * @param bound the bound to paint the text in.
    * @param align alignment.
    * @param wrap true to wrap the text.
    * @param fm font metrics for printing.
    * @param outbound the adjusted bound to paint the text in.
    * @param lineoff line offset
    * @return Vector of int[] {line offset, line end index}.
    */
   public static Vector processText(String str, Bounds bound, int align,
                                    boolean wrap, Font fn, Bounds outbound,
                                    Vector lineoff, int spacing,
                                    FontMetrics fm) {
      return processText(str, bound, align, wrap, fn, outbound, lineoff,
         spacing, fm, 0);
   }

   /**
    * Align a rectangle area inside another rectangle area according
    * to the alignment flag. This method is used internally to handle
    * cell alignment. It can be used as a general purpose utility
    * method.
    * @param box outer rectangle area.
    * @param psize inner rectangle area.
    * @param align alignment flag.
    * @return aligned rectangle area position and size.
    */
   public static Bounds alignPresenterCell(Bounds box, Size psize, int align) {
      Bounds nbox = new Bounds(box.x, box.y, box.width, box.height);

      if(psize.width < nbox.width) {
         if((align & H_CENTER) != 0) {
            nbox.x += (nbox.width - psize.width) / 2;
            nbox.width = psize.width;
         }
         else if((align & H_RIGHT) != 0) {
            // width is actually one less than the box width
            nbox.x += nbox.width - psize.width;
            nbox.width = psize.width;
         }
         else if((align & H_CURRENCY) != 0) {
         }
         else {
            nbox.width = psize.width;
         }
      }

      if(psize.height < nbox.height) {
         if((align & V_CENTER) != 0) {
            nbox.y += (nbox.height - psize.height) / 2;
            nbox.height = psize.height;
         }
         else if((align & V_BOTTOM) != 0) {
            nbox.y += nbox.height - psize.height;
            nbox.height = psize.height;
         }
         else {
            nbox.height = psize.height;
         }
      }

      return nbox;
   }

   /**
    * Align a rectangle area inside another rectangle area according
    * to the alignment flag. This method is used internally to handle
    * cell alignment. It can be used as a general purpose utility
    * method.
    * @param box outer rectangle area.
    * @param psize inner rectangle area.
    * @param align alignment flag.
    * @return aligned rectangle area position and size.
    */
   public static Bounds alignCell(Bounds box, Size psize, int align) {
      Bounds nbox = new Bounds(box.x, box.y, box.width, box.height);

      if(psize.width < nbox.width) {
         if((align & H_CENTER) != 0) {
            nbox.x += (nbox.width - psize.width) / 2;
            nbox.width = psize.width;
         }
         else if((align & H_RIGHT) != 0) {
            // width is actually one less than the box width
            nbox.x += nbox.width - psize.width;
            nbox.width = psize.width;
         }
         else if((align & H_LEFT) != 0) {
            nbox.width = psize.width;
         }
         else if((align & H_CURRENCY) != 0) {
         }
      }

      if(psize.height < nbox.height) {
         if((align & V_CENTER) != 0) {
            nbox.y += (nbox.height - psize.height) / 2;
            nbox.height = psize.height;
         }
         else if((align & V_BOTTOM) != 0) {
            nbox.y += nbox.height - psize.height;
            nbox.height = psize.height;
         }
         else if((align & V_TOP) != 0) {
            nbox.height = psize.height;
         }
      }

      return nbox;
   }

   /**
    * Draw a rectangle with the specified line style.
    * @param g graphics content.
    * @param x left x coordinate.
    * @param y upper y coordinate.
    * @param w rectangle width.
    * @param h rectangle height.
    * @param style line style.
    */
   public static void drawRect(Graphics g, float x, float y, float w,
                               float h, int style) {
      float w2 = getLineAdjustment(g);
      float adj = getLineWidth(style);

      w -= adj;
      h -= adj;

      drawHLine(g, y + w2, x, x + w, style, 0, style); // top
      drawHLine(g, y + h + w2, x, x + w + w2, style, style, 0); // bottom
      drawVLine(g, x + w2, y, y + h, style, 0, style); // left
      drawVLine(g, x + w + w2, y, y + h + w2, style, style, 0); // right

      adj = (adj > 1) ? adj - 0.5f : adj / 2f;

      // fix lower-left corner
      drawVLine(g, x + w2, y + h, y + h + adj, style, 0, style);
      // fix upper-right corner
      drawHLine(g, y + w2, x + w, x + w + adj, style, 0, style);
      // fix lower-right corner
      drawVLine(g, x + w + w2, y + h + w2, y + h + adj, style, style, 0);
      drawHLine(g, y + h + w2, x + w + w2, x + w + adj, style, style, 0);
   }

   /**
    * Draw a rectangle with the specified line style.
    * @param g graphics content.
    * @param x left x coordinate.
    * @param y upper y coordinate.
    * @param w rectangle width.
    * @param h rectangle height.
    * @param top top border style.
    * @param left left border style.
    * @param bottom bottom border style.
    * @param right right border style.
    */
   public static void drawRect(Graphics g, float x, float y, float w,
                               float h, int top, int left, int bottom,
                               int right) {
      float w2 = getLineAdjustment(g);
      float bottomw = getLineWidth(bottom);
      float rightw = getLineWidth(right);

      w -= rightw;
      h -= bottomw;

      drawHLine(g, y + w2, x, x + w, top, 0, left); // top
      drawHLine(g, y + h + w2, x, x + w, bottom, left, 0); // bottom
      drawVLine(g, x + w2, y, y + h, left, 0, top); // left
      drawVLine(g, x + w + w2, y, y + h, right, top, 0); // right

      float badj = (bottomw > 1) ? bottomw - 0.5f : bottomw / 2f;
      float radj = (rightw > 1) ? rightw - 0.5f : rightw / 2f;

      // fix lower-left corner
      drawVLine(g, x + w2, y + h, y + h + badj, left, 0, bottom);
      // fix upper-right corner
      drawHLine(g, y + w2, x + w, x + w + radj, top, 0, right);
      // fix lower-right corner
      drawVLine(g, x + w + w2, y + h, y + h + badj, right, bottom, 0);
      drawHLine(g, y + h + w2, x + w, x + w + radj, bottom, right, 0);
   }

   /**
    * Draw a rectangle with the specified line style.
    * @param pg page context.
    * @param x left x coordinate.
    * @param y upper y coordinate.
    * @param w rectangle width.
    * @param h rectangle height.
    * @param style line style.
    */
   public static void drawRect(StylePage pg, final float x, final float y,
                               final float w, final float h, final int style,
                               final Color color) {
      pg.addPaintable(new BasePaintable(null) {
         @Override
         public void paint(Graphics g) {
            Color oc = g.getColor();

            if(color != null) {
               g.setColor(color);
            }

            drawRect(g, x, y, w, h, style);
            g.setColor(oc);
         }

         @Override
         public Rectangle getBounds() {
            return new Rectangle(round(x), round(y), round(w), round(h));
         }

         @Override
         public void setLocation(Point loc) {
         }

         @Override
         public Point getLocation() {
            return new Point(round(x), round(y));
         }

         @Override
         public ReportElement getElement() {
            return null;
         }
      });
   }

   /**
    * Round double to int.
    */
   public static int round(double v) {
      return (int) Math.ceil(v - 0.01);
   }

   /**
    * Get the width of a line style.
    */
   public static float getLineWidth(int style) {
      if(style == -1) {
         return 0;
      }

      return g2d.getLineWidth(style);
   }

   /**
    * Get a cursor from an image resource file.
    * @param res resource file.
    */
   public static Cursor getCursor(String res) {
      return g2d.getCursor(res);
   }


   /**
    * Reorder the characters in the string according to bidi rules if
    * applicable. This is only supported in jdk 1.4 and later.
    */
   public static String reorderBidi(String str) {
      return g2d.reorderBidi(str);
   }

   public static String processBidi(String str) {
      // handle bidi if necessary
      str = Common.reorderBidi(str);

      // Bug #59512, check if it contains arabic characters and if so reshape it
      if(ArabicTextUtil.getInstance().containsArabic(str)) {
         str = ArabicTextUtil.getInstance().createSubstituteString(str);
      }

      return str;
   }

   public static boolean requiresBidi(String str) {
      return str != null && Bidi.requiresBidi(str.toCharArray(), 0, str.length());
   }

   public static String getFormatType(Format fmt) {
      if(fmt == null) {
         return UNKNOWNFORMAT;
      }

      if(fmt instanceof SimpleDateFormat) {
         return SIMPLEDATEFORMAT;
      }
      else if(fmt.equals(NumberFormat.getCurrencyInstance())) {
         return CURRENCYFORMAT;
      }
      else if(fmt.equals(NumberFormat.getPercentInstance())) {
         return PERCENTFORMAT;
      }
      else if(fmt instanceof DecimalFormat) {
         return DECIMALFORMAT;
      }
      else if(fmt instanceof ChoiceFormat) {
         return CHOICEFORMAT;
      }
      else if(fmt instanceof MessageFormat) {
         return MESSAGEFORMAT;
      }
      else if(fmt instanceof inetsoft.util.MessageFormat) {
         return MESSAGEFORMAT;
      }

      return UNKNOWNFORMAT;
   }

   public static String getFormatPattern(Format fmt) {
      if(fmt == null) {
         return null;
      }

      if(fmt instanceof SimpleDateFormat) {
         return ((SimpleDateFormat) fmt).toPattern();
      }
      else if(fmt.equals(NumberFormat.getCurrencyInstance()) ||
         fmt.equals(NumberFormat.getPercentInstance()))
      {
         return "";
      }
      else if(fmt instanceof DecimalFormat) {
         return ((DecimalFormat) fmt).toPattern();
      }
      else if(fmt instanceof ChoiceFormat) {
         return ((ChoiceFormat) fmt).toPattern();
      }
      else if(fmt instanceof MessageFormat) {
         return ((MessageFormat) fmt).toPattern();
      }
      else if(fmt instanceof inetsoft.util.MessageFormat) {
         return ((inetsoft.util.MessageFormat) fmt).toPattern();
      }

      return fmt.toString();
   }

   /**
    * Convert the format type to XConstants formats.
    */
   public static String convertFormatType(String type) {
      if(MESSAGEFORMAT.equals(type)) {
         return XConstants.MESSAGE_FORMAT;
      }
      else if(PERCENTFORMAT.equals(type)) {
         return XConstants.PERCENT_FORMAT;
      }
      else if(CURRENCYFORMAT.equals(type)) {
         return XConstants.CURRENCY_FORMAT;
      }
      else if(DATEFORMAT.equals(type)) {
         return XConstants.DATE_FORMAT;
      }
      else if(DECIMALFORMAT.equals(type)) {
         return XConstants.DECIMAL_FORMAT;
      }
      else if(SIMPLEDATEFORMAT.equals(type)) {
         return XConstants.DATE_FORMAT;
      }

      return null;
   }

   /**
    * Create a format from the format specification.
    */
   public static Format getFormat(String format, String format_spec) {
      if(format.equals(SIMPLEDATEFORMAT) || format.equals("SimpleDateFormat") ||
         format.equals(DATEFORMAT) || format.equals("DateFormat"))
      {
         return format_spec != null ? Tool.createDateFormat(format_spec) : Tool.createDateFormat("yyyy-MM-dd");
      }
      else if(format.equals(DECIMALFORMAT) || format.equals("DecimalFormat")) {
         return format_spec != null ? new DecimalFormat(format_spec) : NumberFormat.getInstance();
      }
      else if(format.equals(CHOICEFORMAT) || format.equals("ChoiceFormat")) {
         return format_spec != null ? new ChoiceFormat(format_spec) : NumberFormat.getInstance();
      }
      else if(format.equals(MESSAGEFORMAT) || format.equals("MessageFormat")) {
         return format_spec != null ? new inetsoft.util.MessageFormat(format_spec) : null;
      }
      else if(format.equals(CURRENCYFORMAT) || format.equals("CurrencyFormat")) {
         return NumberFormat.getCurrencyInstance();
      }
      else if(format.equals(PERCENTFORMAT) || format.equals("PercentFormat")) {
         return NumberFormat.getPercentInstance();
      }

      return null;
   }

   /**
    * Get the rounding option by using the string name of the options.
    */
   public static RoundingMode getRoundingByName(String round) {
      if(round == null) {
         return RoundingMode.HALF_EVEN;
      }
      else if(round.equals("ROUND_UP")) {
         return RoundingMode.UP;
      }
      else if(round.equals("ROUND_DOWN")) {
         return RoundingMode.DOWN;
      }
      else if(round.equals("ROUND_CEILING")) {
         return RoundingMode.CEILING;
      }
      else if(round.equals("ROUND_FLOOR")) {
         return RoundingMode.FLOOR;
      }
      else if(round.equals("ROUND_HALF_UP")) {
         return RoundingMode.HALF_UP;
      }
      else if(round.equals("ROUND_HALF_DOWN")) {
         return RoundingMode.HALF_DOWN;
      }
      else if(round.equals("ROUND_UNNECESSARY")) {
         return RoundingMode.UNNECESSARY;
      }
      else {
         return RoundingMode.HALF_EVEN;
      }
   }

   private static class ExportGraphicsKey extends RenderingHints.Key {
      public ExportGraphicsKey() {
         super(1000);
      }

      @Override
      public boolean isCompatibleValue(Object val) {
         return (val == VALUE_EXPORT_GRAPHICS_ON ||
                 val == VALUE_EXPORT_GRAPHICS_OFF);
      }
   }

   /**
    * Get wrap text height.
    * @param align alignment.
    */
   public static double getWrapTextHeight(String text, double textWidth, Font font, int align) {
      Bounds bounds = new Bounds(0, 0, (float)textWidth, 1);
      Vector lines = processText(text, bounds, align, true, font, new Bounds(),
                                        new Vector(), 0, Common.getFontMetrics(font), 0);
      double totalH = lines.size() * Common.getHeight(font);
      return totalH + 2;
   }

   /**
    * Rendering hint key that indicates whether a graphics context is being
    * used to export or print a report. The possible values are
    * {@link #VALUE_EXPORT_GRAPHICS_ON} and {@link #VALUE_EXPORT_GRAPHICS_OFF}.
    */
   public static final RenderingHints.Key EXPORT_GRAPHICS =
      new ExportGraphicsKey();

   /**
    * Value for the {@link #EXPORT_GRAPHICS} rendering hint that indicates that
    * a graphics context is being used to export or print a report.
    */
   public static final Object VALUE_EXPORT_GRAPHICS_ON = Boolean.TRUE;

   /**
    * Value for the {@link #EXPORT_GRAPHICS} rendering hint that indicates that
    * a graphics context is being used to generate an interactive report.
    */
   public static final Object VALUE_EXPORT_GRAPHICS_OFF = Boolean.FALSE;

   public static final String DECIMALFORMAT = "DecimalFMT";
   private static final String CHOICEFORMAT = "ChoiceFMT";
   private static final String MESSAGEFORMAT = "MessageFMT";
   private static final String SIMPLEDATEFORMAT = "SimpleDateFMT";
   private static final String DATEFORMAT = "DateFMT";
   private static final String CURRENCYFORMAT = "CurrentFMT";
   private static final String PERCENTFORMAT = "PercentFMT";
   private static final String UNKNOWNFORMAT = "UnknownFMT";
   private static final char GROUP_SYMBOL = '\u039E';
   private static final char SUMMARY_SYMBOL = '\u2211';
   private static final Gop g2d = Gop.getInstance();
   private static Method getTarget;

   private static final Logger LOG = LoggerFactory.getLogger(Common.class);
}
