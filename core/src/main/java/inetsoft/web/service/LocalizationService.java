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
package inetsoft.web.service;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.uql.XPrincipal;
import inetsoft.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service that provides localization support.
 * Localization tag:
 *  in html: _#(id)
 *  in javascript: _#(js:id)
 * Template with parameter can be formatted with Tool.formatCatalogString(str, [args])
 */
@Component
public class LocalizationService {
   /**
    * Clears the i18n cache.
    */
   public void clearI18nCache() throws IOException {
      final String cacheDir = getI18nCacheDirectory().toString();
      I18N_LOCK.writeLock().lock();
      Tool.lock(cacheDir);

      try {
         if(Files.exists(I18N_CACHE_DIR)) {
            boolean success;

            try(Stream<Path> stream = Files.walk(I18N_CACHE_DIR)) {
               success = stream
                  .sorted(Comparator.reverseOrder())
                  .map(Path::toFile)
                  .map(File::delete)
                  .reduce(true, (prev, f) -> prev && f);
            }

            if(!success) {
               LOG.warn("Failed to completely clear I18n cache directory");
            }
         }

         Files.createDirectories(I18N_CACHE_DIR);
         rebuildCache();
      }
      finally {
         Tool.unlock(cacheDir);
         I18N_LOCK.writeLock().unlock();
         clearCache();
         clearCachedPaths();
      }
   }

   public void rebuildCache() throws IOException {
      // This entire thing only takes about ~500ms, so it makes sense to just translate all files in
      // a batch at once. This also allows more advanced caching to be implemented.
      if("false".equals(SreeEnv.getProperty("cache.localized.templates"))) {
         return;
      }

      I18N_LOCK.writeLock().lock();

      try {
         checksums.clear();
         PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

         Set<Resource> translated = Arrays.stream(TRANSLATED_RESOURCES)
            .flatMap(r -> getResources(resolver, r))
            .collect(Collectors.toSet());
         boolean addDefault = true;
         Locale defaultLocale = Locale.getDefault();

         for(Locale locale : getSupportedLocales()) {
            for(Resource resource : translated) {
               localizeResource(locale, resource);
            }

            if(defaultLocale.equals(locale)) {
               addDefault = false;
            }
         }

         if(addDefault) {
            for(Resource resource : translated) {
               localizeResource(defaultLocale, resource);
            }
         }
      }
      finally {
         I18N_LOCK.writeLock().unlock();
      }
   }

   private static String getResourcePath(Resource resource) {
      try {
         final String resourcePath = resource.getURI().toString();
         final int index = resourcePath.lastIndexOf(RESOURCES_DIR);
         return resourcePath.substring(index + RESOURCES_DIR.length());
      }
      catch(IOException e){
         LOG.warn("Failed to get resource URI for {}", resource, e);
         String filename = resource.getFilename();

         if(filename != null) {
            int index = filename.lastIndexOf(RESOURCES_DIR);

            if(index < 0) {
               return filename;
            }

            return filename.substring(index + RESOURCES_DIR.length());
         }
      }

      return null;
   }

   private void localizeResource(Locale locale, Resource resource) throws IOException {
      final String relativePath = getResourcePath(resource);
      final Path cachedPath = getCachePath(relativePath, locale);
      final Path parentPath = cachedPath.getParent();

      if(parentPath != null && !Files.isDirectory(parentPath)) {
         Files.createDirectory(parentPath);
      }

      try(Reader reader = getResourceReader(resource);
          PrintWriter writer = new PrintWriter(Files.newBufferedWriter(cachedPath)))
      {
         localize(reader, writer, locale);
      }

      try(InputStream input = Files.newInputStream(cachedPath)) {
         String checksum = getChecksum(input);
         checksums.put(cachedPath, checksum);
      }
   }

   private static Reader getResourceReader(Resource resource) throws IOException {
      return new BufferedReader(
         new InputStreamReader(getResourceStream(resource), StandardCharsets.UTF_8));
   }

   private static InputStream getResourceStream(Resource resource) throws IOException {
      return resource.getURL().openStream();
   }

   public static Reader getResourceReader(String resource) {
      return new BufferedReader(
         new InputStreamReader(getResourceStream(resource), StandardCharsets.UTF_8));
   }

   private static InputStream getResourceStream(String resource) {
      return LocalizationService.class.getResourceAsStream(resource);
   }

   public void localize(Reader reader, Writer writer, Catalog catalog)
         throws IOException
   {
      StringWriter buffer = null;
      int c;
      boolean inTag = false;
      boolean escaped = false;

      while((c = reader.read()) != -1) {
         if(inTag) {
            if(c == '\\') {
               escaped = true;
            }
            else if(c == ')' && !escaped) {
               String id = buffer.toString();
               boolean jsEncoding = true;

               if(id.startsWith("js:")) {
                  id = id.substring(3);
               }
               else if(id.startsWith("none:")) {
                  jsEncoding = false;
                  id = id.substring(5);
               }

               String str = catalog.getString(id);

               if(jsEncoding) {
                  str = Tool.escapeJavascript(str);
               }

               String escapedNewlines = escapeNewLines(str);
               writer.append(replaceParamSymbol(escapedNewlines, jsEncoding));
               buffer = null;
               inTag = false;
            }
            else {
               escaped = false;
               buffer.write(c);
            }
         }
         else if(c == '_') {
            int c2 = reader.read();

            if(c2 == '#') {
               int c3 = reader.read();

               if(c3 == '(') {
                  inTag = true;
                  buffer = new StringWriter();
               }
               else {
                  writer.write(c);
                  writer.write(c2);

                  if(c3 == -1) {
                     break;
                  }
                  else {
                     writer.write(c3);
                  }
               }
            }
            else {
               writer.write(c);

               if(c2 == -1) {
                  break;
               }
               else {
                  writer.write(c2);
               }
            }
         }
         else {
            writer.write(c);
         }
      }

      if(inTag) {
         writer.write(buffer.toString());
      }
   }

   // {n} can be escaped as \{n\}
   private CharSequence replaceParamSymbol(String str, boolean jsEncoding) {
      StringBuilder out = new StringBuilder();
      final int len = str.length();
      int pos = 0;
      // escape1 is the first \, and escape2 is the \ immediately following a \
      boolean escape1 = false, escape2 = false;

      // without jsEncoding, {n} is escaped as \{n\}
      // for jsEncoding, \ is escaped as \\, so the escaped string is \\{n\\}

      for(int i = 0; i < len; i++) {
         if(str.charAt(i) == '{' && i < len - 1 && Character.isDigit(str.charAt(i + 1))) {
            Integer index = null;
            // escaped is true if \ or \\ for jsEncoding
            boolean escaped = escape1 && (escape2 || !jsEncoding);

            if(escaped) {
               out.append(str, pos, i - (jsEncoding ? 2 : 1));
            }
            else {
               out.append(str, pos, i);
            }

            for(int j = i + 1; j < len; j++) {
               if(escaped && str.charAt(j) == '\\') {
                  char next1 = j < len - 1 ? str.charAt(j + 1) : ' ';
                  char next2 = j < len - 2 ? str.charAt(j + 2) : ' ';

                  // escaped \{0\}, look for \} or \\} for jsEncoding
                  if(jsEncoding && next1 == '\\' && next2 == '}' || !jsEncoding && next1 == '}') {
                     out.append(str, i, j);

                     if(jsEncoding) {
                        i = j + 1;
                        pos = j + 2;
                     }
                     else {
                        i = j;
                        pos = j + 1;
                     }

                     break;
                  }
               }

               if(!Character.isDigit(str.charAt(j))) {
                  if(index == null) {
                     index = Integer.valueOf(str.substring(i + 1, j));
                  }

                  if(str.charAt(j) == '}') {
                     out.append("%s$").append(index);
                     i = j;
                     pos = j + 1;
                     break;
                  }
               }
            }
         }
         else {
            boolean escape = str.charAt(i) == '\\';

            if(escape) {
               if(escape1) {
                  escape2 = true;
               }
               else {
                  escape1 = true;
               }
            }
            else {
               escape1 = escape2 = false;
            }
         }
      }

      if(pos == 0) {
         return str;
      }

      if(pos < len) {
         out.append(str, pos, len);
      }

      return out;
   }

   /**
    * Returns a new string with all \n characters replaced with \\n
    */
   private String escapeNewLines(String text) {
      if(text == null || text.isEmpty()) {
         return text;
      }

      final StringBuilder out = new StringBuilder();
      final char newLine = '\n';
      final String replacementString = "\\n";
      int start = 0;
      int pos = text.indexOf(newLine, start);

      while(pos > -1) {
         out.append(text, start, pos)
            .append(replacementString);

         // increment start and find next occurrence
         start = pos + 1;
         pos = text.indexOf(newLine, start);
      }

      return out.append(text.substring(start)).toString();
   }

   /**
    * Gets the cache path corresponding to the given path and user principal locale.
    *
    * @param path      the base file's path
    * @param principal the user principal
    *
    * @return the cache path
    */
   public Path getCachePath(String path, Principal principal) {
      CachedPathKey key = new CachedPathKey(getLocaleProperty(principal), path);
      return cachedPaths.computeIfAbsent(key, k -> computePath(path, principal));
   }

   private Path computePath(String path, Principal principal) {
      return getPath(path, getSuffix(principal));
   }

   private Path getCachePath(String path, Locale locale) {
      return getPath(path, getSupportedLocaleSuffix(locale));
   }

   private Path getPath(String pathString, String suffix) {
      final FileSystemService fileSystemService = FileSystemService.getInstance();
      final String oldResolvedPath = fileSystemService.getPath(pathString).toString();

      final int extIndex = oldResolvedPath.lastIndexOf('.');
      final String prefix = oldResolvedPath.substring(0, extIndex);
      final String extension = oldResolvedPath.substring(extIndex);
      final String newPath = prefix + suffix + extension;

      final String cacheDir = getI18nCacheDirectory().toString();
      return fileSystemService.getPath(cacheDir, newPath);
   }

   private void clearCachedPaths() {
      cachedPaths.clear();
   }

   private String getChecksum(InputStream input) throws IOException {
      return DigestUtils.sha1Hex(input);
   }

   private Stream<Resource> getResources(ResourcePatternResolver resolver, String pattern)
   {
      try {
         return Arrays.stream(resolver.getResources(pattern))
            .filter(Tool.distinctByKey(LocalizationService::getResourcePath));
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to scan for cache resources", e);
      }
   }

   /**
    * Clears any cached locale information.
    */
   public void clearCache() {
      supportedLocales.clear();
   }

   /**
    * Gets the {@code Catalog} instance for the specified locale.
    *
    * @param locale the desired locale.
    *
    * @return the matching catalog.
    */
   public Catalog getCatalog(Locale locale) {
      String resource =
         SreeEnv.getProperty("StyleReport.locale.resource", Catalog.DEFAULT_RESOURCE);
      return Catalog.getResCatalog(resource, Catalog.DEFAULT_RESOURCE, locale);
   }

   /**
    * Gets the currently configured locales.
    *
    * @return the locales.
    */
   public Set<Locale> getSupportedLocales() {
      Set<Locale> locales;
      String localeProperty = SreeEnv.getProperty("locale.available");

      if(localeProperty != null && !localeProperty.isEmpty()) {
         locales = Arrays.stream(Tool.split(localeProperty, ':'))
            .map(Catalog::parseLocale)
            .collect(Collectors.toSet());
      }
      else {
         locales = Collections.singleton(Locale.getDefault());
      }

      return locales;
   }

   /**
    * Gets the locale property of the specified principal.
    *
    * @param principal the principal to get the locale for.
    *
    * @return the locale property value or {@code null} if not set.
    */
   private String getLocaleProperty(Principal principal) {
      String locale = null;

      if(principal instanceof XPrincipal) {
         locale = ((XPrincipal) principal).getProperty(XPrincipal.LOCALE);
      }

      return locale;
   }

   private void localize(Reader reader, Writer writer, Locale locale)
         throws IOException
   {
      localize(reader, writer, getCatalog(locale));
   }

   /**
    * Gets the locale for the specified principal.
    *
    * @param principal the principal to get the locale for.
    *
    * @return the locale.
    */
   public Locale getLocale(Principal principal) {
      Locale result = null;

      if(principal instanceof XPrincipal) {
         String locale = getLocaleProperty(principal);

         if(locale != null && !locale.isEmpty()) {
            result = getSupportedLocale(Catalog.parseLocale(locale));
         }
      }

      if(result == null) {
         result = getSupportedLocale(Locale.getDefault());
      }

      return result;
   }

   /**
    * Gets the localized file suffix for the specified principal.
    *
    * @param principal the principal to get the locale suffix for.
    *
    * @return the localized file suffix.
    */
   public String getSuffix(Principal principal) {
      String result = null;

      if(principal instanceof XPrincipal) {
         String locale = getLocaleProperty(principal);

         if(locale != null && !locale.isEmpty() && !locale.equals(SUtil.MY_LOCALE)) {
            try {
               result = getSupportedLocaleSuffix(Catalog.parseLocale(locale));
            }
            catch(Exception e) {
               // invalid locale
               LOG.debug("Invalid locale specified: {}", locale, e);
            }
         }
      }

      if(result == null) {
         result = getSupportedLocaleSuffix(Locale.getDefault());
      }

      return result;
   }

   /**
    * Gets the supported locale for the specified user locale. Supported locales are those
    * configured in the administration console.
    *
    * @param userLocale the user locale.
    *
    * @return the supported locale.
    */
   public Locale getSupportedLocale(Locale userLocale) {
      Locale supportedLocale = supportedLocales.get(userLocale);

      if(supportedLocale == null) {
         try {
            String bundleName =
               SreeEnv.getProperty("StyleReport.locale.resource", Catalog.DEFAULT_RESOURCE);
            ResourceBundle bundle = ResourceBundle.getBundle(bundleName, userLocale);
            supportedLocale = bundle.getLocale();
         }
         catch(Exception ignore) {
            supportedLocale = Locale.getDefault();
         }

         supportedLocales.put(userLocale, supportedLocale);
      }

      return supportedLocale;
   }

   /**
    * Gets the localized file suffix for the supported locale that corresponds to the specified user
    * locale.
    *
    * @param userLocale the user locale.
    *
    * @return the localized file suffix.
    *
    * @see #getSupportedLocale(Locale)
    * @see #getSuffix(Locale)
    */
   private String getSupportedLocaleSuffix(Locale userLocale) {
      return getSuffix(getSupportedLocale(userLocale));
   }

   /**
    * Gets the localized file suffix for the specified locale.
    *
    * @param locale the locale.
    *
    * @return the localized file suffix.
    */
   public String getSuffix(Locale locale) {
      StringBuilder suffix = new StringBuilder();

      if(!locale.getLanguage().isEmpty()) {
         suffix.append('_').append(locale.getLanguage());

         if(!locale.getCountry().isEmpty()) {
            suffix.append('_').append(locale.getCountry());

            if(!locale.getVariant().isEmpty()) {
               suffix.append('_').append(locale.getVariant());
            }
         }
      }

      return suffix.toString();
   }

   public String getCachedPath(Path cachedPath) {
      return checksums.get(cachedPath);
   }

   /**
    * @return the i18n cache directory path string
    */
   public Path getI18nCacheDirectory() {
      return I18N_CACHE_DIR;
   }

   public ReadWriteLock getI18nLock() {
      return I18N_LOCK;
   }

   private final Map<Path, String> checksums = new HashMap<>();
   private final ReentrantReadWriteLock I18N_LOCK = new ReentrantReadWriteLock();
   private final Map<Locale, Locale> supportedLocales = new ConcurrentHashMap<>();
   private final Map<CachedPathKey, Path> cachedPaths = new ConcurrentHashMap<>();

   private static final Path I18N_CACHE_DIR = FileSystemService.getInstance().getPath(
      Tool.getCacheDirectory(), "i18n");
   private static final String RESOURCES_DIR = "/inetsoft/web/resources";
   private static final String[] TRANSLATED_RESOURCES = {
      "classpath*:/inetsoft/web/resources/app/*.js",
      "classpath*:/inetsoft/web/resources/em/*.js"
   };
   private static final Logger LOG = LoggerFactory.getLogger(LocalizationService.class);

   private static final class CachedPathKey {
      CachedPathKey(String locale, String file) {
         this.locale = locale;
         this.file = file;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         CachedPathKey that = (CachedPathKey) o;
         return Objects.equals(locale, that.getLocale()) &&
            Objects.equals(file, that.getFile());
      }

      @Override
      public int hashCode() {
         return Objects.hash(locale, file);
      }

      public String getLocale() {
         return this.locale;
      }

      public String getFile() {
         return this.file;
      }

      @Override
      public String toString() {
         return "CachedPathKey(" + file + ", " + locale + ")";
      }

      private final String locale;
      private final String file;
   }
}
