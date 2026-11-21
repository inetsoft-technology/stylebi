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
package inetsoft.uql.rest.datasource;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Gives a data source placeholder values for its string properties, by reflection. A setter that
 * throws is not ignored: it is returned, so the test can compare it with the setters that are
 * known not to be settable and notice a data source whose setters started to fail.
 */
final class DataSourceFiller {
   private static final Set<String> SKIP = Set.of(
      "setName", "setURL", "setUrl", "setTokenUri", "setAuthorizationUri", "setScope",
      "setDescription", "setFolder", "setBody", "setContentType", "setOauthFlags", "setAuthType",
      "setAuthMethod", "setAuthURL", "setTokenPattern", "setServicePrincipalName", "setFullName",
      "setAudience", "setClientAuthMethod", "setCredentialId", "setLocale", "setTimeout");

   private DataSourceFiller() {
   }

   /**
    * @param extraSkip setters, besides the common ones, that the caller leaves alone
    * @return the setters that threw, as {@code Class.setter}, so none is skipped unnoticed.
    */
   static Set<String> fill(Object ds, Set<String> extraSkip) {
      Set<String> failed = new TreeSet<>();

      if(ds == null) {
         return failed;
      }

      for(Method m : ds.getClass().getMethods()) {
         if(m.getName().startsWith("set") && m.getParameterCount() == 1 &&
            m.getParameterTypes()[0] == String.class && !SKIP.contains(m.getName()) &&
            !extraSkip.contains(m.getName()))
         {
            String n = m.getName().toLowerCase();
            String value = n.contains("token") || n.contains("key") || n.contains("secret") ||
               n.contains("password") ? "tok" : "acct1";

            try {
               m.invoke(ds, value);
            }
            catch(Throwable e) {
               failed.add(ds.getClass().getSimpleName() + "." + m.getName());
            }
         }
      }

      return failed;
   }

   /**
    * Fills in the URL when the user is meant to give part of it: the whole URL (Jive) or the
    * account name in [brackets].
    */
   static void fillUrl(Object ds) {
      try {
         String url = (String) ds.getClass().getMethod("getURL").invoke(ds);

         if(url == null || url.isEmpty()) {
            ds.getClass().getMethod("setURL", String.class).invoke(ds, "https://acct1.example.com");
         }
         else if(url.contains("[")) {
            ds.getClass().getMethod("setURL", String.class)
               .invoke(ds, url.replaceAll("\\[[^\\]]*\\]", "acct1"));
         }
      }
      catch(Throwable e) {
         // no URL to complete
      }
   }
}
