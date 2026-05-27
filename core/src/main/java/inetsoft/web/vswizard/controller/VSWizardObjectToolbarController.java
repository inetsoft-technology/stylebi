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
package inetsoft.web.vswizard.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * vs wizard object toolbar controller.
 */
@RestController
public class VSWizardObjectToolbarController {

   @Autowired
   public VSWizardObjectToolbarController(VSWizardObjectToolbarServiceProxy vsWizardObjectToolbarServiceProxy)
   {
      this.vsWizardObjectToolbarServiceProxy = vsWizardObjectToolbarServiceProxy;
   }

   @GetMapping("/api/vswizard/object/toolbar/full-editor")
   public void gotoFullEditor(
      @RequestParam("id") String id,
      @RequestParam(value = "assemblyName", required = false) String assemblyName,
      Principal principal)
      throws Exception
   {

      vsWizardObjectToolbarServiceProxy.gotoFullEditor(id, assemblyName, principal);
   }

   private final VSWizardObjectToolbarServiceProxy vsWizardObjectToolbarServiceProxy;
}
