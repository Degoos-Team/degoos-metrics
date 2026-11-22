package com.degoos.metrics

/** Failures a collector or request may survive: exceptions and linkage errors (a missing class of an optional plugin), never a VirtualMachineError. */
internal fun Throwable.isRecoverable(): Boolean = this is Exception || this is LinkageError
