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
package inetsoft.mv;

import inetsoft.mv.data.MV;
import inetsoft.mv.data.MVStorage;
import inetsoft.mv.fs.*;
import inetsoft.mv.trans.*;
import inetsoft.report.composition.QueryTreeModel.QueryNode;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.report.composition.execution.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.util.swap.XSwapUtil;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.InflaterInputStream;

/**
 * MVDef, the definition of one materialized view for a table assembly, which
 * belongs to a worksheet. The worksheet is the base data source of one
 * viewsheet.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
@SuppressWarnings("WeakerAccess")
public final class MVDef implements Comparable, XMLSerializable, Serializable, Cloneable, MVInfo {
   /**
    * Get the MVHeader of this data ref. Using this header, we could access
    * its associated mv data properly.
    */
   public static String getMVHeader(DataRef ref) {
      boolean numeric = false;

      if(ref instanceof GroupRef) {
         ref = ((GroupRef) ref).getDataRef();
      }

      // @by billh, this data ref comes from one mv table assembly, when
      // generating materialized view, columns are from the base table
      // assembly of this mv table assembly, so we need to use attribute
      // (not alias) to find column in the generated materialized view
      if(ref instanceof ColumnRef) {
         numeric = XSchema.isNumericType(ref.getDataType());
         ref = ((ColumnRef) ref).getDataRef();
      }

      // sub mv?
      if(!(ref instanceof RangeRef)) {
         return ref.getAttribute();
      }

      DataRef oref = ((RangeRef) ref).getDataRef();

      if(ref instanceof DateRangeRef) {
         int dtype = ((DateRangeRef) ref).getDateOption();

         // if date range is none, the original column should be used. there is never a
         // None(col) in the MV data. (49843).
         if(dtype == DateRangeRef.NONE) {
            return oref.getAttribute();
         }

         return DateMVColumn.getRangeName(oref.getAttribute(), dtype);
      }
      else if(ref instanceof NumericRangeRef && numeric) {
         return RangeMVColumn.getRangeName(oref.getAttribute());
      }
      // named group should just get details. (49867)
      else if(ref instanceof NamedRangeRef) {
         return oref.getAttribute();
      }

      return ref.getAttribute();
   }

   /**
    * Get the header for logical model.
    */
   public static String getLMHeader(String header) {
      if(header == null) {
         return null;
      }

      int sep = header.indexOf(':');

      if(sep >= 0) {
         String ent = header.substring(0, sep);
         String attr = header.substring(sep + 1);
         String nheader = Tool.replaceAll(ent, ".", "_") + ":" + attr;
         return nheader.equals(header) ? null : nheader;
      }

      return null;
   }

   /**
    * Get the index of the specified column header.
    */
   public static int indexOfHeader(String header, String[] headers, int start) {
      for(int i = start; i < headers.length; i++) {
         if(headers[i].equals(header)) {
            return i;
         }
      }

      String nheader = getLMHeader(header);

      if(nheader != null) {
         return indexOfHeader(nheader, headers, start);
      }

      // check for aliased column (63670)
      String basecol = getBaseColumn(header);

      if(basecol != null) {
         int idx = indexOfHeader(basecol, headers, start);

         if(idx >= 0) {
            return idx;
         }
      }

      // if the header refers to a null column header (e.g. Column [2]),
      // we allow it to match any null column header (e.g. Column [1])
      // if there is only one null column since the column index of the
      // null column may be different in MV (which could be a sub-table)
      // and the actual table
      if(nullcol.matcher(header).matches()) {
         // check if this header may refer to a null column header
         List<Integer> nullheaders = new ArrayList<>();

         for(int i = start; i < headers.length; i++) {
            if(nullcol.matcher(headers[i]).matches()) {
               nullheaders.add(i);
            }
         }

         if(nullheaders.size() == 1) {
            return nullheaders.get(0);
         }
      }

      return -1;
   }

   // strip off the _n suffix
   private static String getBaseColumn(String header) {
      int idx = header.lastIndexOf('_');

      if(idx > 0) {
         String suffix = header.substring(idx + 1);

         try {
            Integer.parseInt(suffix);
            return header.substring(0, idx);
         }
         catch(Exception ex) {
         }
      }

      return null;
   }

   /**
    * Check if the MVDef has incremental update conditions defined.
    * Used purely for visual confirmation on MV Manage page in EM.
    */
   public boolean isIncremental() {
      return incremental;
   }

   /**
    * Returns whether or not there are incremental conditions on a
    * given worksheet.  Used to initialize the 'incremental' flag, since
    * parsing the worksheet using getWorksheet is considerably slow when
    * processing MVDefs in a loop.
    *
    * @param ws The worksheet which is checked for incremental conditions.
    */
   private boolean checkIncremental(Worksheet ws) {
      String table = getMVTable();
      TableAssembly assembly = (TableAssembly) ws.getAssembly(table);
      ConditionListWrapper conds = assembly.getMVConditionList();

      return conds != null && !conds.isEmpty() ||
         assembly.isMVForceAppendUpdates();
   }

   /**
    * Set if the MV could be an candidate for sharing between different viewsheets.
    */
   public void setShareable(boolean shareable) {
      this.shareable = shareable;
   }

   /**
    * Check if the MV could be an candidate for sharing.
    */
   public boolean isShareable() {
      return shareable;
   }

   /**
    * Get the name of the MVDef by providing viewsheet name and bound table.
    */
   private String getName(Viewsheet vs, String otname) {
      if(vs == null) {
         String name = String.join("_", getEntry().getPath(), otname,
                                   System.currentTimeMillis() + "", index.getAndIncrement() + "",
                                   OrganizationManager.getInstance().getCurrentOrgID());
         return normalize(name);
      }

      AssetEntry entry = vs.getBaseEntry();
      StringBuilder builder = new StringBuilder();
      if(entry != null) {
         builder.append(entry.getPath());
      }

      if(otname != null) {
         builder.append("_");
         builder.append(otname);
      }

      builder.append("_");
      builder.append(System.currentTimeMillis());
      builder.append("_");
      builder.append(index.getAndIncrement());
      builder.append("_");
      builder.append(OrganizationManager.getInstance().getCurrentOrgID());

      return normalize(builder.toString());
   }

   /**
    * Normalize a string value, so that it could be file name.
    */
   private static String normalize(String val) {
      StringBuilder sb = new StringBuilder();
      int length = val.length();

      for(int i = 0; i < length; i++) {
         char c = val.charAt(i);

         if(c == '/' || c == '\\' || c == '?' || c == '*' || c == '>' ||
            c == '|' || c == ':' || c == '<' || c == '"')
         {
            sb.append('_');
         }
         else {
            sb.append(c);
         }
      }

      return sb.toString();
   }

   /**
    * Create an instance of MVDef.
    */
   public MVDef() {
      super();
   }

   /**
    * Create an instance of MVDef.
    */
   public MVDef(String vsId, String wsId, String tname, String otname, Viewsheet vs,
                Worksheet ws, Identity user, TransformationDescriptor desc,
                boolean sub, boolean sonly, boolean bypass)
   {
      this(vsId, wsId, tname, otname, vs, ws,
           user == null ? null : new Identity[]{ user }, desc, sub, sonly, bypass);
   }

   /**
    * Create an instance of MVDef.
    */
   public MVDef(String vsId, String wsId, String tname, String otname, Viewsheet vs,
                Worksheet ws, Identity[] users, TransformationDescriptor desc,
                boolean sub, boolean sonly, boolean bypass)
   {
      this.vsId = vsId;
      this.wsId = wsId;
      this.tname = tname;
      this.otname = otname;
      this.users = users;
      this.sub = sub;
      List<MVColumn> columns = createMVColumns(ws, vs, tname, otname, desc, sonly);
      this.mvname = getName(vs, otname);
      this.container = new MVContainer(columns, ws);
      this.incremental = checkIncremental(ws);
      this.containerRef = new SoftReference<>(container);
      this.lastUpdateTime = System.currentTimeMillis();

      List<MVColumn> nonum = new ArrayList<>();

      for(MVColumn mcol : columns) {
         if(!mcol.isDimension() && !mcol.isNumeric()) {
            nonum.add(mcol);
         }
      }

      // if only one non-numeric column exists in the mv, treat it as for
      // distinct count
      if(nonum.size() == 1) {
         breakcol = nonum.get(0).getColumn().getName();
      }

      VSAssembly[] varr = getVSAssemblies(vs, tname);
      AggregateInfo[] infos = getInfos(varr);

      // special handling for distinct count, breaking on a single
      // count-distinct to allow calculation to be distributed
      for(AggregateInfo ainfo : infos) {
         List<String> cols = findCountDistinct(ainfo);

         if(cols.size() > 0) {
            breakcol = cols.get(0);
            break;
         }
      }

      createMVData(ws, vs, bypass);
   }

   public String getMVName() {
      return this.mvname;
   }

   public void setMVName(String mvname) {
      this.mvname = mvname;
   }

   /**
    * Update this mv def with real data.
    */
   public MVDef update() {
      String file = MVStorage.getFile(mvname);
      MV mv = null;

      String orgID = SUtil.getOrgIDFromMVPath(mvname);

      try {
         mv = MVStorage.getInstance().get(file, orgID);
      }
      catch(Exception ex) {
         LOG.warn("Failed to update materialized view definition " + mvname +
                     " in file " + file, ex);
      }

      if(mv == null) {
         return this;
      }
      else {
         return mv.getDef(true, orgID);
      }
   }

   /**
    * Refresh this mv def.
    */
   public void updateLastUpdateTime() {
      Object value = ThreadContext.getSessionInfo("data.cache.ts");
      this.lastUpdateTime = value instanceof Number ? ((Number) value).longValue()
         : System.currentTimeMillis();
   }

   /**
    * Check if this MVDef matches the specified entry.
    */
   public boolean matches(AssetEntry entry) {
      return entry != null &&
         ((entry.getType() == AssetEntry.Type.VIEWSHEET && Tool.equals(vsId, entry.toIdentifier())) ||
            (entry.getType() == AssetEntry.Type.WORKSHEET && Tool.equals(wsId, entry.toIdentifier())));
   }

   /**
    * Get the mv column by providing its column name
    */
   public MVColumn getColumn(String col, boolean dim) {
      return getColumn(col, dim, false);
   }

   /**
    * Get the mv column by providing its column name
    */
   public MVColumn getColumn(String col, boolean dim, boolean dynamicOnly) {
      List<MVColumn> columns = getColumns();

      for(MVColumn mcol : columns) {
         if(mcol.matches(col, dim) && (!dynamicOnly || mcol instanceof XDynamicMVColumn)) {
            return mcol;
         }
      }

      String ncol = getLMHeader(col);

      if(ncol != null) {
         return getColumn(ncol, dim, dynamicOnly);
      }

      return null;
   }

   /**
    * Share object(s) with target def.
    */
   public void shareWith(MVDef def2) {
      if(getContainer() != null && def2.getContainer() != null) {
         def2.getContainer().columns = getContainer().columns;
      }
   }

   /**
    * Get all mv columns.
    */
   public List<MVColumn> getColumns() {
      MVContainer container = getContainer();
      return container == null ? Collections.emptyList() : container.columns;
   }

   /**
    * Check if one column is in removed column.
    */
   public boolean isRemovedColumn(String col) {
      List<MVColumn> rcols = getRemovedColumns();

      for(MVColumn mcol : rcols) {
         if(mcol.matches(col, true)) {
            return true;
         }

         if(mcol.matches(col, false)) {
            return true;
         }
      }

      String ncol = getLMHeader(col);
      return ncol != null && isRemovedColumn(ncol);
   }

   /**
    * Get all removed columns.
    */
   private List<MVColumn> getRemovedColumns() {
      MVContainer container = getContainer();
      return container == null ? Collections.emptyList() : container.rcolumns;
   }

   public void addRemovedColumn(ColumnRef column) {
      MVContainer container = getContainer();

      if(container != null) {
         container.rcolumns.add(new MVColumn(column));
      }
   }

   /**
    * Remove one column from columns, may be caused by vpm.
    */
   public MVColumn removeColumn(int index) {
      List<MVColumn> columns = getColumns();
      MVColumn removed = columns.remove(index);

      if(removed != null) {
         MVContainer container = getContainer();

         if(container != null) {
            container.rcolumns.add(removed);
         }
      }

      return removed;
   }

   /**
    * Get viewsheet identifier.
    */
   public String getVsId() {
      return vsId;
   }

   public String getVsPath() {
      return AssetEntry.createAssetEntry(vsId).getPath();
   }

   /**
    * Get worksheet identifier.
    */
   public String getWsId() {
      return wsId;
   }

   public String getWsPath() {
      return AssetEntry.createAssetEntry(wsId).getPath();
   }

   /**
    * Get the name of this MVDef.
    */
   public String getName() {
      return mvname;
   }

   /**
    * Get the table assemly.
    */
   public String getMVTable() {
      return tname;
   }

   /**
    * Get the bound table assemly.
    */
   public String getBoundTable() {
      return otname;
   }

   /**
    * Get the last update time.
    */
   public long getLastUpdateTime() {
      return lastUpdateTime;
   }

   /**
    * Check if the version is saved before 11.2
    */
   public boolean isPreV112() {
      return pre_v11_2;
   }

   /**
    * Get the file position.
    */
   public long getFilePosition() {
      return fpos;
   }

   /**
    * Check if is sub mv.
    */
   public boolean isSub() {
      return sub;
   }

   /**
    * Get the asset entry.
    */
   public AssetEntry getEntry() {
      return isWSMV() ? AssetEntry.createAssetEntry(wsId) : AssetEntry.createAssetEntry(vsId);
   }

   /**
    * Set the asset entry.
    */
   public void setEntry(AssetEntry entry) {
      if(entry != null) {
         // only global is supported
         // change register name
         String id = entry.isWorksheet() ? wsId : vsId;

         if(data.isRegistered(id)) {
            data.renameRegistered(id, entry.toIdentifier());
         }

         if(entry.isWorksheet()) {
            wsId = entry.toIdentifier();
         }
         else {
            vsId = entry.toIdentifier();
         }
      }
   }

   public void setVsId(String vsId) {
      this.vsId = vsId;
   }

   public void setWsId(String wsId) {
      this.wsId = wsId;
   }

   /**
    * Set mv created success.
    */
   public void setSuccess(boolean success) {
      this.success = success;
   }

   /**
    * Check mv created success or not.
    */
   public boolean isSuccess() {
      return success;
   }

   /**
    * Set whether mv has been updated.
    */
   public void setUpdated(boolean updated) {
      this.updated = updated;
   }

   /**
    * Check if mv has been updated.
    */
   public boolean isUpdated() {
      return updated;
   }

   /**
    * Set the if the binding source is Logical model.
    */
   public void setLogicalModel(boolean directLM) {
      this.directLM = directLM;
   }

   /**
    * Check if the binding source is Logical model.
    */
   public boolean isLogicalModel() {
      return directLM;
   }

   /**
    * Set bounded logic model tables.
    */
   public void setLMBoundTables(String[] lmtables) {
      this.lmtables = lmtables;
   }

   /**
    * Get logic model bounded tables.
    */
   public String[] getLMBoundTables() {
      return this.lmtables;
   }

   /**
    * Get the break-by column.
    */
   public String getBreakColumn() {
      return breakcol;
   }

   /**
    * Get the data cycle.
    */
   public String getCycle() {
      return cycle;
   }

   /**
    * Set the data cycle.
    */
   public void setCycle(String cycle) {
      this.cycle = cycle;
   }

   /**
    * Check if is changed.
    */
   public boolean isChanged() {
      return changed;
   }

   /**
    * Set whether this mv is changed.
    */
   public void setChanged(boolean changed) {
      this.changed = changed;
   }

   /**
    * Get the worksheet.
    */
   public Worksheet getWorksheet() {
      MVContainer container = getContainer();
      return container == null ? null : container.ws;
   }

   /**
    * Get the user.
    */
   public Identity[] getUsers() {
      return users;
   }

   /**
    * Set the user.
    */
   public void setUsers(Identity[] users) {
      this.users = users;
   }

   public void sortUsers() {
      users = users == null ? null : Arrays.stream(users)
         .sorted( (u1, u2) ->
                     u1.getType() != u2.getType() ?
                        Integer.compare(u1.getType(), u2.getType()) :
                        u1.getName().compareTo(u2.getName()))
         .toArray(Identity[]::new);
   }

   /**
    * Check if contains the specified user.
    */
   public boolean containsUser(Identity user) {
      if(user == null || (user.getType() == Identity.USER && "anonymous".equals(user.getName()))) {
         return users == null || users.length == 0 ||
            user != null && users.length == 1 && users[0].getName().equals(user.getName()) ||
            // MVSupport adds admin to user list when security provider is defined and
            // no user is used for creating MV. In that case, admin is saved in MVDef.
            // If we are passing user as null, which should match the condition
            // in MVSupport for adding admin.
            user == null && users.length == 1 && (users[0].getName().equals("Administrator") ||
                                                  users[0].getName().equals(XPrincipal.SYSTEM));
      }

      if(users == null) {
         return user.getType() == Identity.USER && !"anonymous".equals(user.getName());
      }

      for(Identity user1 : users) {
         if(user1.equals(user)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the mv container.
    */
   MVContainer getContainer() {
      // 1. try strong reference
      MVContainer container = this.container;

      if(container != null) {
         return container;
      }

      // 2. try weak reference
      container = containerRef != null ? containerRef.get() : null;

      if(container != null) {
         return container;
      }

      MVManager mgr = MVManager.getManager();
      long now = System.currentTimeMillis();

      synchronized(this) {
         container = (containerRef != null) ? containerRef.get() : null;

         if(container != null) {
            return container;
         }

         if(mgr.containsMV(this)) {
            mgr.fill(this);
         }

         // for network shared drive, there maybe a latency for file change
         // to appear on other machines, if a ws is not found, we wait for a
         // little to avoid the WS being missing
         if(lastLoad < now - 10000) {
            lastLoad = now;

            for(int i = 0; i < 10 && this.container == null; i++) {
               try {
                  Thread.sleep(200);
                  mgr.refresh();
               }
               catch(Exception ex) {
                  LOG.warn("Failed to refresh manager: " + ex, ex);
               }

               if(mgr.containsMV(this)) {
                  mgr.fill(this);
               }
            }
         }

         container = this.container;
         // release memory in time, container pointed to by cref (weak ref)
         this.container = null;
      }

      return container;
   }

   public MVMetaData getMetaData() {
      return data;
   }

   /**
    * Create mv columns.
    */
   private List<MVColumn> createMVColumns(
      Worksheet ws, Viewsheet vs, String tname, String otname,
      TransformationDescriptor desc, boolean sonly)
   {
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);
      ColumnSelection cols = table.getColumnSelection(true);
      List<MVColumn> list = new ArrayList<>();
      int cnt = cols.getAttributeCount();
      List<MVColumn> list2 = new ArrayList<>();
      VSAssembly[] varr = getVSAssemblies(vs, tname);
      AggregateInfo[] infos = getInfos(varr);

      for(int i = 0; i < cnt; i++) {
         ColumnRef wbcol = (ColumnRef) cols.getAttribute(i);
         ColumnRef bcol = VSUtil.getVSColumnRef(wbcol);
         MVColumn column = new MVColumn(bcol);
         WSColumn wscol = vs == null ? null : desc.getSelectionColumn(tname, wbcol);
         boolean conflict = vs != null && fixMVColumn(ws, tname, otname, column, wbcol,
                                                      wscol, infos, varr);
         conflict = vs != null && fixMVColumn(ws, tname, otname, column, wbcol,
                                                      wscol, infos, varr);

         if(conflict) {
            column.setDimension(true);
         }

         if(!column.isDimension()) {
            list.add(column);
            continue;
         }

         if(column.isDateTime()) {
            column.setDimension(false);
            MVColumn[] dates = DateMVColumn.createAll(table, column, sonly);

            for(MVColumn date : dates) {
               date.setDimension(false);
               list2.add(date);
            }

            list.add(column);
         }
         else if(column.isNumeric()) {
            boolean log = isLog(column, wscol, varr);
            RangeMVColumn range = RangeMVColumn.create(table, column, log);

            if(range != null) {
               list2.add(range);
            }

            list.add(column);
         }
         else {
            list.add(column);
         }

         if(conflict) {
            MVColumn copy = new MVColumn(column.getColumn(), false);
            list.add(copy);
         }
      }

      // append dynamic mv columns
      list.addAll(list2);
      return list;
   }

   /**
    * Fix the information in the specified mv column.
    */
   private static boolean isLog(MVColumn col, WSColumn wscol, VSAssembly[] varr) {
      ColumnRef bcol = col.getColumn();

      for(VSAssembly vsAssembly : varr) {
         if(vsAssembly instanceof TimeSliderVSAssembly) {
            TimeSliderVSAssembly tassembly = (TimeSliderVSAssembly) vsAssembly;
            TimeInfo tinfo = tassembly.getTimeInfo();

            if(!(tinfo instanceof SingleTimeInfo)) {
               continue;
            }

            DataRef[] arr = tassembly.getDataRefs();

            if(arr.length != 1 || !bcol.equals(arr[0])) {
               continue;
            }

            SingleTimeInfo stinfo = (SingleTimeInfo) tinfo;
            return !stinfo.isDateTime() && tassembly.isLogScale();
         }
      }

      RangeInfo rinfo = wscol == null ? null : wscol.getRangeInfo();

      if(rinfo != null) {
         return rinfo.isLogScale();
      }

      // no time slider binding to this data ref? log should be false
      // and user should not change the option at runtime (gui changes)
      return false;
   }

   private static GroupRef findGroup(AggregateInfo ainfo, ColumnRef bcol) {
      GroupRef gref = ainfo.getGroup(bcol);

      if(gref != null) {
         return gref;
      }

      for(int i = 0; i < ainfo.getGroupCount(); i++) {
         GroupRef group = ainfo.getGroup(i);
         DataRef col = group.getDataRef();

         while(col instanceof DataRefWrapper) {
            col = ((DataRefWrapper) col).getDataRef();

            if(bcol.equals(col)) {
               return group;
            }
         }
      }

      return null;
   }

   /**
    * Fix the information in the specified mv column.
    */
   private static boolean fixMVColumn(Worksheet ws, String tname,
                                      String otname, MVColumn col,
                                      ColumnRef wbcol, WSColumn wscol,
                                      AggregateInfo[] infos,
                                      VSAssembly[] varr)
   {
      ColumnRef bcol = col.getColumn();
      String dtype = bcol.getDataType();
      boolean confirmed = false;
      boolean conflict = false;

      // numeric number stored as dimension couldn't be used as measure
      // properly and is most likely not efficient anyway. Using double
      // column so it can be used as both dim and measure, and is reasonably
      // efficient for both
      if(XSchema.DOUBLE.equals(dtype) || XSchema.FLOAT.equals(dtype) ||
         XSchema.INTEGER.equals(dtype) || XSchema.SHORT.equals(dtype) ||
         XSchema.BYTE.equals(dtype) || XSchema.LONG.equals(dtype))
      {
         return false;
      }

      // mv table is not bound table? aggregate info must be moved up in
      // bound table. Check whether the mv column is a dimension or measure
      // using this aggregate info existing in this bound table
      if(!tname.equals(otname)) {
         TableAssembly tassembly = (TableAssembly) ws.getAssembly(tname);
         AggregateInfo ainfo = tassembly.getAggregateInfo();
         GroupRef group = ainfo.getGroup(wbcol);

         if(group != null) {
            if(!confirmed) {
               col.setDimension(true);
               confirmed = true;
            }
            else {
               if(!col.isDimension()) {
                  conflict = true;
               }
            }
         }

         AggregateRef aggregate = ainfo.getAggregate(wbcol);

         if(isMeasure(aggregate)) {
            if(!confirmed) {
               col.setDimension(false);
               confirmed = true;
            }
            else if(col.isDimension()) {
               conflict = true;
            }
         }
      }

      if(wscol != null) {
         if(!confirmed) {
            col.setDimension(true);
            confirmed = true;
         }
         else {
            if(!col.isDimension()) {
               conflict = true;
            }
         }
      }

      // predefined as dimension/measure in chart or crosstab?
      for(AggregateInfo info : infos) {
         GroupRef group = info == null ? null : findGroup(info, bcol);

         if(group != null) {
            if(!confirmed) {
               col.setDimension(true);
               confirmed = true;
            }
            else {
               if(!col.isDimension()) {
                  conflict = true;
               }
            }

            continue;
         }

         AggregateRef aggregate = info == null ? null : info.getAggregate(bcol);

         if(isMeasure(aggregate)) {
            if(!confirmed) {
               col.setDimension(false);
               confirmed = true;

            }
            else if(col.isDimension()) {
               conflict = true;
            }
         }
      }

      // bound to output assembly? it must be a measure
      // bound to selection assembly? it must be a dimension
      OUTER:
      for(VSAssembly vsAssembly : varr) {
         if(vsAssembly instanceof ListInputVSAssembly) {
            ListInputVSAssembly input = (ListInputVSAssembly) vsAssembly;
            ListBindingInfo binding = input.getListBindingInfo();
            String tname2 = binding == null ? null : binding.getTableName();

            if(tname2 == null || tname2.length() == 0) {
               continue;
            }

            DataRef vcolumn = binding.getValueColumn();

            if(vcolumn instanceof ColumnRef) {
               vcolumn = VSUtil.getVSColumnRef((ColumnRef) vcolumn);
            }

            DataRef lcolumn = binding.getLabelColumn();

            if(lcolumn instanceof ColumnRef) {
               lcolumn = VSUtil.getVSColumnRef((ColumnRef) lcolumn);
            }

            if(bcol.equals(vcolumn) || bcol.equals(lcolumn)) {
               if(!confirmed) {
                  col.setDimension(true);
                  confirmed = true;
               }
               else if(!col.isDimension()) {
                  conflict = true;
               }

               continue;
            }
         }
         else if(vsAssembly instanceof OutputVSAssembly) {
            OutputVSAssembly output = (OutputVSAssembly) vsAssembly;
            ScalarBindingInfo binding = output.getScalarBindingInfo();

            if(binding != null && isMeasureAggregate(binding.getAggregateFormula())) {
               DataRef column = binding.getColumn();

               if(column instanceof ColumnRef) {
                  column = VSUtil.getVSColumnRef((ColumnRef) column);
               }

               if(bcol.equals(column)) {
                  if(!confirmed) {
                     col.setDimension(false);
                     confirmed = true;
                  }
                  else if(col.isDimension()) {
                     conflict = true;
                  }

                  continue;
               }

               column = binding.getSecondaryColumn();

               if(column instanceof ColumnRef) {
                  column = VSUtil.getVSColumnRef((ColumnRef) column);
               }

               if(bcol.equals(column)) {
                  if(!confirmed) {
                     col.setDimension(false);
                     confirmed = true;
                  }
                  else if(col.isDimension()) {
                     conflict = true;
                  }

                  continue;
               }
            }
         }
         else if(vsAssembly instanceof SelectionVSAssembly) {
            SelectionVSAssembly selection = (SelectionVSAssembly) vsAssembly;
            DataRef[] arr = selection.getDataRefs();

            for(DataRef selectionRef : arr) {
               if(bcol.equals(selectionRef)) {

                  if(!confirmed) {
                     col.setDimension(true);
                     confirmed = true;
                  }
                  else if(!col.isDimension()) {
                     conflict = true;
                  }

                  continue OUTER;
               }
            }
         }
         else if(vsAssembly instanceof TableVSAssembly) {
            TableVSAssembly tassembly = (TableVSAssembly) vsAssembly;
            TableAssembly table = ws == null ? null :
               (TableAssembly) ws.getAssembly(tassembly.getTableName());

            if(table == null) {
               continue;
            }

            ColumnSelection cols = table.getColumnSelection(true);

            for(int j = 0; j < cols.getAttributeCount(); j++) {
               ColumnRef tcol = (ColumnRef) cols.getAttribute(j);
               tcol = VSUtil.getVSColumnRef(tcol);

               if(bcol.equals(tcol)) {
                  boolean dim = isDimension(table, tcol);

                  if(!confirmed) {
                     col.setDimension(dim);
                     confirmed = true;
                  }
                  else if(col.isDimension() != dim) {
                     conflict = true;
                  }

                  continue OUTER;
               }
            }
         }
      }

      return conflict;
   }

   private static boolean isMeasure(AggregateRef aggregate) {
      return aggregate != null && isMeasureAggregate(aggregate.getFormula());
   }

   private static boolean isMeasureAggregate(AggregateFormula formula) {
         // for aggregate that can operate on string without between treated as measure,
         // we don't mark it as conflict. otherwise an extra measure MVColumn will be
         // created later, which results in error in spark. (60331)
      return formula != null && formula != AggregateFormula.COUNT_ALL &&
         formula != AggregateFormula.COUNT_DISTINCT &&
         formula != AggregateFormula.FIRST &&
         formula != AggregateFormula.LAST &&
         formula != AggregateFormula.MIN &&
         formula != AggregateFormula.MAX;
   }

   /**
    * Check if is dimension.
    */
   private static boolean isDimension(TableAssembly table, ColumnRef col) {
      ColumnSelection cols = table.getColumnSelection();
      AggregateInfo ainfo = table.getAggregateInfo();
      col = AssetUtil.getColumnRefFromAttribute(cols, col, true);
      return ainfo.isEmpty() || ainfo.containsGroup(col);
   }

   /**
    * Get the viewsheet assemblies bind to this table.
    */
   private static VSAssembly[] getVSAssemblies(Viewsheet vs, String tname) {
      if(vs == null) {
         return new VSAssembly[]{};
      }

      Assembly[] arr = vs.getAssemblies();
      List<VSAssembly> list = new ArrayList<>();

      for(Assembly assembly : arr) {
         String bound = ((VSAssembly) assembly).getTableName();

         if(assembly instanceof ListInputVSAssembly) {
            bound = ((ListInputVSAssembly) assembly).getBoundTableName();
         }

         if(tname.equals(bound)) {
            list.add((VSAssembly) assembly);
         }
      }

      VSAssembly[] val = new VSAssembly[list.size()];
      list.toArray(val);
      return val;
   }

   /**
    * Get the predefined dimension/measure informations.
    */
   private static AggregateInfo[] getInfos(VSAssembly[] arr) {
      List<AggregateInfo> list = new ArrayList<>();

      for(VSAssembly assembly : arr) {
         AggregateInfo ainfo = getAggregateInfo(assembly);

         if(ainfo != null) {
            list.add(ainfo);
         }
      }

      return list.toArray(new AggregateInfo[0]);
   }

   /**
    * Get the predefined dimension/measure information for the assembly.
    */
   public static AggregateInfo getAggregateInfo(VSAssembly assembly) {
      DataRef[] rrefs = null;

      if(assembly instanceof ChartVSAssembly) {
         ChartVSAssembly chart = (ChartVSAssembly) assembly;
         VSChartInfo cinfo = chart.getVSChartInfo();

         if(cinfo != null) {
            rrefs = cinfo.getRTFields();
         }
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         CrosstabVSAssembly crosstab = (CrosstabVSAssembly) assembly;
         VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();

         if(cinfo != null) {
            DataRef[] rows = cinfo.getRuntimeRowHeaders();
            DataRef[] cols = cinfo.getRuntimeColHeaders();
            DataRef[] aggs = cinfo.getRuntimeAggregates();
            int rcnt = rows == null ? 0 : rows.length;
            int ccnt = cols == null ? 0 : cols.length;
            int acnt = aggs == null ? 0 : aggs.length;
            rrefs = new DataRef[rcnt + ccnt + acnt];

            if(rcnt > 0) {
               System.arraycopy(rows, 0, rrefs, 0, rcnt);
            }

            if(ccnt > 0) {
               System.arraycopy(cols, 0, rrefs, rcnt, ccnt);
            }

            if(acnt > 0) {
               System.arraycopy(aggs, 0, rrefs, rcnt + ccnt, acnt);
            }
         }
      }

      if(rrefs != null) {
         AggregateInfo ainfo = createAggregateInfo(assembly, rrefs);

         if(ainfo != null) {
            return ainfo;
         }
      }

      if(assembly instanceof CubeVSAssembly) {
         return ((CubeVSAssembly) assembly).getAggregateInfo();
      }
      else if(assembly.getInfo() instanceof SelectionBaseVSAssemblyInfo) {
         SelectionBaseVSAssemblyInfo info = (SelectionBaseVSAssemblyInfo) assembly.getInfo();
         String measure = info.getMeasure();

         if(measure != null && measure.length() > 0) {
            AggregateInfo ainfo = new AggregateInfo();
            ColumnRef ref = new ColumnRef(new AttributeRef(measure));
            ainfo.addAggregate(new AggregateRef(ref, AggregateFormula.getFormula(info.getFormula())));
            return ainfo;
         }
      }
      else if(assembly.getInfo() instanceof OutputVSAssemblyInfo) {
         OutputVSAssemblyInfo outinfo = (OutputVSAssemblyInfo) assembly.getInfo();
         ScalarBindingInfo scalar = outinfo.getScalarBindingInfo();

         if(scalar != null) {
            DataRef col = scalar.getColumn();
            DataRef col2 = scalar.getSecondaryColumn();
            AggregateFormula form = scalar.getAggregateFormula();

            if(form != null && col != null) {
               AggregateInfo ainfo = new AggregateInfo();
               AggregateRef aref = new AggregateRef(col, col2, form);
               aref.setN(scalar.getN());
               ainfo.addAggregate(aref);
               return ainfo;
            }
         }
      }

      return null;
   }

   private static AggregateInfo createAggregateInfo(VSAssembly assembly, DataRef[] refs) {
      AggregateInfo ainfo = new AggregateInfo();
      Viewsheet vs = assembly.getViewsheet();

      if(!(assembly instanceof DataVSAssembly)) {
         return ainfo;
      }

      SourceInfo sinfo = ((DataVSAssembly) assembly).getSourceInfo();
      String stable = sinfo == null || sinfo.isEmpty() ? null : sinfo.getSource();

      for(DataRef ref : refs) {
         if(ref instanceof VSDimensionRef) {
            GroupRef group = ((VSDimensionRef) ref).createGroupRef(null);

            if(!ainfo.containsGroup(group)) {
               ainfo.addGroup(group);
            }
         }
         else if(ref instanceof VSAggregateRef) {
            VSAggregateRef aggr = (VSAggregateRef) ref;
            AggregateRef aref;

            if(aggr.isApplyAlias()) {
               AggregateFormula formula = aggr.getFormula();
               DataRef col = aggr.getDataRef();
               ColumnRef col2 = (ColumnRef) aggr.getSecondaryColumn();
               aref = new AggregateRef(col, col2, formula);
            }
            else {
               aref = aggr.createAggregateRef(null);
            }

            boolean aggc = VSUtil.isAggregateCalc(aref.getDataRef());

            if(aggc && aggr.getDataRef() instanceof CalculateRef &&
               vs != null && stable != null)
            {
               CalculateRef cref = (CalculateRef) aggr.getDataRef().clone();
               ExpressionRef eref = (ExpressionRef) cref.getDataRef();
               String expression = eref.getExpression();
               List<String> matchNames = new ArrayList<>();
               List<AggregateRef> aggs = VSUtil.findAggregate(vs, stable, matchNames, expression);

               for(AggregateRef agg : aggs) {
                  aref = VSUtil.createAliasAgg(agg, true);

                  if(!ainfo.containsAggregate(aref)) {
                     ainfo.addAggregate(aref);
                  }
               }
            }
            else {
               ainfo.addAggregate(aref);
            }
         }
      }

      return ainfo.isEmpty() ? null : ainfo;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "MVDef-" + super.hashCode() + "<" + mvname + ">";
   }

   private String getLMTableStr() {
      String lmStr = null;

      if(lmtables != null && lmtables.length > 0) {
         lmStr = "";

         for(int i = 0; i < lmtables.length; i++) {
            lmStr += (i > 0 ? "," : "") + lmtables[i];
         }
      }

      return lmStr;
   }

   /**
    * Get length.
    */
   public int getLength() {
      String lmStr = getLMTableStr();

      int len = 8; // last update time
      String[] strs = new String[]{ mvname, vsId, tname, otname, sub + "",
                                    directLM + "", lmStr, shareable + "" };

      for(String str : strs) {
         len += (4 + (str == null ? 0 : str.length() * 2));
      }

      len += 4;
      List<MVColumn> columns = getColumns();

      for(MVColumn col : columns) {
         len += getStringLength(col.getClassName()) + col.getDataLength();
      }

      len += 4;
      List<MVColumn> rcolumns = getRemovedColumns();

      for(MVColumn col : rcolumns) {
         len += getStringLength(col.getClassName()) + col.getDataLength();
      }

      return len;
   }

   /**
    * Save to binary storage.
    */
   public void write(WritableByteChannel channel) throws IOException {
      fpos = ((SeekableByteChannel) channel).position();
      String lmStr = getLMTableStr();

      String[] strs = new String[]{ mvname, vsId, tname, otname, sub + "" };
      List<MVColumn> columns = getColumns();
      List<MVColumn> rcolumns = getRemovedColumns();

      int len = getLength();
      ByteBuffer buf = ByteBuffer.allocate(len + 4);
      buf.putInt(len);

      for(int i = 0; i < strs.length; i++) {
         writeString(buf, strs[i]);
      }

      buf.putInt(columns.size());

      for(MVColumn col : columns) {
         writeString(buf, col.getClassName());
         col.write(buf);
      }

      // read and write directLM in the end to avoid bc problem
      writeString(buf, directLM + "");
      writeString(buf, lmStr);

      buf.putInt(rcolumns.size());

      for(MVColumn col : rcolumns) {
         writeString(buf, col.getClassName());
         col.write(buf);
      }

      buf.putLong(lastUpdateTime);
      writeString(buf, shareable + "");

      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }
   }

   /**
    * Load from binary storage.
    */
   public void read(ReadableByteChannel channel) throws IOException {
      fpos = ((SeekableByteChannel) channel).position();
      ByteBuffer buf = ByteBuffer.allocate(4);
      channel.read(buf);
      XSwapUtil.flip(buf);
      int len = buf.getInt();
      buf = ByteBuffer.allocate(len);
      channel.read(buf);
      XSwapUtil.flip(buf);
      mvname = readString(buf);
      vsId = readString(buf);
      tname = readString(buf);
      otname = readString(buf);
      sub = "true".equals(readString(buf));
      len = buf.getInt();
      List<MVColumn> columns = new ArrayList<>();

      for(int i = 0; i < len; i++) {
         String cname = readString(buf);

         try {
            MVColumn col = MVColumn.create(cname);
            col.read(buf);
            columns.add(col);
         }
         catch(Exception ex) {
            LOG.error("Failed to read materialized view column: " + cname, ex);
         }
      }

      // read and write directLM in the end to avoid bc problem
      if(buf.remaining() > 0) {
         directLM = "true".equals(readString(buf));
      }

      if(buf.remaining() > 0) {
         String lmStr = readString(buf);
         this.lmtables = lmStr == null ? new String[0] : lmStr.split(",");
      }

      List<MVColumn> rcolumns = new ArrayList<>();

      if(buf.remaining() > 0) {
         len = buf.getInt();

         for(int i = 0; i < len; i++) {
            String cname = readString(buf);

            try {
               MVColumn col = MVColumn.create(cname);
               col.read(buf);
               rcolumns.add(col);
            }
            catch(Exception ex) {
               LOG.error("Failed to read removed materialized view column: " + cname, ex);
            }
         }
      }

      container = new MVContainer(columns, null);
      container.rcolumns = rcolumns;

      if(buf.remaining() > 0) {
         pre_v11_2 = false;
         lastUpdateTime = buf.getLong();
      }
      else {
         pre_v11_2 = true;
      }

      if(buf.remaining() > 0) {
         shareable = "true".equals(readString(buf));
      }
   }

   /**
    * Get the length of a string.
    */
   private int getStringLength(String str) {
      return 4 + (str == null ? 0 : str.length() * 2);
   }

   /**
    * Write down one string.
    */
   private void writeString(ByteBuffer buf, String str) {
      int ssize = str == null ? -1 : str.length();
      buf.putInt(ssize);

      for(int i = 0; i < ssize; i++) {
         assert str != null;
         buf.putChar(str.charAt(i));
      }
   }

   /**
    * Read in one string.
    */
   private String readString(ByteBuffer buf) {
      String res = null;
      int strlen = buf.getInt();

      if(strlen != -1) {
         char[] chars = new char[strlen];

         for(int j = 0; j < strlen; j++) {
            chars[j] = buf.getChar();
         }

         res = new String(chars);
      }

      return res;
   }

   /**
    * Get the updated timestamp.
    */
   public long lastModified() {
      XServerNode server = FSService.getServer();

      if(server == null) {
         return -1L;
      }

      XFileSystem sys = server.getFSystem();
      XFile file = sys.get(mvname);

      if(file == null) {
         return -1L;
      }

      return file.lastModified();
   }

   /**
    * Check if has data.
    */
   public boolean hasData() {
      XServerNode server = FSService.getServer();

      if(server == null) {
         return true;
      }

      XFileSystem sys = server.getFSystem();
      return sys.contains(mvname);
   }

   /**
    * Dispose this MVDef for it's no longer required.
    */
   public void dispose() {
      XServerNode server = FSService.getServer();

      if(server == null) {
         return;
      }

      removeWorksheet();
      XFileSystem sys = server.getFSystem();
      boolean contained = sys.contains(mvname);

      if(!contained) {
         return;
      }

      sys.remove(mvname);

      if(sys.get(mvname) != null) {
         LOG.warn("Failed to remove sub mv file: {}", mvname);
         return;
      }

      String file = MVStorage.getFile(mvname);

      try {
         MVStorage.getInstance().remove(file);
      }
      catch(Exception e) {
         if(LOG.isDebugEnabled() && !(e instanceof FileNotFoundException)) {
            LOG.debug("Failed to remove mv file: {}", mvname, e);
         }
         else {
            LOG.warn("Failed to remove mv file: {}", mvname);
         }
      }
   }

   /**
    * Compare this mv with another mv.
    */
   @Override
   public int compareTo(Object obj) {
      MVDef def = (MVDef) obj;
      return mvname.compareTo(def.mvname);
   }

   /**
    * Write the xml segment to print writer.
    *
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writeXML0(writer, true);
   }

   /**
    * Write the xml segment to printer writer, worksheet also write to the writer.
    */
   public void writeXML2(PrintWriter writer) {
      writeXML0(writer, false);
   }

   private void writeXML0(PrintWriter writer, boolean separateWS) {
      writer.println("<MVDef sub=\"" + sub + "\">");
      writeCDATA(writer, "name", mvname);
      writeCDATA(writer, "vsId", vsId);
      writeCDATA(writer, "wsId", wsId);
      writeCDATA(writer, "tname", tname);
      writeCDATA(writer, "otname", otname);
      writeCDATA(writer, "cycle", cycle);
      writeCDATA(writer, "breakcol", breakcol);
      writeCDATA(writer, "logicalmodel", directLM + "");
      writeCDATA(writer, "shareable", shareable + "");
      writeCDATA(writer, "incremental", incremental + "");
      writeCDATA(writer, "lastUpdateTime", lastUpdateTime + "");
      writeCDATA(writer, "success", success + "");
      String lmStr = getLMTableStr();

      if(lmStr != null) {
         writeCDATA(writer, "lmtables", lmStr);
      }

      if(version != null) {
         writeCDATA(writer, "version", version);
      }

      if(users != null) {
         for(Identity user : users) {
            writer.format("<user type=\"%d\">", user.getType());
            writer.format("<name><![CDATA[%s]]></name>", user.getName());

            if(user.getOrganizationID() != null) {
               writer.format("<organization><![CDATA[%s]]></organization>", user.getOrganizationID());
            }

            writer.print("</user>");
         }
      }

      MVContainer container = getContainer();
      List<MVColumn> columns = container == null ? Collections.emptyList() : container.columns;

      for(MVColumn col : columns) {
         col.writeXML(writer);
      }

      writer.println("<rcolumns>");

      for(MVColumn col : getRemovedColumns()) {
         col.writeXML(writer);
      }

      writer.println("</rcolumns>");

      Worksheet ws = container == null ? null : container.ws;

      if(ws != null) {
         if(separateWS) {
            try {
               writeWorksheet(ws);
            }
            catch(Exception ex) {
               LOG.error("Failed to write worksheet", ex);
            }
         }
         else {
            ws.writeXML(writer);
         }
      }

      if(data != null) {
         data.writeXML(writer);
      }

      writer.println("</MVDef>");
   }

   private void writeCDATA(PrintWriter writer, String tag, String value) {
      if(value != null) {
         writer.print("<" + tag + "><![CDATA[" + value + "]]></" + tag + ">");
      }
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      parseXML(tag, true);
   }

   /**
    * Method to parse an xml segment.
    */
   public void parseXML(Element tag, boolean fill) throws Exception {
      parseXML0(tag, fill, true);
   }

   /**
    * Method to parse an xml segment, the worksheet also stored in the xml.
    */
   public void parseXML2(Element tag) throws Exception {
      parseXML0(tag, true, false);
   }

   private void parseXML0(Element tag, boolean fill, boolean separateWS)
      throws Exception
   {
      sub = "true".equals(Tool.getAttribute(tag, "sub"));
      mvname = Tool.getChildValueByTagName(tag, "name");
      version = Tool.getChildValueByTagName(tag, "version");
      vsId = Tool.getChildValueByTagName(tag, "vsId");
      wsId = Tool.getChildValueByTagName(tag, "wsId");
      tname = Tool.getChildValueByTagName(tag, "tname");
      otname = Tool.getChildValueByTagName(tag, "otname");
      cycle = Tool.getChildValueByTagName(tag, "cycle");
      breakcol = Tool.getChildValueByTagName(tag, "breakcol");
      directLM = "true".equals(Tool.getChildValueByTagName(tag, "logicalmodel"));
      shareable = "true".equals(Tool.getChildValueByTagName(tag, "shareable"));
      incremental = "true".equals(Tool.getChildValueByTagName(tag, "incremental"));
      String lastUp = Tool.getChildValueByTagName(tag, "lastUpdateTime");

      if(lastUp != null) {
         lastUpdateTime = Long.parseLong(lastUp);
      }

      String successStr = Tool.getChildValueByTagName(tag, "success");
      success = successStr == null || "true".equals(successStr);

      String lmStr = Tool.getChildValueByTagName(tag, "lmtables");

      if(lmStr != null) {
         this.lmtables = lmStr.split(",");
      }

      Element node;
      NodeList list = Tool.getChildNodesByTagName(tag, "user");
      int ulen = list.getLength();
      users = ulen > 0 ? new Identity[ulen] : null;

      for(int i = 0; i < ulen; i++) {
         node = (Element) list.item(i);
         String uname = Tool.getChildValueByTagName(node, "name");
         int utype = Integer.parseInt(Tool.getAttribute(node, "type"));
         String uorg;

         if(uname == null) {
            uname = Tool.getValue(node);
            uorg = null;
         }
         else {
            uorg = Tool.getChildValueByTagName(node, "organization");
         }

         users[i] = new DefaultIdentity(uname, uorg, utype);
      }

      if(fill) {
         List<MVColumn> columns = new ArrayList<>();
         list = Tool.getChildNodesByTagName(tag, "mvcolumn");

         for(int i = 0; i < list.getLength(); i++) {
            node = (Element) list.item(i);
            MVColumn col = MVColumn.create(Tool.getAttribute(node, "class"));
            col.parseXML(node);
            columns.add(col);
         }

         node = Tool.getChildNodeByTagName(tag, "rcolumns");
         List<MVColumn> rcolumns = new ArrayList<>();

         if(node != null) {
            list = Tool.getChildNodesByTagName(node, "mvcolumn");

            for(int i = 0; i < list.getLength(); i++) {
               node = (Element) list.item(i);
               MVColumn col = MVColumn.create(Tool.getAttribute(node, "class"));
               col.parseXML(node);
               rcolumns.add(col);
            }
         }

         Worksheet ws = null;

         if(separateWS) {
            node = Tool.getChildNodeByTagName(tag, "compressedWS");

            // 11.1 (2011-6), changed to write ws to an external file,
            // compressedWS and worksheet can be removed in the future release
            if(node != null) {
               ws = parseCompressedWS(node);
            }
            else {
               node = Tool.getChildNodeByTagName(tag, "worksheet");

               if(node != null) {
                  ws = new WorksheetWrapper(null);
                  ws.parseXML(node);
               }
               else {
                  ws = parseWorksheet();
               }
            }
         }
         else {
            Element wnode = Tool.getChildNodeByTagName(tag, "worksheet");

            if(wnode != null) {
               ws = new WorksheetWrapper(null);
               ws.parseXML(wnode);
            }
         }

         if(ws != null) {
            this.container = new MVContainer(columns, ws);
            this.container.rcolumns = rcolumns;
            this.incremental = checkIncremental(ws);
            this.containerRef = new SoftReference<>(container);
         }
      }

      Element dnode = Tool.getChildNodeByTagName(tag, "MVMetaData");

      if(dnode != null) {
         data = new MVMetaData();
         data.parseXML(dnode);
      }
      else {
         data = new MVMetaData(wsId, otname, tname);

         if(isWSMV()) {
            data.register(wsId, otname);
            data.setWSMV(true);
         }
         else {
            data.register(vsId);
         }
      }
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      MVDef mvDef = (MVDef) o;
      return Objects.equals(mvname, mvDef.mvname) &&
         Objects.equals(cycle, mvDef.cycle);
   }

   @Override
   public int hashCode() {
      return Objects.hash(mvname, cycle);
   }

   /**
    * Check if the two materialized views are equal in content.
    */
   public boolean equalsContent(MVDef def) throws Exception {
      if(def == null) {
         return false;
      }

      // viewsheet and table should be equal
      if(!Tool.equals(vsId, def.vsId) || !Tool.equals(wsId, def.wsId) ||
         !Tool.equals(tname, def.tname) || !Tool.equals(otname, def.otname) || sub != def.sub)
      {
         return false;
      }

      // sql plan should be equal
      String plan1 = getVSPlan();
      String plan2 = def.getVSPlan();
      return plan1.equals(plan2);
   }

   /**
    * Get the execution plan.
    */
   private String getVSPlan() throws Exception {
      // fix bug1322515829739, cache query plan
      if(plan != null) {
         return plan;
      }

      plan = getPlan(false);
      return plan;
   }

   private String getWSPlan() {
      try {
         return getPlan(true);
      }
      catch(SecurityException ex) {
         throw ex;
      }
      catch(Throwable ex) {
         LOG.warn("Failed to create sql plan for table " + tname, ex);
      }

      return "";
   }

   private String getPlan(boolean shrink) throws Exception {
      XPrincipal user = users == null || users.length == 0 ? null : users[0].create();
      Worksheet ws = getWorksheet();
      assert ws != null;
      ws = new WorksheetWrapper(ws);
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);

      // cube table doesn't have a sql plan so return empty string to avoid spamming the log
      if(AssetUtil.isCubeTable(table)) {
         return "";
      }

      MVTransformer.removeMV(table);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.setVPMUser(user);
      box.setWSName(wsId);
      box.setVPMEnabled(!getMetaData().isBypassVPM());
      box.setAnalyzingMV(true);
      TableAssembly ntable = (TableAssembly) table.clone();

      if(shrink) {
         ColumnSelection sel = ntable.getColumnSelection();

         for(int i = sel.getAttributeCount() - 1; i >= 0; i--) {
            DataRef ref = sel.getAttribute(i);

            if(ref instanceof CalculateRef) {
               sel.removeAttribute(i);
            }
         }

         ntable.setColumnSelection(sel);
         TransformationDescriptor.clearPseudoFilter(
            ntable, AssetQuerySandbox.RUNTIME_MODE);
      }

      int mode = AssetQuerySandbox.RUNTIME_MODE;
      AssetQuery query = AssetQuery.createAssetQuery(ntable, mode, box, false,
                                                     -1L, true, true);
      XDataSource source = AssetUtil.getDataSource(ntable);

      // if VPM exists, don't allow creating MV from portal (it could be skipped
      // if not properly configured).
      if(REJECT_VPM.get() && source != null) {
         XDataModel model = XFactory.getRepository().getDataModel(source.getFullName());

         if(model != null && model.getVirtualPrivateModelNames().length > 0) {
            throw new SecurityException(Catalog.getCatalog().getString("vs.mv.portal.vpm"));
         }
      }

      if(getTabularTableAssembly(table) != null) {
         TabularTableAssembly tabularTableAssembly = getTabularTableAssembly(table);
         List<UserVariable> queryVariables = tabularTableAssembly.getTabularTableQueryVariables();

         if(queryVariables.size() > 0) {
            throw new SecurityException(Catalog.getCatalog().getString("vs.mv.tabular.query.variables"));
         }
      }

      source = ConnectionProcessor.getInstance().getDatasource(box.getUser(), source);
      QueryNode node = query.getQueryPlan();

      StringBuilder sb = new StringBuilder(source + "\n");
      node.writeContent(sb, isWSMV());
      return sb.toString();
   }

   private TabularTableAssembly getTabularTableAssembly(TableAssembly assembly) {
      while(assembly instanceof MirrorTableAssembly) {
         assembly = ((MirrorTableAssembly) assembly).getTableAssembly();
      }

      return assembly instanceof TabularTableAssembly ? (TabularTableAssembly) assembly : null;
   }

   /**
    * Check if the table can hit mv.
    */
   public boolean canHitMV(TableAssembly table) {
      // this function should not merge to isValidMV, otherwise, create mv
      // on demand may be cannot work
      if(!isValidMV(table)) {
         return false;
      }

      // check if column exist?
      try {
         table = (TableAssembly) table.clone();
         String[][] mvcols = MVAssetQuery.getAvailableMVColumns(this, table);
         String[] groups = mvcols[0];
         String[] aggregates = mvcols[1];

         if(groups != null) {
            for(String group : groups) {
               if(getColumn(group, true) == null &&
                  getColumn(group, false) == null)
               {
                  return false;
               }
            }
         }

         if(aggregates != null) {
            for(String aggregate : aggregates) {
               if(getColumn(aggregate, false) == null &&
                  getColumn(aggregate, true) == null)
               {
                  return false;
               }
            }
         }
      }
      catch(Exception ex) {
         return false;
      }

      return true;
   }

   /**
    * Check if this MV is valid for the table assembly.
    */
   public boolean isValidMV(TableAssembly table) {
      return isValidMV() && isValidMV(table.getAggregateInfo());
   }

   public boolean isValidMV(AggregateInfo ainfo) {
      XServerNode server = FSService.getServer();
      boolean desktop = server != null && server.getConfig().isDesktop();

      if(desktop || isWSMV()) {
         return true;
      }

      List<String> cols = findCountDistinct(ainfo);

      if(cols.size() == 0) {
         return true;
      }
      else if(cols.size() > 1) {
         return false;
      }

      return cols.get(0).equals(breakcol);
   }

   /**
    * Check if this MV is valid in the current version.
    */
   public boolean isValidMV() {
      // pre-11.1 MV is not compatible with 11.1
      return version != null;
   }

   /**
    * Check this mv can be shared/used for the given mv.
    */
   public boolean isSharedBy(MVDef def) {
      // I have data or you don't have data
      return (isShareable() && def.isShareable() || Objects.equals(vsId, def.vsId)) &&
         (hasData() || !def.hasData()) &&
         // need to have necessary columns to share (48293).
         columnRefsMatch(def) &&
         def.getEntry().getScope() == getEntry().getScope() && data.isSharedBy(def.data) &&
         // if column size is different and contains an embedded/unpivot table then don't share
         // (52564, 60857)
         (getColumnsSize(getColumns()) == getColumnsSize(def.getColumns()) ||
            def.isColumnsShareable(def.getBoundTable()));
   }

   private boolean columnRefsMatch(MVDef def) {
      return getColumnRefs(getColumns()).containsAll(getColumnRefs(def.getColumns()));
   }

   private Set<String> getColumnRefs(List<MVColumn> columns) {
      if(columns == null) {
         return Collections.emptySet();
      }

      return columns.stream()
         .map(MVColumn::getColumn)
         .map(c -> c.getName() + c.getDataType())
         .collect(Collectors.toSet());
   }

   /**
    * Embedded and unpivot tables can only be shared if the columns are identical.
    */
   private boolean isColumnsShareable(String tableName) {
      Worksheet ws = getWorksheet();

      if(ws != null) {
         Assembly assembly = ws.getAssembly(tableName);

         if(assembly instanceof EmbeddedTableAssembly ||
            assembly instanceof UnpivotTableAssembly)
         {
            return false;
         }
         else if(assembly instanceof ComposedTableAssembly) {
            for(TableAssembly table : ((ComposedTableAssembly) assembly).getTableAssemblies(false)) {
               if(!isColumnsShareable(table.getName())) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   private int getColumnsSize(List<MVColumn> columns) {
      return columns == null ? 0 : columns.size();
   }

   /**
    * Share mv.
    */
   public void shareMV(MVDef def) {
      data.register(def.data);
      List<Identity> all = new ArrayList<>();
      Identity[] src = getUsers();

      if(src != null) {
         Collections.addAll(all, src);
      }

      Identity[] src2 = def.getUsers();

      if(src2 != null) {
         for(Identity id : src2) {
            if(!all.contains(id)) {
               all.add(id);
            }
         }
      }

      if(all.size() > 0) {
         Identity[] arr = new Identity[all.size()];
         all.toArray(arr);
         setUsers(arr);
         sortUsers();
      }

      setChanged(true);
   }

   /**
    * Find the count distinct columns.
    */
   private List<String> findCountDistinct(AggregateInfo ainfo) {
      List<String> cols = new ArrayList<>();

      if(ainfo != null) {
         for(AggregateRef aref : ainfo.getAggregates()) {
            if(aref.getFormula() == AggregateFormula.COUNT_DISTINCT) {
               DataRef ref = DataRefWrapper.getBaseDataRef(aref);

               if(ref instanceof AliasDataRef) {
                  ref = ((AliasDataRef) ref).getDataRef();
               }

               cols.add(ref.getAttribute());
            }
         }
      }

      return cols;
   }

   /**
    * Make a copy of this MVDef.
    */
   @Override
   public Object clone() {
      try {
         MVDef mvdef = (MVDef) super.clone();

         if(mvdef.container != null) {
            mvdef.container = mvdef.container.clone();
         }
         return mvdef;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone materialized view definition", ex);
      }

      return null;
   }

   public MVDef deepClone() {
      try {
         MVDef mvdef = (MVDef) super.clone();

         if(mvdef.container != null) {
            mvdef.container = mvdef.container.clone();
         }

         if(data != null) {
            mvdef.data = data.deepClone();
         }

         if(users != null) {
            mvdef.users = (Identity[]) Tool.clone(users);
         }

         if(runtimeVariables != null) {
            mvdef.runtimeVariables = runtimeVariables.clone();
         }

         return mvdef;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone materialized view definition", ex);
      }

      return null;
   }

   /**
    * Get the worksheet file name.
    */
   private String getWorksheetPath() {
      return Tool.getUniqueName(mvname, 61) + ".ws";
   }

   /**
    * Write the worksheet xml as compressed cdata.
    */
   private void writeWorksheet(Worksheet ws) throws IOException {
      if(ws == null) {
         return;
      }

      try {
         Worksheet.setIsTEMP(true);
         String orgID = handleUpdatingOrgID();

         // Queries must use the correct orgID to get the datasource
         if(orgID != null) {
            for(Assembly assembly : ws.getAssemblies()) {
               if(assembly instanceof SQLBoundTableAssembly) {
                  ((SQLBoundTableAssemblyInfo) assembly.getInfo()).getQuery().setOrganizationId(orgID);
               }
            }
         }

         MVWorksheetStorage.getInstance().putWorksheet(getWorksheetPath(), ws, orgID);
      }
      finally {
         Worksheet.setIsTEMP(false);
      }
   }

   //in the case that worksheet is being saved on orgID change, worksheet needs the new orgID passed to save to storage
   //grab new org id from users, which was already updated
   private String handleUpdatingOrgID() {
      Identity user = this.users == null || this.users.length == 0 ? null : this.users[0];
      String uOrgID = user == null ? null : user.getOrganizationID();
      String curOrgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(uOrgID != null && !Tool.equals(curOrgID,uOrgID)) {
         return uOrgID;
      }

      return curOrgID;
   }

   /**
    * Remove the ws file.
    */
   public void removeWorksheet() {
      try {
         MVWorksheetStorage.getInstance().removeWorksheet(getWorksheetPath());

         if(LOG.isDebugEnabled()) {
            LOG.debug("MV worksheet deleted: " + getWorksheetPath());
         }
      }
      catch(FileNotFoundException ignore) {
         if(LOG.isDebugEnabled()) {
            LOG.debug("Failed to remove MV worksheet, it does not exist: " + getWorksheetPath());
         }
      }
      catch(IOException e) {
         LOG.warn("Failed to remove MV worksheet", e);
      }
   }

   /**
    * Parse the ws file.
    */
   private Worksheet parseWorksheet() throws Exception {
      return MVWorksheetStorage.getInstance().getWorksheet(getWorksheetPath(), handleUpdatingOrgID());
   }

   /**
    * Parse the compressed ws node.
    */
   private Worksheet parseCompressedWS(Element elem) throws Exception {
      byte[] arr = Tool.getValue(elem).getBytes();
      arr = Base64.decodeBase64(arr);

      ByteArrayInputStream buf = new ByteArrayInputStream(arr);
      Document doc = Tool.parseXML(new InflaterInputStream(buf));
      Element root = doc.getDocumentElement();
      Worksheet ws = new WorksheetWrapper(null);
      ws.parseXML(root);
      return ws;
   }

   private void createMVData(Worksheet ws, Viewsheet vs, boolean bypass) {
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);
      data = new MVMetaData(wsId == null ? vs.getBaseEntry().toIdentifier() :
                            wsId, otname, tname);
      data.setBypassVPM(bypass);

      if(isWSMV()) {
         data.register(wsId, otname);
         data.setWSMV(true);
         data.setWSMVPlan(getWSMVPlan(table));
      }
      else {
         data.register(vsId);
      }

      ColumnSelection cols = table.getColumnSelection(true);

      if(cols != null) {
         data.setColumns(cols);
      }

      if(breakcol != null) {
         data.setBreakColumn(breakcol);
      }

      String plan = getWSPlan();
      data.setPlan(plan);
   }

   /**
    * Creates a string with the source and condition information for the specified table
    * which is then used to determine whether two WS MVs contain the same data
    */
   private String getWSMVPlan(TableAssembly tableAssembly) {
      StringBuilder stringBuilder = new StringBuilder();

      if(tableAssembly instanceof BoundTableAssembly) {
         stringBuilder.append(((BoundTableAssembly) tableAssembly).getSourceInfo());
      }

      if(tableAssembly.getPreConditionList() != null) {
         stringBuilder.append("PRE:" + tableAssembly.getPreConditionList());
      }

      if(tableAssembly.getPostConditionList() != null) {
         stringBuilder.append("POST:" + tableAssembly.getPostConditionList());
      }

      if(tableAssembly.getRankingConditionList() != null) {
         stringBuilder.append("RANKING:" + tableAssembly.getRankingConditionList());
      }

      if(tableAssembly.getMVUpdatePreConditionList() != null) {
         stringBuilder.append("MV_UPDATE_PRE:" + tableAssembly.getMVUpdatePreConditionList());
      }

      if(tableAssembly.getMVUpdatePostConditionList() != null) {
         stringBuilder.append("MV_UPDATE_POST:" + tableAssembly.getMVUpdatePostConditionList());
      }

      if(tableAssembly.getMVDeletePreConditionList() != null) {
         stringBuilder.append("MV_DELETE_PRE:" + tableAssembly.getMVDeletePreConditionList());
      }

      if(tableAssembly.getMVDeletePostConditionList() != null) {
         stringBuilder.append("MV_DELETE_POST:" + tableAssembly.getMVDeletePostConditionList());
      }

      AggregateInfo ainfo = tableAssembly.getAggregateInfo();

      if(ainfo != null && !ainfo.isEmpty()) {
         List<String> groups = new ArrayList<>();
         List<String> aggs = new ArrayList<>();

         for(GroupRef ref : ainfo.getGroups()) {
            groups.add(ref.getName() + ref.getDateGroup() + ref.isTimeSeries());
         }

         for(AggregateRef ref : ainfo.getAggregates()) {
            aggs.add(VSUtil.getAggregateString(ref, false));
         }

         Collections.sort(groups);
         Collections.sort(aggs);

         stringBuilder.append("GROUPS:" + groups);
         stringBuilder.append("AGGS:" + aggs);
      }

      return stringBuilder.toString();
   }

   // Copy the container from the MVDef
   void copyContainer(MVDef def) {
      container = def.container;
      containerRef = def.containerRef;
      this.incremental = def.incremental;
   }

   /**
    * Check if this MV is for association processing.
    */
   public boolean isAssociationMV() {
      return getMVTable() != null && getMVTable().startsWith(TableMetaDataTransformer.PREFIX);
   }

   /**
    * Get the current runtime variables to be used during query execution
    */
   public VariableTable getRuntimeVariables() {
      return runtimeVariables;
   }

   /**
    * Store the variables to be used during query execution
    *
    * @param runtimeVariables The current runtime variables
    */
   public void setRuntimeVariables(VariableTable runtimeVariables) {
      this.runtimeVariables = runtimeVariables;
   }

   public boolean isWSMV() {
      return vsId == null;
   }

   /**
    * MV Container contains mv columns and worksheet.
    */
   static class MVContainer implements Serializable, Cloneable {
      MVContainer(List<MVColumn> columns, Worksheet ws) {
         this.columns = columns;
         this.ws = ws;
      }

      public MVContainer clone() {
         try {
            MVContainer container = (MVContainer) super.clone();

            if(container.columns != null) {
               container.columns = new ArrayList<>(container.columns);
            }

            if(container.rcolumns != null) {
               container.rcolumns = new ArrayList<>(container.rcolumns);
            }

            return container;
         }
         catch(CloneNotSupportedException e) {
            // not possible
            return this;
         }
      }

      Worksheet ws;
      List<MVColumn> columns;
      List<MVColumn> rcolumns = new ArrayList<>();
   }

   private static final Logger LOG = LoggerFactory.getLogger(MVDef.class);
   private static final Pattern nullcol = Pattern.compile("^Column \\[[0-9]+\\]$");
   private static AtomicInteger index = new AtomicInteger(1);
   // true if disable MV when VPM exists
   public static final ThreadLocal<Boolean> REJECT_VPM = ThreadLocal.withInitial(() -> false);

   transient MVContainer container = null;
   private transient SoftReference<MVContainer> containerRef = new SoftReference<>(null);
   private String plan = null; // query plan of the mvDef
   private String mvname = null; // name of this mv
   private String orgID = null; //orgid of this mv
   private String vsId = null; // viewsheet identifier
   private String wsId = null; // worksheet identifier
   private String tname = null; // mv table name
   private String otname = null; // bound table name
   private boolean sub = false; // sub mv
   private String breakcol = null; // break into blocks by this column
   private String cycle = null; // data cycle
   private boolean directLM = false;// check if the binding is Logical model
   private String[] lmtables = new String[0];
   private String version = FileVersions.MV;
   private long lastUpdateTime;
   private long fpos = 0L;
   private boolean pre_v11_2 = false;
   private boolean success = true;
   private boolean updated = false;
   private MVMetaData data;
   private boolean incremental = false;
   private boolean shareable = true;
   private Identity[] users = null; // user of this mv
   private transient boolean changed = false; // check if is changed
   private transient VariableTable runtimeVariables = new VariableTable();
   private transient long lastLoad; // last refresh/fill ts

   // a transient value for mv manager to check the mv is exist or not
   public transient boolean exist = false; // SharedMVUtil.shareAnalyzedMV
}
