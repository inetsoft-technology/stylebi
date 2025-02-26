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

import inetsoft.report.StyleFont;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.KeyValueStorage;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.util.*;
import inetsoft.util.log.*;
import inetsoft.util.log.logback.LogbackUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class PropertiesEngine {
   /**
    * Gets the singleton instance of {@code PropertiesEngine}.
    *
    * @return the PropertiesEngine instance.
    */
   public static PropertiesEngine getInstance() {
      return SingletonManager.getInstance(PropertiesEngine.class);
   }

   /**
    * Get all properties.
    */
   public Properties getProperties() {
      return getUserEnhancedProperties();
   }

   public Properties getInternalProperties() {
      return ConfigurationContext.getContext().get(PROPERTIES_KEY);
   }

   public String getProperty(String name) {
      return getProperty(name, false);
   }

   public String getProperty(String name, boolean earlyLoaded) {
      return getProperty(name, earlyLoaded, true);
   }

   public String getProperty(String name, boolean earlyLoaded, boolean orgScope) {
      name = orgScope ? fixPropertyName(name, earlyLoaded) : fixPropertyNameCase(name);
      Properties prop = earlyLoaded ? getEarlyLoadedProperties() : getUserEnhancedProperties();
      String val = prop.getProperty(name);

      // handle $(name)
      if(val != null) {
         val = substitute(val, prop);
      }

      return val;
   }

   public String getProperty(String name, Supplier<String> fn) {
      return getProperty(name, fn, false);
   }

   /**
    * Gets the value of a property.
    *
    * @param name the property name.
    * @param fn   a function that supplies the default value of the property.
    *
    * @return the property value.
    */
   public String getProperty(String name, Supplier<String> fn, boolean earlyLoaded) {
      name = fixPropertyName(name, earlyLoaded);
      Properties prop = earlyLoaded ? getEarlyLoadedProperties() : getUserEnhancedProperties();
      String val;

      if(prop.containsKey(name)) {
         val = prop.getProperty(name);
      }
      else {
         val = fn.get();
      }

      if(val != null) {
         val = substitute(val, prop);
      }

      return val;
   }

   public String getProperty(String name, String def) {
      return getProperty(name, def, false);
   }

   /**
    * Get the value of a property.
    * @param name property name.
    * @param def default value if the property is null.
    */
   public String getProperty(String name, String def, boolean earlyLoaded) {
      name = fixPropertyName(name, earlyLoaded);
      Properties prop = earlyLoaded ? getEarlyLoadedProperties() : getUserEnhancedProperties();
      String val = prop.getProperty(name, def);

      // handle $(name)
      if(val != null) {
         val = substitute(val, prop);
      }
      else {
         val = def;
      }

      return val;
   }

   /**
    * Get a property as a font. The property must be a valid font string
    * created by StyleFont.toString().
    */
   public Font getFont(String name) {
      String str = getProperty(name);
      Font font = null;

      if(str != null) {
         font = fontMap.computeIfAbsent(name, key -> StyleFont.decode(str));
      }

      return font;
   }

   /**
    * Remove the named property.
    */
   public void remove(String name) {
      init();
      name = fixPropertyNameCase(name);

      synchronized(changedProps) {
         getInternalProperties().remove(name);
         changedProps.add(name);
      }
   }

   /**
    * Remove the named property.
    */
   public void remove(String name, boolean orgScope) {
      if(orgScope) {
         XPrincipal principal = (XPrincipal) ThreadContext.getPrincipal();
         principal = principal == null ?
            (XPrincipal) ThreadContext.getContextPrincipal() : principal;
         String orgID;

         if(principal == null) {
            orgID = null;
         }
         else {
            orgID = OrganizationManager.getInstance().getCurrentOrgID();
         }

         name = orgID == null ? name : "inetsoft.org." + orgID + "." + fixPropertyNameCase(name);
      }

      remove(name);
   }

   /**
    * Set the value of a property.
    */
   public void setProperty(String name, String val) {
      init();
      Properties prop = getInternalProperties();
      name = fixPropertyNameCase(name);

      if(val == null) {
         remove(name);
      }
      else {
         if(!(val.startsWith("$(sree.home)")) && !name.equals("sree.home")) {
            String home = getProperty("sree.home");
            String valU = val.toUpperCase();
            String homeU = home == null || ".".equals(home) ? null : home.toUpperCase();

            if(homeU != null && valU.contains(homeU)) {
               try {
                  FileSystemService fileSystemService = FileSystemService.getInstance();

                  home = (fileSystemService.getFile(home)).getCanonicalPath();
                  val = (fileSystemService.getFile(val)).getCanonicalPath();
               }
               catch(Exception ignore) {
               }

               if(val.startsWith(home)) {
                  val = "$(sree.home)" + val.substring(home.length());
               }
            }
         }

         // if value is the same then don't set it as changed and just return
         if(Tool.equals(prop.getProperty(name), val)) {
            return;
         }

         synchronized(changedProps) {
            prop.put(name, val);
            changedProps.add(name);
         }

         applyProperty(name);
      }
   }

   /**
    * To apply the property.
    *
    * @param name property name.
    */
   private void applyProperty(String name) {
      applyLogProperty(name);
      applySqlHelperProperty(name);
   }

   private void applySqlHelperProperty(String name) {
      if("mysql.server.timezone".equals(name) || "mysql.local.timezone".equals(name)) {
         SQLHelper.resetCache();
      }
   }

   /**
    * Set the value of a property.
    */
   public void setProperty(String name, String val, boolean orgScope) {
      if(orgScope) {
         XPrincipal principal = (XPrincipal) ThreadContext.getPrincipal();
         principal = principal == null ? (XPrincipal) ThreadContext.getContextPrincipal() : principal;
         String orgID;

         if(principal == null) {
            orgID = null;
         }
         else {
            orgID = OrganizationManager.getInstance().getCurrentOrgID();
         }

         name = orgID == null ? name : "inetsoft.org." + orgID + "." + fixPropertyNameCase(name);
      }

      setProperty(name, val);
   }

   /**
    * Sets the level for a log context.
    *
    * @param context the type of log context.
    * @param name    the name of the log context.
    * @param level   the new level.
    */
   public void setLogLevel(LogContext context, String name, LogLevel level) {
      String property;

      if(context == LogContext.CATEGORY) {
         LogManager.getInstance().setLevel(name, level);
         property = "log.level." + name;
      }
      else {
         LogManager.getInstance().setContextLevel(context, name, level);
         property = "log." + context.name() + ".level." + name;
      }

      if(level == null) {
         setProperty(property, null);
      }
      else {
         setProperty(property, level.level());
      }
   }

   private String fixPropertyName(String name, boolean earlyLoaded) {
      String lcase = fixPropertyNameCase(name);
      return earlyLoaded ? lcase : useAvailableOrgProperty(lcase);
   }

   private String fixPropertyNameCase(String name) {
      if(name != null && !name.startsWith("log.level.") &&
         !name.startsWith("plugin.extra.classpath.") &&
         !name.matches("^log\\.[A-Z_]+\\.level\\..+$") &&
         !name.matches("^inetsoft\\.uql\\.jdbc\\.pool\\..+\\.connectionTestQuery$"))
      {
         return name.toLowerCase();
      }

      if(name != null && name.startsWith("inetsoft.org.")) {
         int index = name.substring(13).indexOf('.');

         if(index >= 0) {
            return name.substring(0, 14 + index) + name.substring(14 + index).toLowerCase();
         }
      }

      return name;
   }

   private String useAvailableOrgProperty(String propertyName) {
      XPrincipal principal = (XPrincipal) ThreadContext.getPrincipal();
      principal = principal == null ? (XPrincipal) ThreadContext.getContextPrincipal() : principal;
      String orgID;
      if(principal == null) {
         orgID = null;
      }
      else {
         orgID = OrganizationManager.getInstance().getCurrentOrgID().toLowerCase();
      }

      List<String> excludedProps = Arrays.asList(
         "security.enabled", "sree.security.listeners", "security.cache", "security.cache.interval");

      if(orgID != null && !excludedProps.contains(propertyName) &&
         !"inetsoft.sree.security.CheckPermissionStrategy".equals(propertyName))
      {
         init();
         Properties prop = getInternalProperties();
         String orgPropertyName = "inetsoft.org." + orgID + "." + propertyName;

         if(prop.containsKey(orgPropertyName)) {
            return orgPropertyName;
         }
      }

      return propertyName;
   }

   /**
    * Get the properties which inited before loading storage, if a property is getted before
    * loading storage then must get the property by getEarlyLoadedProperty function which will
    * get the property from earlyLoadedProperties with the property name not added(or try to added)
    * organizationID.
    *
    * It should be emphasized that the earlyLoadedProperties finally become same with
    * UserEnhancedProperties after initing userEnhancedProperties.
    */
   private Properties getEarlyLoadedProperties() {
      initEarlyLoadedProperties();
      return ConfigurationContext.getContext().get(EARLY_LOADED_PROPERTIES_KEY);
   }

   /**
    * Get properties which loaded base on earlyLoadedProperties, and added some other properties
    * like properties from user storage, which should be getted with organizationID.
    */
   private Properties getUserEnhancedProperties() {
      init();
      return ConfigurationContext.getContext().get(PROPERTIES_KEY);
   }

   /**
    * Initialize the environment.
    */
   public void init() {
      init(false);
   }

   public void init(boolean fromChange) {
      if(!fromChange && getInternalProperties() != null) {
         return;
      }

      getStorage(); // make sure it's initialized outside of lock
      PROPERTIES_LOCK.lock();

      try {
         if(fromChange) {
            String home = getProperty("sree.home");
            clear();
            setProperty("sree.home", home);
         }

         // @by davidd, Recheck once lock acquired to prevent reinitialization.
         if(getInternalProperties() != null) {
            return;
         }

         Properties prop = getEarlyLoadedProperties();

         if(prop instanceof DefaultProperties) {
            prop = ((DefaultProperties) prop).getMainProperties();
         }
         else {
            prop = new LayerProperties();
         }

         String home = ConfigurationContext.getContext().getHome();
         String path = home + "/sree.properties";

         KeyValueStorage<String> storage = getStorage();
         storage.addListener(changeListener);
         loadFromStorage(prop, storage);

         // @by mikec, if sree.home was defined in sree.properties file
         // do not use the parent folder as sree.home
         // use the definition in sree.properties file instead
         if(prop.getProperty("sree.home") != null) {
            home = prop.getProperty("sree.home");
         }

         prop.setProperty("sree.home", home);
         prop.setProperty("sree.properties", path);

         DefaultProperties topProp = new DefaultProperties(prop, getDefaultProperties());
         ConfigurationContext.getContext().put(PROPERTIES_KEY, topProp);
      }
      catch(Exception ex) {
         LOG.error("Failed to initialize SreeEnv: " + ex, ex);
         Properties prop = getDefaultProperties();
         DefaultProperties topProp = new DefaultProperties(prop, prop);
         ConfigurationContext.getContext().put(PROPERTIES_KEY, topProp);
      }
      finally {
         PROPERTIES_LOCK.unlock();
      }

      initLogging();
      initFonts();
      LOG.info("InetSoft {} build {} started", Tool.getReportVersion(), Tool.getBuildNumber());
   }

   private KeyValueStorage<String> getStorage() {
      KeyValueStorage<String> storage = ConfigurationContext.getContext().get(STORAGE_KEY);

      // init before lock to prevent deadlock
      if(storage == null) {
         STORAGE_LOCK.lock();

         try {
            storage = ConfigurationContext.getContext().get(STORAGE_KEY);

            if(storage == null) {
               storage = SingletonManager.getInstance(KeyValueStorage.class, "sreeProperties");
               ConfigurationContext.getContext().put(STORAGE_KEY, storage);
            }
         }
         finally {
            STORAGE_LOCK.unlock();
         }
      }

      return storage;
   }

   private void initEarlyLoadedProperties() {
      if(ConfigurationContext.getContext().get(EARLY_LOADED_PROPERTIES_KEY) != null) {
         return;
      }

      EARLY_LOADED_PROPERTIES_LOCK.lock();

      try {
         if(ConfigurationContext.getContext().get(EARLY_LOADED_PROPERTIES_KEY) != null) {
            return;
         }

         Properties defaultProperties = null;
         Properties noSystemProperties = new Properties();
         Properties base = noSystemProperties;

         try {
            // use the system property as base for all properties
            // the logic was here before uses the defkey's property as base
            // which is not correct because those are not logically connected
            Properties systemProperties = System.getProperties();
            base = new DefaultProperties(noSystemProperties, systemProperties);
         }
         catch(Exception ignore) {
         }

         try {
            defaultProperties = getDefaultProperties();
            Map<String, String> defaults = new HashMap<>();

            for(String key : defaultProperties.stringPropertyNames()) {
               defaults.put(key.toLowerCase(), key);
            }

            for(Map.Entry<String, String> e : System.getenv().entrySet()) {
               String key = e.getKey().toLowerCase();

               if(key.toLowerCase().startsWith("inetsoft_") &&
                  !key.equals("inetsoft_master_password") && !key.equals("inetsoft_master_salt")) {
                  String name = key.toLowerCase().substring(9).replace('_', '.');
                  name = defaults.getOrDefault(name, name);
                  base.setProperty(name, e.getValue());
               }
            }
         }
         catch(Exception ignore) {
         }

         InputStream inp = null;
         String configHome = ConfigurationContext.getContext().getHome();

         try {
            inp = new FileInputStream(configHome + "/sree.properties");
         }
         catch(IOException ignore) {
         }
         catch(SecurityException se) {
            inp = PropertiesEngine.class.getResourceAsStream("/sree.properties");
         }

         try {
            if(inp != null) {
               base.load(inp);
               inp.close();
            }
         }
         catch(Exception e) {
            LOG.error("Failed to load sree.properties file", e);
         }

         // get resource bundles
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

         base = new DefaultProperties(base, defaultProperties);
         ConfigurationContext.getContext().put(EARLY_LOADED_PROPERTIES_KEY, base);
      }
      finally {
         EARLY_LOADED_PROPERTIES_LOCK.unlock();
      }
   }

   public Properties getDefaultProperties() {
      Properties prop = ConfigurationContext.getContext().get(DEFAULTS_PROPERTIES_KEY);

      if(prop != null) {
         return prop;
      }

      prop = new Properties();

      try(InputStream in = PropertiesEngine.class.getResourceAsStream(
         "/inetsoft/report/defaults.properties"))
      {
         prop.load(in);
      }
      catch(IOException exc) {
         LOG.error("Failed to load default properties", exc);
      }

      ConfigurationContext.getContext().put(DEFAULTS_PROPERTIES_KEY, prop);

      return prop;
   }

   /**
    * Clear and reload the properties.
    */
   public void clear() {
      PROPERTIES_LOCK.lock();

      try {
         // @by stephenwebster, For Bug #29148
         // Whenever SreeEnv is cleared, we must reset the log manager prior to a re-initialization
         // of SreeEnv.
         SingletonManager.reset(LogManager.class);
         ConfigurationContext.getContext().remove(PROPERTIES_KEY);
         ConfigurationContext.getContext().remove(DEFAULTS_PROPERTIES_KEY);
         ConfigurationContext.getContext().remove(EARLY_LOADED_PROPERTIES_KEY);
         KeyValueStorage<String> storage = ConfigurationContext.getContext().remove(STORAGE_KEY);

         if(storage != null) {
            try {
               storage.removeListener(changeListener);
            }
            catch(Exception e) {
               LOG.warn("Failed to close key-value storage", e);
            }
         }
      }
      finally {
         PROPERTIES_LOCK.unlock();
      }
   }

   private void loadFromStorage(Properties properties, KeyValueStorage<String> storage) {
      if(properties instanceof LayerProperties) {
         ((LayerProperties) properties).load(storage);
      }
      else {
         storage.stream().forEach(p -> properties.setProperty(p.getKey(), p.getValue()));
      }
   }

   private void saveToStorage(Properties properties, KeyValueStorage<String> storage,
                              Set<String> changedProps)
      throws ExecutionException, InterruptedException
   {
      if(properties instanceof DefaultProperties) {
         saveToStorage(((DefaultProperties) properties).getMainProperties(), storage, changedProps);
      }
      else {
         Set<String> propsToRemove = new TreeSet<>();
         SortedMap<String, String> propsToAdd = new TreeMap<>();

         for(String prop : changedProps) {
            if(properties.containsKey(prop)) {
               propsToAdd.put(prop, properties.getProperty(prop));
            }
            else {
               propsToRemove.add(prop);
            }
         }

         List<Future<?>> futures = new ArrayList<>();

         if(!propsToRemove.isEmpty()) {
            futures.add(storage.removeAll(propsToRemove));
         }

         if(!propsToAdd.isEmpty()) {
            futures.add(storage.putAll(propsToAdd));
         }

         for(Future<?> future : futures) {
            future.get();
         }
      }
   }

   /**
    * Initializes logging.
    */
   private void initLogging() {
      LogManager logManager = LogManager.getInstance();
      logManager.setLevel("inetsoft.scheduler_test", LogLevel.OFF);
      logManager.setLevel("inetsoft.mv_debug", LogLevel.OFF);
      logManager.setLevel("inetsoft.swap_data", LogLevel.OFF);
      logManager.setLevel(SUtil.MAC_LOG_NAME, LogLevel.OFF);
      logManager.setLevel(LogUtil.PERFORMANCE_LOGGER_NAME, LogLevel.OFF);

      if(LicenseManager.getInstance().isEnterprise()) {
         logManager.setLevel("inetsoft.storage.aws.com.amazonaws", LogLevel.WARN);
         logManager.setLevel("inetsoft.storage.aws.org.apache", LogLevel.WARN);
      }
      
      logManager.setLevel("inetsoft_audit", LogLevel.INFO);
      logManager.setLevel("org.apache.ignite", LogLevel.WARN);

      reloadLoggingFramework();

      Properties props = getInternalProperties();

      for(Enumeration<?> e = props.propertyNames(); e.hasMoreElements();) {
         applyLogProperty((String) e.nextElement());
      }

      System.out.println("Using built-in log configuration");
   }

   private void applyLogProperty(String prop) {
      if(Tool.isEmptyString(prop) ||
         !prop.startsWith("log.level.") && !prop.matches("^log\\.[A-Z_]+\\.level\\..+$") &&
         !prop.equals("log.detail.level"))
      {
         return;
      }

      applyLogProperty(prop, getProperty(prop));

      try {
         LogbackUtil.resetLog();
      }
      catch(Exception e) {
         LOG.error("Failed to reset Logback", e);
      }

      SreeEnv.reloadLoggingFramework();
   }

   private void applyLogProperty(String prop, String val) {
      LogManager logManager = LogManager.getInstance();

      if("log.detail.level".equals(prop)) {
         logManager.setLevel(logManager.parseLevel(val));
      }
      else if(prop.startsWith("log.level.")) {
         try {
            LogLevel level = logManager.parseLevel(val);
            prop = prop.substring(10);

            if(prop.isEmpty()) {
               throw new IllegalArgumentException("Empty logger name");
            }

            logManager.setLevel(prop, level);
         }
         catch(IllegalArgumentException exc) {
            // log is not initialized yet, use standard error
            System.err.println("Invalid log property: " + prop + "=" + getProperty(prop));
         }
      }
      else if(prop.matches("^log\\.[A-Z_]+\\.level\\..+$")) {
         try {
            LogContext context = LogContext.valueOf(
               prop.substring(4, prop.indexOf('.', 4)));
            String contextName = prop.substring(prop.indexOf('.', 4) + 7);
            LogLevel level = LogManager.getInstance().parseLevel(val);
            logManager.setContextLevel(context, contextName, level);
         }
         catch(IllegalArgumentException exc) {
            // log is not initialized yet, use standard error
            System.err.println("Invalid log context property: " + prop + "=" +
               getProperty(prop));
         }
      }
   }

   private boolean isScheduler() {
      return Boolean.parseBoolean(System.getProperty("ScheduleServer"));
   }

   public void reloadLoggingFramework() {
      String logFile;
      String prop;

      if(isScheduler()) {
         prop = getPath("schedule.log.file", "schedule.log");
         logFile = SUtil.verifyLog(prop, "schedule.log");
      }
      else {
         prop = getProperty("log.output.file");
         logFile = SUtil.verifyLog(prop, "sree.log");
      }

      String discriminator = getProperty("log.file.discriminator");
      boolean console = "true".equals(getProperty("log.output.stderr"));
      String performanceLevel = getProperty("log.level." + LogUtil.PERFORMANCE_LOGGER_NAME);
      long maxSize = Long.parseLong(getProperty("report.log.max"));
      int maxCount = Integer.parseInt(Objects.toString(getProperty("report.log.count"), "10"));
      boolean performance = performanceLevel != null &&
         !LogLevel.OFF.level().equalsIgnoreCase(performanceLevel) &&
         LogManager.getInstance().parseLevel(prop) != null;
      LogManager.getInstance().initialize(
         logFile, discriminator, console, maxSize, maxCount, performance);
   }

   /**
    * Get a property as an insets. The property must be comma separated
    * numbers (4).
    */
   public Insets getInsets(String name) {
      String str = getProperty(name);

      if(str == null) {
         return null;
      }

      return (Insets) cache.computeIfAbsent(name, key -> {
         String[] arr = Tool.split(str, ',');

         if(arr.length == 4) {
            return new Insets(
               Integer.parseInt(arr[0]), Integer.parseInt(arr[1]), Integer.parseInt(arr[2]),
               Integer.parseInt(arr[3]));
         }

         return null;
      });
   }

   /**
    * Get a property value as a file path. If the path is not absolute
    * a sree.home is defined, the sree.home is prepended to the file name.
    * @param name property name.
    * @param def default value if the property is null.
    */
   public String getPath(String name, String def) {
      String path = getProperty(name, def);
      return getPath(path);
   }

   /**
    * Get physical file path.
    * @param path the specified logical file path.
    * @return physical file path.
    */
   public String getPath(String path) {
      if(path == null || path.length() == 0) {
         return path;
      }

      FileSystemService fileSystemService = FileSystemService.getInstance();

      // if the file exists, don't check sree.home
      if(path.startsWith("$(sree.home)") || !(fileSystemService.getFile(path)).exists()) {
         String home = getProperty("sree.home");

         if(path.equals("$(sree.home)")) {
            if(home != null) {
               return home;
            }
            else {
               return ConfigurationContext.getContext().getHome();
            }
         }
         else if(path.startsWith("$(sree.home)")) {
            int start = 12; // $(sree.home) has 12 characters

            if(path.charAt(start) == '/' || path.charAt(start) == '\\') {
               start++;
            }

            path = path.substring(start);
         }

         // '/' can be used on win32 but it is not recognized as absolute
         if(home != null && !fileSystemService.getFile(path).isAbsolute() && path.length() != 0 &&
            path.charAt(0) != '/' && path.charAt(0) != '\\')
         {
            path = home + File.separator + path;
         }
      }

      String configHome = ConfigurationContext.getContext().getHome();

      // '/' can be used on win32 but it is not recognized as absolute
      if(!fileSystemService.getFile(path).exists() &&
         !fileSystemService.getFile(path).isAbsolute() && path.length() != 0 &&
         path.charAt(0) != '/' && path.charAt(0) != '\\' && !path.startsWith(configHome))
      {
         path = configHome + File.separator + path;
      }

      return path;
   }

   private void initFonts() {
      String prop = getProperty("font.truetype.path");

      if(prop != null) {
         String[] paths = prop.split(";", 0);

         for(String path : paths) {
            if(path != null && path.trim().length() > 0) {
               scanFonts(FileSystemService.getInstance().getFile(path.trim()));
            }
         }
      }
   }

   /**
    * Recursively scans a directory for true type fonts and registers them with
    * the graphics environment.
    *
    * @param dir the directory to scan.
    */
   private void scanFonts(File dir) {
      if(dir == null || !dir.isDirectory()) {
         return;
      }

      for(File file : dir.listFiles()) {
         if(file.isDirectory()) {
            scanFonts(file);
         }
         else {
            if(file.getName().toLowerCase().endsWith(".ttf")) {
               try {
                  Font font = Font.createFont(Font.TRUETYPE_FONT, file);
                  GraphicsEnvironment.getLocalGraphicsEnvironment()
                     .registerFont(font);
               }
               catch(Throwable exc) {
                  System.err.println("Failed to load font " + file + ": " + exc.toString());
               }
            }
         }
      }
   }

   /**
    * Substitute $(name) in the line with the value in dict.
    */
   public String substitute(String line, Properties dict) {
      int idx = 0;

      while((idx = line.indexOf("$(", idx)) >= 0) {
         if(idx == 0 || line.charAt(idx - 1) != '\\') {
            int eidx = line.indexOf(')');

            if(eidx > idx) {
               String name = line.substring(idx + 2, eidx).trim();
               String str = dict.getProperty(name);

               if(str == null) {
                  // does not exist, we shouldn't replace, just return name
                  // as is so later code can replace of logic is there
                  return line;
               }

               String configHome =
                  ConfigurationContext.getContext().getHome();

               if("sree.home".equals(name) && !str.equals(configHome)) {
                  str = configHome;
               }

               line = line.substring(0, idx) + str + line.substring(eidx + 1);
               idx += str.length();
            }
            else {
               break;
            }
         }
         else {
            idx += 2;
         }
      }

      return line;
   }

   /**
    * Saves the in-memory properties to the property file
    * specified by the argument.
    */
   public void save() throws IOException {
      if(getInternalProperties() == null) {
         init();
      }

      Set<String> changedProps;
      Properties prop;

      synchronized(this.changedProps) {
         changedProps = new HashSet<>(this.changedProps);
         this.changedProps.clear();
         prop = (Properties) getInternalProperties().clone();
      }

      String admHome = prop.getProperty("sree.home", ".");
      prop.remove("sree.home");
      prop.remove("sree.properties");
      prop.put("adm.home", admHome);

      KeyValueStorage<String> storage = getStorage();
      storage.removeListener(changeListener);

      try {
         saveToStorage(prop, storage, changedProps);
      }
      catch(ExecutionException | InterruptedException e) {
         throw new IOException("Failed to store properties in storage", e);
      }
      finally {
         storage.addListener(changeListener);
      }
   }

   private static DefaultDebouncer<String> getDebouncer() {
      DEBOUNCER_LOCK.lock();

      try {
         return ConfigurationContext.getContext()
            .computeIfAbsent(DEBOUNCER_KEY, k -> new DefaultDebouncer<>());
      }
      finally {
         DEBOUNCER_LOCK.unlock();
      }
   }

   private final KeyValueStorage.Listener<String> changeListener = new KeyValueStorage.Listener<String>() {
      @Override
      public void entryAdded(KeyValueStorage.Event<String> event) {
         onChange(event);
      }

      @Override
      public void entryUpdated(KeyValueStorage.Event<String> event) {
         onChange(event);
      }

      @Override
      public void entryRemoved(KeyValueStorage.Event<String> event) {
         onChange(event);
      }

      private void onChange(KeyValueStorage.Event<String> e) {
         PropertyChange change = new PropertyChange(e.getKey(), e.getOldValue(), e.getNewValue());
         getDebouncer().debounce(
            "change", 500L, TimeUnit.MILLISECONDS, new ChangeTask(change), this::reduce);
      }

      private Runnable reduce(Runnable r1, Runnable r2) {
         List<PropertyChange> changes = new ArrayList<>();
         ChangeTask task1 = (ChangeTask) r1;
         ChangeTask task2 = (ChangeTask) r2;

         if(task1 != null) {
            changes.addAll(task1.changes);
         }

         if(task2 != null) {
            changes.addAll(task2.changes);
         }

         return new ChangeTask(changes);
      }
   };

   private final class PropertyChange {
      public PropertyChange(String name, String oldValue, String newValue) {
         this.name = name;
         this.oldValue = oldValue;
         this.newValue = newValue;
      }

      public String getName() {
         return name;
      }

      public String getOldValue() {
         return oldValue;
      }

      public String getNewValue() {
         return newValue;
      }

      @Override
      public String toString() {
         return "PropertyChange{" +
            "name='" + name + '\'' +
            ", oldValue='" + oldValue + '\'' +
            ", newValue='" + newValue + '\'' +
            '}';
      }

      private final String name;
      private final String oldValue;
      private final String newValue;
   }

   private final class ChangeTask implements Runnable {
      private ChangeTask(PropertyChange change) {
         this.changes = new ArrayList<>();
         this.changes.add(change);
      }

      private ChangeTask(List<PropertyChange> changes) {
         this.changes = changes;
      }

      @Override
      public void run() {
         PropertiesEngine instance = PropertiesEngine.getInstance();
         String security = instance.getProperty("security.provider");
         String license = instance.getProperty("license.key");

         getStorage().removeListener(changeListener);
         instance.init(true);

         if(instance.getProperty("license.key") == null ||
            "".equals(instance.getProperty("license.key")))
         {
            instance.setProperty("license.key", license);
         }

         // reinitialize security provider if changed
         if(!Tool.equals(instance.getProperty("security.provider"), security)) {
            try {
               SecurityEngine engine = SecurityEngine.getSecurity();

               if(engine != null) {
                  SecurityEngine.clear();
               }
            }
            catch(Exception ex) {
               LOG.error("Failed to reload security provider", ex);
            }
         }

         // reload licenses
         LicenseManager.getInstance().reload();
      }

      private final List<PropertyChange> changes;
   }

   private final Set<String> changedProps = new TreeSet<>();
   private static final String EARLY_LOADED_PROPERTIES_KEY = PropertiesEngine.class.getName() + "_early_loaded_properties";
   private static final String PROPERTIES_KEY = PropertiesEngine.class.getName() + ".properties";
   private static final String DEFAULTS_PROPERTIES_KEY = PropertiesEngine.class.getName() + "_defaults.properties";
   private static final String STORAGE_KEY = PropertiesEngine.class.getName() + ".storage";
   private static final String DEBOUNCER_KEY = PropertiesEngine.class.getName() + ".debouncer";
   private static final Lock PROPERTIES_LOCK = new ReentrantLock();
   private static final Lock EARLY_LOADED_PROPERTIES_LOCK = new ReentrantLock();
   private static final Lock STORAGE_LOCK = new ReentrantLock();
   private static final Lock DEBOUNCER_LOCK = new ReentrantLock();
   private static final Map<String, Object> cache = new ConcurrentHashMap<>(); // cached objects
   private static final Map<String, Font> fontMap = new ConcurrentHashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(PropertiesEngine.class);
}
