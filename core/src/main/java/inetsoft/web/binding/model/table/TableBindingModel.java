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
package inetsoft.web.binding.model.table;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.EmbeddedTableVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;

import java.util.ArrayList;
import java.util.List;

public class TableBindingModel extends BaseTableBindingModel {
   public TableBindingModel() {
   }

   public TableBindingModel(DataRefModelFactoryService refModelService,
      TableVSAssembly assembly)
   {
      setType("table");
      setEmbedded(assembly instanceof EmbeddedTableVSAssembly);
      ColumnSelection cols = assembly.getColumnSelection();

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) cols.getAttribute(i).clone();
         col.setApplyingAlias(false);
         ColumnRefModel detail = (ColumnRefModel) refModelService.createDataRefModel(col);
         fixVSOrder(assembly, col, detail);
         addDetail(detail);
      }
   }

   private void fixVSOrder(TableVSAssembly assembly, ColumnRef col, ColumnRefModel ref) {
      SortInfo sinfo = assembly.getSortInfo();

      if(sinfo != null) {
         SortRef sort = sinfo.getSort(col);

         if(sort != null) {
            ref.setOrder(sort.getOrder());
         }
      }
   }

   /**
    * Set embedded type.
    * @param is embedded type.
    */
   public void setEmbedded(boolean embedded) {
      this.embedded = embedded;
   }

   /**
    * is embedded.
    * @return embedded type.
    */
   public boolean getEmbedded() {
      return embedded;
   }

   /**
    * Set table option.
    * @param option the table option.
    */
   public void setOption(TableOptionInfo option) {
      this.option = option;
   }

   /**
    * Get the table option.
    * @return table option.
    */
   public TableOptionInfo getOption() {
      return option;
   }

   /**
    * Add table group.
    * @param group the table group.
    */
   public void addGroup(BDimensionRefModel group) {
      groups.add(group);
   }

   /**
    * Get the table groups.
    * @return table groups.
    */
   public List<BDimensionRefModel> getGroups() {
      return groups;
   }

   /**
    * Set table groups.
    * @param groups the table groups.
    */
   public void setGroups(List<BDimensionRefModel> groups) {
      this.groups = groups;
   }

   /**
    * Add table detail.
    * @param detail the table detail.
    */
   public void addDetail(DataRefModel detail) {
      details.add(detail);
   }

   /**
    * Get the table details.
    * @return table details.
    */
   public List<DataRefModel> getDetails() {
      return details;
   }

   /**
    * Set table details.
    * @param details the table details.
    */
   public void setDetails(List<DataRefModel> details) {
      this.details = details;
   }

	private boolean embedded = false;
   private TableOptionInfo option = null;
   private List<BDimensionRefModel> groups = new ArrayList<>();
   private List<DataRefModel> details = new ArrayList<>();
}
