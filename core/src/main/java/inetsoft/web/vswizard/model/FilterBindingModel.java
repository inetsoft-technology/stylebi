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
package inetsoft.web.vswizard.model;

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.SelectionVSAssembly;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;

import java.util.ArrayList;
import java.util.List;

public class FilterBindingModel {
   public FilterBindingModel() {
   }

   public FilterBindingModel(DataRefModelFactoryService refModelService,
                             SelectionVSAssembly assembly)
   {

      DataRef[] cols = assembly.getBindingRefs();
      List<String> names = new ArrayList();

      for(int i = 0; cols != null && i < cols.length; i++) {
         String name = cols[i].getAttribute();
         DataRefModel model = refModelService.createDataRefModel(cols[i]);

         if(!names.contains(name)) {
            bindingRefs.add(model);
            names.add(name);
         }
      }
   }

   public void setBindingRefs(List<DataRefModel> bindingRefs) {
       this.bindingRefs = bindingRefs;
   }

   public List<DataRefModel> getBindingRefs() {
       return bindingRefs;
   }

   private List<DataRefModel> bindingRefs = new ArrayList<>();
}
