package com.unk2072.unnotes;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;

public class NoteListActivity extends ActionBarActivity implements NoteListFragment.Callbacks {
    public static final String EXTRA_PATH = "path";

    private boolean mTwoPane;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_list);

        if (savedInstanceState == null) {
            String path = getIntent().getStringExtra(EXTRA_PATH);
            NoteListFragment fragment = NoteListFragment.getInstance(path);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.note_list, fragment)
                    .commit();
        }

        if (findViewById(R.id.note_detail_container) != null) {
            mTwoPane = true;
            ((NoteListFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.note_list))
                    .setActivateOnItemClick(true);
        }
    }

    @Override
    public void onItemSelected(String path, boolean isFolder) {
        if (isFolder) {
            NoteListFragment fragment = NoteListFragment.getInstance(path);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.note_list, fragment)
                    .addToBackStack(null)
                    .commit();
        } else {
            if (mTwoPane) {
                NoteDetailFragment fragment = NoteDetailFragment.getInstance(path);
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
            FragmentManager fragmentManager = getSupportFragmentManager();
            fragmentManager.popBackStack();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
