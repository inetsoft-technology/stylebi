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
package inetsoft.report.painter;

import inetsoft.graph.internal.GTool;
import inetsoft.report.Presenter;
import inetsoft.report.internal.MetaImage;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * The IconCounterPresenter presents an integer number as a number of
 * icons. It is commonly used to represent ratings or other small scale
 * numbers. The image used in the presenter can be changed by the users.
 * This can be used in a table to display numbers as number of icons.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class IconCounterPresenter implements Presenter {
   /**
    * Create a default counter presenter.
    */
   public IconCounterPresenter() {
   }

   /**
    * Create a counter presenter using the specified image as the icon.
    * @param image icon image.
    */
   public IconCounterPresenter(Image image) {
      icon = image;
   }

   /**
    * Set the icon used in this presenter.
    */
   public void setIcon(Image icon) {
      this.icon = icon;
   }

   /**
    * Get the icon used in this presenter.
    */
   public Image getIcon() {
      return icon;
   }

   /**
    * Set the icon color. The following color icons are builtin: red, green,
    * blue, rust, yellow.
    */
   public void setIconColor(Color color) {
      this.color = color;
   }

   /**
    * Get the icon color theme.
    */
   public Color getIconColor() {
      return color;
   }

   /**
    * Paint an object at the specified location.
    * @param g graphical context.
    * @param v object value.
    * @param x x coordinate of the left edge of the paint area.
    * @param y y coordinate of the upper edge of the paint area.
    * @param w area width.
    * @param h area height.
    */
   @Override
   public void paint(Graphics g, Object v, int x, int y, int w, int h) {
      if(!(v instanceof Number)) {
         return;
      }

      double n = ((Number) v).doubleValue();

      // there is no star for a negative value or a number that is not one: show its text, when
      // the table lens gave it, so the cell does not look empty
      if((n < 0 || Double.isNaN(n)) && valueText != null) {
         Shape clip = g.getClip();
         Color oc = g.getColor();

         g.clipRect(x, y, w, h);
         paintValueText(g, oc, x, y, w, h);
         g.setColor(oc);
         g.setClip(clip);
         return;
      }

      if(isModernStyle()) {
         paintModern(g, n, x, y, w, h);
         return;
      }

      loadImages();

      Image img = icon;

      if(img instanceof MetaImage) {
         img = ((MetaImage) img).getImage();
      }

      if(img != null) {
         Image image = img;
         int iw = icon.getWidth(null), ih = icon.getHeight(null);

         if(iw <= 0 || ih <= 0) {
            return;
         }

         // an icon bigger than the cell is scaled down to it, keeping its shape. At its own size
         // an icon wider than the cell fits no star, so the cell drew empty, and one taller than
         // the cell was cut off.
         double scale = Math.min(1.0, Math.min((h - 2) / (double) ih, (w - 4) / (double) iw));
         int sw = Math.max(1, (int) Math.round(iw * scale));
         int sh = Math.max(1, (int) Math.round(ih * scale));
         int top = y + (h - sh) / 2;

         paintStars(g, n, x, y, w, h, sw, g.getColor(), (sg, sx, muted) -> {
            if(sg instanceof Graphics2D) {
               Graphics2D g2 = (Graphics2D) sg;

               if(muted) {
                  g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, MUTED_ALPHA));
               }

               if(sw != iw) {
                  g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                      RenderingHints.VALUE_INTERPOLATION_BILINEAR);
               }
            }

            sg.drawImage(image, sx, top, sw, sh, null);
         });
      }
   }

   /**
    * Lay out the row of stars, so that every drawer draws the same row. A star is drawn only when
    * it fits in the cell whole, so the row never ends in a star cut off by the cell edge. A value
    * that needs more stars than fit draws a plus after the last whole star, so a row that is full
    * does not look like a value that just fills it. Zero, a negative value and NaN draw one muted
    * star, so they do not look like a missing value. A fraction draws part of its last star, at
    * least MIN_PART of a star wide so a small one can still be seen.
    * @param sw the width of one star.
    * @param fg the color of the plus.
    */
   private static void paintStars(Graphics g, double n, int x, int y, int w, int h, int sw,
                                  Color fg, StarDrawer drawer)
   {
      if(sw <= 0) {
         return;
      }

      int step = sw + 2;
      // the stars that fit whole, with 2px before each one
      int fit = w / step;

      if(fit <= 0) {
         return;
      }

      n = snap(n);

      if(n <= 0 || Double.isNaN(n)) {
         drawStar(g, drawer, x + 2, y, sw, h, true);
      }
      else if(Math.ceil(n) > fit) {
         // leave room for the plus, dropping a star if needed
         int count = Math.max(0, (w - 2 - PLUS_SIZE) / step);

         for(int i = 0; i < count; i++) {
            drawStar(g, drawer, x + 2 + i * step, y, sw, h, false);
         }

         paintPlus(g, fg, x + 2 + count * step, y, h);
      }
      else {
         int whole = (int) Math.floor(n);

         for(int i = 0; i < whole; i++) {
            drawStar(g, drawer, x + 2 + i * step, y, sw, h, false);
         }

         double part = n - whole;

         if(part > 0) {
            int visible = Math.min(sw, (int) Math.ceil(Math.max(part, MIN_PART) * sw));
            drawStar(g, drawer, x + 2 + whole * step, y, visible, h, false);
         }
      }
   }

   // draw one star on its own graphics, clipped to the part of it that is shown
   private static void drawStar(Graphics g, StarDrawer drawer, int sx, int y, int visible, int h,
                                boolean muted)
   {
      Graphics sg = g.create();

      try {
         sg.clipRect(sx, y, visible, h);
         drawer.draw(sg, sx, muted);
      }
      finally {
         sg.dispose();
      }
   }

   // a small plus after the last whole star of a row that has more stars than fit
   private static void paintPlus(Graphics g, Color fg, int px, int y, int h) {
      Color oc = g.getColor();
      int cy = y + h / 2;

      if(fg != null) {
         g.setColor(fg);
      }

      g.fillRect(px, cy - 1, PLUS_SIZE, 2);
      g.fillRect(px + PLUS_SIZE / 2 - 1, cy - PLUS_SIZE / 2, 2, PLUS_SIZE);
      g.setColor(oc);
   }

   /**
    * Draw the value's text at the right of the cell, in the cell's text colour but fainter, the
    * same as the Bar presenter does.
    */
   private void paintValueText(Graphics g, Color fg, int x, int y, int w, int h) {
      FontMetrics fm = g.getFontMetrics();
      int tw = fm.stringWidth(valueText);

      g.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), (int) (fg.getAlpha() * 0.65)));
      g.drawString(valueText, Math.max(x + 2, x + w - tw - 2),
                   y + (h - fm.getHeight()) / 2 + fm.getAscent());
   }

   /**
    * Give a cell its own copy that shows the cell's text when its value is negative or NaN, which
    * have no stars. Without it such a value draws one muted star. The text is the cell's formatted
    * value, which only the table lens has. The presenter itself is never changed, since a script
    * presenter is shared by all cells of a column.
    * @param text the cell's formatted value.
    */
   public Presenter withValueText(String text) {
      IconCounterPresenter copy = new IconCounterPresenter(icon);
      copy.bg = bg;
      copy.color = color;
      copy.modernColor = modernColor;
      copy.valueText = text;
      return copy;
   }

   /**
    * The text a negative value is drawn as, or null when the table lens has not given one.
    */
   public String valueText() {
      return valueText;
   }

   /**
    * Calculate the preferred size of the object representation.
    * @param v object value.
    * @return preferred size.
    */
   @Override
   public Dimension getPreferredSize(Object v) {
      if(isModernStyle()) {
         if(v instanceof Number) {
            int n = starCount(((Number) v).doubleValue());
            return new Dimension(n * (STAR_SIZE + 2) + 2, STAR_SIZE + 2);
         }

         return new Dimension(0, 0);
      }

      loadImages();

      if(icon != null && v != null && v instanceof Number) {
         int n = starCount(((Number) v).doubleValue());
         int iconw = icon.getWidth(null);
         int iconh = icon.getHeight(null);

         if(iconw > 0 && iconh > 0) {
            return new Dimension(n * (iconw + 2) + 2, iconh + 2);
         }
      }

      return new Dimension(0, 0);
   }

   // the stars a value takes: zero, a negative value and NaN still take the one muted star, and an
   // endless or huge value takes no more than MAX_STARS, so the width does not overflow
   private static int starCount(double n) {
      n = snap(n);
      return n > 0 ? (int) Math.min(Math.ceil(n), MAX_STARS) : 1;
   }

   // a value within floating point noise of a whole number, such as 1.0000000000000002 from a sum
   // of decimal ratings, is that whole number, so it draws no sliver of a star or plus
   private static double snap(double n) {
      double whole = Math.rint(n);
      return Math.abs(n - whole) <= SNAP_EPSILON * Math.max(1, Math.abs(n)) ? whole : n;
   }

   /**
    * Draw the stars as solid shapes in this color, in place of the built-in orange star image.
    * Null, the default, keeps the image. An icon color chosen with setIconColor() wins. This is
    * not a bean property, so it does not show in the presenter dialog or get saved: the table
    * sets it on a modern table.
    */
   public void useModernStyle(Color starColor) {
      this.modernColor = starColor;
   }

   /**
    * The color of the modern stars, or null for the classic image.
    */
   public Color modernStyle() {
      return modernColor;
   }

   private boolean isModernStyle() {
      return modernColor != null && color == null && icon == null;
   }

   private void paintModern(Graphics g, double n, int x, int y, int w, int h) {
      double cy = y + (h - STAR_SIZE) / 2.0 + STAR_SIZE / 2.0 + 0.4;
      Color muted = new Color(modernColor.getRed(), modernColor.getGreen(), modernColor.getBlue(),
                              Math.round(modernColor.getAlpha() * MUTED_OUTLINE_ALPHA));

      paintStars(g, n, x, y, w, h, STAR_SIZE, modernColor, (sg, sx, isMuted) -> {
         Graphics2D g2 = (Graphics2D) sg;
         Shape shape = star(sx + STAR_SIZE / 2.0, cy);

         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

         // a muted star is an outline, so zero reads as a value but not as one star
         if(isMuted) {
            g2.setColor(muted);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(shape);
         }
         else {
            g2.setColor(modernColor);
            g2.fill(shape);
         }
      });
   }

   // draws one star with its left edge at sx on a graphics of its own
   @FunctionalInterface
   private interface StarDrawer {
      void draw(Graphics g, int sx, boolean muted);
   }

   // a five pointed star with a point straight up
   private static Shape star(double cx, double cy) {
      double outer = STAR_SIZE / 2.0 + 0.2;
      double inner = outer * 0.46;
      java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();

      for(int i = 0; i < 10; i++) {
         double radius = i % 2 == 0 ? outer : inner;
         double angle = -Math.PI / 2 + i * Math.PI / 5;
         double px = cx + radius * Math.cos(angle);
         double py = cy + radius * Math.sin(angle);

         if(i == 0) {
            path.moveTo(px, py);
         }
         else {
            path.lineTo(px, py);
         }
      }

      path.closePath();
      return path;
   }

   /**
    * Check if this presenter should always fill the entire area of a cell. The stars do not, so a
    * table aligns them in the cell. A copy that draws a negative value's text does, so a table
    * does not cut the text to the width of one star.
    */
   @Override
   public boolean isFill() {
      return valueText != null;
   }

   /**
    * Check if the presenter can handle this type of objects.
    * @param type object type.
    * @return true if the presenter can handle this type.
    */
   @Override
   public boolean isPresenterOf(Class type) {
      return Number.class.isAssignableFrom(type);
   }

   /**
    * Check if the presenter can handle this particular object. Normally
    * a presenter handles a class of objects, which is checked by the
    * isPresenterOf(Class) method. If this presenter does not care about
    * the value in the object, it can just call the isPresenterOf() with
    * the class of the object, e.g.<pre>
    *   if(type == null) {
    *      return false;
    *   }
    *   return isPresenterOf(obj.getClass());
    * </pre>
    * @param obj object type.
    * @return true if the presenter can handle this type.
    */
   @Override
   public boolean isPresenterOf(Object obj) {
      return (obj == null) ? false : isPresenterOf(obj.getClass());
   }

   /**
    * Make sure the images are fully loaded.
    */
   private void loadImages() {
      if(icon == null) {
         String gif = "images/beancount.gif";

         if(color != null && color.getAlpha() != 0) {
            gif = "images/star.gif";
         }

         try {
            icon = Tool.getImage(this, gif);

            if(color != null && color.getAlpha() != 0) {
               icon = GTool.changeHue(icon, color);
            }
         }
         catch(Exception e) {
            LOG.error("Failed to load image: " + gif, e);
         }
      }

      if(icon != null) {
         Tool.waitForImage(icon);
      }
   }

   /**
    * Set the font to use for this presenter. A table calls this function
    * before the cell is printed when a presenter is used.
    */
   @Override
   public void setFont(Font font) {
   }

   /**
    * Get the display name of this presenter.
    *
    * @return a user-friendly name for this presenter.
    *
    * @since 5.1
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("Stars (Counter)");
   }

   /**
    * Determine if this Presenter requires raw (unformatted) data.
    *
    * @return <code>true</code>.
    */
   @Override
   public boolean isRawDataRequired() {
      return true;
   }

   /**
    * Set the background.
    */
   @Override
   public void setBackground(Color bg) {
      this.bg = bg;
   }

   /**
    * Get the background.
    */
   public Color getBackground() {
      return bg;
   }

   private Color bg = null;
   private Color color = null;
   private Color modernColor = null;
   private transient Image icon = null;
   // the cell's formatted value, set by the table lens on a copy for a negative value; not a bean
   // property, so it is never saved
   private transient String valueText;

   // the width and height of a star, px; the same as the built-in image
   private static final int STAR_SIZE = 12;
   // the width and height of the plus after a row that has more stars than fit, px
   private static final int PLUS_SIZE = 8;
   // the smallest part of a star a fraction draws, so a small fraction is not a sliver
   private static final double MIN_PART = 0.4;
   // the most stars the preferred size counts, so an endless or huge value has a positive width
   private static final int MAX_STARS = 1000;
   // how near, relative to the value, a value is taken to be the whole number it is near
   private static final double SNAP_EPSILON = 1e-9;
   // the opacity of the muted image star drawn for zero, negative and NaN
   private static final float MUTED_ALPHA = 0.3f;
   // the opacity of the muted modern star's outline
   private static final float MUTED_OUTLINE_ALPHA = 0.6f;

   private static final Logger LOG =
      LoggerFactory.getLogger(IconCounterPresenter.class);
}
