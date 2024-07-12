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
package inetsoft.report;

import java.util.Date;

/**
 * This class holds the report document info attributes.
 * It can be set to generator or formater to support export these
 * information to other file format.
 *
 * @version 8.0, 9/20/2005
 * @author Inetsoft Technology
 */
public class DocumentInfo {
   /**
    * Set the document author.
    */
   public void setAuthor(String author) {
      this.author = author;
   }

   /**
    * Get the document author.
    */
   public String getAuthor() {
      return author;
   }

   /**
    * Set the document creation date.
    */
   public void setCreationDate(Date date) {
      creationDate = date;
   }

   /**
    * Get the document creation date.
    */
   public Date getCreationDate() {
      return creationDate;
   }

   /**
    * Set the document last modification date.
    */
   public void setModDate(Date date) {
      this.modDate = date;
   }

   /**
    * Get the document last modification date.
    */
   public Date getModDate() {
      return modDate;
   }

   /**
    * Set the user who last modified document.
    */
   public void setModUser(String modUser) {
      this.modUser = modUser;
   }

   /**
    * Get the user who last modified document.
    */
   public String getModUser() {
      return modUser;
   }

   /**
    * Set the document title.
    */
   public void setTitle(String title) {
      this.title = title;
   }

   /**
    * Get the document title.
    */
   public String getTitle() {
      return title;
   }

   /**
    * Set the document subject.
    */
   public void setSubject(String subject) {
      this.subject = subject;
   }

   /**
    * Get the document subject.
    */
   public String getSubject() {
      return subject;
   }

   /**
    * Set the document keywords.
    */
   public void setKeywords(String keywords) {
      this.keywords = keywords;
   }

   /**
    * Get the document keywords.
    */
   public String getKeywords() {
      return keywords;
   }

   /**
    * Set the document comments.
    */
   public void setComments(String comments) {
      this.comments = comments;
   }

   /**
    * Get the document comments.
    */
   public String getComments() {
      return this.comments;
   }

   /**
    * Get a string description of the info.
    */
   public String toString() {
      return "DocumentInfo[author: " + author + ", " +
         "creationDate: " + creationDate + ", " +
         "modDate: " + modDate + ", " +
         "title: " + title + ", " +
         "subject: " + subject + ", " +
         "keywords: " + keywords + ", " +
         "comments: " + comments + "]";
   }

   private String author;
   private Date creationDate = new Date();
   private Date modDate;
   private String modUser;
   private String title;
   private String subject;
   private String keywords;
   private String comments;
}

