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
package inetsoft.web.binding.event;

/**
 * Class that encapsulates the parameters for applying a selection.
 *
 * @since 12.3
 */
public class RefreshBindingTreeEvent {
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
    * Get if layout model.
    */
   public boolean isLayoutMode() {
      return layoutMode;
   }

   /**
    * Set the layout model.
    */
   public void setLayoutMode(boolean layoutMode) {
      this.layoutMode = layoutMode;
   }

   private String name;
   private boolean layoutMode;
}
