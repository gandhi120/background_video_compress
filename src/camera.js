import {useRef, useState} from 'react';
import {Platform, Text, TouchableOpacity, View} from 'react-native';
import {Camera, useCameraDevice} from 'react-native-vision-camera';
import {useNavigation} from '@react-navigation/native';
import RNFetchBlob from 'rn-fetch-blob';
import RNFS from 'react-native-fs';

function CameraScreen() {
  const navigation = useNavigation();

  const device = useCameraDevice('back');
  const camera = useRef(null);
  const [isRecording, setIsRecording] = useState(false); // Track recording state

  const takeImage = async () => {
    setIsRecording(true);
    camera.current.startRecording({
      fileType: 'mp4',
      onRecordingFinished: async video => {
        console.log('video', video);
        const date = Date.now();
        let newPath = `file://${video.path}`;
        let targetPath = '';
        if (Platform.OS === 'android') {
          targetPath = `${RNFetchBlob.fs.dirs.DCIMDir}/.digiQC/video/${date}.mp4`;
          await saveImageToDCIM(newPath, targetPath);
        } else {
          newPath = video.path;
        }

        const params = {
          path: Platform.OS === 'android' ? targetPath : newPath,
        };
        navigation.navigate('CapturePreview', {...params});
      },
      onRecordingError: error => console.error(error),
    });
  };

  const saveImageToDCIM = async (oldPath, targetPath) => {
    RNFetchBlob.fs
      .cp(oldPath, targetPath)
      .then(() => {
        console.log('save file');
        RNFetchBlob.fs
          .unlink(oldPath)
          .then(() => {})
          .catch(error => {});
      })
      .catch(error => {});
  };

  const stopVideo = async () => {
    await camera.current.stopRecording();
  };

  return (
    <View style={{flex: 1}}>
      <Camera
        video={true}
        // audio={true}
        ref={camera}
        style={{flex: 1}}
        device={device}
        isActive={true}
      />
      {!isRecording ? (
        <TouchableOpacity
          onPress={() => takeImage()}
          style={{
            height: 70,
            width: 70,
            borderRadius: 100,
            backgroundColor: 'white',
            position: 'absolute',
            bottom: 20,
            alignSelf: 'center',
          }}
        />
      ) : (
        <TouchableOpacity
          onPress={() => stopVideo()}
          style={{
            height: 70,
            width: 70,
            borderRadius: 100,
            backgroundColor: 'white',
            position: 'absolute',
            bottom: 20,
            alignSelf: 'center',
          }}>
          <Text style={{color: 'black'}}>STOP</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}
export default CameraScreen;
