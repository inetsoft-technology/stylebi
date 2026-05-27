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

package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Tool;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class SelectionListService {

   public SelectionListService(VSColumnHandler vsColumnHandler, ViewsheetService viewsheetService,
                               VSAssemblyInfoHandler infoHandler)
   {
      this.vsColumnHandler = vsColumnHandler;
      this.viewsheetService = viewsheetService;
      this.infoHandler = infoHandler;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Map<String, String[]> getTableColumns(@ClusterProxyKey String runtimeId, String table, Principal principal)
      throws Exception
   {

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      ColumnSelection selection = vsColumnHandler.getTableColumns(rvs, table, principal);
      String[] columns = selection.stream().map(DataRef::getName).toArray(String[]::new);
      String[] tooltips = new String[selection.getAttributeCount()];
      String[] dataTypes = new String[selection.getAttributeCount()];

      for(int i = 0; i < columns.length; i++) {
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
      result.put("grayedOutValues", getGrayedOutValues(rvs, table));

      return result;
   }

   private String[] getGrayedOutValues(RuntimeViewsheet rvs, String table) {
      boolean isModel = rvs.getViewsheet().getBaseEntry().isLogicModel();
      DataRefModel[] grayedOutFields = infoHandler.getGrayedOutFields(rvs);
      ArrayList<String> flds = new ArrayList<>();

      for(int i = 0; grayedOutFields != null && i < grayedOutFields.length; i++) {
         String fld = grayedOutFields[i].getName();

         if(isModel) {
            fld = fld.replace(".", ":");
            flds.add(fld);
         }
         else if(Tool.equals(table, grayedOutFields[i].getEntity())) {
            flds.add(grayedOutFields[i].getAttribute());
         }
      }

      return flds.toArray(new String[0]);
   }

   private ViewsheetService viewsheetService;
   private final VSColumnHandler vsColumnHandler;
   private final VSAssemblyInfoHandler infoHandler;
}
