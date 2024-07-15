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
package inetsoft.web.binding.event;

import java.awt.*;

/**
 * Class that encapsulates the parameters for applying a selection.
 *
 * @since 12.3
 */
public class RefreshVSBindingEvent {
   /**
    * Get the assembly name.
    * @return assembly name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the assembly name.
    * @param name assembly name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the viewsheet id.
    * @return viewsheet id.
    */
   public String getVsId() {
      return vsid;
   }

   /**
    * Set the viewsheet id.
    * @param vsid viewsheet id.
    */
   public void setVsId(String vsid) {
      this.vsid = vsid;
   }

   /**
    * Set the max size.
    * @param maxSize max size.
    */
   public void setMaxSize(Dimension maxSize) {
      this.maxSize = maxSize;
   }

   /**
    * Get the max size.
    * @return max size.
    */
   public Dimension getMaxSize() {
      return maxSize;
   }

   private String vsid;
   private String name;
   private Dimension maxSize;
}
