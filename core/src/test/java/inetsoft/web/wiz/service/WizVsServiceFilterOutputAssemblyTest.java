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
package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.web.wiz.model.CreateVisualizationModel;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.model.VisualizationConditionModel;

import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * THE BUG THIS FIXES. A wiz-recommended "bare aggregate" chart — zero dimensions bound, just a
 * single measure — is never a {@code ChartVSAssembly}; wiz's own recommender
 * (WizAutoBindingService.recommendationToVisualization / VSGaugeRecommendation) maps that shape to
 * a {@code GaugeVSAssembly} or {@code TextVSAssembly}, both {@code OutputVSAssembly} subclasses — a
 * SIBLING hierarchy to {@code DataVSAssembly}, not a subtype of it. {@code applyConditionModel}
 * gated on {@code instanceof DataVSAssembly}, so a filter applied to one of these silently did
 * nothing: the call reported success with the pre-filter row count unchanged, no error, no
 * warning. Confirmed live sweeping openproject (H5): a NULL-exclusion filter that correctly reduced
 * 984→810 the moment ANY dimension was bound had ZERO effect on an otherwise-identical bare-scalar
 * KPI chart.
 *
 * <p>Mirrors {@link WizVsServiceFilterCopyTest}'s mocking pattern, swapping the mocked assembly for
 * a {@code GaugeVSAssembly}/{@code TextVSAssembly} to pin the specific type this bug excluded.
 * {@code getViewsheet()=null} keeps {@code VSUtil.getBaseColumns} returning an empty
 * {@code ColumnSelection} — irrelevant to what this test covers (whether the condition reaches the
 * assembly at all, not condition-to-column resolution).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceFilterOutputAssemblyTest {
   private WizVsService service;
   private RuntimeViewsheet rvs;
   private Viewsheet vs;
   private GaugeVSAssembly gauge;
   private Principal user;

   @BeforeEach
   void setUp() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      user = mock(Principal.class);

      WizVsService real = new WizVsService(viewsheetService, engine, securityEngine, null, null);
      service = spy(real);

      gauge = mock(GaugeVSAssembly.class);
      when(gauge.getName()).thenReturn("vs_1");
      when(gauge.isPrimary()).thenReturn(true);

      vs = mock(Viewsheet.class);
      when(vs.getAssemblies()).thenReturn(new Assembly[] { gauge });
      when(vs.getWizInfo()).thenReturn(new Viewsheet.WizInfo(true, null, null));
      ViewsheetInfo vsInfo = mock(ViewsheetInfo.class);
      when(vsInfo.isMetadata()).thenReturn(false);
      when(vs.getViewsheetInfo()).thenReturn(vsInfo);

      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt-1");
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);

      doReturn(new CreateViewsheetResult()).when(service).executeAndExtract(any(), any(), anyInt());
      doReturn(null).when(service).collectFlatBinding(any());
      doReturn("vs-identifier").when(service).persistViewsheet(any(), any(), any());
   }

   /** A minimal but genuinely valid base condition — a flat "Category = 'A'" leaf. */
   private static VisualizationConditionModel simpleCondition() {
      VisualizationConditionModel cm = new VisualizationConditionModel();
      VisualizationConditionModel.ConditionSpec spec = new VisualizationConditionModel.ConditionSpec(
         "Category", null, null, null, null, false, null, null,
         List.of(new VisualizationConditionModel.ValueSpec("VALUE", "A", null)));
      cm.setBaseConditions(List.of(new VisualizationConditionModel.ConditionLeaf(null, spec)));
      return cm;
   }

   private static CreateVisualizationModel request() {
      CreateVisualizationModel model = new CreateVisualizationModel();
      model.setRuntimeId("rt-1");
      model.setConditionModel(simpleCondition());
      return model;
   }

   @Test
   void aBareAggregateGaugeAssemblyReceivesTheFilter() throws Exception {
      // THE REGRESSION. Before the fix, applyConditionModel's `instanceof DataVSAssembly` check
      // rejected this assembly outright and setPreConditionList was never called — the chart kept
      // showing every row, with the call still reporting success.
      service.createViewsheet(request(), user);

      verify(gauge).setPreConditionList(any());
   }

   @Test
   void aFilterThatFullyAppliesToAGaugeReportsNoWarnings() throws Exception {
      // Regression guard mirroring WizVsServiceFilterCopyTest's equivalent: an Output assembly
      // taking the fix's new branch must not, itself, start manufacturing spurious warnings on the
      // ordinary success path.
      assertNull(service.createViewsheet(request(), user).getWarnings());
   }

   @Test
   void aTextAssemblyAlsoReceivesTheFilter() throws Exception {
      // GaugeVSAssembly and TextVSAssembly are the two concrete OutputVSAssembly shapes wiz's own
      // recommender produces for a bare-aggregate chart (gauge vs. text) — both must be fixed, not
      // just one.
      TextVSAssembly text = mock(TextVSAssembly.class);
      when(text.getName()).thenReturn("vs_1");
      when(text.isPrimary()).thenReturn(true);
      when(vs.getAssemblies()).thenReturn(new Assembly[] { text });

      service.createViewsheet(request(), user);

      verify(text).setPreConditionList(any());
   }
}
