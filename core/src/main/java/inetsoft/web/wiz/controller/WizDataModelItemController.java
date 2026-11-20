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
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.DependencyException;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.LogicalModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

import static inetsoft.web.wiz.controller.WizDataModelSupport.*;
import static inetsoft.web.wiz.controller.WizDataModelWire.*;

/**
 * Dependents, delete and rename of physical views, logical models, VPMs and (for dependents and
 * delete) data model folders. The per-kind work goes through {@link WizModelKindOps}; a kind with
 * no implementation in this build answers {@code UNKNOWN: not available in this build}.
 *
 * <p>A delete never applies when its precheck fails for the item, and a folder delete is bound to
 * the exact set of models it contained at the dry run ({@code expect}). Batch routes report a
 * denial per item; {@code /rename} is a single-object route and throws {@code SecurityException}
 * (a 403). Every write is read back. Never declares a local {@code @ExceptionHandler}.</p>
 */
@RestController
@RequestMapping("/api/wiz/data-models")
public class WizDataModelItemController {
   @Autowired
   public WizDataModelItemController(List<WizModelKindOps> kindOps,
                                     DataModelFolderManagerService folderManagerService,
                                     DataSourceService dataSourceService,
                                     LogicalModelService logicalModelService,
                                     XRepository repository)
   {
      this.kindOps = kindOps;
      this.folderManagerService = folderManagerService;
      this.dataSourceService = dataSourceService;
      this.logicalModelService = logicalModelService;
      this.repository = repository;
      this.source = new WizDataModelSource(repository);
      this.gates = new WizDataModelGates(dataSourceService, logicalModelService);
   }

   @PostMapping(value = "/dependencies", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object dependencies(@RequestBody DependenciesRequest request, Principal principal)
      throws Exception
   {
      String database = requireText("database", request.database());
      List<Ref> refs = requireRefs("items", request.items());

      return WizDataModelErrors.run(() -> {
         XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);
         WizModelKindOps.Ctx ctx = new WizModelKindOps.Ctx(database, dataModel, principal);
         List<DepItem> items = new ArrayList<>();

         for(Ref ref : refs) {
            items.add(depItem(ctx, ref));
         }

         return new DependenciesResponse(items, COVERAGE);
      });
   }

   @PostMapping(value = "/delete", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object delete(@RequestBody DeleteRequest request, Principal principal)
      throws Exception
   {
      String database = requireText("database", request.database());

      if(request.items() == null || request.items().isEmpty()) {
         throw new IllegalArgumentException("items is required");
      }

      List<DeleteItem> items = request.items();

      for(int i = 0; i < items.size(); i++) {
         DeleteItem item = items.get(i);
         requireText("items[" + i + "].name", item == null ? null : item.name());
         requireKind("items[" + i + "].kind", item.kind());

         if(KIND_FOLDER.equals(item.kind()) && item.expect() == null) {
            throw new IllegalArgumentException(
               "items[" + i + "].expect is required for a folder: the models it contained at " +
               "the dry run");
         }
      }

      return WizDataModelErrors.run(() -> WizDataModelLocks.withDatabase(
         database, () -> doDelete(database, items, principal)));
   }

   @PostMapping(value = "/rename", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object rename(@RequestBody RenameRequest request, Principal principal)
      throws Exception
   {
      String database = requireText("database", request.database());
      String kind = requireKind("kind", request.kind());
      String name = requireText("name", request.name());
      String newName = requireText("newName", request.newName());

      if(KIND_FOLDER.equals(kind)) {
         throw new IllegalArgumentException(
            "kind: a folder is renamed through folders/rename, not rename");
      }

      WizDataModelNames.requireValidModelName(newName);

      return WizDataModelErrors.run(() -> WizDataModelLocks.withDatabase(database, () -> {
         XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);
         // an item the caller may not see is answered like a missing one: no kind, no clash text
         String existing = gates.visibleKindOfName(dataModel, database, name, principal);

         if(existing == null) {
            throw WizDataModelGates.notFound(kind, "name", database, name);
         }

         if(!existing.equals(kind)) {
            throw new WizDataModelErrors.DmFailure(
               WRONG_KIND, "kind", "\"" + name + "\" is a " + existing + ", not a " + kind,
               null, existing);
         }

         WizModelKindOps ops = opsFor(kind);

         if(ops == null) {
            throw unavailable(kind);
         }

         WizModelKindOps.Ctx ctx = new WizModelKindOps.Ctx(database, dataModel, principal);
         WizModelKindOps.ModelRef ref = new WizModelKindOps.ModelRef(toKind(kind), name, null);
         WizModelKindOps.ModelInfo info = ops.find(ctx, ref).orElseThrow(() ->
            WizDataModelGates.notFound(kind, "name", database, name));

         // the permission to rename first, then the name checks: a caller who may not rename the
         // item learns nothing about which names are taken
         if(!ops.mayRename(ctx, ref)) {
            throw new SecurityException(
               "Unauthorized access to resource \"" + database + "/" +
                  (info.folder() == null || info.folder().isEmpty() ? "" : info.folder() + "/") +
                  name + "\" by user " + principal);
         }

         if(name.equals(newName)) {
            throw new WizDataModelErrors.DmFailure(
               INVALID, "newName", "newName is the current name; nothing to rename");
         }

         WizDataModelNames.requireUniqueInSource(
            dataModel, newName, (k, n) -> gates.maySee(dataModel, database, k, n, principal));

         // the services overwrite the description with their argument: pass the stored one
         String description = request.description() != null ? request.description() :
            info.description();
         WizModelKindOps.OpResult result = ops.rename(ctx, ref, newName, description);

         if(!result.ok()) {
            throw new WizDataModelErrors.DmFailure(
               result.reason() == null ? UNKNOWN : result.reason(), "name", result.message());
         }

         XDataModel after = repository.getDataModel(database);

         if(!kind.equals(kindOfName(after, newName)) || kindOfName(after, name) != null) {
            throw new WizDataModelErrors.DmFailure(
               READ_BACK_FAILED, "newName", "the server reported success but \"" + newName +
               "\" is not stored as a " + kind + " or \"" + name + "\" still exists");
         }

         return new RenameResponse(true, newName, storedFolder(after, kind, newName));
      }));
   }

   // -- dependencies ------------------------------------------------------------------------

   private DepItem depItem(WizModelKindOps.Ctx ctx, Ref ref) throws Exception {
      XDataModel dataModel = ctx.dataModel();
      String database = ctx.database();

      if(KIND_FOLDER.equals(ref.kind())) {
         return folderDepItem(ctx, ref);
      }

      // an item the caller may not see is answered like a missing one, naming no kind
      String existing = gates.visibleKindOfName(dataModel, database, ref.name(), ctx.principal());
      WizModelKindOps ops = opsFor(ref.kind());

      if(existing == null) {
         return missing(ref, NOT_FOUND, "no " + ref.kind() + " named \"" + ref.name() + "\" in " +
            database);
      }

      if(!existing.equals(ref.kind())) {
         return missing(ref, WRONG_KIND, "\"" + ref.name() + "\" is a " + existing + ", not a " +
            ref.kind());
      }

      if(ops == null) {
         return new DepItem(ref.kind(), ref.name(), storedFolder(dataModel, ref.kind(), ref.name()),
                            existing != null, false, UNKNOWN, "not available in this build",
                            List.of(), "", List.of(), List.of(), List.of());
      }

      WizModelKindOps.ModelRef modelRef =
         new WizModelKindOps.ModelRef(toKind(ref.kind()), ref.name(), ref.parent());
      Optional<WizModelKindOps.ModelInfo> info = ops.find(ctx, modelRef);

      if(info.isEmpty()) {
         return missing(ref, NOT_FOUND, "no " + ref.kind() + " named \"" + ref.name() + "\" in " +
            database);
      }

      List<Dependent> dependents =
         visibleOnly(ctx, wire(ops.dependents(ctx, modelRef)));
      List<AlsoDeleted> also = new ArrayList<>();
      List<Dangling> dangling = new ArrayList<>();

      if(!info.get().extended()) {
         also.addAll(extendedChildren(dataModel, ref));
      }

      if(KIND_PHYSICAL_VIEW.equals(ref.kind())) {
         for(Dependent user : visibleOnly(ctx, viewUsers(dataModel, database, ref.name()), false)) {
            dangling.add(new Dangling(user.type(), user.name(), "uses physical view " +
               ref.name()));
         }
      }

      return new DepItem(ref.kind(), ref.name(), info.get().folder(), true,
                         ops.mayDelete(ctx, modelRef), null, null, dependents, describe(dependents),
                         List.of(), also, dangling);
   }

   private DepItem folderDepItem(WizModelKindOps.Ctx ctx, Ref ref) throws Exception {
      XDataModel dataModel = ctx.dataModel();
      String database = ctx.database();
      String folder = ref.name();

      if(!gates.maySee(dataModel, database, KIND_FOLDER, folder, ctx.principal())) {
         return missing(ref, NOT_FOUND, "no folder named \"" + folder + "\" in " + database);
      }

      // a denied answer carries nothing but the denial
      if(!dataSourceService.checkPermission(database, folder, ResourceAction.DELETE,
                                            ctx.principal()))
      {
         return new DepItem(KIND_FOLDER, folder, null, true, false, PERMISSION_DENIED,
                            "the caller lacks DELETE on data model folder \"" + database + "/" +
                            folder + "\"", List.of(), "", List.of(), List.of(), List.of());
      }

      List<Ref> contains = new ArrayList<>();

      for(Ref contained : contents(dataModel, folder)) {
         if(gates.maySee(dataModel, database, contained.kind(), contained.name(),
                         ctx.principal()))
         {
            contains.add(contained);
         }
      }

      List<Object> outer = new ArrayList<>();

      for(XLogicalModel model : logicalModelsIn(dataModel, folder)) {
         Object[] entries = DependencyException.getExistingDependencies(model.getOuterDependencies());

         if(entries != null) {
            outer.addAll(Arrays.asList(entries));
         }
      }

      List<Dangling> dangling = new ArrayList<>();
      List<AlsoDeleted> also = new ArrayList<>();

      for(Ref contained : contains) {
         also.addAll(extendedChildren(dataModel, contained));

         if(KIND_PHYSICAL_VIEW.equals(contained.kind())) {
            for(Dependent user : visibleOnly(ctx, viewUsers(dataModel, database, contained.name()), false)) {
               boolean inside = KIND_LOGICAL_MODEL.equals(user.type()) &&
                  Objects.equals(folder, storedFolder(dataModel, KIND_LOGICAL_MODEL, user.name()));

               if(!inside) {
                  dangling.add(new Dangling(user.type(), user.name(),
                                            "uses physical view " + contained.name()));
                  outer.add(KIND_LOGICAL_MODEL.equals(user.type()) ?
                               dataModel.getLogicalModel(user.name()) :
                               dataModel.getVirtualPrivateModel(user.name()));
               }
            }
         }
      }

      List<Dependent> dependents = visibleOnly(ctx, toDependents(database, outer));

      return new DepItem(KIND_FOLDER, folder, null, true,
                         containedDeletable(ctx, folder, contents(dataModel, folder)),
                         null, null, dependents, describe(dependents), contains, also, dangling);
   }

   private boolean containedDeletable(WizModelKindOps.Ctx ctx, String folder, List<Ref> contains)
      throws Exception
   {
      return containedBlockers(ctx, folder, contains).isEmpty();
   }

   /**
    * What inside the folder stops the caller from deleting it: the names of visible items the
    * caller may not delete (DELETE on a model, WRITE on a view), and a count of items the caller
    * may not see at all (never named: a caller cannot delete what it cannot see).
    */
   private List<String> containedBlockers(WizModelKindOps.Ctx ctx, String folder,
                                          List<Ref> contains)
      throws Exception
   {
      List<String> blockers = new ArrayList<>();
      String database = ctx.database();
      int hidden = 0;

      for(Ref contained : contains) {
         if(!gates.maySee(ctx.dataModel(), database, contained.kind(), contained.name(),
                          ctx.principal()))
         {
            hidden++;
            continue;
         }

         boolean allowed = KIND_LOGICAL_MODEL.equals(contained.kind()) ?
            logicalModelService.checkPermission(database, folder, contained.name(), null,
                                                ResourceAction.DELETE, ctx.principal()) :
            dataSourceService.checkPermission(database, folder, ResourceAction.WRITE,
                                              ctx.principal());

         if(!allowed) {
            blockers.add(contained.name());
         }
      }

      if(hidden > 0) {
         blockers.add(hidden + " item(s) the caller may not see");
      }

      return blockers;
   }

   /** Drops the models, views and VPMs from a dependent list that the caller may not see. */
   private List<Dependent> visibleOnly(WizModelKindOps.Ctx ctx, List<Dependent> dependents) {
      return visibleOnly(ctx, dependents, true);
   }

   /**
    * @param placeholder whether the dropped items leave one nameless entry, so a delete that must
    *                    be forced because of them still is
    */
   private List<Dependent> visibleOnly(WizModelKindOps.Ctx ctx, List<Dependent> dependents,
                                       boolean placeholder)
   {
      List<Dependent> visible = new ArrayList<>();
      int hidden = 0;

      for(Dependent dependent : dependents) {
         String kind = switch(dependent.type()) {
            case KIND_LOGICAL_MODEL -> KIND_LOGICAL_MODEL;
            case KIND_PHYSICAL_VIEW -> KIND_PHYSICAL_VIEW;
            case KIND_VPM -> KIND_VPM;
            default -> null;
         };

         if(kind == null || gates.maySee(ctx.dataModel(), ctx.database(), kind, dependent.name(),
                                         ctx.principal()))
         {
            visible.add(dependent);
         }
         else {
            hidden++;
         }
      }

      if(placeholder && hidden > 0) {
         visible.add(new Dependent("other", hidden + " item(s) the caller may not see", null, null));
      }

      return visible;
   }

   private List<AlsoDeleted> extendedChildren(XDataModel dataModel, Ref ref) {
      List<AlsoDeleted> children = new ArrayList<>();

      if(KIND_LOGICAL_MODEL.equals(ref.kind())) {
         XLogicalModel model = dataModel.getLogicalModel(ref.name());

         if(model != null && model.getLogicalModelNames() != null) {
            for(String child : model.getLogicalModelNames()) {
               children.add(new AlsoDeleted(KIND_LOGICAL_MODEL, child,
                                            "extended model of " + ref.name() + " goes with it"));
            }
         }
      }
      else if(KIND_PHYSICAL_VIEW.equals(ref.kind())) {
         XPartition view = dataModel.getPartition(ref.name());

         if(view != null && view.getPartitionNames() != null) {
            for(String child : view.getPartitionNames()) {
               children.add(new AlsoDeleted(KIND_PHYSICAL_VIEW, child,
                                            "extended view of " + ref.name() + " goes with it"));
            }
         }
      }

      return children;
   }

   private static DepItem missing(Ref ref, String reason, String message) {
      return new DepItem(ref.kind(), ref.name(), null, false, false, reason, message, List.of(),
                         "", List.of(), List.of(), List.of());
   }

   // -- delete ------------------------------------------------------------------------------

   private DeleteResponse doDelete(String database, List<DeleteItem> items, Principal principal)
      throws Exception
   {
      XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);
      WizModelKindOps.Ctx ctx = new WizModelKindOps.Ctx(database, dataModel, principal);
      Set<String> folderNames = new HashSet<>();

      for(DeleteItem item : items) {
         if(KIND_FOLDER.equals(item.kind())) {
            folderNames.add(item.name());
         }
      }

      for(DeleteItem item : items) {
         if(!KIND_FOLDER.equals(item.kind())) {
            String stored = storedFolder(dataModel, item.kind(), item.name());

            if(stored != null && folderNames.contains(stored)) {
               throw new IllegalArgumentException(
                  "items: " + item.kind() + " \"" + item.name() + "\" is inside the folder \"" +
                  stored + "\" that is also listed; list the folder only");
            }
         }
      }

      DeleteResult[] results = new DeleteResult[items.size()];

      for(int i = 0; i < items.size(); i++) {
         results[i] = precheckDelete(ctx, items.get(i));
      }

      // folders first, then the rest, each in request order (as the Portal deletes)
      for(int pass = 0; pass < 2; pass++) {
         for(int i = 0; i < items.size(); i++) {
            DeleteItem item = items.get(i);

            if(results[i] != null || KIND_FOLDER.equals(item.kind()) != (pass == 0)) {
               continue;
            }

            try {
               results[i] = applyDelete(ctx, item);
            }
            catch(SecurityException e) {
               results[i] = deleteFailed(item, PERMISSION_DENIED, e.getMessage(), null);
            }
            catch(Exception e) {
               // the exception text of a server failure can carry a credential (a driver or
               // storage message with a URL password): name the item, log the rest
               LOG.error("Failed to delete {} \"{}\" in {}", item.kind(), item.name(), database, e);
               results[i] = deleteFailed(item, UNKNOWN, "deleting " + item.kind() + " \"" +
                  item.name() + "\" failed on the server; see the server log", null);
            }
         }
      }

      return new DeleteResponse(Arrays.asList(results));
   }

   /** Returns the failure that stops the item before anything is written, or null. */
   private DeleteResult precheckDelete(WizModelKindOps.Ctx ctx, DeleteItem item) throws Exception {
      XDataModel dataModel = ctx.dataModel();
      String database = ctx.database();
      boolean force = Boolean.TRUE.equals(item.force());

      if(KIND_FOLDER.equals(item.kind())) {
         if(!gates.maySee(dataModel, database, KIND_FOLDER, item.name(), ctx.principal())) {
            return deleteFailed(item, NOT_FOUND,
                                "no folder named \"" + item.name() + "\" in " + database, null);
         }

         if(!dataSourceService.checkPermission(database, item.name(), ResourceAction.DELETE,
                                               ctx.principal()))
         {
            return deleteFailed(item, PERMISSION_DENIED,
               "the caller lacks DELETE on data model folder \"" + database + "/" + item.name() +
               "\"", null);
         }

         List<Ref> contains = contents(dataModel, item.name());
         List<String> blockers = containedBlockers(ctx, item.name(), contains);

         if(!blockers.isEmpty()) {
            return deleteFailed(item, CONTAINED_NOT_DELETABLE,
               "the folder \"" + item.name() + "\" contains " + String.join(", ", blockers) +
               " that the caller may not see or delete; nothing was deleted", null);
         }

         List<String> expected = new ArrayList<>();

         for(Ref ref : item.expect()) {
            expected.add(ref.kind() + ":" + ref.name());
         }

         List<String> actual = new ArrayList<>();

         for(Ref ref : contains) {
            actual.add(ref.kind() + ":" + ref.name());
         }

         Collections.sort(expected);
         Collections.sort(actual);

         if(!expected.equals(actual)) {
            List<String> added = new ArrayList<>(actual);
            added.removeAll(expected);
            List<String> removed = new ArrayList<>(expected);
            removed.removeAll(actual);
            return deleteFailed(item, EXPECT_MISMATCH,
               "the folder's contents changed since the dry run (added: " + added + ", removed: " +
               removed + "); run the dry run again", null);
         }

         if(!force) {
            DepItem outer = folderDepItem(ctx, new Ref(KIND_FOLDER, item.name(), null));

            if(!outer.dependents().isEmpty()) {
               return deleteFailed(item, HAS_DEPENDENCIES, outer.text() +
                  ". Call delete_data_models without confirm and show the user the dependents",
                  outer.dependents());
            }
         }

         return null;
      }

      String existing = gates.visibleKindOfName(dataModel, database, item.name(), ctx.principal());

      if(existing == null) {
         return deleteFailed(item, NOT_FOUND, "no " + item.kind() + " named \"" + item.name() +
            "\" in " + database, null);
      }

      if(!existing.equals(item.kind())) {
         return deleteFailed(item, WRONG_KIND, "\"" + item.name() + "\" is a " + existing +
            ", not a " + item.kind(), null);
      }

      WizModelKindOps ops = opsFor(item.kind());

      if(ops == null) {
         return deleteFailed(item, UNKNOWN, "not available in this build", null);
      }

      WizModelKindOps.ModelRef ref =
         new WizModelKindOps.ModelRef(toKind(item.kind()), item.name(), item.parent());

      if(ops.find(ctx, ref).isEmpty()) {
         return deleteFailed(item, NOT_FOUND, "no " + item.kind() + " named \"" + item.name() +
            "\" in " + database, null);
      }

      if(!ops.mayDelete(ctx, ref)) {
         return deleteFailed(item, PERMISSION_DENIED, "the caller lacks the permission to delete " +
            item.kind() + " \"" + database + "/" + item.name() + "\"", null);
      }

      if(!force) {
         // what blocks the delete is decided on EVERY user; what is printed and returned is only
         // what the caller may see (a hidden user is counted, never named)
         List<Dependent> all = wire(ops.dependents(ctx, ref));
         List<Dependent> dependents = visibleOnly(ctx, all);

         if(!all.isEmpty()) {
            boolean models = KIND_PHYSICAL_VIEW.equals(item.kind()) && all.stream()
               .anyMatch(d -> KIND_LOGICAL_MODEL.equals(d.type()) || KIND_VPM.equals(d.type()));

            if(models) {
               StringBuilder names = new StringBuilder();

               for(Dependent d : dependents) {
                  names.append(names.length() == 0 ? "" : ", ").append(d.name());
               }

               return deleteFailed(item, VIEW_IN_USE, "the physical view \"" + item.name() +
                  "\" is used by " + names + "; delete or change those first", dependents);
            }

            return deleteFailed(item, HAS_DEPENDENCIES, describe(dependents) +
               ". Call delete_data_models without confirm and show the user the dependents",
               dependents);
         }
      }

      return null;
   }

   private DeleteResult applyDelete(WizModelKindOps.Ctx ctx, DeleteItem item) throws Exception {
      String database = ctx.database();
      boolean force = Boolean.TRUE.equals(item.force());

      if(KIND_FOLDER.equals(item.kind())) {
         List<Ref> contained = contents(ctx.dataModel(), item.name());
         folderManagerService.deleteDataModelFolder(database, item.name(), ctx.principal());

         if(hasFolder(repository.getDataModel(database), item.name())) {
            return deleteFailed(item, READ_BACK_FAILED, "the server reported success but the " +
               "folder \"" + item.name() + "\" still exists", null);
         }

         List<Ref> deleted = new ArrayList<>(contained);
         deleted.add(new Ref(KIND_FOLDER, item.name(), null));
         return new DeleteResult(item.kind(), item.name(), true, null, null, null, deleted);
      }

      WizModelKindOps ops = opsFor(item.kind());
      WizModelKindOps.ModelRef ref =
         new WizModelKindOps.ModelRef(toKind(item.kind()), item.name(), item.parent());
      WizModelKindOps.OpResult result = ops.delete(ctx, ref, force);

      if(!result.ok()) {
         return deleteFailed(item, result.reason() == null ? UNKNOWN : result.reason(),
                             result.message(), null);
      }

      XDataModel after = repository.getDataModel(database);

      if(item.parent() == null && kindOfName(after, item.name()) != null) {
         return deleteFailed(item, READ_BACK_FAILED, "the server reported success but " +
            item.kind() + " \"" + item.name() + "\" still exists", null);
      }

      List<Ref> deleted = new ArrayList<>();

      for(WizModelKindOps.ModelRef affected : result.affected()) {
         deleted.add(new Ref(toWire(affected.kind()), affected.name(), affected.parent()));
      }

      if(deleted.isEmpty()) {
         deleted.add(new Ref(item.kind(), item.name(), item.parent()));
      }

      return new DeleteResult(item.kind(), item.name(), true, null, null, null, deleted);
   }

   private static List<Dependent> wire(List<WizModelKindOps.DependentRef> refs) {
      List<Dependent> result = new ArrayList<>();

      for(WizModelKindOps.DependentRef ref : refs) {
         result.add(new Dependent(ref.type(), ref.name(), ref.path(), ref.element()));
      }

      return result;
   }

   private static DeleteResult deleteFailed(DeleteItem item, String reason, String message,
                                            List<Dependent> dependents)
   {
      return new DeleteResult(item.kind(), item.name(), false, reason, message, dependents,
                              List.of());
   }

   // -- shared ------------------------------------------------------------------------------

   private static List<Ref> requireRefs(String field, List<Ref> refs) {
      if(refs == null || refs.isEmpty()) {
         throw new IllegalArgumentException(field + " is required");
      }

      for(int i = 0; i < refs.size(); i++) {
         Ref ref = refs.get(i);
         requireText(field + "[" + i + "].name", ref == null ? null : ref.name());
         requireKind(field + "[" + i + "].kind", ref.kind());
      }

      return refs;
   }

   private WizModelKindOps opsFor(String kind) {
      WizModelKindOps.Kind wanted = toKind(kind);

      for(WizModelKindOps ops : kindOps) {
         if(ops.kind() == wanted) {
            return ops;
         }
      }

      return null;
   }

   private static WizDataModelErrors.DmFailure unavailable(String kind) {
      return new WizDataModelErrors.DmFailure(
         UNKNOWN, "kind", kind + " is not available in this build");
   }

   static final String COVERAGE =
      "Coverage: dependents are the assets the server records as depending on the model " +
      "(worksheets, viewsheets, other models, VPM conditions). Assets that use the data " +
      "source's tables directly, or that were saved before dependency records existed, are not " +
      "covered by this check.";

   private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(WizDataModelItemController.class);
   private final List<WizModelKindOps> kindOps;
   private final DataModelFolderManagerService folderManagerService;
   private final DataSourceService dataSourceService;
   private final LogicalModelService logicalModelService;
   private final XRepository repository;
   private final WizDataModelSource source;
   private final WizDataModelGates gates;
}
