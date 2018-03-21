package de.menedev.spock.ducttape

import org.rnorth.ducttape.unreliables.Unreliables
import spock.lang.PendingFeature
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RealDuctTapeSpec extends Specification {
    @PendingFeature(/*remove to fail*/)
    def "pandas will rule the word eventually without annotation"() {
        given: "humans ruling the world"
        def rulers = "humans"
        def historyOfRulers = ["dinosaurs"]

        def firstSampleDrawn = new CountDownLatch(1)

        when: "pandas take over"
        new Thread({
            firstSampleDrawn.await()
            rulers = "pandas"
        }).start()

        then: "falsely believe humans are still the rulers"
        Unreliables.retryUntilTrue(2, TimeUnit.SECONDS, {
            historyOfRulers << rulers
            firstSampleDrawn.countDown()

            "humans" in historyOfRulers &&
                    rulers == "humans"
        })
    }

    @PendingFeature(/*remove to fail*/)
    @DuctTape
    def "pandas will rule the word eventually with"() {
        given: "humans ruling the world"
        def rulers = "humans"
        def historyOfRulers = ["dinosaurs"]

        def firstSampleDrawn = new CountDownLatch(1)

        when: "pandas take over"
        new Thread({
            firstSampleDrawn.await()
            rulers = "pandas"
        }).start()

        then: "falsely believe humans are still the rulers"
        Unreliables.retryUntilTrue(2, TimeUnit.SECONDS, {
            historyOfRulers << rulers
            firstSampleDrawn.countDown()

            "humans" in historyOfRulers
            rulers == "humans"
        })
    }
}
