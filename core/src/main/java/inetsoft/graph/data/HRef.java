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
package inetsoft.graph.data;

import java.io.Serializable;
import java.util.*;

/**
 * Hyperlink.Ref contains the actual hyperlink and the link parameter
 * values.
 */
public class HRef implements Serializable {
   /**
    * Create an empty hyperlink. The setLink() must be called to set the
    * hyperlink before it can be used.
    */
   public HRef() {
   }

   /**
    * Create a hyperlink def.
    * @param link the hyperlink href string.
    */
   public HRef(String link) {
      this.link = link;
   }

   /**
    * Return the name (label) for this link.
    */
   public String getName() {
      return link;
   }

   /**
    * Set the hyperlink. It could be a report name (path) or a URL.
    * @param link for Web URL, the link must be the full URL including
    * the protocol. If the link is to another report, it should be the
    * report path as registered in report server.
    */
   public void setLink(String link) {
      this.link = link;
   }

   /**
    * Get the hyperlink.
    */
   public String getLink() {
      return link;
   }

   /**
    * Set the hyperlink target frame. It is only used in DHTML viewer
    * @param targetFrame is the window name for hyperlink
    */
   public void setTargetFrame(String targetFrame) {
      this.targetFrame = targetFrame;
   }

   /**
    * Get the hyperlink target frame
    */
   public String getTargetFrame() {
      return targetFrame;
   }

   /**
    * Set the tooltip. If tooltip is set, the tip is shown when mouse moves
    * into the hyperlink.
    */
   public void setToolTip(String tip) {
      this.tip = tip;
   }

   /**
    * Get the tooltip.
    */
   public String getToolTip() {
      return tip;
   }

   /**
    * Get all parameter names.
    */
   public Enumeration<String> getParameterNames() {
      if(params == null) {
         return Collections.emptyEnumeration();
      }

      return params.keys();
   }

   /**
    * Get the number of parameters defined for this link.
    */
   public int getParameterCount() {
      return (params == null) ? 0 : params.size();
   }

   /**
    * Get the value for the parameter.
    */
   public Object getParameter(String name) {
      return (params == null) ? null : params.get(name);
   }

   /**
    * Set the value for the parameter.
    * @param name parameter name.
    * @param data parameter value.
    */
   public void setParameter(String name, Object data) {
      if(data == null) {
         if(params != null) {
            params.remove(name);
         }
      }
      else {
         if(params == null) {
            params = new Hashtable<>(3);
         }

         params.put(name, data);
      }
   }

   /**
    * Remove a parameter field.
    * @param name parameter name.
    */
   public void removeParameter(String name) {
      if(params != null) {
         params.remove(name);

         if(params.size() == 0) {
            params = null;
         }
      }
   }

   /**
    * Remove all parameter values.
    */
   public void removeAllParameters() {
      params = null;
   }

   /**
    * Check if equals another Hyperlink.Ref.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof HRef)) {
         return false;
      }

      HRef ref2 = (HRef) obj;

      boolean result = link == null ?
         ref2.link == null : link.equals(ref2.link);

      if(!result) {
         return false;
      }

      result = params == null ?
         params == ref2.params : params.equals(ref2.params);

      if(!result) {
         return false;
      }

      result = tip == null ? ref2.tip == null : tip.equals(ref2.tip);

      if(!result) {
         return false;
      }

      result = targetFrame == null ? ref2.targetFrame == null :
         targetFrame.equals(ref2.targetFrame);

      return result;
   }

   private String link;
   private String targetFrame = "";
   private String tip;
   private Hashtable<String, Object> params;
}
