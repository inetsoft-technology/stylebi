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
package inetsoft.web.composer.model.ws;


import com.fasterxml.jackson.annotation.JsonInclude;
import inetsoft.report.composition.RuntimeWorksheet;

/**
 * Class that represents a basic worksheet.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorksheetModel {
   public WorksheetModel() {}

   public WorksheetModel(RuntimeWorksheet rws) {
      this.setId(rws.getEntry().toIdentifier());
      this.setRuntimeId(runtimeId);
      this.setLabel(rws.getEntry().getName());
      this.setType("worksheet");
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

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public String getRuntimeId() {
      return runtimeId;
   }

   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   public boolean isNewSheet() {
      return newSheet;
   }

   public void setNewSheet(boolean newSheet) {
      this.newSheet = newSheet;
   }

   public boolean getInit() {
      return init;
   }

   public void setInit(boolean init) {
      this.init = init;
   }

   public boolean getNewSheet() {
      return newSheet;
   }

   public int getCurrent() {
      return current;
   }

   public void setCurrent(int current) {
      this.current = current;
   }

   public int getSavePoint() {
      return savePoint;
   }

   public void setSavePoint(int savePoint) {
      this.savePoint = savePoint;
   }

   public boolean isSingleQuery() {
      return singleQuery;
   }

   public void setSingleQuery(boolean singleQuery) {
      this.singleQuery = singleQuery;
   }

   private String id;
   private String label;
   private String type;
   private String runtimeId;
   private boolean newSheet;
   private boolean init;
   private int current;
   private int savePoint;
   private boolean singleQuery;
}
