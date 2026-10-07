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
package inetsoft.sree.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jcraft.jsch.*;
import inetsoft.sree.SreeEnv;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Tool;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for FTP related methods
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class FTPUtil {
   public static void uploadToFTP(String url, File file, boolean isSFTP) throws Throwable {
      ServerPathInfo pathInfo = new ServerPathInfo(url);
      uploadToFTP(url, file, pathInfo, false);
   }

   public static void uploadToFTP(String url, File file, ServerPathInfo pathInfo, boolean append)
      throws Throwable
   {
      Endpoint endpoint = parseEndpoint(url, pathInfo.isSFTP());
      String host = endpoint.host();
      int port = endpoint.port();
      String ftpPath = endpoint.path();
      String userInfo = endpoint.userInfo();
      String user = null;
      String pass = null;

      // the formatted path may contain parameter values, so make sure that a stored credential
      // is only sent to the server that the saved path names
      if((pathInfo.isUseCredential() || !Tool.isEmptyString(pathInfo.getPassword())) &&
         !isSameServer(endpoint, pathInfo))
      {
         throw new Exception("Failed to save file to FTP server: " + host +
            ", the server does not match the saved path");
      }

      if(pathInfo.isUseCredential()) {
         JsonNode credentials = Tool.loadCredentials(pathInfo.getSecretId());

         if(credentials != null) {
            if(credentials.has("username")) {
               user = credentials.get("username").asText();
            }

            if(credentials.has("password")) {
               pass = credentials.get("password").asText();
            }
         }
      }
      else {
         user = pathInfo.getUsername();
         pass = pathInfo.getPassword();
      }

      int index = userInfo != null ? userInfo.indexOf(":") : -1;

      if(index == -1) {
         if(userInfo  != null) {
            user = userInfo;
         }
      }
      else {
         user = userInfo.substring(0, index);
         pass = userInfo.substring(index + 1);
      }

      if(pathInfo.isSFTP()) {
         JSch.setLogger(new SFTPLogger());
         JSch jsch = new JSch();
         Session session = null;
         InputStream in = null;
         String knownHostsBase = SreeEnv.getProperty("ftp.knownhosts.path", null);

         if(knownHostsBase == null) {
            String inetHome = ConfigurationContext.getContext().getHome();
            String inetKnownHosts = inetHome + File.separator + ".ssh" +
               File.separator + "known_hosts";
            knownHostsBase = new File(inetKnownHosts).exists()
               ? inetHome : System.getProperty("user.home");
         }

         String known_hosts = knownHostsBase + File.separator + ".ssh" +
            File.separator + "known_hosts";

         try {
            in = new FileInputStream(file);
            jsch.setKnownHosts(known_hosts);

            if(port != -1) {
               session = jsch.getSession(user, host, port);
            } else {
               session = jsch.getSession(user, host);
            }

            session.setPassword(pass);
            session.connect();

            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            channel.put(in, ftpPath, append ? ChannelSftp.APPEND : ChannelSftp.OVERWRITE);
            session.disconnect();
         }
         catch(JSchException jschex) {
            LOG.error("Failed to upload the file: " + ftpPath
                  + " to the specified FTP url: " + url, jschex);
            throw jschex;
         }
         finally {
            if(session != null && session.isConnected()) {
               session.disconnect();
            }

            IOUtils.closeQuietly(in);
         }
      }
      else {
         FTPClient ftpClient = new FTPClient();

         try {
            if(port != -1) {
               ftpClient.connect(host, port);
            } else {
               ftpClient.connect(host);
            }

            int reply = ftpClient.getReplyCode();

            if(FTPReply.isPositiveCompletion(reply) &&
               ftpClient.login(user, pass))
            {
               ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
               ftpClient.enterLocalPassiveMode();
               InputStream in = new FileInputStream(file);

               // @by stevenkuo bug1418699803218 2014-12-14
               // invalid paths, users, and hosts throw exceptions
               // and displays to users when a scheduled task fails
               if(!ftpClient.storeFile(ftpPath, in)) {
                  String error;
                  reply = ftpClient.getReplyCode();

                  if(reply == FTPReply.FILE_UNAVAILABLE) {
                     error = "Failed to overwrite the file: " + ftpPath;
                  } else {
                     error = "Failed to save file to FTP server: " + host
                        + ", bad file path: " + ftpPath;
                  }

                  in.close();
                  ftpClient.logout();
                  ftpClient.disconnect();
                  throw new Exception(error);
               }

               in.close();
            } else {
               String error = "Failed to login to FTP server: " + host
                  + ", with user: " + user;
               throw new Exception(error);
            }
         } catch(IOException ioex) {
            LOG.error("Failed to upload the file: " + ftpPath
                  + " to the specified FTP url: " + url, ioex);
            throw ioex;
         } finally {
            if(ftpClient.isConnected()) {
               try {
                  ftpClient.logout();
                  ftpClient.disconnect();
               }
               catch(IOException disconnectException) {
                  LOG.warn("An error occurred disconnecting from " +
                        "ftp client." + url, disconnectException);
               }
            }
         }
      }
   }

   /**
    * Parses the server that a saved path points to, in the same way that the file is uploaded.
    */
   public static Endpoint parseEndpoint(ServerPathInfo pathInfo) throws MalformedURLException {
      return parseEndpoint(pathInfo.getPath(), pathInfo.isSFTP());
   }

   /**
    * Parses the server that an FTP or SFTP path points to, in the same way that the file is
    * uploaded.
    */
   public static Endpoint parseEndpoint(String url) throws MalformedURLException {
      return parseEndpoint(url, url.toLowerCase().startsWith("sftp://"));
   }

   private static Endpoint parseEndpoint(String url, boolean sftp) throws MalformedURLException {
      String parseURL = sftp ? url.substring(7) :
         url.startsWith("ftp://") ? url.substring(6) : url;

      // @by stephenwebster, For Bug #6218.
      // The URL coming in may have URL unsafe characters causing the URL to be
      // parsed incorrectly.  For now, just manually parse and encode the user
      // password portion so it parses correctly.
      // A better solution would be to have separate inputs on the GUI side and
      // then passed here as an object, i.e. FTPInfo.
      if(parseURL.contains("@")) {
         String userPasswordString = URLEncoder.encode(
            parseURL.substring(0, parseURL.lastIndexOf("@")), StandardCharsets.UTF_8);
         String hostPortion = parseURL.substring(parseURL.lastIndexOf("@"));
         parseURL = "ftp://" + userPasswordString + hostPortion;
      }
      else {
         parseURL = "ftp://" + parseURL;
      }

      URL ftpURL = new URL(parseURL);
      String userInfo = ftpURL.getUserInfo() == null ? null :
         URLDecoder.decode(ftpURL.getUserInfo(), StandardCharsets.UTF_8);
      return new Endpoint(sftp, ftpURL.getHost(), ftpURL.getPort(), ftpURL.getPath(), userInfo);
   }

   /**
    * Checks if the formatted path points to the server of the saved path. A saved path that
    * can't be parsed, such as one with a parameter in the host or the port, never matches.
    */
   private static boolean isSameServer(Endpoint endpoint, ServerPathInfo pathInfo) {
      try {
         return endpoint.isSameServer(parseEndpoint(pathInfo));
      }
      catch(MalformedURLException ex) {
         LOG.debug("Failed to parse the saved path: " + pathInfo.getPath(), ex);
         return false;
      }
   }

   /**
    * Splits the password out of the user info of an FTP or SFTP path. The text is split with the
    * same steps that {@link #parseEndpoint(String, boolean)} takes before it parses the URL, so
    * the path doesn't need to be a valid URL, and the user, the password and the text after the
    * last '@' are the ones used to log in.
    *
    * @param path the path.
    * @param sftp {@code true} if SFTP is used.
    *
    * @return the path without the password, the user and the password, or {@code null} if the
    *         user info of the path has no password.
    */
   public static PathPassword splitPassword(String path, boolean sftp) {
      // a path too short for the sftp prefix can't be uploaded either
      if(path == null || sftp && path.length() < 7) {
         return null;
      }

      String body = sftp ? path.substring(7) :
         path.startsWith("ftp://") ? path.substring(6) : path;
      String prefix = path.substring(0, path.length() - body.length());
      int at = body.lastIndexOf('@');

      if(at < 0) {
         return null;
      }

      String userInfo = body.substring(0, at);
      int colon = userInfo.indexOf(':');

      // an empty password is left in the path, there is nothing to hide
      if(colon < 0 || colon == userInfo.length() - 1) {
         return null;
      }

      String user = userInfo.substring(0, colon);
      return new PathPassword(prefix + user + body.substring(at), user,
                              userInfo.substring(colon + 1));
   }

   /**
    * A path split by {@link #splitPassword(String, boolean)}.
    *
    * @param path     the path, with the user and without the password.
    * @param user     the user name in the path.
    * @param password the password that was in the path.
    */
   public record PathPassword(String path, String user, String password) {
   }

   /**
    * The server and file that an FTP or SFTP path points to.
    *
    * @param sftp     {@code true} if SFTP is used.
    * @param host     the host name.
    * @param port     the port, or -1 if the default port is used.
    * @param path     the file path on the server.
    * @param userInfo the user name and password in the path, or {@code null} if none.
    */
   public record Endpoint(boolean sftp, String host, int port, String path, String userInfo) {
      /**
       * Determines if another endpoint connects to the same server, using the same protocol, host
       * and port.
       */
      public boolean isSameServer(Endpoint other) {
         return other != null && sftp == other.sftp && !Tool.isEmptyString(host) &&
            host.equalsIgnoreCase(other.host) && getEffectivePort() == other.getEffectivePort();
      }

      private int getEffectivePort() {
         return port != -1 ? port : sftp ? 22 : 21;
      }
   }

   private static class SFTPLogger implements com.jcraft.jsch.Logger {
      @Override
      public boolean isEnabled(int level) {
         return true;
      }

      @Override
      public void log(int level, String message) {
         switch(level) {
         case com.jcraft.jsch.Logger.FATAL:
         case com.jcraft.jsch.Logger.ERROR:
            LOG.error(message);
            break;
         case com.jcraft.jsch.Logger.WARN:
            LOG.warn(message);
            break;
         default:
            LOG.debug(message);
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(FTPUtil.class);
}
