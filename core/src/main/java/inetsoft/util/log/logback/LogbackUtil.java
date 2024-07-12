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

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.*;
import ch.qos.logback.core.Appender;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.util.log.LogLevel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Function;

public class LogbackUtil {
   private LogbackUtil() {
   }

   public static LogLevel getLogLevel(Level level) {
      if(level.toInt() <= Level.DEBUG.toInt()) {
         return LogLevel.DEBUG;
      }
      else if(level.toInt() <= Level.INFO.toInt()) {
         return LogLevel.INFO;
      }
      else if(level.toInt() <= Level.WARN.toInt()) {
         return LogLevel.WARN;
      }
      else {
         return LogLevel.ERROR;
      }
   }

   public static Throwable getThrowable(Object eventObject) {
      Throwable thrown = null;

      if(eventObject instanceof ILoggingEvent) {
         IThrowableProxy proxy = ((ILoggingEvent) eventObject).getThrowableProxy();

         if(proxy instanceof ThrowableProxy) {
            thrown = ((ThrowableProxy) proxy).getThrowable();
         }
      }

      return thrown;
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   public static Appender<ILoggingEvent> createForwardAppender(String clientHost) throws Exception {
      Class<?> cls = Class.forName("inetsoft.enterprise.log.fluentd.ForwardAppender");
      Constructor<?> ctr = cls.getConstructor(Function.class, String.class);
      Function<String, String> getFluentdTag = LogbackUtil::getFluentdTag;
      return (Appender) ctr.newInstance(getFluentdTag, clientHost);
   }

   private static String getFluentdTag(String loggerName) {
      if("inetsoft_audit".equals(loggerName)) {
         return "inetsoft.audit";
      }
      else {
         return "inetsoft.log";
      }
   }

   public static void resetLog() throws Exception {
      if(!LicenseManager.getInstance().isEnterprise()) {
         return;
      }

      Class forwardServiceClass = Class.forName("inetsoft.enterprise.log.fluentd.ForwardService");
      Object forwardService = forwardServiceClass.getMethod("getInstance").invoke(null);
      Method reset = forwardServiceClass.getMethod("reset");
      reset.invoke(forwardService);
   }
}
