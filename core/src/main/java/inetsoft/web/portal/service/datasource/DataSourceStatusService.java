/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.portal.service.datasource;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.AdditionalConnectionDataSource;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
import inetsoft.util.log.LogContext;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import inetsoft.web.portal.data.DataSourceConnectionStatusRequest;
import inetsoft.web.portal.data.DataSourceStatus;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class DataSourceStatusService {
   @Autowired
   public DataSourceStatusService(XRepository repository, SecurityEngine securityEngine,
                                  DataSourceRegistry dataSourceRegistry)
   {
      this.repository = repository;
      this.securityEngine = securityEngine;
      this.dataSourceRegistry = dataSourceRegistry;
   }

   public List<DataSourceStatus> getDataSourceConnectionStatuses(
      DataSourceConnectionStatusRequest request, Principal principal)
      throws Exception
   {
      final List<Thread> threads = new ArrayList<>();
      final List<String> paths = request.paths();
      final XDataSource.Status[] statuses = new XDataSource.Status[paths.size()];

      for(int i = 0; i < paths.size(); i++) {
         final int idx = i;

         // Bug #77429, an unreadable data source gets no status (a null entry), the same as
         // one that does not exist, so it is neither tested nor saved. Bug #78249, an
         // additional connection path P/add is checked as P::add.
         if(!securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE,
            ResourcePermissionService.getDataSourcePermissionName(
               paths.get(idx), dataSourceRegistry),
            ResourceAction.READ))
         {
            continue;
         }

         final Thread thread = new GroupedThread(() -> {
            XDataSource.Status status = null;
            LogContext.setUser(ThreadContext.getContextPrincipal());
            MDC.put("DATA_SOURCE", paths.get(idx));

            try {
               status = getDataSourceConnectionStatus(paths.get(idx), request.updateStatus());
            }
            catch(FileNotFoundException ex) {
               status = null;
            }
            catch(Exception ex) {
               String errorMessage = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
               status = new XDataSource.Status(errorMessage, false,
                                               System.currentTimeMillis());
            }

            statuses[idx] = status;
         }, principal);

         thread.start();
         threads.add(thread);
      }

      for(Thread thread : threads) {
         thread.join();
      }

      // save all data sources with the new status once all of them have finished the checks
      if(request.updateStatus()) {
         for(int i = 0; i < paths.size(); i++) {
            if(statuses[i] == null) {
               continue;
            }

            XDataSource dataSource = getDataSource(paths.get(i));

            if(dataSource != null) {
               dataSource.setStatus(statuses[i]);
               dataSource.setLastModified(System.currentTimeMillis());
               repository.updateDataSourceStatus(dataSource);
            }
         }
      }

      return Arrays.stream(statuses)
         .map(status -> getStatusModel(status, request.timeZone(), principal))
         .toList();
   }

   public XDataSource.Status getDataSourceConnectionStatus(String path, boolean updateStatus)
      throws Exception
   {
      XDataSource dataSource = getDataSource(path);

      if(dataSource == null) {
         throw new FileNotFoundException(path);
      }

      if(updateStatus) {
         updateStatus(dataSource);
      }

      return dataSource.getStatus();
   }

   /**
    * Gets a data source by its path. If the path is that of an additional connection, e.g.
    * {@code P/add}, its base data source is set, so that the additional connection is saved under
    * its parent and not at the top level under its bare name (Bug #77672).
    */
   @SuppressWarnings({ "rawtypes", "unchecked" })
   private XDataSource getDataSource(String path) throws Exception {
      XDataSource dataSource = repository.getDataSource(path);

      if(dataSource instanceof AdditionalConnectionDataSource additional &&
         additional.getBaseDatasource() == null && path != null &&
         !path.equals(dataSource.getFullName()) &&
         path.endsWith("/" + dataSource.getFullName()))
      {
         String parentPath =
            path.substring(0, path.length() - dataSource.getFullName().length() - 1);

         if(repository.getDataSource(parentPath) instanceof AdditionalConnectionDataSource parent) {
            additional.setBaseDatasource(parent);
         }
      }

      return dataSource;
   }

   public void updateStatus(XDataSource dataSource) throws Exception {
      boolean connected = true;
      String errorMessage = null;

      try {
         Object session = repository.bind(System.getProperty("user.name"));
         repository.testDataSource(session, dataSource, null);
      }
      catch(Exception ex) {
         if(LOG.isDebugEnabled()) {
            LOG.debug("Failed to connect to data source {}", dataSource.getFullName(), ex);
         }
         else {
            LOG.info("Failed to connect to data source {}, Reason: {}", dataSource.getFullName(),
                     ex.getMessage());
         }

         connected = false;
         errorMessage = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
      }

      XDataSource.Status status = new XDataSource.Status(errorMessage, connected,
                                                         System.currentTimeMillis());
      dataSource.setStatus(status);
   }

   public DataSourceStatus getStatusModel(XDataSource.Status status, String timeZone, Principal principal) {
      if(status == null) {
         return null;
      }

      Catalog catalog = Catalog.getCatalog(principal);
      String errorMessage = status.getErrorMessage();
      SimpleDateFormat format = Tool.createGregorianDateFormat(SreeEnv.getProperty("format.date.time"));
      format.setTimeZone(TimeZone.getTimeZone(timeZone));
      String time = format.format(new Date(status.getLastUpdateTime()));
      String message;

      if(status.isConnected()) {
         message = catalog.getString("data.datasources.dataSourceConnected", time);
      }
      else {
         String cloudError = SreeEnv.getProperty("datasource.cloudError");
         boolean cloudErrorAppended = false;

         if(errorMessage.contains("username") || errorMessage.contains("password")) {
            message = catalog.getString("data.datasources.loginError");
         }
         else {
            if(errorMessage.contains("network adapter could not establish")) {
               message = catalog.getString("data.datasources.networkError");
            }
            else {
               message = catalog.getString("data.datasources.dataSourceError") + ": " + errorMessage;
            }

            if(StringUtils.hasText(cloudError)) {
               cloudErrorAppended = true;
               message = message.trim() + "\n\n" + cloudError + "\n\n";
            }
         }

         if(!cloudErrorAppended) {
            message = message.trim() + " ";
         }

         message += time;
      }

      return DataSourceStatus.builder()
         .message(message)
         .connected(status.isConnected())
         .build();
   }

   private final XRepository repository;
   private final SecurityEngine securityEngine;
   private final DataSourceRegistry dataSourceRegistry;
   private static final Logger LOG = LoggerFactory.getLogger(DataSourceStatusService.class);
}
