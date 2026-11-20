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

import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.*;
import inetsoft.util.Tool;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.LogicalModelService;
import inetsoft.web.portal.controller.database.LogicalModelTreeService;
import inetsoft.web.portal.model.database.LogicalModelDefinition;
import inetsoft.web.wiz.controller.WizDataModelErrors.DmFailure;
import inetsoft.web.wiz.controller.WizDataModelWire.Err;
import inetsoft.web.wiz.controller.WizLogicalModelWire.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

/**
 * {@code /logical/get}, {@code /logical/create}, {@code /logical/update} of the data model bridge
 * (design 4.1, 4.2). Each route applies its gate first (always a subset of what the service
 * enforces, never the site-administrator gate), runs the validator before the service (the
 * service validates almost nothing), calls the same service method the Portal calls, repeats the
 * Portal's side effect (the metadata refresh after a create, the cache dependence removal after
 * an update), and reads the model back and compares it with what was written (charter C2).
 *
 * <p>Business outcomes are {@code 200 {ok:false, reason, ...}}; a denial is a
 * {@code SecurityException} (403); a malformed request is an {@code IllegalArgumentException}
 * (400). No local {@code @ExceptionHandler}, which would beat {@code WizControllerErrorHandler}.</p>
 */
@RestController
@RequestMapping("/api/wiz/data-models")
public class WizLogicalModelController {
   public WizLogicalModelController(XRepository repository, DataSourceService dataSourceService,
                                    LogicalModelService modelService,
                                    LogicalModelTreeService treeService,
                                    AssetDataCache assetDataCache)
   {
      this.dataSourceService = dataSourceService;
      this.modelService = modelService;
      this.treeService = treeService;
      this.assetDataCache = assetDataCache;
      this.gates = new WizDataModelGates(dataSourceService, modelService);
      this.source = new WizDataModelSource(repository);
   }

   @PostMapping(value = "/logical/get", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object get(@RequestBody GetRequest request, Principal principal) throws Exception {
      return WizDataModelErrors.run(() -> doGet(request, principal));
   }

   @PostMapping(value = "/logical/create", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object create(@RequestBody CreateRequest request, Principal principal)
      throws Exception
   {
      return WizDataModelErrors.run(() -> locked(request == null ? null : request.database(),
                                                 () -> doCreate(request, principal)));
   }

   @PostMapping(value = "/logical/update", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object update(@RequestBody UpdateRequest request, Principal principal)
      throws Exception
   {
      return WizDataModelErrors.run(() -> locked(request == null ? null : request.database(),
                                                 () -> doUpdate(request, principal)));
   }

   /**
    * Runs a write holding the shared per-data-source lock of every data model write (the same one
    * VPM, rename, move and folder writes take), so the namespace check and the write are one step
    * against them on this node.
    */
   private static <T> T locked(String database, WizDataModelErrors.Body<T> body)
      throws Exception
   {
      return WizDataModelLocks.withDatabase(required(database, "database"), body);
   }

   // -- get ------------------------------------------------------------------------------------

   private Object doGet(GetRequest request, Principal principal) throws Exception {
      String database = required(request == null ? null : request.database(), "database");
      String name = required(request.name(), "name");
      // the READ gate runs first; a model the caller cannot read is answered exactly like a
      // missing one, before the view is resolved, so the caller learns nothing about it
      XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);
      String parent = blankToNull(request.parent());
      XLogicalModel model = parent == null ? stored(dataModel, database, name, principal) :
         extended(dataModel, database, parent, name, principal);
      LogicalModelDefinition dto = modelService.getModel(
         database, model.getPartition(), name, parent, principal);
      boolean isExtended = parent != null;
      return new GetResponse(true, MAPPER.toWire(dto, isExtended), blankToNull(dto.getFolder()),
                             isExtended);
   }

   // -- create ---------------------------------------------------------------------------------

   private Object doCreate(CreateRequest request, Principal principal) throws Exception {
      String database = required(request == null ? null : request.database(), "database");
      Definition definition = request.definition();

      if(definition == null) {
         throw new IllegalArgumentException("definition is required");
      }

      WizDataModelNames.requireValidModelName(definition.name());
      XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);
      String name = definition.name();
      String folder = blankToNull(definition.folder());
      requireWrite(database, folder, name, blankToNull(definition.connection()), principal);

      // a clash names the existing kind only when the caller may see that item
      WizDataModelNames.requireUniqueInSource(
         dataModel, name,
         (kind, existing) -> gates.maySee(dataModel, database, kind, existing, principal));
      requireConnectionRead(database, definition.connection(), principal);
      WizLogicalModelValidator.Result result = validator.validate(
         new WizLogicalModelEnv(database, dataModel, dataSourceService), definition,
         WizLogicalModelValidator.Mode.CREATE, null);
      refuse(result);
      boolean dryRun = Boolean.TRUE.equals(request.dryRun());

      if(dryRun) {
         return new CreateResponse(true, name, folder, null, result.warnings());
      }

      Definition normalized = result.normalized();
      LogicalModelDefinition dto = MAPPER.toDto(normalized, null, viewOf(normalized, null));
      modelService.createModel(database, folder, dto.getPartition(), dto, null, principal);
      treeService.refreshPartitionMetaData(database, dto);

      Definition readBack = readBack(database, name, normalized, principal);
      return new CreateResponse(true, name, folder, readBack, result.warnings());
   }

   // -- update ---------------------------------------------------------------------------------

   private Object doUpdate(UpdateRequest request, Principal principal) throws Exception {
      String database = required(request == null ? null : request.database(), "database");
      String name = required(request.name(), "name");
      Definition definition = request.definition();

      if(definition == null) {
         throw new IllegalArgumentException("definition is required");
      }

      XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);
      XLogicalModel stored = stored(dataModel, database, name, principal);

      // the service would answer a body name that differs with a misleading SecurityException
      // (it writes the model named in the body), and renaming has its own route
      if(definition.name() != null && !definition.name().equals(name)) {
         throw fail(
            WizDataModelWire.INVALID, "definition.name",
            "definition.name \"" + definition.name() + "\" differs from name \"" + name +
               "\"; renaming goes through rename, not update",
            List.of(new Err("definition.name", "definition.name \"" + definition.name() +
               "\" differs from name \"" + name + "\"", WizLogicalModelValidator.NAME_INVALID)),
            null);
      }

      Definition submitted = new Definition(
         name, definition.description(), definition.view(), definition.folder(),
         definition.connection(), definition.parent(), definition.extended(),
         definition.entities());
      String targetFolder = blankToNull(submitted.folder());
      requireUpdateAccess(database, stored, targetFolder, principal);
      requireConnectionRead(database, submitted.connection(), principal);

      WizLogicalModelValidator.Result result = validator.validate(
         new WizLogicalModelEnv(database, dataModel, dataSourceService), submitted,
         WizLogicalModelValidator.Mode.UPDATE, stored);
      refuse(result);
      Definition normalized = result.normalized();
      String view = viewOf(normalized, stored);
      List<ElementDependents> elementDependents = elementDependents(stored, normalized);
      List<WizDataModelWire.Dependent> viewDependents =
         Tool.equals(view, stored.getPartition()) ? List.of() :
            WizLogicalModelOps.dependentsOf(stored.getOuterDependencies());

      if(Boolean.TRUE.equals(request.dryRun())) {
         return new UpdateResponse(true, null, result.warnings(), elementDependents,
                                   viewDependents);
      }

      LogicalModelDefinition current = modelService.getModel(
         database, stored.getPartition(), name, null, principal);
      Definition currentWire = MAPPER.toWire(current, false);

      if(WizLogicalModelReadBack.diff(normalized, currentWire) == null) {
         // a no-op (a get result fed back): nothing is written, so nothing changes
         return new UpdateResponse(true, currentWire, result.warnings(), List.of(), List.of());
      }

      LogicalModelDefinition dto = MAPPER.toDto(normalized, stored, view);
      modelService.updateModel(database, dto.getFolder(), name, dto, null, principal);
      assetDataCache.removeCacheDependence(database);

      Definition readBack = readBack(database, name, normalized, principal);
      return new UpdateResponse(true, readBack, result.warnings(), elementDependents,
                                viewDependents);
   }

   // -- helpers --------------------------------------------------------------------------------

   /** Reads the stored model back and fails loud when it is not what was written (C2). */
   private Definition readBack(String database, String name, Definition written,
                              Principal principal)
      throws Exception
   {
      XDataModel dataModel = source.requireJdbcDataModel(database);
      XLogicalModel model = dataModel.getLogicalModel(name);

      if(model == null) {
         throw fail(WizDataModelWire.READ_BACK_FAILED, "definition",
                             "the server reported success but \"" + database + "/" + name +
                                "\" is not there");
      }

      LogicalModelDefinition dto = modelService.getModel(
         database, model.getPartition(), name, null, principal);
      Definition stored = MAPPER.toWire(dto, false);
      String problem = WizLogicalModelReadBack.diff(written, stored);

      if(problem != null) {
         throw fail(WizDataModelWire.READ_BACK_FAILED, "definition",
                             "the server reported success but \"" + database + "/" + name +
                                "\" is not as written: " + problem);
      }

      return stored;
   }

   /**
    * The model stored under this base name that the caller may see. A model the caller may not
    * see is answered exactly like a missing one, and WRONG_KIND names the kind only when the
    * caller can see an item of that kind with this name.
    */
   private XLogicalModel stored(XDataModel dataModel, String database, String name,
                                Principal principal)
   {
      XLogicalModel model = dataModel.getLogicalModel(name);

      if(model != null) {
         if(gates.maySee(dataModel, database, WizDataModelWire.KIND_LOGICAL_MODEL, name,
                         principal))
         {
            return model;
         }

         throw WizDataModelGates.notFound("logicalModel", "name", database, name);
      }

      String existing = gates.visibleKindOfName(dataModel, database, name, principal);

      if(existing == null) {
         throw WizDataModelGates.notFound("logicalModel", "name", database, name);
      }

      throw new DmFailure(WizDataModelWire.WRONG_KIND, "name",
                          "\"" + name + "\" is a " + existing + ", not a logicalModel", null,
                          existing);
   }

   private XLogicalModel extended(XDataModel dataModel, String database, String parent,
                                  String name, Principal principal)
   {
      XLogicalModel base = stored(dataModel, database, parent, principal);
      XLogicalModel model = base.getLogicalModel(name);

      if(model == null || !modelService.checkPermission(
         database, base.getFolder(), name, model.getConnection(), ResourceAction.READ, principal))
      {
         throw WizDataModelGates.notFound("extended logicalModel", "name", database, name);
      }

      return model;
   }

   private String viewOf(Definition definition, XLogicalModel stored) {
      if(!Tool.isEmptyString(definition.view())) {
         return definition.view();
      }

      return stored == null ? null : stored.getPartition();
   }

   private void refuse(WizLogicalModelValidator.Result result) {
      if(result.valid()) {
         return;
      }

      StringBuilder message = new StringBuilder("the logical model definition has ")
         .append(result.errors().size()).append(result.errors().size() == 1 ? " problem: " :
                                                   " problems: ");

      for(int i = 0; i < result.errors().size() && i < 3; i++) {
         Err error = result.errors().get(i);
         message.append(i > 0 ? "; " : "").append(error.field()).append(": ")
            .append(error.message());
      }

      if(result.errors().size() > 3) {
         message.append("; ...");
      }

      throw fail(WizDataModelWire.INVALID, result.errors().get(0).field(),
                          message.toString(), result.errors(), null);
   }

   /** WRITE on the model path, with the model's connection as the service resolves it. */
   private void requireWrite(String database, String folder, String name, String connection,
                             Principal principal)
   {
      if(!modelService.checkPermission(database, folder, name, connection, ResourceAction.WRITE,
                                       principal))
      {
         throw denied(database, folder, name, principal);
      }
   }

   /**
    * What {@code LogicalModelService.updateModel} requires: WRITE on the stored model, and for a
    * changed folder also DELETE on the stored model and WRITE on the target path.
    */
   private void requireUpdateAccess(String database, XLogicalModel stored, String targetFolder,
                                    Principal principal)
   {
      String storedFolder = blankToNull(stored.getFolder());

      if(!modelService.checkPermission(database, storedFolder, stored.getName(),
                                       stored.getConnection(), ResourceAction.WRITE, principal))
      {
         throw denied(database, storedFolder, stored.getName(), principal);
      }

      if(!Tool.equals(storedFolder, targetFolder)) {
         if(!modelService.checkPermission(database, storedFolder, stored.getName(),
                                          stored.getConnection(), ResourceAction.DELETE,
                                          principal))
         {
            throw denied(database, storedFolder, stored.getName(), principal);
         }

         if(!modelService.checkPermission(database, targetFolder, stored.getName(),
                                          stored.getConnection(), ResourceAction.WRITE,
                                          principal))
         {
            throw denied(database, targetFolder, stored.getName(), principal);
         }
      }
   }

   /** An additional connection named by the definition needs READ on {@code db::connection}. */
   private void requireConnectionRead(String database, String connection, Principal principal)
      throws Exception
   {
      if(!Tool.isEmptyString(connection)) {
         gates.requireDataSourcePermission(database + "::" + connection, ResourceAction.READ,
                                           principal);
      }
   }

   private static SecurityException denied(String database, String folder, String name,
                                           Principal principal)
   {
      return new SecurityException(
         "Unauthorized access to resource \"" + database + "/" +
            (Tool.isEmptyString(folder) ? "" : folder + "/") + name + "\" by user " + principal);
   }

   /**
    * The recorded dependents of every stored element the update removes, renames or rebinds (the
    * elements whose change breaks a dependent), so a dry run can show them.
    */
   private List<ElementDependents> elementDependents(XLogicalModel stored,
                                                     Definition normalized)
   {
      List<ElementDependents> result = new ArrayList<>();
      List<Entity> entities = normalized.entities() == null ? List.of() : normalized.entities();
      Enumeration<XEntity> storedEntities = stored.getEntities();

      while(storedEntities.hasMoreElements()) {
         XEntity storedEntity = storedEntities.nextElement();
         Entity claim = null;

         for(Entity entity : entities) {
            String old = WizLogicalModelMapper.resolveOldName(
               entity.oldName(), entity.name(), true, stored.getEntity(entity.name()) != null);

            if(storedEntity.getName().equals(old)) {
               claim = entity;
               break;
            }
         }

         if(claim == null || !storedEntity.getName().equals(claim.name())) {
            add(result, storedEntity.getName(),
                WizLogicalModelOps.dependentsOf(storedEntity.getOuterDepencies()));
         }

         Enumeration<XAttribute> storedAttributes = storedEntity.getAttributes();

         while(storedAttributes.hasMoreElements()) {
            XAttribute attribute = storedAttributes.nextElement();
            Attribute attributeClaim = null;

            for(Attribute candidate : claim == null || claim.attributes() == null ?
               List.<Attribute>of() : claim.attributes())
            {
               String old = WizLogicalModelMapper.resolveOldName(
                  candidate.oldName(), candidate.name(), true,
                  storedEntity.getAttribute(candidate.name()) != null);

               if(attribute.getName().equals(old)) {
                  attributeClaim = candidate;
                  break;
               }
            }

            if(attributeClaim == null || !attribute.getName().equals(attributeClaim.name()) ||
               bindingChanged(attribute, attributeClaim))
            {
               add(result, storedEntity.getName() + "." + attribute.getName(),
                   WizLogicalModelOps.dependentsOf(attribute.getOuterDependencies()));
            }
         }
      }

      return result;
   }

   private static boolean bindingChanged(XAttribute stored, Attribute attribute) {
      if(stored instanceof ExpressionAttribute) {
         return !Tool.equals(((ExpressionAttribute) stored).getExpression(),
                             attribute.expression());
      }

      return !Tool.equals(stored.getTable(), attribute.table()) ||
         !Tool.equals(stored.getColumn(), attribute.column());
   }

   private static void add(List<ElementDependents> result, String element,
                           List<WizDataModelWire.Dependent> dependents)
   {
      if(!dependents.isEmpty()) {
         result.add(new ElementDependents(element, dependents));
      }
   }

   private static DmFailure fail(String reason, String field, String message) {
      return new DmFailure(reason, field, message, null, null);
   }

   private static DmFailure fail(String reason, String field, String message, List<Err> errors,
                                 String existingKind)
   {
      return new DmFailure(reason, field, message, errors, existingKind);
   }

   private static String required(String value, String field) {
      if(value == null || value.isBlank()) {
         throw new IllegalArgumentException(field + " is required");
      }

      return value;
   }

   private static String blankToNull(String value) {
      return Tool.isEmptyString(value) ? null : value;
   }

   private static final WizLogicalModelMapper MAPPER = new WizLogicalModelMapper();
   private final WizLogicalModelValidator validator = new WizLogicalModelValidator();
   private final DataSourceService dataSourceService;
   private final LogicalModelService modelService;
   private final LogicalModelTreeService treeService;
   private final AssetDataCache assetDataCache;
   private final WizDataModelGates gates;
   private final WizDataModelSource source;
}
