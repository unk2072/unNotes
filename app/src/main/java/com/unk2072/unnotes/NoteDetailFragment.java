package com.unk2072.unnotes;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.util.concurrent.Semaphore;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;

import com.dropbox.sync.android.DbxAccount;
import com.dropbox.sync.android.DbxException;
import com.dropbox.sync.android.DbxFile;
import com.dropbox.sync.android.DbxFileSystem;
import com.dropbox.sync.android.DbxPath;

public class NoteDetailFragment extends Fragment {
    private static final String TAG = NoteDetailFragment.class.getName();

    private static final String ARG_PATH = "path";
    private static final String STATE_EDIT = "edit";

    private EditText mText;
    private WebView mWebView;
    private TextView mErrorMessage;
    private View mLoadingSpinner;

    private final DbxLoadHandler mHandler = new DbxLoadHandler(this);

    private DbxFile mFile;
    private final Object mFileLock = new Object();
    private final Semaphore mFileUseSemaphore = new Semaphore(1);
    private boolean mUserHasModifiedText = false;
    private boolean mHasLoadedAnyData = false;
    private boolean mEditMode = false;

    private final DbxFile.Listener mChangeListener = new DbxFile.Listener() {
        @Override
        public void onFileChange(DbxFile file) {
            synchronized(mFileLock) {
                if (file != mFile) {
                    return;
                }
            }

            if (mUserHasModifiedText) {
                return;
            }

            boolean currentIsLatest;
            boolean newerIsCached = false;
            try {
                currentIsLatest = file.getSyncStatus().isLatest;

                if (!currentIsLatest) {
                    newerIsCached = file.getNewerStatus().isCached;
                }
            } catch (DbxException e) {
                Log.w(TAG, "Failed to get sync status", e);
                return;
            }

            if (newerIsCached || !mHasLoadedAnyData) {
                mHandler.sendDoUpdateMessage();
            }
        }
    };

    public NoteDetailFragment() {}

    public static NoteDetailFragment getInstance(String path) {
        NoteDetailFragment fragment = new NoteDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PATH, path);
        fragment.setArguments(args);
        return fragment;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_note_detail, container, false);

        if (savedInstanceState != null) {
            mEditMode = savedInstanceState.getBoolean(STATE_EDIT);
        }
        mText = (EditText)view.findViewById(R.id.note_detail);
        mText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mUserHasModifiedText = true;
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {}
        });
        mText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        mWebView = (WebView)view.findViewById(R.id.note_preview);
        mWebView.getSettings().setJavaScriptEnabled(true);
        if (Build.VERSION.SDK_INT >= 16) {
            mWebView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        }
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.loadUrl("javascript:setMarkdown('/data/data/" + getActivity().getPackageName() +"/files/preview.md')");
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }
        });

        mLoadingSpinner = view.findViewById(R.id.note_loading);
        mErrorMessage = (TextView)view.findViewById(R.id.error_message);

        setHasOptionsMenu(true);
        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_EDIT, mEditMode);
    }

    @Override
    public void onResume() {
        super.onResume();

        mText.setEnabled(false);
        mText.setText("");
        mUserHasModifiedText = false;
        mHasLoadedAnyData = false;

        DbxPath path = new DbxPath(getArguments().getString(ARG_PATH));

        String title = Util.stripExtension("md", path.getName());
        getActivity().setTitle(title);

        DbxAccount acct = NotesAppConfig.getAccountManager(getActivity()).getLinkedAccount();
        if (null == acct) {
            Log.e(TAG, "No linked account.");
            return;
        }

        mErrorMessage.setVisibility(View.GONE);
        mLoadingSpinner.setVisibility(View.VISIBLE);

        try {
            mFileUseSemaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            DbxFileSystem fs = DbxFileSystem.forAccount(acct);
            try {
                mFile = fs.open(path);
            } catch (DbxException.NotFound e) {
                mFile = fs.create(path);
            }
        } catch (DbxException e) {
            Log.e(TAG, "failed to open or create file.", e);
            return;
        }

        mFile.addListener(mChangeListener);
        mHandler.sendDoUpdateMessage();
    }

    @Override
    public void onPause() {
        super.onPause();

        synchronized(mFileLock) {
            mFile.removeListener(mChangeListener);

            if (mUserHasModifiedText && mFile != null) {
                final String newContents = mText.getText().toString();
                mUserHasModifiedText = false;

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "starting write");
                        synchronized (mFileLock) {
                            try {
                                mFile.writeString(newContents);
                            } catch (IOException e) {
                                Log.e(TAG, "failed to write to file", e);
                            }
                            mFile.close();
                            Log.d(TAG, "write done");
                            mFile = null;
                        }
                        mFileUseSemaphore.release();
                    }
                }).start();
            } else {
                mFile.close();
                mFile = null;
                mFileUseSemaphore.release();
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        MenuItem item;
        if (mEditMode) {
            item = menu.add(0, 0, 3, R.string.preview_mode);
            item.setIcon(R.drawable.ic_preview_mode);
            MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
            item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    mWebView.setVisibility(View.VISIBLE);
                    mText.setVisibility(View.GONE);
                    mEditMode = false;
                    getActivity().supportInvalidateOptionsMenu();
                    if (mUserHasModifiedText) {
                        applyNewTextToWebView(mText.getText().toString());
                    }
                    return true;
                }
            });
        } else {
            item = menu.add(0, 0, 3, R.string.edit_mode);
            item.setIcon(R.drawable.ic_edit_mode);
            MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
            item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    mText.setVisibility(View.VISIBLE);
                    mWebView.setVisibility(View.GONE);
                    mEditMode = true;
                    getActivity().supportInvalidateOptionsMenu();
                    return true;
                }
            });
        }

        item = menu.add(0, 0, 4, R.string.help);
        item.setIcon(R.drawable.ic_help);
        MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                WebView webView = new WebView(getActivity());
                webView.loadUrl("file:///android_asset/help.html");

                new AlertDialog.Builder(getActivity())
                        .setView(webView)
                        .setPositiveButton(R.string.close, null)
                        .show();
                return true;
            }
        });
    }

    private void startUpdateOnBackgroundThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (mFileLock) {
                    if (null == mFile || mUserHasModifiedText) {
                        return;
                    }
                    boolean updated;
                    try {
                        updated = mFile.update();
                    } catch (DbxException e) {
                        Log.e(TAG, "failed to update file", e);
                        mHandler.sendLoadFailedMessage(e.toString());
                        return;
                    }

                    if (!mHasLoadedAnyData || updated) {
                        Log.d(TAG, "starting read");
                        String contents;
                        try {
                            contents = mFile.readString();
                        } catch (IOException e) {
                            Log.e(TAG, "failed to read file", e);
                            if (!mHasLoadedAnyData) {
                                mHandler.sendLoadFailedMessage(getString(R.string.error_failed_load));
                            }
                            return;
                        }
                        Log.d(TAG, "read done");

                        if (contents != null) {
                            mHasLoadedAnyData = true;
                        }

                        mHandler.sendUpdateDoneWithChangesMessage(contents);
                    } else {
                        mHandler.sendUpdateDoneWithoutChangesMessage();
                    }
                }
            }
        }).start();
    }

    private void applyNewText(final String data) {
        if (mUserHasModifiedText || data == null) {
            return;
        }
        mText.setText(data);
        mUserHasModifiedText = false;

        applyNewTextToWebView(data);
    }

    private void applyNewTextToWebView(final String data) {
        try {
            OutputStreamWriter out = new OutputStreamWriter(getActivity().openFileOutput("preview.md", Context.MODE_PRIVATE));
            out.write(data);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mWebView.loadUrl("file:///android_asset/index.html");
    }

    private static class DbxLoadHandler extends Handler {

        private final WeakReference<NoteDetailFragment> mFragment;

        public static final int MESSAGE_DO_UPDATE = 1;
        public static final int MESSAGE_UPDATE_DONE = 2;
        public static final int MESSAGE_LOAD_FAILED = 3;

        public DbxLoadHandler(NoteDetailFragment containingFragment) {
            mFragment = new WeakReference<NoteDetailFragment>(containingFragment);
        }

        @Override
        public void handleMessage(Message msg) {
            NoteDetailFragment frag = mFragment.get();
            if (frag == null) {
                return;
            }

            if (msg.what == MESSAGE_DO_UPDATE) {
                if (frag.mUserHasModifiedText) {
                    return;
                }
                frag.mText.setEnabled(false);
                frag.startUpdateOnBackgroundThread();
            } else if (msg.what == MESSAGE_UPDATE_DONE) {
                if (frag.mUserHasModifiedText) {
                    Log.e(TAG, "Somehow user changed text while an update was in progress!");
                }

                if (frag.mEditMode) {
                    frag.mText.setVisibility(View.VISIBLE);
                    frag.mWebView.setVisibility(View.GONE);
                } else {
                    frag.mWebView.setVisibility(View.VISIBLE);
                    frag.mText.setVisibility(View.GONE);
                }
                frag.mLoadingSpinner.setVisibility(View.GONE);
                frag.mErrorMessage.setVisibility(View.GONE);

                boolean gotNewData = msg.arg1 != 0;
                if (gotNewData) {
                    String contents = (String)msg.obj;
                    frag.applyNewText(contents);
                }

                frag.mText.requestFocus();
                frag.mText.setEnabled(true);
            } else if (msg.what == MESSAGE_LOAD_FAILED) {
                String errorText = (String)msg.obj;
                frag.mText.setVisibility(View.GONE);
                frag.mWebView.setVisibility(View.GONE);
                frag.mLoadingSpinner.setVisibility(View.GONE);
                frag.mErrorMessage.setText(errorText);
                frag.mErrorMessage.setVisibility(View.VISIBLE);
            } else {
                throw new RuntimeException("Unknown message");
            }
        }

        public void sendDoUpdateMessage() {
            sendMessage(Message.obtain(this, MESSAGE_DO_UPDATE));
        }

        public void sendUpdateDoneWithChangesMessage(String newContents) {
            sendMessage(Message.obtain(this, MESSAGE_UPDATE_DONE, 1, -1, newContents));
        }

        public void sendUpdateDoneWithoutChangesMessage() {
            sendMessage(Message.obtain(this, MESSAGE_UPDATE_DONE, 0, -1));
        }

        public void sendLoadFailedMessage(String errorText) {
            sendMessage(Message.obtain(this, MESSAGE_LOAD_FAILED, errorText));
        }
    }

}
