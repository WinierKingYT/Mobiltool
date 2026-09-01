package com.personaltool.core.common.qualification

data class QualificationCheck(
    val category: String,
    val checkName: String,
    val isPassed: Boolean,
    val details: String
)

data class QualificationReport(
    val timestamp: Long = System.currentTimeMillis(),
    val totalChecks: Int,
    val passedChecks: Int,
    val failedChecks: Int,
    val isFullyQualified: Boolean,
    val checks: List<QualificationCheck>
)
