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

import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.web.portal.data.DataSourceBrowserService;
import inetsoft.web.portal.data.DataSourceConnectionStatusRequest;
import inetsoft.web.portal.data.DataSourceInfo;
import inetsoft.web.portal.data.DataSourceStatus;
import inetsoft.web.portal.data.ImmutableDataSourceConnectionStatusRequest;
import inetsoft.web.portal.data.PortalDataType;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.wiz.model.WizDatasourceBrowserModel;
import inetsoft.web.wiz.model.WizDatasourceEntry;
import inetsoft.web.wiz.model.WizDatasourceStatus;
import inetsoft.web.wiz.request.WizDatasourceSearchRequest;
import inetsoft.web.wiz.request.WizDatasourceStatusRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

/**
 * Repository browsing for the wiz portal's data source management pages.
 *
 * <p>Lives under {@code /api/wiz} rather than proxying the native {@code /api/data/**} endpoints
 * because the two filters that gate those prefixes disagree: {@code CSRFFilter} exempts only
 * {@code /api/wiz/**}, while {@code AbstractSecurityFilter} authenticates a wiz caller by its
 * cookie or its path. Calling a native endpoint with a wiz token therefore succeeds for GET and
 * fails with 403 for every POST — an asymmetry that is very hard to diagnose from the client
 * side.</p>
 *
 * <p>All work is delegated to the portal services, so permission filtering, folder resolution and
 * connection probing behave exactly as they do in the native portal. Only the response shape
 * differs: raw enum names and epoch millis instead of {@code Catalog}-translated labels and
 * server-formatted dates, because the wiz portal does its own i18n.</p>
 */
@RestController
@RequestMapping("/api/wiz")
public class WizDatabaseController {
   public WizDatabaseController(DataSourceBrowserService dataSourceBrowserService,
                                DataSourceStatusService dataSourceStatusService,
                                XRepository xrepository)
   {
      this.dataSourceBrowserService = dataSourceBrowserService;
      this.dataSourceStatusService = dataSourceStatusService;
      this.xrepository = xrepository;
   }

   /**
    * Lists one folder of the data source repository.
    *
    * @param path      the folder to list. Omitted, empty or {@code "/"} lists the root.
    * @param principal the current user; entries they cannot read are omitted by the service.
    *
    * @return the folder contents plus the breadcrumb trail leading to it.
    */
   @GetMapping(value = "/datasources/browser", produces = MediaType.APPLICATION_JSON_VALUE)
   public WizDatasourceBrowserModel getDatasourceBrowser(
      @RequestParam(value = "path", required = false) String path,
      Principal principal)
      throws Exception
   {
      String folder = normalizePath(path);

      // root=false always: root=true is the move-dialog mode, which returns the single synthetic
      // root folder entry instead of the folder contents.
      List<DataSourceInfo> infos =
         dataSourceBrowserService.getDataSources(folder, false, null, principal);

      // home=true suppresses the synthetic "/" root crumb, whose label would come pre-translated
      // from the server. The client renders its own root label.
      List<DataSourceInfo> crumbs = dataSourceBrowserService.getBreadcrumbs(folder, true, principal);
      List<String> breadcrumb = new ArrayList<>();

      for(DataSourceInfo crumb : crumbs) {
         if(crumb != null) {
            breadcrumb.add(crumb.path());
         }
      }

      return new WizDatasourceBrowserModel(toEntries(infos), breadcrumb, folder == null);
   }

   /**
    * Searches data source and folder names recursively below a folder.
    *
    * @param request   the search scope and the substring to match.
    * @param principal the current user.
    *
    * @return the matching entries, each carrying its full path, best match first. Empty when no
    *         query was supplied — an empty query would otherwise match the entire repository.
    */
   @PostMapping(value = "/datasources/search", produces = MediaType.APPLICATION_JSON_VALUE)
   public List<WizDatasourceEntry> searchDatasources(
      @RequestBody WizDatasourceSearchRequest request,
      Principal principal)
      throws Exception
   {
      String query = request == null ? null : request.query();

      if(query == null || query.isBlank()) {
         return List.of();
      }

      String folder = normalizePath(request.path());

      return toEntries(dataSourceBrowserService.getSearchDataSources(folder, query, principal));
   }

   /**
    * Reports the recorded connection state of several data sources at once.
    *
    * <p>Never re-tests the connections: the underlying service would otherwise open a connection
    * to every listed data source and write the outcome back to the repository. Browsing a folder
    * must not have that side effect, so this reads the stored status only.</p>
    *
    * @param request   the data source paths to report on.
    * @param principal the current user.
    *
    * @return one status per requested path, in the order requested.
    */
   @PostMapping(value = "/datasources/statuses", produces = MediaType.APPLICATION_JSON_VALUE)
   public List<WizDatasourceStatus> getDatasourceStatuses(
      @RequestBody WizDatasourceStatusRequest request,
      Principal principal)
      throws Exception
   {
      List<String> paths = request == null ? null : request.paths();

      if(paths == null || paths.isEmpty()) {
         return List.of();
      }

      DataSourceConnectionStatusRequest statusRequest =
         ImmutableDataSourceConnectionStatusRequest.builder()
            .paths(paths)
            .updateStatus(false)
            .timeZone(TimeZone.getDefault().getID())
            .build();

      List<DataSourceStatus> statuses =
         dataSourceStatusService.getDataSourceConnectionStatuses(statusRequest, principal);
      List<WizDatasourceStatus> result = new ArrayList<>();

      // The service answers positionally and drops the path, so re-attach it by input order. A
      // data source that has never been tested yields a null status rather than a missing element.
      for(int i = 0; i < paths.size(); i++) {
         DataSourceStatus status = i < statuses.size() ? statuses.get(i) : null;
         result.add(new WizDatasourceStatus(paths.get(i), status != null && status.connected(),
                                            status == null ? null : status.message()));
      }

      return result;
   }

   private List<WizDatasourceEntry> toEntries(List<DataSourceInfo> infos) {
      List<WizDatasourceEntry> entries = new ArrayList<>();

      if(infos == null) {
         return entries;
      }

      for(DataSourceInfo info : infos) {
         if(info != null) {
            entries.add(toEntry(info));
         }
      }

      return entries;
   }

   private WizDatasourceEntry toEntry(DataSourceInfo info) {
      String nodeType = info.type() == null ? null : info.type().name();
      boolean folder = isFolder(nodeType);
      String sourceType = null;
      String databaseType = null;

      if(!folder) {
         try {
            XDataSource dataSource = xrepository.getDataSource(info.path());

            if(dataSource != null) {
               sourceType = dataSource.getType();

               if(dataSource instanceof JDBCDataSource jdbcDataSource) {
                  databaseType = jdbcDataSource.getDatabaseTypeString();
               }
            }
         }
         catch(Throwable ex) {
            // One unreadable data source — a missing driver, a broken definition — must not take
            // the whole listing down with it. The row still renders, just without a type.
            LOG.debug("Failed to resolve the type of data source {}: {}", info.path(),
                      ex.getMessage());
         }
      }

      return new WizDatasourceEntry(info.name(), info.path(), nodeType, folder, sourceType,
                                    databaseType, info.createdBy(), info.createdDate(),
                                    info.editable(), info.deletable(), info.hasSubFolder());
   }

   private static boolean isFolder(String nodeType) {
      if(nodeType == null) {
         return false;
      }

      try {
         return FOLDER_TYPES.contains(PortalDataType.valueOf(nodeType));
      }
      catch(IllegalArgumentException ex) {
         // A node type this build does not know about is treated as a leaf, which is the safe
         // default: the client will not try to descend into it.
         return false;
      }
   }

   /**
    * Maps the browser's notion of "no folder" onto the services', which take null for the root but
    * are given an empty string or a slash by clients that build the path by concatenation.
    */
   private static String normalizePath(String path) {
      if(path == null || path.isBlank() || "/".equals(path)) {
         return null;
      }

      return path;
   }

   private static final Set<PortalDataType> FOLDER_TYPES = EnumSet.of(
      PortalDataType.FOLDER,
      PortalDataType.DATA_SOURCE_FOLDER,
      PortalDataType.DATA_SOURCE_ROOT_FOLDER,
      PortalDataType.DATA_MODEL_FOLDER,
      PortalDataType.VPM_FOLDER,
      PortalDataType.PRIVATE_WORKSHEETS_FOLDER,
      PortalDataType.SHARED_WORKSHEETS_FOLDER);

   private static final Logger LOG = LoggerFactory.getLogger(WizDatabaseController.class);

   private final DataSourceBrowserService dataSourceBrowserService;
   private final DataSourceStatusService dataSourceStatusService;
   private final XRepository xrepository;
}
