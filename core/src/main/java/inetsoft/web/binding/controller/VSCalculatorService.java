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

package inetsoft.web.binding.controller;

import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import inetsoft.analytic.composition.ViewsheetService;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class VSCalculatorService {

   public VSCalculatorService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Assembly getAssembly(@ClusterProxyKey String vsId, String assemblyName, Principal principal)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet vs = rvs.getViewsheet();
      return vs.getAssembly(assemblyName);
   }

   private ViewsheetService viewsheetService;
}