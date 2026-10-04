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
      KeyValueStorage<String> storage = getStorage();

      // getStorage() already retried the load once for this freshly-fetched instance (below);
      // this is the cold-start path, so fail fast rather than starting the node with security/
      // SSO settings missing (Bug #76975).
      if(!storage.isLoaded()) {
         throw new IllegalStateException(
            "Failed to load the property storage " + STORAGE_ID + ", the server cannot start " +
            "without its properties");
      }
   }

   /**
    * Gets the key-value storage backing the stored properties, re-fetching it from the
    * {@link KeyValueStorageManager} and re-attaching {@link #changeListener} if the previously
    * held instance was evicted (and thus closed) by the manager's cache. Every read/write of
    * {@link #kvStorage} other than {@link #clear(boolean)}'s listener detach goes through this
    * method instead of the field directly, so a stale, closed instance is replaced before it can
    * silently go empty on enumeration (Bug #77177). Mirrors the established
    * {@link inetsoft.report.LibManager#getStorage()} idiom for a listener-bearing storage holder.
    *
    * <p>A freshly (re-)fetched instance's initial load can fail to complete (a transient
    * {@code cluster.submit()} timeout, interruption, or other failure) the same way it can at
    * cold start (Bug #76975): such an instance is not closed, so this method would otherwise
    * treat it as fine forever after, even though {@code stream()}/{@code keys()}/{@code get()}
    * are gated only by {@code isClosed()}, never by {@code isLoaded()} — the same "silently goes
    * empty" failure mode #76975/#76979 guard against at cold start. The load is retried once,
    * right here, for the same reason {@link #initEngine()} retries once: a second attempt is
    * cheap and often succeeds if the first failure was transient. Unlike cold start, a call here
    * can be reached from the middle of an arbitrary {@code setProperty()}/{@code remove()}/
    * {@code getProperty()} call, so a retry that still does not complete must not throw — it is
    * only logged, and the (possibly still-incomplete) instance is returned anyway, exactly as it
    * would have been before this method existed. The retry runs at most once per freshly-fetched
    * instance (not on every call while it stays unloaded), so a persistently failing load cannot
    * turn every future property access into a repeated, blocking multi-minute cluster call; the
    * instance stays in this degraded state until it is eventually replaced by a fresh one (the
    * next eviction-triggered close(), or a test double swapped in directly).</p>
    *
    * <p>{@code retryLoad()} is not always exception-free: {@code LocalKeyValueStorage.load()}
    * deliberately rethrows the cause of the load task's {@code ExecutionException} when it is a
    * {@code RuntimeException}, rather than swallowing it like a timeout or interruption, so a
    * genuinely broken storage engine still fails loudly at cold start
    * ({@link #initEngine()}'s own {@code isLoaded()} check below). At runtime that same
    * unguarded exception would otherwise propagate straight out of this method into an ordinary
    * {@code setProperty()}/{@code remove()}/{@code getProperty()} call, which is exactly the
    * "must not throw" guarantee this method exists to provide — so the retry is wrapped here and
    * a thrown exception degrades to the same logged, non-fatal outcome as a retry that simply
    * returns {@code false}.</p>
    *
    * <p>The same rethrow can also happen one step earlier, from the fetch itself
    * ({@code keyValueStorageManager.getStorage(STORAGE_ID)} constructing a brand-new
    * {@code LocalKeyValueStorage} whose very first, unconditional load attempt throws) rather
    * than from the retry (Bug #77177 review round 3). Unlike the retry case, there is no
    * {@code storage} object at all to fall back on if the fetch itself throws, so the two
    * situations are handled differently:</p>
    * <ul>
    *    <li>If there is no previous instance to fall back on ({@link #kvStorage} is {@code null}),
    *    this can only be {@link #initEngine()}'s very first call — {@code @PostConstruct}
    *    guarantees it completes, one way or another, before any other method can ever be invoked
    *    on this singleton bean. The exception is left to propagate, exactly as it already would
    *    have before this method existed: this fails Spring bean creation, matching Bug #76975's
    *    cold-start fail-fast intent rather than regressing it.</li>
    *    <li>If a previous instance already exists (even a closed one — an eviction-triggered
    *    re-fetch whose replacement construction fails), the exception is caught, logged, and the
    *    previous (stale/closed) instance is returned for this call instead of throwing — the same
    *    "must not throw at runtime" principle as the retry case. The next call here will attempt
    *    the same (still expensive) construction again, since {@code isClosed()} is still
    *    {@code true} for the previous instance; this is not new or specific to this fix, it is the
    *    same trade-off every other caller of {@link KeyValueStorageManager#getStorage(String)}
    *    (e.g. {@link inetsoft.report.LibManager}, {@code DeviceRegistry}) already accepts, since
    *    the manager's own Caffeine {@code get(id, loader)} caches no failure either.</li>
    * </ul>
    *
    * @return the live key-value storage instance.
    */
   private synchronized KeyValueStorage<String> getStorage() {
      if(kvStorage == null || kvStorage.isClosed()) {
         KeyValueStorage<String> previous = kvStorage;
         KeyValueStorage<String> storage;

         try {
            storage = keyValueStorageManager.getStorage(STORAGE_ID);
            storage.addListener(changeListener);
         }
         catch(Exception e) {
            if(previous == null) {
               // true cold start (initEngine()'s very first call): there is nothing to fall
               // back on, so this must propagate and fail the @PostConstruct bean creation,
               // exactly as it would have before this method existed (Bug #76975)
               throw e;
            }

            // a runtime self-heal after the previous instance was evicted/closed: keep serving
            // the previous, stale instance rather than throwing out of an arbitrary
            // setProperty()/remove()/getProperty() call (Bug #77177 review round 3)
            LOG.warn(
               "Failed to fetch a replacement for the property storage {}; continuing with " +
               "the previous instance until a later access succeeds", STORAGE_ID, e);
            return previous;
         }

         try {
            if(!storage.isLoaded() && !storage.retryLoad()) {
               LOG.warn(
                  "The property storage {} has not finished loading after a retry; properties " +
                  "read from it may be temporarily incomplete until it is next replaced by a " +
                  "successfully loaded instance", STORAGE_ID);
            }
         }
         catch(Exception e) {
            // retryLoad() can rethrow a RuntimeException from a genuinely broken storage engine
            // (LocalKeyValueStorage.load()); at cold start that is meant to fail the node, but a
            // runtime self-heal reached from an arbitrary setProperty()/remove()/getProperty()
            // call must not throw, so it is only logged, the same as a retry that just returns
            // false (Bug #77177 review round 2)
            LOG.warn(
               "Failed to retry loading the property storage {}; properties read from it may " +
               "be temporarily incomplete until it is next replaced by a successfully loaded " +
               "instance", STORAGE_ID, e);
         }

         kvStorage = storage;
      }

      return kvStorage;
   }

   @PreDestroy
   public void shutdown() throws Exception {
      // a change task that is already scheduled must not reload the properties after this engine
      // was shut down. Set the flag first, then stop receiving change events, so that no task can
      // be scheduled after it was cancelled (Bug #77201, Bug #77142)
      closed = true;
      KeyValueStorage<String> storage;

      // read under the same monitor getStorage() writes kvStorage under (Bug #77177)
      synchronized(this) {
         storage = kvStorage;
      }

      if(storage != null) {
         try {
            storage.removeListener(changeListener);
         }
         catch(Exception e) {
            LOG.warn("Failed to remove the property storage listener", e);
         }
      }

      debouncer.cancel("change");
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
      String key = fixPropertyNameCase(name);
      name = key;
      // a writer waits for a running reload, so that it changes the reloaded properties rather
      // than the ones the reload is about to replace (Bug #77142)
      propertiesLock.lock();

      try {
         Properties prop = getLoadedProperties();
         changeProperty(key, () -> prop.remove(key));
      }
      finally {
         propertiesLock.unlock();
      }

      // the SQL helper properties are deliberately not applied here, to keep the existing
      // removal behavior
      applyQueryCacheProperty(name);
      // reset the running log level, which is not read from the properties (Bug #77006)
      resetLogProperty(name);
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

         String key = name;
         String value = val;
         // a writer waits for a running reload, so that it changes the reloaded properties rather
         // than the ones the reload is about to replace (Bug #77142)
         propertiesLock.lock();

         try {
            Properties prop = getLoadedProperties();

            // if value is the same then don't set it as changed and just return
            if(Tool.equals(prop.getProperty(key), value)) {
               return;
            }

            changeProperty(key, () -> prop.put(key, value));
         }
         finally {
            propertiesLock.unlock();
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

      return propertyNameCaseCache.computeIfAbsent(name, PropertiesEngine::computePropertyNameCase);
   }

   private static String computePropertyNameCase(String name) {
      // organization-scoped property names carry the org ID as a case-insensitive segment;
      // strip and lowercase it, then apply the case rules below to the remaining property name
      // so it matches what useAvailableOrgProperty() looks up.
      if(name.startsWith("inetsoft.org.")) {
         int dot = name.indexOf('.', "inetsoft.org.".length());

         if(dot >= 0) {
            String orgID = name.substring("inetsoft.org.".length(), dot);
            String suffix = name.substring(dot + 1);
            // recurse directly rather than through fixPropertyNameCase(): that would re-enter
            // propertyNameCaseCache.computeIfAbsent() for a second key while the outer call for
            // this name is still computing, which ConcurrentHashMap can reject with a recursive
            // update IllegalStateException. Bypassing the cache here is deliberate; recursion is
            // one level deep (org prefix, then the real name), so the cost is negligible.
            return getOrgPropertyPrefix(orgID) + computePropertyNameCase(suffix);
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

   /**
    * Get the prefix of an organization's scoped property names as they are stored, i.e.
    * <code>inetsoft.org.&lt;org&gt;.</code> with the case rules applied that every read and write
    * applies to it. Organization IDs are case insensitive, so IDs that differ only in case have
    * the same prefix. The org ID is lower-cased in the default locale on purpose: it must match
    * {@code computePropertyNameCase()} and the readers, which also use the default locale,
    * so do not change it to {@code Locale.ROOT}.
    */
   public static String getOrgPropertyPrefix(String orgID) {
      return ("inetsoft.org." + orgID + ".").toLowerCase();
   }

   /**
    * Determines if a property name is an organization override,
    * <code>inetsoft.org.&lt;org&gt;.&lt;name&gt;</code>, of a JVM-wide setting that is always read
    * globally. Such an override is never consulted by a property read (see
    * {@link #getPropertyOrgScope(String)}), so storing one has no effect. A global name, without
    * the organization prefix, is not matched.
    *
    * @param name the property name, as it would be passed to {@link #setProperty(String, String)}.
    *
    * @return <code>true</code> if the name is an organization override of a global-only setting.
    */
   public static boolean isExcludedOrgProperty(String name) {
      if(name == null) {
         return false;
      }

      // normalize the name the same way it would be stored
      String stored = computePropertyNameCase(name);
      String prefix = "inetsoft.org.";

      if(!stored.startsWith(prefix)) {
         return false;
      }

      String rest = stored.substring(prefix.length());

      // match the excluded name as a dot-bounded suffix with a non-empty org segment before it,
      // rather than splitting at the first dot, so that an org ID that contains a dot (allowed
      // for an org that predates the ID rules) is matched too
      for(String excluded : EXCLUDED_ORG_PROPERTIES) {
         String suffix = "." + excluded;

         if(rest.length() > suffix.length() && rest.endsWith(suffix)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the property name as it is stored, i.e. with the case rules applied that every read and
    * write applies to it.
    */
   String getPropertyNameCase(String name) {
      return fixPropertyNameCase(name);
   }

   /**
    * Get the organization whose <code>inetsoft.org.&lt;org&gt;.</code> override a property read
    * made on the current thread consults. This is the same resolution that
    * {@link #useAvailableOrgProperty(String)} applies, so a cache of property values can be keyed
    * by it and still resolve exactly as an uncached read does.
    *
    * @param propertyName the property name, with {@link #fixPropertyNameCase(String)} applied.
    *
    * @return the lower case organization ID, or <code>null</code> if the read uses the global
    *         key because the property is never organization scoped or the thread has no
    *         principal. No organization lookup is made in either of those cases.
    */
   static String getPropertyOrgScope(String propertyName) {
      // Fast path: check excluded properties first before any expensive operations
      if(EXCLUDED_ORG_PROPERTIES.contains(propertyName) || !hasThreadPrincipal()) {
         return null;
      }

      String orgID = OrganizationManager.getInstance().getCurrentOrgID();
      return orgID == null ? null : orgID.toLowerCase();
   }

   /**
    * Determines if the current thread has a principal, in which case a property read is
    * resolved in the principal's organization.
    */
   static boolean hasThreadPrincipal() {
      return ThreadContext.getPrincipal() != null || ThreadContext.getContextPrincipal() != null;
   }

   private String useAvailableOrgProperty(String propertyName) {
      String orgID = getPropertyOrgScope(propertyName);

      if(orgID != null) {
         Properties prop = getLoadedProperties();
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
      return getLoadedProperties();
   }

   /**
    * Gets the published properties, loading them first if needed. The field is read once into a
    * local, so the returned properties are never {@code null}: a reload publishes the reloaded
    * properties in a single write and never clears the field (Bug #77142). Only {@link #clear()}
    * does, hence the loop.
    */
   private Properties getLoadedProperties() {
      Properties prop;

      while((prop = internalProperties) == null) {
         init();
      }

      return prop;
   }

   /**
    * Initialize the environment.
    */
   public void init() {
      init(false);
   }

   public void init(boolean fromChange) {
      if(!fromChange && internalProperties != null) {
         return;
      }

      propertiesLock.lock();

      String oldHome = null;
      Properties oldProperties = null;
      EarlyLoadedProperties oldEarlyLoaded = null;
      // the properties published by this call, or kept by a failed reload
      Properties properties = null;

      try {
         EarlyLoadedProperties earlyLoaded;

         if(fromChange) {
            oldHome = getProperty("sree.home");
            oldProperties = internalProperties;
            oldEarlyLoaded = EarlyLoadedProperties.getInstance();
            // @by stephenwebster, For Bug #29148
            // the log manager must be reset prior to a re-initialization of SreeEnv
            logManagerProvider.ifAvailable(LogManager::close);
            defaultProperties = null;
            // the previous properties and early-loaded properties stay published until the
            // reloaded ones replace them, so that no reader ever sees null or properties without
            // the stored values (Bug #77142). The new early-loaded properties are built without
            // touching the installed instance. The change listener stays attached, so that no
            // change stored during the reload is missed (Bug #76954).
            earlyLoaded = EarlyLoadedProperties.create();
         }
         // @by davidd, Recheck once lock acquired to prevent reinitialization.
         else if((properties = internalProperties) != null) {
            return;
         }
         else {
            earlyLoaded = EarlyLoadedProperties.getInstance();
         }

         Properties prop = earlyLoaded.asProperties();

         if(prop instanceof DefaultProperties) {
            prop = ((DefaultProperties) prop).getMainProperties();
         }
         else {
            prop = new LayerProperties();
         }

         String home = ConfigurationContext.getContext().getHome();

         // getStorage() replaces a stale/closed instance before it is used below -- except when
         // the replacement fetch itself fails and a previous instance exists, in which case it
         // falls back to returning that previous, still-closed instance rather than throwing
         // (Bug #77177 review round 3), so a runtime point read/write degrades gracefully instead
         // of crashing an arbitrary setProperty()/remove()/getProperty() call. That fallback is
         // safe for get()/put()-style access (close() never tears down the underlying map), but
         // not here: loadFromStorage() below enumerates via stream()/keys(), which are gated only
         // by isClosed() (not by isLoaded()) and return an empty stream with no exception on a
         // closed instance -- exactly the original #77177 mechanism. Silently proceeding would
         // wipe/incomplete internalProperties the same way the bug this PR exists to fix did, so
         // refuse the reload instead and let the catch block below -- which already exists
         // specifically to keep the previous properties on a failed reload (Bug #76979) -- handle
         // it (Bug #77177 review round 4).
         KeyValueStorage<String> storage = getStorage();

         if(storage.isClosed()) {
            throw new IllegalStateException(
               "The property storage " + STORAGE_ID + " could not be refreshed and remains " +
               "closed; skipping this reload to avoid publishing an incomplete property set");
         }

         // the listener is (re-)added unconditionally here too (harmless if already present, a
         // Set add), so that a storage instance swapped in by something other than getStorage()'s
         // own refetch (e.g. a test double) still gets the listener attached on every reload.
         storage.addListener(changeListener);
         loadFromStorage(prop, storage);

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

         Properties loaded = new DefaultProperties(prop, getDefaultProperties());

         if(fromChange) {
            // the storage contents were loaded into the new early-loaded properties, which drop
            // the keys that were removed from the storage (Bug #76954)
            EarlyLoadedProperties.restore(earlyLoaded);
         }

         internalProperties = properties = loaded;

         if(fromChange) {
            setProperty("sree.home", oldHome);
         }
      }
      catch(Exception ex) {
         if(oldProperties != null) {
            LOG.error("Failed to reload SreeEnv, keeping the previous properties: {}", ex, ex);

            // a failed reload keeps the previously loaded properties. Publishing the defaults
            // would drop every stored property, the security settings included, until the next
            // reload succeeds (Bug #76979)
            if(properties == null) {
               // the old instance is still installed unless installing the new one failed
               EarlyLoadedProperties.restore(oldEarlyLoaded);
               properties = oldProperties;
            }
         }
         else {
            LOG.error("Failed to initialize SreeEnv: {}", ex, ex);
            Properties prop = getDefaultProperties();
            internalProperties = properties = new DefaultProperties(prop, prop);
         }
      }
      finally {
         propertiesLock.unlock();
      }

      // the properties just built are passed on rather than read back from the field, which a
      // concurrent reload or clear() may have replaced in the meantime (Bug #77142)
      initLogging(properties);

      if(fromChange) {
         // initLogging() only applies the log properties that are present in some property
         // layer, so the running log levels of the properties removed by the reload must be
         // reset (Bug #77006)
         resetRemovedLogProperties(oldProperties, properties);
      }

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
      KeyValueStorage<String> storage = getStorage();

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


   /**
    * Gets the value of a property from the sources that never hold key-value storage values,
    * in the same order the cached properties layer them: an {@code INETSOFT_*} environment
    * variable, then a system property, then the built-in defaults. Callers that read a property
    * from the storage with {@link #getPropertyFromStorage(String)} use this when it is not
    * stored, instead of {@link #getProperty(String, String)}, whose cached storage values may be
    * stale on a cluster node that has not reloaded yet (Bug #77323).
    *
    * @param name the name of the property.
    *
    * @return the property value, or {@code null} if none of these sources supplies it.
    */
   public String getPropertyFromNonStorageSources(String name) {
      name = fixPropertyNameCase(name);

      if(name == null) {
         return null;
      }

      String value = EarlyLoadedProperties.getEnvironmentProperty(name);

      if(value == null) {
         value = System.getProperty(name);
      }

      if(value == null) {
         value = getDefaultProperties().getProperty(name);
      }

      return value;
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

         if(removeListener) {
            try {
               // read under the same monitor getStorage() writes kvStorage under, so this never
               // observes a half-published reference; removing the listener from a possibly
               // stale/closed instance is still harmless either way (Bug #77177)
               KeyValueStorage<String> storage;

               synchronized(this) {
                  storage = kvStorage;
               }

               if(storage != null) {
                  storage.removeListener(changeListener);
               }
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
      KeyValueStorage<String> storage = getStorage();

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
   private void initLogging(Properties props) {
      logManagerProvider.ifAvailable(lm -> DEFAULT_LOG_LEVELS.forEach(lm::setLevel));

      reloadLoggingFramework();

      // the levels are all applied first and the logging framework is then reloaded once,
      // rather than once per log property
      Set<String> names = getLogPropertyNames(props);

      for(String name : names) {
         applyLogProperty(name, getProperty(name));
      }

      if(!names.isEmpty()) {
         reloadLogging();
      }

      System.out.println("Using built-in log configuration");
   }

   /**
    * Gets the names of the log level properties set in any layer of the properties: the stored
    * properties, the JVM system properties and defaults.properties. {@link
    * DefaultProperties#propertyNames()} only enumerates the main (stored) layer, so the defaults,
    * e.g. {@code log.detail.level=INFO}, and the {@code -D} system properties would otherwise
    * never be applied (Bug #77302). The value of each property is read with {@link
    * #getProperty(String)}, so the stored value still takes precedence over the system property
    * and the system property over the default.
    *
    * <p>{@code log.detail.level} and {@code log.level.inetsoft} both set the level of the
    * {@code inetsoft} logger; {@link #applyInetsoftLevel()} resolves them together, so the order
    * of the names does not matter. It is kept deterministic, {@code log.detail.level} first.</p>
    *
    * @param props the properties.
    *
    * @return the log property names, {@code log.detail.level} first.
    */
   private static Set<String> getLogPropertyNames(Properties props) {
      Set<String> names = new HashSet<>();
      collectPropertyNames(props, names);
      Set<String> result = new LinkedHashSet<>();

      if(names.contains("log.detail.level")) {
         result.add("log.detail.level");
      }

      names.stream()
         .filter(PropertiesEngine::isLogProperty)
         .sorted()
         .forEach(result::add);
      return result;
   }

   private static void collectPropertyNames(Properties props, Set<String> names) {
      if(props == null) {
         return;
      }

      for(Enumeration<?> e = props.propertyNames(); e.hasMoreElements();) {
         Object name = e.nextElement();

         if(name instanceof String) {
            names.add((String) name);
         }
      }

      if(props instanceof DefaultProperties) {
         DefaultProperties layered = (DefaultProperties) props;
         collectPropertyNames(layered.getMainProperties(), names);
         collectPropertyNames(layered.getDefaultProperties(), names);
      }
   }

   /**
    * Determines if a property sets a running log level.
    *
    * @param prop the property name.
    *
    * @return {@code true} if a log level property, {@code false} otherwise.
    */
   private static boolean isLogProperty(String prop) {
      return !Tool.isEmptyString(prop) &&
         (prop.startsWith("log.level.") || prop.matches("^log\\.[A-Z_]+\\.level\\..+$") ||
         prop.equals("log.detail.level"));
   }

   private void applyLogProperty(String prop) {
      if(!isLogProperty(prop)) {
         return;
      }

      applyLogProperty(prop, getProperty(prop));
      reloadLogging();
   }

   /**
    * Resets the running log level of a removed log property to the effective value of the
    * property (e.g. from defaults.properties), else the built-in level set by
    * {@link #initLogging(Properties)}, else no level, so that it is inherited.
    *
    * @param prop the name of the removed property.
    */
   private void resetLogProperty(String prop) {
      if(resetLogLevel(prop)) {
         reloadLogging();
      }
   }

   /**
    * Resets the running log levels of the log properties that a reload removed.
    *
    * @param oldProperties the properties before the reload.
    * @param props         the reloaded properties.
    */
   private void resetRemovedLogProperties(Properties oldProperties, Properties props) {
      if(oldProperties == null || props == null) {
         return;
      }

      // enumerate the same properties that initLogging() applies, in every property layer. A
      // removed stored property that is still set by a system property or a default was already
      // re-applied with that value by initLogging(), so only the log properties that are no
      // longer set in any layer are reset here
      Set<String> names = getLogPropertyNames(props);
      boolean reset = false;

      for(String prop : getLogPropertyNames(oldProperties)) {
         if(!names.contains(prop) && resetLogLevel(prop)) {
            reset = true;
         }
      }

      if(reset) {
         reloadLogging();
      }
   }

   /**
    * Resets the running log level of a removed log property, without reloading the logging
    * framework.
    *
    * @param prop the name of the removed property.
    *
    * @return {@code true} if the property is a log property, {@code false} otherwise.
    */
   private boolean resetLogLevel(String prop) {
      if(!isLogProperty(prop)) {
         return false;
      }

      if(isInetsoftLevelProperty(prop)) {
         // the other of the two properties that set the inetsoft logger, if any, still applies
         applyInetsoftLevel();
         return true;
      }

      String val = getProperty(prop);

      if(val != null) {
         applyLogProperty(prop, val);
      }
      else if(prop.startsWith("log.level.")) {
         String name = prop.substring(10);

         if(!name.isEmpty()) {
            logManagerProvider.ifAvailable(lm -> lm.setLevel(name, DEFAULT_LOG_LEVELS.get(name)));
         }
      }
      else {
         try {
            LogContext context = LogContext.valueOf(prop.substring(4, prop.indexOf('.', 4)));
            String contextName = prop.substring(prop.indexOf('.', 4) + 7);
            logManagerProvider.ifAvailable(lm -> lm.setContextLevel(context, contextName, null));
         }
         catch(IllegalArgumentException exc) {
            // not a valid log context, so it was never applied
            return false;
         }
      }

      return true;
   }

   private void reloadLogging() {
      try {
         LogbackUtil.resetLog();
      }
      catch(Exception e) {
         LOG.error("Failed to reset Logback", e);
      }

      SreeEnv.reloadLoggingFramework();
   }

   private void applyLogProperty(String prop, String val) {
      if(isInetsoftLevelProperty(prop)) {
         applyInetsoftLevel();
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

   /**
    * Determines if a property sets the level of the {@code inetsoft} logger, which both
    * {@code log.detail.level} and the more specific {@code log.level.inetsoft} do.
    */
   private static boolean isInetsoftLevelProperty(String prop) {
      return "log.detail.level".equals(prop) || INETSOFT_LEVEL_PROPERTY.equals(prop);
   }

   /**
    * Applies the effective level of the {@code inetsoft} logger: {@code log.level.inetsoft} if
    * it is set in any property layer, else {@code log.detail.level} (INFO in
    * defaults.properties), else no level. Both properties are resolved together, so applying or
    * removing one of them never clobbers or drops the other (Bug #77302).
    */
   private void applyInetsoftLevel() {
      String val = getProperty(INETSOFT_LEVEL_PROPERTY);

      if(val == null) {
         val = getProperty("log.detail.level");
      }

      LogLevel level = val == null ? null : LogManager.parseLevel(val);
      logManagerProvider.ifAvailable(lm -> lm.setLevel(level));
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

      Set<String> changedProps;
      Properties prop;
      // the snapshot waits for a running reload, so that it is taken from the reloaded
      // properties, which hold the pending changes it clears (Bug #77142). The storage is written
      // without the lock.
      propertiesLock.lock();

      try {
         Properties current = getLoadedProperties();

         synchronized(this.changedProps) {
            changedProps = new HashSet<>(this.changedProps);
            this.changedProps.clear();
            changedPropsBaseline.clear();
            prop = (Properties) current.clone();
         }
      }
      finally {
         propertiesLock.unlock();
      }

      String admHome = prop.getProperty("sree.home", ".");
      prop.remove("sree.home");
      prop.put("adm.home", admHome);

      // the change listener deliberately stays attached while saving. Removing it here dropped
      // the changes other cluster nodes stored during the save, and the reload that this node's
      // own change events trigger is harmless (Bug #76954).
      try {
         saveToStorage(prop, getStorage(), changedProps);
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
         // the change was already stored, so an engine that has shut down in the meantime drops
         // the reload. The task reloads the engine that scheduled it, not getInstance(), which
         // may already resolve the engine of another application context (Bug #77201)
         if(closed) {
            return;
         }

         // reload the engine that received the change. getInstance() resolves the engine of the
         // current configuration context, which is another engine once the context of this one
         // was replaced, e.g. by the next test class (Bug #77142)
         PropertiesEngine instance = PropertiesEngine.this;
         String security = instance.getProperty("security.provider");
         String license = instance.getProperty("license.key");

         // the change listener stays attached during the reload, so that no change stored in
         // the meantime is missed (Bug #76954)
         init(true);

         if(getProperty("license.key") == null || "".equals(getProperty("license.key"))) {
            setProperty("license.key", license);
         }

         ApplicationPropertiesChangedEvent event = new ApplicationPropertiesChangedEvent(
            this, !Tool.equals(getProperty("security.provider"), security));
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
   // Concurrency axis (Bug #77142): lock-free readers x writers x change-triggered reload x
   // shutdown. The field is published in a single write and a reload never sets it to null, so a
   // reader reads it once into a local and takes no lock. Writers (setProperty, remove and the
   // snapshot of save) hold propertiesLock, like the reload, so that they are never applied to
   // the properties a running reload is about to replace. A shut-down engine never reloads.
   private volatile Properties internalProperties;
   private Properties defaultProperties;
   private final Lock propertiesLock = new ReentrantLock();
   // a single task key, so the sequence of different keys is irrelevant, and cancel() only works
   // on a debouncer that does not preserve it
   private final DefaultDebouncer<String> debouncer = new DefaultDebouncer<>(false);
   private volatile boolean closed;
   private final Map<String, Object> cache = new ConcurrentHashMap<>(); // cached objects
   private final Map<String, Font> fontMap = new ConcurrentHashMap<>();
   private final Map<String, String> propertyNameCaseCache = new ConcurrentHashMap<>();
   // lower-case names of JVM-wide settings, which are always read globally, never from an
   // inetsoft.org.<org>. override
   private static final Set<String> EXCLUDED_ORG_PROPERTIES = Set.of(
      "security.enabled", "sree.security.listeners", "security.cache", "security.cache.interval",
      "inetsoft.sree.security.checkpermissionstrategy",
      // the swapper settings
      "swapper.critical.max.wait", "swapper.gc.min.interval", "swapper.idle.gc.interval",
      "swapper.count", "swapper.free.ratio", "swappable.alive.period",
      "swapper.scalingmetric.excludeeden", "ignore.swapper.memory.state",
      // the cache directory of the swap files and the node settings it defaults from, which a
      // swap file's writer and reader must resolve the same on any thread (Bug #77683)
      "replet.cache.directory", "sree.home", "server.type");
   // the built-in logger levels set by initLogging(), which a removed log property resets to
   private static final Map<String, LogLevel> DEFAULT_LOG_LEVELS = Map.of(
      "inetsoft.scheduler_test", LogLevel.OFF,
      "inetsoft.mv_debug", LogLevel.OFF,
      "inetsoft.swap_data", LogLevel.OFF,
      SUtil.MAC_LOG_NAME, LogLevel.OFF,
      LogUtil.PERFORMANCE_LOGGER_NAME, LogLevel.OFF,
      "inetsoft.storage.aws.com.amazonaws", LogLevel.WARN,
      "inetsoft.storage.aws.org.apache", LogLevel.WARN,
      "org.apache.ignite", LogLevel.WARN);
   private static final String INETSOFT_LEVEL_PROPERTY = "log.level.inetsoft";
   private static final String STORAGE_ID = "sreeProperties";
   private static final Logger LOG = LoggerFactory.getLogger(PropertiesEngine.class);
}
