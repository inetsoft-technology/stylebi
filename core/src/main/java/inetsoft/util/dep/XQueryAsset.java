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

import inetsoft.report.Hyperlink;
import inetsoft.report.ReportSheet;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.*;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.*;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * XQueryAsset represents a query type asset.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class XQueryAsset extends AbstractXAsset {
   /**
    * Query type XAsset.
    */
   public static final String XQUERY = "XQUERY";

   /**
    * Constructor.
    */
   public XQueryAsset() {
      super();
   }

   /**
    * Constructor.
    * @param query the query name.
    */
   public XQueryAsset(String query) {
      this(query, null);
   }

   /**
    * Constructor.
    * @param query the query name.
    * @param report report for local query.
    */
   public XQueryAsset(String query, ReportSheet report) {
      this();
      this.query = query;
      this.report = report;
   }

   /**
    * Get all dependencies of this asset.
    * @return an array of XAssetDependency.
    */
   @Override
   public XAssetDependency[] getDependencies() {
      List<XAssetDependency> dependencies = new ArrayList<>();
      XQueryRepository xrep;
      XQuery xquery = null;

      if(xquery == null) {
         return new XAssetDependency[0];
      }

      // check if depends on a data source
      XDataSource dataSource = xquery.getDataSource();
      String id = "common.xasset.query1";

      if(dataSource != null) {
         String name = dataSource.getFullName();
         String desc = generateDescription(catalog.getString(id, query),
            catalog.getString("common.xasset.dataSource", name));
         dependencies.add(new XAssetDependency(
            new XDataSourceAsset(name), this,
            XAssetDependency.XQUERY_XDATASOURCE, desc));
      }

      String ds = dataSource.getFullName();
      // check if depends on a related partition
      String partition = xquery.getPartition();

      if(partition != null) {
         String desc = generateDescription(catalog.getString(id, query),
            catalog.getString("common.xasset.xpartition", partition));
         XPartition xpartition = getXPartition(ds, partition);
         String folder = xpartition == null ? null : xpartition.getFolder();
         String path =
            XUtil.getDataModelDisplayPath(ds, folder, null, partition);
         dependencies.add(new XAssetDependency(new XPartitionAsset(path), this,
                                               XAssetDependency.XQUERY_XPARTITION, desc));
      }

      // check if depends on another query for variables
      Enumeration<String> e = xquery.getVariableNames();

      while(e.hasMoreElements()) {
         String vname = e.nextElement();
         XVariable variable = xquery.getVariable(vname);
         String qname = null;

         if(variable instanceof UserVariable) {
            qname = ((UserVariable) variable).getChoiceQuery();
         }
         else if(variable instanceof QueryVariable) {
            qname = ((QueryVariable) variable).getQuery();
         }

         if(qname != null && !qname.equals(query)) {
            String desc = generateDescription(
               catalog.getString("common.xasset.variable1", vname, query),
               catalog.getString("common.xasset.query1", qname));
            dependencies.add(new XAssetDependency(
               new XQueryAsset(qname), this,
               XAssetDependency.XQUERY_XQUERY, desc));
         }
      }

      // check if use another query for auto drill
      XSelection selection = xquery.getSelection();

      if(selection != null) {
         Enumeration<XMetaInfo> metainfos = selection.getXMetaInfos();

         while(metainfos.hasMoreElements()) {
            XMetaInfo meta = metainfos.nextElement();

            if(meta != null) {
               XDrillInfo dinfo = meta.getXDrillInfo();

               if(dinfo != null) {
                  Enumeration paths = dinfo.getDrillPaths();

                  while(paths.hasMoreElements()) {
                     DrillPath path = (DrillPath) paths.nextElement();
                     DrillSubQuery dquery = path.getQuery();
                     String queryName = dquery == null ?
                        null : dquery.getQuery();

                     if(queryName != null && !queryName.equals(query)) {
                        String desc = generateDescription(
                           catalog.getString("common.xasset.query2", query),
                           catalog.getString("common.xasset.query1",
                           queryName));
                        dependencies.add(new XAssetDependency(
                           new XQueryAsset(queryName), this,
                           XAssetDependency.XQUERY_DRILL_XQUERY, desc));
                     }
                  }
               }
            }
         }
      }

      return dependencies.toArray(new XAssetDependency[0]);
   }

   private XPartition getXPartition(String datasource, String name) {
      try {
         XRepository repository = XFactory.getRepository();
         XDataModel model = repository.getDataModel(datasource);

         if(model == null) {
            return null;
         }

         return model.getPartition(name);
      }
      catch(Exception ignore) {
      }

      return null;
   }

   /**
    * Get the path of this asset.
    * @return the path of this asset.
    */
   @Override
   public String getPath() {
      return query;
   }

   /**
    * Get the type of this asset.
    * @return the type of this asset.
    */
   @Override
   public String getType() {
      return XQUERY;
   }

   /**
    * Get the owner of this asset if any.
    *
    * @return the owner of this asset if any.
    */
   @Override
   public IdentityID getUser() {
      return null;
   }

   /**
    * Parse an identifier to a real asset.
    * @param identifier the specified identifier, usually with the format of
    * ClassName^path.
    */
   @Override
   public void parseIdentifier(String identifier) {
      int idx = identifier.indexOf("^");
      String className = identifier.substring(0, idx);

      if(!className.equals(getClass().getName())) {
         return;
      }

      query = identifier.substring(idx + 1);
   }

   /**
    * Create an asset by its path and owner if any.
    *
    * @param path         the specified asset path.
    * @param userIdentity the specified asset owner if any.
    */
   @Override
   public void parseIdentifier(String path, IdentityID userIdentity) {
      query = path;
   }

   /**
    * Convert this asset to an identifier.
    * @return an identifier.
    */
   @Override
   public String toIdentifier() {
      return getClass().getName() + "^" + query;
   }

   public synchronized XQuery getXQuery() {
      return null;
   }

   /**
    * Parse content of the specified asset from input stream.
    */
   @Override
   public synchronized void parseContent(InputStream input, XAssetConfig config, boolean isImport)
      throws Exception
   {
   }

   /**
    * Write content of the specified asset to an output stream.
    */
   @Override
   public synchronized boolean writeContent(OutputStream output) throws Exception {
      return true;
   }

   @Override
   public boolean exists() {
      return false;
   }

   @Override
   public long getLastModifiedTime() {
      if(lastModifiedTime != 0) {
         return lastModifiedTime;
      }

      XQuery xQuery = getXQuery();
      return xQuery == null ? 0 : xQuery.getLastModified();
   }

   @Override
   public Resource getSecurityResource() {
      return new Resource(ResourceType.QUERY, query);
   }

   /**
    * Get auto drill links.
    */
   public List<Hyperlink> getDrillLinks() {
      List<Hyperlink> drillLinks = new ArrayList<>();
      XQueryRepository xrep;
      XQuery xquery = null;

      if(xquery == null) {
         return new ArrayList<>();
      }

      XSelection selection = xquery.getSelection();

      if(selection != null) {
         Enumeration<XMetaInfo> metainfos = selection.getXMetaInfos();

         while(metainfos.hasMoreElements()) {
            XMetaInfo meta = metainfos.nextElement();

            if(meta == null) {
               continue;
            }

            meta.processDrillLinks(drillLinks);
         }
      }

      return drillLinks;
   }

   private String query;
   private transient ReportSheet report;
}
