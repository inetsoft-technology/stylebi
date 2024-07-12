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
package inetsoft.uql.erm.transform;

import inetsoft.util.*;
import org.w3c.dom.*;

import java.io.*;
import java.util.*;

/**
 * ERMExporter, transforms data model from SQL server to db2 or oracle.
 *
 * @version 10.1
 * @author InetSoft Technology Corp.
 */
public class ERMExporter {
   public static void main(String[] args) {
      if(args.length < 4) {
         usage();
      }

      try {
         Properties props = new Properties();

         for(int i = 0; i < args.length; i++) {
            String[] items = args[i].split("=");

            if(items.length > 1) {
               props.setProperty(items[0].toLowerCase(), items[1]);
            }
         }

         TransformDescriptor descriptor = new TransformDescriptor(props);

         if(!descriptor.isValid()) {
            usage();
         }

         File infile = descriptor.getInputFile();
         File outfile = descriptor.getOutputFile();

         if(outfile.exists()) {
            File file = FileSystemService.getInstance().getFile(outfile.getPath() + ".bak");
            InputStream backFile = new FileInputStream(outfile);
            OutputStream newFile = new FileOutputStream(file);
            Tool.copyTo(backFile, newFile);
         }

         InputStream in = new FileInputStream(infile);
         Document doc = Tool.parseXML(in);
         ERMExporter exporter = new ERMExporter();
         exporter.transform(descriptor, doc);
         in.close();
         OutputStream out = new FileOutputStream(outfile);
         XMLTool.write(doc, out);
         out.close();
      }
      catch(Exception ex) {
         ex.printStackTrace(); //NOSONAR
      }
   }

   /**
    * Print usage of the tool, and exit.
    */
   private static void usage() {
      System.out.println("Usage:");
      System.out.println("java ERMExporter db=xxx from=xxx to=xxx " +
         "datasource=xxx [url=xxx] [from.prefix=xxx] [to.prefix=xxx] [case]");
      System.out.println("\nOptions:");
      System.out.println("db         -The target data source type, oracle " +
         "db2 or sqlserver");
      System.out.println("from       -The source file");
      System.out.println("to         -The target file");
      System.out.println("datasource -The data source to transform");
      System.out.println("url        -[optional] The target data source url");
      System.out.println("             Default to keep the original url");
      System.out.println("from.prefix-[optional] The source prefix, it " +
         "contains catalog and schema");
      System.out.println("            Default means no catalog or schema");
      System.out.println("to.prefix  -[optional] The target prefix, it " +
         "contains catalog and schema");
      System.out.println("            Default means no catalog or schema");
      System.out.println("case       -[optional] The table and column name, " +
         "display as uppercase or lowercase, db2 and oracle default to " +
         "uppercase, sqlserver default to lowercase"
         );
      System.out.println("\nSample:");
      System.out.println("java ERMExporter db=oracle from=d:/datasource.xml " +
         "to=d:/datasourceOra.xml datasource=Sql2005 from.prefix=SqlDB.user " +
         "to.prefix=OracleDB.user");
      System.exit(1);
   }

   /**
    * Transform data model.
    */
   private void transform(TransformDescriptor desc, Document doc)
      throws Exception
   {
      Element root = doc.getDocumentElement();
      NodeList nodes = Tool.getChildNodesByTagName(root, "datasource");
      String sname = desc.getDataSource();

      for(int i = 0; i < nodes.getLength(); i++) {
         Element elem = (Element) nodes.item(i);
         String sourceName = Tool.getAttribute(elem, "name");

         if(sourceName.equalsIgnoreCase(sname)) {
            new DataSourceTransformer().transform(elem, desc);
            break;
         }
      }

      // transform physical models
      NodeList modelNodes = Tool.getChildNodesByTagName(root, "DataModel");
      Element dataModel = null;

      for(int i = 0; i < modelNodes.getLength(); i++) {
         Element elem = (Element) modelNodes.item(i);
         String ds = Tool.getAttribute(elem, "datasource");

         if(ds.equals(sname)) {
            dataModel = elem;
            break;
         }
      }

      if(dataModel == null) {
         String msg = sname == null || sname.length() == 0 ?
            "Data source not specified!" :
            "Data source not found: " + sname + "!";
         System.err.println(msg);
         System.exit(1);
      }

      NodeList phyNodes = Tool.getChildNodesByTagName(dataModel, "partition");
      Map phyMap = new HashMap();
      Set phyTables = new HashSet();

      // transform partition nodes
      for(int i = 0; i < phyNodes.getLength(); i++) {
         Element elem = (Element) phyNodes.item(i);
         NodeList pnodes = Tool.getChildNodesByTagName(elem, "partition");

         for(int j = 0; j < pnodes.getLength(); j++) {
            Element pelem = (Element) pnodes.item(j);
            XPartitionTransformer extendPartition = new XPartitionTransformer();
            extendPartition.setParentElement(elem);
            extendPartition.transform(pelem, desc);
            Set tables = extendPartition.getTables();
            Iterator iterator = tables.iterator();

            while(iterator.hasNext()) {
               Object key = iterator.next();

               if(!phyTables.contains(key)) {
                  phyTables.add(key);
               }
            }
         }

         XPartitionTransformer phyTransformer = new XPartitionTransformer();
         phyTransformer.transform(elem, desc);

         if(phyTables.size() > 0) {
            phyTransformer.setTables(phyTables);
         }

         phyMap.put(Tool.getAttribute(elem, "name"), phyTransformer);
      }

      // transform logical model nodes
      NodeList lnodes = Tool.getChildNodesByTagName(dataModel, "LogicalModel");

      for(int i = 0; i < lnodes.getLength(); i++) {
         Element elem = (Element) lnodes.item(i);
         String phyName = Tool.getAttribute(elem, "partition");
         NodeList logNodes = Tool.getChildNodesByTagName(elem, "LogicalModel");

         for(int j = 0; j < logNodes.getLength(); j++) {
            Element lnode = (Element) logNodes.item(j);
            XLogicalModelTransformer transformer = new XLogicalModelTransformer(
               (ERMTransformer) phyMap.get(phyName));
            transformer.transform(lnode, desc);
         }

         XLogicalModelTransformer pltransformer =
            new XLogicalModelTransformer((ERMTransformer) phyMap.get(phyName));
         pltransformer.transform(elem, desc);
      }

      // transform VPM node
      Element elem = Tool.getChildNodeByTagName(dataModel, "vpms");

      if(elem != null) {
         VPMTransformer transformer = new VPMTransformer(phyMap);
         transformer.transform(elem, desc);
      }
   }
}