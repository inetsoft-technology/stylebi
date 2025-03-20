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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.binding.drm.AggregateRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
public class VSFormulaController {

   public VSFormulaController(VSFormulaServiceProxy vsFormulaServiceProxy) {
      this.vsFormulaServiceProxy = vsFormulaServiceProxy;
   }

   @RequestMapping(value = "/api/composer/vsformula/fields", method=RequestMethod.GET)
   public Map<String, Object> getFields(@DecodeParam("vsId") String vsId,
                              @RequestParam("assemblyName") String assemblyName,
                              @RequestParam(value="tableName", required=false) String tableName,
      Principal principal)
      throws Exception
   {
      return vsFormulaServiceProxy.getFields(vsId, assemblyName, tableName, principal);
   }

   /**
    * ModifyAggregateFieldEvent.
    */
   @RequestMapping(value = "/api/composer/modifyAggregateField", method=RequestMethod.PUT)
   public void modifyAggregateField(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("tname") String tname,
      @RequestBody Map<String, AggregateRefModel> model,
      Principal principal) throws Exception
   {
      vsFormulaServiceProxy.modifyAggregateField(vsId, assemblyName, tname, model, principal);
   }

   private VSFormulaServiceProxy vsFormulaServiceProxy;
}
