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
package inetsoft.util.log.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

/**
 * Filters out Ignite BinaryContext warnings about classes that cannot use
 * BinaryMarshaller. These warnings are noise since OptimizedMarshaller works fine.
 */
public class IgniteBinaryWarningFilter extends TurboFilter {
   private static final String BINARY_CONTEXT_LOGGER =
      "org.apache.ignite.internal.binary.BinaryContext";
   private static final String FILTER_PATTERN = "OptimizedMarshaller will be used instead";

   @Override
   public FilterReply decide(Marker marker, Logger logger, Level level,
                             String format, Object[] params, Throwable t)
   {
      if(level != Level.WARN || logger == null ||
         !BINARY_CONTEXT_LOGGER.equals(logger.getName()))
      {
         return FilterReply.NEUTRAL;
      }

      // Check format string
      if(format != null && format.contains(FILTER_PATTERN)) {
         return FilterReply.DENY;
      }

      // Check params in case message is passed as parameter
      if(params != null) {
         for(Object param : params) {
            if(param != null && param.toString().contains(FILTER_PATTERN)) {
               return FilterReply.DENY;
            }
         }
      }

      return FilterReply.NEUTRAL;
   }
}
