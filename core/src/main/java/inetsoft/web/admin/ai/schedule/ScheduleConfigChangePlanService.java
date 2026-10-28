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
package inetsoft.web.admin.ai.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.internal.Util;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.FTPUtil;
import inetsoft.sree.schedule.TimeRange;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.PlanHashInputs;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.schedule.SchedulerConfigurationService;
import inetsoft.web.admin.schedule.model.ScheduleConfigurationModel;
import inetsoft.web.admin.schedule.model.ServerLocation;
import inetsoft.web.admin.schedule.model.ServerPathInfoModel;
import inetsoft.web.admin.security.ResourcePermissionModel;
import inetsoft.web.admin.security.ResourcePermissionTableModel;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeRangeModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Resolves a requested list of Server Location/Time Range changes into a {@link ResolvedPlan},
 * plus the before/after full {@link ScheduleConfigurationModel} the apply side needs to actually
 * write -- the schedule-config analog of {@code ScheduleChangePlanService}, replicated rather than
 * shared (same carry-forward rationale that class documents).
 *
 * <p>Server Locations and Time Ranges are resolved by ONE service, not two, because they are two
 * fields of the identical {@code ScheduleConfigurationModel} read and written by the identical
 * {@code SchedulerConfigurationService#getConfiguration}/{@code #setConfiguration} pair -- see
 * {@code 01-design.md}'s "Recommended lane split" section for why splitting this into two Java
 * classes would just duplicate the same read-full/write-full discipline twice.
 *
 * <p>Every change in this area is unconditionally {@code risk:high} / {@code
 * requiresAgentSignoff:true} and {@code snapshotScope:value} / {@code
 * requiresStorageBackup:false} (spec decision 3): both sub-resources are ordinary,
 * restart-durable {@code SreeEnv}-backed state with no non-compensable-delete shape, unlike a
 * data-source/identity/organization delete.
 */
@Component
public class ScheduleConfigChangePlanService {
   @Autowired
   public ScheduleConfigChangePlanService(AdminScheduleConfigGateway gateway) {
      this.gateway = gateway;
   }

   /** The plan plus the before/after full model {@link ScheduleConfigChangesetApplyService} needs
    * to actually write -- see this class's own javadoc for why a full model (not just the touched
    * list) must always be carried forward. */
   public static final class Resolution {
      Resolution(ResolvedPlan plan, ScheduleConfigurationModel before, ScheduleConfigurationModel after,
                List<EntryInfo> entries)
      {
         this.plan = plan;
         this.before = before;
         this.after = after;
         this.entries = entries;
      }

      public ResolvedPlan plan() { return plan; }
      public ScheduleConfigurationModel before() { return before; }
      public ScheduleConfigurationModel after() { return after; }

      /** Parallel to {@link #plan}{@code .changes()} -- lets the apply side verify each entry
       * post-write without re-deriving unitType/key from the free-text {@code PlanChange}. */
      public List<EntryInfo> entries() { return entries; }

      private final ResolvedPlan plan;
      private final ScheduleConfigurationModel before;
      private final ScheduleConfigurationModel after;
      private final List<EntryInfo> entries;
   }

   /**
    * @param unitType one of {@link ScheduleConfigChangeRequest#UNIT_SERVER_LOCATION}/{@link
    *                 ScheduleConfigChangeRequest#UNIT_TIME_RANGE}.
    * @param verb     one of {@code create}/{@code update}/{@code delete}.
    * @param afterKey the entry's key (path/name) AFTER this change -- {@code null} for a delete;
    *                 differs from the {@code PlanChange}'s own {@code property()} (the BEFORE key)
    *                 only for an identity-changing Time Range update.
    * @param advisory the affected-task advisory for a force-allowed Time Range change, else {@code
    *                 null}; the same text appended to the {@code PlanChange}'s description.
    */
   public record EntryInfo(String unitType, String verb, String afterKey, String advisory) {
   }

   /** Convenience for callers (preview) that only need the plan itself. */
   public ResolvedPlan resolve(ScheduleConfigChangePlanRequest req, Principal user) throws Exception {
      return resolveFull(req, user).plan();
   }

   /**
    * Resolves and hashes a plan, reading the full model FRESH every call (spec: "the drift check
    * at apply time is: re-read getConfiguration fresh"). Performs no mutation.
    *
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *                                 list, an unrecognized unitType/verb, a duplicate/missing key,
    *                                 a Server Location label/path conflict, a duplicate Time Range
    *                                 name or more than one default, or an unforced delete/rename of
    *                                 a Time Range a live task still references.
    */
   public Resolution resolveFull(ScheduleConfigChangePlanRequest req, Principal user) throws Exception {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      ScheduleConfigurationModel before = gateway.getFullConfig(user);
      List<ServerLocation> locations = new ArrayList<>(before.serverLocations());
      List<TimeRangeModel> ranges = new ArrayList<>(before.timeRanges());

      List<PlanChange> changes = new ArrayList<>();
      List<EntryInfo> entries = new ArrayList<>();
      // Deferred delete-dependency scans: the FINAL `ranges` list is only known once every change
      // has been folded in, so these are collected here and resolved in a second pass below.
      List<String> pendingRangeNames = new ArrayList<>();
      List<Boolean> pendingForce = new ArrayList<>();
      List<Integer> pendingChangeIndex = new ArrayList<>();

      Set<String> seenLocationKeys = new HashSet<>();
      // Bug #79016: hidden hash inputs for a server location's password/secretId, keyed by the
      // change's index in changes (see hash()).
      Map<String, String> hiddenInputs = new LinkedHashMap<>();
      Set<String> seenRangeKeys = new HashSet<>();
      int index = 0;

      for(ScheduleConfigChangeRequest change : req.getChanges()) {
         String label = "changes[" + index + "]";

         if(change == null) {
            throw new IllegalArgumentException(label + ": must not be null");
         }

         String unitType = change.getUnitType();
         String verb = change.getVerb();

         if(ScheduleConfigChangeRequest.UNIT_SERVER_LOCATION.equals(unitType)) {
            changes.add(resolveServerLocationChange(
               label, change, locations, before.serverLocations(), seenLocationKeys,
               hiddenInputs, changes.size()));
         }
         else if(ScheduleConfigChangeRequest.UNIT_TIME_RANGE.equals(unitType)) {
            TimeRangeResolution r = resolveTimeRangeChange(
               label, change, ranges, seenRangeKeys, hiddenInputs, changes.size());
            changes.add(r.planChange);

            if(r.dependencyCheckRangeName != null) {
               pendingRangeNames.add(r.dependencyCheckRangeName);
               pendingForce.add(change.isForce());
               pendingChangeIndex.add(changes.size() - 1);
            }
         }
         else {
            throw new IllegalArgumentException(
               label + ".unitType: must be \"" + ScheduleConfigChangeRequest.UNIT_SERVER_LOCATION +
               "\" or \"" + ScheduleConfigChangeRequest.UNIT_TIME_RANGE + "\"; got " +
               String.valueOf(unitType));
         }

         entries.add(new EntryInfo(unitType, verb, deriveAfterKey(label, unitType, verb, change), null));
         index++;
      }

      if(!pendingRangeNames.isEmpty()) {
         List<TimeRange> finalInternalRanges = toInternalRanges(ranges);

         for(int i = 0; i < pendingRangeNames.size(); i++) {
            String rangeName = pendingRangeNames.get(i);
            boolean force = pendingForce.get(i);
            int changeIndex = pendingChangeIndex.get(i);
            List<TimeRangeDependency> dependents =
               gateway.findTimeRangeDependents(rangeName, finalInternalRanges);

            if(dependents.isEmpty()) {
               continue;
            }

            String advisory = describeDependents(rangeName, dependents);

            if(!force) {
               throw new IllegalArgumentException(
                  "changes[" + changeIndex + "]: refusing -- " + advisory +
                  " Pass force:true on this change to allow it anyway.");
            }

            changes.set(changeIndex, withAdvisory(changes.get(changeIndex), advisory));
            EntryInfo entry = entries.get(changeIndex);
            entries.set(changeIndex, new EntryInfo(entry.unitType(), entry.verb(), entry.afterKey(),
                                                   advisory));
         }
      }

      String planHash = hash(changes, hiddenInputs);
      String task = req.getTask().trim();
      ResolvedPlan plan = new ResolvedPlan(task, Collections.unmodifiableList(changes), false, true,
                                          planHash, TaskAuditToken.issue(planHash, task));
      ScheduleConfigurationModel after = ScheduleConfigurationModel.builder().from(before)
         .serverLocations(locations)
         .timeRanges(ranges)
         .build();
      return new Resolution(plan, before, after, Collections.unmodifiableList(entries));
   }

   /** The entry's key AFTER this change -- {@code null} for a delete. A server location's key is
    * the converted (credential-split) path, i.e. the path the write stores and the re-read
    * returns; a time range's is read off the raw {@code spec} map. Both are already validated
    * non-blank by {@code convertServerLocation}/{@code convertTimeRange} for every non-delete
    * verb by the time this is called. */
   private static String deriveAfterKey(
      String label, String unitType, String verb, ScheduleConfigChangeRequest change)
   {
      if(ScheduleConfigChangeRequest.VERB_DELETE.equals(verb)) {
         return null;
      }

      if(ScheduleConfigChangeRequest.UNIT_SERVER_LOCATION.equals(unitType)) {
         return convertServerLocation(label, change.getSpec()).path();
      }

      Map<String, Object> spec = change.getSpec();
      Object v = spec == null ? null : spec.get("name");
      return v == null ? null : String.valueOf(v);
   }

   private PlanChange resolveServerLocationChange(
      String label, ScheduleConfigChangeRequest change, List<ServerLocation> locations,
      List<ServerLocation> original, Set<String> seen,
      Map<String, String> hiddenInputs, int changeIndex)
   {
      String verb = requireVerb(label, change.getVerb());

      if(ScheduleConfigChangeRequest.VERB_CREATE.equals(verb)) {
         ServerLocation spec = convertServerLocation(label, change.getSpec());
         requireUnseen(label, spec.path(), seen);

         if(findServerLocationIndex(locations, spec.path()) >= 0) {
            throw new IllegalArgumentException(
               label + ": a server location already exists with path \"" + spec.path() +
               "\" -- use verb=update");
         }

         requireNoServerLocationConflict(label, spec, locations, -1);
         locations.add(spec);
         WrittenForm written = projectWrittenForm(label, spec, original);
         addCredentialHashInputs(hiddenInputs, changeIndex, spec);
         return new PlanChange(spec.path(), null, null, projectServerLocation(written.location),
                               AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_VALUE, true,
                               "create server location" + written.noteSuffix());
      }

      // a key that carries a password names the stored (split) path, and is never echoed with it
      String key = splitKeyPassword(requireKey(label, change.getKey()));
      requireUnseen(label, key, seen);
      int existingIndex = findServerLocationIndex(locations, key);

      if(existingIndex < 0) {
         throw new IllegalArgumentException(
            label + ".key: no server location exists with path \"" + key + "\"");
      }

      ServerLocation existing = locations.get(existingIndex);

      if(ScheduleConfigChangeRequest.VERB_DELETE.equals(verb)) {
         locations.remove(existingIndex);
         return new PlanChange(key, null, projectServerLocation(existing), null,
                               AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_VALUE, true,
                               "delete server location");
      }

      if(ScheduleConfigChangeRequest.VERB_UPDATE.equals(verb)) {
         ServerLocation spec = convertServerLocation(label, change.getSpec());
         requireNoServerLocationConflict(label, spec, locations, existingIndex);
         locations.set(existingIndex, spec);
         WrittenForm written = projectWrittenForm(label, spec, original);
         addCredentialHashInputs(hiddenInputs, changeIndex, spec);
         return new PlanChange(key, null, projectServerLocation(existing),
                               projectServerLocation(written.location),
                               AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_VALUE, true,
                               "update server location" + written.noteSuffix());
      }

      throw unknownVerb(label, verb);
   }

   /** What the write will actually persist for a location, plus an explicit warning when that
    * differs from what the caller sent (the stored password gets cleared). */
   private static final class WrittenForm {
      WrittenForm(ServerLocation location, String note) {
         this.location = location;
         this.note = note;
      }

      String noteSuffix() {
         return note == null ? "" : " -- WARNING: " + note;
      }

      final ServerLocation location;
      final String note;
   }

   /**
    * Mirrors the placeholder-password rule of
    * {@code SchedulerConfigurationService#setServerLocations} (Bug #77953) so the preview never
    * claims {@code password=(set)} for a state the server will clear (Bug #79032). A placeholder
    * password keeps the stored one only if {@code oldPasswordKey} names a CURRENTLY stored
    * location AND that location logs in to the same server as the same user
    * ({@code SchedulerConfigurationService#isSameLocationLogin}). A changed username or a path on
    * a different server is the intended server rule: the preview projects no password and warns.
    * A missing or stale {@code oldPasswordKey} is caller error and is refused, so a stale key can
    * never silently destroy a stored credential.
    */
   private static WrittenForm projectWrittenForm(
      String label, ServerLocation spec, List<ServerLocation> original)
   {
      ServerPathInfoModel info = spec.pathInfoModel();

      if(info == null || !info.ftp() || info.useCredential() ||
         !Util.PLACEHOLDER_PASSWORD.equals(info.password()))
      {
         return new WrittenForm(spec, null);
      }

      ServerPathInfoModel old = null;

      if(info.oldPasswordKey() != null) {
         for(ServerLocation l : original) {
            ServerPathInfoModel oi = l.pathInfoModel();

            if(oi != null && info.oldPasswordKey().equals(oi.oldPasswordKey())) {
               old = oi;
               break;
            }
         }
      }

      if(old == null) {
         throw new IllegalArgumentException(
            label + ".spec.pathInfoModel.oldPasswordKey: the masked password cannot be resolved " +
            "because oldPasswordKey " +
            (info.oldPasswordKey() == null ? "is missing" : "matches no stored server location") +
            "; re-read the server locations for a fresh oldPasswordKey, or supply the real " +
            "password");
      }

      if(!SchedulerConfigurationService.isSameLocationLogin(spec.path(), info.username(), old)) {
         return new WrittenForm(withoutPassword(spec),
            "the login changed (different username or server), so the stored password will be " +
            "CLEARED, not carried over to the new login -- supply the real password to set one");
      }

      if(old.password() == null) {
         return new WrittenForm(withoutPassword(spec), null);
      }

      return new WrittenForm(spec, null);
   }

   private static ServerLocation withoutPassword(ServerLocation spec) {
      ServerPathInfoModel info =
         ServerPathInfoModel.builder().from(spec.pathInfoModel()).password(null).build();
      return ServerLocation.builder()
         .path(spec.path())
         .label(spec.label())
         .pathInfoModel(info)
         .build();
   }

   private static final class TimeRangeResolution {
      TimeRangeResolution(PlanChange planChange, String dependencyCheckRangeName) {
         this.planChange = planChange;
         this.dependencyCheckRangeName = dependencyCheckRangeName;
      }

      final PlanChange planChange;
      /** Non-null only for a delete, or an identity-changing update (name/startTime/endTime
       * differs from what is currently stored) -- see {@code 01-design.md}'s revision section. */
      final String dependencyCheckRangeName;
   }

   private TimeRangeResolution resolveTimeRangeChange(
      String label, ScheduleConfigChangeRequest change, List<TimeRangeModel> ranges, Set<String> seen,
      Map<String, String> hiddenInputs, int changeIndex)
   {
      String verb = requireVerb(label, change.getVerb());

      if(ScheduleConfigChangeRequest.VERB_CREATE.equals(verb)) {
         TimeRangeModel spec = convertTimeRange(label, change.getSpec());
         requireUnseen(label, spec.name(), seen);

         if(findTimeRangeIndex(ranges, spec.name()) >= 0) {
            throw new IllegalArgumentException(
               label + ": a time range already exists named \"" + spec.name() + "\" -- use verb=update");
         }

         if(spec.defaultRange()) {
            clearOtherDefaults(ranges, -1);
         }

         ranges.add(spec);
         requireNoDuplicateNamesOrDefaults(label, ranges);
         addPermissionHashInputs(hiddenInputs, changeIndex, spec);
         return new TimeRangeResolution(
            new PlanChange(spec.name(), null, null, projectTimeRange(spec), AdminChangeRecord.RISK_HIGH,
                           AdminChangeRecord.SCOPE_VALUE, true,
                           "create time range" + permissionNote(spec)),
            null);
      }

      String key = requireKey(label, change.getKey());
      requireUnseen(label, key, seen);
      int existingIndex = findTimeRangeIndex(ranges, key);

      if(existingIndex < 0) {
         throw new IllegalArgumentException(label + ".key: no time range exists named \"" + key + "\"");
      }

      TimeRangeModel existing = ranges.get(existingIndex);

      if(ScheduleConfigChangeRequest.VERB_DELETE.equals(verb)) {
         ranges.remove(existingIndex);
         return new TimeRangeResolution(
            new PlanChange(key, null, projectTimeRange(existing), null, AdminChangeRecord.RISK_HIGH,
                           AdminChangeRecord.SCOPE_VALUE, true, "delete time range"),
            existing.name());
      }

      if(ScheduleConfigChangeRequest.VERB_UPDATE.equals(verb)) {
         TimeRangeModel spec = convertTimeRange(label, change.getSpec());

         if(spec.defaultRange()) {
            clearOtherDefaults(ranges, existingIndex);
         }

         // hash the spec that is actually stored: a rename may carry permissions into it
         TimeRangeModel stored = carryPermissionsOnRename(existing, spec);
         ranges.set(existingIndex, stored);
         requireNoDuplicateNamesOrDefaults(label, ranges);
         addPermissionHashInputs(hiddenInputs, changeIndex, stored);

         boolean identityChanged = !existing.name().equals(spec.name()) ||
            !LocalTime.parse(existing.startTime()).equals(LocalTime.parse(spec.startTime())) ||
            !LocalTime.parse(existing.endTime()).equals(LocalTime.parse(spec.endTime()));

         return new TimeRangeResolution(
            new PlanChange(key, null, projectTimeRange(existing), projectTimeRange(spec),
                           AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_VALUE, true,
                           "update time range" + permissionNote(stored)),
            identityChanged ? existing.name() : null);
      }

      throw unknownVerb(label, verb);
   }

   /**
    * Bug #79545: time range permissions are stored under the range's name, and
    * {@code SchedulerConfigurationService} only writes them when {@code modified} is non-null. A
    * rename that resupplies a read-back record ({@code modified == null}) would therefore write
    * the new name with no grant. When the name changes and the caller did not say whether the
    * permissions were modified, mark them modified and carry the caller's permissions, falling
    * back to the stored ones. Never produces a non-null model with null permissions (that would
    * clear the new name's grants).
    */
   static TimeRangeModel carryPermissionsOnRename(TimeRangeModel existing, TimeRangeModel spec) {
      if(existing.name().equals(spec.name()) || spec.modified() != null) {
         return spec;
      }

      ResourcePermissionModel permissions =
         spec.permissions() != null ? spec.permissions() : existing.permissions();

      if(permissions == null) {
         return spec;
      }

      return TimeRangeModel.builder().from(spec).modified(true).permissions(permissions).build();
   }

   private static void clearOtherDefaults(List<TimeRangeModel> ranges, int exceptIndex) {
      for(int i = 0; i < ranges.size(); i++) {
         if(i == exceptIndex) {
            continue;
         }

         TimeRangeModel r = ranges.get(i);

         if(r.defaultRange()) {
            ranges.set(i, TimeRangeModel.builder().from(r).defaultRange(false).build());
         }
      }
   }

   private static void requireNoDuplicateNamesOrDefaults(String label, List<TimeRangeModel> ranges) {
      Set<String> names = new HashSet<>();
      int defaults = 0;

      for(TimeRangeModel r : ranges) {
         if(!names.add(r.name())) {
            throw new IllegalArgumentException(
               label + ": duplicate time range name \"" + r.name() + "\" -- names must be unique");
         }

         if(r.defaultRange()) {
            defaults++;
         }
      }

      if(defaults > 1) {
         throw new IllegalArgumentException(
            label + ": more than one time range is marked as the default -- at most one may be");
      }
   }

   private static void requireNoServerLocationConflict(
      String label, ServerLocation spec, List<ServerLocation> locations, int excludeIndex)
   {
      String normalizedPath = normalizePath(spec.path());

      for(int i = 0; i < locations.size(); i++) {
         if(i == excludeIndex) {
            continue;
         }

         ServerLocation other = locations.get(i);

         if(spec.label().equals(other.label())) {
            throw new IllegalArgumentException(
               label + ".spec.label: \"" + spec.label() + "\" is already used by another server " +
               "location (path \"" + other.path() + "\") -- labels must be unique");
         }

         String otherPath = normalizePath(other.path());

         if(normalizedPath.equals(otherPath) || normalizedPath.startsWith(otherPath + "/") ||
            otherPath.startsWith(normalizedPath + "/"))
         {
            throw new IllegalArgumentException(
               label + ".spec.path: \"" + spec.path() + "\" overlaps with an existing server " +
               "location's path \"" + other.path() + "\" (one contains the other)");
         }
      }
   }

   static String normalizePath(String path) {
      return path == null ? "" : path.replaceAll("[/\\\\]+$", "");
   }

   private static int findServerLocationIndex(List<ServerLocation> locations, String path) {
      String normalized = normalizePath(path);

      for(int i = 0; i < locations.size(); i++) {
         if(normalizePath(locations.get(i).path()).equals(normalized)) {
            return i;
         }
      }

      return -1;
   }

   private static int findTimeRangeIndex(List<TimeRangeModel> ranges, String name) {
      for(int i = 0; i < ranges.size(); i++) {
         if(ranges.get(i).name().equals(name)) {
            return i;
         }
      }

      return -1;
   }

   private static ServerLocation convertServerLocation(String label, Map<String, Object> spec) {
      if(spec == null) {
         throw new IllegalArgumentException(label + ".spec: required");
      }

      ServerLocation location;

      try {
         location = MAPPER.convertValue(spec, ServerLocation.class);
      }
      catch(Exception e) {
         throw new IllegalArgumentException(label + ".spec: " + messageOf(e));
      }

      if(location.path() == null || location.path().isBlank()) {
         throw new IllegalArgumentException(label + ".spec.path: required non-blank string");
      }

      if(location.label() == null || location.label().isBlank()) {
         throw new IllegalArgumentException(label + ".spec.label: required non-blank string");
      }

      // refused rather than canonicalized away: both shapes below describe a credential the write
      // side cannot persist, so normalizing them would silently store a location with NO
      // credential -- the quiet data loss this whole area's verify-after-write step exists to
      // catch. Only the derived ftp flag is safe to normalize (see canonicalizeServerLocation).
      ServerPathInfoModel info = location.pathInfoModel();

      if(info != null && info.useCredential() && Tool.isEmptyString(info.secretId())) {
         throw new IllegalArgumentException(
            label + ".spec.secretId: required non-blank string when useCredential=true -- the " +
            "credential is resolved by reference, so a blank secretId stores no credential at all");
      }

      if(info != null && !info.useCredential() && Tool.isEmptyString(info.username()) &&
         !Tool.isEmptyString(info.password()))
      {
         throw new IllegalArgumentException(
            label + ".spec.username: required non-blank string when a password is supplied -- a " +
            "password is only ever persisted alongside a username");
      }

      // split AFTER canonicalizing, on the canonical model, with the exact arguments the write
      // uses, so the plan names and shows what the write stores (Bug #79546)
      return SchedulerConfigurationService.splitPathPassword(canonicalizeServerLocation(location));
   }

   /**
    * Rewrites a caller-supplied location into EXACTLY the shape {@code
    * SchedulerConfigurationService#setServerLocations} persists and {@code
    * SUtil#getServerLocations} hands back on the verification re-read.
    *
    * <p>Without this, {@code ScheduleConfigChangesetApplyService}'s write-then-readback check
    * compares the caller's shape against the server's canonical shape and rolls back a write that
    * actually succeeded. The asymmetries closed here are all ones the read side introduces and the
    * write side never round-trips: a null {@code pathInfoModel} (the read always builds one), a
    * trailing slash on the path (stripped on read), and {@code ftp}/{@code useCredential}, which
    * are never persisted as such but DERIVED on read from whether a username/secretId is present.
    * Deriving them the same way here also makes the write itself correct -- {@code
    * setServerLocations} only persists a credential when {@code ftp()} is true, so a caller who
    * supplies a username without {@code ftp:true} would otherwise have it silently dropped.
    *
    * <p>Only shapes that lose NOTHING are normalized here. The two that would cost a credential
    * (a blank {@code secretId} under {@code useCredential=true}, and a password with no username)
    * are refused by {@link #convertServerLocation} before this runs, so reaching the no-credential
    * branch below always means the caller genuinely supplied no credential.
    */
   private static ServerLocation canonicalizeServerLocation(ServerLocation location) {
      String path = normalizePath(location.path());
      ServerPathInfoModel info = location.pathInfoModel();
      ServerPathInfoModel.Builder builder = ServerPathInfoModel.builder().path(path);
      String secretId = info == null || !info.useCredential() ||
         Tool.isEmptyString(info.secretId()) ? null : info.secretId();
      String username = info == null || info.useCredential() ||
         Tool.isEmptyString(info.username()) ? null : info.username();

      if(secretId != null) {
         builder.useCredential(true).secretId(secretId).ftp(true);
      }
      else if(username != null) {
         // the password is only ever persisted alongside a username
         String password = Tool.isEmptyString(info.password()) ? null : info.password();
         builder.useCredential(false)
            .username(username)
            .password(password)
            .oldPasswordKey(info.oldPasswordKey())
            .ftp(true);
      }
      else {
         builder.useCredential(false).ftp(false);
      }

      return ServerLocation.builder()
         .path(path)
         .label(location.label())
         .pathInfoModel(builder.build())
         .build();
   }

   private static TimeRangeModel convertTimeRange(String label, Map<String, Object> spec) {
      if(spec == null) {
         throw new IllegalArgumentException(label + ".spec: required");
      }

      TimeRangeModel range;

      try {
         range = MAPPER.convertValue(spec, TimeRangeModel.class);
      }
      catch(Exception e) {
         throw new IllegalArgumentException(label + ".spec: " + messageOf(e));
      }

      if(range.name() == null || range.name().isBlank()) {
         throw new IllegalArgumentException(label + ".spec.name: required non-blank string");
      }

      return TimeRangeModel.builder()
         .from(range)
         .startTime(requireLocalTime(label + ".spec.startTime", range.startTime()))
         .endTime(requireLocalTime(label + ".spec.endTime", range.endTime()))
         .build();
   }

   /**
    * Validates a time and returns it in the ONE representation a re-read will produce: {@code
    * TimeRangeModel.Builder#from} truncates to the minute and formats with {@code ISO_LOCAL_TIME},
    * which PRINTS the seconds field even when it is zero (unlike {@code LocalTime#toString}). Both
    * documented input formats are accepted, but {@code "09:00"} is canonicalized to {@code
    * "09:00:00"} -- otherwise the apply side's projection comparison could never match and a
    * successful write would be rolled back.
    *
    * <p>Sub-minute precision is refused rather than silently truncated: a time range is stored to
    * the minute, so accepting {@code "09:30:15"} would quietly persist something other than what
    * was asked for.
    */
   private static String requireLocalTime(String label, String value) {
      if(value == null) {
         throw new IllegalArgumentException(label + ": required (HH:mm or HH:mm:ss)");
      }

      LocalTime time;

      try {
         time = LocalTime.parse(value);
      }
      catch(DateTimeParseException e) {
         throw new IllegalArgumentException(label + ": \"" + value + "\" is not a valid HH:mm[:ss] time");
      }

      if(time.getSecond() != 0 || time.getNano() != 0) {
         throw new IllegalArgumentException(
            label + ": \"" + value + "\" has sub-minute precision, but a time range is stored to " +
            "the minute -- use \"HH:mm\" (or \"HH:mm:00\")");
      }

      return time.format(DateTimeFormatter.ISO_LOCAL_TIME);
   }

   private static String requireVerb(String label, String verb) {
      if(ScheduleConfigChangeRequest.VERB_CREATE.equals(verb) ||
         ScheduleConfigChangeRequest.VERB_UPDATE.equals(verb) ||
         ScheduleConfigChangeRequest.VERB_DELETE.equals(verb))
      {
         return verb;
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"create\", \"update\", or \"delete\"; got " + String.valueOf(verb));
   }

   private static IllegalArgumentException unknownVerb(String label, String verb) {
      return new IllegalArgumentException(label + ".verb: unrecognized verb \"" + verb + "\"");
   }

   /** The key without a password embedded in it; gated like the write, so only an ftp path is
    * split. */
   private static String splitKeyPassword(String key) {
      FTPUtil.PathPassword split = SUtil.splitServerLocationPassword(key, false, null, null);
      return split == null ? key : split.path();
   }

   private static String requireKey(String label, String key) {
      if(key == null || key.isBlank()) {
         throw new IllegalArgumentException(label + ".key: required for verb=update/delete");
      }

      return key;
   }

   private static void requireUnseen(String label, String key, Set<String> seen) {
      if(!seen.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for \"" + key + "\" -- list each entry at most once");
      }
   }

   private static List<TimeRange> toInternalRanges(List<TimeRangeModel> ranges) {
      List<TimeRange> result = new ArrayList<>();

      for(TimeRangeModel r : ranges) {
         result.add(new TimeRange(r.name(), r.startTime(), r.endTime(), r.defaultRange()));
      }

      return result;
   }

   private static String describeDependents(String rangeName, List<TimeRangeDependency> dependents) {
      StringBuilder sb = new StringBuilder();
      sb.append(dependents.size()).append(dependents.size() == 1 ? " task" : " tasks")
         .append(" currently reference time range \"").append(rangeName)
         .append("\" and would be SILENTLY reassigned: ");

      for(int i = 0; i < dependents.size(); i++) {
         if(i > 0) {
            sb.append("; ");
         }

         TimeRangeDependency d = dependents.get(i);
         sb.append("task \"").append(d.taskId()).append("\" -> ").append(
            d.reassignedToRangeName() == null
               ? "no remaining time range (would be left with a dangling reference)"
               : ("\"" + d.reassignedToRangeName() + "\""));
      }

      return sb.toString();
   }

   private static PlanChange withAdvisory(PlanChange change, String advisory) {
      return new PlanChange(change.property(), change.orgId(), change.currentValue(),
                            change.proposedValue(), change.risk(), change.snapshotScope(),
                            change.recognized(), change.description() + " -- ALLOWED WITH force=true: " + advisory);
   }

   /**
    * Bug #79016: the shared projection shows only {@code (set)}/{@code (none)} for the password and
    * the secretId, so a plan that supplied a different password or credential reference hashed
    * like any other. The digests of the EFFECTIVE (post-{@link #canonicalizeServerLocation})
    * values go into the plan hash only: apply's write-then-readback check compares the projection
    * against a masked read-back, so the projection itself must stay free of them. A location
    * with no password and no secretId contributes nothing.
    */
   private static void addCredentialHashInputs(Map<String, String> hiddenInputs, int changeIndex,
                                               ServerLocation location)
   {
      ServerPathInfoModel info = location.pathInfoModel();

      if(info == null) {
         return;
      }

      String key = String.valueOf(changeIndex);

      if(info.password() != null) {
         PlanHashInputs.addHidden(hiddenInputs, key, "pw",
                                  PlanHashInputs.digest(PASSWORD_DIGEST_DOMAIN, info.password()));
      }

      if(info.secretId() != null) {
         PlanHashInputs.addHidden(hiddenInputs, key, "secretId",
                                  PlanHashInputs.digest(SECRET_ID_DIGEST_DOMAIN, info.secretId()));
      }
   }

   /** Never leaks the literal password/oldPasswordKey into the displayed projection -- only whether
    * a credential is present, mirroring the masking convention {@code
    * ScheduleTaskChangePlanService}'s XML projection uses for the same concern. The secrets reach
    * the plan hash separately, see {@link #addCredentialHashInputs}. */
   static String projectServerLocation(ServerLocation location) {
      // the credential tail is emitted unconditionally, and the path is normalized the same way
      // the read side normalizes it: a re-read ALWAYS yields a non-null pathInfoModel and a
      // slash-stripped path, so making either conditional here compares two different shapes and
      // fails verification on a write that succeeded.
      ServerPathInfoModel info = location.pathInfoModel();
      // Bug #79016: path, label and username are caller text, so they are separator-escaped; a
      // value holding ";username=x" can no longer pass for the next field.
      return "path=" + PlanHashInputs.escape(normalizePath(location.path())) +
         ";label=" + PlanHashInputs.escape(location.label()) +
         ";ftp=" + (info != null && info.ftp()) +
         ";useCredential=" + (info != null && info.useCredential()) +
         ";username=" + (info == null || info.username() == null ? null :
                              PlanHashInputs.escape(info.username())) +
         ";secretId=" + (info == null || info.secretId() == null ? "(none)" : "(set)") +
         ";password=" + (info == null || info.password() == null ? "(none)" : "(set)");
   }

   /**
    * Bug #79544: the apply side writes a time range's permission grant whenever {@code modified}
    * is non-null (see {@code SchedulerConfigurationService#setTimeRanges}), but the projection
    * carries neither field and apply's read-back check compares only the projection. So a
    * grant added or changed between preview and apply rode through the reviewed planHash. A digest
    * of {@code modified} and the canonical, order-independent permissions goes into the plan hash
    * only (the projection stays as the read-back shape); preview and apply must then carry the
    * same grant. {@code permissions} is ignored when {@code modified} is null, because nothing is
    * written then (a read record echoed back carries permissions but no {@code modified}).
    */
   private static void addPermissionHashInputs(Map<String, String> hiddenInputs, int changeIndex,
                                               TimeRangeModel range)
   {
      if(range.modified() == null) {
         return;
      }

      PlanHashInputs.addHidden(
         hiddenInputs, String.valueOf(changeIndex), "perm",
         PlanHashInputs.digest(PERMISSION_DIGEST_DOMAIN, canonicalPermissions(range)));
   }

   /** One-line reviewer-visible note; not part of the hashed or verified projection. */
   private static String permissionNote(TimeRangeModel range) {
      if(range.modified() == null) {
         return "";
      }

      ResourcePermissionModel perm = range.permissions();
      int entries = perm == null || perm.permissions() == null ? 0 : perm.permissions().size();
      return " -- ALSO WRITES this time range's permissions (modified=" + range.modified() + ", " +
         entries + " grant entries; bound into the plan hash, not shown in the projection)";
   }

   static String canonicalPermissions(TimeRangeModel range) {
      StringBuilder out = new StringBuilder("modified=").append(range.modified());
      ResourcePermissionModel perm = range.permissions();

      if(perm == null) {
         return out.append(";permissions=null").toString();
      }

      out.append(";securityEnabled=").append(perm.securityEnabled())
         .append(";requiresBoth=").append(perm.requiresBoth())
         .append(";grantReadToAll=").append(perm.grantReadToAll())
         .append(";hasOrgEdited=").append(perm.hasOrgEdited())
         .append(";changed=").append(perm.changed());

      if(perm.permissions() == null) {
         return out.append(";entries=null").toString();
      }

      List<String> entries = new ArrayList<>();

      for(ResourcePermissionTableModel entry : perm.permissions()) {
         List<String> actions = new ArrayList<>();
         entry.actions().forEach(a -> actions.add(a.name()));
         Collections.sort(actions);
         IdentityID id = entry.identityID();
         entries.add("id=" + PlanHashInputs.escape(id == null ? null : id.getName()) + "|" +
                     PlanHashInputs.escape(id == null ? null : id.getOrgID()) +
                     ";type=" + entry.type() + ";actions=" + String.join(",", actions));
      }

      Collections.sort(entries);
      return out.append(";entries=[").append(String.join("],[", entries)).append("]").toString();
   }

   static String projectTimeRange(TimeRangeModel range) {
      return "name=" + PlanHashInputs.escape(range.name()) + ";startTime=" + range.startTime() + ";endTime=" +
         range.endTime() + ";defaultRange=" + range.defaultRange();
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   /**
    * SHA-256 over the canonical plan -- same field-order/control-character contract as {@code
    * AdminChangePlanService#hash}/{@code ScheduleChangePlanService#hash}. Deliberately excludes
    * {@code task}, same rationale as both of those.
    */
   private static String hash(List<PlanChange> changes, Map<String, String> hiddenInputs) {
      StringBuilder canonical = new StringBuilder();
      int index = 0;

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonical(change.currentValue())).append(SEP)
            .append(canonical(change.proposedValue())).append(SEP)
            .append(change.risk()).append(SEP)
            .append(change.snapshotScope()).append(SEP);
         // Bug #79016: appended only for a server location that carries a password/secretId.
         PlanHashInputs.appendHidden(canonical, SEP, hiddenInputs, String.valueOf(index++));
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
         throw new IllegalStateException("SHA-256 is required to hash a schedule config change plan", e);
      }
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String PASSWORD_DIGEST_DOMAIN =
      "inetsoft.admin.ai.schedule.serverLocation.password\u0000";
   private static final String PERMISSION_DIGEST_DOMAIN =
      "inetsoft.admin.ai.schedule.timeRange.permissions ";
   private static final String SECRET_ID_DIGEST_DOMAIN =
      "inetsoft.admin.ai.schedule.serverLocation.secretId\u0000";
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private static final ObjectMapper MAPPER = new ObjectMapper();
   private final AdminScheduleConfigGateway gateway;
}
