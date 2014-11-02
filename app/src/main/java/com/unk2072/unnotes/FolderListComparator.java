package com.unk2072.unnotes;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

import com.dropbox.sync.android.DbxFileInfo;

public class FolderListComparator implements Comparator<DbxFileInfo> {
    private final boolean isNameFirst;
    private final boolean isAscending;

    public FolderListComparator(boolean nameFirst, boolean ascending) {
        isNameFirst = nameFirst;
        isAscending = ascending;
    }

    @Override
    public int compare(DbxFileInfo lhs, DbxFileInfo rhs) {
        int rawCmp = rawCompare(lhs, rhs);
        return isAscending ? rawCmp : -rawCmp;
    }

    int rawCompare(DbxFileInfo lhs, DbxFileInfo rhs) {
        if (lhs.isFolder != rhs.isFolder) {
            return lhs.isFolder ? -1 : 1;
        }

        if (isNameFirst) {
            int cmp = comparePaths(lhs, rhs);
            if (0 != cmp) return cmp;
            cmp = compareDates(lhs, rhs);
            if (0 != cmp) return cmp;
        } else {
            int cmp = compareDates(lhs, rhs);
            if (0 != cmp) return cmp;
            cmp = comparePaths(lhs, rhs);
            if (0 != cmp) return cmp;
        }

        long cmp = lhs.size - rhs.size;
        if (0 != cmp) return cmp < 0 ? -1 : 1;
        return 0;
    }

    private int comparePaths(DbxFileInfo lhs, DbxFileInfo rhs) {
        Collator c = Collator.getInstance(Locale.getDefault());
        c.setStrength(Collator.SECONDARY);
        return c.compare(lhs.toString(), rhs.toString());
    }

    private int compareDates(DbxFileInfo lhs, DbxFileInfo rhs) {
        return lhs.modifiedTime.compareTo(rhs.modifiedTime);
    }
}
