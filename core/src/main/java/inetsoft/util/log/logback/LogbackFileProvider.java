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

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.rolling.RollingFileAppender;
import inetsoft.util.FileSystemService;
import inetsoft.util.log.LogFileProvider;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogbackFileProvider implements LogFileProvider {
   static final String BASE_FILE_PATTERN = "%s-%s%s";
   static final String LOG_FILE_PATTERN = "%s-%s-%%i%s";

   @Override
   public Pattern getLogFilePattern(String baseFileName) {
      String baseName = FileSystemService.getInstance()
         .getPath(baseFileName).getFileName().toString();
      int index = baseName.lastIndexOf('.');
      String prefix;
      String suffix;

      if(index < 0) {
         prefix = baseName;
         suffix = "";
      }
      else {
         prefix = baseName.substring(0, index);
         suffix = "\\." + baseName.substring(index + 1);
      }

      return Pattern.compile("^" + prefix + "-(.+)(-(\\d+))?" + suffix + "$");
   }

   @Override
   public Comparator<Path> getComparator(String baseFileName) {
      Pattern pattern = getLogFilePattern(baseFileName);
      return new FileComparator(pattern);
   }

   @Override
   public boolean isRotateSupported(String baseFileName, String fileName) {
      Pattern pattern = getLogFilePattern(baseFileName);
      Matcher matcher = pattern.matcher(fileName);
      return matcher.matches() && matcher.group(2) == null;
   }

   @Override
   public void rotateLogFile() {
      org.slf4j.Logger logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
      Logger logbackLogger;

      if(logger instanceof Logger) {
         logbackLogger = (Logger) logger;
      }
      else {
         LoggerFactory.getLogger(getClass()).warn(
            "Cannot rotate log, unsupported logger class {}", logger.getClass().getName());
         return;
      }

      AsyncAppender parent =
         (AsyncAppender) logbackLogger.getAppender(LogbackInitializer.ASYNC_SREE_LOG);
      RollingFileAppender appender =
         (RollingFileAppender) parent.getAppender(LogbackInitializer.SREE_LOG);
      appender.rollover();
   }

   private static final class FileComparator implements Comparator<Path> {
      FileComparator(Pattern pattern) {
         this.pattern = pattern;
      }

      @Override
      public int compare(Path f1, Path f2) {
         LogFileInfo info1 = null;
         LogFileInfo info2 = null;

         Matcher matcher;

         String n1 = f1.getFileName().toString();
         String n2 = f2.getFileName().toString();

         if((matcher = pattern.matcher(n1)).matches()) {
            info1 = new LogFileInfo(matcher);
         }

         if((matcher = pattern.matcher(n2)).matches()) {
            info2 = new LogFileInfo(matcher);
         }

         if(info1 == null || info2 == null) {
            return Comparator.<Path>naturalOrder().compare(f1, f2);
         }

         return Comparator.comparing(LogFileInfo::getAddress)
            .thenComparing(LogFileInfo::getIndex, Comparator.nullsFirst(Comparator.reverseOrder()))
            .compare(info1, info2);
      }

      private final Pattern pattern;
   }

   private static final class LogFileInfo {
      LogFileInfo(Matcher matcher) {
         address = matcher.group(1);

         if(matcher.group(2) != null) {
            this.index = Integer.parseInt(matcher.group(3));
         }
         else {
            this.index = -1;
         }
      }

      public String getAddress() {
         return address;
      }

      public int getIndex() {
         return index;
      }

      private final String address;
      private final int index;
   }
}
