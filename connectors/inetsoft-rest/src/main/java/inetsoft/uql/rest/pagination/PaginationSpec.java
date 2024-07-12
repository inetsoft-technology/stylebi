/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.pagination;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.function.Consumer;

/**
 * Data class which describes the pagination specification for a REST query.
 */
public class PaginationSpec implements Serializable, XMLSerializable, Cloneable {
   public PaginationSpec() {
      // for deserialization
   }

   public PaginationType getType() {
      return type;
   }

   public void setType(PaginationType type) {
      this.type = type;
   }

   public void setType(String type) {
      this.type = PaginationType.valueOf(type);
   }

   public boolean isZeroBasedPageIndex() {
      return zeroBasedPageIndex;
   }

   public void setZeroBasedPageIndex(boolean zeroBasedPageIndex) {
      this.zeroBasedPageIndex = zeroBasedPageIndex;
   }

   public int getFirstPageIndex() {
      return isZeroBasedPageIndex() ? 0 : 1;
   }

   public int getMaxResultsPerPage() {
      return maxResultsPerPage;
   }

   public void setMaxResultsPerPage(int maxResultsPerPage) {
      this.maxResultsPerPage = maxResultsPerPage;
   }

   public PaginationParameter getTotalPagesParam() {
      return totalPagesParam;
   }

   public void setTotalPagesParam(PaginationParameter totalPagesParam) {
      this.totalPagesParam = totalPagesParam;
   }

   public PaginationParameter getPageNumberParamToWrite() {
      return pageNumberParamToWrite;
   }

   public void setPageNumberParamToWrite(PaginationParameter pageNumberParamToWrite) {
      this.pageNumberParamToWrite = pageNumberParamToWrite;
   }

   public PaginationParameter getHasNextParam() {
      return hasNextParam;
   }

   public void setHasNextParam(PaginationParameter hasNextParam) {
      this.hasNextParam = hasNextParam;
   }

   public PaginationParameter getPageOffsetParamToRead() {
      return pageOffsetParamToRead;
   }

   public void setPageOffsetParamToRead(PaginationParameter pageOffsetParamToRead) {
      this.pageOffsetParamToRead = pageOffsetParamToRead;
   }

   public PaginationParameter getPageOffsetParamToWrite() {
      return pageOffsetParamToWrite;
   }

   public void setPageOffsetParamToWrite(PaginationParameter pageOffsetParamToWrite) {
      this.pageOffsetParamToWrite = pageOffsetParamToWrite;
   }

   public PaginationParameter getPageCountXpath() {
      return pageCountXpath;
   }

   public void setPageCountXpath(PaginationParameter pageCountXpath) {
      this.pageCountXpath = pageCountXpath;
   }

   public PaginationParameter getPageNumberUrlVariable() {
      return pageNumberUrlVariable;
   }

   public void setPageNumberUrlVariable(PaginationParameter pageNumberUrlVariable) {
      this.pageNumberUrlVariable = pageNumberUrlVariable;
   }

   public PaginationParameter getTotalCountParam() {
      return totalCountParam;
   }

   public void setTotalCountParam(PaginationParameter totalCountParam) {
      this.totalCountParam = totalCountParam;
   }

   public PaginationParameter getOffsetParam() {
      return offsetParam;
   }

   public void setOffsetParam(PaginationParameter offsetParam) {
      this.offsetParam = offsetParam;
   }

   public PaginationParameter getLinkParam() {
      return linkParam;
   }

   public void setLinkParam(PaginationParameter linkParam) {
      this.linkParam = linkParam;
   }

   public boolean isIncrementOffset() {
      return incrementOffset;
   }

   public void setIncrementOffset(boolean incrementOffset) {
      this.incrementOffset = incrementOffset;
   }

   public PaginationParameter getMaxResultsPerPageParam() {
      return maxResultsPerPageParam;
   }

   public void setMaxResultsPerPageParam(PaginationParameter maxResultsPerPageParam) {
      this.maxResultsPerPageParam = maxResultsPerPageParam;
   }

   public String getRecordCountPath() {
      return recordCountPath;
   }

   public void setRecordCountPath(String recordCountPath) {
      this.recordCountPath = recordCountPath;
   }

   public String getHasNextParamValue() {
      return hasNextParamValue;
   }

   public void setHasNextParamValue(String hasNextParamValue) {
      this.hasNextParamValue = hasNextParamValue;
   }

   public int getBaseRecordLength() {
      return this.baseRecordLength;
   }

   public void setBaseRecordLength(int baseRecordLength) {
      this.baseRecordLength = baseRecordLength;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.format("<type>%s</type>%n", type.name());
      writer.format("<zeroBasedPageIndex><![CDATA[%s]]></zeroBasedPageIndex>%n",
                    zeroBasedPageIndex);
      writer.format("<maxResultsPerPage><![CDATA[%d]]></maxResultsPerPage>%n", maxResultsPerPage);
      writer.format("<incrementOffset><![CDATA[%s]]></incrementOffset>%n", incrementOffset);
      writer.format("<baseRecordLength><![CDATA[%d]]></baseRecordLength>%n", baseRecordLength);

      if(recordCountPath != null) {
         writer.format("<recordCountPath><![CDATA[%s]]></recordCountPath>%n", recordCountPath);
      }

      if(hasNextParamValue != null) {
         writer.format("<hasNextParamValue><![CDATA[%s]]></hasNextParamValue>%n", hasNextParamValue);
      }

      writePaginationParam(totalPagesParam, "totalPagesParam", writer);
      writePaginationParam(pageNumberParamToWrite, "pageNumberParamToWrite", writer);
      writePaginationParam(hasNextParam, "hasNextParam", writer);
      writePaginationParam(pageOffsetParamToRead, "pageOffsetParamToRead", writer);
      writePaginationParam(pageOffsetParamToWrite, "pageOffsetParamToWrite", writer);
      writePaginationParam(pageCountXpath, "pageCountXpath", writer);
      writePaginationParam(pageNumberUrlVariable, "pageNumberUrlVariable", writer);
      writePaginationParam(totalCountParam, "totalCountParam", writer);
      writePaginationParam(offsetParam, "offsetParam", writer);
      writePaginationParam(linkParam, "linkParam", writer);
      writePaginationParam(maxResultsPerPageParam, "maxResultsPerPageParam", writer);
   }

   private void writePaginationParam(PaginationParameter param, String name, PrintWriter writer) {
      if(param != null) {
         writer.format("<%s>", name);
         param.writeXML(writer);
         writer.format("</%s>%n", name);
      }
   }

   @Override
   public void parseXML(Element root) {
      final String typeName = Tool.getChildValueByTagName(root, "type");

      if(typeName != null) {
         type = PaginationType.valueOf(typeName);
      }

      zeroBasedPageIndex = "true".equals(Tool.getChildValueByTagName(root, "zeroBasedPageIndex"));
      final String maxResultsPerPageStr = Tool.getChildValueByTagName(root, "maxResultsPerPage");

      if(maxResultsPerPageStr != null) {
         this.maxResultsPerPage = Integer.parseInt(maxResultsPerPageStr);
      }

      final String baseRecordLengthStr = Tool.getChildValueByTagName(root, "baseRecordLength");

      if(baseRecordLengthStr != null) {
         this.baseRecordLength = Integer.parseInt(baseRecordLengthStr);
      }

      incrementOffset = "true".equals(Tool.getChildValueByTagName(root, "incrementOffset"));
      recordCountPath = Tool.getChildValueByTagName(root, "recordCountPath");
      hasNextParamValue = Tool.getChildValueByTagName(root, "hasNextParamValue");

      readPaginationParam(root, "totalPagesParam", p -> totalPagesParam = p);
      readPaginationParam(root, "pageNumberParamToWrite", p -> pageNumberParamToWrite = p);
      readPaginationParam(root, "hasNextParam", p -> hasNextParam = p);
      readPaginationParam(root, "pageOffsetParamToRead", p -> pageOffsetParamToRead = p);
      readPaginationParam(root, "pageOffsetParamToWrite", p -> pageOffsetParamToWrite = p);
      readPaginationParam(root, "pageCountXpath", p -> pageCountXpath = p);
      readPaginationParam(root, "pageNumberUrlVariable", p -> pageNumberUrlVariable = p);
      readPaginationParam(root, "totalCountParam", p -> totalCountParam = p);
      readPaginationParam(root, "offsetParam", p -> offsetParam = p);
      readPaginationParam(root, "linkParam", p -> linkParam = p);
      readPaginationParam(root, "maxResultsPerPageParam", p -> maxResultsPerPageParam = p);
   }

   private void readPaginationParam(Element root, String name, Consumer<PaginationParameter> fn) {
      final Element node = Tool.getChildNodeByTagName(root, name);

      if(node != null) {
         final PaginationParameter param = new PaginationParameter();
         param.parseXML(node);
         fn.accept(param);
      }
   }

   @Override
   public Object clone() {
      try {
         PaginationSpec copy = (PaginationSpec) super.clone();
         copy.totalPagesParam = cloneParameter(totalPagesParam);
         copy.pageNumberParamToWrite = cloneParameter(pageNumberParamToWrite);
         copy.hasNextParam = cloneParameter(hasNextParam);
         copy.pageOffsetParamToRead = cloneParameter(pageOffsetParamToRead);
         copy.pageOffsetParamToWrite = cloneParameter(pageOffsetParamToWrite);
         copy.pageCountXpath = cloneParameter(pageCountXpath);
         copy.pageNumberUrlVariable = cloneParameter(pageNumberUrlVariable);
         copy.totalCountParam = cloneParameter(totalCountParam);
         copy.offsetParam = cloneParameter(offsetParam);
         copy.linkParam = cloneParameter(linkParam);
         copy.maxResultsPerPageParam = cloneParameter(maxResultsPerPageParam);
         return copy;
      }
      catch(Exception e) {
         LOG.error("Failed to create copy of PaginationSpec", e);
         return null;
      }
   }

   private PaginationParameter cloneParameter(PaginationParameter param) {
      if(param == null) {
         return null;
      }

      return (PaginationParameter) param.clone();
   }

   public static Builder builder() {
      return new Builder();
   }

   private PaginationType type = PaginationType.NONE;
   private boolean zeroBasedPageIndex = false;
   private int maxResultsPerPage = -1;
   private boolean incrementOffset = false;
   private int baseRecordLength = 0;
   private String recordCountPath;
   private String hasNextParamValue;
   private PaginationParameter totalPagesParam = PaginationParameter.forReadJson();
   private PaginationParameter pageNumberParamToWrite = PaginationParameter.forWrite();
   private PaginationParameter hasNextParam = PaginationParameter.forReadJson();
   private PaginationParameter pageOffsetParamToRead = PaginationParameter.forReadJson();
   private PaginationParameter pageOffsetParamToWrite = PaginationParameter.forWrite();
   private PaginationParameter pageCountXpath = PaginationParameter.forXpath();
   private PaginationParameter pageNumberUrlVariable = PaginationParameter.forUrlVariable();
   private PaginationParameter totalCountParam = PaginationParameter.forReadJson();
   private PaginationParameter offsetParam = PaginationParameter.forWrite();
   private PaginationParameter linkParam = PaginationParameter.forLink();
   private PaginationParameter maxResultsPerPageParam = PaginationParameter.forWrite();

   private static final Logger LOG = LoggerFactory.getLogger(PaginationSpec.class);

   public static final class Builder {

      public Builder type(PaginationType type) {
         this.type = type;
         return this;
      }

      public Builder zeroBasedPageIndex(boolean zeroBasedPageIndex) {
         this.zeroBasedPageIndex = zeroBasedPageIndex;
         return this;
      }

      public Builder maxResultsPerPage(int maxResultsPerPage) {
         this.maxResultsPerPage = maxResultsPerPage;
         return this;
      }

      public Builder incrementOffset(boolean incrementOffset) {
         this.incrementOffset = incrementOffset;
         return this;
      }

      public Builder recordCountPath(String recordCountPath) {
         this.recordCountPath = recordCountPath;
         return this;
      }

      public Builder baseRecordLength(int baseRecordLength) {
         this.baseRecordLength = baseRecordLength;
         return this;
      }

      /**
       * hasNext value that indicates the end of the pagination
       */
      public Builder hasNextParamValue(String hasNextParamValue) {
         this.hasNextParamValue = hasNextParamValue;
         return this;
      }

      public Builder totalPagesParam(PaginationParamType type, String value) {
         totalPagesParam.setType(type);
         totalPagesParam.setValue(value);
         return this;
      }

      public Builder pageNumberParamToWrite(PaginationParamType type, String value) {
         pageNumberParamToWrite.setType(type);
         pageNumberParamToWrite.setValue(value);
         return this;
      }

      public Builder hasNextParam(PaginationParamType type, String value) {
         hasNextParam.setType(type);
         hasNextParam.setValue(value);
         return this;
      }

      public Builder pageOffsetParamToRead(PaginationParamType type, String value) {
         pageOffsetParamToRead.setType(type);
         pageOffsetParamToRead.setValue(value);
         return this;
      }

      public Builder pageOffsetParamToWrite(PaginationParamType type, String value) {
         pageOffsetParamToWrite.setType(type);
         pageOffsetParamToWrite.setValue(value);
         return this;
      }

      public Builder pageCountXpath(PaginationParamType type, String value) {
         pageCountXpath.setType(type);
         pageCountXpath.setValue(value);
         return this;
      }

      public Builder pageNumberUrlVariable(PaginationParamType type, String value) {
         pageNumberUrlVariable.setType(type);
         pageNumberUrlVariable.setValue(value);
         return this;
      }

      public Builder totalCountParam(PaginationParamType type, String value) {
         totalCountParam.setType(type);
         totalCountParam.setValue(value);
         return this;
      }

      public Builder offsetParam(PaginationParamType type, String value) {
         offsetParam.setType(type);
         offsetParam.setValue(value);
         return this;
      }

      public Builder linkParam(PaginationParamType type, String value) {
         return linkParam(type, value, null);
      }

      public Builder linkParam(PaginationParamType type, String value, String rel) {
         linkParam.setType(type);
         linkParam.setValue(value);
         linkParam.setLinkRelation(rel);
         return this;
      }

      public Builder maxResultsPerPageParam(PaginationParamType type, String value) {
         maxResultsPerPageParam.setType(type);
         maxResultsPerPageParam.setValue(value);
         return this;
      }

      public Builder with(PaginationSpec spec) {
         type = spec.type;
         zeroBasedPageIndex = spec.zeroBasedPageIndex;
         maxResultsPerPage = spec.maxResultsPerPage;
         incrementOffset = spec.incrementOffset;
         recordCountPath = spec.recordCountPath;
         hasNextParamValue = spec.hasNextParamValue;
         copyParameter(spec.totalPagesParam, totalPagesParam);
         copyParameter(spec.pageNumberParamToWrite, pageNumberParamToWrite);
         copyParameter(spec.hasNextParam, hasNextParam);
         copyParameter(spec.pageOffsetParamToRead, pageOffsetParamToRead);
         copyParameter(spec.pageOffsetParamToWrite, pageOffsetParamToWrite);
         copyParameter(spec.pageCountXpath, pageCountXpath);
         copyParameter(spec.pageNumberUrlVariable, pageNumberUrlVariable);
         copyParameter(spec.totalCountParam, totalCountParam);
         copyParameter(spec.offsetParam, offsetParam);
         copyParameter(spec.linkParam, linkParam);
         copyParameter(spec.maxResultsPerPageParam, maxResultsPerPageParam);
         return this;
      }

      public PaginationSpec build() {
         PaginationSpec spec = new PaginationSpec();
         spec.type = type;
         spec.zeroBasedPageIndex = zeroBasedPageIndex;
         spec.maxResultsPerPage = maxResultsPerPage;
         spec.incrementOffset = incrementOffset;
         spec.recordCountPath = recordCountPath;
         spec.hasNextParamValue = hasNextParamValue;
         spec.baseRecordLength = baseRecordLength;
         copyParameter(totalPagesParam, spec.totalPagesParam);
         copyParameter(pageNumberParamToWrite, spec.pageNumberParamToWrite);
         copyParameter(hasNextParam, spec.hasNextParam);
         copyParameter(pageOffsetParamToRead, spec.pageOffsetParamToRead);
         copyParameter(pageOffsetParamToWrite, spec.pageOffsetParamToWrite);
         copyParameter(pageCountXpath, spec.pageCountXpath);
         copyParameter(pageNumberUrlVariable, spec.pageNumberUrlVariable);
         copyParameter(totalCountParam, spec.totalCountParam);
         copyParameter(offsetParam, spec.offsetParam);
         copyParameter(linkParam, spec.linkParam);
         copyParameter(maxResultsPerPageParam, spec.maxResultsPerPageParam);
         return spec;
      }

      private void copyParameter(PaginationParameter source, PaginationParameter target) {
         target.setType(source.getType());
         target.setValue(source.getValue());
         target.setLinkRelation(source.getLinkRelation());
      }

      private PaginationType type = PaginationType.NONE;
      private boolean zeroBasedPageIndex = false;
      private int maxResultsPerPage = -1;
      private boolean incrementOffset = false;
      private String recordCountPath;
      private String hasNextParamValue;
      private int baseRecordLength = -1;
      private PaginationParameter totalPagesParam = PaginationParameter.forReadJson();
      private PaginationParameter pageNumberParamToWrite = PaginationParameter.forWrite();
      private PaginationParameter hasNextParam = PaginationParameter.forReadJson();
      private PaginationParameter pageOffsetParamToRead = PaginationParameter.forReadJson();
      private PaginationParameter pageOffsetParamToWrite = PaginationParameter.forWrite();
      private PaginationParameter pageCountXpath = PaginationParameter.forXpath();
      private PaginationParameter pageNumberUrlVariable = PaginationParameter.forUrlVariable();
      private PaginationParameter totalCountParam = PaginationParameter.forReadJson();
      private PaginationParameter offsetParam = PaginationParameter.forWrite();
      private PaginationParameter linkParam = PaginationParameter.forLink();
      private PaginationParameter maxResultsPerPageParam = PaginationParameter.forWrite();
   }
}
