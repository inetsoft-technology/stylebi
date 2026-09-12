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
package inetsoft.sree;

import inetsoft.util.ConfigurationContext;
import inetsoft.util.DefaultProperties;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Provides properties that are available before the Spring context and key-value storage are
 * initialized: system properties, {@code INETSOFT_*} environment variables, and the built-in
 * {@code defaults.properties} classpath resource.
 *
 * <p>This class is intentionally free of Spring dependencies so that it can be used during
 * cluster initialization (before {@link PropertiesEngine} is fully wired). It is a static
 * singleton initialized on first access.</p>
 */
public class EarlyLoadedProperties {
   private final Properties properties;

   private EarlyLoadedProperties() {
      this.properties = build();
   }

   public static EarlyLoadedProperties getInstance() {
      return ConfigurationContext.getContext().computeIfAbsent(
         EarlyLoadedProperties.class.getName(), k -> new EarlyLoadedProperties());
   }

   public String getProperty(String name) {
      return properties.getProperty(name);
   }

   public String getProperty(String name, String def) {
      return properties.getProperty(name, def);
   }

   /**
    * Returns the underlying {@code Properties} object for use by {@link PropertiesEngine} as
    * the early-loaded properties base.
    */
   Properties asProperties() {
      return properties;
   }

   private static Properties build() {
      Properties defaultProperties = loadDefaults();
      Properties noSystemProperties = new Properties();
      Properties base = noSystemProperties;

      try {
         base = new DefaultProperties(noSystemProperties, System.getProperties());
      }
      catch(Exception ignore) {
      }

      try {
         Map<String, String> defaults = new HashMap<>();

         for(String key : defaultProperties.stringPropertyNames()) {
            defaults.put(key.toLowerCase(), key);
         }

         for(Map.Entry<String, String> e : System.getenv().entrySet()) {
            String key = e.getKey().toLowerCase();

            if(key.startsWith("inetsoft_") &&
               !key.equals("inetsoft_master_password") &&
               !key.equals("inetsoft_master_salt") &&
               !key.equals("inetsoft_admin_password"))
            {
               String name = key.substring(9).replace('_', '.');
               name = defaults.getOrDefault(name, name);
               base.setProperty(name, e.getValue());
            }
         }
      }
      catch(Exception ignore) {
      }

      try {
         if(base.getProperty("StyleReport.locale.resource") == null) {
            base.put("StyleReport.locale.resource", "inetsoft/util/srinter");
         }

         if(base.getProperty("sree.bundle") == null) {
            base.put("sree.bundle", "SreeBundle");
         }
      }
      catch(Exception ignore) {
      }

      return new DefaultProperties(base, defaultProperties);
   }

   private static Properties loadDefaults() {
      Properties prop = new Properties();

      try(InputStream in = EarlyLoadedProperties.class.getResourceAsStream(
         "/inetsoft/report/defaults.properties"))
      {
         if(in != null) {
            prop.load(in);
         }
      }
      catch(IOException ignore) {
      }

      return prop;
   }
}
