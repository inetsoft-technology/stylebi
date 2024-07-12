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

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

import java.util.Objects;

public class AuditLogFilter extends Filter<ILoggingEvent> {
   public AuditLogFilter(boolean deny) {
      this.deny = deny;
   }

   @Override
   public FilterReply decide(ILoggingEvent event) {
      boolean audit = Objects.equals(event.getLoggerName(), "inetsoft_audit");

      if(deny) {
         return audit ? FilterReply.DENY : FilterReply.NEUTRAL;
      }
      else {
         return audit ? FilterReply.NEUTRAL : FilterReply.DENY;
      }
   }

   private final boolean deny;
}
