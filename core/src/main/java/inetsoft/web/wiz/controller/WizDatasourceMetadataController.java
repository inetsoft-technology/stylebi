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

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.model.WizMetadataRefreshResult;
import inetsoft.web.wiz.request.WizMetadataRefreshRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/**
 * Refreshes the cached metadata of a data source for the wiz bridge.
 *
 * <p>The body is the Portal's {@code /api/portal/data/datasource/refresh-metadata}: ask the
 * repository to refresh the data source's metadata, then drop the cached default metadata provider.
 * The Portal swallows any failure and answers a bare boolean; this reports an engine exception as
 * {@code ok:false, reason:UNKNOWN} with its text instead. READ on the data source is required, as
 * the Portal endpoint requires. This is not audited in the Portal either.</p>
 */
@RestController
@RequestMapping("/api/wiz")
public class WizDatasourceMetadataController {
   public WizDatasourceMetadataController(XRepository xrepository,
                                          DataSourceService dataSourceService,
                                          SecurityEngine securityEngine)
   {
      this.xrepository = xrepository;
      this.dataSourceService = dataSourceService;
      this.gates = new WizDatasourceGates(securityEngine);
   }

   /**
    * @param request   the data source whose metadata to refresh.
    * @param principal the current user.
    *
    * @return whether the refresh ran, or why not.
    */
   @PostMapping(value = "/datasources/refresh-metadata",
                produces = MediaType.APPLICATION_JSON_VALUE)
   public WizMetadataRefreshResult refreshMetadata(@RequestBody WizMetadataRefreshRequest request,
                                                   Principal principal)
      throws Exception
   {
      String path = request == null ? null : request.path();

      if(path == null || path.isBlank() || "/".equals(path)) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path is required");
      }

      gates.requireDataSourcePermission(path, ResourceAction.READ, principal);

      try {
         XDataSource dataSource = xrepository.getDataSource(path);

         if(dataSource == null) {
            return new WizMetadataRefreshResult(
               false, WizMetadataRefreshResult.DATASOURCE_LOST, null);
         }

         // An XMLA source has no table metadata to refresh: reporting ok:true here would claim a
         // refresh that never loaded a cube.
         if(dataSource instanceof XMLADataSource) {
            return new WizMetadataRefreshResult(
               false, WizMetadataRefreshResult.XMLA_DATASOURCE,
               "XMLA data sources load cube metadata through POST /api/wiz/xmla/refresh-cubes.");
         }

         xrepository.refreshMetaData(path);
         dataSourceService.removeDefaultMetaDataProviderCache(dataSource);

         return new WizMetadataRefreshResult(true, null, null);
      }
      catch(Exception ex) {
         LOG.warn("Failed to refresh the metadata of {}: {}", path, ex.getMessage());

         return new WizMetadataRefreshResult(
            false, WizMetadataRefreshResult.UNKNOWN, ex.getMessage());
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(WizDatasourceMetadataController.class);

   private final XRepository xrepository;
   private final DataSourceService dataSourceService;
   private final WizDatasourceGates gates;
}
