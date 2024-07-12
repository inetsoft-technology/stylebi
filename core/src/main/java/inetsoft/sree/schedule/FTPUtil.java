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
package inetsoft.sree.schedule;

import com.jcraft.jsch.*;
import inetsoft.sree.SreeEnv;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;

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
      String parseURL = pathInfo.isSFTP() ? url.substring(7) :
         url.startsWith("ftp://") ? url.substring(6) : url;

      // @by stephenwebster, For Bug #6218.
      // The URL coming in may have URL unsafe characters causing the URL to be
      // parsed incorrectly.  For now, just manually parse and encode the user
      // password portion so it parses correctly.
      // A better solution would be to have separate inputs on the GUI side and
      // then passed here as an object, i.e. FTPInfo.
      if(parseURL.contains("@")) {
         String userPasswordString =
            URLEncoder.encode(parseURL.substring(0, parseURL.lastIndexOf("@")), "UTF-8");
         String hostPortion = parseURL.substring(parseURL.lastIndexOf("@"));
         parseURL = "ftp://" + userPasswordString + hostPortion;
      }
      else {
         parseURL = "ftp://" + parseURL;
      }

      URL ftpURL = new URL(parseURL);
      String host = ftpURL.getHost();
      int port = ftpURL.getPort();
      String ftpPath = ftpURL.getPath();
      String userInfo = ftpURL.getUserInfo() == null ? null : URLDecoder.decode(ftpURL.getUserInfo(), "UTF-8");
      String user = pathInfo.getUsername();
      String pass = pathInfo.getPassword();
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
         String known_hosts = SreeEnv.getProperty("ftp.knownhosts.path",
            System.getProperty("user.home")) + File.separator + ".ssh" +
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
