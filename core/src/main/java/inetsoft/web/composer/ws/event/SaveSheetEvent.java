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
package inetsoft.web.composer.ws.event;

import javax.annotation.Nullable;

public class SaveSheetEvent implements AssetEvent {
  /**
    * @return the assembly name
    */
   @Nullable
   public String name() {
      return null;
   }

   /**
    * Get the flag that indicates if the save should be forced.
    */
   public boolean isForceSave() {
      return this.forceSave;
   }

   /**
    * Get the flag that indicates if the user has confirmed generate
    * a new materialized view for the viewsheets which using current worksheet.
    *
    * @return <tt>true</tt> if confirmed; <tt>false</tt> otherwise.
    */
   public boolean confirmed() {
      return confirmed;
   }

   /**
    * Set the flag that indicates if the user has confirmed generate
    * a new materialized view for the viewsheets which using current worksheet.
    *
    * @param confirmed <tt>true</tt> if confirmed; <tt>false</tt> otherwise.
    */
   public void setConfirmed(boolean confirmed) {
      this.confirmed = confirmed;
   }

   /**
    * Get if need close after save.
    */
   public boolean isClose() {
      return this.close;
   }

   /**
    * Set if need close after save action.
    */
   public void setClose(boolean close) {
      this.close = close;
   }

   public int getWizardGridRows() {
      return wizardGridRows;
   }

   @Nullable
   public void setWizardGridRows(int wizardGridRows) {
      this.wizardGridRows = wizardGridRows;
   }

   public boolean isNewViewsheetWizard() {
      return newViewsheetWizard;
   }

   @Nullable
   public void setNewViewsheetWizard(boolean newViewsheetWizard) {
      this.newViewsheetWizard = newViewsheetWizard;
   }

   public boolean isUpdateDepend() {
      return updateDepend;
   }

   @Nullable
   public void setUpdateDepend(boolean updateDepend) {
      this.updateDepend = updateDepend;
   }

   private boolean forceSave;
   private boolean confirmed;
   private boolean close;
   private int wizardGridRows;
   private boolean newViewsheetWizard;
   private boolean updateDepend;
}
