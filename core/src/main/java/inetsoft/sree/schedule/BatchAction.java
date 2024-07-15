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
package inetsoft.sree.schedule;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.internal.Util;
import inetsoft.sree.DynamicParameterValue;
import inetsoft.sree.RepletRequest;
import inetsoft.sree.internal.SUtil;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import inetsoft.util.script.ScriptEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.security.Principal;
import java.util.*;

public class BatchAction extends AbstractAction {
   @Override
   public void run(Principal principal) throws Throwable {
      ScheduleManager scheduleManager = ScheduleManager.getScheduleManager();
      ScheduleTask task = scheduleManager.getScheduleTask(taskName);

      if(task != null) {
         runScheduleTaskWithEmbeddedParameters(task, principal);
         runScheduleTaskWithQueryParameters(task, principal);
      }
   }

   private void runScheduleTaskWithQueryParameters(ScheduleTask task, Principal principal) throws Throwable {
      if(queryEntry == null || queryParameters == null || queryParameters.size() == 0) {
         return;
      }

      AssetRepository assetRepository = AssetUtil.getAssetRepository(false);
      TableAssembly tableAssembly = null;
      TableLens queryTable = null;
      Worksheet sheet = null;

      if(queryEntry.isTable()) {
         AssetEntry wsEntry = new AssetEntry(queryEntry.getScope(), AssetEntry.Type.WORKSHEET,
                                             queryEntry.getParentPath(), queryEntry.getUser());
         sheet = (Worksheet) assetRepository.getSheet(wsEntry, principal, true,
                                                      AssetContent.ALL);
         Assembly assembly = sheet.getAssembly(queryEntry.getName());

         if(assembly instanceof TableAssembly) {
            tableAssembly = (TableAssembly) assembly;
         }
      }
      else if(queryEntry.isWorksheet()) {
         sheet = (Worksheet) assetRepository.getSheet(queryEntry, principal, true,
                                                      AssetContent.ALL);
         Assembly assembly = sheet.getPrimaryAssembly();

         if(assembly instanceof TableAssembly) {
            tableAssembly = (TableAssembly) assembly;
         }
      }

      if(tableAssembly != null) {
         AssetQuerySandbox box = new AssetQuerySandbox(sheet);
         AssetQuery query = AssetQuery.createAssetQuery(
            tableAssembly, AssetQuerySandbox.RUNTIME_MODE, box, false,
            -1L, true, false);
         queryTable = query.getTableLens(new VariableTable());
      }

      if(queryTable != null) {
         ColumnIndexMap columnIndexMap = new ColumnIndexMap(queryTable, true);

         for(int r = queryTable.getHeaderRowCount(); queryTable.moreRows(r); r++) {
            ScheduleTask clonedTask = ScheduleTask.copyScheduleTask(task);

            for(int i = 0; i < clonedTask.getActionCount(); i++) {
               ScheduleAction action = clonedTask.getAction(i);

               if(!(action instanceof ViewsheetAction)) {
                  continue;
               }

               RepletRequest repletRequest = ((ViewsheetAction) action).getViewsheetRequest();;

               if(repletRequest == null) {
                  repletRequest = new RepletRequest();
               }

               VariableTable vars = new VariableTable();

               for(String paramName : queryParameters.keySet()) {
                  int c = Util.findColumn(columnIndexMap, queryParameters.get(paramName));

                  if(c >= 0) {
                     Object paramValue = queryTable.getObject(r, c);
                     repletRequest.setParameter(paramName, paramValue);
                     vars.put(paramName, paramValue);
                  }
                  else {
                     LOG.warn("Could not find the column '" + queryParameters.get(paramName) +
                                 "' in table '" + tableAssembly.getName() + "'. Ignoring parameter '" +
                                 paramName + "'");
                  }
               }

               ((ViewsheetAction) action).setViewsheetRequest(repletRequest);
               replaceVariablesInScheduleAction((AbstractAction) action, vars);
            }

            clonedTask.run(principal);
         }
      }
   }

   private void runScheduleTaskWithEmbeddedParameters(ScheduleTask task, Principal principal) throws Throwable {
      if(embeddedParameters != null) {
         for(Map<String, Object> map : embeddedParameters) {
            ScheduleTask clonedTask = ScheduleTask.copyScheduleTask(task);

            for(int i = 0; i < clonedTask.getActionCount(); i++) {
               ScheduleAction action = clonedTask.getAction(i);

               if(!(action instanceof ViewsheetAction)) {
                  continue;
               }

               RepletRequest repletRequest = ((ViewsheetAction) action).getViewsheetRequest();

               if(repletRequest == null) {
                  repletRequest = new RepletRequest();
               }

               VariableTable vars = new VariableTable();
               ScheduleParameterScope scope = null;

               for(String paramName : map.keySet()) {
                  Object val = map.get(paramName);

                  if(val instanceof DynamicParameterValue) {
                     if(scope == null) {
                        scope = new ScheduleParameterScope();
                        ScriptEnv senv = scope.getScriptEnv();
                        senv.addTopLevelParentScope(scope);
                     }

                     val = RepletRequest.executeParameter(((DynamicParameterValue) val), scope);
                  }

                  repletRequest.setParameter(paramName, val);
                  vars.put(paramName, val);
               }

               ((ViewsheetAction) action).setViewsheetRequest(repletRequest);
               replaceVariablesInScheduleAction((AbstractAction) action, vars);
            }

            clonedTask.run(principal);
         }
      }
   }

   private void replaceVariablesInScheduleAction(AbstractAction action, VariableTable vars) {
      action.setEmails(XUtil.replaceVariable(action.getEmails(), vars));
      action.setCCAddresses(XUtil.replaceVariable(action.getCCAddresses(), vars));
      action.setBCCAddresses(XUtil.replaceVariable(action.getBCCAddresses(), vars));
      action.setSubject(XUtil.replaceVariable(action.getSubject(), vars));
      action.setAttachmentName(XUtil.replaceVariable(action.getAttachmentName(), vars));
      action.setMessage(XUtil.replaceVariable(action.getMessage(), vars));
   }

   public String getTaskName() {
      return taskName;
   }

   public void setTaskName(String taskName) {
      this.taskName = taskName;
   }

   public AssetEntry getQueryEntry() {
      return queryEntry;
   }

   public void setQueryEntry(AssetEntry queryEntry) {
      this.queryEntry = queryEntry;
   }

   public Map<String, Object> getQueryParameters() {
      return queryParameters;
   }

   public void setQueryParameters(Map<String, Object> queryParameters) {
      this.queryParameters = queryParameters;
   }

   public List<Map<String, Object>> getEmbeddedParameters() {
      return embeddedParameters;
   }

   public void setEmbeddedParameters(List<Map<String, Object>> embeddedParameters) {
      this.embeddedParameters = embeddedParameters;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<Action type=\"Batch\" class=\"");
      writer.print(getClass().getName());
      writer.print("\" ");
      writer.print("taskName=\"" + byteEncode(taskName) + "\" ");
      writer.println(">");

      if(queryEntry != null) {
         writer.println("<queryEntry>");
         queryEntry.writeXML(writer);
         writer.println("</queryEntry>");
      }

      writer.println("<queryParameters>");
      writeMap(writer, queryParameters);
      writer.println("</queryParameters>");

      writer.println("<embeddedParameters>");

      for(Map<String, Object> map : embeddedParameters) {
         writeMap(writer, map);
      }

      writer.println("</embeddedParameters>");

      writer.println("</Action>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      taskName = Tool.getAttribute(tag, "taskName");
      taskName = byteDecode(taskName);

      Element queryEntryElem = Tool.getChildNodeByTagName(tag, "queryEntry");

      if(queryEntryElem != null) {
         queryEntry = new AssetEntry();
         queryEntry.parseXML(Tool.getChildNodeByTagName(queryEntryElem, "assetEntry"));
      }

      Element queryParametersElem = Tool.getChildNodeByTagName(tag, "queryParameters");

      if(queryParametersElem != null) {
         Element mapElem = Tool.getChildNodeByTagName(queryParametersElem, "map");

         if(mapElem != null) {
            Map<String, Object> map = new LinkedHashMap<>();
            parseMap(mapElem, map);
            this.queryParameters = map;
         }
      }

      Element embeddedParametersElem = Tool.getChildNodeByTagName(tag, "embeddedParameters");

      if(embeddedParametersElem != null) {
         NodeList mapElems = Tool.getChildNodesByTagName(embeddedParametersElem, "map");
         this.embeddedParameters = new ArrayList<>();

         for(int i = 0; i < mapElems.getLength(); i++) {
            Map<String, Object> map = new LinkedHashMap<>();
            parseMap((Element) mapElems.item(i), map);
            this.embeddedParameters.add(map);
         }
      }
   }

   private void writeMap(PrintWriter writer, Map<String, Object> map) {
      if(map.size() == 0) {
         return;
      }

      writer.println("<map>");

      for(String key : map.keySet()) {
         writer.println("<entry>");
         writer.print("<key>");
         writer.print("<![CDATA[" + key + "]]>");
         writer.print("</key>");
         Object val = map.get(key);

         if(val instanceof DynamicParameterValue) {
            DynamicParameterValue parameterValue = (DynamicParameterValue) val;
            writer.print("<dynamicParameterValue>");
            writer.print("<value>");
            writer.print("<![CDATA[" + Tool.getDataString(parameterValue.getValue()) + "]]>");
            writer.print("</value>");
            writer.print("<type>");
            writer.print("<![CDATA[" + parameterValue.getType() + "]]>");
            writer.print("</type>");
            writer.print("<valueType>");
            writer.print("<![CDATA[" + parameterValue.getDataType() + "]]>");
            writer.print("</valueType>");
            writer.print("</dynamicParameterValue>");
         }
         else {
            writer.print("<value>");
            writer.print("<![CDATA[" + Tool.getDataString(val) + "]]>");
            writer.print("</value>");
            writer.print("<valueType>");
            writer.print("<![CDATA[" + Tool.getDataType(val) + "]]>");
            writer.print("</valueType>");
         }

         writer.println("</entry>");
      }

      writer.println("</map>");
   }

   private void parseMap(Element elem, Map<String, Object> map) throws Exception {
      NodeList list = elem.getChildNodes();

      for(int i = 0; i < list.getLength(); i++) {
         if(!(list.item(i) instanceof Element)) {
            continue;
         }

         Element propNode = (Element) list.item(i);
         Element keyNode = Tool.getChildNodeByTagName(propNode, "key");
         String key = Tool.getValue(keyNode);
         Element dynamicParameterValue = Tool.getChildNodeByTagName(propNode, "dynamicParameterValue");

         if(dynamicParameterValue != null) {
            Element valNode = Tool.getChildNodeByTagName(dynamicParameterValue, "value");
            Element typeNode = Tool.getChildNodeByTagName(dynamicParameterValue, "type");
            Element dataTypeNode = Tool.getChildNodeByTagName(dynamicParameterValue, "valueType");
            String value = Tool.getValue(valNode);
            String type = Tool.getValue(typeNode);
            String dataType = Tool.getValue(dataTypeNode);
            map.put(key, new DynamicParameterValue(value, type, dataType));
         }
         else {
            Element valNode = Tool.getChildNodeByTagName(propNode, "value");
            Element valTypeNode = Tool.getChildNodeByTagName(propNode, "valueType");
            String valType = Tool.getValue(valTypeNode);
            Object val = Tool.getData(valType, Tool.getValue(valNode));
            map.put(key, val);
         }
      }
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      BatchAction that = (BatchAction) o;
      return Objects.equals(taskName, that.taskName) &&
         Objects.equals(queryEntry, that.queryEntry) &&
         Objects.equals(queryParameters, that.queryParameters) &&
         Objects.equals(embeddedParameters, that.embeddedParameters);
   }

   @Override
   public String toString() {
      return "BatchAction: " + SUtil.getTaskNameWithoutOrg(taskName);
   }

   private String taskName;
   private AssetEntry queryEntry;
   private Map<String, Object> queryParameters = new LinkedHashMap<>();
   private List<Map<String, Object>> embeddedParameters = new ArrayList<>();
   private static final Logger LOG =
      LoggerFactory.getLogger(BatchAction.class);
}
