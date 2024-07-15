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
import inetsoft.sree.internal.HTMLUtil;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.portal.PortalWelcomePage;
import inetsoft.sree.web.HttpServiceRequest;
import inetsoft.sree.web.HttpServiceResponse;
import inetsoft.util.*;
import inetsoft.web.reportviewer.service.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.*;
import java.security.Principal;
import java.util.Hashtable;

@Controller
public class PortalThemeController {
   /**
    * Get logo image
    */
   @GetMapping("/portal/logo")
   public void getPortalLogo(HttpServletResponse response, Principal principal) throws Exception {
      PortalThemesManager manager = PortalThemesManager.getManager();

      // For modern tab style, use the theme path
      String logoPath = manager.getLogo();
      String logoClassPath = "/inetsoft/sree/portal/images/modern/logo.png";

      getIcon(response, logoPath, manager.getLogoStyle(), logoClassPath, principal);
   }

   /**
    * Get font.css
    */
   @GetMapping("/portal/font.css")
   public void getFontCSS(HttpServletResponse response) throws Exception {
      response.addHeader("Cache-Control", "public, max-age=2592000");
      response.setContentType("text/css");
      String fontPath = SreeEnv.getProperty("sree.home") + "/portal/font";
      DataSpace space = DataSpace.getDataSpace();

      try(PrintWriter writer = response.getWriter()) {
         for(String font : PortalThemesManager.getManager().getUserFonts()) {
            String css = font + ".css";

            if(space.exists(fontPath, css)) {
               try(InputStream input = space.getInputStream(fontPath, css)) {
                  IOUtils.copy(input, writer);
               }
            }
         }
      }
   }

   /**
    * Get a user font file.
    *
    * @param fontName the font file name.
    * @param response the HTTP response object.
    */
   @GetMapping("/portal/font/{fontName:.+}")
   public void getFontFile(@PathVariable("fontName") String fontName, HttpServletResponse response)
      throws Exception
   {
      response.addHeader("Cache-Control", "public, max-age=2592000");
      response.setContentType("application/octet-stream");
      String fontPath = SreeEnv.getProperty("sree.home") + "/portal/font";
      DataSpace space = DataSpace.getDataSpace();

      try(InputStream inp = space.getInputStream(fontPath, fontName)) {
         writeFile(inp, space, response);
      }
   }

   /**
    * Get favicon image
    */
   @GetMapping("/portal/favicon")
   public void getFavicon(HttpServletResponse response, Principal principal) throws Exception
   {
      PortalThemesManager manager = PortalThemesManager.getManager();
      response.addHeader("Cache-Control", "public, max-age=2592000");
      getIcon(
         response, manager.getFavicon(), manager.getFaviconStyle(),
         "/inetsoft/sree/adm/markup/favicon.ico", principal);
   }

   @GetMapping("/portal/source/welcomepage")
   public void getResourcePage(Principal principal,
                               @HttpServletRequestWrapper HttpServiceRequest srequest,
                               HttpServletResponse response) throws Exception
   {
      PortalThemesManager manager = PortalThemesManager.getManager();
      PortalWelcomePage welcomePage = manager.getWelcomePage();
      String sourceName = welcomePage.getData();
      String fontFamily = manager.getFontFamily();
      String theme = PortalThemesManager.getColorTheme();

      Hashtable<Object, Object> dict = new Hashtable<>();
      dict.put("html.character.encode", SreeEnv.getProperty("html.character.encode"));
      dict.put("cached", !sourceName.contains("ssl_blank.html") + "");

      if(fontFamily != null) {
         dict.put("font-family", fontFamily);
         dict.put("THEME", theme);
         dict.put("FONTFAMILY", fontFamily);
      }

      if(principal != null) {
         dict.put("PRINCIPAL", "true");
         dict.put("principal", Tool.byteEncode(principal.getName()));
      }

      response.setContentType("text/html");
      PrintWriter writer = HTMLUtil.getWriter(srequest, new HttpServiceResponse(response));
      HTMLUtil.copyTemplate(sourceName, writer, dict, principal);
      writer.flush();
      writer.close();
   }

   /**
    * Get logo image.
    */
   private void getIcon(HttpServletResponse response, String logo, String logoStyle, String cpath,
                        Principal principal) throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);
      logo = logo != null ? logo : "portal/" + cpath.substring(cpath.lastIndexOf("/") + 1);

      if(PortalThemesManager.DEFAULT_LOGO.equals(logoStyle) ||
         logo.toLowerCase().endsWith(".gif"))
      {
         response.setContentType("image/gif");
      }
      else if(logo.toLowerCase().endsWith(".jpg")) {
         response.setContentType("image/jpeg");
      }
      else if(logo.toLowerCase().endsWith(".png")) {
         response.setContentType("image/png");
      }
      else if(logo.toLowerCase().endsWith(".bmp")) {
         response.setContentType("image/bmp");
      }
      else if(logo.toLowerCase().endsWith(".ico")) {
         response.setContentType("image/x-icon");
      }
      else {
         throw new Exception(catalog.getString("em.portal.logoError", logo));
      }

      DataSpace space = DataSpace.getDataSpace();

      if(PortalThemesManager.DEFAULT_LOGO.equals(logoStyle) ||
         !space.exists(null, logo))
      {
         try(InputStream res = SreeEnv.class.getResourceAsStream(cpath)) {
            space.withOutputStream(null, logo, stream -> Tool.copyTo(res, stream));
         }
         catch(Throwable exc) {
            throw new Exception(
               "Failed to copy logo file " + cpath + " to " + logo, exc);
         }
      }

      try(InputStream inp = space.getInputStream(null, logo)) {
         writeFile(inp, space, response);
      }
   }

   private void writeFile(InputStream inp, DataSpace space, HttpServletResponse response)
      throws Exception
   {

      // @by jasons read resource into memory buffer to avoid file lock contention
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      Tool.copyTo(inp, output);
      ByteArrayInputStream buffer =
         new ByteArrayInputStream(Tool.convertUserByte(output.toByteArray()));

      try(OutputStream out = response.getOutputStream()) {
         Tool.copyTo(buffer, out);
      }
   }
}
