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

package inetsoft.sree;

import inetsoft.util.log.LogContext;
import inetsoft.util.log.LogLevel;
import org.springframework.context.ApplicationEvent;

public class LogLevelChangedEvent extends ApplicationEvent {
   public LogLevelChangedEvent(Object source, LogContext context, String name, LogLevel level) {
      super(source);
      this.context = context;
      this.name = name;
      this.level = level;
   }

   public LogLevelChangedEvent(Object source, String name, LogLevel level) {
      this(source, null, name, level);
   }

   public LogLevelChangedEvent(Object source, LogLevel level) {
      this(source, null, null, level);
   }

   public LogContext getContext() {
      return context;
   }

   public String getName() {
      return name;
   }

   public LogLevel getLevel() {
      return level;
   }

   private final LogContext context;
   private final String name;
   private final LogLevel level;
}
