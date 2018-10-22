Google Mobile Ads Unity Plugin
==============================
The Google Mobile Ads SDK is the latest generation in Google mobile advertising
featuring refined ad formats and streamlined APIs for access to mobile ad
networks and advertising solutions. The SDK enables mobile app developers to
maximize their monetization in native mobile apps.

This repository contains the source code for the Google Mobile Ads Unity
plugin. This plugin enables Unity developers to easily serve Google Mobile Ads
on Android and iOS apps without having to write Java or Objective-C code.
The plugin provides a C# interface for requesting ads that is used by C#
scripts in your Unity project.

Downloads
----------
Please check out our
[releases](//github.com/googleads/googleads-mobile-unity/releases)
for the latest official version of the plugin.

Documentation
--------------
For instructions on using the plugin, please refer to
[this developer guide](//firebase.google.com/docs/admob/unity/start).

Be sure to also join the developer community on
[our forum](//groups.google.com/forum/#!categories/google-admob-ads-sdk/game-engines).

Suggesting improvements
------------------------
To file bugs, make feature requests, or to suggest other improvements,
please use [github's issue tracker](//github.com/googleads/googleads-mobile-unity/issues).

License
-------
[Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html)

GOODROID
-------

### jarファイルを更新したい
1. $cd googleads-mobile-unity/source/android-library/app
2. java編集
3. $gradle makeJar
4. $mv ./build/libs/unity-plugin-library.jar ~/beast/Assets/Plugins/Android/GoogleMobileAdsPlugin/libs/.

### 署名ハッシュ値を更新したい
1. xor_test.javaのhashに署名ハッシュ入れて単体で実行(XORのkeyはbannerにしています)
2. エンコードされた値をsource/android-library/app/src/main/java/com/google/unity/ads/Banner.javaのbannerIdListに登録(署名ハッシュ値許可リストになります)
