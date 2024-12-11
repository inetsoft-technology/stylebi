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
package inetsoft.util.css;

import com.helger.commons.io.IHasInputStream;
import com.helger.css.ECSSVersion;
import com.helger.css.decl.*;
import com.helger.css.decl.shorthand.CSSShortHandDescriptor;
import com.helger.css.decl.shorthand.CSSShortHandRegistry;
import com.helger.css.parser.CSSParseHelper;
import com.helger.css.property.ECSSProperty;
import com.helger.css.propertyvalue.CCSSValue;
import com.helger.css.propertyvalue.CSSSimpleValueWithUnit;
import com.helger.css.reader.CSSReader;
import com.helger.css.utils.*;
import com.helger.css.writer.CSSWriterSettings;
import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.util.*;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * CSS dictionary stores information about all css.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class CSSDictionary {
   /**
    * Fixes CSS file locations that incorrectly use absolute file system paths to use data space
    * relative paths.
    *
    * @param cssLocation the location to fix.
    *
    * @return the fixed location or {@code null} if the location is invalid.
    */
   public static String fixLocation(String cssLocation) {
      if(cssLocation == null || cssLocation.isEmpty()) {
         return null;
      }

      if(!SystemUtils.IS_OS_WINDOWS && cssLocation.matches("^[a-zA-Z]:.+")) {
         // File.isAbsolute() will return false on non-windows with a path like C:\...
         return null;
      }

      FileSystemService fsService = FileSystemService.getInstance();
      File file = fsService.getFile(cssLocation);

      if(file.isAbsolute()) {
         Path home = fsService.getPath(ConfigurationContext.getContext().getHome());
         Path path = file.toPath();
         File temp = null;

         try {
            temp = home.relativize(path).toFile();
         }
         catch(IllegalArgumentException ex) {
            // not relative to the home directory, this isn't actually supported, but is an
            // artifact of the bug where the studio used a regular file chooser for the CSS
            // location
            return null;
         }

         if(temp.isAbsolute()) {
            // not relative to the home directory, this isn't actually supported, but is an
            // artifact of the bug where the studio used a regular file chooser for the CSS
            // location
            return null;
         }
         else {
            return home.relativize(path).toString().replace('\\', '/');
         }
      }

      return file.getPath().replace('\\', '/');
   }

   /**
    * Get css dictionary FOR VIEWSHEETS ONLY
    * @return css dictionary if any, null otherwise.
    */
   public static CSSDictionary getDictionary() {
      return getDictionary("portal", "format.css", false);
   }

   public static CSSDictionary getDictionary(String cssLocation) {
      String location = fixLocation(cssLocation);

      if(location == null) {
         return CSSDictionary.getDictionary(null, null, true);
      }

      final int lastIndex = location.lastIndexOf('/');
      String cssDir = "/";
      String cssFile;

      if(lastIndex != -1) {
         cssDir = location.substring(0, lastIndex);
         cssFile = location.substring(lastIndex + 1);
      }
      else {
         cssFile = location;
      }

      return CSSDictionary.getDictionary(cssDir, cssFile, true);
   }

   /**
    * Get css dictionary.
    * @param cssDir The directory where the css file is located.
    * @param cssFile The name of the css file.
    * @return css dictionary if any, null otherwise.
    */
   public static CSSDictionary getDictionary(String cssDir, String cssFile, boolean isReport) {
      final MapKey key;
      final Principal contextPrincipal = ThreadContext.getContextPrincipal();
      key = new MapKey(cssDir, cssFile, isReport, OrganizationManager.getInstance().getCurrentOrgID());

      Map<MapKey, CSSDictionary> map;
      CSSDictionary dictionary;

      DICTIONARIES_LOCK.readLock().lock();

      try {
         map = ConfigurationContext.getContext().get(DICTIONARIES_KEY);
      }
      finally {
         DICTIONARIES_LOCK.readLock().unlock();
      }

      if(map == null) {
         DICTIONARIES_LOCK.writeLock().lock();

         try {
            map = ConfigurationContext.getContext().get(DICTIONARIES_KEY);

            if(map == null) {
               ConfigurationContext.getContext().put(
                  DICTIONARIES_KEY, map = new HashMap<>());
            }
         }
         finally {
            DICTIONARIES_LOCK.writeLock().unlock();
         }
      }

      DICTIONARIES_LOCK.readLock().lock();

      try {
         dictionary = map.get(key);
      }
      finally {
         DICTIONARIES_LOCK.readLock().unlock();
      }

      if(dictionary != null) {
         long now = System.currentTimeMillis();

         // optimization, avoid repeatedly checking for last modified time. allow 3s for
         // css dictionary change to become effective.
         if(dictionary.lastCheck + 3000 < now) {
            long lastModified = getLastModified(cssDir, cssFile, isReport);
            dictionary.lastCheck = now;

            if(lastModified != dictionary.getLastModifiedTime()) {
               dictionary = null;
               map.remove(key);
            }
         }
      }

      if(dictionary == null) {
         DICTIONARIES_LOCK.writeLock().lock();

         try {
            dictionary = map.get(key);

            if(dictionary == null) {
               final List<String> cssFiles = new ArrayList<>();

               if(!isReport) {
                  if(contextPrincipal instanceof SRPrincipal) {
                     final PortalThemesManager manager = PortalThemesManager.getManager();
                     final Map<String, String> cssEntries = manager.getCssEntries();
                     final String orgFile = cssEntries.get(OrganizationManager.getInstance().getCurrentOrgID());

                     if(orgFile != null) {
                        cssFiles.add(orgFile);
                     }
                  }
               }

               dictionary = map.values().stream()
                  .filter(dict -> dict.isSame(cssDir, cssFile, isReport, cssFiles))
                  .findFirst().orElse(null);

               try {
                  if(dictionary == null) {
                     dictionary = new CSSDictionary(cssDir, cssFile, isReport, cssFiles);
                     map.put(key, dictionary);
                  }
               }
               catch(Exception e) {
                  LOG.error("Failed to load CSS from " + cssFile + " in " + cssDir, e);
               }
            }
         }
         finally {
            DICTIONARIES_LOCK.writeLock().unlock();
         }
      }

      return dictionary;
   }

   // get css file last modified time
   private static long getLastModified(String cssDir, String cssFile, boolean isReport) {
      DataSpace space = DataSpace.getDataSpace();

      if(cssDir != null && cssFile != null) {
         return space.getLastModified(cssDir, cssFile);
      }

      if(isReport) {
         String[] defaultCSS = getDefaultCSSLocation();

         if(defaultCSS != null && space.exists(defaultCSS[0], defaultCSS[1])) {
            return space.getLastModified(defaultCSS[0], defaultCSS[1]);
         }
      }

      return 0;
   }

   // get css default file location
   private static String[] getDefaultCSSLocation() {
      String loc = fixLocation(SreeEnv.getProperty("css.location"));

      if(loc != null) {
         final int lastIndex = loc.lastIndexOf('/');
         String defaultCSSDir = null;
         String defaultCSSFile;

         if(lastIndex != -1) {
            defaultCSSDir = loc.substring(0, lastIndex);
            defaultCSSFile = loc.substring(lastIndex + 1);
         }
         else {
            defaultCSSFile = loc;
         }

         return new String[] {defaultCSSDir, defaultCSSFile};
      }

      return null;
   }

   /**
    * Parse and cache a CSS file
    */
   public static void parseCSSFile(String fileName, byte[] cssData) {
      IHasInputStream inputStream = new IHasInputStream() {
         @Override
         public InputStream getInputStream() {
            return new ByteArrayInputStream(cssData);
         }

         @Override
         public boolean isReadMultiple() {
            return true;
         }
      };

      final CascadingStyleSheet cascadingStyleSheet =
         CSSReader.readFromStream(inputStream,
                                  Charset.defaultCharset(),
                                  getCSSVersion());
      styleSheets.put(fileName, cascadingStyleSheet);
   }

   /**
    * Invalidate the in-memory map and force dictionaries to rebuild on next run.
    */
   public static void resetDictionaryCache() {
      DICTIONARIES_LOCK.writeLock().lock();
      styleSheets.clear();

      try {
         final Map<MapKey, CSSDictionary> map =
            ConfigurationContext.getContext().get(DICTIONARIES_KEY);

         if(map != null) {
            map.clear();
         }
      }
      finally {
         DICTIONARIES_LOCK.writeLock().unlock();
      }
   }

   /**
    * Constructor.
    * @param cssDir The directory where the css file is located.
    * @param cssFile The name of the css file.
    */
   private CSSDictionary(final String cssDir, final String cssFile, boolean isReport,
                         List<String> otherFiles)
   {
      this.cssDir = cssDir;
      this.cssFile = cssFile;
      this.isReport = isReport;
      this.otherFiles = otherFiles;
      init();
   }

   /**
    * Initialize css dictionary.
    */
   @SuppressWarnings("UnusedParameters")
   protected synchronized void init() {
      // clear contains out-of-date css
      clear();

      // clear data change listeners
      dmgr.clear();

      try {
         DataSpace space = DataSpace.getDataSpace();

         // load global default css
         CascadingStyleSheet defaultCSS = parse(
            "/", "inetsoft/util/css/defaults.css", true);

         CascadingStyleSheet defaultReportCSS = null;

         // load default css file for all reports if specified
         if(isReport) {
            String[] defaultCSSLoc = getDefaultCSSLocation();

            if(defaultCSSLoc != null) {
               String defaultCSSDir = defaultCSSLoc[0];
               String defaultCSSFile = defaultCSSLoc[1];

               if(space.exists(defaultCSSDir, defaultCSSFile)) {
                  this.ts = space.getLastModified(defaultCSSDir, defaultCSSFile);
                  dmgr.addChangeListener(space, defaultCSSDir, defaultCSSFile, changeListener);
                  defaultReportCSS = parse(defaultCSSDir, defaultCSSFile, false);
               }
            }
         }

         if(cssDir != null && cssFile != null) {
            if(!space.exists(cssDir, cssFile)) {
               try {
                  space.withOutputStream(cssDir, cssFile, OutputStream::flush);
               }
               catch(Exception invalidPath) {
                  // invalid file path, indicate load via resource
                  cssDir = "/";
               }
            }

            this.ts = space.getLastModified(cssDir, cssFile);
            dmgr.addChangeListener(space, cssDir, cssFile, changeListener);
            css = parse(cssDir, cssFile, false);
         }

         if(defaultReportCSS != null && !defaultReportCSS.equals(css)) {
            for(ICSSTopLevelRule rule : defaultReportCSS.getAllStyleRules()) {
               assert defaultCSS != null;
               defaultCSS.addRule(rule);
            }
         }

         if(css != null) {
            for(ICSSTopLevelRule rule : css.getAllStyleRules()) {
               assert defaultCSS != null;
               defaultCSS.addRule(rule);
            }

            if(!isReport && defaultCSS != null) {
               for(String otherFile : otherFiles) {
                  CascadingStyleSheet mappedCSS = null;

                  if(styleSheets.containsKey(otherFile)) {
                     mappedCSS = styleSheets.get(otherFile);
                  }
                  else if(space.exists(cssDir, otherFile)) {
                     mappedCSS = parse(cssDir, otherFile, false);
                  }

                  if(mappedCSS != null) {
                     for(ICSSTopLevelRule rule : mappedCSS.getAllStyleRules()) {
                        defaultCSS.addRule(rule);
                     }
                  }
               }
            }
         }

         css = defaultCSS;
         initCSSClasses();
         initCSSIDs();
      }
      catch(Exception ex) {
         LOG.error("Failed to read {} from {}", cssFile, cssDir, ex);
      }
   }

   /**
    * This method gets the InputStream to the css style and parse it
    * to a StyleSheet object.
    */
   private CascadingStyleSheet parse(final String dir, final String file,
                                     final boolean isResource)
   {
      try {
         IHasInputStream inputStream = new IHasInputStream() {
            @Override
            public InputStream getInputStream() {
               DataSpace space = DataSpace.getDataSpace();

               try {
                  if(isResource) {
                     return CSSDictionary.class.getResourceAsStream(dir + file);
                  }
                  else {
                     return space.getInputStream(dir, file);
                  }
               }
               catch(IOException e) {
                  LOG.error("Failed to parse CSS: " + dir + file, e);
                  return null;
               }
            }

            @Override
            public boolean isReadMultiple() {
               return true;
            }
         };

         return CSSReader.readFromStream(inputStream,
            Charset.defaultCharset(), getCSSVersion());
      }
      catch(Exception e) {
         LOG.info("Exception: ", e);
      }

      return null;
   }

   /**
    * Get the alignment according to the css type, id and class.
    */
   public int getAlignment(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);

      if(style != null && style.isAlignmentDefined()) {
         return style.getAlignment();
      }

      return -1;
   }

   /**
    * Get the wrapping property according to the css type, id and class.
    */
   public boolean isWrapping(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);
      return style != null && style.isWrappingDefined() && style.isWrapping();
   }

   /**
    * Get the foreground according to the css type, id and class.
    */
   public Color getForeground(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);

      if(style != null && style.isForegroundDefined()) {
         return style.getForeground();
      }

      return null;
   }

   /**
    * Get the background according to the css type, id and class.
    */
   public Color getBackground(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);

      if(style != null && style.isBackgroundDefined()) {
         return style.getBackground();
      }

      return null;
   }

   /**
    * Get the font according to the css type, id and class.
    */
   public Font getFont(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);

      if(style != null && style.isFontDefined()) {
         return style.getFont();
      }

      return null;
   }

   /**
    * Get the borders according to the css type, id and class.
    */
   public Insets getBorders(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);

      if(style != null && style.isBorderDefined()) {
         return style.getBorders();
      }

      return null;
   }

   /**
    * Get the border colors according to the css type, id and class.
    */
   public BorderColors getBorderColors(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);

      if(style != null && style.isBorderColorDefined()) {
         return style.getBorderColors();
      }

      return null;
   }

   /**
    * Get alpha
    */
   public int getAlpha(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);

      if(style != null && style.isAlphaDefined()) {
         return style.getAlpha();
      }

      return 100;
   }

   /**
    * Get padding
    */
   public Insets getPadding(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);

      if(style != null && style.isPaddingDefined()) {
         return style.getPadding();
      }

      return null;
   }

   /**
    * Get width
    */
   public int getWidth(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);

      if(style != null && style.isWidthDefined()) {
         return style.getWidth();
      }

      return 0;
   }

   /**
    * Get height
    */
   public int getHeight(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);

      if(style != null && style.isHeightDefined()) {
         return style.getHeight();
      }

      return 0;
   }

   /**
    * Get visible
    */
   public boolean isVisible(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);

      if(style != null && style.isVisibleDefined()) {
         return style.isVisible();
      }

      return true;
   }

   /**
    * Check if alpha is defined in css file.
    */
   public boolean isAlphaDefined(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);
      return style != null && style.isAlphaDefined();
   }

   /**
    * Get corner radius
    */
   public int getBorderRadius(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);

      if(style != null && style.isBorderRadiusDefined()) {
         return style.getBorderRadius();
      }

      return 0;
   }

   public boolean isBorderRadiusDefined(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);
      return style != null && style.isBorderRadiusDefined();
   }

   /**
    * Check if alignment is defined in css file.
    */
   public boolean isAlignmentDefined(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);
      return style != null && style.isAlignmentDefined();
   }

   /**
    * Check if foreground is defined in css file.
    */
   public boolean isForegroundDefined(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);
      return style != null && style.isForegroundDefined();
   }

   /**
    * Check if background is defined in css file.
    */
   public boolean isBackgroundDefined(CSSParameter ... cssParams)
   {
      CSSStyle style = getStyle(cssParams);
      return style != null && style.isBackgroundDefined();
   }

   /**
    * Check if wrapping is defined in css file.
    */
   public boolean isWrappingDefined(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);
      return style != null && style.isWrappingDefined();
   }

   /**
    * Check if font is defined in css file.
    */
   public boolean isFontDefined(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);
      return style != null && style.isFontDefined();
   }

   /**
    * Check if border is defined in css file.
    */
   public boolean isBorderDefined(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);
      return style != null && style.isBorderDefined();
   }

   /**
    * Check if border color is defined in css file.
    */
   public boolean isBorderColorDefined(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);
      return style != null && style.isBorderColorDefined();
   }

   /**
    * Check if padding is defined in css file.
    */
   public boolean isPaddingDefined(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);
      return style != null && style.isPaddingDefined();
   }

   /**
    * Check if width is defined in css file.
    */
   public boolean isWidthDefined(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);
      return style != null && style.isWidthDefined();
   }

   /**
    * Check if height is defined in css file.
    */
   public boolean isHeightDefined(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);
      return style != null && style.isHeightDefined();
   }

   /**
    * Check if visibility is defined in css file.
    */
   public boolean isVisibleDefined(CSSParameter ... cssParams) {
      CSSStyle style = getStyle(cssParams);
      return style != null && style.isVisibleDefined();
   }

   /**
    * Check if the type is present in CSS file
    */
   public boolean checkPresent(String type) {
      if(css != null) {
         Boolean present = selectorPresentCache.get(type);

         if(present != null) {
            return present;
         }

         List<CSSStyleRule> rules = css.getAllStyleRules();
         present = rules.toString().contains("value=" + type);

         selectorPresentCache.put(type, present);
         return present;
      }

      return false;
   }

   /**
    * Get all css ids applicable to the specified type.
    */
   public String[] getCSSIDs(String type) {
      return getSelectors(type, idMap);
   }

   /**
    * Initialize css id map
    */
   private void initCSSIDs() {
      idMap.clear();

      if(css == null) {
         return;
      }

      for(CSSStyleRule rule : css.getAllStyleRules()) {
         for(CSSSelector selector : rule.getAllSelectors()) {
            String type = "*";

            for(ICSSSelectorMember member : selector.getAllMembers()) {
               if(member instanceof CSSSelectorSimpleMember) {
                  CSSSelectorSimpleMember simpleMember =
                     (CSSSelectorSimpleMember) member;

                  if(simpleMember.isElementName()) {
                     type = simpleMember.getValue();
                  }
                  else if(simpleMember.isHash()) {
                     addSelector(type, simpleMember, idMap);
                  }
               }
               else if(member instanceof ECSSSelectorCombinator) {
                  type = "*";
               }
            }
         }
      }
   }

   /**
    * Get all css classes applicable to the specified type.
    */
   public String[] getCSSClasses(String type) {
      return getSelectors(type, classMap);
   }

   /**
    * Initialize css class map
    */
   private void initCSSClasses() {
      classMap.clear();

      if(css == null) {
         return;
      }

      for(CSSStyleRule rule : css.getAllStyleRules()) {
         for(CSSSelector selector : rule.getAllSelectors()) {
            String type = "*";

            for(ICSSSelectorMember member : selector.getAllMembers()) {
               if(member instanceof CSSSelectorSimpleMember) {
                  CSSSelectorSimpleMember simpleMember =
                     (CSSSelectorSimpleMember) member;

                  if(simpleMember.isElementName()) {
                     type = simpleMember.getValue();
                  }
                  else if(simpleMember.isClass()) {
                     addSelector(type, simpleMember, classMap);
                  }
               }
               else if(member instanceof ECSSSelectorCombinator) {
                  type = "*";
               }
            }
         }
      }
   }

   private String[] getSelectors(String type, Map<String, Set<String>> selectorMap) {
      Set<String> typeSet = selectorMap.get(type);
      Set<String> globalSet = selectorMap.get("*");
      Set<String> resultSet = new TreeSet<>();

      if(typeSet != null) {
         resultSet.addAll(typeSet);
      }

      if(globalSet != null) {
         resultSet.addAll(globalSet);
      }

      return resultSet.toArray(new String[0]);
   }

   private void addSelector(String type, CSSSelectorSimpleMember simpleMember,
                            Map<String, Set<String>> selectorMap)
   {
      String id = simpleMember.getValue().substring(1);
      Set<String> typeSet = selectorMap.get(type);

      if(typeSet == null) {
         typeSet = new HashSet<>();
      }

      typeSet.add(id);
      selectorMap.put(type, typeSet);
   }

   /**
    * Get all possible attribute values for the given attribute name that
    * matches the given type and attributes.
    * e.g. If type == "ChartPalette", attributes == [name='Default'],
    *      and attrName == "index" then this would match selectors such as
    *      ChartPalette[name='Default'][index='1'] and return all
    *      possible values of attribute "index"
    */
   public Set<String> getCSSAttributeValues(String type, Map<String, String> attributes,
                                            String attrName)
   {
      Set<String> attrValues = new LinkedHashSet<>();

      if(css == null) {
         return attrValues;
      }

      for(CSSStyleRule rule : css.getAllStyleRules()) {
         for(CSSSelector selector : rule.getAllSelectors()) {
            boolean typeMatches = false;
            boolean attrMatches = true;
            String attrValue = null;
            String selectorType = "*";

            for(ICSSSelectorMember member : selector.getAllMembers()) {
               if(member instanceof CSSSelectorSimpleMember) {
                  CSSSelectorSimpleMember simpleMember =
                     (CSSSelectorSimpleMember) member;

                  if(simpleMember.isElementName()) {
                     selectorType = simpleMember.getValue();
                  }
               }
               else if(member instanceof CSSSelectorAttribute) {
                  CSSSelectorAttribute attrMember =
                     (CSSSelectorAttribute) member;

                  if(attrMember.getAttrName().equals(attrName)) {
                     attrValue = CSSParseHelper.
                        extractStringValue(attrMember.getAttrValue());
                  }
                  else {
                     String attrValue0 = CSSParseHelper.
                        extractStringValue(attrMember.getAttrValue());

                     if(attributes != null &&
                        attributes.get(attrMember.getAttrName()) != null &&
                        !attributes.get(attrMember.getAttrName()).equals(
                           attrValue0))
                     {
                        attrMatches = false;
                     }
                  }
               }
               else if(member instanceof ECSSSelectorCombinator) {
                  if(typeMatches && attrMatches && attrValue != null) {
                     attrValues.add(attrValue);
                  }

                  typeMatches = false;
                  attrMatches = true;
                  attrValue = null;
                  selectorType = "*";
               }

               if(selectorType.equals(type)) {
                  typeMatches = true;
               }
            }

            if(typeMatches && attrMatches && attrValue != null) {
               attrValues.add(attrValue);
            }
         }
      }

      return attrValues;
   }

   /**
    * Listener added to be notified of the css file has changed in the
    * data space.
    */
   public DataChangeListener changeListener = e -> {
      try {
         // reinitialize css dictionary
         init();
      }
      catch(Exception ex) {
         LOG.error("Failed to re-initialize CSS", ex);
      }
   };

   /**
    * Clear the reference to the last parameters
    */
   public synchronized void clear() {
      css = null;
      styleCache.clear();
      selectorPresentCache.clear();
   }

   /**
    * Get last modified timestamp.
    */
   public long getLastModifiedTime() {
      return ts;
   }

   /**
    * Finds out if the given selectors match the specified parameters.
    * If so then it returns the highest match amongst all the selectors.
    * If no matches are found then it returns a null.
    *
    * @param selectors list of selectors of the given rule
    * @param cssParams CSS Parameters
    * @param ruleIndex position of the rule in the css file
    * @return the highest match
    */
   private RuleWeight matches(List<CSSSelector> selectors,
      CSSParameter[] cssParams, int ruleIndex)
   {
      RuleWeight highestMatch = null;

      selectorLoop:
      for(CSSSelector selector : selectors) {
         cssParamsLoop:
         for(int i = 0; i < cssParams.length; i++) {
            // overall counts of the whole rule
            int typeCount = 0;
            int classCount = 0;
            int attrCount = 0;
            int idCount = 0;

            boolean typeMatches = false;
            boolean classMatches = false;
            boolean idMatches = false;
            boolean matchedAllAttr = true;

            // determines whether type, class, or id is specified
            // in the current selector
            boolean typeSpecified = false;
            boolean classSpecified = false;
            boolean idSpecified = false;

            int curIndex = i;
            CSSParameter cssParam = cssParams[curIndex];

            if(cssParam == null) {
               continue;
            }

            String[] cssClasses = cssParam.getCSSClass() != null ?
               cssParam.getCSSClass().split(",") : new String[0];

            for(ICSSSelectorMember member : selector.getAllMembers()) {
               if(member instanceof CSSSelectorSimpleMember) {
                  CSSSelectorSimpleMember simpleMember =
                     (CSSSelectorSimpleMember) member;

                  if(simpleMember.isHash()) {
                     idSpecified = true;
                     String value = simpleMember.getValue().substring(1);

                     if(Tool.equals(value, cssParam.getCSSID(), false)) {
                        idCount++;
                        idMatches = true;
                     }
                  }
                  else if(simpleMember.isClass()) {
                     // all classes need to match for classMatches to be true
                     String value = simpleMember.getValue().substring(1);

                     if(classMatches(value, cssClasses)) {
                        classCount++;

                        if(!classSpecified) {
                           classMatches = true;
                        }
                     }
                     else {
                        classMatches = false;
                     }

                     classSpecified = true;
                  }
                  else if(simpleMember.isElementName()) {
                     typeSpecified = true;
                     String value = simpleMember.getValue();

                     if(Tool.equals(value, cssParam.getCSSType(), false)) {
                        typeCount++;
                        typeMatches = true;
                     }
                     else if(Tool.equals(value, "*", false)) {
                        typeMatches = true;
                     }
                  }
               }
               else if(member instanceof CSSSelectorAttribute) {
                  CSSSelectorAttribute attrMember =
                     (CSSSelectorAttribute) member;
                  boolean matchedAttr = false;

                  if(cssParam.getCSSAttributes() != null) {
                     for(Map.Entry<String, String> attr :
                        cssParam.getCSSAttributes().entrySet())
                     {
                        if(attr != null && attr.getKey() != null &&
                           attrMember != null && attr.getValue() != null &&
                           Tool.equals(attr.getKey(), attrMember.getAttrName(), false)
                           && Tool.equals(attr.getValue(),
                           CSSParseHelper.extractStringValue(attrMember.getAttrValue()), false))
                        {
                           matchedAttr = true;
                           attrCount++;
                           break;
                        }
                     }
                  }

                  if(!matchedAttr) {
                     matchedAllAttr = false;
                  }
               }
               else if(member instanceof ECSSSelectorCombinator) {
                  boolean matchedCurrent = isValidMatch(typeMatches,
                     classMatches, idMatches, matchedAllAttr, typeSpecified,
                     classSpecified, idSpecified);

                  if(!matchedCurrent) {
                     continue cssParamsLoop;
                  }
                  else if(curIndex == cssParams.length - 1) {
                     continue selectorLoop;
                  }

                  cssParam = cssParams[++curIndex];
                  typeMatches = false;
                  classMatches = false;
                  idMatches = false;
                  matchedAllAttr = true;
                  typeSpecified = false;
                  classSpecified = false;
                  idSpecified = false;
               }
            }

            if(isValidMatch(typeMatches, classMatches,
               idMatches, matchedAllAttr, typeSpecified, classSpecified,
               idSpecified) && curIndex == cssParams.length - 1)
            {
               RuleWeight match = new RuleWeight(typeCount,
                  classCount + attrCount, idCount, ruleIndex);

               if(match.compareTo(highestMatch) > 0) {
                  highestMatch = match;
               }

               break;
            }
         }
      }

      return highestMatch;
   }

   /**
    * Checks if the following css class matches any from the given list of css classes
    * @param cssClass css class to check
    * @param cssClasses list of css classes from css parameter
    * @return true if class matches; false otherwise
    */
   private boolean classMatches(String cssClass, String[] cssClasses) {
      for(String cls : cssClasses) {
         if(Tool.equals(cssClass, cls, false)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Determines whether a match is valid
    */
   private boolean isValidMatch(boolean typeMatches, boolean classMatches,
      boolean idMatches, boolean attrMatches, boolean typeSpecified,
      boolean classSpecified, boolean idSpecified)
   {
      // type[name=value]#id.class, type#id.class
      if(typeSpecified && idSpecified && classSpecified && attrMatches &&
         typeMatches && idMatches && classMatches)
      {
         return true;
      }
      // type[name=value].class, type.class
      else if(!idSpecified && typeSpecified && typeMatches && attrMatches &&
         classSpecified && classMatches)
      {
         return true;
      }
      // type[name=value]#id, type#id
      else if(!classSpecified && typeSpecified && typeMatches && attrMatches &&
         idSpecified && idMatches)
      {
         return true;
      }
      // #id[name=value].class, #id.class
      else if(!typeSpecified && idSpecified && idMatches && attrMatches &&
         classSpecified && classMatches)
      {
         return true;
      }
      // type[name=value], type
      else if(!idSpecified && !classSpecified && typeSpecified &&
         typeMatches && attrMatches)
      {
         return true;
      }
      // #id[name=value], #id
      else if(!typeSpecified && !classSpecified && idSpecified &&
         idMatches && attrMatches)
      {
         return true;
      }
      // .class[name=value], .class
      else if(!typeSpecified && !idSpecified && classSpecified &&
         classMatches && attrMatches)
      {
         return true;
      }

      return false;
   }

   /**
    * Returns a style that matches the given parameters
    *
    * @return style that matches the parameters
    */
   public CSSStyle getStyle(CSSParameter ... cssParams) {
      CascadingStyleSheet css = this.css;

      if(css == null) {
         return null;
      }

      StyleKey styleKey = new StyleKey(cssParams);
      CSSStyleValue result = styleCache.get(styleKey);

      if(result != null) {
         return result.getStyle();
      }

      TreeMap<RuleWeight, List<CSSDeclaration>> matchingRules = new TreeMap<>();
      List<CSSStyleRule> rules = css.getAllStyleRules();

      for(int i = 0; i < rules.size(); i++) {
         CSSStyleRule rule = rules.get(i);
         RuleWeight match = matches(rule.getAllSelectors(), cssParams, i);

         if(match != null) {
            matchingRules.put(match, rule.getAllDeclarations());
         }
      }

      HashMap<Object, CSSDeclaration> properties = new LinkedHashMap<>();

      // add all the variables first
      for(Map.Entry<RuleWeight, List<CSSDeclaration>> entry :
         matchingRules.entrySet())
      {
         for(CSSDeclaration declaration : entry.getValue()) {
            if(declaration.getProperty().startsWith("--")) {
               putProperties(properties, declaration);
            }
         }
      }

      for(Map.Entry<RuleWeight, List<CSSDeclaration>> entry :
         matchingRules.entrySet())
      {
         for(CSSDeclaration declaration : entry.getValue()) {
            if(!declaration.getProperty().startsWith("--")) {
               putProperties(properties, declaration);
            }
         }
      }

      CSSStyle style = getCSSStyle(properties);

      styleCache.put(styleKey, new CSSStyleValue(style));
      return style;
   }

   /**
    * Check if this dictionary is from the same css files.
    */
   public boolean isSame(String cssDir, String cssFile, boolean isReport,
                         List<String> otherFiles)
   {
      return Objects.equals(cssDir, this.cssDir) && Objects.equals(cssFile, this.cssFile) &&
         Objects.equals(otherFiles, this.otherFiles) && isReport == this.isReport;
   }

   /**
    * Constructs a CSSStyle object given a list of all the decomposed
    * properties.
    *
    * @param properties contains decomposed properties
    * @return CSSStyle object
    */
   private CSSStyle getCSSStyle(HashMap<Object, CSSDeclaration> properties) {
      if(properties.size() == 0) {
         return null;
      }

      CSSStyle style = new CSSStyle();

      parseBackground(properties, style);
      parseForeground(properties, style);
      parseFont(properties, style);
      parseAlignment(properties, style);
      parseBorderStyle(properties, style);
      parseBorderColors(properties, style);
      parseAlpha(properties, style);
      parseWrapping(properties, style);
      parseCustomProperties(properties, style);
      parseBorderRadius(properties, style);
      parsePadding(properties, style);
      parseWidth(properties, style);
      parseHeight(properties, style);
      parseVisible(properties, style);

      return style;
   }

   private void parseBackground(HashMap<Object, CSSDeclaration> properties,
      CSSStyle style)
   {
      CSSDeclaration declaration =
         properties.get(ECSSProperty.BACKGROUND_COLOR);

      if(declaration != null) {
         style.setBackground(getColor(ECSSProperty.BACKGROUND_COLOR, declaration));
      }
   }

   private void parseForeground(HashMap<Object, CSSDeclaration> properties,
      CSSStyle style)
   {
      CSSDeclaration declaration = properties.get(ECSSProperty.COLOR);

      if(declaration != null) {
         style.setForeground(getColor(ECSSProperty.COLOR, declaration));
      }
   }

   // @by ChrisSpagnoli bug1426646684840 2015-4-6
   // Warn user (once) if an unsupported font name in the CSS file.
   private static HashSet<String> fontsAvailable = null;
   private static HashSet<String> badFonts = null;

   private void validateFont(final String newFont) {
      if(StyleFont.DEFAULT_FONT_FAMILY.equals(newFont)) {
         return;
      }

      if(fontsAvailable == null) {
         fontsAvailable = new HashSet<>();
         GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
         String[] fonts = ge.getAvailableFontFamilyNames();
         Collections.addAll(fontsAvailable, fonts);
      }

      if(!fontsAvailable.contains(newFont)){
         if(badFonts == null) {
            badFonts = new HashSet<>();
         }

         if(!badFonts.contains(newFont)){
            LOG.warn("CSS Font \""+newFont+
               "\" not supported (by server).");
            badFonts.add(newFont);
         }
      }
   }

   private void parseFont(HashMap<Object, CSSDeclaration> properties,
      CSSStyle style)
   {
      // a new font with default values
      StyleFont font = new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.PLAIN, 10);
      boolean isFont = false;
      CSSDeclaration declaration = properties.get(ECSSProperty.FONT_FAMILY);

      if(declaration != null) {
         String value = getStringValue(declaration);
         validateFont(value);
         if(value != null) {
            font = new StyleFont(value, font.getStyle(), font.getSize());
            isFont = true;
         }
      }

      declaration = properties.get(ECSSProperty.FONT_SIZE);

      if(declaration != null) {
         String value = getStringValue(declaration);
         float size = getFontSize(value);
         font = (StyleFont) font.deriveFont(size);
         isFont = true;
      }

      declaration = properties.get(ECSSProperty.FONT_STYLE);

      if(declaration != null) {
         String value = getStringValue(declaration);

         if(CCSSValue.ITALIC.equalsIgnoreCase(value)) {
            int fontStyle = Font.ITALIC | font.getStyle();
            font = (StyleFont) font.deriveFont(fontStyle);
            isFont = true;
         }
         else if(CCSSValue.NORMAL.equalsIgnoreCase(value)) {
            int fontStyle = (~Font.ITALIC) & font.getStyle();
            font = (StyleFont) font.deriveFont(fontStyle);
            isFont = true;
         }
         else {
            logException(ECSSProperty.FONT_STYLE, value);
         }
      }

      declaration = properties.get(ECSSProperty.FONT_WEIGHT);

      if(declaration != null) {
         String value = getStringValue(declaration);

         if(CCSSValue.BOLD.equalsIgnoreCase(value)) {
            int fontStyle = Font.BOLD | font.getStyle();
            font = (StyleFont) font.deriveFont(fontStyle);
            isFont = true;
         }
         else if(CCSSValue.NORMAL.equalsIgnoreCase(value)) {
            int fontStyle = (~Font.BOLD) & font.getStyle();
            font = (StyleFont) font.deriveFont(fontStyle);
            isFont = true;
         }
         else {
            logException(ECSSProperty.FONT_WEIGHT, value);
         }
      }

      declaration = properties.get(ECSSProperty.TEXT_DECORATION);

      if(declaration != null) {
         String value = getStringValue(declaration);

         if(CCSSValue.UNDERLINE.equalsIgnoreCase(value)) {
            int fontStyle = StyleFont.UNDERLINE | font.getStyle();
            font = (StyleFont) font.deriveFont(fontStyle);
            isFont = true;
         }
         else if(CCSSValue.LINE_THROUGH.equalsIgnoreCase(value)) {
            int fontStyle = StyleFont.STRIKETHROUGH | font.getStyle();
            font = (StyleFont) font.deriveFont(fontStyle);
            isFont = true;
         }
         else if(!CCSSValue.NORMAL.equalsIgnoreCase(value)) {
            logException(ECSSProperty.TEXT_DECORATION, value);
         }
      }

      if(isFont) {
         style.setFont(font);
      }
   }

   private void parseAlignment(HashMap<Object, CSSDeclaration> properties,
      CSSStyle style)
   {
      CSSDeclaration declaration = properties.get(ECSSProperty.TEXT_ALIGN);
      int align = 0;
      boolean isAlign = false;

      if(declaration != null) {
         String value = getStringValue(declaration);
         int halign = StyleConstants.H_LEFT;

         if(CCSSValue.LEFT.equalsIgnoreCase(value)) {
            halign = StyleConstants.H_LEFT;
            isAlign = true;
         }
         else if(CCSSValue.CENTER.equalsIgnoreCase(value)) {
            halign = StyleConstants.H_CENTER;
            isAlign = true;
         }
         else if(CCSSValue.RIGHT.equalsIgnoreCase(value)) {
            halign = StyleConstants.H_RIGHT;
            isAlign = true;
         }
         else {
            logException(ECSSProperty.TEXT_ALIGN, value);
         }

         align &= StyleConstants.V_TOP | StyleConstants.V_CENTER |
            StyleConstants.V_BOTTOM;
         align |= halign;
      }

      declaration = properties.get(ECSSProperty.VERTICAL_ALIGN);

      if(declaration != null) {
         String value = getStringValue(declaration);
         int valign = StyleConstants.V_TOP;

         if(CCSSValue.TOP.equalsIgnoreCase(value) ||
            CCSSValue.TEXT_TOP.equalsIgnoreCase(value))
         {
            valign = StyleConstants.V_TOP;
            isAlign = true;
         }
         else if(CCSSValue.MIDDLE.equalsIgnoreCase(value)) {
            valign = StyleConstants.V_CENTER;
            isAlign = true;
         }
         else if(CCSSValue.BOTTOM.equalsIgnoreCase(value) ||
            CCSSValue.TEXT_BOTTOM.equalsIgnoreCase(value))
         {
            valign = StyleConstants.V_BOTTOM;
            isAlign = true;
         }
         else {
            logException(ECSSProperty.VERTICAL_ALIGN, value);
         }

         align &= StyleConstants.H_LEFT | StyleConstants.H_CENTER |
            StyleConstants.H_RIGHT;
         align |= valign;
      }

      if(isAlign) {
         style.setAlignment(align);
      }
   }

   private void parseBorderStyle(HashMap<Object, CSSDeclaration> properties,
      CSSStyle style)
   {
      Insets borders = new Insets(0, 0, 0, 0);
      CSSDeclaration declaration =
         properties.get(ECSSProperty.BORDER_TOP_STYLE);
      boolean isBorders = false;

      if(declaration != null) {
         borders.top = getBorderStyle(ECSSProperty.BORDER_TOP_STYLE,
            getStringValue(declaration));
         isBorders = true;
      }

      declaration = properties.get(ECSSProperty.BORDER_LEFT_STYLE);

      if(declaration != null) {
         borders.left = getBorderStyle(ECSSProperty.BORDER_LEFT_STYLE,
            getStringValue(declaration));
         isBorders = true;
      }

      declaration = properties.get(ECSSProperty.BORDER_BOTTOM_STYLE);

      if(declaration != null) {
         borders.bottom = getBorderStyle(ECSSProperty.BORDER_BOTTOM_STYLE,
            getStringValue(declaration));
         isBorders = true;
      }

      declaration = properties.get(ECSSProperty.BORDER_RIGHT_STYLE);

      if(declaration != null) {
         borders.right = getBorderStyle(ECSSProperty.BORDER_RIGHT_STYLE,
            getStringValue(declaration));
         isBorders = true;
      }

      if(isBorders) {
         style.setBorders(borders);
      }
   }

   private void parseBorderColors(HashMap<Object, CSSDeclaration> properties,
      CSSStyle style)
   {
      BorderColors borderColors = new BorderColors();
      boolean isBorderColors = false;

      CSSDeclaration declaration = properties.get(ECSSProperty.BORDER_TOP_COLOR);

      if(declaration != null) {
         borderColors.topColor = getColor(ECSSProperty.BORDER_TOP_COLOR,
            declaration);
         isBorderColors = true;
      }

      declaration = properties.get(ECSSProperty.BORDER_LEFT_COLOR);

      if(declaration != null) {
         borderColors.leftColor = getColor(ECSSProperty.BORDER_LEFT_COLOR,
            declaration);
         isBorderColors = true;
      }

      declaration = properties.get(ECSSProperty.BORDER_BOTTOM_COLOR);

      if(declaration != null) {
         borderColors.bottomColor = getColor(ECSSProperty.BORDER_BOTTOM_COLOR,
            declaration);
         isBorderColors = true;
      }

      declaration = properties.get(ECSSProperty.BORDER_RIGHT_COLOR);

      if(declaration != null) {
         borderColors.rightColor = getColor(ECSSProperty.BORDER_RIGHT_COLOR,
            declaration);
         isBorderColors = true;
      }

      if(isBorderColors) {
         style.setBorderColors(borderColors);
      }
   }

   private void parseAlpha(HashMap<Object, CSSDeclaration> properties, CSSStyle style) {
      CSSDeclaration declaration = properties.get(ECSSProperty.OPACITY);

      if(declaration != null) {
         String value = getStringValue(declaration);

         try {
            style.setAlpha((int) (Float.parseFloat(value) * 100));
         }
         catch(NumberFormatException e) {
            logException(ECSSProperty.OPACITY, value);
         }
      }
   }

   private void parseBorderRadius(HashMap<Object, CSSDeclaration> properties, CSSStyle style) {
      CSSDeclaration declaration = properties.get(ECSSProperty.BORDER_RADIUS);

      if(declaration != null) {
         String strValue = getStringValue(declaration);
         CSSSimpleValueWithUnit value = CSSNumberHelper.getValueWithUnit(strValue, false);

         if(value != null) {
            style.setBorderRadius(value.getAsIntValue());
         }
         else {
            logException(ECSSProperty.BORDER_RADIUS, strValue);
         }
      }
   }

   private void parseWrapping(HashMap<Object, CSSDeclaration> properties,
      CSSStyle style)
   {
      CSSDeclaration declaration = properties.get(ECSSProperty.WORD_WRAP);

      if(declaration != null) {
         String value = getStringValue(declaration);

         if(CCSSValue.NORMAL.equalsIgnoreCase(value)) {
            style.setWrapping(false);
         }
         else if("break-word".equalsIgnoreCase(value)) {
            style.setWrapping(true);
         }
         else {
            logException(ECSSProperty.WORD_WRAP, value);
         }
      }
   }

   private void parsePadding(HashMap<Object, CSSDeclaration> properties, CSSStyle style) {
      Insets padding = new Insets(0, 0, 0, 0);
      CSSDeclaration declaration = properties.get(ECSSProperty.PADDING_TOP);
      boolean hasPadding = false;

      if(declaration != null) {
         String strValue = getStringValue(declaration);
         CSSSimpleValueWithUnit value = CSSNumberHelper.getValueWithUnit(strValue, false);

         if(value != null) {
            padding.top = value.getAsIntValue();
            hasPadding = true;
         }
         else {
            logException(ECSSProperty.PADDING_TOP, strValue);
         }
      }

      declaration = properties.get(ECSSProperty.PADDING_LEFT);

      if(declaration != null) {
         String strValue = getStringValue(declaration);
         CSSSimpleValueWithUnit value = CSSNumberHelper.getValueWithUnit(strValue, false);

         if(value != null) {
            padding.left = value.getAsIntValue();
            hasPadding = true;
         }
         else {
            logException(ECSSProperty.PADDING_LEFT, strValue);
         }
      }

      declaration = properties.get(ECSSProperty.PADDING_BOTTOM);

      if(declaration != null) {
         String strValue = getStringValue(declaration);
         CSSSimpleValueWithUnit value = CSSNumberHelper.getValueWithUnit(strValue, false);

         if(value != null) {
            padding.bottom = value.getAsIntValue();
            hasPadding = true;
         }
         else {
            logException(ECSSProperty.PADDING_BOTTOM, strValue);
         }
      }

      declaration = properties.get(ECSSProperty.PADDING_RIGHT);

      if(declaration != null) {
         String strValue = getStringValue(declaration);
         CSSSimpleValueWithUnit value = CSSNumberHelper.getValueWithUnit(strValue, false);

         if(value != null) {
            padding.right = value.getAsIntValue();
            hasPadding = true;
         }
         else {
            logException(ECSSProperty.PADDING_RIGHT, strValue);
         }
      }

      if(hasPadding) {
         style.setPadding(padding);
      }
   }

   private void parseWidth(HashMap<Object, CSSDeclaration> properties, CSSStyle style) {
      CSSDeclaration declaration = properties.get(ECSSProperty.WIDTH);

      if(declaration != null) {
         String strValue = getStringValue(declaration);
         CSSSimpleValueWithUnit value = CSSNumberHelper.getValueWithUnit(strValue, false);

         if(value != null) {
            style.setWidth(value.getAsIntValue());
         }
         else {
            logException(ECSSProperty.WIDTH, strValue);
         }
      }
   }

   private void parseHeight(HashMap<Object, CSSDeclaration> properties, CSSStyle style) {
      CSSDeclaration declaration = properties.get(ECSSProperty.HEIGHT);

      if(declaration != null) {
         String strValue = getStringValue(declaration);
         CSSSimpleValueWithUnit value = CSSNumberHelper.getValueWithUnit(strValue, false);

         if(value != null) {
            style.setHeight(value.getAsIntValue());
         }
         else {
            logException(ECSSProperty.HEIGHT, strValue);
         }
      }
   }

   private void parseVisible(HashMap<Object, CSSDeclaration> properties, CSSStyle style) {
      CSSDeclaration declaration = properties.get(ECSSProperty.VISIBILITY);

      if(declaration != null) {
         String value = getStringValue(declaration);

         if(CCSSValue.VISIBLE.equals(value)) {
            style.setVisible(true);
         }
         else if(CCSSValue.HIDDEN.equals(value)) {
            style.setVisible(false);
         }
         else {
            logException(ECSSProperty.VISIBILITY, value);
         }
      }
   }

   private void parseCustomProperties(HashMap<Object, CSSDeclaration> properties,
      CSSStyle style)
   {
      HashMap<String, String> customProperties = new HashMap<>();

      for(Map.Entry<Object, CSSDeclaration> property : properties.entrySet()) {
         if(property.getKey() instanceof String) {
            String key = (String) property.getKey();
            String value = getStringValue(property.getValue());
            customProperties.put(key, value);
         }
      }

      style.setCustomProperties(customProperties);
   }

   /**
    * Parses the specified css declaration and returns a java.awt.Color object
    *
    * @param declaration CSSDeclaration for any color property
    * @return Color
    */
   private Color getColor(ECSSProperty property, CSSDeclaration declaration) {
      for(ICSSExpressionMember expr : declaration.getExpression().
         getAllMembers())
      {
         if(expr instanceof CSSExpressionMemberTermSimple) {
            CSSExpressionMemberTermSimple simpleExpr =
               (CSSExpressionMemberTermSimple) expr;
            String value = simpleExpr.getValue();

            if(ECSSColor.isDefaultColorName(value)) {
               ECSSColor color =
                  ECSSColor.getFromNameCaseInsensitiveOrNull(value);
               assert color != null;
               return new Color(
                  color.getRed(), color.getGreen(), color.getBlue());
            }
            else {
               try {
                  return Color.decode(value);
               }
               catch(NumberFormatException e) {
                  logException(property, value);
               }
            }
         }
         else if(expr instanceof CSSExpressionMemberFunction) {
            CSSExpressionMemberFunction funcExpr =
               (CSSExpressionMemberFunction) expr;

            if(Tool.equals(funcExpr.getFunctionName(), "rgb", false)) {
               String value = funcExpr.getAsCSSString(new CSSWriterSettings(
                  getCSSVersion()), 0);
               CSSRGB rgb = CSSColorHelper.getParsedRGBColorValue(value);

               if(rgb != null) {
                  int r = Integer.parseInt(rgb.getRed());
                  int g = Integer.parseInt(rgb.getGreen());
                  int b = Integer.parseInt(rgb.getBlue());
                  return new Color(r, g, b);
               }
               else {
                  logException(property, value);
               }
            }
         }
      }

      return null;
   }

   /**
    * Gets the string value from the given CSS declaration
    *
    * @param declaration CSSDeclaration object
    * @return string value of the given declaration
    */
   private String getStringValue(CSSDeclaration declaration) {
      String result = declaration.getExpression().getAsCSSString(
         new CSSWriterSettings(getCSSVersion()), 0);
      result = CSSParseHelper.extractStringValue(result);
      return result;
   }

   /**
    * Gets a font size given a string value
    *
    * @param value string value that specifies the font size
    * @return font size
    */
   private float getFontSize(String value) {
      float size = 10f;

      if(CSSNumberHelper.isValueWithUnit(value)) {
         CSSSimpleValueWithUnit unitValue = CSSNumberHelper.
            getValueWithUnit(value);
         assert unitValue != null;
         size = (float) unitValue.getValue();
      }
      else if(CCSSValue.XX_SMALL.equalsIgnoreCase(value)) {
         size = 6f;
      }
      else if(CCSSValue.X_SMALL.equalsIgnoreCase(value)) {
         size = 8f;
      }
      else if(CCSSValue.SMALL.equalsIgnoreCase(value)) {
         size = 11f;
      }
      else if(CCSSValue.MEDIUM.equalsIgnoreCase(value)) {
         size = 16f;
      }
      else if(CCSSValue.LARGE.equalsIgnoreCase(value)) {
         size = 22f;
      }
      else if(CCSSValue.X_LARGE.equalsIgnoreCase(value)) {
         size = 36f;
      }
      else if(CCSSValue.XX_LARGE.equalsIgnoreCase(value)) {
         size = 72f;
      }
      else {
         logException(ECSSProperty.FONT_SIZE, value);
      }

      return size;
   }

   /**
    * Gets a border style given a string value
    *
    * @param value string value that specifies a border style
    * @return border style
    */
   private int getBorderStyle(ECSSProperty property, String value) {
      int style = 0;

      if(CCSSValue.NONE.equalsIgnoreCase(value)) {
         style = StyleConstants.NO_BORDER;
      }
      else if(CCSSValue.DOTTED.equalsIgnoreCase(value)) {
         style = StyleConstants.DOT_LINE;
      }
      else if(CCSSValue.DASHED.equalsIgnoreCase(value)) {
         style = StyleConstants.DASH_LINE;
      }
      else if(CCSSValue.SOLID.equalsIgnoreCase(value)) {
         style = StyleConstants.THIN_LINE;
      }
      else if(CCSSValue.DOUBLE.equalsIgnoreCase(value)) {
         style = StyleConstants.DOUBLE_LINE;
      }
      else {
         logException(property, value);
      }

      return style;
   }

   /**
    * Puts decomposed properties and their corresponding declarations into
    * the properties hashmap.
    *
    * @param properties hashmap that contains decomposed properties
    * @param declaration css declaration of the given property
    */
   private void putProperties(HashMap<Object, CSSDeclaration> properties,
      CSSDeclaration declaration)
   {
      Object property = ECSSProperty.getFromNameOrNull(
         declaration.getProperty());

      if(property == null) {
         property = declaration.getProperty();
      }

      replaceVariables(declaration, properties);

      if(property instanceof ECSSProperty) {
         if(CSSShortHandRegistry.isShortHandProperty((ECSSProperty) property)) {
            CSSShortHandDescriptor descriptor = CSSShortHandRegistry.
               getShortHandDescriptor((ECSSProperty) property);
            assert descriptor != null;

            for(CSSDeclaration sub : descriptor.getSplitIntoPieces(declaration)) {
               putProperties(properties, sub);
            }
         }
         else {
            properties.put(property, declaration);
         }
      }
      else {
         properties.put(property, declaration);
      }
   }

   /**
    * Log value exceptions.
    */
   private void logException(ECSSProperty property, String value) {
      LOG.warn(Catalog.getCatalog().getString(
         "Unsupported css value", value, property.getName()));
   }

   private void replaceVariables(CSSDeclaration declaration,
                                 HashMap<Object, CSSDeclaration> properties)
   {
      CSSExpression exp = declaration.getExpression();

      for(int i = declaration.getExpression().getMemberCount(); i >= 0; i--) {
         ICSSExpressionMember expMember = declaration.getExpression().getMemberAtIndex(i);

         if(expMember instanceof CSSExpressionMemberFunction) {
            CSSExpressionMemberFunction funcExpr = ((CSSExpressionMemberFunction) expMember);

            if("var".equals(funcExpr.getFunctionName())) {
               String varName = funcExpr.getAsCSSString();
               varName = varName.substring(CCSSValue.PREFIX_VAR_OPEN.length(), varName.length() - 1);

               if(properties.containsKey(varName)) {
                  CSSDeclaration varDeclaration = properties.get(varName);
                  exp.removeMember(i);

                  for(ICSSExpressionMember varExpMember : varDeclaration.getExpression()
                     .getAllMembers().reverse()) {
                     exp.addMember(i, varExpMember);
                  }
               }
            }
         }
      }
   }

   private static ECSSVersion getCSSVersion() {
      return ECSSVersion.CSS30;
   }

   private class RuleWeight implements Comparable<RuleWeight> {
      public RuleWeight(int typeCount, int matchingClassAttrCount,
         int idCount, int ruleIndex)
      {
         this.typeCount = typeCount;
         this.matchingClassAttrCount = matchingClassAttrCount;
         this.idCount = idCount;
         this.ruleIndex = ruleIndex;
      }

      @Override
      public int compareTo(RuleWeight ruleWeight) {
         if(ruleWeight == null) {
            return 1;
         }

         if(idCount == ruleWeight.idCount) {
            if(matchingClassAttrCount == ruleWeight.matchingClassAttrCount) {
               if(typeCount == ruleWeight.typeCount) {
                  if(ruleIndex > ruleWeight.ruleIndex) {
                     return 1;
                  }
                  else {
                     return -1;
                  }
               }
               else if(typeCount > ruleWeight.typeCount) {
                  return 1;
               }
               else {
                  return -1;
               }
            }
            else if(matchingClassAttrCount > ruleWeight.matchingClassAttrCount)
            {
               return 1;
            }
            else {
               return -1;
            }
         }
         else if(idCount > ruleWeight.idCount) {
            return 1;
         }
         else {
            return -1;
         }
      }

      public int hashCode() {
         return super.hashCode();
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof RuleWeight)) {
            return false;
         }

         RuleWeight ruleWeight = (RuleWeight) obj;

         return typeCount == ruleWeight.typeCount &&
            matchingClassAttrCount == ruleWeight.matchingClassAttrCount &&
            idCount == ruleWeight.idCount &&
            ruleIndex == ruleWeight.ruleIndex;
      }

      private final int typeCount;
      private final int matchingClassAttrCount;
      private final int idCount;
      private final int ruleIndex;
   }

   /**
    * Common LRU map used in getStyle to prevent costly matching logic.
    */
   private static final class LRUCache<K, V> extends ConcurrentHashMap<K, V> {
      public LRUCache(int cacheSize) {
         super(cacheSize, 0.75F);
         this.cacheSize = cacheSize;
      }

      @Override
      public V put(K key, V value) {
         // use a concurrent map to avoid lock contention. when the cache size is exceeded,
         // 'randomly' delete entries until it's under the desired size.
         if(size() > cacheSize * 2) {
            synchronized(this) {
               long now = System.currentTimeMillis();

               if(lastTS + 10000 < now) {
                  lastTS = now;
                  int rmCnt = size() - cacheSize;

                  for(Iterator<K> iter = keySet().iterator(); iter.hasNext() && rmCnt > 0; rmCnt--)
                  {
                     remove(iter.next());
                  }
               }
            }
         }

         return super.put(key, value);
      }

      private final int cacheSize;
      private long lastTS;
   }

   /**
    * Wrapper class for a CSS Style, used for caching.
    */
   private static final class CSSStyleValue {
      CSSStyleValue(CSSStyle style) {
         this.style = style;
      }

      public CSSStyle getStyle() {
         return style;
      }

      private final CSSStyle style;
   }

   /**
    * Cache key for a tuple of CSSParameter objects.
    */
   private static final class StyleKey {
      StyleKey(CSSParameter[] parameters) {
         this.parameters = parameters;
         this.hash = Arrays.hashCode(parameters);
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         StyleKey styleKey = (StyleKey) o;
         return Arrays.equals(parameters, styleKey.parameters);
      }

      @Override
      public int hashCode() {
         return hash;
      }

      private final CSSParameter[] parameters;
      private final int hash;
   }

   private static final class MapKey {
      MapKey(String cssDir, String cssFile, boolean isReport, String orgId) {
         this.cssDir = cssDir;
         this.cssFile = cssFile;
         this.isReport = isReport;
         this.orgId = orgId;
      }

      @Override
      public String toString() {
         return "(" + cssDir + "," + cssFile + "," + orgId+ ")";
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         MapKey mapKey = (MapKey) o;
         return (isReport == mapKey.isReport && Objects.equals(orgId, mapKey.orgId) || !isReport) &&
            Objects.equals(cssDir, mapKey.cssDir) &&
            Objects.equals(cssFile, mapKey.cssFile);
      }

      @Override
      public int hashCode() {
         return Objects.hash(cssDir, cssFile, isReport, orgId);
      }

      private final String cssDir;
      private final String cssFile;
      private final boolean isReport;
      private final String orgId;
   }

   private long ts; // last modified timestamp
   private long lastCheck; // last checked timestamp
   private final Map<String, Set<String>> idMap = new HashMap<>();
   private final Map<String, Set<String>> classMap = new HashMap<>();

   private CascadingStyleSheet css;
   private String cssDir;
   private String cssFile;
   private final List<String> otherFiles;
   private final LRUCache<StyleKey, CSSStyleValue> styleCache = new LRUCache<>(5000);
   private final LRUCache<String, Boolean> selectorPresentCache = new LRUCache<>(5000);
   private boolean isReport;

   // data change listener manager
   private DataChangeListenerManager dmgr = new DataChangeListenerManager();

   private static final String DICTIONARIES_KEY =
      CSSDictionary.class.getName() + ".dictionaries";
   private static final ReadWriteLock DICTIONARIES_LOCK =
      new ReentrantReadWriteLock(true);
   private static Map<String, CascadingStyleSheet> styleSheets = new ConcurrentHashMap<>();

   private static final Logger LOG = LoggerFactory.getLogger(CSSDictionary.class);
}
