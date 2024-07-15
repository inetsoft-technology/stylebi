/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.util.Catalog;
import inetsoft.web.service.LocalizationService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;

/**
 * Controller for replacing template localization strings with their localized values.
 */
@Controller
public class TemplateLocalizationController {
   @Autowired
   public TemplateLocalizationController(LocalizationService localizationService) {
      this.localizationService = localizationService;
   }

   @PostConstruct
   public void localizeResources() throws IOException {
      if(!Files.isDirectory(localizationService.getI18nCacheDirectory())) {
         Files.createDirectories(localizationService.getI18nCacheDirectory());
      }

      localizationService.rebuildCache();
   }

   @GetMapping(value = { "/app/*.js", "/em/*.js" })
   public void getLocalizedResource(HttpServletRequest request,
                                    HttpServletResponse response, Principal principal)
      throws IOException
   {
      String path = request.getServletPath();

      if(!"false".equals(SreeEnv.getProperty("cache.localized.templates"))) {
         localizationService.getI18nLock().readLock().lock();

         try {
            Path cachedPath = localizationService.getCachePath(path, principal);

            if(!cachedPath.toFile().exists()) {
               response.sendError(HttpServletResponse.SC_NOT_FOUND);
               return;
            }

            String checksum = request.getHeader("If-None-Match");
            String expectedChecksum = localizationService.getCachedPath(cachedPath);

            if(checksum != null && checksum.startsWith("W/")) {
               checksum = checksum.substring(2);
            }

            if(checksum != null && checksum.startsWith("\"") && checksum.endsWith("\"")) {
               checksum = checksum.substring(1, checksum.length() - 1);
            }

            if(checksum != null && checksum.endsWith("-gzip")) {
               checksum = checksum.substring(0, checksum.length() - 5);
            }

            if(checksum == null || !checksum.equals(expectedChecksum)) {
               response.setCharacterEncoding("UTF-8");
               response.setContentType("text/javascript");
               response.setContentLength((int) Files.size(cachedPath));
               // force check for change. needed to support switching locale in a browser
               response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl
                  .noCache()
                  .cachePrivate()
                  .mustRevalidate()
                  .getHeaderValue());

               if(expectedChecksum != null) {
                  response.setHeader("ETag", '"' + expectedChecksum + '"');
               }

               try(OutputStream output = response.getOutputStream()) {
                  Files.copy(cachedPath, output);
               }

               LOG.debug(
                  "Received script request for {} with invalid If-None-Match header " +
                  "[{} != {}], sending full response with headers\n" +
                  "Cache-Control: {}\nETag: {}",
                  path, checksum, expectedChecksum,
                  response.getHeader(HttpHeaders.CACHE_CONTROL),
                  response.getHeader("ETag"));
            }
            else {
               // instruct the browser to use the cached value
               LOG.debug(
                  "Received script request for {} with matching If-None-Match header, " +
                  "sending not modified response", path);
               response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
            }
         }
         finally {
            localizationService.getI18nLock().readLock().unlock();
         }
      }
      else {
         response.setCharacterEncoding("UTF-8");
         response.setContentType("text/javascript");
         response.setHeader(
            HttpHeaders.CACHE_CONTROL, CacheControl.noStore().mustRevalidate().getHeaderValue());
         String resource = "/inetsoft/web/resources" + path;

         try(Reader reader = LocalizationService.getResourceReader(resource);
             OutputStream output = response.getOutputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BufferedWriter writer =
               new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            localize(reader, writer, principal);
            writer.close();
            output.write(out.toByteArray());
         }
      }
   }

   /**
    * Take the input from reader, localize it, and have the writer write the result.
    *
    * @param reader    the reader to read unlocalized data from
    * @param writer    the writer to write localized data to
    * @param principal the user principal
    */
   private void localize(Reader reader, Writer writer, Principal principal)
         throws IOException
   {
      localizationService.localize(reader, writer, Catalog.getCatalog(principal));
   }

   private final LocalizationService localizationService;
   private static final Logger LOG =
      LoggerFactory.getLogger(TemplateLocalizationController.class);
}
