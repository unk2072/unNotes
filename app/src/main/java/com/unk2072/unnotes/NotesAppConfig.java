package com.unk2072.unnotes;

import android.content.Context;

import com.dropbox.sync.android.DbxAccountManager;

public final class NotesAppConfig {
    private NotesAppConfig() {}

    public static final String appKey = "9ncgcdsmjnzibzh";
    public static final String appSecret = "jowbxfk2dad9zs7";

    public static DbxAccountManager getAccountManager(Context context)
    {
        return DbxAccountManager.getInstance(context.getApplicationContext(), appKey, appSecret);
    }
}
