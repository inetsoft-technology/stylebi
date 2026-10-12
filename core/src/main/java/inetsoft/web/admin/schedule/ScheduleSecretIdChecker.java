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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.util.*;
import inetsoft.web.admin.schedule.model.ServerLocation;
import inetsoft.web.admin.schedule.model.ServerPathInfoModel;
import inetsoft.web.portal.data.SecretIdAuthorizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;

/**
 * Decides which cloud secret ids a user may reference from a schedule task. A task resolves the
 * ids of its save-to-server paths to log in to the server in the path, and the id of its email
 * attachment password to encrypt the attachment. Secret ids are not scoped to a task or an
 * organization, so an id is only accepted if one of these holds:
 * <ul>
 *    <li>the stored task already uses it in the same kind of field. A server path must also
 *    point to the same server as the stored one, with the same user in the path;</li>
 *    <li>it is the id of an administrator-configured server location, and the path points to
 *    the server of that location, with the same user in the path, and is inside the location's
 *    folder;</li>
 *    <li>the caller is a site administrator, or an organization administrator when
 *    multi-tenancy is off.</li>
 * </ul>
 * Only cloud secrets are checked.
 */
public class ScheduleSecretIdChecker {
   public ScheduleSecretIdChecker(SecurityEngine securityEngine) {
      this.authorizer = new SecretIdAuthorizer(securityEngine, null);
   }

   /**
    * Throws an exception if an action references a secret id that the caller may not use.
    *
    * @param action          the action being saved.
    * @param originalActions the actions of the stored task, may be empty.
    * @param principal       the caller.
    */
   public void checkSecretIds(ScheduleAction action, Collection<ScheduleAction> originalActions,
                              Principal principal)
   {
      if(!isAllowed(action, originalActions, principal)) {
         throw new MessageException(
            Catalog.getCatalog().getString("em.schedule.secretIdNotAllowed"));
      }
   }

   /**
    * Determines if the caller may use all the secret ids that a task references.
    *
    * @param task      the task being saved or imported.
    * @param original  the stored task that it replaces, or {@code null} if none.
    * @param principal the caller.
    */
   public boolean isAllowed(ScheduleTask task, ScheduleTask original, Principal principal) {
      List<ScheduleAction> originalActions = getActions(original);

      for(ScheduleAction action : getActions(task)) {
         if(!isAllowed(action, originalActions, principal)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Determines if the caller may use all the secret ids that an action references.
    *
    * @param action          the action being saved.
    * @param originalActions the actions of the stored task, may be empty.
    * @param principal       the caller.
    */
   public boolean isAllowed(ScheduleAction action, Collection<ScheduleAction> originalActions,
                            Principal principal)
   {
      List<ServerPathInfo> paths = getCredentialPaths(action);
      String passwordId = getPasswordSecretId(action);

      if(paths.isEmpty() && passwordId == null || !Tool.isCloudSecrets()) {
         return true;
      }

      List<ServerPathInfo> originalPaths = new ArrayList<>();
      Set<String> originalPasswordIds = new HashSet<>();

      for(ScheduleAction originalAction : originalActions) {
         if(originalAction != null) {
            originalPaths.addAll(getCredentialPaths(originalAction));
            String id = getPasswordSecretId(originalAction);

            if(id != null) {
               originalPasswordIds.add(id);
            }
         }
      }

      boolean allowed = paths.stream().allMatch(path -> isStoredPath(path, originalPaths) ||
         isServerLocationPath(path)) &&
         (passwordId == null || originalPasswordIds.contains(passwordId));
      return allowed || authorizer.canIntroduceSecretIds(principal);
   }

   /**
    * Determines if a stored path uses the same secret id for the same server and user in the path.
    */
   private static boolean isStoredPath(ServerPathInfo path, List<ServerPathInfo> originalPaths) {
      FTPUtil.Endpoint endpoint = parseEndpoint(path);

      if(endpoint == null) {
         return false;
      }

      for(ServerPathInfo originalPath : originalPaths) {
         FTPUtil.Endpoint originalEndpoint = parseEndpoint(originalPath);

         if(Objects.equals(path.getSecretId(), originalPath.getSecretId()) &&
            endpoint.isSameServer(originalEndpoint) && isSamePathUser(endpoint, originalEndpoint))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Bug #77979, determines if two paths have the same user in the path. The user in the path
    * overrides the user name of the secret when the file is uploaded, so a secret may only be
    * used with the path user it is stored or configured with.
    */
   static boolean isSamePathUser(FTPUtil.Endpoint endpoint, FTPUtil.Endpoint other) {
      return other != null && Objects.equals(getPathUser(endpoint), getPathUser(other));
   }

   private static String getPathUser(FTPUtil.Endpoint endpoint) {
      String userInfo = endpoint.userInfo();
      int colon = userInfo == null ? -1 : userInfo.indexOf(':');
      return colon < 0 ? userInfo : userInfo.substring(0, colon);
   }

   /**
    * Determines if a path is inside a configured server location that uses the same secret id.
    */
   private static boolean isServerLocationPath(ServerPathInfo path) {
      FTPUtil.Endpoint endpoint = parseEndpoint(path);

      if(endpoint == null) {
         return false;
      }

      for(ServerLocation location : SUtil.getServerLocations()) {
         ServerPathInfoModel model = location.pathInfoModel();

         if(model == null || !model.useCredential() ||
            !Objects.equals(path.getSecretId(), model.secretId()))
         {
            continue;
         }

         FTPUtil.Endpoint locationEndpoint = parseEndpoint(new ServerPathInfo(model));

         if(locationEndpoint != null && endpoint.isSameServer(locationEndpoint) &&
            isSamePathUser(endpoint, locationEndpoint) &&
            isInFolder(endpoint.path(), locationEndpoint.path()))
         {
            return true;
         }
      }

      return false;
   }

   static boolean isInFolder(String path, String folder) {
      if(path == null || folder == null || Arrays.asList(path.split("/")).contains("..")) {
         return false;
      }

      folder = folder.replaceAll("/+$", "");
      return path.equals(folder) || path.startsWith(folder + "/");
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
    * Gets the save-to-server paths of an action that log in with a secret id.
    */
   private static List<ServerPathInfo> getCredentialPaths(ScheduleAction action) {
      List<ServerPathInfo> paths = new ArrayList<>();

      if(action instanceof ViewsheetAction viewsheetAction &&
         viewsheetAction.getFilePathsMap() != null)
      {
         paths.addAll(viewsheetAction.getFilePathsMap().values());
      }
      else if(action instanceof IndividualAssetBackupAction backupAction) {
         paths.add(backupAction.getServerPath());
      }

      paths.removeIf(path -> path == null || !path.isUseCredential() ||
         Tool.isEmptyString(path.getSecretId()));
      return paths;
   }

   /**
    * Gets the secret id of the email attachment password of an action.
    */
   private static String getPasswordSecretId(ScheduleAction action) {
      if(action instanceof AbstractAction abstractAction && abstractAction.isUseCredential() &&
         !Tool.isEmptyString(abstractAction.getSecretId()))
      {
         return abstractAction.getSecretId();
      }

      return null;
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

   private final SecretIdAuthorizer authorizer;
   private static final Logger LOG = LoggerFactory.getLogger(ScheduleSecretIdChecker.class);
}
