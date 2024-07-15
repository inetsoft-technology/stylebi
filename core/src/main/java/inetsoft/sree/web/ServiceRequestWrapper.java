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
 * Wrap a service request.
 *
 * @author InetSoft Technology Corp.
 * @since  6.0
 */
public class ServiceRequestWrapper implements ServiceRequest {
   /**
    * Constructor.
    */
   public ServiceRequestWrapper(ServiceRequest req) {
      this.req = req;
   }

   /**
    * Set the value of a request parameter.
    * @param name the name of the parameter.
    * @value the value of the parameter.
    */
   @Override
   public void setParameter(String name, String value) {
      req.setParameter(name, value);
   }

   /**
    * Get the value of a request parameter.
    * @param name the name of the parameter.
    * @return the value of the parameter, or <code>null</code> if the parameter
    * does not exist.
    */
   @Override
   public String getParameter(String name) {
      return req.getParameter(name);
   }

   /**
    * Get an array of String objects containing all of the values the given
    * request parameter has, or <code>null</code> if the parameter does not
    * exist.
    * @param name the name of the parameter.
    * @return an array of String objects containing the parameter's values.
    */
   @Override
   public String[] getParameterValues(String name) {
      return req.getParameterValues(name);
   }

   /**
    * Get the names of all request parameters.
    * @return a list of the names of all the parameters in this request.
    */
   @Override
   public Enumeration getParameterNames() {
      return req.getParameterNames();
   }

   /**
    * Get the value of a persistent attribute in the current session.
    * @param name the name of the attribute.
    * @return the value of the attribute.
    */
   @Override
   public Object getAttribute(String name) {
      return req.getAttribute(name);
   }

   /**
    * Set the value of a persistent attribute in the current session.
    * @param name the name of the attribute.
    * @param value the value of the attribute.
    */
   @Override
   public void setAttribute(String name, Object value) {
      req.setAttribute(name, value);
   }

   /**
    * Get the names of all persistent attributes in the current session.
    * @return a list of the names of all the attributes.
    */
   @Override
   public Enumeration getAttributeNames() {
      return req.getAttributeNames();
   }

   /**
    * Get the user that is making the request.
    * @return a Principal object that identifies the user.
    */
   @Override
   public Principal getPrincipal() {
      return req.getPrincipal();
   }

   /**
    * Set the Principal object that identifies the user.
    * @param principal the Principal object.
    */
   @Override
   public void setPrincipal(Principal principal) {
      req.setPrincipal(principal);
   }

   /**
    * Get the IP address of the remote client making the request.
    * @return an IP (Internet Protocol) address.
    */
   @Override
   public String getRemoteAddr() {
      return req.getRemoteAddr();
   }

   /**
    * Get the host name of the remote client making the request.
    * @return an host name.
    */
   @Override
   public String getRemoteHost() {
      return req.getRemoteHost();
   }

   /**
    * Get the host name of the server that received the request.
    * @return server name.
    */
   @Override
   public String getServerName() {
      return req.getServerName();
   }

   /**
    * Get the request protocol.
    * @return protocol such as http, https.
    */
   @Override
   public String getProtocol() {
      return req.getProtocol();
   }

   /**
    * Get the port number on which this request was received.
    * @return the server port.
    */
   @Override
   public int getServerPort() {
      return req.getServerPort();
   }

   /**
    * Get the URI that should be used for all hyperlinks back to the report
    * server.
    * @return the URI of the report server.
    */
   @Override
   public String getLinkURI() {
      return req.getLinkURI();
   }

   /**
    * Get the prefix that is used to identify the forms, parameters, etc. of
    * a report in a page that contains multiple reports.
    * @return the prefix for the report to which this request applies or
    * <code>null</code> if no prefix is applicable.
    */
   @Override
   public String getReportPrefix() {
      return req.getReportPrefix();
   }

   /**
    * Determine if the layout of the report should be overridden and the table
    * layout be used.
    * @return <code>true</code> if the report should be forced to use the table
    * layout; <code>false</code> otherwise.
    */
   @Override
   public boolean isForceTable() {
      return req.isForceTable();
   }

   /**
    * Determine if view type of the report supports frame-based features, such
    * as the new search panel.
    * @return <code>true</code> if the view type supports frames.
    */
   @Override
   public boolean isFrameSupported() {
      return req.isFrameSupported();
   }

   /**
    * Determines if the report toolbar should be made "floating" using CSS-P.
    *
    * @return <code>true</code> if a floating toolbar is supported.
    *
    * @since 8.0
    */
   @Override
   public boolean isFloatToolbarSupported() {
      return req.isFloatToolbarSupported();
   }

   /**
    * Determines if PDF exports should be forced to load in a new window.
    *
    * @return <code>true</code> if PDF exports should be forced to load in a
    *         new window.
    */
   @Override
   public boolean isForcePDFPopup() {
      return req.isForcePDFPopup();
   }

   /**
    * Get the user agent of the client. This is typically the name of the web
    * browser being used.
    * @return the user agent.
    */
   @Override
   public String getUserAgent() {
      return req.getUserAgent();
   }

   /**
    * Get the session ID requested by the client.
    * @return a String specifying the session ID, or <code>null</code> if the
    * request did not specify a session ID.
    */
   @Override
   public String getRequestedSessionId() {
      return req.getRequestedSessionId();
   }

   /**
    * Get an input stream to read the request raw data. This only works for
    * servlet deployment and should not be used in JSP.
    */
   @Override
   public InputStream getInputStream() throws IOException {
      return req.getInputStream();
   }

   /**
    * Get an array containing all of the Cookie objects the client sent with
    * this request. This method returns null if no cookies were sent.
    * @return an array of all the Cookies included with this request,
    * or <code>null</code> if the request has no cookies.
    */
   @Override
   public Cookie[] getCookies() {
      return req.getCookies();
   }

   /**
    * Get the base request.
    */
   public ServiceRequest getRequest() {
      return req;
   }

   private ServiceRequest req;
}
