/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.messaging;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

/**
 * Message-backed {@link Scope} implementation.
 */
public class MessageScope implements Scope {
   @Override
   public Object get(String name, ObjectFactory<?> objectFactory) {
      MessageAttributes attributes = MessageContextHolder.currentMessageAttributes();
      Object scopedObject = attributes.getAttribute(name);

      if(scopedObject == null) {
         scopedObject = objectFactory.getObject();
         attributes.setAttribute(name, scopedObject);
      }

      return scopedObject;
   }

   @Override
   public Object remove(String name) {
      return MessageContextHolder.currentMessageAttributes().removeAttribute(name);
   }

   @Override
   public void registerDestructionCallback(String name, Runnable callback) {
      MessageContextHolder.currentMessageAttributes()
         .registerDestructionCallback(name, callback);
   }

   @Override
   public Object resolveContextualObject(String key) {
      return MessageContextHolder.currentMessageAttributes().resolveReference(key);
   }

   @Override
   public String getConversationId() {
      return null;
   }
}
