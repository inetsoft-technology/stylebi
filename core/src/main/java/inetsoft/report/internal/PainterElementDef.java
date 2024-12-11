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
package inetsoft.report.internal;

import inetsoft.report.*;
import inetsoft.report.internal.info.ElementInfo;
import inetsoft.report.internal.info.PainterElementInfo;
import inetsoft.report.painter.*;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.*;

/**
 * Painter element.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class PainterElementDef extends BaseElement
   implements PainterElement, java.io.Serializable
{
   /**
    * New Painter element.
    */
   public PainterElementDef(ReportSheet report, Painter painter) {
      super(report, false);
      this.layout = report.painterLayout;
      this.anchor = report.anchor;
      this.wrapping = report.wrapping;
      this.margin = report.painterMargin;

      report.anchor = null;
      setPainter(painter);
   }

   /**
    * New Painter element.
    */
   public PainterElementDef() {
      super();
      layout = ReportSheet.PAINTER_NON_BREAK;
      anchor = new Position(0.0F, 0.0F);
      wrapping = ReportSheet.WRAP_BOTH;
      painter = null;
   }

   /**
    * Create a painter element with fixed size.
    */
   public PainterElementDef(ReportSheet report, Painter painter,
                            double winch, double hinch) {
      this(report, painter);

      if(winch > 0 && hinch > 0) {
         setSize(new Size((float) winch, (float) hinch));
      }
   }

   /**
    * Get the max painter size.
    */
   private static Size getMaxSize(BaseElement elem, Size size) {
      ReportSheet report = elem.getReport();

      if(report instanceof TabularSheet) {
         return getMaximumPainterSize((TabularSheet) report, elem, size);
      }

      return size;
   }

   /**
    * Get maximum painter size.
    */
   private static Size getMaximumPainterSize(TabularSheet xsheet, BaseElement elem, Size sz) {
      float f = 0;
      Rectangle rect = elem.getFrame();
      double hindent = elem.getIndent();

      if(rect == null) {
         Size size = xsheet.getPageSize();

         if(xsheet.isLandscape()) {
            size = size.rotate();
         }

         rect = new Rectangle(0, 0, (int) (size.width * 72),
            (int) (size.height * 72));
      }

      Point cell = xsheet.getElementCell(elem);

      if(cell == null) {
         f = rect.width;
      }
      else {
         Rectangle span = xsheet.getCellSpan(cell.y, cell.x);

         if(span != null) {
            for(int i = cell.x + span.x; i < cell.x + span.width; i++) {
               f += xsheet.getColWidthPoints(i);
            }
         }
         else {
            f = xsheet.getColWidthPoints(cell.x);
         }
      }

      sz = new Size(sz);
      sz.width = Math.min(sz.width, (float) (f / 72.0 - hindent));

      if(!elem.isBreakable() && !(elem.getParent() instanceof FixedContainer)) {
         sz.height = Math.min(sz.height, (float) (rect.height / 72.0));
      }

      return sz;
   }

   /**
    * CSS Type to be used when styling this element.
    */
   @Override
   public String getCSSType() {
      return CSSConstants.PAINTER;
   }

   /**
    * Get the painter object.
    */
   @Override
   public Painter getPainter() {
      return painter;
   }

   /**
    * Set the painter object.
    */
   @Override
   public void setPainter(Painter painter) {
      this.painter = painter;
   }

   /**
    * Get the painter external space.
    */
   @Override
   public Insets getMargin() {
      return margin;
   }

   /**
    * Set the painter external space.
    */
   @Override
   public void setMargin(Insets margin) {
      this.margin = margin;
   }

   public byte[] getSVGImage() {
      return (painter instanceof ImagePainter) ?
         ((ImagePainter) painter).getSvgImage() : null;
   }

   public void setSVGImage(byte[] data, Dimension size) {
      setPainter(new ImagePainter(data, size));
   }

   /**
    * Get associated image if the painter is an image painter.
    */
   public Image getImage() {
      return (painter instanceof ImagePainter) ?
         ((ImagePainter) painter).getImage() : null;
   }

   /**
    * Set an image painter to paint the image.
    */
   public void setImage(Image img) {
      setPainter(new ImagePainter(img));
   }

   /**
    * Get the html string if the painter is a HTMLSource.
    */
   public String getHTML() {
      return (painter instanceof HTMLSource) ?
         ((HTMLSource) painter).getHTML() : null;
   }

   /**
    * Set the html to be displayed using HTMLSource.
    */
   public void setHTML(String html) {
      setPainter(new HTMLSource(html));
   }

   /**
    * Get the rotation in degrees.
    */
   @Override
   public int getRotation() {
      return rotation;
   }

   /**
    * Set the rotation degrees. It can be 0, 90, or 270.
    */
   @Override
   public void setRotation(int degree) {
      rotation = degree;
   }

   /**
    * Return the size that is needed for this element.
    */
   @Override
   public Size getPreferredSize() {
      Painter p = getPainterForPreferredSize();
      return getPainterPreferredSize(p);
   }

   Size getPainterPreferredSize(Painter p) {
      int bw = 0, bh = 0;

      if(margin != null) {
         bw = margin.left + margin.right;
         bh = margin.top + margin.bottom;
      }

      Size psize = getSize();

      if(psize != null) {
         return calculateMaxSize(psize, bw, bh);
      }

      Dimension ppsize = p == null ? new Dimension(0, 0) : p.getPreferredSize();

      // @by larryl, if we are getting proportional size (chart), don't add
      // margin to the preferred size since the preferred size in this case
      // should already include the margin
      if(ppsize.width < 0) {
         bw = 0;
      }

      // @by mikec, for auto sized chart if user set a margin larger than it's
      // auto calculated height, the chart will disappeared.
      if(getSize() != null && ppsize.height < 0) {
         bh = 0;
      }

      Dimension d = processPreferredSize(ppsize);
      return new Size(d.width + bw, d.height + bh);
   }

   /**
    * Get the painter prepared for return prefer size.
    */
   protected Painter getPainterForPreferredSize() {
      return painter;
   }

   protected Size calculateMaxSize(Size psize, int bw, int bh) {
      Size sz = getMaxSize(this, psize);
      return new Size(Math.min(sz.width * 72, psize.width * 72 + bw),
         Math.min(sz.height * 72, psize.height * 72 + bh));
   }

   /**
    * Return true if the painter can be broken into segments.
    */
   @Override
   public boolean isBreakable() {
      return layout == ReportSheet.PAINTER_BREAKABLE;
   }

   /**
    * Process the preferred size, deal with rotation and negative size.
    *
    * @param d the preferred dimension to be processed.
    * @return the result dimension.
    */
   protected Dimension processPreferredSize(Dimension d) {
      if(rotation == 90 || rotation == 270) {
         d = new Dimension(d.height, d.width);
      }
      else {
         d = getExternalRectangle(d);
      }

      final ReportSheet sheet = getReport();

      if(d.width < 0) {
         d.width = -d.width * sheet.printBox.width / 1000;
      }

      if(d.height < 0) {
         d.height = -d.height * sheet.printBox.width / 1000;
      }

      Painter p = getPainterForPreferredSize();

      // if text width wider than page width wrap it to avoid
      // overruning the page and be truncated
      if(p instanceof ExpandablePainter) {
         ExpandablePainter painter2 = (ExpandablePainter) p;

         if(painter2.isExpandable()) {
            Position anchor = getAnchor();
            int pagewidth = sheet.printBox.width;

            // adjust for anchor position
            if(anchor != null) {
               pagewidth -= (int) (anchor.x * 72);
            }

            if(d.width > pagewidth && pagewidth != 0) {
               Insets margin = getMargin();

               // @by larryl, in this case, the width is fixed at the page
               // width so the margin width should be subtracted (it will be
               // added back later).
               if(margin != null) {
                  pagewidth -= (margin.left + margin.right);
               }

               d = painter2.getPreferredSize(pagewidth);
            }
         }
      }

      return d;
   }

   /**
    * Get external rectangle of the internal rectangle.
    */
   private Dimension getExternalRectangle(Dimension d) {
      int rotation2 = rotation % 360;

      if(rotation2 > 0 && rotation2 < 360 && rotation2 % 90 != 0 &&
         painter instanceof HTMLSource)
      {
         // if the rotation is in the second quadrant and the fourth
         // quadrant, we should use another angle to calculate size
         boolean converse = rotation2 > 90 && rotation2 < 180 ||
            rotation2 > 270 && rotation2 < 360;
         int angle = rotation2 % 90;
         angle = converse ? 90 - angle : angle;
         int h1 =
            (int) Math.ceil(Math.cos(Math.toRadians(angle)) * d.height);
         int h2 = (int) Math.ceil(Math.sin(Math.toRadians(angle)) * d.width);
         int w1 =
            (int) Math.ceil(Math.sin(Math.toRadians(angle)) * d.height);
         int w2 = (int) Math.ceil(Math.cos(Math.toRadians(angle)) * d.width);
         int nh = h1 + h2;
         int nw = w1 + w2;

         ((HTMLSource) painter).setOriginOffset(converse ? h1 : w1);

         return new Dimension(nw, nh);
      }

      return d;
   }

   /**
    * Reset the printing so the any remaining portion of the painter
    * is ignored, and the next call to print start fresh.
    */
   @Override
   public void resetPrint() {
      super.resetPrint();
      offsetY = 0;
      offsetX = 0;
      consumedY = 0;
      lastH = 0;
      lastW = 0;
      anchorDepth = 0;
      finished = false;
   }

   /**
    * Check if this element has already finished printing.
    */
   public boolean isFinished() {
      return finished;
   }

   /**
    * Get the minimum height for a paintable.
    */
   public float getMinimumHeight() {
      return 0;
   }

   /**
    * Print the element at the printHead location.
    * @return true if the element is not completely printed and
    * need to be called again.
    */
   @Override
   public boolean print(StylePage pg, ReportSheet report) {
      if(!checkVisible() || finished || painter == null) {
         return false;
      }

      ptsize = getPreferredSize();
      int xadj = 0, yadj = 0;
      int hmargin = 0, vmargin = 0;

      if(margin != null) {
         hmargin = margin.left + margin.right;
         vmargin = margin.top + margin.bottom;
         ptsize.width -= hmargin;
         ptsize.height -= vmargin;
         xadj = margin.left;
         yadj = margin.top;

         // adjusted for consumed margin
         if(consumedY > 0) {
            int consumedMarginTop = Math.min(margin.top, consumedY);

            yadj -= consumedMarginTop;
            vmargin -= consumedMarginTop;
         }
      }

      // @by larryl, if the painter has been exhaused, there is no more to print
      // For header and footer, the reset() should be called so offsetY == 0
      if(isEnd()) {
         return false;
      }

      super.print(pg, report);

      // could be changed in super.print()
      if(!checkVisible()) {
         return false;
      }

      float areaH = report.printBox.height - report.printHead.y - vmargin;
      float areaW = report.printBox.width - report.printHead.x - hmargin;

      // @by larryl, avoid infinite loop in case margin is very large
      if(areaH < 0 || areaW < 0) {
         LOG.error(
            "Element skipped because there is not enough space to print: " +
            getID());
         return false;
      }

      // @by larryl, if the continuation is printed in different width, the
      // content could be missed or skewed. Warn user.
      if(offsetY > 0 && lastW != areaW) {
         LOG.warn(
            "Painter continued in an area with different width " +
            "from the previous area, content could be lost: " + getID());
      }

      lastW = (short) areaW;

      // if the space is less than the minimum height for a paintable,
      // force the area to be at least the minimum height, or return true
      // to continue on the next page. In the case of section,
      // if this causes the paintable to be out of the bounds
      // of the band, the rewind/continuation logic takes care of it
      float minH = getMinimumHeight();

      if(areaH < minH) {
         if(isInSection()) {
            areaH = minH;
         }
         else {
            // check if should skip the element altogether
            return !report.skip(minH, areaH);
         }
      }

      // if scalable, the preferred size of the painter is essentially
      // ignored if it's different from the explicitly set size
      Dimension pd = null;

      if(painter.isScalable()) {
         pd = ptsize.getDimension();
      }
      else {
         pd = painter.getPreferredSize();

         if(rotation == 90 || rotation == 270) {
            pd = new Dimension(pd.height, pd.width);
         }
      }

      float painterH = getPainterH(ptsize.height - offsetY, areaH);
      float painterW = Math.min(ptsize.width - offsetX, areaW);

      if(areaH < lastH && report.printHead.y > 0) {
         report.printHead.y += areaH + 1;
         return true;
      }

      if(lastH > 0) {
         painterH = (int) Math.min(painterH, lastH);
      }

      // negative preferred width/height are treated as ratio
      if(pd.width < 0) {
         pd.width = -pd.width * report.printBox.width / 1000;
      }

      if(pd.height < 0) {
         pd.height = -pd.height * report.printBox.width / 1000;
      }

      pd.width = (int) Math.min(pd.width,
         report.printBox.width - report.printHead.x - hmargin);

      if(painter instanceof TextPainter) {
         TextLens lens = ((TextPainter) painter).getTextLens();

         // check if this text as {P} tag and the page number is < 1, skip
         if((lens instanceof HeaderTextLens) &&
            (lens.getText().toUpperCase().indexOf("{P}") >= 0 ||
            lens.getText().toUpperCase().indexOf("{P,") >= 0))
         {
            HFTextFormatter fmt = report.getHFTextFormatter();

            if(fmt != null && fmt.getPageNumber() < 1) {
               return false;
            }
         }
      }

      Painter painter2 = painter;

      try {
         if(painter instanceof TextPainter) {
            painter2 = (Painter) ((TextPainter) painter).clone(null, true);
         }
         else if(painter instanceof PresenterPainter) {
            painter2 = (Painter) ((PresenterPainter) painter).clone();
         }
         else if(painter instanceof ChartPainter) {
            painter2 = (Painter) ((ChartPainter) painter).clone();
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to clone painter", ex);
         painter2 = painter;
      }

      PainterPaintable pt = createPaintable(
         report.printHead.x + report.printBox.x + xadj,
         report.printHead.y + report.printBox.y + yadj,
         painterW, painterH, pd, (int) ptsize.width, (int) ptsize.height,
         this, painter2, offsetX, offsetY, rotation);

      // @by larryl, if the painter can't fit due to line boundary adjustment,
      // continue on the next page
      if(pt == null) {
         return true;
      }

      pg.addPaintable(pt);

      // the paintable height may be adjusted (to multiple of lines) so
      // we use the paintable height for painterH, but this only make
      // sense when difference >= 1, for the returned value by getBounds
      // is a rectangle contains only int values, and painterH is a float
      if(Math.abs(painterH - pt.getBounds().height) >= 1) {
         painterH = pt.getBounds().height;
      }

      if(getWrapping() != ReportSheet.WRAP_NONE) {
         report.printHead.x += painterW + hmargin;
         report.printHead.y += painterH + vmargin;
      }

      offsetX += (int) Math.ceil(painterW);

      // @by larryl, if painter inside a section, don't wrap horizontally
      if(offsetX >= ptsize.width || isInSection()) {
         offsetY += Math.ceil(painterH);
         consumedY += Math.ceil(painterH);
         offsetX = 0;
         lastH = 0;
      }
      else {
         lastH = painterH;
      }

      // remember if the painter has finished printing so we can skip
      // the anchored painter later if it's already finished
      if(isEnd()) {
         finished = true;
      }

      // @by larryl, if a painter is in a section, we always mark it as
      // breakable so if a band go across page, it can be printed completely.
      // Otherwise we would need to push the painter, and everything after it
      // to the next page to avoid missing content, which is complex to do
      return (isBreakable() || isInSection()) && !isEnd() &&
         offsetX < ptsize.width;
   }

   /**
    * Get the getPainterH.
    */
   protected float getPainterH(float painterH, float areaH) {
      return Math.min(painterH, areaH);
   }

   /**
    * Check if has reach the end of the element.
    * @return
    */
   protected boolean isEnd() {
      if(ptsize == null) {
         ptsize = getPreferredSize();
      }

      return offsetY >= ptsize.height;
   }

   /**
    * Create a PainterPaintable.
    */
   protected PainterPaintable createPaintable(float x, float y,
                                              float painterW, float painterH,
                                              Dimension pd, int prefW, int prefH,
                                              ReportElement elem,
                                              Painter painter, int offsetX,
                                              int offsetY, int rotation) {
      // adjust textbox height to multiple of lines
      if(painter instanceof ExpandablePainter &&
	 (offsetY > 0 || offsetY + painterH < prefH))
      {
         ExpandablePainter painter2 = (ExpandablePainter) painter;

         painterH -= painter2.getHeightAdjustment(this, pd, offsetY,
                                                  painterW, painterH);

         if(painterH <= 0) {
            return null;
         }
      }

      return new PainterPaintable(x, y, painterW, painterH, pd, prefW, prefH,
				  elem, painter, offsetX, offsetY, rotation);
   }

   /**
    * Set the size of the element. The size does not include the margin
    * (white space) around the painter.
    * @param size size in inches.
    */
   @Override
   public void setSize(Size size) {
      if("true".equals(getProperty(ReportElement.AUTOSIZE))) {
         ((PainterElementInfo) einfo).setSize(null);
         return;
      }

      ((PainterElementInfo) einfo).setSize(size);
   }

   /**
    * Get the size of the element.
    * @return size in inches.
    */
   @Override
   public Size getSize() {
      return ((PainterElementInfo) einfo).getSize();
   }

   /**
    * Set the wrapping style of this element.
    */
   @Override
   public void setWrapping(int wrapping) {
      this.wrapping = wrapping;
   }

   /**
    * Get the wrapping style of this element.
    */
   @Override
   public int getWrapping() {
      return wrapping;
   }

   /**
    * Set the layout option. Breakable or non-break.
    */
   @Override
   public void setLayout(int opt) {
      layout = opt;
   }

   /**
    * Get the layout option of this element.
    */
   @Override
   public int getLayout() {
      return layout;
   }

   /**
    * Distance from last element.
    */
   @Override
   public Position getAnchor() {
      return anchor;
   }

   /**
    * Distance from last element.
    */
   @Override
   public void setAnchor(Position anchor) {
      // clear anchor element if anchor is removed
      if(anchor == null) {
         anchorElem = null;
      }

      this.anchor = anchor;
   }

   /**
    * Get the hyper link on this element.
    */
   @Override
   public Hyperlink getHyperlink() {
      return link;
   }

   /**
    * Set the hyper link of this element.
    */
   @Override
   public void setHyperlink(Hyperlink link) {
      this.link = link;
   }

   /**
    * Get the hyper link on this element for the specified area.
    * @deprecated, only the element contains linked shape painter supports
    * this method.
    */
   @Deprecated
   @Override
   public Hyperlink getHyperlink(Shape shape) {
      if(linkmap == null) {
         return null;
      }

      return (Hyperlink) linkmap.get(shape);
   }

   /**
    * Set the hyper link of this element for the specified area.
    * @deprecated, only the element contains linked shape painter supports
    * this method.
    */
   @Deprecated
   @Override
   public void setHyperlink(Shape shape, Hyperlink link) {
      if(link == null) {
         linkmap.remove(shape);

         if(linkmap.size() == 0) {
            linkmap = null;
         }
      }
      else {
         if(linkmap == null) {
            linkmap = new Hashtable();
         }

         linkmap.put(shape, link);
      }
   }

   /**
    * Return the areas that have a hyperlink defined. The hyperlink area is
    * similar to imagemap in HTML. Each sub-area in a painter/image can have
    * a different hyperlink.
    * @return enumeration of Shape objects.
    */
   @Override
   public Enumeration getHyperlinkAreas() {
      if(linkmap == null) {
         return Collections.emptyEnumeration();
      }

      return linkmap.keys();
   }

   /**
    * A painter without allowing wrapping on either side is a block
    * element.
    */
   @Override
   public boolean isBlock() {
      return super.isBlock() || wrapping == ReportSheet.WRAP_TOP_BOTTOM;
   }

   /**
    * Return true if this element should start a new line.
    */
   @Override
   public boolean isNewline() {
      return super.isNewline() || wrapping == ReportSheet.WRAP_RIGHT ||
             wrapping == ReportSheet.WRAP_TOP_BOTTOM ||
             (anchor != null && anchor.y > 0);
   }

   /**
    * Return true if this element is the last one on a line.
    */
   @Override
   public boolean isLastOnLine() {
      return wrapping == ReportSheet.WRAP_LEFT ||
             wrapping == ReportSheet.WRAP_TOP_BOTTOM;
   }

   /**
    * Set the attributes of this element.
    */
   @Override
   public void setContext(ReportElement elem) {
      super.setContext(elem);

      if(elem instanceof Context) {
         Context c = (Context) elem;

         setLayout(c.getPainterLayout());
      }
   }

   /**
    * internal.
    */
   public float getAnchorDepth() {
      return anchorDepth;
   }

   /**
    * internal.
    */
   public void setAnchorDepth(float depth) {
      anchorDepth = Math.min(depth, (anchor != null) ? anchor.y : 0);
   }

   /**
    * internal.
    */
   public ReportElement getAnchorElement() {
      return anchorElem;
   }

   /**
    * internal.
    */
   public void setAnchorElement(ReportElement elem) {
      anchorElem = elem;
   }

   /**
    * Get the X anchor distance to the left in pixels.
    */
   public float getAnchorX() {
      if(getAnchor() == null) {
         return 0;
      }

      return (getAnchor().x >= 0) ?
         getAnchor().x * 72 :
         (getAnchor().x * 72 + getReport().printBox.width);
   }

   public String toString() {
      return getID();
   }

   @Override
   public String getType() {
      return "Painter";
   }

   /**
    * Create a proper element info to save the attribute of this element.
    */
   @Override
   protected ElementInfo createElementInfo() {
      return new PainterElementInfo();
   }

   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException
   {
      s.defaultReadObject();

      try {
         if(s.readBoolean()) {
            anchorElem = new BaseElement();
            ((BaseElement) anchorElem).readObjectMin(s);
            anchorElem = (ReportElement) ObjectCache.get(anchorElem);
         }
      }
      catch(java.io.OptionalDataException ex) {
      }

      int linkcnt = s.readInt();

      if(linkcnt > 0) {
         linkmap = new Hashtable();

         for(int i = 0; i < linkcnt; i++) {
            Object obj = s.readObject();

            if(obj instanceof String) {
               double x = s.readDouble();
               double y = s.readDouble();
               double w = s.readDouble();
               double h = s.readDouble();

               obj = new Ellipse2D.Double(x, y, w, h);
            }

            linkmap.put(obj, s.readObject());
         }
      }
   }

   private void writeObject(ObjectOutputStream stream) throws IOException {
      stream.defaultWriteObject();

      if(anchorElem != null) {
         stream.writeBoolean(true);
         ((BaseElement) anchorElem).writeObjectMin(stream);
      }
      else {
         stream.writeBoolean(false);
      }

      if(linkmap == null) {
         stream.writeInt(0);
      }
      else {
         stream.writeInt(linkmap.size());
         Enumeration keys = linkmap.keys();

         while(keys.hasMoreElements()) {
            Object obj = keys.nextElement();

            if(obj instanceof Ellipse2D) {
               double x = 0, y = 0, w = 0, h = 0;

               if(obj instanceof Ellipse2D.Double) {
                  Ellipse2D.Double e = (Ellipse2D.Double) obj;

                  x = e.getX();
                  y = e.getY();
                  w = e.getWidth();
                  h = e.getHeight();
               }
               else if(obj instanceof Ellipse2D.Float) {
                  Ellipse2D.Float e = (Ellipse2D.Float) obj;

                  x = (double) e.getX();
                  y = (double) e.getY();
                  w = (double) e.getWidth();
                  h = (double) e.getHeight();
               }

               stream.writeObject("java.awt.geom.Ellipse2D");
               stream.writeDouble(x);
               stream.writeDouble(y);
               stream.writeDouble(w);
               stream.writeDouble(h);
            }
            else {
               stream.writeObject(obj);
            }

            stream.writeObject(linkmap.get(obj));
         }
      }
   }

   /**
    * Make a copy of this element.
    */
   @Override
   public Object clone() {
      PainterElementDef elem = (PainterElementDef) super.clone();

      if(linkmap != null) {
         elem.linkmap = (Hashtable) linkmap.clone();
      }

      return elem;
   }

   protected Insets margin;

   private Painter painter;
   private int layout;
   private int wrapping;
   // distance from last element. If the y is negative, it's the
   // distance from the top of the line
   private Position anchor = null;
   private int offsetX; // the left position of painter to print
   protected int offsetY; // the top position of painter to print
   private int consumedY; // consumed y including margin
   private float lastH; // the last image section height if wrapped horizontally
   private float anchorDepth = 0; // anchor used so far
   private int rotation = 0; // no rotation
   private Hyperlink link; // hyperlink
   // the element this is anchored against
   private transient ReportElement anchorElem = null;
   private transient Hashtable linkmap = null; // Shape -> Hyperlink
   // ignore next time print is called
   private transient boolean finished = false;
   private transient short lastW = 0; // last printing width
   private Size ptsize = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(PainterElementDef.class);
}
