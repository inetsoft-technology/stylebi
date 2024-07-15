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
package inetsoft.util.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.util.*;
import java.util.regex.Pattern;


// @by: ChrisSpagnoli bug1402502061808 2014-6-18
// Original implementation.

// @by: ChrisSpagnoli feature1355168113753 2014-11-14
// Rewritten to avoid the OutOfMemoryError which was occurring during the
// Export of a large repository.

/**
 * This class implements a mechanism to search a DOM document for certain 
 * phrases, and logs a warning message if those phrases are encountered.
 * It is intended to provide a warning to users who have financial javascript 
 * functions in their pre-existing reports/worksheets/viewsheets.
 *
 * @version 12.0
 * @author InetSoft Technology Corp
 */
public class FinanicalScriptV12 {
   /**
    * This method will search a DOM document for a series of phrases, and 
    * log a warning message if any are encountered.
    *
    * The mechanism used is to recursively traverse the DOM tree, doing a 
    * Pattern match on each node to see if "CALC." exists.  If that pattern
    * is present, then it repeats the recursive traverse and pattern match
    * for each tested CALC financial function.  The DOM tree will be traversed
    * for each pattern tested, but tree traversal terminates for that pattern
    * on a match.
    *
    * While performance is slightly slower than the "transform() and toLower()"
    * approach for a single Viewsheet, it is within 10% of the time of that 
    * approach.  But this approach avoids the heavy memory allocation / 
    * deallocation, so in the scenario of Exporting a large repository of 
    * outdated assets, it is both faster and avoids the OutOfMemoryError which 
    * can occur.
    *
    * @version 12.0
    * @author InetSoft Technology Corp
    */
   public static void grepAndLog(final Document doc, 
                           final String sourceName,
                           final String what) throws Exception
   {
       try {
         Pattern pat = Pattern.compile(Pattern.quote("CALC."),
            Pattern.CASE_INSENSITIVE);

         if(inDomTree(doc, pat)) {
            StringBuilder sb = new StringBuilder();

            for (ListIterator<String> iter = searchPhrases.listIterator(); 
               iter.hasNext(); )
            {
               final String phrase = iter.next();
               pat = Pattern.compile(Pattern.quote(phrase),
                  Pattern.CASE_INSENSITIVE);

               if(inDomTree(doc, pat)) {
                  if(sb.length() > 0) {
                     sb.append(", ");
                  }

                  sb.append(phrase);
                  sb.append("()");
               }
            }

            if(sb.length() > 0) {
               LOG.error(stars);
               LOG.error("The " + what +" " +
                  ((sourceName != null) ? "\""+sourceName+"\"" : "") + 
                  " contains javascript function(s): " + sb.toString() + ".");
                  LOG.error("The rate parameter to the listed functions " +
                     "was changed in 12.0, please review and update this " +
                     what +" for correctness.");
               LOG.error(stars);
            }
         }
      }
      // Since this method only used for warning, just log any Exception.
      catch(Exception e) {
         LOG.warn("Unable to determine if " + what +
            ((sourceName != null) ? "\"" + sourceName + "\"" : "") + 
            " contains one of the javascript functions which were " +
            "changed in 12.0");
      }
   }

   private static boolean inDomTree(final Node node, final Pattern pat) {
      if(node.getNodeType() == Node.TEXT_NODE) {
         final String val = node.getNodeValue();

         if(val != null) {
            if(pat.matcher(val).find()) {
               return true;
            }
         }
      }

      if((node.getNodeType() == Node.DOCUMENT_NODE) || 
         (node.getNodeType() == Node.DOCUMENT_POSITION_DISCONNECTED)) {
         NodeList nodes = node.getChildNodes();

         if((nodes != null) && (nodes.getLength()>0)) {
            for(int i = 0; i < nodes.getLength(); i++) {
               if(inDomTree(nodes.item(i), pat)) {
                  return true;
               }
            }
         }
      }
      return false;
   }

   // The list of the CALC. functions which were changed in 12.0
   // ChrisS bug1404196984255 2014-7-1   add CALC.cumprinc
   private static List<String> searchPhrases = Arrays.asList(
               "CALC.AMORDEGRC",
               "CALC.AMORLINC",
               "CALC.ACCRINT",
               "CALC.ACCRINTM",
               "CALC.CUMIPMT",
               "CALC.CUMPRINC",
               "CALC.DURATION",
               "CALC.EFFECT",
               "CALC.FV",
               "CALC.IPMT",
               "CALC.ISPMT",
               "CALC.MDURATION",
               "CALC.MIRR",
               "CALC.NOMINAL",
               "CALC.NPER",
               "CALC.NPV",
               "CALC.PMT",
               "CALC.PPMT",
               "CALC.PRICE",
               "CALC.PRICEDISC",
               "CALC.PRICEMAT",
               "CALC.PV",
               "CALC.RECEIVED",
               "CALC.TBILLEQ",
               "CALC.TBILLPRICE",
               "CALC.XNPV",
               "CALC.YIELDMAT");

   private static String stars = "************************************************************************************************************************";

   private static final Logger LOG =
      LoggerFactory.getLogger(FinanicalScriptV12.class);

}



// Below this point are various other approaches which were attempted,
// for performance comparison. They "lost".

/* The "winning" results were:
Added to initial load time of SREE-reports: 335ms
Added to load time of first viewsheet (for library bean/parameter sheet/meta report, and worksheets): 296ms
Added to load time of a viewsheet: 7ms
Added to load time of an "infringing" viewsheet: 152ms
*/


// This approach "won" with best performance on a single viewsheet, but 
// caused an OutOfMemoryError when exporting a large repository.

// using transformer, then tolower(): 152ms
/*
import java.io.StringWriter;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

   public static void grepAndLog(final Document doc, 
                           final String sourceName,
                           final String what) throws Exception {

      try {
         // Convert the DOM document into a single string.
         DOMSource domSource = new DOMSource(doc);

         if(!initFlag) {
            TransformerFactory tf = TransformerFactory.newInstance();
            transformer = tf.newTransformer();
            initFlag = true;
         }

         StringWriter writer = new StringWriter();
         StreamResult result = new StreamResult(writer);
         transformer.reset();

         transformer.transform(domSource, result);
         final String textXml = writer.toString().toUpperCase();

         // Check for "CALC." first
         if(textXml.contains("CALC.")) {
            StringBuilder sb = new StringBuilder();

            // Search for each phrase
            for(ListIterator<String> iter = searchPhrases.listIterator(); 
                iter.hasNext();)
            {
               String phrase = iter.next();
               if(textXml.contains(phrase)) {
                  if(sb.length() > 0) {
                     sb.append(", ");
                  }

                  sb.append(phrase);
                  sb.append("()");
               }
            } 

            // If any phrases found, write a "banner" log message.
            if(sb.length() > 0) {
               {  
                  LOG.error(stars);
                  LOG.error("The " + what +" " +
                     ((sourceName != null) ? "\"" + sourceName + "\"" : "") + 
                     " contains javascript function(s): " +
                     sb.toString() + ".");

                  LOG.error("The rate parameter to the listed functions " +
                     "was changed in 12.0, please review and update this " +
                     what +" for correctness.");

                  LOG.error(stars);
               }
            }
         }
      }
      //@temp yanie bug1405406598357 transformer.transform might throw NPE,
      //Since this method only used for warning, so change to catch 
      //exception instead of only catch TransformerException
      catch(Exception e) {
         LOG.warn("Unable to determine if " + what +
            ((sourceName != null) ? "\"" + sourceName + "\"" : "") + 
            " contains one of the javascript functions which were " +
            "changed in 12.0");
      }

   }

   private static boolean initFlag = false;
   private static Transformer transformer = null;
*/


 
// crawling Dom tree, then pattern match (Report11_5): 173, 163 ms
  /* public static void grepAndLog(final Document doc, 
                           final String sourceName,
                           final String what) throws Exception {
      final List<String> searchPhrases = getSearchPhrases();
      StringBuilder sb = new StringBuilder();
      for (ListIterator<String> iter = searchPhrases.listIterator(); iter.hasNext(); ) {
         final String phrase = iter.next();
         Pattern pat = Pattern.compile(Pattern.quote(phrase), Pattern.CASE_INSENSITIVE);
         if(inDomTree(doc, pat)) {
            if(sb.length() > 0) {
               sb.append(", ");
            }
            sb.append(phrase);
            sb.append("()");
         }
      }
      if(sb.length() > 0) {
         LOG.error(stars);
         LOG.error("The " + what +" " +
            ((sourceName != null) ? "\""+sourceName+"\"" : "") + 
            " contains javascript function(s): " + sb.toString() + ".");
         LOG.error("The rate parameter to the listed functions was changed in 12.0, "+
            "please review and update this " + what +" for correctness.");
         LOG.error(stars);
      }    
   }

   private static boolean inDomTree(final Node node, 
                           final Pattern pat) {
      if(node.getNodeType() == Node.TEXT_NODE) {
         final String val = node.getNodeValue();
         if(val != null) {
            if(pat.matcher(val).find()) {
               return true;
            }
         }    
      }      
      if((node.getNodeType() == Node.DOCUMENT_NODE) || 
         (node.getNodeType() == Node.DOCUMENT_POSITION_DISCONNECTED)) {
         NodeList nodes = node.getChildNodes();
         if((nodes != null) && (nodes.getLength()>0)) {
            for(int i = 0; i < nodes.getLength(); i++) {
               if(inDomTree(nodes.item(i), pat)) {
                  return true;
               }   
            }
         }
      }
      return false;
   }  */

// crawling Dom tree, then toLowerCase match (Report11_5): 169, 149 ms  
/*NodeName:#text 1
NodeName:onLoad 1
NodeName:Report 9

NodeName:#text 1
NodeName:dataRef 1
NodeName:dataRef 1
NodeName:ColumnSelection 1
NodeName:normalColumnSelection 1
NodeName:assemblyInfo 1
NodeName:assembly 1
NodeName:oneAssembly 1
NodeName:assemblies 1
NodeName:worksheet 9

NodeName:#text 1
NodeName:loadScript 1
NodeName:viewsheetInfo 1
NodeName:viewsheetInfo 1
NodeName:assembly 9

   public static void grepAndLog(final Document doc, 
                           final String sourceName,
                           final String what) throws Exception {
      final List<String> searchPhrases = getSearchPhrases();
      StringBuilder sb = new StringBuilder();
      for (ListIterator<String> iter = searchPhrases.listIterator(); iter.hasNext(); ) {
         final String phrase = iter.next().toLowerCase();
         if(inDomTree(doc, phrase)) {
            if(sb.length() > 0) {
               sb.append(", ");
            }
            sb.append(phrase);
            sb.append("()");
         }
      }
      if(sb.length() > 0) {
         LOG.error(stars);
         LOG.error("The " + what +" " +
            ((sourceName != null) ? "\""+sourceName+"\"" : "") + 
            " contains javascript function(s): " + sb.toString() + ".");
         LOG.error("The rate parameter to the listed functions was changed in 12.0, "+
            "please review and update this " + what +" for correctness.");
         LOG.error(stars);
      }    
   }

   private static boolean inDomTree(final Node node, 
                           final String phrase) {
      if(node.getNodeType() == Node.TEXT_NODE) {
         final String val = node.getNodeValue();
         if(val != null) {
            if(val.toLowerCase().contains(phrase)) {
System.out.println("val:"+node.getNodeName()+":"+val);
               return true;
            }
         }    
      }      
      if((node.getNodeType() == Node.DOCUMENT_NODE) || 
         (node.getNodeType() == Node.DOCUMENT_POSITION_DISCONNECTED)) {
         NodeList nodes = node.getChildNodes();
         if((nodes != null) && (nodes.getLength()>0)) {
            for(int i = 0; i < nodes.getLength(); i++) {
               if(inDomTree(nodes.item(i), phrase)) {
System.out.println("  NodeName:"+nodes.item(i).getNodeName()+" "+node.getNodeType());
                  return true;
               }   
            }
         }
      }
      return false;
   }  
*/
   
// using transformer, then pattern match: 161 ms, 153ms
/*
   public static void grepAndLog(final Document doc, 
                           final String sourceName,
                           final String what) throws Exception {
      try {
         DOMSource domSource = new DOMSource(doc);
         StringWriter writer = new StringWriter();
         StreamResult result = new StreamResult(writer);
         TransformerFactory tf = TransformerFactory.newInstance();
         Transformer transformer = tf.newTransformer();
         transformer.transform(domSource, result);
         final String textXml = writer.toString();

         Pattern pat = Pattern.compile(Pattern.quote("CALC."), Pattern.CASE_INSENSITIVE);
         if(pat.matcher(textXml).find()) {

            final List<String> searchPhrases = getSearchPhrases();
            StringBuilder sb = new StringBuilder();
            
            for (ListIterator<String> iter = searchPhrases.listIterator(); iter.hasNext(); ) {
               String phrase = iter.next();
               pat = Pattern.compile(Pattern.quote(phrase), Pattern.CASE_INSENSITIVE);
               if(pat.matcher(textXml).find()) {
                  if(sb.length() > 0) {
                     sb.append(", ");
                  }
                  sb.append(phrase);
                  sb.append("()");
               }
            } 
            if(sb.length() > 0) {
//System.out.println(textXml);
               LOG.error(stars);
               LOG.error("The " + what +" " +
                  ((sourceName != null) ? "\""+sourceName+"\"" : "") + 
                  " contains javascript function(s): " + sb.toString() + ".");
               LOG.error("The rate parameter to the listed functions was changed in 12.0, "+
                  "please review and update this " + what +" for correctness.");
               LOG.error(stars);
            }
         }
      }
      catch(TransformerException ex) {
         ex.printStackTrace(); //NOSONAR
      }
   }
*/
 