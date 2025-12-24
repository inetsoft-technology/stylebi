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
package inetsoft.util.config;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import inetsoft.util.*;
import inetsoft.util.config.json.ConfigDeserializer;
import inetsoft.util.config.json.ConfigSerializer;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * {@code InetsoftConfig} contains the bootstrap configuration for an InetSoft installation.
 */
@SingletonManager.Singleton(InetsoftConfig.Reference.class)
@JsonSerialize(using = ConfigSerializer.class)
@JsonDeserialize(using = ConfigDeserializer.class)
public class InetsoftConfig implements Serializable {
   /**
    * The current product configuration version.
    */
   public static final String CURRENT_VERSION = "14.0.0";

   /**
    * The product version that this configuration is for.
    */
   public String getVersion() {
      return version;
   }

   public void setVersion(String version) {
      this.version = version;
   }

   /**
    * The cluster configuration.
    */
   public ClusterConfig getCluster() {
      return cluster;
   }

   public void setCluster(ClusterConfig cluster) {
      Objects.requireNonNull(cluster, "The cluster settings are required");
      this.cluster = cluster;
   }

   /**
    * The path to the local plugin directory.
    */
   public String getPluginDirectory() {
      return pluginDirectory;
   }

   public void setPluginDirectory(String pluginDirectory) {
      this.pluginDirectory = pluginDirectory;
   }

   /**
    * A flag that indicates if FIPS compliance mode is enabled.
    */
   public boolean isFipsComplianceMode() {
      return getSecrets().isFipsComplianceMode();
   }

   /**
    * The key-value storage configuration.
    */
   public KeyValueConfig getKeyValue() {
      return keyValue;
   }

   public void setKeyValue(KeyValueConfig keyValue) {
      Objects.requireNonNull(keyValue, "The key value settings are required");
      this.keyValue = keyValue;
   }

   /**
    * The blob storage configuration.
    */
   public BlobConfig getBlob() {
      return blob;
   }

   public void setBlob(BlobConfig blob) {
      Objects.requireNonNull(blob, "The blob settings are required");
      this.blob = blob;
   }

   /**
    * The external storage configuration.
    */
   public ExternalStorageConfig getExternalStorage() {
      return externalStorage;
   }

   public void setExternalStorage(ExternalStorageConfig externalStorage) {
      this.externalStorage = externalStorage;
   }

   /**
    * The cloud runner configuration.
    */
   public CloudRunnerConfig getCloudRunner() {
      return cloudRunner;
   }

   public void setCloudRunner(CloudRunnerConfig cloudRunner) {
      this.cloudRunner = cloudRunner;
   }

   /**
    * The audit storage configuration.
    */
   public AuditConfig getAudit() {
      return audit;
   }

   public void setAudit(AuditConfig audit) {
      this.audit = audit;
   }

   /**
    * The additional configuration properties that are not part of the main schema.
    */
   @JsonAnyGetter
   public Map<String, Object> getAdditionalProperties() {
      if(additionalProperties == null) {
         additionalProperties = new HashMap<>();
      }

      return additionalProperties;
   }

   @JsonAnySetter
   public void setAdditionalProperties(Map<String, Object> additionalProperties) {
      this.additionalProperties = additionalProperties;
   }

   public SecretsConfig getSecrets() {
      if(secrets == null) {
         secrets = new SecretsConfig();
      }

      return secrets;
   }

   public MetricsConfig getMetrics() {
      return metrics;
   }

   public void setMetrics(MetricsConfig metrics) {
      this.metrics = metrics;
   }

   public NodeProtectionConfig getNodeProtection() {
      return nodeProtection;
   }

   public void setNodeProtection(NodeProtectionConfig nodeProtection) {
      this.nodeProtection = nodeProtection;
   }

   @Override
   public String toString() {
      ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
      mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
      mapper.registerModule(new GuavaModule());
      StringWriter writer = new StringWriter();

      try {
         mapper.writeValue(writer, this);
      }
      catch(IOException e) {
         LoggerFactory.getLogger(InetsoftConfig.class).warn("Failed to serialize config", e);
      }

      return writer.toString();
   }

   public void setSecrets(SecretsConfig secrets) {
      this.secrets = secrets;
   }

   /**
    * Gets the singleton instance of {@code InetsoftConfig}.
    *
    * @return the config instance.
    */
   public static InetsoftConfig getInstance() {
      return SingletonManager.getInstance(InetsoftConfig.class);
   }

   /**
    * Creates the default configuration.
    *
    * @return the default configuration.
    */
   public static InetsoftConfig createDefault() {
      return createDefault(Paths.get(ConfigurationContext.getContext().getHome()));
   }

   /**
    * Creates the default configuration.
    *
    * @param home the configuration home directory.
    *
    * @return the default configuration.
    */
   public static InetsoftConfig createDefault(Path home) {
      InetsoftConfig config = new InetsoftConfig();
      config.setVersion(CURRENT_VERSION);
      config.setPluginDirectory(home.resolve("plugins").toFile().getAbsolutePath());
      config.setCluster(ClusterConfig.createDefault());

      new PropertyProcessor().applyProperties(config);

      if(config.getKeyValue() == null || config.getKeyValue().getType() == null) {
         KeyValueConfig keyValue = new KeyValueConfig();
         keyValue.setType("mapdb");
         MapDBConfig mapdb = new MapDBConfig();
         mapdb.setDirectory(home.resolve("kv").toFile().getAbsolutePath());
         keyValue.setMapdb(mapdb);
         config.setKeyValue(keyValue);
      }

      if(config.getBlob() == null || config.getBlob().getType() == null) {
         BlobConfig blob = new BlobConfig();
         blob.setType("local");
         FilesystemConfig filesystem = new FilesystemConfig();
         filesystem.setDirectory(home.resolve("blob").toFile().getAbsolutePath());
         blob.setFilesystem(filesystem);
         config.setBlob(blob);
      }

      if(config.getExternalStorage() == null || config.getExternalStorage().getType() == null) {
         ExternalStorageConfig external = new ExternalStorageConfig();
         external.setType("filesystem");
         FilesystemConfig filesystem = new FilesystemConfig();
         filesystem.setDirectory(home.resolve("external-storage").toFile().getAbsolutePath());
         external.setFilesystem(filesystem);
         config.setExternalStorage(external);
      }

      if(config.getSecrets() == null) {
         SecretsConfig secretsConfig = new SecretsConfig();
         secretsConfig.setType("local");
         secretsConfig.setFipsComplianceMode(false);
         config.setSecrets(secretsConfig);
      }

      if(config.getAudit() == null) {
         AuditConfig auditConfig = new AuditConfig();
         auditConfig.setType("mapdb");
         config.setAudit(auditConfig);
      }

      return config;
   }

   /**
    * Loads the configuration.
    *
    * @param file the configuration file path.
    *
    * @return the configuration.
    */
   public static InetsoftConfig load(Path file) {
      InetsoftConfig config;

      if(file.toFile().exists()) {
         try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.registerModule(new GuavaModule());
            config = mapper.readValue(file.toFile(), InetsoftConfig.class);
            new PropertyProcessor().applyProperties(config);

            if(config.getAudit() == null && config.getKeyValue() != null &&
               "mapdb".equals(config.getKeyValue().getType()))
            {
               AuditConfig auditConfig = new AuditConfig();
               auditConfig.setType("mapdb");
               config.setAudit(auditConfig);
            }
         }
         catch(IOException e) {
            LoggerFactory.getLogger(InetsoftConfig.class)
               .error("Failed to load config file {}", file, e);
            config = createDefault(file.getParent());
         }
      }
      else {
         config = createDefault(file.getParent());
      }

      return config;
   }

   /**
    * Saves the configuration.
    *
    * @param config the configuration to save.
    */
   public static void save(InetsoftConfig config) {
      Path home = Paths.get(ConfigurationContext.getContext().getHome());
      Path file = home.resolve("inetsoft.yaml");

      if(!file.toFile().exists()) {
         file = home.resolve("inetsoft.yml");

         if(!file.toFile().exists()) {
            // neither exists, use default
            file = home.resolve("inetsoft.yaml");
         }
      }

      save(config, file);
   }

   /**
    * Saves the configuration.
    *
    * @param config the configuration to save.
    * @param file   the configuration file path.
    */
   public static void save(InetsoftConfig config, Path file) {
      Path parent = file.getParent();
      Path temp = parent.resolve(file.toFile().getName() + ".tmp");

      try {
         ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
         mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
         mapper.registerModule(new GuavaModule());
         mapper.writeValue(temp.toFile(), config);
         Files.move(
            temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      }
      catch(Exception e) {
         LoggerFactory.getLogger(InetsoftConfig.class)
            .error("Failed to write config file", e);
      }
      finally {
         Tool.deleteFile(temp.toFile());
      }
   }

   private String version;
   private ClusterConfig cluster;
   private String pluginDirectory;
   private KeyValueConfig keyValue;
   private BlobConfig blob;
   private ExternalStorageConfig externalStorage;
   private CloudRunnerConfig cloudRunner;
   private SecretsConfig secrets;
   private AuditConfig audit;
   private MetricsConfig metrics;
   private NodeProtectionConfig nodeProtection;
   private Map<String, Object> additionalProperties;

   @SingletonManager.ShutdownOrder(after = FileSystemService.class)
   public static final class Reference extends SingletonManager.Reference<InetsoftConfig> {
      @Override
      public InetsoftConfig get(Object... parameters) {
         if(config == null) {
            String home = ConfigurationContext.getContext().getHome();
            File file = new File(home, "inetsoft.yaml");

            if(!file.exists()) {
               file = new File(home, "inetsoft.yml");

               if(!file.exists()) {
                  // neither exists, use default
                  file = new File(home, "inetsoft.yaml");
               }
            }

            boolean save = !file.exists();
            config = load(file.toPath());

            if(save) {
               save(config, file.toPath());
            }
         }

         return config;
      }

      @Override
      public void dispose() {
         config = null;
      }

      private InetsoftConfig config;
   }
}
