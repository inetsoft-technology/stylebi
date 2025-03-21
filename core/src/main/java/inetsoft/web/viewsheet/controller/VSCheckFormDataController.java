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
package inetsoft.web.viewsheet.controller;

import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.viewsheet.model.CheckFormTableDataModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Check whether an action is interfering with a changed form table input.
 */
@Controller
public class VSCheckFormDataController {
   /**
    * Creates a new instance of <tt>VSCheckFormDataController</tt>.
    */
   @Autowired
   public VSCheckFormDataController(VSCheckFormDataServiceProxy vsCheckFormDataServiceProxy) {
      this.vsCheckFormDataServiceProxy = vsCheckFormDataServiceProxy;
   }

   @PostMapping("/api/formDataCheck")
   @ResponseBody
   public boolean checkFormData(@DecodeParam("runtimeId") String runtimeId,
                                @RequestParam("checkCondition") boolean checkCond,
                                @RequestBody CheckFormTableDataModel model,
                                Principal principal)
      throws Exception
   {
      return vsCheckFormDataServiceProxy.checkFormData(runtimeId, checkCond, model, principal);
   }

   @PostMapping("/api/formTableModified")
   @ResponseBody
   public boolean formTableModified(@DecodeParam("runtimeId") String runtimeId,
                                  @RequestBody CheckFormTableDataModel model,
                                  Principal principal)
      throws Exception
   {
      return vsCheckFormDataServiceProxy.formTableModified(runtimeId, model, principal);
   }

   /**
    * Before closing the viewsheet, check if any form tables were edited.
    *
    * @param runtimeId the runtime id of the preview sheet.
    * @param principal a principal identifying the current user.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @GetMapping("/api/vs/checkFormTables")
   @ResponseBody
   public boolean checkTables(@DecodeParam("runtimeId") String runtimeId,
                              Principal principal)
      throws Exception
   {
      return vsCheckFormDataServiceProxy.checkTables(runtimeId, principal);
   }

   private final VSCheckFormDataServiceProxy vsCheckFormDataServiceProxy;
}
