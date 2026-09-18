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
package inetsoft.web.admin.ai.autosave;

import inetsoft.util.audit.AdminChangeRecord;
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
 * Resolves a requested list of Auto Save Recycle Bin changes ({@code restore}/{@code delete}) into
 * a {@link ResolvedPlan} and hashes it -- the Auto Save Recycle Bin analog of
 * {@code RecycleBinChangePlanService}, replicated rather than shared.
 *
 * <p>{@code restore}'s own risk shape mirrors {@code RecycleBinChangePlanService} exactly: a
 * destination collision is refused loud unless {@code overwrite: true}, in which case the entry is
 * accepted but classified {@code RISK_HIGH} (restoring over an existing asset permanently destroys
 * it). {@code delete}, by contrast, is ALWAYS {@code RISK_HIGH} unconditionally -- matching Script
 * Library's own "delete is always high risk, regardless of what turns out to depend on it or not"
 * convention rather than a collision-dependent split -- even though (see
 * {@code AutoSaveRecycleBinChangesetApplyService}) its content is fully recoverable via rollback:
 * the deletion is of a person's own in-progress, unsaved work, and the gap while it stands is real
 * even though the bytes themselves are never actually lost.
 */
@Component
public class AutoSaveRecycleBinChangePlanService {
   @Autowired
   public AutoSaveRecycleBinChangePlanService(AutoSaveRecycleBinService autoSaveRecycleBinService) {
      this.autoSaveRecycleBinService = autoSaveRecycleBinService;
   }

   /**
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *                                 list, an unrecognized verb, a blank/duplicate id, an unused
    *                                 field for the resolved verb, or a {@code restore} whose
    *                                 destination collides with an existing asset without
    *                                 {@code overwrite: true}.
    * @throws inetsoft.web.security.auth.MissingResourceException if {@code id} does not name an
    *         entry visible to {@code user}.
    */
   public ResolvedPlan resolve(AutoSaveRecycleBinChangePlanRequest req, Principal user) throws Exception {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      List<PlanChange> changes = new ArrayList<>();
      Set<String> seenKeys = new HashSet<>();
      int index = 0;

      for(AutoSaveRecycleBinChangeRequest change : req.getChanges()) {
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

   private PlanChange resolveOne(String label, AutoSaveRecycleBinChangeRequest change,
                                 Principal user, Set<String> seenKeys)
      throws Exception
   {
      if(change == null) {
         throw new IllegalArgumentException(label + ": must not be null");
      }

      String verb = requireVerb(label, change.getVerb());
      String id = requireNonBlank(label + ".id", change.getId());

      if(!seenKeys.add(id)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for id \"" + id + "\"; list each entry at most once");
      }

      AutoSaveRecycleBinEntryProjection entry = autoSaveRecycleBinService.requireEntry(id, user);
      String key = "autosave:" + id;

      if(AutoSaveRecycleBinChangeRequest.VERB_DELETE.equals(verb)) {
         requireUnused(label, "assetName", change.getAssetName());
         requireUnused(label, "overwrite", change.getOverwrite());
         String description = "permanently delete the auto-saved " + entry.type() + " draft at " +
            "\"" + entry.path() + "\" from the Auto Save Recycle Bin -- the draft's own content is " +
            "captured and would be restored if this change is later rolled back, but it is gone " +
            "for as long as the deletion stands";
         return new PlanChange(key, null, project(entry), null, AdminChangeRecord.RISK_HIGH,
                               AdminChangeRecord.SCOPE_STORAGE, true, description);
      }

      // verb == restore
      String assetName = change.getAssetName() == null || change.getAssetName().isBlank() ?
         entry.path() : change.getAssetName();
      boolean overwrite = Boolean.TRUE.equals(change.getOverwrite());
      boolean collides = autoSaveRecycleBinService.wouldCollide(entry, assetName, user);

      if(collides && !overwrite) {
         throw new IllegalArgumentException(
            label + ".overwrite: the destination \"" + assetName + "\" is already occupied by an " +
            "existing " + entry.type() + " -- set overwrite: true to replace it (this permanently " +
            "deletes whatever currently occupies that path), or choose a different assetName; " +
            "refusing rather than silently doing nothing");
      }

      String risk = collides ? AdminChangeRecord.RISK_HIGH : AdminChangeRecord.RISK_LOW;
      String description = collides ?
         "restore the auto-saved " + entry.type() + " draft at \"" + entry.path() + "\" to \"" +
            assetName + "\" (overwrite: true -- this PERMANENTLY DESTROYS the existing " +
            entry.type() + " currently at that path before the restore proceeds)" :
         "restore the auto-saved " + entry.type() + " draft at \"" + entry.path() + "\" to \"" +
            assetName + "\"" + (overwrite ?
               " (overwrite: true has no effect -- nothing currently occupies the destination)" : "");
      return new PlanChange(key, null, project(entry), "(restored to \"" + assetName + "\")", risk,
                            AdminChangeRecord.SCOPE_STORAGE, true, description);
   }

   static String requireVerb(String label, String verb) {
      String trimmed = verb == null ? "" : verb.trim();

      if(AutoSaveRecycleBinChangeRequest.VERB_RESTORE.equalsIgnoreCase(trimmed)) {
         return AutoSaveRecycleBinChangeRequest.VERB_RESTORE;
      }

      if(AutoSaveRecycleBinChangeRequest.VERB_DELETE.equalsIgnoreCase(trimmed) ||
         "remove".equalsIgnoreCase(trimmed) || "purge".equalsIgnoreCase(trimmed))
      {
         return AutoSaveRecycleBinChangeRequest.VERB_DELETE;
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"restore\" or \"delete\" (\"remove\"/\"purge\" accepted as " +
         "aliases for \"delete\"), got " + String.valueOf(verb));
   }

   private static void requireUnused(String label, String field, Object value) {
      if(value != null) {
         throw new IllegalArgumentException(
            label + "." + field + ": not used for verb=delete -- refused rather than silently " +
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

   /** Canonical projection of an entry -- used as both the plan hash input and the audit
    * before-value, mirroring {@code RecycleBinChangePlanService.project}'s own role. */
   static String project(AutoSaveRecycleBinEntryProjection entry) {
      return "id=" + entry.id() + ";type=" + entry.type() + ";path=" + canonical(entry.path()) +
         ";scope=" + entry.scope() + ";owner=" + canonical(entry.owner());
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   /** SHA-256 over the canonical plan -- same field-order/control-character contract as every
    * prior area's own {@code hash} method. Deliberately excludes {@code task} (see
    * {@code TaskAuditToken}'s own javadoc for why). */
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
         throw new IllegalStateException("SHA-256 is required to hash an autosave change plan", e);
      }
   }

   private static String canonicalOrMarker(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final AutoSaveRecycleBinService autoSaveRecycleBinService;
}
