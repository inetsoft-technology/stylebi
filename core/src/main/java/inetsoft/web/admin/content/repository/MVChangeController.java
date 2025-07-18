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
package inetsoft.web.admin.content.repository;

import inetsoft.mv.MVManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.security.Principal;
import java.util.concurrent.TimeUnit;

@Controller
@Scope(value = "websocket", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class MVChangeController implements MessageListener {
   @Autowired
   public MVChangeController(SimpMessagingTemplate messagingTemplate)
   {
      this.messagingTemplate = messagingTemplate;
      this.debouncer = new DefaultDebouncer<>();
   }

   @PostConstruct
   public void addListeners() throws Exception {
      MVManager.getManager().addPropertyChangeListener(this.mvListener);
      Cluster.getInstance().addMessageListener(this);
   }

   @PreDestroy
   public void removeListeners() throws Exception {
      MVManager.getManager().removePropertyChangeListener(this.mvListener);
      Cluster.getInstance().removeMessageListener(this);
      debouncer.close();
   }

   @SubscribeMapping(CHANGE_TOPIC)
   public void subscribeToTopic(Principal principal) throws Exception {
      this.principal = principal;
   }

   private void mvPropertyChanged(PropertyChangeEvent event) {
      if(MVManager.MV_CHANGE_EVENT.equals(event.getPropertyName())) {
         String orgId = MVManager.getOrgIdFromEventSource(event.getSource());

         if(orgId != null &&
            !Tool.equals(orgId, OrganizationManager.getInstance().getCurrentOrgID(principal)))
         {
            return;
         }

         scheduleChangeMessage();
      }
   }

   private void scheduleChangeMessage() {
      debouncer.debounce("change", 1L, TimeUnit.SECONDS, this::sendChangeMessage);
   }

   private void sendChangeMessage() {
      messagingTemplate
         .convertAndSendToUser(SUtil.getUserDestination(principal), CHANGE_TOPIC, "");
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof SimpleMessage simpleMessage) {
         if(MVManager.MV_CHANGE_EVENT.equals(simpleMessage.getMessage())) {
            scheduleChangeMessage();
         }
      }
   }

   private Principal principal;

   private final SimpMessagingTemplate messagingTemplate;
   private final Debouncer<String> debouncer;
   private final PropertyChangeListener mvListener = this::mvPropertyChanged;

   private static final String CHANGE_TOPIC = "/em-mv-changed";
}
