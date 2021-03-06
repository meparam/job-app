package com.byteshaft.jobapp.accounts;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.byteshaft.jobapp.MainActivity;
import com.byteshaft.jobapp.R;
import com.byteshaft.jobapp.utils.AppGlobals;
import com.byteshaft.jobapp.utils.Helpers;
import com.byteshaft.requests.HttpRequest;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.FacebookSdkNotInitializedException;
import com.facebook.Profile;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;

public class Login extends Fragment implements View.OnClickListener, HttpRequest.OnErrorListener,
        HttpRequest.OnReadyStateChangeListener {
    private View mBaseView;
    private EditText mEmail;
    private EditText mPassword;
    private Button mLoginButton;
    private TextView mForgotPasswordTextView;
    private TextView mSignUpTextView;
    private String mPasswordString;
    private String mEmailString;
    private HttpRequest request;

    private LoginButton loginButton;
    private CallbackManager callbackManager;
    private FacebookCallback<LoginResult> callback;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FacebookSdk.sdkInitialize(getActivity().getApplicationContext());
        mBaseView = inflater.inflate(R.layout.fragment_login, container, false);
        setHasOptionsMenu(true);
        mEmail = (EditText) mBaseView.findViewById(R.id.email_edit_text);
        mPassword = (EditText) mBaseView.findViewById(R.id.password_edit_text);
        mLoginButton = (Button) mBaseView.findViewById(R.id.button_login);
        mForgotPasswordTextView = (TextView) mBaseView.findViewById(R.id.forgot_password_text_view);
        mSignUpTextView = (TextView) mBaseView.findViewById(R.id.sign_up_text_view);

        mLoginButton.setOnClickListener(this);
        mForgotPasswordTextView.setOnClickListener(this);
        mSignUpTextView.setOnClickListener(this);
        return mBaseView;
    }

    public boolean validate() {
        boolean valid = true;

        mEmailString = mEmail.getText().toString();
        mPasswordString = mPassword.getText().toString();

        System.out.println(mEmailString);
        System.out.println(mPasswordString);

        if (mEmailString.trim().isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(mEmailString).matches()) {
            mEmail.setError("please provide a valid email");
            valid = false;
        } else {
            mEmail.setError(null);
        }
        if (mPasswordString.isEmpty() || mPassword.length() < 4) {
            mPassword.setError("Enter minimum 4 alphanumeric characters");
            valid = false;
        } else {
            mPassword.setError(null);
        }
        return valid;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_login:
                if (validate()) {
                    loginUser(mEmailString, mPasswordString);
                }
                break;
            case R.id.forgot_password_text_view:
                AccountManager.getInstance().loadFragment(new ForgotPassword());
                break;
            case R.id.sign_up_text_view:
                AccountManager.getInstance().loadFragment(new Register());
                break;

        }
    }

    private void loginUser(String email, String password) {
        request = new HttpRequest(getActivity());
        request.setOnReadyStateChangeListener(this);
        request.setOnErrorListener(this);
        request.open("POST", String.format("%slogin", AppGlobals.BASE_URL));
        request.send(getUserLoginData(email, password));
        Helpers.showProgressDialog(getActivity(), "Logging In..");
    }

    private String getUserLoginData(String email, String password) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("email", email);
            jsonObject.put("password", password);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject.toString();

    }

    @Override
    public void onError(HttpRequest request, int readyState, short error, Exception exception) {
        Helpers.dismissProgressDialog();
        switch (readyState) {
            case HttpRequest.ERROR_CONNECTION_TIMED_OUT:
                Helpers.showSnackBar(getView(), "connection time out");
                break;
            case HttpRequest.ERROR_NETWORK_UNREACHABLE:
                Helpers.showSnackBar(getView(), exception.getLocalizedMessage());
                break;
        }

    }

    @Override
    public void onReadyStateChange(HttpRequest request, int readyState) {
        switch (readyState) {
            case HttpRequest.STATE_DONE:
                Helpers.dismissProgressDialog();
                switch (request.getStatus()) {
                    case HttpRequest.ERROR_NETWORK_UNREACHABLE:
                        AppGlobals.alertDialog(getActivity(), getString(R.string.login_failed), getString(R.string.check_internet));
                        break;
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        AppGlobals.alertDialog(getActivity(), getString(R.string.login_failed), getString(R.string.email_not_exist));
                        break;
                    case HttpURLConnection.HTTP_UNAUTHORIZED:
                        AppGlobals.alertDialog(getActivity(), getString(R.string.login_failed), getString(R.string.check_password));
                        break;
                    case HttpURLConnection.HTTP_FORBIDDEN:
                        Helpers.showSnackBar(getView(), R.string.activate_your_account);
                        AccountManager.getInstance().loadFragment(new AccountActivationCode());
                        break;

                    case HttpURLConnection.HTTP_OK:
                        System.out.println(request.getResponseText() + "working ");
                        try {
                            JSONObject jsonObject = new JSONObject(request.getResponseText());
                            String token = jsonObject.getString(AppGlobals.KEY_TOKEN);
                            String userId = jsonObject.getString(AppGlobals.KEY_USER_ID);
                            String email = jsonObject.getString(AppGlobals.KEY_EMAIL);

                            //saving values
                            AppGlobals.saveDataToSharedPreferences(AppGlobals.KEY_EMAIL, email);
                            AppGlobals.saveDataToSharedPreferences(AppGlobals.KEY_USER_ID, userId);
                            AppGlobals.saveDataToSharedPreferences(AppGlobals.KEY_TOKEN, token);
                            Log.i("token", " " + AppGlobals.getStringFromSharedPreferences(AppGlobals.KEY_TOKEN));
                            AppGlobals.loginState(true);
                            gettingUserData();
                            AccountManager.getInstance().finish();
                            startActivity(new Intent(getActivity(), MainActivity.class));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                }
        }
    }

    private void gettingUserData() {
        HttpRequest request = new HttpRequest(AppGlobals.getContext());
        request.setOnReadyStateChangeListener(new HttpRequest.OnReadyStateChangeListener() {
            @Override
            public void onReadyStateChange(HttpRequest request, int readyState) {
                switch (readyState) {
                    case HttpRequest.STATE_DONE:
                        switch (request.getStatus()) {
                            case HttpURLConnection.HTTP_OK:
                                try {
                                    JSONObject jsonObject = new JSONObject(request.getResponseText());
                                    String userId = jsonObject.getString(AppGlobals.KEY_USER_ID);
                                    String email = jsonObject.getString(AppGlobals.KEY_EMAIL);
                                    String UserName = jsonObject.getString(AppGlobals.KEY_USER_NAME);
                                    String skills = jsonObject.getString(AppGlobals.KEY_SKILLS);

                                    //saving values
                                    AppGlobals.saveDataToSharedPreferences(AppGlobals.KEY_EMAIL, email);
                                    AppGlobals.saveDataToSharedPreferences(AppGlobals.KEY_USER_ID, userId);
                                    AppGlobals.saveDataToSharedPreferences(AppGlobals.KEY_USER_NAME, UserName);
                                    AppGlobals.saveDataToSharedPreferences(AppGlobals.KEY_SKILLS, skills);
                                    Log.i("closingTime", " " + AppGlobals.getStringFromSharedPreferences(AppGlobals.KEY_USER_NAME));
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                        }
                }

            }
        });
        request.setOnErrorListener(new HttpRequest.OnErrorListener() {
            @Override
            public void onError(HttpRequest request, int readyState, short error, Exception exception) {
                Helpers.dismissProgressDialog();
                switch (readyState) {
                    case HttpRequest.ERROR_CONNECTION_TIMED_OUT:
                        Helpers.showSnackBar(getView(), "connection time out");
                        break;
                    case HttpRequest.ERROR_NETWORK_UNREACHABLE:
                        Helpers.showSnackBar(getView(), exception.getLocalizedMessage());
                        break;
                }
            }
        });
        request.open("GET", String.format("%sme", AppGlobals.BASE_URL));
        Log.i("Token", " " + AppGlobals.getStringFromSharedPreferences(AppGlobals.KEY_TOKEN));
        request.setRequestHeader("Authorization", "Token " +
                AppGlobals.getStringFromSharedPreferences(AppGlobals.KEY_TOKEN));
        request.send();
    }
}
