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
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.model.database.AssetItem;
import inetsoft.web.portal.model.database.LogicalModel;
import inetsoft.web.portal.model.database.PhysicalModel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

import static inetsoft.web.wiz.controller.WizDataModelSupport.*;
import static inetsoft.web.wiz.controller.WizDataModelWire.*;

/**
 * Data model folder create and rename, and moving models and views between folders.
 *
 * <p>Every write is followed by a read of the stored result: a rename or move that did not take
 * effect is reported as a failure, never as success. The Portal's own folder rename swallows its
 * exceptions (design H-B), so the strict variant is called here. Single-object routes throw
 * {@code SecurityException} (a 403); {@code /move} reports an item's denial per item. Never
 * declares a local {@code @ExceptionHandler}.</p>
 */
@RestController
@RequestMapping("/api/wiz/data-models")
public class WizDataModelFolderController {
   @Autowired
   public WizDataModelFolderController(DatabaseModelBrowserService browserService,
                                       DataModelFolderManagerService folderManagerService,
                                       DataSourceService dataSourceService,
                                       LogicalModelService logicalModelService,
                                       SecurityEngine securityEngine, XRepository repository)
   {
      this.browserService = browserService;
      this.folderManagerService = folderManagerService;
      this.dataSourceService = dataSourceService;
      this.logicalModelService = logicalModelService;
      this.securityEngine = securityEngine;
      this.repository = repository;
      this.source = new WizDataModelSource(repository);
      this.gates = new WizDataModelGates(dataSourceService, logicalModelService);
   }

   @PostMapping(value = "/folders/create", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object createFolder(@RequestBody FolderCreateRequest request, Principal principal)
      throws Exception
   {
      String database = requireText("database", request.database());
      String name = requireText("name", request.name());
      WizDataModelNames.requireValidFolderName(name);

      return WizDataModelErrors.run(() -> WizDataModelLocks.withDatabase(database, () -> {
         gates.requireVisibleJdbcDataModel(source, database, principal);

         // the grant first: only a caller who may create the folder learns that it exists
         if(!securityEngine.checkPermission(principal, ResourceType.DATA_MODEL_FOLDER,
                                            database + "/" + name, ResourceAction.WRITE))
         {
            throw new SecurityException("Unauthorized access to resource \"" + database + "/" +
                                           name + "\" by user " + principal);
         }

         if(browserService.dataModelFolderDuplicateCheck(database, name)) {
            throw new WizDataModelErrors.DmFailure(
               DUPLICATE_NAME, "name", "a folder named \"" + name + "\" already exists in " +
               database + "; nothing changed", null, KIND_FOLDER);
         }

         folderManagerService.setDataModelFolder(database, name, principal);

         if(!hasFolder(repository.getDataModel(database), name)) {
            throw new WizDataModelErrors.DmFailure(
               READ_BACK_FAILED, "name", "the server reported success but the folder \"" + name +
               "\" does not exist");
         }

         return new FolderCreateResponse(true, name);
      }));
   }

   @PostMapping(value = "/folders/rename", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object renameFolder(@RequestBody FolderRenameRequest request, Principal principal)
      throws Exception
   {
      String database = requireText("database", request.database());
      String name = requireText("name", request.name());
      String newName = requireText("newName", request.newName());
      WizDataModelNames.requireValidFolderName(name);
      WizDataModelNames.requireValidFolderName(newName);

      return WizDataModelErrors.run(() -> WizDataModelLocks.withDatabase(database, () -> {
         XDataModel before = gates.requireVisibleJdbcDataModel(source, database, principal);

         // a folder the caller may not read is answered like a missing one
         if(!gates.maySee(before, database, KIND_FOLDER, name, principal)) {
            throw WizDataModelGates.notFound(KIND_FOLDER, "name", database, name);
         }

         // the grants of the service, asked first so the clash check below is only answered to a
         // caller who may rename to that name
         requireFolderGrant(database, name, ResourceAction.WRITE, principal);
         requireFolderGrant(database, name, ResourceAction.DELETE, principal);
         requireFolderGrant(database, newName, ResourceAction.WRITE, principal);

         // the service merges into an existing folder and drops the old entry (design F10)
         if(!name.equals(newName) && hasFolder(before, newName)) {
            throw new WizDataModelErrors.DmFailure(
               DUPLICATE_NAME, "newName", "a folder named \"" + newName + "\" already exists in " +
               database + "; nothing changed", null, KIND_FOLDER);
         }

         List<Ref> contained = contents(before, name);
         browserService.renameDataModelFolderStrict(database, newName, name, principal);

         XDataModel after = repository.getDataModel(database);

         if(!name.equals(newName) && (hasFolder(after, name) || !hasFolder(after, newName))) {
            throw new WizDataModelErrors.DmFailure(
               READ_BACK_FAILED, "newName",
               "the server reported success but the folder list is not as written");
         }

         for(Ref ref : contained) {
            String stored = storedFolder(after, ref.kind(), ref.name());

            if(!newName.equals(stored)) {
               throw new WizDataModelErrors.DmFailure(
                  READ_BACK_FAILED, "name", "the server reported success but " + ref.kind() +
                  " \"" + ref.name() + "\" is stored in folder \"" + stored + "\"");
            }
         }

         List<Ref> visible = new ArrayList<>();

         for(Ref ref : contained) {
            if(gates.maySee(after, database, ref.kind(), ref.name(), principal)) {
               visible.add(ref);
            }
         }

         return new FolderRenameResponse(true, newName, visible);
      }));
   }

   @PostMapping(value = "/move", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object move(@RequestBody MoveRequest request, Principal principal) throws Exception {
      String database = requireText("database", request.database());
      String folder = folderOrRoot(request.folder());

      if(request.items() == null || request.items().isEmpty()) {
         throw new IllegalArgumentException("items is required");
      }

      for(int i = 0; i < request.items().size(); i++) {
         Ref ref = request.items().get(i);
         requireText("items[" + i + "].name", ref == null ? null : ref.name());
         requireKind("items[" + i + "].kind", ref.kind());
      }

      return WizDataModelErrors.run(() -> WizDataModelLocks.withDatabase(database, () -> {
         XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);

         if(folder != null && !gates.maySee(dataModel, database, KIND_FOLDER, folder, principal)) {
            throw new WizDataModelErrors.DmFailure(
               FOLDER_NOT_FOUND, "folder",
               "no data-model folder \"" + folder + "\"; create it with manage_data_model_folder");
         }

         requireDestinationWrite(database, folder, principal);
         MoveResult[] results = new MoveResult[request.items().size()];
         List<Integer> movable = new ArrayList<>();

         for(int i = 0; i < results.length; i++) {
            results[i] = precheck(dataModel, database, request.items().get(i), folder, principal);

            if(results[i] == null) {
               movable.add(i);
            }
         }

         boolean moved = false;

         for(int i : movable) {
            Ref ref = request.items().get(i);
            String from = storedFolder(dataModel, ref.kind(), ref.name());

            if(Objects.equals(from, folder)) {
               results[i] = new MoveResult(ref.kind(), ref.name(), true, null, null, from, folder);
               continue;
            }

            AssetItem item = KIND_LOGICAL_MODEL.equals(ref.kind()) ? new LogicalModel() :
               new PhysicalModel();
            item.setName(ref.name());

            try {
               browserService.moveDataModels(database, List.of(item), folder == null ? "/" :
                                                folder, principal);
               moved = true;
               results[i] = new MoveResult(ref.kind(), ref.name(), true, null, null, from, folder);
            }
            catch(SecurityException e) {
               results[i] = failed(ref, PERMISSION_DENIED, e.getMessage());
            }
         }

         // the registry write of a moved model persists on its own (probe (b)); the data model
         // is written once more after the batch so the folder list and any cache see the result
         // nothing moved, nothing written: no write of a snapshot that may be stale
         if(moved) {
            repository.updateDataModel(dataModel);
         }

         XDataModel after = repository.getDataModel(database);

         for(int i : movable) {
            Ref ref = request.items().get(i);

            if(results[i] != null && results[i].ok()) {
               String stored = storedFolder(after, ref.kind(), ref.name());

               if(!Objects.equals(stored, folder)) {
                  results[i] = failed(ref, READ_BACK_FAILED, "the server reported success but " +
                     ref.kind() + " \"" + ref.name() + "\" is stored in folder \"" + stored +
                     "\"");
               }
            }
         }

         return new MoveResponse(Arrays.asList(results));
      }));
   }

   private void requireFolderGrant(String database, String folder, ResourceAction action,
                                   Principal principal)
      throws Exception
   {
      if(!securityEngine.checkPermission(principal, ResourceType.DATA_MODEL_FOLDER,
                                         database + "/" + folder, action))
      {
         throw new SecurityException("Unauthorized access to resource \"" + database + "/" +
                                        folder + "\" by user " + principal);
      }
   }

   private void requireDestinationWrite(String database, String folder, Principal principal)
      throws Exception
   {
      if(folder == null) {
         if(!dataSourceService.checkPermission(database, ResourceAction.WRITE, principal)) {
            throw new SecurityException(
               "Unauthorized access to resource \"" + database + "\" by user " + principal);
         }
      }
      else if(!securityEngine.checkPermission(principal, ResourceType.DATA_MODEL_FOLDER,
                                              database + "/" + folder, ResourceAction.WRITE))
      {
         throw new SecurityException(
            "Unauthorized access to resource \"" + database + "/" + folder + "\" by user " +
               principal);
      }
   }

   /** Returns the failure of an item that cannot be moved, or null when it may be. */
   private MoveResult precheck(XDataModel dataModel, String database, Ref ref, String destination,
                               Principal principal)
      throws Exception
   {
      if(!StringUtils.isEmpty(ref.parent())) {
         return failed(ref, INVALID, "extended models and views move with their base");
      }

      if(KIND_FOLDER.equals(ref.kind()) || KIND_VPM.equals(ref.kind())) {
         return failed(ref, INVALID, "only logical models and physical views can be moved; " +
            (KIND_FOLDER.equals(ref.kind()) ? "a folder" : "a VPM") + " stays where it is");
      }

      // an item the caller may not see is answered like a missing one, naming no kind
      String existing = gates.visibleKindOfName(dataModel, database, ref.name(), principal);

      if(existing == null) {
         return failed(ref, NOT_FOUND, "no " + ref.kind() + " named \"" + ref.name() + "\" in " +
            database);
      }

      if(!existing.equals(ref.kind())) {
         return failed(ref, WRONG_KIND, "\"" + ref.name() + "\" is a " + existing + ", not a " +
            ref.kind());
      }

      String stored = storedFolder(dataModel, ref.kind(), ref.name());

      if(KIND_LOGICAL_MODEL.equals(ref.kind())) {
         for(ResourceAction action : List.of(ResourceAction.WRITE, ResourceAction.DELETE)) {
            if(!logicalModelService.checkPermission(
               database, stored, ref.name(), null, action, principal))
            {
               return failed(ref, PERMISSION_DENIED, "the caller lacks " + action +
                  " on logical model \"" + database + "/" + ref.name() + "\"");
            }
         }
      }
      else if(!dataSourceService.checkPermission(database, stored, ResourceAction.WRITE, principal)) {
         return failed(ref, PERMISSION_DENIED, "the caller lacks WRITE on physical view \"" +
            database + "/" + ref.name() + "\"");
      }

      return null;
   }

   private static MoveResult failed(Ref ref, String reason, String message) {
      return new MoveResult(ref.kind(), ref.name(), false, reason, message, null, null);
   }

   private final DatabaseModelBrowserService browserService;
   private final DataModelFolderManagerService folderManagerService;
   private final DataSourceService dataSourceService;
   private final LogicalModelService logicalModelService;
   private final SecurityEngine securityEngine;
   private final XRepository repository;
   private final WizDataModelSource source;
   private final WizDataModelGates gates;
}
