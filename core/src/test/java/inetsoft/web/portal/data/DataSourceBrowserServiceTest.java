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
package inetsoft.web.portal.data;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.web.admin.content.repository.RepositoryObjectService;
import inetsoft.web.portal.controller.database.DataSourceService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for the em.locale crash: a malformed em.locale value (one
 * Catalog.parseLocale rejects) must not break data-source browsing for a non-SRPrincipal
 * caller -- getLocale must catch the parse failure and fall back to the default locale.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class DataSourceBrowserServiceTest {
   @Mock private SecurityEngine securityEngine;
   @Mock private RepositoryObjectService repositoryObjectService;
   @Mock private XRepository repository;
   @Mock private DataSourceService dataSourceService;
   @Mock private DataSourceRegistry dataSourceRegistry;
   @Mock private Config uqlConfig;
   @Mock private RenameTransformHandler renameTransformHandler;
   @Mock private Principal principal;

   private MockedStatic<SreeEnv> sreeEnvStatic;
   private DataSourceBrowserService service;

   @BeforeEach
   void setUp() {
      sreeEnvStatic = mockStatic(SreeEnv.class);
      sreeEnvStatic.when(() -> SreeEnv.getProperty("em.locale")).thenReturn("en-US");

      service = new DataSourceBrowserService(securityEngine, repositoryObjectService, repository,
                                              dataSourceService, dataSourceRegistry, uqlConfig,
                                              renameTransformHandler);
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
   }

   @Test
   void getDataSources_malformedEmLocale_doesNotThrow() throws Exception {
      when(dataSourceRegistry.getSubDataSourceNames("/folder", false)).thenReturn(new ArrayList<>());
      when(dataSourceRegistry.getSubfolderNames("/folder")).thenReturn(new ArrayList<>());

      assertDoesNotThrow(
         () -> assertEquals(0, service.getDataSources("/folder", false, null, principal).size()));
   }

   @Test
   void getAllSubDataSources_malformedEmLocale_doesNotThrow() throws Exception {
      when(dataSourceRegistry.getSubDataSourceNames("/folder", true)).thenReturn(new ArrayList<>());
      when(dataSourceRegistry.getSubfolderNames("/folder", true)).thenReturn(new ArrayList<>());

      assertDoesNotThrow(
         () -> assertEquals(0, service.getAllSubDataSources("/folder", principal).size()));
   }
}
