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

import inetsoft.web.binding.model.*;
import inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Controller that provides the REST endpoints for the color mapping dialog.
 *
 * @since 12.3
 */
@Controller
public class ColorMappingDialogController {
   /**
    * Creates a new instance of <tt>ColorMappingDialogController</tt>.
    */
   @Autowired
   public ColorMappingDialogController(
      ColorMappingDialogServiceProxy colorMappingDialogService)
   {
      this.colorMappingDialogService = colorMappingDialogService;
   }

   @RequestMapping(
      value = "/api/composer/vs/getColorMappingDialogModel",
      method = RequestMethod.POST
   )
   @ResponseBody
   public ColorMappingDialogModel getColorMappingDialogModel(
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("vsId") String vsId,
      @RequestParam("dimensionName") String dimensionName,
      @RequestBody CategoricalColorModel frame,
      Principal principal) throws Exception
   {
      return colorMappingDialogService.getColorMappingDialogModel(vsId, assemblyName,
                                                                  dimensionName, frame, principal);
   }

   private final ColorMappingDialogServiceProxy colorMappingDialogService;
}
