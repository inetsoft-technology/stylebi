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
import inetsoft.web.wiz.service.UnsatisfiableBindingException;
import inetsoft.web.wiz.service.WizAutoBindingService;
import inetsoft.web.wiz.service.WizGeoService;
import inetsoft.web.wiz.service.WizVsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/wiz")
public class WizViewsheetController {
   public WizViewsheetController(WizVsService wizVsService,
                                  WizAutoBindingService wizAutoBindingService,
                                  WizGeoService wizGeoService)
   {
      this.wizVsService = wizVsService;
      this.wizAutoBindingService = wizAutoBindingService;
      this.wizGeoService = wizGeoService;
   }

   @PostMapping(value = "/viewsheet/create", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<?> createViewsheet(@RequestBody CreateVisualizationModel model,
                                            Principal user)
   {
      return run("create viewsheet", () -> wizVsService.createViewsheet(model, user));
   }

   @PostMapping(value = "/viewsheet/date-comparison", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<?> applyDateComparison(@RequestBody ApplyDateComparisonModel model,
                                                Principal user)
   {
      return run("apply date comparison", () -> wizVsService.applyDateComparison(model, user));
   }

   @PostMapping(value = "/viewsheet/highlight", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<?> applyHighlight(@RequestBody ApplyHighlightModel model, Principal user) {
      return run("apply highlight", () -> wizVsService.applyHighlight(model, user));
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
   public ResponseEntity<?> setChartFormat(@RequestBody ChartFormatRequest request, Principal user) {
      return run("set chart format", () -> wizAutoBindingService.setChartFormat(request, user));
   }

   @PostMapping(value = "/viewsheet/colors", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<?> setChartColors(@RequestBody ChartColorsRequest request, Principal user) {
      return run("set chart colors", () -> wizAutoBindingService.setChartColors(request, user));
   }

   /**
    * Read-only companion to /viewsheet/colors: which colour parameters the chart can accept. Callers must
    * read this first — the accepted parameters are fixed by the chart's colour binding, and the only
    * valid categoryColors / measureColors keys come from here.
    */
   @PostMapping(value = "/chart/aestheticModel", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<?> getChartAestheticModel(@RequestBody ChartAestheticModelRequest request,
                                                  Principal user)
   {
      return run("read chart aesthetic model",
         () -> wizAutoBindingService.getChartAestheticModel(request, user));
   }

   @PostMapping(value = "/viewsheet/geo/detect", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<?> geoDetect(@Valid @RequestBody GeoDetectRequest request, Principal user) {
      return run("geo detect", () -> wizGeoService.detect(request, user));
   }

   @PostMapping(value = "/viewsheet/geo/apply", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<?> geoApply(@Valid @RequestBody GeoApplyRequest request, Principal user) {
      return run("geo apply", () -> wizGeoService.apply(request, user));
   }

   /**
    * Read-only: returns an existing assembly's rendered data (headers + rows + binding).
    *
    * <p>For answering a question about a chart built EARLIER in a conversation. The caller holds no
    * copy of that chart's rows; it addresses the chart the way the browser embed does — runtime id
    * plus assembly name — and gets back what that chart is showing. Nothing here can change the
    * chart's data range (see {@link AssemblyDataRequest}), so the answer and the chart on screen
    * cannot disagree.
    *
    * <p>An assembly whose runtime has been reaped comes back as an empty result rather than an
    * error: the chart is equally unavailable to the user at that point, so "no data to answer from"
    * is the accurate outcome, not a failure to retry.
    */
   @PostMapping(value = "/viewsheet/assembly-data", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<?> assemblyData(@Valid @RequestBody AssemblyDataRequest request,
                                         Principal user)
   {
      return run("read assembly data", () -> wizVsService.fetchAssemblyData(
         request.getRuntimeId(), request.getAssemblyName(), user));
   }

   @PostMapping(value = "/viewsheet/remove-visualization", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<?> removeVisualization(@Valid @RequestBody RemoveVisualizationRequest request,
                                                Principal user)
   {
      return run("remove visualization", () -> {
         wizVsService.removeVisualization(request.getRuntimeId(), request.getAssemblyName(), user);
         return Map.of("success", true);
      });
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
      // Must precede the IllegalArgumentException catch below (it is a subclass), else it is shadowed.
      catch(UnsatisfiableBindingException e) {
         // Map.of rejects null values, and String.valueOf(null) would emit the literal
         // string "null"; coerce absent fields to "" so the JSON body stays meaningful.
         Map<String, Object> errorBody = new LinkedHashMap<>();
         errorBody.put("error", "unsatisfiable explicit binding");

         // A set-conflict failure carries every pin (no single culprit); report them all
         // as "pins". A single-pin failure carries just role/field; report it as "pin".
         if(!e.getPins().isEmpty()) {
            errorBody.put("pins", e.getPins().stream()
               .map(p -> Map.of("role", nullToEmpty(p.role()), "field", nullToEmpty(p.field())))
               .collect(Collectors.toList()));
         }
         else {
            errorBody.put("pin", Map.of("role", nullToEmpty(e.getRole()), "field", nullToEmpty(e.getField())));
         }

         errorBody.put("reason", nullToEmpty(e.getReason()));
         return ResponseEntity.badRequest().body(errorBody);
      }
      catch(IllegalArgumentException e) {
         return ResponseEntity.badRequest().body(Map.of("error", nullToEmpty(e.getMessage())));
      }
      // Honour the status the thrower chose. ResponseStatusException is a plain RuntimeException, so
      // without this it fell through to the generic handler below and every deliberate 4xx was returned
      // as a content-free 500 — the caller was told "unexpected error, please try again" about a request
      // that could never succeed as sent, and an automated one replays it instead of correcting it.
      // Pre-dates the colour validation that now leans on this: the malformed-hex and unknown-palette
      // rejections in WizAutoBindingService have always thrown this type.
      catch(ResponseStatusException e) {
         return ResponseEntity.status(e.getStatusCode().value())
            .body(Map.of("error", nullToEmpty(e.getReason())));
      }
      catch(Exception e) {
         LOG.error("Failed to {}", action, e);
         return ResponseEntity.internalServerError()
            .body(Map.of("error", "An unexpected error occurred. Please try again."));
      }
   }

   private static String nullToEmpty(String value) {
      return value == null ? "" : value;
   }

   private final WizVsService wizVsService;
   private final WizAutoBindingService wizAutoBindingService;
   private final WizGeoService wizGeoService;
   private static final Logger LOG = LoggerFactory.getLogger(WizViewsheetController.class);
}
