/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.security;

import inetsoft.sree.internal.cluster.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Service;

@Service
public class SSOFilterPublisher implements ApplicationEventPublisherAware, MessageListener {
   @PostConstruct
   public void addListener() {
      cluster = Cluster.getInstance();
      cluster.addMessageListener(this);
   }

   @PreDestroy
   public void removeListener() {
      cluster.removeMessageListener(this);
      cluster = null;
   }

   @Override
   public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
      publisher = applicationEventPublisher;
   }

   public void changeSSOFilterType(SSOType filterType) {
      publishEvent(filterType);

      try {
         cluster.sendMessage(new ChangeSSOFilterMessage(filterType));
      }
      catch(Exception e) {
         LOG.warn("Failed to send SSO change message", e);
      }
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(!event.isLocal() && (event.getMessage() instanceof ChangeSSOFilterMessage)) {
         publisher.publishEvent(((ChangeSSOFilterMessage) event.getMessage()).getType());
      }
   }

   private void publishEvent(SSOType filterType) {
      publisher.publishEvent(new ChangeSSOFilterEvent(this, filterType));
   }

   private ApplicationEventPublisher publisher;
   private Cluster cluster;
   private static final Logger LOG = LoggerFactory.getLogger(SSOFilterPublisher.class);
}
