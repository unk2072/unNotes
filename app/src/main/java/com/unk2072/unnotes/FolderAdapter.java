package com.unk2072.unnotes;

import java.util.List;

import android.content.Context;
import android.os.Build;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.dropbox.sync.android.DbxFileInfo;

class FolderAdapter extends BaseAdapter {
    private final List<DbxFileInfo> mEntries;
    private final LayoutInflater mInflater;
    private final Context mContext;

    public FolderAdapter(Context context, List<DbxFileInfo> entries) {
        mEntries = entries;
        mContext = context;
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return mEntries.size();
    }

    @Override
    public Object getItem(int position) {
        return mEntries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            int id;
            if (Build.VERSION.SDK_INT >= 11) {
                id = android.R.layout.simple_list_item_activated_2;
            } else {
                id = android.R.layout.simple_list_item_2;
            }
            convertView = mInflater.inflate(id, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder)convertView.getTag();
        }

        DbxFileInfo info = mEntries.get(position);
        holder.text1.setText(Util.stripExtension("md", info.path.getName()));
        if (info.isFolder) {
            holder.text2.setText(R.string.status_folder);
        } else {
            holder.text2.setText(DateFormat.getMediumDateFormat(mContext).format(info.modifiedTime) + " " + DateFormat.getTimeFormat(mContext).format(info.modifiedTime));
        }
        return convertView;
    }

    private static class ViewHolder {
        final TextView text1;
        final TextView text2;

        public ViewHolder(View view) {
            text1 = (TextView)view.findViewById(android.R.id.text1);
            text2 = (TextView)view.findViewById(android.R.id.text2);
        }
    }
}
