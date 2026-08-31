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

import inetsoft.sree.security.IdentityID;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.viewsheet.model.LayoutModel;
import inetsoft.web.wiz.viewsheet.model.ViewsheetModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;
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
                                   SheetPropertyService sheetPropertyService,
                                   AssemblyHyperlinkService hyperlinkService,
                                   ChartElementService chartElementService,
                                   ChartRegionPropertyService chartRegionService,
                                   AssemblyConditionService conditionService,
                                   AssemblyHighlightService highlightService,
                                   DateComparisonService comparisonService,
                                   AssemblyConvertService convertService,
                                   SelectionRuntimeService selectionService,
                                   CalendarDisplayService calendarService,
                                   InputValueService inputService,
                                   ViewsheetService viewsheetService,
                                   SheetAgentBroadcastService broadcast,
                                   SheetOpenService openService,
                                   LayoutSessionService layoutSessionService,
                                   LayoutReadService layoutReadService,
                                   PrintDeviceLayoutPropertyService printDeviceLayoutPropertyService,
                                   LayoutMutationService layoutMutationService,
                                   LayoutUndoService layoutUndoService)
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
      this.sheetPropertyService = sheetPropertyService;
      this.hyperlinkService = hyperlinkService;
      this.chartElementService = chartElementService;
      this.chartRegionService = chartRegionService;
      this.conditionService = conditionService;
      this.highlightService = highlightService;
      this.comparisonService = comparisonService;
      this.convertService = convertService;
      this.selectionService = selectionService;
      this.calendarService = calendarService;
      this.inputService = inputService;
      this.viewsheetService = viewsheetService;
      this.broadcast = broadcast;
      this.openService = openService;
      this.layoutSessionService = layoutSessionService;
      this.layoutReadService = layoutReadService;
      this.printDeviceLayoutPropertyService = printDeviceLayoutPropertyService;
      this.layoutMutationService = layoutMutationService;
      this.layoutUndoService = layoutUndoService;
   }

   public record JoinRequest(String code) {}
   /**
    * @param sheetType     the runtime's own type, {@code viewsheet} or {@code worksheet} — NOT the
    *                      plugin that asked. Binding and script both drive a viewsheet runtime, and
    *                      without this the client had to label the session from its own name, which is
    *                      how one open viewsheet came to hold several unrelated sessions.
    * @param editorContext the script/formula location this session is scoped to, or {@code null}
    *                      for a whole-sheet ("Connect to Claude" toolbar) session
    */
   public record JoinResponse(String sessionToken, String runtimeId, String ownerIdentity,
                              String sheetType, EditorContext editorContext) {}

   @PostMapping("/api/wiz/v1/agent/viewsheet/join")
   public JoinResponse join(@RequestBody JoinRequest body, Principal user) throws PairingException {
      requireEnabled();
      JoinSession session = joinService.join(body.code(), user);
      return new JoinResponse(session.sessionToken(), session.runtimeId(), session.ownerIdentity(),
                              session.sheetType().name().toLowerCase(), session.editorContext());
   }

   /**
    * Opens the base worksheet of the viewsheet paired to {@code sessionToken} and pairs a new
    * session for it. {@link SheetOpenService} performs every guard and the browser broadcast;
    * this endpoint only translates its {@link JoinSession} into the same join shape {@link #join}
    * returns, with {@code sheetType} read off the session rather than assumed by the caller.
    */
   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/open-base-worksheet")
   public JoinResponse openBaseWorksheet(@PathVariable String sessionToken, Principal user)
      throws Exception
   {
      requireEnabled();
      JoinSession session = openService.openBaseWorksheet(sessionToken, user);
      return new JoinResponse(session.sessionToken(), session.runtimeId(), session.ownerIdentity(),
                              session.sheetType().name().toLowerCase(), session.editorContext());
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

   /**
    * The viewsheet's <b>own</b> properties — the Composer's Viewsheet Property dialog — as
    * opposed to the assembly trio above. There is no assembly name here: the target is the sheet
    * itself, so {@link SheetPropertyService} is a sibling of {@link AssemblyPropertyService}
    * rather than a fourth assembly type.
    */
   public record ViewsheetPropertyPatchRequest(Map<String, Object> properties) {}

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/vs-properties/list")
   public Map<String, Object> listViewsheetProperties(@PathVariable String sessionToken,
                                                       Principal user)
      throws Exception
   {
      requireEnabled();
      return sheetPropertyService.list(sessionToken, user);
   }

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/vs-properties")
   public Object getViewsheetProperties(@PathVariable String sessionToken,
                                        @RequestParam(required = false) boolean raw,
                                        Principal user)
      throws Exception
   {
      requireEnabled();
      return sheetPropertyService.get(sessionToken, user, raw);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/vs-properties")
   public void setViewsheetProperties(@PathVariable String sessionToken,
                                      @RequestBody ViewsheetPropertyPatchRequest request,
                                      @RequestParam(required = false, defaultValue = "")
                                      String linkUri,
                                      Principal user)
      throws Exception
   {
      requireEnabled();
      sheetPropertyService.set(sessionToken, user, request.properties(), linkUri);
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
   public Map<String, Object> hyperlinkTypes(@PathVariable String sessionToken, Principal user)
      throws Exception
   {
      requireEnabled();
      // Static data, so nothing is exposed by skipping this -- but an agent that gets a clean
      // answer here while every other call reports SESSION_EXPIRED has a contradictory picture of
      // its own session to reason from.
      sessions.resolve(sessionToken, user);
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

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/hyperlink/link-targets")
   public Map<String, Object> listHyperlinkTargets(
      @PathVariable String sessionToken,
      @RequestParam(required = false) String folder,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Integer limit,
      Principal user) throws Exception
   {
      requireEnabled();
      return hyperlinkService.listLinkTargets(sessionToken, user, folder, query, limit);
   }

   public record ElementVisibilityRequest(String assembly, String element, String target,
                                          Boolean visible) {}
   public record PlotResizeRequest(String assembly, Double ratio, Boolean vertical,
                                   Boolean reset) {}

   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/chart/elements")
   public Map<String, Object> chartElementVocabulary(@PathVariable String sessionToken,
                                                     @RequestParam(required = false)
                                                     String assembly,
                                                     Principal user)
      throws Exception
   {
      requireEnabled();

      // With an assembly the service resolves the runtime itself, to read that chart's real axes.
      if(assembly == null || assembly.isBlank()) {
         sessions.resolve(sessionToken, user);
         return chartElementService.vocabulary();
      }

      return chartElementService.vocabulary(sessionToken, user, assembly);
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

   @GetMapping("/api/wiz/v1/agent/viewsheet/convert/vocabulary")
   public Map<String, Object> convertVocabulary() {
      requireEnabled();
      return convertService.vocabulary();
   }

   /**
    * Changes an assembly's type. Returns what was converted and, for a crosstab, what the
    * conversion discarded — the caller cannot see that from the resulting table.
    */
   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/convert")
   public Map<String, Object> convertAssembly(@PathVariable String sessionToken,
                                              @RequestBody ConvertRequest request,
                                              @RequestParam(required = false, defaultValue = "")
                                              String linkUri,
                                              Principal user)
      throws Exception
   {
      requireEnabled();
      return convertService.convert(sessionToken, user, request.assembly(), request.to(), linkUri);
   }


   @GetMapping("/api/wiz/v1/agent/viewsheet/selection/vocabulary")
   public Map<String, Object> selectionVocabulary() {
      requireEnabled();
      return selectionService.vocabulary();
   }

   /**
    * Sets a selection assembly's state. The response reports how many sort cycles it took and
    * whether an active search string scoped the apply — neither is visible in the dashboard.
    */
   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/selection")
   public Map<String, Object> setSelection(@PathVariable String sessionToken,
                                           @RequestBody SelectionRequest request,
                                           @RequestParam(required = false, defaultValue = "")
                                           String linkUri,
                                           Principal user)
      throws Exception
   {
      requireEnabled();
      return selectionService.setSelection(sessionToken, user, request.assembly(), request.values(),
                                          request.sortOrder(), request.singleSelect(), linkUri);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/selection/clear")
   public Map<String, Object> clearSelection(@PathVariable String sessionToken,
                                             @RequestBody SelectionRequest request,
                                             @RequestParam(required = false, defaultValue = "")
                                             String linkUri,
                                             Principal user)
      throws Exception
   {
      requireEnabled();
      return selectionService.clearSelection(sessionToken, user, request.assembly(), linkUri);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/selection/subtree")
   public Map<String, Object> selectSubtree(@PathVariable String sessionToken,
                                            @RequestBody SubtreeRequest request,
                                            @RequestParam(required = false, defaultValue = "")
                                            String linkUri,
                                            Principal user)
      throws Exception
   {
      requireEnabled();
      return selectionService.selectSubtree(sessionToken, user, request.assembly(), request.path(),
                                           request.mode(), linkUri);
   }

   public record SelectionRequest(String assembly, java.util.List<java.util.List<String>> values,
                                  String sortOrder, Boolean singleSelect) {}

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/calendar/display")
   public Map<String, Object> setCalendarDisplay(@PathVariable String sessionToken,
                                                 @RequestBody CalendarDisplayRequest request,
                                                 @RequestParam(required = false, defaultValue = "")
                                                 String linkUri,
                                                 Principal user)
      throws Exception
   {
      requireEnabled();
      return calendarService.setDisplay(sessionToken, user, request.assembly(), request.yearView(),
                                       request.doubleCalendar(), request.rangeComparison(), linkUri);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/calendar/clear")
   public Map<String, Object> clearCalendar(@PathVariable String sessionToken,
                                            @RequestBody CalendarDisplayRequest request,
                                            @RequestParam(required = false, defaultValue = "")
                                            String linkUri,
                                            Principal user)
      throws Exception
   {
      requireEnabled();
      return calendarService.clear(sessionToken, user, request.assembly(), linkUri);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/calendar/dates")
   public Map<String, Object> setCalendarDates(@PathVariable String sessionToken,
                                               @RequestBody CalendarDatesRequest request,
                                               @RequestParam(required = false, defaultValue = "")
                                               String linkUri,
                                               Principal user)
      throws Exception
   {
      requireEnabled();
      return calendarService.setDates(sessionToken, user, request.assembly(), request.dates(),
                                     linkUri);
   }

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/input/value")
   public Map<String, Object> setInputValue(@PathVariable String sessionToken,
                                            @RequestBody InputValueRequest request,
                                            @RequestParam(required = false, defaultValue = "")
                                            String linkUri,
                                            Principal user)
      throws Exception
   {
      requireEnabled();
      return inputService.setValue(sessionToken, user, request.assembly(), request.value(), linkUri);
   }

   public record CalendarDisplayRequest(String assembly, Boolean yearView, Boolean doubleCalendar,
                                        Boolean rangeComparison) {}
   public record CalendarDatesRequest(String assembly, java.util.List<String> dates) {}
   public record InputValueRequest(String assembly, java.util.List<Object> value) {}

   public record SubtreeRequest(String assembly, java.util.List<String> path, String mode) {}

   public record ConvertRequest(String assembly, String to) {}

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
    * The read half of the plot-size pair. It exists because the write above had no observable at
    * all: the ratio scales the plot's minimum size, which shows up as scrollbars in the browser,
    * and the agent's own render is fitted to the assembly box so it cannot show them.
    */
   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/chart/plot-size")
   public Map<String, Object> getChartPlotSize(@PathVariable String sessionToken,
                                               @RequestParam String assembly,
                                               Principal user)
      throws Exception
   {
      requireEnabled();
      return chartElementService.readPlotSize(sessionToken, user, assembly);
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
      throws Exception
   {
      requireEnabled();
      // vocabulary() is a pure static lookup with no per-sheet-type argument, so this only needs
      // resolve()'s underlying liveness/ownership/pane-scope check, not its VIEWSHEET-hardcoded
      // RuntimeViewsheet lookup -- which made this endpoint unreachable from a worksheet-only
      // session (it would 100% throw "Viewsheet runtime not found or expired", mislabeling a
      // worksheet session as an invalid viewsheet one). requireSession() is the sheet-type-agnostic
      // check resolve() itself calls before doing that VIEWSHEET-specific part.
      sessions.requireSession(sessionToken, user);
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
         return highlightRegion(row, col, colName);
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
                                   highlightRegion(row, col, colName));
   }

   /**
    * A {@code null} Region means "the caller named no location", which
    * {@link AssemblyHighlightService#list}/{@code set}/{@code delete} read as "fall forward to
    * the first data cell if the default region has nothing to highlight". Building a
    * {@code Region} unconditionally — as this used to — collapses that signal before it arrives:
    * the record's own compact constructor normalizes a null row/col to 0, so every call produced
    * a non-null {@code Region(0, 0, ...)} and the fall-forward became dead code. Omitting
    * {@code row}/{@code col} then always addressed cell (0,0) — a table's header — and was
    * refused for exposing no highlightable fields.
    *
    * <p>{@code colName} is a standalone address, not a qualifier on {@code row}/{@code col} — a
    * chart has no rows or columns, so picking one of its measures to highlight is addressed by
    * {@code colName} alone. Collapsing to {@code null} on {@code row == null && col == null}
    * without also checking {@code colName} silently drops that address: {@code read()} then
    * substitutes {@code Region.whole()}, whose {@code colName} is also null, so
    * {@code HighlightDialogService} resolves no measure and the call is refused for exposing no
    * highlightable fields. Before this method existed the same request worked, because the old
    * unconditional {@code Region(row, col, colName, ...)} construction preserved {@code colName}
    * even while normalizing {@code row}/{@code col} to 0.
    */
   private static AssemblyHighlightService.Region highlightRegion(Integer row, Integer col,
                                                                   String colName)
   {
      return row == null && col == null && colName == null ? null
         : new AssemblyHighlightService.Region(row, col, colName, false, false);
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

   // ── Layout: list_layouts, get_layout, set_print_layout, manage_device_layout, ──────────
   // ── edit_layout_objects, set_layout_table_options, layout_undo, layout_redo ────────────
   //
   // Validation and delegation only, exactly like every other family in this file (Global
   // Constraint 3 of the layout implementation plan) -- every hazard this family exists to
   // guard against (Hazard 1's preview-clone isolation, Hazard 2's undo-stack-reset-on-switch,
   // Hazard 3's scaleFont-zero refusal) is handled inside the five services below, never here.
   //
   // edit_layout_objects/set_layout_table_options address CONTENT-region objects only -- the
   // spec never surfaces "region" (header/footer/content) as a caller-facing concept, and
   // LayoutReadService.get() itself only ever projects VSLayoutService.CONTENT, so this
   // controller passes that same constant through rather than inventing a region parameter
   // nothing else in this plugin family exposes.

   /** {@code list_layouts}. */
   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/layout/list")
   public Map<String, Object> listLayouts(@PathVariable String sessionToken, Principal user)
      throws Exception
   {
      requireEnabled();
      return layoutReadService.list(sessionToken, user);
   }

   /** {@code get_layout}. */
   @GetMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/layout/{layoutName}")
   public LayoutModel getLayout(@PathVariable String sessionToken,
                                @PathVariable String layoutName, Principal user)
      throws Exception
   {
      requireEnabled();
      return layoutReadService.get(sessionToken, user, layoutName);
   }

   public record LayoutPrintPatchRequest(Map<String, Object> properties) {}

   /** {@code set_print_layout}. */
   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/layout/print")
   public void setPrintLayout(@PathVariable String sessionToken,
                              @RequestBody LayoutPrintPatchRequest request,
                              @RequestParam(required = false, defaultValue = "") String linkUri,
                              Principal user)
      throws Exception
   {
      requireEnabled();
      printDeviceLayoutPropertyService.setPrintLayout(sessionToken, user, request.properties(),
                                                       linkUri);
   }

   public record LayoutDevicePatchRequest(String action, Map<String, Object> properties) {}

   /** {@code manage_device_layout}. */
   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/layout/device")
   public void manageDeviceLayout(@PathVariable String sessionToken,
                                  @RequestBody LayoutDevicePatchRequest request,
                                  @RequestParam(required = false, defaultValue = "")
                                  String linkUri,
                                  Principal user)
      throws Exception
   {
      requireEnabled();
      printDeviceLayoutPropertyService.manageDeviceLayout(sessionToken, user, request.action(),
                                                           request.properties(), linkUri);
   }

   public record LayoutObjectsRequest(String layoutName, String op,
                                      List<Map<String, Object>> objects, Boolean confirmed) {}

   /** {@code edit_layout_objects}. */
   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/layout/objects")
   public Map<String, Object> editLayoutObjects(@PathVariable String sessionToken,
                                                @RequestBody LayoutObjectsRequest request,
                                                Principal user)
      throws Exception
   {
      requireEnabled();
      return layoutMutationService.editObjects(sessionToken, user, request.layoutName(),
                                               request.op(), VSLayoutService.CONTENT,
                                               request.objects(),
                                               Boolean.TRUE.equals(request.confirmed()));
   }

   public record LayoutTableOptionsRequest(String layoutName, String objectName,
                                           int tableLayout) {}

   /** {@code set_layout_table_options}. */
   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/layout/table-options")
   public void setLayoutTableOptions(@PathVariable String sessionToken,
                                     @RequestBody LayoutTableOptionsRequest request,
                                     Principal user)
      throws Exception
   {
      requireEnabled();
      layoutMutationService.setTableLayoutOptions(sessionToken, user, request.layoutName(),
                                                  request.objectName(), VSLayoutService.CONTENT,
                                                  request.tableLayout());
   }

   public record LayoutUndoRedoRequest(String layoutName) {}

   /** {@code layout_undo}. */
   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/layout/undo")
   public Map<String, Object> layoutUndo(@PathVariable String sessionToken,
                                         @RequestBody LayoutUndoRedoRequest request,
                                         Principal user)
      throws Exception
   {
      requireEnabled();
      return layoutUndoService.layoutUndo(sessionToken, user, request.layoutName());
   }

   /** {@code layout_redo}. */
   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/layout/redo")
   public Map<String, Object> layoutRedo(@PathVariable String sessionToken,
                                         @RequestBody LayoutUndoRedoRequest request,
                                         Principal user)
      throws Exception
   {
      requireEnabled();
      return layoutUndoService.layoutRedo(sessionToken, user, request.layoutName());
   }

   /**
    * Request body for the save endpoint. Mirrors
    * {@link inetsoft.web.wiz.worksheet.WorksheetAgentController.SaveRequest}.
    *
    * @param name  optional name/path to save the viewsheet as (e.g. {@code "agent_vs_1"} or
    *              {@code "My Folder/agent_vs_1"}). Required when the viewsheet is untitled
    *              (i.e. has not been saved before). When omitted the viewsheet is saved in-place.
    * @param scope optional scope — {@code "global"} (default) for the shared repository,
    *              {@code "user"} for the user's private folder.
    */
   public record SaveRequest(String name, String scope) {}

   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/save")
   public void save(@PathVariable String sessionToken,
                    @RequestBody(required = false) SaveRequest body,
                    Principal user) throws PairingException
   {
      requireEnabled();
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      AssetEntry entry = rvs.getEntry();
      String name = body != null && body.name() != null ? body.name().trim() : null;

      if(entry.getScope() == AssetRepository.TEMPORARY_SCOPE) {
         if(name == null || name.isEmpty()) {
            throw new PairingException(
               "Viewsheet is unsaved (\"" + entry.toView() + "\"). Provide a 'name' to save it " +
               "(e.g. \"agent_vs_1\").");
         }
      }

      if(!(user instanceof XPrincipal xp)) {
         throw new PairingException("Cannot save: agent principal is not an XPrincipal (" +
                                    user.getClass().getName() + ")");
      }

      if(name != null && !name.isEmpty()) {
         IdentityID uname = IdentityID.getIdentityIDFromKey(user.getName());
         int assetScope = body != null && "user".equalsIgnoreCase(body.scope())
            ? AssetRepository.USER_SCOPE
            : AssetRepository.GLOBAL_SCOPE;
         IdentityID owner = assetScope == AssetRepository.USER_SCOPE ? uname : null;
         entry = new AssetEntry(assetScope, AssetEntry.Type.VIEWSHEET, name, owner, uname.orgID);
      }

      try {
         viewsheetService.setViewsheet(rvs.getViewsheet(), entry, xp, true, true);
         rvs.setEntry(entry);
         rvs.setSavePoint(rvs.getCurrent());
      }
      catch(Exception e) {
         throw new PairingException("Failed to save viewsheet: " + e.getMessage(), e);
      }

      broadcast.broadcastSave(rvs, rvs.getID(), user);
   }

   /**
    * Request body for the attach-base-worksheet endpoint.
    *
    * @param path  path of an existing worksheet asset to attach as this viewsheet's base
    *              (e.g. {@code "Sample Queries/customers"} or {@code "My Folder/agent_ws_1"}).
    * @param scope optional scope to resolve {@code path} in — {@code "global"} (default) for the
    *              shared repository, {@code "user"} for the caller's private folder.
    */
   public record AttachBaseWorksheetRequest(String path, String scope) {}

   /**
    * {@code attach_base_worksheet}. Attaches an existing, named worksheet asset as this paired
    * viewsheet's base, for a viewsheet that currently has none — closing the gap
    * {@code open_base_worksheet} deliberately leaves open (it can only ever follow an
    * already-attached base, never name one). Refuses rather than silently repointing a viewsheet
    * that already has a base; use the Composer UI's own Viewsheet Properties dialog to swap one.
    *
    * <p>This never persists the viewsheet — like every other mutation on this controller, it only
    * updates the paired session's own in-memory {@link Viewsheet}. Call {@code save_viewsheet}
    * separately once ready.</p>
    *
    * @param sessionToken the token obtained at join time
    * @param body         the worksheet path to attach
    * @param user         the authenticated agent principal
    * @throws PairingException if the session is invalid/expired, the viewsheet already has a
    *                          base, no {@code path} was supplied, or {@code path} does not name a
    *                          worksheet the caller can read
    */
   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/attach-base-worksheet")
   public void attachBaseWorksheet(@PathVariable String sessionToken,
                                   @RequestBody AttachBaseWorksheetRequest body,
                                   Principal user) throws PairingException
   {
      requireEnabled();
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      Viewsheet vs = rvs.getViewsheet();

      if(vs.getBaseEntry() != null) {
         throw new PairingException(
            "This viewsheet already has a base worksheet (\"" + vs.getBaseEntry().toView() +
            "\"). attach_base_worksheet only attaches a base when there is none — use " +
            "open_base_worksheet to inspect the current one.");
      }

      String path = body != null && body.path() != null ? body.path().trim() : null;

      if(path == null || path.isEmpty()) {
         throw new PairingException("Provide a 'path' naming the worksheet asset to attach " +
                                    "(e.g. \"Sample Queries/customers\").");
      }

      if(!(user instanceof XPrincipal xp)) {
         throw new PairingException("Cannot attach base worksheet: agent principal is not an " +
                                    "XPrincipal (" + user.getClass().getName() + ")");
      }

      IdentityID uname = IdentityID.getIdentityIDFromKey(user.getName());
      int assetScope = body.scope() != null && "user".equalsIgnoreCase(body.scope())
         ? AssetRepository.USER_SCOPE
         : AssetRepository.GLOBAL_SCOPE;
      IdentityID owner = assetScope == AssetRepository.USER_SCOPE ? uname : null;
      AssetEntry entry = new AssetEntry(assetScope, AssetEntry.Type.WORKSHEET, path,
                                        owner, uname.orgID);

      AssetRepository rep = rvs.getAssetRepository();

      // permission=true so this actually enforces read access, unlike the superficially similar
      // getSheet(entry, null, false, ...) probe used elsewhere in this codebase for freshness
      // checks (e.g. ComposerViewsheetService.checkWorksheetChanged) -- that call deliberately
      // skips the permission check, which would be wrong here: this is a caller-supplied path
      // naming an arbitrary asset, and only checking existence would let an agent confirm a
      // worksheet's presence/absence without being able to read it.
      Object resolved;

      try {
         resolved = rep.getSheet(entry, xp, true, AssetContent.ALL, false);
      }
      catch(Exception e) {
         throw new PairingException(
            "no worksheet named '" + path + "' was found, or you lack permission to read it", e);
      }

      if(resolved == null) {
         throw new PairingException(
            "no worksheet named '" + path + "' was found, or you lack permission to read it");
      }

      try {
         vs.setBaseEntry(entry);
         vs.reloadBaseWorksheet(rep, xp);
      }
      catch(Exception e) {
         // reloadBaseWorksheet performs its own independent getSheet call and can fail even
         // after the probe above succeeded (a permission change, storage error, or corrupt
         // worksheet XML in the narrow window between the two fetches). Roll back setBaseEntry
         // so a failed attach leaves the session exactly as it was before this call -- without
         // this, wentry would stay set while the worksheet (ws) never got populated, reproducing
         // this bug's own broken state, and the guard above would then refuse every retry with a
         // misleading "already has a base worksheet" message.
         vs.setBaseEntry(null);
         throw new PairingException("Failed to attach base worksheet: " + e.getMessage(), e);
      }

      broadcast.broadcastRefresh(rvs, SheetType.VIEWSHEET, rvs.getID(), user);
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

   /**
    * Closes the caller's own pairing session.
    *
    * <p>Resolves the token against the caller first. Without that, this endpoint took a session
    * token and nothing else, so any authenticated caller holding or guessing another user's token
    * could terminate their pairing -- {@code Principal} was accepted and ignored while every other
    * endpoint binds the token through {@code resolve}. Skipping {@code requireEnabled()} is
    * deliberate and stays: disconnecting is always allowed.
    *
    * <p>Also flushes any layout preview-clone runtime {@link LayoutSessionService} is holding for
    * this token. This is the one real detach hook every wiz viewsheet session already closes
    * through (see the class doc), so a layout clone is disposed here rather than through a second,
    * parallel cleanup path.
    */
   @PostMapping("/api/wiz/v1/agent/viewsheet/{sessionToken}/detach")
   public void detach(@PathVariable String sessionToken, Principal user) {
      JoinSession session = sessionService.resolve(sessionToken, agentKey(user));

      if(session != null) {
         sessionService.close(sessionToken);
         layoutSessionService.disposeAll(sessionToken);
      }
   }

   private static String agentKey(Principal agent) {
      if(agent instanceof XPrincipal p) {
         IdentityID id = IdentityID.getIdentityIDFromKey(p.getName());
         return id != null ? id.convertToKey() : p.getName();
      }

      return agent != null ? agent.getName() : null;
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
   private final SheetPropertyService sheetPropertyService;
   private final AssemblyHyperlinkService hyperlinkService;
   private final ChartElementService chartElementService;
   private final ChartRegionPropertyService chartRegionService;
   private final AssemblyConditionService conditionService;
   private final AssemblyHighlightService highlightService;
   private final DateComparisonService comparisonService;
   private final AssemblyConvertService convertService;
   private final SelectionRuntimeService selectionService;
   private final CalendarDisplayService calendarService;
   private final InputValueService inputService;
   private final ViewsheetService viewsheetService;
   private final SheetAgentBroadcastService broadcast;
   private final SheetOpenService openService;
   private final LayoutSessionService layoutSessionService;
   private final LayoutReadService layoutReadService;
   private final PrintDeviceLayoutPropertyService printDeviceLayoutPropertyService;
   private final LayoutMutationService layoutMutationService;
   private final LayoutUndoService layoutUndoService;
}
