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

import inetsoft.web.binding.drm.AggregateRefModel;

/**
 * Class that encapsulates the parameters for change chart type event.
 *
 * @since 12.3
 */
public class ModifyAggregateFieldEvent {
   /**
    * Get the assembly name.
    * @return assembly name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the assembly name.
    * @param name the assembly name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the table name.
    * @return table name.
    */
   public String getTableName() {
      return tableName;
   }

   /**
    * Set the table name.
    * @param tableName the table name.
    */
   public void setTableName(String tableName) {
      this.tableName = tableName;
   }

   /**
    * Get the new aggregate ref.
    * @return new aggregate ref.
    */
   public AggregateRefModel getNewRef() {
      return newRef;
   }

   /**
    * Set the new aggregate ref.
    * @param newRef the new aggregate ref.
    */
   public void setNewRef(AggregateRefModel newRef) {
      this.newRef = newRef;
   }

   /**
    * Get the old aggregate ref.
    * @return old aggregate ref.
    */
   public AggregateRefModel getOldRef() {
      return oldRef;
   }

   /**
    * Set the old aggregate ref.
    * @param oldRef the old aggregate ref.
    */
   public void setOldRef(AggregateRefModel oldRef) {
      this.oldRef = oldRef;
   }

   /**
    * Check if is confirmed.
    * @return <tt>true</tt> if confirmed, <tt>false</tt> otherwise.
    */
   public boolean isConfirmed() {
      return confirmed;
   }

   /**
    * Set if is confirmed.
    * @param confirmed <tt>true</tt> if confirmed, <tt>false</tt> otherwise.
    */
   public void setConfirmed(boolean confirmed) {
      this.confirmed = confirmed;
   }

   private String name;
   private String tableName;
   private AggregateRefModel newRef;
   private AggregateRefModel oldRef;
   private boolean confirmed;
}
