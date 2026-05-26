package com.github.caspervg.dbpfmcp.core

sealed class DbpfException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class InputError(message: String, cause: Throwable? = null) : DbpfException(message, cause)

class PackageError(message: String, cause: Throwable? = null) : DbpfException(message, cause)

class DecodeError(message: String, cause: Throwable? = null) : DbpfException(message, cause)
