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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Whether the portal shows the floating button that opens the standalone Wiz portal in a new
 * browser tab, and where that tab points.
 */
public final class WizPortalEntry {
   public static final String WIZ_PORTAL_VISIBLE = "wiz.portal.visible";
   /**
    * The Wiz gateway address: the Wiz portal is served at its root, the portal's Wiz link
    * opens it and the SSO token endpoint accepts Wiz callbacks only under it.
    */
   public static final String WIZ_SERVICE_URL = "wiz.service.url";

   private WizPortalEntry() {
   }

   public static boolean isVisible() {
      return "true".equalsIgnoreCase(SreeEnv.getProperty(WIZ_PORTAL_VISIBLE, "false")) &&
         getUrl() != null;
   }

   /**
    * The Wiz gateway address ({@code wiz.service.url}, where the Wiz portal is served at the
    * root), trimmed, or null when it is missing or not a safe absolute http(s) URL. This keeps a
    * mis-configured value such as {@code javascript:...}, a protocol-relative {@code //host} or a
    * header-splitting control character out of an href.
    */
   public static String getUrl() {
      return normalizeUrl(SreeEnv.getProperty(WIZ_SERVICE_URL));
   }

   /**
    * The trimmed {@code raw} URL when it is a safe absolute http(s) URL with a host and no query
    * or fragment, otherwise null. The same value is the SSO callback allow-list prefix (a callback
    * path is appended to it), so it needs a full origin and must end in a path.
    */
   public static String normalizeUrl(String raw) {
      String url = raw;

      if(url == null) {
         return null;
      }

      url = url.trim();

      if(url.isEmpty() || containsWhitespaceOrControl(url)) {
         return null;
      }

      return isHttpUrlWithHost(url) ? url : null;
   }

   private static boolean containsWhitespaceOrControl(String url) {
      for(int i = 0; i < url.length(); i++) {
         char c = url.charAt(i);

         if(c <= 0x1F || c == 0x7F || Character.isWhitespace(c) || Character.isSpaceChar(c)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Userinfo is rejected because it is never needed for a portal link and lets
    * {@code https://trusted.example@evil.example} pass for a different host.
    */
   private static boolean isHttpUrlWithHost(String url) {
      String lower = url.toLowerCase(Locale.ROOT);

      if(!lower.startsWith("http://") && !lower.startsWith("https://")) {
         return false;
      }

      try {
         URI uri = new URI(url);
         String host = uri.getHost();
         return host != null && !host.isEmpty() && uri.getRawUserInfo() == null &&
            uri.getRawQuery() == null && uri.getRawFragment() == null;
      }
      catch(URISyntaxException ex) {
         return false;
      }
   }
}
