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
package inetsoft.web.composer.vs.objects.event;

/**
 * Class that encapsulates the parameters for changing the x index of an object.
 *
 * @since 12.3
 */
public class ChangeVSObjectLayerEvent extends VSObjectEvent {
   /**
    * Component to switch indexes with.
    * @return component name
    */
   public String getSwitchWithObject() {
      return switchWithObject;
   }

   /**
    * Component to switch indexes with.
    * @param switchWithObject
    */
   public void setSwitchWithObject(String switchWithObject) {
      this.switchWithObject = switchWithObject;
   }

   /**
    * Gets the z index of the object.
    *
    * @return the z index of the object.
    */
   public int getzIndex() {
      return zIndex;
   }

   /**
    * Sets the z index of the object.
    *
    * @param zIndex the z index of the object.
    */
   public void setzIndex(int zIndex) {
      this.zIndex = zIndex;
   }

   @Override
   public String toString() {
      return "ChangeVSObjectLayerEvent{" +
         "name='" + this.getName() + '\'' +
         ", zIndex=" + zIndex +
         '}';
   }

   private String switchWithObject;
   private int zIndex;
}
