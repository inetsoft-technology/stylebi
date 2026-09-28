/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.schedule;

import inetsoft.report.Paintable;
import inetsoft.report.StylePage;
import inetsoft.report.TabularSheet;
import inetsoft.report.internal.ChartElementDef;
import inetsoft.report.internal.TextPaintable;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.lang.reflect.*;
import java.security.Principal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77193: the threads of the static pool used by {@code ScheduleTask.doRun} are reused
 * across tasks (and users), so per-thread state left by one task must not be visible to the
 * next one. The pool is replaced with a single-thread pool built from the production
 * {@code GroupedThreadFactory} so that consecutive tasks deterministically share a thread.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, ScheduleTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScheduleTaskThreadContextTest {
   private static final String ALICE_MESSAGE = "alice-org chart warning 42";
   private static final AtomicReference<String> ADD_THREAD = new AtomicReference<>();
   private static final AtomicReference<String> SEEN_THREAD = new AtomicReference<>();
   private static final AtomicReference<Object> SEEN = new AtomicReference<>();

   private ExecutorService originalPool;
   private ExecutorService singlePool;

   @BeforeEach
   void setUp() throws Exception {
      Field field = poolField();
      originalPool = (ExecutorService) field.get(null);
      Class<?> factoryClass =
         Class.forName("inetsoft.sree.schedule.ScheduleTask$GroupedThreadFactory");
      Constructor<?> constructor = factoryClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      singlePool = Executors.newFixedThreadPool(1, (ThreadFactory) constructor.newInstance());
      field.set(null, singlePool);
      ADD_THREAD.set(null);
      SEEN_THREAD.set(null);
      SEEN.set(null);
   }

   @AfterEach
   void tearDown() throws Exception {
      poolField().set(null, originalPool);
      singlePool.shutdownNow();
   }

   @Test
   void nextUsersPrintLayoutChartDoesNotPaintPreviousUsersMessage() throws Throwable {
      runTask("alice-task", user("alice", "org1"), new AddMessageAction());
      runTask("bob-task", user("bob", "org2"), new PrintChartAction());

      assertEquals(ADD_THREAD.get(), SEEN_THREAD.get(), "precondition: same pool thread");
      assertNull(SEEN.get(), "bob's chart page painted another user's message");
   }

   @Test
   void nullPrincipalTaskDoesNotInheritPreviousUsersOrganization() throws Throwable {
      runTask("alice-task", user("alice", "org1"), new AddMessageAction());
      runTask("internal-task", null, new ReadOrgAction());

      assertEquals(ADD_THREAD.get(), SEEN_THREAD.get(), "precondition: same pool thread");
      assertEquals(Organization.getDefaultOrganizationID(), SEEN.get(),
                   "null-principal task resolved the previous user's organization");
   }

   @Test
   void taskStillSeesItsOwnMessageDuringRun() throws Throwable {
      runTask("alice-task", user("alice", "org1"), new AddMessageAndPrintChartAction());

      assertNotNull(SEEN.get(), "the task's own message should be painted on its chart page");
      assertTrue(((String) SEEN.get()).contains(ALICE_MESSAGE), (String) SEEN.get());
   }

   private static void runTask(String name, Principal principal, ScheduleAction action)
      throws Throwable
   {
      ScheduleTask task = new ScheduleTask(name);
      task.addAction(action);
      Method method = ScheduleTask.class.getDeclaredMethod("doRun", Principal.class);
      method.setAccessible(true);

      try {
         method.invoke(task, principal);
      }
      catch(InvocationTargetException e) {
         throw e.getCause();
      }
   }

   private static Field poolField() throws NoSuchFieldException {
      Field field = ScheduleTask.class.getDeclaredField("threadPool");
      field.setAccessible(true);
      return field;
   }

   private static XPrincipal user(String name, String org) {
      XPrincipal principal = new XPrincipal(new IdentityID(name, org));
      principal.setProperty("curr_org_id", org);
      return principal;
   }

   /**
    * Mimics the print-layout PDF sink: StyleCore.completeElement on a chart element paints
    * all user messages on the thread into the page info.
    */
   private static String paintChartPage() {
      TabularSheet sheet = new TabularSheet(null, null);
      ChartElementDef elem = new ChartElementDef(sheet);
      StylePage page = new StylePage(new Dimension(612, 792));
      sheet.completeElement(elem, page);
      page.completeInfo();
      StringBuilder text = new StringBuilder();

      for(int i = 0; i < page.getPaintableCount(); i++) {
         Paintable paintable = page.getPaintable(i);

         if(paintable instanceof TextPaintable) {
            text.append(((TextPaintable) paintable).getText()).append('|');
         }
      }

      return text.length() == 0 ? null : text.toString();
   }

   public static class AddMessageAction implements ScheduleAction {
      @Override
      public void run(Principal principal) {
         ADD_THREAD.set(Thread.currentThread().getName());
         Tool.addUserMessage(ALICE_MESSAGE);
      }
   }

   public static class PrintChartAction implements ScheduleAction {
      @Override
      public void run(Principal principal) {
         SEEN_THREAD.set(Thread.currentThread().getName());
         SEEN.set(paintChartPage());
      }
   }

   public static class AddMessageAndPrintChartAction implements ScheduleAction {
      @Override
      public void run(Principal principal) {
         Tool.addUserMessage(ALICE_MESSAGE);
         SEEN.set(paintChartPage());
      }
   }

   public static class ReadOrgAction implements ScheduleAction {
      @Override
      public void run(Principal principal) {
         SEEN_THREAD.set(Thread.currentThread().getName());
         SEEN.set(OrganizationManager.getInstance().getCurrentOrgID());
      }
   }
}
