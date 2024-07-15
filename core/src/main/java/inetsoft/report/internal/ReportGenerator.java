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
import inetsoft.report.PageLayout.InfoText;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Catalog;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;

import java.awt.*;
import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;

/**
 * This class takes a report and produce a enumeration of StylePage objects.
 * The style page is generated on demand to conserve memory. This way
 * the pages don't need to be all held in memory.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ReportGenerator {
   /**
    * Process a report and generate stylepage objects.
    */
   public static Enumeration generate(ReportSheet report) {
      return generate(report, true);
   }

   /**
    * Process a report and generate stylepage objects.
    */
   public static Enumeration generate(ReportSheet report, boolean pagination) {
      return generate(report, pagination, true);
   }

   /**
    * Process a report and generate stylepage objects.
    */
   public static Enumeration generate(ReportSheet report, boolean pagination,
                                      boolean checkBatch) {
      if(pagination) {
         boolean batch = checkBatch ? Util.isBatchWaiting(report) : false;
         Size size = Util.getPageSize(report);
         Dimension dim =
            new Dimension(Math.round(size.width * 72), Math.round(size.height * 72));
         Enumeration pages = new Pages(report, dim);

         if(batch) {
            pages = new PagedEnumeration(pages, true, true);
         }

         return pages;
      }
      else {
         return generateSinglePage(report);
      }
   }

   /**
    * Process a report and generate stylepage objects.
    */
   public static Enumeration generate(ReportSheet report, Dimension pgsize) {
      boolean batch = Util.isBatchWaiting(report);
      Enumeration pages = new Pages(report, pgsize);

      if(batch) {
         pages = new PagedEnumeration(pages, true, true);
      }

      return pages;
   }

   /**
    * Process a report and generate a single style page. The page height is
    * Integer.MAX_VALUE.
    *
    * @param report the report.
    * @return the enumeration stores a single style page.
    */
   private static Enumeration generateSinglePage(ReportSheet report) {
      report.reset();

      Size pgsize = Util.getPageSize(report);
      int height = Math.round(pgsize.height * 72);
      int width = Math.round(pgsize.width * 72);
      int pgorient = report.getOrientation();

      Integer nextO = report.getNextOrientation();

      if(nextO != null && nextO.intValue() != pgorient) {
         int temp = height;

         height = width;
         width = temp;
         pgorient = nextO.intValue();
      }

      removePageBreaks(report, width, height);

      final SingleStylePage pg =
         new SingleStylePage(new Dimension(width, Integer.MAX_VALUE));

      pg.setOrientation(pgorient);
      pg.setMargin(report.getCurrentMargin());

      // fix bug1166519799937, store the original height as pg width when
      // Orientation is LANDSCAPE instead of Integer.MAX_VALUE
      pg.setOrientationSize(new Dimension(width, height));

      try {
         report.printNext(pg);
      }
      finally {
         report.complete();
         ObjectCache.clear();
      }

      int extra = 60;
      addWarnings(report, pg, extra);
      return new SingleEnumeration(pg);
   }

   /**
    * Calculate warnings' bounds.
    */
   private static Rectangle calcBounds(StylePage page,
                                       PageLayout.InfoText shape, int ystart) {
      Font font = shape.getFont();
      FontMetrics fm = Common.getFontMetrics(font);
      int height = (int) Common.getHeight(font);
      int width = (int) Common.stringWidth(shape.getInfo(), font, fm);
      Dimension size = page.getPageDimension();
      int delta = 3;
      int yi = ystart;
      int xStart = size.width - width;

      while(true) {
         Rectangle bounds = new Rectangle(xStart, yi, width, height);

         if(!isOverlapped(bounds, page)) {
            return bounds;
         }

         yi += delta;
      }
   }

   /**
    * Check if current warning is overlapped with others.
    */
   private static boolean isOverlapped(Rectangle bounds, StylePage page) {
      int count = page.getPaintableCount();

      // do not check too many paintables
      for(int i = count - 1; i >= 0 && i >= count - 1000; i--) {
         Paintable pt = page.getPaintable(i);

         // here only check whether current warning is overlapped with other
         // warnings, it doesn't matter if warning is overlapped
         // with not-warning-paintables, because those paintables' position
         // will be adjusted later
         if(isInfoText(pt)) {
            if(bounds.intersects(pt.getBounds())) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if the paintable is for PageLayout.InfoText.
    */
   private static boolean isInfoText(Paintable pt) {
      return pt != null && pt.getElement() instanceof InfoText;
   }

   /**
    * Add warning paintables to current single page.
    * @param report report sheet.
    * @param pg current single page.
    */
   private static void addWarnings(ReportSheet report,
                                   final SingleStylePage pg, int extra) {
      Map warnings = report.getElemInfoMap();
      Set entrySet = new HashSet();
      entrySet.addAll(warnings.entrySet());
      Enumeration elems = ElementIterator.elements(report);

      Object[] entries = entrySet.toArray();
      // sort warnings accroding to their corresponding elem's position
      Arrays.sort(entries, new WarningComparator(pg));
      String warningPos = SreeEnv.getProperty("warning.position.onheader");
      boolean onHeader = "true".equals(warningPos);
      int warningMaxPos = 0;
      // the y coordinate that warnings should start at
      int ystart = getYStart(onHeader, pg);
      int idx = 0;

      while(idx < entries.length) {
         Entry et = (Entry) entries[idx];
         String warning = (String) et.getKey();
         PageLayout.InfoText ishape = new PageLayout.InfoText();
         ishape.setInfo(warning);
         Rectangle bounds = calcBounds(pg, ishape, ystart);
         ishape.setBounds(bounds);

         if(ishape.getBounds() != null) {
            Paintable pt = ishape.getPaintable();
            pg.addPaintable(pt);

            String elemID = report.getElemInfoMap().get(warning);

            if(elemID != null) {
               Map<String, Rectangle> boundsmap = new HashMap<>();
               boundsmap.put(elemID, bounds);
               report.putElemWarnings(warning, boundsmap);
            }
        }

         // the max y coordinate of all current warnings
         warningMaxPos = bounds.y + bounds.height > warningMaxPos ?
            bounds.y + bounds.height : warningMaxPos;
         idx++;
      }

      // add vertical blank between warnings and other paintables
      int warningOffset = 60;
      // after paint warnings, other paintables' position may change
      // we must adjust their positions
      int adjust = onHeader ? warningMaxPos - ystart + warningOffset : 0;
      adjustPageAndPaintables(pg, extra, adjust, false);
   }

   /**
    * Caculate the y coordinate that warnings should start at.
    * @param onheader indicates whether warnings above element or below.
    * @return the y coordinates warnings should start at.
    */
   private static int getYStart(Boolean onheader, StylePage pg) {
      int start = -1;
      int headerStart = 15;
      int warningOffset = 60;

      for(int i = 0; i < pg.getPaintableCount(); i++) {
         Paintable pt = pg.getPaintable(i);
         Rectangle box = pt.getBounds();

         if(onheader) {
            start = start == -1 ? headerStart : Math.min(start, box.y);
         }
         else {
            int curStart = box.y + box.height;
            start = start == -1 ? curStart : Math.max(start, curStart);
         }
      }

      return start + warningOffset;
   }

   /**
    * If warnings are painted above other paintables, we should ajust
    * other paintables' position and page size.
    * @param pg single style page.
    * @param extra extra to reserve in the page.
    * @param adjust size other paintables neet to ajust.
    * @param isAdjustFooter is used to adjust footer.
    */
   private static void adjustPageAndPaintables(StylePage pg, int extra,
      int adjust, boolean isAdjustFooter)
   {
      int maxY = 0;

      // calculate the real height
      for(int i = pg.getPaintableCount() - 1; i >=0; i--) {
         Paintable pt = pg.getPaintable(i);
         Rectangle box = pt.getBounds();

         if(isAdjustFooter && pt.getElement() != null &&
            pt.getElement() instanceof BaseElement && !isInfoText(pt))
         {
            BaseElement elem = (BaseElement) pt.getElement();
            Vector footer = elem.getReport().getAllFooterElements();

            if(footer != null && footer.contains(elem)) {
               adjustLocation(adjust, pt);
               box = pt.getBounds();
            }
         }
         else if(pt instanceof ShapePaintable) {
            PageLayout.Shape shape = ((ShapePaintable) pt).getShape();

            if(!(shape instanceof PageLayout.InfoShape)) {
               adjustLocation(adjust, pt);
               box = pt.getBounds();
            }
         }
         else if(!isInfoText(pt)) {
            adjustLocation(adjust, pt);
            box = pt.getBounds();
         }

         if(box != null) {
            // the element should be invalid, since it located to the most
            // bottom of the page, and will not shown correctly there.
            if(box.y + box.height <= 0) {
               pg.removePaintable(i);
            }
            else {
               maxY = Math.max(maxY, box.y + box.height);
            }
         }
      }

      maxY = maxY == 0 ? (int) (pg.getMargin().top * 72) : maxY;
      Dimension d = pg.getPageDimension();
      pg.setPageDimension(new Dimension(d.width, maxY + extra));
   }

   /**
    * Adjust location of the paintable.
    */
   private static void adjustLocation(int adjust, Paintable pt) {
      Point loc = pt.getLocation();
      loc.y += adjust;

      if(pt instanceof GridPaintable) {
         ((GridPaintable) pt).adjustLoc(new Point(loc.x, loc.y));
      }
      else {
         pt.setLocation(new Point(loc.x, loc.y));
      }
   }

   /**
    * Shrink a page to the height of its contents.
    */
   public static void shrinkPage(StylePage pg, int extra) {
      adjustPageAndPaintables(pg, extra, 0, false);
   }

   /**
    * Remove the page break element for single style page generation.
    *
    * @param report the report.
    */
   private static void removePageBreaks(ReportSheet report, int pgw, int pgh) {
      // remove FillPage and row orientation
      if(report instanceof TabularSheet) {
         TabularSheet treport = (TabularSheet) report;

         for(int i = 0; i < treport.getRowCount(); i++) {
            if(treport.getMinRowHeight(i) < 0) {
               treport.setMinRowHeight(i, pgh);
            }

            Integer o = treport.getRowOrientation(i);

            if(i > 0 ||
               (o != null && o.intValue() == treport.getOrientation())) {
               treport.setRowOrientation(i, null);
            }
         }
      }
   }

   /**
    * A single style page is used as a style page when print report without
    * any pagination, page header or page footer.
    */
   public static class SingleStylePage extends StylePage {
      /**
       * Create a page with the specified size and resolution.
       * @param size page size in pixels.
       */
      public SingleStylePage(Dimension size) {
         super(size);
      }

      /**
       * Set a page Orientation size with the specified size.
       * @param size page size in pixels.
       */
      public void setOrientationSize(Dimension size) {
         this.size = size;
      }

      /**
       * Get a page Orientation size with the specified size.
       * @return the Orientation size.
       */
      public Dimension getOrientationSize() {
         return size;
      }

      private Dimension size;
   }

   static class SingleEnumeration implements Enumeration, Serializable {
      public SingleEnumeration(StylePage page) {
         pg = page;
      }

      @Override
      public boolean hasMoreElements() {
         return index == -1;
      }

      @Override
      public Object nextElement() {
         if(++index > 0) {
            throw new IndexOutOfBoundsException("Index out of bounds!");
         }

         return pg;
      }

      int index = -1;
      StylePage pg;
   }

   /**
    * Comparator for warning informations.
    */
   static class WarningComparator implements Comparator {
      public WarningComparator(StylePage pg) {
         this.pg = pg;
      }

      /**
       * Sort warnings according to the position of the
       * element which the warning belong to.
       */
      @Override
      public int compare(Object o1, Object o2) {
         return getCorresElemYPos(o1) - getCorresElemYPos(o2);
      }

      /**
       * Caculate the y coordinate of the element
       * to which current warning belongs.
       */
      private int getCorresElemYPos(Object obj) {
         Entry et = (Entry) obj;
         String elemID = (String) et.getKey();

         if(pg != null) {
            for(int i = 0; i < pg.getPaintableCount(); i++) {
               Paintable pt = pg.getPaintable(i);
               ReportElement elem = pg.getPaintable(i).getElement();

               if(elem != null && elemID.equals(elem.getID())) {
                  return pt.getBounds().y;
               }
            }
         }

         return 0;
      }

      StylePage pg;
   }

   static class Pages implements Enumeration {
      public Pages(ReportSheet report, Dimension pgsize) {
         this.report = report;
         this.pgsize = pgsize;

         pgorient = report.getOrientation();
         report.reset();
      }

      @Override
      public boolean hasMoreElements() {
         return more;
      }

      @Override
      public Object nextElement() {
         if(!more) {
            return null;
         }

         StylePage pg;
         // generate the pages
         Integer nextO = report.getNextOrientation();

         if(nextO != null && nextO.intValue() != pgorient) {
            pgsize = new Dimension(pgsize.height, pgsize.width);
            pgorient = nextO.intValue();
         }

         try {
            pg = new StylePage(pgsize);
            pg.setOrientation(pgorient);
            count++;
            pg.setPageNum(count);
            // for Feature #26586, add ui processing time record for current report.

            Object result = ProfileUtils.addExecutionBreakDownRecord(report,
               ExecutionBreakDownRecord.UI_PROCESSING_CYCLE, args -> {
                  return report.printNext((StylePage) args[0]);
               }, pg);

            //Object result = report.printNext(pg);
            more = result instanceof Boolean ? ((Boolean) result).booleanValue() : false;
            int maxPages = report.getMaxPages();

            if(more && count == maxPages) {
               String val = report.getProperty("display.warning");

               if(!"false".equals(val)) {
                  String info = Catalog.getCatalog().
                     getString("designer.common.maxPage", maxPages + "");
                  pg.addInfo(info);
               }

               more = false;
            }
         }
         catch(RuntimeException ex) {
            more = false;
            throw ex;
         }
         catch(Exception ex) {
            more = false;
            throw new RuntimeException(ex);
         }
         finally {
            if(!more) {
               report.complete();
               ObjectCache.clear();
            }
         }

         pg.completeInfo();
         processPageWarnings(pg.getBoundsMap(), report);
         return pg;
      }

      /**
       * Get all pages warnings, their bounds and their corresponding element
       * and put them in report's map, warning information key being key.
       * @param map map contains warnings as key and warning's bouds as value in
       *  every page.
       * @param report report sheet.
       */
      private void processPageWarnings(Map<String, Rectangle> map,
                                       ReportSheet report) {
         Set entrySet = map.entrySet();
         Iterator it = entrySet.iterator();

         while(it.hasNext()) {
            Entry entry = (Entry) it.next();
            String warning = (String) entry.getKey();
            addWarningElementID(report, warning, entry);
         }
      }

      /**
       * Check the report sheet have a warning info or not.
       */
      private void addWarningElementID(ReportSheet report, String warning,
                                       Entry entry) {
         String elemID = report.getElemInfoMap().get(warning);
         Map<String, Rectangle> boundsmap = new HashMap<>();
         boundsmap.put(elemID, (Rectangle) entry.getValue());
         report.putElemWarnings(warning, boundsmap);
      }

      boolean more = true;
      ReportSheet report;
      int pgorient = StyleConstants.PORTRAIT;
      Dimension pgsize;
      int count;
   }
}
