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
package inetsoft.web.vswizard.model;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.chart.ChartPreference;

public class VSWizardData {
   public VSWizardData() {
   }

   public VSWizardData(AssetEntry[] selectedEntries, VSTemporaryInfo vsTemporaryInfo) {
      this.selectedEntries = selectedEntries;
      this.vsTemporaryInfo = vsTemporaryInfo;
   }

   public VSWizardData(AssetEntry[] selectedEntries, VSTemporaryInfo vsTemporaryInfo,
                       ChartPreference preferenceInfo)
   {
      this.selectedEntries = selectedEntries;
      this.vsTemporaryInfo = vsTemporaryInfo;
      this.preferenceInfo = preferenceInfo;
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

   public ChartPreference getPreferenceInfo() {
      return preferenceInfo;
   }

   public void setPreferenceInfo(ChartPreference preferenceInfo) {
      this.preferenceInfo = preferenceInfo;
   }

   private AssetEntry[] selectedEntries;
   private VSTemporaryInfo vsTemporaryInfo;
   private ChartPreference preferenceInfo;
}
