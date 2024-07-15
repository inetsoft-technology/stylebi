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
package inetsoft.report.lens;

import inetsoft.report.*;
import inetsoft.report.internal.binding.Field;
import inetsoft.report.internal.binding.GroupField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * DefaultSectionLens is a default implementation of the SectionLens
 * interface. It can be used to create a section lens for adding a
 * section to a report. Since sections are normally created using
 * the report designer, this classes is only used if a section needs
 * to be created dynamically through API.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class DefaultSectionLens implements SectionLens, GroupableLayout {
   /**
    * Create an empty section.
    */
   public DefaultSectionLens() {
      this.header = new SectionBand[] {};
      this.content = new SectionBand[] {};
      this.footer = new SectionBand[] {};
   }

   /**
    * Create an empty section.
    */
   public DefaultSectionLens(ReportSheet report) {
      this(new SectionBand(report), new SectionBand(report),
         new SectionBand(report));

      header[0].setRepeatHeader(true);
   }

   /**
    * Create a plain section.
    */
   public DefaultSectionLens(SectionBand header, SectionBand content, SectionBand footer) {
      this.header = new SectionBand[] {header};
      this.content = new SectionBand[] {content};
      this.footer = new SectionBand[] {footer};
   }

   /**
    * Get the section header frame. If the section does not have a
    * header frame, return null.
    */
   @Override
   public SectionBand[] getSectionHeader() {
      return header;
   }

   /**
    * Get the content of the section content. The content could be an
    * array of SectionBand or a SectionLens. If a SectionLens is returned,
    * the returned section is nested in this section.
    */
   @Override
   public SectionBand[] getSectionContent() {
      return content;
   }

   /**
    * Get the section footer frame. If the section does not have a
    * footer frame, return null.
    */
   @Override
   public SectionBand[] getSectionFooter() {
      return footer;
   }

   /**
    * Visit all bands in the section.
    */
   @Override
   public void visit(Visitor visitor) {
      if(getSectionHeader() != null) {
         visit(getSectionHeader(), visitor, HEADER);
      }

      if(getSectionFooter() != null) {
         visit(getSectionFooter(), visitor, FOOTER);
      }

      SectionBand[] val = getSectionContent();
      visit(val, visitor, CONTENT);
   }

   /**
    * Set reports to a band or an array or bands.
    */
   private void visit(SectionBand[] bands, Visitor visitor, Object type) {
      for(int i = 0; i < bands.length; i++) {
         visitor.visit(bands[i], type);
      }
   }

   @Override
   public Object clone() {
      try {
         DefaultSectionLens section = (DefaultSectionLens) super.clone();

         if(header != null) {
            section.header = cloneBands(header);
         }

         if(footer != null) {
            section.footer = cloneBands(footer);
         }

         section.content = cloneBands(content);

         return section;
      }
      catch(CloneNotSupportedException ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Clone a section band.
    */
   private SectionBand[] cloneBands(SectionBand[] bands) {
      SectionBand[] arr = new SectionBand[bands.length];

      for(int i = 0; i < arr.length; i++) {
         arr[i] = (SectionBand) bands[i].clone();
      }

      return arr;
   }

   public String toString() {
      StringBuilder sbuf = new StringBuilder();

      for(int i = 0; i < header.length; i++) {
         sbuf.append(header[i].toString());
      }

      for(int i = 0; i < ((SectionBand[]) content).length; i++) {
         sbuf.append(((SectionBand[]) content)[i].toString());
      }

      for(int i = 0; i < footer.length; i++) {
         sbuf.append(footer[i].toString());
      }

      return sbuf.toString();
   }

   /**
    * Get all bands infomations.
    */
   @Override
   public List<GroupableBandInfo> getBandInfos() {
      List<GroupableBandInfo> binfos = new ArrayList<>();
      DefaultSectionLens section = this;

      // header, footer
      BandInfo bandInfo = new BandInfo(section.header,
         TableDataPath.HEADER, -1);

      if(bandInfo.isVisible()) {
         binfos.add(bandInfo);
      }

      bandInfo = new BandInfo(section.footer, TableDataPath.GRAND_TOTAL, -1);

      if(bandInfo.isVisible()) {
         binfos.add(bandInfo);
      }

      // detail
      SectionBand[] details = section.content;

      bandInfo = new BandInfo(details, TableDataPath.DETAIL, -1);

      if(bandInfo.isVisible()) {
         binfos.add(bandInfo);
      }

      // keep order from headers -> footers
      Collections.sort((List) binfos);
      return binfos;
   }

   /**
    * Get all cell infomations for the layout.
    */
   @Override
   public List<CellBindingInfo> getCellInfos(boolean all) {
      List<CellBindingInfo> cinfos = new ArrayList<>();
      List<GroupableBandInfo> binfos = getBandInfos();

      for(int i = 0; i < binfos.size(); i++) {
         cinfos.addAll(binfos.get(i).getCellInfos(all));
      }

      return cinfos;
   }

   /**
    * Get a cell infomation.
    */
   public CellBindingInfo getCellInfo(SectionBand band, String eid) {
      List<GroupableBandInfo> binfos = getBandInfos();

      for(int i = 0; i < binfos.size(); i++) {
         BandInfo binfo = (BandInfo) binfos.get(i);

         for(int j = 0; j < binfo.bands.length; j++) {
            if(band == binfo.bands[j]) {
               return new SectionCellBindingInfo(binfo.bands[j], j, eid,
                                                 binfo.type, binfo.level);
            }
         }
      }

      return null;
   }

   /**
    * Band info.
    */
   private static class BandInfo implements GroupableBandInfo, Comparable {
      public BandInfo(SectionBand[] bands, int type, int level) {
         this.bands = bands;
         this.type = type;
         this.level = level;
      }

      /**
       * Check if the band is visible.
       */
      @Override
      public boolean isVisible() {
         for(int i = 0; i < bands.length; i++) {
            if(bands[i].isVisible() || bands[i].getElementCount() > 0) {
               return true;
            }
         }

         return false;
      }

      /**
       * Get band type.
       */
      @Override
      public int getType() {
         return type;
      }

      /**
       * Get band level.
       */
      @Override
      public int getLevel() {
         return level;
      }

      /**
       * Get cell binding infos.
       */
      @Override
      public List<CellBindingInfo> getCellInfos(boolean all) {
         List<CellBindingInfo> cinfos = new ArrayList();

         for(int i = 0; i < bands.length; i++) {
            for(int j = 0; j < bands[i].getElementCount(); j++) {
               cinfos.add(new SectionCellBindingInfo(bands[i], i,
                                                     j, type, level));
            }
         }

         Collections.sort(cinfos, new Comparator<CellBindingInfo>() {
            @Override
            public int compare(CellBindingInfo info1,
                               CellBindingInfo info2) {
               SectionCellBindingInfo s1 = (SectionCellBindingInfo) info1;
               SectionCellBindingInfo s2 = (SectionCellBindingInfo) info2;
               return s1.bidx != s2.bidx ? s1.bidx - s2.bidx :
                  s1.xposi - s2.xposi;
            }
         });

         return cinfos;
      }

      /**
       * Get band.
       */
      @Override
      public Object getBand() {
         return bands;
      }

      /**
       * Compare to another object.
       */
      @Override
      public int compareTo(Object obj) {
         if(obj instanceof BandInfo) {
            return getIndexValue() - ((BandInfo) obj).getIndexValue();
         }

         return 0;
      }

      private int getIndexValue() {
         switch(type) {
         case TableDataPath.HEADER :
            return 1 + level; // header
         case TableDataPath.GROUP_HEADER :
            return 1000 + level; // group header
         case TableDataPath.DETAIL :
            return 2000 + level; // detail
         case TableDataPath.SUMMARY :
            return 3000 - level; // summary
         case TableDataPath.TRAILER :
            return 5000 + level; // footer
         }

         return 0;
      }

      private SectionBand[] bands;
      private int type = -1;
      private int level = -1;
   }

   /**
    * Section binding info.
    */
   public static class SectionCellBindingInfo implements CellBindingInfo {
      public SectionCellBindingInfo(SectionBand band, int bidx, int eidx,
                                    int btype, int blevel)
      {
         this(band, bidx, band.getElement(eidx), btype, blevel);
      }

      public SectionCellBindingInfo(SectionBand band, int bidx, String eidx,
                                    int btype, int blevel)
      {
         this(band, bidx, band.getElement(eidx), btype, blevel);
      }

      public SectionCellBindingInfo(SectionBand band, int bidx,
                                    ReportElement elem, int btype, int blevel) {
         this.band = band;
         this.bidx = bidx;
         this.elem = elem;
         this.btype = btype;
         this.blevel = blevel;
         this.xposi = band.getBounds(band.getElementIndex(elem)).x;
      }

      /**
       * Check if the cell binding is virtual.
       */
      @Override
      public boolean isVirtual() {
         return false;
      }

      /**
       * Get the value for the cell binding.
       */
      @Override
      public String getValue() {
         return null;
      }

      /**
       * Set the value for the cell binding.
       */
      @Override
      public void setValue(String value) {
      }

      /**
       * Get the binding type for the cell.
       */
      @Override
      public int getType() {
         return -1;
      }

      /**
       * Get the group type for the cell, group, summary or detail.
       */
      @Override
      public int getBType() {
         return -1;
      }

      /**
       * Set the group type for the cell, group, summary or detail.
       */
      @Override
      public void setBType(int btype) {
      }

      /**
       * Get the cell binding field.
       */
      @Override
      public Field getField() {
         return null;
      }

      /**
       * Set the cell binding field.
       */
      @Override
      public void setField(Field cfield) {
      }

      /**
       * Get expansion.
       */
      @Override
      public int getExpansion() {
         return btype == TableDataPath.HEADER ||
            btype == TableDataPath.GRAND_TOTAL ?
            GroupableCellBinding.EXPAND_NONE : GroupableCellBinding.EXPAND_V;
      }

      /**
       * Set expansion.
       */
      @Override
      public void setExpansion(int expansion) {
         // do nothing
      }

      /**
       * Get the band type.
       */
      @Override
      public int getBandType() {
         return btype;
      }

      /**
       * Get the group band level for the cell.
       */
      @Override
      public int getBandLevel() {
         return blevel;
      }

      /**
       * Get the position for the cell in the band.
       */
      @Override
      public Point getPosition() {
         // section not care this
         return new Point(0, bidx);
      }

      /**
       * Set value as group.
       */
      @Override
      public void setAsGroup(boolean asGroup) {
      }

      public ReportElement getElement() {
         return elem;
      }

      private SectionBand band;
      private int bidx = -1;
      private ReportElement elem;
      private int blevel = -1;
      private int btype = -1;
      private int xposi = -1;
   }

   private SectionBand[] header;
   private SectionBand[] footer;
   private SectionBand[] content;

   private static final Logger LOG =
      LoggerFactory.getLogger(DefaultSectionLens.class);
}

