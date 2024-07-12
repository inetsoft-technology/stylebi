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
package inetsoft.web.binding.dnd.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.SourceChangeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * This class handles get vsobjectmodel from the server.
 */
@RestController
public class VSSourceChangeController {
   @Autowired
   public VSSourceChangeController(ViewsheetService viewsheetService,
                                   VSAssemblyInfoHandler handler)
   {
      this.viewsheetService = viewsheetService;
      this.handler = handler;
   }

   /**
    * Check is source change in server.
    *
    * @return the SourceChangeMessage object indicating whether the binding information for
    *         the element has changed.
    */
   @GetMapping("/vsassembly/binding/sourcechange")
   public SourceChangeMessage checkSourceChanged(
      @RequestParam("runtimeId") String vsId, @RequestParam("assembly") String aname,
      @RequestParam("table") String table, Principal principal)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(vsId, principal);
      SourceChangeMessage sourceChangeMessage = new SourceChangeMessage();
      sourceChangeMessage.setChanged(false);

      if(rvs != null) {
         VSAssembly assembly = getVSAssembly(rvs, aname);
         sourceChangeMessage.setChanged(handler.sourceChanged(table, assembly));
      }

      return sourceChangeMessage;
   }

   protected VSAssembly getVSAssembly(RuntimeViewsheet rvs, String name) {
      Viewsheet viewsheet = rvs.getViewsheet();
      return (VSAssembly) viewsheet.getAssembly(name);
   }

   private ViewsheetService viewsheetService;
   private final VSAssemblyInfoHandler handler;
}
