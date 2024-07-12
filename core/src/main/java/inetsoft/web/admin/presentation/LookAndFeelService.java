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
package inetsoft.web.admin.presentation;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.FontFaceModel;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.css.CSSDictionary;
import inetsoft.web.admin.model.FileData;
import inetsoft.web.admin.presentation.model.*;
import inetsoft.web.notifications.NotificationService;
import inetsoft.web.viewsheet.Audited;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LookAndFeelService {
   /**
    * Method for getting the model dedicated to the look-and-feel of Portal
    *
    * @return LookAndFeelSettingsModel
    */
   public LookAndFeelSettingsModel getModel(Principal principal, boolean globalProperty) {
      PortalThemesManager manager = PortalThemesManager.getManager();
      manager.loadThemes(); // to get past change listener bug

      boolean asc = "Ascending".equals(SreeEnv.getProperty("repository.tree.sort"));
      boolean customLogoEnabled = SreeEnv.getBooleanProperty("portal.customLogo.enabled");
      boolean repositoryTree = manager.getReportListType() == 0;
      boolean expand = manager.isAutoExpand();
      boolean defaultLogo = !customLogoEnabled || manager.getLogoStyle() == null ||
         "null".equals(manager.getLogoStyle()) || manager.getLogoStyle().equals(PortalThemesManager.DEFAULT_LOGO); // "default"
      boolean defaultFavicon = !customLogoEnabled || manager.getFaviconStyle() == null ||
         manager.getFaviconStyle().equals(PortalThemesManager.DEFAULT_LOGO);
      boolean defaultViewsheet = !manager.getCSSStyle(); // method returns true for custom
      boolean defaultFont = !manager.getFontStyle(); // method returns true for custom

      String logo = "";
      String favicon = "";
      String viewsheet = "";
      List<String> userfonts = new ArrayList<>();
      List<FontFaceModel> fontFaces = new ArrayList<>();

      if(!defaultLogo) {
         logo = manager.getLogo();
      }

      if(!defaultFavicon) {
         favicon = manager.getFavicon();
      }

      if(globalProperty) {
         if(!defaultViewsheet) {
            viewsheet = manager.getCSSFile();
         }
      }
      else {
         String org = OrganizationManager.getCurrentOrgName();
         viewsheet = manager.getCssEntries().get(org);
         defaultViewsheet = viewsheet == null;

         if(defaultViewsheet) {
            viewsheet = "";
         }
      }

      if(!defaultFont) {
         userfonts = manager.getUserFonts();
         fontFaces = manager.getUserFontFaces();
      }

      return LookAndFeelSettingsModel
         .builder()
         .ascending(asc)
         .repositoryTree(repositoryTree)
         .expand(expand)
         .customLogoEnabled(customLogoEnabled)
         .defaultLogo(defaultLogo)
         .logoName(logo)
         .defaultFavicon(defaultFavicon)
         .faviconName(favicon)
         .defaultViewsheet(defaultViewsheet)
         .viewsheetName(viewsheet)
         .defaultFont(defaultFont)
         .userFonts(userfonts)
         .fontFaces(fontFaces)
         .viewsheetCSSEntries(
            manager.getCssEntries().entrySet().stream()
                   .map(this::createViewsheetCSSEntry)
                   .collect(Collectors.toList())
         )
         .vsEnabled(true)
         .build();
   }

   private ViewsheetCSSEntry createViewsheetCSSEntry(Map.Entry<String, String> entry) {
      return ViewsheetCSSEntry.builder()
         .identityName(entry.getKey())
         .fileName(entry.getValue())
         .build();
   }

   /**
    * Method for setting the look-and-feel of Portal
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Look and Feel",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(LookAndFeelSettingsModel model, boolean globalSettings) throws Exception {
      PortalThemesManager manager = PortalThemesManager.getManager();

      String sort = model.ascending() ? "Ascending" : "Descending";
      String logoStyle = model.defaultLogo() ? PortalThemesManager.DEFAULT_LOGO : PortalThemesManager.CUSTOM_LOGO;
      String faviconStyle = model.defaultFavicon() ? PortalThemesManager.DEFAULT_LOGO : PortalThemesManager.CUSTOM_LOGO;
      int repoTree = model.repositoryTree() ? 0 : 1;

      SreeEnv.setProperty("repository.tree.sort", sort);
      manager.setReportListType(repoTree);
      manager.setAutoExpand(model.expand());
      manager.setLogoStyle(logoStyle);
      manager.setFaviconStyle(faviconStyle);

      String dir = "portal";
      DataSpace dataSpace = DataSpace.getDataSpace();

      if(!model.defaultLogo() && model.logoFile() != null && model.logoFile().content() != null) {
         setLogo(model.logoFile(), dataSpace, dir);
      }

      if(!model.defaultFavicon() && model.faviconFile() != null &&
         model.faviconFile().content() != null)
      {
         setFavicon(model.faviconFile(), dataSpace, dir);
      }

      final boolean defaultViewsheet = model.defaultViewsheet();

      if(globalSettings) {
         manager.setCSSStyle(!defaultViewsheet);
      }

      if(model.userformatFile() != null && model.userformatFile().content() != null) {
         InputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(model.userformatFile().content()));

         try {
            dataSpace.withOutputStream(null, "userformat.xml", out -> Tool.fileCopy(in, out));
         }
         catch(Exception e) {
            LOG.error("Error saving file: " + e);
         }
      }

      if(!defaultViewsheet && model.viewsheetFile() != null &&
         model.viewsheetFile().content() != null)
      {
         setViewsheet(model.viewsheetFile(), dataSpace, dir, globalSettings);
      }
      else if(defaultViewsheet) {
         setViewsheet(null, dataSpace, dir, globalSettings);
      }

      // need to reset here so new entries can be rebuilt over any existing ones
      // e.g. user scope file removed, global scope file already exists
      CSSDictionary.resetDictionaryCache();

      updateFonts(
         model.userFonts(), model.fontFaces(), model.deleteFontFaces(), model.newFontFaces(),
         model.defaultFont(), dataSpace);

      manager.save();
      SreeEnv.save();
   }

   /**
    * Method for setting the look-and-feel of Portal
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Look and Feel",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void resetSettings(boolean globalSettings) throws Exception {
      PortalThemesManager manager = PortalThemesManager.getManager();
      Properties defaultProp = SreeEnv.getDefaultProperties();

      if(globalSettings) {
         String logoStyle = PortalThemesManager.DEFAULT_LOGO;
         String faviconStyle =PortalThemesManager.DEFAULT_LOGO;
         int repoTree = 0;

         SreeEnv.setProperty("repository.tree.sort", defaultProp.getProperty("repository.tree.sort"));
         manager.setReportListType(repoTree);
         manager.setAutoExpand(false);
         manager.setLogoStyle(logoStyle);
         manager.setFaviconStyle(faviconStyle);

         manager.setCSSStyle(false);
      }

      String dir = "portal";
      DataSpace dataSpace = DataSpace.getDataSpace();
      setViewsheet(null, dataSpace, dir, globalSettings);

      if(globalSettings) {
         // need to reset here so new entries can be rebuilt over any existing ones
         // e.g. user scope file removed, global scope file already exists
         CSSDictionary.resetDictionaryCache();

         updateFonts(
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            true, dataSpace);
         dataSpace.delete(null, "userformat.xml");
         resetUserFormatFile();
      }

      manager.save();
      SreeEnv.save();
   }

   private void resetUserFormatFile() {
      String home = ConfigurationContext.getContext().getHome();

      if(home == null) {
         return;
      }

      DataSpace space;

      try {
         space = DataSpace.getDataSpace();
      }
      catch(Exception ex) {
         LOG.error("Failed to get dataspace", ex);
         return;
      }

      String file = "userformat.xml";

      try(InputStream fis = ExtendedDecimalFormat.class.getResourceAsStream(file)) {
         if(fis != null) {
            space.withOutputStream(home, file, out -> IOUtils.copy(fis, out));
         }
      }
      catch(Throwable ex) {
         LOG.error("Failed to write user format file", ex);
      }
   }

   private void updateFonts(List<String> userFonts, List<FontFaceModel> fontFaces, List<FontFaceModel> deletedFontFaces,
                            List<UserFontModel> newFontFaces, boolean defaultFont, DataSpace space)
      throws Exception
   {
      PortalThemesManager ptm = PortalThemesManager.getManager();
      String fontPath = SreeEnv.getProperty("sree.home") + "/portal/font";

      if(defaultFont || userFonts.isEmpty()) {
         final List<String> fontFileNamePrefixesToDelete = ptm.getUserFontFaces().stream()
            .map(FontFaceModel::getFileNamePrefix)
            .collect(Collectors.toList());
         deleteFontFiles(fontFileNamePrefixesToDelete, fontPath, space);

         ptm.setUserFontFaces(Collections.emptyMap());
         ptm.setFontStyle(false);
      }
      else {
         if(deletedFontFaces != null && !deletedFontFaces.isEmpty()) {
            final List<String> deletedFontFacePaths = deletedFontFaces.stream()
               .map(FontFaceModel::getFileNamePrefix)
               .collect(Collectors.toList());
            deleteFontFiles(deletedFontFacePaths, fontPath, space);
         }

         if(newFontFaces != null && !newFontFaces.isEmpty()) {
            for(UserFontModel newFontFace : newFontFaces) {
               saveFontFaceFiles(newFontFace, fontPath, space);
            }
         }

         for(String userFont : userFonts) {
            List<FontFaceModel> matchingFontFaces = fontFaces.stream().filter(f -> f.fontName().equals(userFont))
               .collect(Collectors.toList());
            writeFontCSS(userFont, matchingFontFaces, fontPath, space);
         }

         if(!Tool.equals(userFonts, ptm.getUserFonts())) {
            notificationService.sendNotification(
               Catalog.getCatalog().getString("user.fontsChanged"));
         }

         final Map<String, List<FontFaceModel>> fontFaceMap = new LinkedHashMap<>();

         for(FontFaceModel fontFace : fontFaces) {
            fontFaceMap.computeIfAbsent(fontFace.fontName(), k -> new ArrayList<>()).add(fontFace);
         }

         ptm.setUserFontFaces(fontFaceMap);
         ptm.setFontStyle(true);
      }
   }

   private void deleteFontFiles(List<String> fontNames, String fontPath, DataSpace space) {
      if(fontNames == null || fontNames.isEmpty()) {
         return;
      }

      for(String fontName : fontNames) {
         if(space.exists(fontPath, fontName + ".ttf")) {
            space.delete(fontPath, fontName + ".ttf");
         }

         if(space.exists(fontPath, fontName + ".eot")) {
            space.delete(fontPath, fontName + ".eot");
         }

         if(space.exists(fontPath, fontName + ".woff")) {
            space.delete(fontPath, fontName + ".woff");
         }

         if(space.exists(fontPath, fontName + ".svg")) {
            space.delete(fontPath, fontName + ".svg");
         }

         if(space.exists(fontPath, fontName + ".css")) {
            space.delete(fontPath, fontName + ".css");
         }
      }
   }

   private void saveFontFaceFiles(UserFontModel fontFaceModel, String fontPath, DataSpace space)
      throws Exception
   {
      final String fileName = fontFaceModel.getFileNamePrefix();
      deleteFontFiles(Collections.singletonList(fileName), fontPath, space);

      Base64.Decoder decoder = Base64.getDecoder();

      String ttf = fileName + ".ttf";
      byte[] fontData = decoder.decode(fontFaceModel.ttf().content());
      writeStyleFile(fontData, space, fontPath, ttf);

      String eot = null;
      String woff = null;
      String svg = null;

      if(fontFaceModel.eot() != null) {
         eot = fileName + ".eot";
         fontData = decoder.decode(Objects.requireNonNull(fontFaceModel.eot()).content());
         writeStyleFile(fontData, space, fontPath, eot);
      }

      if(fontFaceModel.woff() != null) {
         woff = fileName + ".woff";
         fontData = decoder.decode(Objects.requireNonNull(fontFaceModel.woff()).content());
         writeStyleFile(fontData, space, fontPath, woff);
      }

      if(fontFaceModel.svg() != null) {
         svg = fileName + ".svg";
         fontData = decoder.decode(Objects.requireNonNull(fontFaceModel.svg()).content());
         writeStyleFile(fontData, space, fontPath, svg);
      }
   }

   private void writeFontCSS(String family, List<FontFaceModel> fontFaces, String dir, DataSpace space)
      throws IOException
   {
      final String file = family + ".css";

      try(DataSpace.Transaction tx = space.beginTransaction();
          OutputStream output = tx.newStream(dir, file))
      {
         PrintWriter pw = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
         String path = "../portal/font";

         for(FontFaceModel fontFace : fontFaces) {
            final String fileNamePrefix = fontFace.getFileNamePrefix();

            pw.println("@font-face {");
            pw.format("  font-family: %s;%n", family);
            final String eot = fileNamePrefix + ".eot";

            if(space.exists(dir, eot)) {
               pw.format("  src: url('%s/%s?jbj2y');%n", path, eot);
            }

            List<String> urls = new ArrayList<>();

            if(space.exists(dir, eot)) {
               urls.add(String.format(
                  "url('%s/%s?jbj21y#iefix') format('embedded-opentype')", path, eot));
            }

            final String ttf = fileNamePrefix + ".ttf";

            if(space.exists(dir, ttf)) {
               urls.add(String.format("url('%s/%s?jbj21y') format('truetype')", path, ttf));
            }

            final String woff = fileNamePrefix + ".woff";

            if(space.exists(dir, woff)) {
               urls.add(String.format("url('%s/%s?jbj21y') format('woff')", path, woff));
            }

            final String svg = fileNamePrefix + ".svg";

            if(space.exists(dir, svg)) {
               urls.add(String.format("url('%s/%s?jbj21y#%s') format('svg')", path, svg, family));
            }

            pw.format("  src: %s;%n", String.join(",\n    ", urls));
            final String fontWeight = fontFace.fontWeight();

            if(fontWeight != null && !fontWeight.isEmpty()) {
               pw.format("  font-weight: %s;%n", fontWeight);
            }

            final String fontStyle = fontFace.fontStyle();

            if(fontStyle != null && !fontStyle.isEmpty()) {
               pw.format("  font-style: %s;%n", fontStyle);
            }

            pw.format("}%n");
            pw.flush();
         }

         tx.commit();
      }
   }

   private void setLogo(FileData logo, DataSpace space, String directory) throws Exception {
      PortalThemesManager manager = PortalThemesManager.getManager();

      if(logo != null) {
         byte[] logoData = Base64.getDecoder().decode(logo.content());

         int dotIndex = logo.name().lastIndexOf(".");
         String logoName = "logo" + (dotIndex >= 0 ? logo.name().substring(dotIndex) : ".gif");

         InputStream in = new ByteArrayInputStream(logoData);

         try {
            space.withOutputStream(directory, logoName, out -> Tool.copyTo(in, out));
         }
         catch(Throwable ex) {
            throw new Exception("Failed to write logo", ex);
         }

         manager.setLogo(directory + "/" + logoName);
      }
      else { // in the case that the logo file does not exist, we go back to default logo style and gif
         String modernDir = "portal/images/modern";

         manager.setLogoStyle(PortalThemesManager.DEFAULT_LOGO);
         manager.setLogo(modernDir + "/logo.gif");
      }
   }

   private void setFavicon(FileData favi, DataSpace space, String directory) throws Exception {
      PortalThemesManager manager = PortalThemesManager.getManager();

      if(favi != null) {
         byte[] faviData = Base64.getDecoder().decode(favi.content());

         int dotIndex = favi.name().lastIndexOf(".");
         String faviconName = "favicon" + (dotIndex >= 0 ? favi.name().substring(dotIndex) : ".gif");

         InputStream in = new ByteArrayInputStream(faviData);

         try {
            space.withOutputStream(directory, faviconName, out -> Tool.copyTo(in, out));
         }
         catch(Throwable ex) {
            throw new Exception("Failed to write icon", ex);
         }

         manager.setFavicon(directory + "/" + faviconName);
      }
      else { // in the case that the favicon file does not exist, we go back to default favicon style
         String modernDir = "portal/images/modern";

         manager.setFaviconStyle(PortalThemesManager.DEFAULT_LOGO);
         manager.setFavicon(modernDir + "/favicon.png");
      }
   }

   private void setViewsheet(FileData vs, DataSpace space, String directory, boolean globalSettings) throws Exception {
      PortalThemesManager manager = PortalThemesManager.getManager();
      String cssName = "format.css";
      byte[] cssData;

      if(vs != null) {
         cssData = Base64.getDecoder().decode(vs.content());
      }
      else { // in the case that the css file does not exist, we go back to default css style
         cssData = new byte[0];
      }

      if(globalSettings) {
         writeStyleFile(cssData, space, directory, cssName);
         manager.setCSSFile(directory + "/" + cssName);
      }
      else {
         if(vs != null) {
            cssName = vs.name();
            directory += "/" + OrganizationManager.getCurrentOrgName();
            writeStyleFile(cssData, space, directory, cssName);
            CSSDictionary.parseCSSFile(cssName, cssData);
            manager.addCSSEntry(OrganizationManager.getCurrentOrgName(), OrganizationManager.getCurrentOrgName() + "/" + cssName);
         }
         else {
            final String org = OrganizationManager.getCurrentOrgName();
            final String file = manager.getCssEntries().get(org);

            if(file != null) {
               manager.removeCSSEntry(org);
               space.delete(directory, file);
            }
         }
      }
   }

   private void writeStyleFile(byte[] data, DataSpace space, String directory, String fileName)
      throws Exception
   {
      InputStream in = new ByteArrayInputStream(data);

      try {
         space.withOutputStream(directory, fileName, out -> Tool.copyTo(in, out));
      }
      catch(Throwable ex) {
         throw new Exception("Failed to write style file: " + fileName, ex);
      }
   }

   @Autowired
   private NotificationService notificationService;

   private static final Logger LOG = LoggerFactory.getLogger(LookAndFeelService.class);
}