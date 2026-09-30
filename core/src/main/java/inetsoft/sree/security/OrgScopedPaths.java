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

/**
 * The data space paths that belong to an organization, and the rewrite of such a path to another
 * organization id. The org id is only one segment of a path, the rest of the path may contain
 * the same text (e.g. id "rt" in "portal", id "ta" in "sreeUserData", or in a user name), so
 * only that segment is rewritten. The comparisons are case-sensitive, like the storage keys.
 */
public final class OrgScopedPaths {
   private OrgScopedPaths() {
   }

   /**
    * Determines if a data space path belongs to an organization: {@code portal/<id>} and its
    * children, {@code <id>} and its children, {@code <id>__...}, and the per-user files
    * {@code sreeUserData/<user>_<id>.xml}.
    */
   public static boolean isOrgScopedPath(String path, String orgId) {
      return path.equals(PORTAL + orgId) || path.startsWith(PORTAL + orgId + "/") ||
         path.startsWith(orgId + "__") || path.equals(orgId) || path.startsWith(orgId + "/") ||
         path.startsWith(USER_DATA) && path.endsWith("_" + orgId + ".xml");
   }

   /**
    * Rewrites the organization segment of an org scoped path from the old id to the new id and
    * keeps the rest of the path unchanged. The shapes are tried in a fixed order, portal first,
    * then the per-user files, then the top-level folder, and only the first match is rewritten.
    * A path that does not belong to the old organization is returned unchanged.
    */
   public static String rewrite(String path, String oldId, String newId) {
      if(path == null || oldId == null || newId == null || oldId.equals(newId)) {
         return path;
      }

      String portal = PORTAL + oldId;

      if(path.equals(portal) || path.startsWith(portal + "/")) {
         return PORTAL + newId + path.substring(portal.length());
      }

      // match the whole suffix, a legacy org id may contain "_"
      String suffix = "_" + oldId + ".xml";

      if(path.startsWith(USER_DATA) && path.endsWith(suffix)) {
         return path.substring(0, path.length() - suffix.length()) + "_" + newId + ".xml";
      }

      if(path.equals(oldId) || path.startsWith(oldId + "/") || path.startsWith(oldId + "__")) {
         return newId + path.substring(oldId.length());
      }

      return path;
   }

   private static final String PORTAL = "portal/";
   private static final String USER_DATA = "sreeUserData/";
}
