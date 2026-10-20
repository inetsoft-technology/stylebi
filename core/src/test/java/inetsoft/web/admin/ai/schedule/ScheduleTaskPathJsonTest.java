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
package inetsoft.web.admin.ai.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.web.api.schedule.ScheduleTask;
import inetsoft.web.api.schedule.ScheduleTaskList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78959: the JSON the wiz admin read endpoints emit carries the task's folder path, while
 * the public REST types stay path-free.
 */
@Tag("core")
class ScheduleTaskPathJsonTest {
   private final ObjectMapper mapper = new ObjectMapper();

   @Test
   void listEntriesSerializeWithPathUnderTasksKey() throws Exception {
      AdminScheduleTaskList list = new AdminScheduleTaskList();
      list.getTasks().add(new ScheduleTaskEntry(new ScheduleTask(), "A/B"));

      JsonNode json = mapper.readTree(mapper.writeValueAsString(list));

      assertEquals("A/B", json.get("tasks").get(0).get("path").asText());
   }

   @Test
   void taskViewSerializesTopLevelPathAndNotInsideTask() throws Exception {
      ScheduleTaskView view = new ScheduleTaskView("id", "/", new ScheduleTask(),
                                                   Collections.emptyList(),
                                                   Collections.emptyList());

      JsonNode json = mapper.readTree(mapper.writeValueAsString(view));

      assertEquals("/", json.get("path").asText());
      assertFalse(json.get("task").has("path"));
   }

   @Test
   void publicDtoJsonHasNoPath() throws Exception {
      ScheduleTaskList list = new ScheduleTaskList();
      list.getTasks().add(new ScheduleTask());

      JsonNode json = mapper.readTree(mapper.writeValueAsString(list));

      assertFalse(json.get("tasks").get(0).has("path"));
   }
}
