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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.storage.LoadKeyValueTask;
import inetsoft.util.HashedPassword;
import inetsoft.util.PasswordEncryption;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.config.SecretsConfig;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

/**
 * Class that performs basic initialization of a new storage.
 */
@CommandLine.Command(
   name = "init-storage", mixinStandardHelpOptions = true, version = "init-storage 14.0",
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
      int exitCode = new CommandLine(new StorageInitializer()).execute(args);
      System.exit(exitCode);
   }

   @Override
   public Integer call() throws Exception {
      boolean initialized = isInitialized();

      if(initialized) {
         System.out.println("Storage already initialized");
      }

      SetupExtension.Context context = new SetupExtension.Context(
         initialized, configDirectory, pluginsDirectory, filesDirectory, assetsDirectory,
         scriptsDirectory);
      List<SetupExtension> extensions = new ArrayList<>();
      ServiceLoader.load(SetupExtension.class).forEach(extensions::add);
      System.err.println("LOADED EXTENSIONS: " + extensions);
      context = applyExtensions(context, extensions, (c, e) -> e.start(c));
      System.err.println("UPDATED CONTEXT: " + context);

      context = setProperties(context, extensions);
      context = installPlugins(context, extensions);
      context = configureSecurity(context, extensions);
      context = importFiles(context, extensions);
      context = importAssets(context, extensions);

      if(!initialized) {
         setInitialized();
         System.out.println("Storage initialized successfully");
      }

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
         }
      }

      return applyExtensions(context, extensions, (c, e) -> e.afterPropertiesSet(c));
   }

   private SetupExtension.Context installPlugins(SetupExtension.Context context,
                                                 List<SetupExtension> extensions)
      throws Exception
   {
      installPlugins(new File("/usr/local/inetsoft/plugins"));

      if(context.pluginsDirectory() != null && context.pluginsDirectory().isDirectory()) {
         installPlugins(context.pluginsDirectory());
      }

      return applyExtensions(context, extensions, (c, e) -> e.afterPluginsInstalled(c));
   }

   private void installPlugins(File directory) throws Exception {
      File[] files = directory.listFiles(this::isPluginFile);

      if(files != null && files.length > 0) {
         try(StorageService service = new StorageService(configDirectory.getAbsolutePath())) {
            for(File file : files) {
               service.installPlugin(file);
            }
         }

         // send a message to reload the plugins from the kv store
         Cluster.getInstance().submit("plugins",
                                      new LoadKeyValueTask<>("plugins", true)).get();
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
         String password = System.getenv("INETSOFT_ADMIN_PASSWORD");

         if(!StringUtils.isEmpty(password)) {
            HashedPassword hash = getPasswordEncryption().hash(password, "bcrypt", null, false);
            FSUser user =
               new FSUser(new IdentityID("admin", Organization.getDefaultOrganizationID()));
            user.setPassword(hash.getHash());
            user.setPasswordAlgorithm(hash.getAlgorithm());
            user.setPasswordSalt(null);
            user.setAppendPasswordSalt(false);
            user.setRoles(new IdentityID[]{ new IdentityID("Administrator", null) });
            Path temp = Files.createTempFile("virtual_security", ".xml");

            try(PrintWriter writer = new PrintWriter(Files.newBufferedWriter(temp, StandardCharsets.UTF_8))) {
               writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
               writer.println("<virtualSecurityProvider>");
               user.writeXML(writer);
               writer.println("</virtualSecurityProvider>");
            }

            try(StorageService service = new StorageService(configDirectory.getAbsolutePath())) {
               service.write("virtual_security.xml", temp.toFile());
            }

            Files.delete(temp);
         }
      }

      return applyExtensions(context, extensions, (c, e) -> e.afterSecurityConfigured(c));
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

      return applyExtensions(context, extensions, (c, e) -> e.afterFilesImported(c));
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
         try {
            Class.forName("inetsoft.enterprise.client.ClientFactory");
         }
         catch(ClassNotFoundException ignore) {
            // only supported in enterprise
            return context;
         }

         if(context.assetsDirectory() != null && context.assetsDirectory().isDirectory()) {
            File[] files = context.assetsDirectory().listFiles(
               pathname -> pathname.isFile() && pathname.getName().toLowerCase().endsWith(".zip"));

            if(files != null && files.length > 0) {
               try(AutoCloseable client = openClient()) {
                  Object fileService = getFileService(client);

                  for(File file : files) {
                     importAssets(fileService, file);
                  }
               }
               catch(Exception e) {
                  throw new RuntimeException(
                     "Failed to import assets from '" + context.assetsDirectory().getAbsolutePath() + "'", e);
               }
            }
         }
      }

      return applyExtensions(context, extensions, (c, e) -> e.afterAssetsInstalled(c));
   }

   private AutoCloseable openClient() {
      try {
         Class<?> clientFactoryClass = Class.forName("inetsoft.enterprise.client.ClientFactory");
         Object clientFactory = clientFactoryClass.getConstructor().newInstance();
         Method method = clientFactoryClass.getDeclaredMethod(
            "createLocalClient", String.class, IdentityID.class, String.class);
         String path = configDirectory.getAbsolutePath();
         IdentityID username = new IdentityID("admin", Organization.getDefaultOrganizationID());
         String password = System.getenv("INETSOFT_ADMIN_PASSWORD");

         if(password == null) {
            password = "admin";
         }

         return (AutoCloseable) method.invoke(clientFactory, path, username, password);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to create local client", e);
      }
   }

   private Object getFileService(Object client) {
      try {
         Class<?> clazz = client.getClass();
         Method method = clazz.getMethod("getFileService");
         return method.invoke(client);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to get file service", e);
      }
   }

   private void importAssets(Object fileService, File zipFile) {
      try {
         Class<?> clazz = fileService.getClass();
         Method method = clazz.getDeclaredMethod(
            "importAssets", String.class, List.class, boolean.class);
         method.invoke(fileService, zipFile.getAbsolutePath(), Collections.emptyList(), true);
         System.out.println("Imported assets from " + zipFile.getAbsolutePath());
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to import assets from " + zipFile.getAbsolutePath(), e);
      }
   }

   private SetupExtension.Context applyExtensions(
      SetupExtension.Context context, List<SetupExtension> extensions,
      BiFunction<SetupExtension.Context, SetupExtension, SetupExtension.Context> fn)
   {
      return extensions.stream().reduce(context, fn, (c1, c2) -> c2);
   }
}
