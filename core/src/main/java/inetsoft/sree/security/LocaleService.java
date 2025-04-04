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
package inetsoft.sree.security;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.UserEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.util.Catalog;
import inetsoft.util.SingletonManager;

import java.security.Principal;
import java.util.*;

/**
 * Class that supports user locale registration.
 */
public class LocaleService {

   /**
    * Creates a new instance of {@code LocaleService}.
    */
   public LocaleService() {
   }

   /**
    * Gets the shared instance of {@code LocaleService}.
    *
    * @return the shared service.
    */
   public static LocaleService getInstance() {
      return SingletonManager.getInstance(LocaleService.class);
   }

   /**
    * Gets the locale for the user.
    *
    * @param localeName the name of the locale.
    * @param principal  a principal object identifying the user.
    *
    * @return the locale.
    */
   public String getLocale(String localeName, Principal principal) {
      return getLocale(localeName, IdentityID.getIdentityIDFromKey(principal.getName()), principal);
   }

   /**
    * Gets the locale for the user.
    *
    * @param localeName the name of the locale.
    * @param userId     the user name.
    * @param principal  a principal object identifying the user.
    *
    * @return the locale.
    */
   public String getLocale(String localeName, IdentityID userId, Principal principal) {
      return getLocale(localeName, null, userId, principal);
   }

   /**
    * Gets the locale for the user.
    *
    * @param localeName the name of the locale.
    * @param clientLocale user client locale
    * @param userId     the user name.
    * @param principal  a principal object identifying the user.
    *
    * @return the locale.
    */
   public String getLocale(String localeName, String clientLocale, IdentityID userId,
                           Principal principal)
   {
      initLocale();
      String locale = null;

      if(localeName != null && !localeName.equals(SUtil.MY_LOCALE)) {
         locale = localeMap.get(localeName);
      }
      else if(SUtil.MY_LOCALE.equals(localeName)) {
         SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
         User user = provider.getUser(userId);

         if(user != null) {
            locale = user.getLocale();
            String defaultLocale = Catalog.getCatalog().getString("Default");

            //overwrite default user locale with set organization locale
            if(locale == null || "".equals(locale) || defaultLocale.equals(locale)) {
              Organization userOrg = provider.getOrganization(user.getOrganizationID());

              if(userOrg != null && userOrg.getLocale() != null && !"".equals(userOrg.getLocale())) {
                 locale = userOrg.getLocale();
              }
            }

            if((locale == null || locale.isEmpty()) && clientLocale != null) {
               locale = clientLocale;
            }
         }

         if(locale == null) {
            locale = (String) UserEnv.getProperty(principal, "locale");
         }
      }

      return locale;
   }

   /**
    * Initialize locale.
    */
   private void initLocale() {
      String available = SreeEnv.getProperty("locale.available");

      if(available != null && !available.equals(localeAvailable) &&
         available.length() > 0)
      {
         String[] list = available.split(":");
         int localeCount = list.length + 1;
         String[] locales = new String[localeCount];
         String[] localeValues = new String[localeCount];
         locales[0] = SUtil.MY_LOCALE;
         localeValues[0] = locales[0];

         for(int j = 1; j < localeCount; j++) {
            String localeName = list[j - 1];

            if(localeName.length() < 2) {
               continue;
            }

            Locale locale =
               new Locale(localeName.substring(0, 2), localeName.substring(3));
            locales[j] = locale.getDisplayName();
            localeValues[j] = localeName;
            localeMap.put(localeValues[j], locale.toString());
         }

         localeAvailable = available;
      }
   }

   private String localeAvailable = null;
   private final Map<String, String> localeMap = new HashMap<>();
}
