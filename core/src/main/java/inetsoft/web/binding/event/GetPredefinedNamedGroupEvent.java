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
package inetsoft.web.binding.event;

/**
 * Class that encapsulates the parameters for applying a selection.
 *
 * @since 12.3
 */
public class GetPredefinedNamedGroupEvent {
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
    * Get the cell binding column name.
    * @return the field.
    */
   public String getColumn() {
      return field;
   }

   /**
    * Set the column name.
    * @param field the field.
    */
   public void setColumn(String field) {
      this.field = field;
   }

   private String name;
   private String field;
}
