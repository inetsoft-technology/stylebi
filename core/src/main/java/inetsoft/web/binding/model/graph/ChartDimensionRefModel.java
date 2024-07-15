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
package inetsoft.web.binding.model.graph;

import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.NamedGroupInfoModel;

public class ChartDimensionRefModel extends BDimensionRefModel implements ChartRefModel {
   /**
    * Constructor
    */
   public ChartDimensionRefModel() {
   }

   /**
    * Constructor
    */
   public ChartDimensionRefModel(ChartDimensionRef ref, ChartInfo cinfo) {
      super(ref);
      setOtherEnable(!GraphUtil.isOuterDimRef(ref, cinfo));
      XNamedGroupInfo groupInfo = ref.getNamedGroupInfo();

      if(groupInfo != null) {
         setNamedGroupInfo(new NamedGroupInfoModel(groupInfo));
      }
   }

   /**
    * Set the original descriptor.
    */
   @Override
   public void setOriginal(OriginalDescriptor original) {
      refModelImpl.setOriginal(original);
   }

   /**
    * Get the original descriptor.
    */
   @Override
   public OriginalDescriptor getOriginal() {
      return refModelImpl.getOriginal();
   }

   /**
    * Set the specified info supports inverted chart.
    */
   @Override
   public void setRefConvertEnabled(boolean refConvertEnabled) {
      refModelImpl.setRefConvertEnabled(refConvertEnabled);
   }

   /**
    * Check if the specified data info supports inverted chart.
    */
   @Override
   public boolean isRefConvertEnabled() {
      return refModelImpl.isRefConvertEnabled();
   }

   /**
    * Set this ref is treat as dimension or measure.
    */
   @Override
   public void setMeasure(boolean measure) {
   }

   /**
    * Check if this info is treat as dimension or measure.
    */
   @Override
   public boolean isMeasure() {
      return false;
   }

   /**
    * Check if the group others enabled.
    */
   public boolean isOtherEnable() {
      return otherEnable;
   }

   /**
    * Set is group others or not for chart.
    */
   public void setOtherEnable(boolean otherEnable) {
      this.otherEnable = otherEnable;
   }

   /**
    * Create a chartRef depend on chart info.
    */
   @Override
   public ChartRef createChartRef(ChartInfo cinfo) {
      return new VSChartDimensionRef();
   }

   private ChartRefModelImpl refModelImpl = new ChartRefModelImpl();
   private boolean otherEnable;
}
