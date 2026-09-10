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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.model.OpenViewsheetResult;
import inetsoft.web.wiz.service.WizVisualizationService;
import inetsoft.web.wiz.service.WizVsService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * THE BUG THIS PINS DOWN. {@code hasPreCondition} gated on {@code instanceof DataVSAssembly}, so a
 * saved Gauge/Text (OutputVSAssembly) with a real, non-empty pre-condition was unconditionally
 * reported as {@code hasCondition=false} on reopen. Per the method's own doc comment, the plugin
 * treats that as "unfiltered" and calling apply_filter (REPLACE semantics) on it would silently
 * erase a real filter the chart actually had. Same class of defect as
 * {@link WizVsServiceCarryConditionOutputAssemblyTest} (site 1) and the already-fixed
 * {@code applyConditionModel} (commit 190db1b3e); fixed by widening the cast to
 * {@code DynamicBindableVSAssembly}, the shared interface both hierarchies implement.
 *
 * <p>Follows {@link ViewsheetRuntimeControllerVerifyDispatchTest}'s dispatch-test construction
 * pattern for the controller/mocks, but (unlike that test) needs a real {@link Viewsheet} and
 * {@link GaugeVSAssembly} rather than mocks of them, since this bug is specifically about a real
 * {@code instanceof} check against a real assembly type — those constructors need a live Spring
 * context (see {@code VSAssemblyInfo}'s constructor -> {@code SreeEnv.getProperty}), hence the
 * {@code @SreeHome}/{@code SpringExtension} setup mirroring
 * {@link inetsoft.web.wiz.service.WizVsServiceCarryConditionOutputAssemblyTest} (site 1's test).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetRuntimeControllerHasPreConditionTest {

   private static String managedIdentifier() {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         WizVisualizationService.VISUALIZATION_ROOT_FOLDER_PATH + "/abc", null);
      return entry.toIdentifier();
   }

   private static ConditionList plainCondition(String field) {
      Condition condition = new Condition();
      condition.setOperation(XCondition.EQUAL_TO);
      condition.addValue("A");

      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(new AttributeRef(null, field), condition, 0));
      return conds;
   }

   @Test
   void reopeningAFilteredGaugeReportsHasCondition() throws Exception {
      Principal principal = mock(Principal.class);

      Viewsheet vs = new Viewsheet();
      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "Gauge1");
      gauge.setPreConditionList(plainCondition("Category"));
      vs.addAssembly(gauge);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetService vsService = mock(ViewsheetService.class);
      when(vsService.openViewsheet(any(AssetEntry.class), eq(principal), eq(true)))
         .thenReturn("rt1");
      when(vsService.getViewsheet("rt1", principal)).thenReturn(rvs);

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      WizVsService wizVsService = mock(WizVsService.class);

      ViewsheetRuntimeController controller =
         new ViewsheetRuntimeController(vsService, wizVsService, securityEngine);

      OpenViewsheetResult result = controller.openViewsheet(managedIdentifier(), null, principal);

      // THE REGRESSION. Before the fix, `assembly instanceof DataVSAssembly` evaluated false for
      // the Gauge (findChartAssembly's fallback-to-first-assembly path), so hasPreCondition returned
      // false unconditionally even though the chart is genuinely filtered.
      assertEquals("Gauge1", result.getAssemblyName());
      assertTrue(result.getHasCondition(), "a Gauge with a real pre-condition must report hasCondition=true");
   }

   @Test
   void reopeningAnUnfilteredGaugeReportsNoCondition() throws Exception {
      Principal principal = mock(Principal.class);

      Viewsheet vs = new Viewsheet();
      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "Gauge1");
      vs.addAssembly(gauge);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetService vsService = mock(ViewsheetService.class);
      when(vsService.openViewsheet(any(AssetEntry.class), eq(principal), eq(true)))
         .thenReturn("rt1");
      when(vsService.getViewsheet("rt1", principal)).thenReturn(rvs);

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      WizVsService wizVsService = mock(WizVsService.class);

      ViewsheetRuntimeController controller =
         new ViewsheetRuntimeController(vsService, wizVsService, securityEngine);

      OpenViewsheetResult result = controller.openViewsheet(managedIdentifier(), null, principal);

      assertEquals("Gauge1", result.getAssemblyName());
      assertEquals(Boolean.FALSE, result.getHasCondition());
   }
}
