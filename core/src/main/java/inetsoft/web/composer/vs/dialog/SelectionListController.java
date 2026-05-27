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
package inetsoft.web.composer.vs.dialog;

import inetsoft.web.factory.RemainingPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.*;

@RestController
public class SelectionListController {

   @Autowired
   public SelectionListController(SelectionListServiceProxy selectionListServiceProxy)
   {
      this.selectionListServiceProxy = selectionListServiceProxy;
   }

   /**
    * Get columns of table for selection list editor
    *
    * @param path      the path to the runtime ID and table.
    * @param principal a principal that identifies the current user.
    *
    * @return the array of column names
    *
    * @throws Exception if can't retrieve columns
    */
   @GetMapping("/api/vs/selectionList/columns/**")
   public Map<String, String[]> getTableColumns(@RemainingPath String path, Principal principal)
      throws Exception
   {
      int index = path.lastIndexOf('/');
      String runtimeId = path.substring(0, index);
      String table = path.substring(index + 1);

      return selectionListServiceProxy.getTableColumns(runtimeId, table, principal);
   }


   private final SelectionListServiceProxy selectionListServiceProxy;
}
