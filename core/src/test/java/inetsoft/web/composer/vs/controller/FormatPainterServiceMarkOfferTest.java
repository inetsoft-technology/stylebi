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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.command.OfferVizMarkCommand;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.viewsheet.command.ViewsheetCommand;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The page background change in the format pane offers a mark switch only when it crosses the
 * threshold against the sheet's mark.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class FormatPainterServiceMarkOfferTest {
   @BeforeEach
   void setup() throws Exception {
      service = new FormatPainterService(coreLifecycleService, chartRegionHandler,
                                         viewsheetEngine, objectModelService, bindingService,
                                         vsLayoutService);
      viewsheet = new Viewsheet();

      when(viewsheetEngine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(sandbox));
   }

   @Test
   void aLightMarkedPageSetToBlackIsOfferedDark() throws Exception {
      viewsheet.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);

      setPageBackground("", "#000000");

      List<OfferVizMarkCommand> offers = offers();
      assertEquals(1, offers.size());
      assertTrue(offers.get(0).isDark());
   }

   @Test
   void aSecondDarkColourIsNotOfferedAgain() throws Exception {
      viewsheet.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);

      setPageBackground("", "#000000");
      setPageBackground("#000000", "#1a2b3c");

      assertEquals(1, offers().size());
   }

   @Test
   void theNextCrossingIsOfferedAgain() throws Exception {
      viewsheet.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);

      setPageBackground("", "#000000");
      setPageBackground("#000000", "#ffffff");
      assertEquals(1, offers().size(), "returning to the mark's side offers nothing");

      setPageBackground("#ffffff", "#000000");
      assertEquals(2, offers().size());
   }

   @Test
   void aDarkMarkedPageSetToWhiteIsOfferedLight() throws Exception {
      viewsheet.getVSAssemblyInfo().setVizMark(VizMark.MODERN_DARK);
      setPageBackground("", "#1c1b1f");
      clearInvocations(dispatcher);

      setPageBackground("#1c1b1f", "#ffffff");

      List<OfferVizMarkCommand> offers = offers();
      assertEquals(1, offers.size());
      assertFalse(offers.get(0).isDark());
   }

   @Test
   void aLegacyPageIsNeverOffered() throws Exception {
      viewsheet.getVSAssemblyInfo().setVizMark(null);

      setPageBackground("", "#000000");

      assertTrue(offers().isEmpty());
   }

   private void setPageBackground(String from, String to) throws Exception {
      FormatVSObjectEvent event = new FormatVSObjectEvent();
      event.setFormat(background(to));
      event.setOrigFormat(background(from));
      event.setCharts(new String[0]);

      service.setFormat("rid", event, null, dispatcher, "uri");
   }

   private static VSObjectFormatInfoModel background(String color) {
      VSObjectFormatInfoModel model = new VSObjectFormatInfoModel();
      model.setBackgroundColorType("Static");
      model.setBackgroundColor(color);
      return model;
   }

   private List<OfferVizMarkCommand> offers() {
      ArgumentCaptor<ViewsheetCommand> sent = ArgumentCaptor.forClass(ViewsheetCommand.class);
      verify(dispatcher, atLeast(0)).sendCommand(sent.capture());
      return sent.getAllValues().stream()
         .filter(OfferVizMarkCommand.class::isInstance)
         .map(OfferVizMarkCommand.class::cast)
         .toList();
   }

   private FormatPainterService service;
   private Viewsheet viewsheet;
   @Mock private ViewsheetEngine viewsheetEngine;
   @Mock private RuntimeViewsheet rvs;
   @Mock private ViewsheetSandbox sandbox;
   @Mock private CoreLifecycleService coreLifecycleService;
   @Mock private ChartRegionHandler chartRegionHandler;
   @Mock private VSObjectModelFactoryService objectModelService;
   @Mock private VSBindingService bindingService;
   @Mock private VSLayoutService vsLayoutService;
   @Mock private CommandDispatcher dispatcher;
}
