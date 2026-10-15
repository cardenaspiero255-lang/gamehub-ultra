package com.cardenaspiero255.gamehubultra.integration

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder

/**
 * Signature-protected, read-only on-device handshake with JARVIS.
 * Never calls tool execution or returns internal games, memory or telemetry.
 */
class JarvisStatusProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor {
        val app = context ?: throw SecurityException("provider unavailable")
        val callerPackages = app.packageManager.getPackagesForUid(Binder.getCallingUid())
            ?.toSet() ?: emptySet()
        val declaredCaller = try { callingPackage } catch (_: SecurityException) { null }
        val hasSignaturePermission = app.checkCallingPermission(JarvisStatusContract.PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
        val path = uri.pathSegments.joinToString("/")
        if (uri.scheme != "content" || uri.authority != JarvisStatusContract.AUTHORITY ||
            uri.query != null || uri.fragment != null ||
            projection != null || selection != null || selectionArgs != null ||
            sortOrder != null || declaredCaller != JarvisStatusContract.JARVIS_PACKAGE ||
            !JarvisStatusContract.authorize(
                callerPackages, hasSignaturePermission, path
            )
        ) throw SecurityException("unauthorized read-only caller")

        val reply = JarvisStatusContract.reply()
        return MatrixCursor(arrayOf(
            "schemaVersion", "appPackage", "bridgeMode",
            "remoteExecutionEnabled", "toolExecutionEnabled"
        ), 1).apply {
            addRow(arrayOf(
                reply.version, reply.packageName, reply.status,
                if (reply.remoteExecutionEnabled) 1 else 0,
                if (reply.toolExecutionEnabled) 1 else 0,
            ))
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw SecurityException("JARVIS status provider is read-only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int =
        throw SecurityException("JARVIS status provider is read-only")

    override fun delete(uri: Uri, selection: String?,
                        selectionArgs: Array<out String>?): Int =
        throw SecurityException("JARVIS status provider is read-only")

    override fun call(method: String, arg: String?, extras: android.os.Bundle?): android.os.Bundle? =
        throw SecurityException("arbitrary provider calls are forbidden")
}
