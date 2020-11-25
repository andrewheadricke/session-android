/*
 * Copyright (C) 2013-2018 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.securesms.crypto;

import android.content.Context;

import org.jetbrains.annotations.Nullable;
import org.thoughtcrime.securesms.crypto.storage.TextSecurePreKeyStore;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.session.libsignal.libsignal.IdentityKeyPair;
import org.session.libsignal.libsignal.InvalidKeyException;
import org.session.libsignal.libsignal.InvalidKeyIdException;
import org.session.libsignal.libsignal.ecc.Curve;
import org.session.libsignal.libsignal.ecc.ECKeyPair;
import org.session.libsignal.libsignal.state.PreKeyRecord;
import org.session.libsignal.libsignal.state.PreKeyStore;
import org.session.libsignal.libsignal.state.SignedPreKeyRecord;
import org.session.libsignal.libsignal.state.SignedPreKeyStore;
import org.session.libsignal.libsignal.util.Medium;

import java.util.LinkedList;
import java.util.List;

public class PreKeyUtil {

  @SuppressWarnings("unused")
  private static final String TAG = PreKeyUtil.class.getSimpleName();

  private static final int BATCH_SIZE = 100;

  public synchronized static List<PreKeyRecord> generatePreKeyRecords(Context context) {
    PreKeyStore        preKeyStore    = new TextSecurePreKeyStore(context);
    List<PreKeyRecord> records        = new LinkedList<>();
    int                preKeyIdOffset = TextSecurePreferences.getNextPreKeyId(context);

    for (int i=0;i<BATCH_SIZE;i++) {
      int          preKeyId = (preKeyIdOffset + i) % Medium.MAX_VALUE;
      ECKeyPair    keyPair  = Curve.generateKeyPair();
      PreKeyRecord record   = new PreKeyRecord(preKeyId, keyPair);

      preKeyStore.storePreKey(preKeyId, record);
      records.add(record);
    }

    TextSecurePreferences.setNextPreKeyId(context, (preKeyIdOffset + BATCH_SIZE + 1) % Medium.MAX_VALUE);

    return records;
  }

  public synchronized static SignedPreKeyRecord generateSignedPreKey(Context context, IdentityKeyPair identityKeyPair, boolean active) {
    try {
      SignedPreKeyStore  signedPreKeyStore = new TextSecurePreKeyStore(context);
      int                signedPreKeyId    = TextSecurePreferences.getNextSignedPreKeyId(context);
      ECKeyPair          keyPair           = Curve.generateKeyPair();
      byte[]             signature         = Curve.calculateSignature(identityKeyPair.getPrivateKey(), keyPair.getPublicKey().serialize());
      SignedPreKeyRecord record            = new SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), keyPair, signature);

      signedPreKeyStore.storeSignedPreKey(signedPreKeyId, record);
      TextSecurePreferences.setNextSignedPreKeyId(context, (signedPreKeyId + 1) % Medium.MAX_VALUE);

      if (active) {
        TextSecurePreferences.setActiveSignedPreKeyId(context, signedPreKeyId);
      }

      return record;
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public static synchronized void setActiveSignedPreKeyId(Context context, int id) {
    TextSecurePreferences.setActiveSignedPreKeyId(context, id);
  }

  public static synchronized int getActiveSignedPreKeyId(Context context) {
    return TextSecurePreferences.getActiveSignedPreKeyId(context);
  }

  // region Loki
  public static synchronized @Nullable SignedPreKeyRecord getActiveSignedPreKey(Context context) {
    SignedPreKeyStore signedPreKeyStore = new TextSecurePreKeyStore(context);
    try {
      return signedPreKeyStore.loadSignedPreKey(getActiveSignedPreKeyId(context));
    } catch (InvalidKeyIdException e) {
      return null;
    }
  }

  public synchronized static List<PreKeyRecord> generatePreKeyRecords(Context context, int amount) {
    List<PreKeyRecord> records = new LinkedList<>();
    int preKeyIDOffset = TextSecurePreferences.getNextPreKeyId(context);
    for (int i = 0; i < amount; i++) {
      int preKeyID = (preKeyIDOffset + i) % Medium.MAX_VALUE;
      ECKeyPair keyPair = Curve.generateKeyPair();
      PreKeyRecord record = new PreKeyRecord(preKeyID, keyPair);
      records.add(record);
    }
    TextSecurePreferences.setNextPreKeyId(context, (preKeyIDOffset + BATCH_SIZE + 1) % Medium.MAX_VALUE);
    return records;
  }

  public synchronized static void storePreKeyRecords(Context context, List<PreKeyRecord> records) {
    PreKeyStore preKeyStore = new TextSecurePreKeyStore(context);
    for (PreKeyRecord record : records) {
      preKeyStore.storePreKey(record.getId(), record);
    }
  }

  public synchronized static PreKeyRecord loadPreKey(Context context, int preKeyID) throws InvalidKeyIdException {
    PreKeyStore preKeyStore = new TextSecurePreKeyStore(context);
    return preKeyStore.loadPreKey(preKeyID);
  }
  // endregion
}
