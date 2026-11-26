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

   @Test
   void listEntriesAndTaskViewCarryTheTaskTimeZoneTopLevelAndNotInsideTask() throws Exception {
      AdminScheduleTaskList list = new AdminScheduleTaskList();
      list.getTasks().add(new ScheduleTaskEntry(new ScheduleTask(), "/", "Asia/Tokyo"));
      list.getTasks().add(new ScheduleTaskEntry(new ScheduleTask(), "/"));
      ScheduleTaskView view = new ScheduleTaskView("id", "/", "Asia/Tokyo", new ScheduleTask(),
                                                   Collections.emptyList(),
                                                   Collections.emptyList());

      JsonNode listJson = mapper.readTree(mapper.writeValueAsString(list));
      JsonNode viewJson = mapper.readTree(mapper.writeValueAsString(view));

      assertEquals("Asia/Tokyo", listJson.get("tasks").get(0).get("timeZone").asText());
      assertTrue(listJson.get("tasks").get(1).get("timeZone").isNull());
      assertEquals("Asia/Tokyo", viewJson.get("timeZone").asText());
      assertFalse(viewJson.get("task").has("timeZone"));
   }

   @Test
   void publicDtoJsonHasNoTimeZone() throws Exception {
      ScheduleTaskList list = new ScheduleTaskList();
      list.getTasks().add(new ScheduleTask());

      JsonNode json = mapper.readTree(mapper.writeValueAsString(list));

      assertFalse(json.get("tasks").get(0).has("timeZone"));
   }

   // Bug #82325: the zip password state is on the view, as presence only
   @Test
   void taskViewSerializesEmailZipPasswordsWithoutAnyPasswordKey() throws Exception {
      ScheduleTaskView view = new ScheduleTaskView("id", "/", null, new ScheduleTask(),
         Collections.emptyList(), Collections.emptyList(),
         java.util.List.of(new EmailZipPasswordView(0, true, false, null),
                           new EmailZipPasswordView(2, false, true, "secret-1")));

      String text = mapper.writeValueAsString(view);
      JsonNode json = mapper.readTree(text);

      JsonNode first = json.get("emailZipPasswords").get(0);
      assertEquals(0, first.get("actionIndex").asInt());
      assertTrue(first.get("passwordSet").asBoolean());
      assertFalse(first.get("useCredential").asBoolean());
      JsonNode second = json.get("emailZipPasswords").get(1);
      assertEquals("secret-1", second.get("secretId").asText());
      assertTrue(second.get("useCredential").asBoolean());
      assertFalse(text.toLowerCase().contains("\"password\""), text);
   }

   @Test
   void taskViewWithoutZipPasswordsSerializesAnEmptyList() throws Exception {
      ScheduleTaskView view = new ScheduleTaskView("id", "/", new ScheduleTask(),
                                                   Collections.emptyList(),
                                                   Collections.emptyList());

      assertEquals(0, mapper.readTree(mapper.writeValueAsString(view))
                      .get("emailZipPasswords").size());
   }

   @Test
   void zipPasswordSpecDeserializesFromTheWireAndNeverReprintsTheValue() throws Exception {
      String wire = "{\"actionIndex\":1,\"password\":\"PW-PLACEHOLDER-ZIP-1\"," +
         "\"useCredential\":false,\"secretId\":null,\"unknown\":1}";

      EmailZipPasswordSpec spec = mapper.readValue(wire, EmailZipPasswordSpec.class);

      assertEquals(1, spec.getActionIndex());
      assertEquals("PW-PLACEHOLDER-ZIP-1", spec.getPassword());
      assertFalse(spec.toString().contains("PW-PLACEHOLDER-ZIP-1"));
   }
}
