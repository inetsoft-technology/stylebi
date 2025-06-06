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
package inetsoft.util.log;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.*;
import inetsoft.util.*;
import org.apache.commons.io.IOUtils;
import org.slf4j.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utility methods for logging.
 *
 * @author InetSoft Technology
 * @since 10.1
 */
public final class LogManager implements AutoCloseable, MessageListener {
   /**
    * Creates a new instance of <tt>LogManager</tt>.
    */
   public LogManager() {
      for(LogContext context : LogContext.values()) {
         contextLevels.put(context, new ConcurrentHashMap<>());
      }
   }

   public static LogManager getInstance() {
      return SingletonManager.getInstance(LogManager.class);
   }

   public static void initializeForStartup() {
      useInitializer(LogInitializer::initializeForStartup);
   }

   public void initialize(String logFile, String logFileDiscriminator, boolean console,
                          long maxFileSize, int maxFileCount, boolean performance)
   {
      useInitializer(i -> i.initialize(
         logFile, logFileDiscriminator, console, maxFileSize, maxFileCount, performance));
      Cluster.getInstance().addMessageListener(this);
   }

   @Override
   public void close()  {
      useInitializer(LogInitializer::reset);
      Cluster.getInstance().removeMessageListener(this);
   }

   private static void useInitializer(Consumer<LogInitializer> fn) {
      String factory = LoggerFactory.getILoggerFactory().getClass().getName();
      String initializerClass = null;

      switch(factory) {
      case "ch.qos.logback.classic.LoggerContext":
         initializerClass = "inetsoft.util.log.logback.LogbackInitializer";
         break;
      }

      if(initializerClass != null) {
         try {
            LogInitializer initializer = (LogInitializer) Class.forName(initializerClass)
               .getConstructor().newInstance();
            fn.accept(initializer);
         }
         catch(Exception e) {
            // logging is not initialized yet
            System.err.println("Failed to initialize logging framework");
            e.printStackTrace();//NOSONAR
         }
      }
   }

   public void reset() {
      useInitializer(LogInitializer::reset);
   }

   public LogLevel parseLevel(String level) {
      LogLevel result = Arrays.stream(LogLevel.values())
         .filter(l -> l.level().equalsIgnoreCase(level))
         .findAny()
         .orElse(null);

      if(result == null) {
         String upperLevel = level.toUpperCase();

         // check legacy names
         switch(upperLevel) {
         case "WARNING":
            result = LogLevel.WARN;
            break;
         case "INFO":
            result = LogLevel.INFO;
            break;
         case "FINE":
         case "FINER":
         case "FINEST":
            result = LogLevel.DEBUG;
            break;
         default:
            result = LogLevel.ERROR;
         }
      }

      return result;
   }

   public LogLevel getLevel() {
      return getLevel("inetsoft");
   }

   /**
    * Sets the default log level for all loggers in the "inetsoft" hierarchy.
    *
    * @param level the new log level.
    */
   public void setLevel(LogLevel level) {
      setLevel("inetsoft", level);
   }

   public LogLevel getLevel(String logger) {
      if(!SreeEnv.isInitialized()) {
         return LogLevel.WARN;
      }

      return getContextLevel(LogContext.CATEGORY, logger);
   }

   /**
    * Sets the log level for the specified logger.
    *
    * @param logger the logger to modify.
    * @param level  the new log level.
    */
   public void setLevel(String logger, LogLevel level) {
      setContextLevel(LogContext.CATEGORY, logger, level);
   }

   /**
    * Gets a context-specific log level.
    *
    * @param context the type of log context.
    * @param name    the name of the log context.
    *
    * @return the log level or <tt>Level.OFF</tt> if the default should be used.
    */
   private LogLevel getContextLevel(LogContext context, String name) {
      if(!SUtil.isMultiTenant() && name != null) {
         int orgIndex = name.lastIndexOf(Organization.getDefaultOrganizationID());

         if(orgIndex > 0) {
            name = name.substring(0, orgIndex - 1);
         }
      }

      return contextLevels.get(context).get(name);
   }

   /**
    * Sets a context-specific log level.
    *
    * @param context the type of log context.
    * @param name    the name of the log context.
    * @param level   the new log level. If the level is <tt>null</tt> or
    *                <tt>Level.OFF</tt>, the default level will be used.
    */
   public void setContextLevel(LogContext context, String name, LogLevel level) {
      if(level == null) {
         contextLevels.get(context).remove(name);
      }
      else {
         contextLevels.get(context).put(name, level);
      }
   }

   /**
    * Gets a list of all context-specific log levels that have been set.
    *
    * @return the context levels.
    */
   public List<LogLevelSetting> getContextLevels() {
      List<LogLevelSetting> result = new ArrayList<>();
      final SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

      for(Map.Entry<LogContext, Map<String, LogLevel>> entry : contextLevels.entrySet()) {
         entry.getValue().entrySet().stream()
            .filter(e -> entry.getKey() != LogContext.CATEGORY || !e.getKey().equals("inetsoft"))
            .filter(e -> e.getValue() != LogLevel.OFF)
            .map(e -> buildLogLevelSetting(entry.getKey(), e, provider))
            .forEachOrdered(result::add);
      }

      return result;
   }

   private LogLevelSetting buildLogLevelSetting(LogContext context, Map.Entry<String, LogLevel> entry,
                                                SecurityProvider provider)
   {
      String name = entry.getKey();
      LogLevel level = entry.getValue();
      String orgId = null;
      String orgName = null;
      int idx = name.lastIndexOf("^");

      if(idx != -1) {
         orgId = name.substring(idx + 1);
         orgName = provider.getOrgNameFromID(orgId);
         name = name.substring(0, idx);
      }

      return new LogLevelSetting(context, name, orgName, level);
   }

   public boolean isDebugEnabled(String name) {
      return isLevelEnabled(name, LogLevel.DEBUG);
   }

   public boolean isInfoEnabled(String name) {
      return isLevelEnabled(name, LogLevel.INFO);
   }

   public boolean isWarnEnabled(String name) {
      return isLevelEnabled(name, LogLevel.WARN);
   }

   public boolean isErrorEnabled(String name) {
      return isLevelEnabled(name, LogLevel.ERROR);
   }

   public boolean isLevelEnabled(String name, LogLevel level) {
      if(!SreeEnv.isInitialized()) {
         return level.ordinal() >= LogLevel.WARN.ordinal();
      }

      String logger = name;
      LogLevel current = getLevel(logger);

      while(current == null && logger.contains(".")) {
         logger = logger.substring(0, logger.lastIndexOf('.'));
         current = getLevel(logger);
      }

      if(current == LogLevel.OFF) {
         return false;
      }

      if(current == null) {
         current = LogLevel.ERROR;
      }

      for(LogContext context : LogContext.values()) {
         if(context != LogContext.CATEGORY) {
            String value = MDC.get(context.name());

            if(value != null) {
               LogLevel contextLevel;

               if(context == LogContext.GROUP || context == LogContext.ROLE) {
                  String[] ids = value.split(",");
                  contextLevel = Arrays.stream(ids) //Take the lowest log level from the list of groups/roles
                     .map(id -> getContextLevel(context, id))
                     .filter(Objects::nonNull)
                     .min(Comparator.comparingInt(Enum::ordinal))
                     .orElse(null);
               }
               else {
                  contextLevel = getContextLevel(context, value);
               }

               if(contextLevel != null && contextLevel.ordinal() < current.ordinal()) {
                  current = contextLevel;
               }
            }
         }
      }

      return level.ordinal() >= current.ordinal();
   }

   public void addLogMessageListener(LogMessageListener l, boolean exceptionsOnly) {
      messageListeners.put(l, exceptionsOnly);
   }

   public void removeLogMessageListener(LogMessageListener l) {
      messageListeners.remove(l);
   }

   public void fireLogMessageEvent(Supplier<LogMessageEvent> event, boolean hasThrown) {
      LogMessageEvent evt = null;

      for(Map.Entry<LogMessageListener, Boolean> e : messageListeners.entrySet()) {
         // not thrown and listener is exception only then continue
         if(!hasThrown && e.getValue()) {
            continue;
         }

         if(evt == null) {
            evt = event.get();
         }

         e.getKey().messageLogged(evt);
      }
   }

   /**
    * Gets the path to the log file.
    *
    * @param scheduler <tt>true</tt> to use the scheduler log; <tt>false</tt> to
    *                  use the server log.
    *
    * @return the log file path.
    */
   public String getBaseLogFile(boolean scheduler) {
      if(!SreeEnv.isInitialized()) {
         // don't use file system service, initializing
         String dir = System.getProperty(
            "inetsoft.log.dir", ConfigurationContext.getContext().getHome());
         String file;

         if(scheduler) {
            file = "schedule.log";
         }
         else {
            file = "sree.log";
         }

         return new File(dir, file).getAbsolutePath();
      }

      String property;
      FileSystemService fileSystemService = FileSystemService.getInstance();

      if(scheduler) {
         property = SreeEnv.getProperty("schedule.log.file");

         if(property == null) {
            property = "schedule.log";
         }
      }
      else {
         property = SreeEnv.getProperty("log.output.file");

         if(property == null) {
            property = "sree.log";
         }
      }

      Path path = fileSystemService.getPath(property);

      if(!path.isAbsolute()) {
         Path parent = fileSystemService.getPath(SreeEnv.getProperty("sree.home"));
         path = parent.resolve(path);
      }

      return path.toString();
   }

   private File getLogFile() {
      return getLogFile(false);
   }

   public File getLogFile(boolean scheduler) {
      try(Stream<Path> logFiles = getLogFiles(scheduler, true)) {
         return logFiles
            .map(Path::toFile)
            .findFirst().orElse(null);
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to get current log file", e);
      }
   }

   private Comparator<Path> getComparator(boolean scheduler) {
      Comparator<Path> comparator;

      if(!SreeEnv.isInitialized()) {
         comparator = Comparator.naturalOrder();
      }
      else {
         assert logFileProvider != null;
         comparator = logFileProvider.getComparator(getBaseLogFile(scheduler));
      }

      return comparator;
   }

   /**
    * Output logs to zip output stream. Outputs all logs.
    */
   public void zipLogs(ZipOutputStream output) throws IOException {
      try(Stream<Path> scheduleLogs = getLogFiles(true);
          Stream<Path> appLogs = getLogFiles(false))
      {
         Stream.concat(scheduleLogs, appLogs)
            .forEachOrdered(p -> addEntry(output, p));
      }
   }

   private void addEntry(ZipOutputStream zip, Path file) {
      try {
         ZipEntry entry = new ZipEntry(file.getFileName().toString());
         zip.putNextEntry(entry);

         try {
            Files.copy(file, zip);
         }
         finally {
            zip.closeEntry();
         }
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to add log file to zip", e);
      }
   }

   /**
    * Finds the log file with the specified name.
    *
    * @param name the name of the file.
    *
    * @return the file.
    */
   public File findLogFile(String name) {
      File file;
      FileSystemService fileSystemService = FileSystemService.getInstance();

      if(name == null) {
         file = getLogFile();
      }
      else {
         Path dir = fileSystemService.getPath(getBaseLogFile(false)).getParent();
         Pattern pattern = getLogFilePattern(false);

         if(pattern.matcher(name).matches()) {
            file = dir.resolve(name).toFile();
         }
         else {
            dir = fileSystemService.getPath(getBaseLogFile(true)).getParent();
            pattern = getLogFilePattern(true);

            if(pattern.matcher(name).matches()) {
               file = dir.resolve(name).toFile();
            }
            else {
               file = getLogFile();
            }
         }
      }

      return file;
   }

   /**
    * Gets the contents of a log file.
    *
    * @param file   Log file being read
    * @param offset Line from which to start getting contents. Enter a negative
    *               number to offset from the end of the file.
    * @param length Number of lines to read.
    *
    * @throws IOException if there was an exception readin the file.
    */
   public List<String> getLog(File file, int offset, int length) throws IOException {
      Objects.requireNonNull(file, "The log file cannot be null");
      BufferedReader reader = null;
      List<String> log = new ArrayList<>();

      try {
         if(offset < 0) {
            reader = new TailReader(new FileReader(file), -offset, length);
         }
         else if(offset > 0 || length > 0) {
            reader = new HeadReader(new FileReader(file), offset, length);
         }
         else {
            reader = new BufferedReader(new FileReader(file));
         }

         String line;

         while((line = reader.readLine()) != null) {
            log.add(line);
         }
      }
      finally {
         IOUtils.closeQuietly(reader);
      }

      return log;
   }


   /**
    * Gets vector of log files names.
    */
   public List<File> getLogFiles() {
      try(Stream<Path> appLogs = getLogFiles(false, true);
          Stream<Path> scheduleLogs = getLogFiles(true, true))
      {
         return Stream.concat(appLogs, scheduleLogs)
            .map(Path::toFile)
            .collect(Collectors.toList());
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to get log files", e);
      }
   }

   private Stream<Path> getLogFiles(boolean scheduler) throws IOException {
      return getLogFiles(scheduler, false);
   }

   private Stream<Path> getLogFiles(boolean scheduler, boolean sorted) throws IOException {
      String baseFile = getBaseLogFile(scheduler);
      Path dirPath = FileSystemService.getInstance().getPath(baseFile).getParent();
      Pattern pattern = getLogFilePattern(scheduler);
      Stream<Path> stream = Files.list(dirPath)
         .filter(Files::isRegularFile)
         .filter(p -> pattern.matcher(p.getFileName().toString()).matches());

      if(sorted) {
         stream = stream.sorted(getComparator(scheduler));
      }

      return stream;
   }

   private Pattern getLogFilePattern(boolean scheduler) {
      if(!SreeEnv.isInitialized()) {
         String pattern;

         if(scheduler) {
            pattern = "^schedule.+\\.log$";
         }
         else {
            pattern = "^sree.+\\.log$";
         }

         return Pattern.compile(pattern);
      }

      assert logFileProvider != null;
      return logFileProvider.getLogFilePattern(getBaseLogFile(scheduler));
   }

   public boolean isRotateSupported(String fileName) {
      if(!SreeEnv.isInitialized()) {
         return false;
      }

      assert logFileProvider != null;
      return logFileProvider.isRotateSupported(getBaseLogFile(false), fileName)
         || logFileProvider.isRotateSupported(getBaseLogFile(true), fileName);
   }

   public String getCurrentLog(List<String> logFiles) {
      if(logFiles != null && !logFiles.isEmpty() && SreeEnv.isInitialized() &&
         logFileProvider != null)
      {
         String baseName = getBaseLogFile(false);
         int startIndex = baseName.lastIndexOf(File.separatorChar) + 1;
         int lastIndex = baseName.lastIndexOf('.');
         String prefix;
         String suffix;

         if(lastIndex < 0) {
            prefix = baseName;
            suffix = "";
         }
         else {
            prefix = baseName.substring(startIndex, lastIndex);
            suffix = baseName.substring(lastIndex);
         }

         String discriminator = SreeEnv.getProperty("log.file.discriminator", Tool.getIP());
         String current = prefix + "-" + discriminator + suffix;

         for(String logFile : logFiles) {
            if(current.equals(logFile)) {
               return logFile;
            }
         }

         return logFiles.get(0);
      }

      return null;
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof RotateLogMessage) {
         rotateLogFile();

         try {
            RotateLogCompleteMessage message = new RotateLogCompleteMessage();
            message.setId(((RotateLogMessage) event.getMessage()).getId());
            Cluster.getInstance().sendMessage(event.getSender(), message);
         }
         catch(Exception e) {
            LOG.warn("Failed to send rotate log complete message", e);
         }
      }
   }

   /**
    * Rolls over a log file so that the primary log file is empty. Will rotate all log files
    *
    * @param fileName   the name of the log file being rotated.
    */
   public void rotateLogFile(String fileName) {
      if(!SreeEnv.isInitialized()) {
         return;
      }

      boolean scheduler =
         logFileProvider.isRotateSupported(getBaseLogFile(true), fileName);
      Cluster cluster = Cluster.getInstance();
      List<String> selectedNodes = cluster.getClusterNodes().stream()
         .filter((node) -> (!scheduler && !Boolean.TRUE.equals(cluster.getClusterNodeProperty(node, "scheduler")))
            || (scheduler && Boolean.TRUE.equals(cluster.getClusterNodeProperty(node, "scheduler"))))
         .collect(Collectors.toList());

      if(!selectedNodes.isEmpty()) {
         RotateLogMessage rotateLogMessage = new RotateLogMessage();
         rotateLogMessage.setId(UUID.randomUUID().toString());
         CountDownLatch latch = new CountDownLatch(selectedNodes.size());
         MessageListener listener = event -> {
            if(event.getMessage() instanceof RotateLogCompleteMessage) {
               RotateLogCompleteMessage message = (RotateLogCompleteMessage) event.getMessage();

               if(Tool.equals(rotateLogMessage.getId(), message.getId())) {
                  latch.countDown();
               }
            }
         };

         cluster.addMessageListener(listener);
         String logFileIp = extractIPFromLogName(fileName);

         try {
            for(String node : selectedNodes) {
               // Bug #68658, only rotate log file for selected node.
               if(!Tool.equals(logFileIp, extractIpFromNodeName(node))) {
                  continue;
               }

               cluster.sendMessage(node, rotateLogMessage);
            }

            if(!latch.await(60L, TimeUnit.SECONDS)) {
               LOG.warn("Timed out waiting to rotate the logs");
            }
         }
         catch(Exception e) {
            LOG.error("Failed to send rotate log message", e);
         }
         finally {
            cluster.removeMessageListener(listener);
         }
      }
   }

   public static String extractIPFromLogName(String logFile) {
      String ipV4Pattern = "(\\b(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?\\b)";
      String ipV6Pattern = "(([0-9a-fA-F]{1,4}:){7}([0-9a-fA-F]{1,4}))|((([0-9a-fA-F]{1,4}:){1,7}|:):)|((:[0-9a-fA-F]{1,4}){1,7}:)";

      Pattern pattern = Pattern.compile(ipV4Pattern + "|" + ipV6Pattern);
      Matcher matcher = pattern.matcher(logFile);

      if(matcher.find()) {
         return matcher.group();
      }
      else {
         return null;
      }
   }

   public static String extractIpFromNodeName(String node) {
      if(node == null || node.indexOf(":") == -1) {
         return null;
      }

      return node.split(":")[0];
   }

   public void rotateLogFile() {
      assert logFileProvider != null;
      logFileProvider.rotateLogFile();
   }

   public void logException(Logger log, LogLevel level, String message, Throwable thrown) {
      switch(level) {
      case DEBUG:
         log.debug(message, thrown);
         break;
      case INFO:
         log.info(message, thrown);
         break;
      case WARN:
         log.warn(message, thrown);
         break;
      case ERROR:
         log.error(message, thrown);
         break;
      }
   }

   public void setLogFileProvider(LogFileProvider provider) {
      this.logFileProvider = provider;
   }

   private LogFileProvider logFileProvider;
   private final Map<LogContext, Map<String, LogLevel>> contextLevels = new ConcurrentHashMap<>();
   private final Map<LogMessageListener, Boolean> messageListeners = new ConcurrentHashMap<>();
   private SreeEnv.Value logProvider;
   private static final Logger LOG = LoggerFactory.getLogger(LogManager.class);

   /**
    * Class that reads line from the beginning of an input reader.
    *
    * @author InetSoft Technolgy
    * @since 12.0
    */
   private static final class HeadReader extends BufferedReader {
      /**
       * Creates a new instance of <tt>HeadReader</tt>.
       *
       * @param reader the reader from which all lines may be read.
       * @param offset the number of lines from the beginning of the file at
       *               which to start reading.
       * @param length the number of lines to read. If this value is less than
       *               zero, all remaining lines will be read.
       */
      HeadReader(Reader reader, int offset, int length) {
         super(reader);

         this.offset = offset;
         this.length = length;
      }

      @Override
      public String readLine() throws IOException {
         if(!ready) {
            int skipped = 0;

            while(skipped < offset && super.readLine() != null) {
               ++skipped;
            }

            ready = true;
         }

         String line = length < 0 || count < length ? super.readLine() : null;
         ++count;
         return line;
      }

      private final int offset;
      private final int length;

      private boolean ready = false;
      private int count = 0;
   }

   /**
    * Class that reads line from the end of an input reader.
    *
    * @author InetSoft Technolgy
    * @since 12.0
    */
   private static final class TailReader extends BufferedReader {
      /**
       * Creates a new instance of <tt>TailReader</tt>.
       *
       * @param reader the reader from which all lines may be read.
       * @param offset the number of lines from the <i>end</i> of the file at
       *               which to start reading.
       * @param length the number of lines to read. If this value is less than
       *               zero, all remaining lines will be read.
       */
      TailReader(Reader reader, int offset, int length) {
         super(reader);

         this.offset = offset;
         this.length = length < 0 || length > offset ? offset : length;

         this.queue = new ArrayDeque<>();
      }

      @Override
      public String readLine() throws IOException {
         if(!ready) {
            // fill a buffer of size offset, shifting it to the end of the input
            String line;

            while((line = super.readLine()) != null) {
               if(queue.size() == offset) {
                  queue.removeFirst();
               }

               queue.addLast(line);
            }

            // trim any lines from the end that exceed the length parameter
            while(queue.size() > length) {
               queue.removeLast();
            }

            ready = true;
         }

         return queue.isEmpty() ? null : queue.removeFirst();
      }

      private final int offset;
      private final int length;

      private final Deque<String> queue;
      private boolean ready = false;
   }
}
