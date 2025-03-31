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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.web.adhoc.model.property.MeasureInfo;
import inetsoft.web.adhoc.model.property.TargetInfo;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartTargetLinesPaneModel implements Serializable {
   public boolean isSupportsTarget() {
      return supportsTarget;
   }

   public void setSupportsTarget(boolean supportsTarget) {
      this.supportsTarget = supportsTarget;
   }

   public TargetInfo[] getChartTargets() {
      return chartTargets;
   }

   public void setChartTargets(TargetInfo[] chartTargets) {
      this.chartTargets = chartTargets;
   }

   public TargetInfo getNewTargetInfo() {
      return newTargetInfo;
   }

   public void setNewTargetInfo(TargetInfo newTargetInfo) {
      this.newTargetInfo = newTargetInfo;
   }

   public MeasureInfo[] getAvailableFields() {
      return availableFields;
   }

   public void setAvailableFields(MeasureInfo[] availableFields) {
      this.availableFields = availableFields;
   }

   public Integer[] getDeletedIndexList() {
      return deletedIndexList;
   }

   public void setDeletedIndexList(Integer[] deletedIndexList) {
      this.deletedIndexList = deletedIndexList;
   }
   
   public boolean isMapInfo() {
      return mapInfo;
   }

   public void setMapInfo(boolean mapInfo) {
      this.mapInfo = mapInfo;
   }
   
   private boolean mapInfo;
   private boolean supportsTarget;
   private TargetInfo[] chartTargets;
   private TargetInfo newTargetInfo;
   private MeasureInfo[] availableFields;
   private Integer[] deletedIndexList = new Integer[0];
}