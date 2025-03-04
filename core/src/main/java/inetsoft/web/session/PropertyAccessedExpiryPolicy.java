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
import org.springframework.util.StringUtils;

import javax.cache.configuration.Factory;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import java.io.Serializable;

public final class PropertyAccessedExpiryPolicy implements ExpiryPolicy, Serializable {
   public Factory<ExpiryPolicy> factoryOf() {
      return new FactoryBuilder.SingletonFactory<>(new PropertyAccessedExpiryPolicy());
   }

   @Override
   public Duration getExpiryForCreation() {
      return null;
   }

   @Override
   public Duration getExpiryForAccess() {
      return null;
   }

   @Override
   public Duration getExpiryForUpdate() {
      return null;
   }

   private Duration getExpiryFromProperty() {
      String property = SreeEnv.getProperty("http.session.timeout");

      if(StringUtils.hasText(property)) {
         try {
            return Integer.parseInt(property);
         }
         catch(NumberFormatException e) {
            LOG.error("Invalid value for http.session.timeout: {}", property, e);
         }
      }
   }
}
