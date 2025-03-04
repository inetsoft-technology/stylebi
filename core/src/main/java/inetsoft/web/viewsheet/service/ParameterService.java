/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.ParameterTool;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.assembly.VariableAssemblyModelInfo;
import inetsoft.web.viewsheet.command.CollectParametersCommand;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for parsing, collecting, and refreshing of parameters.
 */
@Service
public class ParameterService {
   @Autowired
   public ParameterService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   /**
    * Reads the parameters from the event into a variable table.
    *
    * @param parameters the input parameters.
    *
    * @return a variable table containing the parameters.
    */
   public VariableTable readParameters(Map<String, String[]> parameters) {
      VariableTable result = new VariableTable();
      final String typeSuffix = ".__type__";

      if(parameters != null) {
         for(Map.Entry<String, String[]> e : parameters.entrySet()) {
            String name = e.getKey();
            String[] values0 = e.getValue();

            if(name.endsWith(typeSuffix)) {
               continue;
            }

            final String[] type = parameters.get(name + typeSuffix);
            Object[] values = Arrays.stream(values0)
               .map(v -> type != null && type.length > 0 ? Tool.getData(type[0], v, true) : v)
               .toArray();

            Object value0 = (values != null && values.length == 1) ? values[0] : values;
            Object value = null;

            if(AssetUtil.isSelectionParam(e.getKey())) {
               try {
                  value = AssetUtil.getSelectionParam(name, value0);
               }
               catch(Exception ex) {
                  LOG.warn("Failed to get selection parameter: {}", e.getKey(), ex);
               }
            }

            value = value == null ? value0 : value;
            result.put(name, value);
         }
      }

      return result;
   }

   public void collectParameters(Viewsheet vs, UserVariable[] variables,
                                 boolean parameterSheet, CommandDispatcher dispatcher)
   {
      collectParameters(vs, variables, parameterSheet, dispatcher, false);
   }

   public void collectParameters(Viewsheet vs, UserVariable[] variables,
                                 boolean parameterSheet, CommandDispatcher dispatcher,
                                 boolean isOpenVS)
   {
      List<VariableAssemblyModelInfo> parameters = new ArrayList<>();
      Collection<UserVariable> ordering = new LinkedHashSet<>();
      ViewsheetInfo info = vs.getViewsheetInfo();

      for(String varName : info.getOrderedVariables()) {
         Arrays.stream(variables)
            .filter(userVar -> varName.equals(userVar.getName()))
            .findFirst()
            .ifPresent(ordering::add);
      }

      Arrays.stream(variables)
         .filter(userVar -> !ordering.contains(userVar))
         .forEach(ordering::add);

      ordering.stream()
         .map(VariableAssemblyModelInfo::new)
         .forEach(parameters::add);

      CollectParametersCommand command = CollectParametersCommand.builder()
         .disableParameterSheet(parameterSheet || info.isDisableParameterSheet())
         .isOpenSheet(isOpenVS)
         .disabledVariables(vs.getDisabledVariables())
         .variables(parameters)
         .build();

      dispatcher.sendCommand(command);
   }

   public void refreshTextParameters(RuntimeViewsheet rvs, VSAssembly assembly) {
      if(!rvs.isRuntime()) {
         return;
      }

      if(assembly instanceof TextVSAssembly) {
         refreshTextParameters(rvs.getViewsheetSandbox(), (TextVSAssemblyInfo) assembly.getInfo());
      }

      if(assembly instanceof Viewsheet) {
         Viewsheet vs = (Viewsheet) assembly;
         Assembly[] assemblies = vs.getAssemblies(false, true,
                                                  !WizardRecommenderUtil.ignoreRefreshTempAssembly(), false);
         Arrays.sort(assemblies, new CoreLifecycleService.TabAnnotationComparator());
         Arrays.stream(assemblies)
            .forEach(assembly0 -> refreshTextParameters(rvs, (VSAssembly) assembly0));
      }
   }

   private void refreshTextParameters(ViewsheetSandbox box, TextVSAssemblyInfo info) {
      ParameterTool ptool = new ParameterTool();
      String text = info.getText();

      if(ptool.containsParameter(text)) {
         String dtext = ptool.parseParameters(box.getVariableTable(), text);
         info.setDisplayText(dtext);
      }
      else {
         info.setDisplayText(text);
      }
   }

   /**
    * Returns the parameters that will be prompted on viewsheet open
    */
   public List<UserVariable> getPromptParameters(Viewsheet sheet, ViewsheetSandbox box,
                                                 VariableTable initvars)
      throws Exception
   {
      List<UserVariable> vars = new ArrayList<>();
      VSEventUtil.refreshParameters(viewsheetService, box, sheet, false, initvars, vars);
      Set<String> disabledVars = sheet.getDisabledVariables();
      Set<String> inputAssemblyNames = Arrays.stream(sheet.getAssemblies(true, true))
         .filter((assembly) -> assembly instanceof InputVSAssembly)
         .map(Assembly::getName)
         .collect(Collectors.toSet());
      vars.removeIf(var -> disabledVars.contains(var.getName()) ||
         inputAssemblyNames.contains(var.getName()));
      return vars;
   }

   private final ViewsheetService viewsheetService;
   private static final Logger LOG = LoggerFactory.getLogger(ParameterService.class);
}
