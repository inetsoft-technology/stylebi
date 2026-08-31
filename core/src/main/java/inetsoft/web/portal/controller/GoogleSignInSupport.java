/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.portal.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;

/**
 * Reads the Google sign-in settings that are rendered onto the unauthenticated pages which
 * offer the Google Identity Services button (login and signup). Both pages must resolve the
 * settings the same way, so they share this class instead of reading the properties directly.
 */
final class GoogleSignInSupport {
   /**
    * Checks whether the Google sign-in button should be offered.
    */
   static boolean isEnabled() {
      return SreeEnv.getBooleanProperty("security.googleSignIn.enabled");
   }

   /**
    * Gets the Google OpenID client id to initialize the sign-in button with.
    *
    * The property may hold a reference to a secret kept in a cloud secrets manager rather than
    * the client id itself, so it must be resolved before it is rendered onto a page. Do not
    * drop the {@link Tool#getClientSecretRealValue(String, String)} call: it returns the value
    * unchanged when the client id is stored literally, which is the only case where reading the
    * property by itself happens to work.
    */
   static String getClientId() {
      return Tool.getClientSecretRealValue(
         SreeEnv.getProperty("styleBI.google.openid.client.id"), "client_id");
   }

   /**
    * Gets the scopes requested from Google.
    */
   static String getScopes() {
      return SreeEnv.getProperty("styleBI.google.openid.scopes", "openid email profile");
   }

   private GoogleSignInSupport() {
   }
}
