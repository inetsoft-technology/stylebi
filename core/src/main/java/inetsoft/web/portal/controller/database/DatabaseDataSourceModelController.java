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
import inetsoft.util.Tool;
import inetsoft.web.factory.RemainingPath;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class DatabaseDataSourceModelController {

   public DatabaseDataSourceModelController(DataSourceService dataSourceService) {
      this.dataSourceService = dataSourceService;
   }

   @GetMapping(value = "/api/data/model/checkEditable/**")
   public boolean checkDatabaseEditable(@RemainingPath String databasePath,
                                        @RequestParam(value = "folder", required = false) String folder,
                                        Principal principal)
      throws Exception
   {
      String path = Tool.byteDecode(databasePath);

      return dataSourceService.checkPermission(
         path, folder, ResourceAction.WRITE, principal);
   }

   private final DataSourceService dataSourceService;
}
