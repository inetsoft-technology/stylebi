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
package inetsoft.web.binding.dnd;

public class TableRemoveColumnInfo extends ChangeColumnInfo {
   public void setType(String rtype) {
      this.type = rtype;
   }

   public String getType() {
      return type;
   }

   public void setIndex(int idx) {
      this.index = idx;
   }

   public int getIndex() {
      return index;
   }
   
   public String toAttribute() {
      String attribute = "";

      if(index != 0) {
         attribute += "_index:" + index;
      }

      return "_type:" + type + attribute;
   }

   private String type;
   private int index;
}