import {useEffect, useRef} from 'react';
import {Image, Text, TouchableOpacity, View} from 'react-native';
import {
  Camera,
  useCameraDevice,
  useCameraPermission,
  useMicrophonePermission,
} from 'react-native-vision-camera';
import Video, {VideoRef} from 'react-native-video';

function CapturePreview({route}) {
  console.log('this.props.route.params', route.params);

  const {path} = route.params;
  //   const { videoPath } = route.params || {};

  //   return (
  //     <View style={{flex: 1}}>
  //       {/* <Text>hello</Text> */}
  //       <Image
  //         style={{height: '100%', width: '100%', resizeMode: 'cover'}}
  //         source={{
  //           uri: path,
  //         }}
  //       />
  //     </View>
  //   );

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
