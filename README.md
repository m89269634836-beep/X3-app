# X3 Flight Remote — experimental Android starter

Open folder in Android Studio and build APK (Build > Build APK(s)). Requires Android 8+.

## Implemented
- BLE scan and connect to an advertising X3 device
- GATT BE80/BE81/BE82 transport, subscribe to notifications
- Send Header16 start/stop/status request packets
- Foreground connectedDevice service and persistent notification
- Volume +/− controls while the app is foreground
- Raw HEX camera replies for diagnosing the actual firmware

## NOT YET IMPLEMENTED / NOT VERIFIED
- Tested recording control on a real X3 (hardware unavailable)
- Camera authorization/pairing handshake and response parsing
- Reliable recording confirmation, battery decoding, auto reconnect/backoff
- Global volume interception (Android restricts it); background notification actions
- Robust command queue and retries

Do not rely on this version during flight. Test with a camera on a desk and verify the recording indicator and actual saved files. Do not repeatedly send commands if camera status is unknown.

Protocol reference: https://github.com/xaionaro-go/insta360ctl
Check upstream repository license and permissions before reusing upstream source; this project independently implements the publicly documented packet framing.
