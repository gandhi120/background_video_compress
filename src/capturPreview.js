import {useEffect, useRef} from 'react';
import {Image, Text, TouchableOpacity, View, NativeModules} from 'react-native';
import {
  Camera,
  useCameraDevice,
  useCameraPermission,
  useMicrophonePermission,
} from 'react-native-vision-camera';
import Video, {VideoRef} from 'react-native-video';
const {VideoModule} = NativeModules;

function CapturePreview({route}) {
  console.log('this.props.route.params', route.params);

  const {path} = route.params;
  useEffect(() => {
    console.log('video compress images');
    setTimeout(() => {
      VideoModule.startVideoCompression(path);
    }, 3000);
  }, []);
  return (
    <Video
      source={{uri: path}}
      style={{width: '100%', height: '100%'}}
      controls
      resizeMode="contain"
      paused={false}
    />
  );
}
export default CapturePreview;
