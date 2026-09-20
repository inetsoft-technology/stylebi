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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One requested schedule-task FOLDER change: {@code create} (a new folder under {@code
 * parentPath}), {@code rename} (change {@code path}'s own leaf name to {@code newPath}), {@code
 * move} (relocate {@code path} to be a child of {@code targetPath}), {@code moveTask} (relocate the
 * schedule TASK {@code taskId} to be a child of {@code targetPath} -- bug #76841, the only verb
 * here that acts on a task rather than a folder), or {@code delete} ({@code path}, recursively --
 * see {@link ScheduleFolderChangePlanService}'s own javadoc for why this is NOT a registry-label-
 * only operation, unlike a viewsheet folder). Exactly the fields the chosen {@code verb} uses
 * apply; {@link ScheduleFolderChangePlanService#resolve} refuses loud on any other field being
 * present, rather than silently ignoring it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleFolderChangeRequest {
   public static final String VERB_CREATE = "create";
   public static final String VERB_RENAME = "rename";
   public static final String VERB_MOVE = "move";
   public static final String VERB_MOVE_TASK = "moveTask";
   public static final String VERB_DELETE = "delete";

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   /** {@code rename}/{@code move}/{@code delete}: the folder being acted on. Not used for {@code
    * create} (use {@code parentPath}/{@code folderName} instead). */
   public String getPath() { return path; }
   public void setPath(String v) { this.path = v; }

   /** {@code create} only: the parent folder the new folder is created under (root if omitted). */
   public String getParentPath() { return parentPath; }
   public void setParentPath(String v) { this.parentPath = v; }

   /** {@code create} only: the new folder's own leaf name (not a full path). */
   public String getFolderName() { return folderName; }
   public void setFolderName(String v) { this.folderName = v; }

   /** {@code rename} only: {@code path}'s new full path. */
   public String getNewPath() { return newPath; }
   public void setNewPath(String v) { this.newPath = v; }

   /** {@code move}/{@code moveTask}: the destination parent folder {@code path}/{@code taskId} is
    * relocated under. */
   public String getTargetPath() { return targetPath; }
   public void setTargetPath(String v) { this.targetPath = v; }

   /** {@code moveTask} only: the id of the schedule task being moved (see {@code
    * list_schedule_tasks}/{@code get_schedule_task} for the id shape). Not used for any other
    * verb -- a folder is identified by {@code path}, a task by {@code taskId}. */
   public String getTaskId() { return taskId; }
   public void setTaskId(String v) { this.taskId = v; }

   /** {@code delete} only: required {@code true} whenever {@code path} is non-empty (contains any
    * schedule task, recursively). Not used for any other verb. */
   public boolean isForce() { return force; }
   public void setForce(boolean v) { this.force = v; }

   private String verb;
   private String path;
   private String parentPath;
   private String folderName;
   private String newPath;
   private String targetPath;
   private String taskId;
   private boolean force;
}
