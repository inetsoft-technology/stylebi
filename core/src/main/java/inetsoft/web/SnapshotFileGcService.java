/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.web;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Deletes permanent (non-temp) snapshot {@code _s.tdat} files that nothing currently references
 * (bug #78035). A snapshot file's data paths can be dropped from every worksheet that ever named
 * them -- a table removed from a worksheet before it is saved, a frozen outer copy's replaced
 * files, files dropped by undo past a save point, or an owner file kept forever by bug #78032's
 * {@code isNamedByFrozenCopy} once the legacy frozen copy that justified keeping it is gone --
 * without anything else ever deleting them. Modeled directly on the two existing analogs for the
 * same kind of problem, {@link EmbeddedTableStorage#removeExpiredTempTables()} and
 * {@link AutoSaveService#removeExpiredAutoSaveFiles()}: a plain Spring {@code @Scheduled} bean on
 * a web-server node, not a Quartz task, looping every organization with per-organization failure
 * isolation, deleting via the existing idempotent {@link EmbeddedTableStorage#removeTable} (safe
 * against a path already gone).
 *
 * <p>Unlike those two analogs, "committed but now unreferenced" cannot be decided from age alone
 * -- a legitimately-still-used file can go untouched for years -- so this one needs an actual
 * reference check. The reference signal is built from reading stored content directly, never the
 * dependency index (it has no file-path-level entries, only sheet-to-sheet references):
 * <ul>
 *    <li>every stored {@code WORKSHEET} asset in the organization's {@code GLOBAL_SCOPE} and each
 *    identity's {@code USER_SCOPE}, read as stored (not opened or locked), for its
 *    {@link SnapshotEmbeddedTableAssembly#getDataPaths()};
 *    <li>every auto-save draft, the same bucket {@link AutoSaveService} already enumerates for its
 *    own temp-file cleanup;
 *    <li>every currently open runtime worksheet session, cluster-wide, via
 *    {@link inetsoft.report.composition.RuntimeSheetCache#getOpenWorksheetDataPaths()}.
 * </ul>
 *
 * <p>Reading every stored worksheet's content on every run would be far more expensive than the
 * metadata-only scans its two analogs do, so the daily run is incremental: a persisted per-org
 * watermark plus a persisted {@code asset key -> data paths} map, refreshed only for keys
 * {@link IndexedStorage#getTimestamps(IndexedStorage.Filter, long)} reports as changed since the
 * last successful run. An imported asset's stored {@code lastModified} is backdated to its
 * original authoring time rather than the moment of import (see
 * {@code BlobIndexedStorage.getLastModified(String, inetsoft.util.XMLSerializable)}), so an
 * incremental scan alone could permanently miss that it now references a path -- a real risk of
 * deleting a file something still names, not just a stale orphan surviving one cycle too long. To
 * close that gap, a full, non-incremental reconciliation sweep (no {@code lastModified} filter)
 * runs on a longer cadence, and a candidate path is only ever actually deleted once both (a) it
 * has been continuously absent from the referenced-paths set for at least a grace period, and (b)
 * at least one full sweep has completed since it was last seen as referenced (or since its
 * creation, if never seen as referenced) and that sweep still shows it unreferenced.
 */
@Service
@Lazy(false)
public class SnapshotFileGcService {
   public SnapshotFileGcService(ViewsheetService viewsheetService,
                                 EmbeddedTableStorage embeddedTableStorage,
                                 KeyValueStorageManager keyValueStorageManager)
   {
      this.viewsheetService = viewsheetService;
      this.embeddedTableStorage = embeddedTableStorage;
      this.keyValueStorageManager = keyValueStorageManager;
   }

   @Scheduled(fixedRate = DAILY_INTERVAL_MS)
   public void removeOrphanedPermanentSnapshotFiles() {
      removeOrphanedPermanentSnapshotFiles(Instant.now());
   }

   /**
    * Runs one cleanup cycle as of the given instant. Package-private so tests can drive the
    * grace period and the weekly full-sweep cadence without waiting on real time.
    */
   void removeOrphanedPermanentSnapshotFiles(Instant now) {
      // the open-runtime-worksheet signal is cluster-wide, not per-org (RuntimeSheetCache has no
      // cheap way to attribute a cached session to an organization), so it is computed once and
      // added to every organization's referenced-paths set. This only ever makes the cleanup more
      // conservative (a path it names is never deleted anywhere), never less safe.
      Set<String> liveSessionPaths = collectLiveSessionDataPaths();
      String[] orgIds = SecurityEngine.getSecurity().getSecurityProvider().getOrganizationIDs();

      for(String orgId : orgIds) {
         try {
            runOrgCycle(orgId, now, liveSessionPaths);
         }
         catch(Exception e) {
            // isolate per-org so one org's storage failure doesn't starve cleanup for every org
            // that sorts after it in getOrganizationIDs() on this (and, if persistent, every
            // subsequent) scheduled run
            LOG.warn("Failed to remove orphaned permanent snapshot data files for organization {}",
                     orgId, e);
         }
      }
   }

   private Set<String> collectLiveSessionDataPaths() {
      try {
         if(viewsheetService instanceof WorksheetEngine engine) {
            return engine.getOpenWorksheetDataPaths();
         }

         LOG.warn("Live runtime worksheet session scan skipped: {} is not a {}, so this cycle " +
                  "has no live-session signal and relies solely on the stored-content and " +
                  "auto-save scans",
                  viewsheetService == null ? "null" : viewsheetService.getClass(),
                  WorksheetEngine.class.getSimpleName());
      }
      catch(Exception ex) {
         LOG.warn("Failed to scan open runtime worksheet sessions for referenced snapshot " +
                  "data files", ex);
      }

      return Collections.emptySet();
   }

   private void runOrgCycle(String orgId, Instant now, Set<String> liveSessionPaths)
      throws Exception
   {
      // resolve this org's own buckets (auto-save, indexed storage) even though this scheduled
      // job runs with no ambient principal -- the same reason AutoSaveService sets this before
      // its own per-org loop body.
      OrganizationContextHolder.setCurrentOrgId(orgId);

      try {
         // every web-server node runs this scheduled job independently against the same
         // per-org persisted SnapshotGcState, and -- unlike the age-only analogs this class is
         // modeled on -- that state is read, mutated, and written back as a whole on every
         // cycle. Without serializing the read-modify-write below, a slower node's full-sweep
         // cycle can read a stale copy, finish after a faster node's incremental cycle has
         // already written a corrected one, and blindly overwrite it -- resurrecting a stale
         // candidate and ultimately deleting a file that is, in truth, still referenced. This
         // is the same per-key cluster write-lock idiom MVSingleDispatcher.save() uses to
         // ensure only one copy of a shared, mutated-in-place resource is modified and saved at
         // a time (bug #78035 review round 1, Important #1).
         String lockKey = "SnapshotFileGcService.state." + orgId;
         Cluster.getInstance().lockKey(lockKey);

         try {
            KeyValueStorage<SnapshotGcState> store = getStateStorage(orgId);
            SnapshotGcState state = store.get(STATE_KEY);

            if(state == null) {
               state = new SnapshotGcState();
            }

            // a never-run org's lastFullSweep is 0, so its very first cycle is always a full
            // sweep, which seeds the asset data paths map the same way a periodic full sweep
            // refreshes it
            boolean fullSweep =
               now.toEpochMilli() - state.lastFullSweep >= FULL_SWEEP_INTERVAL.toMillis();

            if(fullSweep) {
               refreshAllWorksheetEntries(orgId, state);
            }
            else {
               refreshChangedWorksheetEntries(orgId, state, now);
            }

            refreshAutoSaveEntries(orgId, state);

            Set<String> referenced = new HashSet<>(liveSessionPaths);
            state.assetDataPaths.values().forEach(referenced::addAll);
            state.autosaveDataPaths.values().forEach(referenced::addAll);

            processCandidates(orgId, now, fullSweep, referenced, state);

            state.watermark = now.toEpochMilli();

            if(fullSweep) {
               state.lastFullSweep = now.toEpochMilli();
            }

            store.put(STATE_KEY, state).get(10, TimeUnit.SECONDS);
         }
         finally {
            Cluster.getInstance().unlockKey(lockKey);
         }
      }
      finally {
         OrganizationContextHolder.clear();
      }
   }

   /**
    * Full, non-incremental reconciliation sweep: re-reads every stored {@code WORKSHEET} entry's
    * content regardless of its stored {@code lastModified}, so it cannot miss a backdated-import's
    * reference the way an incremental, watermark-bounded scan structurally can.
    */
   private void refreshAllWorksheetEntries(String orgId, SnapshotGcState state) throws Exception {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      Map<String, Set<String>> paths = new HashMap<>();

      for(AssetEntry candidate : enumerateWorksheetEntries(repository, orgId)) {
         String key = candidate.toIdentifier();
         Set<String> dataPaths = readWorksheetDataPaths(repository, candidate, orgId);

         if(dataPaths != null) {
            paths.put(key, dataPaths);
         }
         else {
            // a transient read failure must not look like "no longer references anything" --
            // keep whatever this key's last known-good read found, the same null-safety
            // getFrozenCopyDataPaths/isNamedByFrozenCopy already apply for the same reason
            Set<String> previous = state.assetDataPaths.get(key);

            if(previous != null) {
               paths.put(key, previous);
            }
         }
      }

      state.assetDataPaths = paths;
   }

   /**
    * Incremental scan: only re-reads stored {@code WORKSHEET} entries
    * {@link IndexedStorage#getTimestamps(IndexedStorage.Filter, long)} reports as changed since
    * the last successful run, bounding the daily cost to what changed rather than the total
    * corpus size.
    */
   private void refreshChangedWorksheetEntries(String orgId, SnapshotGcState state, Instant now)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      IndexedStorage.Filter worksheetFilter = key -> {
         AssetEntry e = AssetEntry.createAssetEntry(key);
         return e != null && e.isWorksheet();
      };

      Map<String, Long> changed = storage.getTimestamps(worksheetFilter, state.watermark);

      for(String key : changed.keySet()) {
         AssetEntry entry = AssetEntry.createAssetEntry(key, orgId);

         if(entry == null) {
            continue;
         }

         Set<String> dataPaths = readWorksheetDataPaths(repository, entry, orgId);

         if(dataPaths != null) {
            state.assetDataPaths.put(key, dataPaths);
         }
         // else: a transient read failure keeps whatever was already recorded for this key,
         // the same null-safety the full sweep applies
      }

      // drop bookkeeping for keys that no longer exist, so a deleted worksheet's paths stop
      // being treated as referenced
      Set<String> currentKeys = storage.getKeys(worksheetFilter, orgId);
      state.assetDataPaths.keySet().removeIf(key -> !currentKeys.contains(key));
   }

   /**
    * Enumerate every stored {@code WORKSHEET} entry in an organization: the {@code GLOBAL_SCOPE}
    * root, plus one {@code USER_SCOPE} root per identity in the organization (the production
    * precedent for this primitive, {@code ScheduleService.getViewsheets}, only ever builds a
    * {@code USER_SCOPE} root for the calling principal's own identity, not every identity in an
    * org, so looping every identity here is new, not copied).
    *
    * <p>The {@code GLOBAL_SCOPE} branch of {@code getAllEntries} runs a real ACL check with no
    * automatic site-admin bypass, so it is wrapped in {@link AssetRepository#IGNORE_PERM} --
    * otherwise a global folder this task's (ambient, often null) principal lacks READ on would be
    * silently excluded, causing a real reference to be missed. The {@code USER_SCOPE} branch does
    * no permission filtering at all (confirmed by reading {@code getAllEntries}'s own filter: a
    * {@code USER_SCOPE} root only matches entries by the root's own declared owner, never by the
    * principal argument), so it needs no such wrapping.
    */
   private List<AssetEntry> enumerateWorksheetEntries(AssetRepository repository, String orgId)
      throws Exception
   {
      List<AssetEntry> entries = new ArrayList<>();
      AssetEntry.Selector selector = new AssetEntry.Selector(AssetEntry.Type.WORKSHEET);
      AssetEntry globalRoot = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                             AssetEntry.Type.REPOSITORY_FOLDER, "/", null, orgId);

      AssetRepository.IGNORE_PERM.set(true);

      try {
         entries.addAll(Arrays.asList(
            repository.getAllEntries(globalRoot, null, ResourceAction.READ, selector)));
      }
      finally {
         AssetRepository.IGNORE_PERM.remove();
      }

      for(IdentityID user : SecurityEngine.getSecurity().getOrgUsers(orgId)) {
         AssetEntry userRoot = new AssetEntry(AssetRepository.USER_SCOPE,
                                              AssetEntry.Type.REPOSITORY_FOLDER, "/", user, orgId);
         entries.addAll(Arrays.asList(
            repository.getAllEntries(userRoot, null, ResourceAction.READ, selector)));
      }

      return entries;
   }

   /**
    * Read a stored worksheet entry's content directly (not through
    * {@link AssetRepository#getSheet}, so this never competes with a live editing session's lock)
    * and extract its snapshot tables' data paths.
    *
    * @return the data paths, or {@code null} if the entry could not be read.
    */
   private Set<String> readWorksheetDataPaths(AssetRepository repository, AssetEntry entry,
                                              String orgId)
   {
      try {
         IndexedStorage storage = repository.getStorage(entry);
         Object sheet = storage.getXMLSerializable(entry.toIdentifier(), null, orgId);

         if(sheet instanceof Worksheet) {
            return SnapshotEmbeddedTableAssembly.getDataPaths((Worksheet) sheet);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to read worksheet {} while scanning for referenced snapshot data " +
                  "files", entry, ex);
      }

      return null;
   }

   /**
    * Refresh the auto-save draft bucket's data paths. Re-read fully every cycle rather than
    * incrementally: the bucket is already small and bounded by auto-save's own 7-day retention,
    * the same bucket {@link AutoSaveService#removeExpiredAutoSaveFiles} enumerates for its own
    * temp-file cleanup.
    *
    * <p>Reads each draft's raw stored content directly, rather than through
    * {@link AutoSaveUtils#createAssetEntry}/{@code getAutoSavedSheet} -- those are built for the
    * recycle-bin restore flow (the entry they build always carries a hardcoded "recycled" flag,
    * and parsing a recycled file's own name as an identifier fails for an active, not-yet-recycled
    * draft) and are not a safe fit for simply reading every draft's content regardless of bucket.
    */
   private void refreshAutoSaveEntries(String orgId, SnapshotGcState state) {
      Map<String, Set<String>> paths = new HashMap<>();

      for(boolean recycle : new boolean[] { false, true }) {
         for(String fileName : AutoSaveUtils.getAutoSavedFiles(null, recycle)) {
            try(InputStream input = AutoSaveUtils.getInputStream(fileName, (Principal) null)) {
               if(input == null) {
                  continue;
               }

               Document document = Tool.parseXML(input);
               Element root = document == null ? null : document.getDocumentElement();

               // skip auto-saved viewsheet drafts: a Viewsheet never serializes an inline
               // snapshot-bearing worksheet (its base worksheet is always either a reference to a
               // separately-stored WORKSHEET entry, already covered above, or a freshly-built
               // worksheet with a plain TableAssembly), so there is nothing to extract from one
               if(root == null || !"worksheet".equals(root.getTagName())) {
                  continue;
               }

               Worksheet ws = new Worksheet();
               ws.parseXML(root);
               paths.put(fileName, SnapshotEmbeddedTableAssembly.getDataPaths(ws));
            }
            catch(Exception ex) {
               LOG.warn("Failed to read auto-saved draft {} while scanning for referenced " +
                        "snapshot data files", fileName, ex);
               Set<String> previous = state.autosaveDataPaths.get(fileName);

               if(previous != null) {
                  paths.put(fileName, previous);
               }
            }
         }
      }

      state.autosaveDataPaths = paths;
   }

   /**
    * Compare the organization's existing permanent snapshot files against the referenced-paths
    * set built this cycle, and delete the ones gated as safe to delete.
    */
   private void processCandidates(String orgId, Instant now, boolean fullSweep,
                                  Set<String> referenced, SnapshotGcState state)
   {
      List<String> existing = embeddedTableStorage.listPermanentTablePaths(orgId);
      Set<String> existingSet = new HashSet<>(existing);
      long nowMillis = now.toEpochMilli();

      for(String path : existing) {
         if(referenced.contains(path)) {
            // referenced again (or still) -- any continuous-unreferenced window it was
            // accumulating is over
            state.candidates.remove(path);
            continue;
         }

         CandidateState candidate =
            state.candidates.computeIfAbsent(path, k -> new CandidateState(nowMillis));

         if(fullSweep) {
            candidate.sweepConfirmed = true;
         }

         boolean pastGracePeriod =
            nowMillis - candidate.unreferencedSince >= GRACE_PERIOD.toMillis();

         // both conditions must hold: continuously unreferenced for the grace period, and a
         // weekly full sweep confirming it unreferenced since it was last seen as referenced (or
         // since it was first seen as a candidate, if never seen as referenced) -- an incremental
         // scan's absence alone is never sufficient to authorize deletion (bug #78035)
         if(pastGracePeriod && candidate.sweepConfirmed) {
            try {
               embeddedTableStorage.removeTable(path + SNAPSHOT_FILE_SUFFIX, orgId);
               state.candidates.remove(path);
            }
            catch(IOException ex) {
               LOG.warn("Failed to remove orphaned permanent snapshot data file {}", path, ex);
            }
         }
      }

      // drop bookkeeping for paths that are no longer permanent blobs at all (already deleted
      // some other way, or re-flagged and expired as a temp file)
      state.candidates.keySet().removeIf(path -> !existingSet.contains(path));
   }

   private KeyValueStorage<SnapshotGcState> getStateStorage(String orgId) {
      String storeId = orgId.toLowerCase() + "__" + "snapshotGc";
      return keyValueStorageManager.getStorage(storeId);
   }

   /**
    * Deletes this organization's persisted GC bookkeeping bucket entirely -- mirroring
    * {@link inetsoft.uql.asset.sync.DependencyStorageService#removeDependencyStorage(String)}
    * for an almost-identical per-org {@link KeyValueStorage}-backed bucket. Called from
    * {@link inetsoft.web.admin.security.IdentityService#removeStorages(String)} on org deletion,
    * so a deleted org doesn't leave this small, bounded bucket of GC bookkeeping (never snapshot
    * data itself) behind forever (bug #78035 review round 1, Important #3).
    */
   public void removeState(String orgId) throws Exception {
      KeyValueStorage<SnapshotGcState> store = getStateStorage(orgId);
      store.deleteStore().get(1L, TimeUnit.MINUTES);
      store.close();
   }

   /**
    * Test-only: discards an organization's persisted bookkeeping (watermark, last full sweep
    * time, asset/autosave data path maps, candidate tracking), so a test can start its own cycle
    * sequence from a clean slate regardless of what any other test run against the same
    * organization has already recorded.
    */
   void resetStateForTesting(String orgId) throws Exception {
      getStateStorage(orgId).remove(STATE_KEY).get(10, TimeUnit.SECONDS);
   }

   private final ViewsheetService viewsheetService;
   private final EmbeddedTableStorage embeddedTableStorage;
   private final KeyValueStorageManager keyValueStorageManager;

   private static final String STATE_KEY = "state";
   private static final String SNAPSHOT_FILE_SUFFIX = "_s.tdat";
   private static final long DAILY_INTERVAL_MS = 24L * 60 * 60 * 1000;
   /**
    * How long a path must be continuously absent from the referenced-paths set before it is
    * eligible for deletion. Generous for the dominant remaining cause of the gap -- the time
    * between two scheduled scan runs at a daily cadence, plus ordinary clock skew across cluster
    * nodes -- now that an open runtime session's own edits are a direct signal
    * ({@link inetsoft.report.composition.RuntimeSheetCache#getOpenWorksheetDataPaths()}), not
    * something this grace period has to cover.
    */
   static final Duration GRACE_PERIOD = Duration.ofHours(48);
   /**
    * How often a full, non-incremental reconciliation sweep runs, specifically to catch an
    * imported asset's backdated {@code lastModified} that an incremental scan's watermark filter
    * would otherwise permanently miss. A file is never deleted on an incremental scan's evidence
    * alone; see {@link #processCandidates}.
    */
   static final Duration FULL_SWEEP_INTERVAL = Duration.ofDays(7);

   private static final Logger LOG = LoggerFactory.getLogger(SnapshotFileGcService.class);

   /**
    * Per-organization persisted bookkeeping, so the daily scan can be incremental instead of a
    * full content read every cycle.
    */
   static final class SnapshotGcState implements Serializable {
      /** The completion time of the last successful run (incremental or full), epoch millis. */
      long watermark;
      /** The completion time of the last successful full reconciliation sweep, epoch millis. */
      long lastFullSweep;
      /** Stored worksheet asset key -> its snapshot data paths as of the last read. */
      Map<String, Set<String>> assetDataPaths = new HashMap<>();
      /** Auto-save file name -> its snapshot data paths as of the last read. */
      Map<String, Set<String>> autosaveDataPaths = new HashMap<>();
      /** Data path currently believed unreferenced -> its deletion-eligibility tracking state. */
      Map<String, CandidateState> candidates = new HashMap<>();
   }

   /**
    * Tracks how long a data path has been continuously unreferenced, and whether a full sweep has
    * confirmed it unreferenced during that same continuous window.
    */
   static final class CandidateState implements Serializable {
      CandidateState(long unreferencedSince) {
         this.unreferencedSince = unreferencedSince;
      }

      /** The first time this path was observed unreferenced, in the current continuous window. */
      long unreferencedSince;
      /**
       * {@code true} once a full sweep has run while this path was continuously unreferenced
       * since {@link #unreferencedSince}.
       */
      boolean sweepConfirmed;
   }
}
