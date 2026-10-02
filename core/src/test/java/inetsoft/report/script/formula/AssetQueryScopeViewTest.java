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
package inetsoft.report.script.formula;

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.Worksheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Spec §6.5 (bug #76960): in pool mode each query carries its own parameters and mode through a
 * view of the sandbox scope; everything else is still the shared scope.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssetQueryScopeViewTest {
   @Test
   void viewHasItsOwnParametersAndModeAndSharesTheRest() throws Exception {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      doReturn(new Worksheet()).when(box).getWorksheet();
      AssetQueryScope shared = new AssetQueryScope(box);
      VariableTable sharedVars = new VariableTable();
      sharedVars.put("p", 1);
      shared.setVariableTable(sharedVars);
      shared.setMode(1);

      VariableTable queryVars = new VariableTable();
      queryVars.put("p", 2);
      AssetQueryScope view = shared.queryView(queryVars, 3);

      assertSame(queryVars, view.getVariableTable());
      assertSame(sharedVars, shared.getVariableTable());
      assertEquals(3, view.getMode());
      assertEquals(1, shared.getMode());

      view.putMember("x", 5);
      assertEquals(5, shared.getMember("x"));
      shared.putMember("y", 6);
      assertEquals(6, view.getMember("y"));
      assertTrue(view.hasMember("y"));
      assertTrue(java.util.Arrays.asList(view.getMemberKeys()).contains("parameter"));
      assertSame(shared.getParentScope(), view.getParentScope());
   }
}
