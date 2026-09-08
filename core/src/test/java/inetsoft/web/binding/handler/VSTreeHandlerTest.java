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

package inetsoft.web.binding.handler;

import inetsoft.report.composition.AssetTreeModel;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class VSTreeHandlerTest {
   @Mock VSChartHandler chartHandler;
   @Mock SecurityEngine securityEngine;
   @Mock AssetRepository engine;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock TableDataVSAssemblyInfo cinfo;
   @Mock Principal principal;

   private VSTreeHandler handler;

   @BeforeEach
   public void setUp() {
      handler = new VSTreeHandler(chartHandler, securityEngine);
   }

   /**
    * getChartTreeModel() already guards against a concurrently-disposed/reset viewsheet or a
    * concurrently-removed assembly (see its own null checks and comment). getTableTreeModel()
    * previously had no equivalent guard and threw an unguarded NPE at
    * {@code cass.getViewsheet()} whenever the named assembly could not be resolved -- a defect
    * found while investigating Bug #76488/#76485 (see docs/teams/2026-09-07-bug-76488-76485/).
    * This does not confirm those two bugs' root cause (no reproduction was ever achieved), but
    * the missing guard itself is real and independently worth closing.
    */
   @Test
   public void getTableTreeModel_returnsNull_whenAssemblyCannotBeResolved() throws Exception {
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(cinfo.getAbsoluteName()).thenReturn("Crosstab1");
      when(viewsheet.getAssembly("Crosstab1")).thenReturn(null);

      AssetTreeModel result = handler.getTableTreeModel(engine, rvs, cinfo, principal);

      assertNull(result);
   }

   @Test
   public void getTableTreeModel_returnsNull_whenRuntimeViewsheetHasNoViewsheet() throws Exception {
      when(rvs.getViewsheet()).thenReturn(null);
      when(cinfo.getAbsoluteName()).thenReturn("Crosstab1");

      AssetTreeModel result = handler.getTableTreeModel(engine, rvs, cinfo, principal);

      assertNull(result);
   }
}
