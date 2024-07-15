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
package inetsoft.sree.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Service response that wraps an HttpServletResponse. This class is used for
 * HTTP clients, such as the repository servlet.
 * @author InetSoft Technology Corp.
 * @since  5.0
 */
public class HttpServiceResponse implements ServiceResponse {
   /**
    * Create a new instance of HttpServiceResponse.
    * @param response the base HTTP response.
    */
   public HttpServiceResponse(HttpServletResponse response) {
      // @by larryl, avoid holding the response object directory since the
      // service response will not be freed until report times out. The
      // http servlet response may hold other more heavy objects
      this.responseRef = new WeakReference(response);
   }

   /**
    * Get the mime-type of the requested resource.
    * @return the mime-type identifier for the resource, or <code>null</code> if
    * no resource was requested.
    */
   @Override
   public String getContentType() {
      return contentType;
   }

   /**
    * Set the mime-type of the requested resource.
    * @param contentType the mime-type identifier for the resource.
    */
   @Override
   public void setContentType(String contentType) {
      HttpServletResponse response = (HttpServletResponse) responseRef.get();

      if(response == null) {
         throw new RuntimeException(
            "HttpServletResponse has timed out and is not accessible");
      }

      this.contentType = contentType;
      response.setContentType(StringUtils.normalizeSpace(contentType));
   }

   /**
    * Get the size of the requested resource.
    * @return the size, in bytes, of the requested resource.
    */
   @Override
   public int getContentLength() {
      return contentLength;
   }

   /**
    * Set the size of the requested resource.
    * @param contentLength the size, in bytes, of the requested resource.
    */
   @Override
   public void setContentLength(int contentLength) {
      HttpServletResponse response = (HttpServletResponse) responseRef.get();

      if(response == null) {
         throw new RuntimeException(
            "HttpServletResponse has timed out and is not accessible");
      }

      this.contentLength = contentLength;
      response.setContentLength(contentLength);
   }

   /**
    * Get the output stream for writing resources.
    * @return the reponse output stream.
    * @throws IOException if an I/O error occurs.
    */
   @Override
   public OutputStream getOutputStream() throws IOException {
      HttpServletResponse response = (HttpServletResponse) responseRef.get();

      if(response == null) {
         throw new RuntimeException(
            "HttpServletResponse has timed out and is not accessible");
      }

      return response.getOutputStream();
   }

   /**
    * Get the replet instance ID for the replet to which this repsonse pertains.
    * @return the replet instance ID.
    */
   @Override
   public Object getRepletId() {
      return repletId;
   }

   /**
    * Set the replet instance ID for the replet to which this response pertains.
    * @param repletId the replet instance ID.
    */
   @Override
   public void setRepletId(Object repletId) {
      this.repletId = repletId;
   }

   /**
    * Get the value of an HTTP header used when transmitting the requested
    * resource.
    * @param name the name of the header.
    * @return the value of the header field, or <code>null</code> if the
    * specified header has not been set.
    */
   @Override
   public String getHeader(String name) {
      return headers.getProperty(name);
   }

   /**
    * Set the value of an HTTP header used when transmitting the requested
    * resource.
    * @param name the name of the header.
    * @param value the value of the header field.
    */
   @Override
   public void setHeader(String name, String value) {
      HttpServletResponse response = (HttpServletResponse) responseRef.get();

      if(response == null) {
         throw new RuntimeException(
            "HttpServletResponse has timed out and is not accessible");
      }

      response.setHeader(name, StringUtils.normalizeSpace(value));

      if(value == null) {
         headers.remove(name);
      }
      else {
         headers.setProperty(name, value);
      }
   }

   /**
    * Get the names of all HTTP header fields that should be used when
    * transmitting the requested resource.
    * @return a list of header field names.
    */
   @Override
   public Enumeration getHeaderNames() {
      return headers.propertyNames();
   }

   /**
    * Get the servlet response that this object wraps.
    * @return the base response.
    */
   public HttpServletResponse getResponse() {
      HttpServletResponse response = (HttpServletResponse) responseRef.get();

      if(response == null) {
         throw new RuntimeException(
            "HttpServletResponse has timed out and is not accessible");
      }

      return response;
   }

   /**
    * Adds the specified cookie to the response.
    * This method can be called multiple times to set more than one cookie.
    * @param cookie the Cookie to return to the client
    */
   @Override
   public void addCookie(Cookie cookie) {
      HttpServletResponse response = (HttpServletResponse) responseRef.get();

      if(response == null) {
         throw new RuntimeException(
            "HttpServletResponse has timed out and is not accessible");
      }

      cookie.setSecure(true);
      response.addCookie(cookie);
   }

   /**
    * Determines if this response supports writing resources to the client.
    *
    * @return <code>true</code> if resources are supported; <code>false</code>
    *         otherwise.
    */
   @Override
   public boolean isResourceSupported() {
      return true;
   }

   //private HttpServletResponse response = null;
   private WeakReference responseRef;
   private Object repletId = null;
   private String contentType = null;
   private int contentLength = 0;
   private Properties headers = new Properties();
}

