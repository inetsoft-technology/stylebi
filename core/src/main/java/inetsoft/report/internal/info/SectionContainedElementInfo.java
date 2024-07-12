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

import inetsoft.util.Tool;

import java.awt.*;
import java.io.Serializable;

/**
 * The class was used to hold basic infomation for the element contained in
 * a section.
 *
 * @version 6.5 7/20/2004
 * @author InetSoft Technology Corp
 */
public class SectionContainedElementInfo implements Serializable, Cloneable {
   /**
    * Get the section contained element ID.
    */
   public String getID() {
      return id;
   }

   /**
    * Set the section contained element ID.
    */
   public void setID(String id) {
      this.id = id;
   }

   /**
    * Get the section contained element data.
    */
   public String getData() {
      return data;
   }

   /**
    * Set the section contained element data.
    */
   public void setData(String data) {
      this.data = data;
   }

   /**
    * Get the section contained element type.
    */
   public String getType() {
      return type;
   }

   /**
    * Set the section contained element type.
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Get the section contained element binding.
    */
   public String getBinding() {
      return binding;
   }

   /**
    * Set the section contained element binding.
    */
   public void setBinding(String binding) {
      this.binding = binding;
   }

   /**
    * Set the cell notation label.
    */
   public void setNotationLabel(String notationLabel) {
      this.notationLabel = notationLabel;
   }

   /**
    * Get the section contained element bounds.
    */
   public Rectangle getBounds() {
      return bounds;
   }

   /**
    * Set the section contained element bounds.
    */
   public void setBounds(Rectangle bounds) {
      this.bounds = bounds;
   }

   /**
    * Get the section contained element information.
    */
   public ElementInfo getElementInfo() {
      return einfo;
   }

   /**
    * Set the section contained element information.
    */
   public void setElementInfo(ElementInfo einfo) {
      this.einfo = einfo;
   }

   /**
    * Clone.
    */
   @Override
   public Object clone() throws CloneNotSupportedException {
      SectionContainedElementInfo info =
         (SectionContainedElementInfo) super.clone();
      info.einfo = (ElementInfo) einfo.clone();

      return info;
   }

   private String type;
   private String id;
   private String data;
   private String binding;
   private Rectangle bounds;
   private ElementInfo einfo;
   private String notationLabel;
}
