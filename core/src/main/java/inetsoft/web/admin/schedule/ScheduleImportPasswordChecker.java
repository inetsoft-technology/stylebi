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

import com.fasterxml.jackson.databind.JsonNode;
import inetsoft.sree.schedule.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Bug #77936, applies the stored password rule of the task editor (#77192) to an imported
 * schedule task. The task editor keeps the stored password of a save-to-server path only for the
 * server and the login user it was saved for. An imported file names the server and holds the
 * password side by side, so a local (not secret id) password of the file is only kept if the
 * stored task that the import replaces already has the same password for the same server and
 * login user, the user in the path overriding the user name field (Bug #77979). Otherwise the
 * password is cleared and must be entered again in the task editor. Secret id paths are checked
 * by {@link ScheduleSecretIdChecker}.
 * <p>
 * Bug #77951, a deploy export writes a secret id path as the user name and password that the
 * secret resolves to, without the id. If the stored task uses a secret id for the same server
 * and that secret resolves to the same user name and password, the path gets the stored secret
 * id back instead of being cleared. Only ids of the stored task are restored, never one of the
 * file, so the path is bound to nothing that {@link ScheduleSecretIdChecker} doesn't already
 * accept for it.
 */
public final class ScheduleImportPasswordChecker {
   private ScheduleImportPasswordChecker() {
   }

   /**
    * Clears the local passwords of the save-to-server paths of an imported task that the stored
    * task doesn't already hold for the same server and login user. A path whose user name and
    * password are those of a secret id that the stored task uses for the same server is set
    * back to that secret id, it isn't cleared.
    *
    * @param task     the imported task, it's changed in place.
    * @param original the stored task that the import replaces, or {@code null} if none.
    *
    * @return the paths whose password was cleared, empty if none.
    */
   public static List<String> clearUnboundPasswords(ScheduleTask task, ScheduleTask original) {
      List<ServerPathInfo> originalPaths = new ArrayList<>();
      List<ServerPathInfo> originalSecretPaths = new ArrayList<>();

      for(ScheduleAction action : getActions(original)) {
         originalPaths.addAll(getPasswordPaths(action));
         originalSecretPaths.addAll(getSecretPaths(action));
      }

      List<String> cleared = new ArrayList<>();
      Map<String, Optional<JsonNode>> credentials = new HashMap<>();

      for(ScheduleAction action : getActions(task)) {
         for(ServerPathInfo path : getPasswordPaths(action)) {
            if(isStoredPassword(path, originalPaths)) {
               continue;
            }

            String secretId = getStoredSecretId(path, originalSecretPaths, credentials);

            if(secretId != null) {
               path.setUseCredential(true);
               path.setSecretId(secretId);
               path.setUsername(null);
               path.setPassword(null);
               LOG.debug("The save-to-server path {} of the imported task {} uses the secret " +
                         "id of the stored task again", path.getPath(), task.getTaskId());
            }
            else {
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
    * Determines if a stored path has the same password for the same server and login user.
    * Bug #77979, the user in the path overrides the user name field, as it does when the file is
    * uploaded.
    */
   private static boolean isStoredPassword(ServerPathInfo path, List<ServerPathInfo> originalPaths) {
      for(ServerPathInfo originalPath : originalPaths) {
         if(Objects.equals(path.getPassword(), originalPath.getPassword()) &&
            ScheduleService.isSameLogin(path.getPath(), path.getUsername(),
                                        originalPath.getPath(), originalPath.getUsername()))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Gets the secret id that a stored path uses for the same server, if the secret resolves to
    * the user name and password of the path.
    *
    * @param credentials the secrets resolved so far, by id. A secret that fails to resolve is
    *                    empty, it matches no path.
    *
    * @return the secret id, or {@code null} if none.
    */
   private static String getStoredSecretId(ServerPathInfo path,
                                           List<ServerPathInfo> originalSecretPaths,
                                           Map<String, Optional<JsonNode>> credentials)
   {
      FTPUtil.Endpoint endpoint = parseEndpoint(path);

      if(endpoint == null) {
         return null;
      }

      for(ServerPathInfo originalPath : originalSecretPaths) {
         FTPUtil.Endpoint originalEndpoint = parseEndpoint(originalPath);

         // Bug #77979, the user in the path overrides the secret's user name
         if(!endpoint.isSameServer(originalEndpoint) ||
            !ScheduleSecretIdChecker.isSamePathUser(endpoint, originalEndpoint))
         {
            continue;
         }

         String secretId = originalPath.getSecretId();
         JsonNode credential = credentials
            .computeIfAbsent(secretId, ScheduleImportPasswordChecker::loadCredentials)
            .orElse(null);

         // the deploy export writes the secret's user name and password, or no attribute if the
         // secret has none, which parses as an empty string
         if(credential != null &&
            getText(credential, "username").equals(Objects.toString(path.getUsername(), "")) &&
            getText(credential, "password").equals(path.getPassword()))
         {
            return secretId;
         }
      }

      return null;
   }

   private static Optional<JsonNode> loadCredentials(String secretId) {
      try {
         return Optional.ofNullable(Tool.loadCredentials(secretId));
      }
      catch(RuntimeException e) {
         LOG.debug("Failed to load the secret of a stored schedule path", e);
         return Optional.empty();
      }
   }

   private static String getText(JsonNode credential, String field) {
      return credential.has(field) ? credential.get(field).asText() : "";
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
      List<ServerPathInfo> paths = getServerPaths(action);
      paths.removeIf(path -> path.isUseCredential() || Tool.isEmptyString(path.getPassword()));
      return paths;
   }

   private static List<ServerPathInfo> getServerPaths(ScheduleAction action) {
      List<ServerPathInfo> paths = new ArrayList<>();

      if(action instanceof ViewsheetAction viewsheetAction &&
         viewsheetAction.getFilePathsMap() != null)
      {
         paths.addAll(viewsheetAction.getFilePathsMap().values());
      }
      else if(action instanceof IndividualAssetBackupAction backupAction) {
         paths.add(backupAction.getServerPath());
      }

      paths.removeIf(Objects::isNull);
      return paths;
   }

   /**
    * Gets the save-to-server paths of an action that log in with a secret id.
    */
   private static List<ServerPathInfo> getSecretPaths(ScheduleAction action) {
      List<ServerPathInfo> paths = getServerPaths(action);
      paths.removeIf(path -> !path.isUseCredential() || Tool.isEmptyString(path.getSecretId()));
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
