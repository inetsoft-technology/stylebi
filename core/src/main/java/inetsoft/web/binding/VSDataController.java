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
package inetsoft.web.binding;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.internal.ManualOrderComparer;
import inetsoft.report.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.report.filter.ConditionGroup;
import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.event.GetAvailableValuesEvent;
import inetsoft.web.binding.handler.VSChartDataHandler;
import inetsoft.web.binding.model.ValueLabelListModel;
import inetsoft.web.binding.model.ValueLabelModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.composer.ws.assembly.VariableAssemblyModelInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class VSDataController {
   /*
   * Injection variable in constructor
   */
   @Autowired
   public VSDataController(VSDataServiceProxy vsDataServiceProxy) {
      this.vsDataServiceProxy = vsDataServiceProxy;
   }

   /**
    * Converts elements of empty strings in the the manual order list collection to null.
    * @param list the manual order list.
    * @return the manual order after successful conversion.
    */
   public static List fixNull(List list) {
      if(list == null || list.isEmpty()) {
         return list;
      }

      for(int i = 0 ; i <list.size(); i++) {
         list.set(i, "".equals(list.get(i)) ? null : list.get(i));
      }

      return list;
   }

   /**
    * Get all available attributes.
    */
   @RequestMapping(value = "/api/vsdata/availableValues", method = RequestMethod.PUT)
   public ValueLabelListModel getAvailableValues(@RequestParam("vsId") String vsId,
                                                 @RequestParam("assemblyName") String assemblyName,
                                                 @RequestParam(value = "row", required = false) Integer row,
                                                 @RequestParam(value = "col", required = false) Integer col,
                                                 @RequestParam(value = "dateLevel", required = false) Integer dateLevel,
                                                 @RequestBody GetAvailableValuesEvent event, Principal principal) throws Exception
   {
      return vsDataServiceProxy.getAvailableValues(vsId, assemblyName, row, col, dateLevel, event, principal);
   }

   /**
    * Get all variables required to run the query
    */
   @GetMapping(value = "/api/vsdata/check-variables")
   public List<VariableAssemblyModelInfo> getRequiredVariables(@RequestParam("vsId") String vsId,
                                                               @RequestParam("assemblyName") String assemblyName,
                                                               Principal principal) throws Exception
   {
      return vsDataServiceProxy.getRequiredVariables(vsId, assemblyName, principal);
   }

   private final VSDataServiceProxy vsDataServiceProxy;
}
