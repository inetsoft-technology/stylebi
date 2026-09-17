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
package inetsoft.web.admin.ai.datasource;

import inetsoft.web.admin.datasource.*;
import inetsoft.report.internal.Util;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.admin.general.model.DatabaseSettingsModel;
import inetsoft.web.admin.security.ConnectionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;

/**
 * Resolves a requested list of data-source changes into a {@link ResolvedPlan} and hashes it --
 * the data-sources analog of {@code inetsoft.web.admin.ai.AdminChangePlanService} and (within this
 * run) {@code PermissionChangePlanService}/{@code ProviderChangePlanService}/etc, replicated
 * rather than shared (01-spec.md section 6, carry-forward item 5).
 *
 * <p>Unlike every prior area, this service must itself build two Java-side mechanisms the wrapped
 * {@link DataSourceService} does not provide at all (01-spec.md section 0): the password-merge
 * mechanism (section 0.1, {@link #buildProposedJdbc}/{@link #buildBeforeJdbc}, using {@link
 * DataSourceApiService#getCurrentPassword}) and the dependency preflight for delete (section 0.2,
 * {@link #findDependencies}, calling {@link DependencyTool#getDependencies} directly since {@code
 * deleteDataSource}'s own {@code force} parameter is a dead no-op at this tier).
 *
 * <p><b>No org-targeting parameter is exposed at this tier</b> -- every {@code
 * DataSourceApiService} call this class makes passes {@code organizationid: null} ("the caller's
 * own current organization"), matching what schedule tasks/permissions actually built (neither
 * exposes an org-targeting argument despite their own wrapped {@code *ApiService} supporting one;
 * see {@code AdminScheduleController#listTasks}'s own comment) rather than 01-spec.md section 3's
 * looser description of an optional {@code organizationid} tool argument -- 04-build-java.md
 * records this as a build-time reconciliation toward established precedent, not a scope cut (the
 * underlying service already treats {@code null} as "the caller's own org", so this changes no
 * externally observable behavior for the only caller population this area has: a site
 * administrator's own single-organization session).
 */
@Component
public class DataSourceChangePlanService {
   @Autowired
   public DataSourceChangePlanService(DataSourceService dataSourceService,
                                      DatabaseSettingsService databaseSettingsService)
   {
      this.dataSourceService = dataSourceService;
      this.databaseSettingsService = databaseSettingsService;
   }

   /**
    * Resolves and hashes a plan. Performs no mutation, but DOES perform live reads: the current
    * data source projection for every change (including, for JDBC sources, a real-password read
    * via {@link DataSourceService#getCurrentPassword}) and, for delete, a live dependency-graph
    * read via {@link DependencyTool#getDependencies}.
    *
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *                                 list, an unrecognized verb, a missing/ambiguous id-or-name, an
    *                                 unrecognized/refused spec field, a masked-literal password, a
    *                                 missing {@code confirmRename} on a detected rename, or (for
    *                                 delete) unresolved dependencies without {@code force: true}.
    */
   public ResolvedPlan resolve(DataSourceChangePlanRequest req, Principal user) throws Exception {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      List<PlanChange> changes = new ArrayList<>();
      Set<String> seenNames = new HashSet<>();
      Map<String, String> dependencyProjections = new LinkedHashMap<>();
      int index = 0;

      for(DataSourceChangeRequest change : req.getChanges()) {
         String label = "changes[" + index++ + "]";
         changes.add(resolveOne(label, change, user, seenNames, dependencyProjections));
      }

      String task = req.getTask().trim();
      // Both verbs are risk:high, snapshotScope:storage, unconditionally (section 4/7) -- no
      // "recognized" axis the way an uncatalogued property has one.
      String planHash = hash(changes, dependencyProjections);
      return new ResolvedPlan(task, Collections.unmodifiableList(changes), true, true,
                              planHash, TaskAuditToken.issue(planHash, task));
   }

   private PlanChange resolveOne(String label, DataSourceChangeRequest change, Principal user,
                                 Set<String> seenNames, Map<String, String> dependencyProjections)
      throws Exception
   {
      if(change == null) {
         throw new IllegalArgumentException(label + ": must not be null");
      }

      String verb = requireVerb(label, change.getVerb());

      // Bug 76599: verb=create targets a bare folder path, not an existing data source -- it has
      // no id/name to resolve at all, unlike update/delete below.
      if(DataSourceChangeRequest.VERB_CREATE.equals(verb)) {
         return resolveFolderCreate(label, change, seenNames);
      }

      String name = resolveName(label, change, user);

      if(!seenNames.add(name)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for \"" + name + "\"; list each data source at most once");
      }

      if(DataSourceChangeRequest.VERB_UPDATE.equals(verb)) {
         return resolveUpdate(label, name, change, user);
      }

      return resolveDelete(label, name, change, user, dependencyProjections);
   }

   // ---------------------------------------------------------------- folder create

   /** Bug 76599, Gap 2a: a bare data-source folder create, source-verified metadata-only (the
    * folder registry is independent of any data source's storage, matching {@code
    * ViewsheetChangePlanService#resolveFolderCreate}'s own {@code RISK_LOW} classification for the
    * viewsheets analog). */
   private PlanChange resolveFolderCreate(String label, DataSourceChangeRequest change,
                                          Set<String> seenNames)
      throws Exception
   {
      requireUnused(label, "id", change.getId());
      requireUnused(label, "name", change.getName());
      requireUnused(label, "spec", change.getSpec());
      requireUnused(label, "confirmRename", change.getConfirmRename());
      requireUnused(label, "force", change.getForce());

      String folderPath = requireNonBlank(label + ".folderPath", change.getFolderPath());
      String key = "folder:" + folderPath;

      if(!seenNames.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for folder \"" + folderPath + "\"; list each unit at most " +
            "once");
      }

      if(dataSourceService.dataSourceFolderExists(folderPath)) {
         throw new IllegalArgumentException(
            label + ".folderPath: folder \"" + folderPath + "\" already exists");
      }

      String orgId = OrganizationManager.getInstance().getCurrentOrgID();
      String description = "create data source folder \"" + folderPath + "\"";
      return new PlanChange(key, orgId, "(does not exist)", "(will be created)",
                            AdminChangeRecord.RISK_LOW, AdminChangeRecord.SCOPE_STORAGE, true,
                            description);
   }

   private static void requireUnused(String label, String field, Object value) {
      if(value != null) {
         throw new IllegalArgumentException(
            label + "." + field + ": not used for verb=create; refused rather than silently " +
            "ignored");
      }
   }

   private static String requireNonBlank(String label, String value) {
      String trimmed = blankToNull(value);

      if(trimmed == null) {
         throw new IllegalArgumentException(label + ": required");
      }

      return trimmed;
   }

   // ---------------------------------------------------------------- test connection

   /**
    * Live JDBC connectivity probe against a PENDING (not-yet-applied) set of connection fields --
    * performs no mutation. Bug 76599 (Gap 1): merges {@code req.getSpec()} onto the current data
    * source's real, unmasked connection fields the same way {@link #resolveUpdate} does for a
    * preview, then delegates straight to {@link DatabaseSettingsService#testConnection} -- the
    * exact same JDBC probe EM's own "Test Connection" button already uses.
    *
    * @throws IllegalArgumentException with a field-named message on a missing/ambiguous id-or-
    *                                 name, an unrecognized/refused spec field, or a masked-literal
    *                                 password, or if the resolved data source is not JDBC-type
    *                                 (connectivity testing does not apply to tabular sources).
    */
   public ConnectionStatus testConnection(DataSourceTestConnectionRequest req, Principal user)
      throws Exception
   {
      if(req == null) {
         throw new IllegalArgumentException("id/name and spec are required");
      }

      String label = "testConnection";
      String name = resolveName(label, req.getName(), req.getId(), user);
      String id = resolveCurrentId(label, name, user);
      DataSourceProperties current = dataSourceService.getDataSource(id, user);

      if(!(current instanceof JdbcDataSourceProperties)) {
         throw new IllegalArgumentException(
            label + ": \"" + name + "\" is not a jdbc-type data source -- connectivity testing " +
            "only applies to jdbc-type data sources");
      }

      Map<String, Object> spec = req.getSpec() == null ? Map.of() : req.getSpec();
      JdbcDataSourceProperties proposed = buildProposedJdbc(
         label, id, name, (JdbcDataSourceProperties) current, spec, user);

      DatabaseSettingsModel model = DatabaseSettingsModel.builder()
         .databaseURL(proposed.getUrl())
         .driver(proposed.getDriver())
         .defaultDB(proposed.getDefaultDatabase())
         .requiresLogin(proposed.isRequireLogin())
         .username(proposed.isRequireLogin() ? proposed.getUser() : null)
         .password(proposed.isRequireLogin() ? proposed.getPassword() : null)
         .build();

      return databaseSettingsService.testConnection(model, user);
   }

   /**
    * Resolves the durable identity (name, section 2) for one change entry: id and name accepted,
    * a field-named error if both are given and BOTH resolve, to different data sources (section
    * 11) -- never silently preferring one. When {@code name} is given, it is the identity of
    * record (section 2's own "name, not id, is the durable identity" design) -- a stale/evicted id
    * given ALONGSIDE a valid name is a corroborating hint only, not a hard requirement: a {@link
    * MissingResourceException} resolving the id alone must never block a plan whose name is
    * otherwise valid, which is exactly the id-cache-eviction failure mode section 2 exists to
    * close (a captured id going cold across the human-review gap must not surface a false
    * "not found" for a data source that still exists). Only when id is given WITHOUT a name does
    * it have to resolve at all. {@link DataSourceChangesetApplyService} re-resolves the id again,
    * independently, at apply time (section 2/6), never trusting anything cached from this call.
    */
   private String resolveName(String label, DataSourceChangeRequest change, Principal user)
      throws Exception
   {
      return resolveName(label, change.getName(), change.getId(), user);
   }

   /** Same id/name resolution as the {@link DataSourceChangeRequest}-based overload above, for a
    * caller (bug 76599's {@link #testConnection}) with a plain id/name pair rather than a full
    * change request -- extracted so both share the exact same resolution rules rather than
    * re-deriving them. */
   private String resolveName(String label, String rawNameIn, String rawIdIn, Principal user)
      throws Exception
   {
      String rawName = blankToNull(rawNameIn);
      String rawId = blankToNull(rawIdIn);

      if(rawName == null && rawId == null) {
         throw new IllegalArgumentException(label + ": at least one of id/name is required");
      }

      if(rawName != null) {
         if(rawId != null) {
            String idResolvedName = tryResolveIdToName(rawId, user);

            if(idResolvedName != null && !idResolvedName.equals(rawName)) {
               throw new IllegalArgumentException(
                  label + ": id resolves to \"" + idResolvedName + "\", which does not match the " +
                  "given name \"" + rawName + "\" -- an ambiguous combined input is refused rather " +
                  "than silently resolved one way");
            }
         }

         return rawName;
      }

      // id only, no name given: there is no durable-identity fallback, so id must resolve.
      return dataSourceService.getDataSource(rawId, user).getName();
   }

   /** {@code null} when {@code id} does not resolve (section 2's id-cache-eviction finding) --
    * deliberately NOT propagated as an error here, since the caller in {@link #resolveName} only
    * uses this as a corroborating hint when a name is also present. */
   private String tryResolveIdToName(String id, Principal user) throws Exception {
      try {
         return dataSourceService.getDataSource(id, user).getName();
      }
      catch(inetsoft.web.security.auth.MissingResourceException e) {
         return null;
      }
   }

   /** Re-resolves the current id for a name via a fresh list call -- never a captured id (section
    * 2). Package-visible so {@link DataSourceChangesetApplyService} can call the same logic at
    * apply time. */
   String resolveCurrentId(String label, String name, Principal user) throws Exception {
      DataSourceList list = dataSourceService.getDataSources(name, user);

      if(list.getDataSources() == null || list.getDataSources().isEmpty()) {
         throw new IllegalArgumentException(label + ": no data source named \"" + name + "\" exists");
      }

      return list.getDataSources().get(0).getId();
   }

   // ---------------------------------------------------------------- update

   private PlanChange resolveUpdate(String label, String name, DataSourceChangeRequest change,
                                    Principal user)
      throws Exception
   {
      if(change.getForce() != null) {
         throw new IllegalArgumentException(
            label + ".force: not used for verb=update; remove it or use verb=delete");
      }

      Map<String, Object> spec = change.getSpec();

      if(spec == null || spec.isEmpty()) {
         throw new IllegalArgumentException(label + ".spec: required for verb=update");
      }

      String id = resolveCurrentId(label, name, user);
      DataSourceProperties current = dataSourceService.getDataSource(id, user);

      DataSourceProperties proposed;
      DataSourceProperties before;

      if(current instanceof JdbcDataSourceProperties) {
         JdbcDataSourceProperties currentJdbc = (JdbcDataSourceProperties) current;
         proposed = buildProposedJdbc(label, id, name, currentJdbc, spec, user);
         before = buildBeforeJdbc(id, currentJdbc, user);
      }
      else {
         proposed = buildProposedTabular(label, name, (TabularDataSourceProperties) current, spec);
         before = current;
      }

      // 03-reconcile.md Addition 1: a name that differs from the resolved current name is a
      // distinct, flagged action, never silently accepted the way url/driver/etc are.
      String proposedName = proposed.getName();

      if(proposedName != null && !proposedName.equals(name) &&
         !Boolean.TRUE.equals(change.getConfirmRename()))
      {
         throw new IllegalArgumentException(
            label + ".confirmRename: this update changes name from \"" + name + "\" to \"" +
            proposedName + "\", which renames/moves the data source and may auto-create parent " +
            "folders as a side effect (section 4/0.2's RenameTransformHandler/folder-creation " +
            "finding) -- set confirmRename: true to acknowledge this explicitly, or omit " +
            "spec.name to leave the name unchanged");
      }

      String beforeProjection = DataSourceProjection.project(before);
      String proposedProjection = DataSourceProjection.project(proposed);
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();
      String description = proposedName != null && !proposedName.equals(name)
         ? "update data source \"" + name + "\" (renaming to \"" + proposedName + "\")"
         : "update data source \"" + name + "\"";
      return new PlanChange(name, orgId, beforeProjection, proposedProjection,
                            AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE, true,
                            description);
   }

   /**
    * Section 0.1's password merge, applied to the PROPOSED value that will actually be sent to
    * {@code updateDataSource}: any field in {@code spec} not present is carried over from {@code
    * current} unchanged; password specifically, when omitted and the resolved {@code
    * requireLogin} is {@code true}, is filled in from a fresh {@link
    * DataSourceApiService#getCurrentPassword} read -- never left as the wrapped API's own masked
    * literal, which is what a naive read-then-write would otherwise send back verbatim.
    */
   /** Package-visible so {@link DataSourceChangesetApplyService} can build the SAME proposed value
    * at apply time (never re-deriving the password-merge logic independently, section 0.1). */
   JdbcDataSourceProperties buildProposedJdbc(String label, String id, String name,
                                              JdbcDataSourceProperties current,
                                              Map<String, Object> spec, Principal user)
      throws Exception
   {
      requireAllowedKeys(label, spec, JDBC_ALLOWED_FIELDS);
      JdbcDataSourceProperties proposed = copyJdbc(current);
      proposed.setName(spec.containsKey("name") ? requireStringOrNull(label, "name", spec) : name);

      if(spec.containsKey("url")) {
         proposed.setUrl(requireStringOrNull(label, "url", spec));
      }

      if(spec.containsKey("driver")) {
         proposed.setDriver(requireStringOrNull(label, "driver", spec));
      }

      if(spec.containsKey("defaultDatabase")) {
         proposed.setDefaultDatabase(requireStringOrNull(label, "defaultDatabase", spec));
      }

      if(spec.containsKey("tableName")) {
         proposed.setTableName(resolveTableName(label, spec.get("tableName")));
      }

      if(spec.containsKey("isolation")) {
         proposed.setIsolation(resolveIsolation(label, spec.get("isolation")));
      }

      if(spec.containsKey("ansiJoin")) {
         proposed.setAnsiJoin(requireBoolean(label, "ansiJoin", spec.get("ansiJoin")));
      }

      if(spec.containsKey("requireLogin")) {
         proposed.setRequireLogin(requireBoolean(label, "requireLogin", spec.get("requireLogin")));
      }

      if(spec.containsKey("user")) {
         proposed.setUser(requireStringOrNull(label, "user", spec));
      }

      if(spec.containsKey("useCredentialId")) {
         proposed.setUseCredentialId(requireBoolean(label, "useCredentialId", spec.get("useCredentialId")));
      }

      if(spec.containsKey("credentialID")) {
         proposed.setCredentialID(requireStringOrNull(label, "credentialID", spec));
      }

      if(!proposed.isRequireLogin()) {
         proposed.setPassword(null);
      }
      else if(spec.containsKey("password")) {
         String value = requireStringOrNull(label, "password", spec);
         requireNotMaskingLiteral(label + ".spec.password", value);
         proposed.setPassword(value);
      }
      else if(!current.isRequireLogin()) {
         throw new IllegalArgumentException(
            label + ".spec.password: required when enabling requireLogin on a data source that " +
            "did not previously require login -- there is no existing real password to preserve");
      }
      else {
         // Omitted: preserve the real current password, read fresh (never trust the masked DTO
         // field as an input to a write, section 0.1).
         proposed.setPassword(dataSourceService.getCurrentPassword(id, user));
      }

      requireCredentialCrossValidation(label, spec, proposed);
      return proposed;
   }

   /**
    * Section 0.1's second capture point: the before-DTO used for mid-apply rollback must ALSO
    * carry the real password when the data source currently requires login, or a rollback would
    * write the wrapped API's own masked literal into the live password field -- corrupting it a
    * second, independent way.
    */
   /** Package-visible for the same apply-time reuse reason as {@link #buildProposedJdbc}. */
   JdbcDataSourceProperties buildBeforeJdbc(String id, JdbcDataSourceProperties current,
                                            Principal user)
      throws Exception
   {
      JdbcDataSourceProperties before = copyJdbc(current);

      if(before.isRequireLogin()) {
         before.setPassword(dataSourceService.getCurrentPassword(id, user));
      }

      return before;
   }

   private static JdbcDataSourceProperties copyJdbc(JdbcDataSourceProperties src) {
      JdbcDataSourceProperties copy = new JdbcDataSourceProperties();
      copy.setId(src.getId());
      copy.setName(src.getName());
      copy.setUrl(src.getUrl());
      copy.setDriver(src.getDriver());
      copy.setDefaultDatabase(src.getDefaultDatabase());
      copy.setTableName(src.getTableName());
      copy.setIsolation(src.getIsolation());
      copy.setAnsiJoin(src.isAnsiJoin());
      copy.setRequireLogin(src.isRequireLogin());
      copy.setUser(src.getUser());
      copy.setPassword(src.getPassword());
      copy.setUseCredentialId(src.isUseCredentialId());
      copy.setCredentialID(src.getCredentialID());
      return copy;
   }

   private static void requireCredentialCrossValidation(String label, Map<String, Object> spec,
                                                         JdbcDataSourceProperties proposed)
   {
      if(proposed.isUseCredentialId()) {
         if(spec.containsKey("password") || spec.containsKey("user")) {
            throw new IllegalArgumentException(
               label + ".spec: useCredentialId=true requires credentialID, not password/user in " +
               "cleartext form (a field belonging to the other mode is refused loud, not silently " +
               "dropped)");
         }

         if(proposed.getCredentialID() == null || proposed.getCredentialID().isBlank()) {
            throw new IllegalArgumentException(
               label + ".spec.credentialID: required when useCredentialId=true");
         }
      }
      else if(spec.containsKey("credentialID")) {
         throw new IllegalArgumentException(
            label + ".spec: useCredentialId=false (or unset) does not accept credentialID; set " +
            "useCredentialId=true to use a stored credential instead");
      }
   }

   private static String resolveTableName(String label, Object raw) {
      String value = raw == null ? null : String.valueOf(raw).trim();

      for(String option : TABLE_NAME_OPTIONS) {
         if(option.equalsIgnoreCase(value)) {
            return option;
         }
      }

      throw new IllegalArgumentException(
         label + ".spec.tableName: must be one of " + TABLE_NAME_OPTIONS + ", got " + raw);
   }

   private static JdbcDataSourceProperties.IsolationLevel resolveIsolation(String label, Object raw) {
      String value = raw == null ? null : String.valueOf(raw).trim().toUpperCase();

      for(JdbcDataSourceProperties.IsolationLevel level :
          JdbcDataSourceProperties.IsolationLevel.values())
      {
         if(level.name().equals(value)) {
            return level;
         }
      }

      throw new IllegalArgumentException(
         label + ".spec.isolation: must be one of " +
         Arrays.toString(JdbcDataSourceProperties.IsolationLevel.values()) + ", got " + raw);
   }

   private static boolean requireBoolean(String label, String field, Object raw) {
      if(raw instanceof Boolean) {
         return (Boolean) raw;
      }

      if(raw instanceof String) {
         if("true".equalsIgnoreCase((String) raw)) {
            return true;
         }

         if("false".equalsIgnoreCase((String) raw)) {
            return false;
         }
      }

      throw new IllegalArgumentException(label + ".spec." + field + ": must be a boolean, got " + raw);
   }

   private static String requireStringOrNull(String label, String field, Map<String, Object> spec) {
      Object raw = spec.get(field);

      if(raw == null) {
         return null;
      }

      if(raw instanceof String) {
         return (String) raw;
      }

      throw new IllegalArgumentException(label + ".spec." + field + ": must be a string, got " + raw);
   }

   private static void requireNotMaskingLiteral(String field, String value) {
      if(WRAPPED_API_MASK.equals(value) || Util.PLACEHOLDER_PASSWORD.equals(value)) {
         throw new IllegalArgumentException(
            field + ": looks like a masked placeholder, not a real value -- omit this field to " +
            "leave the password unchanged, or supply the real new password to rotate it");
      }
   }

   private static void requireAllowedKeys(String label, Map<String, Object> spec, Set<String> allowed) {
      for(String key : spec.keySet()) {
         if(!allowed.contains(key)) {
            throw new IllegalArgumentException(
               label + ".spec." + key + ": unrecognized field for a jdbc-type data source " +
               "(allowed: " + allowed + ") -- refused rather than silently dropped");
         }
      }
   }

   /** Package-visible for the same apply-time reuse reason as {@link #buildProposedJdbc}: section
    * 1's tabular restriction (only {@code name} is editable through this API tier at all) applies
    * identically at preview and apply time, so both call this one method rather than re-deriving
    * the field allow-list independently. */
   TabularDataSourceProperties buildProposedTabular(String label, String name,
                                                     TabularDataSourceProperties current,
                                                     Map<String, Object> spec)
   {
      for(String key : spec.keySet()) {
         if(!"name".equals(key)) {
            throw new IllegalArgumentException(
               label + ".spec." + key + ": tabular data source connection configuration is not " +
               "editable through this API tier at all -- only \"name\" can be changed (section 1); " +
               "refused rather than silently accepted and dropped");
         }
      }

      TabularDataSourceProperties proposed = new TabularDataSourceProperties();
      proposed.setId(current.getId());
      proposed.setTabularType(current.getTabularType());
      proposed.setName(spec.containsKey("name") ? requireStringOrNull(label, "name", spec) : name);
      return proposed;
   }

   // ---------------------------------------------------------------- delete

   private PlanChange resolveDelete(String label, String name, DataSourceChangeRequest change,
                                    Principal user, Map<String, String> dependencyProjections)
      throws Exception
   {
      if(change.getSpec() != null) {
         throw new IllegalArgumentException(
            label + ".spec: not used for verb=delete; remove it or use verb=update");
      }

      if(change.getConfirmRename() != null) {
         throw new IllegalArgumentException(label + ".confirmRename: not used for verb=delete");
      }

      boolean force = Boolean.TRUE.equals(change.getForce());
      String id = resolveCurrentId(label, name, user);
      DataSourceProperties current = dataSourceService.getDataSource(id, user);
      DataSourceProperties before = current instanceof JdbcDataSourceProperties
         ? buildBeforeJdbc(id, (JdbcDataSourceProperties) current, user) : current;

      List<AssetObject> dependencies = findDependencies(name);
      requireForceIfDependent(label, name, dependencies, force);

      String beforeProjection = DataSourceProjection.project(before);
      dependencyProjections.put(name, DataSourceProjection.projectDependencies(dependencies));
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();
      String description = dependencies.isEmpty()
         ? "delete data source \"" + name + "\""
         : "delete data source \"" + name + "\" (force: true, " + dependencies.size() +
           " dependent asset(s))";
      return new PlanChange(name, orgId, beforeProjection, null, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, description);
   }

   /** Section 0.2: replicates {@code RepositoryObjectService.checkAssetEntryDependencies}'s own
    * check (community/core/.../content/repository/RepositoryObjectService.java:544-572) directly,
    * since {@code deleteDataSource}'s own {@code force} parameter never reaches this check at the
    * wrapped Public API tier at all. Package-visible so {@link DataSourceChangesetApplyService}
    * can re-run it at apply time (a concurrent change between preview and apply could add a new
    * dependency). */
   List<AssetObject> findDependencies(String name) {
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();
      AssetEntry entry =
         new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, name, null, orgId);
      List<AssetObject> dependencies = DependencyTool.getDependencies(entry.toIdentifier());
      return dependencies == null ? List.of() : dependencies;
   }

   /** Package-visible for the same apply-time re-check reason as {@link #findDependencies}. */
   static void requireForceIfDependent(String label, String name, List<AssetObject> dependencies,
                                       boolean force)
   {
      if(!dependencies.isEmpty() && !force) {
         throw new IllegalArgumentException(
            label + ": data source \"" + name + "\" is referenced by " + dependencies.size() +
            " other asset(s) (" + DataSourceProjection.projectDependencies(dependencies) + ") -- " +
            "refusing to delete without force: true (section 0.2: deleteDataSource's own force " +
            "parameter does not actually enforce this at the wrapped Public API tier, so this " +
            "area's own preflight does)");
      }
   }

   // ---------------------------------------------------------------- shared helpers

   static String requireVerb(String label, String verb) {
      if(verb != null) {
         String trimmed = verb.trim();

         if(DataSourceChangeRequest.VERB_UPDATE.equalsIgnoreCase(trimmed) ||
            "modify".equalsIgnoreCase(trimmed) || "edit".equalsIgnoreCase(trimmed))
         {
            return DataSourceChangeRequest.VERB_UPDATE;
         }

         if(DataSourceChangeRequest.VERB_DELETE.equalsIgnoreCase(trimmed) ||
            "remove".equalsIgnoreCase(trimmed))
         {
            return DataSourceChangeRequest.VERB_DELETE;
         }

         if(DataSourceChangeRequest.VERB_CREATE.equalsIgnoreCase(trimmed) ||
            "add".equalsIgnoreCase(trimmed))
         {
            return DataSourceChangeRequest.VERB_CREATE;
         }
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"update\", \"delete\", or \"create\" (\"modify\"/\"edit\", " +
         "\"remove\", and \"add\" accepted as aliases, respectively), got " + String.valueOf(verb));
   }

   private static String blankToNull(String value) {
      if(value == null) {
         return null;
      }

      String trimmed = value.trim();
      return trimmed.isEmpty() ? null : trimmed;
   }

   /** SHA-256 over the canonical plan. Same field-order/control-character contract as every prior
    * area's own {@code hash} method, extended with one input none of them needed in quite this
    * shape: the delete dependency-preflight projection (section 5) -- folded in per-property so a
    * concurrent change that adds a new dependency between preview and apply also perturbs the
    * hash.
    *
    * <p>Deliberately excludes {@code task}: it is a free-text, audit-only label (see {@link
    * DataSourceChangesetApplyService}'s {@code writeAudit} calls, its only use post-resolve) with
    * no bearing on what is actually mutated or verified, and the caller is never required to
    * replay it byte-for-byte between preview and apply. */
   private static String hash(List<PlanChange> changes,
                              Map<String, String> dependencyProjections)
   {
      StringBuilder canonical = new StringBuilder();

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonical(change.currentValue())).append(SEP)
            .append(canonical(change.proposedValue())).append(SEP)
            .append(change.risk()).append(SEP)
            .append(change.snapshotScope()).append(SEP);
         String depProjection = dependencyProjections.get(change.property());

         if(depProjection != null) {
            canonical.append("deps:").append(depProjection).append(SEP);
         }
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
         throw new IllegalStateException("SHA-256 is required to hash a data source change plan", e);
      }
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   /** The wrapped Public API's own masking literal (section 0.1/9) -- six characters, distinct
    * from {@link Util#PLACEHOLDER_PASSWORD}'s ten. Refused as input the same as the latter. */
   private static final String WRAPPED_API_MASK = "******";
   private static final List<String> TABLE_NAME_OPTIONS = List.of(
      JdbcDataSourceProperties.CATALOG_SCHEMA_OPTION, JdbcDataSourceProperties.SCHEMA_OPTION,
      JdbcDataSourceProperties.TABLE_OPTION, JdbcDataSourceProperties.DEFAULT_OPTION);
   private static final Set<String> JDBC_ALLOWED_FIELDS = Set.of(
      "name", "url", "driver", "defaultDatabase", "tableName", "isolation", "ansiJoin",
      "requireLogin", "user", "password", "useCredentialId", "credentialID");
   private final DataSourceService dataSourceService;
   private final DatabaseSettingsService databaseSettingsService;
}
