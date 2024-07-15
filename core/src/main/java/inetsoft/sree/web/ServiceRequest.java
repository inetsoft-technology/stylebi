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
import java.io.InputStream;
import java.security.Principal;
import java.util.Enumeration;

/**
 * Represents a request to the DHTMLService.
 * @author InetSoft Technology Corp.
 * @since  5.0
 */
public interface ServiceRequest {
   /**
    * Get the value of a request parameter.
    * @param name the name of the parameter.
    * @return the value of the parameter, or <code>null</code> if the parameter
    * does not exist.
    */
   public String getParameter(String name);

   /**
    * Set the value of a request parameter.
    * @param name the name of the parameter.
    * @value the value of the parameter.
    */
   public void setParameter(String name, String value);

   /**
    * Get an array of String objects containing all of the values the given
    * request parameter has, or <code>null</code> if the parameter does not
    * exist.
    * @param name the name of the parameter.
    * @return an array of String objects containing the parameter's values.
    */
   public String[] getParameterValues(String name);

   /**
    * Get the names of all request parameters.
    * @return a list of the names of all the parameters in this request.
    */
   public Enumeration getParameterNames();

   /**
    * Get the value of a persistent attribute in the current session.
    * @param name the name of the attribute.
    * @return the value of the attribute.
    */
   public Object getAttribute(String name);

   /**
    * Set the value of a persistent attribute in the current session.
    * @param name the name of the attribute.
    * @param value the value of the attribute.
    */
   public void setAttribute(String name, Object value);

   /**
    * Get the names of all persistent attributes in the current session.
    * @return a list of the names of all the attributes.
    */
   public Enumeration getAttributeNames();

   /**
    * Get the user that is making the request.
    * @return a Principal object that identifies the user.
    */
   public Principal getPrincipal();

   /**
    * Set the Principal object that identifies the user.
    * @param principal the Principal object.
    */
   public void setPrincipal(Principal principal);

   /**
    * Get the IP address of the remote client making the request.
    * @return an IP (Internet Protocol) address.
    */
   public String getRemoteAddr();

   /**
    * Get the host name of the remote client making the request.
    * @return host name.
    */
   public String getRemoteHost();

   /**
    * Get the host name of the server that received the request.
    * @return server name.
    */
   public String getServerName();

   /**
    * Get the port number on which this request was received.
    * @return the server port.
    */
   public int getServerPort();

   /**
    * Get the URI that should be used for all hyperlinks back to the report
    * server.
    * @return the URI of the report server.
    */
   public String getLinkURI();

   /**
    * Get the prefix that is used to identify the forms, parameters, etc. of
    * a report in a page that contains multiple reports.
    * @return the prefix for the report to which this request applies or
    * <code>null</code> if no prefix is applicable.
    */
   public String getReportPrefix();

   /**
    * Get the request protocol.
    * @return protocol such as http, https.
    */
   public String getProtocol();

   /**
    * Determine if the layout of the report should be overridden and the table
    * layout be used.
    * @return <code>true</code> if the report should be forced to use the table
    * layout; <code>false</code> otherwise.
    */
   public boolean isForceTable();

   /**
    * Determine if view type of the report supports frame-based features, such
    * as the new search panel.
    * @return <code>true</code> if the view type supports frames.
    */
   public boolean isFrameSupported();

   /**
    * Determines if the report toolbar should be made "floating" using CSS-P.
    *
    * @return <code>true</code> if a floating toolbar is supported.
    *
    * @since 8.0
    */
   public boolean isFloatToolbarSupported();

   /**
    * Determines if PDF exports should be forced to load in a new window.
    *
    * @return <code>true</code> if PDF exports should be forced to load in a
    *         new window.
    */
   public boolean isForcePDFPopup();

   /**
    * Get the user agent of the client. This is typically the name of the web
    * browser being used.
    * @return the user agent.
    */
   public String getUserAgent();

   /**
    * Get the session ID requested by the client.
    * @return a String specifying the session ID, or <code>null</code> if the
    * request did not specify a session ID.
    */
   public String getRequestedSessionId();

   /**
    * Get an input stream to read the request raw data. This only works for
    * servlet deployment and should not be used in JSP.
    */
   public InputStream getInputStream() throws IOException;

   /**
    * Get an array containing all of the Cookie objects the client sent with
    * this request. This method returns null if no cookies were sent.
    * @return an array of all the Cookies included with this request,
    * or <code>null</code> if the request has no cookies.
    */
   public Cookie[] getCookies();
}

