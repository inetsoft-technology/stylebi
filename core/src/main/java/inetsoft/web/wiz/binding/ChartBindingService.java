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
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.controller.ChangeChartRefService;
import inetsoft.web.binding.controller.ChangeChartTypeService;
import inetsoft.web.binding.controller.SwapXYBindingService;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.event.ChangeChartTypeEvent;
import inetsoft.web.binding.event.ChangeSeparateStatusEvent;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;

/**
 * Chart data-binding mutations: shelves, chart type, and axis swap.
 *
 * <p>Each public method is exactly one {@code sessions.mutate}, so it is one undo checkpoint
 * in the user's Composer, driven through the capturing dispatcher and broadcast afterwards.
 *
 * <p>Shelf writes are read-modify-write on the model {@code VSBindingService.createModel}
 * returns. {@code ChangeChartRefEvent} carries the entire {@code ChartBindingModel}, so
 * constructing a fresh one would silently discard the thirteen aesthetic fields in
 * {@link ChartBindingFields#AESTHETIC} along with everything else this class does not set.
 */
@Service
public class ChartBindingService {
   @Autowired
   public ChartBindingService(ViewsheetSessionService sessions,
                              VSBindingService binding,
                              ChangeChartRefService refService,
                              ChangeChartTypeService typeService,
                              SwapXYBindingService swapService)
   {
      this.sessions = sessions;
      this.binding = binding;
      this.refService = refService;
      this.typeService = typeService;
      this.swapService = swapService;
   }

   public void setShelf(String sessionToken, Principal user, String assemblyName, String shelf,
                        List<FieldRef> fields, String linkUri) throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChartVSAssembly chart = requireChart(rvs, assemblyName);
         ChartBindingModel model = (ChartBindingModel) binding.createModel(chart);
         ChartBindingMutator.setShelf(model, shelf, fields);

         ChangeChartRefEvent event = new ChangeChartRefEvent();
         event.setName(assemblyName);
         event.setFieldType(shelf);
         event.setModel(model);
         refService.changeChartRef(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   public void setChartType(String sessionToken, Principal user, String assemblyName, int type,
                            Boolean multi, Boolean stackMeasures, Boolean separate,
                            String linkUri) throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         requireChart(rvs, assemblyName);

         ChangeChartTypeEvent event = new ChangeChartTypeEvent();
         event.setName(assemblyName);
         event.setType(type);
         event.setMulti(Boolean.TRUE.equals(multi));
         event.setStackMeasures(Boolean.TRUE.equals(stackMeasures));
         event.setSeparate(!Boolean.FALSE.equals(separate));
         typeService.changeChartType(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   public void swapAxes(String sessionToken, Principal user, String assemblyName, String linkUri)
      throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         requireChart(rvs, assemblyName);

         ChangeSeparateStatusEvent event = new ChangeSeparateStatusEvent();
         event.setName(assemblyName);
         swapService.swapXYBinding(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   private static ChartVSAssembly requireChart(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      if(!(assembly instanceof ChartVSAssembly chart)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not a chart. Chart binding tools only apply to charts.");
      }

      return chart;
   }

   private final ViewsheetSessionService sessions;
   private final VSBindingService binding;
   private final ChangeChartRefService refService;
   private final ChangeChartTypeService typeService;
   private final SwapXYBindingService swapService;
}
