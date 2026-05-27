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

import org.springframework.session.MapSession;

import javax.cache.processor.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class SessionUpdateEntryProcessor implements EntryProcessor<String, MapSession, Object> {
   @SuppressWarnings("unchecked")
   @Override
   public Object process(MutableEntry<String, MapSession> entry, Object... arguments)
      throws EntryProcessorException
   {
      if(arguments.length != 2) {
         throw new IllegalArgumentException("Expected 2 arguments, got " + arguments.length);
      }

      Instant lastAccessTime = (Instant) arguments[0];
      Duration maxInactiveInterval = (Duration) arguments[1];

      MapSession value = entry.getValue();

      if(value == null) {
         return false;
      }

      if(lastAccessTime != null) {
         value.setLastAccessedTime(lastAccessTime);
      }

      if(maxInactiveInterval != null) {
         value.setMaxInactiveInterval(maxInactiveInterval);
      }

      entry.setValue(value);
      return true;
   }
}
