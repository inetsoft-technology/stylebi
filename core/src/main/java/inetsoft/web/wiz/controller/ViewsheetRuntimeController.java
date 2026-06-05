/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import inetsoft.web.wiz.model.CloseViewsheetRequest;
import inetsoft.web.wiz.model.OpenViewsheetResult;
import inetsoft.web.wiz.service.WizVisualizationService;
import inetsoft.web.wiz.service.WizVsService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Validated
@RestController
@RequestMapping("/api/wiz")
public class ViewsheetRuntimeController {

   public ViewsheetRuntimeController(ViewsheetService viewsheetService,
                                     WizVsService wizVsService)
   {
      this.viewsheetService = viewsheetService;
      this.wizVsService = wizVsService;
   }

   /**
    * Opens a temporary wiz runtime viewsheet for the given asset entry identifier.
    * The returned runtime ID can be passed to subsequent createViewsheet calls to
    * append assemblies to the existing viewsheet.
    *
    * @param identifier the asset entry identifier of the viewsheet
    * @param user       the current principal
    * @return the runtime id plus the primary chart assembly name of the opened viewsheet
    */
   @PostMapping(value = "/viewsheet/open", produces = MediaType.APPLICATION_JSON_VALUE)
   public OpenViewsheetResult openViewsheet(@RequestParam("identifier") String identifier,
                                            Principal user) throws Exception
   {
      AssetEntry entry = AssetEntry.createAssetEntry(identifier);

      if(entry == null) {
         throw new IllegalArgumentException("Invalid viewsheet identifier: " + identifier);
      }

      String path = entry.getPath();

      // Saved wiz visualizations live under VISUALIZATION_COMPONENTS_FOLDER_PATH (where save/list/tree
      // persist them) — not the ROOT folder. The guard was checking the wrong constant, rejecting
      // every saved chart.
      if(path == null || !path.startsWith(WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/")) {
         throw new IllegalArgumentException(
            "Viewsheet is not in the managed visualizations folder: " + path);
      }

      String runtimeId = viewsheetService.openViewsheet(entry, user, true);

      OpenViewsheetResult result = new OpenViewsheetResult();
      result.setRuntimeId(runtimeId);

      VSAssembly assembly = findChartAssembly(runtimeId, user);

      if(assembly != null) {
         result.setAssemblyName(assembly.getName());
         result.setBinding(wizVsService.collectFlatBinding(assembly));
      }

      return result;
   }

   /**
    * Resolve the primary chart assembly of an opened runtime so callers can drive
    * filter/browse-data/embed (and echo its binding) without a separate lookup. Prefers a
    * chart assembly; falls back to the first assembly. Returns null if it cannot be
    * determined (non-fatal).
    */
   private VSAssembly findChartAssembly(String runtimeId, Principal user) {
      try {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, user);
         Viewsheet vs = rvs != null ? rvs.getViewsheet() : null;
         Assembly[] assemblies = vs != null ? vs.getAssemblies() : null;

         if(assemblies == null || assemblies.length == 0) {
            return null;
         }

         for(Assembly a : assemblies) {
            if(a instanceof ChartVSAssembly chart) {
               return chart;
            }
         }

         return assemblies[0] instanceof VSAssembly vsa ? vsa : null;
      }
      catch(Exception e) {
         log.warn("Could not resolve primary chart assembly for runtime {}: {}", runtimeId, e.getMessage());
         return null;
      }
   }

   /**
    * Closes the runtime viewsheet identified by the given runtime ID.
    *
    * @param request body containing the runtimeId returned by {@link #openViewsheet}
    * @param user    the current principal
    */
   @PostMapping("/viewsheet/close")
   public void closeViewsheet(@Valid @RequestBody CloseViewsheetRequest request,
                              Principal user) throws Exception
   {
      if(Tool.isEmptyString(request.getRuntimeId())) {
         throw new IllegalArgumentException("runtimeId must not be empty");
      }

      viewsheetService.closeViewsheet(request.getRuntimeId(), user);
   }

   private final ViewsheetService viewsheetService;
   private final WizVsService wizVsService;
   private static final Logger log = LoggerFactory.getLogger(ViewsheetRuntimeController.class);
}
