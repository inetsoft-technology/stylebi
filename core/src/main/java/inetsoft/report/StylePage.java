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
package inetsoft.report;

import inetsoft.report.internal.*;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.VariableTable;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.security.Principal;
import java.util.*;
import java.util.zip.*;

/**
 * A StylePage is created for each page to be printed. The StylePage object
 * contains the information on how to print a page. It is used to
 * provide an abstraction of a page, and allows more sophisticated printing
 * and better effeciency.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class StylePage implements java.io.Serializable, Cloneable {
   /**
    * Create a page with the specified size and resolution.
    * @param size page size in pixels.
    */
   public StylePage(Dimension size) {
      this.size = size;
   }

   /**
    * Print the page on to the graphics.
    */
   public void print(Graphics g) {
      print0(g);
   }

   /**
    * Print the page in synchronized mode.
    */
   private synchronized void print0(Graphics g) {
      Common.startPage(g, this);
      paintBg(g, 1.0, 1.0);
      LicenseManager licenseManager = LicenseManager.getInstance();

      if(licenseManager.isElasticLicense() && licenseManager.getElasticRemainingHours() == 0) {
         Util.drawWatermark(g, size);
      }
      else if(licenseManager.isHostedLicense()) {
         Principal user = ThreadContext.getContextPrincipal();

         if(user instanceof SRPrincipal principal) {
            String orgId = principal.getOrgId();
            String username = principal.getName();

            if(licenseManager.getHostedRemainingHours(orgId, username) == 0) {
               Util.drawWatermark(g, size);
            }
         }
      }

      Rectangle rect = g.getClipBounds();
      int count = getPaintableCount();

      for(int i = 0; i < count; i++) {
         Paintable pt = getPaintable(i);

         if(pt == null) {
            continue;
         }

         if(rect == null || pt.getBounds() == null || rect.intersects(pt.getBounds())) {
            pt.paint(g);
         }
      }
   }

   /**
    * Paint the page background on to the graphics.
    */
   public void paintBg(Graphics g, double xratio, double yratio) {
      Dimension d = getBackgroundSize();

      if(d != null) {
         d = new Dimension((int) (d.width * xratio), (int) (d.height * yratio));
      }

      Dimension localsize = new Dimension(size);

      localsize.width *= xratio;
      localsize.height *= yratio;

      int ix, iy, iw, ih;

      ix = 0;
      iy = 0;
      iw = localsize.width;
      ih = localsize.height;

      if(bg instanceof Color) {
         g.setColor((Color) bg);
         switch(getBackgroundLayout()) {
         case StyleConstants.BACKGROUND_CENTER:
            iw = (d == null) ? localsize.width : d.width;
            ih = (d == null) ? localsize.height : d.height;
            iw = (iw > localsize.width) ? localsize.width : iw;
            ih = (ih > localsize.height) ? localsize.height : ih;
            ix = (localsize.width - iw) / 2;
            iy = (localsize.height - ih) / 2;
         default:
            break;
         }

         g.fillRect(ix, iy, iw, ih);
         g.setColor(Color.black);
      }
      else if(bg instanceof Image) {
         try {
            Image icon = (Image) bg;

            if(icon instanceof MetaImage) {
               icon = ((MetaImage) icon).getImage();
            }

            if(icon != null) {
               Tool.waitForImage(icon);

               int iiw = (d == null) ? icon.getWidth(null) : d.width;
               int iih = (d == null) ? icon.getHeight(null) : d.height;

               switch(getBackgroundLayout()) {
               case StyleConstants.BACKGROUND_CENTER:
                  ix = (localsize.width - iiw) / 2;
                  iy = (localsize.height - iih) / 2;
                  iw = iiw;
                  ih = iih;
               default:
                  break;
               }

               if(iiw > 0 && iih > 0) {
                  for(int y = iy; y < iy + ih; y += iih) {
                     for(int x = ix; x < ix + iw; x += iiw) {
                        g.drawImage(icon, x, y, iiw, iih, null);
                     }
                  }
               }
            }
         }
         catch(Exception ex) {
            // ignore if can not draw the background image
            LOG.warn("Failed to load background image", ex);
         }
      }
   }

   /**
    * Add a warning information to this style page.
    */
   public void addInfo(String info) {
      if(info != null && info.length() > 0) {
         infos.add(info);
      }
   }

   /**
    * Complete all the warning information. They will be added to
    * this style page as printables.
    */
   public void completeInfo() {
      for(int i = 0; i < infos.size(); i++) {
         String info = (String) infos.get(i);
         PageLayout.InfoText ishape = new PageLayout.InfoText(info, this);
         Rectangle bounds = ishape.getBounds();

         if(bounds != null) {
            Paintable pt = ishape.getPaintable();
            boundsMap.put(info, bounds);

            if(pt instanceof TextPaintable) {
               TextPaintable textPt = (TextPaintable) pt;
               textPt.setDisplayOnTree(false);
               addPaintable(textPt);
            }
            else {
               addPaintable(pt);
            }
         }
      }

      infos.clear();
   }

   /**
    * Add a paintable area to the page.
    */
   public void addPaintable(Paintable pt) {
      // if hyperlink defined on paintable, check and set report parameters
      try {
         ReportElement elem = pt.getElement();

         if(elem != null) {
            ReportSheet report = ((BaseElement) elem).getReport();

            if(report != null) {
               VariableTable params = report.getVariableTable();

               if(params != null && params.size() > 0) {
                  Hyperlink.Ref link = ((BasePaintable) pt).getHyperlink();

                  if(link != null && link.isSendReportParameters()) {
                     addParameters(link, params);
                  }
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to add parameters to paintable", ex);
      }

      if((pt instanceof BasePaintable) &&
         ((BasePaintable) pt).isBatchWaiting())
      {
         batchwait = true;
      }

      items.addElement(pt);
   }

   /**
    * Insert a paintable at the specified location.
    */
   public void insertPaintable(int idx, Paintable pt) {
      items.insertElementAt(pt, idx);

      if((pt instanceof BasePaintable) &&
         ((BasePaintable) pt).isBatchWaiting())
      {
         batchwait = true;
      }
   }

   /**
    * Set a paintable at the specified location.
    */
   public void setPaintable(int idx, Paintable pt) {
      items.setElementAt(pt, idx);

      if((pt instanceof BasePaintable) &&
         ((BasePaintable) pt).isBatchWaiting())
      {
         batchwait = true;
      }
   }

   /**
    * Remove all paintables.
    */
   public void removeAllPaintables() {
      items = new Vector();
   }

   /**
    * Get the number of paintable objects in this style page.
    * @return number of paintables.
    */
   public int getPaintableCount() {
      return items == null ? 0 : items.size();
   }

   /**
    * Get the specified paintable object.
    * @param idx paintable index.
    * @return paintable object at the index.
    */
   public Paintable getPaintable(int idx) {
      // is printlayout mode, then current reportsheet is converted by a vs
      // which applied printlayout, we should get paintable by the order of
      // zindex, to make sure the elements be painted by the same hierarchy
      // as the source component in the target vs.
      if("true".equals(getProperty("printlayoutmode")) &&
         sortedItems != null && idx < sortedItems.length)
      {
         return (Paintable) sortedItems[idx];
      }

      if(items != null && idx < items.size()) {
         return (Paintable) items.get(idx);
      }

      return null;
   }

   /**
    * Remove the specified paintable object.
    * @param idx paintable index.
    */
   public void removePaintable(int idx) {
      items.removeElementAt(idx);
   }

   /**
    * Clear all the paintable areas in the page.
    */
   public void reset() {
      reset(true);
   }

   /**
    * Clear all the paintable areas in the page.
    */
   public synchronized void reset(boolean removal) {
      if(removal) {
         items.removeAllElements();
      }

      swapped = false;
      swapfull = false;
   }

   /**
    * Clear cache.
    */
   public void clearCache() {
      for(int i = 0; i < items.size(); i++) {
         BasePaintable paintable = (BasePaintable) items.get(i);

         // by billh, fix customer bug bug1292532167529
         // under extreme condition, one page might be swapped
         // soon after it's used
         if(paintable != null) {
            paintable.clearCache();
         }
      }
   }

   /**
    * Tell if swap file has already been outputed
    */
   public synchronized boolean isSwapped() {
      return swapped;
   }

   /**
    * This method is called after a page is fully printed.
    */
   public void complete() {
      for(int i = 0; i < items.size(); i++) {
         ((BasePaintable) items.get(i)).complete();
      }
   }

   /**
    * Add report parameters to hyperlink.
    */
   private void addParameters(Hyperlink.Ref link, VariableTable params) {
      Enumeration keys = params.keys();

      while(keys.hasMoreElements()) {
         String name = (String) keys.nextElement();

         if(name.startsWith("__service_")) {
            continue;
         }

         if(link.getParameter(name) != null) {
            continue;
         }

         if(params.isInternalParameter(name)) {
            continue;
         }

         try {
            link.setParameter(name, params.get(name));
         }
         catch(Exception ex) {
            LOG.warn("Failed to set parameter on hyperlink " + link + ": " + name, ex);
         }
      }
   }

   /**
    * Returns the dimensions of the page in pixels.
    * The resolution of the page is chosen so that it
    * is similar to the screen resolution.
    */
   public Dimension getPageDimension() {
      return size;
   }

   /**
    * Set the page dimension in pixels.
    * @param size page size.
    */
   public void setPageDimension(Dimension size) {
      this.size = size;
   }

   /**
    * Get the orientation of current page.
    */
   public int getOrientation() {
      return orient;
   }

   /**
    * Set the orientation of the report page.
    */
   public void setOrientation(int orient) {
      this.orient = (byte) orient;
   }

   /**
    * Get the page margin.
    */
   public Margin getMargin() {
      return margin;
   }

   /**
    * Set the page margin. This is for information only and the actual margin
    * used for printing should be set through the ReportSheet or TabularSheet.
    */
   public void setMargin(Margin margin) {
      this.margin = margin;
   }

   /**
    * Get the page default font.
    * @return page font.
    */
   public Font getFont() {
      return font;
   }

   /**
    * Set the default font of this page.
    * @param font default font.
    */
   public void setFont(Font font) {
      this.font = font;
   }

   /**
    * Get the page default foreground color.
    * @return page foreground color.
    */
   public Color getForeground() {
      return color;
   }

   /**
    * Set the page default foreground color.
    * @param color foreground color.
    */
   public void setForeground(Color color) {
      this.color = color;
   }

   /**
    * Get the page background.
    */
   public Object getBackground() {
      return bg;
   }

   /**
    * Set the background of this page. The background can be either a
    * Color or an Image object.
    * @param bg page background.
    */
   public void setBackground(Object bg) {
      this.bg = bg;
   }

   /**
    * Get the page background layout.
    */
   public int getBackgroundLayout() {
      return bglayout;
   }

   /**
    * Set the background layout of this page.
    * @param lay page background layout.
    */
   public void setBackgroundLayout(int lay) {
      this.bglayout = (byte) lay;
   }

   /**
    * Get the page background layout.
    */
   public Dimension getBackgroundSize() {
      return bgsize;
   }

   /**
    * Set the background layout of this page.
    * @param d page background layout.
    */
   public void setBackgroundSize(Dimension d) {
      this.bgsize = d;
   }

   /**
    * Get a property value.
    * @param name property name.
    * @return property value.
    */
   public Object getProperty(String name) {
      return (properties == null) ? null : properties.get(name);
   }

   /**
    * Set a property.
    * @param name property name.
    * @param val property value.
    */
   public void setProperty(String name, Object val) {
      if(val == null) {
         if(properties != null) {
            properties.remove(name);
         }
      }
      else {
         if(properties == null) {
            properties = new Hashtable();
         }

         properties.put(name, val);
      }
   }

   /**
    * Set the page properties.
    */
   public void setProperties(Map props) {
      this.properties = props;
   }

   /**
    * Get the page num.
    */
   public int getPageNum() {
      return pageNum;
   }

   /**
    * Set the page num.
    */
   public void setPageNum(int pageNum) {
      this.pageNum = pageNum;
   }

   /**
    * Write the page to a file. If processing of the report is not
    * completed, this could be a partial save of the page data.
    * This function is used together with restore() to swap page
    * in and out of memory to conserve space. After this function
    * is called, this page can not be used until a load is called.
    * @param output output stream to save the page.
    * @param completed true if the processing of report has completed.
    * @return true if the page is fully saved, false if it is partially
    * saved.
    */
   public synchronized boolean swap(ObjectOutputStream output,
                                    boolean completed) throws IOException {
      // already swapped, just clear memory.
      // If output stream is valid, it must be rewritten
      // because page is swapped in groups in 5.1
      if(swapped && output == null) {
         if(inmemory) {
            for(int i = 0; i < items.size(); i++) {
               Object pt = items.get(i);
               BasePaintable bpt = (pt instanceof BasePaintable) ?
                 (BasePaintable) pt : null;

               if(swapfull || bpt == null || !bpt.isBatchWaiting()) {
                  if(bpt != null && !bpt.isSerializable()) {
                     continue;
                  }

                  items.set(i, null);
               }
            }

            inmemory = false;
         }

         return true;
      }

      if(output == null) {
         return true;
      }

      boolean rc = save(output, completed, true);

      swapfull = rc;
      swapped = true;
      inmemory = false;

      return rc;
   }

   /**
    * Restore a swapped page.
    * @return true if the data is read in, or false if the data is skipped.
    */
   public synchronized boolean restore(ObjectInputStream inp, boolean removal)
      throws IOException, ClassNotFoundException
   {
      if(!swapped || inmemory) {
         return false;
      }

      load(inp, swapfull, true);
      inmemory = true;

      if(removal) {
         swapped = false;
      }

      return true;
   }

   /**
    * Check if the paintable on this page needs to wait for report to finish
    * processing before printed.
    */
   public boolean isBatchWaiting() {
      return batchwait;
   }

   /**
    * Write the page to a file. If processing of the report is not
    * completed, this could be a partial save of the page data.
    * @param stream output stream to save the page.
    * @param completed true if the processing of report has completed.
    * @param swap if true, free the objects that have been saved.
    * @return true if the page is fully saved, false if it is partially
    * saved.
    */
   protected boolean save(ObjectOutputStream stream, boolean completed,
                          boolean swap) throws IOException
   {
      boolean all = true;
      stream.writeInt(getPaintableCount());
      swapped = swapped || swap;

      for(int i = 0; i < getPaintableCount(); i++) {
         Paintable pt = getPaintable(i);
         BasePaintable bpt = (pt instanceof BasePaintable) ?
            (BasePaintable) pt : null;

         // if a paintable needs to wait for the report to complete, and
         // the report is still being processed, ignore it
         if(!completed && bpt != null && bpt.isBatchWaiting()) {
            all = false;
            continue;
         }

         if(bpt != null && !bpt.isSerializable()) {
            stream.writeObject(bpt.getClass().getName());
            continue;
         }

         stream.writeObject(pt);

         if(swap) {
            items.set(i, null); // remove the paintable from memory
         }
      }

      if(!swap) {
         if(bg instanceof Image) {
            stream.writeChar('I');
            Image icon = (Image) bg;
            byte[] buf = Encoder.encodeImage(icon);
            stream.writeObject(buf);

            if(buf != null) {
               stream.writeInt(icon.getWidth(null));
               stream.writeInt(icon.getHeight(null));
            }
         }
         else {
            stream.writeChar('O');
            stream.writeObject(bg);
         }
      }

      return all;
   }

   /**
    * Get the map contains warning information and its bounds.
    */
   public Map<String, Rectangle> getBoundsMap() {
      return boundsMap;
   }

   /**
    * Loaded page contents from a file. The file should be created by a
    * corresponding save() call.
    * @param s saved data file.
    * @param full true if restoring from a fully (not partial) swapped file.
    * @param swap true if the saved file is swapped.
    */
   protected void load(ObjectInputStream s, boolean full, boolean swap)
      throws IOException, ClassNotFoundException
   {
      int cnt = s.readInt();

      // items may be null when deserialized?
      if(items == null) {
         items = new Vector(cnt);
      }

      if(items.size() != cnt) {
         items.setSize(cnt);
      }

      synchronized(items) {
         ArrayList<Integer> removed = new ArrayList<>();

         for(int i = 0; i < cnt; i++) {
            Object obj = items.get(i);
            BasePaintable bpt = (obj instanceof BasePaintable) ? (BasePaintable) obj : null;

            if(full || obj == null) {
               if(bpt != null && !bpt.isSerializable()) {
                  // read writed class name
                  s.readObject();
                  continue;
               }

               obj = s.readObject();

               if(obj instanceof String) {
                  LOG.warn("Problem loading page content, object is not serializable: " + obj);
                  removed.add(Integer.valueOf(i));
               }
               else {
                  items.set(i, obj);
               }
            }
         }

         // remove null paintables
         for(int i = removed.size() - 1; i >= 0; i--) {
            int idx = removed.get(i).intValue();
            items.remove(idx);
         }
      }

      if(!swap) {
         char ch = s.readChar();

         if(ch == 'I') {
            byte[] buf = (byte[]) s.readObject();

            if(buf != null) {
               int w = s.readInt();
               int h = s.readInt();

               bg = Encoder.decodeImage(w, h, buf);
            }
         }
         else {
            bg = s.readObject();
         }
      }
   }

   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException
   {
      s.defaultReadObject();

      if(compress) {
         InflaterInputStream inp = new InflaterInputStream(s);

         s = new ObjectInputStream(inp);
      }

      try {
         load(s, true, false);
      }
      catch(InvalidClassException ex) {
         String msg = ex.getMessage();

         // do not report as a very critical information for incompatible
         // exception, which should be caused by out-of-date class
         if(msg != null && msg.contains("incompatible")) {
            LOG.debug("Failed to read object in page content", ex);
         }
         else {
            throw ex;
         }
      }
   }

   private void writeObject(ObjectOutputStream stream) throws IOException {
      DeflaterOutputStream out = null;

      stream.defaultWriteObject();
      Deflater deflater = null;

      if(compress) {
         deflater = new Deflater(Deflater.BEST_SPEED);

         out = new DeflaterOutputStream(stream, deflater);
         stream = new ObjectOutputStream(out);
      }

      try {
         save(stream, true, false);
         stream.flush();

         if(out != null) {
            out.flush();
            out.finish();
         }
      }
      finally {
         if(deflater != null) {
            deflater.end();
         }
      }
   }

   /**
    * Make a copy of this page.
    */
   @Override
   public Object clone() {
      try {
         StylePage page = (StylePage) super.clone();

         page.items = (Vector) items.clone();
         page.swapfull = false;
         page.swapped = false;

         return page;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone stlye page", ex);
      }

      return null;
   }

   private static boolean compress = true;
   static {
      compress = "true".equals(SreeEnv.getProperty("replet.optimize.network"));
   }

   /**
    * Set the paintables array which is sorted by zindex.
    * Just for vs converted report to make sure the elements is paintabled
    * with same hierarchy as the source components in the target vs.
    */
   public void setSortedPaintables(Object[] arr) {
      sortedItems = arr;
   }

   private Dimension size;
   private Font font = Util.DEFAULT_FONT;
   private Color color = Color.black;
   private Map properties;
   private int pageNum = 0;

   // transient items are serialized in writeObject and readObject
   private transient Vector items = new Vector();
   private Object[] sortedItems = null; // just for vs converted reportsheet.
    private transient Object bg;
   private transient Vector infos = new Vector();

   // swapping variables
   private transient boolean swapfull = false; // full swap
   private transient boolean swapped = false; // true if have been swapped
   private transient boolean inmemory = true; // object in memory
   private transient Boolean eval = null; // true if eval version

   private byte bglayout;
   private byte orient;
   private Dimension bgsize;
   private boolean batchwait = false;
   private Margin margin; // page margin
   private Map<String, Rectangle> boundsMap = new HashMap<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(StylePage.class);
}
