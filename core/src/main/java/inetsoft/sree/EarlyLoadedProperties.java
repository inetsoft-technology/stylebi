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

   /**
    * Creates a new instance built from the system properties, the {@code INETSOFT_*} environment
    * variables and the built-in defaults only, without installing it. {@link PropertiesEngine}
    * loads the key-value storage contents into it during a reload while the current instance
    * stays installed, and installs it with {@link #restore(EarlyLoadedProperties)} once it is
    * complete.
    */
   static EarlyLoadedProperties create() {
      return new EarlyLoadedProperties();
   }

   /**
    * Discards the current instance so that the next {@link #getInstance()} call rebuilds it from
    * the system properties, the {@code INETSOFT_*} environment variables and the built-in
    * defaults only. {@link PropertiesEngine} loads the key-value storage contents into this
    * instance, so {@link PropertiesEngine#clear()} resets it; otherwise keys deleted from the
    * storage would never be removed from memory. A reload does not reset it, but builds a new
    * instance with {@link #create()} (Bug #77142).
    */
   static void reset() {
      ConfigurationContext.getContext().remove(EarlyLoadedProperties.class.getName());
   }

   /**
    * Installs an instance, one discarded by {@link #reset()} or one created by {@link #create()}.
    */
   static void restore(EarlyLoadedProperties instance) {
      ConfigurationContext.getContext().put(EarlyLoadedProperties.class.getName(), instance);
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
         base.putAll(mapEnvironment(System.getenv(), defaultProperties));
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

   /**
    * Gets the value of a property supplied by an {@code INETSOFT_*} environment variable, mapped
    * to a property name the same way the early-loaded properties are built. Unlike
    * {@link #getProperty(String)}, this never returns a value loaded from the key-value storage.
    * The environment is read once, since it cannot change while the JVM runs (Bug #77323).
    *
    * @param name the property name, with the case rules of {@link PropertiesEngine} applied.
    *
    * @return the property value, or {@code null} if no environment variable supplies it.
    */
   public static String getEnvironmentProperty(String name) {
      return name == null ? null : EnvironmentHolder.PROPERTIES.get(name);
   }

   /**
    * Maps the {@code INETSOFT_*} environment variables to property names: the name is
    * lowercased, the {@code inetsoft_} prefix is removed, underscores become dots, and the name
    * of a matching built-in default is used. The master password, master salt and admin password
    * variables are skipped.
    *
    * @param env               the environment variables.
    * @param defaultProperties the built-in default properties.
    *
    * @return the property values keyed by property name.
    */
   static Map<String, String> mapEnvironment(Map<String, String> env,
                                             Properties defaultProperties)
   {
      Map<String, String> defaults = new HashMap<>();

      for(String key : defaultProperties.stringPropertyNames()) {
         defaults.put(key.toLowerCase(), key);
      }

      Map<String, String> properties = new LinkedHashMap<>();

      for(Map.Entry<String, String> e : env.entrySet()) {
         String key = e.getKey().toLowerCase();

         if(key.startsWith("inetsoft_") &&
            !key.equals("inetsoft_master_password") &&
            !key.equals("inetsoft_master_salt") &&
            !key.equals("inetsoft_admin_password"))
         {
            String name = key.substring(9).replace('_', '.');
            name = defaults.getOrDefault(name, name);
            properties.put(name, e.getValue());
         }
      }

      return properties;
   }

   private static final class EnvironmentHolder {
      private static final Map<String, String> PROPERTIES = loadEnvironment();

      private static Map<String, String> loadEnvironment() {
         try {
            return Map.copyOf(mapEnvironment(System.getenv(), loadDefaults()));
         }
         catch(Exception ignore) {
            return Map.of();
         }
      }
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
