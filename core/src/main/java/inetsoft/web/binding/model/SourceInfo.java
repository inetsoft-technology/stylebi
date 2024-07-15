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
package inetsoft.web.binding.model;

import inetsoft.report.internal.binding.SourceAttr;
import inetsoft.uql.*;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SourceInfo {
   public SourceInfo() {
   }

   public SourceInfo(SourceAttr sattr) {
      this.source = sattr.getSource();
      this.prefix = sattr.getPrefix();
      this.type = sattr.getType();
      this.dataSourceType = getSourceType(sattr);
      int joinCount = sattr.getJoinSourceCount();

      if(joinCount > 0) {
         for(int i = 0; i < joinCount; i++) {
            SourceAttr join = sattr.getJoinSource(i);
            SourceInfo joinSrc = new SourceInfo();
            joinSrc.setSource(join.getSource());
            joinSrc.setPrefix(join.getPrefix());
            joinSrc.setType(join.getType());
            joinSources.add(joinSrc);
         }
      }

      loadSupportFullOutJoin();
   }

   public SourceInfo(inetsoft.uql.asset.SourceInfo sinfo) {
      this.source = sinfo.getSource();
      this.prefix = sinfo.getPrefix();
      this.type = sinfo.getType();
      this.view = sinfo.toView();
      this.dataSourceType = getSourceType(sinfo);
      this.isRest = this.dataSourceType != null &&
         this.dataSourceType.startsWith(inetsoft.uql.asset.SourceInfo.REST_PREFIX);
   }

   public String getSource() {
      return source;
   }

   public void setSource(String src) {
      this.source = src;
   }

   public String getPrefix() {
      return prefix;
   }

   public void setPrefix(String prefix) {
      this.prefix = prefix;
   }

   public int getType() {
      return type;
   }

   public void setType(int type) {
      this.type = type;
   }

   public String getView() {
      return view;
   }

   public void setView(String view) {
      this.view = view;
   }

   public String getDataSourceType() {
      return dataSourceType;
   }

   public void setDataSourceType(String dataSourceType) {
      this.dataSourceType = dataSourceType;
   }

   public boolean isSqlServer() {
      return sqlServer;
   }

   public void setSqlServer(boolean sql) {
      this.sqlServer = sql;
   }

   public boolean isSupportFullOutJoin() {
      return isSupportFullOutJoin;
   }

   public void setSupportFullOutJoin(boolean out) {
      this.isSupportFullOutJoin = out;
   }

   public boolean isRest() {
      return isRest;
   }

   public void setRest(boolean rest) {
      isRest = rest;
   }

   public boolean isBrowsable() {
      return browsable;
   }

   public void setBrowsable(boolean browsable) {
      this.browsable = browsable;
   }

   public List<SourceInfo> getJoinSources() {
      return joinSources;
   }

   public void setJoinSources(List<SourceInfo> joinSources) {
      this.joinSources = joinSources;
   }

   public inetsoft.uql.asset.SourceInfo toSourceAttr(inetsoft.uql.asset.SourceInfo sinfo)
   {
      if(sinfo == null) {
         return new inetsoft.uql.asset.SourceInfo(type, prefix, source);
      }

      if(source == null) {
         return sinfo;
      }

      if(Tool.equals(source, sinfo.getSource()) && type == sinfo.getType() &&
         Tool.equals(prefix, sinfo.getPrefix()))
      {
         return sinfo;
      }

      sinfo = new inetsoft.uql.asset.SourceInfo(type, prefix, source);

      return sinfo;
   }

   public void loadSupportFullOutJoin() {
      isSupportFullOutJoin = true;

      try {
         DataSourceRegistry reg = DataSourceRegistry.getRegistry();
         XDataSource ds = reg.getDataSource(prefix);

         if(ds != null) {
            SQLHelper helper = SQLHelper.getSQLHelper(ds);

            if(!helper.supportsOperation(SQLHelper.FULL_OUTERJOIN)) {
               isSupportFullOutJoin = false;
            }
         }
      }
      catch(Exception ex){
      }
   }

   /**
    * Get the source type of a XSourceInfo.
    */
   private String getSourceType(XSourceInfo sinfo) {
      String sourceType = null;

      try {
         String dsName = sinfo == null ? null : sinfo.getPrefix();
         XRepository xrep = XFactory.getRepository();
         XDataSource dx = xrep == null ? null : xrep.getDataSource(dsName);
         sourceType = dx == null ? null : dx.getType();
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }

      return sourceType;
   }

   private String source;
   private String prefix;
   private int type;
   private String view;
   private String dataSourceType;
   private boolean sqlServer = false;
   private boolean isSupportFullOutJoin = false;
   private boolean isRest = false;
   private boolean browsable = true;
   private List<SourceInfo> joinSources = new ArrayList<>();
   private static final Logger LOG = LoggerFactory.getLogger(SourceInfo.class);
}
