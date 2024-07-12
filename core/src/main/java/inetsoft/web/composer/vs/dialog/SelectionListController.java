/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.service.VSInputService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
public class SelectionListController {
   /**
    * @param vsInputService      VSInputService instance
    * @param viewsheetService
    */
   @Autowired
   public SelectionListController(
      VSInputService vsInputService,
      ViewsheetService viewsheetService) {
      this.vsInputService = vsInputService;
      this.viewsheetService = viewsheetService;
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
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      ColumnSelection selection = this.vsInputService.getTableColumns(rvs, table, principal);
      String[] columns = selection.stream().map(DataRef::getName).toArray(String[]::new);
      String[] tooltips = new String[selection.getAttributeCount()];
      String[] dataTypes = new String[selection.getAttributeCount()];

      for(int i = 0; i < columns.length; i ++) {
         if(columns[i].isEmpty()) {
            columns[i] = "Column [" + i + "]";
         }
      }

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         ColumnRef ref = (ColumnRef) selection.getAttribute(i);
         tooltips[i] = ref.getDescription() != null ? ref.getDescription() : "";
         dataTypes[i] = ref.getDataType();
      }

      Map<String, String[]> result = new HashMap<>();
      result.put("columns", columns);
      result.put("tooltips", tooltips);
      result.put("dataTypes", dataTypes);

      return result;
   }

   private final VSInputService vsInputService;
   private final ViewsheetService viewsheetService;
}
