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
package inetsoft.web.binding.dnd.controller;

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
   public VSSourceChangeController(VSSourceChangeServiceProxy vsSourceChangeServiceProxy,
                                   VSAssemblyInfoHandler handler)
   {
      this.handler = handler;
      this.vsSourceChangeServiceProxy = vsSourceChangeServiceProxy;
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
      return vsSourceChangeServiceProxy.checkSourceChanged(vsId, aname, table, handler, principal);
   }

   private VSSourceChangeServiceProxy vsSourceChangeServiceProxy;
   private final VSAssemblyInfoHandler handler;
}
