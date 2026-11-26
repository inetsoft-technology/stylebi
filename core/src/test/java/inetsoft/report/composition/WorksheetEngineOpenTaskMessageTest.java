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
package inetsoft.report.composition;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.internal.cluster.AffinityCallable;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Bug #81431: opening a sheet whose runtime id is owned by another node runs
 * {@code OpenWorksheetTask}/{@code OpenViewsheetTask} on that node. The "asset is locked" warning is
 * added to the user messages of the thread that opens the sheet, those messages are cleared by
 * {@code ServiceProxyContext.postprocess()}, and an affinity call returns only the value of the
 * task, so the request node never showed the warning. The tasks now return the messages with the
 * sheet id and the request node adds them to its own thread.
 *
 * <p>The engine is a mock registered as the worksheet/viewsheet service, as the Spring
 * configuration does with the one {@code ViewsheetEngine} instance; {@code openSheet} adds the
 * warning on the thread it runs on.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  WorksheetEngineOpenTaskMessageTest.EngineConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetEngineOpenTaskMessageTest {
   @Configuration
   static class EngineConfiguration {
      @Bean("viewsheetEngine")
      public ViewsheetService viewsheetService() {
         return Mockito.mock(ViewsheetEngine.class);
      }

      @Bean("worksheetService")
      @Primary
      public WorksheetService worksheetService(ViewsheetService viewsheetService) {
         return viewsheetService;
      }
   }

   @Autowired ViewsheetService service;

   @BeforeEach
   void setUp() {
      Mockito.reset(service);
      Tool.clearUserMessage();
   }

   @AfterEach
   void tearDown() {
      Tool.clearUserMessage();
   }

   @Test
   void remoteWorksheetOpenShowsTheWarningOnTheRequestThread() throws Exception {
      WorksheetEngine engine = (WorksheetEngine) service;
      AssetEntry entry = worksheetEntry();
      Principal user = user();

      doAnswer(inv -> {
         Tool.addUserWarning(WARNING);
         return "ws-1";
      }).when(engine).openSheet(any(), any(), eq("ws-1"));
      when(engine.isLocal("ws-1")).thenReturn(false);
      // the affinity call runs the task on another thread, as the owner node would, and returns
      // only the value of the task
      when(engine.affinityCall(eq("ws-1"), any())).thenAnswer(
         inv -> callOnOtherThread(inv.getArgument(1)));

      assertEquals("ws-1", openWorksheet(engine, entry, user, "ws-1"));

      assertWarningShown();
   }

   @Test
   void localWorksheetOpenStillShowsTheWarning() throws Exception {
      WorksheetEngine engine = (WorksheetEngine) service;

      doAnswer(inv -> {
         Tool.addUserWarning(WARNING);
         return "ws-1";
      }).when(engine).openSheet(any(), any(), eq("ws-1"));
      when(engine.isLocal("ws-1")).thenReturn(true);

      assertEquals("ws-1", openWorksheet(engine, worksheetEntry(), user(), "ws-1"));

      assertWarningShown();
   }

   @Test
   void worksheetTaskReturnsTheWarningBecausePostprocessClearsIt() throws Exception {
      WorksheetEngine engine = (WorksheetEngine) service;

      doAnswer(inv -> {
         Tool.addUserWarning(WARNING);
         return "ws-1";
      }).when(engine).openSheet(any(), any(), eq("ws-1"));

      Class<?> taskClass = Class.forName(
         "inetsoft.report.composition.WorksheetEngine$OpenWorksheetTask");
      Constructor<?> constructor = taskClass.getDeclaredConstructor(
         AssetEntry.class, Principal.class, String.class);
      constructor.setAccessible(true);
      AffinityCallable<?> task = (AffinityCallable<?>) constructor.newInstance(
         worksheetEntry(), user(), "ws-1");

      OpenSheetResult result = (OpenSheetResult) task.call();

      assertEquals("ws-1", result.getId());
      assertEquals(List.of(WARNING), result.getMessages().stream().map(UserMessage::getMessage)
                      .toList());
      assertTrue(Tool.getUserMessages(MessageCommand.Type.WARNING).isEmpty(),
                 "postprocess() clears the thread's messages, so they can only travel in the result");
   }

   @Test
   void viewsheetTaskReturnsTheWarningBecausePostprocessClearsIt() throws Exception {
      ViewsheetEngine engine = (ViewsheetEngine) service;

      doAnswer(inv -> {
         Tool.addUserWarning(WARNING);
         return "vs-1";
      }).when(engine).openSheet(any(), any(), eq("vs-1"));

      Class<?> taskClass = Class.forName(
         "inetsoft.analytic.composition.ViewsheetEngine$OpenViewsheetTask");
      Constructor<?> constructor = taskClass.getDeclaredConstructor(
         AssetEntry.class, Principal.class, String.class);
      constructor.setAccessible(true);
      AffinityCallable<?> task = (AffinityCallable<?>) constructor.newInstance(
         new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "vs", null),
         user(), "vs-1");

      OpenSheetResult result = (OpenSheetResult) task.call();

      assertEquals("vs-1", result.getId());
      assertEquals(List.of(WARNING), result.getMessages().stream().map(UserMessage::getMessage)
                      .toList());
      assertTrue(Tool.getUserMessages(MessageCommand.Type.WARNING).isEmpty());

      result.applyMessages();
      assertWarningShown();
   }

   private static void assertWarningShown() {
      List<UserMessage> shown = Tool.getUserMessages(MessageCommand.Type.WARNING);
      assertEquals(1, shown.size(), "the open warning must reach the request thread");
      assertEquals(WARNING, shown.get(0).getMessage());
   }

   /**
    * Call a task on another thread and return only its value, like an affinity call does.
    */
   private static Object callOnOtherThread(AffinityCallable<?> task) throws Exception {
      AtomicReference<Object> value = new AtomicReference<>();
      AtomicReference<Throwable> error = new AtomicReference<>();
      Thread owner = new Thread(() -> {
         try {
            value.set(task.call());
         }
         catch(Throwable ex) {
            error.set(ex);
         }
      });
      owner.start();
      owner.join(20_000);

      assertFalse(owner.isAlive(), "the owner thread did not finish in time");

      if(error.get() != null) {
         throw new AssertionError("the task failed on the owner thread", error.get());
      }

      return value.get();
   }

   private static String openWorksheet(WorksheetEngine engine, AssetEntry entry, Principal user,
                                       String id) throws Exception
   {
      Class<?> taskClass = Class.forName(
         "inetsoft.report.composition.WorksheetEngine$OpenWorksheetTask");
      Method open = taskClass.getDeclaredMethod(
         "openWorksheet", WorksheetEngine.class, AssetEntry.class, Principal.class, String.class);
      open.setAccessible(true);

      try {
         return (String) open.invoke(null, engine, entry, user, id);
      }
      catch(InvocationTargetException ex) {
         if(ex.getCause() instanceof Exception cause) {
            throw cause;
         }

         throw ex;
      }
   }

   private static AssetEntry worksheetEntry() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws", null);
   }

   private static Principal user() {
      return new XPrincipal(new IdentityID("u81431b", "host-org"));
   }

   private static final String WARNING = "Asset is locked: ws by u81431a and u81431b.";
}
