package de.menedev.spock.ducttape.util

import groovy.transform.CompileStatic
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import org.junit.runner.notification.RunNotifier

@CompileStatic
class RecordingRunNotifier extends RunNotifier {
    final List<Failure> testFailures = []
    final List<Failure> testAssumptionFailures = []

    RecordingRunNotifier() {
        this.addListener(new RunListener() {

            @Override
            void testFailure(Failure failure) throws Exception {
                testFailures.add(failure)
                super.testFailure(failure)
            }

            @Override
            void testAssumptionFailure(Failure failure) {
                testAssumptionFailures.add(failure)
                super.testAssumptionFailure(failure)
            }
        })
    }

}
