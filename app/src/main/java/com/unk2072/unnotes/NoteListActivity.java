package com.unk2072.unnotes;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.view.KeyEvent;
import android.view.MenuItem;

public class NoteListActivity extends ActionBarActivity implements NoteListFragment.Callbacks {
    public static final String EXTRA_PATH = "path";

    private boolean mTwoPane = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_list);

        mTwoPane = findViewById(R.id.note_detail_container) != null;

        if (savedInstanceState == null) {
            String path = getIntent().getStringExtra(EXTRA_PATH);
            NoteListFragment fragment = NoteListFragment.getInstance(path);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.note_list, fragment)
                    .commit();
        }
    }

    @Override
    public void onItemSelected(String path, boolean isFolder) {
        if (isFolder) {
            Fragment fragment;
            if (mTwoPane) {
                fragment = getSupportFragmentManager().findFragmentById(R.id.note_detail_container);
                if (fragment != null) {
                    getSupportFragmentManager().beginTransaction()
                            .remove(fragment)
                            .commit();
                }
            }
            fragment = NoteListFragment.getInstance(path);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.note_list, fragment)
                    .addToBackStack(null)
                    .commit();
        } else {
            if (mTwoPane) {
                Fragment fragment = NoteDetailFragment.getInstance(path);
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.note_detail_container, fragment)
                        .commit();
            } else {
                Intent intent = new Intent(this, NoteDetailActivity.class);
                intent.putExtra(EXTRA_PATH, path);
                startActivity(intent);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getSupportFragmentManager().popBackStack();
            if (mTwoPane) {
                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.note_detail_container);
                if (fragment != null) {
                    getSupportFragmentManager().beginTransaction()
                            .remove(fragment)
                            .commit();
                }
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mTwoPane) {
                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.note_detail_container);
                if (fragment != null) {
                    getSupportFragmentManager().beginTransaction()
                            .remove(fragment)
                            .commit();
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
