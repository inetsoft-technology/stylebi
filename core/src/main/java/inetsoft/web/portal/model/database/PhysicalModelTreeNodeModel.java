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

import javax.annotation.Nullable;

public class PhysicalModelTreeNodeModel extends DatabaseTreeNode{
   public boolean isSelected() {
      return selected;
   }

   public void setSelected(boolean selected) {
      this.selected = selected;
   }

   public String getAlias() {
      return alias;
   }

   @Nullable
   public void setAlias(String alias) {
      this.alias = alias;
   }

   public String getSql() {
      return sql;
   }

   @Nullable
   public void setSql(String sql) {
      this.sql = sql;
   }

   public boolean isAutoAlias() {
      return autoAlias;
   }

   public void setAutoAlias(boolean autoAlias) {
      this.autoAlias = autoAlias;
   }

   public boolean isJoins() {
      return joins;
   }

   public void setJoins(boolean joins) {
      this.joins = joins;
   }

   public String getAliasSource() {
      return aliasSource;
   }

   @Nullable
   public void setAliasSource(String aliasSource) {
      this.aliasSource = aliasSource;
   }

   public boolean isBaseTable() {
      return baseTable;
   }

   public void setBaseTable(boolean baseTable) {
      this.baseTable = baseTable;
   }

   private boolean selected;
   private String alias;
   private String sql;
   private boolean autoAlias;
   private boolean joins; // has joins
   private String aliasSource;
   private boolean baseTable;
}
