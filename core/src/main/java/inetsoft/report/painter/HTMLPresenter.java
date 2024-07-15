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

import inetsoft.report.ReportElement;
import inetsoft.report.internal.ExpandablePresenter;
import inetsoft.report.internal.Graphics2DWrapper;
import inetsoft.report.io.rtf.BasicHTML2;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Catalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.Position;
import javax.swing.text.View;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLWriter;
import java.awt.*;
import java.io.*;

/**
 * HTML presenters supports displaying of basic html.
 *
 * @version 6.1, 9/20/2004
 * @author InetSoft Technology Corp
 */
public class HTMLPresenter implements ExpandablePresenter {
   /**
    * Check if the presenter can handle this type of objects.
    * @param type object type.
    * @return true if the presenter can handle this type.
    */
   @Override
   public boolean isPresenterOf(Class type) {
      return true;
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
      return obj != null;
   }

   /**
    * Check if this presenter should always fill the entire area of a cell.
    */
   @Override
   public boolean isFill() {
      return true;
   }

   /**
    * Set the font to use for this presenter. A table calls this function
    * before the cell is printed when a presenter is used.
    */
   @Override
   public void setFont(Font font) {
      this.font = font;
      comp.setFont(font);
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
      return Catalog.getCatalog().getString("Basic HTML");
   }

   /**
    * Determine if this Presenter requires raw (unformatted) data.
    *
    * @return <code>true</code> if the presenter requires raw data.
    */
   @Override
   public boolean isRawDataRequired() {
      return false;
   }

   /**
    * Calculate the preferred size of the object representation.
    * @param v object value.
    * @return preferred size.
    */
   @Override
   public Dimension getPreferredSize(Object v) {
      if(v == null) {
         return new Dimension(0, 0);
      }

      try {
         View view = BasicHTML.createHTMLView(comp, v.toString());
         return new Dimension((int) view.getPreferredSpan(View.X_AXIS),
                              (int) view.getPreferredSpan(View.Y_AXIS));
      }
      catch(Exception ex) {
         LOG.debug("Failed to create HTML view for: " + v, ex);
         // if the html viewer is not able to paint the contents, defaults
         // to htmlimage size
         return new Dimension(72, 36);
      }
   }

   /**
    * Calculate the preferred size of the object representation.
    * @param v object value.
    * @param width the maximum width of the presenter.
    * @return preferred size.
    */
   @Override
   public Dimension getPreferredSize(Object v, float width) {
      if(v == null) {
         return new Dimension(0, 0);
      }

      View view = BasicHTML.createHTMLView(comp, v.toString());
      view.setSize(width, 20);

      return new Dimension((int) view.getPreferredSpan(View.X_AXIS),
			   (int) view.getPreferredSpan(View.Y_AXIS));
   }

   /**
    * Draw an empty box with label HTML Painter
    */
   @Override
   public void paint(Graphics g, Object html, int x, int y, int w, int h) {
      paint(g, html, x, y, w, h, 0, -1);
   }

   /**
    * Draw the html contents.
    * @param g target graphics.
    * @param html html string.
    * @param x x position of the drawing area.
    * @param y y position of the drawing area.
    * @param w width of the drawing area.
    * @param h height of the drawing area.
    * @param starty the already consumed height.
    * @param bufh the current drawing buffer height.
    */
   @Override
   public void paint(Graphics g, Object html, int x, int y, int w, int h,
                     float starty, float bufh)
   {
      if(html == null) {
         return;
      }

      Font ofont = null;
      Shape oclip = g.getClip();

      if(font != null) {
         ofont = g.getFont();
         g.setFont(font);
      }

      comp.setFont(g.getFont());

      try {
         View view = BasicHTML2.createHTMLView(comp, html.toString());
         int height = h;

         if(bufh > 0) {
            Rectangle rect = new Rectangle(0, 0, w, h);
            int oy = y;

            // check if top is clipped
            if(starty > 0) {
               float top = findClipY(view, starty, rect, null);

               // top of the line is clipped, move back to the top of the clipped line
               // since the clipped line is not displayed (see if(bottom > 0) below).
               // don't move back if the clipped content is kept (compared to MIN_LINE below).
               if(top > 0 && starty > top + MIN_LINE) {
                  y += top;
                  starty -= top;
               }
            }

            // check if the bottom is clipped
            float bottom = findClipY(view, starty + bufh + 1, rect, null);

            if(bottom > 0) {
               int height2 = (int) Math.min(height - bottom, starty + bufh - bottom - oy);

               // don't ignore the content if there is no content to be displayed, such as
               // in the case of a large image that is taller than a page.
               if(height2 >= MIN_LINE) {
                  height = height2;
               }
            }
         }

         // @by larryl, if the graphics has no clipping, the BoxView drawing
         // throws a null pointer exception in jdk 1.4.2
         g.clipRect(x, y, w, height);

         // @by larryl, in jdk 1.5, swing prints text as graphics if the output
         // is PrinterGraphics. We wrap the g to fool swing to use regular
         // drawing so the output is nicer.
         Graphics2DWrapper wrapper = new Graphics2DWrapper((Graphics2D) g, true);
         view.paint(wrapper, new Rectangle(x, y, w, height));
      }
      catch(Exception ex) {
         LOG.warn("Failed to render HTML:" + html, ex);
      }

      if(ofont != null) {
         g.setFont(ofont);
      }

      g.setClip(oclip);
   }

   /**
    * Get the adjustment on height if the height is adjusted to line boundary.
    */
   @Override
   public float getHeightAdjustment(Object html, ReportElement elem,
                                    Dimension pd, float starty, float bufw,
                                    float bufh) {
      if(html == null || starty + bufh >= pd.height) {
         return 0;
      }

      try {
         View view = BasicHTML.createHTMLView(comp, html.toString());

         String prop = SreeEnv.getProperty("htmlpresenter.fitline");
         boolean linewrap = prop.equalsIgnoreCase("true");

         if(linewrap && bufh > 0) {
            Rectangle rect = new Rectangle(0, 0, (int) bufw, (int) bufh);

            // check if the bottom is clipped
            return findClipY(view, starty + bufh, rect, null);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to adjust height: " + html, ex);
      }

      return 0;
   }

   /**
    * Get the html contents for the specified buffer.
    * @param html html string.
    * @param starty the already consumed height.
    * param bufw the current drawing buffer width.
    * param bufh the current drawing buffer height.
    */
   public String getFragment(Object html, float starty, float bufw, float bufh) {
      if(html == null) {
         return null;
      }

      try {
         View view = BasicHTML2.createHTMLView(comp, html.toString());
         Rectangle rect = new Rectangle(0, 0, (int) bufw, (int) bufh);
         int[] range = new int[2];
         int idx1 = 0, idx2 = view.getDocument().getLength();

         // check if top is clipped
         if(starty > 0) {
            float bottom = findClipY(view, starty, rect, range);

            // see paint() for comments related to MIN_LINE
            if(bottom > starty + MIN_LINE) {
               idx1 = range[0];
            }
            else {
               idx1 = range[0] + 1;
            }
         }

         // check if the bottom is clipped
         float bottom = findClipY(view, starty + bufh + 1, rect, range);

         // see paint() for comments related to MIN_LINE
         if(bufh >= MIN_LINE && bottom < starty + bufh + MIN_LINE) {
            idx2 = Math.min(idx2, range[0] + 1);
         }
         else {
            idx2 = range[0];
         }

         if(idx1 > 0 || idx2 <= view.getDocument().getLength()) {
            StringWriter writer = new StringWriter();
            HTMLWriter hwriter = new HTMLWriter(
               writer, (HTMLDocument) view.getDocument(), idx1, idx2 - idx1);
            hwriter.write();
            return writer.getBuffer().toString();
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to render HTML fragment: " + html, ex);
      }

      return html.toString();
   }

   /**
    * Find the elements in the document that is clipped by the y in the middle.
    * Return the clipped Y height from the top of the clipped elements to the
    * clipping line.
    * @param y the clipping position.
    * @param bounds the drawing area.
    * @param range this should be null or a two-element array. If it's an
    * array, the function will fill the 0 and 1 position with the index of the
    * character immediately above and below the clipping point.
    */
   private float findClipY(View view, float y, Rectangle bounds, int[] range) {
      int lo = 0;
      int hi = view.getDocument().getLength() + 1;

      if(range == null) {
         range = new int[2];
      }

      view.setSize(bounds.width, bounds.height);

      try {
         while(hi > lo) {
            int mid = (lo + hi) / 2;
            Rectangle s = (Rectangle)
               view.modelToView(mid, bounds, Position.Bias.Backward);

            if(s.y < y && s.y + s.height > y) {
               float dist = 0;

               for(int k = mid; k >= lo; k--) {
                  s = (Rectangle) view.modelToView(k, bounds,
                                                   Position.Bias.Backward);
                  if(s.y + s.height <= y || s.y >= y) {
                     if(s.height > 0 || s.width > 0) {
                        range[0] = k;
                     }

                     break;
                  }

                  dist = Math.max(y - s.y, dist);
               }

               for(int k = mid + 1; k <= hi; k++) {
                  s = (Rectangle) view.modelToView(k, bounds,
                                                   Position.Bias.Backward);

                  if(s.y + s.height <= y || s.y >= y) {
                     if(s.height > 0 || s.width > 0) {
                        range[1] = k;
                     }

                     break;
                  }

                  dist = Math.max(y - s.y, dist);
               }

               return dist;
            }
            else if(s.y >= y) {
               hi = mid - 1;
               range[1] = mid;
            }
            else {
               lo = mid + 1;
               range[0] = mid;
            }
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to find the clipped height of the HTML view", ex);
      }

      return 0;
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

   private void writeObject(ObjectOutputStream stream) throws IOException {
      // remove all children, fix bug1322841450078
      comp.removeAll();
      stream.defaultWriteObject();
   }

   private Color bg = null;
   private Font font;
   private JComponent comp = new JPanel();

   private static final int MIN_LINE = 12; // minimum line height
   private static final Logger LOG = LoggerFactory.getLogger(HTMLPresenter.class);
}
