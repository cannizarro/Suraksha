# Suraksha


This app will help you convert any android phone to a security camera through which you can locally save the video
feed to the android phone (serving as the camera) as mp4 or watch a live stream on any remote android phone logged in with the same google account.


### Prerequisites

Minimum android SDK supported is 24 so anyone with Android 7.0 (Nougat) and above is fine.

## How to use

### Using your android phone as a *Security Camera*. Using this phone's camera to stream to a remote phone or save video feed locally on this device's storage.
	![Security Camera activity](Security.gif)

### Using your android phone as a *Surveilance Device*. You can watch the live stream of the selected phone among other phones which are set as security cameras. You can also take snapshots of the feed.
	![Surveillance activity](Surveillance.gif)

* You can choose between setting up this device as a security camera or a surveillance device (watch the live stream).
* In security camera mode you can start saving locally or go online after setting the camera's name (unique to this device at any particular time) so that someone logged in with the same google account can view your live feed.
* In surveillance mode you can choose from the list of available online cameras logged in with this google account.

Please give any permission you are prompted. It will just ask to access your camera, audio and storage, duh.

This app works with basically any network due to the robust TURN server provided by Xirsys which will help us dodge most of the NATs and firewalls, but still if you don't get any video feed even when you got `Recieved remote stream` toast, maybe there are chances that your network doesn't like P2P connections and your ISP is an asshole, hehehe.

Well, here are some gifs that will you a walkthrough:


## Built With

* [WebRTC](https://webrtc.org/native-code/android/) - Real time communication for web.
* [Firebase](https://firebase.google.com/) - Realtime database for signalling server.
* [Xirsys](https://xirsys.com/) - For STUN and TURN needs.

## Contributing

Feel free to contribute.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details

## Acknowledgments

* [Vivek's android codelab for WebRTC](https://github.com/vivek1794/webrtc-android-codelab).
