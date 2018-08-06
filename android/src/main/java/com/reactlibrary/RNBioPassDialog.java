package com.reactlibrary;

import java.lang.Math;
import java.lang.Runnable;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.util.TypedValue;
import android.os.CancellationSignal;
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

public class RNBioPassDialog extends BottomSheetDialog {
  static public class AuthenticateCallback {
    public void reject(Throwable e) {}
    public void resolve() {}
  }

  private ReactApplicationContext reactContext;
  private CancellationSignal cancellationSignal;

  private ImageView icon;

  private static ColorStateList themeAttributeToColorStateList(int themeAttributeId, Context context, int fallbackColorId) {
    TypedValue outValue = new TypedValue();
    Resources.Theme theme = context.getTheme();
    boolean wasResolved = theme.resolveAttribute(themeAttributeId, outValue, true);

    if (wasResolved == false) return ContextCompat.getColorStateList(context, fallbackColorId);
    if (outValue.resourceId == 0) return ColorStateList.valueOf(outValue.data);

    return ContextCompat.getColorStateList(context, outValue.resourceId);
  }

  private static int themeAttributeToColor(int themeAttributeId, Context context, int fallbackColorId) {
    TypedValue outValue = new TypedValue();
    Resources.Theme theme = context.getTheme();
    boolean wasResolved = theme.resolveAttribute(themeAttributeId, outValue, true);

    if (wasResolved == false) return ContextCompat.getColor(context, fallbackColorId);
    if (outValue.resourceId == 0) return outValue.data;

    return ContextCompat.getColor(context, outValue.resourceId);
  }

  public RNBioPassDialog(ReactApplicationContext reactContext, String promptText) {
    super((Context) reactContext.getCurrentActivity());

    this.reactContext = reactContext;

    Activity activity = reactContext.getCurrentActivity();
    Context context = (Context) activity;

    float density = context.getResources().getDisplayMetrics().density;

    LinearLayout content = new LinearLayout(context);

    TextView title = new TextView(context);
    TextView prompt = new TextView(context);
    icon = new ImageView(context);
    TextView instructions = new TextView(context);

    {
      LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

      layout.setMargins(Math.round(24 * density), Math.round(24 * density), Math.round(24 * density), Math.round(24 * density));

      content.setLayoutParams(layout);
      content.setOrientation(LinearLayout.VERTICAL);
    }

    {
      ApplicationInfo info = activity.getApplicationInfo();
      PackageManager packageManager = activity.getPackageManager();
      String applicationLabel = packageManager.getApplicationLabel(info).toString();

      title.setText("Fingerprint for \"" + applicationLabel + "\"");
      title.setTextColor(themeAttributeToColor(android.R.attr.textColorPrimary, context, android.R.color.black));
      title.setTextSize(24);
    }

    {
      LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

      layout.setMargins(0, 0, 0, Math.round(8 * density));

      prompt.setLayoutParams(layout);
      prompt.setText(promptText);
      prompt.setTextColor(themeAttributeToColor(android.R.attr.textColorPrimary, context, android.R.color.black));
      prompt.setTextSize(18);
    }

    {
      LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.round(64 * density));

      layout.setMargins(0, Math.round(48 * density), 0, Math.round(16 * density));

      icon.setImageResource(R.drawable.fingerprint);
      icon.setLayoutParams(layout);

      ImageViewCompat.setImageTintList(icon, themeAttributeToColorStateList(android.R.attr.colorAccent, context, android.R.color.black));
    }

    {
      instructions.setGravity(Gravity.CENTER);
      instructions.setText("Touch the fingerprint sensor");
    }

    content.addView(title);
    content.addView(prompt);
    content.addView(icon);
    content.addView(instructions);

    setContentView(content);
  }

  void authenticate(FingerprintManager fingerprintManager, FingerprintManager.CryptoObject cryptoObject, final AuthenticateCallback callback) {
    final RNBioPassDialog dialog = this;
    final Activity activity = reactContext.getCurrentActivity();

    cancellationSignal = new CancellationSignal();

    fingerprintManager.authenticate(cryptoObject, cancellationSignal, 0, new FingerprintManager.AuthenticationCallback() {
      @Override
      public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
        icon.post(new Runnable() {
          public void run () {
            dialog.hide();
            callback.resolve();
          }
        });
      }
    }, null);

    show();
  }
}
