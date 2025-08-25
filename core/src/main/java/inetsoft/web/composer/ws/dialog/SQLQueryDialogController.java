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
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.security.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.BrowseDataModel;
import inetsoft.web.composer.model.ws.BasicSQLQueryModel;
import inetsoft.web.composer.model.ws.SQLQueryDialogModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller that provides the REST endpoints for the sql query dialog.
 *
 * @since 12.3
 */
@Controller
public class SQLQueryDialogController extends WorksheetController {
   public SQLQueryDialogController(SQLQueryDialogServiceProxy dialogService) {
      this.dialogService = dialogService;
   }

   /**
    * Gets the model of the sql query dialog
    *
    * @param runtimeId the runtime identifier of the worksheet.
    * @return the model object.
    */
   @RequestMapping(
      value = "/api/composer/ws/sql-query-dialog-model",
      method = RequestMethod.GET)
   @ResponseBody
   public SQLQueryDialogModel getModel(@RequestParam("runtimeId") String runtimeId,
                                       @RequestParam(name = "tableName", required = false) String tableName,
                                       @RequestParam(name = "dataSource", required = false) String dataSource,
      Principal principal) throws Exception
   {
      return dialogService.getModel(runtimeId, tableName, dataSource, principal);
   }

   /**
    * Gets the model of the sql query dialog
    *
    * @return the model object.
    */
   @RequestMapping(
      value = "/api/data/condition/subquery/sql-query-dialog-model",
      method = RequestMethod.GET)
   @ResponseBody
   public SQLQueryDialogModel getSubQueryModel(
      @RequestParam(name = "dataSource", required = false) String dataSource,
      Principal principal) throws Exception
   {
      boolean sqlEnabled = SecurityEngine.getSecurity().checkPermission(
         principal, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS);

      if(!sqlEnabled) {
         throw new MessageException(
            Catalog.getCatalog().getString("composer.nopermission.physicalTable"));
      }

      SQLQueryDialogModel model = new SQLQueryDialogModel();
      model.setAdvancedEdit(false);
      model.setDataSource(dataSource);
      model.setSimpleModel(new BasicSQLQueryModel());
      List<String> datasources = new ArrayList<>();
      datasources.add(dataSource);
      model.setDataSources(datasources);

      try {
         model.setPhysicalTablesEnabled(securityEngine.checkPermission(
            principal, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS));
         model.setFreeFormSqlEnabled(securityEngine.checkPermission(
            principal, ResourceType.FREE_FORM_SQL, "*", ResourceAction.ACCESS));
      }
      catch(Exception ignore) {
         model.setPhysicalTablesEnabled(false);
         model.setFreeFormSqlEnabled(false);
      }

      return model;
   }

   @PostMapping("/api/composer/ws/sql-query-dialog/clear")
   @ResponseBody
   public SQLQueryDialogModel clearQuery(@RequestParam("runtimeId") String runtimeId,
                                         @RequestParam("dataSource") String dataSource,
                                         @RequestParam("tableName") String tableName,
                                         @RequestParam("advancedEdit") boolean advancedEdit,
                                         Principal principal)
      throws Exception
   {
      return queryManagerService.clearQuery(runtimeId, dataSource, tableName, advancedEdit, principal);
   }

   /**
    * Browses the available data for the given data ref
    *
    * @param runtimeId the runtime identifier of the worksheet.
    * @return the updated model.
    */
   @RequestMapping(
      value = "/api/composer/ws/sql-query-dialog/browse-data",
      method = RequestMethod.POST)
   @ResponseBody
   public BrowseDataModel browseData(@RequestParam("runtimeId") String runtimeId,
                                     @RequestParam("dataSource") String dataSource,
                                     @RequestBody ColumnRefModel dataRefModel,
                                     Principal principal) throws Exception
   {
      return dialogService.browseData(runtimeId, dataSource, dataRefModel, principal);
   }

   @RequestMapping(
      value = "/api/composer/ws/sql-query-dialog/get-sql-string",
      method = RequestMethod.POST)
   @ResponseBody
   public String getSQLString(@RequestBody SQLQueryDialogModel model, Principal principal)
      throws Exception
   {
      BasicSQLQueryModel sqlQueryModel = model.getSimpleModel();
      String dataSource = model.getDataSource();
      String[] selectedColumns = sqlQueryModel.getSelectedColumns();
      XJoin[] joins = sqlQueryModel.toXJoins();
      JDBCDataSource jdbcDataSource =
         (JDBCDataSource) xrepository.getDataSource(dataSource);
      UniformSQL sql = JDBCUtil.createSQL(jdbcDataSource, sqlQueryModel.getTables(),
         selectedColumns, joins, sqlQueryModel.getConditionList(), principal);
      return sql.getSQLString();
   }

   @RequestMapping(
      value = "/api/composer/ws/sql-query-dialog/get-sql-parse-result",
      method = RequestMethod.POST)
   @ResponseBody
   public String getSqlParseResult(@RequestBody String sqlParseResult, Principal principal) {
      UniformSQL sql = new UniformSQL(sqlParseResult);

      synchronized(sql) {
         if(sql.getParseResult() == UniformSQL.PARSE_INIT) {
            try {
               sql.wait();
            }
            catch(InterruptedException ignore) {
            }
         }

      }

      return JDBCUtil.getParseResult(sql.getParseResult());
   }

   @RequestMapping(
      value = "/api/composer/ws/sql-query-dialog/table-columns",
      method = RequestMethod.POST)
   @ResponseBody
   public AssetEntry[] getTableColumns(@RequestBody AssetEntry tableEntry,
                                       Principal principal)
      throws Exception
   {
      WorksheetService engine = getWorksheetEngine();
      return engine.getAssetRepository().getEntries(tableEntry, principal, ResourceAction.READ);
   }

   @PostMapping("/api/composer/ws/sql-query-dialog/change-edit-mode")
   @ResponseBody
   public SQLQueryDialogModel changeEditMode(@RequestBody SQLQueryDialogModel model,
                                             @RequestParam("runtimeWsId") String runtimeWsId,
                                             @RequestParam("runtimeQueryId") String runtimeQueryId,
                                             @RequestParam("advancedEdit") boolean advancedEdit,
                                             Principal principal)
      throws Exception
   {
      return dialogService.changeEditMode(runtimeWsId, runtimeQueryId, advancedEdit, model, principal);
   }

   @PostMapping("/api/composer/ws/sql-query-dialog/query/update")
   @ResponseBody
   public void updateQueryBySample(@RequestBody() BasicSQLQueryModel model,
                                   @RequestParam("runtimeId") String runtimeId,
                                   @RequestParam("datasource") String datasource,
                                   Principal principal)
      throws Exception
   {
      queryManagerService.updateQuery(runtimeId, model, datasource, principal);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/ws/dialog/sql-query-dialog-model")
   public void setModel(@Payload SQLQueryDialogModel model,
                        Principal principal,
                        CommandDispatcher commandDispatcher)
      throws Exception
   {
      boolean sqlEnabled = SecurityEngine.getSecurity().checkPermission(
         principal, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS);

      if(!sqlEnabled) {
         throw new MessageException(
            Catalog.getCatalog().getString("composer.nopermission.physicalTable"));
      }

      dialogService.setModel(getRuntimeId(), model, principal, commandDispatcher);
   }

   @Autowired
   public void setDataRefModelFactoryService(DataRefModelFactoryService dataRefModelFactoryService) {
   }

   @Autowired
   public void setXRepository(XRepository xrepository) {
      this.xrepository = xrepository;
   }

   @Autowired
   public void setSecurityEngine(SecurityEngine securityEngine) {
      this.securityEngine = securityEngine;
   }

   @Autowired
   public void setQueryManagerService(QueryManagerService queryManagerService) {
      this.queryManagerService = queryManagerService;
   }

   private XRepository xrepository;
   private SecurityEngine securityEngine;
   private QueryManagerService queryManagerService;
   private final SQLQueryDialogServiceProxy dialogService;
}
