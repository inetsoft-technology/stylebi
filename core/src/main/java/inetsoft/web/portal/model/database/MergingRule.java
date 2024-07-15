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

public enum MergingRule {
   AND("and"),
   OR("or");

   MergingRule(String type) {
      this.type = type;
   }

   public static MergingRule forType(String type) {
      MergingRule result = null;

      for(MergingRule r : values()) {
         if(r.type.equals(type)) {
            result = r;
            break;
         }
      }

      if(result == null) {
         throw new IllegalArgumentException("Invalid type: " + type);
      }

      return result;
   }

   @JsonValue
   public int toValue() {
      return ordinal();
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   private String type;
}