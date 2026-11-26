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
package inetsoft.web.wiz.controller;

import inetsoft.sree.security.*;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.LogicalModelService;
import inetsoft.web.portal.controller.database.VpmService;
import inetsoft.web.portal.model.database.VPMDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.function.Function;

import static inetsoft.web.wiz.controller.WizDataModelSupport.*;
import static inetsoft.web.wiz.controller.WizDataModelWire.*;

/**
 * {@code /vpm/get}, {@code /vpm/create}, {@code /vpm/update} and {@code /vpm/test} of the data
 * model bridge (design 4.1, 4.2). Every route needs WRITE on the data source (the rule of the
 * Portal's VPM editor, reads included); the work is {@link VpmService}'s, so audit, dependency
 * records and the registry write are the Portal's. A create checks the one name space of the data
 * source, an update refuses a definition named differently from its target, and every write is
 * read back and compared with what was sent.
 *
 * <p>Business outcomes are {@code 200 {ok:false, reason, message}}; a denial is a
 * {@code SecurityException} (403) and a malformed request an {@code IllegalArgumentException}
 * (400). Never declares a local {@code @ExceptionHandler}.</p>
 */
@RestController
@RequestMapping("/api/wiz/data-models")
public class WizVpmController {
   @Autowired
   public WizVpmController(XRepository repository, DataSourceService dataSourceService,
                           LogicalModelService logicalModelService, VpmService vpmService,
                           DataRefModelFactoryService dataRefModelFactoryService,
                           SecurityEngine securityEngine)
   {
      this.repository = repository;
      this.dataSourceService = dataSourceService;
      this.vpmService = vpmService;
      this.securityEngine = securityEngine;
      this.source = new WizDataModelSource(repository);
      this.gates = new WizDataModelGates(dataSourceService, logicalModelService);
      this.mapper = new WizVpmMapper(dataRefModelFactoryService);
      this.ops = new WizVpmOps(dataSourceService, vpmService);
   }

   @PostMapping(value = "/vpm/get", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object get(@RequestBody WizVpmWire.GetRequest request, Principal principal)
      throws Exception
   {
      String database = requireText("database", request.database());
      String name = requireText("name", request.name());

      return WizDataModelErrors.run(() -> {
         XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);
         gates.requireDataSourcePermission(database, ResourceAction.WRITE, principal);
         requireVpm(dataModel, database, name, principal);
         VirtualPrivateModel vpm = vpmService.getStored(database, name, principal);
         Map<String, Object> wire = mapper.toWire(vpm, dataSource(database));
         return new WizVpmWire.GetResponse(
            true, wire, null, false, WizDataModelRevision.of(wire));
      });
   }

   @PostMapping(value = "/vpm/create", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object create(@RequestBody WizVpmWire.WriteRequest request, Principal principal)
      throws Exception
   {
      String database = requireText("database", request.database());
      Map<String, Object> definition = requireDefinition(request.definition());
      String name = requireText("definition.name", WizVpmMapper.string(definition.get("name")));
      WizDataModelNames.requireValidModelName(name);
      boolean dryRun = Boolean.TRUE.equals(request.dryRun());

      return WizDataModelErrors.run(() -> WizDataModelLocks.withDatabase(database, () -> {
         XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);
         gates.requireDataSourcePermission(database, ResourceAction.WRITE, principal);
         // a clash names the existing kind only when the caller may see that item
         WizDataModelNames.requireUniqueInSource(
            dataModel, name,
            (kind, existing) -> gates.maySee(dataModel, database, kind, existing, principal));
         List<String> warnings = validate(
            database, dataModel, definition, WizVpmValidator.Mode.CREATE, null);

         if(dryRun) {
            return new WizVpmWire.WriteResponse(true, name, null, warnings, null);
         }

         VPMDefinition portal = mapper.toDefinition(definition, database, null);
         vpmService.create(database, portal, principal);
         Map<String, Object> readBack = readBack(database, name, definition, principal);
         return new WizVpmWire.WriteResponse(true, name, readBack, warnings, null,
                                             WizDataModelRevision.of(readBack));
      }));
   }

   @PostMapping(value = "/vpm/update", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object update(@RequestBody WizVpmWire.WriteRequest request, Principal principal)
      throws Exception
   {
      String database = requireText("database", request.database());
      String name = requireText("name", request.name());
      Map<String, Object> definition = new LinkedHashMap<>(requireDefinition(request.definition()));
      Object bodyName = definition.get("name");
      boolean dryRun = Boolean.TRUE.equals(request.dryRun());

      return WizDataModelErrors.run(() -> {
         if(bodyName != null && !name.equals(bodyName)) {
            throw new WizDataModelErrors.DmFailure(
               INVALID, "definition.name",
               "definition.name \"" + bodyName + "\" differs from name \"" + name +
               "\"; renaming goes through rename_data_model, not update");
         }

         return WizDataModelLocks.withDatabase(database, () -> {
            XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);
            gates.requireDataSourcePermission(database, ResourceAction.WRITE, principal);
            requireVpm(dataModel, database, name, principal);
            VirtualPrivateModel stored = vpmService.getStored(database, name, principal);
            Map<String, Object> current = mapper.toWire(stored, dataSource(database));

            // the edit was planned from a read; if the VPM is no longer what was read, nothing
            // is written (the revision is of the same wire form the get route returns)
            if(request.baseRevision() != null && !request.baseRevision().isEmpty() &&
               !request.baseRevision().equals(WizDataModelRevision.of(current)))
            {
               throw new WizDataModelErrors.DmFailure(
                  STALE_MODEL, "baseRevision", "VPM " + name + " changed since it was read; " +
                  "read it again and redo the edit; nothing was written");
            }

            // an omitted key keeps its stored value (an explicit null clears it)
            Map<String, Object> merged = new LinkedHashMap<>(current);
            merged.putAll(definition);
            merged.put("name", name);
            List<String> warnings = validate(
               database, dataModel, merged, WizVpmValidator.Mode.UPDATE, stored);
            List<Dependent> dependents = dependents(database, dataModel, name, principal);

            if(dryRun) {
               return new WizVpmWire.WriteResponse(true, name, null, warnings, dependents);
            }

            VPMDefinition portal = mapper.toDefinition(merged, database, stored);
            vpmService.update(database, name, portal, principal);
            Map<String, Object> readBack = readBack(database, name, merged, principal);
            return new WizVpmWire.WriteResponse(true, name, readBack, warnings, dependents,
                                                WizDataModelRevision.of(readBack));
         });
      });
   }

   @PostMapping(value = "/vpm/test", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object test(@RequestBody WizVpmWire.TestRequest request, Principal principal)
      throws Exception
   {
      String database = requireText("database", request.database());
      String type = requireText("principalType", request.principalType()).toLowerCase();
      String principalName = requireText("principalName", request.principalName());

      if(!"user".equals(type) && !"role".equals(type)) {
         throw new IllegalArgumentException(
            "principalType: expected user or role, got \"" + request.principalType() + "\"");
      }

      boolean named = request.name() != null && !request.name().isBlank();

      if(named == (request.definition() != null)) {
         throw new IllegalArgumentException(
            "give exactly one of name (a stored VPM) and definition (a draft)");
      }

      return WizDataModelErrors.run(() -> {
         XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);
         gates.requireDataSourcePermission(database, ResourceAction.WRITE, principal);
         requirePrincipal(type, principalName);
         VPMDefinition portal;

         if(named) {
            requireVpm(dataModel, database, request.name(), principal);
            VirtualPrivateModel stored = vpmService.getStored(database, request.name(), principal);
            portal = vpmService.toModel(stored, database, principal);
         }
         else {
            Map<String, Object> draft = new LinkedHashMap<>(request.definition());
            draft.putIfAbsent("name", "(draft)");
            validate(database, dataModel, draft, WizVpmValidator.Mode.DRAFT, null);
            portal = mapper.toDefinition(draft, database, null);
         }

         String result = vpmService.test(database, type, principalName, portal, principal);
         return new WizVpmWire.TestResponse(true, result);
      });
   }

   // -- helpers -----------------------------------------------------------------------------

   private static Map<String, Object> requireDefinition(Map<String, Object> definition) {
      if(definition == null) {
         throw new IllegalArgumentException("definition is required");
      }

      return definition;
   }

   /**
    * The VPM must exist and be a VPM. A name taken by another kind is WRONG_KIND only when the
    * caller may see that item; an item the caller cannot see is answered like a missing one.
    */
   private void requireVpm(XDataModel dataModel, String database, String name,
                           Principal principal)
   {
      String existing = gates.visibleKindOfName(dataModel, database, name, principal);

      if(existing == null) {
         throw WizDataModelGates.notFound("vpm", "name", database, name);
      }

      if(!KIND_VPM.equals(existing)) {
         throw new WizDataModelErrors.DmFailure(
            WRONG_KIND, "kind", "\"" + name + "\" is a " + existing + ", not a vpm", null,
            existing);
      }
   }

   private List<String> validate(String database, XDataModel dataModel,
                                 Map<String, Object> definition, WizVpmValidator.Mode mode,
                                 VirtualPrivateModel stored)
   {
      WizVpmValidator.Result result = new WizVpmValidator(subqueryCheck(database)).validate(
         dataModel, definition, mode, stored, this::roleExists);

      if(!result.errors().isEmpty()) {
         WizDataModelWire.Err first = result.errors().get(0);
         StringBuilder message = new StringBuilder("the VPM definition is invalid: ");

         for(int i = 0; i < result.errors().size() && i < 5; i++) {
            message.append(i == 0 ? "" : "; ").append(result.errors().get(i).message());
         }

         if(result.errors().size() > 5) {
            message.append("; and ").append(result.errors().size() - 5).append(" more");
         }

         throw new WizDataModelErrors.DmFailure(
            INVALID, first.field(), message.toString(), result.errors(), null);
      }

      return result.warnings();
   }

   /**
    * Reads the model back from the registry and compares it with the canonical form of what was
    * sent; a difference means a field was dropped or rewritten (decision C2).
    */
   private Map<String, Object> readBack(String database, String name,
                                        Map<String, Object> sent, Principal principal)
      throws Exception
   {
      XDataModel after = repository.getDataModel(database);
      VirtualPrivateModel stored = after == null ? null : after.getVirtualPrivateModel(name);

      if(stored == null) {
         throw new WizDataModelErrors.DmFailure(
            READ_BACK_FAILED, "name", "the server reported success but vpm \"" + name +
            "\" is not stored");
      }

      Map<String, Object> actual = mapper.toWire(stored, dataSource(database));
      String difference = firstDifference(
         mapper.normalize(sent), mapper.normalize(actual), "definition");

      if(difference != null) {
         throw new WizDataModelErrors.DmFailure(
            READ_BACK_FAILED, "definition", "the server reported success but \"" + name +
            "\" is not as written: " + difference);
      }

      return actual;
   }

   private List<Dependent> dependents(String database, XDataModel dataModel, String name,
                                      Principal principal)
      throws Exception
   {
      List<Dependent> result = new ArrayList<>();
      WizModelKindOps.Ctx ctx = new WizModelKindOps.Ctx(database, dataModel, principal);

      for(WizModelKindOps.DependentRef ref : ops.dependents(
         ctx, new WizModelKindOps.ModelRef(WizModelKindOps.Kind.VPM, name, null)))
      {
         result.add(new Dependent(ref.type(), ref.name(), ref.path(), ref.element()));
      }

      return result;
   }

   private XDataSource dataSource(String database) {
      return dataSourceService.getDataSource(database);
   }

   private boolean roleExists(String role) {
      try {
         SecurityProvider provider = securityEngine.getSecurityProvider();
         return provider == null || roleKnown(provider, role);
      }
      catch(Exception e) {
         return true;
      }
   }

   /** The organization's role of that name, else a global one such as the built-in Administrator. */
   private static boolean roleKnown(SecurityProvider provider, String role) {
      IdentityID id = new IdentityID(role, OrganizationManager.getInstance().getCurrentOrgID());
      return provider.getRole(id) != null ||
         id.getOrgID() != null && provider.getRole(new IdentityID(role, null)) != null;
   }

   /** A test against an unknown user or role would say nothing; refuse it by name first. */
   private void requirePrincipal(String type, String principalName) {
      SecurityProvider provider = securityEngine.getSecurityProvider();
      IdentityID id = new IdentityID(principalName,
                                     OrganizationManager.getInstance().getCurrentOrgID());
      boolean known = provider == null || ("user".equals(type) ? provider.getUser(id) != null :
         roleKnown(provider, principalName));

      if(!known) {
         String message = "no " + type + " \"" + principalName + "\" in the current organization";
         throw new WizDataModelErrors.DmFailure(
            INVALID, "principalName", message,
            List.of(new Err("principalName", message, "PRINCIPAL_NOT_FOUND")), null);
      }
   }

   /**
    * A syntax check of a subquery's SQL through the parser the Portal's own write uses
    * ({@code JDBCUtil.createXExpression}); null when the source is not a database.
    */
   private Function<String, String> subqueryCheck(String database) {
      XDataSource source = dataSource(database);

      if(!(source instanceof JDBCDataSource)) {
         return null;
      }

      return sql -> {
         UniformSQL parsed = new UniformSQL();
         parsed.setDataSource((JDBCDataSource) source);

         try {
            synchronized(parsed) {
               parsed.setParseSQL(true);
               parsed.setSQLString(sql, true);
               parsed.wait(SUBQUERY_PARSE_MS);
            }
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            return "the SQL could not be checked";
         }

         return parsed.getParseResult() == UniformSQL.PARSE_FAILED ?
            "the SQL cannot be parsed as a select statement" : null;
      };
   }

   /** The first path at which the two canonical forms differ, or null if they are equal. */
   static String firstDifference(Object expected, Object actual, String path) {
      if(expected instanceof Map<?, ?> e && actual instanceof Map<?, ?> a) {
         Set<Object> keys = new LinkedHashSet<>(e.keySet());
         keys.addAll(a.keySet());

         for(Object key : keys) {
            String diff = firstDifference(e.get(key), a.get(key), path + "." + key);

            if(diff != null) {
               return diff;
            }
         }

         return null;
      }

      if(expected instanceof List<?> e && actual instanceof List<?> a) {
         if(e.size() != a.size()) {
            return path + " has " + a.size() + " entries, expected " + e.size();
         }

         for(int i = 0; i < e.size(); i++) {
            String diff = firstDifference(e.get(i), a.get(i), path + "[" + i + "]");

            if(diff != null) {
               return diff;
            }
         }

         return null;
      }

      return Objects.equals(expected, actual) ? null :
         path + " is " + describe(actual) + ", expected " + describe(expected);
   }

   private static String describe(Object value) {
      String text = String.valueOf(value);
      return value == null ? "absent" : "\"" + (text.length() > 80 ? text.substring(0, 80) + "..." :
         text) + "\"";
   }

   private static final long SUBQUERY_PARSE_MS = 5000L;
   private final XRepository repository;
   private final DataSourceService dataSourceService;
   private final VpmService vpmService;
   private final SecurityEngine securityEngine;
   private final WizDataModelSource source;
   private final WizDataModelGates gates;
   private final WizVpmMapper mapper;
   private final WizVpmOps ops;
}
