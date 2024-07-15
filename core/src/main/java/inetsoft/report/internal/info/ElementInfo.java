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
package inetsoft.report.internal.info;

import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.util.IteratorEnumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This is the base class of all element info classes. It contains
 * all common attributes of element info.
 */
public class ElementInfo implements Cloneable, Serializable {
   /**
    * Get an property of this element. The properties are used to extend
    * the report elements and allows additional information to be attached
    * to the elements.
    * @param name property name.
    * @return property value.
    */
   public final String getProperty(String name) {
      return (attrmap == null) ? null : attrmap.get(name);
   }

   /**
    * Set an property value.
    * @param name property name.
    * @param attr property value. Use null value to remove an
    * property.
    */
   public final void setProperty(String name, String attr) {
      if(attr == null) {
         if(attrmap != null) {
            attrmap.remove(name);
         }
      }
      else {
         if(attrmap == null) {
            attrmap = new ConcurrentHashMap();
         }

         attrmap.put(name, attr);
      }
   }

   /**
    * Get all attribute names.
    */
   public Enumeration getPropertyNames(boolean sort) {
      if(attrmap == null) {
         return Collections.emptyEnumeration();
      }

      if(!sort) {
         return attrmap.keys();
      }
      else {
         ArrayList list = new ArrayList(attrmap.keySet());
         Collections.sort(list);
         return new IteratorEnumeration(list.iterator());
      }
   }

   /**
    * Alignment, H_LEFT, H_CENTER, H_RIGHT, and V_TOP, V_CENTER, V_RIGHT.
    */
   public int getAlignment() {
      return alignment;
   }

   /**
    * Alignment, H_LEFT, H_CENTER, H_RIGHT, and V_TOP, V_CENTER, V_RIGHT.
    */
   public void setAlignment(int alignment) {
      this.alignment = (short) alignment;
   }

   /**
    * Indentation in inches.
    */
   public double getIndent() {
      return indent;
   }

   /**
    * Indentation in inches.
    */
   public void setIndent(double indent) {
      this.indent = (float) indent;
   }

   /**
    * Current font.
    */
   public Font getFont() {
      if(font == null) {
         return inetsoft.report.internal.Util.DEFAULT_FONT;
      }

      return font;
   }

   /**
    * Current font.
    */
   public void setFont(Font font) {
      this.font = font;
   }

   /**
    * Foreground color.
    */
   public Color getForeground() {
      return foreground;
   }

   /**
    * Foreground color.
    */
   public void setForeground(Color foreground) {
      this.foreground = foreground;
   }

   /**
    * Background color.
    */
   public Color getBackground() {
      return background;
   }

   /**
    * Background color.
    */
   public void setBackground(Color background) {
      this.background = background;
   }

   /**
    * Line spacing in pixels.
    */
   public int getSpacing() {
      return spacing;
   }

   /**
    * Line spacing in pixels.
    */
   public void setSpacing(int spacing) {
      this.spacing = (short) spacing;
   }

   /**
    * Check if this element is visible. Non-visible elements are
    * not printed.
    * @return true if element is visible.
    */
   public boolean isVisible() {
      return visible;
   }

   /**
    * Set the visibility of this element.
    * @param vis false to hide an element.
    */
   public void setVisible(boolean vis) {
      visible = vis;
   }


   /**
    * Check if the element should be hidden for printing and exporting.
    */
   public boolean isHideOnPrint() {
      return hideOnPrint;
   }

   /**
    * Set if the element should be hidden for printing and exporting.
    */
   public void setHideOnPrint(boolean hide) {
      this.hideOnPrint = hide;
   }

   /**
    * Check if this element should be kept on the same page as the next
    * element.
    */
   public boolean isKeepWithNext() {
      return keep;
   }

   /**
    * Set the keep with next flag of this element.
    */
   public void setKeepWithNext(boolean keep) {
      this.keep = keep;
   }

   /**
    * Return true if this element should occupy one block. It does
    * not share a line with other elements.
    */
   public boolean isBlock() {
      return block;
   }

   /**
    * Set whether this element is block or inline.
    */
   public void setBlock(boolean block) {
      this.block = block;
   }

   /**
    * Get the hyperlink target (anchor) of this element.
    */
   public String getTarget() {
      return target;
   }

   /**
    * Define the hyperlink target (anchor) of this element. If the target is
    * defined, hyperlinks can refered to the location of this element using
    * "#target" notation. This navigation is only used in Style Report
    * Enterprise Edition.
    */
   public void setTarget(String target) {
      this.target = target;
   }

   /**
    * Set the css class of the element.
    * @param elementClass the class name as defined in the css style sheet.
    */
   public void setCSSClass(String elementClass) {
      cssClass = elementClass;
   }

   /**
    * Get the css class of the element
    */
   public String getCSSClass() {
      return cssClass;
   }

   /**
    * Check whether the element is inside a section.
    */
   public boolean isInSection() {
      return (bitprop & IN_SECTION) != 0;
   }

   /**
    * Set if the element is inside a section.
    */
   public void setInSection(boolean section) {
      if(section != isInSection()) {
         bitprop = (byte) (bitprop ^ IN_SECTION);
      }
   }

   /**
    * Check whether the element has been printed.
    */
   public boolean isPrinted() {
      return (bitprop & PRINTED) != 0;
   }

   /**
    * Set if the element has been printed.
    */
   public void setPrinted(boolean flag) {
      if(flag != isPrinted()) {
         bitprop = (byte) (bitprop ^ PRINTED);
      }
   }

   /**
    * Copy the attributes from the element info into this object.
    */
   public void copy(ElementInfo info) {
      this.alignment = info.alignment;
      this.indent = info.indent;
      this.font = info.font;
      this.foreground = info.foreground;
      this.background = info.background;
      this.spacing = info.spacing;
      this.visible = info.visible;
      this.keep = info.keep;
      this.target = info.target;
      this.cssClass = info.cssClass;
      this.block = info.block;
      this.bitprop = info.bitprop;
      this.hideOnPrint = info.hideOnPrint;
      this.alignmentUserFlag = info.alignmentUserFlag;
      this.fontUserFlag = info.fontUserFlag;
      this.foregroundUserFlag = info.foregroundUserFlag;
      this.backgroundUserFlag = info.backgroundUserFlag;

      if(info.attrmap != null) {
         this.attrmap = new ConcurrentHashMap<>(info.attrmap);
      }
   }

   /**
    * Clone this object.
    */
   @Override
   public Object clone() {
      try {
         ElementInfo ei = (ElementInfo) super.clone();

         if(attrmap != null) {
            ei.attrmap = new ConcurrentHashMap<>(attrmap);
         }

         return ei;
      }
      catch(Exception e) {
         LOG.error("Failed to clone element info", e);
      }

      return null;
   }

   /**
    * Get the name of the tag of the root of the properties xml tree.
    */
   public String getTagName() {
      return "elementInfo";
   }

   /**
    * Return true if the element is null.
    */
   public boolean isNull() {
      return alignment == 0 && indent == 0 && font == null &&
             foreground == null && background == null && spacing == 0 &&
             visible == true && keep == false && target == null &&
             (cssClass == null || "".equals(cssClass)) &&
             (attrmap == null || attrmap.size() == 0);
   }

   /**
    * Return true if the font was set via User (overrides CSS).
    */
   public boolean isFontByUser() {
      return fontUserFlag;
   }

   /**
    * Return true if the foreground was set via User (overrides CSS).
    */
   public boolean isForegroundByUser() {
      return foregroundUserFlag;
   }

   /**
    * Set true if the foreground was set via User (overrides CSS).
    */
   public void setForegroundByUser(boolean userFlag) {
      foregroundUserFlag = userFlag;
   }

   /**
    * Return true if the background was set via User (overrides CSS).
    */
   public boolean isBackgroundByUser() {
      return backgroundUserFlag;
   }

   /**
    * Set true if the background was set via User (overrides CSS).
    */
   public void setBackgroundByUser(boolean userFlag) {
      backgroundUserFlag = userFlag;
   }

   /**
    * Return true if the alignment was set via User (overrides CSS).
    */
   public boolean isAlignmentByUser() {
      return alignmentUserFlag;
   }

   /**
    * Set true if the alignment was set via User (overrides CSS).
    */
   public void setAlignmentByUser(boolean userFlag) {
      alignmentUserFlag = userFlag;
   }

   /**
    * Create an ElementInfo.
    */
   protected ElementInfo create() {
      return new ElementInfo();
   }

   /**
    * This method must be overriden by subclass to return the default info in
    * section.
    */
   public ElementInfo createInSection(boolean autoResize, String name) {
      ElementInfo info = create();
      info.foreground = Color.decode("-16777216");
      info.alignment = StyleConstants.H_LEFT;
      info.font = StyleFont.decode("Dialog-PLAIN-10");
      return info;
   }

   // bit property for memory optimization
   private static byte EDITABLE = 1;
   private static byte IN_SECTION = 4;
   private static byte PRINTED = 8;

   private byte bitprop = EDITABLE;

   /**
    * Alignment, H_LEFT, H_CENTER, H_RIGHT, and V_TOP, V_CENTER, V_RIGHT.
    */
   private short alignment;
   /**
    * Alignment was set via User (overrides CSS).
    */
   private boolean alignmentUserFlag;
   /**
    * Indentation in inches.
    */
   private float indent;
   /**
    * Current font.
    */
   private Font font;
   /**
    * Current font was set via User (overrides CSS).
    */
   private boolean fontUserFlag;
   /**
    * Foreground color.
    */
   private Color foreground;
   /**
    * Foreground color was set via User (overrides CSS).
    */
   private boolean foregroundUserFlag;
   /**
    * Background color.
    */
   private Color background;

   /**
    * Background color was set via User (overrides CSS).
    */
   private boolean backgroundUserFlag;
   /**
    * Line spacing in pixels.
    */
   private short spacing;
   /**
    * ReportElement visibility.
    */
   private boolean visible = true;
   /**
    * Keep with next.
    */
   private boolean keep = false;
   /**
    * Hyperlink anchor.
    */
   private String target = null;
   /**
    * CSS class name.
    */
   private String cssClass = null;
   /**
    * Set whether the element should be hidden on print.
    */
   private boolean hideOnPrint = false;

   private ConcurrentHashMap<String, String> attrmap = null; // attributes
   private boolean block; //true if start on a new line

   private static final Logger LOG =
      LoggerFactory.getLogger(ElementInfo.class);
}
