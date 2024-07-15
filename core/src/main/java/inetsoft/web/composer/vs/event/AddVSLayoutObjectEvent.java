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
package inetsoft.web.composer.vs.event;

/**
 * Class that encapsulates the parameters for adding an object to the layout.
 *
 * @since 12.3
 */
public class AddVSLayoutObjectEvent {
   /**
    * Gets the type of object to create.
    *
    * @return the type of object.
    */
   public int getType() {
      return type;
   }

   /**
    * Sets the type of object to create.
    *
    * @param type the type of object.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Gets the x offset of the object.
    *
    * @return the x offset of the object.
    */
   public int getxOffset() {
      return xOffset;
   }

   /**
    * Sets the x offset of the object.
    *
    * @param xOffset the x offset of the object.
    */
   public void setxOffset(int xOffset) {
      this.xOffset = xOffset;
   }

   /**
    * Gets the y offset of the object.
    *
    * @return the y offset of the object.
    */
   public int getyOffset() {
      return yOffset;
   }

   /**
    * Sets the y Offset of the object.
    *
    * @param yOffset the y offset of the object.
    */
   public void setyOffset(int yOffset) {
      this.yOffset = yOffset;
   }

   /**
    * Gets the name of the object.
    *
    * @return the name of the object.
    */
   public String[] getNames() {
      return names;
   }

   /**
    * Sets the names of the objects.
    *
    * @param names the names of the objects.
    */
   public void setNames(String[] names) {
      this.names = names;
   }

   /**
    * Gets the name of the layout.
    *
    * @return the name of the layout.
    */
   public String getLayoutName() {
      return layoutName;
   }

   /**
    * Sets the name of the layout.
    *
    * @param layoutName the name of the layout.
    */
   public void setLayoutName(String layoutName) {
      this.layoutName = layoutName;
   }

   /**
    * Gets the number representing the layout region.
    *
    * @return the number representing the layout region (header, content, footer).
    */
   public int getRegion() {
      return region;
   }

   /**
    * Sets the number representing the layout region.
    *
    * @param region the number representing the layout region (header, content, footer).
    */
   public void setRegion(int region) {
      this.region = region;
   }

   @Override
   public String toString() {
      return "AddNewVSObjectEvent{" +
         "type='" + type + '\'' +
         ", xOffset=" + xOffset +
         ", yOffset=" + yOffset +
         ", names=" + names +
         ", layoutName=" + layoutName +
         ", region=" + region +
         '}';
   }

   private int type;
   private int xOffset;
   private int yOffset;
   private String[] names;
   private String layoutName;
   private int region;
}
