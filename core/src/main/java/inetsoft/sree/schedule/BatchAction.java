/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.sree.schedule;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.internal.Util;
import inetsoft.sree.DynamicParameterValue;
import inetsoft.sree.RepletRequest;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.util.script.ScriptEnv;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.composer.model.vs.DynamicValueModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.security.Principal;
import java.util.*;

public class BatchAction extends AbstractAction {
   /**
    * Runs the child task once per embedded parameter map and once per row of the query. The
    * query is the content of the task that holds this action, so it runs with that task's
    * principal. Bug #77452, the child task runs with its own principal, the same principal it
    * runs with when it is scheduled ({@link SUtil#getScheduleTaskRunPrincipal}), never with the
    * principal of the task that holds this action: anyone who may change the child task would
    * otherwise run its actions with the roles of that task. A nested batch action of the child
    * task gets the child's principal and in turn runs its own child task with that task's
    * principal.
    *
    * @param principal the principal of the task that holds this action.
    */
   @Override
   public void run(Principal principal) throws Throwable {
      ScheduleManager scheduleManager = ScheduleManager.getScheduleManager();
      ScheduleTask task = scheduleManager.getScheduleTask(taskId);

      if(task != null) {
         // Bug #77531, run-time backstop: refuse to dispatch to one of the three internal tasks
         // (asset file backup, task balancer, update assets dependencies) unless the principal of
         // the task that holds this action is a site admin. Those internal actions ignore the
         // principal they are run with and perform no permission check of their own, so a
         // BatchAction saved (or imported) before this fix, or through any path the save-time
         // checks miss, must still be refused here.
         if(ScheduleManager.isInternalTask(task.getTaskId()) &&
            !OrganizationManager.getInstance().isSiteAdmin(principal))
         {
            throw new SecurityException(String.format(
               "Unauthorized access to resource \"%s\" by %s", task.getTaskId(), principal));
         }

         checkTargetPermission(task, principal);
         Principal childPrincipal = getChildPrincipal(task);
         runScheduleTaskWithEmbeddedParameters(task, childPrincipal);
         runScheduleTaskWithQueryParameters(task, principal, childPrincipal);
      }
   }

   /**
    * Bug #77972, the child task runs as its own owner with the parameters of this action, so the
    * owner of the task that holds this action must be allowed to see the child task, the same
    * rule that is checked when the action is saved. A batch action stored before that check, or
    * whose owner has lost access to the child task since, is refused here. Internal child tasks
    * are checked by the Bug #77531 rule alone, and nothing is checked without security.
    *
    * @param task      the child task.
    * @param principal the run principal of the task that holds this action, used when its owner
    *                  wasn't set.
    */
   private void checkTargetPermission(ScheduleTask task, Principal principal)
      throws SecurityException
   {
      if(ScheduleManager.isInternalTask(task.getTaskId()) ||
         !SecurityEngine.getSecurity().isSecurityEnabled())
      {
         return;
      }

      // check the owner, not the run principal, a group or role execute-as principal is named
      // after the group or role (the same as IndividualAssetBackupAction)
      Principal ownerPrincipal;

      if(taskOwner == null) {
         LOG.warn("The owner of the schedule task that runs the batch action of \"{}\" is not " +
                  "set, checking the task against the run principal {}", task.getTaskId(),
                  principal);
         ownerPrincipal = principal;
      }
      else {
         ownerPrincipal = SUtil.getScheduleTaskOwnerPrincipal(taskOwner, null, false);
      }

      if(!ScheduleManager.isBatchTargetPermitted(task, ownerPrincipal)) {
         throw new SecurityException(String.format(
            "Unauthorized access to resource \"%s\" by %s, the batch action target task isn't " +
            "accessible to the task owner", task.getTaskId(),
            taskOwner != null ? taskOwner.convertToKey() : principal));
      }
   }

   /**
    * Sets the owner of the task that runs this action, the target task is checked against it when
    * the action runs. It is set on the runtime copy of the task and is not stored.
    *
    * @param owner the task owner.
    */
   public void setTaskOwner(IdentityID owner) {
      this.taskOwner = owner;
   }

   /**
    * Runs a copy of the child task with its principal, which is also the context principal of
    * this thread while it runs (the actions' pooled threads inherit it). The context principal
    * of the task that holds this action is restored afterwards.
    */
   private static void runChildTask(ScheduleTask clonedTask, Principal childPrincipal)
      throws Throwable
   {
      Principal contextPrincipal = ThreadContext.getContextPrincipal();

      try {
         ThreadContext.setContextPrincipal(childPrincipal);
         clonedTask.run(childPrincipal);
      }
      finally {
         ThreadContext.setContextPrincipal(contextPrincipal);
      }
   }

   /**
    * Gets the principal the child task runs with, the same as ScheduleTaskJob: none for an
    * internal task, otherwise its execute-as identity or its owner, with the locale of the task.
    */
   private Principal getChildPrincipal(ScheduleTask task) {
      if(ScheduleManager.isInternalTask(task.getTaskId())) {
         return null;
      }

      Principal childPrincipal = SUtil.getScheduleTaskRunPrincipal(task, Tool.getIP(), true);

      if(childPrincipal == null) {
         // fail closed, never fall back to the principal of the task that holds this action
         throw new IllegalStateException(
            "Cannot get the principal of the batch action task " + task.getTaskId() +
            ", the task was not run");
      }

      // the child runs with its own locale, the same as when it is run (SUtil.runTask)
      SUtil.applyScheduleTaskLocale(childPrincipal, task.getLocale());
      return childPrincipal;
   }

   private void runScheduleTaskWithQueryParameters(ScheduleTask task, Principal principal,
                                                   Principal childPrincipal)
      throws Throwable
   {
      if(queryEntry == null || queryParameters == null || queryParameters.size() == 0) {
         return;
      }

      // Bug #77549, the auto-save properties are also removed here in case the entry was changed
      // in place or deserialized without going through setQueryEntry() or parseXML()
      AssetEntry entry = removeAutoSaveProperties(queryEntry);
      AssetRepository assetRepository = AssetUtil.getAssetRepository(false);
      TableAssembly tableAssembly = null;
      TableLens queryTable = null;
      Worksheet sheet = null;

      if(entry.isTable()) {
         AssetEntry wsEntry = new AssetEntry(entry.getScope(), AssetEntry.Type.WORKSHEET,
                                             entry.getParentPath(), entry.getUser());
         sheet = (Worksheet) assetRepository.getSheet(wsEntry, principal, true,
                                                      AssetContent.ALL);
         Assembly assembly = sheet.getAssembly(entry.getName());

         if(assembly instanceof TableAssembly) {
            tableAssembly = (TableAssembly) assembly;
         }
      }
      else if(entry.isWorksheet()) {
         sheet = (Worksheet) assetRepository.getSheet(entry, principal, true,
                                                      AssetContent.ALL);
         Assembly assembly = sheet.getPrimaryAssembly();

         if(assembly instanceof TableAssembly) {
            tableAssembly = (TableAssembly) assembly;
         }
      }

      if(tableAssembly != null) {
         AssetQuerySandbox box = new AssetQuerySandbox(sheet);
         box.setBaseUser(principal);
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

            runChildTask(clonedTask, childPrincipal);
         }
      }
   }

   private void runScheduleTaskWithEmbeddedParameters(ScheduleTask task,
                                                      Principal childPrincipal)
      throws Throwable
   {
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

            runChildTask(clonedTask, childPrincipal);
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

   public String getTaskId() {
      return taskId;
   }

   public void setTaskId(String taskId) {
      this.taskId = taskId;
   }

   public AssetEntry getQueryEntry() {
      return queryEntry;
   }

   public void setQueryEntry(AssetEntry queryEntry) {
      this.queryEntry = removeAutoSaveProperties(queryEntry);
   }

   /**
    * Bug #77549, gets a copy of a query entry without the auto-save properties. The entry comes
    * from the client (the task editor or an imported task) with its properties as sent, and an
    * entry with openAutoSaved reads the auto-saved file named by autoFileName without any
    * permission check (AbstractAssetEngine.getSheet), e.g. another user's unsaved worksheet. A
    * batch action query is always a saved worksheet.
    *
    * @return the entry itself if it has none of them.
    */
   public static AssetEntry removeAutoSaveProperties(AssetEntry entry) {
      if(entry == null || AutoSaveUtils.AUTO_SAVE_PROPERTIES.stream()
         .allMatch(p -> entry.getProperty(p) == null))
      {
         return entry;
      }

      AssetEntry copy = (AssetEntry) entry.clone();
      AutoSaveUtils.AUTO_SAVE_PROPERTIES.forEach(p -> copy.setProperty(p, null));
      return copy;
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
      // a task name may contain & < " (Bug #77807)
      writer.print("taskId=\"" + (taskId == null ? null : Tool.escape(byteEncode(taskId))) + "\" ");
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
   public void parseXML(Element tag) throws Exception{
      parseXML(tag, false);
   }

   @Override
   public void parseXML(Element tag, boolean isImportAsSiteAdmin) throws Exception {
      taskId = Tool.getAttribute(tag, "taskId");
      taskId = byteDecode(taskId);

      if(isImportAsSiteAdmin) {
         String taskUser = taskId.substring(0, taskId.indexOf(":"));
         String taskName = taskId.substring(taskId.indexOf(":"));
         IdentityID updatedUser = IdentityID.getIdentityIDFromKey(taskUser);
         updatedUser.setOrgID(OrganizationManager.getInstance().getCurrentOrgID());

         taskId = updatedUser.convertToKey() + taskName;
      }

      Element queryEntryElem = Tool.getChildNodeByTagName(tag, "queryEntry");

      if(queryEntryElem != null) {
         AssetEntry entry = new AssetEntry();
         entry.parseXML(Tool.getChildNodeByTagName(queryEntryElem, "assetEntry"), isImportAsSiteAdmin);
         queryEntry = removeAutoSaveProperties(entry);
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
         writeCDATAElement(writer, "key", key);
         Object val = map.get(key);

         if(val instanceof DynamicParameterValue) {
            DynamicParameterValue parameterValue = (DynamicParameterValue) val;
            writer.print("<dynamicParameterValue>");
            writeCDATAElement(writer, "value", Tool.getDataString(parameterValue.getValue()));
            writer.print("<type>");
            writer.print("<![CDATA[" + parameterValue.getType() + "]]>");
            writer.print("</type>");
            writer.print("<valueType>");
            writer.print("<![CDATA[" + parameterValue.getDataType() + "]]>");
            writer.print("</valueType>");
            writer.print("</dynamicParameterValue>");
         }
         else {
            writeCDATAElement(writer, "value", Tool.getDataString(val));
            writer.print("<valueType>");
            writer.print("<![CDATA[" + Tool.getDataType(val) + "]]>");
            writer.print("</valueType>");
         }

         writer.println("</entry>");
      }

      writer.println("</map>");
   }

   /**
    * Write a free-text CDATA element. A character XML 1.0 can't carry is encoded and the
    * element marked so readCDATAElement decodes it. Other text is written as before
    * (Bug #77806).
    */
   private static void writeCDATAElement(PrintWriter writer, String tag, String text) {
      boolean ctrl = Tool.hasXMLIllegalChars(text);
      writer.print("<" + tag + (ctrl ? " " + XML_ILLEGAL_CHARS_ATTR + "=\"true\"" : "") + ">");
      writer.print("<![CDATA[" + Tool.splitCDATAEnd(ctrl ? Tool.encodeXMLIllegalChars(text) : text) +
                   "]]>");
      writer.print("</" + tag + ">");
   }

   /**
    * Read an element written by writeCDATAElement. Unmarked text is never decoded.
    */
   private static String readCDATAElement(Element elem) {
      String text = Tool.getValue(elem);
      return elem != null && "true".equals(Tool.getAttribute(elem, XML_ILLEGAL_CHARS_ATTR)) ?
         Tool.decodeXMLIllegalChars(text) : text;
   }

   private void parseMap(Element elem, Map<String, Object> map) throws Exception {
      NodeList list = elem.getChildNodes();

      for(int i = 0; i < list.getLength(); i++) {
         if(!(list.item(i) instanceof Element)) {
            continue;
         }

         Element propNode = (Element) list.item(i);
         Element keyNode = Tool.getChildNodeByTagName(propNode, "key");
         String key = readCDATAElement(keyNode);
         Element dynamicParameterValue = Tool.getChildNodeByTagName(propNode, "dynamicParameterValue");

         if(dynamicParameterValue != null) {
            Element valNode = Tool.getChildNodeByTagName(dynamicParameterValue, "value");
            Element typeNode = Tool.getChildNodeByTagName(dynamicParameterValue, "type");
            Element dataTypeNode = Tool.getChildNodeByTagName(dynamicParameterValue, "valueType");
            String value = readCDATAElement(valNode);
            String type = Tool.getValue(typeNode);
            String dataType = Tool.getValue(dataTypeNode);

            // a date value saved before #77605 may have a Buddhist or Japanese year, it is
            // kept as a string as before
            if(DynamicValueModel.VALUE.equals(type) &&
               (XSchema.DATE.equals(dataType) || XSchema.TIME_INSTANT.equals(dataType)))
            {
               value = Tool.toGregorianPersistentDate(value);
            }

            map.put(key, new DynamicParameterValue(value, type, dataType));
         }
         else {
            Element valNode = Tool.getChildNodeByTagName(propNode, "value");
            Element valTypeNode = Tool.getChildNodeByTagName(propNode, "valueType");
            String valType = Tool.getValue(valTypeNode);
            // a date saved before #77605 may have a Buddhist or Japanese year
            Object val = Tool.getPersistentData(valType, readCDATAElement(valNode), false);
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
      return Objects.equals(taskId, that.taskId) &&
         Objects.equals(queryEntry, that.queryEntry) &&
         Objects.equals(queryParameters, that.queryParameters) &&
         Objects.equals(embeddedParameters, that.embeddedParameters);
   }

   @Override
   public String toString() {
      return "BatchAction: " + SUtil.getTaskNameWithoutOrg(taskId);
   }

   /**
    * Marks a CDATA value whose XML 1.0 illegal characters were encoded with
    * {@link Tool#encodeXMLIllegalChars}, the same marker UniformSQL uses (Bug #77806).
    */
   private static final String XML_ILLEGAL_CHARS_ATTR = "ctrlEncoded";

   private String taskId;
   private transient IdentityID taskOwner;
   private AssetEntry queryEntry;
   private Map<String, Object> queryParameters = new LinkedHashMap<>();
   private List<Map<String, Object>> embeddedParameters = new ArrayList<>();
   private static final Logger LOG =
      LoggerFactory.getLogger(BatchAction.class);
}
