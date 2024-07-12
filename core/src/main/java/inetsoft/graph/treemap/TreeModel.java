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
package inetsoft.graph.treemap;

import inetsoft.graph.geometry.TreemapGeometry;
import inetsoft.graph.treeviz.tree.TreeNode;

import java.util.*;

/**
 * An implementation of MapModel that represents
 * a hierarchical structure. It currently cannot
 * handle structural changes to the tree, since it
 * caches a fair amount of information.
 */
public class TreeModel implements MapModel, TreeNode {
   private TreemapGeometry gobj;
   private Mappable mapItem;
   private Mappable[] childItems;
   private Mappable[] cachedTreeItems; // we assume tree structure doesn't change.
   private MapModel[] cachedLeafModels;
   private TreeModel parent;
   private List<TreeNode> children = new ArrayList<>();
   private boolean sumsChildren;
   private boolean leaf;

   public TreeModel() {
      this.mapItem = new MapItem();
      sumsChildren = true;
   }

   public TreeModel(TreemapGeometry gobj) {
      this.mapItem = gobj.getMapItem();
      this.gobj = gobj;
   }

   public void setOrder(int order) {
      mapItem.setOrder(order);
   }

   public MapModel[] getLeafModels() {
      if(cachedLeafModels != null) {
         return cachedLeafModels;
      }

      Vector v = new Vector();
      addLeafModels(v);
      int n = v.size();
      MapModel[] m = new MapModel[n];
      v.copyInto(m);
      cachedLeafModels = m;
      return m;
   }

   private Vector addLeafModels(Vector v) {
      if(!hasChildren()) {
         return v;
      }
      if(!getChild(0).hasChildren()) {
         v.addElement(this);
      }
      else {
         for(int i = childCount() - 1; i >= 0; i--) {
            getChild(i).addLeafModels(v);
         }
      }
      return v;

   }

   public int depth() {
      if(parent == null) {
         return 0;
      }
      return 1 + parent.depth();
   }

   public void layout(MapLayout tiling) {
      layout(tiling, mapItem.getBounds());
   }

   public void layout(MapLayout tiling, Rect bounds) {
      mapItem.setBounds(bounds);
      if(!hasChildren()) {
         return;
      }
      double s = sum();
      tiling.layout(this, bounds);
      for(int i = childCount() - 1; i >= 0; i--) {
         getChild(i).layout(tiling);
      }
   }

   public Mappable[] getTreeItems() {
      if(cachedTreeItems != null) {
         return cachedTreeItems;
      }

      Vector v = new Vector();
      addTreeItems(v);
      int n = v.size();
      Mappable[] m = new Mappable[n];
      v.copyInto(m);
      cachedTreeItems = m;
      return m;
   }

   private void addTreeItems(Vector v) {
      if(!hasChildren()) {
         v.addElement(mapItem);
      }
      else {
         for(int i = childCount() - 1; i >= 0; i--) {
            getChild(i).addTreeItems(v);
         }
      }
   }

   public double sum() {
      if(!sumsChildren) {
         return mapItem.getSize();
      }
      double s = 0;
      for(int i = childCount() - 1; i >= 0; i--) {
         s += getChild(i).sum();
      }
      mapItem.setSize(s);
      return s;
   }

   public Mappable[] getItems() {
      if(childItems != null) {
         return childItems;
      }
      int n = childCount();
      childItems = new Mappable[n];
      for(int i = 0; i < n; i++) {
         childItems[i] = getChild(i).getMapItem();
         childItems[i].setDepth(1 + depth());
      }
      return childItems;
   }

   public Mappable getMapItem() {
      return mapItem;
   }

   public void addChild(TreeModel child) {
      child.setParent(this);
      children.add(child);
      childItems = null;
   }

   public void updateChildRows() {
      children.forEach(child -> ((TreeModel) child).updateChildRows());

      if(gobj != null) {
         int[] childrows = children.stream().map(child -> ((TreeModel) child).gobj)
            .map(gobj -> {
               int[] crows = gobj.getChildRows();
               // use sub (isntead of root) row index so calc columns are accessible. (56711)
               return crows.length == 0 ? new int[]{ gobj.getSubRowIndex() } : crows;
            })
            .flatMapToInt(rows -> Arrays.stream(rows))
            .toArray();
         gobj.setChildRows(childrows);
      }
   }

   public void setParent(TreeModel parent) {
      for(TreeModel p = parent; p != null; p = p.getParent()) {
         if(p == this) {
            throw new IllegalArgumentException("Circular ancestry!");
         }
      }
      this.parent = parent;
   }

   public TreeModel getParent() {
      return parent;
   }

   public int childCount() {
      return children.size();
   }

   public TreeModel getChild(int n) {
      return (TreeModel) children.get(n);
   }

   public boolean hasChildren() {
      return children.size() > 0;
   }

   public void setLeaf(boolean leaf) {
      this.leaf = leaf;
   }

   public void print() {
      print("");
   }

   private void print(String prefix) {
      System.out.println(prefix + "size=" + mapItem.getSize());
      for(int i = 0; i < childCount(); i++) {
         getChild(i).print(prefix + "..");
      }
   }

   // TreeNode methods

   @Override
   public List<TreeNode> children() {
      return children;
   }

   @Override
   public boolean getAllowsChildren() {
      return !leaf;
   }
}
