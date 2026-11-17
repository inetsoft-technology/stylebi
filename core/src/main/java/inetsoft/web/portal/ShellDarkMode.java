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

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.UserEnv;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.security.Principal;

/**
 * Resolves whether the portal and composer shell (navigation, panels, dialogs, lists, forms) is
 * drawn dark. See docs/superpowers/specs/lookfeel/dark-mode.md, "Shell dark".
 *
 * <p>The resolved value is written by the server as the {@link #CSS_CLASS} class on the page's
 * {@code <html>} element, so dark styles apply before the first paint. Dashboard content is not
 * affected: each assembly keeps following its own {@code VizMark}.</p>
 *
 * <p>Resolution order:</p>
 * <ol>
 *    <li>A custom theme is applied: never dark. A custom theme is a full shell palette, and mixing it
 *        with the dark tokens gives unpredictable contrast.</li>
 *    <li>The user's preference ({@link #USER_PREFERENCE}, {@code light} or {@code dark}).</li>
 *    <li>The {@link #PROPERTY} property, organization scoped with a fallback to the site value.</li>
 * </ol>
 *
 * <p>The property defaults to {@code false} and is not exposed in any UI until the portal and the
 * composer are both converted. With it off and no user preference stored, nothing changes: the
 * class and the cookie are never written.</p>
 */
public final class ShellDarkMode {
   /**
    * Organization-scoped property with site fallback. The default for users who have not chosen.
    */
   public static final String PROPERTY = "portal.shellDark";

   /**
    * {@link UserEnv} key holding the user's choice, {@link #LIGHT} or {@link #DARK}. Absent means
    * "use the organization default".
    */
   public static final String USER_PREFERENCE = "shellAppearance";

   public static final String LIGHT = "light";
   public static final String DARK = "dark";

   /**
    * Class written on the {@code <html>} element when the shell is dark.
    */
   public static final String CSS_CLASS = "shell-dark";

   /**
    * Remembers the last resolved mode so the login page, which cannot read the user's preference,
    * can match it. A display hint only: it grants nothing and identifies no one.
    */
   public static final String COOKIE = "inetsoft.shellDark";

   private static final int COOKIE_MAX_AGE = 60 * 60 * 24 * 365;

   /**
    * Script editor theme for a dark shell. A dark shell never has a custom theme, so it replaces
    * the default light editor theme only.
    */
   public static final String SCRIPT_THEME_CSS_PATH = "../" + CustomTheme.ScriptTheme.DARCULA.getFileName();

   private ShellDarkMode() {
   }

   /**
    * Resolves the shell mode for a portal, viewer or composer page.
    *
    * @param principal          the current user, or {@code null}.
    * @param customThemeApplied {@code true} when the page links a custom theme style sheet.
    *
    * @return {@code true} if the shell is drawn dark.
    */
   public static boolean isDark(Principal principal, boolean customThemeApplied) {
      if(customThemeApplied) {
         return false;
      }

      String preference = getUserPreference(principal);

      if(DARK.equals(preference)) {
         return true;
      }

      if(LIGHT.equals(preference)) {
         return false;
      }

      return SreeEnv.getBooleanProperty(PROPERTY, false, true);
   }

   /**
    * Resolves the shell mode for the login page. The user is not known yet, so the last mode
    * remembered in {@link #COOKIE} is used, otherwise the organization default.
    *
    * @param request            the login request.
    * @param orgId              the organization the login page is served for, or {@code null} to
    *                           use the site value.
    * @param customThemeApplied {@code true} when the page links a custom theme style sheet.
    *
    * @return {@code true} if the login page is drawn dark.
    */
   public static boolean isLoginDark(HttpServletRequest request, String orgId,
                                     boolean customThemeApplied)
   {
      if(customThemeApplied) {
         return false;
      }

      Boolean remembered = readCookie(request);
      return remembered != null ? remembered : isDefaultDark(orgId);
   }

   /**
    * Gets the default for an explicit organization, falling back to the site value. Used where no
    * principal is on the thread, so the organization-scoped lookup cannot find the organization.
    */
   static boolean isDefaultDark(String orgId) {
      if(orgId != null) {
         String orgValue = SreeEnv.getProperty(
            PropertiesEngine.getOrgPropertyPrefix(orgId) + PROPERTY, false, false);

         if(orgValue != null) {
            return "true".equals(orgValue.trim());
         }
      }

      String siteValue = SreeEnv.getProperty(PROPERTY, false, false);
      return siteValue != null && "true".equals(siteValue.trim());
   }

   static String getUserPreference(Principal principal) {
      if(principal == null) {
         return null;
      }

      Object value = UserEnv.getProperty(principal, USER_PREFERENCE);
      return value == null ? null : value.toString();
   }

   /**
    * Reads the remembered mode.
    *
    * @return {@code true} or {@code false}, or {@code null} if there is no usable cookie.
    */
   static Boolean readCookie(HttpServletRequest request) {
      Cookie[] cookies = request.getCookies();

      if(cookies == null) {
         return null;
      }

      for(Cookie cookie : cookies) {
         if(COOKIE.equals(cookie.getName())) {
            if("1".equals(cookie.getValue())) {
               return true;
            }

            if("0".equals(cookie.getValue())) {
               return false;
            }
         }
      }

      return null;
   }

   /**
    * Gets the cookie to send with a page response so the login page can follow the resolved mode.
    * Nothing is sent while the shell is light and the browser holds no cookie, so a server that
    * never turns shell dark on never writes one.
    *
    * @param request the page request.
    * @param dark    the resolved mode for this page.
    *
    * @return the cookie to add to the response, or {@code null} if none is needed.
    */
   public static Cookie getCookieUpdate(HttpServletRequest request, boolean dark) {
      Boolean remembered = readCookie(request);

      if(remembered == null && !dark) {
         return null;
      }

      if(remembered != null && remembered == dark) {
         return null;
      }

      Cookie cookie = new Cookie(COOKIE, dark ? "1" : "0");
      String contextPath = request.getContextPath();
      cookie.setPath(contextPath == null || contextPath.isEmpty() ? "/" : contextPath);
      cookie.setHttpOnly(true);
      // a light mode is sent as an expiring cookie, which removes it from the browser
      cookie.setMaxAge(dark ? COOKIE_MAX_AGE : 0);

      String sameSite = SreeEnv.getProperty("same.site", "Lax");
      cookie.setAttribute("SameSite", sameSite);

      if("none".equalsIgnoreCase(sameSite) || request.isSecure() ||
         SreeEnv.getBooleanProperty("secure.cookie"))
      {
         cookie.setSecure(true);
      }

      return cookie;
   }
}
