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
package inetsoft.report.filter;

import inetsoft.report.TableLens;
import inetsoft.report.internal.CellTableLens;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.uql.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HighLight expert group class.
 * This class defines a HighLight Group.
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public class HighlightGroup implements Cloneable, XMLSerializable, Serializable {
   /**
    * The name of the default binding level.
    */
   public static final String DEFAULT_LEVEL = "{DEFAULT}";

   /**
    * Create an empty HighLightGroup.
    */
   public HighlightGroup() {
      super();
   }

   /**
    * Adds a highlight to the default level.
    *
    * @param name the name of the highlight.
    * @param value the highlight definition.
    */
   public synchronized void addHighlight(String name, Highlight value) {
      addHighlight(DEFAULT_LEVEL, name, value);
   }

   /**
    * Adds a highlight to the specified level.
    *
    * @param level the level to which the highlight applies.
    * @param name the name of the highlight.
    * @param value the highlight definition.
    *
    * @since 8.5
    */
   public synchronized void addHighlight(String level, String name, Highlight value) {
      OrderedMap<String, Highlight> group = highlightGroup.get(level);

      if(group == null) {
         group = new OrderedMap<>();
         highlightGroup.put(level, group);
      }

      group.put(name, value);
      updateHighlightGroup0();
   }

   /**
    * Gets the highlight with the specified name from the default level.
    *
    * @param name the name of the highlight.
    *
    * @return the highlight definition or <code>null</code> if no such highlight
    *         exists.
    */
   public Highlight getHighlight(String name) {
      return getHighlight(DEFAULT_LEVEL, name);
   }

   /**
    * Gets the highlight with the specified name.
    *
    * @param level the level to which the highlight applies.
    * @param name the name of the highlight.
    *
    * @return the highlight definition or <code>null</code> if no such highlight
    *         exists.
    *
    * @since 8.5
    */
   public Highlight getHighlight(String level, String name) {
      Highlight result = null;
      OrderedMap group = highlightGroup.get(level);

      if(group != null) {
         result = (Highlight) group.get(name);
      }

      return result;
   }

   /**
    * Gets the number of highlights applied to the default level.
    *
    * @return the number of highlights.
    */
   public int getHighlightCount() {
      return getHighlightCount(DEFAULT_LEVEL);
   }

   /**
    * Gets the number of highlights applied to the specified level.
    *
    * @param level the name of the level.
    *
    * @return the number of highlights.
    *
    * @since 8.5
    */
   public int getHighlightCount(String level) {
      int result = 0;
      OrderedMap group = highlightGroup.get(level);

      if(group != null) {
         result = group.size();
      }

      return result;
   }

   /**
    * Gets the number of highlights applied to all levels in this group.
    *
    * @return the number of highlights.
    *
    * @since 8.5
    */
   public int getAllLevelsHighlightCount() {
      int result = 0;
      String[] levels = getLevels();

      for(String level : levels) {
         result += getHighlightCount(level);
      }

      return result;
   }

   /**
    * Gets the number of levels to which highlights have been applied.
    *
    * @return the number of levels.
    *
    * @since 8.5
    */
   public int getLevelCount() {
      return highlightGroup.size();
   }

   /**
    * Gets the names of the levels to which highlights have been applied.
    *
    * @return the names of the levels.
    */
   public synchronized String[] getLevels() {
      String[] result = new String[highlightGroup.size()];
      Iterator iterator = highlightGroup.keySet().iterator();

      for(int i = 0; iterator.hasNext(); i++) {
         result[i] = (String) iterator.next();
      }

      return result;
   }

   /**
    * Determines if no highlights are applied to any level in this group.
    *
    * @return <code>true</code> if no highlights have been added;
    *         <code>false</code> otherwise.
    *
    * @since 8.5
    */
   public boolean isAllLevelsEmpty() {
      boolean result = true;
      String[] levels = getLevels();

      for(String level : levels) {
         if(!isEmpty(level)) {
            result = false;
            break;
         }
      }

      return result;
   }

   /**
    * Determines if no highlights are applied to the default level.
    *
    * @return <code>true</code> if no highlights have been added;
    *         <code>false</code> otherwise.
    */
   public boolean isEmpty() {
      return isEmpty(DEFAULT_LEVEL);
   }

   /**
    * Determines if no highlights are applied to the specified level.
    *
    * @param level the name of the level.
    *
    * @return <code>true</code> if no highlights have been added;
    *         <code>false</code> otherwise.
    *
    * @since 8.5
    */
   public boolean isEmpty(String level) {
      boolean result = true;
      OrderedMap group = highlightGroup.get(level);

      if(group != null) {
         result = group.isEmpty();
      }

      return result;
   }

   /**
    * Removes the highlights.
    * @param level the name of the level.
    */
   public synchronized void removeHighlights(String level) {
      highlightGroup.remove(level);
      conditionMap.remove(level);
   }

   /**
    * Removes the named highlight from the default level.
    *
    * @param name the name of the highlight.
    */
   public synchronized void removeHighlight(String name) {
      removeHighlight(DEFAULT_LEVEL, name);
   }

   /**
    * Removes the named highlight from the specified level.
    *
    * @param level the name of the level.
    * @param name the name of the highlight.
    *
    * @since 8.5
    */
   public synchronized void removeHighlight(String level, String name) {
      OrderedMap group = highlightGroup.get(level);

      if(group != null) {
         group.remove(name);
      }

      HashMap group1 = (HashMap) conditionMap.get(level);

      if(group1 != null) {
         group1.remove(name);
      }

      updateHighlightGroup0();
   }

   /**
    * Rename the highlight and keep the orders.
    * @param oname the old name of the highlight.
    * @param nname the new name of the highlight.
    * @param lt the highlight object.
    */
   public synchronized void renameHighlight(String oname, String nname,
      Highlight lt)
   {
      renameHighlight(DEFAULT_LEVEL, oname, DEFAULT_LEVEL, nname, lt);
   }

   /**
    * Rename the highlight and keep the orders.
    * @param olevel the old level.
    * @param oname the old name of the highlight.
    * @param nlevel the new level.
    * @param nname the new name of the highlight.
    * @param lt the highlight object.
    */
   public synchronized void renameHighlight(String olevel, String oname,
      String nlevel, String nname, Highlight lt)
   {
      if(olevel != null && olevel.equals(nlevel)) {
         OrderedMap<String, Highlight> group = highlightGroup.get(olevel);

         if(group == null) {
            return;
         }

         OrderedMap<String, Highlight> ngroup = new OrderedMap<>();

         for(Map.Entry<String, Highlight> entry : group.entrySet()) {
            if(entry.getKey().equals(oname)) {
               ngroup.put(nname, entry.getValue());
            }
            else {
               ngroup.put(entry.getKey(), entry.getValue());
            }
         }

         highlightGroup.put(olevel, ngroup);
      }
      else {
         removeHighlight(olevel, oname);
         addHighlight(nlevel, nname, lt);
      }
   }

   /**
    * Exchange the positions of two specified highlight.
    * @param idx1 the first index.
    */
   @SuppressWarnings("UnusedParameters")
   public synchronized void moveHighlight(String level, int idx1, int idx2,
                                          String name1, String name2)
   {
      OrderedMap group = highlightGroup.get(level);

      if(idx1 >= group.size() || idx2 >= group.size()) {
         int direction = idx1 - idx2;

         for(int i = 0; i < group.size(); i++) {
            if(group.getKey(i).equals(name1)) {
               idx1 = i;
               break;
            }
         }

         idx2 = idx1 - direction;
      }

      group.exchange(idx1, idx2);
   }

   /**
    * Gets the names of the highlights that are applied to the default level.
    *
    * @return the names of the highlights.
    */
   public String[] getNames() {
      return getNames(DEFAULT_LEVEL);
   }

   /**
    * Gets the names of the highlights that are applied to the specified level.
    *
    * @param level the name of the level.
    *
    * @return the names of the highlights.
    *
    * @since 8.5
    */
   public String[] getNames(String level) {
      if(level == null) {
         level = DEFAULT_LEVEL;
      }

      String[] result;
      OrderedMap group = highlightGroup.get(level);

      if(group != null) {
         List<String> keys = group.keyList();
         result = keys.toArray(new String[0]);
      }
      else {
         result = new String[0];
      }

      return result;
   }

   /**
    * Finds the highlight in the default level whose conditions match the
    * specified value.
    *
    * @param value the value to evaluate.
    *
    * @return the matching highlight or <code>null</code> if none match.
    */
   public Highlight findGroup(Object value) {
      return findGroup(DEFAULT_LEVEL, value);
   }

   /**
    * Finds the highlight in the specified level whose conditions match the
    * specified value.
    *
    * @param level the name of the level.
    * @param value the value to evaluate.
    *
    * @return the matching highlight or <code>null</code> if none match.
    * @since 8.5
    */
   public Highlight findGroup(String level, Object value) {
      Highlight result = null;
      OrderedMap<String, Highlight> group = highlightGroup0.get(level);

      if(group != null) {
         for(Map.Entry<String, Highlight> entry : group.entrySet()) {
            String name = entry.getKey();
            Highlight highlight = entry.getValue();
            ConditionList conditions = highlight.getConditionGroup();

            if(conditions.getSize() == 0) {
               continue;
            }

            ConditionGroup conditionGroup = new ConditionGroup(0, conditions, getQuerySandbox());

            if(conditionGroup.evaluate(value)) {
               fireHighlightApplied(level, name);
               result = mergeHighlight(result, highlight);
            }
         }
      }

      return result;
   }

   /**
    * Finds the highlight in the default level whose conditions match a value
    * in the specified row of a table.
    *
    * @param lens the TableLens whose values to evaluate.
    * @param row the table row index to evaluate.
    *
    * @return the matching highlight or <code>null</code> if none match.
    */
   public Highlight findGroup(TableLens lens, int row) {
      return findGroup(DEFAULT_LEVEL, lens, row);
   }

   /**
    * Finds the highlight in the specified level whose conditions match a value
    * in the specified row of a table.
    *
    * @param level the name of the level.
    * @param lens the TableLens whose values will be evaluated.
    * @param row the table row index to evaluate.
    *
    * @return the matching highlight or <code>null</code> if none match.
    *
    * @since 8.5
    */
   public Highlight findGroup(String level, TableLens lens, int row) {
      return findGroup(level, lens, row, -1);
   }

   /**
    * Finds the highlight in the default level whose conditions match a value
    * in the specified cell of a table.
    *
    * @param lens the TableLens whose values will be evaluated.
    * @param row the table row index of the value to evaluate.
    * @param col the table column index of the value to evaluate.
    *
    * @return the matching highlight or <code>null</code> if none match.
    */
   public Highlight findGroup(TableLens lens, int row, int col) {
      return findGroup(DEFAULT_LEVEL, lens, row, col);
   }

   /**
    * Finds the highlight in the specified level whose conditions match a value
    * in the specified cell of a table.
    *
    * @param level the name of the level.
    * @param lens the TableLens whose values will be evaluated.
    * @param row the table row index of the value to evaluate.
    * @param col the table column index of the value to evaluate.
    *
    * @return the matching highlight or <code>null</code> if none match.
    *
    * @since 8.5
    */
   public Highlight findGroup(String level, TableLens lens, int row, int col) {
      Highlight result = null;
      OrderedMap<String, Highlight> group = highlightGroup0.get(level);

      if(group != null) {
         for(Map.Entry<String, Highlight> entry : group.entrySet()) {
            String name = entry.getKey();
            Highlight highlight = group.get(name);
            ConditionGroup conditionGroup = null;
            Map<String, ConditionGroup> conditions = conditionMap.get(level);

            if(conditions != null && col != -1) {
               conditionGroup = conditions.get(name);
            }

            if(conditionGroup == null) {
               ConditionList conditionList = highlight.getConditionGroup();

               if(conditionList.getSize() == 0) {
                  continue;
               }

               // @by davidd, unified the findGroup methods to reduce
               // duplicate code blocks.
               if(lens instanceof RuntimeCalcTableLens) {
                  CalcConditionGroup calcConditionGroup =
                     new CalcConditionGroup(
                        (RuntimeCalcTableLens) lens, row, col, conditionList,
                        getQuerySandbox());
                  calcConditionGroup.setNotFoundResult(false);

                  if(calcConditionGroup.evaluate()) {
                     fireHighlightApplied(level, name);
                     result = mergeHighlight(result, highlight);
                  }
               }
               else if(lens instanceof CrossFilter) {
                  CrosstabConditionGroup crosstabConditionGroup =
                     new CrosstabConditionGroup(
                        (CrossFilter) lens, row, col, conditionList,
                        getQuerySandbox());
                  crosstabConditionGroup.setNotFoundResult(false);

                  if(crosstabConditionGroup.evaluate()) {
                     fireHighlightApplied(level, name);
                     result = mergeHighlight(result, highlight);
                  }
               }
               else if(lens instanceof CellTableLens) {
                  FreehandConditionGroup freehandConditionGroup =
                     new FreehandConditionGroup(
                        (CellTableLens) lens, row, col, conditionList,
                        getQuerySandbox());
                  freehandConditionGroup.setNotFoundResult(false);

                  if(freehandConditionGroup.evaluate()) {
                     fireHighlightApplied(level, name);
                     result = mergeHighlight(result, highlight);
                  }
               }
               else {
                  boolean haveField = false;

                  if(col == -1) {
                     conditionList.validate(false);

                     for(int i = 0; i < conditionList.getSize(); i++) {
                        if(haveField) {
                           break;
                        }

                        HierarchyItem item = conditionList.getItem(i);

                        if(item instanceof ConditionItem) {
                           ConditionItem condItem = (ConditionItem) item;

                           if(condItem.getXCondition() instanceof Condition) {
                              Condition cond = (Condition) condItem.getXCondition();

                              for(int j = 0; j < cond.getValueCount(); j++) {
                                 Object val = cond.getValue(j);

                                 if(val instanceof DataRef) {
                                    haveField = true;
                                    break;
                                 }
                              }
                           }
                        }
                     }
                  }

                  if(col != -1 || haveField) {
                     conditionList = conditionList.clone();
                  }

                  conditionGroup = new ConditionGroup(lens, conditionList, getQuerySandbox());
                  conditionGroup.setNotFoundResult(false);

                  if(col != -1) {
                     if(conditions == null) {
                        conditions = new HashMap<>();
                        conditionMap.put(level, conditions);
                     }

                     conditions.put(name, conditionGroup);
                  }
               }
            }

            // for a conditon group is cached, we should also evaluate it
            if(conditionGroup != null && conditionGroup.evaluate(lens, row, col)) {
               fireHighlightApplied(level, name);
               result = mergeHighlight(result, highlight);
            }
         }
      }

      return result;
   }

   /**
    * Merge the highlight settings. h1 takes precedence over h2.
    */
   private Highlight mergeHighlight(Highlight h1, Highlight h2) {
      if(h1 == null) {
         return h2;
      }

      TextHighlight hl = new TextHighlight();

      if(h1.getForeground() != null) {
         hl.setForeground(h1.getForeground());
      }
      else {
         hl.setForeground(h2.getForeground());
      }

      if(h1.getBackground() != null) {
         hl.setBackground(h1.getBackground());
      }
      else {
         hl.setBackground(h2.getBackground());
      }

      if(h1.getFont() != null) {
         hl.setFont(h1.getFont());
      }
      else {
         hl.setFont(h2.getFont());
      }

      return hl;
   }

   /**
    * Clears all cached condition data.
    */
   public void refresh() {
      conditionMap.clear();
   }

   /**
    * Validate the highlight group.
    */
   public synchronized void validate() {
      String[] levels = getLevels();

      for(String level : levels) {
         String[] names = getNames(level);

         for(String name : names) {
            Highlight highlight = getHighlight(level, name);

            if(highlight.isEmpty() && highlight.isConditionEmpty()) {
               removeHighlight(level, name);
            }
         }
      }
   }

   /**
    * Gets all variables defined on the highlights in this group.
    *
    * @return the defined variables.
    */
   public synchronized UserVariable[] getAllVariables() {
      ArrayList list = new ArrayList();
      String[] levels = getLevels();

      for(String level : levels) {
         String[] names = getNames(level);

         for(String name : names) {
            Highlight highlight = getHighlight(level, name);
            UserVariable[] vars = highlight.getAllVariables();
            Collections.addAll(list, vars);
         }
      }

      UserVariable[] result = new UserVariable[list.size()];
      list.toArray(result);

      return result;
   }

   /**
    * Replaces the variable defined on the highlights with those provided.
    *
    * @param vars the new variable values.
    */
   public synchronized void replaceVariables(VariableTable vars) {
      highlightGroup0 = new ConcurrentHashMap<>();
      String[] levels = getLevels();

      for(String level : levels) {
         OrderedMap<String, Highlight> group = Tool.deepCloneMap(highlightGroup.get(level));

         for(Highlight highlight : group.values()) {
            highlight.replaceVariables(vars);
         }

         highlightGroup0.put(level, group);
      }

      refresh();
   }

   /**
    * Update highlightGroup0's highlight at designtime
    */
   public synchronized void updateHighlightGroup0() {
      highlightGroup0 = new ConcurrentHashMap<>();
      String[] levels = getLevels();

      for(String level : levels) {
         OrderedMap<String, Highlight> group = Tool.deepCloneMap(highlightGroup.get(level));
         highlightGroup0.put(level, group);
      }
   }

   /**
    * Writes an XML representation of this object.
    *
    * @param writer the writer to which the XML will be written.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<HighlightGroup>");
      String[] levels = getLevels();

      for(String level : levels) {
         writer.println("<level>");
         writer.print("<name><![CDATA[");
         writer.print(level);
         writer.println("]]></name>");

         String[] names = getNames(level);

         for(String name : names) {
            getHighlight(level, name).writeXML(writer);
         }

         writer.println("</level>");
      }

      writer.println("</HighlightGroup>");
   }

   /**
    * Loads the properties of this object from an XML representation.
    *
    * @param tag the XML representation of this object.
    *
    * @throws Exception if an error occurs during parsing.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      NodeList list = Tool.getChildNodesByTagName(tag, "level");

      if(list == null || list.getLength() == 0) {
         parseHighlightsXML(DEFAULT_LEVEL, tag);
      }
      else {
         for(int i = 0; i < list.getLength(); i++) {
            Element levelElement = (Element) list.item(i);
            String level = Tool.getChildValueByTagName(levelElement, "name");
            parseHighlightsXML(level, levelElement);
         }
      }
   }

   /**
    * Loads the child highlight objects from an XML representation.
    *
    * @param level the level to which the highlights are to be added.
    * @param tag the XML node that contains the XML representation of the
    *            highlight objects.
    *
    * @throws Exception if an error occurs during parsing.
    */
   private void parseHighlightsXML(String level, Element tag) throws Exception {
      NodeList list = Tool.getChildNodesByTagName(tag, "HighlightAttr");

      for(int i = 0; i < list.getLength(); i++) {
         Element element = (Element) list.item(i);

         String type = Tool.getAttribute(element, "type");
         Highlight highlight;

         if(type.equalsIgnoreCase(Highlight.TABLE)) {
            highlight = new ColumnHighlight();
         }
         else {
            highlight = new TextHighlight();
         }

         highlight.parseXML(element);

         if(!highlight.isEmpty()) {
            addHighlight(level, highlight.getName(), highlight);
         }
      }
   }

   /**
    * Clone the highlight group.
    */
   @Override
   public HighlightGroup clone() {
      HighlightGroup group = new HighlightGroup();

      for(String level : highlightGroup.keySet()) {
         OrderedMap<String, Highlight> highlights = highlightGroup.get(level);

         for(String name : highlights.keySet()) {
            group.addHighlight(level, name, highlights.get(name).clone());
         }
      }

      group.conditionMap = Tool.deepCloneMap(conditionMap);
      group.highlightGroup0 = Tool.deepCloneMap(highlightGroup0);
      group.querySandbox = querySandbox;
      return group;
   }

   /**
    * Check if equals another highlight group.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof HighlightGroup)) {
         return false;
      }

      HighlightGroup hg2 = (HighlightGroup) obj;

      if(!highlightGroup.equals(hg2.highlightGroup)) {
         return false;
      }

      return true;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return super.toString() + '{' + highlightGroup + " : " + highlightGroup0 + '}';
   }

   /**
    * Getter of asset query sandbox.
    */
   public Object getQuerySandbox() {
      return querySandbox;
   }

   /**
    * Setter of asset query sandbox.
    */
   public void setQuerySandbox(Object box) {
      this.querySandbox = box;
   }

   /**
    * Adds a listener that is notified when a highlight is applied.
    *
    * @param l the listener to add.
    */
   public void addHighlightAppliedListener(HighlightAppliedListener l) {
      listeners.add(l);
   }

   /**
    * Removes a listener from the notification list.
    *
    * @param l the listener to remove.
    */
   public void removeHighlightAppliedListener(HighlightAppliedListener l) {
      listeners.remove(l);
   }

   public Set<HighlightAppliedListener> getHighlightAppliedListener() {
      return listeners;
   }

   public void setHighlightAppliedListener(Set<HighlightAppliedListener> listeners) {
      this.listeners = listeners;
   }

   /**
    * Notifies all registered listeners that a highlight has been applied.
    *
    * @param level the group level name.
    * @param name  the highlight name.
    */
   private void fireHighlightApplied(String level, String name) {
      HighlightAppliedEvent event = null;

      for(HighlightAppliedListener l : listeners) {
         if(event == null) {
            event = new HighlightAppliedEvent(this, level, name);
         }

         l.highlightApplied(event);
      }
   }

   /**
    * Event that signals that a highlight was applied.
    *
    * @author InetSoft Technology
    * @since  12.0
    */
   public static final class HighlightAppliedEvent extends EventObject {
      private final String level;
      private final String name;

      /**
       * Creates a new instance of <tt>HighlightAppliedEvent</tt>.
       *
       * @param source the source of the event.
       * @param level  the name of the grouping level.
       * @param name   the name of the highlight.
       */
      public HighlightAppliedEvent(Object source, String level, String name) {
         super(source);
         this.level = level;
         this.name = name;
      }

      /**
       * Gets the name of the grouping level.
       *
       * @return the level name.
       */
      public String getLevel() {
         return level;
      }

      /**
       * Gets the name of the highlight.
       *
       * @return the highlight name.
       */
      public String getName() {
         return name;
      }
   }

   /**
    * Listener that is notified when a highlight has been applied.
    *
    * @author InetSoft Technology
    * @since  12.0
    */
   public static interface HighlightAppliedListener {
      /**
       * Called when a highlight has been applied.
       *
       * @param event the event object.
       */
      void highlightApplied(HighlightAppliedEvent event);
   }

   private final Map<String, OrderedMap<String, Highlight>> highlightGroup = new ConcurrentHashMap<>();
   private HashMap<String, Map<String, ConditionGroup>> conditionMap = new HashMap<>();
   private Map<String, OrderedMap<String, Highlight>> highlightGroup0 = highlightGroup;
   private Object querySandbox = null;
   private Set<HighlightAppliedListener> listeners = new LinkedHashSet<>();
}
