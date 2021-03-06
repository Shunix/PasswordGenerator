package com.shunix.encryptor.activity;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.shunix.encryptor.R;
import com.shunix.encryptor.database.DatabaseManager;
import com.shunix.encryptor.utils.AESEncryptor;

import java.lang.ref.WeakReference;

/**
 * @author shunix
 * @since 2015/11/05
 */
public class AddPasswordActivity extends BaseActivity {
    private EditText mEditText;
    private ProgressBar mProgressBar;
    private Animation mShakeAnim;
    private int mPasswordLength;
    private static final String TAG = AddPasswordActivity.class.getName();

    TextView.OnEditorActionListener mListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
            if (i == EditorInfo.IME_ACTION_DONE
                    || i == EditorInfo.IME_ACTION_SEND
                    || (keyEvent != null && KeyEvent.KEYCODE_ENTER == keyEvent.getKeyCode() && keyEvent.getAction() == KeyEvent.ACTION_DOWN)) {
                String text = mEditText.getText().toString();
                if (TextUtils.isEmpty(text)) {
                    mEditText.startAnimation(mShakeAnim);
                } else {
                    try {
                        PasswordTask task = new PasswordTask(AddPasswordActivity.this);
                        task.execute(text);
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
            }
            return false;
        }
    };

    static class PasswordTask extends AsyncTask<String, Void, String[]> {
        private WeakReference<AddPasswordActivity> mActivity;

        public PasswordTask(AddPasswordActivity activity) {
            mActivity = new WeakReference<AddPasswordActivity>(activity);
        }

        @Override
        protected String[] doInBackground(String... strings) {
            if (mActivity.get() == null) {
                return null;
            }
            final AddPasswordActivity activity = mActivity.get();
            String[] params = strings.clone();
            String encryptedPwd = null;
            if (params.length != 1) {
                cancel(true);
            }
            if (!TextUtils.isEmpty(params[0])) {
                DatabaseManager databaseManager = new DatabaseManager(activity.mApp);
                try {
                    String result = databaseManager.queryPassword(params[0]);
                    if (!TextUtils.isEmpty(result)) {
                        mActivity.get().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(activity, activity.getString(R.string.already_exists), Toast.LENGTH_SHORT).show();
                            }
                        });
                        cancel(true);
                    } else {
                        String rootPwd = activity.mApp.getRootPwdInMemory();
                        if (!TextUtils.isEmpty(rootPwd)) {
                            long timestamp = System.currentTimeMillis();
                            AESEncryptor encryptor = new AESEncryptor(rootPwd);
                            encryptedPwd = encryptor.encrypt(params[0] + timestamp);
                            if (encryptedPwd.length() > activity.mPasswordLength) {
                                encryptedPwd = encryptedPwd.substring(0, activity.mPasswordLength);
                            }
                            boolean dbResult = databaseManager.insertPassword(params[0], encryptedPwd, timestamp);
                            if (!dbResult) {
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(activity, activity.getString(R.string.db_op_failed), Toast.LENGTH_SHORT).show();
                                    }
                                });
                                cancel(true);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                } finally {
                    databaseManager.close();
                }
            }
            return new String[] {params[0], encryptedPwd};
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            if (mActivity.get() != null) {
                mActivity.get().hideProgressBar();
            }
        }

        @Override
        protected void onPostExecute(String[] s) {
            super.onPostExecute(s);
            if (mActivity.get() != null) {
                mActivity.get().hideProgressBar();
            }
            startResultActivity(s[0], s[1]);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (mActivity.get() != null) {
                mActivity.get().showProgressBar();
            }
        }

        private void startResultActivity(String name, String pwd) {
            if (mActivity.get() != null) {
                AddPasswordActivity activity = mActivity.get();
                Intent intent = new Intent(activity, AddPasswordResultActivity.class);
                intent.putExtra(AddPasswordResultActivity.NAME_KEY, name);
                intent.putExtra(AddPasswordResultActivity.PWD_KEY, pwd);
                intent.putExtra(JUMP_WITHIN_APP, true);
                activity.startActivity(intent);
                activity.finish();
            }
        }
    }

    void showProgressBar() {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(View.VISIBLE);
        }
    }

    void hideProgressBar() {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_pwd_activity_layout);
        mShakeAnim = AnimationUtils.loadAnimation(mApp, R.anim.edit_text_shake);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mEditText = (EditText) findViewById(R.id.passText);
        mEditText.setOnEditorActionListener(mListener);
        String level = PreferenceManager.getDefaultSharedPreferences(mApp).getString(getString(R.string.pref_enc_key), getString(R.string.high_val));
        if (level.equals(getString(R.string.low_val))) {
            mPasswordLength = 8;
        } else if (level.equals(getString(R.string.mid_val))) {
            mPasswordLength = 12;
        } else {
            mPasswordLength = 16;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent intent = new Intent(AddPasswordActivity.this, PasswordListActivity.class);
        intent.putExtra(JUMP_WITHIN_APP, true);
        startActivity(intent);
        finish();
    }
}
