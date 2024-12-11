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
package inetsoft.sree.internal;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XPrincipal;
import inetsoft.util.*;
import inetsoft.util.log.*;
import org.apache.commons.io.IOUtils;
import org.slf4j.*;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class used to read error and output streams of child processes to
 * prevent them from hanging.
 */
public class ProcessReader {
   /**
    * Default constructor.
    * @param proc - Process from which to read.
    */
   public ProcessReader(Process proc) {
      outReader = new ProcessStreamReader(proc.getInputStream());
      errReader = new ProcessStreamReader(proc.getErrorStream());
   }

   /**
    * Start reading from the process.
    */
   public void read() {
      outReader.start();
      errReader.start();
   }

   /**
    * Reader for the output stream.
    */
   private ProcessStreamReader outReader;

   /**
    * Reader for the error stream.
    */
   private ProcessStreamReader errReader;

   /**
    * Thread which actually reads from the process'
    * output or error stream.
    */
   private class ProcessStreamReader extends GroupedThread {
      /**
       * Default constructor.
       * @param stream - The output or error stream of the
       *                 process that this thread is to
       *                 read.
       */
      ProcessStreamReader(InputStream stream) {
         this.stream = stream;
      }

      /**
       * Starts the thread reading the stream.
       */
      @Override
      protected void doRun() {
         Pattern pattern = Pattern.compile(LOG_PATTERN);
         BufferedReader reader = null;

         TimedQueue.add(messageFlushWorker);

         try {
            reader = new BufferedReader(new InputStreamReader(stream));
            String line;
            boolean firstRecord = true;

            while((line = reader.readLine()) != null) {
               Matcher matcher = pattern.matcher(line);
               boolean logLine = false;

               if(matcher.matches()) {
                  try {
                     logLine = LogManager.getInstance().parseLevel(matcher.group(2)) != null;
                     logLine = true;
                  }
                  catch(Exception ignore) {
                  }
               }

               if(logLine) {
                  if(firstRecord) {
                     setPrincipal(SUtil.getPrincipal(new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()), null, false));
                     addRecord("External process");
                     firstRecord = false;
                  }

                  logMessage();

                  level = LogManager.getInstance().parseLevel(matcher.group(2));

                  if("i.performance".equalsIgnoreCase(matcher.group(5))) {
                     logger = LoggerFactory.getLogger(LogUtil.PERFORMANCE_LOGGER_NAME);
                  }
                  else {
                     logger = LoggerFactory.getLogger(matcher.group(5));
                  }

                  messageLock.lock();

                  try {
                     loadMDC(matcher.group(4));
                     messageBuffer = new StringWriter();
                     messageWriter = new PrintWriter(messageBuffer);
                     messageWriter.println(matcher.group(6));
                     lastMessageAppend = System.currentTimeMillis();
                  }
                  finally {
                     messageLock.unlock();
                  }
               }
               else {
                  messageLock.lock();

                  try {
                     if(messageBuffer == null) {
                        logLine(line);
                     }
                     else {
                        messageWriter.println(line);
                        lastMessageAppend = System.currentTimeMillis();
                     }
                  }
                  finally {
                     messageLock.unlock();
                  }
               }
            }

            logMessage();
            removeRecords();
         }
         catch(Exception exc) {
            LOG.error("Error while reading process output", exc);
         }
         finally {
            IOUtils.closeQuietly(reader);
            TimedQueue.remove(messageFlushWorker);
         }
      }

      private void loadMDC(String mdcString) {
         if(Tool.isEmptyString(mdcString)) {
            return;
         }

         String[] mdcs = mdcString.split(", ");

         if(mdcs == null || mdcs.length == 0) {
            return;
         }

         for(String mdc : mdcs) {
            if(mdc == null) {
               continue;
            }

            String[] keyValue = mdc.split("=", 2);

            if(keyValue == null || keyValue.length != 2 || keyValue[0] == null ||
               keyValue[1] == null)
            {
               continue;
            }

            this.mdcs.put(keyValue[0].trim(), keyValue[1].trim());
         }
      }

      private void logMessage() {
         logMessage(false);
      }

      private void logMessage(boolean checkTime) {
         messageLock.lock();

         try {
            if(messageBuffer != null && (!checkTime ||
               lastMessageAppend != null &&
               System.currentTimeMillis() > lastMessageAppend + 5000L))
            {
               messageWriter.close();

               if(mdcs != null && mdcs.size() > 0) {
                  for(String key : mdcs.keySet()) {
                     MDC.put(key, mdcs.get(key));
                  }
               }

               logLine(messageBuffer.toString().trim());
               messageBuffer = null;
               messageWriter = null;
               lastMessageAppend = null;

               if(mdcs != null && mdcs.size() > 0) {
                  for(String key : mdcs.keySet()) {
                     MDC.remove(key);
                  }
               }

               mdcs.clear();
            }
         }
         finally {
            messageLock.unlock();
         }
      }

      private void logLine(String line) {
         switch(level) {
         case DEBUG:
            logger.debug(line);
            break;
         case INFO:
            logger.info(line);
            break;
         case WARN:
            logger.warn(line);
            break;
         case ERROR:
            logger.error(line);
            break;
         }
      }

      /**
       * The process' output or error stream.
       */
      InputStream stream;

      private Logger logger = LOG;
      private LogLevel level = LogLevel.INFO;
      private StringWriter messageBuffer = null;
      private PrintWriter messageWriter = null;
      private Long lastMessageAppend = null;
      private Lock messageLock = new ReentrantLock();
      private Map<String, String> mdcs = new HashMap<>();

      /**
       * Flushes any content in the message buffer if it has not been written to
       * in over 5 seconds. This prevents a message for sitting in the buffer
       * for a long time until the next message is printed.
       */
      private TimedQueue.TimedRunnable messageFlushWorker =
         new TimedQueue.TimedRunnable(5000L)
      {
         @Override
         public void run() {
            logMessage(true);
         }

         @Override
         public boolean isRecurring() {
            return true;
         }
      };
   }

   private static final String LOG_PATTERN =
      "^([0-9\\- :,]+) +([A-Z]+) +([0-9.]+) +\\[([\\s\\S]*)\\] +([^:]+): (.+)$";
   private static final Logger LOG =
      LoggerFactory.getLogger(ProcessReader.class);
}
