package org.xutils.sample.download;

import android.database.Cursor;

import com.jack.cache.db.converter.ColumnConverter;
import com.jack.cache.db.sqlite.ColumnDbType;

/**
 * Created by wyouflf on 15/11/10.
 */
public class DownloadStateConverter implements ColumnConverter<DownloadState> {

    @Override
    public DownloadState getFieldValue(Cursor cursor, int index) {
        int dbValue = cursor.getInt(index);
        return DownloadState.valueOf(dbValue);
    }

    @Override
    public Object fieldValue2DbValue(DownloadState fieldValue) {
        return fieldValue.value();
    }

    @Override
    public ColumnDbType getColumnDbType() {
        return ColumnDbType.INTEGER;
    }
}
