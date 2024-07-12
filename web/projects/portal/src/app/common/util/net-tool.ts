/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * Common network utility methods
 */
export namespace NetTool {
   export const COOKIE_NAME = "XSRF-TOKEN";
   export const PARAMETER_NAME = "_csrf";

   export function parseCookieValue(cookies: string, name: string): string {
      return cookies.split(";")
         .map(v => v.trim())
         .map(v => {
            const eq = v.indexOf("=");
            return [v.substring(0, eq), v.substring(eq + 1)];
         })
         .filter(pair => pair[0] == name)
         .map(pair => pair[1])[0];
   }

   export function tokenValue(): string {
      const cookies = document && document.cookie || "";

      return NetTool.parseCookieValue(cookies, COOKIE_NAME);
   }

   export function xsrfToken(): string {
      return `${PARAMETER_NAME}=${tokenValue()}`;
   }
}
