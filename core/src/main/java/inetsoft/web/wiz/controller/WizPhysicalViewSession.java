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
package inetsoft.web.wiz.controller;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.XUtil;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.service.database.PhysicalGraphService;
import inetsoft.web.wiz.controller.WizDataModelErrors.DmFailure;
import inetsoft.web.wiz.controller.WizDataModelWire.Err;
import inetsoft.web.wiz.controller.WizPhysicalViewDiff.*;
import inetsoft.web.wiz.controller.WizPhysicalViewMapper.*;
import inetsoft.web.wiz.controller.WizPhysicalViewWire.Warning;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.*;

/**
 * The one-request session of a physical view write (design 8.2). A physical view is edited in
 * the Portal through a RUNTIME copy held on the server under an id; the bridge never lets that id
 * out. {@link #create} and {@link #update} open (or create) the runtime, apply the ordered
 * operations of the diff through {@link PhysicalModelManagerService}, run the warnings check, and
 * save; the runtime is destroyed in a {@code finally} on every path, a dry run included, so no
 * request can leave one behind and no response can carry its id.
 *
 * <p>The runtime is opened through {@code RuntimePartitionService} directly, after the same
 * permission check as {@code PhysicalModelManagerService.openModel/createModel}: those two build
 * a DTO after the runtime is stored, and a failure in that step (the database metadata) would
 * leave a runtime whose id nobody holds. Here the id is held from the moment the runtime
 * exists. The save is the service's own ({@code createAndSaveModel}, {@code
 * updateAndSaveModel}), so its permission checks, audit record and dependency updates all run.</p>
 */
final class WizPhysicalViewSession {
   WizPhysicalViewSession(PhysicalModelManagerService manager, PhysicalModelService modelService,
                          RuntimePartitionService runtimes, PhysicalGraphService graphService,
                          DataSourceService dataSourceService, XRepository repository,
                          SecurityEngine securityEngine)
   {
      this.manager = manager;
      this.modelService = modelService;
      this.runtimes = runtimes;
      this.graphService = graphService;
      this.dataSourceService = dataSourceService;
      this.repository = repository;
      this.securityEngine = securityEngine;
   }

   /** What a write returns: the read-back view (absent on a dry run) and the warnings. */
   record Outcome(String name, String folder, View view, List<Warning> warnings) {
   }

   // -- stateless read (design 8.1) ------------------------------------------------------------

   /** The stored view in its canonical form; opens no runtime. */
   View read(String database, XDataModel dataModel, String parent, String name)
      throws Exception
   {
      XPartition partition = storedPartition(dataModel, parent, name);

      if(partition == null) {
         throw new FileNotFoundException(database + "/" + name);
      }

      PhysicalModelDefinition dto = modelService.createModel(database, dataModel, partition);
      return WizPhysicalViewMapper.fromDto(dto, partition, parent);
   }

   private static XPartition storedPartition(XDataModel dataModel, String parent, String name) {
      if(parent == null) {
         return dataModel.getPartition(name);
      }

      XPartition base = dataModel.getPartition(parent);
      return base == null ? null : base.getPartition(name);
   }

   private long lastModified(String database, String parent, String name) throws Exception {
      XDataModel fresh = repository.getDataModel(database);
      XPartition partition = fresh == null ? null : storedPartition(fresh, parent, name);
      return partition == null ? -1 : partition.getLastModified();
   }

   // -- create ---------------------------------------------------------------------------------

   /**
    * Creates a view from a declarative definition.
    *
    * @param base the base view of an extended view (its permission and the base elements were
    *             already checked by the caller), or null.
    */
   Outcome create(String database, XDataModel dataModel, String parent, Desired desired,
                  boolean dryRun, Principal principal)
      throws Exception
   {
      View want = desired.view();
      // planned before anything is opened: a definition that is inconsistent never costs a runtime
      View start = startOf(database, dataModel, parent, want);
      Plan plan = WizPhysicalViewDiff.plan(start, desired, true);
      String id = null;

      try {
         RuntimePartitionService.RuntimeXPartition runtime =
            createRuntime(database, dataModel, parent, want);
         id = runtime.getId();
         Run run = new Run(database, dataModel, parent, id, principal);
         run.apply(start, desired, plan);
         List<Warning> warnings = run.warnings();

         if(dryRun) {
            return new Outcome(want.name(), want.folder(), null, warnings);
         }

         PhysicalModelDefinition posted = run.posted(plan);
         manager.createAndSaveModel(database, parent == null ?
                                       WizPhysicalViewMapper.blankToNull(want.folder()) : null,
                                    parent, posted, principal);
         View stored = readBack(database, parent, want.name(), run.translate(want), desired);
         return new Outcome(want.name(), stored.folder(), stored, warnings);
      }
      catch(Exception e) {
         throw scrub(e, id);
      }
      finally {
         destroy(id);
      }
   }

   /**
    * What a new view starts as: empty, or for an extended view the base view's tables and joins,
    * all inherited. Description, folder and connection start as asked (the shell carries them).
    */
   private View startOf(String database, XDataModel dataModel, String parent, View want)
      throws Exception
   {
      String folder = WizPhysicalViewMapper.blankToNull(want.folder());

      if(parent == null) {
         return new View(want.name(), want.description(), folder, want.connection(), null,
                         List.of(), List.of());
      }

      View base = read(database, dataModel, null, parent);
      List<TableDef> tables = new ArrayList<>();
      List<JoinDef> joins = new ArrayList<>();
      base.tables().forEach(t -> tables.add(t.withBase(true)));
      base.joins().forEach(j -> joins.add(new JoinDef(
         j.fromTable(), j.fromColumn(), j.toTable(), j.toColumn(), j.type(), j.mergingRule(),
         j.weak(), j.priority(), j.cardinality(), true)));
      return new View(want.name(), want.description(), base.folder(),
                      want.connection() != null ? want.connection() : base.connection(), parent,
                      tables, joins);
   }

   private RuntimePartitionService.RuntimeXPartition createRuntime(
      String database, XDataModel dataModel, String parent, View want)
      throws Exception
   {
      PhysicalModelDefinition shell = new PhysicalModelDefinition();
      shell.setName(want.name());
      shell.setFolder(parent == null ? WizPhysicalViewMapper.blankToNull(want.folder()) : null);
      shell.setDescription(want.description());
      shell.setConnection(want.connection());
      shell.setTables(new ArrayList<>());
      XPartition partition = modelService.createPartition(shell);
      partition.setDataModel(dataModel);

      if(parent != null) {
         XPartition base = dataModel.getPartition(parent);

         if(base == null) {
            throw new FileNotFoundException(database + "/" + parent);
         }

         partition.setBaseParitition(base);

         if(want.connection() == null) {
            partition.setConnection(base.getConnection());
         }
      }

      partition = (XPartition) partition.deepClone(true);
      return runtimes.createModel(partition, database, graphService::shrinkColumnHeight);
   }

   // -- update ---------------------------------------------------------------------------------

   /**
    * Applies a submitted definition to a stored view. A definition equal to the stored view
    * (a {@code get} result fed back) opens no runtime and writes nothing.
    */
   Outcome update(String database, XDataModel dataModel, String parent, String name,
                  Desired desired, boolean dryRun, Principal principal)
      throws Exception
   {
      View stored = read(database, dataModel, parent, name);
      long openedAt = lastModified(database, parent, name);
      Plan plan = WizPhysicalViewDiff.plan(stored, desired, false);

      if(plan.isEmpty()) {
         return new Outcome(name, stored.folder(), stored, List.of());
      }

      String id = null;

      try {
         RuntimePartitionService.RuntimeXPartition runtime =
            runtimes.openModel(dataModel, name, parent, graphService::shrinkColumnHeight);

         if(runtime == null) {
            throw new FileNotFoundException(database + "/" + name);
         }

         id = runtime.getId();
         Run run = new Run(database, dataModel, parent, id, principal);
         run.apply(stored, desired, plan);
         List<Warning> warnings = run.warnings();

         if(dryRun) {
            return new Outcome(name, desired.hasFolder() ? desired.view().folder() :
                                  stored.folder(), null, warnings);
         }

         // the stored view moved on since it was read: refuse before anything is written
         if(lastModified(database, parent, name) != openedAt) {
            throw new DmFailure(
               WizDataModelWire.STALE_VIEW, "name",
               "the view changed while the edit was being applied; nothing was saved. " +
                  "Read it again and repeat");
         }

         PhysicalModelDefinition posted = run.posted(plan);
         String folder = desired.hasFolder() ? desired.view().folder() : stored.folder();
         manager.updateAndSaveModel(database, folder, parent, name, posted, principal);
         View want = run.translate(withStoredDefaults(desired.view(), stored));
         View back = readBack(database, parent, name, want, desired);
         return new Outcome(name, back.folder(), back, warnings);
      }
      catch(Exception e) {
         throw scrub(e, id);
      }
      finally {
         destroy(id);
      }
   }

   private static View withStoredDefaults(View want, View stored) {
      return new View(stored.name(), want.description(), want.folder(), want.connection(),
                      stored.parent(), want.tables(), want.joins());
   }

   // -- shared ---------------------------------------------------------------------------------

   private View readBack(String database, String parent, String name, View want, Desired desired)
      throws Exception
   {
      XDataModel fresh = repository.getDataModel(database);

      if(storedPartition(fresh, parent, name) == null) {
         throw new DmFailure(
            WizDataModelWire.READ_BACK_FAILED, "definition",
            "the server reported success but \"" + database + "/" + name + "\" is not there");
      }

      View stored = read(database, fresh, parent, name);
      String problem = WizPhysicalViewDiff.mismatch(want, desired, stored);

      if(problem != null) {
         throw new DmFailure(
            WizDataModelWire.READ_BACK_FAILED, "definition",
            "the server reported success but \"" + database + "/" + name +
               "\" is not as written: " + problem);
      }

      return stored;
   }

   /** Destroys the runtime; never masks the error that is already on its way out. */
   private void destroy(String id) {
      if(id == null) {
         return;
      }

      try {
         manager.closeModel(id);
      }
      catch(Throwable t) {
         // the runtime also expires on its own after three minutes
         LOG.warn("Failed to destroy a physical view runtime ({})", t.getClass().getName());
      }
   }

   /**
    * An exception whose text carries the runtime id (several services put it in the message of
    * an expired-runtime error) becomes the one business outcome; the id never leaves.
    */
   private static Exception scrub(Exception e, String id) {
      if(id == null) {
         return e;
      }

      for(Throwable t = e; t != null; t = t.getCause()) {
         if(t.getMessage() != null && t.getMessage().contains(id)) {
            return new DmFailure(WizDataModelWire.UNKNOWN,
                                 "the edit session expired; nothing was saved");
         }
      }

      return e;
   }

   /** The state of one request's operations on one runtime. */
   private final class Run {
      Run(String database, XDataModel dataModel, String parent, String id, Principal principal) {
         this.database = database;
         this.dataModel = dataModel;
         this.parent = parent;
         this.id = id;
         this.principal = principal;
      }

      void apply(View start, Desired desired, Plan plan) throws Exception {
         View want = desired.view();

         for(TableDef table : start.tables()) {
            current.put(table.name(), table);
         }

         renamedFrom.putAll(plan.renamedFrom());

         if(plan.folderChanged || plan.descriptionChanged || plan.connectionChanged) {
            applyMetadata(want, plan);
         }

         // 1. joins that are gone
         for(JoinDef join : plan.removeJoins) {
            manager.removeJoin(id, WizPhysicalViewMapper.toJoinModel(join), null,
                               join.fromTable());
         }

         // 2. tables and aliases that are gone
         for(TableDef table : plan.removeTables) {
            manager.removeTable(id, table.name(), table.name());
            current.remove(table.name());
         }

         // 3. aliases and inline views renamed (the alias rule of design 8.4 carries oldName)
         for(Rename rename : plan.renames) {
            requireValidAlias(rename.to().name(), rename.from().name(), rename.to().name());
            PhysicalTableModel model = new PhysicalTableModel();
            model.setAlias(rename.to().name());
            manager.updateAlias(id, rename.from().name(), model);
            current.remove(rename.from().name());
            current.put(rename.to().name(), rename.to());
         }

         for(Rename rename : plan.updateViews) {
            if(!rename.from().name().equals(rename.to().name())) {
               requireValidAlias(rename.to().name(), rename.from().name(), rename.to().name());
            }

            manager.updateInlineView(id, rename.from().name(), viewModel(rename.to()));
            current.remove(rename.from().name());
            current.put(rename.to().name(), rename.to());
         }

         // 4. plain tables, always through addTable(fixBounds=true) so the server lays them out
         for(TableDef table : plan.addPlain) {
            addPlain(table);
         }

         // 5. inline views, then aliases (an alias may alias a view)
         for(TableDef table : plan.addViews) {
            requireValidAlias(table.name(), null, table.name());
            manager.createInlineView(id, viewModel(table));
            requireTable(table.name());
            current.put(table.name(), table);
         }

         for(TableDef table : plan.addAliases) {
            addAlias(table);
         }

         // 6. joins
         applyJoins(plan);
      }

      private void applyMetadata(View want, Plan plan) {
         runtimes.update(id, rp -> {
            XPartition partition = rp.getPartition();

            if(plan.folderChanged) {
               partition.setFolder(WizPhysicalViewMapper.blankToNull(want.folder()));
            }

            if(plan.descriptionChanged) {
               partition.setDescription(WizPhysicalViewMapper.blankToNull(want.description()));
            }

            if(plan.connectionChanged) {
               partition.setConnection(WizPhysicalViewMapper.blankToNull(want.connection()));
            }

            return null;
         });
      }

      private void addPlain(TableDef table) throws Exception {
         XPartition partition = runtimes.getPartition(id);

         if(partition == null) {
            throw expired();
         }

         String connection = partition.getConnection();
         String additional = connection == null || connection.isEmpty() ?
            XUtil.OUTER_MOSE_LAYER_DATABASE : connection;
         DefaultMetaDataProvider provider =
            modelService.getMetaDataProvider0(database, dataModel, additional);
         XNode node = table.schema() != null || table.catalog() != null ?
            provider.getTable(table.catalog(), table.schema(), table.name(), additional, true) :
            provider.getTable(table.name(), additional);

         if(node == null) {
            throw invalid("tables[" + table.name() + "].name", "TABLE_NOT_FOUND",
                          "no table \"" + table.name() + "\" in the database" +
                             (table.schema() == null ? "" : " (schema " + table.schema() + ")") +
                             "; list the tables with get_physical_view_tables or the data " +
                             "source's metadata");
         }

         String catalog = text(node.getAttribute("catalog"));
         String schema = text(node.getAttribute("schema"));
         boolean supportCatalog = "true".equals(text(node.getAttribute("supportCatalog")));
         String effective;

         try {
            effective = manager.addTable(id, node.getName(), catalog, schema,
                                         database + "/TABLE/" + node.getName(), null, null, null,
                                         supportCatalog, true);
         }
         catch(FileNotFoundException e) {
            throw invalid("tables[" + table.name() + "].name", "TABLE_NOT_FOUND",
                          "no table \"" + table.name() + "\" in the database");
         }

         if(effective == null) {
            throw expired();
         }

         if(!effective.equals(table.name())) {
            nameMap.put(table.name(), effective);
            warnings.add(new Warning(
               "TABLE_NAME_RESOLVED", "table \"" + table.name() + "\" is stored as \"" +
               effective + "\"", table.name(), effective));
         }

         current.put(effective, new TableDef(effective, Kind.PLAIN, schema, catalog, null, null,
                                             null, false));
      }

      private void addAlias(TableDef table) throws Exception {
         String source = translateName(table.aliasOf());
         TableDef root = rootOf(source);

         if(root == null) {
            throw invalid("tables[" + table.name() + "].aliasOf", "ALIAS_SOURCE_UNKNOWN",
                          "no table named \"" + table.aliasOf() + "\" in this view to alias");
         }

         PhysicalTableModel model = new PhysicalTableModel();
         model.setName(table.name());
         model.setQualifiedName(source);
         model.setAlias(table.name());
         model.setAliasSource(source);
         model.setJoins(new ArrayList<>());
         model.setAutoAliases(new ArrayList<>());
         model.setAutoAliasesEnabled(false);

         if(root.kind() == Kind.VIEW) {
            model.setType(TableType.VIEW);
            model.setSql(root.sql());
         }
         else {
            model.setType(TableType.PHYSICAL);
            model.setCatalog(root.catalog());
            model.setSchema(root.schema());
         }

         String message = manager.createAlias(id, model);

         if(message != null) {
            throw invalid("tables[" + table.name() + "].name", "ALIAS_INVALID", message);
         }

         requireTable(table.name());
         current.put(table.name(), new TableDef(table.name(), Kind.ALIAS, null, null, source,
                                                null, null, false));
      }

      private TableDef rootOf(String name) {
         TableDef table = current.get(name);

         for(int i = 0; table != null && table.kind() == Kind.ALIAS && i < 64; i++) {
            table = current.get(table.aliasOf());
         }

         return table;
      }

      private PhysicalTableModel viewModel(TableDef table) {
         PhysicalTableModel model = new PhysicalTableModel();
         model.setName(table.name());
         model.setQualifiedName(table.name());
         model.setType(TableType.VIEW);
         model.setSql(table.sql());
         model.setJoins(new ArrayList<>());
         model.setAutoAliases(new ArrayList<>());
         model.setAutoAliasesEnabled(false);
         return model;
      }

      private void requireValidAlias(String name, String oldName, String field) {
         String message = manager.checkAliasValid(id, name, oldName);

         if(message != null) {
            throw invalid("tables[" + field + "].name", "ALIAS_INVALID", message);
         }
      }

      private void requireTable(String name) {
         XPartition partition = runtimes.getPartition(id);

         if(partition == null) {
            throw expired();
         }

         if(!partition.containsTable(name)) {
            throw invalid("tables[" + name + "]", "TABLE_INVALID",
                          "the server did not add the table \"" + name + "\"");
         }
      }

      private void applyJoins(Plan plan) throws Exception {
         List<JoinDef> toAdd = new ArrayList<>();

         for(JoinDef join : plan.addJoins) {
            toAdd.add(translate(join));
         }

         List<JoinChange> toChange = new ArrayList<>();

         for(JoinChange change : plan.changeJoins) {
            toChange.add(new JoinChange(change.stored(), translate(change.desired())));
         }

         for(JoinDef join : toAdd) {
            checkColumns(join);
         }

         for(JoinChange change : toChange) {
            checkColumns(change.desired());
         }

         for(JoinChange change : toChange) {
            JoinDef old = change.stored();
            JoinDef now = change.desired();
            JoinDef merged = new JoinDef(
               now.fromTable(), now.fromColumn(), now.toTable(), now.toColumn(),
               now.type() != null ? now.type() : old.type(),
               now.mergingRule() != null ? now.mergingRule() : old.mergingRule(),
               now.weak() != null ? now.weak() : old.weak(),
               now.priority() != null ? now.priority() : old.priority(),
               now.cardinality() != null ? now.cardinality() : old.cardinality(), false);
            manager.updateJoin(id, WizPhysicalViewMapper.toJoinModel(merged),
                               WizPhysicalViewMapper.toJoinModel(old), merged.fromTable());
         }

         for(JoinDef join : toAdd) {
            JoinModel model = WizPhysicalViewMapper.toJoinModel(join);

            if(model.getCardinality() == null) {
               if(probes < MAX_CARDINALITY_PROBES) {
                  probes++;
                  detectCardinality(join, model);
               }
               else {
                  notDetected++;
               }
            }

            manager.addJoin(id, model, join.fromTable());
         }

         if(notDetected > 0) {
            warnings.add(new Warning(
               "CARDINALITY_NOT_DETECTED", notDetected + " join(s) without a cardinality " +
               "beyond the first " + MAX_CARDINALITY_PROBES + " kept the server default " +
               "(MANY_TO_MANY); send cardinality to set it", "", ""));
         }
      }

      /** The server's own cardinality rule; on failure MANY_TO_MANY with a warning. */
      private void detectCardinality(JoinDef join, JoinModel model) throws Exception {
         try {
            RuntimePartitionService.RuntimeXPartition runtime = runtimes.getRuntimePartition(id);
            CardinalityHelper helper = new CardinalityHelper();
            helper.setTable(join.fromTable());
            helper.setJoin(model);
            helper.setModel(modelService.createModel(dataModel, runtime));
            String connection = runtime.getPartition().getConnection();
            modelService.getCardinality(database, connection, helper, principal);
         }
         catch(SecurityException e) {
            throw e;
         }
         catch(Exception e) {
            LOG.warn("The cardinality of a join could not be detected ({})",
                     e.getClass().getName());
         }

         if(model.getCardinality() == null) {
            model.setCardinality(JoinCardinality.MANY_TO_MANY);
            warnings.add(new Warning(
               "CARDINALITY_DEFAULTED", "the cardinality of the join " + join.label() +
               " could not be detected and was set to MANY_TO_MANY; send cardinality to set it",
               join.fromTable(), join.toTable()));
         }
      }

      private void checkColumns(JoinDef join) throws Exception {
         verifyColumn(join.fromTable(), join.fromColumn(),
                      "joins[" + join.label() + "].from.column");
         verifyColumn(join.toTable(), join.toColumn(), "joins[" + join.label() + "].to.column");
      }

      private void verifyColumn(String table, String column, String field) throws Exception {
         List<String> list = columns.computeIfAbsent(table, t -> {
            try {
               return manager.loadTableColumns(database, id, t, false);
            }
            catch(RuntimeException e) {
               throw e;
            }
            catch(Exception e) {
               throw new DmFailure(
                  WizDataModelWire.METADATA_UNAVAILABLE, field,
                  "the database metadata of table \"" + t + "\" could not be read, so the " +
                     "join columns cannot be verified");
            }
         });

         if(list == null || list.isEmpty()) {
            throw invalid(field, "METADATA_UNAVAILABLE",
                          "no columns could be read from table \"" + table + "\" (for an " +
                             "inline view, check its SQL)");
         }

         if(!list.contains(column)) {
            throw invalid(field, "COLUMN_NOT_IN_TABLE", "table \"" + table + "\" has no column \"" +
               column + "\" (its columns: " + String.join(", ", list) + ")");
         }
      }

      /** The warnings check of {@code PhysicalModelController.getWarnings}. */
      List<Warning> warnings() {
         XPartition partition = runtimes.getPartition(id);

         if(partition == null) {
            throw expired();
         }

         partition = partition.applyAutoAliases();
         int status = partition.getStatus();

         if(status == XPartition.UNJOINED) {
            String[] unjoined = partition.getUnjoinedTables();
            String t1 = unjoined != null && unjoined.length > 0 ? unjoined[0] : "";
            String t2 = unjoined != null && unjoined.length > 1 ? unjoined[1] : "";
            boolean allowed;

            try {
               allowed = securityEngine.checkPermission(
                  principal, ResourceType.CROSS_JOIN, "*", ResourceAction.ACCESS);
            }
            catch(Exception e) {
               allowed = false;
            }

            if(!allowed) {
               throw new DmFailure(
                  WizDataModelWire.UNJOINED_TABLES_PROHIBITED, "joins",
                  "the view has unjoined tables " + t1 + ", " + t2 +
                     " and the caller lacks the cross-join permission");
            }

            warnings.add(new Warning("UNJOINED_TABLES", "the view has unjoined tables " + t1 +
               ", " + t2 + ": queries over them are a cross join", t1, t2));
         }
         else if(status == XPartition.CYCLE) {
            warnings.add(new Warning("CYCLE", "the joins of the view form a cycle", "", ""));
         }

         return new ArrayList<>(warnings);
      }

      /**
       * The definition the save is given: the runtime's own tables with the alias rule of design
       * 8.4 (the save rewrites logical model attributes by {@code oldAlias -> alias}): a plain
       * table has neither, an alias has {@code oldAlias == alias} except a genuine rename, which
       * carries the previous name; a renamed inline view carries the same pair.
       */
      PhysicalModelDefinition posted(Plan plan) throws Exception {
         RuntimePartitionService.RuntimeXPartition runtime = runtimes.getRuntimePartition(id);

         if(runtime == null || runtime.getPartition() == null) {
            throw expired();
         }

         PhysicalModelDefinition posted = modelService.createModel(dataModel, runtime);

         for(PhysicalTableModel table : posted.getTables()) {
            String name = table.getAlias() != null ? table.getAlias() : table.getQualifiedName();
            String previous = renamedFrom.get(name);

            if(previous != null) {
               table.setAlias(name);
               table.setOldAlias(previous);
            }
            else if(table.getAlias() != null) {
               table.setOldAlias(table.getAlias());
            }
            else {
               table.setOldAlias(null);
            }
         }

         return posted;
      }

      /** The submitted view with plain table names replaced by the names the server gave. */
      View translate(View view) {
         if(nameMap.isEmpty()) {
            return view;
         }

         List<TableDef> tables = new ArrayList<>();

         for(TableDef table : view.tables()) {
            tables.add(new TableDef(
               translateName(table.name()), table.kind(), table.schema(), table.catalog(),
               table.aliasOf() == null ? null : translateName(table.aliasOf()), table.sql(),
               table.oldName(), table.baseElement()));
         }

         List<JoinDef> joins = new ArrayList<>();

         for(JoinDef join : view.joins()) {
            joins.add(translate(join));
         }

         return new View(view.name(), view.description(), view.folder(), view.connection(),
                         view.parent(), tables, joins);
      }

      private JoinDef translate(JoinDef join) {
         return new JoinDef(translateName(join.fromTable()), join.fromColumn(),
                            translateName(join.toTable()), join.toColumn(), join.type(),
                            join.mergingRule(), join.weak(), join.priority(), join.cardinality(),
                            join.baseElement());
      }

      private String translateName(String name) {
         return nameMap.getOrDefault(name, name);
      }

      private final String database;
      private final XDataModel dataModel;
      private final String parent;
      private final String id;
      private final Principal principal;
      private final Map<String, TableDef> current = new LinkedHashMap<>();
      private final Map<String, String> nameMap = new LinkedHashMap<>();
      private final Map<String, String> renamedFrom = new LinkedHashMap<>();
      private final Map<String, List<String>> columns = new HashMap<>();
      private final List<Warning> warnings = new ArrayList<>();
      private int probes;
      private int notDetected;
   }

   private static DmFailure invalid(String field, String code, String message) {
      return WizPhysicalViewMapper.invalid(List.of(new Err(field, message, code)));
   }

   private static DmFailure expired() {
      return new DmFailure(WizDataModelWire.UNKNOWN,
                           "the edit session expired; nothing was saved");
   }

   private static String text(Object value) {
      return value == null ? null : value.toString();
   }

   /** The most joins of one request whose cardinality is looked up in the database. */
   static final int MAX_CARDINALITY_PROBES = 100;

   private final PhysicalModelManagerService manager;
   private final PhysicalModelService modelService;
   private final RuntimePartitionService runtimes;
   private final PhysicalGraphService graphService;
   private final DataSourceService dataSourceService;
   private final XRepository repository;
   private final SecurityEngine securityEngine;

   private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(WizPhysicalViewSession.class);
}
