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
package inetsoft.uql.erm;

import inetsoft.sree.SreeEnv;

import java.util.*;

/**
 * Helper class for checking model trap.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ModelTrapHelper {
   /**
    * Constructor.
    */
   public ModelTrapHelper(String[] tables, XPartition partition) {
      this(tables, partition, null);
   }

   /**
    * Constructor.
    */
   public ModelTrapHelper(XRelationship[] relations) {
      this(null, null, relations);
   }

   /**
    * Constructor.
    */
   public ModelTrapHelper(String[] tables, XPartition partition,
                          XRelationship[] relations) {
      if(relations == null) {
         HashSet<String> originating = new HashSet<>();
         HashSet<String> others = new HashSet<>();

         for(int i = 0; i < tables.length; i++) {
            HashSet<String> set = i == 0 ? originating : others;
            set.add(tables[i]);
         }

         relations = partition.findRelationships(originating, others, true);
      }

      this.tables = tables;
      this.relations = relations;
      this.partition = partition;
      map = new HashMap<>();

      for(XRelationship relation : relations) {
         String dtable = getDependentTable(relation);
         String itable = getIndependentTable(relation);
         addRelation(dtable, relation);
         addRelation(itable, relation);
      }

      debug = "true".equals(SreeEnv.getProperty("modeltrap.helper.debug"));
   }

   /**
    * Get dependent table name.
    */
   private String getDependentTable(XRelationship relation) {
      return getTableName(relation, true);
   }

   /**
    * Get independent table name.
    */
   private String getIndependentTable(XRelationship relation) {
      return getTableName(relation, false);
   }

   /**
    * Get table name, if alias exist use alias, otherwise use table name.
    */
   private String getTableName(XRelationship relation, boolean dependent) {
      String dtable = relation.getDependentTable();
      String itable = relation.getIndependentTable();
      String table = dependent ? dtable : itable;
      String otable = dependent ? itable : dtable; // other
      AutoAlias alias = partition.getAutoAlias(table);

      for(int i = 0; alias != null && i < alias.getIncomingJoinCount(); i++) {
         AutoAlias.IncomingJoin join = alias.getIncomingJoin(i);

         if(join.getSourceTable().equals(otable)) {
            return join.getAlias();
         }
      }

      return table;
   }

   /**
    * Add relation.
    */
   private void addRelation(String table, XRelationship relation) {
      ArrayList<XRelationship> relations = map.get(table);

      if(relations == null) {
         relations = new ArrayList<>();
         map.put(table, relations);
      }

      relations.add(relation);
   }

   /**
    * Get tables.
    */
   public String[] getTables() {
      return tables;
   }

   /**
    * Return true if chasm trap exists.
    */
   public boolean isChasmTrap() {
      int level = 0;
      debug("#####################check chasm trap", level);

      for(XRelationship relation : relations) {
         level = 1;
         ArrayList<XRelationship> processed = new ArrayList<>();
         processed.add(relation);
         String[] tables = getTables(relation, XRelationship.ONE);
         debug("check relation:" + relation, level);

         // Tn-n
         if(tables.length == 0) {
            debug("find chasm trap[tn-n]:" + relation, level);
            trapRelations.add(getDependentTable(relation));
            trapRelations.add(getIndependentTable(relation));
            return true;
         }
         // Tn-1
         else if(tables.length == 1) {
            debug("find [tn-1]:" + relation, level);

            if(isChasmTrap(processed, tables[0], level)) {
               trapRelations.add(getDependentTable(relation));
               trapRelations.add(getIndependentTable(relation));
               debug("find chasm trap[tn- 1 - n]:" + relation, level);
               return true;
            }
         }
      }

      return false;
   }

   private boolean isChasmTrap(ArrayList<XRelationship> processed,
                               String table, int level) {
      level++;
      ArrayList<XRelationship> relations = map.get(table);

      for(XRelationship relation : relations) {
         debug("before check relation:" + relation, level);

         if(processed.contains(relation)) {
            continue;
         }

         debug("check relation:" + relation, level);
         processed.add(relation);

         String[] tables = getTables(relation, XRelationship.ONE);

         if(tables.length == 0 || tables.length == 1 && tables[0].equals(table))
         {
            trapRelations.add(getDependentTable(relation));
            trapRelations.add(getIndependentTable(relation));
            debug("find[t1-n, tn-n]:" + relation, level);
            return true;
         }

         String anotherTable = getDependentTable(relation).equals(table) ?
            getIndependentTable(relation) : getDependentTable(relation);

         if(isChasmTrap(processed, anotherTable, level)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Return true if the specified table is in a fan trap.
    */
   public boolean isFanTrap(String table, String[] tables) {
      int level = 0;
      debug("#####################check fan trap:" + table, level);
      ArrayList<String> processed = new ArrayList<>();

      return isFanTrap(processed, table, tables, level);
   }

   /**
    * Return true if the specified table is in a fan trap.
    */
   private boolean isFanTrap(ArrayList<String> processed, String table,
                             String[] tables, int level) {
      level++;
      debug("before check fan trap:" + table, level);

      if(processed.contains(table)) {
         return false;
      }

      debug("check fan trap:" + table, level);
      processed.add(table);
      ArrayList<XRelationship> relations = map.get(table);

      for(int i = 0; relations != null && i < relations.size(); i++) {
         XRelationship relation = relations.get(i);
         debug("check relation:" + relation, level);
         String[] rtables = getTables(relation, XRelationship.MANY);

         // formula T1 - n
         if(tables.length == 2 && rtables.length == 1 &&
            !rtables[0].equals(table))
         {
            return false;
         }

         // T1 - n, Tn-n
         if(rtables.length == 1 && !rtables[0].equals(table) ||
            rtables.length == 2)
         {
            trapRelations.add(getDependentTable(relation));
            trapRelations.add(getIndependentTable(relation));
            debug("find fan trap[t1-n, tn-n]:" + table, level);
            return true;
         }

         // T1-1
         if(rtables.length == 0) {
            String anotherTable = getDependentTable(relation).equals(table) ?
               getIndependentTable(relation) : getDependentTable(relation);

            if(isFanTrap(processed, anotherTable, tables, level)) {
               trapRelations.add(getDependentTable(relation));
               trapRelations.add(getIndependentTable(relation));
               debug("find fan trap[t1-1]:" + table, level);
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Get tables in the relation with the specified cardinality.
    */
   private String[] getTables(XRelationship relation, int cardinality) {
      ArrayList<String> tables = new ArrayList<>();

      if(relation.getDependentCardinality() == cardinality) {
         tables.add(getDependentTable(relation));
      }

      if(relation.getIndependentCardinality() == cardinality) {
         tables.add(getIndependentTable(relation));
      }

      String[] res = new String[tables.size()];
      return tables.toArray(res);
   }

   private void debug(String msg, int level) {
      if(debug) {
         for(int i = 0; i < level; i++) {
            System.out.print("   ");
         }

         System.out.println(msg);
      }
   }

   public String getTrapTables() {
      Iterator it = trapRelations.iterator();
      StringBuilder sb = new StringBuilder();

      while(it.hasNext()) {
         sb.append(sb.length() > 0 ? ", "  + it.next() : it.next());
      }

      return sb.toString();
   }

   private XPartition partition;
   private XRelationship[] relations;
   private HashMap<String, ArrayList<XRelationship>> map;
   private boolean debug = false;
   private String[] tables;
   private HashSet<String> trapRelations = new HashSet<>();
}
