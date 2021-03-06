// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.


package org.chromium.chrome.browser.database;

import android.database.AbstractCursor;
import android.database.CursorWindow;
import android.util.Log;

import org.chromium.base.CalledByNative;

import java.sql.Types;

/**
 * This class exposes the query result from native side.
 */
public class SQLiteCursor extends AbstractCursor {
    private static final String TAG = "SQLiteCursor";
    // Used by JNI.
    private int mNativeSQLiteCursor;

    // The count of result rows.
    private int mCount = -1;

    private int[] mColumnTypes;

    private final Object mColumnTypeLock = new Object();

    // The belows are the locks for those methods that need wait for
    // the callback result in native side.
    private final Object mMoveLock = new Object();
    private final Object mGetBlobLock = new Object();

    private SQLiteCursor(int nativeSQLiteCursor) {
        mNativeSQLiteCursor = nativeSQLiteCursor;
    }

    @CalledByNative
    private static SQLiteCursor create(int nativeSQLiteCursor) {
        return new SQLiteCursor(nativeSQLiteCursor);
    }

    @Override
    public int getCount() {
        synchronized (mMoveLock) {
            if (mCount == -1)
                mCount = nativeGetCount(mNativeSQLiteCursor);
        }
        return mCount;
    }

    @Override
    public String[] getColumnNames() {
        return nativeGetColumnNames(mNativeSQLiteCursor);
    }

    @Override
    public String getString(int column) {
        return nativeGetString(mNativeSQLiteCursor, column);
    }

    @Override
    public short getShort(int column) {
        return (short) nativeGetInt(mNativeSQLiteCursor, column);
    }

    @Override
    public int getInt(int column) {
        return nativeGetInt(mNativeSQLiteCursor, column);
    }

    @Override
    public long getLong(int column) {
        return nativeGetLong(mNativeSQLiteCursor, column);
    }

    @Override
    public float getFloat(int column) {
        return (float)nativeGetDouble(mNativeSQLiteCursor, column);
    }

    @Override
    public double getDouble(int column) {
        return nativeGetDouble(mNativeSQLiteCursor, column);
    }

    @Override
    public boolean isNull(int column) {
        return nativeIsNull(mNativeSQLiteCursor, column);
    }

    @Override
    public void close() {
        super.close();
        nativeDestroy(mNativeSQLiteCursor);
        mNativeSQLiteCursor = 0;
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        synchronized (mMoveLock) {
            nativeMoveTo(mNativeSQLiteCursor, newPosition);
        }
        return super.onMove(oldPosition, newPosition);
    }

    @Override
    public byte[] getBlob(int column) {
        synchronized (mGetBlobLock) {
            return nativeGetBlob(mNativeSQLiteCursor, column);
        }
    }

    @Deprecated
    public boolean supportsUpdates() {
        return false;
    }

    @Override
    protected void finalize() {
        super.finalize();
        if (!isClosed()) {
            Log.w(TAG, "Cursor hasn't been closed");
            close();
        }
    }

    @Override
    public void fillWindow(int position, CursorWindow window) {
        if (position < 0 || position > getCount()) {
            return;
        }
        window.acquireReference();
        try {
            int oldpos = mPos;
            mPos = position - 1;
            window.clear();
            window.setStartPosition(position);
            int columnNum = getColumnCount();
            window.setNumColumns(columnNum);
            while (moveToNext() && window.allocRow()) {
                for (int i = 0; i < columnNum; i++) {
                    boolean hasRoom = true;
                    switch (getColumnType(i)) {
                        case Types.DOUBLE:
                            hasRoom = fillRow(window, Double.valueOf(getDouble(i)), mPos, i);
                            break;
                        case Types.NUMERIC:
                            hasRoom = fillRow(window, Long.valueOf(getLong(i)), mPos, i);
                            break;
                        case Types.BLOB:
                            hasRoom = fillRow(window, getBlob(i), mPos, i);
                            break;
                        case Types.LONGVARCHAR:
                            hasRoom = fillRow(window, getString(i), mPos, i);
                            break;
                        case Types.NULL:
                            hasRoom = fillRow(window, null, mPos, i);
                            break;
                    }
                    if (!hasRoom) {
                        break;
                    }
                }
            }
            mPos = oldpos;
        } catch (IllegalStateException e) {
            // simply ignore it
        } finally {
            window.releaseReference();
        }
    }

    /**
     * Fill row with the given value. If the value type is other than Long,
     * String, byte[] or Double, the NULL will be filled.
     *
     * @return true if succeeded, false if window is full.
     */
    private boolean fillRow(CursorWindow window, Object value, int pos, int column) {
        if (putValue(window, value, pos, column)) {
            return true;
        } else {
            window.freeLastRow();
            return false;
        }
    }

    /**
     * Put the value in given window. If the value type is other than Long,
     * String, byte[] or Double, the NULL will be filled.
     *
     * @return true if succeeded.
     */
    private boolean putValue(CursorWindow window, Object value, int pos, int column) {
        if (value == null) {
            return window.putNull(pos, column);
        } else if (value instanceof Long) {
            return window.putLong((Long) value, pos, column);
        } else if (value instanceof String) {
            return window.putString((String) value, pos, column);
        } else if (value instanceof byte[] && ((byte[]) value).length > 0) {
            return window.putBlob((byte[]) value, pos, column);
        } else if (value instanceof Double) {
            return window.putDouble((Double) value, pos, column);
        } else {
            return window.putNull(pos, column);
        }
    }

    /**
     * @param index the column index.
     * @return the column type from cache or native side.
     */
    private int getColumnType(int index) {
        synchronized (mColumnTypeLock) {
            if (mColumnTypes == null) {
                int columnCount = getColumnCount();
                mColumnTypes = new int[columnCount];
                for (int i = 0; i < columnCount; i++) {
                    mColumnTypes[i] = nativeGetColumnType(mNativeSQLiteCursor, i);
                }
            }
        }
        return mColumnTypes[index];
    }

    private native void nativeDestroy(int nativeSQLiteCursor);
    private native int nativeGetCount(int nativeSQLiteCursor);
    private native String[] nativeGetColumnNames(int nativeSQLiteCursor);
    private native int nativeGetColumnType(int nativeSQLiteCursor, int column);
    private native String nativeGetString(int nativeSQLiteCursor, int column);
    private native byte[] nativeGetBlob(int nativeSQLiteCursor, int column);
    private native boolean nativeIsNull(int nativeSQLiteCursor, int column);
    private native long nativeGetLong(int nativeSQLiteCursor, int column);
    private native int nativeGetInt(int nativeSQLiteCursor, int column);
    private native double nativeGetDouble(int nativeSQLiteCursor, int column);
    private native int nativeMoveTo(int nativeSQLiteCursor, int newPosition);
}
