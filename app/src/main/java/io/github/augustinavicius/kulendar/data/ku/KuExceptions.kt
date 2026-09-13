package io.github.augustinavicius.kulendar.data.ku

sealed class KuException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The university rejected the username or password. Syncing cannot continue without the user. */
class KuInvalidCredentialsException(message: String) : KuException(message)

/** An access or refresh token was rejected; a new one has to be obtained. */
class KuUnauthorizedException(message: String) : KuException(message)

/** No university account has been set up in the app. */
class KuNotSignedInException : KuException("Not signed in")

/** The server failed or throttled the request. Worth retrying later. */
class KuServerException(val code: Int, message: String) : KuException(message)

/** The server could not be reached. Worth retrying later. */
class KuNetworkException(cause: Throwable) : KuException(cause.message ?: "Network error", cause)

/** The server answered with something this app does not understand. */
class KuProtocolException(message: String, cause: Throwable? = null) : KuException(message, cause)
