import ClassicEditor from "@ckeditor/ckeditor5-editor-classic/src/classiceditor";
import Essentials from "@ckeditor/ckeditor5-essentials/src/essentials";
import Paragraph from "@ckeditor/ckeditor5-paragraph/src/paragraph";
import Bold from "@ckeditor/ckeditor5-basic-styles/src/bold";
import Italic from "@ckeditor/ckeditor5-basic-styles/src/italic";
import Underline from "@ckeditor/ckeditor5-basic-styles/src/underline";
import Strikethrough from "@ckeditor/ckeditor5-basic-styles/src/strikethrough";
import Superscript from "@ckeditor/ckeditor5-basic-styles/src/superscript";
import Subscript from "@ckeditor/ckeditor5-basic-styles/src/subscript";
import List from "@ckeditor/ckeditor5-list/src/list";
import FontFamily from "@ckeditor/ckeditor5-font/src/fontfamily";
import FontSize from "@ckeditor/ckeditor5-font/src/fontsize";
import FontColor from "@ckeditor/ckeditor5-font/src/fontcolor";
import FontBackgroundColor from "@ckeditor/ckeditor5-font/src/fontbackgroundcolor";
import Alignment from "@ckeditor/ckeditor5-alignment/src/alignment";
import Link from "@ckeditor/ckeditor5-link/src/link";
import Image from "@ckeditor/ckeditor5-image/src/image";
import ImageToolbar from "@ckeditor/ckeditor5-image/src/imagetoolbar";
import ImageResize from "@ckeditor/ckeditor5-image/src/imageresize";
import ImageUpload from "@ckeditor/ckeditor5-image/src/imageupload";
import Base64UploadAdapter from "@ckeditor/ckeditor5-upload/src/adapters/base64uploadadapter";
import Heading from "@ckeditor/ckeditor5-heading/src/heading";
import Indent from "@ckeditor/ckeditor5-indent/src/indent";

export default class Editor extends ClassicEditor {}

Editor.builtinPlugins = [
   Alignment, 
   Base64UploadAdapter,
   Bold,
   Essentials,
   FontBackgroundColor,
   FontColor,
   FontFamily,
   FontSize,
   Heading,
   Image,
   ImageResize,
   ImageToolbar,
   ImageUpload,
   Indent,
   Italic,
   Link,
   List,
   Paragraph,
   Strikethrough,
   Subscript,
   Superscript,
   Underline
];

Editor.defaultConfig = {
   toolbar: {
      items: [
         "heading",
         "|",
         "bold",
         "italic",
         "underline",
         "strikethrough",
         "|",
         "bulletedList",
         "numberedList",
         "|",
         "superscript",
         "subscript",
         "|",
         "outdent",
         "indent",
         "|",
         "fontFamily",
         "fontSize",
         "fontColor",
         "fontBackgroundColor",
         "|",
         "alignment",
         "|",
         "link",
         "imageUpload"
      ]
   },
   language: "en"
};
