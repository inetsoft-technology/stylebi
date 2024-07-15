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

import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.web.vswizard.model.recommender.VSRecommendType;

public class VSWizardOriginalModel {
   public VSWizardOriginalModel() {
   }

   public void setOriginalName(String originalName) {
      this.originalName = originalName;
   }

   public String getOriginalName() {
      return originalName;
   }

   public void setOriginalType(VSRecommendType originalType) {
      this.originalType = originalType;
   }

   public VSRecommendType getOriginalType() {
      return originalType;
   }

   /**
    * get empty assembly
    */
   public boolean isEmptyAssembly() {
      return emptyAssembly;
   }

   /**
    * when the source of assembly is null, set assembly is true.
    */
   public void setEmptyAssembly(boolean emptyAssembly) {
      this.emptyAssembly = emptyAssembly;
   }

   /**
    * get original binding assembly
    */
   public ChartVSAssembly getTempBinding() {
      return tempBinding;
   }

   /**
    * set original binding assembly to keep the binding info for original assembly
    */
   public void setTempBinding(ChartVSAssembly originalBinding) {
      this.tempBinding = originalBinding;
   }

   /**
    * Get the chart info of the (existing chart) this wizard is opened from.
    */
   public ChartInfo getOriginalChartInfo() {
      return origInfo;
   }

   public void setOriginalChartInfo(ChartInfo info) {
      origInfo = info;
   }

   private String originalName;
   private VSRecommendType originalType;
   private boolean emptyAssembly;//the source of assembly is null
   private ChartVSAssembly tempBinding;// to keep the binding information for originalAssembly
   private ChartInfo origInfo;
}
