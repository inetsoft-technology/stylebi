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
import inetsoft.sree.schedule.TimeRange;
import inetsoft.util.Tool;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.security.auth.MissingResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Export and import-staging for the "Schedule Task export/import" admin-plugin area (Redmine
 * #76719 Gap 3) -- EM's own {@code Settings > Schedule > Tasks} Export/Import Task dialogs, a
 * SEPARATE feature from the existing Repository asset export/import area (which is explicitly
 * scoped to viewsheet/worksheet only; a schedule task under a selected folder is silently excluded
 * from THAT area's own export selection).
 *
 * <p>Talks to {@code ScheduleManager}/{@code ScheduleService}/{@code ScheduleTask}/{@code TimeRange}
 * directly, never to {@code ExportTaskController}/{@code ImportTaskController} (session-cookie/
 * CSRF EM SPA endpoints -- {@code ImportTaskController} additionally stashes its own parsed state
 * in {@code HttpSession}, which a stateless bearer-token wiz client cannot use at all) -- the same
 * "reuse the real service, not the internal controller" rule every other wiz admin-chat area
 * follows.
 *
 * <p>Import staging deliberately mirrors {@code AdminAssetImportController}'s own stage/preview/
 * apply discipline for the Repository asset import area (the closest architectural precedent in
 * this plugin for "upload something, resolve a plan against it, apply later") rather than the
 * simpler bare-action shape export itself uses -- import mutates the schedule task list and can
 * overwrite an existing task, so it needs the full planHash/review discipline every mutating area
 * in this plugin uses.
 */
@Component
public class ScheduleTaskTransferService {
   @Autowired
   public ScheduleTaskTransferService(ScheduleManager scheduleManager, ScheduleService scheduleService) {
      this.scheduleManager = scheduleManager;
      this.scheduleService = scheduleService;
   }

   // ---------------------------------------------------------------- export

   /**
    * @throws IllegalArgumentException naming any task id in {@code taskIds} that does not resolve
    *         to a real, currently-existing task.
    */
   public ScheduleTaskExportResult export(List<String> taskIds, boolean includeDependencies)
      throws Exception
   {
      if(taskIds == null || taskIds.isEmpty()) {
         throw new IllegalArgumentException("taskIds: at least one task id is required");
      }

      for(String taskId : taskIds) {
         if(scheduleManager.getScheduleTask(taskId) == null) {
            throw new IllegalArgumentException(
               "taskIds: no schedule task found for \"" + taskId + "\"");
         }
      }

      Set<String> selected = new LinkedHashSet<>(taskIds);
      Map<String, String> requiredBy = new LinkedHashMap<>();

      for(String taskId : taskIds) {
         collectDependencies(taskId, requiredBy, selected);
      }

      List<String> includedDependencyIds = new ArrayList<>();
      List<String> missingDependencyIds = new ArrayList<>();

      if(includeDependencies) {
         includedDependencyIds.addAll(requiredBy.keySet());
      }
      else {
         missingDependencyIds.addAll(requiredBy.keySet());
      }

      List<String> exportIds = new ArrayList<>(taskIds);

      if(includeDependencies) {
         exportIds.addAll(requiredBy.keySet());
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      scheduleService.exportScheduledTasks(exportIds.toArray(new String[0]), out);
      String xml = Base64.getEncoder().encodeToString(out.toByteArray());
      return new ScheduleTaskExportResult(xml, List.copyOf(exportIds),
         List.copyOf(includedDependencyIds), List.copyOf(missingDependencyIds));
   }

   /** Mirrors {@code ExportTaskController.getTaskRequired}'s own recursive dependency walk exactly
    * -- replicated here because this area calls {@code ScheduleManager} directly rather than
    * through that controller. {@code requiredBy} accumulates every transitively-required task id
    * not already in {@code selected}, mapped to a comma-joined list of the selected task id(s) that
    * (transitively) need it. */
   private void collectDependencies(String taskId, Map<String, String> requiredBy, Set<String> selected) {
      ScheduleTask task = scheduleManager.getScheduleTask(taskId);

      if(task == null) {
         return;
      }

      Enumeration<String> dependencies = task.getDependency();

      while(dependencies.hasMoreElements()) {
         String dependency = dependencies.nextElement();

         if(selected.contains(dependency)) {
            continue;
         }

         String existing = requiredBy.get(dependency);

         if(existing == null) {
            requiredBy.put(dependency, taskId);
            collectDependencies(dependency, requiredBy, selected);
         }
         else if(existing.indexOf(taskId) < 0) {
            requiredBy.put(dependency, existing + "," + taskId);
            collectDependencies(dependency, requiredBy, selected);
         }
      }
   }

   // ---------------------------------------------------------------- import staging

   /**
    * Parses (never mutates) an uploaded export file's {@code <Task>} elements, mirroring
    * {@code ImportTaskController.setTaskFile}'s own XML shape exactly -- but stashes the parsed
    * {@code List<ScheduleTask>} in this service's own token-keyed cache instead of
    * {@code HttpSession}, since a stateless bearer-token client has no session to stash into.
    *
    * @throws IllegalArgumentException if {@code xmlBase64} does not decode/parse as the expected
    *         {@code <schedule>} document shape.
    */
   public ScheduleTaskStagingResult stage(String xmlBase64, Principal user) throws Exception {
      byte[] bytes;

      try {
         bytes = Base64.getDecoder().decode(xmlBase64);
      }
      catch(IllegalArgumentException e) {
         throw new IllegalArgumentException("xml: not valid base64", e);
      }

      Document doc;

      try(InputStream in = new ByteArrayInputStream(bytes)) {
         doc = Tool.parseXML(in, "utf-8");
      }
      catch(Exception e) {
         throw new IllegalArgumentException("xml: could not be parsed as a schedule export file", e);
      }

      NodeList taskNodes = doc.getElementsByTagName("Task");

      if(taskNodes.getLength() == 0) {
         throw new IllegalArgumentException(
            "xml: no <Task> elements found -- this does not look like a schedule task export file");
      }

      List<ScheduleTask> tasks = new ArrayList<>();
      List<StagedScheduleTask> summaries = new ArrayList<>();

      for(int i = 0; i < taskNodes.getLength(); i++) {
         Element element = (Element) taskNodes.item(i);
         ScheduleTask task = new ScheduleTask();

         try {
            task.parseXML(element);
         }
         catch(Exception e) {
            String name = element.getAttribute("name");
            String identifier = Tool.isEmptyString(name) ? "index " + i : "\"" + name + "\"";
            throw new IllegalArgumentException(
               "xml: <Task> " + identifier + " could not be parsed (" + messageOf(e) + ")", e);
         }

         tasks.add(task);
         summaries.add(new StagedScheduleTask(task.getTaskId(), dependencyOf(task),
            scheduleManager.getScheduleTask(task.getTaskId()) != null));
      }

      int timeRangesInFile = 0;
      Element scheduleNode = Tool.getChildNodeByTagName(doc, "schedule");
      Element timeRangesNode = scheduleNode == null ? null :
         Tool.getChildNodeByTagName(scheduleNode, "timeRanges");

      if(timeRangesNode != null) {
         timeRangesInFile = timeRangesNode.getElementsByTagName("timeRange").getLength();
      }

      String stagingToken = UUID.randomUUID().toString();
      evictExpired();
      staged.put(stagingToken, new Staged(tasks, Instant.now(),
         user == null ? null : user.getName()));
      return new ScheduleTaskStagingResult(stagingToken, List.copyOf(summaries), timeRangesInFile);
   }

   /**
    * @throws MissingResourceException if {@code stagingToken} is unknown or has idled past its
    *         30-minute window -- the only remedy is to {@code stage_schedule_task_import} again.
    */
   public ScheduleTask requireStagedTask(String stagingToken, String taskId) throws MissingResourceException {
      Staged entry = requireStaged(stagingToken);

      for(ScheduleTask task : entry.tasks) {
         if(taskId.equals(task.getTaskId())) {
            return task;
         }
      }

      throw new MissingResourceException(
         "taskId: \"" + taskId + "\" is not one of the tasks staged under this stagingToken");
   }

   private Staged requireStaged(String stagingToken) throws MissingResourceException {
      evictExpired();
      Staged entry = stagingToken == null ? null : staged.get(stagingToken);

      if(entry == null) {
         throw new MissingResourceException(
            "stagingToken: unknown or expired (staged content is held in memory for 30 minutes " +
            "of idle time) -- call stage_schedule_task_import again");
      }

      entry.lastAccess = Instant.now();
      return entry;
   }

   private void evictExpired() {
      Instant cutoff = Instant.now().minus(IDLE_TIMEOUT);
      staged.values().removeIf(entry -> entry.lastAccess.isBefore(cutoff));
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static String dependencyOf(ScheduleTask task) {
      Enumeration<String> dependencies = task.getDependency();
      StringBuilder result = new StringBuilder();

      while(dependencies.hasMoreElements()) {
         if(result.length() > 0) {
            result.append(",");
         }

         result.append(dependencies.nextElement());
      }

      return result.toString();
   }

   /** One staged upload -- an in-memory, single-node, idle-expiring cache entry, the same "plain
    * map, no cross-node replication" scope {@code SheetSessionService} already uses elsewhere in
    * this codebase for comparable short-lived session state (there is no pre-existing StyleBI
    * primitive for staging an uploaded schedule-task file the way {@code ImportAssetServiceProxy}
    * already provides for a repository-asset zip). */
   private static final class Staged {
      Staged(List<ScheduleTask> tasks, Instant lastAccess, String ownerName) {
         this.tasks = tasks;
         this.lastAccess = lastAccess;
         this.ownerName = ownerName;
      }

      final List<ScheduleTask> tasks;
      volatile Instant lastAccess;
      final String ownerName;
   }

   private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
   private final ConcurrentHashMap<String, Staged> staged = new ConcurrentHashMap<>();
   private final ScheduleManager scheduleManager;
   private final ScheduleService scheduleService;
}
