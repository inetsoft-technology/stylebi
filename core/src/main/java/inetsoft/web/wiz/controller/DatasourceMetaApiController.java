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

package inetsoft.web.wiz.controller;

import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.web.composer.model.*;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.WizUtil;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.model.osi.OsiDataset;
import inetsoft.web.wiz.request.GetDatabaseTableMetaRequest;
import inetsoft.web.wiz.request.SchemaSearchRequest;
import inetsoft.web.wiz.service.MetadataApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/wiz")
public class DatasourceMetaApiController {
   public DatasourceMetaApiController(MetadataApiService metadataService,
                                      XRepository xrepository,
                                      DataSourceService dataSourceService)
   {
      this.metadataService = metadataService;
      this.xrepository = xrepository;
      this.dataSourceService = dataSourceService;
   }

   /**
    * Lists all data sources available in the repository that the user has READ permission for.
    * Used by the MCP plugin's list_datasources tool.
    */
   @GetMapping(value = "/v1/datasources", produces = MediaType.APPLICATION_JSON_VALUE)
   public List<Map<String, String>> listDatasources(Principal principal) throws Exception {
      String[] names = xrepository.getDataSourceFullNames();
      List<Map<String, String>> result = new ArrayList<>();

      for(String name : Arrays.stream(names).sorted().toArray(String[]::new)) {
         try {
            if(!dataSourceService.checkPermission(name, ResourceAction.READ, principal)) {
               continue;
            }

            XDataSource ds = xrepository.getDataSource(name);
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("type", ds != null ? ds.getType() : "unknown");
            result.add(entry);
         }
         catch(Exception e) {
            LOG.debug("Skipping datasource {} due to error: {}", name, e.getMessage());
         }
      }

      return result;
   }

   @PostMapping("/datasource/table/meta")
   public OsiDataset getDatabaseTableMeta(
      @RequestBody GetDatabaseTableMetaRequest data,
      Principal principal)
      throws Exception
   {
      return metadataService.getMetaData(data, principal);
   }

   @GetMapping("/ws/meta/{id}")
   public WorksheetMeta getWorksheetMeta(
      @PathVariable("id") String worksheetId,
      Principal principal)  throws Exception
   {
      return metadataService.getWorksheetMetaData(WizUtil.decodeId(worksheetId), (XPrincipal) principal);
   }

   /**
    * Gets all tables and FK relationships for the specified datasource.
    *
    * @param dsPath the datasource name or path.
    * @return tables (with catalog/schema/type) and OSI relationships.
    */
   @GetMapping("/datasource/tables")
   public DatasourceTablesResponse getDatabaseTables(
      @RequestParam("dsPath") String dsPath,
      XPrincipal principal)
      throws Exception
   {
      return metadataService.getDatabaseTables(dsPath, principal);
   }

   /**
    * Gets the child nodes of the specified parent node.
    *
    * @return the child nodes.
    */
   @PostMapping("/datasets/asset_tree")
   public TreeNodeModel getNodes(
      @RequestBody(required = false) LoadAssetTreeNodesEvent event,
      Principal principal)
      throws Exception
   {
      if(event == null) {
         event = LoadAssetTreeNodesEvent.builder().build();
      }

      return metadataService.getNodes(event, principal).treeNodeModel();
   }

   /**
    * Gets column metadata for a specific table in a datasource.
    * Used by the MCP plugin's get_table_details tool.
    *
    * @param datasource the datasource name.
    * @param table      the table name.
    * @param catalog    optional catalog name.
    * @param schema     optional schema name.
    * @param principal  the current user.
    * @return column metadata for the table.
    */
   @GetMapping(value = "/v1/table-details",
               produces = MediaType.APPLICATION_JSON_VALUE)
   public DatabaseTableMeta getTableDetails(
      @RequestParam("datasource") String datasource,
      @RequestParam("table") String table,
      @RequestParam(value = "catalog", required = false) String catalog,
      @RequestParam(value = "schema", required = false) String schema,
      Principal principal)
      throws Exception
   {
      return metadataService.getTableDetails(datasource, table, catalog, schema, principal);
   }

   /**
    * Searches for tables and columns matching a keyword across all datasources.
    * Used by the MCP plugin's search_schema tool.
    *
    * @param request   the search request containing query and optional field names.
    * @param principal the current user.
    * @return matching tables with their matched columns.
    */
   @PostMapping(value = "/v1/schema/search", produces = MediaType.APPLICATION_JSON_VALUE)
   public SchemaSearchResponse searchSchema(
      @RequestBody SchemaSearchRequest request,
      Principal principal)
      throws Exception
   {
      return metadataService.searchSchema(request, principal);
   }

   /**
    * Lists logical models (with entities/attributes) for a given datasource that the user has
    * READ permission for. Checks both datasource-level and per-model permissions.
    * Used by the MCP plugin's list_logical_models tool.
    *
    * @param datasource the datasource name (e.g. "Examples/Orders")
    * @param principal  the current user
    * @return list of logical models, each with entity names and attribute metadata
    */
   @GetMapping(value = "/v1/logical-models", produces = MediaType.APPLICATION_JSON_VALUE)
   public List<Map<String, Object>> listLogicalModels(
      @RequestParam("datasource") String datasource,
      Principal principal)
      throws Exception
   {
      if(!dataSourceService.checkPermission(datasource, ResourceAction.READ, principal)) {
         return Collections.emptyList();
      }

      XDataModel dataModel = dataSourceService.getDataModel(datasource);

      if(dataModel == null) {
         return Collections.emptyList();
      }

      List<Map<String, Object>> result = new ArrayList<>();

      for(String modelName : dataModel.getLogicalModelNames()) {
         XLogicalModel lm = dataModel.getLogicalModel(modelName);

         if(lm == null) {
            continue;
         }

         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.LOGIC_MODEL, datasource + "/" + modelName, null);
         entry = dataSourceService.getModelAssetEntry(entry);

         if(entry == null ||
            !dataSourceService.checkPermission(entry, ResourceAction.READ, principal))
         {
            continue;
         }

         List<Map<String, Object>> entities = new ArrayList<>();
         Enumeration<XEntity> entityEnum = lm.getEntities();

         while(entityEnum.hasMoreElements()) {
            XEntity entity = entityEnum.nextElement();
            List<Map<String, String>> attributes = new ArrayList<>();
            Enumeration<XAttribute> attrEnum = entity.getAttributes();

            while(attrEnum.hasMoreElements()) {
               XAttribute attr = attrEnum.nextElement();
               Map<String, String> attrMap = new LinkedHashMap<>();
               attrMap.put("name", attr.getName());
               attrMap.put("type", attr.getDataType());
               attributes.add(attrMap);
            }

            Map<String, Object> entityMap = new LinkedHashMap<>();
            entityMap.put("name", entity.getName());
            entityMap.put("attributes", attributes);
            entities.add(entityMap);
         }

         // Include physical view join relationships so agents know how entities relate.
         List<Map<String, String>> joins = new ArrayList<>();
         String partitionName = lm.getPartition();

         if(partitionName != null) {
            XPartition partition = dataModel.getPartition(partitionName);

            if(partition != null) {
               Enumeration<XRelationship> relEnum = partition.getRelationships();

               while(relEnum.hasMoreElements()) {
                  XRelationship rel = relEnum.nextElement();
                  Map<String, String> joinMap = new LinkedHashMap<>();
                  joinMap.put("table", rel.getDependentTable());
                  joinMap.put("column", rel.getDependentColumn());
                  joinMap.put("foreignTable", rel.getIndependentTable());
                  joinMap.put("foreignColumn", rel.getIndependentColumn());
                  joinMap.put("joinType", rel.getJoinType());
                  joins.add(joinMap);
               }
            }
         }

         Map<String, Object> modelMap = new LinkedHashMap<>();
         modelMap.put("name", modelName);
         modelMap.put("physicalView", partitionName);
         modelMap.put("entities", entities);
         modelMap.put("joins", joins);
         result.add(modelMap);
      }

      return result;
   }

   // A permission denial must surface as 403, not be swallowed to 400 by the catch-all below.
   // More specific than the Exception handler, so it wins for SecurityException within this
   // controller (a local handler also takes precedence over WizControllerErrorHandler).
   @ExceptionHandler({ inetsoft.sree.security.SecurityException.class, java.lang.SecurityException.class })
   @ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
   @ResponseBody
   public Map<String, String> handleSecurityException(Exception e) {
      LOG.warn("Unauthorized datasource metadata access: {}", e.getMessage());
      return Map.of("error", "Forbidden",
                    "message", e.getMessage() != null ? e.getMessage() : "Forbidden");
   }

   @ExceptionHandler(Exception.class)
   @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
   @ResponseBody
   public Map<String, String> handleException(Exception e) {
      LOG.warn("DatasourceMetaApi error: {}", e.getMessage(), e);
      return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
   }

   private static final Logger LOG = LoggerFactory.getLogger(DatasourceMetaApiController.class);

   private final MetadataApiService metadataService;
   private final XRepository xrepository;
   private final DataSourceService dataSourceService;
}
