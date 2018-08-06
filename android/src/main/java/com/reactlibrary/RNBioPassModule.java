package com.reactlibrary;

import android.util.Log;

import com.reactlibrary.RNBioPassDialog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.cert.CertificateException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.UnrecoverableKeyException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import android.support.v4.content.ContextCompat;
import android.support.v4.widget.ImageViewCompat;
import android.support.design.widget.BottomSheetDialog;

import android.hardware.fingerprint.FingerprintManager;

class PromiseRejection extends Exception {
  private String code;

  public PromiseRejection(String code, String message, Throwable t) {
    super(message, t);
    this.code = code;
  }

  public String getCode() {
    return this.code;
  }
}

public class RNBioPassModule extends ReactContextBaseJavaModule {
  private static final String KEYCHAIN_DATA = "RN_BIOPASS";
  private static final String DEFAULT_SERVICE = "RN_BIOPASS_DEFAULT_ALIAS";

  private static final String KEYSTORE_TYPE = "AndroidKeyStore";
  private static final String ENCRYPTION_ALGORITHM = KeyProperties.KEY_ALGORITHM_RSA;
  private static final String ENCRYPTION_BLOCK_MODE = KeyProperties.BLOCK_MODE_ECB;
  private static final String ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1;

  private final ReactApplicationContext reactContext;

  public RNBioPassModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "RNBioPass";
  }

  @ReactMethod
  public void store(String password, final Promise promise) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      promise.reject("NOT_SUPPORTED", "BioPass is not supported on this version of Android");
      return;
    }

    try {
      KeyStore keyStore = getKeyStoreAndLoad();

      boolean contains;
      try {
        contains = keyStore.containsAlias(DEFAULT_SERVICE);
      } catch (KeyStoreException e) {
        throw new PromiseRejection("RUNTIME_ERROR", "Failed to find key", e);
      }

      if (!contains) {
        AlgorithmParameterSpec spec;
        spec = new KeyGenParameterSpec.Builder(
          DEFAULT_SERVICE,
          KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_ENCRYPT)
          .setBlockModes(ENCRYPTION_BLOCK_MODE)
          .setEncryptionPaddings(ENCRYPTION_PADDING)
          .setRandomizedEncryptionRequired(true)
          .setUserAuthenticationRequired(true)
          .build();

        KeyPairGenerator generator;
        try {
          generator = KeyPairGenerator.getInstance(ENCRYPTION_ALGORITHM, KEYSTORE_TYPE);
        } catch (NoSuchAlgorithmException e) {
          throw new PromiseRejection("NOT_SUPPORTED", "Algorithm not supported", e);
        } catch (NoSuchProviderException e) {
          throw new PromiseRejection("NOT_SUPPORTED", "Failed to find Android key store", e);
        }

        try {
          generator.initialize(spec);
        } catch (InvalidAlgorithmParameterException e) {
          throw new PromiseRejection("NO_FINGERPRINT", "No fingerprint enrolled on device", e);
        }

        generator.generateKeyPair();
      }

      Key key = getPublicKeyFromKeyStore(keyStore);

      byte[] encryptedPassword = encryptString(key, password);

      SharedPreferences prefs = reactContext.getSharedPreferences(KEYCHAIN_DATA, Context.MODE_PRIVATE);
      prefs.edit().putString(DEFAULT_SERVICE, Base64.encodeToString(encryptedPassword, Base64.DEFAULT)).apply();

      promise.resolve(null);
    } catch (PromiseRejection e) {
      promise.reject(e.getCode(), e.getMessage(), e.getCause());
    }
  }

  @ReactMethod
  public void retreive(String promptText, final Promise promise) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      promise.reject("NOT_SUPPORTED", "BioPass is not supported on this version of Android");
      return;
    }

    try {
      FingerprintManager fingerprintManager = (FingerprintManager) this.reactContext.getSystemService(Context.FINGERPRINT_SERVICE);

      if (!fingerprintManager.isHardwareDetected()) {
        throw new PromiseRejection("NOT_SUPPORTED", "No fingerprint reader present", null);
      }

      if (!fingerprintManager.hasEnrolledFingerprints()) {
        throw new PromiseRejection("NOT_SUPPORTED", "No fingerprints enrolled", null);
      }

      SharedPreferences prefs = reactContext.getSharedPreferences(KEYCHAIN_DATA, Context.MODE_PRIVATE);

      String encodedEncryptedPassword = prefs.getString(DEFAULT_SERVICE, null);

      if (encodedEncryptedPassword == null) {
        throw new PromiseRejection("NOT_FOUND", "No password stored", null);
      }

      final byte[] encryptedPassword = Base64.decode(encodedEncryptedPassword, Base64.DEFAULT);

      final KeyStore keyStore = getKeyStoreAndLoad();
      final Key key = getPrivateKeyFromKeyStore(keyStore);
      final Cipher cipher = createCipher();

      try {
        cipher.init(Cipher.DECRYPT_MODE, key);
      } catch (InvalidKeyException e) {
        throw new PromiseRejection("RUNTIME_ERROR", "Invalid key", e);
      }

      RNBioPassDialog dialog = new RNBioPassDialog(this.reactContext, promptText);

      dialog.authenticate(fingerprintManager, new FingerprintManager.CryptoObject(cipher), new RNBioPassDialog.AuthenticateCallback() {
        @Override
        public void resolve() {
          try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(encryptedPassword);
            CipherInputStream cipherInputStream = new CipherInputStream(inputStream, cipher);
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];

            while (true) {
              int n;

              try {
                n = cipherInputStream.read(buffer, 0, buffer.length);
              } catch (IOException e) {
                throw new PromiseRejection("RUNTIME_ERROR", "Failed to decrypt", e);
              }

              if (n <= 0) {
                break;
              }
              output.write(buffer, 0, n);
            }

            promise.resolve(new String(output.toByteArray(), Charset.forName("UTF-8")));
          } catch (PromiseRejection e) {
            promise.reject(e.getCode(), e.getMessage(), e.getCause());
          }
        }
      });
    } catch (PromiseRejection e) {
      promise.reject(e.getCode(), e.getMessage(), e.getCause());
    }
  }

  @ReactMethod
  public void delete(final Promise promise) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      promise.reject("NOT_SUPPORTED", "BioPass is not supported on this version of Android");
      return;
    }

    try {
      SharedPreferences prefs = reactContext.getSharedPreferences(KEYCHAIN_DATA, Context.MODE_PRIVATE);

      prefs.edit().remove(DEFAULT_SERVICE).apply();

      KeyStore keyStore = getKeyStoreAndLoad();

      try {
        if (keyStore.containsAlias(DEFAULT_SERVICE)) {
          keyStore.deleteEntry(DEFAULT_SERVICE);
        }
      } catch (KeyStoreException e) {
        throw new PromiseRejection("RUNTIME_ERROR", "Failed to remove key", e);
      }

      promise.resolve(null);
    } catch (PromiseRejection e) {
      promise.reject(e.getCode(), e.getMessage(), e.getCause());
    }
  }

  private byte[] encryptString(Key key, String value) throws PromiseRejection {
    Cipher cipher = createCipher();

    try {
      cipher.init(Cipher.ENCRYPT_MODE, key);
    } catch (InvalidKeyException e) {
      throw new PromiseRejection("RUNTIME_ERROR", "Invalid key", e);
    }

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    byte[] encodedValue;
    try {
      encodedValue = value.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new PromiseRejection("RUNTIME_ERROR", "Unsupported encoding: UTF-8", e);
    }

    // encrypt the value using a CipherOutputStream
    CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, cipher);

    try {
      cipherOutputStream.write(encodedValue);
      cipherOutputStream.close();
    } catch (IOException e) {
      throw new PromiseRejection("RUNTIME_ERROR", "Failed to encrypt", e);
    }

    return outputStream.toByteArray();
  }

  private KeyStore getKeyStoreAndLoad() throws PromiseRejection {
    KeyStore keyStore;

    try {
      keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
    } catch (KeyStoreException e) {
      throw new PromiseRejection("NOT_SUPPORTED", "Could not access Android key store", e);
    }

    try {
      keyStore.load(null);
    } catch (NoSuchAlgorithmException | CertificateException | IOException e) {
      throw new PromiseRejection("NOT_SUPPORTED", "Could not load the Android key store", e);
    }

    return keyStore;
  }

  private PrivateKey getPrivateKeyFromKeyStore(KeyStore keyStore) throws PromiseRejection {
    try {
      return (PrivateKey) keyStore.getKey(DEFAULT_SERVICE, null);
    } catch (KeyStoreException e) {
      throw new PromiseRejection("RUNTIME_ERROR", "Failed to get key", e);
    } catch (NoSuchAlgorithmException e) {
      throw new PromiseRejection("NOT_SUPPORTED", "Algorithm not supported", e);
    } catch (UnrecoverableKeyException e) {
      throw new PromiseRejection("RUNTIME_ERROR", "Failed to get key", e);
    }
  }

  private PublicKey getPublicKeyFromKeyStore(KeyStore keyStore) throws PromiseRejection {
    try {
      KeyFactory keyFactory = KeyFactory.getInstance(ENCRYPTION_ALGORITHM);
      PublicKey publicKey = keyStore.getCertificate(DEFAULT_SERVICE).getPublicKey();
      KeySpec spec = new X509EncodedKeySpec(publicKey.getEncoded());
      return keyFactory.generatePublic(spec);
    } catch (KeyStoreException e) {
      throw new PromiseRejection("RUNTIME_ERROR", "Failed to get key", e);
    } catch (NoSuchAlgorithmException e) {
      throw new PromiseRejection("NOT_SUPPORTED", "Algorithm not supported", e);
    } catch (InvalidKeySpecException e) {
      throw new PromiseRejection("RUNTIME_ERROR", "Invalid key specification", e);
    }
  }

  private Cipher createCipher() throws PromiseRejection {
    try {
      return Cipher.getInstance(ENCRYPTION_ALGORITHM + "/" + ENCRYPTION_BLOCK_MODE + "/" + ENCRYPTION_PADDING);
    } catch (NoSuchAlgorithmException e) {
      throw new PromiseRejection("NOT_SUPPORTED", "Algorithm not supported", e);
    } catch (NoSuchPaddingException e) {
      throw new PromiseRejection("NOT_SUPPORTED", "Padding not supported", e);
    }
  }
}
