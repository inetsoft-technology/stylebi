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

import inetsoft.sree.schedule.AbstractAction;
import inetsoft.sree.schedule.ScheduleAction;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import org.w3c.dom.Document;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.Principal;

/**
 * The single transformation a staged schedule task goes through before it is stored by a
 * schedule-task import. Both the plan projection (preview) and the apply use it, so the plan the
 * reviewer signs shows exactly what is written: every action's link rewritten to this server (as
 * {@code ImportTaskController.updateTaskInfo} does), conditions sanitized against the principal's
 * schedule permissions, then each action sanitized and its secret ids checked, in that order.
 */
final class ScheduleTaskImportTransform {
   private ScheduleTaskImportTransform() {
   }

   /**
    * @return a transformed deep copy of {@code staged}; {@code staged} itself (the shared staging
    *         cache instance) is never mutated.
    */
   static ScheduleTask transform(ScheduleTask staged, ScheduleTask existing, String linkURI,
                                 Principal user, ScheduleTaskService scheduleTaskService)
      throws Exception
   {
      ScheduleTask copy = deepCopy(staged);
      updateTaskLinkUri(copy, linkURI);
      ScheduleTask baseline = existing == null ? new ScheduleTask() : existing;
      scheduleTaskService.sanitizeConditions(copy, baseline, user);

      for(int i = 0; i < copy.getActionCount(); i++) {
         ScheduleAction pairedOld = baseline.getActionCount() > i ? baseline.getAction(i) : null;
         scheduleTaskService.sanitizeAction(copy.getAction(i), pairedOld, user);
      }

      return copy;
   }

   /** As {@link #transform}, but a rejected action surfaces as a field-named
    * IllegalArgumentException at preview instead of failing at apply after the backup. */
   static ScheduleTask transformForPlan(String label, ScheduleTask staged, ScheduleTask existing,
                                        String linkURI, Principal user,
                                        ScheduleTaskService scheduleTaskService)
      throws Exception
   {
      try {
         return transform(staged, existing, linkURI, user, scheduleTaskService);
      }
      catch(MessageException e) {
         throw new IllegalArgumentException(
            label + ".taskId: the staged task cannot be imported as this caller -- " +
            e.getMessage(), e);
      }
   }

   /**
    * Deep-copies {@code task}, isolating every condition/action from the source instance, via an
    * XML round-trip through {@code ScheduleTask.writeXML}/{@code parseXML}. A plain clone only
    * copies the Vectors, so in-place mutation by the sanitizers would corrupt the staging cache.
    */
   static ScheduleTask deepCopy(ScheduleTask task) throws Exception {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         task.writeXML(writer);
      }

      Document document = Tool.parseXML(new StringReader(buffer.toString()));
      ScheduleTask copy = new ScheduleTask();
      copy.parseXML(document.getDocumentElement());
      return copy;
   }

   /** Mirrors {@code ImportTaskController.updateTaskInfo} exactly. */
   private static void updateTaskLinkUri(ScheduleTask task, String linkURI) {
      task.getActionStream().forEach(action -> {
         if(action instanceof AbstractAction abstractAction) {
            abstractAction.setLinkURI(Tool.replaceLocalhost(linkURI));
         }
      });
   }
}
