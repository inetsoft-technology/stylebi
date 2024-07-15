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
package inetsoft.web.binding.service;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class VSTableBindingFactory extends VSBindingFactory<TableVSAssembly, TableBindingModel> {
   @Autowired
   public VSTableBindingFactory(DataRefModelFactoryService refModelService) {
      this.refModelService = refModelService;
   }

   @Override
   public Class<TableVSAssembly> getAssemblyClass() {
      return TableVSAssembly.class;
   }

   /**
    * Creates a new model instance for the specified assembly.
    *
    * @param assembly the assembly.
    *
    * @return a new model.
    */
   @Override
   public TableBindingModel createModel(TableVSAssembly assembly) {
      return new TableBindingModel(refModelService, assembly);
   }

   /**
    * Update a table vs assembly.
    *
    * @param model the specified table binding model.
    * @param assembly the specified table vs assembly.
    *
    * @return the table vs assembly.
    */
   @Override
   public TableVSAssembly updateAssembly(TableBindingModel model,
                                         TableVSAssembly assembly)
   {
      return VSTableBindingFactory.updateTableAssembly(model, assembly);
   }

   public static TableVSAssembly updateTableAssembly(TableBindingModel model,
                                         TableVSAssembly assembly)
   {
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) assembly.getInfo();
      ColumnSelection cols = info.getColumnSelection();
      ArrayList<DataRef> refs = new ArrayList<>();

      for(DataRefModel field : model.getDetails()) {
         DataRef ref = field.createDataRef();
         DataRef oref = cols.getAttribute(ref.getName());

         if(ref instanceof FormRef) {
            FormRef oform = (FormRef) oref;
            FormRef form = (FormRef) ref;
            form.setOption(oform.getOption());
         }
         
         refs.add(ref);
      }
      
      cols.clear();
      
      for(int i = 0; i < refs.size(); i++) {
         cols.addAttribute(refs.get(i));
      }

      return assembly;
   }

   private final DataRefModelFactoryService refModelService;

   @Component
   class VSEmbeddedTableBindingFactory
      extends VSBindingFactory<EmbeddedTableVSAssembly, TableBindingModel>
   {
      @Autowired
      public VSEmbeddedTableBindingFactory(DataRefModelFactoryService refModelService) {
         this.refModelService = refModelService;
      }

      @Override
      public Class<EmbeddedTableVSAssembly> getAssemblyClass() {
         return EmbeddedTableVSAssembly.class;
      }

      /**
       * Creates a new model instance for the specified assembly.
       *
       * @param assembly the assembly.
       *
       * @return a new model.
       */
      @Override
      public TableBindingModel createModel(EmbeddedTableVSAssembly assembly) {
         return new TableBindingModel(refModelService, assembly);
      }

      /**
       * Update a table vs assembly.
       *
       * @param model the specified table binding model.
       * @param assembly the specified table vs assembly.
       *
       * @return the table vs assembly.
       */
      @Override
      public EmbeddedTableVSAssembly updateAssembly(TableBindingModel model,
                                            EmbeddedTableVSAssembly assembly)
      {
         return (EmbeddedTableVSAssembly) VSTableBindingFactory.updateTableAssembly(model, assembly);
      }

      private final DataRefModelFactoryService refModelService;
   }
}
