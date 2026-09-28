/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.health;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Completes actuator web endpoint discovery on the main thread before any connector accepts
 * requests. A first discovery on a request thread that overlaps the management context's
 * discovery can deadlock startup (bug #76974); after this, discovery is a cache hit.
 */
@Component
@Lazy(false)
public class EndpointDiscoveryWarmup implements SmartInitializingSingleton {
   public EndpointDiscoveryWarmup(ObjectProvider<WebEndpointsSupplier> webEndpointsSuppliers) {
      this.webEndpointsSuppliers = webEndpointsSuppliers;
   }

   @Override
   public void afterSingletonsInstantiated() {
      webEndpointsSuppliers.orderedStream().forEach(WebEndpointsSupplier::getEndpoints);
   }

   private final ObjectProvider<WebEndpointsSupplier> webEndpointsSuppliers;
}
