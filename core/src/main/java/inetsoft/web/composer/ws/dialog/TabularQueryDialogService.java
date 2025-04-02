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
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.TabularTableAssemblyInfo;
import inetsoft.uql.tabular.*;
import inetsoft.util.*;
import inetsoft.util.log.LogContext;
import inetsoft.web.composer.model.ws.TabularQueryDialogModel;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@ClusterProxy
public class TabularQueryDialogService extends WorksheetControllerService {

   public TabularQueryDialogService(ViewsheetService viewsheetService,
                                    SecurityEngine securityEngine,
                                    XRepository repository)
   {
      super(viewsheetService);
      this.securityEngine = securityEngine;
      this.repository = repository;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public TabularQueryDialogModel getModel(@ClusterProxyKey String runtimeId, String tableName,
                                           String dataSource, Principal principal) throws Exception
   {
      Worksheet ws = getWorksheetEngine().getWorksheet(runtimeId, principal).getWorksheet();
      TabularView tabularView = null;
      final String queryType;

      if(tableName != null) {
         TabularTableAssembly assembly = (TabularTableAssembly) ws.getAssembly(tableName);
         TabularTableAssemblyInfo info = (TabularTableAssemblyInfo) assembly.getTableInfo();
         TabularQuery query = info.getQuery();
         LayoutCreator layoutCreator = new LayoutCreator();
         tabularView = layoutCreator.createLayout(query);
         queryType = query.getType();

         if(query.getDataSource() != null) {
            dataSource = query.getDataSource().getFullName();
         }

         List<String> records = getThreadRecords(runtimeId, tableName, principal);
         TabularUtil.refreshView(tabularView, query, records, principal);
      }
      else {
         queryType = "";
      }

      XDataSource currentDS = repository.getDataSource(dataSource);
      String[] dataSourceNames = repository.getDataSourceFullNames();

      List<String> datasources = Arrays.stream(dataSourceNames)
         .sorted()
         .filter(dsname -> {
            try {
               XDataSource ds = repository.getDataSource(dsname);
               boolean sameClass = (currentDS != null && currentDS.getClass().equals(ds.getClass()));
               boolean sameType = (currentDS == null && queryType.equals(ds.getType()));
               return securityEngine.checkPermission(
                  principal, ResourceType.DATA_SOURCE, dsname, ResourceAction.READ) &&
                  (sameClass || sameType);
            }
            catch(Exception e) {
               LOG.debug("Datasource not available for dialog {}", dsname, e);
               return false;
            }
         })
         .collect(Collectors.toList());

      TabularQueryDialogModel model = new TabularQueryDialogModel();
      model.setDataSource(dataSource);
      model.setDataSources(datasources);
      model.setTableName(tableName);
      model.setTabularView(tabularView);
      return model;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setModel(@ClusterProxyKey String runtimeId, TabularQueryDialogModel model,
                        Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      String dataSource = model.getDataSource();
      TabularView tabularView = model.getTabularView();
      String name = model.getTableName();

      RuntimeWorksheet rws = getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      TabularTableAssembly assembly = name == null ? null :
         (TabularTableAssembly) ws.getAssembly(name);

      if(assembly == null) {
         name = model.getTableName() == null ?
            AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET) : model.getTableName();
         assembly = new TabularTableAssembly(ws, name);
         AssetEventUtil.adjustAssemblyPosition(assembly, ws);
         TabularQuery query = TabularUtil.createQuery(dataSource);
         List<String> records = getThreadRecords(rws.getID(), name, principal);
         TabularUtil.refreshView(tabularView, query, records, principal);
         Exception ex = null;

         try {
            setUpTable(assembly, query, dataSource, rws);
         }
         catch(Exception ex0) {
            ex = ex0;
         }

         ws.addAssembly(assembly);
         WorksheetEventUtil.createAssembly(rws, assembly, commandDispatcher, principal);

         if(ex == null) {
            WorksheetEventUtil.loadTableData(rws, name, true, true);
         }

         WorksheetEventUtil.refreshAssembly(rws, name, true, commandDispatcher, principal);
         WorksheetEventUtil.layout(rws, commandDispatcher);
      }
      else {
         TabularTableAssemblyInfo info = (TabularTableAssemblyInfo) assembly.getTableInfo();
         TabularQuery query = info.getQuery();
         XDataSource ds = null;

         try {
            if(dataSource != null) {
               ds = XFactory.getRepository().getDataSource(dataSource);
            }
         }
         catch(Exception e) {
            LOG.warn("Unable to update query datasource: " + query.getName(), e);
         }

         if(query.getDataSource() == null || ds != null &&
            (!query.getDataSource().getFullName().equals(ds.getFullName()) ||
               ds.getLastModified() > query.getDataSource().getLastModified()))
         {
            query.setDataSource(ds);
         }

         List<String> records = getThreadRecords(rws.getID(), model.getTableName(), principal);
         TabularUtil.refreshView(tabularView, query, records, principal);
         setUpTable(assembly, query, dataSource, rws);
         WorksheetEventUtil.loadTableData(rws, name, true, true);
         WorksheetEventUtil.refreshAssembly(rws, name, true, commandDispatcher, principal);
      }

      WorksheetEventUtil.refreshColumnSelection(rws, name, true);
      AssetEventUtil.refreshTableLastModified(ws, name, true);
      WorksheetEventUtil.refreshVariables(rws, super.getWorksheetEngine(), name, commandDispatcher);

      final UserMessage msg = CoreTool.getUserMessage();

      if(msg != null) {
         final MessageCommand messageCommand = MessageCommand.fromUserMessage(msg);
         messageCommand.setAssemblyName(assembly.getName());
         commandDispatcher.sendCommand(messageCommand);
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public List<String> getThreadRecords(@ClusterProxyKey String runtimeId, String tableName,
                                              Principal principal) throws Exception
   {
      ArrayList<String> records = new ArrayList<>();

      if(Thread.currentThread() instanceof GroupedThread) {
         GroupedThread parentThread = (GroupedThread) Thread.currentThread();

         for(Object record : parentThread.getRecords()) {
            if(record instanceof String) {
               records.add((String) record);
            }
         }
      }
      else if(runtimeId != null){
         RuntimeWorksheet rws = getWorksheetEngine().getWorksheet(runtimeId, principal);

         records.add(LogContext.WORKSHEET.getRecord(rws.getEntry().getPath()));

         if(tableName != null) {
            records.add(LogContext.ASSEMBLY.getRecord(tableName));
         }

         records.add(LogContext.DASHBOARD.getRecord(rws.getEntry().getPath()));
      }

      return records;
   }

   private void setUpTable(TabularTableAssembly assembly,
                           TabularQuery query, String dataSource, RuntimeWorksheet rws) throws Exception
   {
      TabularTableAssemblyInfo info = (TabularTableAssemblyInfo) assembly.getTableInfo();
      info.setQuery(query);
      SourceInfo sinfo = new SourceInfo(SourceInfo.DATASOURCE, dataSource, dataSource);
      info.setSourceInfo(sinfo);
      assembly.loadColumnSelection
         (rws.getAssetQuerySandbox().getVariableTable(), true,
          rws.getAssetQuerySandbox().getQueryManager());
   }

   private final SecurityEngine securityEngine;
   private final XRepository repository;
   private static final Logger LOG = LoggerFactory.getLogger(TabularQueryDialogController.class);
}
