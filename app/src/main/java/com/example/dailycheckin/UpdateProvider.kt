package com.example.dailycheckin

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/**
 * 极简文件共享 Provider（替代 androidx FileProvider，零依赖）
 * 仅共享应用私有下载目录里的更新包，用于 ACTION_VIEW 调起系统安装器
 * content://com.example.dailycheckin.updateprovider/update.apk
 */
class UpdateProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.example.dailycheckin.updateprovider"
        val UPDATE_URI: Uri = Uri.parse("content://$AUTHORITY/update.apk")

        fun updateFile(context: android.content.Context): File =
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "daily-tasks-update.apk")
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "application/vnd.android.package-archive"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = updateFile(context!!)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        val file = updateFile(context!!)
        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        cursor.addRow(arrayOf<Any>(file.name, file.length()))
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
