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
package inetsoft.web.portal.model.database;

import com.fasterxml.jackson.annotation.JsonValue;
import inetsoft.uql.erm.XRelationship;
import inetsoft.uql.util.rgraph.Relation;

public enum JoinType {
   EQUAL(XRelationship.EQUAL),
   LEFT_OUTER(XRelationship.LEFT_OUTER),
   RIGHT_OUTER(XRelationship.RIGHT_OUTER),
   FULL_OUTER(Relation.FULL_OUTER),
   GREATER(XRelationship.GREATER),
   LESS(XRelationship.LESS),
   GREATER_EQUAL(XRelationship.GREATER_EQUAL),
   LESS_EQUAL(XRelationship.LESS_EQUAL),
   NOT_EQUAL(XRelationship.NOT_EQUAL);

   JoinType(String type) {
      this.type = type;
   }

   public static JoinType forType(String type) {
      JoinType result = null;

      for(JoinType t : values()) {
         if(t.type.equals(type)) {
            result = t;
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