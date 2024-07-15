/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.css;

import inetsoft.util.Tool;

import java.io.Serializable;
import java.util.*;

/**
 * CSSParameter
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class CSSParameter implements Serializable{
   public CSSParameter() {
   }

   public CSSParameter(String type, String id, String cls,
      Map<String, String> attributes)
   {
      this.type = type;
      this.id = id;
      this.cls = cls;
      this.attributes = attributes;
   }

   public void setCSSType(String type) {
      this.type = type;
   }

   public String getCSSType() {
      return type;
   }

   public void setCSSID(String id) {
      this.id = id;
   }

   public String getCSSID() {
      return id;
   }

   public void setCSSClass(String cls) {
      this.cls = cls;
   }

   public String getCSSClass() {
      return cls;
   }

   public void setCSSAttributes(Map<String, String> attributes) {
      this.attributes = attributes;
   }

   public Map<String, String> getCSSAttributes() {
      return attributes;
   }

   @Override
   public boolean equals(Object o) {
      if(!(o instanceof CSSParameter)) {
         return false;
      }

      CSSParameter cssParam = (CSSParameter) o;

      if(Tool.compare(type, cssParam.type, false, true) == 0 &&
         Tool.compare(id, cssParam.id, false, true) == 0 &&
         Tool.compare(cls, cssParam.cls) == 0)
      {
         if(attributes == null && cssParam.attributes == null) {
            return true;
         }
         else if(attributes == null || cssParam.attributes == null) {
            return false;
         }
         else if(attributes.size() == cssParam.attributes.size()) {
            for(Map.Entry<String, String> entry : attributes.entrySet()) {
               String value = cssParam.attributes.get(entry.getKey());

               if(entry != null && entry.getValue() != null &&
                  !(entry.getValue().equalsIgnoreCase(value)))
               {
                  return false;
               }
            }

            return true;
         }
         else {
            return false;
         }
      }
      else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      int result = type != null ? type.hashCode() : 0;
      result = 31 * result + (id != null ? id.hashCode() : 0);
      result = 31 * result + (cls != null ? cls.hashCode() : 0);
      result = 31 * result + (attributes != null ? attributes.hashCode() : 0);
      return result;
   }

   @Override
   public String toString() {
      return "type: " + type + " id: " + id + " class: " + cls +
         " attributes: " + attributes;
   }

   public static CSSParameter[] getAllCSSParams(List<CSSParameter> parentParams, CSSParameter cssParam) {
      List<CSSParameter> params = new ArrayList<>();

      if(parentParams != null) {
         params.addAll(parentParams);
      }

      params.add(cssParam);
      return params.toArray(new CSSParameter[0]);
   }

   private String type;
   private String id;
   private String cls;
   private Map<String, String> attributes;
}