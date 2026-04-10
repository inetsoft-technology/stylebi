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
package inetsoft.setup;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.ignite.IgniteCluster;
import inetsoft.sree.security.*;
import inetsoft.storage.LoadKeyValueTask;
import inetsoft.util.*;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.config.SecretsConfig;
import inetsoft.util.log.LogUtil;
import inetsoft.util.log.logback.AuditLogFilter;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.*;
import org.springframework.context.support.AbstractApplicationContext;
import picocli.CommandLine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

/**
 * Class that performs basic initialization of a new storage.
 */
@CommandLine.Command(
   name = "init-storage", mixinStandardHelpOptions = true, version = "init-storage 1.2",
   description = "Initializes storage for a StyleBI instance"
)
public class StorageInitializer implements Callable<Integer> {
   @CommandLine.Option(
      names = { "-c", "--config" }, paramLabel = "CONFIG_DIR",
      description = "The path to the configuration directory", required = true)
   private File configDirectory;

   @CommandLine.Option(
      names = { "-p", "--plugins" }, paramLabel = "PLUGINS_DIR",
      description = "The path to the directory containing the plugin files"
   )
   private File pluginsDirectory;

   @CommandLine.Option(
      names = { "-a", "--assets" }, paramLabel = "ASSETS_DIR",
      description = "The path to the directory containing the asset files to import"
   )
   private File assetsDirectory;

   @CommandLine.Option(
      names = { "-f", "--files" }, paramLabel = "FILES_DIR",
      description = "The path to the directory containing files to import to the data space"
   )
   private File filesDirectory;

   @CommandLine.Option(
      names = { "-s", "--scripts" }, paramLabel = "SCRIPTS_DIR",
      description = "The path to the directory containing DSL script files to execute"
   )
   private File scriptsDirectory;

   private static final String STORAGE_INITIALIZED_KEY = "storage.initialized";

   /**
    * The main entry point of the program.
    *
    * @param args the command line arguments.
    */
   public static void main(String[] args) {
      System.setProperty("inetsoftStorageInitializing", "true");
      int exitCode = new CommandLine(new StorageInitializer()).execute(args);
      System.exit(exitCode);
   }

   @Override
   public Integer call() throws Exception {
      Instant startTime = Instant.now();
      initLogging();
      boolean initialized = isInitialized();

      if(initialized) {
         System.out.println("Storage already initialized");
      }

      SetupExtension.Context context = new SetupExtension.Context(
         initialized, configDirectory, pluginsDirectory, filesDirectory, assetsDirectory,
         scriptsDirectory, new HashMap<>());

      try {
         List<SetupExtension> extensions = new ArrayList<>();
         ServiceLoader.load(SetupExtension.class).forEach(extensions::add);
         context = applyExtensions(context, extensions, SetupExtension.Phase.START, (c, e) -> e.start(c));

         context = setProperties(context, extensions);
         context = installPlugins(context, extensions);
         context = configureSecurity(context, extensions);
         context = importFiles(context, extensions);
         context = importAssets(context, extensions);
         reloadClusterPlugins(context);
      }
      finally {
         for(Object value : context.attributes().values()) {
            if(value instanceof AutoCloseable closeable) {
               try {
                  closeable.close();
               }
               catch(Exception e) {
                  System.err.println("Failed to close context attribute");
                  //noinspection CallToPrintStackTrace
                  e.printStackTrace();
               }
            }
         }
      }

      if(!initialized) {
         setInitialized();
         System.out.println("Storage initialized successfully");
      }

      Instant endTime = Instant.now();
      System.out.println("Initialization completed in " + Duration.between(startTime, endTime));
      return 0;
   }

   private boolean isInitialized() throws Exception {
      try(PropertiesService service = new PropertiesService(configDirectory.getAbsolutePath())) {
         return "true".equals(service.get(STORAGE_INITIALIZED_KEY));
      }
   }

   private void setInitialized() throws Exception {
      try(PropertiesService service = new PropertiesService(configDirectory.getAbsolutePath())) {
         service.put(STORAGE_INITIALIZED_KEY, "true");
      }
   }

   private SetupExtension.Context setProperties(SetupExtension.Context context,
                                                List<SetupExtension> extensions)
      throws Exception
   {
      if(!context.initialized()) {
         try(PropertiesService service = new PropertiesService(configDirectory.getAbsolutePath())) {
            for(Map.Entry<String, String> e : System.getenv().entrySet()) {
               if(e.getKey().startsWith("INETSOFTENV_")) {
                  String property = e.getKey()
                     .substring(12)
                     .toLowerCase()
                     .replace('_', '.');
                  service.put(property, e.getValue());
                  System.out.println("Set property " + property);
               }
            }

            service.put("schedule.auto.start", "false");
            service.put("schedule.auto.stop", "false");
            service.put("font.truetype.path", "/usr/share/fonts/truetype/;$(sree.home)/fonts");
         }
      }

      return applyExtensions(context, extensions, SetupExtension.Phase.AFTER_PROPERTIES_SET, (c, e) -> e.afterPropertiesSet(c));
   }

   private SetupExtension.Context installPlugins(SetupExtension.Context context,
                                                 List<SetupExtension> extensions)
      throws Exception
   {
      installPlugins(new File("/usr/local/inetsoft/plugins"), context);

      if(context.pluginsDirectory() != null && context.pluginsDirectory().isDirectory()) {
         installPlugins(context.pluginsDirectory(), context);
      }

      return applyExtensions(context, extensions, SetupExtension.Phase.AFTER_PLUGINS_INSTALLED, (c, e) -> e.afterPluginsInstalled(c));
   }

   private void installPlugins(File directory, SetupExtension.Context context) throws Exception {
      File[] files = directory.listFiles(this::isPluginFile);

      if(files != null && files.length > 0) {
         try(StorageService service = new StorageService(configDirectory.getAbsolutePath())) {
            for(File file : files) {
               service.installPlugin(file);
            }
         }

         context.attributes().put("pluginsInstalled", true);
      }
   }

   private boolean isPluginFile(File file) {
      String name = file.getName();
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         String suffix = name.substring(index).toLowerCase();
         return ".zip".equals(suffix);
      }

      return false;
   }

   private SetupExtension.Context configureSecurity(SetupExtension.Context context,
                                                    List<SetupExtension> extensions)
      throws Exception
   {
      if(!context.initialized()) {
         String password = AdminCredentialUtil.getRequiredAdminPassword();
         HashedPassword hash = getPasswordEncryption().hash(password, "bcrypt", null, false);
         FSUser user =
            new FSUser(new IdentityID("admin", Organization.getDefaultOrganizationID()));
         user.setPassword(hash.getHash());
         user.setPasswordAlgorithm(hash.getAlgorithm());
         user.setPasswordSalt(null);
         user.setAppendPasswordSalt(false);
         user.setRoles(new IdentityID[]{ new IdentityID("Administrator", null) });
         Path temp = Files.createTempFile("virtual_security", ".xml");

         try {
            try(PrintWriter writer = new PrintWriter(Files.newBufferedWriter(temp, StandardCharsets.UTF_8))) {
               writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
               writer.println("<virtualSecurityProvider>");
               user.writeXML(writer);
               writer.println("</virtualSecurityProvider>");
            }

            try(StorageService service = new StorageService(configDirectory.getAbsolutePath())) {
               service.write("virtual_security.xml", temp.toFile());
            }
         }
         finally {
            Files.deleteIfExists(temp);
         }
      }

      return applyExtensions(context, extensions, SetupExtension.Phase.AFTER_SECURITY_CONFIGURED, (c, e) -> e.afterSecurityConfigured(c));
   }

   private PasswordEncryption getPasswordEncryption() {
      File file = new File(configDirectory, "inetsoft.yaml");

      if(!file.exists() && new File(configDirectory, "inetsoft.yml").exists()) {
         file = new File(configDirectory, "inetsoft.yml");
      }

      SecretsConfig config = null;

      if(file.exists()) {
         config = InetsoftConfig.load(file.toPath()).getSecrets();
      }

      if(config == null) {
         config = new SecretsConfig();
         config.setFipsComplianceMode(false);
      }

      return PasswordEncryption.newInstance(config);
   }

   private SetupExtension.Context importFiles(SetupExtension.Context context,
                                              List<SetupExtension> extensions)
      throws Exception
   {
      if(!context.initialized() &&
         context.filesDirectory() != null && context.filesDirectory().isDirectory())
      {
         try(StorageService service = new StorageService(configDirectory.getAbsolutePath())) {
            importFiles(service, context.filesDirectory(), context.filesDirectory());
         }
      }

      return applyExtensions(context, extensions, SetupExtension.Phase.AFTER_FILES_IMPORTED, (c, e) -> e.afterFilesImported(c));
   }

   private void importFiles(StorageService service, File root, File file) throws IOException {
      if(file.isDirectory()) {
         File[] files = file.listFiles();

         if(files != null) {
            for(File f : files) {
               importFiles(service, root, f);
            }
         }
      }
      else {
         String name = root.toPath().relativize(file.toPath()).toString();
         service.write(name, file);
      }
   }

   private SetupExtension.Context importAssets(SetupExtension.Context context,
                                               List<SetupExtension> extensions)
   {
      if(!context.initialized()) {
         context = applyExtensions(context, extensions, SetupExtension.Phase.INSTALL_ASSETS, (c, e) -> e.installAssets(c));
      }

      return applyExtensions(context, extensions, SetupExtension.Phase.AFTER_ASSETS_INSTALLED, (c, e) -> e.afterAssetsInstalled(c));
   }

   private SetupExtension.Context applyExtensions(
      SetupExtension.Context context, List<SetupExtension> extensions, SetupExtension.Phase phase,
      BiFunction<SetupExtension.Context, SetupExtension, SetupExtension.Context> fn)
   {
      return extensions.stream()
         .peek(e -> System.out.println("Applying extension " + phase.getPhase() + ": " + e.getClass().getName()))
         .reduce(context, fn, (c1, c2) -> c2);
   }

   private void initLogging() {
      LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
      context.reset();

      Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
      rootLogger.setLevel(Level.ERROR);

      rootLogger.addAppender(createConsoleAppender(context));

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
      context.getLogger("inetsoft_audit").setLevel(Level.INFO);
   }

   private AsyncAppender createConsoleAppender(LoggerContext context) {
      ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
      appender.setName("STDOUT");
      appender.setContext(context);
      appender.setEncoder(createEncoder(context));
      appender.addFilter(new AuditLogFilter(true));
      appender.start();
      return createAsyncAppender("ASYNC_STDOUT", appender, context);
   }

   private PatternLayoutEncoder createEncoder(LoggerContext context) {
      PatternLayoutEncoder encoder = new PatternLayoutEncoder();
      encoder.setContext(context);
      encoder.setPattern("%d %level %property{LOCAL_IP_ADDR} [%mdc] %c{1}: %message %n%ex");
      encoder.start();
      return encoder;
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

   private void reloadClusterPlugins(SetupExtension.Context context) {
      if(Boolean.TRUE.equals(context.attributes().get("pluginsInstalled"))) {
         // send a message to reload the plugins from the kv store
         String home = context.configDirectory().getAbsolutePath();
         System.setProperty("sree.home", home);
         ConfigurationContext configContext = ConfigurationContext.getContext();
         configContext.setHome(home);

         try {
            InetsoftConfig.bootstrap();

            try(AbstractApplicationContext applicationContext = new AnnotationConfigApplicationContext(ClusterConfig.class)) {
               configContext.setApplicationContext(applicationContext);
               Cluster.getInstance().submit("plugins", new LoadKeyValueTask<>("plugins", true));
               context.attributes().put("pluginsInstalled", false);
            }
         }
         finally {
            configContext.setApplicationContext(null);
            InetsoftConfig.BOOTSTRAP_INSTANCE = null;
         }
      }
   }

   @Configuration
   private static class ClusterConfig {
      @Bean
      public InetsoftConfig inetsoftConfig() {
         return InetsoftConfig.BOOTSTRAP_INSTANCE;
      }

      @Bean
      public Cluster cluster(InetsoftConfig config) {
         // todo use client mode
         return new IgniteCluster();
      }
   }
}
