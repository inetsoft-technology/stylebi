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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class XAttributeModel extends ElementModel {
   public XAttributeModel() {
      this.setLeaf(true);
   }

   public String getParentEntity() {
      return parentEntity;
   }

   public void setParentEntity(String parentEntity) {
      this.parentEntity = parentEntity;
   }

   public String getTable() {
      return table;
   }

   public void setTable(String table) {
      this.table = table;
   }

   public String getColumn() {
      return column;
   }

   public void setColumn(String column) {
      this.column = column;
   }

   public String getDataType() {
      return dataType;
   }

   public void setDataType(String dataType) {
      this.dataType = dataType;
   }

   public XFormatInfoModel getFormat() {
      return format;
   }

   public void setFormat(XFormatInfoModel format) {
      this.format = format;
   }

   public AutoDrillInfo getDrillInfo() {
      return drillInfo;
   }

   public void setDrillInfo(AutoDrillInfo drillInfo) {
      this.drillInfo = drillInfo;
   }

   public boolean isBrowseData() {
      return browseData;
   }

   public void setBrowseData(boolean browseData) {
      this.browseData = browseData;
   }

   public String getBrowseQuery() {
      return browseQuery;
   }

   public void setBrowseQuery(String browseQuery) {
      this.browseQuery = browseQuery;
   }

   public boolean isAggregate() {
      return aggregate;
   }

   public void setAggregate(boolean aggregate) {
      this.aggregate = aggregate;
   }

   public boolean isParseable() {
      return parseable;
   }

   public void setParseable(boolean parseable) {
      this.parseable = parseable;
   }

   public RefTypeModel getRefType() {
      return refType;
   }

   public void setRefType(RefTypeModel refType) {
      this.refType = refType;
   }

   public String getQualifiedName() {
      return qualifiedName;
   }

   public void setQualifiedName(String qualifiedName) {
      this.qualifiedName = qualifiedName;
   }

   public String getExpression() {
      return expression;
   }

   public void setExpression(String expression) {
      this.expression = expression;
   }

   public int getDepth() {
      return depth;
   }

   public void setDepth(int depth) {
      this.depth = depth;
   }

   private String parentEntity;
   private String table;
   private String column;
   private String dataType;
   private XFormatInfoModel format = new XFormatInfoModel();
   private AutoDrillInfo drillInfo = new AutoDrillInfo();
   private boolean browseData;
   private boolean aggregate;
   private boolean parseable;
   private String browseQuery;
   private RefTypeModel refType;
   private String qualifiedName;
   private String expression;
   private int depth = 1;
}