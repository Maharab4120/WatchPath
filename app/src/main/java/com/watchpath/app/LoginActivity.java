package com.watchpath.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.watchpath.app.data.remote.CloudListSync;
import com.watchpath.app.util.AuthHelper;

/**
 * Entry screen. Modes:
 *  - Guest: no account, My List stays on this device (silent anonymous auth).
 *  - Account: email + password with email verification.
 *  - Google: one-tap sign-in via Credential Manager.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    public static final String PREFS = "auth_prefs";
    public static final String KEY_GUEST = "guest_mode";
    public static final String EXTRA_FORCE_SHOW = "force_show";

    private FirebaseAuth auth;
    private boolean signUpMode = false;

    private TextInputLayout layoutEmail, layoutPassword, layoutConfirm;
    private TextInputEditText editEmail, editPassword, editConfirm;
    private Button buttonPrimary, buttonGuest;
    private Button buttonGoogle;
    private TextView textToggle, textForgot, textStatus;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();

        // Already chose guest, or already logged in with a verified account: skip this screen.
        // Unless force_show was set: the user opened login deliberately (e.g. "Log in" from main).
        boolean forceShow = getIntent().getBooleanExtra(EXTRA_FORCE_SHOW, false);
        if (!forceShow && shouldSkipLogin()) {
            openMain();
            return;
        }

        setContentView(R.layout.activity_login);
        layoutEmail = findViewById(R.id.layoutEmail);
        layoutPassword = findViewById(R.id.layoutPassword);
        layoutConfirm = findViewById(R.id.layoutConfirm);
        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        editConfirm = findViewById(R.id.editConfirm);
        buttonPrimary = findViewById(R.id.buttonPrimary);
        buttonGuest = findViewById(R.id.buttonGuest);
        buttonGoogle = findViewById(R.id.buttonGoogle);
        buttonGoogle.setOnClickListener(v -> startGoogleSignIn());
        textToggle = findViewById(R.id.textToggle);
        textForgot = findViewById(R.id.textForgot);
        textStatus = findViewById(R.id.textStatus);
        progress = findViewById(R.id.progress);

        buttonPrimary.setOnClickListener(v -> onSubmit());
        buttonGuest.setOnClickListener(v -> continueAsGuest());
        textToggle.setOnClickListener(v -> {
            signUpMode = !signUpMode;
            clearMessages();
            updateModeUi();
        });
        textForgot.setOnClickListener(v -> sendPasswordReset());
        updateModeUi();
    }

    // ---------- Routing ----------

    private boolean shouldSkipLogin() {
        FirebaseUser u = auth.getCurrentUser();
        boolean loggedIn = u != null && !u.isAnonymous() && u.isEmailVerified();
        boolean guest = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_GUEST, false);
        return loggedIn || guest;
    }

    private void openMain() {
        Intent i = new Intent(this, MainActivity.class);
        // Fresh task: no stale MainActivity underneath, and Back won't return to login
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void setGuestFlag(boolean value) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_GUEST, value).apply();
    }

    private void continueAsGuest() {
        FirebaseUser u = auth.getCurrentUser();
        if (u != null && !u.isAnonymous()) auth.signOut(); // never reuse a half-finished real account
        setGuestFlag(true);
        AuthHelper.ensureSignedIn(); // silent anonymous user so requests/reports still work
        openMain();
    }

    // ---------- Submit ----------

    private void onSubmit() {
        clearMessages();
        String email = text(editEmail);
        String pass = text(editPassword);

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            layoutEmail.setError(getString(R.string.error_email));
            return;
        }
        if (pass.length() < 6) {
            layoutPassword.setError(getString(R.string.error_password_short));
            return;
        }
        if (signUpMode) {
            if (!pass.equals(text(editConfirm))) {
                layoutConfirm.setError(getString(R.string.error_password_mismatch));
                return;
            }
            signUp(email, pass);
        } else {
            logIn(email, pass);
        }
    }

    private void signUp(String email, String pass) {
        setLoading(true);
        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                setLoading(false);
                showStatus(friendlyError(task.getException()));
                return;
            }
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) { setLoading(false); return; }

            user.sendEmailVerification().addOnCompleteListener(t -> {
                auth.signOut(); // they must verify before they can use the account
                setLoading(false);
                signUpMode = false;
                updateModeUi();
                showStatus(getString(R.string.status_verify_sent, email));
            });
        });
    }

    private void logIn(String email, String pass) {
        setLoading(true);
        auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                setLoading(false);
                showStatus(friendlyError(task.getException()));
                return;
            }
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) { setLoading(false); return; }

            // reload() refreshes the "email verified" flag from the server
            user.reload().addOnCompleteListener(r -> {
                if (user.isEmailVerified()) {
                    setGuestFlag(false);
                    // Download this account's list and merge in anything saved as a guest, then continue
                    CloudListSync.syncNow(getApplicationContext(),
                            WatchPathApp.getDatabase().mediaDao(), this::openMain);
                } else {
                    setLoading(false);
                    showUnverifiedDialog(user);
                }
            });
        });
    }

    private void showUnverifiedDialog(FirebaseUser user) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_unverified_title)
                .setMessage(R.string.dialog_unverified_msg)
                .setPositiveButton(R.string.action_resend, (d, w) ->
                        user.sendEmailVerification().addOnCompleteListener(t -> {
                            auth.signOut();
                            showStatus(getString(R.string.status_resent));
                        }))
                .setNegativeButton(android.R.string.ok, (d, w) -> auth.signOut())
                .setOnCancelListener(d -> auth.signOut())
                .show();
    }

    private void sendPasswordReset() {
        clearMessages();
        String email = text(editEmail);
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            layoutEmail.setError(getString(R.string.error_email));
            return;
        }
        auth.sendPasswordResetEmail(email).addOnCompleteListener(t ->
                // Same message whether or not the account exists (avoids leaking who has an account)
                showStatus(getString(R.string.status_reset_sent)));
    }

    // ---------- Google sign-in (Credential Manager) ----------

    private void startGoogleSignIn() {
        clearMessages();
        setLoading(true);

        GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)   // show every Google account on the device
                .setServerClientId(getString(R.string.default_web_client_id))
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();

        CredentialManager.create(this).getCredentialAsync(
                this, request, new CancellationSignal(), ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleGoogleCredential(result.getCredential());
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        setLoading(false);
                        Log.e(TAG, "Google sign-in failed", e);
                        if (e instanceof NoCredentialException) {
                            showStatus(getString(R.string.error_google_no_account));
                        } else if (!(e instanceof GetCredentialCancellationException)) {
                            showStatus(getString(R.string.error_google_failed)); // cancel = say nothing
                        }
                    }
                });
    }

    private void handleGoogleCredential(Credential credential) {
        try {
            if (credential instanceof CustomCredential
                    && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                GoogleIdTokenCredential google =
                        GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
                firebaseAuthWithGoogle(google.getIdToken());
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not read Google credential", e);
        }
        setLoading(false);
        showStatus(getString(R.string.error_google_failed));
    }

    private void firebaseAuthWithGoogle(String idToken) {
        // If a guest (anonymous) session is active, end it first. Otherwise Firebase tries to
        // "upgrade" the guest into this Google account, which fails with
        // "credential already in use" once the Google account already exists in the project.
        FirebaseUser current = auth.getCurrentUser();
        if (current != null && current.isAnonymous()) {
            auth.signOut();
        }

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                setLoading(false);
                showStatus(friendlyError(task.getException()));
                return;
            }
            setGuestFlag(false);
            // Same as email login: pull the account's list, merge any guest items, then continue
            CloudListSync.syncNow(getApplicationContext(),
                    WatchPathApp.getDatabase().mediaDao(), this::openMain);
        });
    }

    // ---------- UI helpers ----------

    private void updateModeUi() {
        layoutConfirm.setVisibility(signUpMode ? View.VISIBLE : View.GONE);
        textForgot.setVisibility(signUpMode ? View.GONE : View.VISIBLE);
        buttonPrimary.setText(signUpMode ? R.string.action_sign_up : R.string.action_log_in);
        textToggle.setText(signUpMode ? R.string.toggle_to_login : R.string.toggle_to_signup);
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        buttonPrimary.setEnabled(!loading);
        buttonGuest.setEnabled(!loading);
        buttonGoogle.setEnabled(!loading);
    }

    private void showStatus(String message) {
        textStatus.setText(message);
        textStatus.setVisibility(View.VISIBLE);
    }

    private void clearMessages() {
        layoutEmail.setError(null);
        layoutPassword.setError(null);
        layoutConfirm.setError(null);
        textStatus.setVisibility(View.GONE);
    }

    private String text(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private String friendlyError(Exception e) {
        if (e instanceof FirebaseNetworkException) return getString(R.string.error_network);
        if (e instanceof FirebaseAuthUserCollisionException) return getString(R.string.error_email_in_use);
        if (e instanceof FirebaseAuthWeakPasswordException) return getString(R.string.error_password_short);
        if (e instanceof FirebaseAuthInvalidCredentialsException
                || e instanceof FirebaseAuthInvalidUserException) return getString(R.string.error_bad_login);
        if (e instanceof FirebaseTooManyRequestsException) return getString(R.string.error_too_many);
        Log.e(TAG, "Auth error", e);
        return getString(R.string.error_generic);
    }
}