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

import javax.annotation.Nullable;

public class DatabaseTreeNode {
   public String getPath() {
      return path;
   }

   public void setPath(String path) {
      this.path = path;
   }

   public String getParr() {
      return parr;
   }

   @Nullable
   public void setParr(String parr) {
      this.parr = parr;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public String getCatalog() {
      return catalog;
   }

   @Nullable
   public void setCatalog(String catalog) {
      this.catalog = catalog;
   }

   public String getSchema() {
      return schema;
   }

   @Nullable
   public void setSchema(String schema) {
      this.schema = schema;
   }

   public String getQualifiedName() {
      return qualifiedName;
   }

   @Nullable
   public void setQualifiedName(String qualifiedName) {
      this.qualifiedName = qualifiedName;
   }

   public String getEntity() {
      return entity;
   }

   @Nullable
   public void setEntity(String entity) {
      this.entity = entity;
   }

   public String getAttribute() {
      return attribute;
   }

   @Nullable
   public void setAttribute(String attribute) {
      this.attribute = attribute;
   }

   public String getDatabase() {
      return database;
   }

   @Nullable
   public void setDatabase(String database) {
      this.database = database;
   }

   public String getPhysicalView() {
      return physicalView;
   }

   @Nullable
   public void setPhysicalView(String physicalView) {
      this.physicalView = physicalView;
   }

   public boolean isSupportCatalog() {
      return supportCatalog;
   }

   public void setSupportCatalog(boolean supportCatalog) {
      this.supportCatalog = supportCatalog;
   }

   @Override
   public String toString() {
      return "DatabaseTreeNode{" +
         "path='" + path + '\'' +
         ", name='" + name + '\'' +
         ", type='" + type + '\'' +
         ", parr='" + parr + '\'' +
         ", catalog='" + catalog + '\'' +
         ", schema='" + schema + '\'' +
         ", qualifiedName='" + qualifiedName + '\'' +
         ", entity='" + entity + '\'' +
         ", attribute='" + attribute + '\'' +
         ", database='" + database + '\'' +
         ", physicalView='" + physicalView + '\'' +
         ", supportCatalog=" + supportCatalog +
         '}';
   }

   private String path;
   private String parr;
   private String name;
   private String type;
   private String catalog;
   private String schema;
   private String qualifiedName;
   private String entity;
   private String attribute;
   private String database;
   private String physicalView;
   private boolean supportCatalog;
}
