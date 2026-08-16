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
import inetsoft.web.binding.controller.ChangeChartAestheticService;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Chart aesthetic mutations: channel field bindings and visual frames.
 *
 * <p>Structurally identical to {@link ChartBindingService} — each public method is exactly one
 * {@code sessions.mutate}, so it is one undo checkpoint in the user's Composer, driven through
 * the capturing dispatcher and broadcast afterwards.
 *
 * <p>Writes go through {@code changeChartAesthetic} rather than {@code changeChartRef}: it
 * posts the same {@code ChangeChartRefEvent}, but clears the viewsheet's shared frames when
 * the colour or shape channel changes. Routing an aesthetic write through the data-binding
 * service instead would leave stale shared frames behind.
 */
@Service
public class ChartAestheticAgentService {
   @Autowired
   public ChartAestheticAgentService(ViewsheetSessionService sessions,
                                VSBindingService binding,
                                ChangeChartAestheticService aestheticService)
   {
      this.sessions = sessions;
      this.binding = binding;
      this.aestheticService = aestheticService;
   }

   public void setField(String sessionToken, Principal user, String assemblyName, String channel,
                        FieldRef field, String linkUri) throws Exception
   {
      // Validated before the runtime is touched, so a bad channel costs nothing and does not
      // open a checkpoint the caller then has to undo.
      String name = AestheticChannels.requireFieldChannel(channel);

      apply(sessionToken, user, assemblyName, name, linkUri,
            model -> ChartAestheticMutator.setField(model, name, field));
   }

   public void clearField(String sessionToken, Principal user, String assemblyName,
                          String channel, String linkUri) throws Exception
   {
      String name = AestheticChannels.requireFieldChannel(channel);

      apply(sessionToken, user, assemblyName, name, linkUri,
            model -> ChartAestheticMutator.clearField(model, name));
   }

   public void setFrame(String sessionToken, Principal user, String assemblyName, String channel,
                        Map<String, Object> frame, String linkUri) throws Exception
   {
      String name = AestheticChannels.requireFrameChannel(channel);

      apply(sessionToken, user, assemblyName, name, linkUri,
            model -> ChartAestheticMutator.setFrame(model, name, frame));
   }

   /** Reads the channels without opening a checkpoint. */
   public Map<String, Object> read(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      ChartVSAssembly chart = requireChart(rvs, assemblyName);
      return ChartAestheticMutator.describe((ChartBindingModel) binding.createModel(chart));
   }

   /** The channels and frame types available, so an agent can discover rather than guess. */
   public Map<String, Object> options() {
      return Map.of(
         "fieldChannels", AestheticChannels.FIELD_CHANNELS,
         "frameChannels", AestheticChannels.SUPPORTED_FRAME_CHANNELS,
         "frameTypes", List.of("static", "categorical", "gradient", "palette"),
         "palettes", List.copyOf(VisualFrameAliases.PALETTES.keySet()));
   }

   private void apply(String sessionToken, Principal user, String assemblyName, String channel,
                      String linkUri, java.util.function.Consumer<ChartBindingModel> mutation)
      throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChartVSAssembly chart = requireChart(rvs, assemblyName);
         ChartBindingModel model = (ChartBindingModel) binding.createModel(chart);
         mutation.accept(model);

         ChangeChartRefEvent event = new ChangeChartRefEvent();
         event.setName(assemblyName);
         event.setFieldType(channel);
         event.setModel(model);
         aestheticService.changeChartAesthetic(runtimeId, event, user, dispatcher, linkUri);
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
            ", not a chart. Chart aesthetic tools only apply to charts.");
      }

      return chart;
   }

   private final ViewsheetSessionService sessions;
   private final VSBindingService binding;
   private final ChangeChartAestheticService aestheticService;
}
