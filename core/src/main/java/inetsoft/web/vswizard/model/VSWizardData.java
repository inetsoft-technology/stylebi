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
package inetsoft.web.vswizard.model;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;

public class VSWizardData {
   public VSWizardData() {
   }

   public VSWizardData(AssetEntry[] selectedEntries, VSTemporaryInfo vsTemporaryInfo) {
      this.selectedEntries = selectedEntries;
      this.vsTemporaryInfo = vsTemporaryInfo;
   }

   public AssetEntry[] getSelectedEntries() {
      return selectedEntries;
   }

   public void setSelectedEntries(AssetEntry[] selectedEntries) {
      this.selectedEntries = selectedEntries;
   }

   public VSTemporaryInfo getVsTemporaryInfo() {
      return vsTemporaryInfo;
   }

   public void setVsTemporaryInfo(VSTemporaryInfo vsTemporaryInfo) {
      this.vsTemporaryInfo = vsTemporaryInfo;
   }

   private AssetEntry[] selectedEntries;
   private VSTemporaryInfo vsTemporaryInfo;
}
