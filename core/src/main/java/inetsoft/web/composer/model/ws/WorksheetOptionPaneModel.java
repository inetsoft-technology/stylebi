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
package inetsoft.web.composer.model.ws;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.Worksheet;

public class WorksheetOptionPaneModel {

   public WorksheetOptionPaneModel() {}

   public WorksheetOptionPaneModel(RuntimeWorksheet rws) {
      Worksheet ws = rws.getWorksheet();

      this.setName(rws.getEntry().getName());
      this.setAlias(ws.getWorksheetInfo().getAlias() != null ?
                       ws.getWorksheetInfo().getAlias() : rws.getEntry().getAlias());
      this.setDataSource(rws.getEntry().isReportDataSource());
      this.setDescription(ws.getWorksheetInfo().getDescription() != null
                          ? ws.getWorksheetInfo().getDescription()
                          : rws.getEntry().getProperty("description"));
   }

   /**
    * Gets the name of the worksheet.
    *
    * @return the name.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the name of the worksheet.
    *
    * @param name the name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Gets the display name of the worksheet.
    *
    * @return the display name.
    */
   public String getAlias() {
      return alias == null ? "" : alias;
   }

   /**
    * Sets the display name of the worksheet.
    *
    * @param alias the display name.
    */
   public void setAlias(String alias) {
      this.alias = alias;
   }

   /**
    * Gets a description of the worksheet.
    *
    * @return the description.
    */
   public String getDescription() {
      return description;
   }

   /**
    * Sets a description of the worksheet.
    *
    * @param description the description.
    */
   public void setDescription(String description) {
      this.description = description == null ? "" : description;
   }

   public boolean getDataSource() {
      return dataSource;
   }

   public void setDataSource(boolean dataSource) {
      this.dataSource = dataSource;
   }

   @Override
   public String toString() {
      return "WorksheetOptionPaneModel{" +
         ", alias='" + alias + '\'' +
         ", description='" + description + '\'' +
         ", name='" + name + '\'' +
         ", dataSource='" + dataSource + '\'' +
         '}';
   }

   private String alias;
   private String description;
   private String name;
   private boolean dataSource;
}
