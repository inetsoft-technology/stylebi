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

package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A script still can't get the asset engine when it calls getAssetRepository indirectly:
 * through a JS function Java calls back, a held function value, or a host method handed to
 * Java as a functional interface. Only Java JDK code sits between the script and AssetUtil
 * on these routes, so they must count as the script's own call. (Bug #77827)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class AssetRepositoryScriptRoutesTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private static final String REFUSED = "A script may not get the asset repository";

   private static final String FUNCS =
      "var AU = Java.type('inetsoft.uql.asset.internal.AssetUtil');" +
      "var AL = Java.type('java.util.ArrayList');" +
      "function list() { var l = new AL(); l.add(false); return l; }";

   private ViewsheetScope viewsheetScope;

   @BeforeEach
   void setUp() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox().orElseThrow();
      viewsheetScope = new ViewsheetScope(sandbox, false);
   }

   @ParameterizedTest
   @ValueSource(strings = {
      // a JS callback that Java (ArrayList.forEach) calls
      "var r; list().forEach(function(d) { r = AU.getAssetRepository(d); }); typeof r",
      // a JS comparator that Java (ArrayList.sort) calls
      "var r; var l = list(); l.add(true);" +
         "l.sort(function(a, b) { r = AU.getAssetRepository(a); return 0; }); typeof r",
      // a held function value, called plainly and through call, apply and bind
      "var g = AU.getAssetRepository; typeof g(false)",
      "typeof AU.getAssetRepository.call(null, false)",
      "typeof Function.prototype.apply.call(AU.getAssetRepository, null, [false])",
      "typeof AU.getAssetRepository.bind(null, true)()",
      // the host method itself handed to Java as a java.util.function.Function
      "var r = list().stream().map(AU.getAssetRepository).findFirst(); typeof r.get()",
      // many calls in a loop, so a hot call site gets the same answer
      "var n = 0; for(var i = 0; i < 2000; i++) { try { AU.getAssetRepository(false); }" +
         " catch(e) { n++; } } if(n !== 2000) { 'refused ' + n } else { throw '" + REFUSED +
         "' }"
   })
   void indirectRouteRefused(String script) throws Exception {
      Object result = run(script);

      assertInstanceOf(String.class, result, String.valueOf(result));
      assertTrue(((String) result).startsWith("error: "), String.valueOf(result));
      assertTrue(((String) result).contains(REFUSED), String.valueOf(result));
   }

   private Object run(String script) throws Exception {
      return viewsheetScope.execute(
         "try { " + FUNCS + script + " } catch(e) { 'error: ' + e }",
         viewsheetScope.getVSAScriptable(ViewsheetScope.VIEWSHEET_SCRIPTABLE), false);
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ViewsheetScopeTest.ASSET_ID);
      event.setViewer(true);
      return event;
   }
}
