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
package inetsoft.uql.erm;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.CompositeColumnHelper;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.util.Catalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.security.Principal;
import java.util.*;

/**
 * A skeletal implementation of an model trap context.
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public abstract class AbstractModelTrapContext extends AbstractModelContext {
   /**
    * Initialize.
    */
   protected void init(TableAssembly table, Principal user, boolean checkbase) {
      this.table = table;
      TableAssembly baseTable = table;

      while(baseTable instanceof MirrorTableAssembly) {
         baseTable = ((MirrorTableAssembly) baseTable).getTableAssembly();
      }

      if(baseTable instanceof BoundTableAssembly) {
         init(((BoundTableAssembly) baseTable).getSourceInfo(), user,
               checkbase);
      }
   }

   /**
    * Initialize.
    */
   protected void init(XSourceInfo source, Principal user) {
      init(source, user, true);
   }

   /**
    * Initialize.
    */
   protected void init(XSourceInfo source, Principal user, boolean checkbase) {
      if(source != null && source.getType() == XSourceInfo.MODEL) {
         DataSourceRegistry dsr = DataSourceRegistry.getRegistry();
         XDataModel xdm = dsr.getDataModel(source.getPrefix());
         lm = xdm == null ? null : xdm.getLogicalModel(source.getSource(), user);
         init(user, xdm, checkbase);
      }

      this.source = source;
   }

   /**
    * Initialize.
    */
   protected void init(Principal user, XDataModel xdm, boolean checkbase) {
      if(lm == null) {
         return;
      }

      partition = xdm.getPartition(lm.getPartition(), user);

      if(partition == null) {
         // probably a partial import
         LOG.warn("Partition not found: " + lm.getPartition());
         return;
      }

      partition.applyAutoAliases();
      helper = new CompositeColumnHelper(table);

      if(lm != null && partition != null) {
         Enumeration relations = partition.getRelationships();

         while(relations.hasMoreElements()) {
            XRelationship relation = (XRelationship) relations.nextElement();

            if(relation.getIndependentCardinality() != 0 ||
               relation.getDependentCardinality() != 0)
            {
               isCheckTrap = true;
               break;
            }
         }

         if(isCheckTrap && table != null && checkbase) {
            if(!isWS || !(table instanceof BoundTableAssembly)) {
               isCheckTrap = !containTrap(table);
            }
         }
      }
   }

   /**
    * Check if contains trap.
    */
   protected boolean containTrap(TableAssembly table) {
      HashSet<DataRef> all = new HashSet<>();
      HashSet<DataRef> aggs = new HashSet<>();
      getAttributes(table, all, aggs, false);
      DataRef[] allAttributes = new DataRef[all.size()];
      all.toArray(allAttributes);
      String[] tables = getTables(allAttributes);
      DataRef[] refs = new DataRef[aggs.size()];
      aggs.toArray(refs);
      ModelTrapHelper helper = new ModelTrapHelper(tables, partition);
      TrapInfo info = checkTrap(helper, refs, true, true);

      if(info.showWarning()) {
         return true;
      }

      if(table instanceof MirrorTableAssembly) {
         return containTrap(((MirrorTableAssembly) table).getTableAssembly());
      }

      return false;
   }

   /**
    * Get all attributes and aggregate attributes in binding.
    */
   protected void getAttributes(TableAssembly table, HashSet<DataRef> refs,
                                HashSet<DataRef> aggs, boolean agg)
   {
      getAttributes(table, refs, aggs, agg, true);
   }

   /**
    * Get all attributes and aggregate attributes in binding.
    * @param table the table assembly.
    * @param refs all refs.
    * @param aggs all aggregate refs
    * @param agg if true get aggregates.
    * @param base if true get base model field.
    */
   protected void getAttributes(TableAssembly table, HashSet<DataRef> refs,
                                HashSet<DataRef> aggs, boolean agg,
                                boolean base)
   {
      if(table == null) {
         return;
      }

      CompositeColumnHelper helper = base ? new CompositeColumnHelper(table) :
         null;
      ConditionList cond = table.getPreConditionList().getConditionList();

      for(int i = 0; i < cond.getSize(); i++) {
         if(cond.isConditionItem(i)) {
            addAttributes(refs, cond.getAttribute(i));
            XCondition xcond = cond.getConditionItem(i).getXCondition();

            if(xcond instanceof AssetCondition) {
               DataRef[] dvalues = ((AssetCondition) xcond).getDataRefValues();

               for(DataRef ref : dvalues) {
                  addAttributes(helper, refs, ref);
               }
            }
         }
      }

      cond = table.getPostConditionList().getConditionList();

      for(int i = 0; i < cond.getSize(); i++) {
         if(cond.isConditionItem(i)) {
            addAttributes(helper, refs, cond.getAttribute(i));
         }
      }

      agg = agg || table.isAggregate();

      if(agg) {
         AggregateInfo ainfo = table.getAggregateInfo();

         for(int i = 0; i < ainfo.getGroupCount(); i++) {
            addAttributes(helper, refs, ainfo.getGroup(i));
         }

         for(int i = 0; i < ainfo.getAggregateCount(); i++) {
            AggregateRef aref = ainfo.getAggregate(i);
            addAttributes(helper, aggs, aref);

            if(aref.getSecondaryColumn() != null) {
               addAttributes(helper, aggs, aref.getSecondaryColumn());
            }
         }
      }

      if(!agg || table instanceof MirrorTableAssembly) {
         ColumnSelection cols = table.getColumnSelection();

         for(int i = 0; i < cols.getAttributeCount(); i++) {
            addAttributes(helper, refs, cols.getAttribute(i));
         }
      }

      refs.addAll(aggs);
      fixAggregates(refs, aggs);
   }

   /**
    * Add attributes.
    */
   protected void addAttributes(CompositeColumnHelper helper,
                                HashSet<DataRef> set,
                                DataRef ref)
   {
      if(ref == null) {
         return;
      }

      if(helper == null) {
         set.add(ref);
      }
      else {
         for(DataRef attribute : helper.getAttributes(ref)) {
            set.add(attribute);
         }
      }
   }

   /**
    * Return if we should check the trap.
    */
   public boolean isCheckTrap() {
      return isCheckTrap;
   }

   /**
    * Return if new trap is found or not.
    */
   protected TrapInfo checkTrap() {
      if(!isCheckTrap) {
         return new TrapInfo();
      }

      nhelper = new ModelTrapHelper(tables, partition);

      if(otables != null) {
         ModelTrapHelper ohelper = new ModelTrapHelper(otables, partition);
         oinfo = checkTrap(ohelper, oaggs, true, true);
      }

      boolean checkChasm = otables == null || otables.length != tables.length;
      info = checkTrap(nhelper, aggs, true, true);

      return oinfo != null && oinfo.showWarning() ? new TrapInfo() : info;
   }

   /**
    * Get trap condition.
    */
   public String getTrapCondition() {
      String tables = nhelper.getTrapTables();

      if(info.chasmTrap && !info.ochasmTrap) {
         return catalog.getString("Chasm Trap is on") + " " + tables;
      }
      else if(info.table != null || info.agg != null) {
         return catalog.getString("Fan Trap is on") + " " + tables;
      }

      return "";
   }

   /**
    * Return TrapInfo as the result after checking trap.
    */
   private TrapInfo checkTrap(ModelTrapHelper helper, DataRef[] aggs,
                              boolean checkChasm, boolean checkFan) {
      TrapInfo info = new TrapInfo();
      info.chasmTrap = checkChasm && helper.isChasmTrap();

      if(!info.chasmTrap && checkFan) {
         LOOP:
         for(DataRef ref : aggs) {
            String[] tables = getTables(ref);

            for(String table : tables) {
               if(helper.isFanTrap(table, tables)) {
                  info.agg = ref;
                  break LOOP;
               }
            }
         }
      }

      return info;
   }

   /**
    * Return the grayed fields.
    */
   public DataRef[] getGrayedFields() {
      if(!isCheckTrap || info == null || info.showWarning()) {
         return new DataRef[0];
      }

      DataRef[] grayedFields = getGrayedFields0(getAllAttributes(), tables,
         helper);

      if(isDebugMode()) {
         printFields("__grayed__fields__", grayedFields);
      }

      return grayedFields;
   }

   /**
    * Get grayed fields.
    */
   protected DataRef[] getGrayedFields0(DataRef[] allAttributes,
                                        String[] tables,
                                        CompositeColumnHelper chelper)
   {
      if(initAgg) {
         initAggregateRefs(allAttributes);
      }

      ArrayList<DataRef> res = new ArrayList<>();

      LOOP:
      for(DataRef ref : allAttributes) {
         DataRef[] nref = chelper.getAttributes(ref);
         String[] ntables = (String[]) merge(tables, getTables(nref));
         boolean checkChasm = tables.length != ntables.length;
         boolean checkFan = isMeasure(ref);
         DataRef[] naggs = checkFan ? nref : new DataRef[] {};
         ModelTrapHelper helper = new ModelTrapHelper(ntables, partition);

         if(checkChasm) {
            naggs = (DataRef[]) merge(aggs, naggs);
            checkFan = true;
         }

         TrapInfo info = checkTrap(helper, naggs, checkChasm, checkFan);

         if(info.showWarning()) {
            res.add(ref);
            continue LOOP;
         }
      }

      DataRef[] refs = new DataRef[res.size()];

      return res.toArray(refs);
   }

   /**
    * Print fields.
    */
   protected void printFields(String prefix, DataRef[] fields) {
      System.out.print(prefix);

      for(DataRef fld:fields) {
         if(fld != null) {
            System.out.print(fld.getName() + ",");
         }
      }

      System.out.println();
   }

   /**
    * Print fields.
    */
   protected void printFields(String prefix, AbstractCollection<DataRef> fields)
   {
      System.out.print(prefix);

      for(DataRef fld:fields) {
         if(fld != null) {
            System.out.print(fld.getName() + ",");
         }
      }

      System.out.println();
   }

   /**
    * Check if the data ref is measure.
    */
   protected boolean isMeasure(DataRef ref) {
      if(anames != null) {
         for(String name:anames) {
            if(ref.getName().equals(name)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Init all aggregate refs.
    */
   protected void initAggregateRefs() {
      initAggregateRefs(getAllAttributes());
   }

   /**
    * Init all aggregate refs.
    */
   protected void initAggregateRefs(DataRef[] refs) {
      List<DataRef> arefs = new ArrayList<>();

      for(DataRef attribute:refs) {
         if(isDefaultMeasure(attribute)) {
            arefs.add(attribute);
         }
      }

      anames = new String[arefs.size()];

      for(int i = 0; i < anames.length; i++) {
         anames[i] = arefs.get(i).getName();
      }
   }

   /**
    * Check if the data ref is measure.
    */
   protected boolean isDefaultMeasure(DataRef ref) {
      boolean measure = false;

      if(ref.getRefType() != DataRef.DIMENSION) {
         measure = ref.getRefType() == DataRef.MEASURE ||
            AssetUtil.isNumberType(ref.getDataType());

         if(!measure) {
            XAttribute xattribute = getAttribute(ref);

            if(xattribute instanceof ExpressionAttribute &&
               ((ExpressionAttribute) xattribute).isAggregateExpression())
            {
               measure = true;
            }
         }
      }

      return measure;
   }

   /**
    * Merge arrays.
    */
   protected Object[] merge(Object[] objs1, Object[] objs2) {
      HashSet<Object> set = new HashSet<>();
      set.addAll(Arrays.asList(objs1));
      set.addAll(Arrays.asList(objs2));
      Object[] res = (Object[]) Array.newInstance(
         objs1.getClass().getComponentType(), set.size());
      return set.toArray(res);
   }

   /**
    * Get all attributes in the binding tree.
    */
   protected DataRef[] getAllAttributes() {
      ArrayList<DataRef> list = new ArrayList<>();
      boolean model = table == null ||
         isWS && table instanceof BoundTableAssembly;

      if(!model) {
         ColumnSelection cols = table.getColumnSelection();

         for(int i = 0; i < cols.getAttributeCount(); i++) {
            ColumnRef ref = (ColumnRef) cols.getAttribute(i);

            if(isReport && helper != null) {
               AttributeRef attribute = new AttributeRef(helper.getName(ref));
               attribute.setRefType(ref.getRefType());
               ColumnRef nref = new ColumnRef(attribute);
               nref.setDataType(ref.getDataType());
               ref = nref;
            }

            list.add(ref);
         }
      }
      else {
         for(int i = 0 ; lm != null && i < lm.getEntityCount(); i++) {
            XEntity entity = lm.getEntityAt(i);

            for(int j = 0; j < entity.getAttributeCount(); j++) {
               XAttribute attribute = entity.getAttributeAt(j);
               AttributeRef ref = new AttributeRef(entity.getName(),
                                                   attribute.getName());
               ref.setRefType(attribute.getRefType());
               ColumnRef field = new ColumnRef(ref);
               field.setDataType(attribute.getDataType());
               list.add(field);
            }
         }

         if(table != null) {
            ColumnSelection cols = table.getColumnSelection();

            for(int i = 0; i < cols.getAttributeCount(); i++) {
               DataRef ref = cols.getAttribute(i);

               if(ref.isExpression()) {
                  list.add(ref);
               }
            }
         }
      }

      DataRef[] res = new DataRef[list.size()];
      return list.toArray(res);
   }

   /**
    * Is debug mode.
    */
   public boolean isDebugMode() {
      return "true".equals(SreeEnv.getProperty("modeltrap.context.debug"));
   }

   public class TrapInfo {
      public boolean showWarning() {
         return chasmTrap && !ochasmTrap || table != null || agg != null;
      }

      public String toString() {
         return chasmTrap && !ochasmTrap ? "Chasm Trap" : table != null ?
            "Fan trap is on " + table : agg != null ? agg +
            " is on fan trap" : "";
      }

      public boolean ochasmTrap;
      public boolean chasmTrap;
      public String table;
      public DataRef agg;
   }

   protected XPartition partition;
   protected String[] tables;
   protected String[] otables;
   protected DataRef[] aggs;
   protected DataRef[] oaggs;
   protected TrapInfo info;
   protected TrapInfo oinfo;
   protected ModelTrapHelper nhelper;
   protected TableAssembly table;
   protected boolean isReport;
   protected boolean isWS;
   protected boolean isCheckTrap;
   protected XSourceInfo source;
   protected String[] anames; // all aggreate ref names
   protected boolean initAgg;
   private boolean debug = false;
   private Catalog catalog = Catalog.getCatalog();

   private static final Logger LOG = LoggerFactory.getLogger(AbstractModelTrapContext.class);
}
