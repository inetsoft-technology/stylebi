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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import inetsoft.util.log.LogManager;
import org.slf4j.Marker;

import java.util.Objects;

import static inetsoft.util.log.logback.LogbackUtil.getLogLevel;

/**
 * Filter for logback that accepts log events based on the context levels configured in
 * {@link LogManager}.
 */
public class LogbackContextFilter extends TurboFilter {
   @Override
   public FilterReply decide(Marker marker, Logger logger, Level level, String format,
                             Object[] params, Throwable t)
   {
      if(log == null) {
         log = LogManager.getInstance();
      }

      if(Objects.equals(logger.getName(), ologger) && Objects.equals(level, olevel)) {
         return odecide;
      }

      FilterReply reply = log.isLevelEnabled(logger.getName(), getLogLevel(level))
         ? FilterReply.ACCEPT : FilterReply.NEUTRAL;

      this.ologger = logger.getName();
      this.olevel = level;
      this.odecide = reply;
      return reply;
   }

   private LogManager log = null;
   private String ologger;
   private Level olevel;
   private FilterReply odecide;
}
