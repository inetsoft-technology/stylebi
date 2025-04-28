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
package inetsoft.web.portal.controller.database;

import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.schema.XVariable;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.*;
import inetsoft.web.adhoc.model.FormatInfoModel;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.StringReader;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.Arrays;

/**
 * Controller that provides a REST endpoint for managing logical data models.
 */
@RestController
public class LogicalModelController {
   @Autowired
   public LogicalModelController(AssetRepository assetRepository,
                                 DataSourceService dataSourceService,
                                 LogicalModelService modelService,
                                 DatasourcesService datasourcesService,
                                 LogicalModelTreeService treeService)
   {
      this.assetRepository = assetRepository;
      this.dataSourceService = dataSourceService;
      this.modelService = modelService;
      this.datasourcesService = datasourcesService;
      this.treeService = treeService;
   }

   /**
    * Gets the selected logical model.
    * @return the DTO structure logical model
    */
   @GetMapping(value = "/api/data/logicalmodel/models")
   @ResponseBody
   public LogicalModelDefinition getModel(@RequestParam("database") String database,
                                          @RequestParam("physicalModel") String physicalModel,
                                          @RequestParam("name") String name,
                                          @RequestParam(value = "parent", required = false) String parent,
                                          Principal principal)
      throws Exception
   {
      LogicalModelDefinition model =
         modelService.getModel(database, physicalModel, name, parent, principal);
      return model;
   }

   /**
    * Creates a XLogicalModel in the datasource
    * @param event edit logical model event
    * @return the logical model if successful
    * @throws Exception if an error prevents the model from being created
    */
   @PostMapping("/api/data/logicalmodel/models")
   @ResponseBody
   @ResponseStatus(HttpStatus.CREATED)
   public LogicalModelDefinition addModel(@RequestBody EditLogicalModelEvent event,
                                          Principal principal) throws Exception
   {
      LogicalModelDefinition model = event.getModel();
      String folder = Tool.isEmptyString(model.getFolder()) ? null : model.getFolder();
      model.setFolder(folder);

      model = modelService.createModel(
         event.getDatabase(), folder, event.getPhysicalModel(), event.getModel(), event.getParent(),
         principal);
      treeService.refreshPartitionMetaData(event.getDatabase(), model);
      return model;
   }

   /**
    * Creates a XLogicalModel in the datasource
    * @param event edit logical model event
    * @return the logical model if successful
    * @throws Exception if an error prevents the model from being created
    */
   @PostMapping("/api/data/logicalmodel/extended")
   @ResponseBody
   @ResponseStatus(HttpStatus.CREATED)
   public LogicalModelDefinition addExtendedModel(@RequestBody EditLogicalModelEvent event,
                                                  Principal principal)
      throws Exception
   {
      LogicalModelDefinition model = modelService.createExtendedModel(
         event.getDatabase(), event.getPhysicalModel(), event.getModel(), event.getParent(),
         principal);
      treeService.refreshPartitionMetaData(event.getDatabase(), model);
      return model;
   }

   /**
    * Updates a XLogicalModel in the data source.
    * @param event   edit model event.
    * @return the logical model
    * @throws Exception if failed to update the logical model
    */
   @PutMapping("/api/data/logicalmodel/models")
   @ResponseBody
   public LogicalModelDefinition updateModel(@RequestBody EditLogicalModelEvent event,
                                             Principal principal)
      throws Exception
   {
      LogicalModelDefinition model = event.getModel();
      String folder = model != null ? model.getFolder() : null;

      return modelService.updateModel(
         event.getDatabase(), folder, event.getName(), model, event.getParent(), principal);
   }

   /**
    * Gets the tables of a physical model.
    * @return the list of tables.
    * @throws Exception if the model could not be obtained.
    */
   @PostMapping("/api/data/logicalModel/tables/nodes")
   public TreeNodeModel getPhysicalModelTablesTree(@RequestBody GetModelEvent event)
      throws Exception
   {
      return treeService.getPhysicalModelTree(
         event.getDatasource(), event.getPhysicalName(), event.getLogicalName(), event.getParent(),
         event.getAdditional());
   }

   /**
    * Checks if expression SQL Query is valid
    * @return the error response string
    */
   @RequestMapping(
      value = "/api/data/logicalModel/attribute/expression",
      method = RequestMethod.POST
   )
   public StringWrapper checkExpressionSQL(@RequestBody StringWrapper wrapper) {
      String expressionString = wrapper == null ? null : wrapper.getBody();

      if(expressionString == null || expressionString.trim().isEmpty()) {
         return null;
      }

      String result = null;

      inetsoft.uql.util.sqlparser.SQLLexer lexer =
         new inetsoft.uql.util.sqlparser.SQLLexer(new StringReader(expressionString));
      inetsoft.uql.util.sqlparser.SQLParser parser =
         new inetsoft.uql.util.sqlparser.SQLParser(lexer);

      try {
         parser.value_exp();
      }
      catch(Exception ex) {
         result = ex.toString();
         StringWrapper response = new StringWrapper();
         response.setBody(result);
         return response;
      }

      String token;

      try {
         token = lexer.nextToken().toString();
      }
      catch(Exception ex) {
         return null;
      }

      if(token != null && !token.toLowerCase().contains("null")) {
         result = "\"" + token + "\"";
         StringWrapper response = new StringWrapper();
         response.setBody(result);
         return response;
      }

      return null;
   }

   @GetMapping("/api/data/logicalModel/checkDuplicate")
   public boolean checkLogicalModelDuplicate(@RequestParam("database") String database,
                                             @RequestParam("name") String name)
      throws Exception
   {
      return dataSourceService.isUniqueModelName(database, name);
   }

   @GetMapping("/api/data/logicalModel/extended/checkDuplicate")
   public boolean checkExtendedModelDuplicate(@RequestParam("database") String database,
                                              @RequestParam("physicalModel") String physicalModel,
                                              @RequestParam("parent") String parent,
                                              @RequestParam("name") String name)
      throws Exception
   {
      return !dataSourceService.isUniqueExtendedLogicalModelName(database, physicalModel, parent,
         name);
   }

   /**
    * @param event   rename model event.
    * @throws Exception if the model could not be renamed.
    */
   @PutMapping("/api/data/logicalmodel/rename")
   @ResponseBody
   public void renameModel(@RequestBody RenameModelEvent event, Principal principal)
      throws Exception
   {
      modelService.renameModel(
         event.getDatabase(), event.getFolder(), event.getNewName(), event.getOldName(),
         event.getDescription(), principal);
   }

   /**
    * Deletes a logical model.
    * @param database   the database name
    * @param name       the model name
    * @throws Exception if the model could not be removed
    */
   @DeleteMapping("/api/data/logicalmodel/models")
   @ResponseBody
   public void removeModel(@RequestParam("database") String database,
                           @RequestParam("name") String name,
                           @RequestParam(value = "parent", required = false) String parent,
                           @RequestParam(value = "folder", required = false) String folder,
                           Principal principal)
      throws Exception
   {
      modelService.removeModel(database, folder, name, parent, principal);
   }

   @GetMapping("/api/data/logicalmodel/permission/editable")
   public boolean getLogicalModelEditable(@RequestParam("database") String database,
                                          @RequestParam("name") String name,
                                          @RequestParam(value = "folder", required = false) String folder,
                                          Principal principal)
   {
      String databasePath = Tool.byteDecode(database);

      return modelService.checkPermission(databasePath, folder, name, null,
         ResourceAction.WRITE, principal);
   }

   /**
    * Gets display string for selected attribute format
    * @param format the FormatInfo
    * @return the formatted string
    */
   @PostMapping("/api/data/logicalModel/attribute/format")
   @ResponseBody
   public String getFormatString(@RequestBody AttributeFormatInfoModel format) {
      String formatStr = FormatInfoModel.getDurationFormat(format.getFormat(),
         format.isDurationPadZeros());
      XFormatInfo xFormat = new XFormatInfo(formatStr, format.getFormatSpec());
      return xFormat.toString();
   }

   @GetMapping("/api/data/logicalModel/vs/autoDrill-parameters")
   public String[] getViewsheetParameters(@RequestParam("assetId") String assetId,
                                          Principal principal)
      throws Exception
   {
      AssetEntry entry = AssetEntry.createAssetEntry(assetId);
      Viewsheet vs = (Viewsheet)
         assetRepository.getSheet(entry, principal, false, AssetContent.NO_DATA);

      if(vs == null) {
         return new String[0];
      }

      return Arrays.stream(vs.getAllVariables())
         .map(XVariable::getName)
         .toArray(String[]::new);
   }

   @GetMapping("/api/data/logicalmodel/settings")
   public LogicalModelSettings getLMHierarchyEnableProperty(@RequestParam("ds") String ds)
      throws RemoteException
   {
      XDataSource dataSource = dataSourceService.getDataSource(ds);
      SQLHelper sqlHelper = dataSourceService.getSqlHelper(dataSource, null);

      boolean fullDateSupport = sqlHelper.supportFunction(SQLHelper.FULL_YEAR_FUNCTION)
         && sqlHelper.supportFunction(SQLHelper.FULL_QUARTER_FUNCTION)
         && sqlHelper.supportFunction(SQLHelper.FULL_MONTH_FUNCTION)
         && sqlHelper.supportFunction(SQLHelper.FULL_DAY_FUNCTION);

      return new LogicalModelSettings(fullDateSupport);
   }

   /**
    * Checks if target entities/attributes have outer dependencies.
    * @return the dependencies exception string
    */
   @RequestMapping(
      value = "/api/data/logicalmodel/checkOuterDependencies",
      method = RequestMethod.POST
   )
   public StringWrapper checkOuterDependencies(@RequestBody CheckDependenciesEvent event)
      throws Exception
   {
      if(event.isNewCreate()) {
         return null;
      }

      String dataSource = event == null ? null : event.getDatabaseName();
      String parent = event == null ? null : event.getParent();
      String modelName = event == null ? null : event.getModelName();
      ElementModel[] elems = event == null ? null : event.getModelElements();
      Catalog catalog = Catalog.getCatalog();

      if(dataSource == null || modelName == null) {
         return null;
      }

      XDataModel dataModel = modelService.getDataModel(dataSource);
      XLogicalModel logicalModel = null;

      if(parent != null) {
         logicalModel = dataModel.getLogicalModel(parent);

         if(logicalModel == null) {
            throw new MessageException(
               catalog.getString("data.logicalmodel.cannotFind", parent));
         }

         logicalModel = logicalModel.getLogicalModel(modelName);
      }
      else {
         logicalModel = dataModel.getLogicalModel(modelName);
      }

      if(logicalModel == null) {
         throw new MessageException(
            catalog.getString("data.logicalmodel.cannotFind", modelName));
      }

      try {
         if(elems == null) {
            datasourcesService.checkModelOuterDependencies(logicalModel);
         }
         else {
            for(ElementModel elem : elems) {
               checkOuterDependencies(logicalModel, elem);
            }
         }
      }
      catch(Exception ex) {
         StringWrapper wrapper = new StringWrapper();
         wrapper.setBody(ex.getMessage());
         return wrapper;
      }

      return null;
   }

   private void checkOuterDependencies(XLogicalModel logicalModel, ElementModel model) {
      if(model == null || model.getOldName() == null) {
         return;
      }

      String name = model.getOldName();
      DependencyException ex = null;

      if(model instanceof EntityModel) {
         XEntity entity = logicalModel.getEntity(name);
         ex = entity.getDependencyException();
      }
      else if(model instanceof XAttributeModel) {
         String entityName = ((XAttributeModel) model).getParentEntity();
         XEntity entity = logicalModel.getEntity(entityName);
         XAttribute attribute = entity.getAttribute(name);
         ex = attribute.getDependencyException();
      }

      if(ex != null) {
         throw ex;
      }
   }

   private final AssetRepository assetRepository;
   private final DataSourceService dataSourceService;
   private final LogicalModelService modelService;
   private final DatasourcesService datasourcesService;
   private final LogicalModelTreeService treeService;
}