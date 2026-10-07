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

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Catalog;
import inetsoft.web.admin.schedule.model.ScheduleTaskEditorModel;
import inetsoft.web.admin.schedule.model.TaskOptionsPaneModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77856, a task name made only of whitespace is refused by the task save and by the task
 * rename, before anything is renamed or stored. Leading and trailing spaces stay legal.
 */
@Tag("core")
class ScheduleTaskBlankNameTest {
   // new IdentityID("alice", "orga").convertToKey()
   private static final String OWNER_KEY = "alice" + IdentityID.KEY_DELIMITER + "orga";
   private static final String OLD_ID = OWNER_KEY + ":Daily";

   private ScheduleManager scheduleManager;
   private ScheduleService scheduleService;
   private ScheduleTaskService taskService;
   private Principal principal;

   @BeforeEach
   void setUp() {
      scheduleManager = mock(ScheduleManager.class);
      scheduleService = mock(ScheduleService.class);
      taskService = new ScheduleTaskService(mock(AnalyticRepository.class), scheduleManager,
                                            scheduleService, null, mock(SecurityProvider.class),
                                            null, mock(SecurityEngine.class));
      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(OWNER_KEY);
   }

   @ParameterizedTest
   @ValueSource(strings = { "", " ", "   ", "\t" })
   void saveTask_blankName_isRefusedBeforeAnythingIsSaved(String name) throws Exception {
      Exception ex = assertThrows(Exception.class, () -> taskService.saveTask(
         model(name), "http://localhost/", principal, true));

      assertEquals(Catalog.getCatalog().getString("em.scheduler.emptyTaskName"), ex.getMessage());
      verify(scheduleService, never()).updateTaskName(any(), any(), any(), any());
      verify(scheduleService, never()).saveTask(any(), any(), any());
      verifyNoInteractions(scheduleManager);
   }

   // the rename refuses a blank name before the rename is queued or the task is looked up
   @ParameterizedTest
   @ValueSource(strings = { " ", "   ", OWNER_KEY + ": ", OWNER_KEY + ":  " })
   void updateTaskName_blankName_isRefused(String name) {
      ScheduleService service = new ScheduleService(null, scheduleManager, mock(ScheduleClient.class),
                                                    null, null, null, null, null, null, null,
                                                    null, null, null);
      IdentityID owner = new IdentityID("alice", "orga");

      Exception ex = assertThrows(Exception.class,
                                  () -> service.updateTaskName(OLD_ID, name, owner, principal));

      assertEquals(Catalog.getCatalog().getString("em.scheduler.emptyTaskName"), ex.getMessage());
      verifyNoInteractions(scheduleManager);
   }

   @Test
   void isBlankTaskName_checksTheNamePartOfTheId() {
      assertTrue(ScheduleService.isBlankTaskName(null));
      assertTrue(ScheduleService.isBlankTaskName(""));
      assertTrue(ScheduleService.isBlankTaskName("  "));
      assertTrue(ScheduleService.isBlankTaskName(OWNER_KEY + ":"));
      assertTrue(ScheduleService.isBlankTaskName(OWNER_KEY + ": "));

      assertFalse(ScheduleService.isBlankTaskName("Daily"));
      assertFalse(ScheduleService.isBlankTaskName(" a"));
      assertFalse(ScheduleService.isBlankTaskName("a "));
      assertFalse(ScheduleService.isBlankTaskName(OWNER_KEY + ": a"));
      assertFalse(ScheduleService.isBlankTaskName(OWNER_KEY + ":a "));
      // a colon is a legal task name character, without an owner the id is the name itself
      assertFalse(ScheduleService.isBlankTaskName("a: "));
      // a user name with a colon, the name follows the first colon after the org
      assertFalse(ScheduleService.isBlankTaskName(
         new IdentityID("a:b", "orga").convertToKey() + ": x"));
      assertTrue(ScheduleService.isBlankTaskName(
         new IdentityID("a:b", "orga").convertToKey() + ": "));
   }

   // a colon is a legal task name character, a rename to a name with a colon passes the blank
   // check and goes on to look up the task (the rename itself is not under test here)
   @ParameterizedTest
   @ValueSource(strings = { "task:name", "a: b", " :", OWNER_KEY + ":task:name" })
   void updateTaskName_nameWithColon_isNotRefused(String name) {
      assertFalse(ScheduleService.isBlankTaskName(OWNER_KEY + ":" + name));
      assertFalse(ScheduleService.isBlankTaskName(
         new IdentityID("admin", "host-org").convertToKey() + ":" + name));

      ScheduleService service = new ScheduleService(null, scheduleManager, mock(ScheduleClient.class),
                                                    null, null, null, null, null, null, null,
                                                    null, null, null);
      IdentityID owner = new IdentityID("alice", "orga");

      // the mocked manager has no task, so the rename fails after the blank check
      Exception ex = assertThrows(Exception.class,
                                  () -> service.updateTaskName(OLD_ID, name, owner, principal));

      assertNotEquals(Catalog.getCatalog().getString("em.scheduler.emptyTaskName"), ex.getMessage());
      verify(scheduleManager).getScheduleTask(OLD_ID);
   }

   private static ScheduleTaskEditorModel model(String name) {
      return ScheduleTaskEditorModel.builder()
         .taskName(name)
         .oldTaskName(OLD_ID)
         .options(mock(TaskOptionsPaneModel.class))
         .build();
   }
}
