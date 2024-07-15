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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CurrentSelectionVSAssemblyInfo;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSSelectionContainerModel
   extends VSCompositeModel<CurrentSelectionVSAssembly>
{
   public VSSelectionContainerModel(CurrentSelectionVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      CurrentSelectionVSAssemblyInfo assemblyInfo =
        (CurrentSelectionVSAssemblyInfo) assembly.getVSAssemblyInfo();

      titleRatio = assemblyInfo.getTitleRatio();
      titleRatio = Double.isNaN(titleRatio) ? 0.5 : titleRatio;
      title = assemblyInfo.getTitle();
      supportRemoveChild = assemblyInfo.isAdhocEnabled();

      if(assemblyInfo.isShowCurrentSelection()) {
         String[] outTitles = assemblyInfo.getOutSelectionTitles();
         String[] outValues = assemblyInfo.getOutSelectionValues();
         String[] outNames = assemblyInfo.getOutSelectionNames();
         outerSelections = new OuterSelection[outTitles.length];

         for(int i = 0; i < outerSelections.length; i++) {
            outerSelections[i] = new OuterSelection(outTitles[i], outValues[i], outNames[i]);
         }
      }
      else {
         outerSelections = new OuterSelection[0];
      }

      String[] children = assemblyInfo.getAbsoluteAssemblies();

      if(children != null) {
         Viewsheet vs = assembly.getViewsheet();

         while(vs.isEmbedded() && vs.getViewsheet() != null) {
            vs = vs.getViewsheet();
         }

         List<String> childrenList = new ArrayList<>();
         List<VSObjectModel> childObjectList = new ArrayList<>();

         for(String child : children) {
            Assembly aobj = vs.getAssembly(child);

            if(aobj == null) {
               continue;
            }

            childrenList.add(child);

            if(aobj instanceof TimeSliderVSAssembly) {
               childObjectList.add(new VSRangeSliderModel((TimeSliderVSAssembly) aobj, rvs));
            }
            else if(aobj instanceof SelectionListVSAssembly) {
               childObjectList.add(new VSSelectionListModel((SelectionListVSAssembly) aobj, rvs));
            }
            else if(aobj instanceof SelectionTreeVSAssembly) {
               childObjectList.add(new VSSelectionTreeModel((SelectionTreeVSAssembly) aobj, rvs));
            }
         }

         this.childrenNames = childrenList.toArray(new String[0]);
         this.vsobjects = childObjectList.toArray(new VSObjectModel[0]);

         final Dimension maxSize = assemblyInfo.getMaxSize();

         if(maxSize != null) {
            final VSFormatModel titleFormat = getTitleFormat();
            final VSFormatModel objectFormat = getObjectFormat();

            renderMaxMode(maxSize, assemblyInfo.getMaxModeZIndex(), titleFormat, objectFormat);
            this.maxMode = true;
         }
      }
   }

   protected void renderMaxMode(Dimension maxSize,
                                int zIndex,
                                VSFormatModel titleFormat,
                                VSFormatModel objectFormat)
   {
      double width = Math.max(objectFormat.getWidth(), maxSize.getWidth());

      setMaxModeLayout(titleFormat, objectFormat, zIndex, maxSize.height, width);
   }

   protected void setMaxModeLayout(VSFormatModel titleFormat, VSFormatModel objectFormat,
                                   int zIndex, int fullHeight, double width)
   {
      objectFormat.setLeft(0);
      objectFormat.setTop(0);
      objectFormat.setWidth(width);
      titleFormat.setWidth(width);
      objectFormat.setHeight(fullHeight);
      objectFormat.setzIndex(zIndex);
   }

   @Override
   public String getTitle() {
      return title;
   }

   public double getTitleRatio() {
      return titleRatio;
   }

   public int getDataRowHeight() {
      return dataRowHeight;
   }

   public OuterSelection[] getOuterSelections() {
      return outerSelections;
   }

   public VSObjectModel[] getChildObjects() {
      return vsobjects;
   }

   public String[] getChildrenNames() {
      return childrenNames;
   }

   public boolean isSupportRemoveChild() {
      return supportRemoveChild;
   }

   public void setSupportRemoveChild(boolean supportRemoveChild) {
      this.supportRemoveChild = supportRemoveChild;
   }

   public boolean isMaxMode() {
      return maxMode;
   }

   public void setMaxMode(boolean maxMode) {
      this.maxMode = maxMode;
   }

   @Override
   public String toString() {
      return "{" + super.toString() + " " +
         "title:" + title + " " +
         "titleRatio:" + titleRatio + " " +
         "supportRemoveChild:" + supportRemoveChild + " " +
         "outerSelections:" + java.util.Arrays.toString(outerSelections) + " " +
         "dataRowHeight:" + dataRowHeight + " " +
         "childrenNames:" + java.util.Arrays.toString(childrenNames) + " " +
         "vsobjects:" + java.util.Arrays.toString(vsobjects) + "}";
   }

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class OuterSelection {
      public OuterSelection(String title, String value, String name) {
         this.title = title;
         this.value = value;
         this.name = name;
      }

      public String getTitle() {
         return title;
      }

      public String getValue() {
         return value;
      }

      public String getName() {
         return name;
      }

      @Override
      public String toString() {
         return "{ title:" + title + " "+
            "value:" + value + " "+
            "name:" + name + "} ";
      }

      private String title;
      private String value;
      private String name;
   }

   private String title;
   private double titleRatio;
   private OuterSelection[] outerSelections;
   private int dataRowHeight = 18;
   private VSObjectModel[] vsobjects;
   private String[] childrenNames;
   private boolean supportRemoveChild;

   private boolean maxMode;

   @Component
   public static final class VSSelectionContainerModelFactory
      extends VSObjectModelFactory<CurrentSelectionVSAssembly, VSSelectionContainerModel>
   {
      public VSSelectionContainerModelFactory() {
         super(CurrentSelectionVSAssembly.class);
      }

      @Override
      public VSSelectionContainerModel createModel(CurrentSelectionVSAssembly assembly,
                                                   RuntimeViewsheet rvs)
      {
         return new VSSelectionContainerModel(assembly, rvs);
      }
   }
}
