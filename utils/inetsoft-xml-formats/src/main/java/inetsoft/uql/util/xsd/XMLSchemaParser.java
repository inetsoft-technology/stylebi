/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */
package inetsoft.uql.util.xsd;

import inetsoft.uql.schema.*;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.xerces.impl.dv.XSSimpleType;
import org.apache.xerces.parsers.XMLGrammarPreparser;
import org.apache.xerces.xni.grammars.*;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xerces.xs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * XML Schema parser, generates XTypeNode trees.
 *
 * @author Joshua Kramer, InetSoft Technology Corp.
 */
public class XMLSchemaParser {
   public XMLSchemaParser(InputStream inputStream) {
      stream = inputStream;
   }

   public void parse() throws Exception {
      XSModel xsmdl = preparseGrammar();

      convertXSModelToXTypeNode(xsmdl);
   }

   /**
    * Create an XTypeNode from an XSObjectList (e.g. the contents of a
    * "<sequence>").
    */
   XTypeNode processObjectList(XSObjectList xsObjList)
         throws XSDParserException
   {
      // Need to create a parent to hold these new elements
      UserDefinedType returnVal = new UserDefinedType();

      (returnVal).setUserType(new XTypeNode());

      for(int i = 0; i < xsObjList.getLength(); i++) {
         XSParticle particle = (XSParticle) xsObjList.item(i);
         XSTerm xsTerm = particle.getTerm();

         if(xsTerm instanceof XSElementDeclaration) {
            XTypeNode child = processElement((XSElementDeclaration) xsTerm,
               returnVal);

            child.setMinOccurs(particle.getMinOccurs());
            child.setMaxOccurs(particle.getMaxOccursUnbounded() ?
               XTypeNode.STAR :
               particle.getMaxOccurs());
         }
         else if(xsTerm instanceof XSModelGroup) {
            XTypeNode modelNode = processXSModelGroup((XSModelGroup) xsTerm);

            if(modelNode instanceof UserDefinedType) {
               UserDefinedType userDefModelNode = (UserDefinedType) modelNode;
               int numChildren = userDefModelNode.getChildCount();

               for(int j = 0; j < numChildren; j++) {
                  returnVal.addChild(userDefModelNode.getChild(j));
               }
            }
            else {
               returnVal.addChild(modelNode);
            }
         }
         else {
            // @by larryl, allow parser to continue if type is missing in the
            // element tag
            LOG.error("Wildcards not supported: " + xsTerm);
         }
      }

      return returnVal;
   }

   /**
    * Process an XSModelGroup ("<choice>", "<sequence>", "<all>").
    */
   XTypeNode processXSModelGroup(XSModelGroup modelGroup)
         throws XSDParserException
   {
      // The DTDParser does not distinguish between "choice" and "sequence", so
      // I see no reason to do so here (or to test for "all", for that matter).
      return processObjectList(modelGroup.getParticles());
   }

   /**
    * Process a "<complexType>".
    */
   XTypeNode processComplexType(XSComplexTypeDefinition cmplxType)
         throws XSDParserException
   {
      short contentType = cmplxType.getContentType();
      XTypeNode returnVal = new XTypeNode();

      if(contentType == XSComplexTypeDefinition.CONTENTTYPE_ELEMENT ||
         contentType == XSComplexTypeDefinition.CONTENTTYPE_MIXED)
      {
         XSTerm xsTerm = cmplxType.getParticle().getTerm();

         if(xsTerm instanceof XSElementDeclaration) {
            // I don't know if reaching here is legal, but just in case...
            XSParticle particle = cmplxType.getParticle();

            returnVal = processElement((XSElementDeclaration) xsTerm, null);
            returnVal.setMinOccurs(particle.getMinOccurs());
            returnVal.setMaxOccurs(particle.getMaxOccursUnbounded() ?
               XTypeNode.STAR :
               particle.getMaxOccurs());
         }
         else if(xsTerm instanceof XSModelGroup) {
            returnVal = processXSModelGroup((XSModelGroup) xsTerm);
         }
         else {
            throw new XSDParserException("Wildcards not supported");
         }
      }
      else if(contentType == XSComplexTypeDefinition.CONTENTTYPE_SIMPLE) {
         returnVal = processSimpleType(cmplxType.getSimpleType());
      }
      else { // XSComplexTypeDefinition.CONTENTTYPE_EMPTY
         returnVal = new StringType(cmplxType.getName());
      }

      XSObjectList attributeList = cmplxType.getAttributeUses();

      if(attributeList != null) {
         int attributeListLength = attributeList.getLength();

         if(attributeListLength > 0) {
            for(int i = 0; i < attributeListLength; i++) {
               XSObject item = attributeList.item(i);

               if(item instanceof XSAttributeUse) {
                  XSAttributeDeclaration attrDecl =
                     ((XSAttributeUse) item).getAttrDeclaration();
                  String attName = attrDecl.getName();
                  XSSimpleType attAsSimple =
                     (XSSimpleType) attrDecl.getTypeDefinition();
                  XTypeNode attType = createPrimType(attName, attAsSimple);

                  returnVal.addAttribute(attType);
               }
            }
         }
      }

      return returnVal;
   }

   /**
    * Create an XValueNode from an XSSimpleType.
    */
   XTypeNode createPrimType(String name, XSSimpleType simpleType) {
      switch(simpleType.getPrimitiveKind()) {
      case XSSimpleType.PRIMITIVE_STRING:
         return new StringType(name);
      case XSSimpleType.PRIMITIVE_BOOLEAN:
         return new BooleanType(name);
      case XSSimpleType.PRIMITIVE_DATE:
         return new DateType(name);
      case XSSimpleType.PRIMITIVE_DOUBLE:
         return new DoubleType(name);
      case XSSimpleType.PRIMITIVE_FLOAT:
         return new FloatType(name);
      case XSSimpleType.PRIMITIVE_DECIMAL:
         if(!simpleType.isDefinedFacet(XSSimpleType.FACET_MAXINCLUSIVE)) {
            return new DoubleType(name);
         }

         String maxInclVal =
            simpleType.getLexicalFacetValue(XSSimpleType.FACET_MAXINCLUSIVE);

         if(maxInclVal == null) {
            return new DoubleType(name);
         }
         else if(maxInclVal.equals(byteMaxInclVal)) {
            return new ByteType(name);
         }
         else if(maxInclVal.equals(shortMaxInclVal)) {
            return new ShortType(name);
         }
         else if(maxInclVal.equals(intMaxInclVal)) {
            return new IntegerType(name);
         }
         else if(maxInclVal.equals(longMaxInclVal)) {
            return new IntegerType(name);
         }
         else {
            return new DoubleType(name);
         }
      case XSSimpleType.PRIMITIVE_DATETIME:
         TimeInstantType timeInstantType = new TimeInstantType(name);
         // use the ISO 8601 extended format CCYY-MM-DDThh:mm:ss
         // for TimeInstantTypes discovered in "XSD-based" datasources
         timeInstantType.setFormat("yyyy-MM-dd'T'HH:mm:ss");
         return timeInstantType;
      case XSSimpleType.PRIMITIVE_TIME:
         return new TimeType(name);
      default:
         return new StringType(name);
      }
   }

   /**
    * Create an XValueNode from an XSSimpleType (throw an exception for
    * XSSimpleTypes that are not supported).
    */
   XTypeNode processSimpleType(XSSimpleTypeDefinition simpleTypeDef)
         throws XSDParserException
   {
      if(simpleTypeDef.getVariety() == XSSimpleTypeDefinition.VARIETY_ATOMIC) {
         XSSimpleType simpleType = (XSSimpleType) simpleTypeDef;

         return createPrimType(simpleType.getName(), simpleType);
      }
      else { // list or union
         return new StringType(simpleTypeDef.getName());
      }
   }

   /**
    * Processes the content of an Element after creation of "XTypeNode" object.
    */
   XTypeNode processElementContent(XSElementDeclaration xsElement)
         throws XSDParserException
   {
      XSTypeDefinition xsType = xsElement.getTypeDefinition();
      XTypeNode returnVal;

      if(xsType.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) {
         returnVal = processComplexType((XSComplexTypeDefinition) xsType);
      }
      else { // XSTypeDefinition.SIMPLE_TYPE
         returnVal = processSimpleType((XSSimpleTypeDefinition) xsType);
      }

      returnVal.setName(xsElement.getName());
      return returnVal;
   }

   /**
    * Processes an XSElementDeclaration object obtained via the Xerces API.
    */
   XTypeNode processElement(XSElementDeclaration xsElement, XTypeNode parent)
         throws XSDParserException
   {
      String elementName = xsElement.getName();
      XTypeNode type;

      if(xsElement.getScope() != XSConstants.SCOPE_GLOBAL) {
         if(processing.contains(xsElement)) {
            type = new UserDefinedType(elementName);
            ((UserDefinedType) type).setUserType(parent);

            if(parent != null) {
               parent.addChild(type);
            }
         }
         else {
            processing.add(xsElement);
            type = processElementContent(xsElement);

            if(parent != null) {
               parent.addChild(type);
            }

            processing.remove(xsElement);
         }
      }
      else {
         if(globalElems.get(elementName) == null) {
            // recursive schema
            if(processing.contains(xsElement)) {
               // Handle Recursive XSD's by adding a UserDefinedType
               // as a child (creates a type of cyclic reference)
               type = new UserDefinedType(elementName);
               ((UserDefinedType) type).setUserType(parent);
               parent.addChild(type);
            }
            else {
               processing.add(xsElement);
               type = processElementContent(xsElement);

               if(parent == null) {
                  rootmap.put(elementName, type);
               }
               else {
                  parent.addChild(type);
               }

               globalElems.put(elementName, type);
               processing.remove(xsElement);
            }
         }
         else {
            type = (XTypeNode) globalElems.get(elementName);

            if(parent != null) {
               parent.addChild(type);
            }

            rootmap.remove(elementName);
         }
      }

      if(type instanceof UserDefinedType) {
         UserDefinedType utype = (UserDefinedType) type;
         utype.getUserType().setName(elementName + "_" +
            utype.getUserType().getName());
      }

      return type;
   }

   /**
    * Convert an XSModel to an XTypeNode
    */
   public void convertXSModelToXTypeNode(XSModel xsmdl)
         throws XSDParserException
   {
      XSNamedMap xsNMap = xsmdl.getComponents(XSConstants.ELEMENT_DECLARATION);

      for(int i = 0; i < xsNMap.getLength(); i++) {
         XSElementDeclaration xselm = (XSElementDeclaration) xsNMap.item(i);

         processElement(xselm, null);
      }
   }

   /**
    * Parses the grammar using Xerces, result is an XSModel that can be accessed
    * using the Xerces-provided API.
    */
   public XSModel preparseGrammar() throws Exception {
      XMLGrammarPreparser preparser = new XMLGrammarPreparser();

      preparser.registerPreparser(XMLGrammarDescription.XML_SCHEMA, null);
      final BOMInputStream bomInputStream = new BOMInputStream(stream);

      InputStreamReader inputReader = new InputStreamReader(bomInputStream, "UTF8");
      XMLInputSource inputSource = new XMLInputSource(null, null, null,
                                                      inputReader, "UTF8");
      Grammar grmr = preparser.preparseGrammar(XMLGrammarDescription.XML_SCHEMA,
                                               inputSource);

      if(grmr == null) {
         throw new Exception("Invalid xsd file.");
      }

      return ((XSGrammar) grmr).toXSModel();
   }

   /**
    * Get the root type of the schema.
    */
   public XTypeNode getRoot() {
      Enumeration elements = rootmap.elements();

      if(rootmap.size() >= 1) {
         if(rootmap.size() > 1) {
            LOG.warn("XSD has more than one root!");
         }

         return (XTypeNode) elements.nextElement();
      }

      LOG.warn("XSD does not have a root!");
      Enumeration elems = types.elements();

      return (XTypeNode) elems.nextElement();
   }

   // @by louis, pass the security scanning
   /*public static void main(String[] args) {
      for(int i = 0; i < args.length; i++) {
         try {
            InputStream input = new FileInputStream(args[i]);
            XSDParser parser = new XSDParser(input);

            parser.parse();
            inetsoft.uql.util.XMLUtil.printTree(parser.getRoot(), "");
         } 
         catch(Exception ex) {
            ex.printStackTrace();
         }
      }
   }*/

   private InputStream stream;
   // nodes that is not a child of other types
   private Hashtable rootmap = new Hashtable(); // name -> XTypeNode
   private Hashtable types = new Hashtable(); // name -> XTypeNode
   private Hashtable globalElems = new Hashtable();
   private HashSet processing = new HashSet();
   private final String byteMaxInclVal = "127";
   private final String shortMaxInclVal = "32767";
   private final String longMaxInclVal = "9223372036854775807";
   private final String intMaxInclVal = "2147483647";

   class XSDParserException extends Exception {
      XSDParserException() {
      }

      XSDParserException(String s1) {
         super(s1);
      }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(XMLSchemaParser.class);
}
