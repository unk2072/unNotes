package com.unk2072.unnotes;

import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxException;
import com.dropbox.sync.android.DbxFileInfo;
import com.dropbox.sync.android.DbxFileSystem;
import com.dropbox.sync.android.DbxPath;

public class NoteListFragment extends ListFragment implements LoaderCallbacks<List<DbxFileInfo>> {
    private static final String ARG_PATH = "path";
    private static final String STATE_ACTIVATED_POSITION = "activated_position";

    private static final int MENU_RENAME = 1;
    private static final int MENU_DELETE = 2;

    private Callbacks mCallbacks = sDummyCallbacks;
    private int mActivatedPosition = ListView.INVALID_POSITION;
    private View mView;
    private View mEmptyText;
    private View mLinkButton;
    private View mLoadingSpinner;
    private DbxAccountManager mAccountManager;

    private String mPath;
    private boolean mTwoPane;

    public interface Callbacks {
        public void onItemSelected(String path, boolean isFolder);
    }

    private static Callbacks sDummyCallbacks = new Callbacks() {
        @Override
        public void onItemSelected(String path, boolean isFolder) {
        }
    };

    public static NoteListFragment getInstance(String path) {
        NoteListFragment fragment = new NoteListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PATH, TextUtils.isEmpty(path) ? "" : path);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onResume() {
        super.onResume();

        boolean isRoot;
        if (TextUtils.isEmpty(mPath)) {
            getActivity().setTitle(R.string.app_name);
            isRoot = true;
        } else {
            getActivity().setTitle(new DbxPath(mPath).getName());
            isRoot = false;
        }
        ActionBar actionBar = ((ActionBarActivity)getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(!isRoot);
        }
        doLoad();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_note_list, container, false);
        mEmptyText = view.findViewById(R.id.empty_text);
        mLinkButton = view.findViewById(R.id.link_button);
        mLoadingSpinner = view.findViewById(R.id.list_loading);

        mLinkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAccountManager.startLinkFromSupportFragment(NoteListFragment.this, 0);
            }
        });
        mPath = getArguments().getString(ARG_PATH);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mView = view;
        if (!mAccountManager.hasLinkedAccount()) {
            showUnlinkedView();
        } else {
            showLinkedView();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mTwoPane = getActivity().findViewById(R.id.note_detail_container) != null;
        if (mTwoPane) {
            if (savedInstanceState != null) {
                mActivatedPosition = savedInstanceState.getInt(STATE_ACTIVATED_POSITION, ListView.INVALID_POSITION);
            }
            getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        }
        getListView().setEmptyView(mView.findViewById(android.R.id.empty));
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof Callbacks)) {
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        }
        mAccountManager = NotesAppConfig.getAccountManager(activity);
        mCallbacks = (Callbacks) activity;
        setHasOptionsMenu(true);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = sDummyCallbacks;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0) {
            if (resultCode == Activity.RESULT_OK) {
                showLinkedView();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mActivatedPosition != ListView.INVALID_POSITION) {
            outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
        }
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);
        DbxFileInfo info = (DbxFileInfo)getListAdapter().getItem(position);
        if (mTwoPane) {
            if (info.isFolder) {
                getListView().setItemChecked(mActivatedPosition = ListView.INVALID_POSITION, true);
            } else {
                mActivatedPosition = position;
            }
        }
        mCallbacks.onItemSelected(info.path.toString(), info.isFolder);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        menu.add(Menu.NONE, MENU_RENAME, Menu.NONE, R.string.menu_rename);
        menu.add(Menu.NONE, MENU_DELETE, Menu.NONE, R.string.menu_delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final DbxFileInfo info = (DbxFileInfo)getListAdapter().getItem(((AdapterView.AdapterContextMenuInfo)item.getMenuInfo()).position);

        int itemId = item.getItemId();
        if (itemId == MENU_RENAME) {
            final EditText input = new EditText(getActivity());
            input.setText(Util.stripExtension("md", info.path.getName()));
            input.setSelectAllOnFocus(true);

            new AlertDialog.Builder(getActivity())
                    .setView(input)
                    .setPositiveButton(R.string.rename_confirm, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            String name = input.getText().toString();
                            if (TextUtils.isEmpty(name)) {
                                return;
                            }
                            if (!name.endsWith(".md")) {
                                name += ".md";
                            }

                            DbxPath p;
                            try {
                                if (name.contains("/")) {
                                    Toast.makeText(getActivity(), R.string.error_invalid_name, Toast.LENGTH_LONG).show();
                                    return;
                                }
                                p = new DbxPath(mPath + "/" + name);
                            } catch (DbxPath.InvalidPathException e) {
                                Toast.makeText(getActivity(), R.string.error_invalid_name, Toast.LENGTH_LONG).show();
                                return;
                            }

                            try {
                                DbxFileSystem.forAccount(mAccountManager.getLinkedAccount()).move(info.path, p);
                            } catch (DbxException.Exists e) {
                                Toast.makeText(getActivity(), R.string.error_already_exists, Toast.LENGTH_LONG).show();
                            } catch (DbxException e) {
                                e.printStackTrace();
                            }
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else if (itemId == MENU_DELETE) {
            try {
                DbxFileSystem.forAccount(mAccountManager.getLinkedAccount()).delete(info.path);
            } catch (DbxException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (mAccountManager.hasLinkedAccount()) {
            MenuItem item = menu.add(0, 0, 1, R.string.new_folder_option);
            item.setIcon(R.drawable.ic_new_folder_option);
            MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
            item.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    final EditText input = new EditText(getActivity());
                    input.setHint(R.string.new_folder_name_hint);
                    input.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

                    new AlertDialog.Builder(getActivity())
                            .setView(input)
                            .setPositiveButton(R.string.new_confirm, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    String name = input.getText().toString();
                                    if (TextUtils.isEmpty(name)) {
                                        name = input.getHint().toString();
                                    }

                                    DbxPath p;
                                    try {
                                        if (name.contains("/")) {
                                            Toast.makeText(getActivity(), R.string.error_invalid_name, Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        p = new DbxPath(mPath + "/" + name);
                                    } catch (DbxPath.InvalidPathException e) {
                                        Toast.makeText(getActivity(), R.string.error_invalid_name, Toast.LENGTH_LONG).show();
                                        return;
                                    }

                                    try {
                                        DbxFileSystem.forAccount(mAccountManager.getLinkedAccount()).createFolder(p);
                                    } catch (DbxException.Exists e) {
                                        Toast.makeText(getActivity(), R.string.error_already_exists, Toast.LENGTH_LONG).show();
                                    } catch (DbxException e) {
                                        e.printStackTrace();
                                    }
                                    mCallbacks.onItemSelected(p.toString(), true);
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                    return true;
                }
            });

            item = menu.add(0, 0, 2, R.string.new_note_option);
            item.setIcon(R.drawable.ic_new_note_option);
            MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
            item.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    final EditText input = new EditText(getActivity());
                    input.setHint(R.string.new_note_name_hint);
                    input.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

                    new AlertDialog.Builder(getActivity())
                            .setView(input)
                            .setPositiveButton(R.string.new_confirm, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    String name = input.getText().toString();
                                    if (TextUtils.isEmpty(name)) {
                                        name = input.getHint().toString();
                                    }
                                    if (!name.endsWith(".md")) {
                                        name += ".md";
                                    }

                                    DbxPath p;
                                    try {
                                        if (name.contains("/")) {
                                            Toast.makeText(getActivity(), R.string.error_invalid_name, Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        p = new DbxPath(mPath + "/" + name);
                                    } catch (DbxPath.InvalidPathException e) {
                                        Toast.makeText(getActivity(), R.string.error_invalid_name, Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    mCallbacks.onItemSelected(p.toString(), false);
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                    return true;
                }
            });

            item = menu.add(0, 0, 5, R.string.settings);
            item.setIcon(R.drawable.ic_settings);
            MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
            item.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    WebView webView = new WebView(getActivity());
                    webView.loadUrl("file:///android_asset/about.html");

                    new AlertDialog.Builder(getActivity())
                            .setView(webView)
                            .setPositiveButton(R.string.close, null)
                            .setNegativeButton(R.string.unlink_from_dropbox, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    mAccountManager.unlink();
                                    setListAdapter(null);
                                    showUnlinkedView();
                                }
                            })
                            .show();
                    return true;
                }
            });
        }
    }

    @Override
    public Loader<List<DbxFileInfo>> onCreateLoader(int id, Bundle args) {
        if (TextUtils.isEmpty(mPath)) {
            return new FolderLoader(getActivity(), mAccountManager, DbxPath.ROOT);
        }
        return new FolderLoader(getActivity(), mAccountManager, new DbxPath(mPath));
    }

    @Override
    public void onLoadFinished(Loader<List<DbxFileInfo>> loader, List<DbxFileInfo> data) {
        mEmptyText.setVisibility(View.VISIBLE);
        mLoadingSpinner.setVisibility(View.GONE);

        setListAdapter(new FolderAdapter(getActivity(), data));
        if (mTwoPane) {
            getListView().setItemChecked(mActivatedPosition, true);
        }
        registerForContextMenu(getListView());
    }

    @Override
    public void onLoaderReset(Loader<List<DbxFileInfo>> loader) {
    }

    private void doLoad() {
        if (mAccountManager.hasLinkedAccount()) {
            mLoadingSpinner.setVisibility(View.VISIBLE);
            mEmptyText.setVisibility(View.GONE);
            getLoaderManager().restartLoader(0, null, this);
        }
    }

    private void showUnlinkedView() {
        mLinkButton.setVisibility(View.VISIBLE);
        mEmptyText.setVisibility(View.GONE);
        mLoadingSpinner.setVisibility(View.GONE);
        getActivity().supportInvalidateOptionsMenu();
        mView.postInvalidate();
    }

    private void showLinkedView() {
        mLoadingSpinner.setVisibility(View.VISIBLE);
        mEmptyText.setVisibility(View.GONE);
        mLinkButton.setVisibility(View.GONE);
        getActivity().supportInvalidateOptionsMenu();
        mView.postInvalidate();
        doLoad();
    }
}
