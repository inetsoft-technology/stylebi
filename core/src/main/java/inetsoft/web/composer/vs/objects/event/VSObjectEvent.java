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
 * Class that encapsulates the parameters for object events.
 *
 * @since 12.3
 */
public class VSObjectEvent {
   /**
    * Sets the object name.
    *
    * @param name the object name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Gets the object name.
    *
    * @return the object name.
    */
   public String getName() {
      return name;
   }

   private String name;
}
