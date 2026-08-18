/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.binding;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.AssemblyBinding;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads an assembly's binding into the shared {@link FieldRef} vocabulary.
 *
 * <p>A pure read: {@code VSBindingService.createModel} is a function of the assembly, so no
 * dispatcher, checkpoint, or broadcast is involved.
 */
@Service
public class BindingReadService {
   @Autowired
   public BindingReadService(VSBindingService binding) {
      this.binding = binding;
   }

   public AssemblyBinding read(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      // A calc table's binding is cell-structured and lives in its layout, not its binding
      // model — CalcTableBindingModel adds no fields at all. See spec 2e.
      if(assembly instanceof CalcTableVSAssembly) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a calc table. Its binding lives in its cell layout, " +
            "not its binding model — read it with get_calc_layout instead.");
      }

      BindingModel model = binding.createModel(assembly);
      Map<String, List<FieldRef>> shelves = new LinkedHashMap<>();

      if(model instanceof ChartBindingModel chart) {
         shelves.put("x", refs(chart.getXFields()));
         shelves.put("y", refs(chart.getYFields()));
         shelves.put("group", refs(chart.getGroupFields()));
      }
      else if(model instanceof CrosstabBindingModel crosstab) {
         shelves.put("rows", refs(crosstab.getRows()));
         shelves.put("cols", refs(crosstab.getCols()));
         shelves.put("aggregates", refs(crosstab.getAggregates()));
      }
      else if(model instanceof TableBindingModel table) {
         shelves.put("groups", refs(table.getGroups()));
         shelves.put("details", refs(table.getDetails()));
         shelves.put("aggregates", refs(table.getAggregates()));
      }

      return new AssemblyBinding(assemblyName,
                                 assembly.getClass().getSimpleName(),
                                 model == null || model.getSource() == null
                                    ? null : model.getSource().getSource(),
                                 shelves);
   }

   private static List<FieldRef> refs(List<? extends DataRefModel> models) {
      List<FieldRef> refs = new ArrayList<>();

      if(models != null) {
         for(DataRefModel model : models) {
            refs.add(FieldRefFactory.from(model));
         }
      }

      return refs;
   }

   private final VSBindingService binding;
}
