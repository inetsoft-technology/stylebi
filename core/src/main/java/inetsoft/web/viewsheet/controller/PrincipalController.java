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
package inetsoft.web.viewsheet.controller;

import inetsoft.sree.security.SecurityEngine;
import inetsoft.web.viewsheet.command.SetPrincipalCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;

@Controller
public class PrincipalController {
   @Autowired
   public PrincipalController(SecurityEngine securityEngine) {
      this.securityEngine = securityEngine;
   }

   @GetMapping("/api/viewsheet/get-principal")
   @ResponseBody
   public SetPrincipalCommand getPrincipal(Principal principal) throws Exception {
      if(securityEngine == null) {
         return null;
      }

      return new SetPrincipalCommand(securityEngine, principal);
   }

   private final SecurityEngine securityEngine;
}
