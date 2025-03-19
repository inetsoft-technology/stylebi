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
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.util.*;
import inetsoft.util.log.LogContext;
import inetsoft.web.portal.data.DataSourceConnectionStatusRequest;
import inetsoft.web.portal.data.DataSourceStatus;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class DataSourceStatusService {
   @Autowired
   public DataSourceStatusService(XRepository repository) {
      this.repository = repository;
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

         final Thread thread = new Thread(() -> {
            XDataSource.Status status = null;
            LogContext.setUser(ThreadContext.getContextPrincipal());
            MDC.put("DATA_SOURCE", paths.get(idx));

            try {
               status = getDataSourceConnectionStatus(paths.get(idx), request.updateStatus());
            }
            catch(Exception ex) {
               String errorMessage = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
               status = new XDataSource.Status(errorMessage, false,
                                               System.currentTimeMillis());
            }

            statuses[idx] = status;
         });

         thread.start();
         threads.add(thread);
      }

      for(Thread thread : threads) {
         thread.join();
      }

      // save all data sources with the new status once all of them have finished the checks
      if(request.updateStatus()) {
         for(int i = 0; i < paths.size(); i++) {
            XDataSource dataSource = repository.getDataSource(paths.get(i));

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
      XDataSource dataSource = repository.getDataSource(path);

      if(dataSource == null) {
         throw new FileNotFoundException(path);
      }

      if(updateStatus) {
         updateStatus(dataSource);
      }

      return dataSource.getStatus();
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
      SimpleDateFormat format = new SimpleDateFormat(SreeEnv.getProperty("format.date.time"));
      format.setTimeZone(TimeZone.getTimeZone(timeZone));
      String time = format.format(new Date(status.getLastUpdateTime()));
      String message;

      if(status.isConnected()) {
         message = catalog.getString("data.datasources.dataSourceConnected", time);
      }
      else {
         message = catalog.getString("data.datasources.dataSourceError");

         if(errorMessage.contains("username") || errorMessage.contains("password")) {
            message = catalog.getString("data.datasources.loginError");
         }
         else if(errorMessage.contains("network adapter could not establish")) {
            message = catalog.getString("data.datasources.networkError");
         }
         else {
            message += ": " + errorMessage;
         }

         message += " " + time;
      }

      return DataSourceStatus.builder()
         .message(message)
         .connected(status.isConnected())
         .build();
   }

   private final XRepository repository;
   private static final Logger LOG = LoggerFactory.getLogger(DataSourceStatusService.class);
}
