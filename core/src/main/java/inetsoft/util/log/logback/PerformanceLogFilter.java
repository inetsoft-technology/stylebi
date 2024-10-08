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
package inetsoft.util.log.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import inetsoft.util.log.LogUtil;

public class PerformanceLogFilter<T extends ILoggingEvent> extends Filter<T> {
   public PerformanceLogFilter(boolean performance) {
      this.performance = performance;
   }

   @Override
   public FilterReply decide(T event) {
      if(performance) {
         return !LogUtil.PERFORMANCE_LOGGER_NAME.equals(event.getLoggerName()) ?
            FilterReply.DENY : FilterReply.NEUTRAL;
      }
      else {
         return LogUtil.PERFORMANCE_LOGGER_NAME.equals(event.getLoggerName()) ?
            FilterReply.DENY : FilterReply.NEUTRAL;
      }
   }

   private boolean performance;
}
