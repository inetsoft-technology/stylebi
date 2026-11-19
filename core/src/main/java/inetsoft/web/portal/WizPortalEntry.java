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
package inetsoft.web.portal;

import inetsoft.sree.SreeEnv;

/**
 * Whether the portal toolbar shows the button that opens the standalone Wiz portal in a new
 * browser tab, and where that tab points.
 */
public final class WizPortalEntry {
   public static final String WIZ_PORTAL_VISIBLE = "wiz.portal.visible";
   public static final String WIZ_PORTAL_URL = "wiz.portal.url";

   private WizPortalEntry() {
   }

   public static boolean isVisible() {
      return "true".equalsIgnoreCase(SreeEnv.getProperty(WIZ_PORTAL_VISIBLE, "false")) &&
         getUrl() != null;
   }

   /**
    * The configured URL, trimmed, or null when it is missing or not http(s) / root-relative. The
    * scheme check keeps a mis-configured value such as {@code javascript:...} out of an href.
    */
   public static String getUrl() {
      String url = SreeEnv.getProperty(WIZ_PORTAL_URL);

      if(url == null) {
         return null;
      }

      url = url.trim();

      if(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/")) {
         return url;
      }

      return null;
   }
}
