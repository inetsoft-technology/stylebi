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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

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
   public ResponseEntity<?> createViewsheet(@RequestBody CreateVisualizationModel model,
                                            Principal user)
   {
      return run("create viewsheet", () -> wizVsService.createViewsheet(model, user));
   }

   @PostMapping("/viewsheet/validateBinding")
   public void validateBinding(@RequestBody CreateVisualizationModel model,
                               Principal user) throws Exception
   {
      wizVsService.validateBinding(model, user);
   }

   @PostMapping(value = "/viewsheet/autoBinding", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<?> autoBinding(@RequestBody AutoBindingRequest request, Principal user) {
      return run("run autoBinding", () -> wizAutoBindingService.autoBinding(request, user));
   }

   @PostMapping(value = "/viewsheet/changeType", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<?> changeType(@RequestBody ChangeTypeRequest request, Principal user) {
      return run("change chart type", () -> wizAutoBindingService.changeType(request, user));
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

   @FunctionalInterface
   private interface ControllerAction {
      Object run() throws Exception;
   }

   private ResponseEntity<?> run(String action, ControllerAction body) {
      try {
         return ResponseEntity.ok(body.run());
      }
      catch(IllegalArgumentException e) {
         return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
      }
      catch(Exception e) {
         LOG.error("Failed to {}", action, e);
         return ResponseEntity.internalServerError()
            .body(Map.of("error", "An unexpected error occurred. Please try again."));
      }
   }

   private final WizVsService wizVsService;
   private final WizAutoBindingService wizAutoBindingService;
   private static final Logger LOG = LoggerFactory.getLogger(WizViewsheetController.class);
}
