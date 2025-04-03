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

package inetsoft.web.composer.ws.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.filter.SortOrder;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Tool;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.condition.ConditionExpression;
import inetsoft.web.composer.model.condition.ConditionUtil;
import inetsoft.web.composer.model.ws.GroupingAssemblyDialogModel;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class GroupingAssemblyDialogService extends WorksheetControllerService {

   public GroupingAssemblyDialogService(ViewsheetService viewsheetService,
                                        DataRefModelFactoryService dataRefModelFactoryService)
   {
      super(viewsheetService);
      this.dataRefModelFactoryService = dataRefModelFactoryService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public GroupingAssemblyDialogModel getGroupingAssemblyModel(@ClusterProxyKey String runtimeId,
                                                               String groupingName, Principal principal) throws Exception
   {
      GroupingAssemblyDialogModel model = new GroupingAssemblyDialogModel();
      RuntimeWorksheet rws = super.getWorksheetEngine()
         .getWorksheet(Tool.byteDecode(runtimeId), principal);
      NamedGroupAssembly assembly = (NamedGroupAssembly) rws.getWorksheet()
         .getAssembly(groupingName);

      if(assembly == null) {
         model.setGroupAllOthers(true);
      }
      else {
         model.setOldName(assembly.getName());
         model.setType(assembly.getAttachedDataType());

         if(assembly.getAttachedSource() != null) {
            SourceInfo info = assembly.getAttachedSource();
            AssetEntry.Type type = info.getType() == SourceInfo.MODEL ?
               AssetEntry.Type.LOGIC_MODEL : AssetEntry.Type.QUERY;
            String folder = info.getProperty(SourceInfo.QUERY_FOLDER);

            if(info.getType() == SourceInfo.MODEL) {
               DataSourceRegistry registry = DataSourceRegistry.getRegistry();
               XDataModel ds = registry.getDataModel(info.getPrefix());
               XLogicalModel lg = ds.getLogicalModel(info.getSource());
               folder = lg == null ? null : lg.getFolder();
            }

            String path = info.getPrefix() + "/" +
               (folder != null ? folder + "/" : "") + info.getSource();

            AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, type, path,
                                              null);
            entry.setProperty("prefix", info.getPrefix());
            entry.setProperty("source", info.getSource());
            entry.setProperty("type", info.getType() + "");
            entry.setProperty("isJDBC", info.getProperty("isJDBC"));
            model.setOnlyFor(entry);
         }

         if(assembly.getAttachedAttribute() != null) {
            model.setAttribute(dataRefModelFactoryService
                                  .createDataRefModel(assembly.getAttachedAttribute()));
         }

         model.setGroupAllOthers(
            assembly.getNamedGroupInfo().getOthers() == SortOrder.GROUP_OTHERS);
         String[] groups = assembly.getNamedGroupInfo().getGroups();
         ConditionExpression[] expressions = new ConditionExpression[assembly
            .getNamedGroupInfo().getGroups().length];

         for(int i = 0; i < expressions.length; i++) {
            ConditionList list = assembly.getNamedGroupInfo()
               .getGroupCondition(groups[i]);
            expressions[i] = new ConditionExpression();
            expressions[i]
               .populateConditionListModel(list, this.dataRefModelFactoryService);
            expressions[i].setName(groups[i]);
         }

         model.setConditionExpressions(expressions);
      }

      model.setVariableNames(getVariableList(rws.getWorksheet()));
      return model;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setGroupingAssemblyDialogProperties(@ClusterProxyKey String runtimeId,
                                                   GroupingAssemblyDialogModel model,
                                                   Principal principal,
                                                   CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      boolean created = false;
      String name = model.getOldName();
      String dataType = model.getType();
      NamedGroupInfo groupInfo = extractGroupInfo(model, principal);
      NamedGroupAssembly assembly = (NamedGroupAssembly) ws.getAssembly(name);

      if(assembly == null) {
         created = true;
         assembly = new DefaultNamedGroupAssembly(ws, model.getNewName());
      }

      SourceInfo sourceInfo = null;
      DataRef ref = null;

      if(model.getOnlyFor() != null) {
         AssetEntry entry = model.getOnlyFor();
         sourceInfo = new SourceInfo(Integer.parseInt(entry.getProperty("type")),
                                     entry.getProperty("prefix"),
                                     entry.getProperty("source"));
         sourceInfo.setProperty("isJDBC", entry.getProperty("isJDBC"));
         ref = ConditionUtil.getOriginalDataRef(
            model.getAttribute().createDataRef(), sourceInfo, super.getWorksheetEngine(),
            principal);
      }

      assembly.setAttachedSource(sourceInfo);
      assembly.setAttachedDataType(dataType);
      assembly.setAttachedAttribute(ref);
      assembly.setNamedGroupInfo(groupInfo);
      assembly.setAttachedType(dataType == null ?
                                  NamedGroupAssembly.COLUMN_ATTACHED :
                                  NamedGroupAssembly.DATA_TYPE_ATTACHED);

      assembly.checkValidity();

      if(created) {
         assembly.setPixelOffset(new Point(25, 25));
         //bug1345491121757, change assembly position before layout.
         AssetEventUtil.adjustAssemblyPosition(assembly, ws);
         ws.addAssembly(assembly);
         WorksheetEventUtil.createAssembly(rws, assembly, commandDispatcher, principal);
         WorksheetEventUtil.focusAssembly(assembly.getName(), commandDispatcher);
      }

      // contains variable?
      if(WorksheetEventUtil.refreshVariables(
         rws, super.getWorksheetEngine(), commandDispatcher, false))
      {
         return null;
      }

      WorksheetEventUtil.refreshAssembly(
         rws, model.getNewName(), model.getOldName(), true, commandDispatcher, principal);
      WorksheetEventUtil.loadTableData(rws, name, true, true);
      WorksheetEventUtil.layout(rws, commandDispatcher);
      AssetEventUtil.refreshTableLastModified(ws, name, true);

      return null;
   }

   /**
    * Get a list of variable assembly names.
    */
   private String[] getVariableList(Worksheet ws) {
      Assembly[] arr = ws.getAssemblies();
      ArrayList<String> list = new ArrayList<>();
      Set<String> added = new HashSet<>();

      for(int i = 0; i < arr.length; i++) {
         WSAssembly assembly = (WSAssembly) arr[i];

         if(assembly.isVariable() && assembly.isVisible()) {
            UserVariable var = ((VariableAssembly) assembly).getVariable();

            if(var != null && !added.contains(var.getName())) {
               added.add(var.getName());
               list.add(var.getName());
            }
         }
      }

      return list.toArray(new String[list.size()]);
   }

   private NamedGroupInfo extractGroupInfo(
      GroupingAssemblyDialogModel model, Principal principal) throws Exception
   {
      NamedGroupInfo info = new NamedGroupInfo();
      info.setOthers(
         model.getGroupAllOthers() ? SortOrder.GROUP_OTHERS : SortOrder.LEAVE_OTHERS);

      for(int i = 0; i < model.getConditionExpressions().length; i++) {
         info.setGroupCondition(model.getConditionExpressions()[i].getName(),
                                model.getConditionExpressions()[i]
                                   .extractConditionList(model.getOnlyFor(), super.getWorksheetEngine(), principal));
      }

      return info;
   }

   private DataRefModelFactoryService dataRefModelFactoryService;
}
