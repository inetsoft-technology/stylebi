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
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.UserEnv;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.*;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.condition.ExpressionValueModel;
import inetsoft.web.composer.model.vs.VariableListDialogModel;
import inetsoft.web.composer.model.ws.VariableAssemblyDialogModel;
import inetsoft.web.composer.model.ws.VariableTableListDialogModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.List;
import java.util.*;

/**
 * Controller that provides the REST endpoints for the variable assembly dialog.
 *
 * @since 12.3
 */
@Controller
public class VariableAssemblyDialogController extends WorksheetController {
   /**
    * Gets the top-level descriptor of the assembly.
    *
    * @param runtimeId the runtime identifier of the worksheet.
    *
    * @return the assembly descriptor.
    */
   @RequestMapping(
      value = "/api/composer/ws/variable-assembly-dialog-model/{runtimeid}",
      method = RequestMethod.GET)
   @ResponseBody
   public VariableAssemblyDialogModel getVariableAssemblyDialogModel(
      @PathVariable("runtimeid") String runtimeId,
      @RequestParam(value = "variable", required = false) String variableName,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      VariableAssemblyDialogModel result = new VariableAssemblyDialogModel();
      RuntimeWorksheet rws = super.getWorksheetEngine()
         .getWorksheet(runtimeId, principal);
      VariableAssembly assembly = (VariableAssembly) rws.getWorksheet()
         .getAssembly(variableName);

      if(assembly == null || assembly.getVariable() == null) {
         result.setType(XSchema.STRING);
         result.setSelectionList("none");
         result.setNone(true);
      }
      else {
         AssetVariable variable = assembly.getVariable();
         result.setOldName(variable.getName());
         result.setLabel(variable.getAlias());
         result.setType(variable.getTypeNode().getType());
         result.setNone(variable.getValueNode() == null);

         if(!result.isNone() && variable.getValueNode() != null) {
            XValueNode valueNode = variable.getValueNode();
            Object val = valueNode.getValue();

            if(valueNode.getValue() instanceof ExpressionValue) {
               ExpressionValue expVal = (ExpressionValue) variable.getValueNode().getValue();
               ExpressionValueModel expValModel = new ExpressionValueModel();
               expValModel.setExpression(expVal.getExpression());
               result.setDefaultValue(expValModel);
            }
            else if(val != null) {
               result.setDefaultValue(Tool.getDataString(val));
            }

            if(result.getDefaultValue() == null) {
               result.setNone(true);
            }
         }

         if(variable.getDisplayStyle() == UserVariable.NONE) {
            result.setSelectionList("none");
         }
         else if(variable.getTableName() == null) {
            result.setSelectionList("embedded");
            VariableListDialogModel listModel = result.getVariableListDialogModel();
            String[] choices = new String[variable.getChoices().length];
            String[] values = new String[variable.getValues().length];

            for(int i = 0; i < variable.getChoices().length; i++) {
               if(variable.getChoices()[i] == null || variable
                  .getChoices()[i] instanceof String)
               {
                  choices[i] = (String) variable.getChoices()[i];
               }
            }

            Object[] varr = variable.getValues();

            for(int i = 0; i < varr.length; i++) {
               if(varr[i] instanceof Timestamp) {
                  values[i] = Tool.formatDateTime((Timestamp) varr[i]);
               }
               else if(varr[i] instanceof Time) {
                  values[i] = Tool.formatTime((Time) varr[i]);
               }
               else if(varr[i] instanceof Date) {
                  values[i] = Tool.formatDate((Date) varr[i]);
               }
               else if(varr[i] != null) {
                  values[i] = varr[i].toString();
               }
               else {
                  values[i] = null;
               }
            }

            listModel.setLabels(choices);
            listModel.setValues(values);
         }
         else {
            result.setSelectionList("query");
            VariableTableListDialogModel tableModel = result.getVariableTableListDialogModel();
            tableModel.setTableName(variable.getTableName());
            TableAssembly table = (TableAssembly) rws.getWorksheet().getAssembly(variable.getTableName());

            DataRef label = variable.getLabelAttribute();

            if(label != null) {
               tableModel.setLabel(getColumnAttributeName(table, label));
            }

            DataRef value = variable.getValueAttribute();

            if(value != null) {
               tableModel.setValue(getColumnAttributeName(table, value));
            }
         }

         result.setDisplayStyle(variable.getDisplayStyle());
      }

      result.setOtherVariables(getOtherVariableNames(rws, variableName, principal));

      return result;
   }

   /**
    * Get a list of variable names for this worksheet.
    */
   private List<String> getOtherVariableNames(RuntimeWorksheet rws, String varName,
                                              Principal principal)
   {
      Worksheet ws = rws.getWorksheet();
      Assembly[] arr = ws.getAssemblies();
      List<String> list = new ArrayList<>();
      Set added = new HashSet();

      for(int i = 0; i < arr.length; i++) {
         WSAssembly assembly = (WSAssembly) arr[i];

         if(assembly.isVariable() && assembly.isVisible()) {
            VariableAssembly vassembly = (VariableAssembly) assembly;
            UserVariable var = vassembly.getVariable();

            if(var != null && !Tool.equals(var.getName(), varName) && !added.contains(var.getName())) {
               added.add(var.getName());
               list.add(var.getName());
            }
         }
      }

      return list;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/ws/dialog/variable-assembly-dialog-model")
   public void setVariableAssemblyProperties(
      @Payload VariableAssemblyDialogModel model,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      Worksheet ws = rws.getWorksheet();
      VariableAssembly assembly = model
         .getOldName() == null ? null : (VariableAssembly) ws
         .getAssembly(model.getOldName());

      // new variable
      if(assembly == null) {
         assembly = new DefaultVariableAssembly(ws, model.getNewName());
         assembly.setVariable(convertModelToAssetVariable(model, ws));
         assembly.setPixelOffset(new Point(25, 25));
         AssetEventUtil.adjustAssemblyPosition(assembly, ws);
         ws.addAssembly(assembly);
         WorksheetEventUtil.createAssembly(rws, assembly, commandDispatcher, principal);
         WorksheetEventUtil
            .refreshAssembly(rws, model.getNewName(), model.getOldName(), true,
                             commandDispatcher, principal);
         WorksheetEventUtil.layout(rws, commandDispatcher);
         WorksheetEventUtil.focusAssembly(model.getNewName(), commandDispatcher);
      }
      else {
         AssetVariable ovar = assembly.getVariable();
         assembly.setVariable(convertModelToAssetVariable(model, ws));

         // clear out remembered variable value in case default value changed
         if(ovar == null ||
            !Objects.equals(ovar.getValueNode(), assembly.getVariable().getValueNode()))
         {
            UserEnv.setProperty(principal, box.getWSName() + " variable : " + assembly.getName(),
                                null);
         }

         WorksheetEventUtil.refreshAssembly(rws, model.getNewName(), model.getOldName(), true,
                                            commandDispatcher, principal);
      }

      AssetEventUtil.refreshTableLastModified(ws, model.getNewName(), true);
   }


   private AssetVariable convertModelToAssetVariable(
      VariableAssemblyDialogModel model, Worksheet ws)
   {
      AssetVariable variable = new AssetVariable();
      variable.setSortValue(false);
      variable.setName(model.getNewName());
      variable.setAlias(model.getLabel());
      variable.setTypeNode(XSchema.createPrimitiveType(model.getType()));
      variable.setValueNode(null);

      if(model.getDefaultValue() != null && !model.isNone()) {
         if(model.getDefaultValue() instanceof ExpressionValueModel) {
            ExpressionValueModel expValModel = (ExpressionValueModel) model.getDefaultValue();
            ExpressionValue expVal = new ExpressionValue();
            expVal.setType(ExpressionValue.JAVASCRIPT);
            expVal.setExpression(expValModel.getExpression());
            variable.setValueNode(
               XValueNode.createValueNode(expVal, "default", model.getType()));
         }
         else {
            Object valueString = Tool.getData(
               variable.getTypeNode().getType(), model.getDefaultValue());
            variable.setValueNode(XValueNode.createValueNode(valueString, "default"));
         }
      }

      if("embedded".equals(model.getSelectionList())) {
         variable.setTableName(null);
         variable.setChoices(model.getVariableListDialogModel().getLabels());
         Object[] values = Arrays.stream(model.getVariableListDialogModel().getValues())
            .map((value) -> Tool.getData(model.getType(), value)).toArray();
         variable.setValues(values);
      }
      else if("query".equals(model.getSelectionList()) &&
         model.getVariableTableListDialogModel().getTableName() != null)
      {
         TableAssembly assembly = (TableAssembly) ws
            .getAssembly(model.getVariableTableListDialogModel().getTableName());
         variable.setTableName(model.getVariableTableListDialogModel().getTableName());
         variable.setLabelAttribute(
            getColumnAttribute(assembly, model.getVariableTableListDialogModel().getLabel()));
         variable.setValueAttribute(
            getColumnAttribute(assembly, model.getVariableTableListDialogModel().getValue()));
      }

      if("none".equals(model.getSelectionList())) {
         variable.setDisplayStyle(UserVariable.NONE);
      }
      else {
         variable.setDisplayStyle(model.getDisplayStyle());
         variable.setMultipleSelection(UserVariable.LIST == model.getDisplayStyle());
      }

      return variable;
   }

   private DataRef getColumnAttribute(TableAssembly table, String name) {
      if(table == null) {
         return null;
      }

      ColumnSelection cols = table.getColumnSelection();
      DataRef attribute = cols.getAttribute(name);

      if(attribute != null) {
         return attribute;
      }

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         DataRef ref = cols.getAttribute(i);
         String fakeColName = "Column [" + i + "]";

         if(Tool.isEmptyString(ref.getName()) && fakeColName.equals(name)) {
            attribute = ref;
         }
      }

      return attribute;
   }

   private String getColumnAttributeName(TableAssembly table, DataRef ref) {
      if(table == null || ref == null) {
         return null;
      }

      if(!Tool.isEmptyString(ref.getAttribute())) {
         return ref.getAttribute();
      }

      ColumnSelection cols = table.getColumnSelection();
      int index = cols.indexOfAttribute(ref);

      return index > -1 ? "Column [" + index + "]" : ref.getAttribute();
   }
}
