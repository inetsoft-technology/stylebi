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

import inetsoft.util.log.LogLevel;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

public class LogLevelsChangedEvent extends ApplicationEvent {
   public LogLevelsChangedEvent(Object source, Map<String, LogLevel> levels) {
      super(source);
      this.levels = levels;
   }

   public Map<String, LogLevel> getLevels() {
      return levels;
   }

   private final Map<String, LogLevel> levels;
}
