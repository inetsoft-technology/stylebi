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
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityException;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.util.*;
import inetsoft.util.log.*;
import inetsoft.util.log.logback.LogbackUtil;
import inetsoft.util.script.JavaScriptEngine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Service
@Lazy
public class PropertiesEngine {
   public PropertiesEngine(KeyValueStorageManager keyValueStorageManager,
                           FileSystemService fileSystemService,
                           ApplicationEventPublisher eventPublisher,
                           ObjectProvider<LogManager> logManagerProvider)
   {
      this.keyValueStorageManager = keyValueStorageManager;
      this.fileSystemService = fileSystemService;
      this.eventPublisher = eventPublisher;
      this.logManagerProvider = logManagerProvider;
   }

   @PostConstruct
   public void initEngine() {
      addPropertyChangeListener("string.compare.casesensitive", evt -> Tool.invalidateCaseSensitive());
      // the new value is taken from the event rather than read back, because the
      // in-memory properties are not reloaded until the change task is debounced
      addPropertyChangeListener(
         QueryCacheSettings.LIMIT_PROPERTY,
         evt -> QueryCacheSettings.applyLimit((String) evt.getNewValue()));
      addPropertyChangeListener(
         QueryCacheSettings.TIMEOUT_PROPERTY,
         evt -> QueryCacheSettings.applyTimeout((String) evt.getNewValue()));
      // mysql.server.timezone/mysql.local.timezone are baked into SQLHelper's static dfuncs cache
      // on first load. applySqlHelperProperty() (below) already resets that cache on the node
      // that writes the property; this listener covers the cluster-sync path, where a peer node
      // learns of the change via this KeyValueStorage listener instead of its own setProperty
      // call and would otherwise never invalidate its own copy of the cache.
      addPropertyChangeListener("mysql.server.timezone", evt -> SQLHelper.resetCache());
      addPropertyChangeListener("mysql.local.timezone", evt -> SQLHelper.resetCache());
      kvStorage = keyValueStorageManager.getStorage("sreeProperties");
   }

   @PreDestroy
   public void shutdown() throws Exception {
      debouncer.close();
   }

   /**
    * Gets the singleton instance of {@code PropertiesEngine}.
    *
    * @return the PropertiesEngine instance.
    */
   public static PropertiesEngine getInstance() {
      return ConfigurationContext.getContext().getSpringBean(PropertiesEngine.class);
   }

   /**
    * Get all properties.
    */
   public Properties getProperties() {
      return getUserEnhancedProperties();
   }

   public Properties getInternalProperties() {
      return internalProperties;
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
      Properties prop = getInternalProperties();
      String key = fixPropertyNameCase(name);
      name = key;
      changeProperty(key, () -> prop.remove(key));

      // the log and SQL helper properties are deliberately not applied here, to keep the
      // existing removal behavior
      applyQueryCacheProperty(name);
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
      checkScriptThread();
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

         String key = name;
         String value = val;
         changeProperty(key, () -> prop.put(key, value));
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
      applyQueryCacheProperty(name);
   }

   /**
    * Push a changed query cache setting into the running caches, so that it takes effect
    * without a restart. This handles the change on this node; the property change
    * listener handles it on the other nodes of the cluster.
    *
    * @param name the property name.
    */
   private void applyQueryCacheProperty(String name) {
      if(QueryCacheSettings.LIMIT_PROPERTY.equals(name) ||
         QueryCacheSettings.TIMEOUT_PROPERTY.equals(name))
      {
         QueryCacheSettings.apply();
      }
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
      checkScriptThread();
      String property;

      if(context == LogContext.CATEGORY) {
         property = "log.level." + name;
         logManagerProvider.ifAvailable(lm -> lm.setLevel(name, level));
      }
      else {
         property = "log." + context.name() + ".level." + name;
         logManagerProvider.ifAvailable(lm -> lm.setContextLevel(context, name, level));
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
      if(name == null) {
         return null;
      }

      return propertyNameCaseCache.computeIfAbsent(name, this::computePropertyNameCase);
   }

   private String computePropertyNameCase(String name) {
      // organization-scoped property names carry the org ID as a case-insensitive segment;
      // strip and lowercase it, then apply the case rules below to the remaining property name
      // so it matches what useAvailableOrgProperty() looks up.
      if(name.startsWith("inetsoft.org.")) {
         int dot = name.indexOf('.', "inetsoft.org.".length());

         if(dot >= 0) {
            String orgPrefix = name.substring(0, dot + 1);
            String suffix = name.substring(dot + 1);
            // recurse directly rather than through fixPropertyNameCase(): that would re-enter
            // propertyNameCaseCache.computeIfAbsent() for a second key while the outer call for
            // this name is still computing, which ConcurrentHashMap can reject with a recursive
            // update IllegalStateException. Bypassing the cache here is deliberate; recursion is
            // one level deep (org prefix, then the real name), so the cost is negligible.
            return orgPrefix.toLowerCase() + computePropertyNameCase(suffix);
         }

         return name.toLowerCase();
      }

      if(!name.startsWith("log.level.") &&
         !name.startsWith("plugin.extra.classpath.") &&
         !name.matches("^log\\.[A-Z_]+\\.level\\..+$") &&
         !name.matches("^inetsoft\\.uql\\.jdbc\\.pool\\..+\\.connectionTestQuery$"))
      {
         return name.toLowerCase();
      }

      return name;
   }

   private String useAvailableOrgProperty(String propertyName) {
      // Fast path: check excluded properties first before any expensive operations
      if(EXCLUDED_ORG_PROPERTIES.contains(propertyName)) {
         return propertyName;
      }

      XPrincipal principal = (XPrincipal) ThreadContext.getPrincipal();
      principal = principal == null ? (XPrincipal) ThreadContext.getContextPrincipal() : principal;

      if(principal == null) {
         return propertyName;
      }

      String orgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(orgID != null) {
         orgID = orgID.toLowerCase();
         init();
         Properties prop = getInternalProperties();
         String orgPropertyName = "inetsoft.org." + orgID + "." + propertyName;

         if(prop.containsKey(orgPropertyName)) {
            return orgPropertyName;
         }
      }

      return propertyName;
   }

   private Properties getEarlyLoadedProperties() {
      return EarlyLoadedProperties.getInstance().asProperties();
   }

   /**
    * Get properties which loaded base on earlyLoadedProperties, and added some other properties
    * like properties from user storage, which should be getted with organizationID.
    */
   private Properties getUserEnhancedProperties() {
      init();
      return internalProperties;
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

      propertiesLock.lock();

      String oldHome = null;
      Properties oldProperties = null;

      try {
         if(fromChange) {
            oldHome = getProperty("sree.home");
            oldProperties = getInternalProperties();
            // keep the change listener attached, so that no change stored during the reload is
            // missed (Bug #76954)
            clear(false);
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

         kvStorage.addListener(changeListener);
         loadFromStorage(prop, kvStorage);

         // @by mikec, if sree.home was defined in sree.properties file
         // do not use the parent folder as sree.home
         // use the definition in sree.properties file instead
         if(prop.getProperty("sree.home") != null) {
            home = prop.getProperty("sree.home");
         }

         prop.setProperty("sree.home", home);

         if(fromChange) {
            // re-apply the pending properties before the reloaded properties are published, so
            // no other thread can see or save the reloaded properties without them (Bug #76954)
            restorePendingChanges(oldProperties, prop);
         }

         internalProperties = new DefaultProperties(prop, getDefaultProperties());

         if(fromChange) {
            setProperty("sree.home", oldHome);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to initialize SreeEnv: {}", ex, ex);
         Properties prop = getDefaultProperties();
         internalProperties = new DefaultProperties(prop, prop);
      }
      finally {
         propertiesLock.unlock();
      }

      initLogging();
      initFonts();
      LOG.info("InetSoft {} build {} started", Tool.getReportVersion(), Tool.getBuildNumber());
   }

   /**
    * Reads a property directly from the backing key-value storage, bypassing the in-memory
    * cache. Use this when a stale null from the cache would cause irreversible side effects.
    *
    * <p>A failure to read the store is propagated rather than reported as an absent value. Every
    * caller uses this method to decide whether a key already exists before generating a new one,
    * and "storage is unreadable" must not be mistaken for "storage is confirmed empty" — that
    * mistake is what lets two nodes each generate a different key. Failing here is recoverable;
    * silently minting a second signing or encryption key is not.
    *
    * @param name the name of the property.
    *
    * @return the stored value, or {@code null} if the store confirms there is no such property.
    *
    * @throws RuntimeException if the store could not be read.
    */
   public String getPropertyFromStorage(String name) {
      name = fixPropertyNameCase(name);
      KeyValueStorage<String> storage = kvStorage;

      if(storage == null) {
         throw new IllegalStateException(
            "Cannot read property " + name + " from storage: the property storage is not " +
            "initialized yet.");
      }

      try {
         return storage.get(name);
      }
      catch(Exception e) {
         LOG.warn("Failed to read property from storage: {}", name, e);
         throw new RuntimeException("Failed to read property from storage: " + name, e);
      }
   }


   public Properties getDefaultProperties() {
      Properties prop = defaultProperties;

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

      defaultProperties = prop;
      return prop;
   }

   /**
    * Clear and reload the properties.
    */
   public void clear() {
      clear(true);
   }

   private void clear(boolean removeListener) {
      propertiesLock.lock();

      try {
         // @by stephenwebster, For Bug #29148
         // Whenever SreeEnv is cleared, we must reset the log manager prior to a re-initialization
         // of SreeEnv.
         logManagerProvider.ifAvailable(LogManager::close);
         internalProperties = null;
         defaultProperties = null;
         // the storage contents are loaded into the early-loaded properties, so they must be
         // rebuilt for a reload to drop keys that were removed from the storage (Bug #76954)
         EarlyLoadedProperties.reset();

         if(removeListener && kvStorage != null) {
            try {
               kvStorage.removeListener(changeListener);
            }
            catch(Exception e) {
               LOG.warn("Failed to close key-value storage", e);
            }
         }
      }
      finally {
         propertiesLock.unlock();
      }
   }

   /**
    * Applies a change to a property and marks it as pending (changed but not saved). The first
    * time a property becomes pending, its stored value is recorded, so that a reload can tell
    * whether another node changed the property after it was edited locally. The storage is never
    * read while holding the {@code changedProps} monitor.
    *
    * @param name   the property name.
    * @param change the change to the in-memory properties.
    */
   private void changeProperty(String name, Runnable change) {
      boolean pending;

      synchronized(changedProps) {
         pending = changedProps.contains(name);
      }

      StorageValue baseline = pending ? null : readStorageValue(name);
      boolean baselineMissing;

      synchronized(changedProps) {
         change.run();
         baselineMissing = false;

         if(changedProps.add(name)) {
            if(baseline == null) {
               // it was pending when checked, but a save() cleared it in the meantime
               changedPropsBaseline.remove(name);
               baselineMissing = true;
            }
            else {
               changedPropsBaseline.put(name, baseline);
            }
         }
      }

      if(baselineMissing) {
         StorageValue value = readStorageValue(name);

         if(value != null) {
            synchronized(changedProps) {
               if(changedProps.contains(name)) {
                  changedPropsBaseline.putIfAbsent(name, value);
               }
            }
         }
      }
   }

   private StorageValue readStorageValue(String name) {
      KeyValueStorage<String> storage = kvStorage;

      if(storage == null) {
         return null;
      }

      try {
         return new StorageValue(storage.get(name));
      }
      catch(Exception e) {
         LOG.debug("Failed to read property from storage: {}", name, e);
         return null;
      }
   }

   /**
    * Re-applies the pending properties to the reloaded properties, before they are published. A
    * pending property is only re-applied if its stored value is still the one it had when the
    * property was edited locally. If another node changed it in the meantime, the stored value
    * wins and the property is no longer pending, so that a later save on this node does not
    * overwrite the other node's change. If the stored value was not known, the reloaded value
    * wins as well.
    *
    * @param oldProperties the in-memory properties before the reload, holding the local values.
    * @param properties    the reloaded properties the local values are applied to.
    */
   private void restorePendingChanges(Properties oldProperties, Properties properties) {
      if(oldProperties == null) {
         return;
      }

      Map<String, StorageValue> baselines = new HashMap<>();

      synchronized(changedProps) {
         for(String name : changedProps) {
            baselines.put(name, changedPropsBaseline.get(name));
         }
      }

      if(baselines.isEmpty()) {
         return;
      }

      // read the storage outside of the monitor
      Map<String, StorageValue> current = new HashMap<>();

      for(Map.Entry<String, StorageValue> e : baselines.entrySet()) {
         if(e.getValue() != null) {
            current.put(e.getKey(), readStorageValue(e.getKey()));
         }
      }

      // the layer that saveToStorage() writes, i.e. without the JVM system properties
      Properties oldValues = getInnermostProperties(oldProperties);

      synchronized(changedProps) {
         for(Map.Entry<String, StorageValue> e : baselines.entrySet()) {
            String name = e.getKey();

            // saved while reloading
            if(!changedProps.contains(name)) {
               continue;
            }

            StorageValue baseline = e.getValue();
            StorageValue stored = current.get(name);

            if(baseline != null && stored != null &&
               Tool.equals(baseline.value(), stored.value()))
            {
               if(oldValues.containsKey(name)) {
                  properties.put(name, oldValues.getProperty(name));
               }
               else {
                  properties.remove(name);
               }
            }
            else {
               changedProps.remove(name);
               changedPropsBaseline.remove(name);
            }
         }
      }
   }

   private static Properties getInnermostProperties(Properties properties) {
      while(properties instanceof DefaultProperties) {
         properties = ((DefaultProperties) properties).getMainProperties();
      }

      return properties;
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
      throws ExecutionException, InterruptedException, TimeoutException
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
            future.get(2L, TimeUnit.MINUTES);
         }
      }
   }

   /**
    * Initializes logging.
    */
   private void initLogging() {
      logManagerProvider.ifAvailable(lm -> {
         lm.setLevel("inetsoft.scheduler_test", LogLevel.OFF);
         lm.setLevel("inetsoft.mv_debug", LogLevel.OFF);
         lm.setLevel("inetsoft.swap_data", LogLevel.OFF);
         lm.setLevel(SUtil.MAC_LOG_NAME, LogLevel.OFF);
         lm.setLevel(LogUtil.PERFORMANCE_LOGGER_NAME, LogLevel.OFF);
         lm.setLevel("inetsoft.storage.aws.com.amazonaws", LogLevel.WARN);
         lm.setLevel("inetsoft.storage.aws.org.apache", LogLevel.WARN);
         lm.setLevel("org.apache.ignite", LogLevel.WARN);
      });

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
      if("log.detail.level".equals(prop)) {
         logManagerProvider.ifAvailable(lm -> lm.setLevel(LogManager.parseLevel(val)));
      }
      else if(prop.startsWith("log.level.")) {
         try {
            LogLevel level = LogManager.parseLevel(val);
            String name = prop.substring(10);

            if(name.isEmpty()) {
               throw new IllegalArgumentException("Empty logger name");
            }

            logManagerProvider.ifAvailable(lm -> lm.setLevel(name, level));
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
            LogLevel level = LogManager.parseLevel(val);
            logManagerProvider.ifAvailable(lm -> lm.setContextLevel(context, contextName, level));
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

      if(isScheduler() || !StringUtils.isBlank(System.getProperty("ScheduleTaskRunner"))) {
         prop = getPath("schedule.log.file", "schedule.log");
         logFile = SUtil.verifyLog(prop, "schedule.log");
      }
      else {
         prop = getProperty("log.output.file");
         logFile = SUtil.verifyLog(prop, "sree.log");
      }

      String discriminator = getProperty("log.file.discriminator");
      boolean console = !"true".equals(System.getProperty("ScheduleServer")) &&
         "true".equals(getProperty("log.output.stderr"));
      String performanceLevel = getProperty("log.level." + LogUtil.PERFORMANCE_LOGGER_NAME);
      long maxSize = Long.parseLong(getProperty("report.log.max"));
      int maxCount = Integer.parseInt(Objects.toString(getProperty("report.log.count"), "10"));
      boolean performance = performanceLevel != null &&
         !LogLevel.OFF.level().equalsIgnoreCase(performanceLevel);
      logManagerProvider.ifAvailable(lm -> lm.initialize(
         logFile, discriminator, console, maxSize, maxCount, performance));
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
      if(path == null || path.isEmpty()) {
         return path;
      }

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
         if(home != null && !fileSystemService.getFile(path).isAbsolute() && !path.isEmpty() &&
            path.charAt(0) != '/' && path.charAt(0) != '\\')
         {
            path = home + File.separator + path;
         }
      }

      String configHome = ConfigurationContext.getContext().getHome();

      // '/' can be used on win32 but it is not recognized as absolute
      if(!fileSystemService.getFile(path).exists() &&
         !fileSystemService.getFile(path).isAbsolute() && !path.isEmpty() &&
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
            if(path != null && !path.trim().isEmpty()) {
               scanFonts(fileSystemService.getFile(path.trim()));
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

      for(File file : Objects.requireNonNull(dir.listFiles())) {
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
                  System.err.println("Failed to load font " + file + ": " + exc);
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
      checkScriptThread();

      if(getInternalProperties() == null) {
         init();
      }

      Set<String> changedProps;
      Properties prop;

      synchronized(this.changedProps) {
         changedProps = new HashSet<>(this.changedProps);
         this.changedProps.clear();
         changedPropsBaseline.clear();
         prop = (Properties) getInternalProperties().clone();
      }

      String admHome = prop.getProperty("sree.home", ".");
      prop.remove("sree.home");
      prop.put("adm.home", admHome);

      // the change listener deliberately stays attached while saving. Removing it here dropped
      // the changes other cluster nodes stored during the save, and the reload that this node's
      // own change events trigger is harmless (Bug #76954).
      try {
         saveToStorage(prop, kvStorage, changedProps);
      }
      catch(ExecutionException | InterruptedException | TimeoutException e) {
         throw new IOException("Failed to store properties in storage", e);
      }
   }

   public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
      support.addPropertyChangeListener(propertyName, listener);
   }

   public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
      support.removePropertyChangeListener(propertyName, listener);
   }

   public static void checkScriptThread() {
      if(JavaScriptEngine.isScriptThread()) {
         throw new RuntimeException(new SecurityException(Catalog.getCatalog().getString("js.envs.blocked")));
      }
   }

   private final KeyValueStorage.Listener<String> changeListener = new KeyValueStorage.Listener<>() {
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

         support.firePropertyChange(e.getKey(), e.getOldValue(), e.getNewValue());

         debouncer.debounce(
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

   private static final class PropertyChange {
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

   /**
    * A value read from the property storage, {@code null} if the property is not stored.
    */
   private record StorageValue(String value) {
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

         // the change listener stays attached during the reload, so that no change stored in
         // the meantime is missed (Bug #76954)
         instance.init(true);

         if(instance.getProperty("license.key") == null ||
            "".equals(instance.getProperty("license.key")))
         {
            instance.setProperty("license.key", license);
         }

         ApplicationPropertiesChangedEvent event = new ApplicationPropertiesChangedEvent(
            this, !Tool.equals(instance.getProperty("security.provider"), security));
         eventPublisher.publishEvent(event);
      }

      private final List<PropertyChange> changes;
   }

   private final KeyValueStorageManager keyValueStorageManager;
   private final FileSystemService fileSystemService;
   private final ApplicationEventPublisher eventPublisher;
   private final ObjectProvider<LogManager> logManagerProvider;
   private final Set<String> changedProps = new TreeSet<>();
   // the stored value of each pending property when it became pending, guarded by changedProps
   private final Map<String, StorageValue> changedPropsBaseline = new HashMap<>();
   private final PropertyChangeSupport support = new PropertyChangeSupport(PropertiesEngine.class);
   private KeyValueStorage<String> kvStorage;
   private Properties internalProperties;
   private Properties defaultProperties;
   private final Lock propertiesLock = new ReentrantLock();
   private final DefaultDebouncer<String> debouncer = new DefaultDebouncer<>();
   private final Map<String, Object> cache = new ConcurrentHashMap<>(); // cached objects
   private final Map<String, Font> fontMap = new ConcurrentHashMap<>();
   private final Map<String, String> propertyNameCaseCache = new ConcurrentHashMap<>();
   private static final Set<String> EXCLUDED_ORG_PROPERTIES = Set.of(
      "security.enabled", "sree.security.listeners", "security.cache", "security.cache.interval",
      "inetsoft.sree.security.checkpermissionstrategy");
   private static final Logger LOG = LoggerFactory.getLogger(PropertiesEngine.class);
}
