/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.ai.file;

import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.file.FileService;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.security.auth.MissingResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * REST controller for the repository-maintenance admin-plugin area. Wraps
 * {@link FileService#rebuildDependencies}/{@link FileService#repairRepositoryFolders} and
 * their status-poll counterparts -- no new service class for the delegation itself (both are
 * single, already-typed, no-diffable-input calls), just this controller plus the single-flight
 * guard and audit/error-cleanup this area's own design adds on top.
 *
 * <p>Same {@code requireSiteAdmin}/{@code AdminAiCallerGuard} shape as every prior area's own
 * controller -- copied, not shared. This is belt-and-suspenders: {@code FileService}'s own
 * caller (the enterprise {@code FileApiService} it is delegated from) independently enforces
 * {@code @Secured(EM_COMPONENT, "settings/content/data-space")} in a multi-tenant deployment too,
 * but this controller's own gate is what actually decides admission for every deployment mode,
 * not a redundant no-op.
 *
 * <p>Both maintenance verbs are genuinely deployment-wide (no organization-switching concept
 * anywhere in {@code FileService}) -- every audit record this controller writes carries a
 * {@code null} organization id, the deliberate-not-missing convention every prior area's own
 * {@code orgId} documents.
 */
@RestController
public class AdminRepositoryMaintenanceController {
   @Autowired
   public AdminRepositoryMaintenanceController(FileService fileService) {
      this.fileService = fileService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/data-space",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/repository-maintenance/rebuild-dependencies")
   public RepositoryMaintenanceToken rebuildDependencies(
      @RequestBody(required = false) RebuildDependenciesKickoffRequest request, Principal user)
   {
      requireSiteAdmin(user);
      long timeoutMs = request != null && request.getTimeoutMs() != null
         ? request.getTimeoutMs() : DEFAULT_TIMEOUT_MS;
      String token = fileService.rebuildDependencies(timeoutMs, user);
      writeAudit(token, PROPERTY_DEPENDENCY_GRAPH, ACTION_START, null, user);
      return new RepositoryMaintenanceToken(token);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/data-space",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/repository-maintenance/rebuild-dependencies/{token}")
   public RepositoryMaintenanceStatus getRebuildDependenciesStatus(
      @PathVariable("token") String token, Principal user) throws MissingResourceException
   {
      requireSiteAdmin(user);
      var raw = fileService.getRebuildDependenciesStatus(token);
      String error = null;

      if(raw.isComplete()) {
         if(raw.isFailed()) {
            error = raw.getError() != null && raw.getError().contains("TimeoutException")
               ? "A rebuild is already in progress; try again once it finishes."
               : cleanErrorMessage(raw.getError());
         }

         writeAudit(token, PROPERTY_DEPENDENCY_GRAPH, ACTION_COMPLETE,
                   raw.isFailed() ? AdminChangeRecord.STATUS_FAILED : AdminChangeRecord.STATUS_VERIFIED,
                   user);
      }

      return new RepositoryMaintenanceStatus(token, raw.isComplete(), raw.isFailed(), error);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/data-space",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/repository-maintenance/repair-repository-folders")
   public RepositoryMaintenanceToken repairRepositoryFolders(Principal user) {
      requireSiteAdmin(user);

      // Reserve the slot atomically BEFORE calling the wrapped service, not after -- claiming
      // the token only once it's already known would leave a window where two concurrent callers
      // could both pass the guard and both call FileService.repairRepositoryFolders (the exact
      // gap this guard exists to close, since the underlying method has no dedup of its own).
      if(!inFlightRepairToken.compareAndSet(null, PENDING_TOKEN)) {
         String outstanding = inFlightRepairToken.get();

         // The in-memory flag can outlive the repair it was set for (e.g. a status poll that
         // observed completion never reached this field, or nobody polled at all) -- self-check
         // the outstanding token's live status before taking a stuck flag at face value, and
         // release + retry once rather than refusing a kickoff that should actually be allowed.
         if(!PENDING_TOKEN.equals(outstanding) && isStaleCompletedRepair(outstanding)) {
            inFlightRepairToken.compareAndSet(outstanding, null);

            if(!inFlightRepairToken.compareAndSet(null, PENDING_TOKEN)) {
               String stillOutstanding = inFlightRepairToken.get();
               throw new RepositoryRepairInProgressException(
                  PENDING_TOKEN.equals(stillOutstanding) ? null : stillOutstanding);
            }
         }
         else {
            throw new RepositoryRepairInProgressException(
               PENDING_TOKEN.equals(outstanding) ? null : outstanding);
         }
      }

      try {
         String token = fileService.repairRepositoryFolders(user);
         inFlightRepairToken.set(token);
         writeAudit(token, PROPERTY_REPOSITORY_FOLDERS, ACTION_START, null, user);
         return new RepositoryMaintenanceToken(token);
      }
      catch(RuntimeException e) {
         inFlightRepairToken.compareAndSet(PENDING_TOKEN, null);
         throw e;
      }
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/data-space",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/repository-maintenance/repair-repository-folders/{token}")
   public RepositoryMaintenanceStatus getRepairRepositoryFoldersStatus(
      @PathVariable("token") String token, Principal user) throws MissingResourceException
   {
      requireSiteAdmin(user);
      var raw = fileService.getRepairRepositoryFoldersStatus(token);
      String error = null;

      if(raw.isComplete()) {
         inFlightRepairToken.compareAndSet(token, null);

         if(raw.isFailed()) {
            error = cleanErrorMessage(raw.getError());
         }

         writeAudit(token, PROPERTY_REPOSITORY_FOLDERS, ACTION_COMPLETE,
                   raw.isFailed() ? AdminChangeRecord.STATUS_FAILED : AdminChangeRecord.STATUS_VERIFIED,
                   user);
      }

      return new RepositoryMaintenanceStatus(token, raw.isComplete(), raw.isFailed(), error);
   }

   /**
    * Self-heal check for {@link #repairRepositoryFolders}'s single-flight guard: is the token
    * currently held in {@link #inFlightRepairToken} actually still running, or did its own
    * completion just never make it back to that field (no poll ever happened, or a poll happened
    * on a caller/thread that didn't observe the release)? A token no longer tracked at all by
    * {@code FileService} is treated the same as a completed one -- either way, nothing is
    * still running under it.
    *
    * <p>Uses {@link FileService#isRepairRepositoryFoldersComplete}, not
    * {@link FileService#getRepairRepositoryFoldersStatus}, deliberately: the latter removes
    * the task's entry from {@code FileService}'s own tracking map the first time it observes
    * completion, regardless of caller. This check can run for an outstanding token that belongs
    * to a *different* caller (an unrelated kickoff attempt racing in), and must never rob that
    * caller of their own genuine status-poll result as a side effect of merely peeking.
    */
   private boolean isStaleCompletedRepair(String token) {
      try {
         return fileService.isRepairRepositoryFoldersComplete(token);
      }
      catch(MissingResourceException e) {
         return true;
      }
   }

   /** Same rationale and shape as every prior area's own {@code requireSiteAdmin}. */
   private void requireSiteAdmin(Principal user) {
      AdminAiCallerGuard.requireBearerAuthenticatedRequest();

      if(!OrganizationManager.getInstance().isSiteAdmin(user)) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Site Administrator role required");
      }
   }

   /**
    * {@code FileService}'s own status DTOs put a raw, unredacted stack trace in their
    * {@code error} field ({@code e.printStackTrace(...)}) -- this must never be relayed to an
    * LLM/user verbatim. Extracts just the exception's own message from the first line, stripping
    * the leading exception-class-name prefix(es) a wrapped {@code ExecutionException} adds.
    */
   private String cleanErrorMessage(String rawStackTrace) {
      if(rawStackTrace == null || rawStackTrace.isEmpty()) {
         return "The operation failed.";
      }

      String firstLine = rawStackTrace.split("\\r?\\n", 2)[0];
      String message = firstLine
         .replaceFirst("^[\\w.$]+Exception:\\s*", "")
         .replaceFirst("^[\\w.$]+Exception:\\s*", "");

      return message.isEmpty() ? "The operation failed." : message;
   }

   /**
    * Writes a manual audit record for a verb {@code FileService} itself audits for no caller
    * today. {@code objectType} mirrors the {@code ActionRecord.OBJECT_TYPE_*} convention every
    * other admin-chat audit record uses, but is defined locally rather than added to
    * {@code ActionRecord} itself. An audit failure must never replace the real outcome -- same
    * rule every prior area's apply service follows.
    */
   private void writeAudit(String token, String property, String action, String status,
                           Principal user)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(token);
         record.setProperty(property);
         record.setObjectType(OBJECT_TYPE_REPOSITORY_MAINTENANCE);
         record.setAction(action);
         record.setStatus(status);
         record.setUserName(user == null ? null : user.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, user);
      }
      catch(Exception auditFailure) {
         LOG.error("Failed to write repository maintenance audit record for token {}", token,
                   auditFailure);
      }
   }

   @ExceptionHandler(MissingResourceException.class)
   @ResponseStatus(HttpStatus.NOT_FOUND)
   @ResponseBody
   public Map<String, String> handleMissingResource(MissingResourceException ex) {
      return Map.of("status", "not-found", "error", String.valueOf(ex.getMessage()));
   }

   @ExceptionHandler(RepositoryRepairInProgressException.class)
   @ResponseStatus(HttpStatus.CONFLICT)
   @ResponseBody
   public Map<String, String> handleRepairInProgress(RepositoryRepairInProgressException ex) {
      return Map.of("status", "conflict", "error", String.valueOf(ex.getMessage()));
   }

   /** Placeholder held in {@link #inFlightRepairToken} between reserving the guard slot and
    * obtaining the real token from {@code FileService.repairRepositoryFolders} -- never
    * exposed in a response or an exception message ({@link RepositoryRepairInProgressException}
    * only prints an outstanding token when it is a real one, not this placeholder). */
   private static final String PENDING_TOKEN = " pending";
   private static final long DEFAULT_TIMEOUT_MS = 300000;
   private static final String PROPERTY_DEPENDENCY_GRAPH = "dependency-graph";
   private static final String PROPERTY_REPOSITORY_FOLDERS = "repository-folders";
   private static final String OBJECT_TYPE_REPOSITORY_MAINTENANCE = "repository-maintenance";
   private static final String ACTION_START = "start";
   private static final String ACTION_COMPLETE = "complete";
   private static final Logger LOG =
      LoggerFactory.getLogger(AdminRepositoryMaintenanceController.class);

   private final FileService fileService;
   private final AtomicReference<String> inFlightRepairToken = new AtomicReference<>();
}
