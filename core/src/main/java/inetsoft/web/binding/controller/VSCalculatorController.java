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
package inetsoft.web.binding.controller;

import inetsoft.uql.asset.Assembly;
import inetsoft.util.Catalog;
import inetsoft.web.binding.handler.CalculatorHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.model.DimensionInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

/**
 * Controller that provides a REST endpoint for composer viewsheet actions.
 */
@RestController
public class VSCalculatorController {

   public VSCalculatorController(VSCalculatorServiceProxy vsCalculatorServiceProxy) {
      this.vsCalculatorServiceProxy = vsCalculatorServiceProxy;
   }

   @RequestMapping(value = "/api/composer/dims", method = RequestMethod.GET)
   @ResponseBody
   public Map<String, List<DimensionInfo>> getDimensionInfos(@RequestParam("vsId") String vsId,
                                               @RequestParam("assemblyName") String assemblyName,
                                               Principal principal) throws Exception
   {
      return vsCalculatorServiceProxy.getDimensionInfos(vsId, assemblyName, chartHandler, catalog, principal);
   }


   private Assembly getAssembly(String vsId, String assemblyName, Principal principal) throws Exception
   {
      return vsCalculatorServiceProxy.getAssembly(vsId, assemblyName, principal);
   }

   @RequestMapping(value = "/api/composer/supportReset", method = RequestMethod.GET)
   public Map supportReset(@RequestParam("vsId") String vsId,
                               @RequestParam("assemblyName") String assemblyName,
                               @RequestParam("aggreName") String aggreName, Principal principal)
      throws Exception
   {
      return vsCalculatorServiceProxy.supportReset(vsId, assemblyName, aggreName, chartHandler, calculatorHandler, principal);
   }



   @RequestMapping(value = "/api/composer/resetOptions", method = RequestMethod.GET)
   @ResponseBody
   public Map getResetOptions(@RequestParam("vsId") String vsId,
                                         @RequestParam("assemblyName") String assemblyName,
                                         @RequestParam("aggreName") String aggreName, Principal principal)
      throws Exception
   {
      return vsCalculatorServiceProxy.getResetOptions(vsId, assemblyName, aggreName, chartHandler, calculatorHandler, principal);
   }

   @Autowired
   private VSChartHandler chartHandler;
   @Autowired
   private CalculatorHandler calculatorHandler;
   private VSCalculatorServiceProxy vsCalculatorServiceProxy;
   private Catalog catalog = Catalog.getCatalog();
}
