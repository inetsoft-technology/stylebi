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
package inetsoft.util.dep;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.owasp.encoder.Encode;

import java.io.*;
import java.nio.charset.Charset;
import java.util.List;
import java.util.jar.JarOutputStream;

/**
 * AbstractXAsset describes replets, datasources, worksheets etc. as assets, and
 * keeps their relationship.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public abstract class AbstractXAsset implements XAsset {
   /**
    * Get the last modified time of this asset.
    */
   @Override
   public long getLastModifiedTime() {
      return lastModifiedTime;
   }

   @Override
   public void setLastModifiedTime(long lastModifiedTime) {
      this.lastModifiedTime = lastModifiedTime;
   }

   /**
    * Parse content of the specified asset from input stream.
    */
   @Override
   public synchronized void parseContent(InputStream input, XAssetConfig config, boolean isImport)
      throws Exception
   {
      throw new RuntimeException("Unsupported method is called!");
   }

   /**
    * Write content of the specified asset to an output stream.
    */
   @Override
   public synchronized boolean writeContent(OutputStream output) throws Exception {
      throw new RuntimeException("Unsupported method is called!");
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      StringBuilder buffer = new StringBuilder();
      buffer.append(getClass().getSimpleName());

      if(getPath() != null) {
         buffer.append(": [");
         buffer.append(getPath());

         if(getUser() != null) {
            buffer.append(", ");
            buffer.append(getUser());
         }

         buffer.append("]");
      }

      return buffer.toString();
   }

   /**
    * Generate a hashcode for this object.
    * @return a hashcode for this object.
    */
   public int hashCode() {
      int hash = 0;

      if(getType() != null) {
         hash += getType().hashCode();
      }

      if(getPath() != null) {
         hash += getPath().hashCode();
      }

      if(getUser() != null) {
         hash += getUser().hashCode();
      }

      return hash;
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         // ignore it
      }

      return null;
   }

   /**
    * Check if equals another object.
    * @param obj the specified opject to compare.
    * @return <code>true</code> if equals, <code>false</code> otherwise.
    */
   public boolean equals(Object obj) {
      return toIdentifier().equals(((XAsset) obj).toIdentifier());
   }

   /**
    * Check if this asset is visible to client users.
    */
   @Override
   public boolean isVisible() {
      return true;
   }

   /**
    * Generate dependency description.
    */
   protected String generateDescription(String from, String to) {
      return catalog.getString("common.xasset.depends", from, to);
   }

   /**
    * Get jar output stream.
    * @param output the specified output stream.
    * @return the jar output stream.
    */
   protected JarOutputStream getJarOutputStream(OutputStream output)
      throws Exception
   {
      JarOutputStream out;

      if(output instanceof JarOutputStream) {
         out = (JarOutputStream) output;
      }
      else {
         out = new JarOutputStream(output);
      }

      return out;
   }

   /**
    * Replace file separator for zip.
    */
   protected static String replaceFilePath(String path) {
      return Tool.replaceAll(path, "/", "^_^");
   }

   protected void getSourceDependencies(List<XAssetDependency> dependencies, String dataSource,
                                        String source, boolean logicalModel, String desc, int type1,
                                        int type2)
   {
      if(!logicalModel) {
         XDataSourceAsset[] res = XDataSourceAsset.getAssets(dataSource);

         for(XDataSourceAsset asset : res) {
            String path = asset.getPath();
            String toDesc = source == null ?
               catalog.getString("common.xasset.dataSource", path) :
               catalog.getString("common.xasset.dataSource2", source, path);
            dependencies.add(new XAssetDependency(asset, this,
               type1, generateDescription(desc, toDesc)));
         }
      }
      else {
         XLogicalModelAsset[] res =
            XLogicalModelAsset.getAssets(dataSource + "^" + source);

         for(XLogicalModelAsset asset : res) {
            String toDesc = catalog.getString(
               "common.xasset.dataSource1", asset.getName(), dataSource);
            dependencies.add(new XAssetDependency(asset, this, type2,
               generateDescription(desc, toDesc)));
         }
      }
   }

   /**
    * Get the entry description.
    * @return the description.
    */
   protected String getEntryDescription(AssetEntry asset) {
      StringBuilder root = new StringBuilder();
      String name = "Worksheet";
      AssetEntry.Type type = asset.getType();
      int scope = asset.getScope();

      if(type == AssetEntry.Type.VIEWSHEET)
      {
         name = "Viewsheet";
      }
      else if(type == AssetEntry.Type.VIEWSHEET_SNAPSHOT) {
         name = "Snapshot";
      }

      if(scope == AssetRepository.GLOBAL_SCOPE) {
         root.append("Global ");
         root.append(name);
      }
      else if(scope == AssetRepository.REPORT_SCOPE) {
         root.append("Report ");
         root.append(name);
      }
      else if(scope == AssetRepository.USER_SCOPE) {
         root.append("User ");
         root.append(name);
      }

      root.append("/");
      root.append(asset.getPath());

      return root.toString();
   }

   /**
    * Process local query.
    */
   protected void processLocalXAsset(XAsset xasset, String desc, List<XAssetDependency> deps) {
      XAssetDependency[] dependencies = xasset.getDependencies(deps);

      for(XAssetDependency dependency : dependencies) {
         // enhance description
         String description = desc + " " + dependency.toString();
         XAsset asset = dependency.getDependedXAsset();
         int depType = getDependencyType(asset);
         deps.add(new XAssetDependency(asset, this, depType, description));
      }
   }

   /**
    * Get dependency type.
    */
   protected int getDependencyType(XAsset asset) {
      if(asset instanceof XQueryAsset) {
         return XAssetDependency.REPORT_QUERY;
      }
      else if(asset instanceof XDataSourceAsset) {
         return XAssetDependency.REPORT_DATASOURCE;
      }
      else if(asset instanceof XLogicalModelAsset) {
         return XAssetDependency.REPORT_XLOGICALMODEL;
      }
      else if(asset instanceof WorksheetAsset) {
         return XAssetDependency.REPORT_WORKSHEET;
      }

      return -1;
   }

   static void parseAsset(InputStream input, AssetParser parser) throws Exception {
      parseAsset(input, Charset.defaultCharset(), parser);
   }

   static void parseAsset(InputStream input, Charset encoding, AssetParser parser)
      throws Exception
   {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      String comment = parseComment(input, buffer, encoding);

      if(buffer.size() > 0) {
         parser.parse(comment, new ByteArrayInputStream(buffer.toByteArray()));
      }
   }

   public static String parseComment(InputStream input, ByteArrayOutputStream buffer,
                                      Charset encoding) throws IOException
   {
      BufferedReader reader = new BufferedReader(new InputStreamReader(input, encoding));
      StringWriter comment = new StringWriter();
      PrintWriter commentWriter = new PrintWriter(comment);
      PrintWriter bufferWriter = new PrintWriter(new OutputStreamWriter(buffer, encoding));
      String line = reader.readLine();

      if(line == null) {
         return null;
      }

      if(line.equals("__COMMENT__")) {
         while((line = reader.readLine()) != null && !line.equals("__END OF COMMENT__")) {
            commentWriter.println(line);
         }
      }
      else {
         bufferWriter.println(line);
      }

      while((line = reader.readLine()) != null) {
         bufferWriter.println(line);
      }

      commentWriter.close();
      bufferWriter.close();
      return comment.toString();
   }

   static void writeComment(String comment, OutputStream out) {
      if(comment != null && !comment.trim().isEmpty()) {
         PrintWriter writer = new PrintWriter(out);
         writer.println("__COMMENT__");
         writer.println(Encode.forXml(comment.trim()));
         writer.println("__END OF COMMENT__");
         writer.flush();
      }
   }

   protected long lastModifiedTime; // to temp
   protected transient Catalog catalog = Catalog.getCatalog();

   @FunctionalInterface
   protected interface AssetParser {
      void parse(String comment, InputStream input) throws Exception;
   }
}
