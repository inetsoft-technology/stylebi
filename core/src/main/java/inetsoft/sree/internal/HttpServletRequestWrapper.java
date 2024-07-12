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
package inetsoft.sree.internal;

import jakarta.servlet.http.HttpServletRequest;

import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * Provide an interface to forward requests to a real requests. It can be
 * used to change the behavior of http requests.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class HttpServletRequestWrapper
   extends jakarta.servlet.http.HttpServletRequestWrapper
{
   public HttpServletRequestWrapper(HttpServletRequest req) {
      super(req);
   }

   @Override
   public String getParameter(String name) {
      if(ignore.contains(name)) {
         return null;
      }

      return super.getParameter(name);
   }

   @Override
   public Enumeration getParameterNames() {
      Enumeration names = super.getParameterNames();
      Vector vec = new Vector();

      while(names.hasMoreElements()) {
         Object name = names.nextElement();

         if(ignore.contains(name)) {
            continue;
         }

         vec.addElement(name);
      }

      return vec.elements();
   }

   @Override
   public String[] getParameterValues(String name) {
      if(ignore.contains(name)) {
         return null;
      }

      return super.getParameterValues(name);
   }

   @Override
   public StringBuffer getRequestURL() {
      return super.getRequestURL();
   }

   @Override
   public Map getParameterMap() {
      return super.getParameterMap();
   }

   @Override
   public void setCharacterEncoding(String enc)
      throws UnsupportedEncodingException
   {
      super.setCharacterEncoding(enc);
   }

   /**
    * This method takes a parameter name to ignore in the request, it won't
    * treat this parameter as part of the request when asking for, it's value
    * or the parameter names.
    */
   public void ignoreParameter(String name) {
      ignore.add(name);
   }

   Vector ignore = new Vector();
}

