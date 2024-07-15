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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

@JsonIgnoreProperties
public class GraphNodeModel {

   public GraphNodeModel() {
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getLabel() {
      return label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getTableName() {
      return tableName;
   }

   public void setTableName(String tableName) {
      this.tableName = tableName;
   }

   public String getTooltip() {
      return tooltip;
   }

   public void setTooltip(String tooltip) {
      this.tooltip = tooltip;
   }

   public String getAliasSource() {
      return aliasSource;
   }

   public void setAliasSource(String aliasSource) {
      this.aliasSource = aliasSource;
   }

   public String getTreeLink() {
      return treeLink;
   }

   public void setTreeLink(String treeLink) {
      this.treeLink = treeLink;
   }

   public String getOutgoingAliasSource() {
      return outgoingAliasSource;
   }

   public void setOutgoingAliasSource(String outgoingAliasSource) {
      this.outgoingAliasSource = outgoingAliasSource;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      }

      if (o == null || getClass() != o.getClass()) {
         return false;
      }

      GraphNodeModel that = (GraphNodeModel) o;

      return id.equals(that.id);
   }

   @Override
   public int hashCode() {
      return Objects.hash(id);
   }

   private String id;
   private String name;
   private String tableName;
   private String label;
   private String tooltip;
   private String treeLink;
   private String aliasSource; // alias and auto alias
   private String outgoingAliasSource;
}
