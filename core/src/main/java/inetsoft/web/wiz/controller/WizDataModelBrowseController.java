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
import inetsoft.uql.erm.XDataModel;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.model.database.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

import static inetsoft.web.wiz.controller.WizDataModelSupport.*;
import static inetsoft.web.wiz.controller.WizDataModelWire.*;

/**
 * Browsing the data models of a database (JDBC) data source: {@code /browse} lists or searches the
 * physical views, logical models, VPMs and folders, {@code /physical/tables} lists the tables and
 * columns of a physical view (the picker a logical model author needs).
 *
 * <p>Visibility is the Portal's: the services filter what the caller may see, so an unreadable
 * source lists nothing. {@code /physical/tables} adds its own READ check on the source because the
 * database tree leaks to a caller without it (probe (c)). Never declares a local
 * {@code @ExceptionHandler}, which would beat {@code WizControllerErrorHandler}.</p>
 */
@RestController
@RequestMapping("/api/wiz/data-models")
public class WizDataModelBrowseController {
   @Autowired
   public WizDataModelBrowseController(DatabaseModelBrowserService browserService,
                                       DataSourceService dataSourceService,
                                       LogicalModelService logicalModelService,
                                       LogicalModelTreeService treeService,
                                       XRepository repository)
   {
      this.browserService = browserService;
      this.dataSourceService = dataSourceService;
      this.treeService = treeService;
      this.source = new WizDataModelSource(repository);
      this.gates = new WizDataModelGates(dataSourceService, logicalModelService);
   }

   @PostMapping(value = "/browse", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object browse(@RequestBody BrowseRequest request, Principal principal) throws Exception {
      String database = requireText("database", request.database());
      Set<String> kinds = parseKinds(request.kinds());
      int limit = parseLimit(request.limit());

      return WizDataModelErrors.run(() -> doBrowse(request, database, kinds, limit, principal));
   }

   @PostMapping(value = "/physical/tables", produces = MediaType.APPLICATION_JSON_VALUE)
   public Object physicalTables(@RequestBody PhysicalTablesRequest request, Principal principal)
      throws Exception
   {
      String database = requireText("database", request.database());
      String view = requireText("view", request.view());

      return WizDataModelErrors.run(() -> {
         // the gates run first: a source or view the caller may not see is answered like a
         // missing one (review R1-4)
         XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);

         if(!gates.maySee(dataModel, database, KIND_PHYSICAL_VIEW, view, principal)) {
            throw WizDataModelGates.notFound(KIND_PHYSICAL_VIEW, "view", database, view);
         }

         gates.requireEditAccess(database, request.connection(), principal);

         TreeNodeModel root = treeService.getPhysicalModelTree(
            database, view, null, null, request.connection());
         List<PhysicalTable> tables = new ArrayList<>();

         for(TreeNodeModel table : root.children()) {
            List<PhysicalColumn> columns = new ArrayList<>();

            for(TreeNodeModel column : table.children()) {
               if(column.data() instanceof XAttributeModel attribute) {
                  columns.add(new PhysicalColumn(attribute.getName(), attribute.getDataType()));
               }
            }

            tables.add(new PhysicalTable(table.label(), columns));
         }

         return new PhysicalTablesResponse(database, view, tables);
      });
   }

   private BrowseResponse doBrowse(BrowseRequest request, String database, Set<String> kinds,
                                   int limit, Principal principal)
      throws Exception
   {
      XDataModel dataModel = gates.requireVisibleJdbcDataModel(source, database, principal);
      String folder = folderOrRoot(request.folder());
      String query = request.query() == null || request.query().isBlank() ? null :
         request.query();

      // a folder the caller may not read is answered like a missing one, not as an empty list
      if(folder != null && !gates.maySee(dataModel, database, KIND_FOLDER, folder, principal)) {
         throw new WizDataModelErrors.DmFailure(
            FOLDER_NOT_FOUND, "folder",
            "no data-model folder \"" + folder + "\"; create it with manage_data_model_folder");
      }

      AssetListBrowseModel listing = query == null ?
         browserService.getDataModelBrowseModel(database, folder, principal, false).getListModel() :
         browserService.getSearchDataModel(database, folder, query, principal).getListModel();
      List<AssetItem> assets = new ArrayList<>();

      if(listing.getItems() != null) {
         assets.addAll(Arrays.asList(listing.getItems()));
      }

      if(folder == null && kinds.contains(KIND_VPM)) {
         AssetListBrowseModel vpms = query == null ?
            dataSourceService.getVpmBrowseModel(database, principal) :
            dataSourceService.getSearchVpmBrowseModel(database, query, principal);

         if(vpms.getItems() != null) {
            assets.addAll(Arrays.asList(vpms.getItems()));
         }
      }

      List<BrowseFolder> folders = new ArrayList<>();
      List<BrowseItem> items = new ArrayList<>();

      for(AssetItem asset : assets) {
         if(asset instanceof DataModelFolder f) {
            if(kinds.contains(KIND_FOLDER)) {
               folders.add(new BrowseFolder(
                  f.getName(), f.isEditable(), f.isDeletable(), f.getCreatedBy(), date(f)));
            }
         }
         else if(asset instanceof PhysicalModel view) {
            if(kinds.contains(KIND_PHYSICAL_VIEW)) {
               items.add(item(KIND_PHYSICAL_VIEW, view, view.getFolderName(), false, null));

               if(view.getExtendViews() != null) {
                  for(PhysicalModel child : view.getExtendViews()) {
                     items.add(item(KIND_PHYSICAL_VIEW, child, view.getFolderName(), true,
                                    view.getName()));
                  }
               }
            }
         }
         else if(asset instanceof LogicalModel model) {
            if(kinds.contains(KIND_LOGICAL_MODEL)) {
               items.add(item(KIND_LOGICAL_MODEL, model, model.getFolderName(), false, null));

               if(model.getExtendModels() != null) {
                  for(LogicalModel child : model.getExtendModels()) {
                     items.add(item(KIND_LOGICAL_MODEL, child, model.getFolderName(), true,
                                    model.getName()));
                  }
               }
            }
         }
         else if(asset instanceof Vpm vpm) {
            if(kinds.contains(KIND_VPM)) {
               items.add(item(KIND_VPM, vpm, null, false, null));
            }
         }
      }

      Comparator<String> byName = String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
      folders.sort((a, b) -> byName.compare(a.name(), b.name()));
      items.sort(Comparator.comparing(BrowseItem::name, byName).thenComparing(BrowseItem::kind));
      int total = items.size();

      if(items.size() > limit) {
         items = new ArrayList<>(items.subList(0, limit));
      }

      return new BrowseResponse(database, folder, query, folders, items, total);
   }

   private static BrowseItem item(String kind, AssetItem asset, String folder, boolean extended,
                                  String parent)
   {
      return new BrowseItem(kind, asset.getName(), folder, asset.getDescription(), extended,
                            parent, asset.isEditable(), asset.isDeletable(),
                            asset.getCreatedBy(), date(asset));
   }

   private static Long date(AssetItem asset) {
      return asset.getCreatedDate() > 0 ? asset.getCreatedDate() : null;
   }

   private static Set<String> parseKinds(List<String> kinds) {
      Set<String> result = new LinkedHashSet<>();

      if(kinds == null || kinds.isEmpty()) {
         result.addAll(List.of(KIND_PHYSICAL_VIEW, KIND_LOGICAL_MODEL, KIND_VPM, KIND_FOLDER));
         return result;
      }

      for(int i = 0; i < kinds.size(); i++) {
         result.add(requireKind("kinds[" + i + "]", kinds.get(i)));
      }

      return result;
   }

   private static int parseLimit(Integer limit) {
      if(limit == null) {
         return DEFAULT_LIMIT;
      }

      if(limit < 1) {
         throw new IllegalArgumentException("limit: must be at least 1, got " + limit);
      }

      return Math.min(limit, MAX_LIMIT);
   }

   private static final int DEFAULT_LIMIT = 200;
   private static final int MAX_LIMIT = 1000;
   private final DatabaseModelBrowserService browserService;
   private final DataSourceService dataSourceService;
   private final LogicalModelTreeService treeService;
   private final WizDataModelSource source;
   private final WizDataModelGates gates;
}
