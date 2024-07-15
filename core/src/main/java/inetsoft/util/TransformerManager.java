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
package inetsoft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.util.*;

/**
 * TransformerManager provides the API for transforming templates with
 * different versions. It also can be used to transform other xml files,
 * such as source.xml etc.
 *
 * @version 10.2
 * @author Inetsoft Technology Corp
 */
public class TransformerManager {
   public static final String TEMPLATE = "template";
   public static final String SOURCE = "datasource";
   public static final String QUERY = "query";
   public static final String SECURITY = "security";
   public static final String SCHEDULE = "schedule";
   public static final String WORKSHEET = "worksheet";
   public static final String VIEWSHEET = "viewsheet";
   public static final String VPM = "vpm";
   public static final String PORTAL = "portal";
   public static final String ADHOC = "adhoc";
   public static final ThreadLocal<List<Exception>> TRANS_EXCEPTIONS = new ThreadLocal<>();

   private TransformerManager(String type) {
      try {
         InputStream input = getClass().getResourceAsStream("/inetsoft/util/version.xml");
         Document doc = Tool.parseXML(input);
         NodeList nlist = doc.getElementsByTagName(type);

         if(nlist != null && nlist.getLength() > 0) {
            NodeList transformers = ((Element) nlist.item(0)).
               getElementsByTagName("transformer");

            for(int i = 0; i < transformers.getLength(); i++) {
               Element transformer = (Element) transformers.item(i);
               String version = transformer.getAttribute("version");
               String transf = transformer.getAttribute("class");

               transformerMap.put(version, transf);
               versionSet.add(version);
            }
         }
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
      }
   }

   /**
    * Return a transformed xml document with current version.
    *
    * @param doc original xml document
    */
   public Node transform(Node doc) throws Exception {
      // @by ChrisS bug1402502061808 2014-6-18
      // Retrieve the "sourceName" parameter from properties.
      Properties propsIn = getProperties();
      final String sourceName = propsIn.getProperty("sourceName");

      if(doc == null) {
         throw new Exception(" Missing Document obj!");
      }

      for(String ver : versionSet) {
         String cname = transformerMap.get(ver);
         XMLTransformer transf = (XMLTransformer) Class.forName(cname).newInstance();

         // @by ChrisS bug1402502061808 2014-6-18
         // Set the "sourceName" parameter property in XMLTransformer.
         if(sourceName != null) {
            properties.setProperty("sourceName", sourceName);
         }
         else {
            properties.remove("sourceName");
         }

         transf.setProperties(properties);
         doc = transf.transform(doc);
      }

      return doc;
   }

   /**
    * Return a transformed xml document with special version.
    *
    * @param doc     original xml document
    * @param version file version info
    */
   public Node transform(Node doc, String version) throws Exception {
      int idx = versionSet.indexOf(version);

      for(int i = 0; i < idx; i++) {
         Object ver = versionSet.get(i);

         String cname = transformerMap.get(ver);
         XMLTransformer transf = (XMLTransformer) Class.forName(cname).newInstance();
         transf.setProperties(properties);
         doc = transf.transform(doc);
      }

      return doc;
   }

   /**
    * Get configuration properties.
    */
   public Properties getProperties() {
      return properties;
   }

   /**
    * Set configuration properties.
    */
   public void setProperties(Properties prop) {
      properties = prop == null ? new Properties() : prop;
   }

   /**
    * Get the transformer manager.
    */
   public static TransformerManager getManager(String type) {
      return getManager(type, null);
   }

   /**
    * Get the transformer manager.
    */
   public static TransformerManager getManager(String type, Properties prop) {
      TransformerManager manager = managerMap.get(type);

      if(manager == null) {
         manager = new TransformerManager(type);
         managerMap.put(type, manager);
      }

      manager.setProperties(prop);
      return manager;
   }

   /**
    * Main entrance.
    */
   public static void main(String[] args) {
      String fname = null;
      String type = TEMPLATE;
      String version = null;

      for(int i = 0; i < args.length; i++) {
         if(args[i].equals("-t")) {
            type = args[i + 1];
         }
         if(args[i].equals("-r")) {
            version = args[i + 1];
         }
         else {
            fname = args[i];
         }
      }

      if(fname == null) {
         System.err.println("Usage: java inetsoft.util.TransformerManager [-t] target");
         System.exit(1);
      }

      try {
         File file = FileSystemService.getInstance().getFile(fname);
         InputStream input = new FileInputStream(file);
         input = new BufferedInputStream(input);
         Document doc = Tool.parseXML(input);

         TransformerManager transf = TransformerManager.getManager(type);

         if(version != null) {
            XMLTool.write(transf.transform(doc, version));
         }
         else {
            XMLTool.write(transf.transform(doc));
         }
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }
   }

   private static Hashtable<String, TransformerManager> managerMap = new Hashtable<>();
   private Hashtable<String, String> transformerMap = new Hashtable<>();
   private Vector<String> versionSet = new Vector<>();
   private Properties properties = new Properties();
   private static final Logger LOG = LoggerFactory.getLogger(TransformerManager.class);
}
