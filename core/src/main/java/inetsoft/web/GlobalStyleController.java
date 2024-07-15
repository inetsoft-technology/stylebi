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
package inetsoft.web;

import com.github.benmanes.caffeine.cache.*;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.accept.PathExtensionContentNegotiationStrategy;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.*;
import java.security.Principal;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("deprecation")
@Controller
public class GlobalStyleController implements ApplicationContextAware {
   @GetMapping({ "/app/global.css", "/em/theme.css", "/app/assets/**", "/em/assets/**",
                 "/app/theme-variables.css", "/em/theme-variables.css",
                 "/em/theme-dark.css"})
   public void getStyle(HttpServletRequest request, HttpServletResponse response, Principal user)
      throws IOException
   {
      String path = request.getServletPath();

      if(path.endsWith("loading.svg")) {
         String loading = SreeEnv.getProperty("html.loading.icon");

         if(loading != null) {
            path = path.substring(0, path.length() - 11) + loading;
         }
      }

      StyleResource resource = getResource(path, user);

      // use max-age caching for static resources to minimize the number of requests to load the
      // application
      if(resource.isStaticResource()) {
         writeResource(response, resource, false);
      }
      else {
         String checksum = request.getHeader("If-None-Match");

         if(checksum != null && checksum.startsWith("\"") && checksum.endsWith("\"")) {
            checksum = checksum.substring(1, checksum.length() - 1);
         }

         if(checksum == null || !checksum.equals(resource.getHash())) {
            writeResource(response, resource, true);
         }
         else {
            response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
         }
      }
   }

   @Override
   public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
      this.context = applicationContext;
   }

   private synchronized StyleResource getResource(String path, Principal user) throws IOException {
      String themeId = CustomThemesManager.getManager().getSelectedTheme(user);
      ResourceKey key = new ResourceKey(themeId, path);

      try {
         StyleResource resource = resources.get(key);

         if(resource == null || resource.isModified()) {
            resources.invalidate(key);
            resource = resources.get(key);
         }

         return resource;
      }
      catch(Exception e) {
         throw new IOException("Failed to get requested resource", e);
      }
   }

   private void writeResource(HttpServletResponse response, StyleResource resource, boolean etag)
      throws IOException
   {
      response.setCharacterEncoding("UTF-8");

      if(resource.getContentType() != null) {
         response.setContentType(resource.getContentType());
      }

      response.setContentLength(resource.getLength());

      if(etag) {
         response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl
            .noCache()
            .cachePrivate()
            .mustRevalidate()
            .getHeaderValue());
         response.setHeader("ETag", '"' + resource.getHash() + '"');
      }
      else {
         response.setHeader(
            HttpHeaders.CACHE_CONTROL,
            CacheControl.maxAge(Duration.of(7L, ChronoUnit.DAYS)).getHeaderValue());
      }

      try(InputStream input = resource.getInputStream();
          OutputStream output = response.getOutputStream())
      {
         IOUtils.copy(input, output);
      }
   }

   private ApplicationContext context;
   private final LoadingCache<ResourceKey, StyleResource> resources = Caffeine.newBuilder()
      .maximumSize(1000)
      .expireAfterAccess(10, TimeUnit.MINUTES)
      .build(new CacheLoader<ResourceKey, StyleResource>() {
         @Override
         public StyleResource load(ResourceKey resourceKey) throws Exception {
            return new StyleResource(resourceKey.path, resourceKey.themeId, context);
         }
      });

   private static final Logger LOG = LoggerFactory.getLogger(GlobalStyleController.class);
   private static final PathExtensionContentNegotiationStrategy contentTypes;
   private static final Set<String> staticTypes = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
      "eot", "ttf", "woff", "woff2"
   )));

   static {
      Map<String, MediaType> map = new HashMap<>();
      map.put("woff", new MediaType("font", "woff"));
      map.put("woff2", new MediaType("font", "woff2"));
      contentTypes = new PathExtensionContentNegotiationStrategy(map);
   }

   private static final class ResourceKey {
      public ResourceKey(String themeId, String path) {
         this.themeId = themeId;
         this.path = path;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         ResourceKey that = (ResourceKey) o;
         return Objects.equals(themeId, that.themeId) &&
            Objects.equals(path, that.path);
      }

      @Override
      public int hashCode() {
         return Objects.hash(themeId, path);
      }

      private final String themeId;
      private final String path;
   }

   private static final class StyleResource {
      StyleResource(String path, String themeId, ResourcePatternResolver resolver)
         throws IOException
      {
         StringBuilder location = new StringBuilder("theme:");
         boolean themeExists = themeId != null &&
            CustomThemesManager.getManager().getCustomThemes().stream()
               .anyMatch(t -> t.getId().equals(themeId));

         if(themeId == null || !themeExists) {
            location.append("default");
         }
         else {
            location.append(themeId);
         }

         location.append("/inetsoft/web/resources").append(path);
         resource = resolver.getResource(location.toString());
         modified = resource.lastModified();
         MediaType mediaType = contentTypes.getMediaTypeForResource(resource);

         if(mediaType == null) {
            contentType = null;
            LOG.warn("Failed to determine media type for '{}'", resource.getFilename());
         }
         else {
            contentType = mediaType.toString();
         }

         ByteArrayOutputStream buffer = new ByteArrayOutputStream();

         try(InputStream input = resource.getInputStream()) {
            IOUtils.copy(input, buffer);
         }
         catch(Exception ex) {
            LOG.error("Failed to load resource: " + resource);
         }

         hash = DigestUtils.sha1Hex(buffer.toByteArray());
         length = buffer.size();

         String extension = StringUtils.getFilenameExtension(resource.getFilename());
         staticResource = extension == null || staticTypes.contains(extension.toLowerCase());
      }

      public String getHash() {
         return hash;
      }

      public int getLength() {
         return length;
      }

      public boolean isModified() throws IOException {
         return modified > 0L && resource.lastModified() > modified;
      }

      public String getContentType() {
         return contentType;
      }

      public boolean isStaticResource() {
         return staticResource;
      }

      public InputStream getInputStream() throws IOException {
         return resource.getInputStream();
      }

      @Override
      public String toString() {
         return "StyleResource{" +
            "resource=" + resource +
            ", hash='" + hash + '\'' +
            ", modified=" + modified +
            ", length=" + length +
            ", contentType='" + contentType + '\'' +
            '}';
      }

      private final Resource resource;
      private final String hash;
      private final long modified;
      private final int length;
      private final String contentType;
      private final boolean staticResource;
   }
}
