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
package inetsoft.report.internal;

import java.util.*;

public class StyleCurrentElements <T> implements Cloneable {

   public StyleCurrentElements() {
   }

   public StyleCurrentElements(int selected, Vector<T> elements) {
      this(convertKey(selected), elements);
   }

   public StyleCurrentElements(String selected, Vector<T> elements) {
      this.selected = selected;
      this.elements = elements;
   }

   public synchronized void setHeader(String selected, Vector<T> elements) {
      this.selected = selected;
      this.elements = elements;
   }

   /**
    * convert embed header id
    * @param selected int or int string
    */
   private static String convertKey(Object selected) {
      if(selected == null) {
         return null;
      }

      return EMBED_HEADER + selected;
   }

   public String getSelected() {
      return selected;
   }

   public boolean isSelected(Object selected) {
      return Objects.equals(this.selected, selected) ||
         Objects.equals(this.selected, convertKey(selected));
   }

   public void setSelected(String selected) {
      this.selected = selected;
   }

   public void setSelected(int flag) {
      this.selected = convertKey(flag);
   }

   public Enumeration<T> getElements() {
      return elements.elements();
   }

   public void setElements(Vector<T> elements) {
      this.elements = elements;
   }

   public void addElement(T ele) {
      elements.addElement(ele);
   }

   public int size() {
      return elements.size();
   }

   public T[] toArray(T[] elems) {
      return elements.toArray(elems);
   }

   public T elementAt(int index) {
      return elements.elementAt(index);
   }

   public int indexOf(T elem, int start) {
      return elements.indexOf(elem, start);
   }

   public void insertElementAt(T elem, int index) {
      elements.insertElementAt(elem, index);
   }

   public void removeElementAt(int index) {
      elements.removeElementAt(index);
   }

   public void setElementAt(T elem, int index) {
      elements.setElementAt(elem, index);
   }

   @Override
   public Object clone() {
      StyleCurrentElements<T> curr = new StyleCurrentElements<>();

      curr.selected = this.selected;
      curr.elements = elements;

      return curr;
   }

   public StyleCurrentElements clone(StyleCore styleCore, boolean flag, boolean header) {
      if(styleCore == null ||
         header && (styleCore.currHeader == null || styleCore.currHeader.elements == null) ||
         !header && (styleCore.currFooter == null || styleCore.currFooter.elements == null))
      {
         return (StyleCurrentElements) this.clone();
      }

      Vector vector = styleCore.cloneElements(
         header ? styleCore.currHeader.elements : styleCore.currFooter.elements);
      StyleCurrentElements curr = new StyleCurrentElements<>();

      curr.selected = this.selected;
      curr.elements = vector;

      return curr;
   }

   private String selected;
   private Vector<T> elements;

   public static final String EMBED_HEADER = "Embed:";
}
