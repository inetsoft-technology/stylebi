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
package inetsoft.report.internal.info;

import inetsoft.report.SectionBand;
import inetsoft.report.internal.BindingInfo;
import inetsoft.report.internal.Cacheable;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Section element encapsulate the printing of a section.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class SectionBandInfo implements Serializable, Cacheable {
   /**
    * Default Constructor.
    */
   public SectionBandInfo() {
   }

   /**
    * Constructor, create with a sectionBand param.
    */
   public SectionBandInfo(SectionBand band, String type, int level,
                          int bandidx, float h) {
      this.type = type;
      this.level = level;
      this.bandidx = (short) bandidx;
      this.height = h;
      this.band = band;
      bg = band.getBackground();
      bandHeight = band.getHeight();
      vlines = band.getVSeparators();
      visible = band.isVisible();
   }

   /**
    * Get band info hash code.
    */
   public int hashCode() {
      return level + type.hashCode() + bandidx + (int) height +
         (int) bandHeight;
   }

   public boolean equals(Object obj) {
      if(obj instanceof SectionBandInfo) {
         SectionBandInfo info = (SectionBandInfo) obj;

         return Tool.equals(info.type, type) && info.level == level &&
            info.height == height && info.advance == advance &&
            info.offset == offset && info.bandidx == bandidx &&
            info.band == band &&
            bandHeight == info.bandHeight &&
            equalsColor(bg, info.bg);
      }

      return false;
   }

   /**
    * Check if equals in color.
    */
   private boolean equalsColor(Color a, Color b) {
      return a == null ? a == b : a.equals(b);
   }

   /**
    * Clone a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         SectionBandInfo info = (SectionBandInfo) super.clone();

         for(int i = 0; elemSet != null && i < elemSet.size(); i++) {
            Object elem = ((SectionContainedElementInfo)elemSet.get(i)).clone();
            info.addElement((SectionContainedElementInfo) elem);
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone section band info", ex);
         return this;
      }
   }

   /**
    * Get a band vertical separator.
    */
   public SectionBand.Separator getVSeparator(int id) {
      return vlines[id];
   }

   /**
    * Return separator arry.
    */
   public SectionBand.Separator[] getVSeparators() {
      return vlines;
   }

   /**
    * Set separator array.
    */
   public void setVSeparators(SectionBand.Separator[] vlines) {
      this.vlines = vlines;
   }

   /**
    * Get band background color.
    */
   public Color getBackground() {
      return bg;
   }

   /**
    * Set band background color.
    */
   public void setBackground(Color bg) {
      this.bg = bg;
   }

   /**
    * Set groupValue in sectionband.
    */
   public void setGroupValue(String groupValue) {
      this.groupValue = groupValue;
   }

   /**
    * Get groupValue in sectionband.
    */
   public String getGroupValue() {
      return groupValue;
   }

   /**
    * get band type.
    */
   public String getType() {
      return this.type;
   }

   /**
    * set band type.
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * get band level.
    */
   public int getLevel() {
      return this.level;
   }

   /**
    * set band level.
    */
   public void setLevel(int level) {
      this.level = level;
   }

   /**
    * Check if this band is visible.
    */
   public boolean isVisible() {
      return visible;
   }

   /**
    * Show or hide this band.
    * @param vis false to hide this band.
    */
   public void setVisible(boolean vis) {
      this.visible = vis;
   }

   public float getHeight() {
      return height;
   }

   public void setHeight(float height) {
      this.height = height;
   }

   /**
    * Get originally specified band height.
    */
   public float getBandHeight() {
      return bandHeight;
   }

   /**
    * Set the band height.
    * @param inch frame height in inches.
    */
   public void setBandHeight(float inch) {
      this.bandHeight = Math.max(0, inch); // no negative height
   }

   /**
    * Add an element to sectionBand.
    */
   public void addElement(SectionContainedElementInfo elem) {
      if(elemSet == null) {
         elemSet = new Vector();
      }

      elemSet.add(elem);
   }

   /**
    * Return all elements in this sectionBand.
    */
   public Enumeration getElements() {
      return (elemSet != null) ? elemSet.elements() : new Vector().elements();
   }

   public void setElements(SectionContainedElementInfo[] elems) {
      elemSet = null;

      for(SectionContainedElementInfo elem: elems) {
         addElement(elem);
      }
   }

   /**
    * Return the index of band in band array.
    */
   public int getIndex() {
      return (int) bandidx;
   }

   /**
    * Set band index.
    */
   public void setIndex(int index) {
      this.bandidx = (short) index;
   }

   public float getAdvance() {
      return advance;
   }

   public void setAdvance(float advance) {
      this.advance = advance;
   }

   public float getOffset() {
      return offset;
   }

   public void setOffset(float offset) {
      this.offset = offset;
   }

   private Color bg;
   private float bandHeight; // band height instead of printed band height
   private float height; // printed band height, may be < bandHeight if cut off
   private boolean visible = false;
   private SectionBand.Separator[] vlines = new SectionBand.Separator[0];
   private String type = BindingInfo.CONTENT; // design mode only
   private int level; // design mode only
   private short bandidx; // index of band in band array, design mode only
   private float advance = 0; // y position advance if at bottom
   // offset is the top of the current band paintable area from the band
   // this is only positive if a band is wrapped across pages
   private float offset;
   private Vector elemSet;
   private String groupValue = null;

   public transient SectionBand band = null; // can only be used in design mode
   public transient int row = -1;

   private static final Logger LOG =
      LoggerFactory.getLogger(SectionBandInfo.class);
}
