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
package inetsoft.sree.security;

import inetsoft.sree.internal.SUtil;
import inetsoft.util.Catalog;
import inetsoft.util.DataSpace;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.config.*;
import inetsoft.web.admin.schedule.model.ServerLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Server-side rules for organization ids. An organization id is used as the top-level folder of
 * the organization's files in external storage, so it must not name a folder that the system
 * itself writes to, must not collide with the configured storage base path, and must not
 * contain path characters.
 */
public final class OrganizationIdRules {
   private OrganizationIdRules() {
   }

   /**
    * Checks the id of a new organization. An empty id is skipped, the caller generates one.
    *
    * @throws MessageException if the id is reserved or contains invalid characters.
    */
   public static void checkCreate(String id) throws MessageException {
      if(Tool.isEmptyString(id)) {
         return;
      }

      check(id);
   }

   /**
    * Checks a new organization id on rename. A null or unchanged id is not a change, so an
    * existing organization whose id predates these rules can still be saved.
    *
    * @throws MessageException if the new id is reserved or contains invalid characters.
    */
   public static void checkRename(String oldId, String newId) throws MessageException {
      if(newId == null || newId.equals(oldId)) {
         return;
      }

      check(newId);
   }

   /**
    * Determines if an id is reserved: a system folder of external storage, the default or self
    * organization id, or an id that collides with the external storage base path. The comparison
    * ignores case.
    */
   public static boolean isReserved(String id) {
      if(id == null) {
         return false;
      }

      String lower = id.toLowerCase(Locale.ROOT);

      if(SYSTEM_FOLDERS.contains(lower) ||
         lower.equals(Organization.getDefaultOrganizationID().toLowerCase(Locale.ROOT)) ||
         lower.equals(Organization.getSelfOrganizationID().toLowerCase(Locale.ROOT)))
      {
         return true;
      }

      String base = getStorageBasePath();

      if(base == null) {
         return false;
      }

      // S3 leaves a key unprefixed when it already starts with the base, so an id that starts
      // with the base or equals its first segment would write outside of, or over, other tenants
      int slash = base.indexOf('/');
      String first = slash < 0 ? base : base.substring(0, slash);
      return lower.startsWith(base) || lower.equals(first);
   }

   /**
    * Determines if a top-level folder of external storage is used by the system: one of the
    * system folders, or the first segment of the S3 base path. Unlike {@link #isReserved}, the
    * default organization id is not a system folder, its users write under it. The comparison
    * ignores case.
    */
   public static boolean isStorageSystemFolder(String segment) {
      if(segment == null) {
         return false;
      }

      String lower = segment.toLowerCase(Locale.ROOT);

      if(SYSTEM_FOLDERS.contains(lower)) {
         return true;
      }

      String base = getStorageBasePath();

      if(base == null) {
         return false;
      }

      int slash = base.indexOf('/');
      return lower.equals(slash < 0 ? base : base.substring(0, slash));
   }

   /**
    * Gets the paths of the configured server save locations, except the FTP locations, as they
    * are used in the keys of the task save files in external storage.
    */
   public static List<String> getSaveLocationPaths() {
      List<String> paths = new ArrayList<>();

      try {
         List<ServerLocation> locations = SUtil.getServerLocations();

         if(locations == null) {
            return paths;
         }

         for(ServerLocation location : locations) {
            // without trailing slashes, as addUserSpacePathPrefix uses it
            String path = location.path() == null ? null :
               location.path().replaceAll("[/\\\\]+$", "");
            boolean ftp = location.pathInfoModel() != null && location.pathInfoModel().ftp();

            if(!ftp && path != null && !trimSlashes(path).isEmpty() && !paths.contains(path)) {
               paths.add(path);
            }
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to get the server save locations", e);
      }

      return paths;
   }

   /**
    * Determines if a folder of external storage is, or is a parent of, one of the server save
    * locations. Renaming such a folder would move the files of every organization in the
    * location. The comparison ignores case and leading and trailing slashes.
    */
   public static boolean containsSaveLocation(String folder, List<String> locations) {
      String path = trimSlashes(folder).toLowerCase(Locale.ROOT);

      if(path.isEmpty()) {
         return false;
      }

      for(String location : locations) {
         String lpath = trimSlashes(location).toLowerCase(Locale.ROOT);

         if(lpath.equals(path) || lpath.startsWith(path + "/")) {
            return true;
         }
      }

      return false;
   }

   /**
    * Determines if the data space already contains paths that an organization with the id would
    * own (see {@link DataSpace#getOrgScopedPaths}). Such paths belong to someone else, and would
    * be moved on rename and deleted with the organization. The comparison is case-sensitive, like
    * the data space paths. Returns false if the data space is not available.
    */
   public static boolean hasDataSpacePaths(String id) {
      try {
         DataSpace dataSpace = DataSpace.getDataSpace();
         return dataSpace != null && dataSpace.hasOrgScopedPaths(id);
      }
      catch(Exception e) {
         LOG.debug("Failed to check the data space paths of organization id {}", id, e);
         return false;
      }
   }

   private static void check(String id) throws MessageException {
      if(!VALID_ID.matcher(id).matches() || isReserved(id) ||
         DATASPACE_FOLDERS.contains(id.toLowerCase(Locale.ROOT)))
      {
         throw new MessageException(
            Catalog.getCatalog().getString("em.security.reservedOrganizationID", id));
      }

      // not a reserved id, updateTaskSaveFiles still moves the files in the save locations of an
      // existing organization with this id, and skips only the folders of the locations
      if(isSaveLocationFolder(id)) {
         throw new MessageException(
            Catalog.getCatalog().getString("em.security.saveLocationOrganizationID", id));
      }

      if(hasDataSpacePaths(id)) {
         throw new MessageException(
            Catalog.getCatalog().getString("em.security.dataSpaceOrganizationID", id));
      }
   }

   /**
    * Determines if an id names a folder of a server save location path. The task save files
    * of an organization are in a folder named by the id at the top of external storage and in
    * each save location, so such an id would share its folder with the location.
    */
   private static boolean isSaveLocationFolder(String id) {
      for(String location : getSaveLocationPaths()) {
         for(String folder : trimSlashes(location).split("/")) {
            if(folder.equalsIgnoreCase(id)) {
               return true;
            }
         }
      }

      return false;
   }

   private static String trimSlashes(String path) {
      return path == null ? "" :
         path.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
   }

   /**
    * Gets the lowercase base path of the configured external storage, without leading or
    * trailing slashes, or null if there is none. Only S3 has a base path, GCS and Azure write to
    * the root of the bucket or container.
    */
   private static String getStorageBasePath() {
      try {
         ExternalStorageConfig storage = InetsoftConfig.getInstance().getExternalStorage();

         if(storage == null || !"s3".equals(storage.getType()) || storage.getS3() == null) {
            return null;
         }

         String path = storage.getS3().getPath();

         if(path == null) {
            return null;
         }

         path = path.trim().replaceAll("^/+", "").replaceAll("/+$", "");
         return path.isEmpty() ? null : path.toLowerCase(Locale.ROOT);
      }
      catch(Exception e) {
         LOG.debug("Failed to get the external storage base path", e);
         return null;
      }
   }

   // top-level folders written by DataSpaceSettingsService, ServerMonitoringController (heap
   // dumps) and StatusDumpService
   private static final Set<String> SYSTEM_FOLDERS = Set.of("backup", "heapdump", "status");
   // global data space folders that an org id would claim through the portal/<id> and <id>
   // shapes of DataSpace.getOrgScopedPaths: the top-level portal, sreeUserData, fonts,
   // web-assets and inetsoftdb, and the portal/shapes, portal/theme and portal/font folders.
   // Not system folders of external storage, so kept apart from SYSTEM_FOLDERS
   private static final Set<String> DATASPACE_FOLDERS = Set.of(
      "portal", "sreeuserdata", "fonts", "web-assets", "inetsoftdb", "shapes", "theme", "font");
   // same as FormValidators.validOrgID in the EM
   private static final Pattern VALID_ID = Pattern.compile("^[a-zA-Z0-9-]+$");
   private static final Logger LOG = LoggerFactory.getLogger(OrganizationIdRules.class);
}
