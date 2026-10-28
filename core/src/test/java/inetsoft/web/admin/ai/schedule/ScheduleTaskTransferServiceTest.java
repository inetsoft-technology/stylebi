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

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.security.IdentityID;
import inetsoft.web.admin.schedule.ScheduleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for the round-1 PR review's Finding 2: a malformed {@code <Task>} attribute
 * must surface as a clean, field-named {@link IllegalArgumentException} (mapped to a 400 by {@code
 * AdminScheduleTransferController}), not a raw {@code NumberFormatException} escaping as an opaque
 * 500.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScheduleTaskTransferServiceTest {
   @Mock private ScheduleManager scheduleManager;
   @Mock private ScheduleService scheduleService;
   @Mock private Principal user;

   @Test void stageWrapsAMalformedTaskAttributeInACleanIllegalArgumentException() {
      ScheduleTaskTransferService service =
         new ScheduleTaskTransferService(scheduleManager, scheduleService);
      String xml = "<schedule><Task name=\"t1\" owner=\"admin\" enabled=\"true\" " +
         "lastModified=\"not-a-number\"><Condition type=\"TimeCondition\" hour=\"9\" " +
         "minute=\"0\"/></Task></schedule>";
      String base64 = Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.stage(base64, user));

      assertTrue(ex.getMessage().startsWith("xml:"), "expected a field-named xml: message, got: " +
         ex.getMessage());
      assertTrue(ex.getMessage().contains("t1"),
         "expected the failing task to be named in the message, got: " + ex.getMessage());
      assertInstanceOf(NumberFormatException.class, ex.getCause(),
         "the original parse failure should be preserved as the cause");
   }

   @Test void stageStillThrowsCleanlyOnUnparseableXml() {
      ScheduleTaskTransferService service =
         new ScheduleTaskTransferService(scheduleManager, scheduleService);
      String base64 = Base64.getEncoder().encodeToString("not xml at all".getBytes(StandardCharsets.UTF_8));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.stage(base64, user));

      assertTrue(ex.getMessage().startsWith("xml:"));
   }

   @Test void stageRefusesAnHourOutOfRange() {
      assertRefused(everyDayTask("t1", 25, 0, 0), "t1", "hour");
   }

   @Test void stageRefusesAMinuteOutOfRangeUnderAValidHour() {
      assertRefused(everyDayTask("t1", 9, 70, 0), "t1", "minute");
   }

   @Test void stageRefusesASecondOutOfRangeUnderAValidHourAndMinute() {
      assertRefused(everyDayTask("t1", 9, 30, 60), "t1", "second");
   }

   @Test void stageAcceptsATimeInRange() throws Exception {
      ScheduleTaskStagingResult result = stage(everyDayTask("t1", 23, 59, 59));

      assertEquals(1, result.tasks().size());
   }

   @Test void stageIgnoresTheTimeFieldsOfAnAtCondition() throws Exception {
      String xml = "<Task name=\"t1\" owner=\"admin\" enabled=\"true\"><Condition " +
         "type=\"TimeCondition\" timeType=\"0\" time=\"1700000000000\" hour=\"99\" " +
         "minute=\"99\" second=\"99\"/></Task>";

      assertEquals(1, stage(xml).tasks().size());
   }

   @Test void stageAcceptsAnEveryHourConditionWithoutEndFields() throws Exception {
      String xml = "<Task name=\"t1\" owner=\"admin\" enabled=\"true\"><Condition " +
         "type=\"TimeCondition\" timeType=\"8\" hour=\"0\" minute=\"15\" second=\"0\" " +
         "hour_end=\"-1\" minute_end=\"-1\" second_end=\"-1\"/></Task>";

      assertEquals(1, stage(xml).tasks().size());
   }

   @Test void stageRefusesAnEveryHourEndFieldOutOfRange() {
      String xml = "<Task name=\"t1\" owner=\"admin\" enabled=\"true\"><Condition " +
         "type=\"TimeCondition\" timeType=\"8\" hour=\"0\" minute=\"15\" second=\"0\" " +
         "hour_end=\"-1\" minute_end=\"60\" second_end=\"-1\"/></Task>";

      assertRefused(xml, "t1", "end minute");
   }

   @Test void stageRefusesTheWholeFileWhenOneTaskIsOutOfRange() {
      String xml = everyDayTask("good", 9, 0, 0) + everyDayTask("bad", 9, 61, 0);

      assertRefused(xml, "bad", "minute");
   }

   @Test void stageStillAcceptsTasksWithoutATimeCondition() throws Exception {
      String xml = "<Task name=\"none\" owner=\"admin\" enabled=\"true\"/>" +
         "<Task name=\"after\" owner=\"admin\" enabled=\"true\"><Condition " +
         "type=\"Completion\" task=\"none\"/></Task>";

      assertEquals(2, stage(xml).tasks().size());
   }

   @Test void stageAcceptsTasksWrittenByTheScheduleTaskItself() throws Exception {
      ScheduleTask daily = new ScheduleTask("daily");
      daily.setOwner(new IdentityID("admin", "host-org"));
      daily.addCondition(TimeCondition.at(7, 5, 0));
      ScheduleTask hourly = new ScheduleTask("hourly");
      hourly.setOwner(new IdentityID("admin", "host-org"));
      hourly.addCondition(TimeCondition.atHours(new int[0], 8, 30, 0));
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      daily.writeXML(writer);
      hourly.writeXML(writer);
      writer.flush();

      assertEquals(2, stage(out.toString()).tasks().size());
   }

   private ScheduleTaskStagingResult stage(String tasksXml) throws Exception {
      ScheduleTaskTransferService service =
         new ScheduleTaskTransferService(scheduleManager, scheduleService);
      String xml = "<schedule>" + tasksXml + "</schedule>";
      String base64 = Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
      return service.stage(base64, user);
   }

   private void assertRefused(String tasksXml, String taskName, String field) {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> stage(tasksXml));

      assertTrue(ex.getMessage().startsWith("xml:"), "got: " + ex.getMessage());
      assertTrue(ex.getMessage().contains("\"" + taskName + "\""), "got: " + ex.getMessage());
      assertTrue(ex.getMessage().contains("The " + field + " of a time condition"),
         "got: " + ex.getMessage());
   }

   private static String everyDayTask(String name, int hour, int minute, int second) {
      return "<Task name=\"" + name + "\" owner=\"admin\" enabled=\"true\"><Condition " +
         "type=\"TimeCondition\" timeType=\"1\" hour=\"" + hour + "\" minute=\"" + minute +
         "\" second=\"" + second + "\"/></Task>";
   }
}
