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
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.util.credential.CredentialService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.model.ExpressionRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.LogicalModelService;
import inetsoft.web.portal.controller.database.VpmService;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A real data source registry behind the real {@link VpmService} and the bridge classes of the VPM
 * lane; only permissions, the dependency handler and the security provider are mocked. Each
 * instance owns a fresh data source (one empty physical view {@code pv}).
 */
final class WizVpmTestSupport {
   /** Beans every VPM lane test needs: the dependency store (mock) and the credential service. */
   @Configuration
   static class Beans {
      @Bean
      public DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }

      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }

   private static int count = 0;

   final String ds;
   final Principal bob = new Principal() {
      @Override
      public String getName() {
         return "bob";
      }

      @Override
      public String toString() {
         return "bob";
      }
   };
   final XRepository repository = mock(XRepository.class);
   final DataSourceService dataSourceService = mock(DataSourceService.class);
   final SecurityEngine securityEngine = mock(SecurityEngine.class);
   final SecurityProvider securityProvider = mock(SecurityProvider.class);
   final DependencyHandler dependencyHandler = mock(DependencyHandler.class);
   final DataRefModelFactoryService factory = new DataRefModelFactoryService(List.of(
      new AttributeRefModel.AttributeRefModelFactory(),
      new ExpressionRefModel.ExpressionRefModelFactory()));
   final LogicalModelService logicalModelService = mock(LogicalModelService.class);
   final VpmService service;
   final WizVpmMapper mapper = new WizVpmMapper(factory);
   final JDBCDataSource jdbc = new JDBCDataSource();
   boolean writeAllowed = true;

   WizVpmTestSupport() throws Exception {
      DataSourceRegistry.getRegistry().init();
      ds = "wizvpm_" + (++count);
      XDataModel dataModel = new XDataModel(ds);
      XPartition view = new XPartition("pv");
      view.addTable("ORDERS", null, null);
      dataModel.addPartition(view);
      DataSourceRegistry.getRegistry().clearCache();
      jdbc.setName(ds);

      when(repository.getDataSource(anyString())).thenReturn(jdbc);
      when(dataSourceService.getDataSource(anyString())).thenReturn(jdbc);
      when(repository.getDataModel(anyString())).thenAnswer(i -> fresh(i.getArgument(0)));
      when(dataSourceService.checkPermission(anyString(), any(ResourceAction.class),
                                             any(Principal.class)))
         .thenAnswer(i -> writeAllowed);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(i -> i.getArgument(0));
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(logicalModelService.checkPermission(any(), any(), any(), any(), any(), any()))
         .thenReturn(true);
      service = new VpmService(factory, dataSourceService, repository, securityEngine,
                               dependencyHandler, mock(RenameTransformHandler.class));
   }

   /** A controller over the real service; the source guard needs a JDBC source in the mock. */
   WizVpmController controller() {
      return new WizVpmController(repository, dataSourceService, logicalModelService,
                                  service, factory, securityEngine);
   }

   XDataModel fresh() {
      return fresh(ds);
   }

   private static XDataModel fresh(String name) {
      DataSourceRegistry.getRegistry().clearCache();
      return new XDataModel(name);
   }
}
