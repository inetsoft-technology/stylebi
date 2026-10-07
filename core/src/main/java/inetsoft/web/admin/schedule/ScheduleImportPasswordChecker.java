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
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Bug #77936, applies the stored password rule of the task editor (#77192) to an imported
 * schedule task. The task editor keeps the stored password of a save-to-server path only for the
 * server it was saved for. An imported file names the server and holds the password side by
 * side, so a local (not secret id) password of the file is only kept if the stored task that the
 * import replaces already has the same user name and password for the same server. Otherwise the
 * password is cleared and must be entered again in the task editor. Secret id paths are checked
 * by {@link ScheduleSecretIdChecker}.
 */
public final class ScheduleImportPasswordChecker {
   private ScheduleImportPasswordChecker() {
   }

   /**
    * Clears the local passwords of the save-to-server paths of an imported task that the stored
    * task doesn't already hold for the same server and user name.
    *
    * @param task     the imported task, it's changed in place.
    * @param original the stored task that the import replaces, or {@code null} if none.
    *
    * @return the paths whose password was cleared, empty if none.
    */
   public static List<String> clearUnboundPasswords(ScheduleTask task, ScheduleTask original) {
      List<ServerPathInfo> originalPaths = new ArrayList<>();

      for(ScheduleAction action : getActions(original)) {
         originalPaths.addAll(getPasswordPaths(action));
      }

      List<String> cleared = new ArrayList<>();

      for(ScheduleAction action : getActions(task)) {
         for(ServerPathInfo path : getPasswordPaths(action)) {
            if(!isStoredPassword(path, originalPaths)) {
               path.setPassword("");
               cleared.add(path.getPath());
            }
         }
      }

      if(!cleared.isEmpty()) {
         LOG.warn("The passwords of the save-to-server paths {} of the imported task {} are " +
                  "cleared, the stored task doesn't use them for the same server and user, " +
                  "enter them again in the task", cleared, task.getTaskId());
      }

      return cleared;
   }

   /**
    * Determines if a stored path has the same user name and password for the same server.
    */
   private static boolean isStoredPassword(ServerPathInfo path, List<ServerPathInfo> originalPaths) {
      FTPUtil.Endpoint endpoint = parseEndpoint(path);

      if(endpoint == null) {
         return false;
      }

      for(ServerPathInfo originalPath : originalPaths) {
         if(Objects.equals(path.getUsername(), originalPath.getUsername()) &&
            Objects.equals(path.getPassword(), originalPath.getPassword()) &&
            endpoint.isSameServer(parseEndpoint(originalPath)))
         {
            return true;
         }
      }

      return false;
   }

   private static FTPUtil.Endpoint parseEndpoint(ServerPathInfo path) {
      try {
         return path.getPath() == null ? null : FTPUtil.parseEndpoint(path);
      }
      catch(Exception e) {
         LOG.debug("Failed to parse the server of a schedule path", e);
         return null;
      }
   }

   /**
    * Gets the save-to-server paths of an action that log in with a local password.
    */
   private static List<ServerPathInfo> getPasswordPaths(ScheduleAction action) {
      List<ServerPathInfo> paths = new ArrayList<>();

      if(action instanceof ViewsheetAction viewsheetAction &&
         viewsheetAction.getFilePathsMap() != null)
      {
         paths.addAll(viewsheetAction.getFilePathsMap().values());
      }
      else if(action instanceof IndividualAssetBackupAction backupAction) {
         paths.add(backupAction.getServerPath());
      }

      paths.removeIf(path -> path == null || path.isUseCredential() ||
         Tool.isEmptyString(path.getPassword()));
      return paths;
   }

   private static List<ScheduleAction> getActions(ScheduleTask task) {
      List<ScheduleAction> actions = new ArrayList<>();

      if(task != null) {
         for(int i = 0; i < task.getActionCount(); i++) {
            actions.add(task.getAction(i));
         }
      }

      return actions;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ScheduleImportPasswordChecker.class);
}
