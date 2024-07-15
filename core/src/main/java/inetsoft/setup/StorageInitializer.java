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

import inetsoft.sree.security.*;
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
      else {
         setProperties();
      }

      installPlugins();

      if(!initialized) {
         configureSecurity();
         importAssets();
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

   private void setProperties() throws Exception {
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

   private void installPlugins() throws Exception {
      if(pluginsDirectory == null) {
         pluginsDirectory = new File("/usr/local/inetsoft/plugins");
      }

      if(pluginsDirectory.isDirectory()) {
         File[] files = pluginsDirectory.listFiles(this::isPluginFile);

         if(files != null && files.length > 0) {
            try(StorageService service = new StorageService(configDirectory.getAbsolutePath())) {
               for(File file : files) {
                  service.installPlugin(file);
               }
            }
         }
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

   private void configureSecurity() throws Exception {
      String password = System.getenv("INETSOFT_ADMIN_PASSWORD");

      if(!StringUtils.isEmpty(password)) {
         HashedPassword hash = getPasswordEncryption().hash(password, "bcrypt", null, false);
         FSUser user =
            new FSUser(new IdentityID("admin", Organization.getDefaultOrganizationName()));
         user.setPassword(hash.getHash());
         user.setPasswordAlgorithm(hash.getAlgorithm());
         user.setPasswordSalt(null);
         user.setAppendPasswordSalt(false);
         user.setRoles(new IdentityID[] { new IdentityID("Administrator", null) });
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

   private void importAssets() {
      try {
         Class.forName("inetsoft.enterprise.client.ClientFactory");
      }
      catch(ClassNotFoundException ignore) {
         // only supported in enterprise
         return;
      }

      if(assetsDirectory != null && assetsDirectory.isDirectory()) {
         File[] files = assetsDirectory.listFiles(
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
                  "Failed to import assets from '" + assetsDirectory.getAbsolutePath() + "'", e);
            }
         }
      }
   }

   private AutoCloseable openClient() {
      try {
         Class<?> clientFactoryClass = Class.forName("inetsoft.enterprise.client.ClientFactory");
         Object clientFactory = clientFactoryClass.getConstructor().newInstance();
         Method method = clientFactoryClass.getDeclaredMethod(
            "createLocalClient", String.class, IdentityID.class, String.class);
         String path = configDirectory.getAbsolutePath();
         IdentityID username = new IdentityID("admin", Organization.getDefaultOrganizationName());
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
}
