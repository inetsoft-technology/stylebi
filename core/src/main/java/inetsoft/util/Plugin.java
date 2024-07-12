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
package inetsoft.util;

import inetsoft.sree.SreeEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Class that encapsulates meta-data for a plugin.
 */
public final class Plugin {
   /**
    * Creates a new instance of <tt>Plugin</tt>.
    *
    * @param id              the unique plugin identifier.
    * @param name            the plugin name.
    * @param vendor          the plugin vendor.
    * @param version         the plugin version.
    * @param folder          the folder in which the plugin is installed.
    * @param requiredPlugins the identifiers of the required plugins.
    *
    * @throws IOException if an I/O error occurs.
    */
   Plugin(String id, String name, String vendor, String version, boolean readOnly, File folder,
          String[] requiredPlugins, Descriptor descriptor, Plugins plugins) throws IOException
   {
      this.id = id;
      this.name = name;
      this.vendor = vendor;
      this.version = version;
      this.readOnly = readOnly;
      this.folder = folder;
      this.requiredPlugins = requiredPlugins;
      this.descriptor = descriptor;

      List<URL> urls = new ArrayList<>();
      FileSystemService fileSystemService = FileSystemService.getInstance();

      // used in development to point to live class. it could potentially
      // be useful in deployment to add access classes such as spark
      // data sources
      String extra = SreeEnv.getProperty("plugin.extra.classpath." + id, "");

      for(String dir : Tool.split(extra, File.pathSeparatorChar)) {
         if(!dir.isEmpty()) {
            if(dir.endsWith("/*") || dir.endsWith("\\*")) {
               addJars(fileSystemService.getFile(dir.substring(0, dir.length() - 2)), urls);
            }
            else {
               urls.add(fileSystemService.getFile(dir).toURI().toURL());
            }
         }
      }

      urls.add(fileSystemService.getFile(folder, "classes").toURI().toURL());

      File lib = fileSystemService.getFile(folder, "lib");
      addJars(lib, urls);

      PluginClassLoader loader = new PluginClassLoader(
         urls.toArray(new URL[0]), Plugin.class.getClassLoader(), requiredPlugins, plugins,
         descriptor.pluginClassloaderFirst);

      if(descriptor.mergeInto != null) {
         Plugin plugin = plugins.getPlugin(descriptor.mergeInto);

         if(plugin != null) {
            loader = plugin.loader.merge(loader);
         }
         else if(descriptor.mergeIntoRequired) {
            throw new RuntimeException("Plugin to be merged by " + id +
                                       " is missing: " + descriptor.mergeInto);
         }
      }

      this.loader = loader;
      services = new HashMap<>();

      File servicesFolder = fileSystemService.getFile(folder, "classes/META-INF/services");
      File[] serviceFiles = servicesFolder.listFiles();

      if(serviceFiles != null) {
         for(File serviceFile : serviceFiles) {
            Set<String> implClasses = new LinkedHashSet<>();
            services.put(serviceFile.getName(), implClasses);

            try(BufferedReader reader = new BufferedReader(new FileReader(serviceFile))) {
               String line;

               while((line = reader.readLine()) != null) {
                  int comment = line.indexOf('#');

                  if(comment >= 0) {
                     line = line.substring(0, comment);
                  }

                  line = line.trim();

                  if(!line.isEmpty() && !implClasses.contains(line)) {
                     implClasses.add(line);
                  }
               }
            }
         }
      }
   }

   // preload service classes
   void preload() {
      /* preload causes class loading problem
      for(Set<String> classes: services.values()) {
         for(String cls : classes) {
            try {
               loader.loadClass(cls, true).newInstance();
            }
            catch(Exception ex) {
               LOG.info("Failed to preload service: " + cls);
               // ignore
            }
         }
      }
      */
   }

   // Add jars in directory lib to urls
   private static void addJars(File lib, List<URL> urls) throws IOException {
      if(lib.isDirectory()) {
         File[] jars = lib.listFiles((dir, name) -> {
            String lowerName = name.toLowerCase();
            return lowerName.endsWith(".jar") || lowerName.endsWith(".zip");
         });

         if(jars != null) {
            for(File jar : jars) {
               urls.add(jar.toURI().toURL());
            }
         }
      }
   }

   /**
    * Gets the unique identifier of this plugin.
    *
    * @return the plugin identifier.
    */
   public String getId() {
      return id;
   }

   /**
    * Gets the display name of this plugin.
    *
    * @return the plugin name.
    */
   public String getName() {
      return name;
   }

   /**
    * Gets the name of the vendor that created this plugin.
    *
    * @return the plugin vendor.
    */
   public String getVendor() {
      return vendor;
   }

   /**
    * Gets the version of this plugin.
    *
    * @return the plugin version.
    */
   public String getVersion() {
      return version;
   }

   /**
    * Gets if the plugin is read only in the Enterprise Manager
    *
    * @return the plugin read only status
    */
   public boolean isReadOnly() {
      return readOnly;
   }

   /**
    * Gets the archive file for the plugin.
    *
    * @return the archive file or <tt>null</tt> if there is none.
    */
   public File getArchive() {
      File archive = FileSystemService.getInstance().
         getFile(folder.getParentFile(), folder.getName() + ".zip");
      return archive.isFile() ? archive : null;
   }

   /**
    * Get the plugin folder.
    */
   public File getFolder() {
      return folder;
   }

   /**
    * Get the plugins depended on by this plugin.
    */
   public String[] getRequiredPlugins() {
      return requiredPlugins;
   }

   /**
    * Get the main classes contained in this plugin. This can be used to
    * execute the class from PluginRunner.
    */
   public Map<String, String> getMainClasses() {
      return descriptor.mainClasses;
   }

   /**
    * Get the plugin class loader.
    */
   public PluginClassLoader getClassLoader() {
      return loader;
   }

   public Descriptor getDescriptor() {
      return descriptor;
   }

   /**
    * Gets an instance of the first implementation of the specified service.
    *
    * @param serviceClass the service class.
    *
    * @param <T> the service type.
    *
    * @return a new instance of the service or <tt>null</tt> if this plugin does not
    *         provide an implementation.
    */
   public <T> T getService(Class<T> serviceClass) {
      T result = null;
      Set<String> implClasses = services.get(serviceClass.getName());

      if(implClasses != null && !implClasses.isEmpty()) {
         result = createService(implClasses.iterator().next(), serviceClass);
      }

      return result;
   }

   /**
    * Gets the implementations of the specified service provided by this plugin.
    *
    * @param serviceClass the service class.
    *
    * @param <T> the service type.
    *
    * @return a list of service instances.
    */
   public <T> List<T> getServices(Class<T> serviceClass) {
      List<T> result = new ArrayList<>();
      Set<String> implClasses = services.get(serviceClass.getName());

      if(implClasses != null) {
         for(String implClass : implClasses) {
            result.add(createService(implClass, serviceClass));
         }
      }

      return result;
   }

   /**
    * Creates an instance of a service implementation.
    *
    * @param implClass    the name of the implementation class.
    * @param serviceClass the service class.
    *
    * @param <T> the service type.
    *
    * @return a new instance of the implementation class.
    */
   private <T> T createService(String implClass, Class<T> serviceClass) {
      T result;

      try {
         result = serviceClass.cast(
            Class.forName(implClass, true, loader).getConstructor().newInstance());
      }
      catch(InstantiationException | IllegalAccessException |
            NoSuchMethodException | ClassNotFoundException e)
      {
         throw new RuntimeException("Failed to create service instance", e);
      }
      catch(InvocationTargetException e) {
         throw new RuntimeException(
            "Failed to create service instance", e.getTargetException());
      }

      return result;
   }

   public static final class PluginClassLoader extends URLClassLoader {
      PluginClassLoader(URL[] urls, ClassLoader parent, String[] requiredPlugins, Plugins plugins,
                        boolean parentLast)
      {
         super(urls, parent);
         this.plugins = plugins;
         this.parentLast = parentLast;
         URL guavaUrl = null;

         for(URL url : urls) {
            try {
               String name = new File(url.toURI()).getName();

               if(name.matches("^guava-.+\\.jar$")) {
                  guavaUrl = url;
               }

               if(name.matches("^jackson-.+\\.jar$")) {
                  jacksonUrls.add(url);
               }
            }
            catch(URISyntaxException e) {
               // shouldn't happen, it came from a file in the first place
               LOG.debug("Invalid URL: " + url, e);
            }
         }

         this.guavaUrl = guavaUrl;
         this.requiredPlugins = requiredPlugins;
      }

      // Merge the specified class loader (url list) into this loader
      // so they essentially become the same plugin.
      public PluginClassLoader merge(PluginClassLoader loader) {
         Set<URL> all = new HashSet<>(Arrays.asList(getURLs()));

         for(URL url : loader.getURLs()) {
            if(!all.contains(url)) {
               addURL(url);
            }
         }

         return this;
      }

      @Override
      protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException
      {
         synchronized(this.getClassLoadingLock(name)) {
            if(notfound.containsKey(name)) {
               throw new ClassNotFoundException("Class not found: " + name);
            }

            Class<?> clazz = findLoadedClass(name);

            if(clazz != null) {
               return clazz;
            }

            for(String requiredPlugin : requiredPlugins) {
               Plugin plugin = plugins.getPlugin(requiredPlugin);

               if(plugin != null) {
                  try {
                     clazz = plugin.loader.loadClass(name, false);

                     if(resolve) {
                        resolveClass(clazz);
                     }

                     return clazz;
                  }
                  catch(ClassNotFoundException ignore) {
                  }
               }
            }

            for(URL url : jacksonUrls) {
               clazz = findClassInJAR(url, name, "com.fasterxml.jackson.", resolve);

               if(clazz != null) {
                  return clazz;
               }
            }

            clazz = findClassInJAR(guavaUrl, name, "com.google.", resolve);

            if(clazz != null) {
               return clazz;
            }

            if(parentLast) {
               if(clazz == null) {
                  try {
                     clazz = findClass(name);
                  }
                  catch(ClassNotFoundException ignore) {
                  }

                  if(clazz != null) {
                     if(resolve) {
                        resolveClass(clazz);
                     }

                     return clazz;
                  }
               }
            }

            return super.loadClass(name, resolve);
         }
      }

      private Class findClassInJAR(URL searchURL,
                                   String name,
                                   String prefix,
                                   boolean resolve) throws ClassNotFoundException
      {
         if(searchURL != null && name.startsWith(prefix)) {
            String path = name.replace('.', '/').concat(".class");
            URL url = findResource(path);

            if(url != null && "jar".equals(url.getProtocol())) {
               path = url.getFile();
               int index = path.indexOf('!');

               if(index >= 0) {
                  path = path.substring(0, index);
               }

               try {
                  url = new URL(path);

                  if(url.equals(searchURL)) {
                     Class<?> clazz = findClass(name);

                     if(resolve) {
                        resolveClass(clazz);
                     }

                     return clazz;
                  }
               }
               catch(MalformedURLException e) {
                  LOG.warn("Failed to check JAR file URL", e);
               }
            }
         }

         return null;
      }

      @Override
      public URL getResource(String name) {
         if(parentLast) {
            URL url = null;

            if(url == null) {
               url = findResource(name);
            }

            if(url != null) {
               return url;
            }
         }

         URL url = super.getResource(name);

         if(url == null) {
            for(String requiredPlugin : requiredPlugins) {
               Plugin plugin = plugins.getPlugin(requiredPlugin);

               if(plugin != null) {
                  url = plugin.loader.getResource(name);
               }

               if(url != null) {
                  break;
               }
            }
         }

         return url;
      }

      @Override
      public Enumeration<URL> getResources(String name) throws IOException {
         Collection<URL> urls = new ArrayList<>();

         if(parentLast) {
            urls.addAll(Collections.list(findResources(name)));

            if(getParent() != null) {
               urls.addAll(Collections.list(getParent().getResources(name)));
            }
         }
         else {
            urls.addAll(Collections.list(super.getResources(name)));
         }

         for(String requiredPlugin : requiredPlugins) {
            Plugin plugin = plugins.getPlugin(requiredPlugin);

            if(plugin != null) {
               urls.addAll(Collections.list(plugin.loader.getResources(name)));
            }
         }

         return Collections.enumeration(urls);
      }

      @Override
      public String toString() {
         return super.toString() + "[" + Arrays.toString(getURLs()) + "]";
      }

      private final URL guavaUrl;
      private final Set<URL> jacksonUrls = new HashSet<>();
      private final String[] requiredPlugins;
      private final Map<String, Boolean> notfound = new ConcurrentHashMap<>();
      private final Plugins plugins;
      private final boolean parentLast;
   }

   public static final class Descriptor implements Comparable<Descriptor>, Serializable {
      public Descriptor() {
      }

      public Descriptor(File file) throws IOException {
         Manifest manifest;

         try {
            manifest = getManifest(file, "classes/META-INF/MANIFEST.MF");
         }
         catch(FileNotFoundException ex) {
            manifest = getManifest(file, "META-INF/MANIFEST.MF");
         }

         this.file = file.getName();
         this.id = manifest.getMainAttributes().getValue("Plugin-Id");
         this.name = manifest.getMainAttributes().getValue("Plugin-Name");
         this.version = manifest.getMainAttributes().getValue("Plugin-Version");
         this.vendor = manifest.getMainAttributes().getValue("Plugin-Vendor");
         this.mergeInto = manifest.getMainAttributes().getValue("Plugin-Merge-Into");
         this.mergeIntoRequired = !"false".equals(
            manifest.getMainAttributes().getValue("Plugin-Merge-Into-Required"));
         this.preload = "true".equals(manifest.getMainAttributes().getValue("Preload"));
         this.pluginClassloaderFirst =
            "true".equals(manifest.getMainAttributes().getValue("Plugin-Classloader-First"));

         // main-class-id:main-class-full-name;...
         String value = manifest.getMainAttributes().getValue("Main-Classes");

         if(value != null) {
            for(String item : value.split(";")) {
               String[] pair = item.split(":");

               if(pair.length != 2) {
                  LOG.warn("Main class format error: " + item);
               }
               else {
                  mainClasses.put(pair[0], pair[1]);
               }
            }
         }

         List<String> requiredPlugins = new ArrayList<>();
         List<String> requiredApis = new ArrayList<>();
         // Plugin-Requires is a list of plugin separated by ';', each plugin
         // is identified as id:versionExpr
         value = manifest.getMainAttributes().getValue("Plugin-Requires");

         if(value != null) {
            for(String item : value.split(";")) {
               int index = item.indexOf(':');
               String requiredId = index < 0 ? item : item.substring(0, index);

               try {
                  ApiVersion.forId(requiredId);
                  requiredApis.add(item);
               }
               catch(IllegalArgumentException ignore) {
                  requiredPlugins.add(requiredId);
               }
            }
         }

         this.requiredPlugins = requiredPlugins.toArray(new String[0]);
         this.requiredApis = requiredApis.toArray(new String[0]);
      }

      private Manifest getManifest(File file, String fileName) throws IOException {
         if(file.isDirectory()) {
            File manifestFile = FileSystemService.getInstance()
               .getFile(file, fileName);

            try(InputStream input = new FileInputStream(manifestFile)) {
               return new Manifest(input);
            }
         }
         else {
            try(ZipFile zip = new ZipFile(file)) {
               ZipEntry entry = zip.getEntry(fileName);

               if(entry == null) {
                  throw new IOException("The plugin manifest is missing");
               }

               try(InputStream input = zip.getInputStream(entry)) {
                  return new Manifest(input);
               }
            }
         }
      }

      public String getId() {
         return id;
      }

      public void setId(String id) {
         this.id = id;
      }

      public String getName() {
         return name;
      }

      public void setName(String name) {
         this.name = name;
      }

      public String getVersion() {
         return version;
      }

      public void setVersion(String version) {
         this.version = version;
      }

      public String getVendor() {
         return vendor;
      }

      public void setVendor(String vendor) {
         this.vendor = vendor;
      }

      public Map<String, String> getMainClasses() {
         return mainClasses;
      }

      public void setMainClasses(Map<String, String> mainClasses) {
         this.mainClasses = mainClasses;
      }

      public String getMergeInto() {
         return mergeInto;
      }

      public void setMergeInto(String mergeInto) {
         this.mergeInto = mergeInto;
      }

      public boolean isMergeIntoRequired() {
         return mergeIntoRequired;
      }

      public void setMergeIntoRequired(boolean mergeIntoRequired) {
         this.mergeIntoRequired = mergeIntoRequired;
      }

      public String[] getRequiredPlugins() {
         return requiredPlugins;
      }

      public void setRequiredPlugins(String[] requiredPlugins) {
         this.requiredPlugins = requiredPlugins;
      }

      public String[] getRequiredApis() {
         return requiredApis;
      }

      public void setRequiredApis(String[] requiredApis) {
         this.requiredApis = requiredApis;
      }

      public boolean isPreload() {
         return preload;
      }

      public void setPreload(boolean preload) {
         this.preload = preload;
      }

      public boolean isPluginClassloaderFirst() {
         return pluginClassloaderFirst;
      }

      public void setPluginClassloaderFirst(boolean pluginClassloaderFirst) {
         this.pluginClassloaderFirst = pluginClassloaderFirst;
      }

      public String getFile() {
         return file;
      }

      public void setFile(String file) {
         this.file = file;
      }

      // @temp, need to implement dependency aware ordering. currently the
      // order is controlled by order of names
      @Override
      public int compareTo(Descriptor desc) {
         return file.compareTo(desc.file);
      }

      @Override
      public String toString() {
         return "Descriptor{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", version='" + version + '\'' +
            ", vendor='" + vendor + '\'' +
            ", file=" + file +
            '}';
      }

      private String id;
      private String name;
      private String version;
      private String vendor;
      // main class id -> main class (full) name
      private Map<String, String> mainClasses = new HashMap<>();
      private String mergeInto;
      private boolean mergeIntoRequired;
      private String[] requiredPlugins;
      private String[] requiredApis;
      private boolean preload;
      private boolean pluginClassloaderFirst;
      private String file;
   }

   private final String id;
   private final String name;
   private final String vendor;
   private final String version;
   private final boolean readOnly;
   private final Map<String, Set<String>> services;
   private final PluginClassLoader loader;
   private final File folder;
   private final String[] requiredPlugins;
   private final Descriptor descriptor;

   private static final Logger LOG = LoggerFactory.getLogger(Plugin.class);
}
