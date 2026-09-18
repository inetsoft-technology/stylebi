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
import inetsoft.web.admin.schedule.ScheduleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
