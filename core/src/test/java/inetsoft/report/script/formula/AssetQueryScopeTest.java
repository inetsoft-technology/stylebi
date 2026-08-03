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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.test.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/script/viewsheet/ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
public class AssetQueryScopeTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private ViewsheetSandbox sandbox;
   private AssetQuerySandbox wbox;

   @BeforeEach
   void setUp() {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      sandbox = rvs.getViewsheetSandbox().orElseThrow();
      wbox = sandbox.getAssetQuerySandbox();
   }

   /**
    * A qualified read of a viewsheet assembly name -- e.g.
    * worksheet['TableView1'] -- is dispatched straight at the worksheet's
    * AssetQueryScope, so it has to resolve through the chain that
    * AssetQuerySandbox#createAssetQueryScope() links to the viewsheet's
    * ViewsheetScope (added by Bug #75526 so viewsheet assemblies are visible in
    * worksheet scripts). Mirrors
    * ViewsheetScopeTest#testWorksheetTableResolvedThroughChain (#75807), the
    * same defect on the other side of the viewsheet/worksheet scope link.
    */
   @Test
   void testViewsheetAssemblyResolvedThroughChain() {
      AssetQueryScope scope = new AssetQueryScope(wbox);
      String aname = Arrays.stream(sandbox.getViewsheet().getAssemblies())
         .map(Assembly::getName)
         .filter(name -> !scope.hasMember(name))
         .findFirst()
         .orElseThrow();

      // not resolvable until the viewsheet scope is chained onto this scope
      assertFalse(scope.hasMember(aname));
      assertNull(scope.getMember(aname));

      ViewsheetScope vscope = new ViewsheetScope(sandbox, false);
      JavaScriptEngine.addToPrototype(scope, vscope);

      assertTrue(scope.hasMember(aname));
      assertNotNull(scope.getMember(aname));

      // a name owned by neither scope stays absent
      assertFalse(scope.hasMember("NoSuchAssembly"));
      assertNull(scope.getMember("NoSuchAssembly"));
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ASSET_ID);
      event.setViewer(true);
      return event;
   }

   public static final String ASSET_ID = "1^128^__NULL__^ViewsheetScopeTest";
}
