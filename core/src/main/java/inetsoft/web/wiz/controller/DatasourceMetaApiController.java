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
import inetsoft.web.composer.model.*;
import inetsoft.web.wiz.WizUtil;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.model.osi.OsiDataset;
import inetsoft.web.wiz.request.GetDatabaseTableMetaRequest;
import inetsoft.web.wiz.request.SchemaSearchRequest;
import inetsoft.web.wiz.service.MetadataApiService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/wiz")
public class DatasourceMetaApiController {
   public DatasourceMetaApiController(MetadataApiService metadataService, XRepository xrepository) {
      this.metadataService = metadataService;
      this.xrepository = xrepository;
   }

   /**
    * Lists all data sources available in the repository.
    * Used by the MCP plugin's list_datasources tool.
    */
   @GetMapping(value = "/v1/datasources", produces = MediaType.APPLICATION_JSON_VALUE)
   public List<Map<String, String>> listDatasources() throws RemoteException {
      String[] names = xrepository.getDataSourceNames();
      return Arrays.stream(names)
         .sorted()
         .map(name -> {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("name", name);
            try {
               XDataSource ds = xrepository.getDataSource(name);
               entry.put("type", ds != null ? ds.getType() : "unknown");
            }
            catch(Exception e) {
               entry.put("type", "unknown");
            }
            return entry;
         })
         .collect(Collectors.toList());
   }

   @PostMapping("/datasource/table/meta")
   public OsiDataset getDatabaseTableMeta(
      @RequestBody GetDatabaseTableMetaRequest data)
      throws Exception
   {
      return metadataService.getMetaData(data);
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
   @GetMapping(value = "/v1/datasources/{datasource}/tables/{table}",
               produces = MediaType.APPLICATION_JSON_VALUE)
   public DatabaseTableMeta getTableDetails(
      @PathVariable("datasource") String datasource,
      @PathVariable("table") String table,
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

   private final MetadataApiService metadataService;
   private final XRepository xrepository;
}
