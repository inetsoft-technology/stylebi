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
package inetsoft.web.portal.model.database;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TableType {
   PHYSICAL(0), // PartitionTable.PHYSICAL
   VIEW(1); // PartitionTable.VIEW

   TableType(int type) {
      this.type = type;
   }

   public static TableType forType(int type) {
      TableType result = null;

      for(TableType t : values()) {
         if(t.type == type) {
            result = t;
            break;
         }
      }

      if(result == null) {
         throw new IllegalArgumentException("Invalid type: " + type);
      }

      return result;
   }

   public int getType() {
      return type;
   }

   public void setType(int type) {
      this.type = type;
   }

   @JsonValue
   public int toValue() {
      return ordinal();
   }

   private int type;
}