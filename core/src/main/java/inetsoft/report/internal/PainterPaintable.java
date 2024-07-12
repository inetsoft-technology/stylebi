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
import inetsoft.report.painter.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.*;

/**
 * The PainterPaintable encapsulate printing of a painter.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class PainterPaintable extends BasePaintable {
   /**
    * Default constructor
    */
   public PainterPaintable() {
      this(new PainterElementDef());
   }

   /**
    * Default constructor
    */
   protected PainterPaintable(ReportElement elem) {
      this.elem = elem;
      x = 0.0F;
      y = 0.0F;
      painterW = 0;
      painterH = 0;
      pd = new Dimension(0, 0);
      prefH = 0;
      prefW = 0;
      painter = null;
      offsetY = 0;
      offsetX = 0;
      fg = Color.black;
      bg = null;
   }

   public PainterPaintable(float x, float y, float painterW, float painterH,
                           Dimension pd, int prefW, int prefH,
                           ReportElement elem, Painter painter,
                           int offsetX, int offsetY, int rotation) {
      super(elem);
      this.x = (int) x;
      this.y = (int) y;
      this.painterW = painterW;
      this.painterH = painterH;
      this.prefH = prefH;
      this.prefW = prefW;
      this.pd = pd;
      this.painter = painter;
      this.offsetX = offsetX;
      this.offsetY = offsetY;
      this.rotation = rotation;

      fg = elem.getForeground();
      bg = elem.getBackground();

      if(elem instanceof HyperlinkSupport) {
         processHyperlink();
      }
   }

   /**
    * Process Hyperlink.
    */
   protected void processHyperlink() {
      Hyperlink link = ((PainterElement) elem).getHyperlink();

      if(link != null) {
         setHyperlink(new Hyperlink.Ref(link));
      }
   }

   /**
    * Get painter background.
    */
   public Color getBackground() {
      return bg;
   }

   /**
    * Get painter foreground.
    */
   public Color getForeground() {
      return fg;
   }

   /**
    * Set an user object. The object must be serializable.
    */
   @Override
   public void setUserObject(Object obj) {
      super.setUserObject(obj);
   }

   /**
    * Get the painter rotation angle in degrees.
    */
   public int getRotation() {
      return rotation;
   }

   /**
    * Check if this paintable is a fragment of a painter.
    */
   public boolean isFragment() {
      return offsetY > 0 || offsetY + painterH < prefH;
   }

   /**
    * Prepare the painter. Handle the rotation.
    */
   public Painter preparePainter() {
      int rotation2 = rotation % 360;

      // check for rotation
      if(rotation == 90 || rotation == 270) {
         return new RotatedPainter(painter, rotation);
      }
      else if(rotation2 > 0 && rotation2 < 360 && painter instanceof HTMLSource) {
         return new RotatedPainter(painter, rotation2);
      }

      return painter;
   }

   /**
    * Paint the image/painter.
    */
   @Override
   public void paint(Graphics g) {
      Shape clip = g.getClip();
      Font ofont = g.getFont();

      int border = getBorderWidth();
      // Graphics2D.clip() (int Gop2D) does not seem to work on Images
      g.clipRect((int) x, (int) y, (int) painterW + border, (int) painterH + border);
      g.setFont(getElement().getFont());

      Painter painter0 = preparePainter();
      float bufferW = pd.width * painterW / Math.max(1, prefW);
      // painterH may be different than the d.height if painter
      // is broken up (offsetY > 0), the buffer reflect the
      // actual size of the paintable area on current page
      float bufferH = pd.height * painterH / Math.max(1, prefH);
      boolean bufit = !painter0.isScalable() &&
         (bufferW != painterW || bufferH != painterH);

      if(getElement() instanceof ImageElementDef && painter0 instanceof ImagePainter) {
         boolean aspect = ((ImageElementDef) getElement()).isAspect();
         ((ImagePainter) painter0).setAspect(aspect);
      }

      // if the painter is scalable, the painter and buffer size
      // are always the same, don't buffer the image in these
      // cases. (text don't look nice when printed to image then
      // copied to printer)
      if(bufit) {
         Common.paint(g, x + panX, y + panY, painterW, painterH, painter0, -offsetX,
                      -offsetY, pd.width, pd.height, bufferW, bufferH, fg, bg, 0, -1);
      }
      else {
         paint(g, painter0, painterW, painterH);
      }

      g.setClip(clip);
      g.setFont(ofont);
   }

   /**
    * Get the border width to add to the paintable area.
    */
   protected int getBorderWidth() {
      return 0;
   }

   /**
    * Paint the painter paintable.
    * @param g the graphics.
    * @param painter0 the painter.
    * @param bufferW the width of paint buffer.
    * @param bufferH the height of paint buffer.
    */
   protected void paint(Graphics g, Painter painter0, float bufferW, float bufferH) {
      if(bg != null) {
         paintBg(g, painter0, bufferW, bufferH);
      }

      paintFg(g, painter0, bufferW, bufferH);
   }

   /**
    * Paint the painter paintable.
    * @param g the graphics.
    * @param painter0 the painter.
    * @param bufferW the width of paint buffer.
    * @param bufferH the height of paint buffer.
    */
   protected void paintFg(Graphics g, Painter painter0, float bufferW, float bufferH) {
      g.setColor(fg);
      painter0.paint(g, (int) (x - offsetX + panX), (int) (y - offsetY + panY), pd.width, pd.height);
   }

   /**
    * Paint the painter paintable.
    * @param g the graphics.
    * @param painter0 the painter.
    * @param bufferW the width of paint buffer.
    * @param bufferH the height of paint buffer.
    */
   protected void paintBg(Graphics g, Painter painter0, float bufferW, float bufferH) {
      g.setColor(bg);
      g.fillRect((int) x, (int) y, (int) bufferW, (int) bufferH);
   }

   /**
    * Return the bounds of this paintable area.
    * @return area bounds or null if element does not occupy an area.
    */
   @Override
   public Rectangle getBounds() {
      return new Rectangle((int) x, (int) y, (int) painterW, (int) painterH);
   }

   /**
    * Return the Dimension of this paintable area.
    * @return the Dimension of this paintable area.
    */
   public Dimension getDimension() {
      return pd;
   }

   /**
    * Return the y offset of the painter.
    * @return the y offset of the painter.
    */
   public int getYOffset() {
      return offsetY;
   }

   /**
    * Return the x offset of the painter.
    * @return the x offset of the painter.
    */
   public int getXOffset() {
      return offsetX;
   }

   /**
    * Check if this painter is painting with no clipping and transformation.
    */
   public boolean isIdentityTransform() {
      return prefW == pd.width && prefH == pd.height && offsetX == 0 &&
         offsetY == 0 && pd.width == (int) painterW &&
         pd.height == (int) painterH;
   }

   /**
    * Set the location of this paintable area. This is used internally
    * for small adjustments during printing.
    * @param loc new location for this paintable.
    */
   @Override
   public void setLocation(Point loc) {
      x = loc.x;
      y = loc.y;
   }

   /**
    * Get the location of this paintable.
    */
   @Override
   public Point getLocation() {
      return new Point((int) x, (int) y);
   }

   /**
    * Get the painter that is used to paint this element.
    */
   public Painter getPainter() {
      return painter;
   }

   /**
    * Get the hyper link on this element.
    */
   @Override
   public Hyperlink.Ref getHyperlink() {
      return link;
   }

   /**
    * Set the hyper link of this element.
    */
   public void setHyperlink(Hyperlink.Ref link) {
      this.link = link;
   }

   /**
    * Get the drill hyperlinks on this element.
    */
   protected Hyperlink.Ref[] getDrillHyperlinks() {
      return dlinks;
   }

   /**
    * Set the drill hyperlinks of this element.
    */
   protected void setDrillHyperlinks(Hyperlink.Ref[] links) {
      if(links == null) {
         links = new Hyperlink.Ref[0];
      }

      this.dlinks = links;
   }

   /**
    * Get all hyperlinks of this element, including hyperlink and drill
    * hyperlinks.
    */
   public Hyperlink.Ref[] getHyperlinks() {
      if(getHyperlink() == null) {
         return dlinks;
      }

      Hyperlink.Ref[] links = new Hyperlink.Ref[dlinks.length + 1];
      links[0] = getHyperlink();

      for(int i = 0; i < dlinks.length; i++) {
         links[i + 1] = dlinks[i];
      }

      return links;
   }

   /**
    * Return the areas that have a hyperlink defined. The hyperlink area is
    * similar to imagemap in HTML. Each sub-area in a painter/image can have
    * a different hyperlink.
    * @return enumeration of Shape objects.
    */
   public Enumeration getHyperlinkAreas() {
      return Collections.emptyEnumeration();
   }

   /**
    * Get the hyper link on this element for specified location.
    */
   public Hyperlink.Ref getHyperlink(Point loc) {
      return null;
   }

   /**
    * Get the all drill hyperlinks on this element for specified location.
    */
   public Hyperlink.Ref[] getHyperlinks(Point loc) {
      return null;
   }

   /**
    * Check if this paintable must wait for the entire report to be processed.
    * This is true for elements that need information from report, such
    * as page total, table of contents page index.
    */
   @Override
   public boolean isBatchWaiting() {
      return false;
   }

   /**
    * Get the pan X offset.
    */
   public int getPanX() {
      return panX;
   }

   /**
    * Set the pan X offset. This is used in interactive panning in studio.
    */
   public void setPanX(int panX) {
      this.panX = panX;
   }

   /**
    * Get the pan Y offset.
    */
   public int getPanY() {
      return panY;
   }

   /**
    * Set the pan Y offset. This is used in interactive panning in studio.
    */
   public void setPanY(int panY) {
      this.panY = panY;
   }

   /**
    * Read Object.
    */
   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException
   {
      s.defaultReadObject();
      elem = (ReportElement) s.readObject();
      Object obj = s.readObject();

      if(obj == null || !obj.equals("null")) {
         link = (Hyperlink.Ref) obj;
      }
   }

   /**
    * Write Object.
    */
   private void writeObject(ObjectOutputStream stream) throws IOException {
      // @by jasons, if the background is transparent (null), we need to store
      // the background color of the parent section band for use later
      if(bg == null && (elem instanceof BaseElement)) {
         Object parent = ((BaseElement) elem).getParent();

         if(parent != null && parent instanceof SectionBand) {
            bg = ((SectionBand) parent).getBackground();
         }
      }

      stream.defaultWriteObject();
      stream.writeObject(elem);

      if(link != null) {
         stream.writeObject(link);
      }
      else {
         // so readObject doesn't get out of sync
         stream.writeObject("null");
      }
   }

   protected float x, y;
   protected Painter painter;
   protected float painterW, painterH;
   protected Color fg, bg;

   private int offsetX, offsetY, prefH, prefW;
   private int rotation = 0;
   private Dimension pd; // painter preferred size
   private Hyperlink.Ref link = null;
   private Hyperlink.Ref[] dlinks = new Hyperlink.Ref[0];

   private transient int panX, panY;

   private static final Logger LOG = LoggerFactory.getLogger(PainterPaintable.class);
}
