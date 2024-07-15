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
package inetsoft.web.viewsheet.event;

import javax.annotation.Nullable;

public class CheckMVEvent implements ViewsheetEvent {
   /**
    * Set if wait and prompt user to refresh until background mv is done.
    */
   public void setWaitFor(boolean waitFor) {
      this.waitFor = waitFor;
   }

   /**
    * Get if wait and prompt user to refresh until background mv is done.
    */
   public boolean isWaitFor() {
      return this.waitFor;
   }

   /**
    * Set if run mv in background.
    */
   public void setBackground(boolean background) {
      this.background = background;
   }

   /**
    * Set if refresh the viewsheet directly.
    */
   public boolean isRefreshDirectly() {
      return this.refreshDirectly;
   }

   /**
    * Get if refresh the viewsheet directly.
    */
   public void setRefreshDirectly(boolean refreshDirectly) {
      this.refreshDirectly = refreshDirectly;
   }

   /**
    * Set if run mv in background.
    */
   public boolean isBackground() {
      return this.background;
   }

   /**
    * Gets the asset entry identifier of the viewsheet.
    *
    * @return the entry identifier.
    */
   public String getEntryId() {
      return entryId;
   }

   /**
    * Sets the asset entry identifier of the viewsheet.
    *
    * @param entryId the entry identifier.
    */
   public void setEntryId(String entryId) {
      this.entryId = entryId;
   }

   /**
    * @return the assembly name
    */
   @Nullable
   public String name() {
      return null;
   }

   /**
    * Check if the event was confirmed by a message dialog
    *
    * @return if the event was confirmed
    */
   public boolean confirmed() {
      return this.confirmed;
   }

   public void setConfirmed(boolean confirmed) {
      this.confirmed = confirmed;
   }

   public boolean isConfirmed() {
      return this.confirmed;
   }

   public String toString() {
      return "entryId: " + entryId + " background: " + background +
         " waitFor: " + waitFor + " confirmed: " + confirmed +
         " refreshDirectly: " + refreshDirectly;
   }

   private String entryId;
   private boolean background;
   private boolean waitFor;
   private boolean confirmed;
   private boolean refreshDirectly;
}
