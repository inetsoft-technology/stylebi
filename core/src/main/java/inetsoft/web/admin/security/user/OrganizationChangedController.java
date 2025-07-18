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

package inetsoft.web.admin.security.user;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.web.admin.security.ChangeCurrentOrganizationEvent;
import inetsoft.web.admin.security.IdentityChangedMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Arrays;

@Controller
@Scope(value = "websocket", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class OrganizationChangedController implements MessageListener {
   public OrganizationChangedController(SimpMessagingTemplate messagingTemplate) {
      this.messagingTemplate = messagingTemplate;
   }

   @SubscribeMapping(CHANGE_TOPIC)
   public void subscribeToTopic(Principal principal) {
      this.principal = principal;
   }

   @PostConstruct
   public void addListeners() throws Exception {
      Cluster.getInstance().addMessageListener(this);
   }

   @PreDestroy
   public void removeListeners() throws Exception {
      Cluster.getInstance().removeMessageListener(this);
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(!(event.getMessage() instanceof IdentityChangedMessage message) ||
         message.getType() != Identity.ORGANIZATION || message.getOldIdentity() == null ||
         !OrganizationManager.getInstance().isSiteAdmin(principal))
      {
         return;
      }

      String currentOrgID = OrganizationManager.getInstance().getCurrentOrgID(principal);

      if(!Tool.equals(message.getOldIdentity().getOrgID(), currentOrgID)) {
         return;
      }

      String newOrgId = message.getIdentity() != null ? message.getIdentity().getOrgID() :
         Organization.getDefaultOrganizationID();
      ((XPrincipal) principal).setProperty("curr_org_id", newOrgId);
      String newOrgName = message.getIdentity() != null ? message.getIdentity().getName() : null;

      EditableAuthenticationProvider provider =
         SUtil.getEditableAuthenticationProvider(SecurityEngine.getSecurity().getSecurityProvider(),
            new IdentityID(newOrgName, newOrgId), Identity.ORGANIZATION);

      messagingTemplate
         .convertAndSendToUser(SUtil.getUserDestination(principal), CHANGE_TOPIC,
                               ChangeCurrentOrganizationEvent.builder()
                                  .provider(provider != null ? provider.getProviderName() : null)
                                  .currentOrganization(newOrgId)
                                  .build());
   }

   private final SimpMessagingTemplate messagingTemplate;
   private Principal principal;
   private static final String CHANGE_TOPIC = "/current-org-changed";
}
