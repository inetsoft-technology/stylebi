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

package inetsoft.web.wiz.controller;

import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.AbstractJoinTableAssembly;
import inetsoft.uql.asset.UnpivotTableAssembly;
import inetsoft.uql.asset.MirrorTableAssembly;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.*;
import inetsoft.util.Tool;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.request.GetDatabaseTableMetaRequest;
import inetsoft.web.wiz.request.GetWorksheetMetaRequest;
import inetsoft.web.wiz.service.MetadataService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class DatasourceMetaController {
   public DatasourceMetaController(MetadataService metadataService) {
      this.metadataService = metadataService;
   }

   @GetMapping(value = "/api/datasource/table/meta")
   public DatabaseTableMeta getMetaData(@RequestBody GetDatabaseTableMetaRequest data)
      throws Exception
   {

      return metadataService.getMetaData(data);
   }

   @GetMapping(value = "/api/ws/meta")
   public WorksheetMeta getWorksheetMetaData(@RequestBody GetWorksheetMetaRequest data, XPrincipal principal)
      throws Exception
   {
      return metadataService.getWorksheetMetaData(data.getWorksheetId(), principal);
   }

   private final MetadataService metadataService;
}
