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
package com.inetsoft.build.runner;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.model.interpolation.MavenBuildTimestamp;
import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.*;
import java.util.*;

@Mojo(
   name = "configure", threadSafe = true, defaultPhase = LifecyclePhase.VALIDATE,
   requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
@Execute(phase = LifecyclePhase.GENERATE_TEST_RESOURCES)
public class ConfigureMojo extends AbstractMojo {
   /**
    * The current maven project.
    */
   @Parameter(property = "project", required = true, readonly = true)
   private MavenProject project;

   /**
    * The current maven session.
    */
   @Parameter(defaultValue = "${session}", readonly = true, required = true)
   private MavenSession session;

   /**
    * The resource filtering support.
    */
   @Component(role = MavenResourcesFiltering.class, hint = "default")
   protected MavenResourcesFiltering mavenResourcesFiltering;

   /**
    * The InetSoft configuration file (inetsoft.yaml) to use.
    */
   @Parameter(property = "configFile")
   private File configFile;

   /**
    * The storage backup file to restore into the target environment.
    */
   @Parameter(property = "backupFile")
   private File backupFile;

   /**
    * The directory where the server will be configured.
    */
   @Parameter(property = "configDirectory")
   private File configDirectory;

   /**
    * Additional properties to set in the configuration (sree.properties).
    */
   @Parameter(property = "properties")
   private Properties properties;

   /**
    * The character encoding to use when writing the configuration file.
    */
   @Parameter(defaultValue = "${project.build.sourceEncoding}")
   private String encoding;

   @Override
   public void execute() throws MojoExecutionException, MojoFailureException {
      if(configFile == null || configDirectory == null) {
         getLog().debug("Skipping configure server because configFile or configDirectory is not set");
         return;
      }

      if(new File(configDirectory, configFile.getName()).exists()) {
         getLog().debug("Skipping configure server because config file already exists");
         return;
      }

      try {
         Resource configResource = new Resource();
         configResource.setDirectory(configFile.getParentFile().getAbsolutePath());
         configResource.setIncludes(List.of(configFile.getName()));
         configResource.setFiltering(true);

         Properties additionalProperties = new Properties();
         String timeStamp = new MavenBuildTimestamp().formattedTimestamp();
         additionalProperties.put("maven.build.timestamp", timeStamp);
         additionalProperties.put("inetsoft.cluster.group", "inetsoft-" + UUID.randomUUID());

         if(project.getBasedir() != null) {
            additionalProperties.put(
               "project.baseUri",
               project.getBasedir().getAbsoluteFile().toURI().toString());
         }

         MavenResourcesExecution execution = new MavenResourcesExecution(
            List.of(configResource), configDirectory, project, encoding, null,
            Collections.emptyList(), session);
         execution.setInjectProjectBuildFilters(false);
         execution.setAdditionalProperties(additionalProperties);
         execution.setUseDefaultFilterWrappers(true);
         mavenResourcesFiltering.filterResources(execution);
      }
      catch(Exception e) {
         throw new MojoFailureException("Failed to copy config file", e);
      }

      try {
         Files.createDirectories(configDirectory.toPath().getParent().resolve("temp"));
      }
      catch(Exception e) {
         throw new MojoFailureException("Failed to create server temp directory", e);
      }

      try(URLClassLoader loader = getDependencyClassloader()) {
         Object setupService;

         try {
            Class<?> clazz = loader.loadClass("inetsoft.shell.setup.SetupService");
            Constructor<?> cstr = clazz.getConstructor();
            setupService = cstr.newInstance();
         }
         catch(Exception e) {
            throw new MojoFailureException("Failed to setup instance", e);
         }

         String configDir = configDirectory.getAbsolutePath();

         if(backupFile != null && backupFile.exists()) {
            restoreBackup(loader, setupService, configDir);
         }

         if(properties != null && !properties.isEmpty()) {
            try(AutoCloseable propertiesService = getPropertiesService(loader, setupService, configDir)) {
               for(String prop : properties.stringPropertyNames()) {
                  putProperty(loader, propertiesService, prop, properties.getProperty(prop));
               }

               File localPropFile = new File(project.getBasedir(), "local.properties");

               if(localPropFile.exists()) {
                  Properties localProps = new Properties();

                  try(InputStream input = Files.newInputStream(localPropFile.toPath())) {
                     localProps.load(input);
                  }

                  for(String prop : localProps.stringPropertyNames()) {
                     putProperty(loader, propertiesService, prop, localProps.getProperty(prop));
                  }
               }
            }
            catch(Exception e) {
               getLog().warn("Failed to close properties service", e);
            }
         }

         List<File> plugins = project.getArtifacts().stream()
            .filter(this::isPluginFile)
            .map(Artifact::getFile)
            .filter(Objects::nonNull)
            .toList();

         try(AutoCloseable storageService = getStorageService(loader, setupService, configDir)) {
            for(File file : plugins) {
               installPlugin(loader, storageService, file);
            }
         }
      }
      catch(MojoFailureException | MojoExecutionException e) {
         throw e;
      }
      catch(Exception e) {
         throw new MojoFailureException("Failed to configure server", e);
      }
   }

   private URLClassLoader getDependencyClassloader() throws MojoExecutionException {
      try {
         URL[] urls = project.getArtifacts().stream()
            .filter(a -> !"zip".equals(a.getType()))
            .filter(a -> "compile".equals(a.getScope()) || "runtime".equals(a.getScope()))
            .map(Artifact::getFile)
            .filter(Objects::nonNull)
            .map(this::toUrl)
            .toArray(URL[]::new);
         return new URLClassLoader(urls);
      }
      catch(Exception e) {
         throw new MojoExecutionException("Failed to create class loader from project dependencies", e);
      }
   }

   private void restoreBackup(ClassLoader loader, Object setupService, String configDir)
      throws MojoExecutionException
   {
      ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(loader);

      try {
         Method method = setupService.getClass().getMethod("getStorageService", String.class);

         try(AutoCloseable storageService = (AutoCloseable) method.invoke(setupService, configDir)) {
            method = storageService.getClass().getMethod("restore", File.class);
            method.invoke(storageService, backupFile);
         }
      }
      catch(Exception e) {
         throw new MojoExecutionException("Failed to restore storage backup", e);
      }
      finally {
         Thread.currentThread().setContextClassLoader(oldLoader);
      }
   }

   private AutoCloseable getPropertiesService(ClassLoader loader, Object setupService,
                                              String configDir) throws MojoExecutionException
   {
      ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(loader);

      try {
         Method method = setupService.getClass().getMethod("getPropertiesService", String.class);
         return (AutoCloseable) method.invoke(setupService, configDir);
      }
      catch(Exception e) {
         throw new MojoExecutionException("Failed to get properties service", e);
      }
      finally {
         Thread.currentThread().setContextClassLoader(oldLoader);
      }
   }

   private AutoCloseable getStorageService(ClassLoader loader, Object setupService,
                                           String configDir) throws MojoExecutionException
   {
      ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(loader);

      try {
         Method method = setupService.getClass().getMethod("getStorageService", String.class);
         return (AutoCloseable) method.invoke(setupService, configDir);
      }
      catch(Exception e) {
         throw new MojoExecutionException("Failed to get storage service", e);
      }
      finally {
         Thread.currentThread().setContextClassLoader(oldLoader);
      }
   }

   private void putProperty(ClassLoader loader, Object propertiesService, String name, String value)
      throws MojoExecutionException
   {
      ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(loader);

      try {
         Method method = propertiesService.getClass().getMethod("put", String.class, String.class);
         method.invoke(propertiesService, name, value);
      }
      catch(Exception e) {
         throw new MojoExecutionException("Failed to set property", e);
      }
      finally {
         Thread.currentThread().setContextClassLoader(oldLoader);
      }
   }

   private void installPlugin(ClassLoader loader, Object storageService, File file)
      throws MojoExecutionException
   {
      ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(loader);

      try {
         Method method = storageService.getClass().getMethod("installPlugin", File.class);
         method.invoke(storageService, file);
      }
      catch(Exception e) {
         throw new MojoExecutionException("Failed to install plugin", e);
      }
      finally {
         Thread.currentThread().setContextClassLoader(oldLoader);
      }
   }

   private URL toUrl(File file) {
      try {
         return file.toURI().toURL();
      }
      catch(MalformedURLException e) {
         throw new RuntimeException(e);
      }
   }

   private boolean isPluginFile(Artifact artifact) {
      return "zip".equals(artifact.getType()) &&
         artifact.getGroupId() != null && artifact.getGroupId().startsWith("com.inetsoft");
   }
}
