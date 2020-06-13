# Suraksha


This app will help you convert any android phone to a security camera through which you can locally save the video
feed to the android phone (serving as the camera) as mp4 or watch a live stream on any remote android phone logged in with the same google account.


### Prerequisites

Minimum android SDK supported is 24 so anyone with Android 7.0 (Nougat) and above is fine.

## How to use

This app can be run on your android device, through android studio, by cloning this repo and then adding the google-services.json file from your own firebase project naming the app suraksha. In your firebase project you have to enable firebase database and google authentication. I also have a class named APIKeys which contains the API key for the Xirsys TURN server channel I am using. So you can also make a developer account on Xirsys for free and put your own API keys in `getIceServer()` method. Feel free to raise an issue.

This repo can be used mainly as a reference for WebRTC and how to capture frames (remote) or record videostream (local) using WebRTC.

The WebRTC using Activities are:
- `[CameraActivity](https://github.com/cannizarro/Suraksha/blob/master/app/src/main/java/com/cannizarro/securitycamera/CameraActivity.java)`: captures a local videostream, which can then be saved locally or live streamed to a remote party.
- `[SurveilActivity](https://github.com/cannizarro/Suraksha/blob/master/app/src/main/java/com/cannizarro/securitycamera/SurveilActivity.java)`: get live stream from a camera which is online and capture frames of it.


### Here are some gifs that will give you a walkthrough:

#### Using your android phone as a *Security Camera*. 
Using this phone's camera to stream to a remote phone or save video feed locally on this device's storage.

<img src="https://github.com/cannizarro/Suraksha/raw/master/Security.gif" width="320" height="640" />

#### Using your android phone as a *Surveilance Device*. 
You can watch the live stream of the selected phone among other phones which are set as security cameras. You can also take snapshots of the feed.

<img src="https://github.com/cannizarro/Suraksha/raw/master/Surveillance.gif" width="320" height="570" />

* You can choose between setting up this device as a security camera or a surveillance device (watch the live stream).
* In security camera mode you can start saving locally or go online after setting the camera's name (unique to this device at any particular time) so that someone logged in with the same google account can view your live feed.
* In surveillance mode you can choose from the list of available online cameras logged in with this google account.

Please give any permission you are prompted. It will just ask to access your camera, audio and storage, duh.

This app works with basically any network due to the robust TURN server provided by Xirsys which will help us dodge most of the NATs and firewalls, but still if you don't get any video feed even when you got `Recieved remote stream` toast, maybe there are chances that your network doesn't like P2P connections and your ISP is an asshole, hehehe.


## Built With

* [WebRTC](https://webrtc.org/native-code/android/) - Real time communication for web.
* [Firebase](https://firebase.google.com/) - Realtime database for signalling server.
* [Xirsys](https://xirsys.com/) - For STUN and TURN needs.

## Contributing

Feel free to contribute.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details

## Acknowledgments

* [Vivek Chanddru's android codelab for WebRTC](https://github.com/vivek1794/webrtc-android-codelab).
* [Roberto Leinardi's SpeedDial FAB implementation](https://github.com/leinardi/FloatingActionButtonSpeedDial)
