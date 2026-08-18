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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.SheetAgentFeature;
import inetsoft.web.wiz.pairing.SheetSessionService;
import inetsoft.web.wiz.script.ScriptGrammar;
import inetsoft.web.wiz.script.ScriptTarget;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptTargetsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller that wires {@link WorksheetScriptService}'s worksheetExpression/
 * worksheetCondition script surface (G2 Task 8) to real HTTP endpoints (G2 Task 8b).
 *
 * <p>Before this class existed, {@link WorksheetScriptService} had no caller anywhere in
 * {@code core/src/main/java} outside its own class -- its only exerciser was a unit test that
 * mocked {@link WorksheetAgentController}, so the "front door, not second writer" guarantee (that
 * routing through {@code edit_expression}/{@code edit_condition} preserves undo/redo and the
 * refresh broadcast) was asserted only against a Mockito stub. This controller is the front door.
 *
 * <p>Modeled on {@link inetsoft.web.wiz.script.ViewsheetAgentController}, the viewsheet-side
 * equivalent: session resolution via {@link #resolveSession}, and (inside
 * {@link WorksheetScriptService#write}/{@link WorksheetScriptService#read}) {@code
 * PaneScopeService.check} run before the underlying edit op is ever reached, exactly as {@code
 * ViewsheetAgentController#requirePaneScope} runs before its own mutation lambda.
 *
 * <p>No separate {@code join}/{@code detach} here -- a worksheet session, pane-scoped or not,
 * already joins and detaches through {@link WorksheetAgentController}; this surface only adds the
 * two worksheetExpression/worksheetCondition-specific reads and the one write, nested under that
 * same controller's own {@code /api/wiz/v1/agent/worksheet} namespace.
 *
 * <p>URL prefix: {@code /api/wiz/v1/agent/worksheet/{sessionToken}/script}
 */
@RestController
public class WorksheetScriptController {

   @Autowired
   public WorksheetScriptController(SheetAgentFeature feature,
                                    SheetSessionService sessionService,
                                    WorksheetEditService editService,
                                    WorksheetScriptService scriptService)
   {
      this.feature = feature;
      this.sessionService = sessionService;
      this.editService = editService;
      this.scriptService = scriptService;
   }

   // ---------------------------------------------------------------------------
   // Endpoints
   // ---------------------------------------------------------------------------

   /**
    * Enumerates every worksheetExpression/worksheetCondition target on the joined worksheet.
    *
    * <p>Scoped for a pane session exactly as {@code ViewsheetAgentController#targets} scopes the
    * viewsheet side (G2 Task 7): a whole-sheet session sees the full enumeration (discovery only
    * -- {@link #readScript}/{@link #writeScript} still refuse it per-target), a pane-scoped
    * session sees only its own grant. {@code supportedKinds()} is included here too -- before this
    * endpoint, a worksheet-joined session had no wire-level way to discover that these two kinds
    * exist at all, even though {@link ScriptGrammar#supportedKinds()} already advertised them (see
    * that method's javadoc on the "silent capability lie" this closes).
    */
   @GetMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/script/targets")
   public ScriptTargetsResponse targets(@PathVariable String sessionToken, Principal user)
      throws PairingException
   {
      requireEnabled();
      RuntimeWorksheet rws = editService.resolve(sessionToken, user);
      JoinSession session = resolveSession(sessionToken, user);
      return new ScriptTargetsResponse(ScriptGrammar.VERSION, ScriptGrammar.supportedKinds(),
                                       scriptService.list(rws, session));
   }

   /** Reads the current text of {@code target} (a worksheetExpression/worksheetCondition location). */
   @GetMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/script")
   public ScriptInfo readScript(@PathVariable String sessionToken,
                                @RequestParam(required = false) String target,
                                @RequestParam(required = false) String id,
                                @RequestParam(required = false) String kind,
                                @RequestParam(required = false) String assembly,
                                @RequestParam(required = false) String name,
                                Principal user)
      throws PairingException
   {
      requireEnabled();
      JoinSession session = resolveSession(sessionToken, user);
      ScriptTarget t = ScriptTarget.resolve(null, id, kind, assembly, name, target);
      return scriptService.read(session, user, t);
   }

   public record WriteScriptRequest(String target, String id, String kind, String assembly,
                                    String name, String text, String type, Boolean sql,
                                    String newName) {}

   /**
    * Overwrites the expression/condition text at {@code target}, through {@link
    * WorksheetScriptService#write} -- the ONLY mutating call this endpoint makes; the actual
    * write always lands via {@link WorksheetAgentController#edit}.
    *
    * <p>{@code type}/{@code sql}/{@code newName}, when present, are forwarded as {@code extras}
    * so {@link WorksheetScriptService#write} refuses them loudly (redirecting to worksheet-chat)
    * rather than this controller silently dropping them on the floor -- exactly the tool-misuse
    * failure mode an LLM caller sending a few fields too many must not trigger silently.
    */
   @PostMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/script")
   public void writeScript(@PathVariable String sessionToken,
                           @RequestBody WriteScriptRequest req, Principal user)
      throws Exception
   {
      requireEnabled();
      JoinSession session = resolveSession(sessionToken, user);
      ScriptTarget t = ScriptTarget.resolve(null, req.id(), req.kind(), req.assembly(), req.name(),
                                            req.target());
      scriptService.write(session, user, t, req.text(), extras(req));
   }

   private static Map<String, Object> extras(WriteScriptRequest req) {
      Map<String, Object> extras = new LinkedHashMap<>();

      if(req.type() != null) {
         extras.put("type", req.type());
      }

      if(req.sql() != null) {
         extras.put("sql", req.sql());
      }

      if(req.newName() != null) {
         extras.put("newName", req.newName());
      }

      return extras;
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

   /** Resolves the joined session itself -- {@link WorksheetScriptService} needs the whole thing. */
   private JoinSession resolveSession(String sessionToken, Principal user) throws PairingException {
      JoinSession session = sessionService.resolve(sessionToken, agentKey(user));

      if(session == null) {
         throw new PairingException(
            PairingException.Kind.SESSION_EXPIRED, "Invalid or expired session: " + sessionToken);
      }

      return session;
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
   private final SheetSessionService sessionService;
   private final WorksheetEditService editService;
   private final WorksheetScriptService scriptService;
}
