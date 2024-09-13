package com.tans.beambox.file

import androidx.annotation.Keep

@Keep
sealed class FileLeaf(
) {
    abstract val name: String
    abstract val path: String
    abstract val lastModified: Long
    @Keep
    data class CommonFileLeaf(
        override val name: String,
        override val path: String,
        override val lastModified: Long,
        val size: Long) : FileLeaf()

    @Keep
    data class DirectoryFileLeaf(
        override val name: String,
        override val path: String,
        override val lastModified: Long,
        val childrenCount: Long) : FileLeaf()
}