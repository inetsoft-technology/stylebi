/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.internal.j2d;

import inetsoft.graph.internal.GTool;
import inetsoft.report.*;
import inetsoft.report.internal.*;
import inetsoft.report.painter.HTMLSource;
import inetsoft.report.painter.ImagePainter;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import inetsoft.util.graphics.SVGSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.*;
import java.awt.image.*;
import java.awt.print.*;
import java.io.OutputStream;
import java.util.*;

/**
 * Gop2D is the JDK1.2 (Java2D) implementation of the same operations in
 * Gop class.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Gop2D extends Gop {
   public Gop2D() {
      // This property is for Data Solutions, which uses linemetrics to
      // calculate the string position and need the use the same calculation
      // in reports so the positions match
      String prop = SreeEnv.getProperty("report.stringwidth.fontmetrics");

      stringWidthFontMetrics = prop.equals("true");
   }

   /**
    * Check if the runtime is Java2.
    */
   @Override
   public boolean isJava2() {
      return true;
   }

   /**
    * Calculate the font height. This is same as the FontMetrics.getHeight()
    * for regular fonts. If the font is a StyleFont, the height is adjusted
    * for the underline accordingly.
    * @param font font object.
    * @param fm font metrics or null.
    * @return font height in pixels.
    */

   /*
    * jdk 1.2 bug, see drawString()
    public float getHeight(Font font, FontMetrics fm) {
    LineMetrics lm = font.getLineMetrics("M", fontRenderContext);
    float h = lm.getAscent() + lm.getDescent();

    if(font instanceof StyleFont) {
    StyleFont sfont = (StyleFont) font;

    if((sfont.getStyle() & StyleFont.UNDERLINE) != 0) {
    return h + getLineWidth(sfont.getLineStyle()) - 1;
    }
    }

    return h;
    }
    */

   /**
    * Calculate the width of the string.
    * @param str string text.
    * @param fn font.
    * @param fm font metrics.
    */
   @Override
   public float stringWidth(String str, Font fn, FontMetrics fm) {
      if(str == null || str.length() == 0) {
         return 0f;
      }

      //@by mikec, bug1192044667595
      // use text layout will cause a little inconsistent between
      // screen preview and pdf export for customer presenter.
      // preview with overlap and pdf not.
      // use font metrics string width will make them consistent,
      // both no overlap.
      float w;

      if(fn == null) {
         fn = fm.getFont();
      }

      if(stringWidthFontMetrics) {
         w = fm.stringWidth(str);
      }
      else {
         w = (new TextLayout(str, fn, fontRenderContext)).getAdvance();
      }

      return adjustSpacing(fn, w);
   }

   /**
    * Get the font ascent value.
    * @param fm font metrics.
    * @return font ascent.
    */

   /*
    * jdk 1.2 bug, see drawString()
    public float getAscent(FontMetrics fm) {
    LineMetrics lm = fm.getFont().getLineMetrics("M", fontRenderContext);
    return lm.getAscent();
    }
    */

   /**
    * Set the clip area.
    * @param g graphics context.
    * @param box clipping bounds.
    */
   @Override
   public void setClip(Graphics g, Bounds box) {
      try {
         Graphics2D g2d = (Graphics2D) g;

         g2d.setClip(new Rectangle2D.Float(box.x, box.y, box.width,
                                           box.height));
      }
      catch(Exception e) {
         super.setClip(g, box);
      }
   }

   /**
    * Clip the current clip area.
    * @param g graphics context.
    * @param box clipping bounds.
    */
   @Override
   public void clipRect(Graphics g, Bounds box) {
      try {
         Graphics2D g2d = (Graphics2D) g;

         g2d.clip(new Rectangle2D.Float(box.x, box.y, box.width, box.height));
      }
      catch(Exception e) {
         super.clipRect(g, box);
      }
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
   @Override
   public void drawImage(Graphics g, Image img, float x, float y,
                         float w, float h, ImageObserver observer) {
      try {
         Graphics2D g2d = (Graphics2D) g;
         Dimension d = new Dimension(img.getWidth(null), img.getHeight(null));
         AffineTransform trans = new AffineTransform();

         trans.translate(x, y);

         // unwrap image
         if(img instanceof MetaImage) {
            // JPEG is written to PDF as is
            if(!(g instanceof CustomGraphics) ||
               !((CustomGraphics) g).isSupported(CustomGraphics.JPEG_EXPORT))
            {
               img = ((MetaImage) img).getImage();
            }
         }

         // @by larryl 2004-9-25, scale if w OR h is different
         if(w > 0 && h > 0 && (d.width != (int) w && d.width != 0 ||
            d.height != (int) h && d.height != 0))
         {
            String prop = SreeEnv.getProperty("StyleReport.ditherImage");

            if(prop != null && prop.equals("true")) {
               img = img.getScaledInstance((int) w, (int) h,
                                           Image.SCALE_SMOOTH);
            }
            else {
               trans.scale(w / d.width, h / d.height);
            }
         }

         if(img instanceof RenderedImage) {
            g2d.drawRenderedImage((RenderedImage) img, trans);
         }
         else {
            g2d.drawImage(img, trans, observer);
         }
      }
      catch(ClassCastException e) {
         super.drawImage(g, img, x, y, w, h, observer);
      }
   }

   /**
    * Rotate the graphics coordinate.
    */
   @Override
   public void rotate(Graphics g, double angle) {
      try {
         ((Graphics2D) g).rotate(angle);
      }
      catch(ClassCastException e) {
      }
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
   @Override
   public void paint(Graphics g, float x, float y, float w, float h,
                     Painter painter, float sx, float sy, float sw,
                     float sh, float clipw, float cliph, Color fg, Color bg,
                     float bufy, float bufh) {
      try {
         // jdk1.2 bug, if a graphics is 'create()'ed, changes in the
         // created graphics affects the original graphics
         // make sure the g is Graphics2D, otherwise an un-disposed g
         Graphics2D g2d = (Graphics2D) g;
         Shape clip = g2d.getClip();
         AffineTransform trans = g2d.getTransform();
         Color color = g2d.getColor();

         g2d.translate(x, y);
         g2d.clip(new Rectangle2D.Float(0, 0, w, h));

         if(bg != null) {
            g2d.setColor(bg);
            g2d.fill(new Rectangle2D.Float(0, 0, w, h));
         }

         g2d.setColor((fg == null) ? color : fg);

         boolean aspect = painter instanceof ImagePainter && ((ImagePainter) painter).isAspect();

         if(aspect) {
            paintPainter(painter, g2d, (int) sx, (int) sy, (int) w, (int) h, bufy, bufh);
         }
         else {
            g2d.scale(w / clipw, h / cliph);

            // @by vincentx, 2004-08-25, fix bug1091609049927
            // scale sx and sy.
            sx = sx * clipw / Math.max(1, w);
            sy = sy * cliph / Math.max(1, h);

            paintPainter(painter, g2d, (int) sx, (int) sy, (int) sw, (int) sh, bufy, bufh);
         }

         g2d.setTransform(trans);
         g2d.setClip(clip);
         g2d.setColor(color);
      }
      catch(ClassCastException e) {
         super.paint(g, x, y, w, h, painter, sx, sy, sw, sh, clipw, cliph, fg,
                     bg, bufy, bufh);
      }
   }

   /**
    * Get the the line adjustment. For integer based coordinate system,
    * this is always 0. For fraction coordinate system, this is half
    * of a one point line width.
    */
   @Override
   public float getLineAdjustment(Graphics g) {
      // Win32Graphics has built-in adjustment of 0.5
      return ((g instanceof PrinterGraphics) && (g instanceof Graphics2D) &&
         !(g instanceof CustomGraphics)) ? 0.5f : 0f;
   }

   /**
    * Check if the graphics is a Graphics2D object.
    */
   @Override
   public boolean isGraphics2D(Graphics g) {
      return g instanceof Graphics2D;
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
   @Override
   public void drawHLine(Graphics g, float y, float x1, float x2, int style,
                         int top, int bottom) {
      try {
         Graphics2D g2d = (Graphics2D) g;

         if((!(g instanceof CustomGraphics) ||
             ((CustomGraphics) g).isSupported(CustomGraphics.G_DASH_LINE)) &&
            (style & SOLID_MASK) != 0 && (style & DASH_MASK) != 0)
         {
            // @by larryl, see comments in Gop
            //float adj = Math.min(getLineWidth(top), getLineWidth(bottom));
            float adj = 0;

            drawLine(g, x1 + adj, y, x2, y, style);
         }
         else {
            super.drawHLine(g, y, x1, x2, style, top, bottom);
         }
      }
      catch(Exception e) {
         super.drawHLine(g, y, x1, x2, style, top, bottom);
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
   @Override
   public void drawVLine(Graphics g, float x, float y1, float y2, int style,
                         int left, int right) {
      try {
         Graphics2D g2d = (Graphics2D) g;

         if((!(g instanceof CustomGraphics) ||
             ((CustomGraphics) g).isSupported(CustomGraphics.G_DASH_LINE)) &&
            (style & SOLID_MASK) != 0 && (style & DASH_MASK) != 0)
         {
            float adj = Math.min(getLineWidth(left), getLineWidth(right));

            drawLine(g, x, y1 + adj, x, y2, style);
         }
         else {
            super.drawVLine(g, x, y1, y2, style, left, right);
         }
      }
      catch(Exception e) {
         super.drawVLine(g, x, y1, y2, style, left, right);
      }
   }

   /**
    * Draw a styled polygon.
    */
   @Override
   public void drawPolygon(Graphics g, Polygon p, int style) {
      try {
         Graphics2D g2d = (Graphics2D) g;
         Stroke ostroke = g2d.getStroke();
         Stroke stroke = getStroke(style);
         boolean need = !stroke.equals(ostroke);

         if(need) {
            g2d.setStroke(stroke);
         }

         drawPolygon(g2d, p);

         if(need) {
            g2d.setStroke(ostroke);
         }
      }
      catch(ClassCastException e) {
         super.drawPolygon(g, p, style);
      }
   }

   /**
    * Draw a plain polygon.
    */
   @Override
   public void drawPolygon(Graphics g, Polygon p) {
      try {
         Graphics2D g2d = (Graphics2D) g;

         g.drawPolygon(p);
      }
      catch(ClassCastException e) {
         super.drawPolygon(g, p);
      }
   }

   /**
    * Draw an arc with the specified line style.
    */
   @Override
   public void drawArc(Graphics g, float x, float y, float w, float h,
                       float startAngle, float angle, int style) {
      try {
         Graphics2D g2d = (Graphics2D) g;
         Stroke ostroke = g2d.getStroke();
         Stroke stroke = getStroke(style);
         boolean need = !stroke.equals(ostroke);

         if(need) {
            g2d.setStroke(stroke);
         }

         drawArc(g, x, y, w, h, startAngle, angle);

         if(need) {
            g2d.setStroke(ostroke);
         }
      }
      catch(ClassCastException e) {
         super.drawArc(g, round(x), round(y), round(w), round(h),
            round(startAngle), round(angle), style);
      }
   }

   /**
    * Draw an arc with the plain line style.
    */
   @Override
   public void drawArc(Graphics g, float x, float y, float w, float h,
                       float startAngle, float angle) {
      try {
         Graphics2D g2d = (Graphics2D) g;
         Arc2D arc = new Arc2D.Float(x, y, w, h, startAngle, angle, Arc2D.OPEN);

         g2d.draw(arc);
      }
      catch(ClassCastException e) {
         super.drawArc(g, round(x), round(y), round(w), round(h),
            round(startAngle), round(angle));
      }
   }

   /**
    * Draw a styled line.
    */
   @Override
   public void drawLine(Graphics g, double x1, double y1, double x2,
                        double y2, int style) {
      // handle double line
      if((style & StyleConstants.DOUBLE_MASK) != 0) {
         double xd = x2 - x1, yd = y2 - y1;
         double l = Math.sqrt(xd * xd + yd * yd);

         if(l != 0) {
            float w = getLineWidth(style) - 1;
            float xadj = (float) (-yd * w / l);
            float yadj = (float) (xd * w / l);

            drawLine(g, x1, y1, x2, y2, StyleConstants.THIN_LINE);
            drawLine(g, x1 + xadj, y1 + yadj, x2 + xadj, y2 + yadj,
                     StyleConstants.THIN_LINE);
            return;
         }
      }

      try {
         Graphics2D g2d = (Graphics2D) g;
         Stroke ostroke = g2d.getStroke();
         Stroke stroke = getStroke(style);
         boolean need = !stroke.equals(ostroke);

         if(need) {
            g2d.setStroke(stroke);
         }

         Object hint = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);

         // turn off if horizontal or vertical line to avoid dot lines being
         // drawn as a thin line
         if(hint != null) {
            if(x1 == x2 || y1 == y2) {
               g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_OFF);
            }
            else if(SreeEnv.getProperty("image.antialias").
                    equalsIgnoreCase("true")) {
               g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
            }
         }

         drawLine(g2d, x1, y1, x2, y2);

         if(hint != null) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hint);
         }

         if(need) {
            g2d.setStroke(ostroke);
         }
      }
      catch(ClassCastException e) {
         super.drawLine(g, x1, y1, x2, y2, style);
      }
   }

   /**
    * Draw a styled polyline.
    */
   @Override
   public void drawPolyline(Graphics g, float[] xs, float[] ys, int np,
                            int style) {
      Graphics2D g2d = null;
      Stroke ostroke = null;
      boolean need = false;

      try {
         g2d = (Graphics2D) g;
         ostroke = g2d.getStroke();

         Stroke stroke = getStroke(style);

         need = !stroke.equals(ostroke);

         if(need) {
            g2d.setStroke(stroke);
         }

         Object hint = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);

         if(SreeEnv.getProperty("image.antialias").
            equalsIgnoreCase("true") && hint != null)
         {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                 RenderingHints.VALUE_ANTIALIAS_ON);
         }

         if(SVGSupport.getInstance().isSVGGraphics(g2d)) {
            for(int i = 1; i < np; i++) {
               g2d.draw(new Line2D.Float(xs[i - 1], ys[i - 1], xs[i], ys[i]));
            }
         }
         else {
            g2d.draw(new PolylineShape(xs, ys, np));
         }

         if(hint != null) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hint);
         }

         if(need) {
            g2d.setStroke(ostroke);
         }
      }
      catch(Exception ex) {
         super.drawPolyline(g, xs, ys, np, style);

         // @by charvi 2004-04-15
         // @fixed bug1081402864431
         // Reset the stroke, otherwise the subsequent lines will be
         // draw with the incorrect stroke.
         if(g2d != null && need) {
            g2d.setStroke(ostroke);
         }
      }
   }

   /**
    * Draw a plain 1 pt line.
    */
   @Override
   public void drawLine(Graphics g, double x1, double y1, double x2, double y2) {
      try {
         Graphics2D g2d = (Graphics2D) g;

         /*
          if(!(g instanceof CustomGraphics)) {
          // adjust for the pixels width
          double w = 0.5; // half of line width
          double xd = x2 - x1, yd = y2 - y1;

          double l = Math.sqrt(xd*xd + yd*yd);
          // move the end point to middle of line width
          double xadj = (l > 0) ? (w * Math.abs(yd) / l) : 0;
          double yadj = (l > 0) ? (w * Math.abs(xd) / l) : 0;

          x1 = (float) (x1 + xadj);
          x2 = (float) (x2 + xadj);
          y1 = (float) (y1 + yadj);
          y2 = (float) (y2 + yadj);
          }
          */

         g2d.draw(new Line2D.Double(x1, y1, x2, y2));
      }
      catch(ClassCastException e) {
         super.drawLine(g, x1, y1, x2, y2);
      }
   }

   /**
    * Fill an area with the specified fill pattern.
    * @param area shape to fill.
    * @param fill fill pattern, a Color or Paint (JDK1.2 only) object.
    */
   @Override
   public void fill(Graphics g, Shape area, Object fill) {
      try {
         Graphics2D g2d = (Graphics2D) g;
         Paint pt = g2d.getPaint();

         g2d.setPaint((Paint) fill);
         g2d.fill(area);
         g2d.setPaint(pt);
      }
      catch(ClassCastException e) {
         super.fill(g, area, fill);
      }
   }

   /**
    * Fill a rectanglar area.
    * @param x left x coordinate.
    * @param y upper y coordinate.
    * @param w rectangle width.
    * @param h rectangle height.
    */
   @Override
   public void fillRect(Graphics g, float x, float y, float w, float h) {
      try {
         ((Graphics2D) g).fill(new Rectangle2D.Float(x, y, w, h));
      }
      catch(ClassCastException e) {
         super.fillRect(g, x, y, w, h);
      }
   }

   /**
    * Fill an arc with the specified fill pattern.
    */
   @Override
   public void fillArc(Graphics g, float x, float y, float w, float h,
                       float startAngle, float angle, Object fill) {
      try {
         Graphics2D g2d = (Graphics2D) g; // make sure it's Graphics2D
         Arc2D arc = new Arc2D.Float(x, y, w, h, startAngle, angle, Arc2D.PIE);

         fill(g, arc, fill);
      }
      catch(ClassCastException e) {
         super.fillArc(g, x, y, w, h, startAngle, angle, fill);
      }
   }

   /**
    * Draw string on graphics.
    * @param g graphics context.
    * @param str string contents.
    * @param x x coordinate.
    * @param y y coordinate.
    */
   @Override
   public void drawString(Graphics g, String str, float x, float y) {
      try {
         Graphics2D g2d = (Graphics2D) g;
         Object hint = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);

         if(hint != null) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
               RenderingHints.VALUE_ANTIALIAS_OFF);
         }

         g2d.drawString(str, x, y);

         if(hint != null) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hint);
         }
      }
      catch(ClassCastException e) {
         super.drawString(g, str, x, y);
      }
   }

   /**
    * Return all font names supported in this environment.
    * @return all available fonts.
    */
   @Override
   public String[] getAllFonts() {
      if(fonts == null) {
         fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().
            getAvailableFontFamilyNames();
      }

      return fonts;
   }

   /**
    * Get the font face name.
    */
   @Override
   public String getFontName(Font fn) {
      return fn.getName();
   }

   /**
    * Get the postscript font name.
    */
   @Override
   public String getPSName(Font fn) {
      return fn.getPSName();
   }

   /**
    * Paint the painter by rotating it 90 degrees clockwise.
    */
   @Override
   public void paintRotate(Painter painter, Graphics g, float x, float y,
                           float w, float h, int rotation)
   {
      try {
         Graphics2D g2d = (Graphics2D) g;
         AffineTransform trans = g2d.getTransform();
         Color c = g2d.getColor();
         Font font = g2d.getFont();
         Shape clip = g2d.getClip();

         if(painter instanceof HTMLSource && rotation > 0 && rotation < 360 &&
            rotation != 90 && rotation != 270)
         {
            if(rotation == 180) {
               g2d.translate(x + w, y + h);
               g2d.rotate(Math.PI);
               painter.paint(g2d, 0, 0, round(w), round(h));
            }
            else {
               int originOffset = ((HTMLSource) painter).getOriginOffset();
               boolean converse = rotation > 90 && rotation < 180 ||
                  rotation > 270 && rotation < 360;
               Dimension ppsize = painter.getPreferredSize();
               int ow = ppsize.width;
               int oh = ppsize.height;

               if(rotation < 90) {
                  g2d.translate(x + originOffset, y);
               }
               else if(rotation > 90 && rotation < 180) {
                  g2d.translate(x + w, y + originOffset);
               }
               else if(rotation > 180 && rotation < 270) {
                  g2d.translate(x + w - originOffset, y + h);
               }
               else if(rotation > 270 && rotation < 360) {
                  g2d.translate(x, y + h - originOffset);
               }

               g2d.rotate(Math.PI * rotation / 180);
               painter.paint(g2d, 0, 0, round(ow), round(oh));
            }
         }
         else {
            if(rotation == 270) {
               // @by larryl, when the graphics is rotated, the java graphics and
               // pdf treats the center point different. In java, we have to minus
               // 1 to get the real center point. In pdf, the minus one is
               // unnecessary. Here we just enlarge the clip by one point to
               // accommodate both.
               if(clip instanceof Rectangle2D) {
                  Rectangle2D clip2 = (Rectangle2D) clip;
                  clip2.add(clip2.getX(), clip2.getY() - 1);

                  g2d.setClip(clip2);
               }

               g2d.translate(x, y + h + 1);
               g2d.rotate(Math.PI * 3 / 2);
            }
            // default to 90
            else {
               // see comments above
               if(clip instanceof Rectangle2D) {
                  Rectangle2D clip2 = (Rectangle2D) clip;

                  clip2.add(clip2.getX() - 1, clip2.getY());

                  g2d.setClip(clip2);
               }

               g2d.translate(x + w - 1, y);
               g2d.rotate(Math.PI / 2);
            }

            painter.paint(g2d, 0, 0, round(h), round(w));
         }

         g2d.setTransform(trans);
         g2d.setColor(c);
         g2d.setFont(font);
         g2d.setClip(clip);
      }
      catch(ClassCastException e) {
         super.paintRotate(painter, g, x, y, w, h, rotation);
      }
   }

   /**
    * This method is called before a page is printed for any initialization.
    */
   @Override
   public void startPage(Graphics g, StylePage page) {
      super.startPage(g, page);

      try {
         Graphics2D g2 = (Graphics2D) g;

         g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER));

         if(SreeEnv.getProperty("image.type").equalsIgnoreCase("png") &&
            SreeEnv.getProperty("image.antialias").equalsIgnoreCase("true"))
         {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
         }
      }
      catch(ClassCastException e) {
      }

      Rectangle rect = g.getClipBounds();

      // it's the first time a page is printed
      // the subsequent scans (~10 pixels lines per scan) are not
      // reinitialized
      if((g instanceof PrinterGraphics) && !(g instanceof CustomGraphics) &&
         rect.height > 50)
      {
         Dimension size = page.getPageDimension();
         Color color = g.getColor();

         /*
          * JDK1.2 BUG, float wouldn't work when printing to a printer using
          * drawString() along. The drawImage() somehow corrects this problem
          */
         // force text to appear
         g.drawImage(dot, size.width / 2, size.height / 2, null);

         // figure out the top and bottom of the paintable elements
         g.setColor(Color.white);
         int top = rect.y + rect.height, bottom = 0;

         for(int i = 0; i < page.getPaintableCount(); i++) {
            Rectangle box = page.getPaintable(i).getBounds();

            top = Math.min(top, box.y);
            bottom = Math.max(bottom, box.y + box.height);
         }

         // drawing a vertical line to force printer to scan all areas
         // between the top and bottom
         /*
          * JDK1.2 bug, sometimes the printer scanning skips a section
          * for no reason. This forces it to scan all lines between the
          * top and bottom elements.
          */
         if(bottom > top) {
            drawLine(g, rect.x, top, rect.y, bottom);
         }
      }
   }

   /**
    * Write an image to an output stream.
    */
   @Override
   public void writeJPEG(Image img, OutputStream output) {
      try {
         int type = (img instanceof BufferedImage) ?
            ((BufferedImage) img).getType() : 0;

         if(img instanceof BufferedImage &&
            // @by vincentx, 2004-09-23
            // currently there is a problem in showing JPEG image
            // with the following image types (all image types with alpha).
            type != BufferedImage.TYPE_INT_ARGB &&
            type != BufferedImage.TYPE_INT_ARGB_PRE &&
            type != BufferedImage.TYPE_4BYTE_ABGR &&
            type != BufferedImage.TYPE_4BYTE_ABGR_PRE)
         {
            ImageIO.write((BufferedImage) img, "jpeg", output);
            output.flush();
            return;
         }
      }
      catch(Throwable e) {
         LOG.warn("Failed to encode image as JPEG", e);
      }

      super.writeJPEG(img, output);
   }

   /**
    * Get the width of a line style.
    */
   @Override
   public float getLineWidth(int style) {
      return GTool.getLineWidth(style);
   }

   /**
    * Get a cursor from an image resource file.
    * @param res resource file.
    */
   @Override
   public Cursor getCursor(String res) {
      Cursor cursor = (Cursor) cursormap.get(res);

      if(cursor == null) {
         Image img = Tool.getImage(Common.class, res);

         if(img == null) {
            cursor = new Cursor(Cursor.DEFAULT_CURSOR);
         }
         else {
            cursor = Tool.getToolkit().createCustomCursor(
               img, new Point(16, 16), res);
         }

         cursormap.put(res, cursor);
      }

      return cursor;
   }

   /**
    * Create a stroke object from a line style.
    */
   private Stroke getStroke(int style) {
      return GTool.getStroke(style);
   }

   private static BufferedImage dot = new BufferedImage(
      1, 1, BufferedImage.TYPE_INT_RGB);

   static {
      dot.setRGB(0, 0, 0xFFFFFFFF);
   }

   private AffineTransform notransform = new AffineTransform();
   /**
    * fractional metrics
    */
   private FontRenderContext fontRenderContext =
      new FontRenderContext(notransform, false, true);
   private String[] fonts;
   private Hashtable cursormap = new Hashtable(); // res -> Cursor
   private boolean stringWidthFontMetrics = true;

   private static final Logger LOG =
      LoggerFactory.getLogger(Gop2D.class);
}
