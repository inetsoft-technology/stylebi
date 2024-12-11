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

import com.fasterxml.jackson.databind.JsonNode;
import inetsoft.report.StyleFont;
import inetsoft.util.*;
import inetsoft.util.log.LogContext;
import inetsoft.util.log.LogLevel;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * StyleReport/EE environment properties setting. It loads the sree.properties
 * file and inherits all properties from the system properties. If an
 * application needs to modify properties used in SREE, it should change
 * the property through this class. Some of the property is used at replet
 * engine initialization time. Subsequent changes to the property may
 * not affect the report generation.
 *
 * @version 3.0, 5/10/2000
 * @author InetSoft Technology Corp
 */
@SuppressWarnings("deprecation")
public class SreeEnv {

   public static String getEarlyLoadedProperty(String name) {
      return PropertiesEngine.getInstance().getProperty(name, true);
   }

   public static String getEarlyLoadedProperty(String name, String def) {
      return PropertiesEngine.getInstance().getProperty(name, def, true);
   }

   public static String getProperty(String name) {
      return PropertiesEngine.getInstance().getProperty(name, false);
   }

   public static String getProperty(String name, boolean earlyLoaded, boolean orgScope) {
      return PropertiesEngine.getInstance().getProperty(name, earlyLoaded, orgScope);
   }

   public static String getProperty(String name, String def) {
      return PropertiesEngine.getInstance().getProperty(name, def, false);
   }

   public static String getProperty(String name, boolean earlyLoaded, String def) {
      return PropertiesEngine.getInstance().getProperty(name, def, earlyLoaded);
   }

   public static String computePropertyIfAbsent(String name, Supplier<String> fn) {
      return PropertiesEngine.getInstance().getProperty(name, fn, false);
   }

   public static boolean getBooleanProperty(String name, String ... trueValues) {
      String property = getProperty(name);

      if(property != null) {
         if(trueValues.length == 0) {
            return "true".equals(property);
         }

         for(String trueValue : trueValues) {
            if(trueValue.equals(property)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Get a property as an integer.
    */
   public static Integer getInt(String name) {
      String str = getProperty(name);
      Integer val = null;

      if(str != null) {
         val = (Integer) cache.computeIfAbsent(name, key -> Integer.valueOf(str));
      }

      return val;
   }

   /**
    * Get a property as an integer.
    */
   public static Long getLong(String name) {
      String str = getProperty(name);
      Long val = null;

      if(str != null) {
         val = (Long) cache.computeIfAbsent(name, key -> Long.valueOf(str));
      }

      return val;
   }

   /**
    * Get a property as a boolean value.
    */
   public static Boolean getBoolean(String name) {
      String str = getProperty(name);
      Boolean val = null;

      if(str != null) {
         val = (Boolean) cache.computeIfAbsent(name, key -> Boolean.valueOf(str));
      }

      return val;
   }

   /**
    * Get a property as an insets. The property must be comma separated
    * numbers (4).
    */
   public static Insets getInsets(String name) {
      return PropertiesEngine.getInstance().getInsets(name);
   }

   /**
    * Get a property as a font. The property must be a valid font string
    * created by StyleFont.toString().
    */
   public static Font getFont(String name) {
      String str = getProperty(name);
      Font font = null;

      if(str != null) {
         font = fontMap.computeIfAbsent(name, key -> StyleFont.decode(str));
      }

      return font;
   }

   /**
    * Get a property value as a file path. If the path is not absolute
    * a sree.home is defined, the sree.home is prepended to the file name.
    * @param name property name.
    * @param def default value if the property is null.
    */
   public static String getPath(String name, String def) {
      return PropertiesEngine.getInstance().getPath(name, def);
   }

   /**
    * Get physical file path.
    * @param path the specified logical file path.
    * @return physical file path.
    */
   public static String getPath(String path) {
      return PropertiesEngine.getInstance().getPath(path);
   }

   /**
    * Set the value of a password property.
    *
    * @param name the name of the property.
    * @param value the value of the property.
    */
   public static void setPassword(String name, String value) {
      if(Tool.isEmptyString(value)) {
         return;
      }

      if(!Tool.isCloudSecrets()) {
         value = Tool.encryptPassword(value);
      }

      PropertiesEngine.getInstance().setProperty(name, value);
   }

   /**
    * Get the value of a password property.
    *
    * @param name the name of the property.
    *
    * @return the value of the property.
    */
   public static String getPassword(String name) {
      String encryptedPassword = getProperty(name);

      if(Tool.isEmptyString(encryptedPassword)) {
         return encryptedPassword;
      }

      if(Tool.isCloudSecrets()) {
         JsonNode jsonNode = Tool.loadCredentials(encryptedPassword, false);
         String secretKey = "password";

         if("openid.client.id".equals(name) || "styleBI.google.openid.client.id".equals(name)) {
            secretKey = "client_id";
         }
         else if("openid.client.secret".equals(name) || "styleBI.google.openid.client.secret".equals(name)) {
            secretKey = "client_secret";
         }

         if(jsonNode != null && jsonNode.has(secretKey)) {
            return jsonNode.get(secretKey).asText();
         }

         return null;
      }
      else {
         return Tool.decryptPassword(encryptedPassword);
      }
   }

   /**
    * Sets the level for a log context.
    *
    * @param context the type of log context.
    * @param name    the name of the log context.
    * @param level   the new level.
    */
   public static void setLogLevel(LogContext context, String name, LogLevel level) {
      PropertiesEngine.getInstance().setLogLevel(context, name, level);
   }

   /**
    * Set the value of a property.
    */
   public static void setProperty(String name, String val) {
      PropertiesEngine.getInstance().setProperty(name, val);
   }

   /**
    * Set the value of a property.
    */
   public static void setProperty(String name, String val, boolean orgScope) {
      PropertiesEngine.getInstance().setProperty(name, val, orgScope);
   }

   /**
    * Remove the named property.
    */
   public static void remove(String name) {
     PropertiesEngine.getInstance().remove(name);
   }

   /**
    * Remove the named property.
    */
   public static void remove(String name, boolean orgScope) {
      PropertiesEngine.getInstance().remove(name, orgScope);
   }

   public static void resetProperty(String name, boolean orgScope) {
      if(orgScope) {
         remove(name, true);
      }
      else {
         String defaultValue = getDefaultProperties().getProperty(name);
         setProperty(name, defaultValue, false);
      }
   }

   /**
    * Get all properties.
    */
   public static Properties getProperties() {
      return PropertiesEngine.getInstance().getProperties();
   }

   /**
    * Clear and reload the properties.
    */
   public static void clear() {
      PropertiesEngine.getInstance().clear();
   }

   /**
    * Initialize the environment.
    */
   public static void init() {
      PropertiesEngine.getInstance().init(false);
   }

   public static void reloadLoggingFramework() {
      PropertiesEngine.getInstance().reloadLoggingFramework();
   }

   /**
    * Saves the in-memory properties to the property file
    * specified by the argument.
    */
   public static void save() throws IOException {
      PropertiesEngine.getInstance().save();
   }

   public static Properties getDefaultProperties() {
      return PropertiesEngine.getInstance().getDefaultProperties();
   }

   public static boolean isInitialized() {
      try {
         return PropertiesEngine.getInstance().getProperties() != null;
      }
      catch(SingletonManager.ResurrectException ex) {
         //fix bug#43719 when resurrectException is caught, the isInitialized should be false.
         return false;
      }
   }

   private static final Map<String, Object> cache = new ConcurrentHashMap<>(); // cached objects
   private static final Map<String, Font> fontMap = new ConcurrentHashMap<>();

   /**
    * A value that is cached for the timeout period.
    */
   public static class Value {
      public Value(String name, int timeout, String def) {
         this(name, timeout);
         this.def = def;
      }

      public Value(String name, int timeout) {
         this.name = name;
         this.timeout = timeout;
      }

      public String get() {
         long now = System.currentTimeMillis();

         if(now - ts > timeout) {
            updateValue();
         }

         return value;
      }

      /**
       * Imperatively update the underlying property value without regard to the timeout.
       */
      public void updateValue() {
         value = def != null ? SreeEnv.getProperty(name, def) : SreeEnv.getProperty(name);
         ts = System.currentTimeMillis();
      }

      private final int timeout;
      private final String name;
      private String value;
      private String def;
      private long ts;
   }
}
