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
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.script.knowledge.ScriptApiService;
import inetsoft.web.wiz.script.knowledge.ScriptContextService;
import inetsoft.web.wiz.script.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

/**
 * REST controller that exposes viewsheet script editing capabilities to the wiz script agent.
 *
 * <p>All endpoints except {@link #detach} are protected by the {@link SheetAgentFeature}
 * flag; a disabled flag returns {@code 403 Forbidden}.</p>
 *
 * <p>Mutating endpoints ({@code write}, {@code enable}, {@code execute-live}) broadcast a
 * viewsheet refresh to the owning browser session after a successful operation.</p>
 *
 * <p>URL prefix: {@code /api/wiz/v1/agent/script}</p>
 */
@RestController
public class ScriptAgentController {

   @Autowired
   public ScriptAgentController(SheetAgentFeature feature,
                                 SheetJoinService joinService,
                                 SheetSessionService sessionService,
                                 SheetRuntimeAccess runtimeAccess,
                                 SheetAgentBroadcastService broadcast,
                                 ScriptReadService readService,
                                 ScriptWriteService writeService,
                                 ScriptExecuteService executeService,
                                 ScriptContextService contextService,
                                 ScriptApiService apiService,
                                 ViewsheetService viewsheetService)
   {
      this.feature = feature;
      this.joinService = joinService;
      this.sessionService = sessionService;
      this.runtimeAccess = runtimeAccess;
      this.broadcast = broadcast;
      this.readService = readService;
      this.writeService = writeService;
      this.executeService = executeService;
      this.contextService = contextService;
      this.apiService = apiService;
      this.viewsheetService = viewsheetService;
   }

   // ---------------------------------------------------------------------------
   // Join / detach
   // ---------------------------------------------------------------------------

   /**
    * Join a viewsheet session using a single-use pairing code.
    *
    * @param code the pairing code minted by the browser-side mint endpoint
    * @param user the authenticated agent principal
    * @return session token and identifying metadata
    * @throws PairingException if the code is invalid/expired, the user doesn't match,
    *                          or the feature flag is off
    */
   @PostMapping("/api/wiz/v1/agent/script/join")
   public JoinResponse join(@RequestParam String code, Principal user) throws PairingException {
      requireEnabled();
      JoinSession session = joinService.join(code, user);
      return new JoinResponse(session.sessionToken(), session.runtimeId(), session.ownerIdentity());
   }

   /**
    * Close the agent session. Always succeeds (no feature-gate check) so the agent can
    * clean up even when the flag is toggled off mid-session.
    *
    * @param sessionToken the token to invalidate
    */
   @PostMapping("/api/wiz/v1/agent/script/{sessionToken}/detach")
   public void detach(@PathVariable String sessionToken) {
      sessionService.close(sessionToken);
   }

   // ---------------------------------------------------------------------------
   // Read operations
   // ---------------------------------------------------------------------------

   /**
    * List all script locations in the viewsheet (vs-init, vs-load, per-assembly scripts,
    * and onClick handlers for clickable assemblies).
    *
    * @param sessionToken the token obtained at join time
    * @param user         the authenticated agent principal
    * @return list of {@link ScriptInfo} entries, one per script location
    * @throws PairingException if the session is invalid/expired or the runtime is not found
    */
   @GetMapping("/api/wiz/v1/agent/script/{sessionToken}/targets")
   public List<ScriptInfo> targets(@PathVariable String sessionToken, Principal user)
      throws PairingException
   {
      requireEnabled();
      RuntimeViewsheet rvs = resolveViewsheet(sessionToken, user);
      return readService.list(rvs);
   }

   /**
    * Read the script text and enabled state for a single script location.
    *
    * @param sessionToken the token obtained at join time
    * @param target       canonical target string (e.g. {@code "vs-init"},
    *                     {@code "assembly:Chart1"})
    * @param user         the authenticated agent principal
    * @return the script text and enabled flag for the requested location
    * @throws PairingException if the session is invalid/expired, the runtime is not found,
    *                          or the target string is invalid
    */
   @GetMapping("/api/wiz/v1/agent/script/{sessionToken}/script")
   public ScriptInfo readScript(@PathVariable String sessionToken,
                                @RequestParam String target,
                                Principal user)
      throws PairingException
   {
      requireEnabled();
      RuntimeViewsheet rvs = resolveViewsheet(sessionToken, user);
      ScriptTarget parsed = parseTarget(target);
      return readService.read(rvs, parsed);
   }

   /**
    * Read the live scripting environment context: all assemblies with their types,
    * the set of globally-available functions, and the predefined context variables.
    *
    * @param sessionToken the token obtained at join time
    * @param user         the authenticated agent principal
    * @return the live {@link ScriptContext} for the joined viewsheet
    * @throws PairingException if the session is invalid/expired or the runtime is not found
    */
   @GetMapping("/api/wiz/v1/agent/script/{sessionToken}/context")
   public ScriptContext context(@PathVariable String sessionToken, Principal user)
      throws PairingException
   {
      requireEnabled();
      RuntimeViewsheet rvs = resolveViewsheet(sessionToken, user);
      return contextService.context(rvs);
   }

   /**
    * Look up the signature of a single function or prototype method from the
    * StyleBI JavaScript API index ({@code js-functions.json}).
    *
    * <p>Accepted name formats: {@code "formatDate"} (top-level) or
    * {@code "Number.toFixed"} (prototype method).</p>
    *
    * @param name the function or method name to look up
    * @return the matching {@link FunctionSignature}, or {@code null} when not found
    */
   @GetMapping("/api/wiz/v1/agent/script/signature")
   public FunctionSignature signature(@RequestParam String name) {
      requireEnabled();
      return apiService.lookup(name);
   }

   // ---------------------------------------------------------------------------
   // Write operations
   // ---------------------------------------------------------------------------

   /**
    * Write script text to a script location in the viewsheet, then broadcast a refresh.
    *
    * @param sessionToken the token obtained at join time
    * @param req          must contain {@code target} and {@code text} (null clears the script)
    * @param user         the authenticated agent principal
    * @throws PairingException if the session is invalid/expired, the runtime is not found,
    *                          or the target string is invalid
    */
   @PostMapping("/api/wiz/v1/agent/script/{sessionToken}/script")
   public void writeScript(@PathVariable String sessionToken,
                           @RequestBody ScriptEditRequest req,
                           Principal user)
      throws PairingException
   {
      requireEnabled();
      JoinSession session = resolveSession(sessionToken, user);
      RuntimeViewsheet rvs = resolveViewsheetFromSession(session, user);
      ScriptTarget target = parseTarget(req.target());
      writeService.write(rvs, target, req.text());
      broadcast.broadcastRefresh(rvs, SheetType.VIEWSHEET, session.runtimeId(), user);
   }

   /**
    * Toggle the {@code scriptEnabled} flag on an assembly script location, then broadcast.
    *
    * <p>Only {@code ASSEMBLY}-location targets are supported; vs-init and vs-load always
    * execute and cannot be toggled.</p>
    *
    * @param sessionToken the token obtained at join time
    * @param req          must contain {@code target} and {@code enabled}
    * @param user         the authenticated agent principal
    * @throws PairingException if the session is invalid/expired, the target is not an
    *                          assembly location, or the assembly is not found
    */
   @PostMapping("/api/wiz/v1/agent/script/{sessionToken}/enable")
   public void enableScript(@PathVariable String sessionToken,
                            @RequestBody ScriptEditRequest req,
                            Principal user)
      throws PairingException
   {
      requireEnabled();
      JoinSession session = resolveSession(sessionToken, user);
      RuntimeViewsheet rvs = resolveViewsheetFromSession(session, user);
      ScriptTarget target = parseTarget(req.target());
      boolean enabled = req.enabled() != null && req.enabled();
      writeService.setEnabled(rvs, target, enabled);
      broadcast.broadcastRefresh(rvs, SheetType.VIEWSHEET, session.runtimeId(), user);
   }

   // ---------------------------------------------------------------------------
   // Execute operations
   // ---------------------------------------------------------------------------

   /**
    * Dry-run a script against a snapshot of the viewsheet state without committing changes.
    *
    * <p>For assembly targets the assembly's {@link inetsoft.uql.viewsheet.internal.VSAssemblyInfo}
    * is cloned before execution and restored afterwards, so the live runtime is not mutated.</p>
    *
    * @param sessionToken the token obtained at join time
    * @param req          must contain {@code target}
    * @param user         the authenticated agent principal
    * @return execution result including {@code ok}, any returned value, error details,
    *         and the list of assembly-info fields that changed during the run
    * @throws PairingException if the session is invalid/expired, the sandbox is absent,
    *                          or the target is invalid
    */
   @PostMapping("/api/wiz/v1/agent/script/{sessionToken}/execute")
   public ScriptExecResult dryRun(@PathVariable String sessionToken,
                                   @RequestBody ScriptEditRequest req,
                                   Principal user)
      throws PairingException
   {
      requireEnabled();
      RuntimeViewsheet rvs = resolveViewsheet(sessionToken, user);
      ScriptTarget target = parseTarget(req.target());
      return executeService.dryRun(rvs, target);
   }

   /**
    * Execute a script live against the viewsheet runtime.
    *
    * <p>If the script contains destructive globals ({@code saveWorksheet}, {@code runQuery},
    * {@code setCellValue}, {@code refreshData}) and {@code confirmed} is not {@code true},
    * this returns a {@link ScriptExecResult#requiresConfirmation()} result instead of
    * executing. Re-send the request with {@code confirmed: true} to proceed.</p>
    *
    * <p>On success, broadcasts a viewsheet refresh to the owning browser session.</p>
    *
    * @param sessionToken the token obtained at join time
    * @param req          must contain {@code target}; {@code confirmed} bypasses the
    *                     destructive-globals guardrail
    * @param user         the authenticated agent principal
    * @return execution result; may be a {@code requiresConfirmation} result rather than
    *         a failure when destructive globals are detected
    * @throws PairingException if the session is invalid/expired, the sandbox is absent,
    *                          or the target is invalid
    */
   @PostMapping("/api/wiz/v1/agent/script/{sessionToken}/execute-live")
   public ScriptExecResult runLive(@PathVariable String sessionToken,
                                    @RequestBody ScriptEditRequest req,
                                    Principal user)
      throws PairingException
   {
      requireEnabled();
      JoinSession session = resolveSession(sessionToken, user);
      RuntimeViewsheet rvs = resolveViewsheetFromSession(session, user);
      ScriptTarget target = parseTarget(req.target());
      boolean confirmed = req.confirmed() != null && req.confirmed();
      ScriptExecResult result = executeService.runLive(rvs, target, confirmed);

      if(result.ok()) {
         broadcast.broadcastRefresh(rvs, SheetType.VIEWSHEET, session.runtimeId(), user);
      }

      return result;
   }

   // ---------------------------------------------------------------------------
   // Persistence
   // ---------------------------------------------------------------------------

   /**
    * Persist the current in-memory viewsheet state back to the asset repository.
    *
    * @param sessionToken the token obtained at join time
    * @param user         the authenticated agent principal
    * @throws PairingException if the session is invalid/expired, the runtime is not found,
    *                          or the save operation fails
    */
   @PostMapping("/api/wiz/v1/agent/script/{sessionToken}/save")
   public void save(@PathVariable String sessionToken, Principal user) throws PairingException {
      requireEnabled();
      RuntimeViewsheet rvs = resolveViewsheet(sessionToken, user);

      try {
         viewsheetService.setViewsheet(rvs.getViewsheet(), rvs.getEntry(),
                                       user, true, true);
      }
      catch(Exception e) {
         throw new PairingException("Failed to save viewsheet: " + e.getMessage());
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

   private JoinSession resolveSession(String sessionToken, Principal agent)
      throws PairingException
   {
      String key = agentKey(agent);
      JoinSession session = sessionService.resolve(sessionToken, key);

      if(session == null) {
         throw new PairingException("Invalid or expired session: " + sessionToken);
      }

      return session;
   }

   private RuntimeViewsheet resolveViewsheet(String sessionToken, Principal agent)
      throws PairingException
   {
      JoinSession session = resolveSession(sessionToken, agent);
      return resolveViewsheetFromSession(session, agent);
   }

   private RuntimeViewsheet resolveViewsheetFromSession(JoinSession session, Principal agent)
      throws PairingException
   {
      return (RuntimeViewsheet) runtimeAccess.getSheetForPairing(
         SheetType.VIEWSHEET, session.runtimeId(), agent);
   }

   private ScriptTarget parseTarget(String target) throws PairingException {
      try {
         return ScriptTarget.parse(target);
      }
      catch(IllegalArgumentException e) {
         throw new PairingException("Invalid script target: " + target);
      }
   }

   private String agentKey(Principal agent) {
      if(agent instanceof XPrincipal p) {
         IdentityID id = IdentityID.getIdentityIDFromKey(p.getName());
         return id != null ? id.convertToKey() : p.getName();
      }

      return agent.getName();
   }

   // ---------------------------------------------------------------------------
   // Inner types
   // ---------------------------------------------------------------------------

   /**
    * Minimal response returned by the {@link #join} endpoint.
    *
    * @param sessionToken  reusable token for subsequent calls
    * @param runtimeId     server-side runtime identifier of the viewsheet
    * @param ownerIdentity identity key of the browser user who owns the runtime
    */
   public record JoinResponse(String sessionToken, String runtimeId, String ownerIdentity) {}

   // ---------------------------------------------------------------------------
   // Dependencies
   // ---------------------------------------------------------------------------

   private final SheetAgentFeature feature;
   private final SheetJoinService joinService;
   private final SheetSessionService sessionService;
   private final SheetRuntimeAccess runtimeAccess;
   private final SheetAgentBroadcastService broadcast;
   private final ScriptReadService readService;
   private final ScriptWriteService writeService;
   private final ScriptExecuteService executeService;
   private final ScriptContextService contextService;
   private final ScriptApiService apiService;
   private final ViewsheetService viewsheetService;
}
