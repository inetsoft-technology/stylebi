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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;

/**
 * Base class for all paintable classes.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class BasePaintable implements Paintable {
   protected BasePaintable() {
   }

   /**
    * Create a paintable.
    */
   public BasePaintable(ReportElement elem) {
      this.elem = elem;

      if(elem != null) {
         BaseElement elem2 = (BaseElement) elem;
         ReportSheet report = elem2.getReport();
         userObj = elem2.getUserObject();

         if(report != null) {
             if(false) {
               this.frame = ((BaseElement) elem).getFrame();
            }
         }
      }
   }

   /**
    * Check if is serializable.
    */
   public boolean isSerializable() {
      return true;
   }

   /**
    * Set the report element that this paintable area corresponds to.
    */
   public void setElement(ReportElement elem) {
      this.elem = elem;
   }

   /**
    * Get the report element that this paintable area corresponds to.
    * @return report element.
    */
   @Override
   public ReportElement getElement() {
      return elem;
   }

   /**
    * Get the row.
    */
   public final int getRow() {
      if(row == null) {
         row = (userObj instanceof BindingInfo) ?
            Integer.valueOf(((BindingInfo) userObj).getRow()) : Integer.valueOf(-1);
      }

      return row.intValue();
   }

   /**
    * Set an user object. The object must be serializable.
    */
   public void setUserObject(Object obj) {
      userObj = obj;

      // @by larryl, element inside a EditRegion which is in turn inside a
      // section will have the BindingInfo set
      Object parent = ((BaseElement) elem).getParent();

      ((BaseElement) elem).setInSection(parent instanceof SectionBand &&
                                        obj instanceof BindingInfo);
   }

   /**
    * Get the user object.
    */
   public Object getUserObject() {
      return userObj;
   }

   /**
    * Check if this paintable must wait for the entire report to be processed.
    * This is true for elements that need information from report, such
    * as page total, table of contents page index.
    */
   public boolean isBatchWaiting() {
      return false;
   }

   /**
    * Set the frame (print box) of this element.
    */
   public void setFrame(Rectangle frame) {
      this.frame = frame;
   }

   /**
    * Get the frame of this element.
    */
   public Rectangle getFrame() {
      return frame;
   }

   /**
    * Set the virtual clipping area for this paintable. This does not affect
    * how the painter is painted. It is only for informational purpose for
    * the elements in section bands. The clip is used in designer to
    * calculate the placement of an element.
    * @param clip clipping area Y is the distance of the current paintable
    * area to the top of the section band. It is positive if the band is
    * broken up into multiple pages.
    */
   public void setVirtualClip(Rectangle clip) {
      this.vclip = clip;
   }

   /**
    * Get the virtual clipping area for this paintable.
    */
   public Rectangle getVirtualClip() {
      return vclip;
   }

   /**
    * This method is called when a page is fully printed.
    */
   public void complete() {
   }

   /**
    * Clear cache.
    */
   public void clearCache() {
   }

   /**
    * Get the hyper link on this element.
    */
   public Hyperlink.Ref getHyperlink() {
      return null;
   }

   /**
    * Check whether the element is inside a section.
    */
   public boolean isInSection() {
      return elem instanceof BaseElement && ((BaseElement) elem).isInSection();
   }

   static Color readColor(ObjectInputStream s)
      throws IOException, ClassNotFoundException {
      Integer color = (Integer) s.readObject();

      return (color == null) ? null : new Color(color.intValue());
   }

   static void writeColor(ObjectOutputStream s, Color color)
      throws IOException {
      s.writeObject((color == null) ? null : Integer.valueOf(color.getRGB()));
   }

   static Font readFont(ObjectInputStream s)
      throws IOException, ClassNotFoundException {
      String font = (String) s.readObject();

      return (font == null) ? null : StyleFont.decode(font);
   }

   static void writeFont(ObjectOutputStream s, Font font) throws IOException {
      s.writeObject((font == null) ? null : StyleFont.toString(font));
   }

   protected transient ReportElement elem;
   private transient Rectangle frame; // used in designer only
   private transient Integer row = null;
   private Rectangle vclip; // virtual clip
   private Object userObj;
   protected boolean strictNull = true; // for bc

   private static final Logger LOG =
      LoggerFactory.getLogger(BasePaintable.class);
}
