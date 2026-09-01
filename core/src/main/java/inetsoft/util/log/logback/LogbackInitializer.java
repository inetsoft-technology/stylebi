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

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.*;
import ch.qos.logback.core.util.FileSize;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.util.*;
import inetsoft.util.log.*;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;

/**
 * Implementation of {@link LogInitializer} for Logback.
 */
public class LogbackInitializer implements LogInitializer {
   @Override
   public void initializeForStartup() {
      LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
      context.reset();
      context.addTurboFilter(new IgniteBinaryWarningFilter());

      Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
      rootLogger.setLevel(Level.ERROR);
      rootLogger.addAppender(createConsoleAppender(context, true));

      context.getLogger("inetsoft.scheduler_test").setLevel(Level.OFF);
      context.getLogger("mv_debug").setLevel(Level.OFF);
      context.getLogger("inetsoft_swap_data").setLevel(Level.OFF);
      context.getLogger(SUtil.MAC_LOG_NAME).setLevel(Level.OFF);
      context.getLogger(LogUtil.PERFORMANCE_LOGGER_NAME).setLevel(Level.OFF);
      context.getLogger("liquibase").setLevel(Level.WARN);
      context.getLogger("org.apache.ignite").setLevel(Level.WARN);

      context.getLogger(DataSpace.class).setLevel(Level.INFO);
      context.getLogger("inetsoft.util.db").setLevel(Level.INFO);
      context.getLogger("inetsoft.util.Plugins").setLevel(Level.INFO);
      context.getLogger("inetsoft.util.Drivers").setLevel(Level.INFO);
      context.getLogger("inetsoft.shell.setup").setLevel(Level.INFO);
      context.getLogger("inetsoft.setup").setLevel(Level.INFO);
      context.getLogger("inetsoft.enterprise.setup").setLevel(Level.INFO);
   }

   @Override
   public void initialize(String logFile, String logFileDiscriminator, boolean console,
                          long maxFileSize, int maxFileCount, boolean performance)
   {
      LogManager.getInstance().setLogFileProvider(new LogbackFileProvider());
      Path logFilePath = FileSystemService.getInstance().getPath(logFile);

      LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
      context.reset();
      context.putProperty("LOCAL_IP_ADDR", logFileDiscriminator != null ? logFileDiscriminator : Tool.getIP());
      context.addTurboFilter(new IgniteBinaryWarningFilter());
      context.addTurboFilter(new LogbackSpringExceptionFilter());
      context.addTurboFilter(new LogbackContextFilter());

      Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
      rootLogger.setLevel(Level.ERROR);
      // isFluentdEnabled(), not a bare comparison against log.provider: the forwarder is
      // enterprise-only, so on a community build the selection cannot take effect and the
      // reflective load below would only throw. Reporting the failure is handled after the
      // appenders are attached -- see the end of this method.
      boolean useFluentd = LogbackUtil.isFluentdEnabled();
      boolean fluentdUnavailable =
         !useFluentd && LogbackUtil.FLUENTD_PROVIDER.equals(SreeEnv.getProperty("log.provider"));
      Exception fluentdFailure = null;
      AsyncAppender appender = null;

      if(useFluentd) {
         try {
            appender = createFluentdAppender(context, logFileDiscriminator);
            appender.addFilter(new PerformanceLogFilter<>(false));
         }
         catch(Exception e) {
            fluentdFailure = e;
            // keep the stderr print: it is the only report available if anything below fails
            // before the appenders are attached
            System.err.println("Failed to create Fluentd appender: " + e);
         }
      }

      if(appender == null) {
         appender = createFileAppender(
            context, logFilePath.toString(), logFileDiscriminator, maxFileSize, maxFileCount);
         appender.addFilter(new PerformanceLogFilter<>(false));
         appender.addFilter(new AuditLogFilter(true));
      }

      rootLogger.addAppender(appender);

      if(performance) {
         String performanceLogFile = getPerformanceLogFile(logFilePath);

         if(performanceLogFile != null) {
            AsyncAppender fileAppender = createFileAppender(
               context, performanceLogFile, logFileDiscriminator, maxFileSize, maxFileCount);
            fileAppender.addFilter(new PerformanceLogFilter<>(true));
            fileAppender.addFilter(new AuditLogFilter(true));
            rootLogger.addAppender(fileAppender);
         }
      }

      rootLogger.addAppender(createListenerAppender(context));
      rootLogger.addAppender(createTraceAppender(context));

      if(console) {
         rootLogger.addAppender(createConsoleAppender(context));
      }

      reportFluentdFallback(fluentdUnavailable, fluentdFailure, logFilePath);
   }

   /**
    * Reports that log forwarding was requested but is not happening.
    *
    * Called at the end of {@link #initialize} on purpose: only once every appender is attached to
    * the root logger can a log record reach the log file. The stderr print in {@code initialize}
    * happens while logging is still being initialised, so on its own it lands nowhere an operator
    * looks -- not in the log file, and not in Enterprise Manager.
    *
    * ERROR, not WARN, for two reasons. {@code initialize} has just called {@code context.reset()}
    * and set the root level to ERROR, and the per-logger levels from {@code log.detail.level} and
    * {@code log.level.*} are only applied afterwards (see {@code PropertiesEngine.initLogging}) --
    * so anything below ERROR is dropped here on every call. And the condition is an error: the
    * operator configured a log pipeline that is receiving nothing.
    *
    * {@code initialize} re-runs on every {@code SreeEnv.reloadLoggingFramework()}, which
    * {@code PropertiesEngine.initLogging} triggers once per {@code log.*} property at startup, so
    * the report is emitted only when the state changes rather than once per reload.
    */
   private void reportFluentdFallback(boolean unavailable, Exception failure, Path logFilePath) {
      if(!unavailable && failure == null) {
         fluentdFallbackReported = false;
         return;
      }

      if(fluentdFallbackReported) {
         return;
      }

      fluentdFallbackReported = true;

      if(unavailable) {
         LoggerFactory.getLogger(LogbackInitializer.class).error(
            "log.provider is set to \"{}\", but log forwarding is only available in the " +
            "enterprise edition. Log records are being written to {} instead.",
            LogbackUtil.FLUENTD_PROVIDER, logFilePath);
      }
      else {
         LoggerFactory.getLogger(LogbackInitializer.class).error(
            "Failed to create the Fluentd appender. Log records are being written to {} instead.",
            logFilePath, failure);
      }
   }

   /**
    * Get the performance log file path for base log file.format is logFileBaseName-performance
    * @param baseLogFilePath base log file path.
    * @return logFileBaseName-performance
    */
   private String getPerformanceLogFile(Path baseLogFilePath) {
      if(baseLogFilePath == null) {
         return null;
      }

      Path parentPath = baseLogFilePath.getParent();

      if(parentPath != null) {
         String fileName = baseLogFilePath.toString().substring(parentPath.toString().length());
         int index = fileName.lastIndexOf(".");

         if(index < 0) {
            return parentPath + File.separator + fileName + "-performance";
         }
         else if(index > 0) {
            String suffix = fileName.substring(index);
            String name = fileName.substring(0, index);

            return parentPath + name + "-performance" + suffix;
         }
      }

      return null;
   }

   @Override
   public void reset() {
      try {
         LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
         context.reset();
         // use basic console logging when not configured
         Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
         rootLogger.setLevel(Level.ERROR);
         rootLogger.addAppender(createConsoleAppender(context));
      }
      catch(ShutdownException ignore) {
      }
   }

   private PatternLayoutEncoder createEncoder(LoggerContext context) {
      return createEncoder(context, false);
   }

   private PatternLayoutEncoder createEncoder(LoggerContext context, boolean startup) {
      PatternLayoutEncoder encoder = new PatternLayoutEncoder();
      encoder.setContext(context);
      String pattern = startup
         ? "%d %level %property{LOCAL_IP_ADDR} [%mdc] %c{1}: %message %n%ex" // don't look up, Spring not initialized yet
         : SreeEnv.getProperty("log.message.pattern");
      encoder.setPattern(pattern);
      encoder.start();
      return encoder;
   }

   private AsyncAppender createConsoleAppender(LoggerContext context) {
      return createConsoleAppender(context, false);
   }

   private AsyncAppender createConsoleAppender(LoggerContext context, boolean startup) {
      ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
      appender.setName("STDOUT");
      appender.setContext(context);
      appender.setEncoder(createEncoder(context, startup));
      appender.addFilter(new AuditLogFilter(true));
      appender.start();
      return createAsyncAppender("ASYNC_STDOUT", appender, context);
   }

   private AsyncAppender createTraceAppender(LoggerContext context) {
      LogbackTraceAppender<ILoggingEvent> appender = new LogbackTraceAppender<>();
      appender.setName("TRACE");
      appender.setContext(context);
      appender.addFilter(new AuditLogFilter(true));
      appender.start();
      return createAsyncAppender("ASYNC_STDOUT", appender, context);
   }

   private SizeBasedTriggeringPolicy<ILoggingEvent> createTriggeringPolicy(LoggerContext context,
                                                                           long maxFileSize)
   {
      FileSize fileSize = FileSize.valueOf(String.format("%dKB", maxFileSize));
      SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new SizeBasedTriggeringPolicy<>();
      triggeringPolicy.setContext(context);
      triggeringPolicy.setMaxFileSize(fileSize);
      triggeringPolicy.start();
      return triggeringPolicy;
   }

   private Tuple2<String, String> getPrefixAndSuffix(String logFile) {
      int index = logFile.lastIndexOf('.');
      String prefix;
      String suffix;

      if(index < 0) {
         prefix = logFile;
         suffix = "";
      }
      else {
         prefix = logFile.substring(0, index);
         suffix = logFile.substring(index);
      }

      return new Tuple2<>(prefix, suffix);
   }

   private FixedWindowRollingPolicy createRollingPolicy(LoggerContext context,
                                                        Tuple2<String, String> prefixAndSuffix,
                                                        String logFileDiscriminator,
                                                        int maxFileCount)
   {
      String fileNamePattern = String.format(
         LogbackFileProvider.LOG_FILE_PATTERN, prefixAndSuffix.getFirst(),
         logFileDiscriminator == null ? Tool.getIP() : logFileDiscriminator, prefixAndSuffix.getSecond());

      FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
      rollingPolicy.setContext(context);
      rollingPolicy.setFileNamePattern(fileNamePattern);
      rollingPolicy.setMinIndex(1);
      rollingPolicy.setMaxIndex(maxFileCount);

      return rollingPolicy;
   }

   private AsyncAppender createFileAppender(LoggerContext context, String logFile,
                                            String logFileDiscriminator, long maxFileSize,
                                            int maxFileCount)
   {
      SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy =
         createTriggeringPolicy(context, maxFileSize);
      Tuple2<String, String> prefixAndSuffix = getPrefixAndSuffix(logFile);
      FixedWindowRollingPolicy rollingPolicy =
         createRollingPolicy(context, prefixAndSuffix, logFileDiscriminator, maxFileCount);

      String fileName = String.format(
         LogbackFileProvider.BASE_FILE_PATTERN, prefixAndSuffix.getFirst(),
         logFileDiscriminator == null ? Tool.getIP() : logFileDiscriminator, prefixAndSuffix.getSecond());

      RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
      appender.setName(SREE_LOG);
      appender.setContext(context);
      appender.setFile(fileName);
      appender.setTriggeringPolicy(triggeringPolicy);
      appender.setRollingPolicy(rollingPolicy);
      appender.setEncoder(createEncoder(context));

      rollingPolicy.setParent(appender);
      rollingPolicy.start();

      appender.start();

      return createAsyncAppender(ASYNC_SREE_LOG, appender, context);
   }

   private AsyncAppender createListenerAppender(LoggerContext context) {
      LogbackListenerAppender<ILoggingEvent> appender = new LogbackListenerAppender<>();
      appender.setName("LISTENERS");
      appender.setContext(context);
      appender.setEncoder(createEncoder(context));
      appender.addFilter(new AuditLogFilter(true));
      appender.start();
      return createAsyncAppender("ASYNC_LISTENERS", appender, context);
   }

   private AsyncAppender createFluentdAppender(LoggerContext context, String logFileDiscriminator) throws Exception {
      String clientHost = logFileDiscriminator == null ? Tool.getIP() : logFileDiscriminator;
      Appender<ILoggingEvent> appender = LogbackUtil.createForwardAppender(clientHost);
      appender.setName("FLUENTD");
      appender.setContext(context);
      appender.start();
      return createAsyncAppender("ASYNC_FLUENTD", appender, context);
   }

   private AsyncAppender createAsyncAppender(String name, Appender<ILoggingEvent> ref,
                                             LoggerContext context)
   {
      AsyncAppender appender = new AsyncAppender();
      appender.setName(name);
      appender.setContext(context);
      appender.setQueueSize(1000);
      appender.addAppender(ref);
      appender.start();
      return appender;
   }

   private static volatile boolean fluentdFallbackReported;

   static final String SREE_LOG = "SREE_LOG";
   static final String ASYNC_SREE_LOG = "ASYNC_SREE_LOG";
}
