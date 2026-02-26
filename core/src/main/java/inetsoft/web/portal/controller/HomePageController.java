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
package inetsoft.web.portal.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.DataSpace;
import inetsoft.util.Tool;
import inetsoft.web.assistant.AIAssistantController;
import inetsoft.web.viewsheet.service.LinkUri;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Controller
public class HomePageController {
   @Autowired
   public HomePageController(DataSpace dataSpace) {
      this.dataSpace = dataSpace;
      this.webAssetsExist = dataSpace.isDirectory("web-assets");
   }

   @GetMapping({
      "/app", "/app/", "/app/index.html", "/app/adhoc", "/app/composer",
      "/app/portal", "/app/portal/**", "/app/viewer", "/app/viewer/**",
      "/app/wizard", "/app/wizard/**", "/app/reportviewer", "/app/reportviewer/**",
      "/app/embed/**", "/app/reload"
   })
   public ModelAndView showHomePage(HttpServletRequest request, HttpServletResponse response,
                                    @LinkUri String linkUri)
   {
      return prepareHomePageResponse(request, response, linkUri);
   }

   private ModelAndView prepareHomePageResponse(HttpServletRequest request, HttpServletResponse response,
                                                @LinkUri String linkUri)
   {
      String header = CacheControl.noCache()
         .cachePrivate()
         .mustRevalidate()
         .getHeaderValue();
      response.setHeader(HttpHeaders.CACHE_CONTROL, header);

      boolean hasOrgTheme = false;

      if(OrganizationManager.getInstance().getCurrentOrgID() != null) {
         String orgId = OrganizationManager.getInstance().getCurrentOrgID();
         SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

         if(provider.getOrganization(orgId) != null &&
            !Tool.isEmptyString(provider.getOrganization(orgId).getTheme()) &&
            !(Tool.equals("default", provider.getOrganization(orgId).getTheme())))
         {
            hasOrgTheme = true;
         }
      }

      CustomThemesManager themes = CustomThemesManager.getManager();
      String customLoadingText = SreeEnv.getProperty("portal.customLoadingText").replaceAll("\\s", " ");
      ModelAndView model = new ModelAndView("app/index");
      model.addObject("linkUri", linkUri);
      model.addObject("customTheme", themes.isCustomThemeApplied() || hasOrgTheme);
      model.addObject("scriptThemeCssPath", themes.getScriptThemeCssPath(true));
      model.addObject("customLoadingText", customLoadingText);

      addAdditionalStyles(model, linkUri);
      addAdditionalScripts(model, linkUri);
      addChatAppScript(model);
      addDataSpaceTags(model, linkUri);
      addOpenGraphTags(model, linkUri, request.getRequestURI(), request.getHeader("User-Agent"));

      return model;
   }


//   @GetMapping({"/app/reload"})
//   public ModelAndView pingServer(HttpServletRequest request, HttpServletResponse response,
//                                    @LinkUri String linkUri)
//   {
//      // Handle ping
////      return null;
//
//      return showHomePage(request, response, linkUri);
//   }

   @PostMapping({"/app/portal/**", "/app/viewer/**", "/app/reportviewer/**"})
   public ModelAndView postHomePage(HttpServletRequest request, HttpServletResponse response,
                                    @LinkUri String linkUri)
   {
      final ModelAndView model = prepareHomePageResponse(request, response, linkUri);
      final Map<String, String[]> parameterMap = new HashMap<>(request.getParameterMap());
      final Map<String, String[]> queryParams = Tool.parseQueryString(request.getQueryString());

      // subtract query params to leave only post params.
      for(Map.Entry<String, String[]> entry : queryParams.entrySet()) {
         if(Arrays.equals(entry.getValue(), parameterMap.get(entry.getKey()))) {
            parameterMap.remove(entry.getKey());
         }
      }

      model.addObject("postParams", parameterMap);
      return model;
   }

   private void addOpenGraphTags(ModelAndView model, String linkUri, String requestUri,
                                 String userAgent)
   {
      String title = null;
      String description = null;
      String url = null;

      if(isBotAgent(userAgent)) {
         // only open the viewsheet/report for bot agents that use the Open Graph tags as this can
         // be a little time-consuming
         try {
            int index = requestUri.indexOf("/app/viewer/view/");

            if(index >= 0) {
               String path = requestUri.substring(index + 17);
               url = linkUri + "app/viewer/view/" + path;
               String assetId;

               if(path.startsWith("global/")) {
                  String assetPath = path.substring(7);
                  assetId = "1^128^__NULL__^" + URLDecoder.decode(assetPath, StandardCharsets.UTF_8);
               }
               else if(path.startsWith("user/")) {
                  path = path.substring(5);
                  index = path.indexOf('/');
                  String user = path.substring(0, index);
                  String assetPath = path.substring(index + 1);
                  assetId = "4^128^" + user + "^" + URLDecoder.decode(assetPath, StandardCharsets.UTF_8);
               }
               else {
                  assetId = URLDecoder.decode(path, StandardCharsets.UTF_8);
               }

               AssetRepository repository = AssetUtil.getAssetRepository(false);
               AssetEntry entry = AssetEntry.createAssetEntry(assetId);

               if(entry != null) {
                  entry = repository.getAssetEntry(entry);
               }

               if(entry != null) {
                  title = entry.getAlias() == null ? entry.getPath() : entry.getAlias();
                  Viewsheet vs = (Viewsheet) repository.getSheet(entry, null, false, AssetContent.ALL);
                  description = vs.getViewsheetInfo().getDescription();

                  index = title.lastIndexOf('/');

                  if(index >= 0) {
                     title = title.substring(index + 1);
                  }
               }
            }
         }
         catch(Exception e) {
            LOG.error("Failed to get title and description for URL: {}", requestUri, e);
         }
      }

      if(title == null) {
         title = SreeEnv.getProperty("share.opengraph.title");
      }

      if(description == null) {
         description = SreeEnv.getProperty("share.opengraph.description");
      }

      if(url == null) {
         url = linkUri + "/app/portal";
      }

      OpenGraphImage image = getOpenGraphImage(linkUri);

      // Normalize the strings, these are used as attribute values, so the whitespace that we allow
      // is a space (ASCII 32) character.
      String siteName = SreeEnv.getProperty("share.opengraph.sitename").replaceAll("\\s", " ");
      title = title.replaceAll("\\s", " ");
      description = description.replaceAll("\\s", " ");

      model.addObject("openGraphSiteName", siteName);
      model.addObject("openGraphTitle", title);
      model.addObject("openGraphDescription", description);
      model.addObject("openGraphUrl", url);
      model.addObject("openGraphImage", image.url);
      model.addObject("openGraphImageWidth", image.width);
      model.addObject("openGraphImageHeight", image.height);
   }

   private void addAdditionalStyles(ModelAndView model, String linkUri) {
      addAdditionalTags(
         model, "additionalStyles", SreeEnv.getProperty("portal.additional.styles"), linkUri);
   }

   private void addAdditionalScripts(ModelAndView model, String linkUri) {
      addAdditionalTags(
         model, "additionalScripts", SreeEnv.getProperty("portal.additional.scripts"), linkUri);
   }

   /**
    * When {@code chat.app.server.url} is configured, injects the AI assistant web component
    * script so that the {@code <ai-assistant>} custom element is available in the portal.
    * The script is served by the assistant-client at
    * {@code {chatAppServerUrl}/web-component/ai-assistant.umd.js}.
    */
   @SuppressWarnings({ "unchecked", "rawtypes" })
   private void addChatAppScript(ModelAndView model) {
      String chatAppUrl = SreeEnv.getProperty(AIAssistantController.CHAT_APP_SERVER_URL);

      if(chatAppUrl == null || chatAppUrl.trim().isEmpty()) {
         return;
      }

      chatAppUrl = chatAppUrl.trim();

      if(chatAppUrl.endsWith("/")) {
         chatAppUrl = chatAppUrl.substring(0, chatAppUrl.length() - 1);
      }

      List scripts = (List) model.getModel()
         .computeIfAbsent("additionalScripts", k -> new ArrayList<>());
      scripts.add(chatAppUrl + "/web-component/ai-assistant.umd.js");
   }

   private void addDataSpaceTags(ModelAndView model, String linkUrl) {
      if(webAssetsExist) {
         addDataSpaceTags(model, linkUrl, "web-assets");
      }
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private void addDataSpaceTags(ModelAndView model, String linkUrl, String dir) {
      for(String file : dataSpace.list(dir)) {
         String path = dir + "/" + file; // NOSONAR

         if(dataSpace.isDirectory(path)) {
            addDataSpaceTags(model, linkUrl, path);
            return;
         }

         // strip off leading 'web-assets/'
         path = path.substring(11);

         if(file.toLowerCase().endsWith(".css")) {
            List tags = (List) model.getModel()
               .computeIfAbsent("additionalStyles", k -> new ArrayList<>());
            tags.add(linkUrl + path);
         }
         else if(file.toLowerCase().endsWith(".js")) {
            List tags = (List) model.getModel()
               .computeIfAbsent("additionalScripts", k -> new ArrayList<>());
            tags.add(linkUrl + path);
         }
      }
   }

   private void addAdditionalTags(ModelAndView model, String key, String property, String linkUri) {
      if(property != null) {
         List<String> tags = Arrays.stream(property.split(","))
            .map(String::trim)
            .filter(t -> !t.isEmpty())
            .map(t -> resolveTagLink(t, linkUri))
            .collect(Collectors.toList());

         model.addObject(key, tags);
      }
   }

   private String resolveTagLink(String href, String base) {
      try {
         URI uri = new URI(href);

         if(!uri.isAbsolute()) {
            return base + href;
         }
      }
      catch(URISyntaxException e) {
         LOG.debug("Invalid additional URL: {}", href, e);
      }

      return href;
   }

   private boolean isBotAgent(String userAgent) {
      if(userAgent == null) {
         return false;
      }

      String lower = userAgent.toLowerCase();

      for(String botAgent : botAgents) {
         if(lower.contains(botAgent)) {
            return true;
         }
      }

      return false;
   }

   private OpenGraphImage getOpenGraphImage(String linkUri) {
      String image = SreeEnv.getProperty("share.opengraph.image");
      URL resource = null;

      if(!StringUtils.hasText(image)) {
         image = "/images/logo.png";
      }

      if(image.startsWith("http")) {
         try {
            resource = URI.create(image).toURL();
         }
         catch(MalformedURLException e) {
            LOG.warn("Invalid image URL: {}", image, e);
         }
      }
      else if(image.startsWith("//")) {
         if(linkUri.startsWith("http")) {
            int index = linkUri.indexOf(':');

            if(index > 0) {
               try {
                  resource = URI.create(linkUri.substring(0, index + 1) + image).toURL();
               }
               catch(MalformedURLException e) {
                  throw new RuntimeException(e);
               }
            }
         }
      }
      else {
         if(image.startsWith("/")) {
            image = image.substring(1);
         }

         resource = getClass().getResource("/inetsoft/web/resources/" + image);
         image = linkUri + image;
      }

      imageLock.lock();

      try {
         if(cachedImage == null || !cachedImage.url.equals(image)) {
            int width = -1;
            int height = -1;

            if(resource != null) {
               try {
                  BufferedImage img = ImageIO.read(resource);
                  width = img.getWidth();
                  height = img.getHeight();
               }
               catch(Exception e) {
                  LOG.warn("Failed to resolve image URL: {}", resource, e);
               }
            }

            cachedImage =  new OpenGraphImage(image, width, height);
         }

         return cachedImage;
      }
      finally {
         imageLock.unlock();
      }
   }


   private final DataSpace dataSpace;
   private final boolean webAssetsExist;
   private final Lock imageLock = new ReentrantLock();
   private volatile OpenGraphImage cachedImage;
   private static final Set<String> botAgents =
      new HashSet<>(Arrays.asList("facebook", "slackbot", "linkedinbot", "twitterbot"));
   private static final Logger LOG = LoggerFactory.getLogger(HomePageController.class);

   private static final class OpenGraphImage {
      public OpenGraphImage(String url, int width, int height) {
         this.url = url;
         this.width = width;
         this.height = height;
      }

      private final String url;
      private final int width;
      private final int height;
   }
}
