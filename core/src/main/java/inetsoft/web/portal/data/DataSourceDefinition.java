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
package inetsoft.web.portal.data;

import inetsoft.uql.tabular.TabularView;

import java.util.List;

public class DataSourceDefinition extends BaseDataSourceDefinition{
   public String getParentDataSource() {
      return parentDataSource;
   }

   public void setParentDataSource(String parentDataSource) {
      this.parentDataSource = parentDataSource;
   }

   /**
    * Gets the type of data source.
    *
    * @return the data source type.
    */
   public String getType() {
      return type;
   }

   /**
    * Sets the type of data source.
    *
    * @param type the data source type.
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Gets the flag that indicates if the current user is allowed to delete or
    * rename this data source connection.
    *
    * @return <tt>true</tt> if deletable; <tt>false</tt> otherwise.
    */
   public boolean isDeletable() {
      return deletable;
   }

   /**
    * Sets the flag that indicates if the current user is allowed to delete or
    * rename this data source connection.
    *
    * @param deletable <tt>true</tt> if deletable; <tt>false</tt> otherwise.
    */
   public void setDeletable(boolean deletable) {
      this.deletable = deletable;
   }

   /**
    * Gets the tabular view of this data source
    *
    * @return the tabular view
    */
   public TabularView getTabularView() {
      return tabularView;
   }

   /**
    * Sets the tabular view of this data source
    *
    * @param tabularView the tabular view
    */
   public void setTabularView(TabularView tabularView) {
      this.tabularView = tabularView;
   }

   public int getSequenceNumber() {
      return sequenceNumber;
   }

   public void setSequenceNumber(int sequenceNumber) {
      this.sequenceNumber = sequenceNumber;
   }

   public List<DataSourceDefinition> getAdditionalConnections() {
      return additionalConnections;
   }

   public void setAdditionalConnections(List<DataSourceDefinition> additionalConnections) {
      this.additionalConnections = additionalConnections;
   }

   private String parentDataSource;
   private String type;
   private boolean deletable;
   private TabularView tabularView;
   private int sequenceNumber;
   private List<DataSourceDefinition> additionalConnections;
}