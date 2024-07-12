/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.log.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import inetsoft.util.log.LogManager;
import org.slf4j.Marker;

import java.io.IOException;

/**
 * Filter for logback that accepts or denies log events coming from Spring that are otherwise
 * not possible to handle through a typical Exception Handler mechanism.
 */
public class LogbackSpringExceptionFilter extends TurboFilter {
   @Override
   public FilterReply decide(Marker marker, Logger logger, Level level, String format,
                             Object[] params, Throwable t)
   {
      if(log == null) {
         log = LogManager.getInstance();
      }

      // don't call isDebugEnabled() if level is debug, otherwise it creates an
      // infinite recursion
      if(level != Level.DEBUG && log.isDebugEnabled(logger.getName())) {
         return FilterReply.NEUTRAL;
      }

      if(isDeliveryException(t) || isConnectionResetException(t)) {
         return FilterReply.DENY;
      }

      return FilterReply.NEUTRAL;
   }

   private boolean isDeliveryException(Throwable t) {
      return t != null && DELIVERY_EXCEPTION.equals(t.getClass().getName()) &&
         t.getMessage().endsWith("Session closed");
   }

   private boolean isConnectionResetException(Throwable t) {
      return t != null && EOF_EXCEPTION.equals(t.getClass().getName()) &&
         t.getCause() != null && t.getCause() instanceof IOException &&
         "Connection reset by peer".equals(t.getCause().getMessage());
   }

   private LogManager log = null;
   private static final String DELIVERY_EXCEPTION =
      "org.springframework.web.socket.sockjs.SockJsMessageDeliveryException";
   private static final String EOF_EXCEPTION = "org.eclipse.jetty.io.EofException";
}
