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

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PhysicalTableModel {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getQualifiedName() {
      return qualifiedName;
   }

   public void setQualifiedName(String qualifiedName) {
      this.qualifiedName = qualifiedName;
   }

   public String getPath() {
      return path;
   }

   public void setPath(String path) {
      this.path = path;
   }

   public String getAlias() {
      return alias;
   }

   public void setAlias(String alias) {
      this.alias = alias;
   }

   public String getOldAlias() {
      return oldAlias;
   }

   public void setOldAlias(String oldAlias) {
      this.oldAlias = oldAlias;
   }

   public String getCatalog() {
      return catalog;
   }

   public void setCatalog(String catalog) {
      this.catalog = catalog;
   }

   public String getSchema() {
      return schema;
   }

   public void setSchema(String schema) {
      this.schema = schema;
   }

   public TableType getType() {
      return type;
   }

   public void setType(TableType type) {
      this.type = type;
   }

   public String getSql() {
      return sql;
   }

   public void setSql(String sql) {
      this.sql = sql;
   }

   public String getAliasSource() {
      return aliasSource;
   }

   public void setAliasSource(String aliasSource) {
      this.aliasSource = aliasSource;
   }

   public List<JoinModel> getJoins() {
      if(joins == null) {
         joins = new ArrayList<>();
      }

      return joins;
   }

   public void setJoins(List<JoinModel> joins) {
      this.joins = joins;
   }

   public List<AutoAliasJoinModel> getAutoAliases() {
      if(autoAliases == null) {
         autoAliases = new ArrayList<>();
      }

      return autoAliases;
   }

   public void setAutoAliases(List<AutoAliasJoinModel> autoAliases) {
      this.autoAliases = autoAliases;
   }

   public boolean isAutoAliasesEnabled() {
      return autoAliasesEnabled;
   }

   public void setAutoAliasesEnabled(boolean autoAliasesEnabled) {
      this.autoAliasesEnabled = autoAliasesEnabled;
   }

   public Rectangle getBounds() {
      return bounds;
   }

   public void setBounds(Rectangle bounds) {
      this.bounds = bounds;
   }

   public List<GraphColumnInfo> getCols() {
      return cols;
   }

   public void setCols(List<GraphColumnInfo> cols) {
      this.cols = cols;
   }

   public boolean isBaseTable() {
      return baseTable;
   }

   public void setBaseTable(boolean baseTable) {
      this.baseTable = baseTable;
   }

   public String getOutgoingAliasSource() {
      return outgoingAliasSource;
   }

   public void setOutgoingAliasSource(String outgoingAliasSource) {
      this.outgoingAliasSource = outgoingAliasSource;
   }

   @Override
   public String toString() {
      return "PhysicalTableModel{" +
         "name='" + name + '\'' +
         ", catalog='" + catalog + '\'' +
         ", schema='" + schema + '\'' +
         ", qualifiedName='" + qualifiedName + '\'' +
         ", path='" + path + '\'' +
         ", alias='" + alias + '\'' +
         ", type=" + type +
         ", sql='" + sql + '\'' +
         ", aliasSource='" + aliasSource + '\'' +
         ", joins=" + joins +
         ", autoAliases=" + autoAliases +
         ", autoAliasesEnabled=" + autoAliasesEnabled +
         ", baseTable=" + baseTable +
         ", outgoingAliasSource='" + outgoingAliasSource + '\'' +
         '}';
   }

   private String name;
   private String catalog;
   private String schema;
   private String qualifiedName;
   private String path;
   private String alias;
   private String oldAlias;
   private TableType type;
   private String sql;
   private String aliasSource;
   private List<JoinModel> joins;
   private List<AutoAliasJoinModel> autoAliases;
   private boolean autoAliasesEnabled;
   @JsonIgnore
   private Rectangle bounds;
   @JsonIgnore
   private List<GraphColumnInfo> cols;
   private boolean baseTable;
   private String outgoingAliasSource;
}