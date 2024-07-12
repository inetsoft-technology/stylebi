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
import inetsoft.report.css.CSSApplyer;
import inetsoft.report.filter.*;
import inetsoft.report.internal.binding.*;
import inetsoft.report.internal.info.*;
import inetsoft.report.lens.*;
import inetsoft.uql.*;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.css.CSSStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.security.Principal;
import java.util.*;

/**
 * Section element encapsulate the printing of a section.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class SectionElementDef extends BaseElement
   implements SectionElement, TableGroupable, Tabular, CSSApplyer
{
   static final int OK = 0; // band printed OK
   static final int ADVANCE = 1; // band printed, go to next page
   static final int REPEAT = 2; // band not printed, go to next page
   static final int MORE = 3; // band not completed, continue on next page
   static final int ABORT = 4; // stop printing and ignore remaining contents

   /**
    * Create a table element.
    */
   public SectionElementDef() {
      super();
   }

   /**
    * Create a table element from a table lens.
    */
   public SectionElementDef(ReportSheet report, SectionLens section, TableLens tablelens) {
      super(report, true);

      setAlignment(StyleConstants.H_LEFT | StyleConstants.V_TOP);
      setSection(section);
      setTable(tablelens);

      // default to grow
      setProperty(GROW, "true");
   }

   /**
    * Create a default table element.
    */
   public SectionElementDef(ReportSheet report) {
      this(report, new DefaultSectionLens(new SectionBand(report),
                                          new SectionBand(report),
                                          new SectionBand(report)));

      getSection().getSectionHeader()[0].setRepeatHeader(true);
      getSection().getSectionContent()[0].setHeight(0.6f);
   }

   /**
    * Create a table element from a table lens.
    */
   public SectionElementDef(ReportSheet report, DefaultSectionLens lens) {
      this(report, lens, new DefaultTableLens(1, 1));
      DefaultTableLens table = new DefaultTableLens(2, 15);
      table.setHeaderRowCount(1);
      setTable(table);
   }

   /**
    * Create a proper element info to save the attribute of this element.
   */
   @Override
   protected ElementInfo createElementInfo() {
      return new SectionElementInfo();
   }

   /**
    * Set the report this element is contained in.
    */
   @Override
   public void setReport(ReportSheet report) {
      super.setReport(report);
      setReport(getSection(), report);
   }

   /**
    * Set the new report for the elements.
    */
   private void setReport(SectionLens lens, final ReportSheet report) {
      if(lens == null) {
         return;
      }

      lens.visit(new SectionLens.Visitor() {
         @Override
         public void visit(SectionBand band, Object type) {
            band.setReport(report);
         }
      });
   }

   /**
    * Ignore the remaining print task if any.
    */
   @Override
   public void reset() {
      super.reset();

      // reset contained elements
      if(section != null) {
         section.visit(new SectionLens.Visitor() {
            @Override
            public void visit(SectionBand band, Object type) {
               for(int i = 0; i < band.getElementCount(); i++) {
                  BaseElement elem = (BaseElement) band.getElement(i);
                  elem.reset();
               }
            }
         });
      }
   }

   /**
    * Reset the printing so the any remaining portion of the table
    * is ignored, and the next call to print start fresh.
    */
   @Override
   public void resetPrint() {
      super.resetPrint();

      currentRow = 1;
      lastHeaderRow = -1;
      footerLevel = 0;
      printedOver = false;
      moreband = null;
      moreHeight = 0;
      moreGap = 0;
      lastType = BindingInfo.CONTENT;
      headers = null;
      firsttime = true;
      nextHeader = null;
      curridx = 0;
      currinfo = null;
      currbands = null;
      footeridx = 0;

      bandinfos.removeAllElements();

      // @by larryl, need to clear toptable so the attributes (format...)
      // are applied. Since format change (in designer) does not set
      // table or bindingAttr, the resetFilter() is not called
      toptable = null;
   }

   /**
    * Reset cached tables. The next time getTable() is called, all filtering
    * are re-applied.
    */
   @Override
   public void resetFilter() {
      toptable = null;

   }

   /**
    * Get the filter info holder of this table.
    */
   @Override
   public BindingAttr getBindingAttr() {
      return ((SectionElementInfo) einfo).getBindingAttr();
   }

   /**
    * Apply a css style to a section lens.
    *
    * @param style the specified css style
    */
   @Override
   public void applyCSSStyle(final CSSStyle style) {
      SectionLens lens = getSection();

      if(lens != null) {
         lens.visit(new SectionLens.Visitor() {
            @Override
            public void visit(SectionBand band, Object type) {
               if(style.getBackground() != null) {
                  band.setBackground(style.getBackground());
               }
            }
         });
      }
   }

   /**
    * Return the size that is needed for this element.
    */
   @Override
   public Size getPreferredSize() {
      return new Size(400, 85); // used in dnd only
   }

   /**
    * Return true if the element can be broken into segments.
    */
   @Override
   public boolean isBreakable() {
      return true;
   }

   /**
    * Get section bands.
    *
    * @param section the section
    * @param type the bands type
    * @return the band array
    */
   private SectionBand[] getSectionBands(SectionLens section, String type) {
      if(type.equals(BindingInfo.HEADER)) {
         return section.getSectionHeader();
      }
      else if(type.equals(BindingInfo.FOOTER)) {
         return section.getSectionFooter();
      }
      else {
         return section.getSectionContent();
      }
   }

   /**
    * Check if a table lens derives TableSummaryFilter.
    *
    * @param lens the specified table lens
    * @return true if yes, false otherwise
    */
   private boolean derivesTableSummaryFilter(TableLens lens) {
      do {
         if(lens instanceof TableSummaryFilter) {
            // @by larryl, if only summary is returned, use the row as
            // detail instead of grand total (worksheet)
            return !((TableSummaryFilter) lens).isSummaryOnly();
         }

         if(!(lens instanceof TableFilter)) {
            return false;
         }
         else {
            lens = ((TableFilter) lens).getTable();
         }
      }
      while(true);
   }

   /**
    * Return true if more is to be printed for this section.
    */
   @Override
   public boolean print(StylePage pg, ReportSheet report) {
      super.print0(pg, report);

      if(!checkVisible() || isPrintedOver()) {
         return false;
      }

      SectionLens section = getSection();

      if(section == null) {
         throw new RuntimeException("Section content can not be null");
      }

      // initialize the section band if first time printing
      if(firsttime) {
         initSection(section);
      }

      // prepare data stuff
      TableLens lens = getTable();
      Hashtable colmap = new Hashtable(); // column name -> col idx

      // create colmap, for performance
      if(lens != null && lens.moreRows(0)) {
         int ccnt = lens.getColCount();

         for(int i = 0; i < ccnt; i++) {
            // @by jasons this used to check the upper case, but this is
            // inconsistent with the way the rest of the binding is performed
            // so it was changed.
            Object o = XUtil.getHeader(lens, i);

            if(!colmap.containsKey(o)) {
               colmap.put(o, i);
            }
         }
      }

      PrintInfo rinfo = new PrintInfo();

      if(firsttime) {
         if(lens instanceof AbstractTableLens) {
            Locale local = Catalog.getCatalog().getLocale();
            local = local == null ? Locale.getDefault() : local;

            ((AbstractTableLens) lens).setLocal(local);
         }
      }

      boolean rc = print0(pg, report, lens, rinfo);

      if(rc) {
         if(moreband != null || report.rewinded) {
            printedOver = false;
         }
         else {
            printedOver = !moreToPrint(rinfo.getCurrentRow(),
                                       rinfo.getType(), lens,
                                       rinfo.getCurrentIndex(),
                                       rinfo.getCurrentBands());
         }
      }

      if((!rc || printedOver) && true && lens != null) {
         LOG.debug(
            "Section " + getID() + " finished processing: " +
               lens.getRowCount());
      }

      return rc;
   }

   /**
    * An inner class to store some useful information for evaluate
    * whether printed over.
    */
   private class PrintInfo {
      int getCurrentIndex() {
         return (bands == null) ? curridx : index;
      }

      SectionBand[] getCurrentBands() {
         return (bands == null) ? currbands : bands;
      }

      int getCurrentRow() {
         return (bands == null) ? currentRow : crow;
      }

      String getType() {
         return (bands == null) ? lastType : type;
      }

      SectionBand[] bands = null;
      int index = -1;
      int crow = -1;
      String type = null;
   }

   // get the row number for binding to repeat header bands
   private int getRepeatHeaderRow(TableLens lens) {
      // since we are repeating the header bands, we should use the same row
      // for binding as the last page, otherwise it could be bound to the
      // wrong row (grandtotal) that does not have correct data
      if(lastHeaderRow >= 0) {
         return lastHeaderRow;
      }

      return (lens != null && lens.moreRows(currentRow)) ? currentRow : (currentRow - 1);
   }

   /**
    * Print this section.
    */
   private boolean print0(StylePage pg, ReportSheet report, TableLens lens,
                          PrintInfo rinfo)
   {
      // initialize setting for printing stuff.
      final int optcnt = pg.getPaintableCount();
      final float top = report.printHead.y;
      final boolean atTop = top == 0 &&
         // see comments in printBand()
         (report.npframes == null ||
            report.npframes[0].height <= report.printBox.height);

      // header row used for repeat header binding
      final int headerRow = getRepeatHeaderRow(lens);
      final boolean headerContinue = nextHeader != null;
      // if only repeated header, rewind it to avoid blank page.
      final boolean rewindHeaderIfEmpty = !headerContinue && !firsttime;
      boolean more = false; // more to print on next page

      report.printHead.x = 0;
      bandinfos.removeAllElements();

      // @by larryl, reset moreHeight if moreband is not set. The moreHeight
      // could be leftover from the previous page even when moreband is not
      // set. This ensures any leftover value is not used.
      if(moreband == null) {
         moreHeight = 0;
         moreGap = 0;
      }

      // The repeatLevel combines with noRepeat to control which repeating
      // header bands will be printed in the header loop.
      // The repeatLevel restricts the repeating to be under
      // certain header level.
      // The noRepeat can be used to eliminate individual band in a group
      // header while allowing others to be printed.
      final int repeatLevel = 0;

      // here we need calculate repeat level before keep original printing
      // locations, since the calcRepeatLevel may change curridx value.
      // save original previous printing location and info.
      final SectionBand[] ocurrbands = currbands;
      final BindingInfo ocurrinfo = currinfo;
      final int ocurridx = curridx;

      float headerH = 0; // header band height(only repeating header)

      // print all header bands
      for(int i = (nextHeader == null ? 0 : nextHeader.x); i <= repeatLevel && i < headers.size(); i++) {
         SectionBand[] bands = (SectionBand[]) headers.elementAt(i);

         // print the bands (band index j) at the header level(i)
         for(int j = (nextHeader == null ? 0 : nextHeader.y); j < bands.length; j++) {
            SectionBand band = bands[j];
            nextHeader = null;

            if(band != null && band.isPrintable() &&
               // should not skip the underlay header if never printed
               // @by larryl, the condition to skip underlay band was added in
               // 6.5, which causes header band's repeat header to not apply.
               // My guess is the original reason for the restriction is to
               // deal with page continuation issues for underlay band. Tested
               // with this condition removed and worked fine.
               // (!band.isUnderlay() || firsttime) &&
               (band.isRepeatHeader() || firsttime || headerContinue ||
                  (i == 0 && printToHeader != -1 && j > printToHeader)))
            {
               rinfo.bands = bands;
               rinfo.crow = headerRow;
               rinfo.type = BindingInfo.HEADER;
               rinfo.index = j;

               final float oldY = report.printHead.y;
               final int rc = printBand(pg, report, band, BindingInfo.HEADER,
                                        i, j, false, headerH, lens);

               if(band.isRepeatHeader()) {
                  headerH += report.printHead.y - oldY;
               }

               if(rc == OK || rc == ADVANCE) {
                  band.resetForceBreakable();
               }

               if(rc == ABORT) {
                  addSectionPaintable(pg, report, top, optcnt, false);
                  return false;
               }
               else if(rc != OK) {
                  // @by larryl, if newPageBefore, this header should be
                  // printed on the next page again. This shouldn't be done
                  // for group headers as they will be handled properly
                  // in grouped printing. It's necessary for non-repeating
                  // top-level header otherwise it will be missing
                  if(rc == REPEAT && i == 0) {
                     nextHeader = new Point(0, j);
                  }

                  if(moreband == band) {
                     // header continued, remember where it starts on next page
                     nextHeader = new Point(i, j);

                     // @by larryl, if it continues, turn off repeat otherwise
                     // it could turn into an infinite loop. ignore if no
                     // continuation.

                     // if the section is printed at top(the 1st element of
                     // a new page), it means that the repeated headers have
                     // a higher height than the whole page. Then if we do
                     // not turn off the repeat header setting, it will cause
                     // another continue in next page which eventually will
                     // cause an infinite loop.
                     if(atTop) {
                        band.setRepeatHeader(false);
                     }
                  }

                  // if should move the next row, print the next header
                  // on next page
                  if(rc == ADVANCE) {
                     // if already printing from middle of headers, move to next
                     // header on the next page
                     if(nextHeader != null) {
                        nextHeader.y += 1;
                     }
                     else if(band.isRepeatHeader() && atTop) {
                        // avoid infinite loop, advance to next and don't repeat
                        // see comments above.
                        nextHeader = new Point(i, j + 1);
                        band.setRepeatHeader(false);
                     }
                     else if(i == 0) {
                        printToHeader = j;
                     }
                  }

                  addSectionPaintable(pg, report, top, optcnt, true);
                  return true;
               }
            }
         }
      }

      rinfo.bands = null;
      final int headerBands = bandinfos.size(); //keep header bands count

      // save original previous printing location and info. We have finished
      // printing the repeated header bands together with all pushing down
      // bands, so the variables used in printing the header bands are undone
      // to restore to the mode to continue the regular contents.
      currbands = ocurrbands;
      currinfo = ocurrinfo;
      curridx = ocurridx;

      // @comment, the keepTogether, widow/orphan control are implemented
      // by pushing elements currently printed on this page to the next page.
      // The elements to push down is kept in the keep???? variables and the
      // logic for adding them back is implemented by the previous 'if'
      // statement. The removing is done at the end of this method.

      // keep current number of header paintables
      final int headercnt = pg.getPaintableCount();

      boolean first = true; // the band is be first printed
      // if the band is rewind at last page, it will not
      // be a first printed band (false).

      final boolean tableSummary = derivesTableSummaryFilter(lens);

      // handles both no-binding and plain table binding
      while(lens == null && currentRow <= 1 ||
         lens != null && lens.moreRows(currentRow)) {
         final SectionBand band = getSectionBand(section,
                                                 BindingInfo.CONTENT);

         int rc = OK;

         if(band != null && band.isPrintable()) {
            // is grandtotal row of table summary filter, print it later
            if(tableSummary && lens != null && lens.moreRows(currentRow) &&
               currentRow == lens.getRowCount() - 1)
            {
               break;
            }
            else {
               if(lens != null) {
                     /*
                       @by mikec, this code was found added since 7.0, but
                       can not see any reason for it. The remove bound
                       work should be handled in the bind() method logic,
                       instead of here.
                       This logic here caused bug1166051880992 which
                       lost binding fields for content taller than one page.
                     if(currentRow == lens.getHeaderRowCount()) {
                        band.removeAllValues();
                        }
                     */
                  if(!true) {
                     nextRow();
                     continue;
                  }
               }

               lastType = BindingInfo.CONTENT;

               rc = printBand(pg, report, band, BindingInfo.CONTENT, 0,
                              curridx, first, headerH, lens);
            }

            // go to next page if not enough space
            if(rc == MORE) {
               more = true;
               break;
            }
            else if(rc == ABORT) {
               addSectionPaintable(pg, report, top, optcnt, false);
               return false;
            }

            first = rc != OK;
         }

         if((rc == OK || rc == ADVANCE) && !more) {
            if(band != null) {
               band.resetForceBreakable();
            }

            nextRow();
         }

         if(rc != OK) {
            more = true;
            break;
         }
      }

      if(!more) {
         // unwind footer bands
         while(footerLevel > 0) {
            SectionBand band = getSectionBand(section,
                                              BindingInfo.FOOTER);
            int rc = OK;

            if(band != null && band.isPrintable()) {
               lens.moreRows(Integer.MAX_VALUE);

               // bind data.
               lens.getRowCount();

               lastType = BindingInfo.FOOTER;

               rc = printBand(pg, report, band, BindingInfo.FOOTER,
                              footerLevel, curridx, first, headerH, lens);

               // go to next page if not enough space
               if(rc == MORE) {
                  more = true;
                  break;
               }
               else if(rc == ABORT) {
                  addSectionPaintable(pg, report, top, optcnt, false);
                  return false;
               }
            }

            // printed, pop stack
            if((rc == OK || rc == ADVANCE) && ++curridx >= currbands.length) {
               if(band != null) {
                  band.resetForceBreakable();
               }

               footerLevel--;
               curridx = 0;
               currbands = null;
            }

            if(rc != OK) {
               more = true;
               break;
            }
         }

         // grand total band
         if(!more) {
            SectionBand[] footerbands = section.getSectionFooter();

            // print top level footer bands
            for(; footeridx < footerbands.length && !more; footeridx++) {
               SectionBand footerband = footerbands[footeridx];

               // top level footer band
               if(footerband.isPrintable()) {
                  if(lens != null) {
                     lens.moreRows(Integer.MAX_VALUE);

                     // bind data.
                     lens.getRowCount();
                  }

                  lastType = BindingInfo.FOOTER;

                  int rc = printBand(pg, report, footerband, BindingInfo.FOOTER,
                                     0, footeridx, first, headerH, lens);

                  switch(rc) {
                  case OK:
                     footerband.resetForceBreakable();
                     break;
                  case ADVANCE:
                     footerband.resetForceBreakable();
                     more = true;
                     break;
                  case MORE:
                     footeridx--;
                     more = true;
                     break;
                  case ABORT:
                     addSectionPaintable(pg, report, top, optcnt, false);
                     return false;
                  case REPEAT:
                     footeridx--;
                     more = true;
                     break;
                  }
               }
            }

            if(more) {
               rinfo.crow = lens.getRowCount();
               rinfo.index = footeridx;
               rinfo.bands = footerbands;
               rinfo.type = BindingInfo.FOOTER;
            }
         }
      }

      boolean empty = false;

      if(!more) {
         ObjectCache.clear();

         // if only header and should rewind, remove the paintable
         if(rewindHeaderIfEmpty && bandinfos.size() == headerBands &&
            pg.getPaintableCount() == headercnt)
         {
            for(int idx = pg.getPaintableCount() - 1; idx >= optcnt; idx--) {
               pg.removePaintable(idx);
            }

            empty = true;
         }
      }

      // if header rewinded, restore header position
      if(empty) {
         report.printHead.y = top;
      }
      else {
         addSectionPaintable(pg, report, top, optcnt, more);

         // add line spacing
         report.printHead.y += getSpacing() + 1;
      }

      // @by larryl, if this section starting print from the top, and no element
      // can fit under the headers, don't repeat the headers anymore, otherwise
      // there will be an infinite loop
      if(more && atTop &&
         (headerBands == bandinfos.size() ||
          headerBands == bandinfos.size() - 1 &&
          bandinfos.get(bandinfos.size() - 1).getHeight() == 0))
      {
         no_repeat:
         for(int i = headers.size() - 1; i >= 0; i--) {
            SectionBand[] bands = (SectionBand[]) headers.get(i);

            for(int j = bands.length - 1; j >= 0; j--) {
               if(bands[j].isRepeatHeader()) {
                  LOG.warn("Repeating header is causing an infinite loop, " +
                           "header repeating has been disabled");
                  bands[j].setRepeatHeader(false);
                  break no_repeat;
               }
            }
         }
      }

      return more;
   }

   /**
    * Move to the next row.
    */
   public int nextRow() {
      // if there are still more visible bands to display
      // for the current row, increase the band index
      if(currbands != null) {
         for(int i = curridx + 1; i < currbands.length; i++) {
            if(currbands[i].isPrintable()) {
               curridx = i;

               return currentRow;
            }
         }
      }

      currentRow++;

      currinfo = null;
      currbands = null;
      curridx = 0;

      return currentRow;
   }

   /**
    * Get a section band at specified level and type.
    */
   private SectionBand getSectionBand(SectionLens section, String type) {
      if(currbands == null || curridx >= currbands.length) {
         if(type.equals(BindingInfo.HEADER)) {
            currbands = section.getSectionHeader();
         }
         else if(type.equals(BindingInfo.FOOTER)) {
            currbands = section.getSectionFooter();
         }
         else {
            currbands = section.getSectionContent();
         }

         curridx = 0;
      }

      return curridx < currbands.length ? currbands[curridx] : null;
   }

   /**
    * Check whether or not the new page request is valid.
    */
   public boolean isPrintedOver() {
      return printedOver;
   }

   /*
    * Check if there is any more band in this section would to be printed.
    */
   private boolean moreToPrint(int currRow, String type, TableLens lens,
                               int idx, SectionBand[] bands) {
      if(!isLastVisibleBand(idx, bands)) {
         return true;
      }

      if(type.equals(BindingInfo.HEADER)) {
         SectionLens section1 = getSection();

         if(isVisibleContentBandExists() ||
            isVisibleFooterBandExists(section1)) {
            return true;
         }
      }
      else if(type.equals(BindingInfo.CONTENT)) {
         final boolean tableSummary = derivesTableSummaryFilter(lens);
         boolean more = (lens != null) && (tableSummary ?
                                           lens.moreRows(currRow + 1) :
                                           lens.moreRows(currRow));

         if(isVisibleFooterBandExists(section) || more) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the idx is the last visible band for current band group.
    */
   private boolean isLastVisibleBand(int idx, SectionBand[] bands) {
      if(bands != null) {
         for(int i = idx; i < bands.length; i++) {
            if(bands[i].isPrintable() && bands[i].getHeight() > 0) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Check if there is any content band is visible.
    */
   private boolean isVisibleContentBandExists() {
      final SectionLens section = getSection();
      SectionBand[] bands = (SectionBand[]) section.getSectionContent();
      return !isLastVisibleBand(0, bands);
   }

   /**
    * Check if there is any footer band of this level is visible.
    */
   private boolean isVisibleFooterBandExists(SectionLens section) {
      SectionBand[] bands = getSectionBands(section, BindingInfo.FOOTER);
      return !isLastVisibleBand(0, bands);
   }

   /**
    * Add a section paintable to the page if necessary.
    */
   private void addSectionPaintable(StylePage pg, ReportSheet report,
                                    float top, int optcnt, boolean more) {
      // if it's empty but in design mode, we print 'hidden section'
       if(bandinfos.size() > 0 || false && firsttime && !more) {
         SectionBandInfo[] arr = new SectionBandInfo[bandinfos.size()];
         float height = top + report.printBox.y;
         int[] rows = new int[arr.length];

         for(int i = 0; i < arr.length; i++) {
            arr[i] = bandinfos.get(i);
            rows[i] = arr[i].row;

            // ignore underlay band
            height += arr[i].getHeight();

            // @by larryl, the height is set to MAX for single page view
            // and adding anything to it causes overflow which would make
            // the total negative, casting to double avoids the overflow
            if(height > report.printBox.y + (double) report.printBox.height) {
               arr[i].setHeight(arr[i].getHeight() - height +
                                   (report.printBox.y + report.printBox.height));
            }

            // @by billh, as section paintable object count might be a very
            // huge value, here we use cached section band infos if any to
            // create section paintable object to lower down section band info
            // object count
            arr[i] = (SectionBandInfo) ObjectCache.get(arr[i]);
         }

         int[] brows = new int[arr.length];

         for(int i = 0; i < brows.length; i++) {
            getTable();
            brows[i] = rows[i];
         }

         pg.insertPaintable(optcnt,
            new SectionPaintable(report.printBox.x, top + report.printBox.y,
                                 report.printBox.width, arr, rows, brows, this));

         // advance Y if empty and design mode
         if(bandinfos.size() == 0) {
            report.printHead.y += 20;
         }
      }

      firsttime = false;
   }

   /**
    * Get the section lens.
    */
   @Override
   public SectionLens getSection() {
      return this.section;
   }

   /**
    * Set the section lens.
    */
   @Override
   public void setSection(SectionLens section) {
      this.section = section;
   }

   /**
    * Find an element in the section with the specified ID.
    * @param id element id.
    */
   @Override
   public ReportElement getElement(String id) {
      return Util.getElement(getSection(), id);
   }

   /**
    * Find all elements in the section.
    */
   @Override
   public ReportElement[] getElements() {
      Enumeration elems = ElementIterator.elements(this);
      Vector result = new Vector();

      while(elems.hasMoreElements()) {
         Object elem = elems.nextElement();
         result.add(elem);
      }

      ReportElement[] elements = new ReportElement[result.size()];
      result.copyInto(elements);

      return elements;
   }

   /**
    * Print a section band.
    * @return one of the printing stats code.
    */
   private int printBand(StylePage pg, ReportSheet report, SectionBand band,
                         String type, int level, int bandidx, boolean first,
                         float headerH, TableLens lens) {
      // must be done before return
      currinfo = new BindingInfo(getID(), currentRow, type, level);
      boolean breakBefore = (!first || !band.isRepeatHeader()) &&
         report.printHead.y > headerH &&
         currentRow > (level - (type.equals(BindingInfo.HEADER) ? 1 : 0));

      // check for page break before the band
      // @by larryl, if it follows immediately of all repeating headers,
      // dont advance. Otherwise there is an infinite loop.
      // @by mikec, for header band, the level was added one before call
      // this method, now we reverse it back. Otherwise the inner header
      // band will not apply page before for the first time print.
      // @see bug1164252329609
      if(breakBefore && band.isPageBefore()) {
         return REPEAT;
      }

      final int optcnt = pg.getPaintableCount();

      // print from top of page
      final boolean atTop = report.printHead.y <= headerH &&
         // if in tabular report, and next page is bigger, we don't count as
         // at top since we can try to print a non-break band in whole on
         // next page without risking going into an infinite loop
         (report.npframes == null ||
          report.npframes[0].height <= report.printBox.height);

      float oY = report.printHead.y;

      float bandH = band.getHeight() * 72;
      boolean more = moreband == band;

      // @by larryl, if band is not breakable and is rewound, the band is
      // fully removed and should start at regular positions
      // @by stephenwebster, fix bug1400873156569
      // Implemented a reset in SectionBand to consider whether the band
      // is a continuation, resolves a backwards compatibility issue
      // introduced with fix for bug1380057168718
      if(!more || !isBreakable(band)) {
         band.reset(more);
      }

      // @by larryl, make sure the band height is enought for elements if
      // they are moved in contitnuation
      bandH = getMaxBandHeight(band, bandH);

       boolean designTime = false;

      float startH = 0;

      if(moreband == band) {
         startH = moreHeight;
      }

      report.rewinded = false; // reset rewinded flag
      bandH -= startH;

      float obandH = bandH; // original band height
      float maxH = report.printBox.height - report.printHead.y; // max height

      bandH = Math.min(bandH, maxH);
      // negative causes size/y calculation problem. (62224)
      bandH = Math.max(0, bandH);

      if(designTime) {
         bandH = Math.max(bandH, 5);
      }

      // the difference between the real height and the height limited by page
      float diffH = Math.max(0, obandH - bandH);

      int ocnt = pg.getPaintableCount();
      Rectangle box = new Rectangle(report.printBox.x,
         (int) report.printHead.y + report.printBox.y, report.printBox.width,
         (int) bandH);

      int rc = report.printFixedContainer(pg, band, box, more, startH, headerH, obandH);

      // @by larryl, if a non-breakable band is not completely printed, but
      // there are enough space on the current page, it means there are
      // contents that are cut off, and the element is not auto-size. In
      // this case we should just let the band stay as is.
      if(rc != StyleCore.COMPLETED && maxH >= obandH && !band.isBreakable() &&
         isFixedSize(band))
      {
         rc = StyleCore.COMPLETED;
      }
      // @by mikec, otherwise we should set the non-breakable band to breakable
      // to avoid any content be lost which is not what customer expected.
      else if(!band.isBreakable() && atTop && !isFixedSize(band)) {
         band.forceBreakable();
      }

      // if the current band continues to next page, and there are elements
      // that are in the middle of the flow, we will shrink the band below.
      // if it's MORE_ELEM, we don't shrink the band so the empty space
      // between elements are preserved
      boolean morecontinue = rc == StyleCore.MORE_FLOW;

      // calculate the bottom of all paintable and adjust hyperlinks.
      int bottom = box.y;
      int ptcnt = pg.getPaintableCount();
      // GridPaintable, shrink grid paintable in subreport later
      Vector grids = new Vector();
      // paintables generated from the direct children of this section
      Vector directpts = new Vector();
      Vector ignored = new Vector(); // ignored element for shrink to fit

      for(int i = ocnt; i < ptcnt; i++) {
         Paintable pt = pg.getPaintable(i);

         if(pt instanceof GridPaintable) {
            grids.add(pt);
            continue;
         }
         else if(pt instanceof ShapePaintable) {
            continue;
         }

         BaseElement elem = (BaseElement) pt.getElement();
         // only adjust hyperlink for elements in this report, not subreport
         // @by mikec, the original logic here seems not correct, the correct
         // logic here seems should be avoid adjust hyperlink for elements
         // in a subreport directly inside a section. For example, if we
         // have a section with a sub report inside and in the subreport we
         // have some text element, here we have no responsibility to adjust
         // the hyperlink. But if we have a bean with a section in it, which
         // contains heading element, when print that section, the hyperlink
         // seems should be adjusted.
         boolean notsub = elem != null && elem.parent == band;

         // set binding info
         if(notsub) {
            // remember for later use
            directpts.add(pt);
            ((BasePaintable) pt).setUserObject(currinfo);
         }

         Rectangle pb = pt.getBounds();

         // invisible element (height == 0) should be ignored
         if(pb.height > 0) {
            bottom = Math.max(bottom, pb.y + pb.height);
         }
      }

      // @by larryl, if a band is not breakable and it's not at the top of the
      // page, force the band to be rewound. If the band is a continuation of
      // a band, ignore and let it continue
      // @by mikec, if the band is already invisible by script, don't rewound it,
      // otherwise will cause infinite loop.
      if(!atTop && moreband != band && !isBreakable(band) &&
         (rc != StyleCore.COMPLETED ||
          (bottom - box.y > maxH && band.isPrintable())))
      {
         // @by larryl, since this band will be rewound, we need to add a
         // band info otherwise the rewind() will remove the previous band
         SectionBandInfo info = new SectionBandInfo(band, type, level, bandidx, bandH);
         info.row = currentRow;
         bandinfos.addElement(info);
         // @by larryl, set the moreinfo so bind() knows it's a rebind
         // on the next page
         return checkRewind(report, pg, band, oY, headerH, optcnt, atTop, rc);
      }

      // continue the current band if more
      if(rc != StyleCore.COMPLETED) {
         moreband = band;
      }
      // @by larryl, if no more to print and the band's height is fully
      // consumed, reset the continuation setting
      else if(moreband == band && diffH == 0) {
         moreband = null;
         // @by larryl, don't clear the moreHeight here since the band could
         // still be marked as moreband later. The moreHeight would not
         // cause any problem if moreband is null as it's never used if
         // moreband is null
         // moreHeight = 0;
         // @by larryl, don't clear moreinfo here since it's used in
         // bind() to determine if a band has already be bound
      }

      if(!band.isPrintable()) {
         while(pg.getPaintableCount() > ocnt) {
            pg.removePaintable(pg.getPaintableCount() - 1);
         }

         band.setVisible(true);

         if(band == moreband) {
            moreband = null;
            moreHeight = 0;
            moreGap = 0;
            // @by larryl, don't clear moreinfo here since it's used in
            // bind() to determine if a band has already be bound
         }

         return OK;
      }

      boolean shrunk = false; // if shrink to fit is done
      // @by larryl, since the gap should return the gap that is not consumed
      // by the current printing, we need to add the current printBand consumed
      // height (bottom - box.y) to the startH to be consistent.
      // @by mikec, the logic adding consumed here seems not correct.
      // when we calculate bottom gap, our logic will always check the gap
      // between the last element and section bottom border, there seem
      // no reason count in the consumed height(in some case when the element
      // will expand the consumed height will not be correct.
      // actually we should always use the gap of design time.
      // @see bug1182347359456
      float bgap = getBottomGap(band, moreGap);

      // shrink the band if allowed, or contains subreport
      if(morecontinue && !designTime && rc == StyleCore.COMPLETED) {
         // if more to print, the bandH can't be shrunk to 0 or we will have
         // infinite loop (e.g. all new lines on the current page).
         // If shrinkToFit, allow 0 height otherwise empty band will show
         if(morecontinue && bottom > box.y || !morecontinue && bottom >= box.y){
            bandH = bottom - box.y;

            if(moreband != band) {
               bandH += bgap;
            }
         }
      }
      else {
         float nbandH = Math.max(bandH, bottom + bgap - box.y);
         // @by larryl, the maxH is the maximum on the current page and should
         // be the limit on the band height
         bandH = Math.min(nbandH, maxH);
         moreGap = Math.max(0, bgap - (nbandH - bandH));
      }

      // if the band is shrunk, remove the ignored empty elements
      if(shrunk) {
         for(int i = ignored.size() - 1; i >= 0; i--) {
            pg.removePaintable(((Integer) ignored.get(i)).intValue());
         }
      }

      // @by larryl, if shrink band, also shrink grid inside the band
      for(int i = 0; i < grids.size(); i++) {
         GridPaintable grid = (GridPaintable) grids.get(i);

         if(grid.getRowCount() > 0) {
            Rectangle gbox = grid.getBounds();
            float maxBottom = box.y + bandH;

            // @by larryl, if there are multiple subreports in a band, the
            // top subreport can not overlap the one below it
            for(int j = i + 1; j < grids.size(); j++) {
               Rectangle nextbox = ((GridPaintable) grids.get(j)).getBounds();

               // if a subreport is contained in a subreport, ignore overlapping
               if(nextbox.y > gbox.y &&
                  nextbox.y + nextbox.height >= gbox.y + gbox.height)
               {
                  maxBottom = nextbox.y - gbox.y;
                  break;
               }
            }

            // @by larryl, this is similar to above. If there are elements
            // below the subreport, we make sure the subreport grid doesn't
            // overlap the element. Both of this would not be necessary if
            // the grid is already properly shrunk in the printFixed...
            for(int j = 0; j < directpts.size(); j++) {
               Paintable pt = (Paintable) directpts.get(j);
               Rectangle pbox = pt.getBounds();

               if(pbox.intersects(gbox) && pbox.y > gbox.y) {
                  maxBottom = Math.min(maxBottom, pbox.y);
               }
            }

            if(gbox.y + gbox.height > maxBottom) {
               int lastRow = grid.getTopRow() + grid.getRowCount() - 1;
               int newh = (int) Math.max(0, grid.getRowHeight(lastRow) -
                                         (gbox.y + gbox.height - maxBottom));
               grid.setRowHeight(lastRow, newh);
            }
         }
      }

      // record band bandinfos
      SectionBandInfo info = new SectionBandInfo(band, type, level, bandidx, bandH);
      int status = OK;
      info.setOffset(startH); // record the offset to the top of the printed band

      report.printHead.x = 0;
      report.printHead.y += bandH;

      // check for existing object
      info.row = currentRow;
      bandinfos.addElement(info);

      // subreport not finished, continue
      if(moreband == band) {
         return checkRewind(report, pg, band, oY, headerH, optcnt, atTop, rc);
      }

      // if printed out of bounds, check for rewind
      // @by billh, if printed out of bounds but already completed,
      // the remainder might be bottom gap. To discard the bottom gap
      // seems more meaningful than to print it in next page, but
      // it's just a personal opinion, we may change the logic
      // according to end users' feedback or use a property instead.
      /*
      if((rc != StyleCore.COMPLETED &&
          report.printHead.y > report.printBox.height - diffH) ||
         (rc == StyleCore.COMPLETED &&
          report.printHead.y - gap > report.printBox.height - diffH))
      */
      // @by larryl, restored the logic to keep the gap regardless of whether
      // the printing is completed. This seems to produce more uniform output.
      // By ignoring the gap, the report may contain same band with very
      // different size, which looks a little weired. Also, since users have
      // control of the gap by setting the shrink-to-fit, it would be better
      // to give users the explicit control instead of force one behavior.
      // @by larryl, add 1 to be consistent with rewind() where a rounding
      // error of 1 is allowed when deciding whether to rewind an element
      if(report.printHead.y > report.printBox.height - diffH + 1) {
         report.printHead.y += diffH;
         moreband = band;
         return checkRewind(report, pg, band, oY, headerH, optcnt, atTop, rc);
      }

      boolean breakAfter = !report.isSinglePageForTopReport() &&
         (level > 0 || !BindingInfo.HEADER.equals(type) || !band.isRepeatHeader());

      // page after is never applied to the first level header.
      // this should be checked after the bounds above so if a band is not
      // finished, don't apply the pageAfter yet
      if(breakAfter && band.isPageAfter() && !report.isExportSinglePage()) {
         return ADVANCE;
      }

      // finished printing, make sure we clear the moreHeight here since we
      // don't clear it when moreband is cleared for the purpose of rewinding.
      // now we know the band is not going to be rewound so we can safely clear
      if(moreband == null) {
         moreHeight = 0;
         moreGap = 0;
      }

      // @by larryl, if there is no more than 5 points left, don't attempt to
      // print the next band. If not, this may create the undesirable effect
      // of a slim slice of the next band being printed at the bottom of a
      // page (e.g. background)
      if(status == OK && report.printHead.y >= report.printBox.height - 5) {
         int r = currentRow + (currbands != null &&
                               curridx + 1 >= currbands.length ? 1 : 0);
         int idx = curridx + 1;

         if(moreToPrint(r, type, lens, idx, currbands)) {
            return ADVANCE;
         }
      }

      return status;
   }

   /**
    * Check if a band is breakable.
    */
   private static boolean isBreakable(SectionBand band) {
      // if a band underlays the following bands, it should not be breakable.
      // otherwise if the band is printed across page, the first part is not
      // going to be used by the underlaid bands
      return (band.isBreakable() || band.isForceBreakable());
   }

   /**
    * Check if a section band is fixed size and would not expand dynamically.
    */
   static final boolean isFixedSize(SectionBand band) {
      for(int i = 0; i < band.getElementCount(); i++) {
         ReportElement elem = band.getElement(i);
         String prop = elem.getProperty(ReportElement.GROW);
         boolean cangrow = (prop != null && prop.equalsIgnoreCase("true"));
         boolean expandable = cangrow;

         if(expandable) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check and perform rewinding if necessary.
    * @rc the return code from printFixedContainer.
    * @return MORE if need to continue to next page, or ABORT if the
    * section should be ignored, or ADVANCE if printing should move to
    * next row on next page.
    */
   private int checkRewind(ReportSheet report, StylePage pg, SectionBand band,
                           float oY, float headerH, int ptcnt, boolean atTop,
                           int rc)
   {
      boolean continuation = rc == StyleCore.MORE_FLOW;
      double bH = report.printHead.y - oY;

      if(oY > Math.ceil(headerH) || !atTop || isBreakable(band)) {
         report.printHead.y = oY;
         rewind(pg, report, band, ptcnt, continuation);

         if(!isBreakable(band) && report.skip(Math.ceil(headerH), bH)) {
            ObjectCache.clear();
            return ABORT;
         }

         return MORE;
      }

      // @by larryl, here we need to clear the moreband since we are not
      // going to continue the same band on the next page
      moreband = null;
      // we also clear the moreHeight since we are not continuing on this band,
      // and this band will not be rewound
      moreHeight = 0;
      moreGap = 0;

      return ADVANCE;
   }

   /**
    * Get the maximum band height.
    */
   private float getMaxBandHeight(SectionBand band, float bandH) {
      // make sure the band is tall enough for all elements, the elements
      // may be moved when an element grow and the elements below it
      // pushed down
      // only check if a continued band
      if(band == moreband) {
         for(int i = 0; i < band.getElementCount(); i++) {
            final Rectangle bounds = band.getPrintBounds(i);

            bandH = Math.max(bandH, bounds.y + bounds.height);
         }
      }

      return bandH;
   }

   /**
    * Get the gap between the bottom of all elements and the bottom of band.
    */
   private int getBottomGap(SectionBand band, float consumedGap) {
      // if band is empty, treat gap as 0
      if(band.getElementCount() == 0) {
         return 0;
      }

      int bottom = 0;

      for(int i = 0; i < band.getElementCount(); i++) {
         Rectangle box = band.getBounds(i);

         bottom = Math.max(box.y + box.height, bottom);
      }

      // @by billh, band height and paintable bounds are static
      // when printing a section, so startH should be ignored
      // bottom = (int) Math.max(bottom, startH);

      int bandH = (int) (band.getHeight() * 72);

      return (int) Math.max(0, bandH - bottom - consumedGap);
   }

   /**
    * Restore the style page to the specified number of paintables.
    */
   private void rewind(StylePage pg, ReportSheet report, SectionBand band,
                       int count, boolean continuation)
   {
      float lastY = report.printBox.y + report.printBox.height;
      float bottom = report.printHead.y;
      float rewindTop = report.printBox.height;
      int ocnt = pg.getPaintableCount();
      boolean breakable = isBreakable(band);
      Vector shapes = new Vector();

      // if breakable and the first section is empty, use the full page
      // so an empty section would not disappear
      if(!breakable || pg.getPaintableCount() != count) {
         for(int idx = pg.getPaintableCount() - 1; idx >= count; idx--) {
            Paintable pt = pg.getPaintable(idx);
            BaseElement elem = (BaseElement) pt.getElement();

            // shape paintable not rewound unless whole band is rewound
            if(breakable && pt instanceof ShapePaintable) {
               shapes.add(pt);
               continue;
            }

            // if breakable, only rewind the ones outside of printable area
            if(breakable) {
               Rectangle box = pt instanceof TablePaintable ?
                  ((TablePaintable) pt).getBounds2() : pt.getBounds();

               // @by larryl, add adjustment to allow rounding error
               if(box.y + box.height <= lastY + 1) {
                  // use the bottom of the kept element as bottom
                  bottom = Math.max(bottom,
                     box.y + box.height - report.printBox.y);
                  continue;
               }
               else if(elem != null) {
                  // use the top of the removed element as bottom
                  rewindTop = Math.min(rewindTop, box.y - report.printBox.y);
               }
            }

            // if band is rewound, make sure the rewound elements don't
            // disappear. This can happen if the band is continued and
            // the moreHeight is used to check if an element is within
            // the print bounds
            if(moreband == band && moreHeight > 0 && elem != null) {
               int edx = band.getElementIndex(elem.getID());

               if(edx >= 0) {
                  Rectangle bounds = band.getPrintBounds(edx);

                  if(bounds.height + bounds.y <= moreHeight) {
                     bounds.y = (int) Math.ceil(moreHeight);
                     band.setPrintBounds(edx, bounds);
                  }
               }
            }

            // if removing a text, add it back so it's not lost
            if(elem != null) {
               elem.rewind(pt);
            }

            pg.removePaintable(idx);
            report.rewinded = true; // set rewinded flag
         }
      }

      // if not breakable, always fully rewinded
      if(breakable) {
         float h = (float) Math.ceil(bottom - report.printHead.y);
         SectionBandInfo info = bandinfos.elementAt(bandinfos.size() - 1);

         // if not more_flow, we want to use the band height
         // calculated. otherwise empty space between elements are not
         // filled to end of page
         // @by larryl, if there are paintables removed above, the new height
         // should be used even if h is 0. Otherwise the moreHeight will be
         // set to band height, and the rewound elements will be skipped on
         // the next page
         if((h > 0 || pg.getPaintableCount() == count && ocnt > count) &&
            (continuation || report.rewinded))
         {
            info.setHeight(h);
         }
         else {
            h = info.getHeight();
         }

         // if the removed elements is above (overlap vertically) the kept
         // elements, the moreHeight will be > top of removed element. when
         // we continue on next page, the position of the removed element
         // needs to be moved down by the difference to account for the
         // discrepency
         if(rewindTop < bottom) {
            // @by larryl, remove (rewind) any elements that are below the top
            // of the rewound elements. Since the elements will be pushed down
            // to the next page (in the following loop) to be printed together,
            // they should not be kept on the current page
            for(int idx = pg.getPaintableCount() - 1; idx >= count; idx--) {
               Paintable pt = pg.getPaintable(idx);
               BaseElement elem = (BaseElement) pt.getElement();

               if(pt instanceof ShapePaintable) {
                  continue;
               }

               Rectangle box = pt.getBounds();

               if(box.y >= rewindTop + report.printBox.y) {
                  // if removing a text, add it back so it's not lost
                  if(elem != null) {
                     elem.rewind(pt);
                  }

                  pg.removePaintable(idx);
               }
            }

            // @by billh, ignore mistake less than 1. When transform
            // float to int, mistake less than 1 is always ignored,
            // and the precision loss problem exists in print widely
            int diff = (int) Math.floor(bottom - rewindTop);
            float top = rewindTop - report.printHead.y;

            for(int i = 0; i < band.getElementCount(); i++) {
               Rectangle bounds = band.getPrintBounds(i);

               if(bounds.y >= top) {
                  bounds.y += diff;
                  band.setPrintBounds(i, bounds);
               }
            }
         }

         // make sure all shapes (which is not rewound) are properly clipped
         for(int i = 0; i < shapes.size(); i++) {
            ShapePaintable pt = (ShapePaintable) shapes.get(i);
            Rectangle vclip = pt.getVirtualClip();

            if(vclip != null) {
               vclip.height = Math.min(vclip.height, (int) h);
               pt.setVirtualClip(vclip);
            }
         }

         report.printHead.y += h;
         moreHeight += h;

         bandinfos.setElementAt(info, bandinfos.size() - 1);
      }
      // not breakable
      else {
         // remove last bandinfo
         if(bandinfos.size() > 0) {
            bandinfos.removeElementAt(bandinfos.size() - 1);
         }

         // @by larryl, if section is fully rewound, don't continue.
         // print from scratch next time
         moreHeight = 0;
         moreGap = 0;
         // we can't clear out the moreband here otherwise the scripts in
         // the band will be evaluated again (already execed in printFix...),
         // which may cause problem if they have accummulative effects
         moreband = band;
         // @by larryl, don't clear moreinfo here since it's used in
         // bind() to determine if a band has already be bound
      }
   }

   /**
    * Initialize a section. Handle the presenter setting.
    */
   private void initSection(SectionLens section) {
      if(headers == null) {
         headers = new Vector();
      }

      if(section != null) {
         headers.add(section.getSectionHeader());
      }
   }

   /**
    * Get the tablelens (original) bound to this element.
    */
   public TableLens getData() {
      return table;
   }

   /**
    * Set the data in the tabular element.
    */
   @Override
   public void setData(TableLens model) {
      setTable(model);
   }

   /**
    * Set the table lens.
    */
   @Override
   public void setTable(TableLens table) {
      this.table = table;
      resetFilter();
   }

   /**
    * Get the base table lens.
    */
   @Override
   public TableLens getBaseTable() {
      return this.table;
   }

   /**
    * Get the table to be used for printing.
    */
   @Override
   public TableLens getTopTable() {
      return toptable;
   }

   /**
    * Get the table lens in this element. The tables may be nested:
    * [filter]
    * [table]
    */
   @Override
   public TableLens getTable() {
      // @by mikec, if the base table is null, it will run into infinite
      // loop in applying the filters, just return here.
      if(table == null) {
         return table;
      }

      // if toptable set explicitly, use it directly
      if(toptable != null) {
         return toptable;
      }

      return toptable = table;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return getID();
   }

   /**
    * Get element type.
    */
   @Override
   public String getType() {
      return "Section";
   }

   /**
    * Set the user variable
    */
   public void setVariable(VariableTable vars) {
      this.vars = vars;
   }

   /**
    * Get the user variable
    */
   public VariableTable getVariable() {
      return (vars == null) ? new VariableTable() : vars;
   }

   /**
    * Set principal of the grouped thread.
    */
   public void setPrincipal(Principal user) {
      this.user = user;
   }

   /**
    * Get principal of the grouped thread.
    */
   public Principal getPrincipal() {
      return user;
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      SectionElementDef elem = (SectionElementDef) super.clone();

      elem.bandinfos = new Vector();
      elem.section = (SectionLens) section.clone();

      return elem;
   }

   // table lens overrides all filters
   private transient TableLens toptable;

   // table lens
   private TableLens table;
   private SectionLens section;

   private transient SectionBand moreband = null; // if subreport not finished
   private transient float moreHeight = 0; // consumed height in continuation
   private transient float moreGap = 0; // consumed bottom gap in continuation

   private transient SectionBand[] currbands = null; // current bands for print
   private transient BindingInfo currinfo = null; // current section info
   private transient int curridx = 0; // current band in the band array
   private transient int footeridx = 0; // grand total footer band index

   private transient int currentRow = 1; // current row in the table

   private transient int footerLevel = 0; // last printed footer level
   private transient int lastHeaderRow = -1; // last header row
   private transient String lastType = BindingInfo.CONTENT;
   private transient Vector headers = null; // header bands, SectionBand[]
   private transient boolean firsttime = true; // first print()
   private transient Point nextHeader = null; // for header continuation
                                              // [group level, band No.]
   private transient int printToHeader = -1;

   // paintables from last page
   // section paintable info, design time only
   private transient Vector<SectionBandInfo> bandinfos = new Vector<>();

   private transient boolean printedOver = false;

   private transient Principal user = null;
   private transient VariableTable vars = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(SectionElementDef.class);
}
