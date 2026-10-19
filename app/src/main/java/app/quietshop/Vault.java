package app.quietshop;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** App-private encrypted session. Neither secrets nor upstream responses are logged. */
final class Vault {
  private static SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if (!store.containsAlias("quietshop.session")) {
      KeyGenerator gen =
          KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
      gen.init(
          new KeyGenParameterSpec.Builder(
                  "quietshop.session",
                  KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      gen.generateKey();
    }
    return (SecretKey) store.getKey("quietshop.session", null);
  }

  static synchronized void save(Context c, JSONObject value) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key());
    String iv = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP);
    String blob =
        Base64.encodeToString(
            cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    if (!c.getSharedPreferences("vault", 0)
        .edit()
        .putString("iv", iv)
        .putString("blob", blob)
        .commit()) throw new Exception("Session storage failed.");
  }

  static synchronized JSONObject read(Context c) throws Exception {
    var p = c.getSharedPreferences("vault", 0);
    if (!p.contains("blob")) return new JSONObject();
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE,
        key(),
        new GCMParameterSpec(128, Base64.decode(p.getString("iv", ""), Base64.NO_WRAP)));
    return new JSONObject(
        new String(
            cipher.doFinal(Base64.decode(p.getString("blob", ""), Base64.NO_WRAP)),
            StandardCharsets.UTF_8));
  }

  static synchronized void clear(Context c) {
    c.getSharedPreferences("vault", 0).edit().clear().commit();
  }
}
