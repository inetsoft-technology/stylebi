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
package inetsoft.web.composer.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.report.internal.Util;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
public class VSTestController {

   @Autowired
   public VSTestController(ViewsheetService viewsheetService, VSTestServiceProxy vsTestService) {
      this.viewsheetService = viewsheetService;
      this.vsTestService = vsTestService;
   }

   @RequestMapping(
      value = "/test/vs/openViewsheet",
      method = RequestMethod.POST
   )
   @ResponseBody
   public String openViewsheet(
         @RequestParam("identifier") String identifier,
         @RequestParam(value = "viewer", defaultValue = "false") boolean viewer,
         Principal principal
      ) throws Exception {
      AssetEntry entry = AssetEntry.createAssetEntry(identifier);

      ViewsheetService engine = viewsheetService;
      String id  = engine.openViewsheet(entry, principal, viewer);

      return vsTestService.openViewsheet(id, principal);
   }

   @RequestMapping(
      value = "/test/vs/getChartArea",
      method = RequestMethod.POST
   )
   @ResponseBody
   public ChartArea getChartArea(
         @RequestParam("viewsheetId") String vsId,
         @RequestParam("objectId") String objectId,
         @LinkUri String linkUri,
         Principal principal
      ) throws Exception
   {
      return vsTestService.getChartArea(vsId, objectId, linkUri, principal);
   }

   @RequestMapping(
      value = "/test/vs/loadTableLensCount",
      method = RequestMethod.POST
   )
   @ResponseBody
   public Object loadTableLensCount(
         @RequestParam("viewsheetId") String vsId,
         @RequestParam("name") String name,
         Principal principal
      ) throws Exception
   {
      return vsTestService.loadTableLensCount(vsId, name, principal);
   }


   private final ViewsheetService viewsheetService;
   private final VSTestServiceProxy vsTestService;
}