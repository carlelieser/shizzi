package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Test

class TetherServiceContractTest {

    /** Shizuku sends this transaction to stop a user service; see https://github.com/carlelieser/shizzi/issues/87 */
    @Test
    fun `answers shizuku's destroy transaction`() {
        assertEquals(SHIZUKU_DESTROY_TRANSACTION, ITetherService.Stub.TRANSACTION_destroy)
    }

    private companion object {
        const val SHIZUKU_DESTROY_TRANSACTION = 16777115
    }
}
