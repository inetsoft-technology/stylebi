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
package inetsoft.web.wiz.binding;

import inetsoft.web.wiz.binding.model.AssemblyBinding;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST surface for agent-driven binding discovery, reads, and chart data-binding writes.
 *
 * <p>Pairs against a VIEWSHEET runtime exactly as {@code ViewsheetAgentController} does. The
 * {@code /join} endpoint here is a second door to the same {@link SheetJoinService}, not a
 * second session model — it exists so the binding plugin can be installed and paired on its
 * own, without the viewsheet plugin.
 *
 * <p>The discovery and read endpoints are pure reads — no dispatcher, checkpoint, or
 * broadcast. The chart endpoints mutate, and each is exactly one
 * {@code ViewsheetSessionService.mutate}, so one call is one undo checkpoint in the user's
 * Composer.
 */
@RestController
public class BindingAgentController {
   @Autowired
   public BindingAgentController(SheetAgentFeature feature,
                                 SheetJoinService joinService,
                                 SheetSessionService sessionService,
                                 ViewsheetSessionService sessions,
                                 BindableFieldsService fieldsService,
                                 BindingReadService readService,
                                 ChartBindingService chartService,
                                 ChartAestheticAgentService aestheticService,
                                 TableBindingService tableService,
                                 CalcTableService calcService)
   {
      this.feature = feature;
      this.joinService = joinService;
      this.sessionService = sessionService;
      this.sessions = sessions;
      this.fieldsService = fieldsService;
      this.readService = readService;
      this.chartService = chartService;
      this.aestheticService = aestheticService;
      this.tableService = tableService;
      this.calcService = calcService;
   }

   public record JoinRequest(String code) {}
   public record JoinResponse(String sessionToken, String runtimeId, String ownerIdentity) {}

   @PostMapping("/api/wiz/v1/agent/binding/join")
   public JoinResponse join(@RequestBody JoinRequest body, Principal user) throws PairingException {
      requireEnabled();
      JoinSession session = joinService.join(body.code(), user);
      return new JoinResponse(session.sessionToken(), session.runtimeId(), session.ownerIdentity());
   }

   @GetMapping("/api/wiz/v1/agent/binding/{sessionToken}/fields")
   public List<BindableTable> fields(@PathVariable String sessionToken,
                                     @RequestParam(required = false) String assembly,
                                     Principal user)
      throws Exception
   {
      requireEnabled();
      return fieldsService.list(sessions.runtimeId(sessionToken, user), assembly, user);
   }

   @GetMapping("/api/wiz/v1/agent/binding/{sessionToken}/binding")
   public AssemblyBinding binding(@PathVariable String sessionToken,
                                  @RequestParam String assembly,
                                  Principal user)
      throws Exception
   {
      requireEnabled();
      return readService.read(sessions.resolve(sessionToken, user), assembly);
   }

   public record ShelfRequest(String assembly, String shelf, List<FieldRef> fields) {}
   public record ChartTypeRequest(String assembly, Integer type, Boolean multi,
                                  Boolean stackMeasures, Boolean separate) {}
   public record SwapRequest(String assembly) {}

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/chart/shelf")
   public void setChartShelf(@PathVariable String sessionToken,
                             @RequestBody ShelfRequest request,
                             @RequestParam(required = false, defaultValue = "") String linkUri,
                             Principal user)
      throws Exception
   {
      requireEnabled();
      chartService.setShelf(sessionToken, user, request.assembly(), request.shelf(),
                            request.fields(), linkUri);
   }

   /** {@code field} may be null, which clears the shelf. */
   public record SingleShelfRequest(String assembly, String shelf, FieldRef field) {}

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/chart/single-shelf")
   public void setChartSingleShelf(@PathVariable String sessionToken,
                                   @RequestBody SingleShelfRequest request,
                                   @RequestParam(required = false, defaultValue = "")
                                   String linkUri,
                                   Principal user)
      throws Exception
   {
      requireEnabled();
      chartService.setSingleShelf(sessionToken, user, request.assembly(), request.shelf(),
                                  request.field(), linkUri);
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/chart/type")
   public void setChartType(@PathVariable String sessionToken,
                            @RequestBody ChartTypeRequest request,
                            @RequestParam(required = false, defaultValue = "") String linkUri,
                            Principal user)
      throws Exception
   {
      requireEnabled();

      if(request.type() == null) {
         throw new IllegalArgumentException(
            "set_chart_type requires 'type' — the GraphTypes chart-type code.");
      }

      chartService.setChartType(sessionToken, user, request.assembly(), request.type(),
                                request.multi(), request.stackMeasures(), request.separate(),
                                linkUri);
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/chart/swap-axes")
   public void swapChartAxes(@PathVariable String sessionToken,
                             @RequestBody SwapRequest request,
                             @RequestParam(required = false, defaultValue = "") String linkUri,
                             Principal user)
      throws Exception
   {
      requireEnabled();
      chartService.swapAxes(sessionToken, user, request.assembly(), linkUri);
   }

   public record AestheticFieldRequest(String assembly, String channel, FieldRef field) {}
   public record AestheticFrameRequest(String assembly, String channel,
                                       Map<String, Object> frame) {}

   @GetMapping("/api/wiz/v1/agent/binding/{sessionToken}/chart/aesthetics")
   public Map<String, Object> chartAesthetics(@PathVariable String sessionToken,
                                              @RequestParam String assembly,
                                              Principal user)
      throws Exception
   {
      requireEnabled();
      return aestheticService.read(sessionToken, user, assembly);
   }

   /**
    * Chart-type-aware discovery arrives with Phase 2, when channel validity starts to depend
    * on the chart type. Until then every supported channel applies to every chart, and saying
    * so plainly beats a list that pretends to be filtered.
    */
   @GetMapping("/api/wiz/v1/agent/binding/{sessionToken}/chart/aesthetic-options")
   public Map<String, Object> aestheticOptions(@PathVariable String sessionToken,
                                               Principal user)
   {
      requireEnabled();
      return aestheticService.options();
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/chart/aesthetic-field")
   public void setAestheticField(@PathVariable String sessionToken,
                                 @RequestBody AestheticFieldRequest request,
                                 @RequestParam(required = false, defaultValue = "") String linkUri,
                                 Principal user)
      throws Exception
   {
      requireEnabled();
      aestheticService.setField(sessionToken, user, request.assembly(), request.channel(),
                                request.field(), linkUri);
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/chart/aesthetic-field/clear")
   public void clearAestheticField(@PathVariable String sessionToken,
                                   @RequestBody AestheticFieldRequest request,
                                   @RequestParam(required = false, defaultValue = "") String linkUri,
                                   Principal user)
      throws Exception
   {
      requireEnabled();
      aestheticService.clearField(sessionToken, user, request.assembly(), request.channel(),
                                  linkUri);
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/chart/frame")
   public void setVisualFrame(@PathVariable String sessionToken,
                              @RequestBody AestheticFrameRequest request,
                              @RequestParam(required = false, defaultValue = "") String linkUri,
                              Principal user)
      throws Exception
   {
      requireEnabled();
      aestheticService.setFrame(sessionToken, user, request.assembly(), request.channel(),
                                request.frame(), linkUri);
   }

   public record TableShelfRequest(String assembly, String shelf, List<FieldRef> fields) {}

   /** {@code force} discards fields already bound to the old source; absent means false. */
   public record TableSourceRequest(String assembly, String table, Boolean force) {}
   public record TableFieldRequest(String assembly, String shelf, FieldRef field,
                                   Integer position) {}
   public record TableRemoveRequest(String assembly, String shelf, String column) {}
   public record TableMoveRequest(String assembly, String fromShelf, String toShelf,
                                  String column, Integer position) {}

   @GetMapping("/api/wiz/v1/agent/binding/{sessionToken}/table/binding")
   public Map<String, Object> tableBinding(@PathVariable String sessionToken,
                                           @RequestParam String assembly,
                                           Principal user)
      throws Exception
   {
      requireEnabled();
      return tableService.read(sessionToken, user, assembly);
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/table/fields")
   public void setTableFields(@PathVariable String sessionToken,
                              @RequestBody TableShelfRequest request,
                              Principal user)
      throws Exception
   {
      requireEnabled();
      tableService.setShelf(sessionToken, user, request.assembly(), request.shelf(),
                            request.fields());
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/table/source")
   public void setTableSource(@PathVariable String sessionToken,
                              @RequestBody TableSourceRequest request,
                              Principal user)
      throws Exception
   {
      requireEnabled();
      tableService.setSource(sessionToken, user, request.assembly(), request.table(),
                             Boolean.TRUE.equals(request.force()));
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/table/field/add")
   public void addTableField(@PathVariable String sessionToken,
                             @RequestBody TableFieldRequest request,
                             Principal user)
      throws Exception
   {
      requireEnabled();
      tableService.addField(sessionToken, user, request.assembly(), request.shelf(),
                            request.field(), request.position());
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/table/field/remove")
   public void removeTableField(@PathVariable String sessionToken,
                                @RequestBody TableRemoveRequest request,
                                Principal user)
      throws Exception
   {
      requireEnabled();
      tableService.removeField(sessionToken, user, request.assembly(), request.shelf(),
                               request.column());
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/table/field/move")
   public void moveTableField(@PathVariable String sessionToken,
                              @RequestBody TableMoveRequest request,
                              Principal user)
      throws Exception
   {
      requireEnabled();
      tableService.moveField(sessionToken, user, request.assembly(), request.fromShelf(),
                             request.toShelf(), request.column(), request.position());
   }

   public record CellBindingRequest(String assembly, Integer row, Integer col,
                                    Map<String, Object> binding) {}

   @GetMapping("/api/wiz/v1/agent/binding/{sessionToken}/calc/layout")
   public Map<String, Object> calcLayout(@PathVariable String sessionToken,
                                         @RequestParam String assembly,
                                         Principal user)
      throws Exception
   {
      requireEnabled();
      return calcService.readLayout(sessionToken, user, assembly);
   }

   @GetMapping("/api/wiz/v1/agent/binding/{sessionToken}/calc/cell")
   public Map<String, Object> calcCell(@PathVariable String sessionToken,
                                       @RequestParam String assembly,
                                       @RequestParam int row,
                                       @RequestParam int col,
                                       Principal user)
      throws Exception
   {
      requireEnabled();
      return calcService.readCell(sessionToken, user, assembly, row, col);
   }

   @GetMapping("/api/wiz/v1/agent/binding/{sessionToken}/calc/vocabulary")
   public Map<String, Object> calcVocabulary(@PathVariable String sessionToken, Principal user) {
      requireEnabled();
      return calcService.vocabulary();
   }

   @GetMapping("/api/wiz/v1/agent/binding/{sessionToken}/calc/cell/script")
   public Map<String, Object> calcCellScript(@PathVariable String sessionToken,
                                             @RequestParam String assembly,
                                             @RequestParam int row,
                                             @RequestParam int col,
                                             Principal user)
      throws Exception
   {
      requireEnabled();
      return calcService.cellScript(sessionToken, user, assembly, row, col);
   }

   @GetMapping("/api/wiz/v1/agent/binding/{sessionToken}/calc/named-groups")
   public Map<String, Object> calcNamedGroups(@PathVariable String sessionToken,
                                              @RequestParam String assembly,
                                              @RequestParam(required = false) String column,
                                              Principal user)
      throws Exception
   {
      requireEnabled();
      return calcService.namedGroups(sessionToken, user, assembly, column);
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/calc/cell")
   public void setCalcCell(@PathVariable String sessionToken,
                           @RequestBody CellBindingRequest request,
                           Principal user)
      throws Exception
   {
      requireEnabled();

      if(request.row() == null || request.col() == null) {
         throw new IllegalArgumentException(
            "set_cell_binding requires 'row' and 'col' — calc-table cells are addressed by " +
            "coordinate.");
      }

      calcService.setCellBinding(sessionToken, user, request.assembly(), request.row(),
                                 request.col(), request.binding());
   }

   public record TableSortRequest(String assembly, String shelf, String column, String direction,
                                  String sortByField, List<String> manualOrder) {}
   public record TableRankingRequest(String assembly, String shelf, String column, String mode,
                                     Integer n, String measure, Boolean others) {}
   public record TableLabelRequest(String assembly, Map<String, String> labels) {}
   public record TableOptionRequest(String assembly, Map<String, Object> options) {}

   @GetMapping("/api/wiz/v1/agent/binding/{sessionToken}/table/options")
   public Map<String, Object> tableOptionVocabulary(@PathVariable String sessionToken,
                                                    Principal user)
   {
      requireEnabled();
      return tableService.optionVocabulary();
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/table/sort")
   public void setTableSort(@PathVariable String sessionToken,
                            @RequestBody TableSortRequest request,
                            Principal user)
      throws Exception
   {
      requireEnabled();
      tableService.setSort(sessionToken, user, request.assembly(), request.shelf(),
                           request.column(),
                           new DimensionSortRanking.Sort(request.direction(),
                                                         request.sortByField(),
                                                         request.manualOrder()));
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/table/ranking")
   public void setTableRanking(@PathVariable String sessionToken,
                               @RequestBody TableRankingRequest request,
                               Principal user)
      throws Exception
   {
      requireEnabled();
      tableService.setRanking(sessionToken, user, request.assembly(), request.shelf(),
                              request.column(),
                              new DimensionSortRanking.Ranking(request.mode(), request.n(),
                                                               request.measure(),
                                                               request.others()));
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/table/labels")
   public void setTableLabels(@PathVariable String sessionToken,
                              @RequestBody TableLabelRequest request,
                              Principal user)
      throws Exception
   {
      requireEnabled();
      tableService.setColumnLabels(sessionToken, user, request.assembly(), request.labels());
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/table/option")
   public void setTableOptions(@PathVariable String sessionToken,
                               @RequestBody TableOptionRequest request,
                               Principal user)
      throws Exception
   {
      requireEnabled();
      tableService.setOptions(sessionToken, user, request.assembly(), request.options());
   }

   public record CalcLayoutRequest(String assembly, String op, Integer row, Integer col,
                                   Integer rows, Integer cols, Integer n) {}
   public record CalcCopyRequest(String assembly, String op, Integer row, Integer col,
                                 Integer rows, Integer cols, Integer targetRow,
                                 Integer targetCol) {}

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/calc/layout")
   public Map<String, Object> modifyCalcLayout(@PathVariable String sessionToken,
                                               @RequestBody CalcLayoutRequest request,
                                               Principal user)
      throws Exception
   {
      requireEnabled();

      if(request.row() == null || request.col() == null) {
         throw new IllegalArgumentException(
            "modify_calc_layout requires 'row' and 'col' — the anchor the operation applies at.");
      }

      return calcService.modifyLayout(sessionToken, user, request.assembly(), request.op(),
                                      request.row(), request.col(), request.rows(),
                                      request.cols(), request.n());
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/calc/cells/copy")
   public Map<String, Object> copyCalcCells(@PathVariable String sessionToken,
                                            @RequestBody CalcCopyRequest request,
                                            Principal user)
      throws Exception
   {
      requireEnabled();

      if(request.row() == null || request.col() == null) {
         throw new IllegalArgumentException(
            "copy_calc_cells requires 'row' and 'col' — the source range's top-left cell.");
      }

      java.awt.Rectangle source = new java.awt.Rectangle(
         request.col(), request.row(),
         request.cols() == null ? 1 : request.cols(),
         request.rows() == null ? 1 : request.rows());
      java.awt.Rectangle target = request.targetRow() == null || request.targetCol() == null
         ? null
         : new java.awt.Rectangle(request.targetCol(), request.targetRow(), source.width,
                                  source.height);

      return calcService.copyCells(sessionToken, user, request.assembly(), request.op(), source,
                                   target);
   }

   @PostMapping("/api/wiz/v1/agent/binding/{sessionToken}/detach")
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
   private final BindableFieldsService fieldsService;
   private final BindingReadService readService;
   private final ChartBindingService chartService;
   private final ChartAestheticAgentService aestheticService;
   private final TableBindingService tableService;
   private final CalcTableService calcService;
}
