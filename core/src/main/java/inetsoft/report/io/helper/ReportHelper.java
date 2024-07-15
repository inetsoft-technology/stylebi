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
package inetsoft.report.io.helper;

import inetsoft.report.*;
import inetsoft.report.internal.PaperSize;
import inetsoft.report.internal.StyleCore;
import inetsoft.report.io.Builder;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.InputStream;
import java.util.*;

/**
 * This class read report object from template file.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ReportHelper {
   /**
    * Report template format. This is the format created by the
    * Style Report Designer. It could be a simple report skeleton,
    * or a template with embedded queries, scripts.
    */
   public static final int TEMPLATE = 1;
   /**
    * Report XML format. The report format always captures the
    * entire report. If a report is created from a template, the
    * report format saves the actual report data instead of
    * meta data.
    */
   public static final int REPORT = 2;
   /**
    * Use this constant as version in getReportHelper() to get the helper
    * classes of newest version.
    */
   public static final String MAX_VERSION = "99999";

   /**
    * Get the helper according to the tag node and version.
    */
   public static ReportHelper getReportHelper(Element tag, String version, int type) {
      initVersionMap();
      String name = tag.getTagName();

      try {
         String helperClass = getHelperFromMap(name, version, type);

         if(helperClass != null) {
            return createHelper(helperClass, version, type);
         }
         else {
            Hashtable vmap = getVersionMap(name, type);

            // not an entity's tag, and has no helper associated.
            if(vmap == null) {
               return null;
            }

            String vname = version;

            if(vname == null || vname.length() == 0) {
               // no version info. use the helper of the oldest version.
               Enumeration versions = vmap.keys();

               while(versions.hasMoreElements()) {
                  String v = (String) versions.nextElement();

                  if(vname == null || vname.length() == 0 ||
                    compareTo(vname, v) > 0) {
                     vname = v;
                  }
               }
            }
            else {
               // no current version. search for the recent version.
               if(vmap.get(vname) == null) {
                  String rvname = "";
                  Enumeration versions = vmap.keys();

                  while(versions.hasMoreElements()) {
                     String v = (String) versions.nextElement();

                     if(compareTo(vname, v) > 0 && compareTo(rvname, v) < 0) {
                        rvname = v;
                     }
                  }

                  if(rvname.length() > 0) {
                     vname = rvname;
                  }
               }
            }

            String cname = (String) vmap.get(vname);
            ReportHelper helper = createHelper(cname, version, type);

            addHelperToMap(name, version, type, cname);

            return helper;
         }
      }
      catch(Exception e) {
         LOG.error("Error parsing template: helper for node " +
            name + " in version " + version + " not found", e);
         return null;
      }
   }

   /**
    * Compare the 2 versions, if the former version is larger than the latter
    * one, positive number is returned, if less than, negative is returned.
    * if the same, 0 is returned.
    */
   private static float compareTo(String version1, String version2) {
      float v1 = version1 == null || version1.equals("") ? 0 :
                 Float.parseFloat(version1);
      float v2 = version2 == null || version2.equals("") ? 0 :
                 Float.parseFloat(version2);

      return v1 - v2;
   }

   /**
    * Reload the version information from the config file.
    */
   public static void reloadVersionMap() {
      templateVersionMap.clear();
      reportVersionMap.clear();
      initVersionMap();
   }

   /**
    * Parse the tag node and return the ReportSheet created.
    * @param tag the xml node with tag name "Report".
    * @param param this parameter will be different objects depending on the
    *              helper.
    *              For ReportHelper, this parameter should be a String of dir.
    */
   public Object read(Element tag, Object param) throws Exception {
      if(!tag.getTagName().equals("Report")) {
         return null;
      }

      ReportSheet sheet = createSheet();

      setReportDir(sheet, param == null ? "." : (String) param);
      readReportAttributes(sheet, tag);

      // is build registry, we just need to parse report properties
      if(!Builder.isBuildRegistry()) {
         sheet.parseAssetRepository(tag);
      }

      parseChildNodes(tag);
      return sheet;
   }

   /**
    * Create a StyleSheet instance.
    * @return StyleSheet object.
    */
   protected ReportSheet createSheet() {
      ReportSheet report = new TabularSheet();
      return report;
   }

   /**
    * Set the attributes of report.
    * @param root the element node with tag name of "Report".
    */
   protected void readReportAttributes(ReportSheet sheet, Element root) {
      String val;

      if((val = Tool.getAttribute(root, "Unit")) != null) {
         sheet.setUnit(val);
      }

      readPageSize(sheet, root);
      setMargin(sheet, root);

      try {
         val = Tool.getAttribute(root, "HeaderFromEdge");
         sheet.setHeaderFromEdge(Double.valueOf(val).doubleValue());
         val = Tool.getAttribute(root, "FooterFromEdge");
         sheet.setFooterFromEdge(Double.valueOf(val).doubleValue());
         val = Tool.getAttribute(root, "PageNumbering");
         sheet.setPageNumberingStart(Integer.parseInt(val));
      }
      catch(NullPointerException e) {// ignore if the info not there
      }

      if((val = Tool.getAttribute(root, "HorizontalWrap")) != null) {
         sheet.setHorizontalWrap(val.equalsIgnoreCase("true"));
      }

      if((val = Tool.getAttribute(root, "CSSLocation")) != null) {
         sheet.setCSSLocation(val);
      }

      if((val = Tool.getAttribute(root, "CSSClass")) != null) {
         sheet.setCSSClass(val);
      }

      if((val = Tool.getAttribute(root, "CSSId")) != null) {
         sheet.setCSSId(val);
      }

      // set the default tab stops
      if((val = Tool.getAttribute(root, "TabStops")) != null) {
         String[] sa = Tool.split(val, ',');
         double[] ws = new double[sa.length];

         for(int i = 0; i < ws.length; i++) {
            try {
               ws[i] = Double.valueOf(sa[i]).doubleValue();
            }
            catch(Exception e) {
               // ignore this stop if not the correct format
               LOG.error("Invalid tab stop value: " + sa[i], e);
               ws[i] = (i > 0) ? ws[i - 1] : 0.0;
            }
         }

         sheet.setCurrentTabStops(ws);
      }
   }

   /**
    * Set the page size and orientation of report.
    * @param root the element node with tag name of "Report".
    */
   protected void readPageSize(ReportSheet sheet, Element root) {
      String str = Tool.getAttribute(root, "PageSize");

      if(str != null) {
         sheet.setPageSize(PaperSize.getSize(str));
      }
      else {
         str = Tool.getAttribute(root, "PaperSize");
         if(str != null) {
            sheet.setPageSize(PaperSize.getSize(str));
         }
      }

      str = Tool.getAttribute(root, "Orientation");
      if(str != null) {
         sheet.setOrientation(PaperSize.getOrientation(str));
      }

      if((str = Tool.getAttribute(root, "CustomPageSize")) != null) {
         sheet.setCustomPageSize("true".equalsIgnoreCase(str));
      }
   }

   /**
    * Set the margin of report.
    * @param root the element node with tag name of "Report".
    */
   protected void setMargin(ReportSheet sheet, Element root) {
      Margin margin = new Margin(0, 0, 0, 0);

      try {
         margin.top = Double.valueOf(Tool.getAttribute(root, "Top")).
            doubleValue();
         margin.left = Double.valueOf(Tool.getAttribute(root, "Left")).
            doubleValue();
         margin.bottom = Double.valueOf(Tool.getAttribute(root, "Bottom")).
            doubleValue();
         margin.right = Double.valueOf(Tool.getAttribute(root, "Right")).
            doubleValue();
         sheet.setMargin(margin);
      }
      catch(NullPointerException e) {// ignore if the info not there
      }
   }

   /**
    * Parse the child nodes of tag to fill the report object.
    * @param root the element node with tag name of "Report".
    */
   protected void parseChildNodes(Element root) throws Exception {
      // read table styles at first
      Vector styles = new Vector();
      NodeList nlist = root.getChildNodes();

      if(!Builder.isBuildRegistry()) {
         for(int i = 0; i < nlist.getLength(); i++) {
            Node node = nlist.item(i);

            if(!(node instanceof Element)) {
               continue;
            }

            Element tag = (Element) node;
            String name = tag.getTagName();

            if(name.equals("table-style")) { // backward compatibility
               ReportHelper helper = getReportHelper(tag, reportVersion, reportType);

               if(helper == null) {
                  continue;
               }

               XTableStyle style = (XTableStyle) helper.read(tag, null);
               styles.addElement(style);
               stylemap.put(style.getName(), style);
            }
         }

         Builder.setEmbeddedStyles(styles);
      }
   }

   /**
    * Set the directory of the report resources.
    */
   private void setReportDir(ReportSheet sheet, String dir) {
      if(sheet == null) {
         return;
      }

      if(dir == null) {
         try {
            dir = SreeEnv.getProperty("user.dir");
         }
         catch(Exception e) {
         }

         if(dir == null) {
            dir = ".";
         }
      }

      sheet.setDirectory(dir);
   }

   /**
    * Load the version information from the config file.
    */
   private static void initVersionMap() {
      if(!templateVersionMap.isEmpty()) {
         return;
      }

      try {
         InputStream input = SreeEnv.class.getResourceAsStream(
            "/inetsoft/report/io/version.xml");
         Document doc = Tool.parseXML(input);
         NodeList nlist = doc.getElementsByTagName("VersionRegistry");

         if(nlist != null && nlist.getLength() > 0) {
            Element node = (Element) nlist.item(0);

            nlist = Tool.getChildNodesByTagName(node, "entity");

            if(nlist != null) {
               for(int i = 0; i < nlist.getLength(); i++) {
                  Element elem = (Element) nlist.item(i);
                  String tag = Tool.getAttribute(elem, "tag");
                  NodeList vlist = Tool.getChildNodesByTagName(elem, "helper");

                  if(tag != null && vlist != null) {
                     Hashtable vt = new Hashtable();
                     Hashtable vr = new Hashtable();

                     for(int j = 0; j < vlist.getLength(); j++) {
                        Element velem = (Element) vlist.item(j);
                        String ver = Tool.getAttribute(velem, "version");
                        String type = Tool.getAttribute(velem, "type");
                        String cls = Tool.getValue(velem);

                        if(type != null && type.equals("REPORT")) {
                           vr.put(ver, cls);
                        }
                        else {
                           vt.put(ver, cls);
                        }
                     }

                     templateVersionMap.put(tag, vt);

                     if(!vr.isEmpty()) {
                        reportVersionMap.put(tag, vr);
                     }
                  }
               }
            }
         }
      }
      catch(Exception e) {
         LOG.error("Failed to initialize version map", e);
      }
   }

   /**
    * Get the version map of an element.
    * @param type TEMPLAT or REPORT.
    */
   private static Hashtable getVersionMap(String name, int type) {
      if(type == REPORT) {
         Hashtable vmap = (Hashtable) reportVersionMap.get(name);

         if(vmap != null) {
            return vmap;
         }
      }

      return (Hashtable) templateVersionMap.get(name);
   }

   /**
    * Get the helper class name from the cache map.
    */
   private static String getHelperFromMap(String name, String version, int type) {
      return (String) helperMap.get(genHelperKey(name, version, type));
   }

   /**
    * Add a helper to the cache map.
    */
   private static void addHelperToMap(String name, String version, int type,
      String helper) {
      if(helperMapMaxCount == -1) {
         // init the threshold.
         try {
            String cn = SreeEnv.getProperty("helper.map.max");

            helperMapMaxCount = Integer.parseInt(cn);
         }
         catch(Exception e) {
            helperMapMaxCount = 120;
         }
      }

      String key = genHelperKey(name, version, type);

      // add the helper to map.
      synchronized(helperKeys) {
         helperMap.put(key, helper);
         helperKeys.insertElementAt(key, 0);

         // clear the obsolete helpers.
         if(helperMapMaxCount > 0 && helperKeys.size() > helperMapMaxCount) {
            while(helperKeys.size() > helperMapMaxCount / 2) {
               String lastKey = (String) helperKeys.elementAt(helperKeys.size() - 1);
               helperMap.remove(lastKey);
               helperKeys.removeElementAt(helperKeys.size() - 1);
            }
         }
      }
   }

   /**
    * Generate the key of the helperMap.
    */
   private static String genHelperKey(String name, String version, int type) {
      return name + "\n" + version + "\n" + type;
   }

   /**
    * Create a helper by class name.
    */
   private static ReportHelper createHelper(String cname, String version, int type)
      throws Exception
   {
      ReportHelper helper = null;

      try {
         helper = (ReportHelper) Class.forName(cname).newInstance();
         helper.reportVersion = version;
         helper.reportType = type;
      }
      catch(ClassNotFoundException ex) {
         LOG.warn("Class {} is no longer supported.", cname);
      }

      return helper;
   }

   private Hashtable stylemap = new Hashtable();
   protected String reportVersion = "";
   protected int reportType;

   // tag -> hashtable: v;  v: element version -> class name
   private static Hashtable templateVersionMap = new Hashtable();
   private static Hashtable reportVersionMap = new Hashtable();
   // tag+"\n"+report version+"\n"+type -> class.
   private static Hashtable helperMap = new Hashtable();
   private static Vector helperKeys = new Vector();
   private static int helperMapMaxCount = -1;

   private static final Logger LOG =
      LoggerFactory.getLogger(ReportHelper.class);
}
