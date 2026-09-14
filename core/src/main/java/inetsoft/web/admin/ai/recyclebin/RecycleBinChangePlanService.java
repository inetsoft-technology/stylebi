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
package inetsoft.web.admin.ai.recyclebin;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;

/**
 * Resolves a requested list of recycle-bin changes ({@code restore}/{@code purge}) into a {@link
 * ResolvedPlan} and hashes it -- the recycle-bin analog of {@code
 * inetsoft.web.admin.ai.AdminChangePlanService} and this run's other per-area plan services,
 * replicated rather than shared (track-a-recycle-bin/01-design.md section 2/3).
 *
 * <p>The one piece of genuinely new logic this area introduces beyond every prior area's own
 * plan-resolve shape: a {@code restore} whose destination is already occupied is refused loud
 * here -- naming the occupied path -- unless {@code overwrite: true} is also given, rather than
 * silently reproducing {@code RecycleUtils.restoreSheet}/{@code restoreWSFolder}/{@code
 * restoreRepositoryFolder}'s own current behavior of a silent no-op (repo CLAUDE.md's
 * "tool-misuse is a plugin gap" doctrine; the settled disagreement in
 * track-a-recycle-bin/03-reconcile.md). When {@code overwrite: true} IS given and a collision is
 * real, the entry is accepted but classified {@code RISK_HIGH} -- restoring over an existing asset
 * permanently destroys it via a raw hard delete, worse than {@code purge} itself (section 4 risk
 * 1).
 */
@Component
public class RecycleBinChangePlanService {
   @Autowired
   public RecycleBinChangePlanService(RecycleBinService recycleBinService) {
      this.recycleBinService = recycleBinService;
   }

   /**
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *                                 list, an unrecognized verb, a blank path, a duplicate path, an
    *                                 unused field for the resolved verb (e.g. {@code overwrite} on
    *                                 a {@code purge}), or a {@code restore} whose destination
    *                                 collides with an existing asset without {@code
    *                                 overwrite: true}.
    * @throws inetsoft.web.security.auth.MissingResourceException if {@code path} does not name a
    *         recycle bin entry visible to {@code user}.
    */
   public ResolvedPlan resolve(RecycleBinChangePlanRequest req, Principal user) throws Exception {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      List<PlanChange> changes = new ArrayList<>();
      Set<String> seenKeys = new HashSet<>();
      int index = 0;

      for(RecycleBinChangeRequest change : req.getChanges()) {
         String label = "changes[" + index++ + "]";
         changes.add(resolveOne(label, change, user, seenKeys));
      }

      String task = req.getTask().trim();
      boolean requiresAgentSignoff = changes.stream()
         .anyMatch(c -> AdminChangeRecord.RISK_HIGH.equals(c.risk()));
      String planHash = hash(changes);
      return new ResolvedPlan(task, Collections.unmodifiableList(changes), true,
                              requiresAgentSignoff, planHash, TaskAuditToken.issue(planHash, task));
   }

   private PlanChange resolveOne(String label, RecycleBinChangeRequest change, Principal user,
                                 Set<String> seenKeys)
      throws Exception
   {
      if(change == null) {
         throw new IllegalArgumentException(label + ": must not be null");
      }

      String verb = requireVerb(label, change.getVerb());
      String path = requireNonBlank(label + ".path", change.getPath());

      if(!seenKeys.add(path)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for path \"" + path + "\"; list each entry at most once");
      }

      RecycleBin.Entry entry = recycleBinService.requireEntry(path, user);
      String type = RecycleBinService.typeOf(entry);
      String key = "recyclebin:" + path;
      String orgId = entry.getOriginalUser() != null ? entry.getOriginalUser().getOrgID() :
         OrganizationManager.getInstance().getCurrentOrgID();
      String beforeProjection = project(entry, type);

      if(RecycleBinChangeRequest.VERB_PURGE.equals(verb)) {
         requireUnused(label, "overwrite", change.getOverwrite());
         String description = "permanently purge " + type + " \"" + entry.getOriginalPath() +
            "\" from the recycle bin -- this is terminal: it has no live inverse, and it is the " +
            "product's own last line of defense, so nothing recovers it afterward";
         return new PlanChange(key, orgId, beforeProjection, null, AdminChangeRecord.RISK_HIGH,
                               AdminChangeRecord.SCOPE_STORAGE, true, description);
      }

      boolean overwrite = Boolean.TRUE.equals(change.getOverwrite());
      boolean collides = recycleBinService.wouldCollide(entry);

      if(collides && !overwrite) {
         throw new IllegalArgumentException(
            label + ".overwrite: the destination \"" + entry.getOriginalPath() + "\" is already " +
            "occupied by an existing " + type + " -- set overwrite: true to replace it (this " +
            "permanently deletes whatever currently occupies that path, bypassing the recycle " +
            "bin), or choose a different entry; refusing rather than silently doing nothing");
      }

      String risk = collides ? AdminChangeRecord.RISK_HIGH : AdminChangeRecord.RISK_LOW;
      String description = collides ?
         "restore " + type + " \"" + entry.getOriginalPath() + "\" (overwrite: true -- this " +
            "PERMANENTLY DESTROYS the existing " + type + " currently at that path, bypassing the " +
            "recycle bin, before the restore itself proceeds)" :
         "restore " + type + " \"" + entry.getOriginalPath() + "\"" +
            (overwrite ? " (overwrite: true has no effect -- nothing currently occupies the " +
               "destination)" : "");
      return new PlanChange(key, orgId, beforeProjection, "(restored to \"" +
         entry.getOriginalPath() + "\")", risk, AdminChangeRecord.SCOPE_STORAGE, true, description);
   }

   static String requireVerb(String label, String verb) {
      String trimmed = verb == null ? "" : verb.trim();

      if(RecycleBinChangeRequest.VERB_RESTORE.equalsIgnoreCase(trimmed)) {
         return RecycleBinChangeRequest.VERB_RESTORE;
      }

      if(RecycleBinChangeRequest.VERB_PURGE.equalsIgnoreCase(trimmed) ||
         "remove".equalsIgnoreCase(trimmed) || "delete".equalsIgnoreCase(trimmed))
      {
         return RecycleBinChangeRequest.VERB_PURGE;
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"restore\" or \"purge\" (\"remove\"/\"delete\" accepted as " +
         "aliases for \"purge\"), got " + String.valueOf(verb));
   }

   private static void requireUnused(String label, String field, Object value) {
      if(value != null) {
         throw new IllegalArgumentException(
            label + "." + field + ": not used for verb=purge -- refused rather than silently " +
            "ignored");
      }
   }

   private static String requireNonBlank(String label, String value) {
      String trimmed = value == null ? null : value.trim();

      if(trimmed == null || trimmed.isEmpty()) {
         throw new IllegalArgumentException(label + ": required");
      }

      return trimmed;
   }

   /** Canonical projection of a recycle bin entry -- used as both the plan hash input and the
    * audit before-value, mirroring {@code ViewsheetProjection}'s own role for its area. */
   static String project(RecycleBin.Entry entry, String type) {
      return "type=" + type +
         ";path=" + canonical(entry.getPath()) +
         ";originalPath=" + canonical(entry.getOriginalPath()) +
         ";originalName=" + canonical(entry.getName()) +
         ";originalScope=" + entry.getOriginalScope() +
         ";originalOwner=" +
         canonical(entry.getOriginalUser() == null ? null : entry.getOriginalUser().convertToKey());
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   /** SHA-256 over the canonical plan -- same field-order/control-character contract as every
    * prior area's own {@code hash} method. Deliberately excludes {@code task} (see {@code
    * TaskAuditToken}'s own javadoc for why). */
   private static String hash(List<PlanChange> changes) {
      StringBuilder canonical = new StringBuilder();

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonicalOrMarker(change.currentValue())).append(SEP)
            .append(canonicalOrMarker(change.proposedValue())).append(SEP)
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
         throw new IllegalStateException("SHA-256 is required to hash a recycle bin change plan", e);
      }
   }

   private static String canonicalOrMarker(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final RecycleBinService recycleBinService;
}
