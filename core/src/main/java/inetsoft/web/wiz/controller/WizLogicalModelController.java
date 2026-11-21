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
      // phase 2: an extended model is editable (its inherited elements carry baseElement:true and
      // are read-only one by one), so readOnly is false; extended and parent say what it is
      return new GetResponse(true, MAPPER.toWire(dto, isExtended), blankToNull(dto.getFolder()),
                             false);
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
      String parent = blankToNull(definition.parent());
      // an extended model lives in its base model's folder; a missing or invisible base model is
      // answered like a missing one before anything else is said
      XLogicalModel baseModel = parent == null ? null :
         stored(dataModel, database, parent, principal);
      String folder = baseModel != null ? blankToNull(baseModel.getFolder()) :
         blankToNull(definition.folder());
      requireWrite(database, folder, name, blankToNull(definition.connection()), principal);

      if(baseModel == null) {
         // a clash names the existing kind only when the caller may see that item
         WizDataModelNames.requireUniqueInSource(
            dataModel, name,
            (kind, existing) -> gates.maySee(dataModel, database, kind, existing, principal));
      }
      else if(baseModel.getLogicalModel(name) != null) {
         boolean seen = modelService.checkPermission(
            database, folder, name, baseModel.getLogicalModel(name).getConnection(),
            ResourceAction.READ, principal);
         throw fail(WizDataModelWire.DUPLICATE_NAME, "name",
                    seen ? "a logicalModel named \"" + name + "\" already exists under \"" +
                       parent + "\" in " + database + "; nothing changed" :
                       "the name \"" + name + "\" is already used under \"" + parent + "\" in " +
                          database + "; nothing changed", null,
                    seen ? WizDataModelWire.KIND_LOGICAL_MODEL : null);
      }

      requireConnectionRead(database, definition.connection(), principal);
      Definition baseWire = baseModel == null ? null : MAPPER.toWire(
         modelService.getModel(database, baseModel.getPartition(), parent, null, principal),
         false);
      WizLogicalModelValidator.Result result = validator.validate(
         new WizLogicalModelEnv(database, dataModel, dataSourceService), definition,
         WizLogicalModelValidator.Mode.CREATE, null,
         baseModel == null ? null :
            new WizLogicalModelValidator.Extended(baseModel, baseWire, false));
      refuse(result);
      boolean dryRun = Boolean.TRUE.equals(request.dryRun());

      if(dryRun) {
         return new CreateResponse(true, name, folder, null, result.warnings());
      }

      Definition normalized = baseModel == null ? result.normalized() :
         inBase(mergeBase(result.normalized(), baseWire), baseModel);
      String view = baseModel == null ? viewOf(normalized, null) : baseModel.getPartition();
      LogicalModelDefinition dto = MAPPER.toDto(normalized, null, view);

      if(baseModel != null) {
         dto.setParent(parent);
         dto.setFolder(folder);
      }

      modelService.createModel(database, folder, dto.getPartition(), dto, parent, principal);
      treeService.refreshPartitionMetaData(database, dto);

      Definition readBack = readBack(database, name, parent, normalized, principal);
      return new CreateResponse(true, name, folder, readBack, result.warnings());
   }

   /** An extended model has its base model's view and folder, whatever the caller left out. */
   private static Definition inBase(Definition d, XLogicalModel base) {
      // the description is the base model's too (the server reads it through the base)
      return new Definition(d.name(), base.getDescription(), base.getPartition(),
                            blankToNull(base.getFolder()), d.connection(), d.parent(),
                            d.extended(), d.entities());
   }

   /**
    * The definition an extended model is created with: the base model's entities and attributes
    * (read-only, flagged) merged with the model's own. The service would add the inherited ones
    * itself; having them in the written definition keeps the read-back comparison exact.
    */
   private static Definition mergeBase(Definition own, Definition baseWire) {
      List<Entity> merged = new ArrayList<>();
      Set<String> seen = new HashSet<>();

      for(Entity entity : own.entities() == null ? List.<Entity>of() : own.entities()) {
         Entity inherited = null;

         for(Entity candidate : baseWire.entities()) {
            if(candidate.name().equals(entity.name())) {
               inherited = candidate;
            }
         }

         if(inherited == null) {
            merged.add(entity);
            continue;
         }

         seen.add(entity.name());
         List<Attribute> attributes = new ArrayList<>(inherited.attributes());
         Set<String> inheritedNames = new HashSet<>();

         for(Attribute attribute : inherited.attributes()) {
            inheritedNames.add(attribute.name());
         }

         for(Attribute attribute : entity.attributes() == null ? List.<Attribute>of() :
            entity.attributes())
         {
            if(!inheritedNames.contains(attribute.name())) {
               attributes.add(attribute);
            }
         }

         merged.add(new Entity(inherited.name(), entity.oldName(), inherited.description(),
                               inherited.visible(), inherited.baseElement(), null, attributes));
      }

      for(Entity inherited : baseWire.entities()) {
         if(!seen.contains(inherited.name())) {
            merged.add(inherited);
         }
      }

      return new Definition(own.name(), own.description(), own.view(), own.folder(),
                            own.connection(), own.parent(), own.extended(), merged);
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
      // an extended model is addressed by its name plus definition.parent
      String parent = blankToNull(definition.parent());
      XLogicalModel stored = parent == null ? stored(dataModel, database, name, principal) :
         extended(dataModel, database, parent, name, principal);
      XLogicalModel baseModel = stored.getBaseModel();

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
      String targetFolder = baseModel != null ? blankToNull(baseModel.getFolder()) :
         blankToNull(submitted.folder());
      requireUpdateAccess(database, stored, targetFolder, principal);
      requireConnectionRead(database, submitted.connection(), principal);

      LogicalModelDefinition current = modelService.getModel(
         database, stored.getPartition(), name, parent, principal);
      Definition currentWire = MAPPER.toWire(current, parent != null);
      WizLogicalModelValidator.Result result = validator.validate(
         new WizLogicalModelEnv(database, dataModel, dataSourceService), submitted,
         WizLogicalModelValidator.Mode.UPDATE, stored,
         baseModel == null ? null : new WizLogicalModelValidator.Extended(
            baseModel, inheritedOnly(currentWire), true));
      refuse(result);
      Definition normalized = baseModel == null ? result.normalized() :
         inBase(result.normalized(), baseModel);
      String view = viewOf(normalized, stored);
      // decided on the full sets, shown filtered to what the caller may see (R3-3): the
      // placeholder carries the hidden count, so the confirm still has something to bind to
      List<ElementDependents> elementDependents = visibleElements(
         dataModel, database, principal, elementDependents(stored, normalized));
      List<WizDataModelWire.Dependent> viewDependents = Tool.equals(view, stored.getPartition()) ?
         List.of() : visibleOnly(dataModel, database, principal,
                                 WizLogicalModelOps.dependentsOf(stored.getOuterDependencies()));

      if(Boolean.TRUE.equals(request.dryRun())) {
         return new UpdateResponse(true, null, result.warnings(), elementDependents,
                                   viewDependents);
      }

      if(WizLogicalModelReadBack.diff(normalized, currentWire, parent != null) == null) {
         // a no-op (a get result fed back): nothing is written, so nothing changes
         return new UpdateResponse(true, currentWire, result.warnings(), List.of(), List.of());
      }

      LogicalModelDefinition dto = MAPPER.toDto(normalized, stored, view);

      if(baseModel != null) {
         dto.setParent(parent);
         dto.setFolder(targetFolder);
      }

      modelService.updateModel(database, dto.getFolder(), name, dto, parent, principal);
      assetDataCache.removeCacheDependence(database);

      Definition readBack = readBack(database, name, parent, normalized, principal);
      return new UpdateResponse(true, readBack, result.warnings(), elementDependents,
                                viewDependents);
   }

   // -- helpers --------------------------------------------------------------------------------

   /** Reads the stored model back and fails loud when it is not what was written (C2). */
   private Definition readBack(String database, String name, String parent, Definition written,
                               Principal principal)
      throws Exception
   {
      XDataModel dataModel = source.requireJdbcDataModel(database);
      XLogicalModel base = parent == null ? null : dataModel.getLogicalModel(parent);
      XLogicalModel model = parent == null ? dataModel.getLogicalModel(name) :
         base == null ? null : base.getLogicalModel(name);

      if(model == null) {
         throw fail(WizDataModelWire.READ_BACK_FAILED, "definition",
                    "the server reported success but \"" + database + "/" +
                       (parent == null ? "" : parent + "/") + name + "\" is not there");
      }

      LogicalModelDefinition dto = modelService.getModel(
         database, model.getPartition(), name, parent, principal);
      Definition stored = MAPPER.toWire(dto, parent != null);
      // an extended model lists what it inherits wherever the server puts it: compare the set
      String problem = WizLogicalModelReadBack.diff(written, stored, parent != null);

      if(problem != null) {
         throw fail(WizDataModelWire.READ_BACK_FAILED, "definition",
                    "the server reported success but \"" + database + "/" + name +
                       "\" is not as written: " + problem);
      }

      return stored;
   }

   /** The entities and attributes flagged as inherited, with nothing else. */
   private static Definition inheritedOnly(Definition current) {
      List<Entity> entities = new ArrayList<>();

      for(Entity entity : current.entities()) {
         List<Attribute> inherited = new ArrayList<>();

         for(Attribute attribute : entity.attributes()) {
            if(Boolean.TRUE.equals(attribute.baseElement())) {
               inherited.add(attribute);
            }
         }

         if(Boolean.TRUE.equals(entity.baseElement()) || !inherited.isEmpty()) {
            entities.add(new Entity(entity.name(), entity.oldName(), entity.description(),
                                    entity.visible(), entity.baseElement(), null, inherited));
         }
      }

      return new Definition(current.name(), current.description(), current.view(),
                            current.folder(), current.connection(), current.parent(),
                            current.extended(), entities);
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
      // an extended model's permission path is under its base model's folder
      String storedFolder = blankToNull(stored.getBaseModel() != null ?
                                           stored.getBaseModel().getFolder() : stored.getFolder());

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

   private List<ElementDependents> visibleElements(XDataModel dataModel, String database,
                                                   Principal principal,
                                                   List<ElementDependents> elements)
   {
      List<ElementDependents> result = new ArrayList<>();

      for(ElementDependents element : elements) {
         result.add(new ElementDependents(
            element.element(), visibleOnly(dataModel, database, principal,
                                           element.dependents())));
      }

      return result;
   }

   /**
    * Drops the logical models, views and VPMs the caller may not see from a dependent list; the
    * dropped ones leave one nameless entry with their count, so a change that has dependents
    * still shows (and a confirm still binds to the full set through the count).
    */
   List<WizDataModelWire.Dependent> visibleOnly(XDataModel dataModel, String database,
                                                Principal principal,
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
