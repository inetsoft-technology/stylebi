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

package inetsoft.web.admin.content.dataspace;

import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.admin.content.dataspace.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
public class DataSpaceShapeTreeController {
   @Autowired
   public DataSpaceShapeTreeController(DataSpaceContentSettingsService dataSpaceContentSettingsService) {
      this.dataSpaceContentSettingsService = dataSpaceContentSettingsService;
   }

   @GetMapping("/api/em/content/data-space/shapes/tree")
   public DataSpaceTreeModel getDataSpaceTree(
      @DecodeParam(value = "path", required = false) String parentPath,
      @RequestParam(value = "init", required = false) boolean init)
      throws Exception
   {
      if(init) {
         DataSpaceTreeModel tree = dataSpaceContentSettingsService.getTree(parentPath);
         DataSpaceTreeNodeModel shapes = DataSpaceTreeNodeModel.builder().label("shapes")
            .path(parentPath)
            .folder(true)
            .children(tree.nodes())
            .build();
         List<DataSpaceTreeNodeModel> models = new ArrayList<>();
         models.add(shapes);

         return DataSpaceTreeModel.builder().nodes(models).build();
      }

      return dataSpaceContentSettingsService.getTree(parentPath);
   }

   private final DataSpaceContentSettingsService dataSpaceContentSettingsService;
}
