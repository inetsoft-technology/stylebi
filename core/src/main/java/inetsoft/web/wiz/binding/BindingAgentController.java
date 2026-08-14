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
                                 ChartAestheticService aestheticService)
   {
      this.feature = feature;
      this.joinService = joinService;
      this.sessionService = sessionService;
      this.sessions = sessions;
      this.fieldsService = fieldsService;
      this.readService = readService;
      this.chartService = chartService;
      this.aestheticService = aestheticService;
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
   private final ChartAestheticService aestheticService;
}
