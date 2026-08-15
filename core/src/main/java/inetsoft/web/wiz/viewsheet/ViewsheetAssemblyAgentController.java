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
package inetsoft.web.wiz.viewsheet;

import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.viewsheet.model.ViewsheetModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.wiz.script.ScriptImageService;

import java.security.Principal;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST surface for agent-driven viewsheet layout editing.
 *
 * <p>Validation and delegation only — every mutation goes through the Composer's own services
 * via {@link ViewsheetSessionService}, which supplies the capturing dispatcher, the checkpoint,
 * and the browser broadcast.
 */
@RestController
public class ViewsheetAssemblyAgentController {
   @Autowired
   public ViewsheetAssemblyAgentController(SheetAgentFeature feature,
                                   SheetJoinService joinService,
                                   SheetSessionService sessionService,
                                   ViewsheetSessionService sessions,
                                   ViewsheetReadService readService,
                                   ViewsheetEditService editService,
                                   ViewsheetFormatService formatService,
                                   ScriptImageService imageService,
                                   AssemblyPropertyService propertyService,
                                   AssemblyHyperlinkService hyperlinkService,
                                   ChartElementService chartElementService,
                                   ChartRegionPropertyService chartRegionService,
                                   AssemblyConditionService conditionService,
                                   AssemblyHighlightService highlightService,
                                   DateComparisonService comparisonService,
                                   ViewsheetService viewsheetService,
                                   SheetAgentBroadcastService broadcast)
   {
      this.feature = feature;
      this.joinService = joinService;
      this.sessionService = sessionService;
      this.sessions = sessions;
      this.readService = readService;
      this.editService = editService;
      this.formatService = formatService;
      this.imageService = imageService;
      this.propertyService = propertyService;
      this.hyperlinkService = hyperlinkService;
      this.chartElementService = chartElementService;
      this.chartRegionService = chartRegionService;
      this.conditionService = conditionService;
      this.highlightService = highlightService;
      this.comparisonService = comparisonService;
      this.viewsheetService = viewsheetService;
      this.broadcast = broadcast;
   }

   public record JoinRequest(String code) {}
   public record JoinResponse(String sessionToken, String runtimeId, String ownerIdentity) {}

   @PostMapping("/api/wiz/v1/agent/viewsheet/join")
   public JoinResponse join(@RequestBody JoinRequest body, Principal user) throws PairingException {
      requireEnabled();
      JoinSession session = joinService.join(body.code(), user);
      return new JoinResponse(session.sessionToken(), session.runtimeId(), session.ownerIdentity());
   }

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/model")
   public ViewsheetModel model(@PathVariable String sessionToken, Principal user)
      throws PairingException
   {
      requireEnabled();
      return readService.read(sessions.resolve(sessionToken, user));
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/edit")
   public void edit(@PathVariable String sessionToken,
                    @RequestBody EditRequest request,
                    @RequestParam(required = false, defaultValue = "") String linkUri,
                    Principal user)
      throws Exception
   {
      requireEnabled();
      editService.apply(sessionToken, user, request, linkUri);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/format")
   public void format(@PathVariable String sessionToken,
                      @RequestBody ViewsheetFormatService.FormatRequest request,
                      @RequestParam(required = false, defaultValue = "") String linkUri,
                      Principal user)
      throws Exception
   {
      requireEnabled();
      formatService.setFormat(sessionToken, user, request, linkUri);
   }

   public record ImageResponse(String image, String format, int width, int height, String note) {}

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/image")
   public ImageResponse image(@PathVariable String sessionToken,
                              @RequestParam(required = false) String target,
                              @RequestParam(required = false) Integer width,
                              @RequestParam(required = false) Integer height,
                              Principal user)
      throws Exception
   {
      requireEnabled();
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      ScriptImageService.ChartImage image = target == null || target.isBlank()
         ? imageService.getViewsheetImage(rvs, width, height, user)
         : imageService.getAssemblyImage(rvs, target, width, height, user);

      return new ImageResponse(Base64.getEncoder().encodeToString(image.pngBytes()),
                               image.isPng() ? "png" : "svg",
                               image.width(), image.height(), image.note());
   }

   public record PropertyPatchRequest(String assembly, Map<String, Object> properties) {}

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/properties/list")
   public Map<String, Object> listAssemblyProperties(@PathVariable String sessionToken,
                                                     @RequestParam String assembly,
                                                     Principal user)
      throws Exception
   {
      requireEnabled();
      return propertyService.list(sessionToken, user, assembly);
   }

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/properties")
   public Object getAssemblyProperties(@PathVariable String sessionToken,
                                       @RequestParam String assembly,
                                       @RequestParam(required = false) boolean raw,
                                       Principal user)
      throws Exception
   {
      requireEnabled();
      return propertyService.get(sessionToken, user, assembly, raw);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/properties")
   public void setAssemblyProperties(@PathVariable String sessionToken,
                                     @RequestBody PropertyPatchRequest request,
                                     @RequestParam(required = false, defaultValue = "") String linkUri,
                                     Principal user)
      throws Exception
   {
      requireEnabled();
      propertyService.set(sessionToken, user, request.assembly(), request.properties(), linkUri);
   }

   public record HyperlinkRequest(String assembly, Integer row, Integer col, String colName,
                                  Boolean axis, Boolean text, Boolean titleLink,
                                  Boolean emptyPlotLink, Map<String, Object> link) {
      AssemblyHyperlinkService.Region region() {
         return new AssemblyHyperlinkService.Region(
            row, col, colName, Boolean.TRUE.equals(axis), Boolean.TRUE.equals(text),
            Boolean.TRUE.equals(titleLink), Boolean.TRUE.equals(emptyPlotLink));
      }
   }

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/hyperlink")
   public Map<String, Object> getHyperlink(@PathVariable String sessionToken,
                                           @RequestParam String assembly,
                                           @RequestParam(required = false) Integer row,
                                           @RequestParam(required = false) Integer col,
                                           @RequestParam(required = false) String colName,
                                           @RequestParam(required = false) boolean titleLink,
                                           Principal user)
      throws Exception
   {
      requireEnabled();
      return hyperlinkService.read(
         sessionToken, user, assembly,
         new AssemblyHyperlinkService.Region(row, col, colName, false, false, titleLink, false));
   }

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/hyperlink/types")
   public Map<String, Object> hyperlinkTypes(@PathVariable String sessionToken, Principal user) {
      requireEnabled();
      return hyperlinkService.linkTypes();
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/hyperlink")
   public void setHyperlink(@PathVariable String sessionToken,
                            @RequestBody HyperlinkRequest request,
                            @RequestParam(required = false, defaultValue = "") String linkUri,
                            Principal user)
      throws Exception
   {
      requireEnabled();
      hyperlinkService.set(sessionToken, user, request.assembly(), request.region(),
                           request.link(), linkUri);
   }

   public record ElementVisibilityRequest(String assembly, String element, String target,
                                          Boolean visible) {}
   public record PlotResizeRequest(String assembly, Double ratio, Boolean vertical,
                                   Boolean reset) {}

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/chart/elements")
   public Map<String, Object> chartElementVocabulary(@PathVariable String sessionToken,
                                                     Principal user)
   {
      requireEnabled();
      return chartElementService.vocabulary();
   }

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/chart/regions")
   public Map<String, Object> chartRegionVocabulary(@PathVariable String sessionToken,
                                                    Principal user)
   {
      requireEnabled();
      return chartRegionService.vocabulary();
   }

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/chart/region-properties")
   public Map<String, Object> listChartRegionProperties(@PathVariable String sessionToken,
                                                        @RequestParam String assembly,
                                                        @RequestParam String region,
                                                        @RequestParam String target,
                                                        @RequestParam(required = false)
                                                        String field,
                                                        Principal user)
      throws Exception
   {
      requireEnabled();
      return chartRegionService.list(sessionToken, user, assembly, region, target, field);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/chart/region-properties")
   public void setChartRegionProperties(@PathVariable String sessionToken,
                                        @RequestBody RegionPropertiesRequest request,
                                        @RequestParam(required = false, defaultValue = "")
                                        String linkUri,
                                        Principal user)
      throws Exception
   {
      requireEnabled();
      chartRegionService.set(sessionToken, user, request.assembly(), request.region(),
                             request.target(), request.field(), request.properties(), linkUri);
   }

   public record RegionPropertiesRequest(String assembly, String region, String target,
                                         String field, Map<String, Object> properties) {}

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/chart/element-visibility")
   public void setChartElementVisibility(
      @PathVariable String sessionToken,
      @RequestBody ElementVisibilityRequest request,
      @RequestParam(required = false, defaultValue = "") String linkUri,
      Principal user) throws Exception
   {
      requireEnabled();

      if(request.visible() == null) {
         throw new IllegalArgumentException(
            "set_chart_element_visibility requires 'visible' — true to show, false to hide. " +
            "Defaulting it either way would guess at the caller's intent.");
      }

      chartElementService.setVisibility(sessionToken, user, request.assembly(),
                                        request.element(), request.target(), request.visible(),
                                        linkUri);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/chart/plot-size")
   public void resizeChartPlot(@PathVariable String sessionToken,
                               @RequestBody PlotResizeRequest request,
                               @RequestParam(required = false, defaultValue = "") String linkUri,
                               Principal user)
      throws Exception
   {
      requireEnabled();
      chartElementService.resizePlot(sessionToken, user, request.assembly(), request.ratio(),
                                     Boolean.TRUE.equals(request.vertical()),
                                     Boolean.TRUE.equals(request.reset()), linkUri);
   }

   /**
    * One clause in the flat condition vocabulary. {@code junction} joins it to the NEXT clause,
    * so the last clause must not carry one — {@link ConditionVocabulary} enforces that.
    */
   public record ConditionClause(String field, String operator, List<Object> values,
                                 String junction, Boolean negated) {
      ConditionVocabulary.Clause toClause() {
         return new ConditionVocabulary.Clause(field, operator, values, junction,
                                               Boolean.TRUE.equals(negated));
      }
   }

   public record ConditionRequest(String assembly, List<ConditionClause> conditions) {}

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/condition")
   public Map<String, Object> getCondition(@PathVariable String sessionToken,
                                           @RequestParam String assembly,
                                           Principal user)
      throws Exception
   {
      requireEnabled();
      return conditionService.read(sessionToken, user, assembly);
   }

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/condition/vocabulary")
   public Map<String, Object> conditionVocabulary(@PathVariable String sessionToken,
                                                  Principal user)
   {
      requireEnabled();
      return conditionService.vocabulary();
   }

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/condition/values")
   public Object browseConditionValues(@PathVariable String sessionToken,
                                       @RequestParam String assembly,
                                       @RequestParam String column,
                                       Principal user)
      throws Exception
   {
      requireEnabled();
      return conditionService.browseValues(sessionToken, user, assembly, column);
   }

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/condition/date-ranges")
   public Object conditionDateRanges(@PathVariable String sessionToken, Principal user)
      throws Exception
   {
      requireEnabled();
      return conditionService.dateRanges(sessionToken, user);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/condition")
   public Map<String, Object> setCondition(
      @PathVariable String sessionToken,
      @RequestBody ConditionRequest request,
      @RequestParam(required = false, defaultValue = "") String linkUri,
      Principal user) throws Exception
   {
      requireEnabled();
      List<ConditionVocabulary.Clause> clauses = new java.util.ArrayList<>();

      if(request.conditions() != null) {
         for(ConditionClause clause : request.conditions()) {
            clauses.add(clause.toClause());
         }
      }

      int applied = conditionService.set(sessionToken, user, request.assembly(), clauses, linkUri);
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("ok", true);
      out.put("conditionCount", applied);
      return out;
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/condition/clear")
   public void clearCondition(@PathVariable String sessionToken,
                              @RequestBody ConditionRequest request,
                              @RequestParam(required = false, defaultValue = "") String linkUri,
                              Principal user)
      throws Exception
   {
      requireEnabled();
      conditionService.clear(sessionToken, user, request.assembly(), linkUri);
   }

   public record HighlightRequest(String assembly, Integer row, Integer col, String colName,
                                  String name, String foreground, String background,
                                  List<ConditionClause> conditions, Boolean applyRow,
                                  Boolean replace) {
      AssemblyHighlightService.Region region() {
         return new AssemblyHighlightService.Region(row, col, colName, false, false);
      }

      AssemblyHighlightService.Highlight highlight() {
         List<ConditionVocabulary.Clause> clauses = new java.util.ArrayList<>();

         if(conditions != null) {
            for(ConditionClause clause : conditions) {
               clauses.add(clause.toClause());
            }
         }

         return new AssemblyHighlightService.Highlight(name, foreground, background, clauses,
                                                       Boolean.TRUE.equals(applyRow));
      }
   }

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/highlights")
   public Map<String, Object> listHighlights(@PathVariable String sessionToken,
                                             @RequestParam String assembly,
                                             @RequestParam(required = false) Integer row,
                                             @RequestParam(required = false) Integer col,
                                             @RequestParam(required = false) String colName,
                                             Principal user)
      throws Exception
   {
      requireEnabled();
      return highlightService.list(sessionToken, user, assembly,
                                   new AssemblyHighlightService.Region(row, col, colName, false,
                                                                       false));
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/highlights")
   public void setHighlight(@PathVariable String sessionToken,
                            @RequestBody HighlightRequest request,
                            @RequestParam(required = false, defaultValue = "") String linkUri,
                            Principal user)
      throws Exception
   {
      requireEnabled();
      highlightService.set(sessionToken, user, request.assembly(), request.region(),
                           request.highlight(), Boolean.TRUE.equals(request.replace()), linkUri);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/highlights/delete")
   public void deleteHighlight(@PathVariable String sessionToken,
                               @RequestBody HighlightRequest request,
                               @RequestParam(required = false, defaultValue = "") String linkUri,
                               Principal user)
      throws Exception
   {
      requireEnabled();
      highlightService.delete(sessionToken, user, request.assembly(), request.region(),
                              request.name(), linkUri);
   }

   public record DateComparisonRequest(String assembly, Integer periods, String level,
                                       String endDate, Boolean endToday, String interval,
                                       Boolean useFacet, Boolean onlyShowMostRecentDate,
                                       Map<String, Object> frame) {
      DateComparisonService.Comparison comparison() {
         return new DateComparisonService.Comparison(
            periods, level, endDate, Boolean.TRUE.equals(endToday), interval, useFacet,
            onlyShowMostRecentDate, frame);
      }
   }

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/date-comparison")
   public Map<String, Object> getDateComparison(@PathVariable String sessionToken,
                                                @RequestParam String assembly,
                                                Principal user)
      throws Exception
   {
      requireEnabled();
      return comparisonService.read(sessionToken, user, assembly);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/date-comparison")
   public void setDateComparison(
      @PathVariable String sessionToken,
      @RequestBody DateComparisonRequest request,
      @RequestParam(required = false, defaultValue = "") String linkUri,
      Principal user) throws Exception
   {
      requireEnabled();
      comparisonService.set(sessionToken, user, request.assembly(), request.comparison(), linkUri);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/date-comparison/clear")
   public void clearDateComparison(
      @PathVariable String sessionToken,
      @RequestBody DateComparisonRequest request,
      @RequestParam(required = false, defaultValue = "") String linkUri,
      Principal user) throws Exception
   {
      requireEnabled();
      comparisonService.clear(sessionToken, user, request.assembly(), linkUri);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/save")
   public void save(@PathVariable String sessionToken, Principal user) throws PairingException {
      requireEnabled();
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      AssetEntry entry = rvs.getEntry();

      if(entry.getScope() == AssetRepository.TEMPORARY_SCOPE) {
         throw new PairingException(
            "Viewsheet is unsaved (\"" + entry.toView() + "\"). Save it with a name in the " +
            "StyleBI Composer first — there is no save-as through this tool — then call " +
            "save_viewsheet again.");
      }

      if(!(user instanceof XPrincipal xp)) {
         throw new PairingException("Cannot save: agent principal is not an XPrincipal (" +
                                    user.getClass().getName() + ")");
      }

      try {
         viewsheetService.setViewsheet(rvs.getViewsheet(), entry, xp, true, true);
         rvs.setSavePoint(rvs.getCurrent());
      }
      catch(Exception e) {
         throw new PairingException("Failed to save viewsheet: " + e.getMessage(), e);
      }

      broadcast.broadcastSave(rvs, rvs.getID(), user);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/undo")
   public Map<String, Object> undo(@PathVariable String sessionToken, Principal user)
      throws Exception
   {
      requireEnabled();
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      boolean undone = rvs.undo(null);
      broadcast.broadcastRefresh(rvs, SheetType.VIEWSHEET, rvs.getID(), user);
      return undoState("undone", undone, rvs);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/redo")
   public Map<String, Object> redo(@PathVariable String sessionToken, Principal user)
      throws Exception
   {
      requireEnabled();
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      boolean redone = rvs.redo(null);
      broadcast.broadcastRefresh(rvs, SheetType.VIEWSHEET, rvs.getID(), user);
      return undoState("redone", redone, rvs);
   }

   private static Map<String, Object> undoState(String key, boolean applied, RuntimeViewsheet rvs) {
      Map<String, Object> state = new LinkedHashMap<>();
      state.put(key, applied);
      state.put("checkpoint", rvs.getCurrent());
      state.put("total", rvs.size());
      state.put("savePoint", rvs.getSavePoint());
      return state;
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/detach")
   public void detach(@PathVariable String sessionToken, Principal user) {
      sessionService.close(sessionToken);
   }

   private void requireEnabled() {
      if(!feature.isEnabled()) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                           "Sheet agent pairing is disabled");
      }
   }

   @ExceptionHandler(PairingException.class)
   public ResponseEntity<Map<String, String>> handlePairingException(PairingException e) {
      HttpStatus status = switch(e.getKind()) {
         case SESSION_EXPIRED -> HttpStatus.NOT_FOUND;
         case USER_MISMATCH, FEATURE_DISABLED -> HttpStatus.FORBIDDEN;
         case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
         case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
         default -> HttpStatus.BAD_REQUEST;
      };

      Map<String, String> body = new LinkedHashMap<>();
      body.put("error", e.getMessage());
      body.put("errorCode", e.getKind().name());
      return ResponseEntity.status(status).body(body);
   }

   private final SheetAgentFeature feature;
   private final SheetJoinService joinService;
   private final SheetSessionService sessionService;
   private final ViewsheetSessionService sessions;
   private final ViewsheetReadService readService;
   private final ViewsheetEditService editService;
   private final ViewsheetFormatService formatService;
   private final ScriptImageService imageService;
   private final AssemblyPropertyService propertyService;
   private final AssemblyHyperlinkService hyperlinkService;
   private final ChartElementService chartElementService;
   private final ChartRegionPropertyService chartRegionService;
   private final AssemblyConditionService conditionService;
   private final AssemblyHighlightService highlightService;
   private final DateComparisonService comparisonService;
   private final ViewsheetService viewsheetService;
   private final SheetAgentBroadcastService broadcast;
}
