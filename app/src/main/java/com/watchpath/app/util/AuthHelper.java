package com.watchpath.app.util;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Single owner of anonymous sign-in. Guarantees we start at most one
 * sign-in at a time, so two quick callers can't create two different users.
 */
public final class AuthHelper {

    private static final String TAG = "AuthHelper";
    private static Task<AuthResult> pendingSignIn;

    private AuthHelper() {}

    /** Starts anonymous sign-in only if needed. Safe to call many times. */
    public static synchronized void ensureSignedIn() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) return; // already signed in (Firebase remembers it)

        boolean needNew = pendingSignIn == null
                || (pendingSignIn.isComplete() && !pendingSignIn.isSuccessful());
        if (needNew) {
            pendingSignIn = auth.signInAnonymously();
            pendingSignIn.addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Anonymous auth OK. UID: " + auth.getUid());
                } else {
                    Log.e(TAG, "Anonymous auth failed", task.getException());
                }
            });
        }
    }

    /** Returns the UID, waiting for the in-flight sign-in if needed.
     *  BLOCKING: call from a background thread only. */
    public static String getUidBlocking() throws Exception {
        ensureSignedIn();

        Task<AuthResult> inFlight;
        synchronized (AuthHelper.class) {
            inFlight = pendingSignIn;
        }
        if (inFlight != null) Tasks.await(inFlight);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) throw new Exception("Failed to authenticate anonymously");
        return user.getUid();
    }
}