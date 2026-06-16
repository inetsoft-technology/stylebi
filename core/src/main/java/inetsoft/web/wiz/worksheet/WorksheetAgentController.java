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
import inetsoft.report.composition.WorksheetService;
import inetsoft.uql.XPrincipal;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

/**
 * REST controller that exposes worksheet editing capabilities to the wiz sheet agent.
 *
 * <p>All endpoints except {@link #detach} are protected by the {@link SheetAgentFeature}
 * flag; a disabled flag returns {@code 403 Forbidden}.</p>
 *
 * <p>URL prefix: {@code /api/wiz/v1/agent/worksheet}</p>
 */
@RestController
public class WorksheetAgentController {

   @Autowired
   public WorksheetAgentController(SheetAgentFeature feature,
                                   SheetJoinService joinService,
                                   SheetSessionService sessionService,
                                   WorksheetReadService readService,
                                   WorksheetEditService editService,
                                   WorksheetService worksheetService)
   {
      this.feature = feature;
      this.joinService = joinService;
      this.sessionService = sessionService;
      this.readService = readService;
      this.editService = editService;
      this.worksheetService = worksheetService;
   }

   // ---------------------------------------------------------------------------
   // Endpoints
   // ---------------------------------------------------------------------------

   /**
    * Join a worksheet session using a single-use pairing code.
    *
    * @param code the pairing code minted by the browser-side mint endpoint
    * @param user the authenticated agent principal
    * @return session token and identifying metadata
    * @throws PairingException if the code is invalid/expired, the user doesn't match,
    *                          or the feature flag is off (feature gate throws this first
    *                          via {@link SheetJoinService#join})
    */
   @PostMapping("/api/wiz/v1/agent/worksheet/join")
   public JoinResponse join(@RequestParam String code, Principal user) throws PairingException {
      requireEnabled();
      JoinSession session = joinService.join(code, user);
      return new JoinResponse(session.sessionToken(), session.runtimeId(), session.ownerIdentity());
   }

   /**
    * Read the current structural model of the worksheet identified by {@code sessionToken}.
    *
    * @param sessionToken the token obtained at join time
    * @param user         the authenticated agent principal
    * @return a snapshot of tables, columns, filters, aggregates, joins, and sort specs
    * @throws PairingException if the session is invalid/expired or the runtime is not found
    */
   @GetMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/model")
   public WorksheetModel read(@PathVariable String sessionToken, Principal user)
      throws PairingException
   {
      requireEnabled();
      RuntimeWorksheet rws = editService.resolve(sessionToken, user);
      return readService.read(rws);
   }

   /**
    * Apply a single structural mutation to the worksheet.
    *
    * @param sessionToken the token obtained at join time
    * @param req          the edit operation and its parameters
    * @param user         the authenticated agent principal
    * @throws PairingException if the session is invalid/expired, the runtime is not found,
    *                          or the requested operation is unknown
    */
   @PostMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/edit")
   public void edit(@PathVariable String sessionToken,
                    @RequestBody EditRequest req,
                    Principal user)
      throws PairingException
   {
      requireEnabled();
      editService.apply(sessionToken, user, editor -> dispatch(editor, req));
   }

   /**
    * Persist the current in-memory worksheet state back to the asset repository.
    *
    * @param sessionToken the token obtained at join time
    * @param user         the authenticated agent principal
    * @throws PairingException if the session is invalid/expired or the runtime is not found
    */
   @PostMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/save")
   public void save(@PathVariable String sessionToken, Principal user) throws PairingException {
      requireEnabled();
      RuntimeWorksheet rws = editService.resolve(sessionToken, user);

      try {
         worksheetService.setWorksheet(rws.getWorksheet(), rws.getEntry(),
                                       (XPrincipal) user, true, true);
      }
      catch(Exception e) {
         throw new PairingException("Failed to save worksheet: " + e.getMessage());
      }
   }

   /**
    * Close the agent session.  Always succeeds (no feature-gate check) so the agent can
    * clean up even when the flag is toggled off mid-session.
    *
    * @param sessionToken the token to invalidate
    */
   @PostMapping("/api/wiz/v1/agent/worksheet/{sessionToken}/detach")
   public void detach(@PathVariable String sessionToken) {
      sessionService.close(sessionToken);
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

   private void dispatch(WorksheetEditService.Editor editor, EditRequest req)
      throws PairingException
   {
      switch(req.op() == null ? "" : req.op()) {
         case "add_column" ->
            editor.addColumn(req.table(), req.name(), req.type());
         case "remove_column" ->
            editor.removeColumn(req.table(), req.column());
         case "rename_column" ->
            editor.renameColumn(req.table(), req.column(), req.newName());
         case "add_filter" ->
            editor.addFilter(req.table(), req.field(), req.operation(),
                             req.values() != null
                                ? req.values().toArray(new String[0])
                                : new String[0]);
         case "remove_filter" ->
            editor.removeFilter(req.table(), req.field());
         case "set_group_aggregate" ->
            editor.setGroupAggregate(req.table(), req.groups(),
                                     req.aggregates() != null
                                        ? req.aggregates().stream()
                                            .map(a -> new WorksheetMutationSupport.AggregateSpec(
                                                a.field(), a.formula(), a.alias()))
                                            .toList()
                                        : List.of());
         case "add_expression_column" ->
            editor.addExpressionColumn(req.table(), req.name(), req.expression(),
                                       req.type(), req.sql());
         case "set_sort" ->
            editor.setSort(req.table(), req.field(), req.direction());
         case "add_join" ->
            editor.addJoin(req.name(), req.leftTable(), req.leftKey(),
                           req.rightTable(), req.rightKey(), req.joinType());
         case "remove_join" ->
            editor.removeJoin(req.name());
         case "add_table" ->
            editor.addTable(req.table(), new String[0]);
         default ->
            throw new PairingException("Unknown op: " + req.op());
      }
   }

   // ---------------------------------------------------------------------------
   // Inner types
   // ---------------------------------------------------------------------------

   /**
    * Minimal response returned by the {@link #join} endpoint.
    *
    * @param sessionToken  reusable token for subsequent calls
    * @param runtimeId     server-side runtime identifier of the worksheet
    * @param ownerIdentity identity key of the browser user who owns the runtime
    */
   public record JoinResponse(String sessionToken, String runtimeId, String ownerIdentity) {}

   // ---------------------------------------------------------------------------
   // Dependencies
   // ---------------------------------------------------------------------------

   private final SheetAgentFeature feature;
   private final SheetJoinService joinService;
   private final SheetSessionService sessionService;
   private final WorksheetReadService readService;
   private final WorksheetEditService editService;
   private final WorksheetService worksheetService;
}
