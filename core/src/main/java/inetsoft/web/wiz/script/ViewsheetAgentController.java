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
package inetsoft.web.wiz.script;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.script.model.FunctionSignature;
import inetsoft.web.wiz.script.model.ScriptContext;
import inetsoft.web.wiz.script.model.ScriptExecResult;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptTargetInfo;
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
 * REST controller that exposes viewsheet script editing capabilities to the wiz sheet agent.
 *
 * <p>Mirrors {@code WorksheetAgentController}'s shape, scoped to script read/write/execute
 * instead of worksheet structural mutation. All endpoints except {@link #detach} are protected
 * by the {@link SheetAgentFeature} flag; a disabled flag returns {@code 403 Forbidden}.</p>
 *
 * <p>URL prefix: {@code /api/wiz/v1/agent/script}</p>
 */
@RestController
public class ViewsheetAgentController {

   @Autowired
   public ViewsheetAgentController(SheetAgentFeature feature,
                                   SheetJoinService joinService,
                                   SheetSessionService sessionService,
                                   ScriptEditService editService,
                                   ScriptReadService readService,
                                   ScriptExecuteService executeService,
                                   ScriptContextService contextService,
                                   ScriptApiService apiService,
                                   ViewsheetService viewsheetService,
                                   SheetAgentBroadcastService broadcast)
   {
      this.feature = feature;
      this.joinService = joinService;
      this.sessionService = sessionService;
      this.editService = editService;
      this.readService = readService;
      this.executeService = executeService;
      this.contextService = contextService;
      this.apiService = apiService;
      this.viewsheetService = viewsheetService;
      this.broadcast = broadcast;
   }

   // ---------------------------------------------------------------------------
   // Endpoints
   // ---------------------------------------------------------------------------

   /**
    * Join a viewsheet script session using a single-use pairing code.
    *
    * <p>Note: {@code code} is a query param (not a JSON body) — the wiz-services proxy calls
    * this with {@code POST ...?code=...}.</p>
    */
   @PostMapping("/api/wiz/v1/agent/script/join")
   public JoinResponse join(@RequestParam String code, Principal user) throws PairingException {
      requireEnabled();
      JoinSession session = joinService.join(code, user);
      return new JoinResponse(session.sessionToken(), session.runtimeId(), session.ownerIdentity());
   }

   public record JoinResponse(String sessionToken, String runtimeId, String ownerIdentity) {}

   /** Enumerates every scriptable target on the joined viewsheet. */
   @GetMapping("/api/wiz/v1/agent/script/{sessionToken}/targets")
   public List<ScriptTargetInfo> targets(@PathVariable String sessionToken, Principal user)
      throws PairingException
   {
      requireEnabled();
      RuntimeViewsheet rvs = editService.resolve(sessionToken, user);
      return readService.list(rvs);
   }

   /** Reads the current text + enabled-state of {@code target}. */
   @GetMapping("/api/wiz/v1/agent/script/{sessionToken}/script")
   public ScriptInfo readScript(@PathVariable String sessionToken,
                                @RequestParam String target, Principal user)
      throws PairingException
   {
      requireEnabled();
      RuntimeViewsheet rvs = editService.resolve(sessionToken, user);
      return readService.read(rvs, ScriptTarget.parse(target));
   }

   public record WriteScriptRequest(String target, String text) {}

   /** Overwrites the script text at {@code target} and broadcasts a refresh. */
   @PostMapping("/api/wiz/v1/agent/script/{sessionToken}/script")
   public void writeScript(@PathVariable String sessionToken,
                           @RequestBody WriteScriptRequest req, Principal user)
      throws Exception
   {
      requireEnabled();
      ScriptTarget target = ScriptTarget.parse(req.target());
      editService.apply(sessionToken, user, rvs -> editService.write(rvs, target, req.text()));
   }

   public record SetEnabledRequest(String target, boolean enabled) {}

   /** Toggles whether the script at {@code target} is enabled and broadcasts a refresh. */
   @PostMapping("/api/wiz/v1/agent/script/{sessionToken}/enable")
   public void setEnabled(@PathVariable String sessionToken,
                          @RequestBody SetEnabledRequest req, Principal user)
      throws Exception
   {
      requireEnabled();
      ScriptTarget target = ScriptTarget.parse(req.target());
      editService.apply(sessionToken, user, rvs -> editService.setEnabled(rvs, target, req.enabled()));
   }

   public record ExecuteRequest(String target) {}

   /** Dry-runs the script currently saved at {@code target} (see {@link ScriptExecuteService}). */
   @PostMapping("/api/wiz/v1/agent/script/{sessionToken}/execute")
   public ScriptExecResult execute(@PathVariable String sessionToken,
                                   @RequestBody ExecuteRequest req, Principal user)
      throws PairingException
   {
      requireEnabled();
      ScriptTarget target = ScriptTarget.parse(req.target());
      RuntimeViewsheet rvs = editService.resolve(sessionToken, user);
      return executeService.dryRun(rvs, target);
   }

   public record ExecuteLiveRequest(String target, boolean confirmed) {}

   /** Runs the script currently saved at {@code target} live and broadcasts a refresh. */
   @PostMapping("/api/wiz/v1/agent/script/{sessionToken}/execute-live")
   public ScriptExecResult executeLive(@PathVariable String sessionToken,
                                       @RequestBody ExecuteLiveRequest req, Principal user)
      throws Exception
   {
      requireEnabled();
      ScriptTarget target = ScriptTarget.parse(req.target());
      return editService.applyOnRuntime(sessionToken, user,
         rvs -> executeService.runLive(rvs, target, req.confirmed()));
   }

   /** Live introspection of the joined viewsheet's scriptable surface. */
   @GetMapping("/api/wiz/v1/agent/script/{sessionToken}/context")
   public ScriptContext context(@PathVariable String sessionToken, Principal user)
      throws PairingException
   {
      requireEnabled();
      RuntimeViewsheet rvs = editService.resolve(sessionToken, user);
      return contextService.context(rvs);
   }

   /**
    * Best-effort static API metadata lookup (Layer A). Not session-scoped — mirrors
    * wiz-services' {@code GET /v1/agent/script/signature?name=} (no {@code sessionToken}).
    */
   @GetMapping("/api/wiz/v1/agent/script/signature")
   public FunctionSignature signature(@RequestParam String name) {
      requireEnabled();
      return apiService.lookup(name);
   }

   /**
    * Persists the joined viewsheet as-is (no save-as; the wiz-services proxy sends no body).
    * Mirrors {@code WorksheetAgentController#save}'s persist + broadcast pattern.
    */
   @PostMapping("/api/wiz/v1/agent/script/{sessionToken}/save")
   public void save(@PathVariable String sessionToken, Principal user) throws PairingException {
      requireEnabled();
      RuntimeViewsheet rvs = editService.resolve(sessionToken, user);
      AssetEntry entry = rvs.getEntry();

      if(entry.getScope() == AssetRepository.TEMPORARY_SCOPE) {
         throw new PairingException(
            "Viewsheet is unsaved (\"" + entry.toView() + "\"). Save it with a name in the " +
            "StyleBI Composer first (there is no save-as through this tool), then call " +
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

   /**
    * Invalidate a session. Idempotent — a foreign/expired token is silently ignored, matching
    * {@code WorksheetAgentController#detach}. Not feature-gated (always allowed to disconnect).
    */
   @PostMapping("/api/wiz/v1/agent/script/{sessionToken}/detach")
   public void detach(@PathVariable String sessionToken, Principal user) {
      JoinSession s = sessionService.resolve(sessionToken, agentKey(user));

      if(s != null) {
         sessionService.close(sessionToken);
      }
   }

   // ---------------------------------------------------------------------------
   // Internal helpers
   // ---------------------------------------------------------------------------

   private void requireEnabled() {
      if(!feature.isEnabled()) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                           "Sheet agent pairing is disabled");
      }
   }

   private static String agentKey(Principal agent) {
      if(agent instanceof XPrincipal p) {
         IdentityID id = IdentityID.getIdentityIDFromKey(p.getName());
         return id != null ? id.convertToKey() : p.getName();
      }

      return agent != null ? agent.getName() : null;
   }

   // ---------------------------------------------------------------------------
   // Exception handling
   // ---------------------------------------------------------------------------

   @ExceptionHandler(PairingException.class)
   public ResponseEntity<Map<String, String>> handlePairingException(PairingException e) {
      HttpStatus status = switch(e.getKind()) {
         case SESSION_EXPIRED  -> HttpStatus.NOT_FOUND;
         case USER_MISMATCH,
              FEATURE_DISABLED -> HttpStatus.FORBIDDEN;
         case RATE_LIMITED     -> HttpStatus.TOO_MANY_REQUESTS;
         case INTERNAL        -> HttpStatus.INTERNAL_SERVER_ERROR;
         default              -> HttpStatus.BAD_REQUEST;
      };
      Map<String, String> body = new LinkedHashMap<>();
      body.put("error", e.getMessage());
      body.put("errorCode", e.getKind().name());
      return ResponseEntity.status(status).body(body);
   }

   // ---------------------------------------------------------------------------
   // Dependencies
   // ---------------------------------------------------------------------------

   private final SheetAgentFeature feature;
   private final SheetJoinService joinService;
   private final SheetSessionService sessionService;
   private final ScriptEditService editService;
   private final ScriptReadService readService;
   private final ScriptExecuteService executeService;
   private final ScriptContextService contextService;
   private final ScriptApiService apiService;
   private final ViewsheetService viewsheetService;
   private final SheetAgentBroadcastService broadcast;
}
