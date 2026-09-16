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
import inetsoft.sree.schedule.TimeRange;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.schedule.model.ScheduleConfigurationModel;
import inetsoft.web.admin.schedule.model.ServerLocation;
import inetsoft.web.admin.schedule.model.ServerPathInfoModel;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeRangeModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.time.LocalTime;
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
    */
   public record EntryInfo(String unitType, String verb, String afterKey) {
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
            changes.add(resolveServerLocationChange(label, change, locations, seenLocationKeys));
         }
         else if(ScheduleConfigChangeRequest.UNIT_TIME_RANGE.equals(unitType)) {
            TimeRangeResolution r = resolveTimeRangeChange(label, change, ranges, seenRangeKeys);
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

         entries.add(new EntryInfo(unitType, verb, deriveAfterKey(unitType, verb, change)));
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
         }
      }

      String planHash = hash(changes);
      String task = req.getTask().trim();
      ResolvedPlan plan = new ResolvedPlan(task, Collections.unmodifiableList(changes), false, true,
                                          planHash, TaskAuditToken.issue(planHash, task));
      ScheduleConfigurationModel after = ScheduleConfigurationModel.builder().from(before)
         .serverLocations(locations)
         .timeRanges(ranges)
         .build();
      return new Resolution(plan, before, after, Collections.unmodifiableList(entries));
   }

   /** The entry's key AFTER this change -- {@code null} for a delete. Read directly off the raw
    * {@code spec} map (already validated non-blank by {@code convertServerLocation}/{@code
    * convertTimeRange} for every non-delete verb by the time this is called). */
   private static String deriveAfterKey(String unitType, String verb, ScheduleConfigChangeRequest change) {
      if(ScheduleConfigChangeRequest.VERB_DELETE.equals(verb)) {
         return null;
      }

      Map<String, Object> spec = change.getSpec();
      String field = ScheduleConfigChangeRequest.UNIT_SERVER_LOCATION.equals(unitType) ? "path" : "name";
      Object v = spec == null ? null : spec.get(field);
      return v == null ? null : String.valueOf(v);
   }

   private PlanChange resolveServerLocationChange(
      String label, ScheduleConfigChangeRequest change, List<ServerLocation> locations, Set<String> seen)
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
         return new PlanChange(spec.path(), null, null, projectServerLocation(spec),
                               AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_VALUE, true,
                               "create server location");
      }

      String key = requireKey(label, change.getKey());
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
         return new PlanChange(key, null, projectServerLocation(existing), projectServerLocation(spec),
                               AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_VALUE, true,
                               "update server location");
      }

      throw unknownVerb(label, verb);
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
      String label, ScheduleConfigChangeRequest change, List<TimeRangeModel> ranges, Set<String> seen)
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
         return new TimeRangeResolution(
            new PlanChange(spec.name(), null, null, projectTimeRange(spec), AdminChangeRecord.RISK_HIGH,
                           AdminChangeRecord.SCOPE_VALUE, true, "create time range"),
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

         ranges.set(existingIndex, spec);
         requireNoDuplicateNamesOrDefaults(label, ranges);

         boolean identityChanged = !existing.name().equals(spec.name()) ||
            !LocalTime.parse(existing.startTime()).equals(LocalTime.parse(spec.startTime())) ||
            !LocalTime.parse(existing.endTime()).equals(LocalTime.parse(spec.endTime()));

         return new TimeRangeResolution(
            new PlanChange(key, null, projectTimeRange(existing), projectTimeRange(spec),
                           AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_VALUE, true,
                           "update time range"),
            identityChanged ? existing.name() : null);
      }

      throw unknownVerb(label, verb);
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

      return location;
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

      requireLocalTime(label + ".spec.startTime", range.startTime());
      requireLocalTime(label + ".spec.endTime", range.endTime());
      return range;
   }

   private static void requireLocalTime(String label, String value) {
      if(value == null) {
         throw new IllegalArgumentException(label + ": required (HH:mm or HH:mm:ss)");
      }

      try {
         LocalTime.parse(value);
      }
      catch(DateTimeParseException e) {
         throw new IllegalArgumentException(label + ": \"" + value + "\" is not a valid HH:mm[:ss] time");
      }
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

   /** Never leaks the literal password/oldPasswordKey into the plan hash/narrative -- only whether
    * a credential is present, mirroring the masking convention {@code
    * ScheduleTaskChangePlanService}'s XML projection uses for the same concern. */
   static String projectServerLocation(ServerLocation location) {
      StringBuilder sb = new StringBuilder();
      sb.append("path=").append(location.path()).append(";label=").append(location.label());
      ServerPathInfoModel info = location.pathInfoModel();

      if(info != null) {
         sb.append(";ftp=").append(info.ftp())
            .append(";useCredential=").append(info.useCredential())
            .append(";username=").append(info.username())
            .append(";secretId=").append(info.secretId() == null ? "(none)" : "(set)")
            .append(";password=").append(info.password() == null ? "(none)" : "(set)");
      }

      return sb.toString();
   }

   static String projectTimeRange(TimeRangeModel range) {
      return "name=" + range.name() + ";startTime=" + range.startTime() + ";endTime=" +
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
   private static String hash(List<PlanChange> changes) {
      StringBuilder canonical = new StringBuilder();

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonical(change.currentValue())).append(SEP)
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
         throw new IllegalStateException("SHA-256 is required to hash a schedule config change plan", e);
      }
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private static final ObjectMapper MAPPER = new ObjectMapper();
   private final AdminScheduleConfigGateway gateway;
}
