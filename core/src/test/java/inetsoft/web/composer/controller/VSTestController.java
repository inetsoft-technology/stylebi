/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
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
   public VSTestController(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
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
      RuntimeViewsheet nrvs = engine.getViewsheet(id, principal);
      VariableTable variables = nrvs.getViewsheetSandbox().getVariableTable();
      nrvs.getViewsheetSandbox().getAssetQuerySandbox().refreshVariableTable(variables);
      nrvs.getViewsheetSandbox().reset(null, nrvs.getViewsheet().getAssemblies(),
         new ChangedAssemblyList(), true, true, null);

      return id;
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
      try {
         ViewsheetService engine = viewsheetService;
         RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
         Viewsheet vs = rvs.getViewsheet();
         ChartVSAssembly chartAssembly = (ChartVSAssembly) vs.getAssembly(objectId);
         ChartArea chartArea = getChartArea0(rvs, chartAssembly, linkUri);

         return chartArea;
      } catch(Exception e) {
         e.printStackTrace();
      }

      return null;
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
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);

      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return 0;
      }

      try {
         String oname = name;
         boolean detail = oname.startsWith(Assembly.DETAIL);

         if(detail) {
            oname = oname.substring(Assembly.DETAIL.length());
         }

         try {
            VSAssembly assembly = (VSAssembly) rvs.getViewsheet().getAssembly(oname);

            if(!VSEventUtil.isVisibleTabVS(assembly, rvs.isRuntime())) {
               return 0;
            }
         }
         catch(Exception ex) {
            //ignore, not expecting any exception here.
         }

         VSTableLens lens = box.getVSTableLens(oname, detail);

         if(lens == null || Util.isTimeoutTable(lens)) {
            return 0;
         }

         int ccount = lens.getColCount();

         return ccount;
      }catch(Exception e) {
         e.printStackTrace();
         throw new Exception(e);
      }
   }

   private ChartArea getChartArea0(RuntimeViewsheet rvs, ChartVSAssembly chartAssembly,
                                 String linkUri)
      throws Exception
   {
      // Get ChartArea, using mechanism lifted from GetChartAreaEvent
      ChartArea chartArea;

      try {
         ViewsheetSandbox box = rvs.getViewsheetSandbox();
         box.setUser(SUtil.getPrincipal(new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getCurrentOrgName()), null, false));

         VSChartInfo cinfo = chartAssembly.getVSChartInfo();
         final String absoluteName = chartAssembly.getAbsoluteName();
         cinfo.setLocalMap(
            VSUtil.getLocalMap(rvs.getViewsheet(), absoluteName));

         VGraphPair pair = box.getVGraphPair(absoluteName, true, null);

         if(pair != null && !pair.isCompleted()) {
            box.clearGraph(absoluteName);
            pair = box.getVGraphPair(absoluteName, true, null);
         }

         XCube cube = chartAssembly.getXCube();
         boolean drill = !rvs.isTipView(absoluteName) &&
            ((ChartVSAssemblyInfo) chartAssembly.getInfo()).isDrillEnabled();

         if(cube == null) {
            SourceInfo src = chartAssembly.getSourceInfo();

            if(src != null) {
               cube = AssetUtil.getCube(src.getPrefix(), src.getSource());
            }
         }

         chartArea = pair == null || !pair.isCompleted()
            ? null : new ChartArea(pair, linkUri, cinfo, cube, drill);

      } catch(Exception ex) {
         ex.printStackTrace();
         return null;
      }

      return chartArea;
   }

   private final ViewsheetService viewsheetService;
}