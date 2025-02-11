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

package inetsoft.web.composer.ws.dialog;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.ignite.IgniteCluster;
import inetsoft.web.composer.model.ws.SQLQueryDialogModel;
import org.apache.ignite.Ignite;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.resources.SpringApplicationContextResource;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
public class SQLQueryDialogServiceProxy {
   public static final class GetModelCallable implements IgniteCallable<SQLQueryDialogModel> {
      public GetModelCallable(String runtimeId, String tableName, String dataSource, Principal principal) {
         this.runtimeId = runtimeId;
         this.tableName = tableName;
         this.dataSource = dataSource;
         this.principal = principal;
      }

      @Override
      public SQLQueryDialogModel call() throws Exception {
         SQLQueryDialogService service = applicationContext.getBean(SQLQueryDialogService.class);
         return service.getModel(runtimeId, tableName, dataSource, principal);
      }

      @SpringApplicationContextResource
      private ApplicationContext applicationContext;

      private final String runtimeId;
      private final String tableName;
      private final String dataSource;
      private final Principal principal;
   }

   public SQLQueryDialogModel getModel(String runtimeId, String tableName, String dataSource, Principal principal) {
      Ignite ignite = ((IgniteCluster) Cluster.getInstance()).getIgniteInstance();
      return ignite.compute().affinityCall("runtimeSheets", runtimeId, new GetModelCallable(runtimeId, tableName, dataSource, principal));
   }
}
