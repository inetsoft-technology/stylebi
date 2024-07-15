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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.web.composer.model.vs.ViewsheetParametersDialogModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ViewsheetSettingsService {
   @Autowired
   public ViewsheetSettingsService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   public ViewsheetParametersDialogModel getViewsheetParameterInfo(RuntimeViewsheet rvs)
      throws Exception
   {
      List<UserVariable> vars = new ArrayList<>();
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetInfo info = viewsheet.getViewsheetInfo();
      ViewsheetParametersDialogModel vsParametersDialogModel = new ViewsheetParametersDialogModel();

      VSEventUtil.refreshParameters(viewsheetService, rvs.getViewsheetSandbox(),
                                    rvs.getViewsheet(), false,
                                    new VariableTable(), vars);
      VariableTable parameters = viewsheet.getVariableTable(); //parameter sheet
      @SuppressWarnings("unchecked") Enumeration<String> enumeration = parameters.keys();

      List<String> disabledList = new ArrayList<>();
      List<String> varNames =
         vars.stream().map(UserVariable::getName).collect(Collectors.toList());

      for(String dVar : info.getDisabledVariables()) {
         if(varNames.contains(dVar)) {
            disabledList.add(dVar);
         }
      }

      vsParametersDialogModel.setDisabledParameters(disabledList.toArray(new String[0]));

      Collection<String> enabledVarsList = new LinkedHashSet<>();

      for(String oVar : info.getOrderedVariables()) {
         if(varNames.contains(oVar)) {
            enabledVarsList.add(oVar);
         }
      }

      while(enumeration.hasMoreElements()) {
         String varName = enumeration.nextElement();

         if(!disabledList.contains(varName)) {
            enabledVarsList.add(varName);
         }
      }

      for(UserVariable var : vars) {
         String varName = var.getName();

         if(!disabledList.contains(varName)) {
            enabledVarsList.add(varName);
         }
      }

      vsParametersDialogModel.setEnabledParameters(enabledVarsList.toArray(new String[0]));
      return vsParametersDialogModel;
   }

   public void setViewsheetParameterInfo(ViewsheetInfo info, ViewsheetParametersDialogModel model) {
      for(String param : info.getDisabledVariables()) {
         info.removeDisabledVariable(param);
      }

      for(String param : model.getDisabledParameters()) {
         info.addDisabledVariable(param);
      }

      info.setOrderedVariables(Arrays.asList(model.getEnabledParameters()));
   }

   private final ViewsheetService viewsheetService;
}
