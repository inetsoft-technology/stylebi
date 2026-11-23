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
package inetsoft.web.admin.content.repository;

import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82119: additionalDeletable only looked at the extended partitions and logical models of
 * an additional connection, so a physical view or logical model whose own connection is the
 * additional connection (what a data model's "connection" field writes) was not a dependency and
 * the connection was removed without force.
 */
@Tag("core")
class DatabaseDatasourcesServiceAdditionalDeletableTest {
   private static final String BASE = "S";
   private DatabaseDatasourcesService service;
   private XDataModel model;

   @BeforeEach
   void setUp() throws Exception {
      XRepository repository = mock(XRepository.class);
      DataSourceRegistry registry = mock(DataSourceRegistry.class);
      JDBCDataSource base = mock(JDBCDataSource.class);
      model = mock(XDataModel.class);
      when(model.getPartitionNames()).thenReturn(new String[0]);
      when(model.getLogicalModelNames()).thenReturn(new String[0]);

      for(String name : new String[] { "A1", "A2", "A3" }) {
         JDBCDataSource additional = mock(JDBCDataSource.class);
         when(additional.getFullName()).thenReturn(BASE + "::" + name);
         when(base.getDataSource(name)).thenReturn(additional);
      }

      when(base.getFullName()).thenReturn(BASE);
      when(repository.getDataSource(BASE)).thenReturn(base);
      when(registry.getDataModel(BASE)).thenReturn(model);
      service = new DatabaseDatasourcesService(
         mock(DatabaseTypeService.class),
         mock(SecurityEngine.class), mock(DatabaseSettingsService.class), repository,
         mock(ResourcePermissionService.class), mock(DataSourceStatusService.class),
         mock(IgniteSessionRepository.class), registry, mock(RenameTransformHandler.class));
   }

   private void view(String name, String connection) {
      XPartition view = mock(XPartition.class);
      when(view.getConnection()).thenReturn(connection);
      when(model.getPartitionNames()).thenReturn(new String[] { name });
      when(model.getPartition(name)).thenReturn(view);
   }

   private void logical(String name, String connection) {
      XLogicalModel logical = mock(XLogicalModel.class);
      when(logical.getConnection()).thenReturn(connection);
      when(model.getLogicalModelNames()).thenReturn(new String[] { name });
      when(model.getLogicalModel(name)).thenReturn(logical);
   }

   @Test
   void physicalViewBoundToTheConnectionIsADependency() throws Exception {
      view("PV151", "A1");

      assertTrue(service.additionalDeletable(BASE, "A1"));
      assertFalse(service.additionalDeletable(BASE, "A3"));
   }

   @Test
   void logicalModelBoundToTheConnectionIsADependency() throws Exception {
      logical("LM151", "A2");

      assertTrue(service.additionalDeletable(BASE, "A2"));
      assertFalse(service.additionalDeletable(BASE, "A1"));
   }

   @Test
   void unboundViewAndModelLeaveEveryConnectionRemovable() throws Exception {
      view("PV", null);
      logical("LM", null);

      assertFalse(service.additionalDeletable(BASE, "A1"));
      assertFalse(service.additionalDeletable(BASE, "A2"));
   }

   @Test
   void missingConnectionIsNotADependency() throws Exception {
      view("PV", "Gone");

      assertFalse(service.additionalDeletable(BASE, "Gone"));
   }
}
