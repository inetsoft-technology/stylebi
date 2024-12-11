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

package inetsoft.web.admin.content.plugins;

import inetsoft.sree.internal.SUtil;
import inetsoft.util.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.Principal;
import java.util.concurrent.TimeUnit;

@Controller
@Scope(value = "websocket", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class PluginChangeController {
   @Autowired
   public PluginChangeController(SimpMessagingTemplate messagingTemplate) {
      this.debouncer = new DefaultDebouncer<>();
      this.messagingTemplate = messagingTemplate;
   }

   @PostConstruct
   public void addListeners() {
      Plugins.getInstance().addActionListener(this.pluginListener);
   }

   @PreDestroy
   public void removeListeners() {
      Plugins.getInstance().removeActionListener(this.pluginListener);
   }

   @SubscribeMapping(CHANGE_TOPIC)
   public void subscribeToTopic(Principal principal) throws Exception {
      this.principal = principal;
   }

   private void pluginChanged(ActionEvent actionEvent) {
      debouncer.debounce("change", 1L, TimeUnit.SECONDS, this::sendChangeMessage);
   }

   private void sendChangeMessage() {
      messagingTemplate
         .convertAndSendToUser(SUtil.getUserDestination(principal), CHANGE_TOPIC, "");
   }

   private Principal principal;
   private final Debouncer<String> debouncer;
   private final SimpMessagingTemplate messagingTemplate;
   private static final String CHANGE_TOPIC = "/em-plugin-changed";
   private final ActionListener pluginListener = this::pluginChanged;
}
