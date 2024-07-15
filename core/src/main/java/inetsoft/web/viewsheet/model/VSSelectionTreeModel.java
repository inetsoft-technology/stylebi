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
import inetsoft.uql.viewsheet.CompositeSelectionValue;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import org.springframework.stereotype.Component;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSSelectionTreeModel extends VSSelectionBaseModel<SelectionTreeVSAssembly> {
   public VSSelectionTreeModel(SelectionTreeVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      SelectionTreeVSAssemblyInfo assemblyInfo =
        (SelectionTreeVSAssemblyInfo) assembly.getVSAssemblyInfo();
      CompositeSelectionValue cvalue = assembly.getCompositeSelectionValue();
      String search = getSearchString();
      cvalue = search != null && search.length() > 0 && cvalue != null ?
         cvalue.findAll(search, false) : cvalue;

      root = new CompositeSelectionValueModel(cvalue, null, assemblyInfo, null, false);
      mode = assembly.getMode();
      selectChildren = assemblyInfo.isSelectChildren();
      expandAll = assemblyInfo.isExpandAll();
      singleSelectionLevels = assemblyInfo.getSingleSelectionLevels();
      levels = assemblyInfo.getDataRefs().length;
   }

   public CompositeSelectionValueModel getRoot() {
      return root;
   }

   public int getMode() {
      return mode;
   }

   public boolean isSelectChildren() {
      return selectChildren;
   }

   public boolean isExpandAll() { return expandAll; }

   public List<Integer> getSingleSelectionLevels() {
      return singleSelectionLevels;
   }

   public int getLevels() { return levels; }

   @Override
   public String toString() {
      return "{" + super.toString() + " " +
         "root:" + root  + " " +
         "mode:" + mode + " " +
         "selectChildren:" + selectChildren + " " +
         "expandAll:" + expandAll + " " +
         "levels:" + levels + "}";
   }

   private CompositeSelectionValueModel root;
   private int mode;
   private boolean selectChildren;
   private boolean expandAll;
   private List<Integer> singleSelectionLevels;
   private int levels;


   @Component
   public static final class VSSelectionTreeModelFactory
      extends VSObjectModelFactory<SelectionTreeVSAssembly, VSSelectionTreeModel>
   {
      public VSSelectionTreeModelFactory() {
         super(SelectionTreeVSAssembly.class);
      }

      @Override
      public VSSelectionTreeModel createModel(SelectionTreeVSAssembly assembly,
                                              RuntimeViewsheet rvs)
      {
         return new VSSelectionTreeModel(assembly, rvs);
      }
   }
}
