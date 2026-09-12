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
      return getExpiryFromProperty();
   }

   @Override
   public Duration getExpiryForAccess() {
      return getExpiryFromProperty();
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
   private static final Logger LOG = LoggerFactory.getLogger(PropertyAccessedExpiryPolicy.class);
}
