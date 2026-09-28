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

import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.config.*;
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

   private static void check(String id) throws MessageException {
      if(!VALID_ID.matcher(id).matches() || isReserved(id)) {
         throw new MessageException(
            Catalog.getCatalog().getString("em.security.reservedOrganizationID", id));
      }
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
   // same as FormValidators.validOrgID in the EM
   private static final Pattern VALID_ID = Pattern.compile("^[a-zA-Z0-9-]+$");
   private static final Logger LOG = LoggerFactory.getLogger(OrganizationIdRules.class);
}
