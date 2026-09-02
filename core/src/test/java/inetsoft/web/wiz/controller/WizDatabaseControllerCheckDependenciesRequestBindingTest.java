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

import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyException;
import inetsoft.uql.util.Config;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.portal.data.DataSourceBrowserService;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.wiz.service.EndpointCatalogReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Exercises {@link WizDatabaseController#checkOuterDependencies} through real Spring MVC request
 * binding, the way {@code WizDatabaseControllerDeleteMoveTest} cannot: that test calls
 * {@code controller.checkOuterDependencies(...)} directly with a Java object it constructs itself,
 * so it never goes through an {@code HttpMessageConverter} and cannot catch a wire-shape mismatch.
 *
 * <p>The endpoint's only real production caller (wiz-services'
 * {@code datasourceManagementService.ts}) sends {@code {"items": [...]}} -- a JSON object -- not a
 * bare JSON array. Before this fix, the endpoint's {@code @RequestBody} parameter was a bare
 * {@code WizDatasourceRef[]}, which Jackson cannot bind an object body to; every real call 400ed.
 * This test posts that exact object shape and asserts a 200 with the expected body, which would
 * fail against the pre-fix bare-array signature.</p>
 */
@Tag("core")
class WizDatabaseControllerCheckDependenciesRequestBindingTest {
   private MockMvc mvc;
   private DatasourcesService datasourcesService;

   @BeforeEach
   void setUp() {
      datasourcesService = mock(DatasourcesService.class);

      WizDatabaseController controller = new WizDatabaseController(
         mock(DataSourceBrowserService.class), mock(DataSourceStatusService.class),
         mock(DatabaseDatasourcesService.class), mock(DatabaseTypeService.class),
         mock(SecurityEngine.class), mock(Config.class), mock(XRepository.class),
         mock(EndpointCatalogReader.class), datasourcesService);

      mvc = standaloneSetup(controller).build();
   }

   @Test
   void bindsAJsonObjectBodyWithAnItemsArray() throws Exception {
      DependencyException conflict = new DependencyException(new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, "/orders", null));
      conflict.addDependency(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws1", null));
      doThrow(conflict).when(datasourcesService).checkDataSourceOuterDependencies("/orders");

      mvc.perform(post("/api/wiz/datasources/checkOuterDependencies")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"items\":[{\"path\":\"/orders\",\"name\":\"orders\",\"folder\":false}]}"))
         .andExpect(status().isOk())
         .andExpect(content().string(org.hamcrest.Matchers.containsString("/orders")));
   }
}
