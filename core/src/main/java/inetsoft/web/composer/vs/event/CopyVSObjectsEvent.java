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
package inetsoft.web.composer.vs.event;

/**
 * Class that encapsulates the parameters for opening a viewsheet.
 *
 * @since 12.3
 */
public class CopyVSObjectsEvent {
   /**
    * Gets the list of object names to copy.
    */
   public String[] getObjects() {
      return objects;
   }

   /**
    * Sets the list of object names to copy.
    */
   public void setObjects(String[] objects) {
      this.objects = objects;
   }

   /**
    * Gets whether this is a cut event.
    */
   public boolean isCut() {
      return cut;
   }

   /**
    * Sets whether this is a cut event.
    */
   public void setCut(boolean isCut) {
      this.cut = isCut;
   }

   /**
    * Gets the x offset of the object.
    */
   public int getxOffset() {
      return xOffset;
   }

   /**
    * Sets the x offset of the object.
    */
   public void setxOffset(int xOffset) {
      this.xOffset = xOffset;
   }

   /**
    * Gets the y offset of the object.
    */
   public int getyOffset() {
      return yOffset;
   }

   /**
    * Sets the yOffset of the object.
    */
   public void setyOffset(int yOffset) {
      this.yOffset = yOffset;
   }

   /**
    * Check if the offset is relative to the first assembly position.
    */
   public boolean isRelative() {
      return relative;
   }

   public void setRelative(boolean relative) {
      this.relative = relative;
   }

   @Override
   public String toString() {
      return "CopyVSObjectsEvent{" + "cut='" + cut + "\'}";
   }

   private String[] objects;
   private boolean cut;
   private int xOffset;
   private int yOffset;
   private boolean relative;
}
