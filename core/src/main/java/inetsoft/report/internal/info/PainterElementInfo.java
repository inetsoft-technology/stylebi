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
package inetsoft.report.internal.info;

import inetsoft.report.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents a painter element.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class PainterElementInfo extends ElementInfo {
   /**
    * construct the class
    */
   public PainterElementInfo() {
      super();
   }

   /**
    * Get the painter size
    */
   public Size getSize() {
      return size;
   }

   /**
    * Set the painter size
    */
   public void setSize(Size size) {
      this.size = size;
   }

   /**
    * Return true if the borderColor was set via User (overrides CSS).
    */
   public boolean isBorderColorByUser() {
      return borderColorByUser;
   }

   /**
    * Set true if the borderColor was set via User (overrides CSS).
    */
   public void setBorderColorByUser(boolean userFlag) {
      borderColorByUser = userFlag;
   }

   /**
    * Return true if the borderColor was set via User (overrides CSS).
    */
   public boolean isBordersByUser() {
      return bordersByUser;
   }

   /**
    * Set true if the borderColor was set via User (overrides CSS).
    */
   public void setBordersByUser(boolean userFlag) {
      bordersByUser = userFlag;
   }

   /**
    * Get the name of the tag of the root of the properties xml tree.
    */
   @Override
   public String getTagName() {
      return "painterElementInfo";
   }

   /**
    * Clones this object
    */
   @Override
   public Object clone() {
      try {
         PainterElementInfo iinfo = (PainterElementInfo) super.clone();

         if(size != null) {
            iinfo.size = new Size(size);
         }

         return iinfo;
      }
      catch(Exception e) {
         LOG.error("Failed to clone painter element info", e);
      }

      return null;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "PainterElementInfo: " + size + "|" + hashCode();
   }

   private Size size;
   private boolean borderColorByUser;
   private boolean bordersByUser;

   private static final Logger LOG =
      LoggerFactory.getLogger(PainterElementInfo.class);
}
