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
package inetsoft.web.admin.ai.scriptlibrary;

import inetsoft.sree.security.ResourceAction;
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
 * Resolves a requested list of Script Library changes ({@code create}/{@code rename}/
 * {@code update}/{@code delete}) into a {@link ResolvedPlan} and hashes it -- the Script Library
 * analog of {@code RecycleBinChangePlanService}/{@code ViewsheetChangePlanService}, replicated
 * rather than shared, matching every prior area's own precedent.
 *
 * <p>Unlike Viewsheets' own {@code delete} (declared non-compensable), a Script Library
 * {@code delete} IS fully compensable -- its own rollback recreates the script from the text/
 * description captured at apply time, the same "capture the bytes, restore them on rollback"
 * shape Custom Shapes' {@code delete} already establishes for this plugin. {@code delete} is
 * still always classified {@code RISK_HIGH}, unconditionally, matching Viewsheets'/data sources'/
 * MV's own "delete is always high risk" precedent rather than RecycleBin's restore-specific
 * "risk depends on a side effect" shape -- a delete's own destructive nature does not depend on
 * whether anything turns out to depend on it, and the gap while the deletion stands is real even
 * though the content itself is recoverable. The DEPENDENCY check ({@code dependents}/
 * {@code force}) is a separate, orthogonal gate: with dependents found and {@code force} not set,
 * the plan is refused outright; with {@code force: true}, the plan proceeds and the dependent list
 * is carried forward as an advisory.
 */
@Component
public class ScriptLibraryChangePlanService {
   @Autowired
   public ScriptLibraryChangePlanService(ScriptLibraryService scriptLibraryService) {
      this.scriptLibraryService = scriptLibraryService;
   }

   /**
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty
    *                                 change list, an unrecognized verb, a blank/duplicate name, a
    *                                 {@code create}/{@code rename} colliding with an existing
    *                                 name, an {@code update} with nothing to change, an unused
    *                                 field for the resolved verb, or a {@code delete} with
    *                                 dependents and {@code force} not set.
    * @throws inetsoft.web.security.auth.MissingResourceException if {@code name} does not name a
    *         Script Library entry visible to {@code user} (verbs other than {@code create}).
    */
   public ResolvedPlan resolve(ScriptLibraryChangePlanRequest req, Principal user) throws Exception {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      List<PlanChange> changes = new ArrayList<>();
      Set<String> seenKeys = new HashSet<>();
      int index = 0;

      for(ScriptLibraryChangeRequest change : req.getChanges()) {
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

   private PlanChange resolveOne(String label, ScriptLibraryChangeRequest change, Principal user,
                                 Set<String> seenKeys)
      throws Exception
   {
      if(change == null) {
         throw new IllegalArgumentException(label + ": must not be null");
      }

      String verb = requireVerb(label, change.getVerb());

      return switch(verb) {
         case ScriptLibraryChangeRequest.VERB_CREATE -> resolveCreate(label, change, user, seenKeys);
         case ScriptLibraryChangeRequest.VERB_RENAME -> resolveRename(label, change, user, seenKeys);
         case ScriptLibraryChangeRequest.VERB_UPDATE -> resolveUpdate(label, change, user, seenKeys);
         default -> resolveDelete(label, change, user, seenKeys);
      };
   }

   private PlanChange resolveCreate(String label, ScriptLibraryChangeRequest change,
                                    Principal user, Set<String> seenKeys)
   {
      String name = requireNonBlank(label + ".name", change.getName());
      requireUnused(label, "newName", change.getNewName());
      requireUnused(label, "force", change.getForce());
      claim(label, "scriptlibrary:" + name, seenKeys);
      scriptLibraryService.requirePermission(name, user, ResourceAction.ADMIN);

      if(scriptLibraryService.exists(name, user)) {
         throw new IllegalArgumentException(label + ".name: a script library entry named \"" +
            name + "\" already exists -- use verb=\"update\"/\"rename\" to change it, or pick a " +
            "different name");
      }

      String description = change.getDescription() == null ? "" : change.getDescription();
      String text = change.getText() == null ? "" : change.getText();
      String proposed = project(name, description, text);
      return new PlanChange("scriptlibrary:" + name, null, null, proposed,
                            AdminChangeRecord.RISK_LOW, AdminChangeRecord.SCOPE_STORAGE, true,
                            "create script library entry \"" + name + "\"");
   }

   private PlanChange resolveRename(String label, ScriptLibraryChangeRequest change,
                                    Principal user, Set<String> seenKeys)
      throws Exception
   {
      String name = requireNonBlank(label + ".name", change.getName());
      String newName = requireNonBlank(label + ".newName", change.getNewName());
      requireUnused(label, "description", change.getDescription());
      requireUnused(label, "text", change.getText());
      requireUnused(label, "force", change.getForce());
      claim(label, "scriptlibrary:" + name, seenKeys);

      if(newName.equals(name)) {
         throw new IllegalArgumentException(
            label + ".newName: must differ from the current name \"" + name + "\"");
      }

      ScriptLibraryEntryDetail entry = scriptLibraryService.requireEntry(name, user);
      scriptLibraryService.requirePermission(name, user, ResourceAction.ADMIN);

      if(scriptLibraryService.exists(newName, user)) {
         throw new IllegalArgumentException(label + ".newName: a script library entry named \"" +
            newName + "\" already exists -- pick a different name");
      }

      String before = project(entry);
      String after = project(newName, entry.description(), entry.text());
      return new PlanChange("scriptlibrary:" + name, null, before, after,
                            AdminChangeRecord.RISK_LOW, AdminChangeRecord.SCOPE_STORAGE, true,
                            "rename script library entry \"" + name + "\" to \"" + newName +
                            "\" -- any script/viewsheet/task that calls it by name must be " +
                            "updated separately; this does not rewrite callers");
   }

   private PlanChange resolveUpdate(String label, ScriptLibraryChangeRequest change,
                                    Principal user, Set<String> seenKeys)
      throws Exception
   {
      String name = requireNonBlank(label + ".name", change.getName());
      requireUnused(label, "newName", change.getNewName());
      requireUnused(label, "text", change.getText());
      requireUnused(label, "force", change.getForce());
      claim(label, "scriptlibrary:" + name, seenKeys);

      if(change.getDescription() == null) {
         throw new IllegalArgumentException(
            label + ": nothing to change -- verb=\"update\" requires \"description\"");
      }

      ScriptLibraryEntryDetail entry = scriptLibraryService.requireEntry(name, user);
      scriptLibraryService.requirePermission(name, user, ResourceAction.ADMIN);
      String before = project(entry);
      String after = project(name, change.getDescription(), entry.text());
      return new PlanChange("scriptlibrary:" + name, null, before, after,
                            AdminChangeRecord.RISK_LOW, AdminChangeRecord.SCOPE_STORAGE, true,
                            "update the description of script library entry \"" + name + "\"");
   }

   private PlanChange resolveDelete(String label, ScriptLibraryChangeRequest change,
                                    Principal user, Set<String> seenKeys)
      throws Exception
   {
      String name = requireNonBlank(label + ".name", change.getName());
      requireUnused(label, "newName", change.getNewName());
      requireUnused(label, "description", change.getDescription());
      requireUnused(label, "text", change.getText());
      claim(label, "scriptlibrary:" + name, seenKeys);

      ScriptLibraryEntryDetail entry = scriptLibraryService.requireEntry(name, user);
      scriptLibraryService.requirePermission(name, user, ResourceAction.ADMIN);
      List<String> dependents = scriptLibraryService.dependents(name);
      boolean force = Boolean.TRUE.equals(change.getForce());

      if(!dependents.isEmpty() && !force) {
         throw new IllegalArgumentException(label + ".force: script library entry \"" + name +
            "\" is used by: " + String.join(", ", dependents) + " -- deleting it would break " +
            "those; set force: true to delete anyway (its content is captured and restored if " +
            "this change is later rolled back, but the gap while the deletion stands would still " +
            "break those callers)");
      }

      String before = project(entry);
      // Always RISK_HIGH, unconditionally -- matching Viewsheets'/data sources'/MV's own "delete
      // is always high risk regardless of what it turns out to affect" precedent, not RecycleBin's
      // restore-specific "risk depends on a side effect" shape (a delete's own destructive nature
      // does not depend on whether anything happens to depend on it). Content is still fully
      // recoverable via rollback (this area's own delete is compensable, unlike those precedents'
      // own non-compensable deletes) -- acknowledgeIrreversibleDelete is required all the same,
      // since the GAP while the deletion stands is real and this plugin's own backup/rollback
      // machinery cannot shrink it to zero.
      String description = dependents.isEmpty() ?
         "delete script library entry \"" + name + "\" (no known dependents)" :
         "delete script library entry \"" + name + "\" (force: true -- still used by: " +
            String.join(", ", dependents) + ")";
      return new PlanChange("scriptlibrary:" + name, null, before, null, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, description);
   }

   static String requireVerb(String label, String verb) {
      String trimmed = verb == null ? "" : verb.trim();

      if(ScriptLibraryChangeRequest.VERB_CREATE.equalsIgnoreCase(trimmed)) {
         return ScriptLibraryChangeRequest.VERB_CREATE;
      }

      if(ScriptLibraryChangeRequest.VERB_RENAME.equalsIgnoreCase(trimmed)) {
         return ScriptLibraryChangeRequest.VERB_RENAME;
      }

      if(ScriptLibraryChangeRequest.VERB_UPDATE.equalsIgnoreCase(trimmed)) {
         return ScriptLibraryChangeRequest.VERB_UPDATE;
      }

      if(ScriptLibraryChangeRequest.VERB_DELETE.equalsIgnoreCase(trimmed) ||
         "remove".equalsIgnoreCase(trimmed))
      {
         return ScriptLibraryChangeRequest.VERB_DELETE;
      }

      throw new IllegalArgumentException(label + ".verb: must be \"create\", \"rename\", " +
         "\"update\", or \"delete\" (\"remove\" accepted as an alias for \"delete\"), got " +
         String.valueOf(verb));
   }

   private static void claim(String label, String key, Set<String> seenKeys) {
      if(!seenKeys.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for \"" + key.substring(key.indexOf(':') + 1) +
            "\"; list each entry at most once");
      }
   }

   private static void requireUnused(String label, String field, Object value) {
      if(value != null) {
         throw new IllegalArgumentException(
            label + "." + field + ": not used for this verb -- refused rather than silently " +
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

   /** Canonical projection of an existing entry -- used as both the plan hash input and the audit
    * before-value, mirroring {@code RecycleBinChangePlanService.project}'s own role. */
   static String project(ScriptLibraryEntryDetail entry) {
      return project(entry.name(), entry.description(), entry.text());
   }

   /** {@code text} is folded into a short digest, not embedded verbatim -- a script body can be
    * arbitrarily large, and the digest is exactly as effective for drift detection (the only
    * thing the hash needs) without inflating every plan/audit record by the script's full size. */
   static String project(String name, String description, String text) {
      return "name=" + canonical(name) + ";description=" + canonical(description) +
         ";textDigest=" + digest(text);
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static String digest(String text) {
      if(text == null) {
         return NULL_MARKER;
      }

      try {
         byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(text.getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(digest.length * 2);

         for(byte b : digest) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      }
      catch(NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 is required to digest a script body", e);
      }
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
         throw new IllegalStateException("SHA-256 is required to hash a script library change plan", e);
      }
   }

   private static String canonicalOrMarker(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final ScriptLibraryService scriptLibraryService;
}
