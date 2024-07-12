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
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.status.ErrorStatus;
import inetsoft.util.log.*;

import static inetsoft.util.log.logback.LogbackUtil.getLogLevel;

/**
 * Appender for Logback that fires log events back to the application.
 */
public class LogbackListenerAppender<E> extends UnsynchronizedAppenderBase<E> {
   @Override
   public void start() {
      if(this.encoder == null) {
         addStatus(new ErrorStatus("No layout set for the encoder named \"" + name + "\".", this));
      }
      else {
         super.start();
      }
   }

   @Override
   protected void append(E eventObject) {
      Throwable thrown = LogbackUtil.getThrowable(eventObject);
      LogManager.getInstance().fireLogMessageEvent(() -> this.createEvent(eventObject), thrown != null);
   }

   public Encoder<E> getEncoder() {
      return encoder;
   }

   public void setEncoder(Encoder<E> encoder) {
      this.encoder = encoder;
   }

   private LogMessageEvent createEvent(E eventObject) {
      return new LogMessageEvent(
         this, getLogger(eventObject), getMessage(eventObject), getLevel(eventObject),
         LogbackUtil.getThrowable(eventObject));
   }

   private String getLogger(E eventObject) {
      String logger;

      if(eventObject instanceof ILoggingEvent) {
         logger = ((ILoggingEvent) eventObject).getLoggerName();
      }
      else {
         logger = "";
      }

      return logger;
   }

   private LogLevel getLevel(E eventObject) {
      LogLevel level;

      if(eventObject instanceof ILoggingEvent) {
         level = getLogLevel(((ILoggingEvent) eventObject).getLevel());
      }
      else {
         level = LogLevel.INFO;
      }

      return level;
   }

   private String getMessage(E eventObject) {
      return new String(encoder.encode(eventObject));
   }

   private Encoder<E> encoder;
}
