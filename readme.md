# react-native-biopass

Store a password behind biometric authentication.

Currently only supports the Android platform.

## Installation

```sh
npm install --save react-native-biopass
react-native link react-native-biopass
```

## Usage

```js
import BioPass from 'react-native-biopass'

// Store a password for future retreival
BioPass.store("secret")
  .then(() => console.log(`Password stored!`))
  .catch((err) => console.log(`Failed to store password: ${err}`)

// Retreive a stored password (will trigger Fingerprint / TouchID / FaceID prompt)
BioPass.retreive("Give us the secret password!")
  .then((password) => console.log(`The password was: ${password}`))
  .catch((err) => console.log(`Failed to retreive password: ${err}`)

// Delete the stored password
BioPass.delete()
  .then(() => console.log(`Password deleted!`))
  .catch((err) => console.log(`Failed to delete password: ${err}`)
```

## Related Projects

- [BioPass for iOS](https://github.com/LinusU/BioPass)

## Manual installation

### iOS

1. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
2. Go to `node_modules` ➜ `react-native-biopass` and add `RNBioPass.xcodeproj`
3. In XCode, in the project navigator, select your project. Add `libRNBioPass.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
4. Run your project (`Cmd+R`)<

### Android

1. Open up `android/app/src/main/java/[...]/MainActivity.java`
    - Add `import com.reactlibrary.RNBioPassPackage;` to the imports at the top of the file
    - Add `new RNBioPassPackage()` to the list returned by the `getPackages()` method
1. Append the following lines to `android/settings.gradle`:

    ```gradle
    include ':react-native-biopass'
    project(':react-native-biopass').projectDir = new File(rootProject.projectDir '../node_modules/react-native-biopass/android')
    ```

1. Insert the following lines inside the dependencies block in `android/app/build.gradle`:

    ```gradle
      compile project(':react-native-biopass')
    ```

#### Windows

[Read it! :D](https://github.com/ReactWindows/react-native)

1. In Visual Studio add the `RNBioPass.sln` in `node_modules/react-native-biopass/windows/RNBioPass.sln` folder to their solution, reference from their app.
2. Open up your `MainPage.cs` app
    - Add `using Bio.Pass.RNBioPass;` to the usings at the top of the file
    - Add `new RNBioPassPackage()` to the `List<IReactPackage>` returned by the `Packages` method
