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
package inetsoft.util;

import inetsoft.sree.SreeEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central point of control for the query data cache settings,
 * <tt>query.cache.limit</tt> and <tt>query.cache.timeout</tt>.
 * <p>
 * The caches that are sized from these two properties register themselves here. When
 * either property is changed, the new value is pushed into every registered cache, so
 * that a change made in the Enterprise Manager takes effect immediately instead of on
 * the next restart.
 *
 * @version 15.0
 * @author InetSoft Technology Corp
 */
public final class QueryCacheSettings {
   /**
    * Creates a new instance of QueryCacheSettings.
    */
   private QueryCacheSettings() {
   }

   /**
    * Register a cache that is sized from the query cache properties. The current values
    * are applied to the cache before this method returns. Only a weak reference to the
    * cache is kept, so registration does not prevent it from being collected.
    *
    * @param cache the cache to register.
    */
   public static void register(DataCache<?, ?> cache) {
      if(cache == null) {
         return;
      }

      add(cache);
      cache.setLimit(getLimit());
      cache.setTimeout(getTimeout());
   }

   /**
    * Add a cache to the registry without applying the current settings to it.
    */
   static void add(DataCache<?, ?> cache) {
      synchronized(caches) {
         caches.add(cache);
      }
   }

   /**
    * Get the configured maximum number of entries.
    */
   public static int getLimit() {
      return parseLimit(SreeEnv.getProperty(LIMIT_PROPERTY));
   }

   /**
    * Get the configured cache timeout, in milliseconds.
    */
   public static long getTimeout() {
      return parseTimeout(SreeEnv.getProperty(TIMEOUT_PROPERTY));
   }

   /**
    * Push the current property values into every registered cache.
    */
   public static void apply() {
      int limit = getLimit();
      long timeout = getTimeout();

      for(DataCache<?, ?> cache : snapshot()) {
         cache.setLimit(limit);
         cache.setTimeout(timeout);
      }
   }

   /**
    * Push a new cache size into every registered cache.
    * <p>
    * This takes the raw property value rather than reading it back from {@link SreeEnv}
    * because the property change notification is fired before the in-memory properties
    * are reloaded, so a read here would see the previous value.
    *
    * @param value the new value of <tt>query.cache.limit</tt>, or <tt>null</tt> if the
    *              property was removed.
    */
   public static void applyLimit(String value) {
      int limit = parseLimit(value);

      for(DataCache<?, ?> cache : snapshot()) {
         cache.setLimit(limit);
      }
   }

   /**
    * Push a new cache timeout into every registered cache.
    *
    * @param value the new value of <tt>query.cache.timeout</tt>, or <tt>null</tt> if the
    *              property was removed.
    */
   public static void applyTimeout(String value) {
      long timeout = parseTimeout(value);

      for(DataCache<?, ?> cache : snapshot()) {
         cache.setTimeout(timeout);
      }
   }

   /**
    * Get the registered caches. A copy is taken so that the caches are not updated while
    * holding the monitor.
    */
   private static DataCache<?, ?>[] snapshot() {
      synchronized(caches) {
         return caches.toArray(new DataCache<?, ?>[0]);
      }
   }

   /**
    * Parse a cache size. The value is bounded to the range of an int, since that is what
    * the caches accept, so that an out-of-range value stored in the property does not
    * fail the parse.
    */
   static int parseLimit(String value) {
      if(value == null) {
         return DEFAULT_LIMIT;
      }

      try {
         return (int) Math.max(0, Math.min(Integer.MAX_VALUE, Long.parseLong(value.trim())));
      }
      catch(NumberFormatException ex) {
         LOG.warn("Invalid value for {}, using {}: {}", LIMIT_PROPERTY, DEFAULT_LIMIT, value);
         return DEFAULT_LIMIT;
      }
   }

   /**
    * Parse a cache timeout, in milliseconds.
    */
   static long parseTimeout(String value) {
      if(value == null) {
         return DEFAULT_TIMEOUT;
      }

      try {
         return Math.max(0L, Long.parseLong(value.trim()));
      }
      catch(NumberFormatException ex) {
         LOG.warn("Invalid value for {}, using {}: {}", TIMEOUT_PROPERTY, DEFAULT_TIMEOUT, value);
         return DEFAULT_TIMEOUT;
      }
   }

   /**
    * The property that holds the maximum number of cache entries.
    */
   public static final String LIMIT_PROPERTY = "query.cache.limit";
   /**
    * The property that holds the cache timeout, in milliseconds.
    */
   public static final String TIMEOUT_PROPERTY = "query.cache.timeout";
   /**
    * The default cache size, matching defaults.properties.
    */
   public static final int DEFAULT_LIMIT = 100;
   /**
    * The default cache timeout, matching defaults.properties.
    */
   public static final long DEFAULT_TIMEOUT = 600000L;

   private static final DataCache.CacheSet caches = new DataCache.CacheSet();
   private static final Logger LOG = LoggerFactory.getLogger(QueryCacheSettings.class);
}
