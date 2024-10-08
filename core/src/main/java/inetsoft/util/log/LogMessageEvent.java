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
package inetsoft.util.log;

import java.util.EventObject;

public class LogMessageEvent extends EventObject {
   public LogMessageEvent(Object source, String category, String message, LogLevel level,
                          Throwable thrown)
   {
      super(source);
      this.category = category;
      this.message = message;
      this.level = level;
      this.thrown = thrown;
   }

   public String getCategory() {
      return category;
   }

   public String getMessage() {
      return message;
   }

   public LogLevel getLevel() {
      return level;
   }

   public Throwable getThrown() {
      return thrown;
   }

   private final String category;
   private final String message;
   private final LogLevel level;
   private final Throwable thrown;
}
