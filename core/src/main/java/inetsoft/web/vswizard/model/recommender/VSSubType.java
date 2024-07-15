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
package inetsoft.web.vswizard.model.recommender;

import inetsoft.util.Tool;

import javax.annotation.Nullable;

public class VSSubType implements SubType {
   public VSSubType() {
   }

   public VSSubType(String type) {
      this.type = type;
   }

   public VSSubType(String type, boolean selected) {
      this.type = type;
      this.selected = selected;
   }

   /**
    * Setter for type.
    */
   @Override
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Getter for type.
    */
   @Override
   public String getType() {
      return type;
   }

   @Override
   public boolean isSelected() {
      return selected;
   }

   @Override
   public void setSelected(boolean selected) {
      this.selected = selected;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      VSSubType that = (VSSubType) o;

      return Tool.equals(type, that.type);
   }

   private String type;
   @Nullable
   private boolean selected;
}
