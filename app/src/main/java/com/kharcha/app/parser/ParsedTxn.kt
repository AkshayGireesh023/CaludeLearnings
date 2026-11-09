package com.kharcha.app.parser

import com.kharcha.app.data.TxnType

/** A transaction pulled out of an SMS or a notification, before it is stored. */
data class ParsedTxn(
    val amount: Double,
    val type: TxnType,
    val merchant: String?,
    val account: String? = null,
    val bank: String? = null,
    val balance: Double? = null,
    val refNo: String? = null,
    val isCard: Boolean = false,
    val isPersonVpa: Boolean = false,
    /** Credit card bill payment or a transfer between your own accounts: not real spending. */
    val selfTransfer: Boolean = false,
    val timestamp: Long,
    val raw: String,
)
