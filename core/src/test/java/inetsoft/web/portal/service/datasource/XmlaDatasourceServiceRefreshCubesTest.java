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

package inetsoft.web.portal.service.datasource;

import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.XDomain;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.uql.xmla.*;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.portal.service.datasource.XmlaDatasourceService.CubeRefreshResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82089: loading the cube metadata of a stored XMLA data source. The endpoint is replaced by
 * an override of {@code loadCubes}; the repository is a mock that keeps what is written to it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  XmlaDatasourceServiceRefreshCubesTest.CredentialConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class XmlaDatasourceServiceRefreshCubesTest {
   @Configuration
   static class CredentialConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);

         return ctor.newInstance();
      }
   }

   @Test
   void savesTheLoadedCubesAndReportsTheCountFromTheReadBack() throws Exception {
      Fixture f = new Fixture();
      f.cubes = List.of(cube("Sales", false, null), cube("Budget", false, null));

      CubeRefreshResult result = f.service.refreshStoredCubes("olap/sales");

      assertTrue(result.ok());
      assertEquals(2, result.cubeCount());
      assertEquals("http://olap.example/xmla", result.url());
      assertEquals(2, Collections.list(f.saved.getCubes()).size());
      assertEquals("olap/sales", ((Domain) f.saved).getDataSource());
   }

   @Test
   void keepsTheOriginalOrderAndMetaInfoEditedOnTheStoredDomain() throws Exception {
      Fixture f = new Fixture();
      XMetaInfo meta = new XMetaInfo();
      meta.setProperty("edited", "yes");
      Domain old = new Domain();
      old.setDataSource("olap/sales");
      old.setCubes(List.of(cube("Sales", true, meta)));
      f.saved = old;
      f.cubes = List.of(cube("Sales", false, null));

      CubeRefreshResult result = f.service.refreshStoredCubes("olap/sales");

      assertTrue(result.ok());
      assertNotSame(old, f.saved, "the fresh domain must have been written");
      Cube cube = (Cube) f.saved.getCube("Sales");
      Dimension dim = (Dimension) cube.getDimensions().nextElement();
      assertTrue(dim.isOriginalOrder(), "the edited dimension order must survive a refresh");
      assertSame(meta, dim.getLevelAt(0).getXMetaInfo(), "level meta info must survive");
      assertSame(meta, ((Measure) cube.getMeasures().nextElement()).getXMetaInfo(),
                 "measure meta info must survive");
      verify(f.repository).updateDomain(any(XDomain.class), eq(true));
   }

   @Test
   void namesTheEndpointWhenTheConnectionFails() throws Exception {
      Fixture f = new Fixture();
      f.failure = new RuntimeException("Connection refused");

      CubeRefreshResult result = f.service.refreshStoredCubes("olap/sales");

      assertFalse(result.ok());
      assertEquals(CubeRefreshResult.CONNECTION_FAILED, result.status());
      assertEquals("http://olap.example/xmla", result.url());
      assertEquals("Connection refused", result.message());
      verify(f.repository, never()).updateDomain(any(XDomain.class), anyBoolean());
   }

   @Test
   void anEndpointWithNoCubesIsAFailureAndLeavesTheStoredDomainAlone() throws Exception {
      Fixture f = new Fixture();
      f.cubes = List.of();

      CubeRefreshResult result = f.service.refreshStoredCubes("olap/sales");

      assertEquals(CubeRefreshResult.NO_CUBES, result.status());
      verify(f.repository, never()).updateDomain(any(XDomain.class), anyBoolean());
   }

   @Test
   void aSwallowedWriteFailureIsReportedAsPersistFailed() throws Exception {
      Fixture f = new Fixture();
      f.cubes = List.of(cube("Sales", false, null));
      f.dropWrites = true;

      CubeRefreshResult result = f.service.refreshStoredCubes("olap/sales");

      assertFalse(result.ok());
      assertEquals(CubeRefreshResult.PERSIST_FAILED, result.status());
      assertEquals(0, result.cubeCount());
   }

   @Test
   void aMissingCatalogIsRefusedBeforeConnecting() throws Exception {
      Fixture f = new Fixture();
      f.source.setCatalogName("");

      CubeRefreshResult result = f.service.refreshStoredCubes("olap/sales");

      assertEquals(CubeRefreshResult.CATALOG_REQUIRED, result.status());
      assertFalse(f.loaded, "no connection should be attempted");
   }

   @Test
   void aPathWithNoXmlaSourceIsNotFound() throws Exception {
      Fixture f = new Fixture();
      when(f.repository.getDataSource("db/orders")).thenReturn(null);

      assertThrows(FileNotFoundException.class, () -> f.service.refreshStoredCubes("db/orders"));
   }

   private static Cube cube(String name, boolean originalOrder, XMetaInfo meta) {
      DimMember level = new DimMember();
      level.setName("Region");
      level.setUniqueName("[Geo].[Region]");
      level.setXMetaInfo(meta);
      Dimension dim = new Dimension();
      dim.setDimensionName("Region");
      dim.setLevels(List.of(level));
      dim.setOriginalOrder(originalOrder);
      Measure measure = new Measure();
      measure.setName("Amount");
      measure.setXMetaInfo(meta);
      Cube cube = new Cube();
      cube.setName(name);
      cube.setDimensions(List.of(dim));
      cube.setMeasures(List.of(measure));

      return cube;
   }

   private static final class Fixture {
      final XRepository repository = mock(XRepository.class);
      final XMLADataSource source = new XMLADataSource();
      Collection<?> cubes = List.of();
      Exception failure;
      boolean loaded;
      boolean dropWrites;
      XDomain saved;
      final XmlaDatasourceService service;

      Fixture() throws Exception {
         source.setName("olap/sales");
         source.setURL("http://olap.example/xmla");
         source.setCatalogName("Sales");
         when(repository.getDataSource("olap/sales")).thenReturn(source);
         when(repository.getDomain("olap/sales")).thenAnswer(inv -> saved);
         doAnswer(inv -> {
            if(!dropWrites) {
               saved = inv.getArgument(0);
            }

            return null;
         }).when(repository).updateDomain(any(XDomain.class), anyBoolean());

         service = new XmlaDatasourceService(
            repository, mock(SecurityEngine.class), null, mock(DataSourceRegistry.class),
            mock(Config.class), mock(DependencyHandler.class), null)
         {
            @Override
            protected Collection<?> loadCubes(XMLADataSource dataSource) throws Exception {
               loaded = true;

               if(failure != null) {
                  throw failure;
               }

               return cubes;
            }
         };
      }
   }
}
