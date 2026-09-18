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
package inetsoft.web.admin.ai.shapes;

import inetsoft.sree.security.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ImageShapes;
import inetsoft.util.DataSpace;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.ai.file.StoredAssetPathValidator;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves a requested list of custom-shape changes into a {@link ResolvedPlan} and hashes it -- the
 * custom-shape analog of {@code inetsoft.web.admin.ai.file.StoredAssetChangePlanService}, replicated
 * rather than shared, matching every prior area's own "replicate, don't generalize" precedent
 * (01-design.md section 1.5).
 *
 * <p>Also hosts the scope/path/permission resolution shared by all three custom-shape tools
 * (01-design.md section 2.1): {@link #requireScope}/{@link #resolveShapesRoot} (static) and
 * {@link #requireShapesPermission} (needs {@link SecurityEngine}) are called both here, per entry,
 * and directly by {@code AdminShapesController#list} -- one shared permission helper for
 * list/upload/delete, rather than three copies of {@code DataSpaceTreeController
 * .checkDeletePermission}'s branching.
 */
@Component
public class ShapeChangePlanService {
   @Autowired
   public ShapeChangePlanService(DataSpace dataSpace, SecurityEngine securityEngine) {
      this.dataSpace = dataSpace;
      this.securityEngine = securityEngine;
   }

   /**
    * Resolves and hashes a plan. Performs no mutation, but does perform live reads of every named
    * shape's current state (and an unconditional attempt to capture its prior bytes for a live
    * rollback -- 01-design.md section 1.4/2.3).
    */
   public ResolvedPlan resolve(ShapeChangePlanRequest req, Principal user) throws Exception {
      List<ResolvedChange> resolved = resolveEntries(req, user);
      List<PlanChange> changes = new ArrayList<>();

      for(ResolvedChange entry : resolved) {
         changes.add(entry.planChange());
      }

      String task = req.getTask().trim();
      List<PlanChange> immutableChanges = Collections.unmodifiableList(changes);
      String planHash = hash(immutableChanges);
      // Every change in this area is RISK_HIGH/SCOPE_STORAGE unconditionally (01-design.md section
      // 2.3 item 3 -- "there is no low-risk verb in this area"), so signoff is always required too.
      return new ResolvedPlan(task, immutableChanges, true, true, planHash,
                              TaskAuditToken.issue(planHash, task));
   }

   /** Package-visible so {@link ShapeChangesetApplyService} can re-resolve the SAME way, at apply
    * time, never trusting anything captured at preview time (drift protection). */
   List<ResolvedChange> resolveEntries(ShapeChangePlanRequest req, Principal user) throws Exception {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      List<ResolvedChange> result = new ArrayList<>();
      Set<String> seenPaths = new HashSet<>();
      int index = 0;

      for(ShapeChangeRequest raw : req.getChanges()) {
         String label = "changes[" + index++ + "]";

         if(raw == null) {
            throw new IllegalArgumentException(label + ": must not be null");
         }

         String verb = requireVerb(label, raw.getVerb());
         String scope = requireScope(label + ".scope", raw.getScope());
         String root = resolveShapesRoot(scope);
         requireShapesPermission(user, root);
         String name = StoredAssetPathValidator.requireSiblingName(raw.getName(), label + ".name");
         String subPath = raw.getSubPath() == null
            ? "" : StoredAssetPathValidator.requirePath(raw.getSubPath(), label + ".subPath");
         String parentDir = subPath.isEmpty() ? root : root + "/" + subPath;
         String path = parentDir + "/" + name;

         if(!seenPaths.add(path)) {
            throw new IllegalArgumentException(
               label + ": duplicate entry for \"" + path + "\"; list each path at most once");
         }

         ResolvedChange resolved = ShapeChangeRequest.VERB_UPLOAD.equals(verb)
            ? resolveUpload(label, scope, name, subPath, parentDir, path, raw)
            : resolveDelete(label, scope, name, subPath, parentDir, path, raw);
         result.add(resolved);
      }

      return result;
   }

   // ---------------------------------------------------------------- per-verb resolution

   private ResolvedChange resolveUpload(String label, String scope, String name, String subPath,
                                        String parentDir, String path, ShapeChangeRequest raw)
      throws Exception
   {
      if(raw.getContent() == null) {
         throw new IllegalArgumentException(
            label + ".content: required for verb=upload (base64)");
      }

      byte[] content;

      try {
         content = Base64.getDecoder().decode(raw.getContent());
      }
      catch(IllegalArgumentException e) {
         throw new IllegalArgumentException(
            label + ".content: must be valid base64 (" + e.getMessage() + ")");
      }

      ShapeProjection proposed =
         new ShapeProjection(name, path, scope, content.length, ShapeProjection.sha256Hex(content));
      boolean existsAlready = dataSpace.exists(null, path);
      String priorContentBase64 = null;
      ShapeProjection prior = null;

      if(existsAlready) {
         byte[] priorBytes = readAll(path);
         priorContentBase64 = Base64.getEncoder().encodeToString(priorBytes);
         prior = new ShapeProjection(
            name, path, scope, priorBytes.length, ShapeProjection.sha256Hex(priorBytes));
      }

      String description = existsAlready
         ? "overwrite shape \"" + name + "\" (" + content.length + " bytes, replacing an existing " +
           prior.byteLength() + "-byte shape)"
         : "upload shape \"" + name + "\" (" + content.length + " bytes)";
      // Both branches are unconditionally compensable (01-design.md section 1.4): a fresh upload's
      // rollback is deleting the new file, an overwrite's rollback is restoring the captured prior
      // bytes -- there is no non-compensable case in this area, unlike Stored Assets' own `write`.
      PlanChange planChange = new PlanChange(path, currentOrgId(), ShapeProjection.describe(prior),
         proposed.describe(), AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE, true,
         description);
      return new ResolvedChange(ShapeChangeRequest.VERB_UPLOAD, scope, name, subPath, parentDir, path,
         content, priorContentBase64, planChange);
   }

   private ResolvedChange resolveDelete(String label, String scope, String name, String subPath,
                                        String parentDir, String path, ShapeChangeRequest raw)
      throws Exception
   {
      if(raw.getContent() != null) {
         throw new IllegalArgumentException(label + ".content: not used for verb=delete");
      }

      if(!dataSpace.exists(null, path)) {
         throw new IllegalArgumentException(
            label + ": \"" + path + "\" does not exist -- refusing to delete a shape that is not " +
            "there");
      }

      byte[] priorBytes = readAll(path);
      String priorContentBase64 = Base64.getEncoder().encodeToString(priorBytes);
      ShapeProjection prior = new ShapeProjection(
         name, path, scope, priorBytes.length, ShapeProjection.sha256Hex(priorBytes));
      PlanChange planChange = new PlanChange(path, currentOrgId(), prior.describe(), "exists=false",
         AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE, true,
         "delete shape \"" + name + "\" (" + priorBytes.length + " bytes)");
      return new ResolvedChange(ShapeChangeRequest.VERB_DELETE, scope, name, subPath, parentDir, path,
         null, priorContentBase64, planChange);
   }

   private byte[] readAll(String path) throws Exception {
      try(InputStream in = dataSpace.getInputStream(null, path)) {
         return IOUtils.toByteArray(in);
      }
   }

   // ---------------------------------------------------------------- validation helpers

   static String requireVerb(String label, String verb) {
      String trimmed = verb == null ? null : verb.trim().toLowerCase(Locale.ROOT);

      if(ShapeChangeRequest.VERB_UPLOAD.equals(trimmed) || ShapeChangeRequest.VERB_DELETE.equals(trimmed)) {
         return trimmed;
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"upload\" or \"delete\", got " + verb);
   }

   /** Same rationale/shape as {@code AdminPresentationController#requireScopeParam}, but returns the
    * normalized scope string (not a boolean) since {@link #resolveShapesRoot} needs it too. */
   static String requireScope(String label, String scope) {
      if(scope == null) {
         throw new IllegalArgumentException(
            label + ": required, must be \"global\" or \"organization\"");
      }

      String trimmed = scope.trim();

      if(ShapeChangeRequest.SCOPE_GLOBAL.equalsIgnoreCase(trimmed)) {
         return ShapeChangeRequest.SCOPE_GLOBAL;
      }

      if(ShapeChangeRequest.SCOPE_ORGANIZATION.equalsIgnoreCase(trimmed)) {
         return ShapeChangeRequest.SCOPE_ORGANIZATION;
      }

      throw new IllegalArgumentException(
         label + ": must be \"global\" or \"organization\", got \"" + scope + "\"");
   }

   /** No {@code orgId} argument anywhere -- {@code scope="organization"} always means the calling
    * principal's own organization, resolved ambiently via {@link ImageShapes#getShapesDirectory()}'s
    * own internal current-org lookup (01-design.md section 2.1). */
   static String resolveShapesRoot(String normalizedScope) {
      return ShapeChangeRequest.SCOPE_GLOBAL.equals(normalizedScope)
         ? ImageShapes.getGlobalShapesDirectory()
         : ImageShapes.getShapesDirectory();
   }

   /**
    * Mirrors {@code DataSpaceTreeController.checkDeletePermission}'s branching (01-design.md section
    * 2.1), keyed off the REAL resolved root (never the {@code "portal/shapes"} sentinel
    * {@code DataSpaceFolderSettingsController} uses -- this wrapper always has the real path
    * already).
    */
   void requireShapesPermission(Principal user, String resolvedRoot) throws Exception {
      boolean isGlobalRoot = resolvedRoot.equals(ImageShapes.getGlobalShapesDirectory());
      boolean ok = isGlobalRoot
         ? securityEngine.checkPermission(
              user, ResourceType.EM_COMPONENT, "settings/presentation/settings", ResourceAction.ACCESS)
         : securityEngine.checkPermission(
              user, ResourceType.EM_COMPONENT, "settings/presentation/settings", ResourceAction.ACCESS) ||
           securityEngine.checkPermission(
              user, ResourceType.EM_COMPONENT, "settings/presentation/org-settings", ResourceAction.ACCESS);

      if(!ok) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "settings/presentation/settings (or org-settings for an organization-scoped call) " +
            "required");
      }
   }

   private static String currentOrgId() {
      return OrganizationManager.getInstance().getCurrentOrgID();
   }

   // ---------------------------------------------------------------- hash

   /** SHA-256 over the canonical plan, same field-order/control-character-free contract as every
    * other area's own {@code hash} method. Package-visible so {@link ShapeChangesetApplyService} can
    * recompute the identical hash from a freshly re-resolved entry list. Deliberately does NOT take
    * {@code task} -- same rationale as every prior area's own {@code hash} (a free-text paraphrase
    * must never trip a false plan-hash conflict). */
   static String hash(List<PlanChange> changes) {
      StringBuilder canonical = new StringBuilder();

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonicalValue(change.currentValue())).append(SEP)
            .append(canonicalValue(change.proposedValue())).append(SEP)
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
         throw new IllegalStateException("SHA-256 is required to hash a shape change plan", e);
      }
   }

   private static String canonicalValue(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final DataSpace dataSpace;
   private final SecurityEngine securityEngine;

   /** One resolved change: everything {@link ShapeChangesetApplyService} needs to actually execute
    * the verb, plus the {@link PlanChange} record describing it. {@code content} is null for
    * {@code delete}; {@code priorContentBase64} is null only for an upload with no existing shape at
    * {@code path} (a fresh upload's rollback is simply deleting the new file). */
   record ResolvedChange(String verb, String scope, String name, String subPath, String parentDir,
                        String path, byte[] content, String priorContentBase64, PlanChange planChange)
   {
   }
}
