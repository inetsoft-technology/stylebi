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
package inetsoft.util.xml;

import java.io.*;

/**
 * XML pull parser.
 */
public class XMLPParser {
   public static final String NO_NAMESPACE = "";
   public static final int START_DOCUMENT = 0;
   public static final int END_DOCUMENT = 1;
   public static final int START_TAG = 2;
   public static final int END_TAG = 3;
   public static final int TEXT = 4;
   public static final int CDSECT = 5;
   public static final int ENTITY_REF = 6;
   public static final int IGNORABLE_WHITESPACE = 7;
   public static final int PROCESSING_INSTRUCTION = 8;
   public static final int COMMENT = 9;
   public static final int DOCDECL = 10;
   String[] TYPES = {"START_DOCUMENT", "END_DOCUMENT", "START_TAG", "END_TAG",
         "TEXT", "CDSECT", "ENTITY_REF", "IGNORABLE_WHITESPACE",
         "PROCESSING_INSTRUCTION", "COMMENT", "DOCDECL"};

   // ----------------------------------------------------------------------
   // namespace related features

   /**
    * This feature determines whether the parser processes namespaces. As for
    * all features, the default value is false.
    * <p>
    * <strong>NOTE:</strong> The value can not be changed during parsing an must
    * be set before parsing.
    *
    * @see #getFeature
    * @see #setFeature
    */
   String FEATURE_PROCESS_NAMESPACES = "http://xmlpull.org/v1/doc/features.html#process-namespaces";

   /**
    * This feature determines whether namespace attributes are exposed via the
    * attribute access methods. Like all features, the default value is false.
    * This feature cannot be changed during parsing.
    *
    * @see #getFeature
    * @see #setFeature
    */
   String FEATURE_REPORT_NAMESPACE_ATTRIBUTES = "http://xmlpull.org/v1/doc/features.html#report-namespace-prefixes";

   /**
    * This feature determines whether the document declaration is processed. If
    * set to false, the DOCDECL event type is reported by nextToken() and
    * ignored by next().
    *
    * If this featue is activated, then the document declaration must be
    * processed by the parser.
    *
    * <p>
    * <strong>Please note:</strong> If the document type declaration was
    * ignored, entity references may cause exceptions later in the parsing
    * process. The default value of this feature is false. It cannot be changed
    * during parsing.
    *
    * @see #getFeature
    * @see #setFeature
    */
   String FEATURE_PROCESS_DOCDECL = "http://xmlpull.org/v1/doc/features.html#process-docdecl";
   // NOTE: no interning of those strings --> by Java lang spec they MUST be
   // already interned
   protected static final String XML_URI = "http://www.w3.org/XML/1998/namespace";
   protected static final String XMLNS_URI = "http://www.w3.org/2000/xmlns/";
   protected static final String FEATURE_XML_ROUNDTRIP = "http://xmlpull.org/v1/doc/features.html#xml-roundtrip";
   protected static final String FEATURE_NAMES_INTERNED = "http://xmlpull.org/v1/doc/features.html#names-interned";
   protected static final String PROPERTY_XMLDECL_VERSION = "http://xmlpull.org/v1/doc/properties.html#xmldecl-version";
   protected static final String PROPERTY_XMLDECL_STANDALONE = "http://xmlpull.org/v1/doc/properties.html#xmldecl-standalone";
   protected static final String PROPERTY_XMLDECL_CONTENT = "http://xmlpull.org/v1/doc/properties.html#xmldecl-content";
   protected static final String PROPERTY_LOCATION = "http://xmlpull.org/v1/doc/properties.html#location";

   /**
    * Implementation notice: the is instance variable that controls if
    * newString() is interning.
    * <p>
    * <b>NOTE:</b> newStringIntern <b>always</b> returns interned strings and
    * newString MAY return interned String depending on this variable.
    * <p>
    * <b>NOTE:</b> by default in this minimal implementation it is false!
    */
   protected boolean allStringsInterned;

   protected void resetStringCache() {
      // System.out.println("resetStringCache() minimum called");
   }

   protected String newString(char[] cbuf, int off, int len) {
      return new String(cbuf, off, len);
   }

   protected String newStringIntern(char[] cbuf, int off, int len) {
      return (new String(cbuf, off, len)).intern();
   }

   private static final boolean TRACE_SIZING = false;

   // NOTE: features are not resettable and typically defaults to false ...
   protected boolean processNamespaces;
   protected boolean roundtripSupported;

   // global parser state
   protected String location;
   protected int lineNumber;
   protected int columnNumber;

   public long icol;
   public long istart;
   public long iend;

   protected boolean seenRoot;
   protected boolean reachedEnd;
   protected int eventType;
   protected boolean emptyElementTag;
   // element stack
   protected int depth;
   protected char[][] elRawName;
   protected int[] elRawNameEnd;
   protected int[] elRawNameLine;

   protected String[] elName;
   protected String[] elPrefix;
   protected String[] elUri;
   protected int[] elNamespaceCount;

   /**
    * Make sure that we have enough space to keep element stack if passed size.
    * It will always create one additional slot then current depth
    */
   protected void ensureElementsCapacity() {
      final int elStackSize = elName != null ? elName.length : 0;
      if((depth + 1) >= elStackSize) {
         // we add at least one extra slot ...
         final int newSize = (depth >= 7 ? 2 * depth : 8) + 2; // = lucky 7 + 1
         // //25
         if(TRACE_SIZING) {
            System.err.println("TRACE_SIZING elStackSize " + elStackSize
                  + " ==> " + newSize);
         }
         final boolean needsCopying = elStackSize > 0;
         String[] arr = null;
         // reuse arr local variable slot
         arr = new String[newSize];
         if(needsCopying)
            System.arraycopy(elName, 0, arr, 0, elStackSize);
         elName = arr;
         arr = new String[newSize];
         if(needsCopying)
            System.arraycopy(elPrefix, 0, arr, 0, elStackSize);
         elPrefix = arr;
         arr = new String[newSize];
         if(needsCopying)
            System.arraycopy(elUri, 0, arr, 0, elStackSize);
         elUri = arr;

         int[] iarr = new int[newSize];
         if(needsCopying) {
            System.arraycopy(elNamespaceCount, 0, iarr, 0, elStackSize);
         }
         else {
            // special initialization
            iarr[0] = 0;
         }
         elNamespaceCount = iarr;

         // TODO: avoid using element raw name ...
         iarr = new int[newSize];
         if(needsCopying) {
            System.arraycopy(elRawNameEnd, 0, iarr, 0, elStackSize);
         }
         elRawNameEnd = iarr;

         iarr = new int[newSize];
         if(needsCopying) {
            System.arraycopy(elRawNameLine, 0, iarr, 0, elStackSize);
         }
         elRawNameLine = iarr;

         final char[][] carr = new char[newSize][];
         if(needsCopying) {
            System.arraycopy(elRawName, 0, carr, 0, elStackSize);
         }
         elRawName = carr;
      }
   }

   // attribute stack
   protected int attributeCount;
   protected String[] attributeName;
   protected int[] attributeNameHash;
   protected String[] attributePrefix;
   protected String[] attributeUri;
   protected String[] attributeValue;

   /**
    * Make sure that in attributes temporary array is enough space.
    */
   protected void ensureAttributesCapacity(int size) {
      final int attrPosSize = attributeName != null ? attributeName.length : 0;
      if(size >= attrPosSize) {
         final int newSize = size > 7 ? 2 * size : 8; // = lucky 7 + 1 //25
         if(TRACE_SIZING) {
            System.err.println("TRACE_SIZING attrPosSize " + attrPosSize
                  + " ==> " + newSize);
         }
         final boolean needsCopying = attrPosSize > 0;
         String[] arr = null;

         arr = new String[newSize];
         if(needsCopying)
            System.arraycopy(attributeName, 0, arr, 0, attrPosSize);
         attributeName = arr;

         arr = new String[newSize];
         if(needsCopying)
            System.arraycopy(attributePrefix, 0, arr, 0, attrPosSize);
         attributePrefix = arr;

         arr = new String[newSize];
         if(needsCopying)
            System.arraycopy(attributeUri, 0, arr, 0, attrPosSize);
         attributeUri = arr;

         arr = new String[newSize];
         if(needsCopying)
            System.arraycopy(attributeValue, 0, arr, 0, attrPosSize);
         attributeValue = arr;

         if(!allStringsInterned) {
            final int[] iarr = new int[newSize];
            if(needsCopying)
               System.arraycopy(attributeNameHash, 0, iarr, 0, attrPosSize);
            attributeNameHash = iarr;
         }

         arr = null;
         // //assert attrUri.length > size
      }
   }

   // namespace stack
   protected int namespaceEnd;
   protected String[] namespacePrefix;
   protected int[] namespacePrefixHash;
   protected String[] namespaceUri;

   protected void ensureNamespacesCapacity(int size) {
      final int namespaceSize = namespacePrefix != null ? namespacePrefix.length
            : 0;
      if(size >= namespaceSize) {
         final int newSize = size > 7 ? 2 * size : 8; // = lucky 7 + 1 //25
         if(TRACE_SIZING) {
            System.err.println("TRACE_SIZING namespaceSize " + namespaceSize
                  + " ==> " + newSize);
         }
         final String[] newNamespacePrefix = new String[newSize];
         final String[] newNamespaceUri = new String[newSize];
         if(namespacePrefix != null) {
            System.arraycopy(namespacePrefix, 0, newNamespacePrefix, 0,
                  namespaceEnd);
            System.arraycopy(namespaceUri, 0, newNamespaceUri, 0, namespaceEnd);
         }
         namespacePrefix = newNamespacePrefix;
         namespaceUri = newNamespaceUri;

         if(!allStringsInterned) {
            final int[] newNamespacePrefixHash = new int[newSize];
            if(namespacePrefixHash != null) {
               System.arraycopy(namespacePrefixHash, 0, newNamespacePrefixHash,
                     0, namespaceEnd);
            }
            namespacePrefixHash = newNamespacePrefixHash;
         }
      }
   }

   /**
    * simplistic implementation of hash function that has <b>constant</b> time
    * to compute - so it also means diminishing hash quality for long strings
    * but for XML parsing it should be good enough ...
    */
   protected static final int fastHash(char[] ch, int off, int len) {
      if(len == 0)
         return 0;
      // assert len >0
      int hash = ch[off]; // hash at beginning
      hash = (hash << 7) + ch[off + len - 1]; // hash at the end

      if(len > 16)
         hash = (hash << 7) + ch[off + (len / 4)]; // 1/4 from beginning
      if(len > 8)
         hash = (hash << 7) + ch[off + (len / 2)]; // 1/2 of string size ...
      // notice that hash is at most done 3 times <<7 so shifted by 21 bits 8
      // bit value
      // so max result == 29 bits so it is quite just below 31 bits for long
      // (2^32) ...

      return hash;
   }

   // entity replacement stack
   protected int entityEnd;

   protected String[] entityName;
   protected char[][] entityNameBuf;
   protected String[] entityReplacement;
   protected char[][] entityReplacementBuf;

   protected int[] entityNameHash;

   protected void ensureEntityCapacity() {
      final int entitySize = entityReplacementBuf != null ? entityReplacementBuf.length
            : 0;
      if(entityEnd >= entitySize) {
         final int newSize = entityEnd > 7 ? 2 * entityEnd : 8; // = lucky 7 + 1
         // //25
         if(TRACE_SIZING) {
            System.err.println("TRACE_SIZING entitySize " + entitySize
                  + " ==> " + newSize);
         }
         final String[] newEntityName = new String[newSize];
         final char[][] newEntityNameBuf = new char[newSize][];
         final String[] newEntityReplacement = new String[newSize];
         final char[][] newEntityReplacementBuf = new char[newSize][];
         if(entityName != null) {
            System.arraycopy(entityName, 0, newEntityName, 0, entityEnd);
            System.arraycopy(entityNameBuf, 0, newEntityNameBuf, 0, entityEnd);
            System.arraycopy(entityReplacement, 0, newEntityReplacement, 0,
                  entityEnd);
            System.arraycopy(entityReplacementBuf, 0, newEntityReplacementBuf,
                  0, entityEnd);
         }
         entityName = newEntityName;
         entityNameBuf = newEntityNameBuf;
         entityReplacement = newEntityReplacement;
         entityReplacementBuf = newEntityReplacementBuf;

         if(!allStringsInterned) {
            final int[] newEntityNameHash = new int[newSize];
            if(entityNameHash != null) {
               System.arraycopy(entityNameHash, 0, newEntityNameHash, 0,
                     entityEnd);
            }
            entityNameHash = newEntityNameHash;
         }
      }
   }

   // input buffer management
   protected static final int READ_CHUNK_SIZE = 8 * 1024; // max data chars in
   // one read() call
   protected Reader reader;
   protected String inputEncoding;
   protected InputStream inputStream;

   protected int bufLoadFactor = 95; // 99%
   // protected int bufHardLimit; // only matters when expanding

   protected char[] buf = new char[Runtime.getRuntime().freeMemory() > 1000000L ? READ_CHUNK_SIZE
      : 256];
   protected int bufSoftLimit = (bufLoadFactor * buf.length) / 100; // desirable
   // size of
   // buffer
   protected boolean preventBufferCompaction;

   protected int bufAbsoluteStart; // this is buf
   protected int bufStart;
   protected int bufEnd;
   protected int pos;
   protected int posStart;
   protected int posEnd;

   protected char[] pc = new char[Runtime.getRuntime().freeMemory() > 1000000L ? READ_CHUNK_SIZE
      : 64];
   protected int pcStart;
   protected int pcEnd;

   protected boolean usePC;
   protected boolean seenStartTag;
   protected boolean seenEndTag;
   protected boolean pastEndTag;
   protected boolean seenAmpersand;
   protected boolean seenMarkup;
   protected boolean seenDocdecl;

   // transient variable set during each call to next/Token()
   protected boolean tokenize;
   protected String text;
   protected String entityRefName;

   protected String xmlDeclVersion;
   protected Boolean xmlDeclStandalone;
   protected String xmlDeclContent;

   protected void reset() {
      location = null;
      lineNumber = 1;
      columnNumber = 0;
      seenRoot = false;
      reachedEnd = false;
      eventType = START_DOCUMENT;
      emptyElementTag = false;

      depth = 0;

      attributeCount = 0;

      namespaceEnd = 0;

      entityEnd = 0;

      reader = null;
      inputEncoding = null;

      preventBufferCompaction = false;
      bufAbsoluteStart = 0;
      bufEnd = bufStart = 0;
      pos = posStart = posEnd = 0;

      pcEnd = pcStart = 0;

      usePC = false;

      seenStartTag = false;
      seenEndTag = false;
      pastEndTag = false;
      seenAmpersand = false;
      seenMarkup = false;
      seenDocdecl = false;

      xmlDeclVersion = null;
      xmlDeclStandalone = null;
      xmlDeclContent = null;

      resetStringCache();
   }

   public XMLPParser() {
   }

   /**
    * Method setFeature
    *
    * @param name a String
    * @param state a boolean
    *
    *
    */
   public void setFeature(String name, boolean state) {
      if(name == null)
         throw new IllegalArgumentException("feature name should not be null");
      if(FEATURE_PROCESS_NAMESPACES.equals(name)) {
         if(eventType != START_DOCUMENT)
            throw new RuntimeException(
                  "namespace processing feature can only be changed before parsing",
                  null);
         processNamespaces = state;
      }
      else if(FEATURE_NAMES_INTERNED.equals(name)) {
         if(state != false) {
            throw new RuntimeException(
                  "interning names in this implementation is not supported");
         }
      }
      else if(FEATURE_PROCESS_DOCDECL.equals(name)) {
         if(state != false) {
            throw new RuntimeException("processing DOCDECL is not supported");
         }
      }
      else if(FEATURE_XML_ROUNDTRIP.equals(name)) {
         roundtripSupported = state;
      }
      else {
         throw new RuntimeException("unsupported feature " + name);
      }
   }

   /** Unknown properties are <strong>always</strong> returned as false */
   public boolean getFeature(String name) {
      if(name == null)
         throw new IllegalArgumentException("feature name should not be null");
      if(FEATURE_PROCESS_NAMESPACES.equals(name)) {
         return processNamespaces;
      }
      else if(FEATURE_NAMES_INTERNED.equals(name)) {
         return false;
      }
      else if(FEATURE_PROCESS_DOCDECL.equals(name)) {
         return false;
      }
      else if(FEATURE_XML_ROUNDTRIP.equals(name)) {
         return roundtripSupported;
      }
      return false;
   }

   public void setProperty(String name, Object value) {
      if(PROPERTY_LOCATION.equals(name)) {
         location = (String) value;
      }
      else {
         throw new RuntimeException("unsupported property: '" + name + "'");
      }
   }

   public Object getProperty(String name) {
      if(name == null)
         throw new IllegalArgumentException("property name should not be null");
      if(PROPERTY_XMLDECL_VERSION.equals(name)) {
         return xmlDeclVersion;
      }
      else if(PROPERTY_XMLDECL_STANDALONE.equals(name)) {
         return xmlDeclStandalone;
      }
      else if(PROPERTY_XMLDECL_CONTENT.equals(name)) {
         return xmlDeclContent;
      }
      else if(PROPERTY_LOCATION.equals(name)) {
         return location;
      }
      return null;
   }

   public void setInput(Reader in) {
      reset();
      reader = in;
   }

   public void setInput(java.io.InputStream inputStream, String inputEncoding) {
      if(inputStream == null) {
         throw new IllegalArgumentException("input stream can not be null");
      }
      this.inputStream = inputStream;
      Reader reader;

      try {
         if(inputEncoding != null) {
            reader = new InputStreamReader(inputStream, inputEncoding);
         }
         else {
            // by default use UTF-8 (InputStreamReader(inputStream)) would use
            // OS default ...
            reader = new InputStreamReader(inputStream, "UTF-8");
         }
      }
      catch(UnsupportedEncodingException une) {
         throw new RuntimeException("could not create reader for encoding "
               + inputEncoding + " : " + une, une);
      }

      setInput(reader);
      this.inputEncoding = inputEncoding;
   }

   public String getInputEncoding() {
      return inputEncoding;
   }

   public void defineEntityReplacementText(String entityName, String replacementText) {
      ensureEntityCapacity();

      // this is to make sure that if interning works we will take advantage of
      // it ...
      this.entityName[entityEnd] = newString(entityName.toCharArray(), 0,
            entityName.length());
      entityNameBuf[entityEnd] = entityName.toCharArray();

      entityReplacement[entityEnd] = replacementText;
      entityReplacementBuf[entityEnd] = replacementText.toCharArray();
      if(!allStringsInterned) {
         entityNameHash[entityEnd] = fastHash(entityNameBuf[entityEnd], 0,
               entityNameBuf[entityEnd].length);
      }
      ++entityEnd;
      // TODO disallow < or & in entity replacement text (or ]]>???)
      // TOOD keepEntityNormalizedForAttributeValue cached as well ...
   }

   public int getNamespaceCount(int depth) {
      if(processNamespaces == false || depth == 0) {
         return 0;
      }

      if(depth < 0 || depth > this.depth)
         throw new IllegalArgumentException("allowed namespace depth 0.."
               + this.depth + " not " + depth);
      return elNamespaceCount[depth];
   }

   public String getNamespacePrefix(int pos) {
      if(pos < namespaceEnd) {
         return namespacePrefix[pos];
      }
      else {
         throw new RuntimeException("position " + pos
               + " exceeded number of available namespaces " + namespaceEnd);
      }
   }

   public String getNamespaceUri(int pos) {
      if(pos < namespaceEnd) {
         return namespaceUri[pos];
      }
      else {
         throw new RuntimeException("position " + pos
               + " exceeded number of available namespaces " + namespaceEnd);
      }
   }

   public String getNamespace(String prefix) {
      if(prefix != null) {
         for(int i = namespaceEnd - 1; i >= 0; i--) {
            if(prefix.equals(namespacePrefix[i])) {
               return namespaceUri[i];
            }
         }
         if("xml".equals(prefix)) {
            return XML_URI;
         }
         else if("xmlns".equals(prefix)) {
            return XMLNS_URI;
         }
      }
      else {
         for(int i = namespaceEnd - 1; i >= 0; i--) {
            if(namespacePrefix[i] == null) { // "") { //null ) { //TODO check
               // FIXME Alek
               return namespaceUri[i];
            }
         }

      }
      return null;
   }

   public int getDepth() {
      return depth;
   }

   private static int findFragment(int bufMinPos, char[] b, int start, int end) {
      if(start < bufMinPos) {
         start = bufMinPos;
         if(start > end)
            start = end;
         return start;
      }
      if(end - start > 65) {
         start = end - 10; // try to find good location
      }
      int i = start + 1;
      while(--i > bufMinPos) {
         if((end - i) > 65)
            break;
         final char c = b[i];
         if(c == '<' && (start - i) > 10)
            break;
      }
      return i;
   }

   /**
    * Return string describing current position of parsers as text 'STATE [seen
    * %s...] @line:column'.
    */
   public String getPositionDescription() {
      String fragment = null;
      if(posStart <= pos) {
         final int start = findFragment(0, buf, posStart, pos);

         if(start < pos) {
            fragment = new String(buf, start, pos - start);
         }
         if(bufAbsoluteStart > 0 || start > 0)
            fragment = "..." + fragment;
      }

      return " " + TYPES[eventType]
            + (fragment != null ? " seen " + printable(fragment) + "..." : "")
            + " " + (location != null ? location : "") + "@" + getLineNumber()
            + ":" + getColumnNumber();
   }

   public int getLineNumber() {
      return lineNumber;
   }

   public int getColumnNumber() {
      return columnNumber;
   }

   public boolean isWhitespace() {
      if(eventType == TEXT || eventType == CDSECT) {
         if(usePC) {
            for(int i = pcStart; i < pcEnd; i++) {
               if(!isS(pc[i]))
                  return false;
            }
            return true;
         }
         else {
            for(int i = posStart; i < posEnd; i++) {
               if(!isS(buf[i]))
                  return false;
            }
            return true;
         }
      }
      else if(eventType == IGNORABLE_WHITESPACE) {
         return true;
      }
      throw new RuntimeException(
            "no content available to check for white spaces");
   }

   public String getText() {
      if(eventType == START_DOCUMENT || eventType == END_DOCUMENT) {
         return null;
      }
      else if(eventType == ENTITY_REF) {
         return text;
      }
      if(text == null) {
         if(!usePC || eventType == START_TAG || eventType == END_TAG) {
            text = new String(buf, posStart, posEnd - posStart);
         }
         else {
            text = new String(pc, pcStart, pcEnd - pcStart);
         }
      }
      return text;
   }

   public char[] getTextCharacters(int[] holderForStartAndLength) {
      if(eventType == TEXT) {
         if(usePC) {
            holderForStartAndLength[0] = pcStart;
            holderForStartAndLength[1] = pcEnd - pcStart;
            return pc;
         }
         else {
            holderForStartAndLength[0] = posStart;
            holderForStartAndLength[1] = posEnd - posStart;
            return buf;

         }
      }
      else if(eventType == START_TAG || eventType == END_TAG
            || eventType == CDSECT || eventType == COMMENT
            || eventType == ENTITY_REF || eventType == PROCESSING_INSTRUCTION
            || eventType == IGNORABLE_WHITESPACE || eventType == DOCDECL) {
         holderForStartAndLength[0] = posStart;
         holderForStartAndLength[1] = posEnd - posStart;
         return buf;
      }
      else if(eventType == START_DOCUMENT || eventType == END_DOCUMENT) {
         holderForStartAndLength[0] = holderForStartAndLength[1] = -1;
         return null;
      }
      else {
         throw new IllegalArgumentException("unknown text eventType: "
               + eventType);
      }
   }

   public String getNamespace() {
      if(eventType == START_TAG) {
         return processNamespaces ? elUri[depth] : NO_NAMESPACE;
      }
      else if(eventType == END_TAG) {
         return processNamespaces ? elUri[depth] : NO_NAMESPACE;
      }
      return null;
   }

   public String getName() {
      if(eventType == START_TAG) {
         return elName[depth];
      }
      else if(eventType == END_TAG) {
         return elName[depth];
      }
      else if(eventType == ENTITY_REF) {
         if(entityRefName == null) {
            entityRefName = newString(buf, posStart, posEnd - posStart);
         }
         return entityRefName;
      }
      else {
         return null;
      }
   }

   public String getPrefix() {
      if(eventType == START_TAG) {
         return elPrefix[depth];
      }
      else if(eventType == END_TAG) {
         return elPrefix[depth];
      }
      return null;
   }

   public boolean isEmptyElementTag() {
      if(eventType != START_TAG)
         throw new RuntimeException(
               "parser must be on START_TAG to check for empty element",
               null);
      return emptyElementTag;
   }

   public int getAttributeCount() {
      if(eventType != START_TAG)
         return -1;
      return attributeCount;
   }

   public String getAttributeNamespace(int index) {
      if(eventType != START_TAG)
         throw new IndexOutOfBoundsException(
               "only START_TAG can have attributes");
      if(processNamespaces == false)
         return NO_NAMESPACE;
      if(index < 0 || index >= attributeCount)
         throw new IndexOutOfBoundsException("attribute position must be 0.."
               + (attributeCount - 1) + " and not " + index);
      return attributeUri[index];
   }

   public String getAttributeValue(String name) {
      for(int i = 0; i < attributeName.length; i++) {
         if(name.equals(attributeName[i])) {
            return attributeValue[i];
         }
      }

      return null;
   }

   public String getAttributeName(int index) {
      if(eventType != START_TAG)
         throw new IndexOutOfBoundsException(
               "only START_TAG can have attributes");
      if(index < 0 || index >= attributeCount)
         throw new IndexOutOfBoundsException("attribute position must be 0.."
               + (attributeCount - 1) + " and not " + index);
      return attributeName[index];
   }

   public String getAttributePrefix(int index) {
      if(eventType != START_TAG)
         throw new IndexOutOfBoundsException(
               "only START_TAG can have attributes");
      if(processNamespaces == false)
         return null;
      if(index < 0 || index >= attributeCount)
         throw new IndexOutOfBoundsException("attribute position must be 0.."
               + (attributeCount - 1) + " and not " + index);
      return attributePrefix[index];
   }

   public String getAttributeType(int index) {
      if(eventType != START_TAG)
         throw new IndexOutOfBoundsException(
               "only START_TAG can have attributes");
      if(index < 0 || index >= attributeCount)
         throw new IndexOutOfBoundsException("attribute position must be 0.."
               + (attributeCount - 1) + " and not " + index);
      return "CDATA";
   }

   public boolean isAttributeDefault(int index) {
      if(eventType != START_TAG)
         throw new IndexOutOfBoundsException(
               "only START_TAG can have attributes");
      if(index < 0 || index >= attributeCount)
         throw new IndexOutOfBoundsException("attribute position must be 0.."
               + (attributeCount - 1) + " and not " + index);
      return false;
   }

   public String getAttributeValue(int index) {
      if(eventType != START_TAG)
         throw new IndexOutOfBoundsException(
               "only START_TAG can have attributes");
      if(index < 0 || index >= attributeCount)
         throw new IndexOutOfBoundsException("attribute position must be 0.."
               + (attributeCount - 1) + " and not " + index);
      return attributeValue[index];
   }

   public String getAttributeValue(String namespace, String name) {
      if(eventType != START_TAG)
         throw new IndexOutOfBoundsException(
               "only START_TAG can have attributes" + getPositionDescription());
      if(name == null) {
         throw new IllegalArgumentException("attribute name can not be null");
      }
      // TODO make check if namespace is interned!!! etc. for names!!!
      if(processNamespaces) {
         if(namespace == null) {
            namespace = "";
         }

         for(int i = 0; i < attributeCount; ++i) {
            if((namespace == attributeUri[i] || namespace
                  .equals(attributeUri[i]))
                  // (namespace != null && namespace.equals(attributeUri[ i ]))
                  // taking advantage of String.intern()
                  && name.equals(attributeName[i])) {
               return attributeValue[i];
            }
         }
      }
      else {
         if(namespace != null && namespace.length() == 0) {
            namespace = null;
         }
         if(namespace != null)
            throw new IllegalArgumentException(
                  "when namespaces processing is disabled attribute namespace must be null");
         for(int i = 0; i < attributeCount; ++i) {
            if(name.equals(attributeName[i])) {
               return attributeValue[i];
            }
         }
      }
      return null;
   }

   public int getEventType() {
      return eventType;
   }

   public void require(int type, String namespace, String name) throws IOException {
      if(processNamespaces == false && namespace != null) {
         throw new RuntimeException(
               "processing namespaces must be enabled on parser (or factory)"
                     + " to have possible namespaces declared on elements"
                     + (" (position:" + getPositionDescription()) + ")");
      }
      if(type != getEventType()
            || (namespace != null && !namespace.equals(getNamespace()))
            || (name != null && !name.equals(getName()))) {
         throw new RuntimeException(
               "expected event "
                     + TYPES[type]
                     + (name != null ? " with name '" + name + "'" : "")
                     + (namespace != null && name != null ? " and" : "")
                     + (namespace != null ? " with namespace '" + namespace
                           + "'" : "")
                     + " but got"
                     + (type != getEventType() ? " " + TYPES[getEventType()]
                           : "")
                     + (name != null && getName() != null
                           && !name.equals(getName()) ? " name '" + getName()
                           + "'" : "")
                     + (namespace != null && name != null && getName() != null
                           && !name.equals(getName()) && getNamespace() != null
                           && !namespace.equals(getNamespace()) ? " and" : "")
                     + (namespace != null && getNamespace() != null
                           && !namespace.equals(getNamespace()) ? " namespace '"
                           + getNamespace() + "'"
                           : "") + (" (position:" + getPositionDescription())
                     + ")");
      }
   }

   /**
    * Skip sub tree that is currently parser positioned on. <br>
    * NOTE: parser must be on START_TAG and when function returns parser will be
    * positioned on corresponding END_TAG
    */
   public void skipSubTree() throws IOException {
      require(START_TAG, null, null);
      int level = 1;
      while(level > 0) {
         int eventType = next();
         if(eventType == END_TAG) {
            --level;
         }
         else if(eventType == START_TAG) {
            ++level;
         }
      }
   }

   public String nextText() throws IOException {
      if(getEventType() != START_TAG) {
         throw new RuntimeException(
            "parser must be on START_TAG to read next text", null);
      }
      int eventType = next();
      if(eventType == TEXT) {
         final String result = getText();
         eventType = next();
         if(eventType != END_TAG) {
            throw new RuntimeException(
               "TEXT must be immediately followed by END_TAG and not "
                        + TYPES[getEventType()], null);
         }
         return result;
      }
      else if(eventType == END_TAG) {
         return "";
      }
      else {
         throw new RuntimeException(
            "parser must be on START_TAG or TEXT to read text", null);
      }
   }

   public int nextTag() throws IOException {
      next();
      if(eventType == TEXT && isWhitespace()) { // skip whitespace
         next();
      }
      if(eventType != START_TAG && eventType != END_TAG) {
         throw new RuntimeException("expected START_TAG or END_TAG not "
               + TYPES[getEventType()], null);
      }
      return eventType;
   }

   public int next() throws IOException {
      tokenize = false;
      return nextImpl();
   }

   public int nextToken() throws IOException {
      tokenize = true;
      return nextImpl();
   }

   protected int nextImpl() throws IOException {
      text = null;
      pcEnd = pcStart = 0;
      usePC = false;
      bufStart = posEnd;
      if(pastEndTag) {
         pastEndTag = false;
         --depth;
         namespaceEnd = elNamespaceCount[depth]; // less namespaces available
      }
      if(emptyElementTag) {
         emptyElementTag = false;
         pastEndTag = true;
         return eventType = END_TAG;
      }

      // [1] document ::= prolog element Misc*
      if(depth > 0) {

         if(seenStartTag) {
            seenStartTag = false;
            return eventType = parseStartTag();
         }
         if(seenEndTag) {
            seenEndTag = false;
            return eventType = parseEndTag();
         }

         // ASSUMPTION: we are _on_ first character of content or markup!!!!
         // [43] content ::= CharData? ((element | Reference | CDSect | PI |
         // Comment) CharData?)*
         char ch;
         if(seenMarkup) { // we have read ahead ...
            seenMarkup = false;
            ch = '<';
         }
         else if(seenAmpersand) {
            seenAmpersand = false;
            ch = '&';
         }
         else {
            ch = more();
         }
         posStart = pos - 1; // VERY IMPORTANT: this is correct start of
         // event!!!

         // when true there is some potential event TEXT to return - keep
         // gathering
         boolean hadCharData = false;

         // when true TEXT data is not continual (like <![CDATA[text]]>) and
         // requires PC merging
         boolean needsMerging = false;

         MAIN_LOOP: while(true) {
            // work on MARKUP
            if(ch == '<') {
               if(hadCharData) {
                  if(tokenize) {
                     seenMarkup = true;
                     return eventType = TEXT;
                  }
               }
               ch = more();
               if(ch == '/') {
                  if(!tokenize && hadCharData) {
                     seenEndTag = true;
                     return eventType = TEXT;
                  }
                  return eventType = parseEndTag();
               }
               else if(ch == '!') {
                  ch = more();
                  if(ch == '-') {
                     parseComment();
                     if(tokenize)
                        return eventType = COMMENT;
                     if(!usePC && hadCharData) {
                        needsMerging = true;
                     }
                     else {
                        posStart = pos; // completely ignore comment
                     }
                  }
                  else if(ch == '[') {
                     // must remember previous posStart/End as it merges with
                     // content of CDATA
                     parseCDSect(hadCharData);

                     if(tokenize)
                        return eventType = CDSECT;
                     final int cdStart = posStart;
                     final int cdEnd = posEnd;
                     final int cdLen = cdEnd - cdStart;

                     if(cdLen > 0) { // was there anything inside CDATA section?
                        hadCharData = true;
                        if(!usePC) {
                           needsMerging = true;
                        }
                     }
                  }
                  else {
                     throw new RuntimeException(
                           "unexpected character in markup " + printable(ch),
                           null);
                  }
               }
               else if(ch == '?') {
                  parsePI();
                  if(tokenize)
                     return eventType = PROCESSING_INSTRUCTION;
                  if(!usePC && hadCharData) {
                     needsMerging = true;
                  }
                  else {
                     posStart = pos; // completely ignore PI
                  }

               }
               else if(isNameStartChar(ch)) {
                  if(!tokenize && hadCharData) {
                     seenStartTag = true;

                     return eventType = TEXT;
                  }
                  return eventType = parseStartTag();
               }
               else {
                  throw new RuntimeException(
                        "unexpected character in markup " + printable(ch),
                        null);
               }
               // do content compaction if it makes sense!!!!

            }
            else if(ch == '&') {
               // work on ENTITTY

               if(tokenize && hadCharData) {
                  seenAmpersand = true;
                  return eventType = TEXT;
               }
               final int oldStart = posStart + bufAbsoluteStart;
               final int oldEnd = posEnd + bufAbsoluteStart;
               final char[] resolvedEntity = parseEntityRef();
               if(tokenize)
                  return eventType = ENTITY_REF;
               // check if replacement text can be resolved !!!
               if(resolvedEntity == null) {
                  if(entityRefName == null) {
                     entityRefName = newString(buf, posStart, posEnd - posStart);
                  }
                  throw new RuntimeException(
                     "could not resolve entity named '"
                              + printable(entityRefName) + "'", null);
               }

               posStart = oldStart - bufAbsoluteStart;
               posEnd = oldEnd - bufAbsoluteStart;
               if(!usePC) {
                  if(hadCharData) {
                     joinPC(); // posEnd is already set correctly!!!
                     needsMerging = false;
                  }
                  else {
                     usePC = true;
                     pcStart = pcEnd = 0;
                  }
               }

               // write into PC replacement text - do merge for replacement
               // text!!!!
               for(int i = 0; i < resolvedEntity.length; i++) {
                  if(pcEnd >= pc.length)
                     ensurePC(pcEnd);
                  pc[pcEnd++] = resolvedEntity[i];

               }
               hadCharData = true;
            }
            else {

               if(needsMerging) {
                  joinPC(); // posEnd is already set correctly!!!
                  needsMerging = false;
               }

               // no MARKUP not ENTITIES so work on character data ...

               // [14] CharData ::= [^<&]* - ([^<&]* ']]>' [^<&]*)

               hadCharData = true;

               boolean normalizedCR = false;
               final boolean normalizeInput = tokenize == false
                     || roundtripSupported == false;
               // use loop locality here!!!!
               boolean seenBracket = false;
               boolean seenBracketBracket = false;
               do {

                  // check that ]]> does not show in
                  if(ch == ']') {
                     if(seenBracket) {
                        seenBracketBracket = true;
                     }
                     else {
                        seenBracket = true;
                     }
                  }
                  else if(seenBracketBracket && ch == '>') {
                     throw new RuntimeException(
                           "characters ]]> are not allowed in content",
                           null);
                  }
                  else {
                     if(seenBracket) {
                        seenBracketBracket = seenBracket = false;
                     }
                  }
                  if(normalizeInput) {
                     // deal with normalization issues ...
                     if(ch == '\r') {
                        normalizedCR = true;
                        posEnd = pos - 1;
                        // posEnd is already is set
                        if(!usePC) {
                           if(posEnd > posStart) {
                              joinPC();
                           }
                           else {
                              usePC = true;
                              pcStart = pcEnd = 0;
                           }
                        }

                        if(pcEnd >= pc.length)
                           ensurePC(pcEnd);
                        pc[pcEnd++] = '\n';
                     }
                     else if(ch == '\n') {
                        if(!normalizedCR && usePC) {
                           if(pcEnd >= pc.length)
                              ensurePC(pcEnd);
                           pc[pcEnd++] = '\n';
                        }
                        normalizedCR = false;
                     }
                     else {
                        if(usePC) {
                           if(pcEnd >= pc.length)
                              ensurePC(pcEnd);
                           pc[pcEnd++] = ch;
                        }
                        normalizedCR = false;
                     }
                  }

                  ch = more();
               }
               while(ch != '<' && ch != '&');
               posEnd = pos - 1;
               continue MAIN_LOOP; // skip ch = more() from below - we are
               // alreayd ahead ...
            }
            ch = more();
         }
      }
      else {
         if(seenRoot) {
            return parseEpilog();
         }
         else {
            return parseProlog();
         }
      }
   }

   protected int parseProlog() throws IOException {
      // [2] prolog: ::= XMLDecl? Misc* (doctypedecl Misc*)? and look for [39]
      // element

      char ch;
      if(seenMarkup) {
         ch = buf[pos - 1];
      }
      else {
         ch = more();
      }

      if(eventType == START_DOCUMENT) {
         // bootstrap parsing with getting first character input!
         // deal with BOM
         // detect BOM and drop it (Unicode int Order Mark)
         if(ch == '\uFFFE') {
            throw new RuntimeException(
               "first character in input was UNICODE noncharacter (0xFFFE)"
                        + "- input requires int swapping", null);
         }
         if(ch == '\uFEFF') {
            // skipping UNICODE int Order Mark (so called BOM)
            ch = more();
         }
      }
      seenMarkup = false;
      boolean gotS = false;
      posStart = pos - 1;
      final boolean normalizeIgnorableWS = tokenize == true
            && roundtripSupported == false;
      boolean normalizedCR = false;
      while(true) {
         // deal with Misc
         // [27] Misc ::= Comment | PI | S
         // deal with docdecl --> mark it!
         // else parseStartTag seen <[^/]
         if(ch == '<') {
            if(gotS && tokenize) {
               posEnd = pos - 1;
               seenMarkup = true;
               return eventType = IGNORABLE_WHITESPACE;
            }
            ch = more();
            if(ch == '?') {
               // check if it is 'xml'
               // deal with XMLDecl
               if(parsePI()) { // make sure to skip XMLDecl
                  if(tokenize) {
                     return eventType = PROCESSING_INSTRUCTION;
                  }
               }
               else {
                  // skip over - continue tokenizing
                  posStart = pos;
                  gotS = false;
               }

            }
            else if(ch == '!') {
               ch = more();
               if(ch == 'D') {
                  if(seenDocdecl) {
                     throw new RuntimeException(
                           "only one docdecl allowed in XML document",
                           null);
                  }
                  seenDocdecl = true;
                  parseDocdecl();
                  if(tokenize)
                     return eventType = DOCDECL;
               }
               else if(ch == '-') {
                  parseComment();
                  if(tokenize)
                     return eventType = COMMENT;
               }
               else {
                  throw new RuntimeException("unexpected markup <!"
                        + printable(ch), null);
               }
            }
            else if(ch == '/') {
               throw new RuntimeException("expected start tag name and not "
                     + printable(ch), null);
            }
            else if(isNameStartChar(ch)) {
               seenRoot = true;
               return parseStartTag();
            }
            else {
               throw new RuntimeException("expected start tag name and not "
                     + printable(ch), null);
            }
         }
         else if(isS(ch)) {
            gotS = true;
            if(normalizeIgnorableWS) {
               if(ch == '\r') {
                  normalizedCR = true;

                  // posEnd is already is set
                  if(!usePC) {
                     posEnd = pos - 1;
                     if(posEnd > posStart) {
                        joinPC();
                     }
                     else {
                        usePC = true;
                        pcStart = pcEnd = 0;
                     }
                  }

                  if(pcEnd >= pc.length)
                     ensurePC(pcEnd);
                  pc[pcEnd++] = '\n';
               }
               else if(ch == '\n') {
                  if(!normalizedCR && usePC) {
                     if(pcEnd >= pc.length)
                        ensurePC(pcEnd);
                     pc[pcEnd++] = '\n';
                  }
                  normalizedCR = false;
               }
               else {
                  if(usePC) {
                     if(pcEnd >= pc.length)
                        ensurePC(pcEnd);
                     pc[pcEnd++] = ch;
                  }
                  normalizedCR = false;
               }
            }
         }
         else {
            throw new RuntimeException(
               "only whitespace content allowed before start tag and not "
                        + printable(ch), null);
         }
         ch = more();
      }
   }

   protected int parseEpilog() throws IOException {
      if(eventType == END_DOCUMENT) {
         throw new RuntimeException("already reached end of XML input",
                                       null);
      }
      if(reachedEnd) {
         return eventType = END_DOCUMENT;
      }
      boolean gotS = false;
      final boolean normalizeIgnorableWS = tokenize == true
            && roundtripSupported == false;
      boolean normalizedCR = false;
      try {
         // epilog: Misc*
         char ch;
         if(seenMarkup) {
            ch = buf[pos - 1];
         }
         else {
            ch = more();
         }
         seenMarkup = false;
         posStart = pos - 1;
         if(!reachedEnd) {
            while(true) {
               // deal with Misc
               // [27] Misc ::= Comment | PI | S
               if(ch == '<') {
                  if(gotS && tokenize) {
                     posEnd = pos - 1;
                     seenMarkup = true;
                     return eventType = IGNORABLE_WHITESPACE;
                  }
                  ch = more();
                  if(reachedEnd) {
                     break;
                  }
                  if(ch == '?') {
                     // check if it is 'xml'
                     // deal with XMLDecl
                     parsePI();
                     if(tokenize)
                        return eventType = PROCESSING_INSTRUCTION;

                  }
                  else if(ch == '!') {
                     ch = more();
                     if(reachedEnd) {
                        break;
                     }
                     if(ch == 'D') {
                        parseDocdecl(); // FIXME
                        if(tokenize)
                           return eventType = DOCDECL;
                     }
                     else if(ch == '-') {
                        parseComment();
                        if(tokenize)
                           return eventType = COMMENT;
                     }
                     else {
                        throw new RuntimeException("unexpected markup <!"
                              + printable(ch), null);
                     }
                  }
                  else if(ch == '/') {
                     throw new RuntimeException(
                        "end tag not allowed in epilog but got "
                                 + printable(ch), null);
                  }
                  else if(isNameStartChar(ch)) {
                     throw new RuntimeException(
                        "start tag not allowed in epilog but got "
                                 + printable(ch), null);
                  }
                  else {
                     throw new RuntimeException(
                        "in epilog expected ignorable content and not "
                                 + printable(ch), null);
                  }
               }
               else if(isS(ch)) {
                  gotS = true;
                  if(normalizeIgnorableWS) {
                     if(ch == '\r') {
                        normalizedCR = true;

                        // posEnd is alreadys set
                        if(!usePC) {
                           posEnd = pos - 1;
                           if(posEnd > posStart) {
                              joinPC();
                           }
                           else {
                              usePC = true;
                              pcStart = pcEnd = 0;
                           }
                        }

                        if(pcEnd >= pc.length)
                           ensurePC(pcEnd);
                        pc[pcEnd++] = '\n';
                     }
                     else if(ch == '\n') {
                        if(!normalizedCR && usePC) {
                           if(pcEnd >= pc.length)
                              ensurePC(pcEnd);
                           pc[pcEnd++] = '\n';
                        }
                        normalizedCR = false;
                     }
                     else {
                        if(usePC) {
                           if(pcEnd >= pc.length)
                              ensurePC(pcEnd);
                           pc[pcEnd++] = ch;
                        }
                        normalizedCR = false;
                     }
                  }
               }
               else {
                  throw new RuntimeException(
                     "in epilog non whitespace content is not allowed but got "
                              + printable(ch), null);
               }
               ch = more();
               if(reachedEnd) {
                  break;
               }

            }
         }
      }
      catch(EOFException ex) {
         reachedEnd = true;
      }
      if(reachedEnd) {
         if(tokenize && gotS) {
            posEnd = pos; // well - this is LAST available character pos
            return eventType = IGNORABLE_WHITESPACE;
         }
         return eventType = END_DOCUMENT;
      }
      else {
         throw new RuntimeException("internal error in parseEpilog");
      }
   }

   public int parseEndTag() throws IOException {
      // ASSUMPTION ch is past "</"
      // [42] ETag ::= '</' Name S? '>'
      char ch = more();
      if(!isNameStartChar(ch)) {
         throw new RuntimeException("expected name start and not "
               + printable(ch), null);
      }
      posStart = pos - 3;
      final int nameStart = pos - 1 + bufAbsoluteStart;
      do {
         ch = more();
      }
      while(isNameChar(ch));

      // now we go one level down -- do checks
      // --depth; //FIXME

      int off = nameStart - bufAbsoluteStart;
      final int len = (pos - 1) - off;
      final char[] cbuf = elRawName[depth];
      if(elRawNameEnd[depth] != len) {
         // construct strings for exception
         final String startname = new String(cbuf, 0, elRawNameEnd[depth]);
         final String endname = new String(buf, off, len);
         throw new RuntimeException("end tag name </" + endname
               + "> must match start tag name <" + startname + ">"
               + " from line " + elRawNameLine[depth], null);
      }
      for(int i = 0; i < len; i++) {
         if(buf[off++] != cbuf[i]) {
            // construct strings for exception
            final String startname = new String(cbuf, 0, len);
            final String endname = new String(buf, off - i - 1, len);
            throw new RuntimeException("end tag name </" + endname
                  + "> must be the same as start tag <" + startname + ">"
                  + " from line " + elRawNameLine[depth], null);
         }
      }

      while(isS(ch)) {
         ch = more();
      } // skip additional white spaces
      if(ch != '>') {
         throw new RuntimeException("expected > to finish end tag not "
               + printable(ch) + " from line " + elRawNameLine[depth],
                                       null);
      }

      // namespaceEnd = elNamespaceCount[ depth ]; //FIXME

      posEnd = pos;
      iend = icol;
      pastEndTag = true;
      return eventType = END_TAG;
   }

   public int parseStartTag() throws IOException {
      // ASSUMPTION ch is past <T
      // [40] STag ::= '<' Name (S Attribute)* S? '>'
      // [44] EmptyElemTag ::= '<' Name (S Attribute)* S? '/>'
      ++depth; // FIXME

      posStart = pos - 2;
      istart = icol - 2;
      emptyElementTag = false;
      attributeCount = 0;
      // retrieve name
      final int nameStart = pos - 1 + bufAbsoluteStart;
      int colonPos = -1;
      char ch = buf[pos - 1];
      if(ch == ':' && processNamespaces)
         throw new RuntimeException(
               "when namespaces processing enabled colon can not be at element name start",
               null);
      while(true) {
         ch = more();
         if(!isNameChar(ch))
            break;
         if(ch == ':' && processNamespaces) {
            if(colonPos != -1)
               throw new RuntimeException(
                     "only one colon is allowed in name of element when namespaces are enabled",
                     null);
            colonPos = pos - 1 + bufAbsoluteStart;
         }
      }

      // retrieve name
      ensureElementsCapacity();

      // TODO check for efficient interning and then use elRawNameInterned!!!!

      int elLen = (pos - 1) - (nameStart - bufAbsoluteStart);
      if(elRawName[depth] == null || elRawName[depth].length < elLen) {
         elRawName[depth] = new char[2 * elLen];
      }
      System.arraycopy(buf, nameStart - bufAbsoluteStart, elRawName[depth], 0,
            elLen);
      elRawNameEnd[depth] = elLen;
      elRawNameLine[depth] = lineNumber;

      String name = null;

      // work on prefixes and namespace URI
      String prefix = null;
      if(processNamespaces) {
         if(colonPos != -1) {
            prefix = elPrefix[depth] = newString(buf, nameStart
                  - bufAbsoluteStart, colonPos - nameStart);
            name = elName[depth] = newString(buf, colonPos + 1
                  - bufAbsoluteStart,
                  pos - 2 - (colonPos - bufAbsoluteStart));
         }
         else {
            prefix = elPrefix[depth] = null;
            name = elName[depth] = newString(buf, nameStart - bufAbsoluteStart,
                  elLen);
         }
      }
      else {
         name = elName[depth] = newString(buf, nameStart - bufAbsoluteStart,
               elLen);
      }
      
      while(true) {
         while(isS(ch)) {
            ch = more();
         } // skip additional white spaces

         if(ch == '>') {
            break;
         }
         else if(ch == '/') {
            if(emptyElementTag)
               throw new RuntimeException("repeated / in tag declaration",
                                             null);
            emptyElementTag = true;
            ch = more();
            if(ch != '>')
               throw new RuntimeException("expected > to end empty tag not "
                     + printable(ch), null);
            break;
         }
         else if(isNameStartChar(ch)) {
            ch = parseAttribute();
            ch = more();
            continue;
         }
         else {
            throw new RuntimeException("start tag unexpected character "
                  + printable(ch), null);
         }
         // ch = more(); // skip space
      }

      // now when namespaces were declared we can resolve them
      if(processNamespaces) {
         String uri = getNamespace(prefix);
         if(uri == null) {
            if(prefix == null) { // no prefix and no uri => use default
               // namespace
               uri = NO_NAMESPACE;
            }
            else {
               throw new RuntimeException(
                  "could not determine namespace bound to element prefix "
                           + prefix, null);
            }

         }
         elUri[depth] = uri;

         // resolve attribute namespaces
         for(int i = 0; i < attributeCount; i++) {
            final String attrPrefix = attributePrefix[i];
            if(attrPrefix != null) {
               final String attrUri = getNamespace(attrPrefix);
               if(attrUri == null) {
                  throw new RuntimeException(
                     "could not determine namespace bound to attribute prefix "
                              + attrPrefix, null);

               }
               attributeUri[i] = attrUri;
            }
            else {
               attributeUri[i] = NO_NAMESPACE;
            }
         }

         // TODO
         // [ WFC: Unique Att Spec ]
         // check attribute uniqueness constraint for attributes that has
         // namespace!!!

         for(int i = 1; i < attributeCount; i++) {
            for(int j = 0; j < i; j++) {
               if(attributeUri[j] == attributeUri[i]
                     && (allStringsInterned
                           && attributeName[j].equals(attributeName[i]) || (!allStringsInterned
                           && attributeNameHash[j] == attributeNameHash[i] && attributeName[j]
                           .equals(attributeName[i])))

               ) {
                  // prepare data for nice error message?
                  String attr1 = attributeName[j];
                  if(attributeUri[j] != null)
                     attr1 = attributeUri[j] + ":" + attr1;
                  String attr2 = attributeName[i];
                  if(attributeUri[i] != null)
                     attr2 = attributeUri[i] + ":" + attr2;
                  throw new RuntimeException("duplicated attributes "
                        + attr1 + " and " + attr2, null);
               }
            }
         }

      }
      else { // ! processNamespaces

         // [ WFC: Unique Att Spec ]
         // check raw attribute uniqueness constraint!!!
         for(int i = 1; i < attributeCount; i++) {
            for(int j = 0; j < i; j++) {
               if((allStringsInterned
                     && attributeName[j].equals(attributeName[i]) || (!allStringsInterned
                     && attributeNameHash[j] == attributeNameHash[i] && attributeName[j]
                     .equals(attributeName[i])))

               ) {
                  // prepare data for nice error message?
                  final String attr1 = attributeName[j];
                  final String attr2 = attributeName[i];
                  throw new RuntimeException("duplicated attributes "
                        + attr1 + " and " + attr2, null);
               }
            }
         }
      }

      elNamespaceCount[depth] = namespaceEnd;
      posEnd = pos;
      return eventType = START_TAG;
   }

   protected char parseAttribute() throws IOException {
      // parse attribute
      // [41] Attribute ::= Name Eq AttValue
      // [WFC: No External Entity References]
      // [WFC: No < in Attribute Values]
      final int prevPosStart = posStart + bufAbsoluteStart;
      final int nameStart = pos - 1 + bufAbsoluteStart;
      int colonPos = -1;
      char ch = buf[pos - 1];
      if(ch == ':' && processNamespaces)
         throw new RuntimeException(
               "when namespaces processing enabled colon can not be at attribute name start",
               null);

      boolean startsWithXmlns = processNamespaces && ch == 'x';
      int xmlnsPos = 0;

      ch = more();
      while(isNameChar(ch)) {
         if(processNamespaces) {
            if(startsWithXmlns && xmlnsPos < 5) {
               ++xmlnsPos;
               if(xmlnsPos == 1) {
                  if(ch != 'm')
                     startsWithXmlns = false;
               }
               else if(xmlnsPos == 2) {
                  if(ch != 'l')
                     startsWithXmlns = false;
               }
               else if(xmlnsPos == 3) {
                  if(ch != 'n')
                     startsWithXmlns = false;
               }
               else if(xmlnsPos == 4) {
                  if(ch != 's')
                     startsWithXmlns = false;
               }
               else if(xmlnsPos == 5) {
                  if(ch != ':')
                     throw new RuntimeException(
                        "after xmlns in attribute name must be colon"
                                 + "when namespaces are enabled", null);
               }
            }
            if(ch == ':') {
               if(colonPos != -1)
                  throw new RuntimeException(
                     "only one colon is allowed in attribute name"
                              + " when namespaces are enabled", null);
               colonPos = pos - 1 + bufAbsoluteStart;
            }
         }
         ch = more();
      }

      ensureAttributesCapacity(attributeCount);

      // --- start processing attributes
      String name = null;
      String prefix = null;
      // work on prefixes and namespace URI
      if(processNamespaces) {
         if(xmlnsPos < 4)
            startsWithXmlns = false;
         if(startsWithXmlns) {
            if(colonPos != -1) {
               final int nameLen = pos - 2 - (colonPos - bufAbsoluteStart);
               if(nameLen == 0) {
                  throw new RuntimeException(
                     "namespace prefix is required after xmlns: "
                              + " when namespaces are enabled", null);
               }
               name = // attributeName[ attributeCount ] =
               newString(buf, colonPos - bufAbsoluteStart + 1, nameLen);
               // pos - 1 - (colonPos + 1 - bufAbsoluteStart)
            }
         }
         else {
            if(colonPos != -1) {
               int prefixLen = colonPos - nameStart;
               prefix = attributePrefix[attributeCount] = newString(buf,
                     nameStart - bufAbsoluteStart, prefixLen);

               int nameLen = pos - 2 - (colonPos - bufAbsoluteStart);
               name = attributeName[attributeCount] = newString(buf, colonPos
                     - bufAbsoluteStart + 1, nameLen);
            }
            else {
               prefix = attributePrefix[attributeCount] = null;
               name = attributeName[attributeCount] = newString(buf, nameStart
                     - bufAbsoluteStart, pos - 1
                     - (nameStart - bufAbsoluteStart));
            }
            if(!allStringsInterned) {
               attributeNameHash[attributeCount] = name.hashCode();
            }
         }

      }
      else {
         // retrieve name
         name = attributeName[attributeCount] = newString(buf, nameStart
               - bufAbsoluteStart, pos - 1 - (nameStart - bufAbsoluteStart));

         if(!allStringsInterned) {
            attributeNameHash[attributeCount] = name.hashCode();
         }
      }

      // [25] Eq ::= S? '=' S?
      while(isS(ch)) {
         ch = more();
      } // skip additional spaces
      if(ch != '=')
         throw new RuntimeException("expected = after attribute name",
                                       null);
      ch = more();
      while(isS(ch)) {
         ch = more();
      } // skip additional spaces

      // [10] AttValue ::= '"' ([^<&"] | Reference)* '"'
      // | "'" ([^<&'] | Reference)* "'"
      final char delimit = ch;
      if(delimit != '"' && delimit != '\'')
         throw new RuntimeException(
            "attribute value must start with quotation or apostrophe not "
                     + printable(delimit), null);
      // parse until delimit or < and resolve Reference
      // [67] Reference ::= EntityRef | CharRef

      boolean normalizedCR = false;
      usePC = false;
      pcStart = pcEnd;
      posStart = pos;

      while(true) {
         ch = more();
         if(ch == delimit) {
            break;
         }
         if(ch == '<') {
            throw new RuntimeException(
                  "markup not allowed inside attribute value - illegal < ",
                  null);
         }
         if(ch == '&') {
            // extractEntityRef
            posEnd = pos - 1;
            if(!usePC) {
               final boolean hadCharData = posEnd > posStart;
               if(hadCharData) {
                  // posEnd is already set correctly!!!
                  joinPC();
               }
               else {
                  usePC = true;
                  pcStart = pcEnd = 0;
               }
            }

            final char[] resolvedEntity = parseEntityRef();
            // check if replacement text can be resolved !!!
            if(resolvedEntity == null) {
               if(entityRefName == null) {
                  entityRefName = newString(buf, posStart, posEnd - posStart);
               }
               throw new RuntimeException("could not resolve entity named '"
                     + printable(entityRefName) + "'", null);
            }
            // write into PC replacement text - do merge for replacement
            // text!!!!
            for(int i = 0; i < resolvedEntity.length; i++) {
               if(pcEnd >= pc.length)
                  ensurePC(pcEnd);
               pc[pcEnd++] = resolvedEntity[i];
            }
         }
         else if(ch == '\t' || ch == '\n' || ch == '\r') {
            // do attribute value normalization
            // as described in http://www.w3.org/TR/REC-xml#AVNormalize
            // TODO add test for it form spec ...
            // handle EOL normalization ...
            if(!usePC) {
               posEnd = pos - 1;
               if(posEnd > posStart) {
                  joinPC();
               }
               else {
                  usePC = true;
                  pcEnd = pcStart = 0;
               }
            }

            if(pcEnd >= pc.length)
               ensurePC(pcEnd);
            if(ch != '\n' || !normalizedCR) {
               pc[pcEnd++] = ' ';
            }

         }
         else {
            if(usePC) {
               if(pcEnd >= pc.length)
                  ensurePC(pcEnd);
               pc[pcEnd++] = ch;
            }
         }
         normalizedCR = ch == '\r';
      }

      if(processNamespaces && startsWithXmlns) {
         String ns = null;
         if(!usePC) {
            ns = newStringIntern(buf, posStart, pos - 1 - posStart);
         }
         else {
            ns = newStringIntern(pc, pcStart, pcEnd - pcStart);
         }
         ensureNamespacesCapacity(namespaceEnd);
         int prefixHash = -1;
         if(colonPos != -1) {
            if(ns.length() == 0) {
               throw new RuntimeException(
                     "non-default namespace can not be declared to be empty string",
                     null);
            }
            // declare new namespace
            namespacePrefix[namespaceEnd] = name;
            if(!allStringsInterned) {
               prefixHash = namespacePrefixHash[namespaceEnd] = name.hashCode();
            }
         }
         else {
            // declare new default namespace ...
            namespacePrefix[namespaceEnd] = null; // ""; //null; //TODO check
            // FIXME Alek
            if(!allStringsInterned) {
               prefixHash = namespacePrefixHash[namespaceEnd] = -1;
            }
         }
         namespaceUri[namespaceEnd] = ns;

         // detect duplicate namespace declarations!!!
         final int startNs = elNamespaceCount[depth - 1];
         for(int i = namespaceEnd - 1; i >= startNs; --i) {
            if(((allStringsInterned || name == null) && namespacePrefix[i] == name)
                  || (!allStringsInterned && name != null
                        && namespacePrefixHash[i] == prefixHash && name
                        .equals(namespacePrefix[i]))) {
               final String s = name == null ? "default" : "'" + name + "'";
               throw new RuntimeException(
                     "duplicated namespace declaration for " + s + " prefix",
                     null);
            }
         }

         ++namespaceEnd;

      }
      else {
         if(!usePC) {
            attributeValue[attributeCount] = new String(buf, posStart, pos - 1
                  - posStart);
         }
         else {
            attributeValue[attributeCount] = new String(pc, pcStart, pcEnd
                  - pcStart);
         }
         ++attributeCount;
      }
      posStart = prevPosStart - bufAbsoluteStart;
      return ch;
   }

   protected char[] charRefOneCharBuf = new char[1];

   protected char[] parseEntityRef() throws IOException {
      // entity reference
      // http://www.w3.org/TR/2000/REC-xml-20001006#NT-Reference
      // [67] Reference ::= EntityRef | CharRef

      // ASSUMPTION just after &
      entityRefName = null;
      posStart = pos;
      char ch = more();
      if(ch == '#') {
         // parse character reference
         char charRef = 0;
         ch = more();
         if(ch == 'x') {
            // encoded in hex
            while(true) {
               ch = more();
               if(ch >= '0' && ch <= '9') {
                  charRef = (char) (charRef * 16 + (ch - '0'));
               }
               else if(ch >= 'a' && ch <= 'f') {
                  charRef = (char) (charRef * 16 + (ch - ('a' - 10)));
               }
               else if(ch >= 'A' && ch <= 'F') {
                  charRef = (char) (charRef * 16 + (ch - ('A' - 10)));
               }
               else if(ch == ';') {
                  break;
               }
               else {
                  throw new RuntimeException(
                     "character reference (with hex value) may not contain "
                              + printable(ch), null);
               }
            }
         }
         else {
            // encoded in decimal
            while(true) {
               if(ch >= '0' && ch <= '9') {
                  charRef = (char) (charRef * 10 + (ch - '0'));
               }
               else if(ch == ';') {
                  break;
               }
               else {
                  throw new RuntimeException(
                     "character reference (with decimal value) may not contain "
                              + printable(ch), null);
               }
               ch = more();
            }
         }
         posEnd = pos - 1;
         charRefOneCharBuf[0] = charRef;
         if(tokenize) {
            text = newString(charRefOneCharBuf, 0, 1);
         }
         return charRefOneCharBuf;
      }
      else {
         // [68] EntityRef ::= '&' Name ';'
         // scan name until ;
         if(!isNameStartChar(ch)) {
            return getChar(ch, "start with");
         }

         while(true) {
            ch = more();

            if(ch == ';') {
               break;
            }

            if(!isNameChar(ch)) {
               return getChar(ch, "contain");
            }
         }

         posEnd = pos - 1;
         // determine what name maps to
         final int len = posEnd - posStart;
         if(len == 2 && buf[posStart] == 'l' && buf[posStart + 1] == 't') {
            if(tokenize) {
               text = "<";
            }
            charRefOneCharBuf[0] = '<';
            return charRefOneCharBuf;
         }
         else if(len == 3 && buf[posStart] == 'a' && buf[posStart + 1] == 'm'
               && buf[posStart + 2] == 'p') {
            if(tokenize) {
               text = "&";
            }
            charRefOneCharBuf[0] = '&';
            return charRefOneCharBuf;
         }
         else if(len == 2 && buf[posStart] == 'g' && buf[posStart + 1] == 't') {
            if(tokenize) {
               text = ">";
            }
            charRefOneCharBuf[0] = '>';
            return charRefOneCharBuf;
         }
         else if(len == 4 && buf[posStart] == 'a' && buf[posStart + 1] == 'p'
               && buf[posStart + 2] == 'o' && buf[posStart + 3] == 's') {
            if(tokenize) {
               text = "'";
            }
            charRefOneCharBuf[0] = '\'';
            return charRefOneCharBuf;
         }
         else if(len == 4 && buf[posStart] == 'q' && buf[posStart + 1] == 'u'
               && buf[posStart + 2] == 'o' && buf[posStart + 3] == 't') {
            if(tokenize) {
               text = "\"";
            }
            charRefOneCharBuf[0] = '"';
            return charRefOneCharBuf;
         }
         else {
            final char[] result = lookuEntityReplacement(len);
            if(result != null) {
               return result;
            }
         }
         if(tokenize)
            text = null;
         return null;
      }
   }

   protected char[] lookuEntityReplacement(int entitNameLen) {
      if(!allStringsInterned) {
         final int hash = fastHash(buf, posStart, posEnd - posStart);
         LOOP: for(int i = entityEnd - 1; i >= 0; --i) {
            if(hash == entityNameHash[i]
                  && entitNameLen == entityNameBuf[i].length) {
               final char[] entityBuf = entityNameBuf[i];
               for(int j = 0; j < entitNameLen; j++) {
                  if(buf[posStart + j] != entityBuf[j])
                     continue LOOP;
               }
               if(tokenize)
                  text = entityReplacement[i];
               return entityReplacementBuf[i];
            }
         }
      }
      else {
         entityRefName = newString(buf, posStart, posEnd - posStart);
         for(int i = entityEnd - 1; i >= 0; --i) {
            // take advantage that interning for newStirng is enforced
            if(entityRefName == entityName[i]) {
               if(tokenize)
                  text = entityReplacement[i];
               return entityReplacementBuf[i];
            }
         }
      }
      return null;
   }

   protected void parseComment() throws IOException {
      // implements XML 1.0 Section 2.5 Comments

      // ASSUMPTION: seen <!-
      char ch = more();
      if(ch != '-')
         throw new RuntimeException("expected <!-- for comment start",
                                       null);
      if(tokenize)
         posStart = pos;

      final int curLine = lineNumber;
      final int curColumn = columnNumber;
      try {
         final boolean normalizeIgnorableWS = tokenize == true
               && roundtripSupported == false;
         boolean normalizedCR = false;

         boolean seenDash = false;
         boolean seenDashDash = false;
         while(true) {
            // scan until it hits -->
            ch = more();
            if(seenDashDash && ch != '>') {
               throw new RuntimeException(
                  "in comment after two dashes (--) next character must be >"
                           + " not " + printable(ch), null);
            }
            if(ch == '-') {
               if(!seenDash) {
                  seenDash = true;
               }
               else {
                  seenDashDash = true;
                  seenDash = false;
               }
            }
            else if(ch == '>') {
               if(seenDashDash) {
                  break; // found end sequence!!!!
               }
               else {
                  seenDashDash = false;
               }
               seenDash = false;
            }
            else {
               seenDash = false;
            }
            if(normalizeIgnorableWS) {
               if(ch == '\r') {
                  normalizedCR = true;

                  // posEnd is already set
                  if(!usePC) {
                     posEnd = pos - 1;
                     if(posEnd > posStart) {
                        joinPC();
                     }
                     else {
                        usePC = true;
                        pcStart = pcEnd = 0;
                     }
                  }

                  if(pcEnd >= pc.length)
                     ensurePC(pcEnd);
                  pc[pcEnd++] = '\n';
               }
               else if(ch == '\n') {
                  if(!normalizedCR && usePC) {
                     if(pcEnd >= pc.length)
                        ensurePC(pcEnd);
                     pc[pcEnd++] = '\n';
                  }
                  normalizedCR = false;
               }
               else {
                  if(usePC) {
                     if(pcEnd >= pc.length)
                        ensurePC(pcEnd);
                     pc[pcEnd++] = ch;
                  }
                  normalizedCR = false;
               }
            }
         }

      }
      catch(EOFException ex) {
         // detect EOF and create meaningful error ...
         throw new RuntimeException("comment started on line " + curLine
               + " and column " + curColumn + " was not closed", ex);
      }
      if(tokenize) {
         posEnd = pos - 3;
         if(usePC) {
            pcEnd -= 2;
         }
      }
   }

   protected boolean parsePI() throws IOException {
      // implements XML 1.0 Section 2.6 Processing Instructions

      // [16] PI ::= '<?' PITarget (S (Char* - (Char* '?>' Char*)))? '?>'
      // [17] PITarget ::= Name - (('X' | 'x') ('M' | 'm') ('L' | 'l'))
      // ASSUMPTION: seen <?
      if(tokenize)
         posStart = pos;
      final int curLine = lineNumber;
      final int curColumn = columnNumber;
      int piTargetStart = pos + bufAbsoluteStart;
      int piTargetEnd = -1;
      final boolean normalizeIgnorableWS = tokenize == true
            && roundtripSupported == false;
      boolean normalizedCR = false;

      try {
         boolean seenQ = false;
         char ch = more();
         if(isS(ch)) {
            throw new RuntimeException(
                  "processing instruction PITarget must be exactly after <? and not white space character",
                  null);
         }
         while(true) {
            // scan until it hits ?>
            if(ch == '?') {
               seenQ = true;
            }
            else if(ch == '>') {
               if(seenQ) {
                  break; // found end sequence!!!!
               }
               seenQ = false;
            }
            else {
               if(piTargetEnd == -1 && isS(ch)) {
                  piTargetEnd = pos - 1 + bufAbsoluteStart;

                  // [17] PITarget ::= Name - (('X' | 'x') ('M' | 'm') ('L' |
                  // 'l'))
                  if((piTargetEnd - piTargetStart) == 3) {
                     if((buf[piTargetStart] == 'x' || buf[piTargetStart] == 'X')
                           && (buf[piTargetStart + 1] == 'm' || buf[piTargetStart + 1] == 'M')
                           && (buf[piTargetStart + 2] == 'l' || buf[piTargetStart + 2] == 'L')) {
                        if(piTargetStart > 3) { // <?xml is allowed as first
                           // characters in input ...
                           throw new RuntimeException(
                                 "processing instruction can not have PITarget with reserveld xml name",
                                 null);
                        }
                        else {
                           if(buf[piTargetStart] != 'x'
                                 && buf[piTargetStart + 1] != 'm'
                                 && buf[piTargetStart + 2] != 'l') {
                              throw new RuntimeException(
                                    "XMLDecl must have xml name in lowercase",
                                    null);
                           }
                        }
                        parseXmlDecl(ch);
                        if(tokenize)
                           posEnd = pos - 2;
                        final int off = piTargetStart - bufAbsoluteStart + 3;
                        final int len = pos - 2 - off;
                        xmlDeclContent = newString(buf, off, len);
                        return false;
                     }
                  }
               }
               seenQ = false;
            }
            if(normalizeIgnorableWS) {
               if(ch == '\r') {
                  normalizedCR = true;

                  // posEnd is already set
                  if(!usePC) {
                     posEnd = pos - 1;
                     if(posEnd > posStart) {
                        joinPC();
                     }
                     else {
                        usePC = true;
                        pcStart = pcEnd = 0;
                     }
                  }

                  if(pcEnd >= pc.length)
                     ensurePC(pcEnd);
                  pc[pcEnd++] = '\n';
               }
               else if(ch == '\n') {
                  if(!normalizedCR && usePC) {
                     if(pcEnd >= pc.length)
                        ensurePC(pcEnd);
                     pc[pcEnd++] = '\n';
                  }
                  normalizedCR = false;
               }
               else {
                  if(usePC) {
                     if(pcEnd >= pc.length)
                        ensurePC(pcEnd);
                     pc[pcEnd++] = ch;
                  }
                  normalizedCR = false;
               }
            }
            ch = more();
         }
      }
      catch(EOFException ex) {
         // detect EOF and create meaningful error ...
         throw new RuntimeException(
            "processing instruction started on line " + curLine
                     + " and column " + curColumn + " was not closed", ex);
      }
      if(piTargetEnd == -1) {
         piTargetEnd = pos - 2 + bufAbsoluteStart;
      }

      piTargetStart -= bufAbsoluteStart;
      piTargetEnd -= bufAbsoluteStart;

      if(tokenize) {
         posEnd = pos - 2;
         if(normalizeIgnorableWS) {
            --pcEnd;
         }
      }
      return true;
   }

   protected static final char[] VERSION = "version".toCharArray();
   protected static final char[] NCODING = "ncoding".toCharArray();
   protected static final char[] TANDALONE = "tandalone".toCharArray();
   protected static final char[] YES = "yes".toCharArray();
   protected static final char[] NO = "no".toCharArray();

   protected void parseXmlDecl(char ch) throws IOException {
      // [23] XMLDecl ::= '<?xml' VersionInfo EncodingDecl? SDDecl? S? '?>'

      // first make sure that relative positions will stay OK
      preventBufferCompaction = true;
      bufStart = 0; // necessary to keep pos unchanged during expansion!

      // --- parse VersionInfo

      // [24] VersionInfo ::= S 'version' Eq ("'" VersionNum "'" | '"'
      // VersionNum '"')
      // parse is positioned just on first S past <?xml
      ch = skipS(ch);
      ch = requireInput(ch, VERSION);
      // [25] Eq ::= S? '=' S?
      ch = skipS(ch);
      if(ch != '=') {
         throw new RuntimeException(
            "expected equals sign (=) after version and not "
                     + printable(ch), null);
      }
      ch = more();
      ch = skipS(ch);
      if(ch != '\'' && ch != '"') {
         throw new RuntimeException(
            "expected apostrophe (') or quotation mark (\") after version and not "
                     + printable(ch), null);
      }
      final char quotChar = ch;
      // int versionStart = pos + bufAbsoluteStart; // required if
      // preventBufferCompaction==false
      final int versionStart = pos;
      ch = more();
      // [26] VersionNum ::= ([a-zA-Z0-9_.:] | '-')+
      while(ch != quotChar) {
         if((ch < 'a' || ch > 'z') && (ch < 'A' || ch > 'Z')
               && (ch < '0' || ch > '9') && ch != '_' && ch != '.' && ch != ':'
               && ch != '-') {
            throw new RuntimeException(
               "<?xml version value expected to be in ([a-zA-Z0-9_.:] | '-')"
                        + " not " + printable(ch), null);
         }
         ch = more();
      }
      final int versionEnd = pos - 1;
      parseXmlDeclWithVersion(versionStart, versionEnd);
      preventBufferCompaction = false; // alow again buffer commpaction - pos
      // MAY chnage
   }

   protected void parseXmlDeclWithVersion(int versionStart, int versionEnd) throws IOException
   {
      // check version is "1.0"
      if((versionEnd - versionStart != 3) || buf[versionStart] != '1'
            || buf[versionStart + 1] != '.' || buf[versionStart + 2] != '0') {
         throw new RuntimeException(
            "only 1.0 is supported as <?xml version not '"
                     + printable(new String(buf, versionStart, versionEnd
                           - versionStart)) + "'", null);
      }
      xmlDeclVersion = newString(buf, versionStart, versionEnd - versionStart);

      // [80] EncodingDecl ::= S 'encoding' Eq ('"' EncName '"' | "'" EncName
      // "'" )
      char ch = more();
      ch = skipS(ch);
      if(ch == 'e') {
         ch = more();
         ch = requireInput(ch, NCODING);
         ch = skipS(ch);
         if(ch != '=') {
            throw new RuntimeException(
               "expected equals sign (=) after encoding and not "
                        + printable(ch), null);
         }
         ch = more();
         ch = skipS(ch);
         if(ch != '\'' && ch != '"') {
            throw new RuntimeException(
               "expected apostrophe (') or quotation mark (\") after encoding and not "
                        + printable(ch), null);
         }
         final char quotChar = ch;
         final int encodingStart = pos;
         ch = more();
         // [81] EncName ::= [A-Za-z] ([A-Za-z0-9._] | '-')*
         if((ch < 'a' || ch > 'z') && (ch < 'A' || ch > 'Z')) {
            throw new RuntimeException(
               "<?xml encoding name expected to start with [A-Za-z]"
                        + " not " + printable(ch), null);
         }
         ch = more();
         while(ch != quotChar) {
            if((ch < 'a' || ch > 'z') && (ch < 'A' || ch > 'Z')
                  && (ch < '0' || ch > '9') && ch != '.' && ch != '_'
                  && ch != '-') {
               throw new RuntimeException(
                  "<?xml encoding value expected to be in ([A-Za-z0-9._] | '-')"
                           + " not " + printable(ch), null);
            }
            ch = more();
         }
         final int encodingEnd = pos - 1;

         // TODO reconcile with setInput encodingName
         inputEncoding = newString(buf, encodingStart, encodingEnd
               - encodingStart);
         ch = more();
      }

      ch = skipS(ch);
      // [32] SDDecl ::= S 'standalone' Eq (("'" ('yes' | 'no') "'") | ('"'
      // ('yes' | 'no') '"'))
      if(ch == 's') {
         ch = more();
         ch = requireInput(ch, TANDALONE);
         ch = skipS(ch);
         if(ch != '=') {
            throw new RuntimeException(
               "expected equals sign (=) after standalone and not "
                        + printable(ch), null);
         }
         ch = more();
         ch = skipS(ch);

         if(ch != '\'' && ch != '"') {
            throw new RuntimeException(
               "expected apostrophe (') or quotation mark (\") after encoding and not "
                        + printable(ch), null);
         }

         char quotChar = ch;
         ch = more();

         if(ch == 'y') {
            ch = requireInput(ch, YES);
            xmlDeclStandalone = Boolean.TRUE;
         }
         else if(ch == 'n') {
            ch = requireInput(ch, NO);
            xmlDeclStandalone = Boolean.FALSE;
         }
         else {
            throw new RuntimeException(
               "expected 'yes' or 'no' after standalone and not "
                        + printable(ch), null);
         }
         if(ch != quotChar) {
            throw new RuntimeException("expected " + quotChar
                  + " after standalone value not " + printable(ch), null);
         }
         ch = more();
      }

      ch = skipS(ch);
      if(ch != '?') {
         throw new RuntimeException("expected ?> as last part of <?xml not "
               + printable(ch), null);
      }
      ch = more();
      if(ch != '>') {
         throw new RuntimeException("expected ?> as last part of <?xml not "
               + printable(ch), null);
      }
   }

   protected void parseDocdecl() throws IOException {
      // ASSUMPTION: seen <!D
      char ch = more();
      if(ch != 'O')
         throw new RuntimeException("expected <!DOCTYPE", null);
      ch = more();
      if(ch != 'C')
         throw new RuntimeException("expected <!DOCTYPE", null);
      ch = more();
      if(ch != 'T')
         throw new RuntimeException("expected <!DOCTYPE", null);
      ch = more();
      if(ch != 'Y')
         throw new RuntimeException("expected <!DOCTYPE", null);
      ch = more();
      if(ch != 'P')
         throw new RuntimeException("expected <!DOCTYPE", null);
      ch = more();
      if(ch != 'E')
         throw new RuntimeException("expected <!DOCTYPE", null);
      posStart = pos;
      // do simple and crude scanning for end of doctype

      // [28] doctypedecl ::= '<!DOCTYPE' S Name (S ExternalID)? S? ('['
      // (markupdecl | DeclSep)* ']' S?)? '>'
      int bracketLevel = 0;
      final boolean normalizeIgnorableWS = tokenize == true && roundtripSupported == false;
      boolean normalizedCR = false;
      while(true) {
         ch = more();
         if(ch == '[')
            ++bracketLevel;
         if(ch == ']')
            --bracketLevel;
         if(ch == '>' && bracketLevel == 0)
            break;
         if(normalizeIgnorableWS) {
            if(ch == '\r') {
               normalizedCR = true;

               // posEnd is alreadys set
               if(!usePC) {
                  posEnd = pos - 1;
                  if(posEnd > posStart) {
                     joinPC();
                  }
                  else {
                     usePC = true;
                     pcStart = pcEnd = 0;
                  }
               }

               if(pcEnd >= pc.length)
                  ensurePC(pcEnd);
               pc[pcEnd++] = '\n';
            }
            else if(ch == '\n') {
               if(!normalizedCR && usePC) {
                  if(pcEnd >= pc.length)
                     ensurePC(pcEnd);
                  pc[pcEnd++] = '\n';
               }
               normalizedCR = false;
            }
            else {
               if(usePC) {
                  if(pcEnd >= pc.length)
                     ensurePC(pcEnd);
                  pc[pcEnd++] = ch;
               }
               normalizedCR = false;
            }
         }

      }
      posEnd = pos - 1;
   }

   protected void parseCDSect(boolean hadCharData) throws IOException {
      // implements XML 1.0 Section 2.7 CDATA Sections

      // [18] CDSect ::= CDStart CData CDEnd
      // [19] CDStart ::= '<![CDATA['
      // [20] CData ::= (Char* - (Char* ']]>' Char*))
      // [21] CDEnd ::= ']]>'

      // ASSUMPTION: seen <![
      char ch = more();
      if(ch != 'C')
         throw new RuntimeException("expected <[CDATA[ for comment start",
                                       null);
      ch = more();
      if(ch != 'D')
         throw new RuntimeException("expected <[CDATA[ for comment start",
                                       null);
      ch = more();
      if(ch != 'A')
         throw new RuntimeException("expected <[CDATA[ for comment start",
                                       null);
      ch = more();
      if(ch != 'T')
         throw new RuntimeException("expected <[CDATA[ for comment start",
                                       null);
      ch = more();
      if(ch != 'A')
         throw new RuntimeException("expected <[CDATA[ for comment start",
                                       null);
      ch = more();
      if(ch != '[')
         throw new RuntimeException("expected <![CDATA[ for comment start",
                                       null);

      final int cdStart = pos + bufAbsoluteStart;
      final int curLine = lineNumber;
      final int curColumn = columnNumber;
      final boolean normalizeInput = tokenize == false || roundtripSupported == false;

      try {
         if(normalizeInput) {
            if(hadCharData) {
               if(!usePC) {
                  // posEnd is correct already!!!
                  if(posEnd > posStart) {
                     joinPC();
                  }
                  else {
                     usePC = true;
                     pcStart = pcEnd = 0;
                  }
               }
            }
         }
         boolean seenBracket = false;
         boolean seenBracketBracket = false;
         boolean normalizedCR = false;
         while(true) {
            // scan until it hits "]]>"
            ch = more();
            if(ch == ']') {
               if(!seenBracket) {
                  seenBracket = true;
               }
               else {
                  seenBracketBracket = true;
               }
            }
            else if(ch == '>') {
               if(seenBracket && seenBracketBracket) {
                  break; // found end sequence!!!!
               }
               else {
                  seenBracketBracket = false;
               }
               seenBracket = false;
            }
            else {
               if(seenBracket) {
                  seenBracket = seenBracketBracket = false;
               }
            }
            if(normalizeInput) {
               // deal with normalization issues ...
               if(ch == '\r') {
                  normalizedCR = true;
                  posStart = cdStart - bufAbsoluteStart;
                  posEnd = pos - 1; // posEnd is alreadys set
                  if(!usePC) {
                     if(posEnd > posStart) {
                        joinPC();
                     }
                     else {
                        usePC = true;
                        pcStart = pcEnd = 0;
                     }
                  }

                  if(pcEnd >= pc.length)
                     ensurePC(pcEnd);
                  pc[pcEnd++] = '\n';
               }
               else if(ch == '\n') {
                  if(!normalizedCR && usePC) {
                     if(pcEnd >= pc.length)
                        ensurePC(pcEnd);
                     pc[pcEnd++] = '\n';
                  }
                  normalizedCR = false;
               }
               else {
                  if(usePC) {
                     if(pcEnd >= pc.length)
                        ensurePC(pcEnd);
                     pc[pcEnd++] = ch;
                  }
                  normalizedCR = false;
               }
            }
         }
      }
      catch(EOFException ex) {
         // detect EOF and create meaningful error ...
         throw new RuntimeException("CDATA section started on line "
               + curLine + " and column " + curColumn + " was not closed",
                                       ex);
      }
      if(normalizeInput) {
         if(usePC) {
            pcEnd = pcEnd - 2;
         }
      }
      posStart = cdStart - bufAbsoluteStart;
      posEnd = pos - 3;
   }

   protected void fillBuf() throws IOException {
      if(reader == null)
         throw new RuntimeException(
               "reader must be set before parsing is started");

      // see if we are in compaction area
      if(bufEnd > bufSoftLimit) {

         // expand buffer it makes sense!!!!
         boolean compact = bufStart > bufSoftLimit;
         boolean expand = false;
         if(preventBufferCompaction) {
            compact = false;
            expand = true;
         }
         else if(!compact) {
            // freeSpace
            if(bufStart < buf.length / 2) {
               // less then half buffer available forcompactin --> expand
               // instead!!!
               expand = true;
            }
            else {
               // at least half of buffer can be reclaimed --> worthwhile
               // effort!!!
               compact = true;
            }
         }

         // if buffer almost full then compact it
         if(compact) {
            // TODO: look on trashing
            // //assert bufStart > 0
            System.arraycopy(buf, bufStart, buf, 0, bufEnd - bufStart);
            if(TRACE_SIZING)
               System.out.println("TRACE_SIZING fillBuf() compacting "
                     + bufStart
                     + " bufEnd="
                     + bufEnd
                     + " pos="
                     + pos
                     + " posStart="
                     + posStart
                     + " posEnd="
                     + posEnd
                     + " buf first 100 chars:"
                     + new String(buf, bufStart,
                           bufEnd - bufStart < 100 ? bufEnd - bufStart : 100));

         }
         else if(expand) {
            final int newSize = 2 * buf.length;
            final char[] newBuf = new char[newSize];
            if(TRACE_SIZING)
               System.out.println("TRACE_SIZING fillBuf() " + buf.length
                     + " => " + newSize);
            System.arraycopy(buf, bufStart, newBuf, 0, bufEnd - bufStart);
            buf = newBuf;
            if(bufLoadFactor > 0) {
               bufSoftLimit = (int) ((((long) bufLoadFactor) * buf.length) / 100);
            }

         }
         else {
            throw new RuntimeException("internal error in fillBuffer()");
         }
         bufEnd -= bufStart;
         ////////////////// mikec
         pos -= bufStart;
         posStart -= bufStart;
         posEnd -= bufStart;
         bufAbsoluteStart += bufStart;
         bufStart = 0;
         if(TRACE_SIZING)
            System.out.println("TRACE_SIZING fillBuf() after bufEnd=" + bufEnd
                  + " pos=" + pos + " posStart=" + posStart + " posEnd="
                  + posEnd + " buf first 100 chars:"
                  + new String(buf, 0, bufEnd < 100 ? bufEnd : 100));
      }
      // at least one character must be read or error
      final int len = buf.length - bufEnd > READ_CHUNK_SIZE ? READ_CHUNK_SIZE
            : buf.length - bufEnd;
      final int ret = reader.read(buf, bufEnd, len);
      if(ret > 0) {
         bufEnd += ret;
         if(TRACE_SIZING)
            System.out.println("TRACE_SIZING fillBuf() after filling in buffer"
                  + " buf first 100 chars:"
                  + new String(buf, 0, bufEnd < 100 ? bufEnd : 100));

         return;
      }
      if(ret == -1) {
         if(bufAbsoluteStart == 0 && pos == 0) {
            throw new EOFException("input contained no data");
         }
         else {
            if(seenRoot && depth == 0) { // inside parsing epilog!!!
               reachedEnd = true;
               return;
            }
            else {
               StringBuilder expectedTagStack = new StringBuilder();

               if(depth > 0) {
                  expectedTagStack.append(" - expected end tag");

                  if(depth > 1) {
                     expectedTagStack.append("s"); // more than one end tag
                  }

                  expectedTagStack.append(" ");

                  for(int i = depth; i > 0; i--) {
                     String tagName = new String(elRawName[i], 0,
                           elRawNameEnd[i]);
                     expectedTagStack.append("</").append(tagName).append('>');
                  }

                  expectedTagStack.append(" to close");

                  for(int i = depth; i > 0; i--) {
                     if(i != depth) {
                        expectedTagStack.append(" and"); // more than one end
                        // tag
                     }
                     String tagName = new String(elRawName[i], 0,
                           elRawNameEnd[i]);
                     expectedTagStack.append(" start tag <" + tagName + ">");
                     expectedTagStack.append(" from line " + elRawNameLine[i]);
                  }

                  expectedTagStack.append(", parser stopped on");
               }
               throw new EOFException("no more data available"
                     + expectedTagStack.toString() + getPositionDescription());
            }
         }
      }
      else {
         throw new IOException("error reading input, returned " + ret);
      }
   }

   protected char more() throws IOException {
      if(pos >= bufEnd) {
         fillBuf();
         // this return value should be ignonored as it is used in epilog
         // parsing ...
         if(reachedEnd)
            return (char) -1;
      }
      final char ch = buf[pos++];
      // line/columnNumber
      if(ch == '\n') {
         ++lineNumber;
         columnNumber = 1;
      }
      else {
         ++columnNumber;
      }

      int inc = 1;

      if(ch > 127 && ch <= 2047) {
         inc = 2;
      }
      else if(ch > 2047 && ch <= 65535) {
         inc = 3;
      }
      else if(ch > 65535 && ch <= 1114111) {
         inc = 4;
      }

      icol += inc;

      return ch;
   }

   protected void ensurePC(int end) {
      final int newSize = end > READ_CHUNK_SIZE ? 2 * end : 2 * READ_CHUNK_SIZE;
      final char[] newPC = new char[newSize];
      if(TRACE_SIZING)
         System.out.println("TRACE_SIZING ensurePC() " + pc.length + " ==> "
               + newSize + " end=" + end);
      System.arraycopy(pc, 0, newPC, 0, pcEnd);
      pc = newPC;
   }

   protected void joinPC() {
      final int len = posEnd - posStart;
      final int newEnd = pcEnd + len + 1;
      if(newEnd >= pc.length)
         ensurePC(newEnd); // add 1 for extra space for one char
      System.arraycopy(buf, posStart, pc, pcEnd, len);
      pcEnd += len;
      usePC = true;

   }

   protected char requireInput(char ch, char[] input) throws IOException {
      for(int i = 0; i < input.length; i++) {
         if(ch != input[i]) {
            throw new RuntimeException("expected " + printable(input[i])
                  + " in " + new String(input) + " and not " + printable(ch),
                                          null);
         }
         ch = more();
      }
      return ch;
   }

   protected char requireNextS() throws IOException {
      final char ch = more();
      if(!isS(ch)) {
         throw new RuntimeException("white space is required and not "
               + printable(ch), null);
      }
      return skipS(ch);
   }

   protected char skipS(char ch) throws IOException {
      while(isS(ch)) {
         ch = more();
      } // skip additional spaces
      return ch;
   }

   // nameStart / name lookup tables based on XML 1.1
   // http://www.w3.org/TR/2001/WD-xml11-20011213/
   protected static final int LOOKUP_MAX = 0x400;
   protected static final char LOOKUP_MAX_CHAR = (char) LOOKUP_MAX;
   protected static boolean[] lookupNameStartChar = new boolean[LOOKUP_MAX];
   protected static boolean[] lookupNameChar = new boolean[LOOKUP_MAX];

   private static final void setName(char ch) {
      lookupNameChar[ch] = true;
   }

   private static final void setNameStart(char ch) {
      lookupNameStartChar[ch] = true;
      setName(ch);
   }

   static {
      setNameStart(':');
      for(char ch = 'A'; ch <= 'Z'; ++ch)
         setNameStart(ch);
      setNameStart('_');
      for(char ch = 'a'; ch <= 'z'; ++ch)
         setNameStart(ch);
      for(char ch = '\u00c0'; ch <= '\u02FF'; ++ch)
         setNameStart(ch);
      for(char ch = '\u0370'; ch <= '\u037d'; ++ch)
         setNameStart(ch);
      for(char ch = '\u037f'; ch < '\u0400'; ++ch)
         setNameStart(ch);
      
      setName('^');
      setName('-');
      setName('.');
      setName('\\');
      setName('~');
      setName('`');
      setName('@');
      setName('#');
      setName('&');
      setName('$');
      setName('|');
      setName(';');      
      setName('\'');
      setName('<');
      setName('"');      
      
      for(char ch = '0'; ch <= '9'; ++ch)
         setName(ch);
      setName('\u00b7');
      for(char ch = '\u0300'; ch <= '\u036f'; ++ch)
         setName(ch);
   }

   protected boolean isNameStartChar(char ch) {
      return (ch < LOOKUP_MAX_CHAR && lookupNameStartChar[ch])
            || (ch >= LOOKUP_MAX_CHAR && ch <= '\u2027')
            || (ch >= '\u202A' && ch <= '\u218F')
            || (ch >= '\u2800' && ch <= '\uFFEF');
   }

   protected boolean isNameChar(char ch) {
      return (ch < LOOKUP_MAX_CHAR && lookupNameChar[ch])
            || (ch >= LOOKUP_MAX_CHAR && ch <= '\u2027')
            || (ch >= '\u202A' && ch <= '\u218F')
            || (ch >= '\u2800' && ch <= '\uFFEF');
   }

   protected boolean isS(char ch) {
      return (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t');
   }

   protected String printable(char ch) {
      if(ch == '\n') {
         return "\\n";
      }
      else if(ch == '\r') {
         return "\\r";
      }
      else if(ch == '\t') {
         return "\\t";
      }
      else if(ch == '\'') {
         return "\\'";
      }
      if(ch > 127 || ch < 32) {
         return "\\u" + Integer.toHexString((int) ch);
      }
      return "" + ch;
   }

   protected String printable(String s) {
      if(s == null)
         return null;
      final int sLen = s.length();
      StringBuilder buf = new StringBuilder(sLen + 10);
      for(int i = 0; i < sLen; ++i) {
         buf.append(printable(s.charAt(i)));
      }
      s = buf.toString();
      return s;
   }

   protected char[] getChar(char ch, String msg) {
      throw new RuntimeException(
         "entity reference name can not " + msg + " character "
                  + printable(ch) + "'", null);
   }
}
