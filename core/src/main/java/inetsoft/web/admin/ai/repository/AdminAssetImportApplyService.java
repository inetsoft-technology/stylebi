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
package inetsoft.web.admin.ai.repository;

import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.content.repository.ImportAssetServiceProxy;
import inetsoft.web.admin.content.repository.model.ImportAssetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Applies a reviewed repository-import plan. Import has no live inverse in this cut
 * (01-design.md section 6) -- unlike {@code AdminChangesetApplyService}/{@code
 * ViewsheetChangesetApplyService}, there is no rollback path: the outcome is {@code "applied"} or
 * {@code "failed"} only, and the Tier-2 snapshot taken before apply is the sole recovery path.
 */
@Component
public class AdminAssetImportApplyService {
   @Autowired
   public AdminAssetImportApplyService(RepositoryImportChangePlanService planService,
                                       ImportAssetServiceProxy importService,
                                       AdminBackupService backupService)
   {
      this.planService = planService;
      this.importService = importService;
      this.backupService = backupService;
   }

   /** Thrown when {@code apply} carries a hash that does not match the freshly resolved plan. */
   public static class PlanHashMismatchException extends RuntimeException {
      public PlanHashMismatchException(RepositoryImportPlan current) {
         super("planHash: does not match the current plan; re-preview before applying");
         this.current = current.withoutTaskToken();
      }

      public RepositoryImportPlan current() { return current; }

      private final transient RepositoryImportPlan current;
   }

   /** Thrown when {@code apply} carries a missing, invalid, or foreign {@code taskToken}. */
   public static class TaskTokenMismatchException extends RuntimeException {
      public TaskTokenMismatchException(RepositoryImportPlan current, String message) {
         super(message);
         this.current = current.withoutTaskToken();
      }

      public RepositoryImportPlan current() { return current; }

      private final transient RepositoryImportPlan current;
   }

   public RepositoryImportApplyResult apply(RepositoryImportApplyRequest req, Principal user)
      throws Exception
   {
      APPLY_LOCK.lock();

      try {
         RepositoryImportPlan plan = planService.resolve(req, user);

         if(req.getPlanHash() == null || !plan.planHash().equals(req.getPlanHash())) {
            throw new PlanHashMismatchException(plan);
         }

         String reviewedTask;

         try {
            reviewedTask = TaskAuditToken.verify(req.getTaskToken(), plan.planHash());
         }
         catch(TaskAuditToken.TaskTokenException e) {
            throw new TaskTokenMismatchException(plan, e.getMessage());
         }

         if(req.getReviewOutcome() == null || req.getReviewOutcome().trim().isEmpty()) {
            throw new IllegalArgumentException(
               "reviewOutcome: required because this changeset mutates the repository");
         }

         if(plan.acknowledgeOverwriteRequired() && !req.isAcknowledgeOverwrite()) {
            throw new IllegalArgumentException(
               "acknowledgeOverwrite: must be true because this import would overwrite " +
               plan.willOverwrite().size() + " existing asset(s) (" +
               String.join(", ", plan.willOverwrite().stream().map(AssetExistenceEntry::path)
                  .toList()) + ")");
         }

         String txId = "repoimport-" + newIdSuffix();
         String backupRef = backupService.backup(txId);

         List<String> ignoreAssetNames = req.getIgnoreAssetNames() == null
            ? Collections.emptyList() : req.getIgnoreAssetNames();
         List<String> ignoreList = RepositoryImportChangePlanService.ignoreListIndices(
            plan.jarInfo().dependentAssets(), ignoreAssetNames);

         Map<String, Boolean> bookmarkResolutions = new HashMap<>();

         if(req.getBookmarkResolutions() != null) {
            for(RepositoryImportRequest.BookmarkResolutionEntry r : req.getBookmarkResolutions()) {
               if(!r.isKeepImported()) {
                  String key = r.getViewsheetPath() + "|" + r.getUser() + "|" + r.getBookmarkName();
                  bookmarkResolutions.put(key, Boolean.FALSE);
               }
            }
         }

         String targetFolderPath = blankToNull(req.getTargetFolderPath());
         String targetOwner = blankToNull(req.getTargetOwner());
         Integer locationType = targetFolderPath == null ? null
            : computeLocationTypeFrom(plan.jarInfo());

         ImportAssetResponse response = importService.importAsset(plan.stagingToken(),
            targetFolderPath, locationType, targetOwner, true, ignoreList, req.isOverwrite(),
            false, user, bookmarkResolutions);

         try {
            importService.finishImport(plan.stagingToken());
         }
         catch(Exception e) {
            LOG.warn("Failed to evict staged import context {} after apply", plan.stagingToken(), e);
         }

         boolean verified = response != null && !response.failed();
         String status = verified ? "applied" : "failed";
         writeAudit(txId, reviewedTask, plan.stagingToken(), status, backupRef,
            req.getReviewOutcome(), user);

         return new RepositoryImportApplyResult(txId, status, backupRef,
            response == null ? Collections.emptyList() : response.failedAssets(),
            response == null ? Collections.emptyList() : response.ignoreUserAssets());
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   private static Integer computeLocationTypeFrom(
      inetsoft.web.admin.content.repository.model.ExportedAssetsModel jarInfo)
   {
      boolean allWorksheets = jarInfo.selectedEntities() != null &&
         !jarInfo.selectedEntities().isEmpty() &&
         jarInfo.selectedEntities().stream()
            .allMatch(e -> e.type() == inetsoft.sree.RepositoryEntry.WORKSHEET);
      return allWorksheets ? inetsoft.sree.RepositoryEntry.WORKSHEET_FOLDER
         : inetsoft.sree.RepositoryEntry.FOLDER;
   }

   private void writeAudit(String txId, String task, String stagingToken, String status,
                           String backupRef, String reviewOutcome, Principal user)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(stagingToken);
         record.setObjectType(ActionRecord.OBJECT_TYPE_FOLDER);
         record.setAction(AdminChangeRecord.ACTION_APPLY);
         record.setStatus(status);
         record.setRiskLevel(AdminChangeRecord.RISK_HIGH);
         record.setSnapshotScope(AdminChangeRecord.SCOPE_STORAGE);
         record.setBackupRef(backupRef);
         record.setReviewOutcome(reviewOutcome);
         record.setUserName(user == null ? null : user.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, user);
      }
      catch(Exception auditFailure) {
         LOG.error("Failed to write repository-import admin change audit record for transaction {}",
                   txId, auditFailure);
      }
   }

   private static String blankToNull(String value) {
      if(value == null) {
         return null;
      }

      String trimmed = value.trim();
      return trimmed.isEmpty() ? null : trimmed;
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   private static final Logger LOG = LoggerFactory.getLogger(AdminAssetImportApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final RepositoryImportChangePlanService planService;
   private final ImportAssetServiceProxy importService;
   private final AdminBackupService backupService;
}
