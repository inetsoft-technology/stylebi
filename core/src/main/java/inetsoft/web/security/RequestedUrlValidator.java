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
package inetsoft.web.security;

/**
 * Validates client-supplied post-login redirect targets (the {@code requestedUrl} of the login
 * page and the Google SSO state) so they cannot point at a foreign origin. The login page uses
 * the value both as the endpoint the credentials are sent to and as the post-login redirect.
 */
public final class RequestedUrlValidator {
   private static final char BACKSLASH = (char) 92;

   private RequestedUrlValidator() {
   }

   /**
    * Returns the requested URL if it is safe, otherwise the fallback.
    *
    * @param requestedUrl the client-supplied value.
    * @param linkUri      the server's link URI (scheme://host[:port]/context/).
    * @param fallback     the value to use when the requested URL is empty or unsafe.
    */
   public static String sanitize(String requestedUrl, String linkUri, String fallback) {
      return isSafe(requestedUrl, linkUri) ? requestedUrl : fallback;
   }

   /**
    * Determines whether the URL is an app-relative path (including a scheme-less one such as
    * {@code app/portal}) or an absolute URL starting with the server's own link URI.
    */
   public static boolean isSafe(String requestedUrl, String linkUri) {
      if(requestedUrl == null || requestedUrl.isEmpty()) {
         return false;
      }

      // browsers strip tab/CR/LF and leading/trailing C0 controls and spaces before parsing, so
      // such characters can turn an apparently relative value into "//host"; backslashes are
      // treated as slashes
      for(int i = 0; i < requestedUrl.length(); i++) {
         char c = requestedUrl.charAt(i);

         if(c <= 0x20 || c == 0x7f || c == BACKSLASH) {
            return false;
         }
      }

      if(linkUri != null && !linkUri.isEmpty() && requestedUrl.startsWith(linkUri) &&
         containsNoControl(linkUri))
      {
         return true;
      }

      if(requestedUrl.charAt(0) == '/') {
         // protocol-relative "//host" is foreign
         return requestedUrl.length() < 2 || requestedUrl.charAt(1) != '/';
      }

      // scheme-less relative path: a ':' before the first '/', '?' or '#' would be a scheme
      for(int i = 0; i < requestedUrl.length(); i++) {
         char c = requestedUrl.charAt(i);

         if(c == '/' || c == '?' || c == '#') {
            break;
         }

         if(c == ':') {
            return false;
         }
      }

      return true;
   }

   private static boolean containsNoControl(String value) {
      for(int i = 0; i < value.length(); i++) {
         if(value.charAt(i) <= 0x20 || value.charAt(i) == BACKSLASH) {
            return false;
         }
      }

      return true;
   }
}
