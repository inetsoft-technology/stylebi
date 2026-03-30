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
package inetsoft.web.messaging;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;

/**
 * Bean factory post-processor that registers the message and websocket scopes.
 * Implements {@link Ordered} with {@link Ordered#LOWEST_PRECEDENCE} to guarantee this runs after
 * Spring's {@code WebSocketScopeConfigurer} (which uses {@code LOWEST_PRECEDENCE - 1}), allowing
 * the websocket scope to be replaced with {@link SafeSimpSessionScope}.
 */
public class MessageScopePostProcessor implements BeanFactoryPostProcessor, Ordered {
   @Override
   public void postProcessBeanFactory(ConfigurableListableBeanFactory factory)
      throws BeansException
   {
      factory.registerScope("message", new MessageScope());
      factory.registerScope("websocket", new SafeSimpSessionScope());
   }

   @Override
   public int getOrder() {
      return Ordered.LOWEST_PRECEDENCE;
   }
}
