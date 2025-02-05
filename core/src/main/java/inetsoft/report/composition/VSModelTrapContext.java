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
package inetsoft.report.composition;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.CompositeColumnHelper;
import inetsoft.uql.erm.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;

import java.security.Principal;
import java.util.*;

/**
 * Model trap context for viewsheet.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class VSModelTrapContext extends AbstractModelTrapContext {
   /**
    * Constructor.
    */
   public VSModelTrapContext(RuntimeViewsheet rvs) {
      this(rvs, false);
   }

   /**
    * Constructor.
    */
   public VSModelTrapContext(RuntimeViewsheet rvs, boolean initAgg) {
      this.rvs = rvs;
      this.vs = rvs.getViewsheet();
      this.initAgg = initAgg;

      init(vs == null ? null : vs.getBaseEntry(),
           rvs.getViewsheetSandbox() == null ?
           null : rvs.getViewsheetSandbox().getUser(),
           vs == null ? false : !vs.isDirectSource());
   }

   /**
    * Initialize.
    */
   protected void init(AssetEntry entry, Principal user, boolean checkbase) {
      if(vs == null || entry == null) {
         return;
      }

      if(entry.getType().id() == 101) {
         DataSourceRegistry dsr = DataSourceRegistry.getRegistry();
         XDataModel xdm = dsr.getDataModel(entry.getProperty("prefix"));

         if(xdm == null) {
            throw new MessageException(Catalog.getCatalog().getString(
            "common.dataModelNotFound") + ": " +
            entry.getProperty("prefix"));
         }

         lm = xdm.getLogicalModel(entry.getProperty("source"), user);
         init(user, xdm, checkbase);
      }
      else if(entry.getType() == AssetEntry.Type.WORKSHEET) {
         Worksheet ws = vs.getBaseWorksheet();
         TableAssembly table = getTableAssembly(ws);

         if(table != null) {
            init(table, user, true);
         }
      }
   }

   /**
    * Get the bound table assembly.
    */
   private TableAssembly getTableAssembly(Worksheet ws) {
   	while(ws instanceof WorksheetWrapper) {
   	   ws = ((WorksheetWrapper) ws).getWorksheet();
   	}

      Assembly[] arr = ws.getAssemblies();

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] instanceof BoundTableAssembly) {
            return (BoundTableAssembly) arr[i];
         }
      }

      return null;
   }

   /**
    * Return TrapInfo as the result after checking trap.
    */
   public TrapInfo checkTrap(VSAssemblyInfo oinfo, VSAssemblyInfo ninfo) {
      return checkTrap(oinfo, ninfo, null, null);
   }

   /**
    * Return TrapInfo as the result after checking trap.
    */
   private TrapInfo checkTrap(VSAssemblyInfo oinfo, VSAssemblyInfo info,
                              String tname, CalculateRef ncalc)
   {
      if(vs == null || info == null) {
         return new TrapInfo();
      }

      CalculateRef ocalc = null;

      try {
         if(tname != null && ncalc != null) {
            ocalc = vs.getCalcField(tname, ncalc.getName());
            vs.addCalcField(tname, ncalc);
         }

         VSModelContext context = new VSModelContext(rvs);
         HashSet<DataRef> all = new HashSet<>();
         HashSet<DataRef> aggs = new HashSet<>();
         addCalcAttributes(all);
         getAttributes(getTable(info), all, aggs, false);
         context.getAllAttributes(info, all, aggs);
         DataRef[] allAttributes = new DataRef[all.size()];
         all.toArray(allAttributes);
         tables = getTables(allAttributes);
         this.aggs = new DataRef[aggs.size()];
         aggs.toArray(this.aggs);

         if(isDebugMode()) {
            printFields("__new__all__", all);
            printFields("__new__aggs__", aggs);
         }

         if(initAgg) {
            initAggregateRefs(info);
         }

         if(tname != null) {
            if(ocalc != null) {
               vs.addCalcField(tname, ocalc);
            }
            else if(ncalc != null) {
               vs.removeCalcField(tname, ncalc.getName());
            }
         }

         all = new HashSet<>();
         aggs = new HashSet<>();
         addCalcAttributes(all);
         getAttributes(getTable(oinfo), all, aggs, false);
         context.getAllAttributes(oinfo, all, aggs);
         allAttributes = new DataRef[all.size()];
         all.toArray(allAttributes);
         otables = getTables(allAttributes);
         oaggs  = new DataRef[aggs.size()];
         aggs.toArray(oaggs);

         if(isDebugMode()) {
            printFields("__old__all__", all);
            printFields("__old__aggs__", aggs);
         }

         return super.checkTrap();
      }
      finally {
         if(tname != null) {
            if(ocalc != null) {
               vs.addCalcField(tname, ocalc);
            }
            else if(ncalc != null) {
               vs.removeCalcField(tname, ncalc.getName());
            }
         }
      }
   }

   public TrapInfo checkCalcTrap(String tname, CalculateRef ncalc) {
      if(vs == null) {
         return new TrapInfo();
      }

      CalculateRef ocalc = null;

      try {
         if(tname != null && ncalc != null) {
            ocalc = vs.getCalcField(tname, ncalc.getName());
            vs.addCalcField(tname, ncalc);
         }

         VSModelContext context = new VSModelContext(rvs);
         HashSet<DataRef> all = new HashSet<>();
         HashSet<DataRef> aggs = new HashSet<>();
         addCalcAttributes(all);
         context.getAllAttributes(null, all, aggs);
         DataRef[] allAttributes = new DataRef[all.size()];
         all.toArray(allAttributes);
         tables = getTables(allAttributes);
         this.aggs = new DataRef[aggs.size()];
         aggs.toArray(this.aggs);

         if(isDebugMode()) {
            printFields("__new__all__", all);
            printFields("__new__aggs__", aggs);
         }

         if(tname != null) {
            if(ocalc != null) {
               vs.addCalcField(tname, ocalc);
            }
            else if(ncalc != null) {
               vs.removeCalcField(tname, ncalc.getName());
            }
         }

         all = new HashSet<>();
         aggs = new HashSet<>();
         addCalcAttributes(all);
         context.getAllAttributes(null, all, aggs);
         allAttributes = new DataRef[all.size()];
         all.toArray(allAttributes);
         otables = getTables(allAttributes);
         oaggs = new DataRef[aggs.size()];
         aggs.toArray(oaggs);

         if(isDebugMode()) {
            printFields("__old__all__", all);
            printFields("__old__aggs__", aggs);
         }

         return super.checkTrap();
      }
      finally {
         if(tname != null) {
            if(ocalc != null) {
               vs.addCalcField(tname, ocalc);
            }
            else if(ncalc != null) {
               vs.removeCalcField(tname, ncalc.getName());
            }
         }
      }
   }

   private void addCalcAttributes(HashSet<DataRef> all) {
      if(vs == null) {
         return;
      }

      Collection<String> tables = vs.getCalcFieldSources();

      for(String table : tables) {
         TableAssembly base = vs.getBaseWorksheet().getVSTableAssembly(table);
         CompositeColumnHelper helper = new CompositeColumnHelper(base);
         CalculateRef[] calcs = vs.getCalcFields(table);

         if(calcs == null || calcs.length == 0) {
            continue;
         }

         for(CalculateRef calc : calcs) {
            if(calc.getName().startsWith("Range@") || calc.getName().startsWith("Total@")) {
               continue;
            }

            DataRef[] refs = findAttributes(calc);

            for(DataRef ref : refs) {
               addCalcRefAttributes(helper, all, base, ref);
            }
         }
      }
   }

   private void addCalcRefAttributes(CompositeColumnHelper helper, HashSet<DataRef> all,
                                     TableAssembly table, DataRef ref)
   {
      if(vs == null) {
         return;
      }

      String attr = ref.getAttribute();
      int idx = attr.indexOf("([");
      String secondAttr = null;

      if(idx > 0 && attr.endsWith("])")) {
         attr = attr.substring(idx + 2, attr.length() - 2);
      }

      int idx0 = attr.indexOf("], [");

      if(idx0 > 0) {
         secondAttr = attr.substring(idx0 + 4);
         attr = attr.substring(0, idx0);
      }

      ColumnSelection selection = table.getColumnSelection(true).clone();

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         DataRef col = selection.getAttribute(i);

         if(Tool.equals(attr, col.getAttribute()) || Tool.equals(secondAttr, col.getAttribute())) {
            addAttributes(helper, all, col);
         }
      }
   }

   /**
    * Return TrapInfo as the result after checking trap.
    */
   public TrapInfo checkTrap(VSAssemblyInfo info,
                             String tname, CalculateRef ncalc) {
      if(tname == null || ncalc == null) {
         return new TrapInfo();
      }

      return checkTrap(info, info, tname, ncalc);
   }

   /**
    * Init all aggregate refs.
    */
   private void initAggregateRefs(VSAssemblyInfo info) {
      AggregateInfo ainfo = null;

      // for vs, only chart and crosstab can introduce aggregates
      if(info instanceof ChartVSAssemblyInfo) {
         ChartVSAssemblyInfo cinfo = (ChartVSAssemblyInfo) info;
         ainfo = cinfo.getVSChartInfo().getAggregateInfo();
      }
      else if(info instanceof CrosstabVSAssemblyInfo) {
         CrosstabVSAssemblyInfo cinfo = (CrosstabVSAssemblyInfo) info;
         VSCrosstabInfo crosstabInfo = cinfo.getVSCrosstabInfo();
         ainfo = crosstabInfo == null ? null : crosstabInfo.getAggregateInfo();
         ainfo = ainfo == null ? new AggregateInfo() : ainfo;
      }
      else if(info instanceof CalcTableVSAssemblyInfo) {
         CalcTableVSAssemblyInfo cinfo = (CalcTableVSAssemblyInfo) info;
         ainfo = cinfo.getAggregateInfo();
      }

      List<DataRef> arefs = new ArrayList<>();

      if(ainfo != null) {
         arefs.addAll(Arrays.asList(ainfo.getAggregates()));

         GroupRef[] grefs = ainfo.getGroups();
         HashSet<DataRef> set = new HashSet<>();
         TableAssembly table = getTable(info);
         CompositeColumnHelper chelper = new CompositeColumnHelper(table);

         for(int i = 0; i < grefs.length; i++) {
            addAttributes(chelper, set, grefs[i]);
         }

         DataRef[] arr = new DataRef[set.size()];
         gnames = new String[arr.length];
         set.toArray(arr);

         for(int i = 0; i < gnames.length; i++) {
            gnames[i] = getName(arr[i]);
         }
      }
      else {
         gnames = null;
      }

      for(DataRef attribute:getAllAttributes()) {
         if(isDefaultMeasure(attribute) &&
            (ainfo == null || !ainfo.containsGroup(attribute)))
         {
            arefs.add(attribute);
         }
      }

      anames = new String[arefs.size()];

      for(int i = 0; i < anames.length; i++) {
         anames[i] = getName(arefs.get(i));
      }
   }

   /**
    * Check if the data ref is measure.
    */
   @Override
   protected boolean isMeasure(DataRef ref) {
      if(gnames != null) {
         for(String name:gnames) {
            if(ref.getName().equals(name)) {
               return false;
            }
         }
      }

      return super.isMeasure(ref);
   }

   /**
    * Get name.
    */
   private String getName(DataRef ref) {
      String name = ref.getName();
      int idx = name.indexOf(":");

      if(idx >= 0) {
         name = name.substring(0, idx) + "." +
            name.substring(idx + 1, name.length());
      }

      return name;
   }

   /**
    * Get based worksheet table.
    */
   private TableAssembly getTable(VSAssemblyInfo info) {
      AssetEntry entry = vs == null ? null : vs.getBaseEntry();

      if(entry == null || entry.getType() != AssetEntry.Type.WORKSHEET) {
         return null;
      }

      String tableName = null;

      if(info instanceof DataVSAssemblyInfo) {
         DataVSAssemblyInfo dinfo = (DataVSAssemblyInfo) info;
         SourceInfo sinfo = dinfo.getSourceInfo();

         if(sinfo != null) {
            tableName = sinfo.getSource();
         }
      }
      else if(info instanceof OutputVSAssemblyInfo) {
         ScalarBindingInfo sbinfo =
            ((OutputVSAssemblyInfo) info).getScalarBindingInfo();

         if(sbinfo != null) {
            tableName = sbinfo.getTableName();
         }
      }

      TableAssembly table = null;

      if(tableName != null) {
         Worksheet ws = vs.getBaseWorksheet();
         table = (TableAssembly) ws.getAssembly(tableName);

         while(table instanceof MirrorTableAssembly) {
            table = ((MirrorTableAssembly) table).getTableAssembly();
         }
      }

      return table;
   }

   /**
    * Return the grayed fields.
    */
   @Override
   public DataRef[] getGrayedFields() {
      if(table == null) {
         return super.getGrayedFields();
      }

      if(vs == null || !isCheckTrap || info == null || info.showWarning()) {
         return new DataRef[0];
      }

      DataRef[] grayedFields = new DataRef[0];
      Worksheet ws = vs.getBaseWorksheet();

      for(Assembly assembly: ws.getAssemblies()) {
         if(!(assembly instanceof TableAssembly)) {
            continue;
         }

         TableAssembly table = (TableAssembly) assembly;
         HashSet<DataRef> all = new HashSet<>();
         HashSet<DataRef> aggs = new HashSet<>();
         getAttributes(table, all, aggs, false, false);
         addCalcFields(all, table.getName());
         DataRef[] allAttributes = new DataRef[all.size()];
         all.toArray(allAttributes);

         String[] tables = getTables(allAttributes);

         CompositeColumnHelper chelper = new CompositeColumnHelper(table);
         DataRef[] flds = getGrayedFields0(allAttributes, tables, chelper);
         DataRef[] gflds = new AttributeRef[flds.length];
         String tname = table.getName();

         tname = VSUtil.stripOuter(tname);

         for(int i = 0; i < gflds.length; i++) {
            String attribute = null;

            if(flds[i] instanceof ColumnRef) {
               attribute = ((ColumnRef) flds[i]).getAlias();
            }

            if(attribute == null) {
               attribute = flds[i].getAttribute();
            }

            gflds[i] = new AttributeRef(tname, attribute);
         }

         grayedFields = (DataRef[]) merge(grayedFields, gflds);
      }

      if(isDebugMode()) {
         printFields("__all__grayed__fields__", grayedFields);
      }

      return grayedFields;
   }

   private void addCalcFields(HashSet<DataRef> all, String tableName) {
      if(tableName.endsWith("_O")) {
         tableName = tableName.substring(0, tableName.length() - 2);
         addCalcFields0(all, tableName);
         return;
      }

      addCalcFields0(all, tableName);
   }

   private void addCalcFields0(HashSet<DataRef> all, String tableName) {
      CalculateRef[] calcs = vs.getCalcFields(tableName);

      if(calcs == null || calcs.length == 0) {
         return;
      }

      for(CalculateRef calc : calcs) {
         all.add(calc);
      }
   }

   /**
    * Get the name of all database tables referenced by this data attributes.
    */
   protected String[] getTables(DataRef[] refs) {
      List<DataRef> list = new ArrayList<>();

      for(int i = 0; refs != null && i < refs.length; i++) {
         DataRef[] attributes = getAttributes(refs[i]);

         if(attributes != null) {
            list.addAll(Arrays.asList(attributes));
         }
         else {
            list.add(refs[i]);
         }
      }

      return super.getTables(list.toArray(new DataRef[list.size()]));
   }

   /**
    * Return the attributes from calcfield.
    */
   private DataRef[] getAttributes(DataRef column) {
      if(vs == null || vs.getBaseEntry() == null || column == null) {
         return null;
      }

      String modelName = vs.getBaseEntry().getName();
      CalculateRef[] calcs = vs.getCalcFields(modelName);

      if(calcs == null || calcs.length == 0) {
         return null;
      }

      for(int i = 0; i < calcs.length; i++) {
         if(column.getName().equals(calcs[i].getName())) {
            return findAttributes(calcs[i]);
         }
      }

      return null;
   }

   /**
    * Find the attributes from the target calcfield.
    */
   private DataRef[] findAttributes(CalculateRef column) {
      if(column == null || !(column.getDataRef() instanceof ExpressionRef)) {
         return null;
      }

      ExpressionRef ref = (ExpressionRef) column.getDataRef();
      Enumeration eu = XUtil.findAttributes(ref.getExpression());
      List<DataRef> list = new ArrayList<>();

      while(eu.hasMoreElements()) {
         list.add((DataRef) eu.nextElement());
      }

      return list.toArray(new DataRef[list.size()]);
   }

   @Override
   protected DataRef[] getAllAttributes() {
      DataRef[] refs = super.getAllAttributes();
      CalculateRef[] calcs = null;

      if(vs.getBaseEntry() != null) {
         String modelName = vs.getBaseEntry().getName();
         calcs = vs.getCalcFields(modelName);
      }

      if(calcs == null || calcs.length == 0) {
         return refs;
      }

      return (DataRef[]) merge(refs, calcs);
   }


   private String[] gnames; // all group filed names
   private RuntimeViewsheet rvs;
   private Viewsheet vs;
}
