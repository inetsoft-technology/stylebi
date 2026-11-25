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
import inetsoft.uql.jdbc.util.JDBCUtil;
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
 * {@code /physical/get}, {@code /physical/create}, {@code /physical/update} and
 * {@code /physical/autojoin} of the data model
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
         securityEngine, new PhysicalGraphModelService(runtimes, physicalModelService, manager));
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

   /**
    * The Auto Join Tables function: the joins the Portal's dialog would build for the tables of
    * a stored view or of a draft. Read only (no lock, nothing stored), gated like {@code get}.
    */
   @PostMapping(value = "/physical/autojoin", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object autoJoin(@RequestBody AutoJoinRequest request, Principal principal)
      throws Exception
   {
      return WizDataModelErrors.run(() -> doAutoJoin(request, principal));
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
      Map<String, Object> definition = WizPhysicalViewMapper.toWire(view);
      return new GetResponse(true, definition, view.folder(), false, revisionOf(view));
   }

   // -- auto join ------------------------------------------------------------------------------

   private Object doAutoJoin(AutoJoinRequest request, Principal principal) throws Exception {
      String database = required(request == null ? null : request.database(), "database");
      String name = blankToNull(request.name());
      boolean hasTables = request.tables() != null;

      if((name != null) == hasTables) {
         throw WizAutoJoinProposal.invalid(
            "name", "SOURCE_INVALID",
            "give exactly one of name (a stored view) and tables (a draft)");
      }

      XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);

      if(!hasTables) {
         // a view the caller may not see is answered like a missing one, as get does
         stored(dataModel, database, blankToNull(request.parent()), name, principal);
         return session.autoJoin(database, dataModel, blankToNull(request.parent()), name, null,
                                 request.mode(), request.columns(), principal);
      }

      if(blankToNull(request.parent()) != null) {
         throw WizAutoJoinProposal.invalid(
            "parent", "SOURCE_INVALID", "parent goes with name, not with tables");
      }

      if(request.tables().size() < 2) {
         throw WizAutoJoinProposal.invalid(
            "tables", "TABLES_TOO_FEW",
            "auto join needs at least two tables, got " + request.tables().size());
      }

      String connection = blankToNull(request.connection());
      requireConnection(database, connection, principal);
      // the service checks this too, but only after the draft was built (metadata read, inline
      // view SQL run): a caller who may not edit the data source is turned away before that
      dataSourceService.checkDataModelEditPermission(database, connection, principal);
      Map<String, Object> draft = new LinkedHashMap<>();
      draft.put("name", "autojoin-draft");
      draft.put("tables", request.tables());

      if(connection != null) {
         draft.put("connection", connection);
      }

      return session.autoJoin(database, dataModel, null, null, draft, request.mode(),
                              request.columns(), principal);
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
         // the kinds, the folders and the views share one name space, an extended view too
         WizDataModelNames.requireUniqueInSource(
            dataModel, name,
            (kind, existing) -> gates.maySee(dataModel, database, kind, existing, principal));

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
      requireFullOuterSupported(database, want.connection(), want.joins(), Set.of());
      Desired desired = withFolder(parsed, folder);
      WizPhysicalViewSession.Outcome outcome = session.create(
         database, dataModel, parent, desired, Boolean.TRUE.equals(request.dryRun()), principal);
      Map<String, Object> written = wire(outcome);
      return new WriteResponse(true, name, outcome.folder(), written, outcome.warnings(), null,
                               outcome.view() == null ? null : revisionOf(outcome.view()));
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

      // the edit was planned from a read: refuse it before anything is opened when the stored
      // view is no longer what that read returned (dry runs included)
      if(request.baseRevision() != null && !request.baseRevision().isEmpty()) {
         View current = session.read(database, dataModel, parent, name);
         String revision = revisionOf(current);

         if(!revision.equals(request.baseRevision())) {
            throw new DmFailure(
               WizDataModelWire.STALE_VIEW, "baseRevision",
               "view " + name + " changed since it was read; read it again and redo the edit; " +
                  "nothing was written");
         }
      }

      if(parsed.hasJoins()) {
         Set<String> keep = new HashSet<>();

         for(JoinDef stored : session.read(database, dataModel, parent, name).joins()) {
            if("FULL_OUTER".equals(stored.type())) {
               keep.add(stored.key());
            }
         }

         requireFullOuterSupported(database, parsed.hasConnection() ? want.connection() :
                                      view.getConnection(), want.joins(), keep);
      }

      boolean dryRun = Boolean.TRUE.equals(request.dryRun());
      List<WizDataModelWire.Dependent> dependents = visibleOnly(
         dataModel, database, principal, dependentsOf(database, dataModel, parent, name,
                                                      principal));
      WizPhysicalViewSession.Outcome outcome = session.update(
         database, dataModel, parent, name, parsed, dryRun, principal);
      Map<String, Object> written = wire(outcome);
      return new WriteResponse(true, name, outcome.folder(), written, outcome.warnings(),
                               dependents,
                               outcome.view() == null ? null : revisionOf(outcome.view()));
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

   /**
    * The revision of a view: taken over the definition without its layout, so that moving a table
    * does not make an edit that was planned from an earlier read stale.
    */
   private static String revisionOf(View view) {
      return WizDataModelRevision.of(WizPhysicalViewMapper.toRevisionForm(view));
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
         desired.hasTables(), desired.hasJoins(), desired.autoLayout(), desired.viewport());
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

   /**
    * A FULL_OUTER join is refused on a database whose SQL helper does not support it, as the
    * Portal's Edit Join dialog does not offer the type there. A join already stored as FULL_OUTER
    * ({@code keep}, by join key) is left alone so an unrelated edit is not blocked.
    */
   private void requireFullOuterSupported(String database, String connection,
                                          List<JoinDef> joins, Set<String> keep)
      throws Exception
   {
      if(joins == null || joins.stream().noneMatch(j -> "FULL_OUTER".equals(j.type()))) {
         return;
      }

      XDataSource dataSource = dataSourceService.getDataSource(database);
      JDBCDataSource jdbc = dataSource instanceof JDBCDataSource ? (JDBCDataSource) dataSource :
         null;

      if(jdbc != null && connection != null && !connection.isEmpty() &&
         jdbc.getDataSource(connection) != null)
      {
         jdbc = jdbc.getDataSource(connection);
      }

      checkFullOuter(database, joins, keep, jdbc == null || JDBCUtil.supportFullOuterJoin(jdbc, true));
   }

   static void checkFullOuter(String database, List<JoinDef> joins, Set<String> keep,
                              boolean supported)
   {
      if(supported || joins == null) {
         return;
      }

      for(int i = 0; i < joins.size(); i++) {
         JoinDef join = joins.get(i);

         if("FULL_OUTER".equals(join.type()) && !keep.contains(join.key())) {
            throw invalid("definition.joins[" + join.label() + "].type", "JOIN_TYPE_UNSUPPORTED",
                          "join type FULL_OUTER is not supported by the database of " + database +
                             "; use EQUAL, LEFT_OUTER or RIGHT_OUTER; nothing changed");
         }
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
