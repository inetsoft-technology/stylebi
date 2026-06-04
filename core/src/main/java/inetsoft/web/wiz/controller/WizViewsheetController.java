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

import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.service.WizAutoBindingService;
import inetsoft.web.wiz.service.WizVsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/wiz")
public class WizViewsheetController {
   public WizViewsheetController(WizVsService wizVsService,
                                  WizAutoBindingService wizAutoBindingService)
   {
      this.wizVsService = wizVsService;
      this.wizAutoBindingService = wizAutoBindingService;
   }

   @PostMapping(value = "/viewsheet/create", produces = MediaType.APPLICATION_JSON_VALUE)
   public CreateViewsheetResult createViewsheet(@RequestBody CreateVisualizationModel model,
                                                Principal user) throws Exception
   {
      return wizVsService.createViewsheet(model, user);
   }

   @PostMapping("/viewsheet/validateBinding")
   public void validateBinding(@RequestBody CreateVisualizationModel model,
                               Principal user) throws Exception
   {
      wizVsService.validateBinding(model, user);
   }

   @PostMapping(value = "/viewsheet/autoBinding", produces = MediaType.APPLICATION_JSON_VALUE)
   public AutoBindingResponse autoBinding(@RequestBody AutoBindingRequest request,
                                          Principal user) throws Exception
   {
      return wizAutoBindingService.autoBinding(request, user);
   }

   @PostMapping(value = "/viewsheet/changeType", produces = MediaType.APPLICATION_JSON_VALUE)
   public CreateViewsheetResult changeType(@RequestBody ChangeTypeRequest request,
                                           Principal user) throws Exception
   {
      return wizAutoBindingService.changeType(request, user);
   }

   @PostMapping(value = "/viewsheet/format", produces = MediaType.APPLICATION_JSON_VALUE)
   public CreateViewsheetResult setChartFormat(@RequestBody ChartFormatRequest request,
                                               Principal user) throws Exception
   {
      return wizAutoBindingService.setChartFormat(request, user);
   }

   @PostMapping(value = "/viewsheet/colors", produces = MediaType.APPLICATION_JSON_VALUE)
   public CreateViewsheetResult setChartColors(@RequestBody ChartColorsRequest request,
                                               Principal user) throws Exception
   {
      return wizAutoBindingService.setChartColors(request, user);
   }

   @DeleteMapping("/viewsheet")
   public void deleteViewsheet(@RequestParam("identifier") String identifier,
                               Principal user) throws Exception
   {
      wizVsService.deleteViewsheet(identifier, user);
   }

   private final WizVsService wizVsService;
   private final WizAutoBindingService wizAutoBindingService;
}
