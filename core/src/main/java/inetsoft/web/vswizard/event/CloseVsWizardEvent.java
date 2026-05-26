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

package inetsoft.web.vswizard.event;

import java.io.Serializable;

public class CloseVsWizardEvent implements Serializable {
   public void setEditMode(String editMode) {
       this.editMode = editMode;
   }

   public String getEditMode() {
       return editMode;
   }

   public void setAssemblyName(String assemblyName) {
       this.assemblyName = assemblyName;
   }

   public String getAssemblyName() {
       return assemblyName;
   }

   public void setWizardGridRows(int wizardGridRows) {
       this.wizardGridRows = wizardGridRows;
   }

   /**
    * The grid row count in wizard grid pane which will be used to insert space
    * when new viewsheet by wizard and click finish to viewsheet pane.
    */
   public int getWizardGridRows() {
       return wizardGridRows;
   }

   private String editMode;
   private String assemblyName;
   private int wizardGridRows;
}
