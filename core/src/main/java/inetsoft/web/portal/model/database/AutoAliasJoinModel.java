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
package inetsoft.web.portal.model.database;

import inetsoft.uql.erm.AutoAlias;

public class AutoAliasJoinModel {
   public AutoAliasJoinModel() {
   }

   public AutoAliasJoinModel(AutoAlias.IncomingJoin incomingJoin) {
      setForeignTable(incomingJoin.getSourceTable());
      setAlias(incomingJoin.getAlias());
      setPrefix(incomingJoin.getPrefix());
      setKeepOutgoing(incomingJoin.isKeepOutgoing());
      setSelected(true);
   }

   public String getForeignTable() {
      return foreignTable;
   }

   public void setForeignTable(String foreignTable) {
      this.foreignTable = foreignTable;
   }

   public String getAlias() {
      return alias;
   }

   public void setAlias(String alias) {
      this.alias = alias;
   }

   public boolean isKeepOutgoing() {
      return keepOutgoing;
   }

   public void setKeepOutgoing(boolean keepOutgoing) {
      this.keepOutgoing = keepOutgoing;
   }

   public String getPrefix() {
      return prefix;
   }

   public void setPrefix(String prefix) {
      this.prefix = prefix;
   }

   public boolean isSelected() {
      return selected;
   }

   public void setSelected(boolean selected) {
      this.selected = selected;
   }

   @Override
   public String toString() {
      return "AutoAliasJoinModel{" +
         "foreignTable='" + foreignTable + '\'' +
         ", alias='" + alias + '\'' +
         ", keepOutgoing=" + keepOutgoing +
         ", prefix='" + prefix + '\'' +
         ", selected=" + selected +
         '}';
   }

   private String foreignTable;
   private String alias;
   private boolean keepOutgoing;
   private String prefix;
   private boolean selected;
}