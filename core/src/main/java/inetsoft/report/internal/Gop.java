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

import inetsoft.graph.internal.GTool;
import inetsoft.report.*;
import inetsoft.report.painter.ImagePainter;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.awt.*;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageObserver;
import java.io.OutputStream;
import java.util.*;

/**
 * The Gop class provides an API for operations that are different in
 * JDK1.1 and JDK1.2. It checks which environment the program is running,
 * and instantiate a Gop or a Gop2D to provide the functionality
 * appropriate for the environment. This is the primary mechanism to
 * support compatibility on both JDK1.1 and JDK1.2.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Gop implements StyleConstants {
   private static Gop gop = null;
   /**
    * Get an instance of Gop object.
    */
   public static Gop getInstance() {
      if(gop == null) {
         try {
            Class.forName("javax.print.PrintServiceLookup");
            gop = (Gop) Class.forName(
               "inetsoft.report.internal.j2d.Gop1_4").newInstance();
         }
         catch(Throwable ex) {
            try {
               Class.forName("java.awt.JobAttributes");
               gop = (Gop) Class.forName(
                  "inetsoft.report.internal.j2d.Gop1_3").newInstance();
            }
            catch(Throwable e) {
               try {
                  Class.forName("java.awt.Graphics2D");
                  gop = (Gop) Class.forName(
                     "inetsoft.report.internal.j2d.Gop2D").newInstance();
               }
               catch(Throwable e2) {
                  gop = new Gop();
               }
            }
         }
      }

      return gop;
   }

   /**
    * Check if the runtime is Java2.
    */
   public boolean isJava2() {
      return false;
   }

   /**
    * Get the the line adjustment. For integer based coordinate system,
    * this is always 0. For fraction coordinate system, this is half
    * of a one point line width.
    */
   public float getLineAdjustment(Graphics g) {
      return 0;
   }

   /**
    * Check if the graphics is a Graphics2D object.
    */
   public boolean isGraphics2D(Graphics g) {
      return false;
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
   public void drawHLine(Graphics g, float y, float x1, float x2, int style,
                         int top, int bottom) {
      float w = getLineWidth(style);
      float t1 = Math.min(x1, x2), t2 = Math.max(x1, x2);

      x1 = t1;
      x2 = t2;

      if((style & SOLID_MASK) != 0) {
         // @by larryl, for solid lines, don't apply the adjust for horizontal
         // lines so the horizontal lines covers the vertical lines, which is
         // more visually pleasant than having the vertical line on top
         //float adj = Math.min(getLineWidth(top), getLineWidth(bottom));
         float adj = 0;

         if((style & DASH_MASK) != 0) {
            boolean dot = true;
            int dlen = (style & DASH_MASK) >> 4;

            for(float x = x1 + adj; x <= x2; x += dlen, dot = !dot) {
               if(dot) {
                  drawLine(g, x, y, Math.min(x2, x + dlen - 1), y, THIN_LINE);
               }
            }
         }
         else {
            int style2 = ((style & FRACTION_WIDTH_MASK) != 0) ? style :
               THIN_LINE;

            if(g instanceof PDFPrinter) {
               PDFPrinter pdf = (PDFPrinter) g;
               Stroke ostroke = pdf.getStroke();
               Stroke stroke = GTool.getStroke(style2);
               boolean needed = ostroke != null ?
                  !ostroke.equals(stroke) : false;

               if(needed) {
                  pdf.setStroke(stroke);
               }

               //@by mikec.
               //pdf printer should be able to take care of the
               //width of the line, should set the y to the middle of the line.
               pdf.setLineWidth(w);
               pdf.drawLine(x1, y + w / 2, x2, y + w / 2);
               pdf.setLineWidth(1);

               if(needed) {
                  pdf.setStroke(ostroke);
               }
            }
            else {
               double inc = 1;

               if(Common.isGraphics2D(g) &&
                  ((Graphics2D) g).getTransform() != null)
               {
                  double scaleY = ((Graphics2D) g).getTransform().getScaleY();

                  if(scaleY != 0) {
                     inc = 1 / Math.abs(scaleY);
                  }
               }

               for(double i = 0; i < w; i += inc) {
                  drawLine(g, x1 + adj, y + i, x2, y + i, style2);
               }
            }
         }
      }
      else if(style != 0) {
         Color c = g.getColor();

         if(c == null) {
            c = new Color(0, 0, 0);
         }

         if((style & RAISED_MASK) != 0) {
            g.setColor(Tool.brighter(c));
         }
         else if((style & LOWERED_MASK) != 0) {
            g.setColor(Tool.darker(c));
         }

         float adj = getLineWidth(top);
         adj = (adj > 0) ? (adj - 1) : adj;

         if(x1 + adj < x2) {
            drawLine(g, x1 + adj, y, x2, y, THIN_LINE);
         }

         if((style & RAISED_MASK) != 0) {
            g.setColor(Tool.darker(c));
         }
         else if((style & LOWERED_MASK) != 0) {
            g.setColor(Tool.brighter(c));
         }

         adj = getLineWidth(bottom);
         adj = (adj > 0) ? (adj - 1) : adj;

         if(x1 + adj < x2) {
            drawLine(g, x1 + adj, y + w - 1, x2, y + w - 1, THIN_LINE);
         }

         g.setColor(c);
      }
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
   public void drawVLine(Graphics g, float x, float y1, float y2, int style,
                         int left, int right) {
      float w = getLineWidth(style);
      float t1 = Math.min(y1, y2), t2 = Math.max(y1, y2);

      y1 = t1;
      y2 = t2;

      if((style & SOLID_MASK) != 0) {
         float adj = Math.min(getLineWidth(left), getLineWidth(right));

         if((style & DASH_MASK) != 0) {
            boolean dot = true;
            int dlen = (style & DASH_MASK) >> 4;

            for(float y = y1 + adj; y <= y2; y += dlen, dot = !dot) {
               if(dot) {
                  drawLine(g, x, y, x, Math.min(y2, y + dlen - 1), THIN_LINE);
               }
            }
         }
         else {
            int style2 = ((style & FRACTION_WIDTH_MASK) != 0) ?
               style : THIN_LINE;

            if(g instanceof PDFPrinter) {
               PDFPrinter pdf = (PDFPrinter) g;
               Stroke ostroke = pdf.getStroke();
               Stroke stroke = GTool.getStroke(style2);
               boolean needed = ostroke != null ?
                  !ostroke.equals(stroke) : false;

               if(needed) {
                  pdf.setStroke(stroke);
               }

               pdf.setLineWidth(w);
               pdf.drawLine(x + w / 2, y1 + adj, x + w / 2, y2);
               pdf.setLineWidth(1);

               if(needed) {
                  pdf.setStroke(ostroke);
               }
            }
            else {
               double inc = 1;

               if(Common.isGraphics2D(g) &&
                  ((Graphics2D) g).getTransform() != null)
               {
                  double scaleX = ((Graphics2D) g).getTransform().getScaleX();

                  if(scaleX != 0) {
                     inc = 1 / Math.abs(scaleX);
                  }
               }

               for(double i = 0; i < w; i += inc) {
                  drawLine(g, x + i, y1 + adj, x + i, y2, style2);
               }
            }
         }
      }
      else if(style != 0) {
         Color c = g.getColor();

         if((style & RAISED_MASK) != 0) {
            g.setColor(Tool.brighter(c));
         }
         else if((style & LOWERED_MASK) != 0) {
            g.setColor(Tool.darker(c));
         }

         // make sure no gap in intersection
         float adj = getLineWidth(left);
         adj = (adj > 0) ? (adj - 1) : adj;

         if(y1 + adj < y2) {
            drawLine(g, x, y1 + adj, x, y2, THIN_LINE);
         }

         if((style & RAISED_MASK) != 0) {
            g.setColor(Tool.darker(c));
         }
         else if((style & LOWERED_MASK) != 0) {
            g.setColor(Tool.brighter(c));
         }

         adj = getLineWidth(right);
         adj = (adj > 0) ? (adj - 1) : adj;

         if(y1 + adj < y2) {
            drawLine(g, x + w - 1, y1 + adj, x + w - 1, y2, THIN_LINE);
         }

         g.setColor(c);
      }
   }

   /**
    * Draw a styled polygon.
    */
   public void drawPolygon(Graphics g, Polygon p, int style) {
      drawPolygon(g, p);
   }

   /**
    * Draw a plain polygon.
    */
   public void drawPolygon(Graphics g, Polygon p) {
      g.drawPolygon(p);
   }

   /**
    * Draw a styled oval.
    */
   public void drawOval(Graphics g, float x, float y, float w, float h,
                        int style) {
      drawArc(g, x, y, w, h, 0, 360, style);
   }

   /**
    * Draw a plain oval.
    */
   public void drawOval(Graphics g, float x, float y, float w, float h) {
      g.drawOval(round(x), round(y), round(w), round(h));
   }

   /**
    * Draw an arc with the specified line style.
    */
   public void drawArc(Graphics g, float x, float y, float w, float h,
                       float startAngle, float angle, int style) {
      drawArc(g, round(x), round(y), round(w), round(h), round(startAngle),
              round(angle));
   }

   /**
    * Draw an arc with the plain line style.
    */
   public void drawArc(Graphics g, float x, float y, float w, float h,
                       float startAngle, float angle) {
      g.drawArc(round(x), round(y), round(w), round(h), round(startAngle),
                round(angle));
   }

   /**
    * Draw a plain 1 pt line.
    */
   public void drawLine(Graphics g, double x1, double y1, double x2, double y2) {
      g.drawLine(round(x1), round(y1), round(x2), round(y2));
   }

   /**
    * Draw a styled line.
    */
   public void drawLine(Graphics g, double x1, double y1, double x2,
                        double y2, int style) {
      drawLine(g, round(x1), round(y1), round(x2), round(y2));
   }

   /**
    * Draw a styled polyline.
    */
   public void drawPolyline(Graphics g, float[] xs, float[] ys, int np,
                            int style) {
      int[] xs2 = new int[np];
      int[] ys2 = new int[np];

      for(int i = 0; i < np; i++) {
         xs2[i] = (int) Math.round(xs[i]);
         ys2[i] = (int) Math.round(ys[i]);
      }

      g.drawPolyline(xs2, ys2, np);
   }

   /**
    * Fill a rectanglar area.
    * @param x left x coordinate.
    * @param y upper y coordinate.
    * @param w rectangle width.
    * @param h rectangle height.
    */
   public void fillRect(Graphics g, float x, float y, float w, float h) {
      Bounds nbox = (new Bounds(x, y, w, h)).round();

      g.fillRect(round(nbox.x), round(nbox.y), round(nbox.width),
                 round(nbox.height));
   }

   /**
    * Fill an arc with the specified fill pattern.
    */
   public void fillArc(Graphics g, float x, float y, float w, float h,
                       float startAngle, float angle, Object fill) {
      try {
         g.setColor((Color) fill);
      }
      catch(Exception e) {
         g.setColor(Color.gray);
      }

      g.fillArc((int) x, (int) y, (int) w, (int) h, (int) startAngle,
                round(angle));
   }

   /**
    * Fill an area with the specified fill pattern.
    * @param area shape to fill.
    * @param fill fill pattern, a Color or Paint (JDK1.2 only) object.
    */
   public void fill(Graphics g, Shape area, Object fill) {
      Color c = g.getColor();

      try {
         g.setColor((Color) fill);
      }
      catch(Exception e) {
         g.setColor(Color.gray);
      }

      if(area instanceof Rectangle) {
         Rectangle rect = (Rectangle) area;

         g.fillRect(rect.x, rect.y, rect.width, rect.height);
      }
      else if(area instanceof Polygon) {
         g.fillPolygon((Polygon) area);
      }

      g.setColor(c);
   }

   /**
    * Set the clip area.
    * @param g graphics context.
    * @param box clipping bounds.
    */
   public void setClip(Graphics g, Bounds box) {
      Bounds nbox = box.round();

      g.setClip(round(nbox.x), round(nbox.y), round(nbox.width),
                round(nbox.height));
   }

   /**
    * Clip the current clip area.
    * @param g graphics context.
    * @param box clipping bounds.
    */
   public void clipRect(Graphics g, Bounds box) {
      Bounds nbox = box.round();

      g.clipRect((int) nbox.x, (int) nbox.y, round(nbox.width),
                 round(nbox.height));
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
   public void drawImage(Graphics g, Image img, float x, float y,
                         float w, float h, ImageObserver observer) {
      Dimension d = new Dimension(img.getWidth(null), img.getHeight(null));
      String prop = SreeEnv.getProperty("StyleReport.ditherImage");

      // unwrap image
      if(img instanceof MetaImage) {
         // JPEG is written to PDF as is
         if(!(g instanceof CustomGraphics) ||
            !((CustomGraphics) g).isSupported(CustomGraphics.JPEG_EXPORT))
         {
            img = ((MetaImage) img).getImage();
         }
      }

      if(w > 0 && h > 0 && d.width != round(w) && d.height != round(h) &&
         (prop != null && prop.equals("true")))
      {
         img = img.getScaledInstance(round(w), round(h), Image.SCALE_SMOOTH);
      }

      g.drawImage(img, round(x), round(y), round(w), round(h), observer);
   }

   /**
    * Rotate the graphics coordinate.
    */
   public void rotate(Graphics g, double angle) {
   }

   /**
    * Calculate the font height. This is more accurate than FontMetrics.getHeight as it avoids integer rounding.
    * @param font font object.
    * @return font height in pixels.
    */
   public float getHeight(Font font) {
      // Bug #51234 FontMetrics#getHeight on the regular-sized font will have integer rounding that
      // can result in the height being wrong by up to 2px. Multiplying by a factor and then
      // dividing the resultant height by that factor
      // gives a much more accurate result.
      final float factor = 1000F;
      final Font largeFont = font.deriveFont(font.getSize2D() * factor);
      final FontMetrics fm = Common.getFractionalFontMetrics(largeFont);
      float h = fm.getHeight() / factor;

      if(font instanceof StyleFont) {
         StyleFont sfont = (StyleFont) font;

         if((sfont.getStyle() & StyleFont.UNDERLINE) != 0) {
            return h + getLineWidth(sfont.getUnderlineStyle());
         }
      }

      return novratio ? h : adjustHeight(font, h);
   }

   /**
    * Calculate the width of the string.
    * @param str string text.
    * @param fn font.
    * @param fm font metrics.
    */
   public float stringWidth(String str, Font fn, FontMetrics fm) {
      if(fn == null) {
         fn = fm.getFont();
      }

      return adjustSpacing(fn, fm.stringWidth(str));
   }

   /**
    * Get the font ascent value.
    * @param fm font metrics.
    * @return font ascent.
    */
   public float getAscent(FontMetrics fm) {
      return fm.getAscent();
   }

   /**
    * Get the font ascent value.
    * @param font font.
    * @return font ascent.
    */
   public float getAscent(Font font) {
      // Bug #51234 FontMetrics#getAscent on the regular-sized font will have integer rounding that
      // can result in the ascent being wrong by up to 2px. Multiplying by a factor and then
      // dividing the resultant ascent by that factor
      // gives a much more accurate result.
      final float factor = 1000F;
      final Font largeFont = font.deriveFont(font.getSize2D() * factor);
      final FontMetrics fm = Common.getFractionalFontMetrics(largeFont);
      return fm.getAscent() / factor;
   }

   /**
    * Get the font descent value.
    * @param font font.
    * @return font descent.
    */
   public float getDescent(Font font) {
      // Bug #51234 FontMetrics#getDescent on the regular-sized font will have integer rounding that
      // can result in the descent being wrong by up to 2px. Multiplying by a factor and then
      // dividing the resultant descent by that factor
      // gives a much more accurate result.
      final float factor = 1000F;
      final Font largeFont = font.deriveFont(font.getSize2D() * factor);
      final FontMetrics fm = Common.getFractionalFontMetrics(largeFont);
      return fm.getDescent() / factor;
   }

   /**
    * Draw string on graphics.
    * @param g graphics context.
    * @param str string contents.
    * @param x x coordinate.
    * @param y y coordinate.
    */
   public void drawString(Graphics g, String str, float x, float y) {
      g.drawString(str, round(x), round(y));
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
    * @param cliph clip height.
    * @param fg foreground color.
    * @param bg background color.
    * @param bufy already consumed height.
    * @param bufh available paintable height in graphics.
    */
   public synchronized void paint(Graphics g, float x, float y, float w,
                                  float h, Painter painter, float sx,
                                  float sy, float sw, float sh,
                                  float clipw, float cliph,
                                  Color fg, Color bg, float bufy, float bufh) {
      // if no scale necessary, don't buffer the image
      if(w == sw && h == sh) {
         Shape clip = g.getClip();
         Color color = g.getColor();

         g.translate(round(x), round(y));
         g.clipRect(0, 0, round(w), round(h));

         // jdk1.1 does not handle transparency correctly, always fill the
         // background
         g.setColor((bg == null) ? Color.white : bg);
         g.fillRect(0, 0, round(w), round(h));

         g.setColor(fg);
         paintPainter(painter, g, (int) sx, (int) sy, (int) sw, (int) sh,
                      bufy, bufh);

         g.translate(-round(x), -round(y));
         g.setClip(clip);
         g.setColor(color);
      }
      // special handling for image painter, this is mainly done to optimize
      // the pdf generation so the images can be cached and reused in pdf
      else if(painter instanceof ImagePainter) {
         Image img = ((ImagePainter) painter).getImage();
         boolean fit = ((ImagePainter) painter).isFit();

         if(img instanceof MetaImage) {
            img = ((MetaImage) img).getImage();
         }

         // logic taken from Gop2D.paint()
         if(img != null) {
            Shape clip = g.getClip();
            Color color = g.getColor();

            g.translate((int) x, (int) y);
            g.clipRect(0, 0, (int) w, (int) h);

            if(bg != null) {
               g.setColor(bg);
               g.fillRect(0, 0, (int) w, (int) h);
            }

            float xscale = w / clipw;
            float yscale = h / cliph;

            // manual scale, same as Gop2D
            if(fit) {
               Common.drawImage(g, img, sx * xscale, sy * yscale, sw * xscale,
                  sh * yscale, null);
            }
            else {
               Common.drawImage(g, img, sx * xscale, sy * yscale, 0, 0, null);
            }

            g.translate(-(int) x, -(int) y);
            g.setClip(clip);
            g.setColor(color);
         }
      }
      // handle scaling by painting to a buffer and then scale
      else {
         Dimension bSize = new Dimension(round(clipw), round(cliph));
         Image buffer = Tool.createImage(bSize.width, bSize.height, false);
         Graphics ng = buffer.getGraphics();

         ng.setClip(0, 0, bSize.width, bSize.height);

         ng.setColor((bg == null) ? Color.white : bg);
         ng.fillRect(0, 0, bSize.width, bSize.height);

         ng.setColor(fg);
         paintPainter(painter, ng, round(sx), round(sy), round(sw), round(sh),
                      bufy, bufh);
         ng.dispose();

         if(g instanceof CustomGraphics) {
            Common.drawImage(g, buffer, x, y, w, h, null);
         }
         // off-screen image does not print well in jdk1.1
         else {
            Common.drawImage(g,
               Tool.getToolkit().createImage(buffer.getSource()), x, y, w, h,
               null);
         }
      }
   }

   /**
    * Call paint() on painter.
    */
   protected void paintPainter(Painter painter, Graphics g, int x, int y,
                               int w, int h, float bufy, float bufh) {
      if(painter instanceof ExpandablePainter) {
         ((ExpandablePainter) painter).paint(g, x, y, w, h, bufy, bufh);
      }
      else {
         painter.paint(g, x, y, w, h);
      }
   }

   /**
    * Return all font names supported in this environment.
    * @return all available fonts.
    */
   public String[] getAllFonts() {
      if(fonts == null) {
         fonts = Tool.getToolkit().getFontList();
      }

      return fonts;
   }

   /**
    * Get the font face name.
    */
   public String getFontName(Font fn) {
      return fn.getName();
   }

   /**
    * Get the postscript font name.
    */
   public String getPSName(Font fn) {
      return fn.getName();
   }

   /**
    * Paint the painter by rotating it 90 degrees clockwise.
    */
   public synchronized void paintRotate(Painter painter, Graphics g, float x,
                                        float y, float w, float h,
                                        int rotation) {
      x = round(x);
      y = round(y);
      w = round(w);
      h = round(h);

      Image buffer = Tool.createImage((int) h, (int) w, true);
      Graphics bg = buffer.getGraphics();

      painter.paint(bg, 0, 0, (int) h, (int) w);
      bg.dispose();

      RotateFilter filter = new RotateFilter();
      Image filtered = Tool.getToolkit().
         createImage(new FilteredImageSource(buffer.getSource(), filter));

      g.drawImage(filtered, (int) x, (int) y, null);
   }

   /**
    * This method is called before a page is printed for any initialization.
    */
   public void startPage(Graphics g, StylePage page) {
      // this is done to avoid a bug in JDK1.1 PS printing where a null
      // clip area causes an exception
      // also in visual age 2.0, a clip bounds of MAX_INTEGER size is
      // returned initially and could not be passed to setClip() later
      Dimension size = page.getPageDimension();
      Rectangle box = g.getClipBounds();
      String prop = SreeEnv.getProperty("os.name");
      boolean win9x = g.getClass().getName().indexOf("PeekG") < 0 &&
         (prop != null) && prop.startsWith("Windows 9");

      // win9x passes in wrong clipping sometimes (in jdk1.1 only)
      if(win9x && !Common.isGraphics2D(g) || g.getClip() == null ||
         box == null ||
         box.x < 0 && box.y < 0 && box.width > size.width &&
         box.height > size.height)
      {
         g.setClip(new Rectangle(0, 0, size.width, size.height));
      }
   }

   /**
    * Write an image to an output stream.
    */
   public void writeJPEG(Image img, OutputStream output) {
      JpegEncoder jpeg = new JpegEncoder(img, 80, output);

      jpeg.Compress();
   }

   /**
    * Round double to int.
    */
   public static int round(double v) {
      return Common.round(v);
   }

   /**
    * Adjust the string width using user specified ratio.
    */
   protected float adjustSpacing(Font font, float width) {
      if(font == null || noratio) {
         return width;
      }

      if(fontratios == null) {
         fontratios = new HashMap();

         // get the user defined font ratio to adjust for pdf output, e.g.
         // pdf.font.ratio=MS Hei:1.1;Algerian-bolditalic:1.02
         String prop = SreeEnv.getProperty("font.ratio.x");

         if(prop == null) {
            // obsolete
            prop = SreeEnv.getProperty("pdf.font.ratio");
         }

         if(prop != null) {
            parseRatios(fontratios, prop);
         }

         noratio = fontratios.size() == 0;
      }

      if(!noratio) {
         Double ratio = (Double) fontratios.get(getNameWithStyle(font));
         return (ratio == null) ? width :
            (float) (width * ratio.doubleValue());
      }

      return width;
   }

   /**
    * Adjust the string width using user specified ratio.
    */
   protected final float adjustHeight(Font font, float height) {
      if(vfontratios == null) {
         vfontratios = new HashMap();

         // get the user defined font ratio to adjust for pdf output, e.g.
         // font.ratio.y=MS Hei:1.1;Algerian-italic:1.02
         String prop = SreeEnv.getProperty("font.ratio.y");

         if(prop != null) {
            parseRatios(vfontratios, prop);
         }

         novratio = vfontratios.size() == 0;
      }

      if(!novratio) {
         Double ratio = (Double) vfontratios.get(getNameWithStyle(font));
         return (ratio == null) ? height :
            (int) (height * ratio.doubleValue());
      }

      return height;
   }

   private String getNameWithStyle(Font font) {
      String name = font.getName();

      if((font.getStyle() & Font.BOLD) != 0 &&
         (font.getStyle() & Font.ITALIC) != 0)
      {
         return name + "-bolditalic";
      }
      else if((font.getStyle() & Font.BOLD) != 0) {
         return name + "-bold";
      }
      else if((font.getStyle() & Font.ITALIC) != 0) {
         return name + "-italic";
      }

      return name;
   }

   /**
    * Parse ratio string, in the format as:
    * font-name:ratio;font-name:ration
    */
   protected void parseRatios(HashMap fontratios, String prop) {
      String[] pairs = Tool.split(prop, ';');

      for(int i = 0; i < pairs.length; i++) {
         String[] pair = Tool.split(pairs[i], ':');

         if(pair.length == 2 && pair[0].length() > 0) {
            try {
               Double ratio = Double.valueOf(pair[1]);
               String name = pair[0];

               if(name.indexOf('-') < 0) {
                  // add all font styles
                  fontratios.put(name + "-bold", ratio);
                  fontratios.put(name + "-italic", ratio);
                  fontratios.put(name + "-bolditalic", ratio);
               }

               fontratios.put(name, ratio);
            }
            catch(Exception e) {
            }
         }
      }
   }

   /**
    * Get the width of a line style.
    */
   public float getLineWidth(int style) {
      return (style & StyleConstants.WIDTH_MASK) + (float)
         Math.ceil(((style & StyleConstants.FRACTION_WIDTH_MASK) >> 16) / 16f);
   }

   /**
    * Get a cursor from an image resource file.
    * @param res resource file.
    */
   public Cursor getCursor(String res) {
      return new Cursor(Cursor.DEFAULT_CURSOR);
   }

   /**
    * Reorder the characters in the string according to bidi rules if
    * applicable. This is only supported in jdk 1.4 and later.
    */
   public String reorderBidi(String str) {
      return str;
   }

   static String[] fonts = null; // font list
   HashMap fontratios = null; // font name -> spacing ratio
   HashMap vfontratios = null; // font name -> height ratio
   boolean noratio = false;
   boolean novratio = false;
}
