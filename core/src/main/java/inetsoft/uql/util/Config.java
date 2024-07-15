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
package inetsoft.uql.util;

import com.google.common.collect.Iterables;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.uql.XNode;
import inetsoft.uql.XQuery;
import inetsoft.uql.tabular.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.awt.*;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Configuration class for the UQL package. This class reads the config.xml
 * and keeps the information on the supported query types and their
 * corresponding classes.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
@SingletonManager.Singleton(Config.Reference.class)
public class Config implements Serializable {
   /**
    * Config is a singleton and can not be instantiated explicitly.
    */
   private Config() {
      try {
         InputStream input = XNode.class.getResourceAsStream("/inetsoft/uql/config.xml");
         Document doc = Tool.parseXML(input);
         NodeList nlist = doc.getElementsByTagName("dependon");

         if(nlist.getLength() > 0) {
            Element node = (Element) nlist.item(0);
            String name = node.getAttribute("name");
            String cls = node.getAttribute("class");

            if(isDriverMissing(cls)) {
               throw new RuntimeException("XBuilder Error: " + name + " is not properly installed!");
            }
         }

         nlist = doc.getElementsByTagName("datasource");

         for(int i = 0; i < nlist.getLength(); i++) {
            Element node = (Element) nlist.item(i);
            String type = Tool.getAttribute(node, "type");
            DSInfo info = createDSInfo(node);

            if(info != null) {
               dxmap.put(type, info);
            }
         }

         loadServices();
         readJDBCDrivers(doc);
      }
      catch(Exception e) {
         LOG.error("Failed to load config.xml", e);
      }
   }

   public static Config getConfig() {
      return SingletonManager.getInstance(Config.class);
   }

   private boolean isDriverMissing(String className) {
      try {
         Class.forName(className);
         return false;
      }
      catch(Exception ignore) {
         return true;
      }
   }

   private DSInfo createDSInfo(Element node) {
      String type = Tool.getAttribute(node, "type");

      if(type == null) {
         LOG.error("Data source type missing in config.xml");
         return null;
      }

      String dxpane = Tool.getAttribute(node, "datasourcepane");
      String qpane = Tool.getAttribute(node, "querypane");
      String dxclass = Tool.getAttribute(node, "datasourceclass");
      String qclass = Tool.getAttribute(node, "queryclass");
      String handler = Tool.getAttribute(node, "handler");
      String icon = Tool.getAttribute(node, "icon");
      String dxwizard = Tool.getAttribute(node, "datasourcewizard");
      String qwizard = Tool.getAttribute(node, "querywizard");
      String dmhandler = Tool.getAttribute(node, "modelhandler");
      String agent = Tool.getAttribute(node, "agent");
      Element labelNode = Tool.getChildNodeByTagName(node, "label");
      Element displayLabelNode = Tool.getChildNodeByTagName(node, "displayLabel");
      Element descNode = Tool.getChildNodeByTagName(node, "description");
      String label = (labelNode == null) ? null : Tool.getValue(labelNode);
      String displayLabel = (displayLabelNode == null) ? null
         : Tool.getValue(displayLabelNode);
      String description = (descNode == null) ? null : Tool.getValue(descNode);

      return new DSInfo(
         dxclass, qclass, dxpane, qpane, handler, dxwizard, qwizard, dmhandler,
         agent, icon, label, displayLabel, description, null, null);
   }

   /**
    * Reloads the tabular data source services in response to a class loader
    * change.
    */
   public static void reloadServices() {
      getConfig().loadServices();
   }

   /**
    * Load the tabular services.
    */
   private void loadServices() {
      List<TabularService> services =
         Plugins.getInstance().getServices(TabularService.class, null);

      for(TabularService service : services) {
         String type = service.getDataSourceType();

         if(!dxmap.containsKey(type)) {
            try {
               dxmap.put(type, createDSInfo(service));
            }
            catch(Exception ex) {
               LOG.error("Class for Tabular Service not found: {}", type, ex);
            }
         }
      }
   }

   private DSInfo createDSInfo(TabularService service) throws Exception {
      ClassLoader loader = service.getClass().getClassLoader();

      Class<?> clazz = Class.forName(service.getDataSourceClass(), true, loader);
      String dxPane = getPropertyPane(clazz, "inetsoft.uql.gui.tabular.TabularDataSourceProperty");
      String dxWizard = getWizard(clazz, "inetsoft.uql.gui.tabular.TabularDataSourceWizard");

      clazz = Class.forName(service.getQueryClass(), true, loader);
      String qpane = getPropertyPane(clazz, "inetsoft.uql.gui.tabular.TabularQueryProperty");
      String qwizard = getWizard(clazz, "inetsoft.uql.gui.tabular.TabularQueryWizard");

      return new DSInfo(
         service.getDataSourceClass(), service.getQueryClass(),
         dxPane, qpane, "inetsoft.uql.tabular.impl.TabularHandler",
         dxWizard, qwizard, null, null, service.getIcon(),
         service.getDataSourceType(), service.getDisplayLabel(), service.getDescription(),
         service.getRuntimeClass(), service);
   }

   private String getPropertyPane(Class<?> clazz, String defaultValue) {
      PropertyPane annotation = clazz.getAnnotation(PropertyPane.class);
      return annotation == null ? defaultValue : annotation.value();
   }

   private String getWizard(Class<?> clazz, String defaultValue) {
      Wizard annotation = clazz.getAnnotation(Wizard.class);
      return annotation == null ? defaultValue : annotation.value();
   }

   /**
    * Remove the dxmap's value for this plugin.
    */
   public static void removePlugin(Plugin plugin) {
      getConfig().removePluginValue(plugin);
   }

   /**
    * Remove the dxmap's value that key is dataSourceType.
    */
   private void removePluginValue(Plugin plugin) {
      List<TabularService> services = plugin.getServices(TabularService.class);

      for(TabularService service : services) {
         String type = service.getDataSourceType();
         dxmap.remove(type);
      }
   }

   /**
    * Get all configured data source types.
    */
   public static List<String> getDataSourceTypes() {
      return new ArrayList<>(getConfig().dxmap.keySet());
   }

   /**
    * Get a list of tabular data source types.
    */
   public static List<String> getTabularDataSourceTypes() {
      final Config config = getConfig();
      List<String> types = new ArrayList<>();

      for(String type : config.dxmap.keySet()) {
         if(config.dxmap.get(type) != null && config.dxmap.get(type).isTabular()) {
            types.add(type);
         }
      }

      return types;
   }

   /**
    * Get the name of the data source property pane class for the
    * specified data source type.
    */
   public static String getDataSourcePane(String dxtype) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      return (ds == null) ? null : ds.getDataSourcePane();
   }

   /**
    * Get the name of the data source wizard class for the
    * specified data source type.
    */
   public static String getDataSourceWizard(String dxtype) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      return (ds == null) ? null : ds.getDataSourceWizard();
   }

   /**
    * Get the name of the query wizard class for the
    * specified data source type.
    */
   public static String getQueryWizard(String dxtype) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      return (ds == null) ? null : ds.getQueryWizard();
   }

   /**
    * Get the name of the data source class for the specified data
    * source type.
    */
   public static String getDataSourceClass(String dxtype) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      return (ds == null) ? null : ds.getDataSourceClass();
   }

   /**
    * Get the name of the query class for the specified data source type.
    */
   public static String getQueryClass(String dxtype) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      return (ds == null) ? null : ds.getQueryClass();
   }

   /**
    * Get the query handler class name.
    */
   public static String getHandlerClass(String dxtype) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      return (ds == null) ? null : ds.getHandlerClass();
   }

   /**
    * Get the query handler class name.
    */
   public static String getAgentClass(String dxtype) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      return (ds == null) ? null : ds.getAgentClass();
   }

   /**
    * Get the data source label.
    */
   public static String getLabel(String dxtype) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      return (ds == null) ? null : ds.getLabel();
   }

   /**
    * Get the data source display label.
    */
   public static String getDisplayLabel(String dxtype) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      return (ds == null) ? null : ds.getDisplayLabel();
   }

   /**
    * Get the data source display label.
    */
   public static String getDisplayLabel(String dxtype, Locale locale) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      return (ds == null) ? null : ds.getDisplayLabel(locale);
   }

   /**
    * Get the data source description.
    */
   public static String getDescription(String dxtype) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      return (ds == null) ? null : ds.getDescription();
   }

   /**
    * Get the tabular data source runtime.
    */
   public static String getRuntime(String dxtype) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      return (ds == null) ? null : ds.getRuntime();
   }

   /**
    * Get the data source description.
    */
   public static String getIconResource(String dxtype) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      return (ds == null) ? null : ds.getIconResource();
   }

   /**
    * Get the resource bundle for this data source type
    */
   public static ResourceBundle getResourceBundle(String dxtype) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      return (ds == null) ? null : ds.getResourceBundle();
   }

   /**
    * Get the icon representing the data source.
    */
   public static Image getIcon(String dxtype) {
      return getConfig().imagemap.computeIfAbsent(dxtype, Config::loadIcon);
   }

   private static Image loadIcon(String dxtype) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      String res = (ds == null) ? null : ds.getIconResource();

      if(res != null) {
         try {
            URL url = Config.getResource(dxtype, res);
            return Toolkit.getDefaultToolkit().getImage(url);
         }
         catch(Exception e) {
            LOG.error("Failed to get icon: " + res, e);
         }
      }

      return null;
   }

   /**
    * Loads a data source or supporting class from the appropriate class loader.
    *
    * @param dxtype    the data source type.
    * @param className the name of the class to load.
    *
    * @return the loaded class.
    *
    * @throws ClassNotFoundException if a class with the specified name could not be
    *                                found.
    */
   public static Class<?> getClass(String dxtype, String className)
      throws ClassNotFoundException
   {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      Class<?> result;

      if(ds != null && ds.isTabular()) {
         result = Class.forName(
            className, true, ds.tabularService.getClass().getClassLoader());
      }
      else {
         result = Drivers.getInstance().getDriverClass(className);
      }

      return result;
   }

   /**
    * Loads a resource from the appropriate class loader for a data source type.
    *
    * @param dxtype the type of data source.
    * @param name   the resource name.
    *
    * @return the resource URL or <tt>null</tt> if it was not found.
    */
   public static URL getResource(String dxtype, String name) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      URL result;

      if(ds != null && ds.isTabular()) {
         result = ds.tabularService.getClass().getResource(name);
      }
      else {
         result = Drivers.getInstance().getDriverResource(name);
      }

      return result;
   }

   /**
    * Get the data model handler class name.
    */
   public static String getModelHandlerClass(String dxtype) {
      DSInfo ds = getConfig().dxmap.get(dxtype);
      return (ds == null) ? null : ds.getModelHandlerClass();
   }

   /**
    * Get the icon representing the query type.
    */
   public static String getQueryIcon() {
      return QUERY_ICON;
   }

   /**
    * Get the icon representing a logical model.
    */
   public static String getLogicalModelIcon() {
      return LOGICAL_ICON;
   }

   /**
    * Get the icon representing a XEntity.
    */
   public static String getEntityIcon() {
      return getQueryIcon();
   }

   /**
    * Get the icon representing a XAttribute.
    */
   public static String getAttributeIcon() {
      return getQueryIcon();
   }

   /**
    * Get the icon representing a column in a table.
    */
   public static String getColumnIcon() {
      return COLUMN_ICON;
   }

   /**
    * Get the icon representing a report sheet.
    */
   public static String getReportIcon() {
      return REPORT_ICON;
   }

   /**
    * Get the icon representing formula field.
    */
   public static String getFormulaIcon() {
      return FORMULA_ICON;
   }

   /**
    * Get the icon representing a report element.
    */
   public static String getReportDataIcon() {
      return REPORT_DATA_ICON;
   }

   /**
    * Get the icon representing a data source folder.
    */
   public static String getDataSourceFolderIcon() {
      return DATASOURCE_FOLDER_ICON;
   }

   /**
    * Get the icon representing a query parameter.
    */
   public static String getParameterIcon() {
      return getQueryIcon();
   }

   /**
    * Get the jdbc datasource types.
    */
   public static List<String> getJDBCDataSources() {
      return getConfig().drivers.stream()
         .map(DriverInfo::getType)
         .collect(Collectors.toList());
   }

   /**
    * Get the driver for the jdbc datasource.
    */
   public static String getJDBCDriver(String name) {
      return getConfig().drivers.stream()
         .filter(d -> d.getType().equals(name))
         .map(DriverInfo::getDriver)
         .findAny()
         .orElse("");
   }

   /**
    * Get the drivers for the jdbc datasource.
    */
   public static List<String> getJDBCDrivers(String name) {
      return getConfig().drivers.stream()
         .filter(d -> d.getType().equals(name))
         .map(DriverInfo::getDriver)
         .collect(Collectors.toList());
   }

   /**
    * Get the driver for the jdbc datasource.
    */
   public static String getJDBCDriver(int idx) {
      final Config config = getConfig();

      return idx < 0 || idx >= config.drivers.size() ? "" :
         config.drivers.get(idx).getDriver();
   }

   /**
    * Get the index of a jdbc driver.
    */
   public static int indexOfJDBCDriver(String driver) {
      return Iterables.indexOf(
         getConfig().drivers, d -> Objects.requireNonNull(d).getDriver().equals(driver));
   }

   /**
    * Get the description for the jdbc datasource.
    */
   public static String getJDBCDescription(int idx) {
      final Config config = getConfig();

      return idx < 0 || idx >= config.drivers.size() ?  null :
         config.drivers.get(idx).getDescription();
   }

   /**
    * Get the url prefix for the jdbc datasource.
    */
   public static String getJDBCUrl(int idx) {
      final Config config = getConfig();

      return idx < 0 || idx >= config.drivers.size() ?  null :
         config.drivers.get(idx).getUrl();
   }

   /**
    * Get the url prefix for the jdbc datasource.
    */
   public static String getJDBCUrl(String name) {
      return getConfig().drivers.stream()
         .filter(d -> d.getType().equals(name))
         .map(DriverInfo::getUrl)
         .findAny()
         .orElse("");
   }

   /**
    * Get the type of jdbc datasource by the driver.
    */
   public static String getJDBCType(String driver) {
      if(driver == null) {
         return "";
      }

      for(DriverInfo info : getConfig().drivers) {
         if(info.getDriver().equals(driver)) {
            return info.getType();
         }
      }

      driver = driver.toLowerCase();

      if(driver.contains("oracle")) {
         return "Oracle";
      }
      else if(driver.contains("db2")) {
         return "DB2";
      }
      else if(driver.contains("sqlserver")) {
         return "SQL Server";
      }
      else if(driver.contains("mysql") || driver.contains("mariadb")) {
         return "MySQL";
      }
      else if(driver.contains("derby")) {
         return "Derby";
      }
      else if(driver.contains("sybase")) {
         return "Sybase";
      }
      else if(driver.contains("denodo")) {
         return "Denodo";
      }

      return "";
   }

   public static Map<String, String> getDefaultPoolProperties(int idx) {
      Config config = getConfig();
      return idx < 0 || idx >= config.drivers.size() ?
         Collections.emptyMap() :
         config.drivers.get(idx).getDefaultPoolProperties();
   }

   public static Map<String, String> getDefaultPoolProperties(String name) {
      return getConfig().drivers.stream()
         .filter(d -> d.getType().equals(name))
         .map(DriverInfo::getDefaultPoolProperties)
         .findAny()
         .orElseGet(Collections::emptyMap);
   }

   private void readJDBCDrivers(Document doc) {
      NodeList nlist = doc.getElementsByTagName("jdbcdrivers");

      if(nlist != null && nlist.getLength() > 0) {
         NodeList clist = ((Element) nlist.item(0)).
            getElementsByTagName("jdbcdriver");

         if(clist == null) {
            return;
         }

         for(int i = 0; i < clist.getLength(); i++) {
            Element node = (Element) clist.item(i);
            String type = Tool.getAttribute(node, "name");
            String driver = Tool.getAttribute(node, "driver");
            String url = Tool.getAttribute(node, "url");
            String description = Tool.getAttribute(node, "description");
            Map<String, String> properties = new HashMap<>();

            if((node = Tool.getChildNodeByTagName(node, "defaultPoolProperties")) != null) {
               NodeList plist = Tool.getChildNodesByTagName(node, "property");

               if(plist != null) {
                  for(int j = 0; j < plist.getLength(); j++) {
                     Element pnode = (Element) plist.item(j);
                     properties.put(Tool.getAttribute(pnode, "name"), Tool.getValue(pnode));
                  }
               }
            }

            drivers.add(new DriverInfo(type, description, driver, url, properties));
         }
      }
   }

   /**
    * Data source descriptor.
    */
   private static final class DSInfo {
      DSInfo(String dxclass, String qclass, String dxpane,
             String qpane, String handler, String dxwizard,
             String qwizard, String dmhandler, String agent,
             String icon, String label, String displayLabel,
             String description, String runtime,
             TabularService tabularService)
      {
         this.dxclass = dxclass;
         this.qclass = qclass;
         this.dxpane = dxpane;
         this.qpane = qpane;
         this.handler = handler;
         this.dxwizard = dxwizard;
         this.qwizard = qwizard;
         this.dmhandler = dmhandler;
         this.agent = agent;
         this.icon = icon;
         this.label = label;
         this.displayLabel = displayLabel;
         this.description = description;
         this.runtime = runtime;
         this.tabularService = tabularService;
      }

      boolean isTabular() {
         return tabularService != null;
      }

      String getDataSourceClass() {
         return dxclass;
      }

      String getQueryClass() {
         return qclass;
      }

      String getDataSourcePane() {
         return dxpane;
      }

      String getQueryPane() {
         return qpane;
      }

      String getHandlerClass() {
         return handler;
      }

      String getDataSourceWizard() {
         return dxwizard;
      }

      String getQueryWizard() {
         return qwizard;
      }

      String getModelHandlerClass() {
         return dmhandler;
      }

      String getAgentClass() {
         return agent;
      }

      String getIconResource() {
         return icon;
      }

      String getLabel() {
         return label;
      }

      String getDisplayLabel() {
         return displayLabel;
      }

      String getDisplayLabel(Locale locale) {
         return tabularService == null ?
            displayLabel : tabularService.getDisplayLabel(locale);
      }

      String getDescription() {
         return description;
      }

      String getRuntime() {
         return runtime;
      }

      ResourceBundle getResourceBundle() {
         ResourceBundle bundle = null;

         if(tabularService != null) {
            bundle = tabularService.getResourceBundle();
         }

         return bundle;
      }

      private final String dxclass;
      private final String qclass;
      private final String dxpane;
      private final String qpane;
      private final String handler;
      private final String dxwizard;
      private final String qwizard;
      private final String dmhandler;
      private final String agent;
      private final String icon;
      private final String label;
      private final String displayLabel;
      private final String description;
      private final String runtime;
      private final TabularService tabularService;
   }

   private static final class DriverInfo implements Serializable {
      DriverInfo(String type, String description, String driver, String url,
                 Map<String, String> defaultPoolProperties)
      {
         this.type = type;
         this.description = description;
         this.driver = driver;
         this.url = url;
         this.defaultPoolProperties = Collections.unmodifiableMap(defaultPoolProperties);
      }

      String getType() {
         return type;
      }

      String getDescription() {
         return description;
      }

      String getDriver() {
         return driver;
      }

      String getUrl() {
         return url;
      }

      Map<String, String> getDefaultPoolProperties() {
         return defaultPoolProperties;
      }

      private final String type;
      private final String description;
      private final String driver;
      private final String url;
      private final Map<String, String> defaultPoolProperties;

      private static final long serialVersionUID = 1L;
   }

   @SingletonManager.ShutdownOrder(after = PortalThemesManager.Reference.class)
   public static final class Reference extends SingletonManager.Reference<Config> {
      @Override
      public Config get(Object... parameters) {
         if(config == null) {
            config = new Config();
         }

         return config;
      }

      @Override
      public void dispose() {
         config = null;
      }

      private Config config;
   }

   private transient Map<String, DSInfo> dxmap = new HashMap<>();
   private transient ConcurrentMap<String, Image> imagemap = new ConcurrentHashMap<>();
   private List<DriverInfo> drivers = new ArrayList<>();

   private static final String QUERY_ICON = "db-table.svg";
   private static final String LOGICAL_ICON = "logical-model.svg";
   private static final String REPORT_DATA_ICON = "report.svg";
   private static final String REPORT_ICON = "report.svg";
   private static final String COLUMN_ICON = "column.svg";
   private static final String FORMULA_ICON = "formula.svg";
   private static final String DATASOURCE_FOLDER_ICON = "folder.svg";
   private static final Logger LOG = LoggerFactory.getLogger(Config.class);
}
