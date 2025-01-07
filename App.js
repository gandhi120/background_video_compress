import React, {Component, useEffect} from 'react';
import {
  Text,
  TouchableOpacity,
  View,
  PermissionsAndroid,
  Platform,
} from 'react-native';
import {NavigationContainer, useNavigation} from '@react-navigation/native';
import {createNativeStackNavigator} from '@react-navigation/native-stack';
import CameraScreen from './src/camera';
import CapturePreview from './src/capturPreview';
import RNFetchBlob from 'rn-fetch-blob';
import {request, PERMISSIONS, RESULTS} from 'react-native-permissions';

const Stack = createNativeStackNavigator();

function HomeScreen() {
  const navigation = useNavigation();
  useEffect(() => {
    requestStoragePermission();
    createNoMediaFile();
  }, []);
  const requestStoragePermission = async () => {
    if (Platform.OS === 'android') {
      console.log('PermissionsAndroid', PermissionsAndroid);

      try {
        const granted = await PermissionsAndroid.requestMultiple([
          PermissionsAndroid.PERMISSIONS.READ_MEDIA_AUDIO,
          PermissionsAndroid.PERMISSIONS.READ_MEDIA_VIDEO,
          PermissionsAndroid.PERMISSIONS.READ_MEDIA_IMAGES,
          PermissionsAndroid.PERMISSIONS.CAMERA,
          PermissionsAndroid.PERMISSIONS.CAMERA,
          PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
        ]);
        console.log('granted', granted);

        return (
          granted['android.permission.WRITE_MEDIA_VIDEO'] ===
            PermissionsAndroid.RESULTS.GRANTED ||
          granted['android.permission.READ_MEDIA_VIDEO'] ===
            PermissionsAndroid.RESULTS.GRANTED ||
          granted['android.permission.READ_MEDIA_IMAGES'] ===
            PermissionsAndroid.RESULTS.GRANTED
        );
      } catch (err) {
        console.warn(err);
        return false;
      }
    } else {
      const result = await request(PERMISSIONS.IOS.MICROPHONE);

      if (result === RESULTS.GRANTED) {
        console.log('Microphone permission granted');
      } else {
        console.log('Microphone permission denied');
      }
    }
    return true;
  };
  const createNoMediaFile = async () => {
    try {
      const digiQCFolderPath = `${RNFetchBlob.fs.dirs.DCIMDir}/.digiQC/video`;
      const digiQCChunkFolderPath = `${RNFetchBlob.fs.dirs.DCIMDir}/.digiQC/video/chunks`;
      const digiQCChunkCompressedFolderPath = `${RNFetchBlob.fs.dirs.DCIMDir}/.digiQC/video/compressed_chunks`;
      // Check if the directory exists; if not, create it
      const folderExists = await RNFetchBlob.fs.isDir(digiQCFolderPath);
      const folderOneExists = await RNFetchBlob.fs.isDir(digiQCChunkFolderPath);
      const folderTwoExists = await RNFetchBlob.fs.isDir(
        digiQCChunkCompressedFolderPath,
      );
      if (!folderExists) {
        await RNFetchBlob.fs.mkdir(digiQCFolderPath);
      }
      if (!folderOneExists) {
        await RNFetchBlob.fs.mkdir(digiQCChunkFolderPath);
      }
      if (!folderTwoExists) {
        await RNFetchBlob.fs.mkdir(digiQCChunkCompressedFolderPath);
      }
    } catch (error) {
      // Logger.error('Error creating .digiqc folder' + error);
    }
  };

  return (
    <View
      style={{
        backgroundColor: 'white',
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
      }}>
      <TouchableOpacity
        style={{
          backgroundColor: 'yellow',
          padding: 10,
          alignItems: 'center',
          justifyContent: 'center',
        }}
        onPress={() => navigation.navigate('Camera')}>
        <Text style={{color: 'black'}}> capture video </Text>
      </TouchableOpacity>
    </View>
  );
}

function RootStack() {
  return (
    <Stack.Navigator>
      <Stack.Screen name="Home" component={HomeScreen} />
      <Stack.Screen
        name="Camera"
        component={CameraScreen}
        options={{headerShown: false}}
      />
      <Stack.Screen
        name="CapturePreview"
        component={CapturePreview}
        options={{headerShown: false}}
      />
    </Stack.Navigator>
  );
}

export default class App extends Component {
  render() {
    return (
      <NavigationContainer>
        <RootStack />
      </NavigationContainer>
    );
  }
}
