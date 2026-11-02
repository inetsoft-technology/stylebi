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
package inetsoft.web.admin.schedule;

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.util.Catalog;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.AdminExceptionHandler;
import inetsoft.web.admin.ResourceExistsError;
import inetsoft.web.security.auth.ResourceExistsException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.UndeclaredThrowableException;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #75342, renaming a schedule task to the name of another task is a conflict: it throws
 * {@link ResourceExistsException}, which {@link AdminExceptionHandler} answers with HTTP 409 and
 * the message in the body, instead of a {@code MessageException} answered with HTTP 500.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScheduleDuplicateNameRenameTest {
   private static final String ORG = "dupnorg";
   private static final IdentityID ALICE = new IdentityID("alice", ORG);
   private static final String TASK1 = ALICE.convertToKey() + ":Task1";
   private static final String TASK2 = ALICE.convertToKey() + ":Task2";

   private static SecurityTestDataBuilder builder;

   @Autowired
   ScheduleManager scheduleManager;

   private ScheduleService service;
   private SRPrincipal alice;
   private Principal oldContext;

   @BeforeAll
   static void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("dupnOrg", ORG)
         .addOrgAdminRole("dupnOrgAdmin", ORG)
         .addUser("alice", ORG, "password")
         .addUserToRole("alice", "dupnOrgAdmin", ORG);
      builder.setup();
   }

   @AfterAll
   static void teardownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      oldContext = ThreadContext.getContextPrincipal();
      alice = builder.principalOf("alice", ORG);
      alice.setProperty("__internal__", "true");
      ThreadContext.setContextPrincipal(alice);

      ScheduleTask task1 = new ScheduleTask("Task1");
      task1.setOwner(ALICE);
      ScheduleTask task2 = new ScheduleTask("Task2");
      task2.setOwner(ALICE);
      scheduleManager.save(List.of(task1, task2), ORG);

      service = new ScheduleService(null, scheduleManager,
                                    (ScheduleClient) ReflectionTestUtils.getField(
                                       scheduleManager, "scheduleClient"),
                                    null, null, null, null, null, null,
                                    mock(ScheduleTaskFolderService.class), null, null, null);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(oldContext);

      @SuppressWarnings("unchecked")
      Map<String, ScheduleTask> map =
         (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(ORG);
      map.values().removeIf(t -> t != null && Set.of("Task1", "Task2").contains(t.getName()));
   }

   @Test
   void renameToExistingName_throwsResourceExists_with409AndMessage() throws Exception {
      ResourceExistsException ex = assertThrows(ResourceExistsException.class,
                                                () -> renameTask(TASK1, TASK2));

      String message = Catalog.getCatalog(alice).getString("em.schedule.task.duplicateName");
      assertEquals(message, ex.getMessage());
      assertNotNull(scheduleManager.getScheduleTask(TASK1, ORG), "task is not renamed");
      assertNotNull(scheduleManager.getScheduleTask(TASK2, ORG), "target is not replaced");

      // the admin advice maps it to 409 with the message in the body, the shape the EM editor
      // reads as error.error.message
      assertEquals(HttpStatus.CONFLICT,
                   AdminExceptionHandler.class.getMethod("handleExistingResource",
                                                         ResourceExistsException.class)
                      .getAnnotation(ResponseStatus.class).value());
      ResourceExistsError body = new AdminExceptionHandler(null).handleExistingResource(ex);
      assertEquals(message, body.getMessage());
   }

   private void renameTask(String oldId, String newId) throws Exception {
      try {
         ReflectionTestUtils.invokeMethod(service, "renameTask", oldId, newId, ALICE, alice);
      }
      catch(UndeclaredThrowableException e) {
         throw (Exception) e.getUndeclaredThrowable();
      }
   }
}
