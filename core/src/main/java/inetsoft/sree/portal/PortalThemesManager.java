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
package inetsoft.sree.portal;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.util.*;
import inetsoft.util.gui.GuiTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.xpath.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A portal themes manager is a manager of portal themes. It is used to read &
 * write information of themes, and is the only access to portal themes.
 *
 * @version 8.5, 07/12/2006
 * @author InetSoft Technology Corp
 */
@SingletonManager.Singleton(PortalThemesManager.Reference.class)
public class PortalThemesManager implements XMLSerializable, AutoCloseable {
   /**
    * Help button.
    */
   public static final int HELP_BUTTON = 0x01;
   /**
    * Preferences button.
    */
   public static final int PREFERENCES_BUTTON = 0x02;
   /**
    * Logout button.
    */
   public static final int LOGOUT_BUTTON = 0x04;
   /**
    * Search button.
    */
   public static final int SEARCH_BUTTON = 0x08;
   /**
    * Home button.
    */
   public static final int HOME_BUTTON = 0x10;
   /**
    * Tree type report list.
    */
   public static final int TREE = 0;
   /**
    * List type report list.
    */
   public static final int LIST = 1;
   /**
    * The Default style for logo.
    */
   public static final String DEFAULT_LOGO = "default";
   /**
    * The Custom style for logo.
    */
   public static final String CUSTOM_LOGO = "custom";

   /**
    * Return a portal themes manager.
    */
   public static synchronized PortalThemesManager getManager() {
      return SingletonManager.getInstance(PortalThemesManager.class);
   }

   /**
    * Add a portal tab at specified index.
    * @param tab the specified portal tab.
    * @param index the specified index.
    */
   public void addPortalTab(PortalTab tab, int index) {
      if(index < 0 || index > portalTabs.size()) {
         return;
      }

      portalTabs.add(index, tab);
   }

   /**
    * Remove a portal tab in the specified index.
    * @param index the specified tab index.
    * @return the removed portal tab.
    */
   public PortalTab removePortalTab(int index) {
      if(index < 0 || index >= portalTabs.size()) {
         return null;
      }

      return portalTabs.remove(index);
   }

   /**
    * Get a portal tab in the specified index.
    * @param index the specified index.
    * @return a portal tab in the specified index.
    */
   public PortalTab getPortalTab(int index) {
      if(index < 0 || index >= portalTabs.size()) {
         return null;
      }

      return portalTabs.get(index);
   }

   /**
    * Get portal tabs count.
    * @return portal tabs count.
    */
   public int getPortalTabsCount() {
      return portalTabs.size();
   }

   /**
    * Get copyright.
    * @return copyright.
    */
   public String getCopyright() {
      return copyright;
   }

   /**
    * Set copyright.
    * @param copyright the specified copyright.
    */
   public void setCopyright(String copyright) {
      this.copyright = copyright;
   }

   /**
    * Get font family.
    * @return the specified font family.
    */
   public String getFontFamily() {
      return fontFamily;
   }

   /**
    * Set font family.
    * @param fontFamily the specified font family.
    */
   public void setFontFamily(String fontFamily) {
      this.fontFamily = fontFamily;
   }

   /**
    * Get logo.
    * @return the specified logo.
    */
   public String getLogo() {
      return logo;
   }

   /**
    * Set logo.
    * @param logo the specified logo.
    */
   public void setLogo(String logo) {
      this.logo = logo;
   }

   /**
    * Get logo style. true = custom
    * @return the specified logo style.
    */
   public boolean isLogoStyle() {
      return logoStyle;
   }

   public boolean hasCustomLogo(String org) {
      boolean orgLogoExists = false;

      if(org != null) {
         Map<String, String> logoEntries = getLogoEntries();
         orgLogoExists = !Tool.isEmptyString(logoEntries.get(org));
      }

      return orgLogoExists || (isLogoStyle() && !Tool.isEmptyString(logo));
   }

   /**
    * Set logo style. true = custom
    * @param logoStyle the specified logo style.
    */
   public void setLogoStyle(boolean logoStyle) {
      this.logoStyle = logoStyle;
   }

   /**
    * Get favorite icon.
    */
   public String getFavicon() {
      return favicon;
   }

   /**
    * Set favorite icon.
    */
   public void setFavicon(String favicon) {
      this.favicon = favicon;
   }

   /**
    * Get favorite icon style.
    */
   public boolean isFaviconStyle() {
      return faviconStyle;
   }

   /**
    * Set favorite icon style.
    */
   public void setFaviconStyle(boolean faviconStyle) {
      this.faviconStyle = faviconStyle;
   }

   /**
    * Get css.
    * @return the specified css.
    */
   public String getCSSFile() {
      return cssfile;
   }

   /**
    * Set css.
    * @param css the specified css.
    */
   public void setCSSFile(String css) {
      this.cssfile = css;
   }

   /**
    * Get css style.
    * @return the specified css style.
    */
   public boolean getCSSStyle() {
      return cssStyle;
   }

   /**
    * @return map of user -> css file name (no path)
    */
   public Map<String, String> getCssEntries() {
      return Collections.unmodifiableMap(cssEntries);
   }

   public void addCSSEntry(String identityName, String fileName) {
      cssEntries.put(identityName, fileName);
   }

   public void removeCSSEntry(String identityName) {
      cssEntries.remove(identityName);
   }

   public void clearCSSMapping() {
      cssEntries.clear();
   }

   /**
    * @return map of user -> favicon file name (no path)
    */
   public Map<String, String> getFaviconEntries() {
      return Collections.unmodifiableMap(faviconEntries);
   }

   public void addFaviconEntry(String identityName, String fileName) {
      faviconEntries.put(identityName, fileName);
   }

   public void removeFaviconEntry(String identityName) {
      faviconEntries.remove(identityName);
   }

   public void clearFaviconMapping() {
      faviconEntries.clear();
   }

   /**
    * @return map of user -> logo file name (no path)
    */
   public Map<String, String> getLogoEntries() {
      return Collections.unmodifiableMap(logoEntries);
   }

   public void addLogoEntry(String identityName, String fileName) {
      logoEntries.put(identityName, fileName);
   }

   public void removeLogoEntry(String identityName) {
      logoEntries.remove(identityName);
   }

   public void clearLogoMapping() {
      logoEntries.clear();
   }

   /**
    * Set css style.
    * @param cssStyle the specified css style.
    */
   public void setCSSStyle(boolean cssStyle) {
      this.cssStyle = cssStyle;
   }

   /**
    * Get font style.
    * @return the specified font style.
    */
   public boolean getFontStyle() {
      return fontStyle;
   }

   /**
    * Set font style.
    * @param fontStyle the specified font style.
    */
   public void setFontStyle(boolean fontStyle) {
      this.fontStyle = fontStyle;
   }

   /**
    * Get user fonts.
    * @return the user fonts.
    */
   public List<String> getUserFonts() {
      return userFonts;
   }

   /**
    * Set user fonts.
    * @param userFonts user fonts.
    */
   private void setUserFonts(List<String> userFonts) {
      this.userFonts = userFonts;
   }

   public List<FontFaceModel> getUserFontFaces() {
      return userFontFaces.values().stream().flatMap(List::stream).collect(Collectors.toList());
   }

   public void setUserFontFaces(Map<String, List<FontFaceModel>> userFontFaces) {
      this.userFontFaces = userFontFaces;
      updateUserFontFaces();
   }

   private void updateUserFontFaces() {
      setUserFonts(new ArrayList<>(userFontFaces.keySet()));
   }

   /**
    * Get report list type.
    * @return report list type.
    */
   public int getReportListType() {
      return reportListType;
   }

   /**
    * Set report list type.
    * @param reportListType the specified report list type.
    */
   public void setReportListType(int reportListType) {
      this.reportListType = reportListType;
   }

   /**
    * Get if auto expand all nodes of the report tree.
    * @return <code>true</code> if auto expand, <code>false</code> otherwise.
    */
   public boolean isAutoExpand() {
      return autoExpand;
   }

   /**
    * Set auto expand the report tree.
    * @param autoExpand the specified value of auto expand.
    */
   public void setAutoExpand(boolean autoExpand) {
      this.autoExpand = autoExpand;
   }

   /**
    * Get welcome page.
    * @return welcome page.
    */
   public PortalWelcomePage getWelcomePage() {
      return welcomePage;
   }

   /**
    * Set welcome page.
    * @param welcomePage the specified welcome page.
    */
   public void setWelcomePage(PortalWelcomePage welcomePage) {
      this.welcomePage = welcomePage;
   }

   /**
    * Set welcome page for special organization.
    *
    * @param orgId the organization id.
    * @param welcomePage the specified welcome page.
    */
   public void setWelcomePage(String orgId, PortalWelcomePage welcomePage) {
      welcomePageEntries.put(orgId, welcomePage);
   }

   /**
    * Get Welcome page for the special organization.
    *
    * @param orgId the organization id.
    */
   public PortalWelcomePage getWelcomePage(String orgId) {
      return welcomePageEntries.get(orgId);
   }

   /**
    * Remove the welcome page for the special organization.
    *
    * @param orgId the organization id.
    */
   public void removeWelcomePage(String orgId) {
      welcomePageEntries.remove(orgId);
   }

   public void clearOrgWelcomePage() {
      welcomePageEntries.clear();
   }

   /**
    * Check if the specified button is visible.
    * @param btnValue the specified button value.
    * @return <code>true</code> if visible, <code>false</code> otherwise.
    */
   public boolean isButtonVisible(int btnValue) {
      return (btnValue & buttonVisible) == btnValue;
   }

   /**
    * Set visible button.
    * @param visible the specified visible button.
    */
   public void setButtonVisible(int btnValue, boolean visible) {
      if(visible) {
         buttonVisible |= btnValue;
      }
      else {
         buttonVisible &= ~btnValue;
      }
   }

   /**
    * Get color scheme. Currently hardcoded to 'granite'. Keep in place
    * if we decide to support more themes in the future.
    */
   public static String getColorTheme() {
      return "granite";
   }


   public List<PortalTab> getPortalTabs() {
      return portalTabs;
   }

   public void setPortalTabs(List<PortalTab> portalTabs) {
      this.portalTabs = portalTabs;
   }

   @Override
   public void close() throws Exception {
      dmgr.clear();
   }

   public static void clear() {
      SingletonManager.reset(PortalThemesManager.class);
   }

   /**
    * Save portal themes to a .xml file.
    */
   public void save() {
      String name = SreeEnv.getPath("portal.themes.file", "portalthemes.xml");
      DataSpace space = DataSpace.getDataSpace();

      try {
         dmgr.removeChangeListener(space, null, name, changeListener);

         try(DataSpace.Transaction tx = space.beginTransaction();
             OutputStream out = tx.newStream(null, name))
         {

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

            writer.println("<?xml version=\"1.0\"?>");
            writer.println("<PortalThemes>");
            writer.println("<Version>" + FileVersions.PORTAL_THEMES + "</Version>");
            writeXML(writer);
            writer.println("</PortalThemes>");
            writer.flush();

            tx.commit();
         }
      }
      catch(Throwable ex) {
         LOG.error("Failed to save portal themes file", ex);
      }
      finally {
         if(space != null) {
            dmgr.addChangeListener(space, null, name, changeListener);
         }
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writeCDATA(writer, "reportListType", reportListType + "");
      writeCDATA(writer, "autoExpand", autoExpand + "");
      writeCDATA(writer, "buttonVisible", buttonVisible + "");
      writeCDATA(writer, "logoStyle", logoStyle + "");
      writeCDATA(writer, "cssStyle", cssStyle + "");
      writeCDATA(writer, "fontFamily", fontFamily);
      writeCDATA(writer, "copyright", copyright);
      writeCDATA(writer, "cssfile", cssfile);
      writeCDATA(writer, "fontStyle", fontStyle);
      writeCDATA(writer, "logo", logo);
      writeCDATA(writer, "logoStyle", logoStyle);
      writeCDATA(writer, "favicon", favicon);
      writeCDATA(writer, "faviconStyle", faviconStyle);

      if(!userFontFaces.isEmpty()) {
         writer.println("<userFontFaces>");

         for(FontFaceModel fontFace : getUserFontFaces()) {
            writer.println("<fontFace>");

            writeCDATA(writer, "userFont", fontFace.fontName());
            writeCDATA(writer, "identifier", fontFace.identifier());
            writeCDATA(writer, "fontWeight", fontFace.fontWeight());
            writeCDATA(writer, "fontStyle", fontFace.fontStyle());

            writer.println("</fontFace>");
         }

         writer.println("</userFontFaces>");
      }

      if(portalTabs.size() > 0) {
         writer.println("<portalTabs>");

         for(PortalTab portalTab : portalTabs) {
            portalTab.writeXML(writer);
         }

         writer.println("</portalTabs>");
      }

      if(cssEntries.size() > 0) {
         writer.println("<cssEntries>");

         for(Map.Entry<String, String> entry : cssEntries.entrySet()) {
            writer.println("<cssEntry>");
            writeCDATA(writer, "identityName", entry.getKey());
            writeCDATA(writer, "cssFile", entry.getValue());
            writer.println("</cssEntry>");
         }

         writer.println("</cssEntries>");
      }

      if(faviconEntries.size() > 0) {
         writer.println("<faviconEntries>");

         for(Map.Entry<String, String> entry : faviconEntries.entrySet()) {
            writer.println("<faviconEntry>");
            writeCDATA(writer, "identityName", entry.getKey());
            writeCDATA(writer, "faviconFile", entry.getValue());
            writer.println("</faviconEntry>");
         }

         writer.println("</faviconEntries>");
      }

      if(logoEntries.size() > 0) {
         writer.println("<logoEntries>");

         for(Map.Entry<String, String> entry : logoEntries.entrySet()) {
            writer.println("<logoEntry>");
            writeCDATA(writer, "identityName", entry.getKey());
            writeCDATA(writer, "logoFile", entry.getValue());
            writer.println("</logoEntry>");
         }

         writer.println("</logoEntries>");
      }

      if(welcomePage != null) {
         welcomePage.writeXML(writer);
      }

      if(welcomePageEntries != null) {
         writer.println("<welcomePageEntries>");

         for(Map.Entry<String, PortalWelcomePage> entry : welcomePageEntries.entrySet()) {
            writer.print("<orgPortalWelcomePage orgId=\"");
            writer.print(entry.getKey());
            writer.println("\">");
            entry.getValue().writeXML(writer);
            writer.println("</orgPortalWelcomePage>");
         }

         writer.println("</welcomePageEntries>");
      }
   }

   /**
    * Method to parse an xml segment.
    * @param tag the specified xml element.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      Element node = Tool.getChildNodeByTagName(tag, "reportListType");

      if(node != null) {
         reportListType = Integer.parseInt(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(tag, "autoExpand");
      autoExpand = node != null && "true".equals(Tool.getValue(node));

      node = Tool.getChildNodeByTagName(tag, "buttonVisible");

      if(node != null) {
         buttonVisible = Integer.parseInt(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(tag, "fontFamily");

      if(node != null) {
         fontFamily = Tool.getValue(node);
      }

      node = Tool.getChildNodeByTagName(tag, "copyright");

      if(node != null) {
         copyright = Tool.getValue(node);
      }

      node = Tool.getChildNodeByTagName(tag, "logo");

      if(node != null) {
         logo = Tool.getValue(node);
      }

      node = Tool.getChildNodeByTagName(tag, "logoStyle");

      if(node != null) {
         logoStyle = "true".equals(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(tag, "favicon");

      if(node != null) {
         favicon = Tool.getValue(node);
      }

      node = Tool.getChildNodeByTagName(tag, "faviconStyle");

      if(node != null) {
         faviconStyle = "true".equals(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(tag, "cssfile");

      if(node != null) {
         cssfile = Tool.getValue(node);
      }

      node = Tool.getChildNodeByTagName(tag, "cssStyle");

      if(node != null) {
         cssStyle = "true".equals(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(tag, "fontStyle");

      if(node != null) {
         fontStyle = "true".equals(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(tag, "userFontFaces");

      if(node != null) {
         userFontFaces = new LinkedHashMap<>();
         NodeList list = Tool.getChildNodesByTagName(node, "fontFace");

         for(int i = 0; i < list.getLength(); i++) {
            Element elem = (Element) list.item(i);
            String userFont = Tool.getValue(Tool.getChildNodeByTagName(elem, "userFont"));
            String identifier = Tool.getValue(Tool.getChildNodeByTagName(elem, "identifier"));
            String fontWeight = Tool.getValue(Tool.getChildNodeByTagName(elem, "fontWeight"));
            String fontStyle = Tool.getValue(Tool.getChildNodeByTagName(elem, "fontStyle"));

            userFontFaces.computeIfAbsent(userFont, k -> new ArrayList<>())
               .add(FontFaceModel.builder()
                       .fontName(userFont)
                       .identifier(identifier == null ? FontFaceModel.EMPTY_IDENTIFIER : identifier)
                       .fontWeight(fontWeight)
                       .fontStyle(fontStyle)
                       .build());
         }
      }

      // Feature #56765: userFonts is no longer written to XML, but check existence to convert it to a font face
      // representation.
      node = Tool.getChildNodeByTagName(tag, "userFonts");

      if(node != null) {
         NodeList list = Tool.getChildNodesByTagName(node, "userFont");

         for(int i = 0; i < list.getLength(); i++) {
            Element elem = (Element) list.item(i);
            Element font = Tool.getChildNodeByTagName(elem, "userFont" + i);
            String userFont = Tool.getValue(font);

            if(!userFontFaces.containsKey(userFont)) {
               userFontFaces.computeIfAbsent(userFont, k -> new ArrayList<>())
                  .add(FontFaceModel.builder()
                          .emptyFontFace(userFont)
                          .build());
            }
         }
      }

      updateUserFontFaces();
      node = Tool.getChildNodeByTagName(tag, "portalTabs");

      if(node != null) {
         portalTabs.clear();
         NodeList list = Tool.getChildNodesByTagName(node, "portalTab");

         for(int i = 0; i < list.getLength(); i++) {
            Element elem = (Element) list.item(i);
            PortalTab ptab = new PortalTab();
            ptab.parseXML(elem);
            portalTabs.add(ptab);
         }
      }

      node = Tool.getChildNodeByTagName(tag, "cssEntries");

      if(node != null) {
         cssEntries.clear();
         final NodeList list = Tool.getChildNodesByTagName(node, "cssEntry");

         for(int i = 0; i < list.getLength(); i++) {
            final Node cssEntry = list.item(i);

            if(cssEntry != null) {
               final Element nameNode = Tool.getChildNodeByTagName(cssEntry, "identityName");
               final Element fileNode = Tool.getChildNodeByTagName(cssEntry, "cssFile");
               final String identityName = Tool.getValue(nameNode);
               final String cssFile = Tool.getValue(fileNode);

               if(identityName != null && cssFile != null) {
                  cssEntries.put(identityName, cssFile);
               }
            }
         }
      }

      node = Tool.getChildNodeByTagName(tag, "faviconEntries");

      if(node != null) {
         faviconEntries.clear();
         final NodeList list = Tool.getChildNodesByTagName(node, "faviconEntry");

         for(int i = 0; i < list.getLength(); i++) {
            final Node favEntry = list.item(i);

            if(favEntry != null) {
               final Element nameNode = Tool.getChildNodeByTagName(favEntry, "identityName");
               final Element fileNode = Tool.getChildNodeByTagName(favEntry, "faviconFile");
               final String identityName = Tool.getValue(nameNode);
               final String favFile = Tool.getValue(fileNode);

               if(identityName != null && favFile != null) {
                  faviconEntries.put(identityName, favFile);
               }
            }
         }
      }

      node = Tool.getChildNodeByTagName(tag, "logoEntries");

      if(node != null) {
         logoEntries.clear();
         final NodeList list = Tool.getChildNodesByTagName(node, "logoEntry");

         for(int i = 0; i < list.getLength(); i++) {
            final Node logoEntry = list.item(i);

            if(logoEntry != null) {
               final Element nameNode = Tool.getChildNodeByTagName(logoEntry, "identityName");
               final Element fileNode = Tool.getChildNodeByTagName(logoEntry, "logoFile");
               final String identityName = Tool.getValue(nameNode);
               final String logoFile = Tool.getValue(fileNode);

               if(identityName != null && logoFile != null) {
                  logoEntries.put(identityName, logoFile);
               }
            }
         }
      }

      node = Tool.getChildNodeByTagName(tag, "portalWelcomePage");

      if(node != null) {
         welcomePage = new PortalWelcomePage();
         welcomePage.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(tag, "welcomePageEntries");

      if(node != null) {
         welcomePageEntries.clear();
         final NodeList list = Tool.getChildNodesByTagName(node, "orgPortalWelcomePage");

         for(int i = 0; i < list.getLength(); i++) {
            final Node orgEntry = list.item(i);

            if(orgEntry != null) {
               final Element page = Tool.getChildNodeByTagName(orgEntry, "portalWelcomePage");
               final String orgId = Tool.getAttribute((Element) orgEntry, "orgId");

               if(page == null || orgId == null) {
                  continue;
               }

               PortalWelcomePage welcomePage = new PortalWelcomePage();
               welcomePage.parseXML(page);
               setWelcomePage(orgId, welcomePage);
            }
         }
      }
   }

   /**
    * Write CDATA.
    */
   private void writeCDATA(PrintWriter writer, String key, Object value) {
      if(value != null) {
         writer.println("<" + key + "><![CDATA[" + value + "]]></" + key + ">");
      }
   }

   /**
    * Build up the portal manager by parse a .xml file.
    */
   public void loadThemes() {
      DataSpace space = DataSpace.getDataSpace();
      String name = SreeEnv.getPath("portal.themes.file", "portalthemes.xml");
      boolean saveFile = false;

      try {
         Document doc = null;

         try(InputStream repository = space.getInputStream(null, name)) {
            if(repository != null) {
               doc = Tool.parseXML(repository);
               XPath xpath = XPathFactory.newInstance().newXPath();
               String versionXmlPath = "//PortalThemes/Version";
               Double oldVersion = (Double) xpath.evaluate(versionXmlPath, doc, XPathConstants.NUMBER);
               TransformerManager.getManager(TransformerManager.PORTAL).transform(doc);
               Double newVersion = (Double) xpath.evaluate(versionXmlPath, doc, XPathConstants.NUMBER);

               if(newVersion != null && newVersion >= 9.5 && oldVersion < newVersion) {
                  saveFile = true;
               }
            }
         }

         if(doc == null) {
            try(InputStream repository =
                   SUtil.getInputStream("/inetsoft/sree/portal/portalthemes.xml"))
            {
               doc = Tool.parseXML(repository);
               saveFile = true;
            }
         }

         Element node = Tool.getFirstElement(doc);

         if(node != null) {
            parseXML(node);
         }

         dmgr.addChangeListener(space, null, name, changeListener);
      }
      catch(Exception ex) {
         LOG.error("Failed to load portal themes file: " + name, ex);
      }
      finally {
         initUserFonts();
      }

      if(saveFile) {
         save();
      }
   }

   private void initUserFonts() {
      for(FontFaceModel fontFace : PortalThemesManager.getManager().getUserFontFaces()) {
         Font font = GuiTool.getUserFont(fontFace.fontName(), fontFace.getFileNamePrefix());

         if(font != null) {
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
         }
      }
   }

   /**
    * Data change listener.
    */
   private final DataChangeListener changeListener = e -> {
      LOG.debug(e.toString());
      reset();

      try {
         loadThemes();
      }
      catch(Exception ex) {
         LOG.error("Failed to load portal themes", ex);
      }
   };

   /**
    * Reset variables before reload.
    */
   private void reset() {
      reportListType = 0;
      buttonVisible = 0;
      copyright = null;
      fontFamily = null;
      logo = null;
      logoStyle = false;
      favicon = null;
      faviconStyle = false;
      cssfile = null;
      cssStyle = false;
      fontStyle = false;
      userFonts = new ArrayList<>();
      welcomePage = null;
      portalTabs.clear();
   }

   private int reportListType;
   private int buttonVisible;
   private boolean autoExpand = false;
   private String fontFamily;
   private boolean logoStyle;
   private String logo;
   private boolean faviconStyle;
   private String favicon;
   private boolean cssStyle;
   private String cssfile;
   private boolean fontStyle;
   private List<String> userFonts = new ArrayList<>();
   private Map<String, List<FontFaceModel>> userFontFaces = new LinkedHashMap<>();
   // identityName -> cssFile
   private final Map<String, String> cssEntries = new ConcurrentHashMap<>();
   private final Map<String, String> faviconEntries = new ConcurrentHashMap<>();
   private final Map<String, String> logoEntries = new ConcurrentHashMap<>();
   private final Map<String, PortalWelcomePage> welcomePageEntries = new ConcurrentHashMap<>();
   private PortalWelcomePage welcomePage;
   private List<PortalTab> portalTabs = new ArrayList<>();
   private String copyright;
   private final DataChangeListenerManager dmgr = new DataChangeListenerManager();

   private static final Logger LOG = LoggerFactory.getLogger(PortalThemesManager.class);

   @SingletonManager.ShutdownOrder()
   public static final class Reference extends SingletonManager.Reference<PortalThemesManager> {
      @Override
      public synchronized PortalThemesManager get(Object... parameters) {
         if(manager == null) {
            manager = new PortalThemesManager();
            manager.loadThemes();
         }

         return manager;
      }

      @Override
      public void dispose() {
         if(manager != null) {
            try {
               manager.close();
            }
            catch(Exception e) {
               LOG.warn("Failed to close theme manager", e);
            }

            manager = null;
         }
      }

      private PortalThemesManager manager;
   }
}
