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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.ThreadContext;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.command.AddVSObjectCommand;
import inetsoft.web.viewsheet.command.RefreshVSObjectCommand;
import inetsoft.web.viewsheet.event.*;
import inetsoft.web.viewsheet.model.VSObjectModel;
import inetsoft.web.viewsheet.model.VSSpinnerModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78878: a Submit with "Refresh Dashboard after Submit" off must send a submitted input
 * back with its applied value when the input sits in a container or in an embedded viewsheet,
 * where the client knows it by its absolute name.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
class VSInputServiceSubmitNestedInputTest {
   @BeforeEach
   void savePrincipal() {
      oldPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void restorePrincipal() {
      ThreadContext.setContextPrincipal(oldPrincipal);
   }

   @Test
   void refreshOffSendsInputInContainer() throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      vs.addAssembly(spinner(vs));
      GroupContainerVSAssembly group = new GroupContainerVSAssembly(vs, "Group1");
      group.setAssemblies(new String[] { "TopNSpinner" });
      vs.addAssembly(group);
      submit(vs, box, "TopNSpinner");
   }

   @Test
   void refreshOffSendsInputInEmbeddedViewsheet() throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      // an embedded viewsheet with its own (empty) base worksheet, as EmbeddedOnLoadFixture builds it
      Viewsheet child = new Viewsheet();
      Field ws = Viewsheet.class.getDeclaredField("ws");
      ws.setAccessible(true);
      ws.set(child, new Worksheet());
      child.setEntry(new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                     "test/VSInputServiceSubmitNestedInputTest/child", null,
                                     Organization.getDefaultOrganizationID()));
      child.createVSAssembly("Emb");
      child.addAssembly(spinner(child));
      vs.addAssembly(child);
      submit(vs, box, "Emb.TopNSpinner");
   }

   private static SpinnerVSAssembly spinner(Viewsheet vs) {
      SpinnerVSAssembly spinner = new SpinnerVSAssembly(vs, "TopNSpinner");
      SpinnerVSAssemblyInfo spinnerInfo = (SpinnerVSAssemblyInfo) spinner.getVSAssemblyInfo();
      spinnerInfo.setMinValue("1");
      spinnerInfo.setMaxValue("20");
      spinnerInfo.setIncrementValue("1");
      spinnerInfo.setSelectedObject(1);
      spinnerInfo.setSubmitOnChange(false);
      return spinner;
   }

   private void submit(Viewsheet vs, ViewsheetSandbox box, String spinnerName) throws Exception {
      SubmitVSAssembly button = new SubmitVSAssembly(vs, "ApplyButton");
      ((SubmitVSAssemblyInfo) button.getVSAssemblyInfo()).setRefresh(false);
      vs.addAssembly(button);

      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
      VSSubmitEvent submitEvent = ImmutableVSSubmitEvent.builder()
         .values(new InputValue[] {
            ImmutableInputValue.builder().assemblyName(spinnerName).value(5).build() })
         .build();
      VSInputService inputService = new VSInputService(
         objectService, coreLifecycleService, viewsheetService, mock(VSObjectPropertyService.class),
         null, null, null, null, null, null);
      CommandDispatcher browser = dispatcher();
      inputService.onClick(vsResource.getRuntimeId(), "ApplyButton", "1", "1", submitEvent, "",
                           ThreadContext.getPrincipal(), browser);

      assertEquals("5", ((VSSpinnerModel) lastModel(browser, spinnerName)).getCurrentLabel());
   }

   /**
    * The model of the last add or refresh command sent for an assembly by the submit click.
    */
   private static VSObjectModel<?> lastModel(CommandDispatcher dispatcher, String name) {
      VSObjectModel<?> last = null;

      for(Invocation invocation : mockingDetails(dispatcher).getInvocations()) {
         if(!invocation.getMethod().getName().equals("sendCommand")) {
            continue;
         }

         for(Object arg : invocation.getArguments()) {
            VSObjectModel<?> model = null;

            if(arg instanceof AddVSObjectCommand add) {
               model = add.getModel();
            }
            else if(arg instanceof RefreshVSObjectCommand refresh) {
               model = refresh.getInfo();
            }

            if(model != null && name.equals(model.getAbsoluteName())) {
               last = model;
            }
         }
      }

      assertNotNull(last, "nothing was sent for " + name);
      return last;
   }

   private static CommandDispatcher dispatcher() {
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      when(dispatcher.detach()).thenReturn(dispatcher);
      when(dispatcher.iterator()).thenAnswer(inv -> Collections.emptyIterator());
      return dispatcher;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   @Autowired CoreLifecycleService coreLifecycleService;
   @Autowired ViewsheetService viewsheetService;
   @Autowired VSObjectService objectService;

   private Principal oldPrincipal;
}
