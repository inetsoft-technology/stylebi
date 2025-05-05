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
package inetsoft.sree.internal;

import com.github.mustachejava.DefaultMustacheFactory;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.tabular.oauth.AuthorizationClient;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.util.*;
import inetsoft.web.admin.general.model.model.SMTPAuthType;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.mail.*;
import org.joda.time.Instant;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.internet.*;

import javax.naming.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Mailer handles mail delivery.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Mailer {
   /**
    *
    * Constructor.
    */
   public Mailer() {
      String encode = System.getProperty("mail.mime.charset");
      String nencode = SreeEnv.getProperty("mail.mime.charset");

      if((encode == null || encode.isEmpty())  &&
         nencode != null && !nencode.isEmpty())
      {
         System.setProperty("mail.mime.charset", nencode);
      }
   }

   /**
    * Send an email with file attachment.
    */
   public void send(String toaddrs, String from, String subject, String body,
                    File attach) throws MessagingException, NamingException
   {
      send(toaddrs, null, null, from, subject, body, attach);
   }

   /**
    * Send an email with MHTML mime
    */
   public void send(String toaddrs, String ccaddrs, String bccaddrs,
                    String from, String subject, String body, File attach)
         throws MessagingException, NamingException
   {
      send(toaddrs, ccaddrs, bccaddrs, from, subject, body, attach, false);
   }

   /**
    * Send an email with MHTML mime
    */
   public void send(String toaddrs, String ccaddrs, String bccaddrs,
                    String from, String subject, String body, File attach,
                    boolean htmlContent)
         throws MessagingException, NamingException
   {
      send(toaddrs, ccaddrs, bccaddrs, from, subject, body, attach, null,
           false, htmlContent);
   }

   /**
    * Send an email with file attachment which includes "CC" and "BCC" adresses.
    */
   public void send(String toaddrs, String ccaddrs, String bccaddrs,
                    String from, String subject, String body, File attach,
                    Collection<?> images, boolean mhtml, boolean htmlContent)
         throws MessagingException, NamingException
   {
      File[] attachments = null;

      if(attach != null) {
         attachments = new File[] { attach };
      }

      send(toaddrs, ccaddrs, bccaddrs, from, subject, body, attachments,
           images, mhtml, htmlContent);
   }

   /**
    * Sends an email message.
    *
    * @param toaddrs the recipients of the message.
    * @param ccaddrs the copy recipients of the message.
    * @param bccaddrs the blind copy recipients of the message.
    * @param from the sender of the message.
    * @param subject the subject line of the message.
    * @param body the body text of the message.
    * @param attach the file attachments. If the message is in HTML, the first
    *               element in this array should be the HTML message body.
    * @param images the inline images for the HTML message body.
    * @param mhtml <code>true</code> if the message has an HTML body.
    *
    * @throws MessagingException if the message could not be sent.
    * @throws NamingException if an error occured while obtaining the mail
    *                         session from JNDI.
    *
    * @since 8.5
    */
   public void send(String toaddrs, String ccaddrs, String bccaddrs,
                    String from, String subject, String body, File[] attach,
                    Collection<?> images, boolean mhtml, boolean htmlContent)
         throws MessagingException, NamingException
   {
      String jndiUrl = SreeEnv.getProperty("mail.jndi.url");

      if(!jndiUrl.isEmpty()) {
         Context initCtx = new InitialContext();
         Session session = (Session) initCtx.lookup(jndiUrl);

         if(session != null) {
            send(session, toaddrs, ccaddrs, bccaddrs, from, subject, body,
                 attach, images, mhtml, htmlContent);
            return;
         }
      }

      // @by jamshedd reading the smtp servers from the
      // semicolon delimited string
      String smtpHosts = SreeEnv.getProperty("mail.smtp.host");

      if(smtpHosts.isEmpty()) {
      	 throw new MessagingException("smtp.host not correctly configured.");
      }

      String[] hostArr = smtpHosts.split(",");
      boolean sendCompleted = false;
      String prot = SreeEnv.getProperty("mail.ssl").equals("true") ?
         "smtps" : "smtp";

      String[] exArr = new String[hostArr.length];

      for(int i = 0; i < hostArr.length && !sendCompleted; i++) {
         try {
            SMTPAuthType authType = SMTPAuthType.forValue(SreeEnv.getProperty("mail.smtp.auth"));
            Properties prop = new Properties();
            prop.setProperty("mail." + prot + ".host", hostArr[i]);
            prop.setProperty("mail." + prot + ".auth",
                             (authType != SMTPAuthType.NONE) + "");

            if(authType == SMTPAuthType.SASL_XOAUTH2 || authType == SMTPAuthType.GOOGLE_AUTH) {
               prop.setProperty("mail.smtp.auth.mechanisms", "XOAUTH2");
            }

            // @by stephenwebster, for bug1401228327081
            // Add support for TLS. Note: With Gmail, prot needed to be smtp
            // I believe this should be TLS or SSL, but not together.
            if("true".equals(SreeEnv.getProperty("mail.tls"))) {
               prop.setProperty("mail." + prot + ".starttls.enable", "true");
            }
            // @by stephenwebster, for bug1401228327081
            // Ability to customize port used for smtp server.
            if(SreeEnv.getProperty("mail.smtp.port") != null) {
               prop.setProperty("mail." + prot + ".port", SreeEnv.getProperty("mail.smtp.port"));
            }

            if(SreeEnv.getProperty("mail.smtp.ssl.protocols") != null) {
               prop.setProperty(
                  "mail." + prot + ".ssl.protocols",
                  SreeEnv.getProperty("mail.smtp.ssl.protocols"));
            }

            Session session = Session.getInstance(prop, null);
            send(session, toaddrs, ccaddrs, bccaddrs, from, subject, body,
                 attach, images, mhtml, htmlContent);
            sendCompleted = true;
         }
         catch(Exception e) {
            exArr[i] = hostArr[i] + ": " + e.getMessage();
         }
      }

      if(!sendCompleted) {
         throw new MessagingException(Tool.concat(exArr, '\n'));
      }
   }

   /**
    * Sends an email message.
    *
    * @param session the mail session in which the message will be sent.
    * @param toaddrs the recipients of the message.
    * @param ccaddrs the copy recipients of the message.
    * @param bccaddrs the blind copy recipients of the message.
    * @param from the sender of the message.
    * @param subject the subject line of the message.
    * @param body the body text of the message.
    * @param attach the file attachments. If the message is in HTML, the first
    *               element in this array should be the HTML message body.
    * @param images the inline images for the HTML message body.
    * @param mhtml <code>true</code> if the message has an HTML body.
    *
    * @throws MessagingException if the message could not be sent.
    *
    * @since 8.5
    */
   public void send(Session session, String toaddrs, String ccaddrs,
                    String bccaddrs, String from, String subject, String body,
                    File[] attach, Collection<?> images, boolean mhtml,
                    boolean htmlContent)
         throws MessagingException
   {
      try {
         Properties prop = session.getProperties();
         String hostInSession =
            prop.getProperty("mail.smtps.host") != null ? "smtps" :
               prop.getProperty("mail.smtp.host") != null ? "smtp" : null;
         String hostInEnv =
            "true".equals(SreeEnv.getProperty("mail.ssl")) ? "smtps" :
               "smtp";
         String prot = hostInSession != null ? hostInSession : hostInEnv;
         String smtp = prop.getProperty("mail." + prot + ".host");
         toaddrs = checkAddresses(toaddrs);
         ccaddrs = checkAddresses(ccaddrs);
         bccaddrs = checkAddresses(bccaddrs);
         String nencode = SreeEnv.getProperty("mail.mime.charset");
         SMTPAuthType authType = SMTPAuthType.forValue(SreeEnv.getProperty("mail.smtp.auth"));

         if(nencode != null && !nencode.isEmpty()) {
            prop.setProperty("mail.mime.charset", nencode);
         }

         HtmlEmail email = new HtmlEmail();

         if(!StringUtils.isEmpty(toaddrs)) {
            for(String toaddr : toaddrs.split("[;,]", 0)) {
               email.addTo(StringUtils.normalizeSpace(toaddr));
            }
         }

         if(!StringUtils.isEmpty(ccaddrs)) {
            for(String ccaddr : ccaddrs.split("[;,]", 0)) {
               email.addCc(StringUtils.normalizeSpace(ccaddr));
            }
         }

         if(!StringUtils.isEmpty(bccaddrs)) {
            for(String bccaddr : bccaddrs.split("[;,]", 0)) {
               email.addBcc(StringUtils.normalizeSpace(bccaddr));
            }
         }

         if(from == null || from.trim().isEmpty()) {
            from = SreeEnv.getProperty("mail.from.address");
         }

         if(subject == null) {
            subject = "";
         }

         if(body == null) {
            body = "";
         }

         if(from == null || StringUtils.normalizeSpace(from) == null) {
            from = "";
         }

         email.setFrom(from);
         String encoding = nencode != null && !nencode.isEmpty() ?
            nencode : Charset.defaultCharset().displayName();
         email.setCharset(encoding);
         String template = getEmailTemplate();

         if(mhtml && attach != null && attach.length > 0) {
            enforceAttachmentLimit(attach[0]);
            StringBuilder builder = new StringBuilder();

            // Add the user's message before the inline HTML
            if(!body.isEmpty()) {
               builder.append("<p>");
               builder.append(htmlContent ? body.trim() : Tool.encodeHTML(body.trim(), true));
               builder.append("</p>");
               builder.append("<hr align=\"left\" width=\"50%\" />");
            }

            builder.append(getHTMLText(attach[0]));
            String bodyHtml = builder.toString();

            if(template != null) {
               bodyHtml = applyEmailTemplate(bodyHtml, template);
            }

            Map<String, byte[]> embeddedImages = new HashMap<>();
            bodyHtml = extractImages(bodyHtml, embeddedImages);

            if(images != null) {
               for(Object imageObj : images) {
                  String image = Tool.cleanseCRLF((String) imageObj);
                  DataSource fds = new FileDataSource(attach[0].getParent() + "/" + image);
                  String cid = email.embed(fds, image);
                  bodyHtml = bodyHtml.replace("\"cid:" + image + "\"", "\"cid:" + cid + "\"");
               }
            }

            if(!embeddedImages.isEmpty()) {
               addEmbeddedImages(email, embeddedImages);
            }

            email.setHtmlMsg(bodyHtml);
         }
         else if(htmlContent || template != null) {
            String bodyHtml;

            if(htmlContent) {
               if(template == null) {
                  bodyHtml = body;
               }
               else {
                  bodyHtml = applyEmailTemplate(body, template);
               }
            }
            else {
               bodyHtml = applyEmailTemplate(Tool.encodeHTML(body.trim(), true), template);
            }

            Map<String, byte[]> imgs = new HashMap<>();
            bodyHtml = extractImages(bodyHtml, imgs);
            String plainText = getPlainText(bodyHtml);

            if(!Tool.isEmptyString(bodyHtml)) {
               email.setHtmlMsg(bodyHtml);
            }

            if(!Tool.isEmptyString(plainText)) {
               email.setTextMsg(plainText);
            }

            if(!imgs.isEmpty()) {
               addEmbeddedImages(email, imgs);
            }
         }
         else if(!Tool.isEmptyString(body)) {
            email.setTextMsg(body);
         }

         Set<String> attachmentNames = new HashSet<>();

         if(attach != null) {
            for(int i = (mhtml ? 1 : 0); i < attach.length; i++) {
               FileDataSource fds = new FileDataSource(attach[i]);
               String attachmentName = stripRepletID(attach[i].getName());
               email.attach(fds, attachmentName, null);
               attachmentNames.add(attachmentName);
            }
         }

         email.setSubject(StringUtils.normalizeSpace(subject));
         email.setSentDate(new Date());

         // @by billh, property "mail.smtp.auth" is used both in sree
         // and javamail, so do not rename it please...
         boolean auth = authType != SMTPAuthType.NONE;

         try(Transport trans = session.getTransport(prot)) {
            String user = null;
            String pass = null;

            // need authentication?
            if(authType == SMTPAuthType.SMTP_AUTH) {
               user = SreeEnv.getProperty("mail.smtp.user");
               pass = SreeEnv.getPassword("mail.smtp.pass");
            }
            else if(authType == SMTPAuthType.SASL_XOAUTH2 || authType == SMTPAuthType.GOOGLE_AUTH) {
               user = SreeEnv.getProperty("mail.smtp.user");
               pass = getAccessToken(authType);
            }

            String[] smtpHostArr;

            // Inprise sets up a proxy smtp host that doesn't work, make sure
            // the one specified in sree.properties is being used.
            if(smtp == null) {
               String smtpHosts = SreeEnv.getProperty("mail.smtp.host");

               if(smtpHosts.isEmpty()) {
                  throw new MessagingException("smtp.host not correctly configured.");
               }

               smtpHostArr = smtpHosts.split(",");
            }
            else {
               smtpHostArr = new String[]{ smtp };
            }

            boolean sendCompleted = false;
            String[] exArr = new String[smtpHostArr.length];

            for(int i = 0; i < smtpHostArr.length && !sendCompleted; i++) {
               try {
                  trans.connect(smtpHostArr[i], user, pass);
                  sendCompleted = true;
               }
               catch(Exception e) {
                  exArr[i] = smtpHostArr[i] + ": " + e.getMessage();
               }
            }

            if(!sendCompleted) {
               throw new MessagingException(Tool.concat(exArr, '\n'));
            }

            email.setMailSession(session);
            email.buildMimeMessage();
            MimeMessage msg = email.getMessage();
            setAttachmentContentTransferEncoding(msg, attachmentNames, "base64");

            if(auth) {
               msg.saveChanges();
            }

            if(msg.getAllRecipients() != null) {
               trans.sendMessage(msg, msg.getAllRecipients());
            }
         }
         catch(SendFailedException ex) {
            // fix Bug #24023, since SendFailedException message don't have enough
            // information of the failed reason, so get the nest orginal exception.
            if(ex.getCause() != null) {
               throw new MessagingException(ex.getCause().getMessage());
            }

            throw ex;
         }
      }
      catch(EmailException e) {
         throw new MessageException(e.getMessage(), e);
      }
   }

   private static String getAccessToken(SMTPAuthType authType) {
      String expiration = SreeEnv.getProperty("mail.smtp.tokenExpiration");
      String accessToken = SreeEnv.getPassword("mail.smtp.accessToken");

      if(expiration != null && Instant.ofEpochMilli(Long.parseLong(expiration)).isAfter(Instant.now())) {
         return accessToken;
      }

      Tokens tokens;

      try {
         final Set<String> flagsSet = new HashSet<>();

         final String refreshToken = SreeEnv.getPassword("mail.smtp.refreshToken");
         final String clientId = SreeEnv.getProperty("mail.smtp.clientId");
         final String clientSecret = SreeEnv.getPassword("mail.smtp.clientSecret");
         final String tokenUri = authType == SMTPAuthType.GOOGLE_AUTH ?
            "https://oauth2.googleapis.com/token" : SreeEnv.getProperty("mail.smtp.tokenUri");

         tokens = AuthorizationClient.refresh(null, refreshToken, clientId, clientSecret,
                                              tokenUri, flagsSet, false, null);

         SreeEnv.setPassword("mail.smtp.accessToken", tokens.accessToken());
         SreeEnv.setPassword("mail.smtp.refreshToken",tokens.refreshToken());
         SreeEnv.setProperty("mail.smtp.tokenExpiration", tokens.expiration() + "");
         return tokens.accessToken();
      }
      catch(Exception e) {
         LOG.error("Failed to refresh access token", e);
         return null;
      }
   }

   private static void setAttachmentContentTransferEncoding(MimeMessage msg,
                                                            Set<String> attachmentNames,
                                                            String encoding)
      throws MessageException
   {
      try {
         Object content = msg.getContent();

         if(!(content instanceof MimeMultipart)) {
            return;
         }

         MimeMultipart mimeMultipart = (MimeMultipart) content;

         for(int i = 0; i < mimeMultipart.getCount(); i++) {
            MimeBodyPart bodyPart = (MimeBodyPart) mimeMultipart.getBodyPart(i);

            if(bodyPart.getFileName() != null && attachmentNames.contains(bodyPart.getFileName())) {
               bodyPart.setHeader("Content-Transfer-Encoding", encoding);
            }
         }
      }
      catch(Exception e) {
         throw new MessageException(e.getMessage(), e);
      }
   }

   /**
    * Enforces the attachment size limit
    * @param file   the file whose size must be below the limit
    * @throws MessagingException   thrown if the limit is exceeded.
    */
   private void enforceAttachmentLimit(File file) throws MessagingException
   {
      long bytes = file.length();

      String maxAttachStr = SreeEnv.getProperty("mail.attachment.maxBytes");
      long maxAttachBytes = 10240000;  // default attachment limit 10MB.

      try {
         maxAttachBytes = Long.parseLong(maxAttachStr);
      }
      catch(NumberFormatException npe) {
         // do nothing
      }

      if(bytes > maxAttachBytes) {
         throw new MessagingException(
               Catalog.getCatalog().getString("Attachment Exceeds Max Size") +
               " [" + bytes + "] > [" + maxAttachBytes + "]");
      }
   }

   /**
    * Strip off special name for better usability.
    */
   public static String stripRepletID(String fname) {
      if(fname == null) {
         return null;
      }

      int fidx = fname.indexOf("Sxx_");

      if(fidx >= 1) {
         int eidx = fname.indexOf("_Sxx", fidx);

         if(eidx > 1) {
            fname = fname.substring(0, fidx) + fname.substring(eidx + 4);
         }
      }

      return fname;
   }

   /**
    * Make sure the address list is in a valid form.
    */
   private static String checkAddresses(String addrs) {
      if(addrs != null) {
         addrs = Tool.replaceAll(addrs, ",,", ",");
      }

      return Tool.cleanseCRLF(addrs);
   }

   /**
    * Return the html data in the File as text that can be used for MHTML
    */
   private String getHTMLText(File file) {
      if(file == null || !file.exists()) {
         return "";
      }

      InputStream in = null;

      try {
         in = Files.newInputStream(file.toPath());
         return IOUtils.toString(in, StandardCharsets.UTF_8);
      }
      catch (Exception e) {
         return "";
      }
      finally {
         IOUtils.closeQuietly(in);
      }
   }

   private String extractImages(String html, Map<String, byte[]> images) {
      if(html == null) {
         html = "";
      }

      Document document = Jsoup.parse(html);
      document.outputSettings(new Document.OutputSettings().prettyPrint(false));

      for(Element img : document.select("img")) {
         String src = img.attr("src");

         if(src.startsWith("data:image/png;base64,")) {
            byte[] data = Base64.getDecoder().decode(src.substring(22));
            String name = UUID.randomUUID() + ".png";
            images.put(name, data);
            img.attr("src", name);
         }
      }

      return document.outerHtml();
   }

   private void addEmbeddedImages(HtmlEmail email, Map<String, byte[]> images) throws EmailException
   {
      for(Map.Entry<String, byte[]> e : images.entrySet()) {
         DataSource dataSource = new ByteArrayDataSource(e.getValue(), "image/png");
         email.embed(dataSource, e.getKey(), e.getKey());
      };
   }

   private static String getPlainText(String html) {
      if(html == null) {
         return null;
      }

      Document document = Jsoup.parse(html);
      document.outputSettings(new Document.OutputSettings().prettyPrint(false));
      document.select("br").append("\\n");
      document.select("p").prepend("\\n\\n");
      String s = document.html().replaceAll("\\\\n", "\n");
      return Jsoup
         .clean(s, "", Safelist.none(), new Document.OutputSettings().prettyPrint(false))
         .replace('\u00a0', ' ');
   }

   private String getEmailTemplate() {
      DataSpace dataSpace = DataSpace.getDataSpace();

      if(dataSpace.exists(null, "email-template.html")) {
         try(InputStream input = dataSpace.getInputStream(null, "email-template.html")) {
            return IOUtils.toString(input, StandardCharsets.UTF_8);
         }
         catch(IOException e) {
            LoggerFactory.getLogger(getClass()).error("Failed to load the email template", e);
         }
      }

      return null;
   }

   private String applyEmailTemplate(String body, String template) {
      Map<String, Object> scopes = Collections.singletonMap("message", body);
      StringWriter buffer = new StringWriter();
      new DefaultMustacheFactory()
         .compile(new StringReader(template), "email")
         .execute(buffer, scopes);
      return buffer.toString();
   }

   private static final Logger LOG = LoggerFactory.getLogger(Mailer.class);
}
