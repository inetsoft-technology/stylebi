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
package inetsoft.web.admin.ai.sessions;

import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.user.UserService;
import inetsoft.web.admin.user.UserSessionMonitoringTableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Applies a user-session logout plan and audits each session. {@code UserService#logoutSession}
 * swallows every failure and returns normally, so success is decided only by reading the session
 * list back and checking that the session is gone.
 */
@Component
public class UserSessionChangesetApplyService {
   public static final String STATUS_APPLIED = AdminChangesetApplyService.STATUS_APPLIED;
   public static final String STATUS_PARTIAL = "partial";
   public static final String STATUS_FAILED = AdminChangeRecord.STATUS_FAILED;

   @Autowired
   public UserSessionChangesetApplyService(UserSessionChangePlanService planService,
                                           UserService userService)
   {
      this(planService, userService, VERIFY_TIMEOUT_MS, VERIFY_POLL_MS);
   }

   UserSessionChangesetApplyService(UserSessionChangePlanService planService,
                                    UserService userService, long verifyTimeoutMs,
                                    long verifyPollMs)
   {
      this.planService = planService;
      this.userService = userService;
      this.verifyTimeoutMs = verifyTimeoutMs;
      this.verifyPollMs = verifyPollMs;
   }

   public UserSessionApplyResult apply(UserSessionApplyRequest req, Principal user) {
      APPLY_LOCK.lock();

      try {
         ResolvedPlan plan = planService.resolve(req, user);

         if(req.getPlanHash() == null || !plan.planHash().equals(req.getPlanHash())) {
            throw new AdminChangesetApplyService.PlanHashMismatchException(plan);
         }

         String reviewedTask;

         try {
            reviewedTask = TaskAuditToken.verify(req.getTaskToken(), plan.planHash());
         }
         catch(TaskAuditToken.TaskTokenException e) {
            throw new AdminChangesetApplyService.TaskTokenMismatchException(plan, e.getMessage());
         }

         if(req.getReviewOutcome() == null || req.getReviewOutcome().trim().isEmpty()) {
            throw new IllegalArgumentException(
               "reviewOutcome: required -- risk: high is unconditional for this area");
         }

         String address = UserSessionChangePlanService.resolveAddress(req.getClusterNode());
         String txId = "user-session-" + String.format("%016x", RANDOM.nextLong());
         String[] ids = plan.changes().stream().map(PlanChange::property).toArray(String[]::new);
         String callError = null;

         try {
            userService.logoutSession(address, ids, user);
         }
         catch(Exception e) {
            callError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
         }

         Set<String> remaining = awaitLogout(address, user, ids);
         List<UserSessionApplyOutcome> results = new ArrayList<>();

         for(PlanChange change : plan.changes()) {
            boolean gone = !remaining.contains(change.property());
            String status = gone ? AdminChangeRecord.STATUS_VERIFIED : STATUS_FAILED;
            String error = null;

            if(!gone) {
               error = "session is still listed after the logout request" +
                  (callError == null ? "" : ": " + callError) +
                  " -- it may not have been logged out";
            }

            String after = gone ? UserSessionChangePlanService.STATE_LOGGED_OUT :
               UserSessionChangePlanService.STATE_ACTIVE;
            results.add(new UserSessionApplyOutcome(change.property(), change.currentValue(),
                                                    after, status, error));
            writeAudit(txId, reviewedTask, change.property(), change.currentValue(), after,
                       status, req.getReviewOutcome(), user);
         }

         return new UserSessionApplyResult(txId, overall(results), null, results);
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   /**
    * Reads the session list back until none of the requested sessions is listed or the timeout
    * elapses. The session list comes from a cached metrics snapshot that is refreshed with a
    * debounce after a logout, so the first read-back usually still lists the sessions.
    */
   private Set<String> awaitLogout(String address, Principal user, String[] ids) {
      long deadline = System.currentTimeMillis() + verifyTimeoutMs;
      Set<String> requested = new HashSet<>(Arrays.asList(ids));
      Set<String> remaining;

      while(true) {
         remaining = userService.getServerSessionModel(address, user).stream()
            .map(UserSessionMonitoringTableModel::sessionID)
            .collect(Collectors.toSet());

         if(remaining.stream().noneMatch(requested::contains) ||
            System.currentTimeMillis() >= deadline)
         {
            return remaining;
         }

         try {
            Thread.sleep(verifyPollMs);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            return remaining;
         }
      }
   }

   static String overall(List<UserSessionApplyOutcome> results) {
      boolean anyOk = false;
      boolean anyFailed = false;

      for(UserSessionApplyOutcome o : results) {
         if(AdminChangeRecord.STATUS_VERIFIED.equals(o.status())) {
            anyOk = true;
         }
         else {
            anyFailed = true;
         }
      }

      if(anyOk && anyFailed) {
         return STATUS_PARTIAL;
      }

      return anyFailed ? STATUS_FAILED : STATUS_APPLIED;
   }

   private void writeAudit(String txId, String task, String sessionId, String before,
                           String after, String status, String reviewOutcome, Principal user)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(sessionId);
         record.setObjectType(OBJECT_TYPE_USER_SESSION);
         record.setBeforeValue(before);
         record.setAfterValue(after);
         record.setAction(AdminChangeRecord.ACTION_APPLY);
         record.setStatus(status);
         record.setRiskLevel(AdminChangeRecord.RISK_HIGH);
         record.setSnapshotScope(AdminChangeRecord.SCOPE_VALUE);
         record.setBackupRef(null);
         record.setReviewOutcome(reviewOutcome);
         record.setUserName(user == null ? null : user.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, user);
      }
      catch(Exception auditFailure) {
         LOG.error("Failed to write user session admin change audit record for transaction {}",
                   txId, auditFailure);
      }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(UserSessionChangesetApplyService.class);
   private static final String OBJECT_TYPE_USER_SESSION = "user-session";
   private static final long VERIFY_TIMEOUT_MS = 10000L;
   private static final long VERIFY_POLL_MS = 500L;
   private static final SecureRandom RANDOM = new SecureRandom();
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final UserSessionChangePlanService planService;
   private final UserService userService;
   private final long verifyTimeoutMs;
   private final long verifyPollMs;
}
