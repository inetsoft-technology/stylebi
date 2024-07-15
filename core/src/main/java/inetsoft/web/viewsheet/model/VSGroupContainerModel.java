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
package inetsoft.web.viewsheet.model;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.GroupContainerVSAssembly;
import inetsoft.uql.viewsheet.internal.GroupContainerVSAssemblyInfo;
import org.springframework.stereotype.Component;

public class VSGroupContainerModel extends VSObjectModel<GroupContainerVSAssembly> {
   public VSGroupContainerModel(GroupContainerVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      GroupContainerVSAssemblyInfo assemblyInfo =
         (GroupContainerVSAssemblyInfo) assembly.getVSAssemblyInfo();
      noImageFlag = assemblyInfo.getBackgroundImage() == null;
      imageAlpha = assemblyInfo.getImageAlpha();
      scaleInfo = ScaleInfoModel.of(assemblyInfo.isMaintainAspectRatio(),
                                    assemblyInfo.isTile(),
                                    assemblyInfo.isScaleImage());
   }

   public boolean getNoImageFlag() {
      return noImageFlag;
   }

   public String getImageAlpha() {
      return imageAlpha;
   }

   public ScaleInfoModel getScaleInfo() {
      return scaleInfo;
   }

   @Component
   public static final class VSGaugeModelFactory
      extends VSObjectModelFactory<GroupContainerVSAssembly, VSGroupContainerModel>
   {
      public VSGaugeModelFactory() {
         super(GroupContainerVSAssembly.class);
      }

      @Override
      public VSGroupContainerModel createModel(GroupContainerVSAssembly assembly,
                                               RuntimeViewsheet rvs)
      {
         return new VSGroupContainerModel(assembly, rvs);
      }
   }

   private boolean noImageFlag;
   private String imageAlpha;
   private final ScaleInfoModel scaleInfo;
}
