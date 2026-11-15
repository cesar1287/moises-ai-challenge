package com.example.moisessongs.data

import com.example.moisessongs.domain.DataFailure
import com.example.moisessongs.domain.DataFailureException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class DataFailuresTest {
    @Test fun `HTTP rate limits are distinct from other service errors`() {
        for ((status, expected) in listOf(429 to DataFailure.RATE_LIMIT, 401 to DataFailure.SERVICE,
            404 to DataFailure.SERVICE, 503 to DataFailure.SERVICE)) {
            val failure = HttpException(Response.error<Unit>(status, "".toResponseBody()))
            val mapped = failure.asDomainFailure()
            assertEquals(expected, mapped.failure)
            assertSame(failure, mapped.cause)
        }
    }

    @Test fun `connection failures preserve their cause while unexpected failures remain distinct`() {
        val offline = IOException("offline")
        assertEquals(DataFailure.CONNECTION, offline.asDomainFailure().failure)
        assertSame(offline, offline.asDomainFailure().cause)
        assertEquals(DataFailure.UNEXPECTED, IllegalStateException("unexpected").asDomainFailure().failure)
    }

    @Test fun `an existing domain failure is not wrapped again`() = runTest {
        val failure = DataFailureException(DataFailure.STORAGE, IOException("storage"))
        try { dataCall<Unit> { throw failure }; fail("Expected failure") }
        catch (actual: DataFailureException) { assertSame(failure, actual) }
    }

    @Test fun `cancellation propagates without becoming a user visible failure`() = runTest {
        val cancelled = CancellationException("cancelled")
        try { dataCall<Unit> { throw cancelled }; fail("Expected cancellation") }
        catch (actual: CancellationException) { assertSame(cancelled, actual) }
    }
}
