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
package inetsoft.web.composer.ws;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.ScriptException;
import inetsoft.web.composer.ws.assembly.WorksheetEventService;
import inetsoft.web.composer.ws.assembly.WorksheetEventServiceProxy;
import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76408 - opening a worksheet as its own document (e.g. clicking the base
 * worksheet link in the composer's bottom status bar) never linked the new
 * worksheet's {@link AssetQuerySandbox} back to the originating viewsheet's
 * {@link inetsoft.report.composition.execution.ViewsheetSandbox}, so script
 * expressions like {@code worksheet['TableView1'].visible} could never resolve
 * the viewsheet assembly. Exercises the real production method
 * (WorksheetEventService#openWorksheet, the body OpenWorksheetController calls)
 * and executes the script through the real GraalJS engine
 * (AssetQuerySandbox#getScriptEnv()), not just raw scope hasMember/getMember
 * checks.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/script/viewsheet/ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
public class OpenWorksheetControllerSandboxLinkTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private ViewsheetService viewsheetService;
   private WorksheetEventService eventService;
   private String openedWorksheetId;

   @org.junit.jupiter.api.BeforeEach
   void setUp(org.springframework.context.ApplicationContext ctx) {
      viewsheetService = ctx.getBean(ViewsheetService.class);
      ObjectProvider<WorksheetEventServiceProxy> noProxy = new ObjectProvider<>() {
         @Override
         public WorksheetEventServiceProxy getObject() {
            throw new NoSuchBeanDefinitionException(WorksheetEventServiceProxy.class);
         }

         @Override
         public WorksheetEventServiceProxy getIfAvailable() {
            return null;
         }
      };
      eventService = new WorksheetEventService(viewsheetService, noProxy);
   }

   @AfterEach
   void tearDown() throws Exception {
      if(openedWorksheetId != null) {
         viewsheetService.closeWorksheet(openedWorksheetId, null);
         openedWorksheetId = null;
      }
   }

   /**
    * Worksheet opened from within a running viewsheet (vsId set, mirroring the
    * composer's "click link to worksheet in the bottom bar" flow) must resolve
    * viewsheet assemblies through its worksheet scope.
    */
   @Test
   void testWorksheetOpenedFromViewsheetResolvesViewsheetAssembly() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      Principal user = rvs.getUser();
      AssetEntry wsEntry = rvs.getViewsheet().getBaseEntry();
      assertNotNull(wsEntry, "fixture viewsheet must have a base worksheet entry");

      openedWorksheetId = openWorksheet(wsEntry, rvs.getID(), user);

      RuntimeWorksheet rws = viewsheetService.getWorksheet(openedWorksheetId, user);
      AssetQuerySandbox wbox = rws.getAssetQuerySandbox();
      assertNotNull(wbox.getViewsheetSandbox(),
                    "worksheet opened from a viewsheet must be linked to that viewsheet's sandbox");

      Object visible = execute(wbox, "worksheet['TableView1'].visible");
      assertEquals(Boolean.TRUE, visible);
   }

   /**
    * Worksheet opened with no originating viewsheet (e.g. from the portal or
    * repository tree) must keep vsbox == null, same as before the fix.
    */
   @Test
   void testWorksheetOpenedWithoutViewsheetHasNoSandboxLink() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      Principal user = rvs.getUser();
      AssetEntry wsEntry = rvs.getViewsheet().getBaseEntry();
      assertNotNull(wsEntry, "fixture viewsheet must have a base worksheet entry");

      openedWorksheetId = openWorksheet(wsEntry, null, user);

      RuntimeWorksheet rws = viewsheetService.getWorksheet(openedWorksheetId, user);
      AssetQuerySandbox wbox = rws.getAssetQuerySandbox();
      assertNull(wbox.getViewsheetSandbox(),
                 "worksheet opened with no originating viewsheet must not be linked to any sandbox");

      ScriptException ex = assertThrows(ScriptException.class,
                                        () -> execute(wbox, "worksheet['TableView1'].visible"));
      assertTrue(ex.getMessage().contains("visible"));
   }

   private Object execute(AssetQuerySandbox wbox, String cmd) throws Exception {
      ScriptEnv senv = wbox.getScriptEnv();
      Object script = senv.compile(cmd);
      return senv.exec(script, wbox.getScope(), wbox.getScope(), null);
   }

   /**
    * Opens a worksheet through the real {@link WorksheetEventService#openWorksheet}
    * method body (the same one {@code OpenWorksheetController} calls for the STOMP
    * /ws/open message), including the new vsId-based sandbox linking.
    */
   private String openWorksheet(AssetEntry wsEntry, String vsId, Principal user) throws Exception {
      GenericMessage<String> message = new GenericMessage<>("test");
      MessageAttributes messageAttributes = new MessageAttributes(message);
      StompHeaderAccessor headerAccessor = messageAttributes.getHeaderAccessor();
      headerAccessor.setUser(user);
      SimpMessagingTemplate messagingTemplate = new SimpMessagingTemplate(new MessageChannel() {
         @Override
         public boolean send(Message<?> message) {
            return true;
         }

         @Override
         public boolean send(Message<?> message, long timeout) {
            return true;
         }
      });
      CommandDispatcherService dispatcherService = new CommandDispatcherService(messagingTemplate, null) {
         @Override
         public void convertAndSendToUser(String user, String destination, Object payload,
                                          Map<String, Object> headers) throws MessagingException
         {
            // NO-OP
         }
      };
      CommandDispatcher commandDispatcher = new CommandDispatcher(headerAccessor, dispatcherService, null);
      MessageContextHolder.setMessageAttributes(messageAttributes);

      try {
         String id = viewsheetService.openWorksheet((AssetEntry) wsEntry.clone(), user);
         eventService.openWorksheet(id, user, (AssetEntry) wsEntry.clone(), false, false, vsId,
                                     commandDispatcher);
         return id;
      }
      finally {
         MessageContextHolder.setMessageAttributes(null);
      }
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ASSET_ID);
      event.setViewer(true);
      return event;
   }

   public static final String ASSET_ID = "1^128^__NULL__^ViewsheetScopeTest";
}
