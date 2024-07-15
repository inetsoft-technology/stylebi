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
import org.springframework.util.StringUtils;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphModel {

   public GraphColumnInfo findColumnInfo(String columnName) {
      if(cols == null || StringUtils.isEmpty(columnName)) {
         return null;
      }

      for(GraphColumnInfo col : cols) {
         if(columnName.equals(col.getName())) {
            return col;
         }
      }

      return null;
   }

   public GraphNodeModel getNode() {
      return node;
   }

   public void setNode(GraphNodeModel node) {
      this.node = node;
   }

   public List<GraphColumnInfo> getCols() {
      return cols;
   }

   public void setCols(List<GraphColumnInfo> cols) {
      this.cols = cols;
   }

   public Rectangle getBounds() {
      return bounds;
   }

   public void setBounds(Rectangle bounds) {
      this.bounds = bounds;
   }

   public boolean isShowColumns() {
      return showColumns;
   }

   public void setShowColumns(boolean showColumns) {
      this.showColumns = showColumns;
   }

   public GraphEdgeModel getEdge() {
      return edge;
   }

   public void setEdge(GraphEdgeModel edge) {
      this.edge = edge;
   }

   public boolean isAlias() {
      return alias;
   }

   public void setAlias(boolean alias) {
      this.alias = alias;
   }

   public boolean isAutoAlias() {
      return autoAlias;
   }

   public void setAutoAlias(boolean autoAlias) {
      this.autoAlias = autoAlias;
   }

   public boolean isSql() {
      return sql;
   }

   public void setSql(boolean sql) {
      this.sql = sql;
   }

   public boolean isBaseTable() {
      return baseTable;
   }

   public void setBaseTable(boolean baseTable) {
      this.baseTable = baseTable;
   }

   public boolean isAutoAliasByOutgoing() {
      return autoAliasByOutgoing;
   }

   public void setAutoAliasByOutgoing(boolean autoAliasByOutgoing) {
      this.autoAliasByOutgoing = autoAliasByOutgoing;
   }

   public boolean isDesignModeAlias() {
      return designModeAlias;
   }

   public void setDesignModeAlias(boolean designModeAlias) {
      this.designModeAlias = designModeAlias;
   }

   public String getOutgoingAutoAliasSource() {
      return outgoingAutoAliasSource;
   }

   public void setOutgoingAutoAliasSource(String outgoingAutoAliasSource) {
      this.outgoingAutoAliasSource = outgoingAutoAliasSource;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      }

      if (o == null || getClass() != o.getClass()) {
         return false;
      }

      GraphModel that = (GraphModel) o;

      return node.equals(that.node);
   }

   @Override
   public int hashCode() {
      return Objects.hash(node);
   }

   private GraphNodeModel node;
   private GraphEdgeModel edge;
   private List<GraphColumnInfo> cols;
   private Rectangle bounds;
   private boolean showColumns;
   private boolean alias;
   private boolean autoAlias;
   private boolean sql;
   private boolean baseTable;
   private boolean autoAliasByOutgoing;
   private boolean designModeAlias;
   @Nullable
   private String outgoingAutoAliasSource; // source table name of out going auto alias.
}
