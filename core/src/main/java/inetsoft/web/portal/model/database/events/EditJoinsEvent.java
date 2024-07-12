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
package inetsoft.web.portal.model.database.events;

import com.fasterxml.jackson.annotation.*;
import inetsoft.web.portal.model.database.JoinModel;
import inetsoft.web.portal.model.database.PhysicalTableModel;

import javax.annotation.Nullable;

public class EditJoinsEvent {
   public EditJoinEventItem[] getJoinItems() {
      return joinItems;
   }

   public void setJoinItems(EditJoinEventItem[] joinItems) {
      this.joinItems = joinItems;
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   @JsonIgnoreProperties(ignoreUnknown = true)
   @JsonTypeInfo(
      include = JsonTypeInfo.As.PROPERTY,
      use = JsonTypeInfo.Id.NAME,
      property = "actionType"
   )
   @JsonSubTypes({
      @JsonSubTypes.Type(value = ModifyJoinEventItem.class, name = "modify"),
      @JsonSubTypes.Type(value = RemoveJoinEventItem.class, name = "remove"),
      @JsonSubTypes.Type(value = EditJoinEventItem.class, name = "add")
   })
   public static class EditJoinEventItem {
      public PhysicalTableModel getTable() {
         return table;
      }

      public void setTable(PhysicalTableModel table) {
         this.table = table;
      }

      public JoinModel getJoin() {
         return join;
      }

      @Nullable
      public void setJoin(JoinModel join) {
         this.join = join;
      }

      private PhysicalTableModel table;
      private JoinModel join;
   }

   public static class ModifyJoinEventItem extends EditJoinEventItem {
      public JoinModel getOldJoin() {
         return oldJoin;
      }

      @Nullable
      public void setOldJoin(JoinModel oldJoin) {
         this.oldJoin = oldJoin;
      }

      private JoinModel oldJoin;
   }

   public static class RemoveJoinEventItem extends EditJoinEventItem {
      public String getForeignTable() {
         return foreignTable;
      }

      @Nullable
      public void setForeignTable(String foreignTable) {
         this.foreignTable = foreignTable;
      }

      private String foreignTable;
   }

   private EditJoinEventItem[] joinItems;
   private String id;
}
