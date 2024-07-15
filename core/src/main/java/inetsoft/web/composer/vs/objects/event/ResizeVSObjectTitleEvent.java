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
package inetsoft.web.composer.vs.objects.event;

/**
 * Class that encapsulates the parameters for resizing an object title.
 *
 * @since 12.3
 */
public class ResizeVSObjectTitleEvent extends ResizeVSObjectEvent {
   /**
    * Gets the height of the object title.
    *
    * @return the height of the object title.
    */
   public int getTitleHeight() {
      return titleHeight;
   }

   /**
    * Sets the height of the object title.
    *
    * @param titleHeight the width of the object.
    */
   public void setTitleHeight(int titleHeight) {
      this.titleHeight = titleHeight;
   }

   @Override
   public String toString() {
      return "ResizeVSObjectTitleEvent{" +
         "name='" + this.getName() + '\'' +
         ", titleHeight=" + this.titleHeight +
         '}';
   }

   private int titleHeight;
}
