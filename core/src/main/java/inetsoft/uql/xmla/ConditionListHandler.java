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
package inetsoft.uql.xmla;

import inetsoft.uql.*;
import inetsoft.util.Queue;
import inetsoft.util.Tool;

import java.util.*;

/**
 * ConditionListHandler translates a condition from a data model into
 * a filter node.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class ConditionListHandler {
   /**
    * Generates an <code>XFilterNode</code> object that can be used by
    * OLAP execution.
    * @param conditions conditionlist object.
    * @return an XFilterNode object.
    */
   public static XNode createFilterNode(ConditionList conditions) {
      if(conditions == null) {
         return null;
      }

      Queue queue = new Queue();

      for(int i = 0; i < conditions.getSize(); i++) {
         HierarchyItem item = (HierarchyItem) conditions.getItem(i);

         if(item instanceof ConditionItem) {
            final ConditionItem citem = (ConditionItem) item;
            queue.enqueue(createItem(citem));
         }
         else if(item instanceof JunctionOperator) {
            final JunctionOperator jitem = (JunctionOperator) item;
            queue.enqueue(createItem(jitem));
         }
      }

      return walk(queue);
   }

   /**
    * Convert a node tree to a forest.
    * @param a list contains XNode(s).
    */
   public static void toForest(ArrayList<XNode> list) {
      if(list == null) {
         return;
      }

      boolean changed = true;

      while(changed) {
         changed = false;
         Iterator<XNode> it = list.iterator();

         while(it.hasNext()) {
            XNode node = it.next();
            changed = validateNode(node, list);

            if(changed) {
               break;
            }
         }
      }
   }

   /**
    * Convert nodes tree to a forest, grouped by dimension.
    */
   public static HashMap<String, XNode> getFilters(XNode node) {
      HashMap<String, XNode> filters = new HashMap();

      if(node == null) {
         return filters;
      }

      String dimension = getDimension(node);

      if(dimension != null) {
         fillDimension(filters, dimension, node);
         return filters;
      }

      for(int i = 0; i < node.getChildCount(); i++) {
         HashMap<String, XNode> filters0 = getFilters(node.getChild(i));
         Iterator<String> it = filters0.keySet().iterator();

         while(it.hasNext()) {
            dimension = it.next();
            fillDimension(filters, dimension, filters0.get(dimension));
         }
      }

      return filters;
   }

   /**
    * Merge XNode from same dimension.
    */
   private static XNode mergeNodes(XNode node1, XNode node2) {
      // just 'and' same dimension, since 'or' has already been broken
      XMLASet set = new XMLASet(XMLASet.AND);
      set.addChild(node1, false, false);
      set.addChild(node2, false, false);

      return new XMLASetItem(set, -1).getNode();
   }

   /**
    * Fill merged dimension.
    */
   private static void fillDimension(HashMap<String, XNode> map, String dim,
                                     XNode node) {
      if(map.containsKey(dim)) {
         XNode node0 = map.get(dim);
         node = mergeNodes(node0, node);
      }

      map.put(dim, node);
   }

   /**
    * Validate an XNode to check if it's mergeable.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   private static boolean validateNode(XNode node, ArrayList<XNode> list) {
      if(node == null) {
         return false;
      }

      if("true".equals(node.getAttribute("solid"))) {
         return false;
      }

      if(getDimension(node) != null) {
         node.setAttribute("solid", "true");
         return false;
      }

      XMLASet set = (XMLASet) node;
      String relation = set.getRelation();
      XNode node0 = node.getChild(0);
      XNode node1 = node.getChild(1);

      if(XMLASet.OR.equals(relation)) {
         // is root node
         if(list.contains(node)) {
            list.remove(node);
            list.add(node0);
            list.add(node1);

            return true;
         }

         // get root node
         XNode root = node;

         while(root != null && !list.contains(root)) {
            root = root.getParent();
         }

         list.remove(root);
         XNode pnode = node.getParent();
         pnode.removeChild(node, false);

         XNode root0 = (XNode) root.clone();
         XNode root1 = (XNode) root.clone();
         XNode pnode0 = findNode(root0, pnode);
         pnode0.addChild(node0, false, false);
         XNode pnode1 = findNode(root1, pnode);
         pnode1.addChild(node1, false, false);
         list.add(root0);
         list.add(root1);

         return true;
      }

      if(validateNode(node0, list)) {
         return true;
      }

      return validateNode(node1, list);
   }

   /**
    * Get dimension name from a node or its children.
    * @param node the specified node contains dimension.
    * @return the dimension name if this node is a leaf node and contains a
    * dimension name, or, all of its children, including sub-children have the
    * same dimension name, return <tt>null</tt> otherwise.
    */
   static String getDimension(XNode node) {
      String dim = (String) node.getAttribute("dimension");

      if(dim != null) {
         return dim;
      }

      int nodeCnt = node.getChildCount();

      if(nodeCnt == 0) {
         return "[Measures]";
      }

      dim = getDimension(node.getChild(0));

      for(int i = 1; i < nodeCnt; i++) {
         if(!Tool.equals(dim, getDimension(node.getChild(i)))) {
            return null;
         }
      }

      return dim;
   }

   /**
    * Create XMLANode Item.
    */
   private static XMLANodeItem createItem(ConditionItem item) {
      XMLANode node = new XMLANode();
      node.setValue(item);
      String[] names = XMLAUtil.getNames(item.getAttribute());
      String dim = names[0];
      dim = dim == null ? names[1] : dim;
      node.setAttribute("dimension", dim);

      return new XMLANodeItem(node, item.getLevel());
   }

   /**
    * Create XMLASet Item.
    */
   private static XMLASetItem createItem(JunctionOperator item) {
      XMLASet result = new XMLASet(XMLASet.AND);

      if(item.getJunction() == JunctionOperator.OR) {
         result = new XMLASet(XMLASet.OR);
      }

      return new XMLASetItem(result, item.getLevel());
   }

   /**
    * Calculate the condition group.
    */
   private static XMLANode walk(Queue queue) {
      Stack stack = new Stack();

      while(queue.peek() != null) {
         if(stack.empty()) {
            stack.push(queue.dequeue());
            continue;
         }

         HierarchyItem istack = (HierarchyItem) stack.peek();
         HierarchyItem iqueue = (HierarchyItem) queue.peek();

         if(iqueue == null) {
            break;
         }
         else if(iqueue.getLevel() > istack.getLevel()) {
            stack.push(queue.dequeue());
         }
         else if(iqueue.getLevel() == istack.getLevel()) {
            if(iqueue instanceof XMLASetItem) {
               stack.push(queue.dequeue());
            }
            else {
               XMLASetItem op = (XMLASetItem) istack;

               if(op.getXMLASet().getRelation().equals(XMLASet.AND)) {
                  calcQueueStack(queue, stack);
               }
               else {
                  stack.push(queue.dequeue());
               }
            }
         }
         else {
            calcStack(queue, stack);
         }
      }

      while(!stack.empty() && stack.size() > 2) {
         calcStackStack(queue, stack);
      }

      return stack.empty() ? null : ((XMLANodeItem) stack.pop()).getNode();
   }

   /**
    * Calculate conditions from stack and put back to stack.
    */
   private static void calcStackStack(Queue queue, Stack stack) {
      XMLANodeItem i1 = (XMLANodeItem) stack.pop();
      XMLASetItem op = (XMLASetItem) stack.pop();
      XMLANodeItem i2 = (XMLANodeItem) stack.pop();
      XMLANodeItem result = calc(i1, op, i2, queue, stack);

      stack.push(result);
   }

   /**
    * Calculate conditions from queue and stack and put to queue.
    */
   private static void calcQueueStack(Queue queue, Stack stack) {
      XMLANodeItem i2 = (XMLANodeItem) queue.dequeue();
      XMLASetItem op = (XMLASetItem) stack.pop();
      XMLANodeItem i1 = (XMLANodeItem) stack.pop();
      XMLANodeItem result = calc(i1, op, i2, queue, stack);

      queue.add(0, result);
   }

   /**
    * Calculate conditions from stack and put to queue.
    */
   private static void calcStack(Queue queue, Stack stack) {
      XMLANodeItem i2 = (XMLANodeItem) stack.pop();
      XMLASetItem op = (XMLASetItem) stack.pop();
      XMLANodeItem i1 = (XMLANodeItem) stack.pop();
      XMLANodeItem result = calc(i1, op, i2, queue, stack);

      queue.add(0, result);
   }

   /**
    * Check the result item level.
    */
   private static int checkLevel(int original, Queue queue, Stack stack)
   {
      int lvl1 = queue.peek() == null ?
         -1 :
         ((HierarchyItem) queue.peek()).getLevel();
      int lvl2 = stack.empty() ?
         -1 :
         ((HierarchyItem) stack.peek()).getLevel();
      int lvl = Math.max(lvl1, lvl2);

      return (lvl == -1) ? original : lvl;
   }

   /**
    * Calculate two boolean values.
    */
   private static XMLANodeItem calc(XMLANodeItem i1, XMLASetItem op,
                                    XMLANodeItem i2, Queue queue,
                                    Stack stack) {
      final int lvl = checkLevel(op.getLevel(), queue, stack);
      XMLASet set = op.getXMLASet();

      set.addChild(i1.getNode(), false, false);
      set.addChild(i2.getNode(), false, false);
      op.setLevel(lvl);

      return op;
   }

   /**
    * Find out the specified node in a node tree.
    */
   private static XNode findNode(XNode tree, XNode node) {
      if(Tool.equals(tree, node)) {
         return tree;
      }

      for(int i = 0; i < tree.getChildCount(); i++) {
         XNode nodei = findNode(tree.getChild(i), node);

         if(nodei != null) {
            return nodei;
         }
      }

      return null;
   }
}
