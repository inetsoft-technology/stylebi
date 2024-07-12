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
package inetsoft.uql.tabular;

import java.lang.annotation.Annotation;

/**
 * This class wraps View2 in View1 API.
 *
 * @author InetSoft Technology
 * @since 12.0
 */
public class View1Proxy implements View1 {
   public View1Proxy(View2 view2) {
      this.view2 = view2;
   }
   
   /**
    * Wrap a View2 array into View1 array.
    */
   public static View1[] wrap(View2[] views) {
      View1[] arr = new View1[views.length];
      
      for(int i = 0; i < arr.length; i++) {
         arr[i] = new View1Proxy(views[i]);
      }
      
      return arr;
   }
   
   @Override
   public ViewType type() {
      return view2.type();
   }

   @Override
   public String text() {
      return view2.text();
   }

   @Override
   public String color() {
      return view2.color();
   }

   @Override
   public String font() {
      return view2.font();
   }

   @Override
   public String value() {
      return view2.value();
   }

   @Override
   public int row() {
      return view2.row();
   }

   @Override
   public int col() {
      return view2.col();
   }

   @Override
   public int rowspan() {
      return view2.rowspan();
   }

   @Override
   public int colspan() {
      return view2.colspan();
   }

   @Override
   public ViewAlign align() {
      return view2.align();
   }

   @Override
   public ViewAlign verticalAlign() {
      return view2.verticalAlign();
   }

   @Override
   public int paddingLeft() {
      return view2.paddingLeft();
   }

   @Override
   public int paddingRight() {
      return view2.paddingRight();
   }

   @Override
   public int paddingTop() {
      return view2.paddingTop();
   }

   @Override
   public int paddingBottom() {
      return view2.paddingBottom();
   }

   @Override
   public View2[] elements() {
      return new View2[0];
   }

   @Override
   public boolean vertical() {
      return false;
   }

   @Override
   public Button button() {
      return view2.button();
   }

   @Override
   public String visibleMethod() {
      return view2.visibleMethod();
   }

   @Override
   public boolean wrap() {
      return view2.wrap();
   }

   @Override
   public Class<? extends Annotation> annotationType() {
      return View1.class;
   }

   private View2 view2;
}
