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
package inetsoft.sree.web;

import jakarta.servlet.http.Cookie;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;

/**
 * Represents a response from the DHTMLService.
 * @author InetSoft Technology Corp.
 * @since  5.0
 */
public interface ServiceResponse {
   /**
    * Get the output stream for writing resources.
    * @return the reponse output stream.
    * @throws IOException if an I/O error occurs.
    */
   public OutputStream getOutputStream() throws IOException;
   
   /**
    * Get the replet instance ID for the replet to which this repsonse pertains.
    * @return the replet instance ID.
    */
   public Object getRepletId();
   
   /**
    * Set the replet instance ID for the replet to which this response pertains.
    * @param repletId the replet instance ID.
    */
   public void setRepletId(Object repletId);
   
   /**
    * Get the mime-type of the requested resource.
    * @return the mime-type identifier for the resource, or <code>null</code> if
    * no resource was requested.
    */
   public String getContentType();
   
   /**
    * Set the mime-type of the requested resource.
    * @param contentType the mime-type identifier for the resource.
    */
   public void setContentType(String contentType);
   
   /**
    * Get the size of the requested resource.
    * @return the size, in bytes, of the requested resource.
    */
   public int getContentLength();
   
   /**
    * Set the size of the requested resource.
    * @param contentLength the size, in bytes, of the requested resource.
    */
   public void setContentLength(int contentLength);
   
   /**
    * Get the value of an HTTP header used when transmitting the requested
    * resource.
    * @param name the name of the header.
    * @return the value of the header field, or <code>null</code> if the
    * specified header has not been set.
    */
   public String getHeader(String name);
   
   /**
    * Set the value of an HTTP header used when transmitting the requested
    * resource.
    * @param name the name of the header.
    * @param value the value of the header field.
    */
   public void setHeader(String name, String value);
   
   /**
    * Get the names of all HTTP header fields that should be used when
    * transmitting the requested resource.
    * @return a list of header field names.
    */
   public Enumeration getHeaderNames();
   
   /**
    * Adds the specified cookie to the response. 
    * This method can be called multiple times to set more than one cookie.
    * @param cookie the Cookie to return to the client
    */
   public void addCookie(Cookie cookie);
   
   /**
    * Determines if this response supports writing resources to the client.
    *
    * @return <code>true</code> if resources are supported; <code>false</code>
    *         otherwise.
    */
   public boolean isResourceSupported();
}

