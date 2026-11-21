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
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.service.database.PhysicalGraphService;
import inetsoft.web.wiz.controller.WizDataModelErrors.DmFailure;
import inetsoft.web.wiz.controller.WizDataModelWire.Err;
import inetsoft.web.wiz.controller.WizPhysicalViewMapper.*;
import inetsoft.web.wiz.controller.WizPhysicalViewWire.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

import static inetsoft.web.wiz.controller.WizPhysicalViewMapper.blankToNull;

/**
 * {@code /physical/get}, {@code /physical/create}, {@code /physical/update} of the data model
 * bridge (design 4.1, 4.2, 8). A view that the caller cannot see (WRITE on its folder or the
 * data source) is answered exactly like a missing one; a view the caller can see but may not
 * write to is a 403 naming the action and the path. The writes run in a one-request session
 * ({@link WizPhysicalViewSession}) that always destroys its runtime; no runtime id is in any
 * request or response.
 *
 * <p>No local {@code @ExceptionHandler}, which would beat {@code WizControllerErrorHandler}.</p>
 */
@RestController
@RequestMapping("/api/wiz/data-models")
public class WizPhysicalViewController {
   public WizPhysicalViewController(XRepository repository, DataSourceService dataSourceService,
                                    LogicalModelService modelService,
                                    PhysicalModelManagerService manager,
                                    PhysicalModelService physicalModelService,
                                    RuntimePartitionService runtimes,
                                    PhysicalGraphService graphService,
                                    SecurityEngine securityEngine, WizPhysicalViewOps ops)
   {
      this.dataSourceService = dataSourceService;
      this.gates = new WizDataModelGates(dataSourceService, modelService);
      this.source = new WizDataModelSource(repository);
      this.session = new WizPhysicalViewSession(
         manager, physicalModelService, runtimes, graphService, dataSourceService, repository,
         securityEngine);
      this.ops = ops;
   }

   @PostMapping(value = "/physical/get", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object get(@RequestBody GetRequest request, Principal principal) throws Exception {
      return WizDataModelErrors.run(() -> doGet(request, principal));
   }

   @PostMapping(value = "/physical/create", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object create(@RequestBody WriteRequest request, Principal principal)
      throws Exception
   {
      return WizDataModelErrors.run(() -> locked(request, () -> doCreate(request, principal)));
   }

   @PostMapping(value = "/physical/update", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object update(@RequestBody WriteRequest request, Principal principal)
      throws Exception
   {
      return WizDataModelErrors.run(() -> locked(request, () -> doUpdate(request, principal)));
   }

   /** Runs a write holding the shared per-data-source lock of every data model write. */
   private static <T> T locked(WriteRequest request, WizDataModelErrors.Body<T> body)
      throws Exception
   {
      return WizDataModelLocks.withDatabase(
         required(request == null ? null : request.database(), "database"), body);
   }

   // -- get ------------------------------------------------------------------------------------

   private Object doGet(GetRequest request, Principal principal) throws Exception {
      String database = required(request == null ? null : request.database(), "database");
      String name = required(request.name(), "name");
      XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);
      String parent = blankToNull(request.parent());
      stored(dataModel, database, parent, name, principal);
      View view = session.read(database, dataModel, parent, name);
      return new GetResponse(true, WizPhysicalViewMapper.toWire(view), view.folder(), false);
   }

   // -- create ---------------------------------------------------------------------------------

   private Object doCreate(WriteRequest request, Principal principal) throws Exception {
      String database = required(request == null ? null : request.database(), "database");
      Desired parsed = WizPhysicalViewMapper.parse(request.definition());
      String parent = parentOf(request, parsed);
      View want = parsed.view();
      String name = want.name();
      WizDataModelNames.requireValidModelName(name);
      XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);
      String folder = blankToNull(want.folder());

      if(parent == null) {
         gates.requireFolderPermission(database, folder, ResourceAction.WRITE, principal);
         // a clash names the existing kind only when the caller may see that item
         WizDataModelNames.requireUniqueInSource(
            dataModel, name,
            (kind, existing) -> gates.maySee(dataModel, database, kind, existing, principal));
         requireFolder(dataModel, folder);
      }
      else {
         XPartition base = stored(dataModel, database, null, parent, principal);
         gates.requireFolderPermission(database, base.getFolder(), ResourceAction.WRITE,
                                       principal);

         if(base.getPartition(name) != null) {
            throw new DmFailure(
               WizDataModelWire.DUPLICATE_NAME, "definition.name",
               "a physicalView named \"" + name + "\" already exists under \"" + parent +
                  "\" in " + database + "; nothing changed", null,
               WizDataModelWire.KIND_PHYSICAL_VIEW);
         }

         if(folder != null && !folder.equals(blankToNull(base.getFolder()))) {
            throw invalid("definition.folder", "FOLDER_INVALID",
                          "an extended view lives in the folder of its base view");
         }
      }

      requireConnection(database, want.connection(), principal);
      Desired desired = withFolder(parsed, folder);
      WizPhysicalViewSession.Outcome outcome = session.create(
         database, dataModel, parent, desired, Boolean.TRUE.equals(request.dryRun()), principal);
      return new WriteResponse(true, name, outcome.folder(), wire(outcome), outcome.warnings(),
                               null);
   }

   // -- update ---------------------------------------------------------------------------------

   private Object doUpdate(WriteRequest request, Principal principal) throws Exception {
      String database = required(request == null ? null : request.database(), "database");
      String name = required(request.name(), "name");
      // the gates run first: a view the caller cannot see is answered like a missing one
      XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);
      String parent = blankToNull(request.parent());
      XPartition view = stored(dataModel, database, parent, name, principal);
      Desired parsed = WizPhysicalViewMapper.parse(request.definition());
      parentOf(request, parsed);
      View want = parsed.view();

      // renaming has its own route; the service would answer a body name that differs with a
      // misleading SecurityException
      if(want.name() != null && !want.name().equals(name)) {
         String message = "definition.name \"" + want.name() + "\" differs from name \"" + name +
            "\"; renaming goes through rename, not update";
         throw new DmFailure(WizDataModelWire.INVALID, "definition.name", message,
                             List.of(new Err("definition.name", message, "NAME_INVALID")), null);
      }

      String baseFolder = parent == null ? blankToNull(view.getFolder()) :
         blankToNull(dataModel.getPartition(parent).getFolder());
      gates.requireFolderPermission(database, baseFolder, ResourceAction.WRITE, principal);

      if(parsed.hasFolder()) {
         String folder = blankToNull(want.folder());

         if(parent != null && !Objects.equals(folder, baseFolder)) {
            throw invalid("definition.folder", "FOLDER_INVALID",
                          "an extended view lives in the folder of its base view");
         }

         if(parent == null && !Objects.equals(folder, baseFolder)) {
            requireFolder(dataModel, folder);
            gates.requireFolderPermission(database, folder, ResourceAction.WRITE, principal);
         }
      }

      if(parsed.hasConnection()) {
         requireConnection(database, want.connection(), principal);
      }

      boolean dryRun = Boolean.TRUE.equals(request.dryRun());
      List<WizDataModelWire.Dependent> dependents = visibleOnly(
         dataModel, database, principal, dependentsOf(database, dataModel, parent, name,
                                                      principal));
      WizPhysicalViewSession.Outcome outcome = session.update(
         database, dataModel, parent, name, parsed, dryRun, principal);
      return new WriteResponse(true, name, outcome.folder(), wire(outcome), outcome.warnings(),
                               dependents);
   }

   // -- helpers --------------------------------------------------------------------------------

   private List<WizDataModelWire.Dependent> dependentsOf(String database, XDataModel dataModel,
                                                         String parent, String name,
                                                         Principal principal)
      throws Exception
   {
      WizModelKindOps.Ctx ctx = new WizModelKindOps.Ctx(database, dataModel, principal);
      List<WizDataModelWire.Dependent> result = new ArrayList<>();

      for(WizModelKindOps.DependentRef ref :
         ops.dependents(ctx, new WizModelKindOps.ModelRef(
            WizModelKindOps.Kind.PHYSICAL_VIEW, name, parent)))
      {
         result.add(new WizDataModelWire.Dependent(ref.type(), ref.name(), ref.path(),
                                                   ref.element()));
      }

      return result;
   }

   /**
    * Dependents shown to the caller: the items the caller may not see collapse into one
    * placeholder, so the count is still there.
    */
   private List<WizDataModelWire.Dependent> visibleOnly(
      XDataModel dataModel, String database, Principal principal,
      List<WizDataModelWire.Dependent> dependents)
   {
      List<WizDataModelWire.Dependent> visible = new ArrayList<>();
      int hidden = 0;

      for(WizDataModelWire.Dependent dependent : dependents) {
         String kind = switch(dependent.type()) {
            case WizDataModelWire.KIND_LOGICAL_MODEL -> WizDataModelWire.KIND_LOGICAL_MODEL;
            case WizDataModelWire.KIND_PHYSICAL_VIEW -> WizDataModelWire.KIND_PHYSICAL_VIEW;
            case WizDataModelWire.KIND_VPM -> WizDataModelWire.KIND_VPM;
            default -> null;
         };

         if(kind == null || gates.maySee(dataModel, database, kind, dependent.name(), principal)) {
            visible.add(dependent);
         }
         else {
            hidden++;
         }
      }

      if(hidden > 0) {
         visible.add(new WizDataModelWire.Dependent(
            "other", hidden + " item(s) the caller may not see", null, null));
      }

      return visible;
   }

   private static Map<String, Object> wire(WizPhysicalViewSession.Outcome outcome) {
      return outcome.view() == null ? null : WizPhysicalViewMapper.toWire(outcome.view());
   }

   /** The base view of the request: the {@code parent} of the request or of the definition. */
   private static String parentOf(WriteRequest request, Desired parsed) {
      String fromRequest = blankToNull(request.parent());
      String fromDefinition = blankToNull(parsed.view().parent());

      if(fromRequest != null && fromDefinition != null && !fromRequest.equals(fromDefinition)) {
         throw invalid("definition.parent", "PARENT_CONFLICT",
                       "parent \"" + fromRequest + "\" and definition.parent \"" +
                          fromDefinition + "\" differ");
      }

      return fromRequest != null ? fromRequest : fromDefinition;
   }

   private static Desired withFolder(Desired desired, String folder) {
      View view = desired.view();
      return new Desired(
         new View(view.name(), view.description(), folder, view.connection(), view.parent(),
                  view.tables(), view.joins()),
         desired.hasDescription(), desired.hasFolder(), desired.hasConnection(),
         desired.hasTables(), desired.hasJoins());
   }

   /**
    * The stored view (or extended view) that the caller may see. One that the caller may not
    * see is answered exactly like a missing one, and WRONG_KIND names a kind only when the
    * caller can see an item of that kind with this name.
    */
   private XPartition stored(XDataModel dataModel, String database, String parent, String name,
                             Principal principal)
   {
      if(parent != null) {
         XPartition base = stored(dataModel, database, null, parent, principal);
         XPartition view = base.getPartition(name);

         if(view == null) {
            throw WizDataModelGates.notFound("extended physicalView", "name", database, name);
         }

         return view;
      }

      XPartition view = dataModel.getPartition(name);

      if(view != null) {
         if(gates.maySee(dataModel, database, WizDataModelWire.KIND_PHYSICAL_VIEW, name,
                         principal))
         {
            return view;
         }

         throw WizDataModelGates.notFound("physicalView", "name", database, name);
      }

      String existing = gates.visibleKindOfName(dataModel, database, name, principal);

      if(existing == null) {
         throw WizDataModelGates.notFound("physicalView", "name", database, name);
      }

      throw new DmFailure(WizDataModelWire.WRONG_KIND, "name",
                          "\"" + name + "\" is a " + existing + ", not a physicalView", null,
                          existing);
   }

   private static void requireFolder(XDataModel dataModel, String folder) {
      if(folder != null && !WizDataModelSupport.hasFolder(dataModel, folder)) {
         throw new DmFailure(
            WizDataModelWire.FOLDER_NOT_FOUND, "definition.folder",
            "no data-model folder \"" + folder + "\"; create it with manage_data_model_folder");
      }
   }

   /** An additional connection named by the definition must exist and needs READ. */
   private void requireConnection(String database, String connection, Principal principal)
      throws Exception
   {
      if(connection == null || connection.isEmpty()) {
         return;
      }

      gates.requireDataSourcePermission(database + "::" + connection, ResourceAction.READ,
                                        principal);
      XDataSource dataSource = dataSourceService.getDataSource(database);

      if(!(dataSource instanceof JDBCDataSource) ||
         ((JDBCDataSource) dataSource).getDataSource(connection) == null)
      {
         throw invalid("definition.connection", "CONNECTION_NOT_FOUND",
                       "no additional connection \"" + connection + "\" on " + database);
      }
   }

   private static DmFailure invalid(String field, String code, String message) {
      return WizPhysicalViewMapper.invalid(List.of(new Err(field, message, code)));
   }

   private static String required(String value, String field) {
      if(value == null || value.isBlank()) {
         throw new IllegalArgumentException(field + " is required");
      }

      return value;
   }

   private final DataSourceService dataSourceService;
   private final WizDataModelGates gates;
   private final WizDataModelSource source;
   private final WizPhysicalViewSession session;
   private final WizPhysicalViewOps ops;
}
