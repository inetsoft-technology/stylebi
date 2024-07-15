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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import org.springframework.stereotype.Component;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSTabModel extends VSObjectModel<TabVSAssembly> {
   public VSTabModel(TabVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);

      TabVSAssemblyInfo info = (TabVSAssemblyInfo) assembly.getVSAssemblyInfo().clone();

      if(rvs.isRuntime()) {
         VSUtil.fixSelected(info, rvs.getViewsheet());
      }

      String[] labels0 = info.getLabels();
      String[] children = info.getAssemblies();
      String[] labels = new String[children.length];

      for(int i = 0; i < labels.length; i++) {
         if(i >= labels0.length || labels0[i] == null || "".equals(labels0[i])) {
            labels[i] = children[i];
         }
         else {
            labels[i] = labels0[i];
         }
      }

      // if no labels are set, use assembly names as labels
      if(labels.length == 0) {
         labels = new String[children.length];
         System.arraycopy(children, 0, labels, 0, children.length);
      }

      if(children != null) {
         this.childrenNames = new String[children.length];
         System.arraycopy(children, 0, this.childrenNames, 0, children.length);
      }

      this.labels = new String[labels.length];
      System.arraycopy(labels, 0, this.labels, 0, labels.length);

      this.selected = info.getSelected();

      FormatInfo fmtInfo = info.getFormatInfo();
      VSCompositeFormat compositeFormat = fmtInfo.getFormat(
         TabVSAssemblyInfo.ACTIVE_TAB_PATH, false);
      activeFormat = new VSFormatModel(compositeFormat, info);

      roundTopCornersOnly = info.isRoundTopCornersOnly();
   }

   public String[] getLabels() {
      return labels;
   }

   public String[] getChildrenNames() {
      return childrenNames;
   }

   public String getSelected() {
      return selected;
   }

   public VSFormatModel getActiveFormat() {
      return activeFormat;
   }

   public boolean isRoundTopCornersOnly() {
      return roundTopCornersOnly;
   }

   public void setRoundTopCornersOnly(boolean roundTopCornersOnly) {
      this.roundTopCornersOnly = roundTopCornersOnly;
   }

   private String[] labels;
   private String[] childrenNames;
   private String selected;
   private VSFormatModel activeFormat;
   private boolean roundTopCornersOnly;

   @Component
   public static final class VSTabModelFactory
      extends VSObjectModelFactory<TabVSAssembly, VSTabModel>
   {
      public VSTabModelFactory() {
         super(TabVSAssembly.class);
      }

      @Override
      public VSTabModel createModel(TabVSAssembly assembly, RuntimeViewsheet rvs) {
         return new VSTabModel(assembly, rvs);
      }
   }
}
