/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.test;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.DeploymentInfo;
import inetsoft.util.*;
import inetsoft.util.dep.*;
import inetsoft.web.admin.deploy.PartialDeploymentJarInfo;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Test-only import of an exported asset jar for {@link SreeHomeExtension}. This is the former
 * DeployManagerService.importAssets(byte[], boolean, ActionRecord), which was removed from
 * production because it imports without a principal (Bug #77407). It is kept here so that the
 * {@code @SreeHome(importResources)} fixtures are loaded exactly as before: no permission or
 * owner checks, no dependency registration, no org remapping and no audit records.
 */
final class TestAssetImporter {
   private TestAssetImporter() {
   }

   static void importAssets(InputStream data, boolean replace) throws Exception {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      String cacheFolder = fileSystemService.getCacheDirectory() + File.separator +
         "testAssetImport" + System.nanoTime();
      final ArrayList<String> fileOrders = new ArrayList<>();
      Map<String, String> names = new HashMap<>();

      try {
         try(JarInputStream jarIn = new JarInputStream(data)) {
            JarEntry jentry;

            while((jentry = (JarEntry) jarIn.getNextEntry()) != null) {
               String ename = jentry.getName();
               String fname = "JarFileInfo.xml".equals(ename) ? ename :
                  "f" + Math.abs(ename.hashCode());
               File outFile = fileSystemService.getFile(cacheFolder + File.separator + fname);
               names.put(fname, ename);

               if(jentry.isDirectory()) {
                  outFile.mkdirs();
               }
               else {
                  outFile.getParentFile().mkdirs();

                  if(!outFile.exists()) {
                     outFile.createNewFile();
                     fileOrders.add(outFile.getName());
                  }

                  try(OutputStream out = new FileOutputStream(outFile)) {
                     Tool.copyTo(jarIn, out);
                  }
               }
            }
         }

         File file = fileSystemService.getFile(cacheFolder, "JarFileInfo.xml");
         Document infoDom;

         try(InputStream in = new FileInputStream(file)) {
            infoDom = Tool.parseXML(in);
         }

         final PartialDeploymentJarInfo info = new PartialDeploymentJarInfo();
         final DeploymentInfo deploymentInfo = new DeploymentInfo(info, names, cacheFolder);
         names = deploymentInfo.getNames();
         info.parseXML(infoDom.getDocumentElement());
         Tool.deleteFile(file);

         XAssetConfig config = new XAssetConfig();
         config.setOverwriting(replace);
         File[] files = deploymentInfo.getFiles();

         Arrays.sort(files, (f1, f2) -> {
            int result = fileOrders.indexOf(f1.getName()) - fileOrders.indexOf(f2.getName());
            return result != 0 ? result : Long.compare(f1.lastModified(), f2.lastModified());
         });

         for(File file1 : files) {
            String filename = file1.isDirectory() ? null : names.get(file1.getName());

            if(filename == null) {
               continue;
            }

            filename = Tool.replaceAll(filename, "^_^", "/");

            if(filename.startsWith("__")) {
               // the removed method wrote __SUBREPORT_/__TEMPLATE_/__REPORTFILE_ entries to the
               // data space and skipped every other "__" entry (e.g. __WS_EMBEDDED_TABLE_). No
               // fixture has the first kind, so refuse them instead of importing them differently
               if(filename.startsWith("__SUBREPORT_") || filename.startsWith("__TEMPLATE_") ||
                  filename.startsWith("__REPORTFILE_"))
               {
                  throw new UnsupportedOperationException(
                     "Test asset import does not support " + filename);
               }

               continue;
            }

            int idx = filename.indexOf("_");

            if(idx < 0) {
               continue;
            }

            String type = filename.substring(0, idx);

            if(!XAssetUtil.getXAssetTypes(true).contains(type)) {
               continue;
            }

            XAsset asset = XAssetUtil.createXAsset(filename.substring(idx + 1));

            if(asset == null || !type.equals(asset.getType())) {
               continue;
            }

            InputStream in = new FileInputStream(file1);

            try {
               if(ViewsheetAsset.VIEWSHEET.equals(type)) {
                  TransformerManager xform =
                     TransformerManager.getManager(TransformerManager.VIEWSHEET);
                  Properties propsOut = new Properties();
                  propsOut.setProperty("sourceName", filename);
                  xform.setProperties(propsOut);

                  Document doc = (Document) xform.transform(Tool.parseXML(in));
                  in.close();
                  ByteArrayOutputStream output = new ByteArrayOutputStream();
                  PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(output, StandardCharsets.UTF_8));
                  writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                  writeNode(writer, doc.getDocumentElement());
                  writer.flush();
                  in = new ByteArrayInputStream(output.toByteArray());

                  if(asset.getPath().contains("/")) {
                     setFolderProperty(
                        asset.getPath().substring(0, asset.getPath().lastIndexOf("/")), info);
                  }
               }

               asset.parseContent(in, config, true, false);
            }
            finally {
               IOUtils.closeQuietly(in);
            }
         }
      }
      finally {
         Tool.deleteFile(fileSystemService.getFile(cacheFolder));
         LibManager manager = LibManagerProvider.getInstance().getManager();

         if(manager.isDirty()) {
            manager.save();
         }
      }
   }

   private static void writeNode(PrintWriter writer, Node node) {
      switch(node.getNodeType()) {
      case Node.CDATA_SECTION_NODE:
         writer.print("<![CDATA[");
         writer.print(node.getNodeValue());
         writer.print("]]>");
         break;
      case Node.ELEMENT_NODE:
         writer.print("<");
         writer.print(node.getNodeName());
         NamedNodeMap attrs = node.getAttributes();

         for(int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            writer.print(" ");
            writer.print(attr.getNodeName());
            writer.print("=\"");
            writer.print(Tool.encodeHTMLAttribute(attr.getNodeValue()));
            writer.print("\"");
         }

         writer.print(">");
         NodeList elems = node.getChildNodes();

         for(int i = 0; i < elems.getLength(); i++) {
            writeNode(writer, elems.item(i));
         }

         writer.print("</");
         writer.print(node.getNodeName());
         writer.print(">");
         break;
      case Node.TEXT_NODE:
         writer.print(Tool.encodeHTMLAttribute(node.getNodeValue()));
         break;
      }
   }

   private static void setFolderProperty(String folder, PartialDeploymentJarInfo info)
      throws Exception
   {
      RepletRegistry registry = RepletRegistryManager.getInstance().getRegistry();
      String[] values = Tool.split(folder, '/');
      String newFolder = "";

      for(int i = 0; i < values.length; i++) {
         newFolder = i > 0 ? newFolder + "/" + values[i] : values[i];

         if(registry.isFolder(newFolder)) {
            continue;
         }

         registry.addFolder(newFolder);
         registry.setFolderAlias(newFolder, info.getFolderAlias().get(newFolder));
         registry.setFolderDescription(newFolder, info.getFolderDescription().get(newFolder));
         registry.save();
      }
   }
}
