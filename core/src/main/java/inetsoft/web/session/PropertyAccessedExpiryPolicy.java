/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.session;

import inetsoft.sree.SreeEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.cache.configuration.Factory;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

public final class PropertyAccessedExpiryPolicy implements ExpiryPolicy, Serializable {
   public Factory<ExpiryPolicy> factoryOf() {
      return new FactoryBuilder.SingletonFactory<>(new PropertyAccessedExpiryPolicy());
   }

   @Override
   public Duration getExpiryForCreation() {
      return getTimeToLiveFromProperty();
   }

   @Override
   public Duration getExpiryForAccess() {
      return getTimeToLiveFromProperty();
   }

   /**
    * The Ignite time-to-live of a session cache entry: the session timeout plus
    * {@link #TTL_MARGIN_SECONDS}. A session ends when its {@code MapSession} expires, which an
    * {@code IgniteSessionRepository} sweep (checkSessions on every node every 20 seconds, or a
    * lazy {@code findById}) detects and turns into {@code deleteById}: the LOGOFF audit record,
    * the license release and the {@code SessionExpiredEvent}. The entry must still be in the
    * cache when the sweep runs. If the TTL equaled the timeout, the entry would vanish at the
    * moment the session expired and no sweep could ever see it (Bug #77886). The TTL is only a
    * backstop for a session no sweep reached.
    */
   static Duration getTimeToLiveFromProperty() {
      Duration timeout = getExpiryFromProperty();
      long seconds = timeout.getTimeUnit().toSeconds(timeout.getDurationAmount());
      return new Duration(TimeUnit.SECONDS, seconds + TTL_MARGIN_SECONDS);
   }

   @Override
   public Duration getExpiryForUpdate() {
      return null;
   }

   static Duration getExpiryFromProperty() {
      String property = SreeEnv.getProperty("http.session.timeout");

      if(StringUtils.hasText(property)) {
         try {
            return new Duration(TimeUnit.SECONDS, Long.parseLong(property));
         }
         catch(NumberFormatException e) {
            LOG.error("Invalid value for http.session.timeout: {}", property, e);
         }
      }

      property = Integer.toString(DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
      SreeEnv.setProperty("http.session.timeout", property);
      return new Duration(TimeUnit.SECONDS, DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
   }

   public static final int DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS = 1800;
   // three checkSessions periods (20 s each): covers a late or failed pass and clock skew
   // between the nodes that set lastAccessedTime and the node that sweeps
   static final long TTL_MARGIN_SECONDS = 60;
   private static final Logger LOG = LoggerFactory.getLogger(PropertyAccessedExpiryPolicy.class);
}
