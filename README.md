# JavaCV to Wowza - Android Boilerplate

Few short notes for the beginning. 

- This application is boilerplate for live streaming from android to Wowza via RTMP protocol. It is based on [JavaCV library](https://github.com/bytedeco/javacv). Many thanks to [Samuel Audet](https://github.com/saudet)
- It is built on AndroidStudio 2.1.2
- Compatible with Marshmallow (using Marshmallow permission model)

# Getting started

## JavaCV in short

This boilerplate is actually a simple wrapper around JavaCV library. More about JavaCV you can find at [Samuel Audet repository](https://github.com/bytedeco/javacv). In short, it is using precompiled C libraries placed in jniLibs folder for each architecture, then, as dependencies javaccv.jar, javacpp.jar and ofcourse ffmpeg.jar. And thats mostly all about needed libraries. Then we need to build android app by using those libraries.

You can reference libraries above through gradle/maven or by downloading and copy-pasting files in libs folder. I've did this way, since i could not make it work via gradle/maven. It does not mean that its not possible, its probably lack of my knowledge.. :(.

### Possible issues 

Issues with copy-paste libraries can occur if you mix different versions of .so files and .jar libraries. So, this would be some steps you'd have to do:
- Download latest javacv-bin
- Extract library on file system
- copy ffmpeg.jar, javacv.jar and javacpp.jar in libs folder of your android project
- extract ffmpeg-android-x86.jar, ffmpeg-android-arm.jar, artoolkitplus-android-x86.jar and artoolkitplus-android-arm.jar and copy all .so files to related folders (arm to jniLibs/armeabi and to jniLibs/armeabi-v7a, x86 to jniLibs/x86). And that should be it.

##How it works

AS any library it is good to know how it works but it is nor necessary. So, i'll describe this boilerplate in few steps only. I assume that you've checked [source code](https://github.com/bajicdusko/JavaCVWowzaStarterKit/blob/master/app/src/main/java/com/bajicdusko/javacvwowzastarterkit/LiveBroadcastActivity.java) and [layout](https://github.com/bajicdusko/JavaCVWowzaStarterKit/blob/master/app/src/main/res/layout/activity_live_broadcast.xml) already.

1. On activity initialization, we take care of camera choice and camera initialization. This boilerplate is still using old Camera API.
2. When SurfaceHolder gets ready, we connect camera with holder and preview starts.
3. On SurfaceChanged, we are inspecting possible video formats and framerates that camera can support and we are choosing nearest profile to those settings you've required.
4. On broadcast button click, FFMpegRecorder is started and AudioRecord thread is started also. AudioRecord thread is initializing microphone and taking data from it. 
5. Video and audio needs to be synced and it is done in onPreviewFrame method. Currently, there is some audio lag on recording start.
6. When broadcast is stopped, we are releasing and stopping recorder, audiothread also. Camera preview still remains. 

##Features

There are few default features that this boilerplate should support:

1. Torch on/off
2. Camera switching
3. Auto-Focus
4. Screen rotation. 

All of those features are currently in #TODO, or #INPROGRESS and none of them is working currently (they will)

##Wowza settings

AS you may have seen already in source, there are few variables that you should set for your own environment. wowzaUsername and wowzaPassword are optional.

```
//broadcast credentials (if stream source requires authentication)
private String wowzaUsername;
private String wowzaPassword;
private String wowzaIp = "xxx.xxx.xxx.xxx";
private int wowzaLivePort = 1935; //by default Wowza settings
private String wowzaApplicationName = "";
private String wowzaStreamName = "";
```

Also there are few properties that are hardcoded for wowza:

```
private final String PROTOCOL = "rtmp://";
.
.
recorder.setFormat("flv");
recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
```

I have tried RTSP and it is not working, but since Wowza accept RTP and RTMP its not a huge issue.
