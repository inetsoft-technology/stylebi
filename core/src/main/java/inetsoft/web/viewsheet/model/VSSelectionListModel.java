/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.Common;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.springframework.stereotype.Component;

import java.awt.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSSelectionListModel extends VSSelectionBaseModel<SelectionListVSAssembly> {
   public VSSelectionListModel(SelectionListVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      SelectionListVSAssemblyInfo sinfo = (SelectionListVSAssemblyInfo)assembly.getInfo();
      SelectionList slist = assembly.getSelectionList();
      String search = getSearchString();
      slist = search != null && search.length() > 0 && slist != null ?
         slist.findAll(search, false) : slist;
      FormatInfo finfo = sinfo.getFormatInfo();

      if(slist != null) {
         for(SelectionValue svalue : slist.getSelectionValues()) {
            if(svalue.getFormat() == null) {
               svalue.setFormat(finfo.getFormat(DETAIL));
            }
         }
      }

      selectionList = new SelectionListModel(slist, sinfo, 0);
      adhocFilter = sinfo.isAdhocFilter();
      numCols = sinfo.getColumnCount();
      renderSizeWithParentMaxMode(assembly, assembly.getContainer(), rvs.getViewsheet());

      VSAssembly container = assembly.getContainer();

      if(container != null && container instanceof CurrentSelectionVSAssembly) {
         VSFormatModel objectFormat = this.getObjectFormat();
         int width = (int) objectFormat.getWidth();
         int height = (int) objectFormat.getHeight();
         final TableDataPath path = new TableDataPath(-1, TableDataPath.OBJECT);
         final VSCompositeFormat format = container.getFormatInfo().getFormat(path);
         final Insets borders = format.getBorders();

         // when a selection list is the child of a selection container its pushed over by the
         // borders
         if(borders != null) {
            width -= Common.getLineWidth(borders.left);
            width -= Common.getLineWidth(borders.right);
         }

         Dimension objSize = new Dimension(width, height);
         objectFormat.setSize(objSize);

         CurrentSelectionVSAssemblyInfo containerInfo =
            ((CurrentSelectionVSAssemblyInfo) container.getVSAssemblyInfo());
         double titleRatio = containerInfo.getTitleRatio();
         titleRatio = Double.isNaN(titleRatio) ? 0.5 : titleRatio;
         this.setTitleRatio(titleRatio);
         supportRemoveChild = containerInfo.isAdhocEnabled();
      }
   }

   protected void renderSizeWithParentMaxMode(SelectionListVSAssembly selection,
                                              VSAssembly objContainer, Viewsheet vs)
   {
      if(objContainer instanceof CurrentSelectionVSAssembly) {
         MaxModeSupportAssemblyInfo maxModeInfo =
            (CurrentSelectionVSAssemblyInfo) objContainer.getVSAssemblyInfo();
         Dimension containerMaxSize = maxModeInfo.getMaxSize();

         if(containerMaxSize != null) {
            final VSFormatModel titleFormat = getTitleFormat();
            final VSFormatModel objectFormat = getObjectFormat();
            double width = Math.max(objectFormat.getWidth(), containerMaxSize.getWidth());
            objectFormat.setWidth(width);
            titleFormat.setWidth(width);
            objectFormat.setHeight(
               getHeightInMaxModeContainer(selection, (CurrentSelectionVSAssembly) objContainer,
                  vs));
         }
      }
   }

   private double getHeightInMaxModeContainer(SelectionListVSAssembly selection,
                                              CurrentSelectionVSAssembly objContainer, Viewsheet vs)
   {
      Dimension maxSize = objContainer.getMaxModeInfo().getMaxSize();
      String[] assemblies = objContainer.getAssemblies();
      double height = maxSize.getHeight();

      if(objContainer.getVSAssemblyInfo() instanceof TitledVSAssemblyInfo) {
         height -= ((TitledVSAssemblyInfo) objContainer.getVSAssemblyInfo()).getTitleHeight();
      }

      if(assemblies == null) {
         return height;
      }

      for(String assemblyName : assemblies) {
         VSAssembly assembly = vs.getAssembly(assemblyName);

         if(assembly == null) {
            continue;
         }

         if(Tool.equals(assembly.getAbsoluteName(), getAbsoluteName())) {
            break;
         }

         VSAssemblyInfo assemblyInfo = assembly.getVSAssemblyInfo();
         double objectHeight = -1;

         if(assemblyInfo instanceof TitledVSAssemblyInfo) {
            objectHeight = ((TitledVSAssemblyInfo) assemblyInfo).getTitleHeight();
         }

         if(objectHeight > 0) {
            height -= objectHeight;
         }
      }

      SelectionListVSAssemblyInfo selectionListInfo = selection.getSelectionListInfo();
      double valueMaxHeight = 0;
      valueMaxHeight += selectionListInfo.getTitleHeight();
      SelectionList selectionListValue = selectionListInfo.getSelectionList();

      if(selectionListValue != null) {
         valueMaxHeight +=
            selectionListValue.getSelectionValueCount() * selectionListInfo.getCellHeight();
      }

      return Math.min(height, valueMaxHeight);
   }

   public SelectionListModel getSelectionList() {
      return selectionList;
   }

   public boolean isSupportRemoveChild() {
      return supportRemoveChild;
   }

   public boolean isAdhocFilter() {
      return adhocFilter;
   }

   public int getNumCols() {
      return numCols;
   }

   @Override
   public String toString() {
      return "{"+ super.toString() + " " +
         "selectionList: " + selectionList + " "+
         "adhocFilter:" + adhocFilter + " "+
         "supportRemoveChild:" + supportRemoveChild + "} ";
   }

   private static final TableDataPath DETAIL = new TableDataPath(-1, TableDataPath.DETAIL);
   private SelectionListModel selectionList;
   private boolean supportRemoveChild;
   private boolean adhocFilter;
   private int numCols;

   @Component
   public static final class VSSelectionListModelFactory
      extends VSObjectModelFactory<SelectionListVSAssembly, VSSelectionListModel>
   {
      public VSSelectionListModelFactory() {
         super(SelectionListVSAssembly.class);
      }

      @Override
      public VSSelectionListModel createModel(SelectionListVSAssembly assembly,
                                              RuntimeViewsheet rvs)
      {
         return new VSSelectionListModel(assembly, rvs);
      }
   }
}
