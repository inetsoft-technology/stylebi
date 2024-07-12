"use strict";

const path = require("path");
const webpack = require("webpack");
const {styles} = require("@ckeditor/ckeditor5-dev-utils");
const CKEditorWebpackPlugin = require("@ckeditor/ckeditor5-dev-webpack-plugin");

module.exports = {
   devtool: 'source-map',
   performance: { hints: false },

   entry: path.resolve( __dirname, 'src', 'ckeditor.js' ),

   output: {
      library: "CustomEditor",
      path: path.resolve(__dirname),
      filename: "ckeditor.js",
      libraryTarget: "umd",
      libraryExport: "default"
   },

   plugins: [
      new CKEditorWebpackPlugin({
         language: "en",
         additionalLanguages: "all"
      })
   ],

   module: {
      rules: [
         {
            test: /\.svg$/,
            use: ["raw-loader"]
         },
         {
            test: /\.css$/,
            use: [
               {
                  loader: "style-loader",
                  options: {
                     injectType: "singletonStyleTag",
                     attributes: {
                        "data-cke": true
                     }
                  }
               },
               {
                  loader: "css-loader"
               },
               {
                  loader: "postcss-loader",
                  options: {
                     postcssOptions: styles.getPostCssConfig({
                        themeImporter: {
                           themePath: require.resolve("@ckeditor/ckeditor5-theme-lark")
                        },
                        minify: true
                     })
                  }
               }
            ]
         }
      ]
   }
};
