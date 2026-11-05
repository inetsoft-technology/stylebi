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

import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.DataSpace;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.PlanHashInputs;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves a requested list of stored-asset changes into a {@link ResolvedPlan} and hashes it --
 * the stored-asset analog of {@code inetsoft.web.admin.ai.AdminChangePlanService} and (per
 * 01-design.md section 6.3) {@code DataSourceChangePlanService}, replicated rather than shared,
 * matching every prior area's own "replicate, don't generalize" precedent.
 *
 * <p><b>Scope deviation from 01-design.md/03-reconcile.md, recorded here per this build's own
 * reporting duty:</b> {@code write}'s {@code sourcePath}/multipart binary-upload path is NOT
 * implemented in this cut -- only {@code content} (plain UTF-8 text) is accepted. See 04-build.md
 * for why (time-boxing a genuinely open, unprecedented "JSON plan + attached binary part" request
 * shape flagged as an open risk item by both 01-design.md section 9 item 3 and 02-verify-plan.md)
 * and what a follow-up would need.
 */
@Component
public class StoredAssetChangePlanService {
   @Autowired
   public StoredAssetChangePlanService(DataSpace dataSpace) {
      this.dataSpace = dataSpace;
   }

   /**
    * Resolves and hashes a plan. Performs no mutation, but does perform live reads of every named
    * path's current state (and, for a potential overwrite/delete, an attempt to capture its prior
    * text content for a live rollback).
    */
   public ResolvedPlan resolve(StoredAssetChangePlanRequest req, Principal user) {
      List<ResolvedChange> resolved = resolveEntries(req);
      List<PlanChange> changes = new ArrayList<>();

      for(ResolvedChange entry : resolved) {
         changes.add(entry.planChange());
      }

      String task = req.getTask().trim();
      List<PlanChange> immutableChanges = Collections.unmodifiableList(changes);
      String planHash = hash(immutableChanges, hiddenInputs(resolved));
      boolean signoff = resolved.stream()
         .anyMatch(r -> AdminChangeRecord.RISK_HIGH.equals(r.planChange().risk()));
      return new ResolvedPlan(task, immutableChanges, true, signoff, planHash,
                              TaskAuditToken.issue(planHash, task));
   }

   /** Package-visible so {@link StoredAssetChangesetApplyService} can re-resolve the SAME way, at
    * apply time, never trusting anything captured at preview time (drift protection). */
   List<ResolvedChange> resolveEntries(StoredAssetChangePlanRequest req) {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      List<ResolvedChange> result = new ArrayList<>();
      Set<String> seenPaths = new HashSet<>();
      int index = 0;

      for(StoredAssetChangeRequest raw : req.getChanges()) {
         String label = "changes[" + index++ + "]";

         if(raw == null) {
            throw new IllegalArgumentException(label + ": must not be null");
         }

         String unitType = requireUnitType(label, raw.getUnitType());
         String verb = requireVerb(label, unitType, raw.getVerb());
         String path = StoredAssetPathValidator.requirePath(raw.getPath(), label + ".path");

         if(!seenPaths.add(path)) {
            throw new IllegalArgumentException(
               label + ": duplicate entry for \"" + path + "\"; list each path at most once");
         }

         ResolvedChange resolved;

         switch(verb) {
            case StoredAssetChangeRequest.VERB_CREATE:
               resolved = resolveCreate(label, path, raw);
               break;
            case StoredAssetChangeRequest.VERB_WRITE:
               resolved = resolveWrite(label, path, raw);
               break;
            case StoredAssetChangeRequest.VERB_RENAME:
               resolved = resolveRename(label, unitType, path, raw);
               break;
            default:
               resolved = resolveDelete(label, unitType, path, raw);
         }

         result.add(resolved);
      }

      return result;
   }

   // ---------------------------------------------------------------- per-verb resolution

   private ResolvedChange resolveCreate(String label, String path, StoredAssetChangeRequest raw) {
      if(raw.getContent() != null || raw.getNewName() != null) {
         throw new IllegalArgumentException(label + ": content/newName not used for verb=create");
      }

      StoredAssetPathValidator.requireValidNameChars(leafOf(path), label + ".path");

      if(dataSpace.exists(null, path)) {
         throw new IllegalArgumentException(
            label + ": \"" + path + "\" already exists -- refusing to create over an existing " +
            "entry (use a different path, or verb=write to modify a file's content)");
      }

      PlanChange planChange = new PlanChange(path, currentOrgId(), "exists=false",
         "exists=true (folder)", AdminChangeRecord.RISK_LOW, AdminChangeRecord.SCOPE_STORAGE, true,
         "create folder \"" + path + "\"");
      return new ResolvedChange(StoredAssetChangeRequest.UNIT_FOLDER,
         StoredAssetChangeRequest.VERB_CREATE, path, null, null, true, null, planChange, Map.of());
   }

   private ResolvedChange resolveWrite(String label, String path, StoredAssetChangeRequest raw) {
      if(raw.getNewName() != null) {
         throw new IllegalArgumentException(label + ".newName: not used for verb=write");
      }

      StoredAssetPathValidator.requireValidNameChars(leafOf(path), label + ".path");

      String content = raw.getContent();

      if(content == null) {
         throw new IllegalArgumentException(
            label + ".content: required for verb=write (plain UTF-8 text; a binary sourcePath " +
            "upload is not supported in this cut -- see 04-build.md)");
      }

      int byteLength = content.getBytes(StandardCharsets.UTF_8).length;

      if(byteLength > WRITE_CONTENT_CAP_BYTES) {
         throw new IllegalArgumentException(
            label + ".content: " + byteLength + " bytes, over the " + WRITE_CONTENT_CAP_BYTES +
            "-byte inline write cap");
      }

      if(dataSpace.exists(null, path) && dataSpace.isDirectory(path)) {
         throw new IllegalArgumentException(label + ": \"" + path + "\" is a folder, not a file");
      }

      boolean exists = dataSpace.exists(null, path);
      byte[] currentBytes = exists ? tryReadBytes(path) : null;
      String priorText = decodeUtf8(currentBytes);
      boolean compensable = !exists || priorText != null;
      String currentSignature = exists
         ? "exists=true;size=" + dataSpace.getFileLength(null, path) : "exists=false";
      String risk = exists ? AdminChangeRecord.RISK_HIGH : AdminChangeRecord.RISK_LOW;
      String description = exists
         ? "overwrite file \"" + path + "\" (" + byteLength + " bytes)" +
           (compensable ? "" : " (non-compensable: prior content could not be captured as text)")
         : "create file \"" + path + "\" (" + byteLength + " bytes)";
      PlanChange planChange = new PlanChange(path, currentOrgId(), currentSignature,
         "exists=true;size=" + byteLength, risk, AdminChangeRecord.SCOPE_STORAGE, true, description);
      // The projection above carries only exists/size, so the reviewed content and the current
      // file's bytes are bound into the plan hash through hidden digests (Bug #79930, mirroring
      // #79429): a same-length swap of content, or a same-size concurrent edit, must not hash alike.
      Map<String, String> hidden = new LinkedHashMap<>();
      PlanHashInputs.addHidden(hidden, path, "proposed",
         sha256Hex(content.getBytes(StandardCharsets.UTF_8)));
      PlanHashInputs.addHidden(hidden, path, "current",
         !exists ? PlanHashInputs.digest(WRITE_DOMAIN, null)
            : currentBytes == null ? UNREADABLE_MARKER : sha256Hex(currentBytes));
      return new ResolvedChange(StoredAssetChangeRequest.UNIT_FILE, StoredAssetChangeRequest.VERB_WRITE,
         path, null, content, compensable, priorText, planChange, hidden);
   }

   private ResolvedChange resolveRename(String label, String unitType, String path,
                                       StoredAssetChangeRequest raw)
   {
      if(raw.getContent() != null) {
         throw new IllegalArgumentException(label + ".content: not used for verb=rename");
      }

      String newName = StoredAssetPathValidator.requireSiblingName(raw.getNewName(), label + ".newName");
      StoredAssetPathValidator.requireValidNameChars(newName, label + ".newName");

      if(!dataSpace.exists(null, path)) {
         throw new StoredAssetNotFoundException(path, label);
      }

      boolean actualFolder = dataSpace.isDirectory(path);
      requireUnitTypeMatches(label, unitType, actualFolder, path);
      String newPath = join(parentOf(path), newName);

      if(dataSpace.exists(null, newPath)) {
         throw new IllegalArgumentException(
            label + ": \"" + newPath + "\" already exists -- refusing to rename over an " +
            "existing entry");
      }

      PlanChange planChange = new PlanChange(path, currentOrgId(), path, newPath,
         AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE, true,
         "rename \"" + path + "\" to \"" + newPath + "\"" +
         (actualFolder
            ? " (renaming a folder changes every descendant's own addressable path, not just " +
              "this one name)"
            : ""));
      return new ResolvedChange(unitType, StoredAssetChangeRequest.VERB_RENAME, path, newPath, null,
         true, null, planChange, Map.of());
   }

   private ResolvedChange resolveDelete(String label, String unitType, String path,
                                       StoredAssetChangeRequest raw)
   {
      if(raw.getContent() != null || raw.getNewName() != null) {
         throw new IllegalArgumentException(label + ": content/newName not used for verb=delete");
      }

      StoredAssetPathValidator.requireNotRoot(path, label + ".path");

      if(!dataSpace.exists(null, path)) {
         throw new StoredAssetNotFoundException(path, label);
      }

      boolean actualFolder = dataSpace.isDirectory(path);
      requireUnitTypeMatches(label, unitType, actualFolder, path);
      boolean compensable = false;
      String priorText = null;

      if(!actualFolder) {
         long size = dataSpace.getFileLength(null, path);

         if(size <= DELETE_COMPENSABLE_CAP_BYTES) {
            priorText = tryReadText(path);
            compensable = priorText != null;
         }
      }

      String description = (actualFolder ? "delete folder \"" : "delete file \"") + path + "\"" +
         (compensable ? "" :
            " (non-compensable: " + (actualFolder
               ? "there is no bounded way to capture an unbounded subtree's full content at " +
                 "preview time"
               : "prior content could not be captured as text, or exceeds the capture cap") + ")");
      PlanChange planChange = new PlanChange(path, currentOrgId(), "exists=true", "exists=false",
         AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE, true, description);
      return new ResolvedChange(unitType, StoredAssetChangeRequest.VERB_DELETE, path, null, null,
         compensable, priorText, planChange, Map.of());
   }

   // ---------------------------------------------------------------- validation helpers

   static String requireUnitType(String label, String unitType) {
      if(StoredAssetChangeRequest.UNIT_FILE.equalsIgnoreCase(unitType)) {
         return StoredAssetChangeRequest.UNIT_FILE;
      }

      if(StoredAssetChangeRequest.UNIT_FOLDER.equalsIgnoreCase(unitType)) {
         return StoredAssetChangeRequest.UNIT_FOLDER;
      }

      throw new IllegalArgumentException(
         label + ".unitType: must be \"file\" or \"folder\", got " + unitType);
   }

   static String requireVerb(String label, String unitType, String verb) {
      String trimmed = verb == null ? null : verb.trim().toLowerCase(Locale.ROOT);
      Set<String> valid = StoredAssetChangeRequest.UNIT_FOLDER.equals(unitType)
         ? Set.of(StoredAssetChangeRequest.VERB_CREATE, StoredAssetChangeRequest.VERB_RENAME,
                  StoredAssetChangeRequest.VERB_DELETE)
         : Set.of(StoredAssetChangeRequest.VERB_WRITE, StoredAssetChangeRequest.VERB_RENAME,
                  StoredAssetChangeRequest.VERB_DELETE);

      if(trimmed == null || !valid.contains(trimmed)) {
         throw new IllegalArgumentException(
            label + ".verb: must be one of " + valid + " for unitType=\"" + unitType + "\", got " +
            verb);
      }

      return trimmed;
   }

   private static void requireUnitTypeMatches(String label, String unitType, boolean actualFolder,
                                              String path)
   {
      if(StoredAssetChangeRequest.UNIT_FOLDER.equals(unitType) != actualFolder) {
         throw new IllegalArgumentException(
            label + ".unitType: \"" + unitType + "\" does not match the actual entry at \"" +
            path + "\" (" + (actualFolder ? "a folder" : "a file") + ")");
      }
   }

   private String tryReadText(String path) {
      return decodeUtf8(tryReadBytes(path));
   }

   /** Reads the whole file once; {@code null} when it cannot be read. */
   private byte[] tryReadBytes(String path) {
      try(InputStream in = dataSpace.getInputStream(null, path)) {
         return IOUtils.toByteArray(in);
      }
      catch(Exception e) {
         return null;
      }
   }

   /** Strict UTF-8 decode; {@code null} for unreadable or non-UTF-8 bytes. */
   private static String decodeUtf8(byte[] bytes) {
      if(bytes == null) {
         return null;
      }

      try {
         Charset.availableCharsets().get("UTF-8").newDecoder().decode(ByteBuffer.wrap(bytes));
         return new String(bytes, StandardCharsets.UTF_8);
      }
      catch(Exception e) {
         return null;
      }
   }

   private static String sha256Hex(byte[] bytes) {
      try {
         byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
         StringBuilder hex = new StringBuilder(digest.length * 2);

         for(byte b : digest) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      }
      catch(NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 is required to hash a stored asset change plan", e);
      }
   }

   private static String currentOrgId() {
      return OrganizationManager.getInstance().getCurrentOrgID();
   }

   static String parentOf(String path) {
      int idx = path.lastIndexOf('/');
      return idx < 0 ? "" : path.substring(0, idx);
   }

   static String leafOf(String path) {
      int idx = path.lastIndexOf('/');
      return idx < 0 ? path : path.substring(idx + 1);
   }

   static String join(String parent, String name) {
      return parent.isEmpty() ? name : parent + "/" + name;
   }

   // ---------------------------------------------------------------- hash

   /** SHA-256 over the canonical plan, same field-order/control-character-free contract as every
    * other area's own {@code hash} method. Package-visible so {@link
    * StoredAssetChangesetApplyService} can recompute the identical hash from a freshly re-resolved
    * entry list. Deliberately does NOT take {@code task} -- same rationale as every prior area's
    * own {@code hash} (a free-text paraphrase must never trip a false plan-hash conflict). */
   static String hash(List<PlanChange> changes, Map<String, String> hidden) {
      StringBuilder canonical = new StringBuilder();

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonicalValue(change.currentValue())).append(SEP)
            .append(canonicalValue(change.proposedValue())).append(SEP)
            .append(change.risk()).append(SEP)
            .append(change.snapshotScope()).append(SEP);
         PlanHashInputs.appendHidden(canonical, SEP, hidden, change.property());
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
         throw new IllegalStateException("SHA-256 is required to hash a stored asset change plan", e);
      }
   }

   /** Collects every resolved entry's hidden hash inputs, keyed by the change's path. */
   static Map<String, String> hiddenInputs(List<ResolvedChange> resolved) {
      Map<String, String> all = new LinkedHashMap<>();

      for(ResolvedChange entry : resolved) {
         all.putAll(entry.hidden());
      }

      return all;
   }

   private static String canonicalValue(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final String WRITE_DOMAIN = "storedAsset.write.";
   /** Fixed (not a digest of an error) so a read failure hashes the same on preview and apply. */
   private static final String UNREADABLE_MARKER = "unreadable";
   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   static final int WRITE_CONTENT_CAP_BYTES = 5_000_000;
   static final int DELETE_COMPENSABLE_CAP_BYTES = 2_000_000;
   private final DataSpace dataSpace;

   /** One resolved change: everything {@link StoredAssetChangesetApplyService} needs to actually
    * execute the verb, plus the {@link PlanChange} record describing it. {@code compensable} and
    * {@code priorText} together determine whether a live rollback exists for this entry (see
    * 01-design.md section 6.3's per-verb rollback table). */
   record ResolvedChange(String unitType, String verb, String path, String newPath, String content,
                        boolean compensable, String priorText, PlanChange planChange,
                        Map<String, String> hidden)
   {
   }
}
