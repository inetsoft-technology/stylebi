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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.user.UserService;
import inetsoft.web.admin.user.UserSessionMonitoringTableModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;

/**
 * Resolves a requested user-session logout into a {@link ResolvedPlan}, mirroring Enterprise
 * Manager Monitoring &gt; Users ({@code UserStatusController}). Sessions are looked up through the
 * same {@link UserService#getServerSessionModel} the EM grid uses, so the caller can only target
 * sessions of its own organization that it has ADMIN permission over.
 *
 * <p>Every plan is high risk: ending a session discards the user's unsaved work and cannot be
 * undone. A session id that is not in the caller-visible list is refused with a field-named
 * message rather than silently skipped ({@code UserService#logoutSession} itself skips such ids
 * silently).
 */
@Component
public class UserSessionChangePlanService {
   public static final String STATE_ACTIVE = "active";
   public static final String STATE_LOGGED_OUT = "logged out";

   @Autowired
   public UserSessionChangePlanService(UserService userService) {
      this.userService = userService;
   }

   /** Resolves the optional cluster node filter exactly as the EM controller does. */
   static String resolveAddress(String clusterNode) {
      if(clusterNode == null || clusterNode.trim().isEmpty()) {
         return null;
      }

      try {
         return SUtil.computeServerClusterNode(clusterNode.trim());
      }
      catch(Exception e) {
         throw new IllegalArgumentException(
            "clusterNode: cannot resolve \"" + clusterNode + "\" to a cluster node: " +
            e.getMessage());
      }
   }

   public ResolvedPlan resolve(UserSessionChangePlanRequest req, Principal user) {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one session is required");
      }

      String address = resolveAddress(req.getClusterNode());
      IdentityID caller = callerIdentity(user);
      Map<String, UserSessionMonitoringTableModel> visible = new HashMap<>();

      for(UserSessionMonitoringTableModel s : userService.getServerSessionModel(address, user)) {
         visible.put(s.sessionID(), s);
      }

      List<PlanChange> changes = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      List<String> stableCurrents = new ArrayList<>();

      for(int i = 0; i < req.getChanges().size(); i++) {
         UserSessionChangeRequest change = req.getChanges().get(i);
         String label = "changes[" + i + "]";

         if(change == null || change.getSessionId() == null ||
            change.getSessionId().trim().isEmpty())
         {
            throw new IllegalArgumentException(label + ".sessionId: required -- a session id " +
               "from list_user_sessions");
         }

         String id = change.getSessionId().trim();

         if(!seen.add(id)) {
            throw new IllegalArgumentException(label + ".sessionId: \"" + id +
               "\" appears more than once in this plan");
         }

         UserSessionMonitoringTableModel s = visible.get(id);

         if(s == null) {
            throw new IllegalArgumentException(label + ".sessionId: no active session \"" + id +
               "\" visible to you (it may have ended already, belong to another organization, or " +
               "the id is wrong) -- call list_user_sessions for current ids");
         }

         // the age changes every second: it is shown to the reviewer but never hashed
         String stableState = STATE_ACTIVE + " (user " + s.user() + ", organization " +
            s.organization() + ", address " + s.address();
         String current = stableState + ", age " + s.age() + ")";
         stableCurrents.add(stableState + ")");
         String description = "log out session " + id + " of user " + s.user() +
            " -- ends that session immediately, discarding any unsaved work, and cannot be undone";

         if(isCallerSession(s, caller)) {
            description += "; WARNING: this is a session of the administrator running this " +
               "request, which may be the one in use right now";
         }

         changes.add(new PlanChange(id, null, current, STATE_LOGGED_OUT,
            AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_VALUE, true, description));
      }

      String task = req.getTask().trim();
      String planHash = hash(changes, stableCurrents, address);
      return new ResolvedPlan(task, changes, false, true, planHash,
                              TaskAuditToken.issue(planHash, task));
   }

   /**
    * The identity a session of the caller carries. A session lists its login user (bare name) and
    * its organization id, while {@code Principal.getName()} is the identity key
    * {@code name~;~orgID}, so the two are compared as identities, not as strings.
    */
   private static final String GLOBAL_ORG = "__GLOBAL__";

   private static IdentityID callerIdentity(Principal user) {
      if(user == null) {
         return null;
      }

      if(user instanceof SRPrincipal) {
         IdentityID login = ((SRPrincipal) user).getClientUserID();

         if(login != null) {
            return login;
         }
      }

      return IdentityID.getIdentityIDFromKey(user.getName());
   }

   /**
    * True when the session belongs to the caller: same user name in the same organization. Another
    * organization's user with the same bare name is not the caller; no organization on either side
    * (the global scope) matches only itself.
    */
   private static boolean isCallerSession(UserSessionMonitoringTableModel s, IdentityID caller) {
      return caller != null && s.userName() != null && s.userName().equals(caller.getName()) &&
         Objects.equals(globalToNull(s.organization()), globalToNull(caller.getOrgID()));
   }

   private static String globalToNull(String orgID) {
      return orgID == null || orgID.isEmpty() || GLOBAL_ORG.equals(orgID) ? null : orgID;
   }

   private static String hash(List<PlanChange> changes, List<String> stableCurrents,
                              String address)
   {
      StringBuilder canonical = new StringBuilder();
      canonical.append(canonical(address)).append(SEP);

      for(int i = 0; i < changes.size(); i++) {
         PlanChange change = changes.get(i);
         canonical.append(change.property()).append(SEP)
            .append(canonical(stableCurrents.get(i))).append(SEP)
            .append(canonical(change.proposedValue())).append(SEP)
            .append(change.risk()).append(SEP)
            .append(change.snapshotScope()).append(SEP);
      }

      try {
         byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(digest.length * 2);

         for(byte b : digest) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      }
      catch(NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 is required to hash a user session plan", e);
      }
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final UserService userService;
}
