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
package inetsoft.web.portal.controller.database;

import inetsoft.util.data.CommonKVModel;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.util.XUtil;
import inetsoft.util.ExtendedDecimalFormat;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.model.FormatInfoModel;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.cube.XMetaInfoModel;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.*;

public class DatabaseModelUtil {
   /**
    * Use overloaded methods with parr parameters
    */
   @Deprecated
   public static AssetEntry getDatabaseEntry(String entryPath,
                                             AssetRepository repository,
                                             String additional, Principal principal)
      throws Exception
   {
      return getDatabaseEntry(entryPath, null, repository, additional, principal);
   }

   /**
    * Gets the database asset entry for the specified path.
    *
    * @param entryPath the path to the database entry.
    *
    * @param parr see {@link AssetEntry#PATH_ARRAY} for special chars
    *
    * @return the asset entry.
    *
    * @throws Exception if the entry could not be obtained.
    */
   public static AssetEntry getDatabaseEntry(String entryPath, String parr,
                                             AssetRepository repository,
                                             String additional, Principal principal)
      throws Exception
   {
      AssetEntry resultEntry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.PHYSICAL_FOLDER,
         entryPath, null);

      if(parr != null) {
         resultEntry.setProperty(AssetEntry.PATH_ARRAY, parr);
      }

      LinkedList<AssetEntry> path = new LinkedList<>();

      while(resultEntry != null) {
         path.addFirst(resultEntry);
         resultEntry = resultEntry.getParent();
      }

      resultEntry = path.removeFirst();

      while(!path.isEmpty()) {
         addAdditionalProperties(resultEntry, additional);
         AssetEntry targetEntry = path.removeFirst();
         AssetEntry[] entries = repository.getEntries(
            resultEntry, principal, ResourceAction.READ, new AssetEntry.Selector(
               AssetEntry.Type.DATA, AssetEntry.Type.PHYSICAL,
               AssetEntry.Type.FOLDER));
         boolean found = false;

         for(AssetEntry entry : entries) {
            if(entry.getName().equals(targetEntry.getName())) {
               resultEntry = entry;
               found = true;
               break;
            }
         }

         if(!found) {
            throw new FileNotFoundException(targetEntry.getPath());
         }
      }

      return resultEntry;
   }

   public static void addAdditionalProperties(AssetEntry entry, String additional) {
      entry.setProperty(XUtil.DATASOURCE_ADDITIONAL, !StringUtils.isEmpty(additional) ? additional :
         XUtil.OUTER_MOSE_LAYER_DATABASE);
      entry.setProperty(XUtil.PORTAL_DATA, "true");
   }

   /**
    * Get a specific additional data source.
    */
   public static XDataSource getDatasource(XDataSource ds, String additional) {
      if(ds instanceof JDBCDataSource) {
         JDBCDataSource jds = (JDBCDataSource) ds;

         if(additional != null && !XUtil.OUTER_MOSE_LAYER_DATABASE.equals(additional)) {
            JDBCDataSource source =  jds.getDataSource(additional);
            return source == null ? ds : source;
         }
      }

      return ds;
   }

   /**
    * Get the source table of auto alias by outgoing.
    * @param table real table name
    * @return outgoing alias source. <tt>null</tt> if it's not outgoing.
    */
   @Nullable
   public static String getOutgoingAutoAliasSource(String table, XPartition partition,
                                                   XPartition applyAutoAliasPartition)
   {
      if(partition == null || table == null) {
         return null;
      }

      XPartition originalPartition = partition;

      if(applyAutoAliasPartition == null) {
         applyAutoAliasPartition = partition.applyAutoAliases();
      }

      String autoAliasSource = table;

      while(originalPartition.getPartitionTable(autoAliasSource) == null &&
         !originalPartition.isAlias(autoAliasSource) && applyAutoAliasPartition.isAlias(table))
      {
         XPartition.PartitionTable partitionTable =
            applyAutoAliasPartition.getPartitionTable(autoAliasSource);

         if(partitionTable == null || applyAutoAliasPartition.getAliasTable(table) == null) {
            return null;
         }

         if(partitionTable.getSourceTable() != null) {
            return partitionTable.getSourceTable();
         }

         return applyAutoAliasPartition.getAliasTable(table);
      }

      return autoAliasSource;
   }

   /**
    * Get the source table of auto alias by outgoing.
    */
   public static String getOutgoingAutoAliasSource(String table, XPartition partition) {
      return getOutgoingAutoAliasSourceOrTable(table, partition, partition.applyAutoAliases());
   }

   /**
    * @param table real table name
    * @return outgoing source name or <code>table</code>
    */
   public static String getOutgoingAutoAliasSourceOrTable(String table,
                                                          XPartition partition,
                                                          XPartition applyPartition)
   {
      String aliasSource = getOutgoingAutoAliasSource(table, partition, applyPartition);

      return aliasSource == null ? table : aliasSource;
   }

   public static XMetaInfo createXMetaInfo(XFormatInfoModel formatInfoModel,
                                           AutoDrillInfo autoDrillInfo)
   {
      XMetaInfo xMetaInfo = new XMetaInfo();

      if(formatInfoModel != null) {
         String format = FormatInfoModel.getDurationFormat(formatInfoModel.getFormat(),
            formatInfoModel.isDurationPadZeros());
         XFormatInfo xFormatInfo =
            new XFormatInfo(format, formatInfoModel.getFormatSpec());
         xMetaInfo.setXFormatInfo(xFormatInfo);
      }

      if(autoDrillInfo != null) {
         xMetaInfo.setXDrillInfo(createXDrillInfo(autoDrillInfo));
      }

      return xMetaInfo;
   }

   public static XDrillInfo createXDrillInfo(AutoDrillInfo autoDrillInfo) {
      if(autoDrillInfo == null) {
         return null;
      }

      XDrillInfo xDrillInfo = new XDrillInfo();

      for(AutoDrillPathModel path : autoDrillInfo.getPaths()) {
         DrillPath drillPath = new DrillPath(path.getName());
         drillPath.setLink(path.getLink());
         drillPath.setToolTip(path.getTip());
         drillPath.setTargetFrame("".equals(path.getTargetFrame()) ?
            "SELF" : path.getTargetFrame());
         drillPath.setSendReportParameters(path.isPassParams());
         drillPath.setDisablePrompting(path.isDisablePrompting());
         drillPath.setLinkType(path.getLinkType());
         DrillSubQueryModel subQueryModel = path.getQuery();

         if(subQueryModel != null) {
            DrillSubQuery sub = new DrillSubQuery();
            AssetEntry entry = subQueryModel.getEntry();
            sub.setWsEntry(entry);
            List<CommonKVModel<String, String>> params = subQueryModel.getParams();

            if(params != null && params.size() != 0) {
               path.getQuery().getParams().forEach(p -> sub.setParameter(p.getKey(), p.getValue()));
            }

            drillPath.setQuery(sub);
         }

         for(DrillParameterModel drillParameter : path.getParams()) {
            drillPath.setParameterField(drillParameter.getName(), drillParameter.getField());

            if(drillParameter.getType() != null && !drillParameter.getType().isEmpty())  {
               drillPath.setParameterType(drillParameter.getName(), drillParameter.getType());
            }
         }

         xDrillInfo.addDrillPath(drillPath);
      }

      return xDrillInfo;
   }

   public static XMetaInfo createXMetaInfo(XMetaInfoModel metaInfoModel) {
      if(metaInfoModel == null) {
         return null;
      }

      XMetaInfo xMetaInfo = DatabaseModelUtil.createXMetaInfo(metaInfoModel.getFormatInfo(),
         metaInfoModel.getDrillInfo());
      xMetaInfo.setAsDate(metaInfoModel.isAsDate());
      xMetaInfo.setDatePattern(metaInfoModel.getDatePattern());

      if(!Tool.isEmptyString(metaInfoModel.getLocale())) {
         xMetaInfo.setLocale(new Locale(metaInfoModel.getLocale()));
      }

      return xMetaInfo;
   }

   public static XFormatInfoModel createXFormatInfoModel(XFormatInfo xFormatInfo) {
      if(xFormatInfo == null) {
         return null;
      }

      XFormatInfoModel formatInfo = new XFormatInfoModel();
      formatInfo.setFormat(xFormatInfo.getFormat());
      formatInfo.setFormatSpec(xFormatInfo.getFormatSpec());
      formatInfo.setDecimalFmts(ExtendedDecimalFormat.getSuffix().toArray(new String[0]));

      return formatInfo;
   }

   public static XMetaInfoModel createXMetaInfoModel(XMetaInfo metaInfo, XRepository repository,
                                                     Principal principal)
      throws Exception
   {
      if(metaInfo == null) {
         return null;
      }

      XMetaInfoModel metaInfoModel = new XMetaInfoModel();
      metaInfoModel.setAsDate(metaInfo.isAsDate());

      if(metaInfo.getLocale() != null) {
         metaInfoModel.setLocale(metaInfo.getLocale().toString());
      }

      if(metaInfo.getDatePattern() != null) {
         metaInfoModel.setDatePattern(metaInfo.getDatePattern());
      }

      if(metaInfo.getXFormatInfo() != null) {
         metaInfoModel.setFormatInfo(
            DatabaseModelUtil.createXFormatInfoModel(metaInfo.getXFormatInfo()));
      }

      metaInfoModel.setDrillInfo(DatabaseModelUtil.createAutoDrillInfoModel(
         metaInfo.getXDrillInfo(), repository, principal));

      return metaInfoModel;
   }

   public static AutoDrillInfo createAutoDrillInfoModel(XDrillInfo xDrillInfo,
                                                        XRepository repository,
                                                        Principal principal)
      throws Exception
   {
      if(xDrillInfo == null) {
         return new AutoDrillInfo();
      }

      AutoDrillInfo drillInfo = new AutoDrillInfo();
      @SuppressWarnings("unchecked")
      Enumeration<DrillPath> drillPaths = xDrillInfo.getDrillPaths();
      List<AutoDrillPathModel> paths = new ArrayList<>();

      while(drillPaths.hasMoreElements()) {
         DrillPath path = drillPaths.nextElement();

         if(path.getLinkType() != DrillPath.WEB_LINK &&
            path.getLinkType() != DrillPath.VIEWSHEET_LINK)
         {
            continue;
         }

         AutoDrillPathModel autoPath = new AutoDrillPathModel();

         autoPath.setName(path.getName());
         autoPath.setLink(path.getLink());
         autoPath.setTargetFrame(path.getTargetFrame());
         autoPath.setTip(path.getToolTip());
         autoPath.setPassParams(path.isSendReportParameters());
         autoPath.setDisablePrompting(path.isDisablePrompting());
         autoPath.setLinkType(path.getLinkType());

         @SuppressWarnings("unchecked")
         Enumeration<String> parameters = path.getParameterNames();
         List<DrillParameterModel> params = new ArrayList<>();

         while(parameters.hasMoreElements()) {
            DrillParameterModel param = new DrillParameterModel();
            String name = parameters.nextElement();
            param.setName(name);
            param.setField(path.getParameterField(name));
            param.setType(path.getParameterType(name));
            params.add(param);
         }

         autoPath.setParams(params);
         autoPath.setQuery(createDrillSubQueryModel(path.getQuery()));

         paths.add(autoPath);
      }

      drillInfo.setPaths(paths);

      return drillInfo;
   }

   public static DrillSubQueryModel createDrillSubQueryModel(DrillSubQuery subQuery) {
      if(subQuery == null) {
         return null;
      }

      DrillSubQueryModel subQueryModel = new DrillSubQueryModel();
      subQueryModel.setEntry(getSubWSEntry(subQuery.getWsEntry()));
      List<CommonKVModel<String, String>> subParams = new ArrayList<>();
      Iterator<String> subQueryParameterNames = subQuery.getParameterNames();

      while(subQueryParameterNames.hasNext()) {
         CommonKVModel param = new CommonKVModel();
         String name = subQueryParameterNames.next();
         param.setKey(name);
         param.setValue(subQuery.getParameter(name));
         subParams.add(new CommonKVModel(name, subQuery.getParameter(name)));
      }

      subQueryModel.setParams(subParams);

      return subQueryModel;
   }

   private static AssetEntry getSubWSEntry(AssetEntry wsEntry) {
      IndexedStorage indexedStorage = IndexedStorage.getIndexedStorage();
      AssetFolder folder = null;
      AssetEntry parent = wsEntry.getParent();
      String pidentifier = parent.toIdentifier();

      try {
         folder = (AssetFolder) indexedStorage.getXMLSerializable(pidentifier, null);
      }
      catch(Exception ex) {
         LOG.debug("Failed to parse: " + pidentifier, ex);
      }

      if(folder == null) {
         return wsEntry;
      }

      AssetEntry assetEntry = folder.getEntry(wsEntry);

      return assetEntry == null ? wsEntry : assetEntry;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(DatabaseModelUtil.class);
}
