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
package inetsoft.web.viewsheet.service;

import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

/**
 * Class that handles resolving <tt>CommandDispatcher</tt> arguments on message handler
 * methods.
 *
 * @since 12.3
 */
public class CommandDispatcherArgumentResolver implements HandlerMethodArgumentResolver {
   @Override
   public boolean supportsParameter(MethodParameter parameter) {
      return CommandDispatcher.class.isAssignableFrom(parameter.getParameterType());
   }

   @Override
   public Object resolveArgument(MethodParameter parameter, Message<?> message) {
      MessageAttributes attributes = MessageContextHolder.currentMessageAttributes();
      StompHeaderAccessor headerAccessor = attributes.getHeaderAccessor();
      return new CommandDispatcher(headerAccessor, messagingTemplate, sessionRepository);
   }

   @Autowired
   public void setMessagingTemplate(
      @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") SimpMessagingTemplate messagingTemplate)
   {
      this.messagingTemplate = messagingTemplate;
   }

   @Autowired
   public void setSessionRepository(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
      this.sessionRepository = sessionRepository;
   }

   private SimpMessagingTemplate messagingTemplate;
   private FindByIndexNameSessionRepository<? extends Session> sessionRepository;
}
