package de.menedev.spock.ducttape

import de.menedev.spock.ducttape.util.RecordingRunNotifier
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.intellij.lang.annotations.Language
import org.junit.runner.notification.Failure
import org.rnorth.ducttape.unreliables.Unreliables
import org.spockframework.compiler.SpockTransform
import org.spockframework.runtime.ConditionNotSatisfiedError
import org.spockframework.runtime.SpockComparisonFailure
import org.spockframework.runtime.Sputnik
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class DuctTapeSpec extends Specification {

    def createGroovyShell() {
        def cc = new CompilerConfiguration()
        def imports = new ImportCustomizer()
        imports.addImports(Unreliables.canonicalName)
        imports.addImports(DuctTape.canonicalName)
        imports.addImports(Specification.canonicalName)
        imports.addImports(SpockTransform.canonicalName)
        imports.addImports(TimeUnit.canonicalName)
        cc.addCompilationCustomizers(imports)
        def classLoader = new GroovyClassLoader()
        def s = new GroovyShell(classLoader, cc)
        return s
    }

    List<Failure> runTests(@Language("Groovy") String specString) {
        def s = createGroovyShell()
        s.parse(specString)

        def notifier = new RecordingRunNotifier()
        def loadedClasses = s.classLoader.loadedClasses
        loadedClasses.findAll {
            Specification.isAssignableFrom(it)
        }.each {
            new Sputnik(it).run(notifier)
        }
        return notifier.testFailures
    }

    def "retryUntilTrue 1 == 1 with timeout"() {
        when:
        def failures = runTests("""
        @DuctTape
        class TestSpec extends Specification {
            def test() {
            expect:
                Unreliables.retryUntilTrue(10, TimeUnit.MILLISECONDS, {
                    1 == 1
                })
            }        
        }
        """)

        then:
        failures.empty
    }

    def "retryUntilTrue 1 == 2 with timeout"() {
        when:
        def failures = runTests("""
        @DuctTape
        class TestSpec extends Specification {
            def test() {
            expect:
                Unreliables.retryUntilTrue(10, TimeUnit.MILLISECONDS, {
                    1 == 2
                })
            }        
        }
        """)

        then:
        failures
                .collect { it.exception as SpockComparisonFailure }
                .findAll {
            it.actual.trim() == "1" &&
                    it.expected.trim() == "2"
        }
    }

    def "retryUntilTrue 1 == 1 with retry limit"() {
        when:
        def failures = runTests("""
        @DuctTape
        class TestSpec extends Specification {
            def test() {
            expect:
                Unreliables.retryUntilTrue(10, {
                    1 == 1
                })
            }        
        }
        """)

        then:
        failures.empty
    }

    def "retryUntilTrue 1 == 2 with retry limit"() {
        when:
        def failures = runTests("""
        @DuctTape
        class TestSpec extends Specification {
            def test() {
            expect:
                Unreliables.retryUntilTrue(10, {
                    1 == 2
                })
            }        
        }
        """)

        then:
        failures.collect {
            it
        }
                .collect { it.exception as SpockComparisonFailure }
                .findAll {
                    it.actual.trim() == "1" &&
                    it.expected.trim() == "2"
                }
    }


    def "retryUntilTrue multiple conditions with timeout"() {
        when:
        def failures = runTests("""
        @DuctTape
        class TestSpec extends Specification {
            def test() {
            expect:
                Unreliables.retryUntilTrue(10, TimeUnit.MILLISECONDS, {
                    1 < $expected
                    2 < $expected
                    3 < $expected
                    4 < $expected
                    true
                })
            }        
        }
        """)

        def error = "$failedActually < $expected"

        then:
        failures
                .collect { it.exception as ConditionNotSatisfiedError }
                .findAll {
            it.condition.text == error
        }

        where:
        expected | failedActually
        "1" | "1"
        "2" | "2"
        "3" | "3"
        "4" | "4"
    }

    def "retryUntilTrue empty closure with timeout fails with TimeoutException"() {
        when:
        def failures = runTests("""
        @DuctTape
        class TestSpec extends Specification {
            @spock.lang.FailsWith(org.rnorth.ducttape.TimeoutException)
            def "empty closure fails"() {
                expect:
                Unreliables.retryUntilTrue(10, TimeUnit.MILLISECONDS, { })
            }
        }
        """)

        then:
        failures.empty
    }


    def "retryUntilTrue empty closure with retry limit fails with RetryCountExceededException"() {
        when:
        def failures = runTests("""
        @DuctTape
        class TestSpec extends Specification {
            @spock.lang.FailsWith(org.rnorth.ducttape.RetryCountExceededException)
            def "empty closure fails"() {
                expect:
                Unreliables.retryUntilTrue(10, { })
            }
        }
        """)

        then:
        failures.empty
    }

    def "evaluation result of the closure can change to true for retryUntilTrue with timeout"() {
        when:
        def failures = runTests("""
        import java.util.concurrent.CountDownLatch
        
        @DuctTape
        class TestSpec extends Specification {
            def "pandas will rule the word eventually"() {
                given: "humans ruling the world"
                def rulers = "humans"
                def historyOfRulers = ["dinosaurs"]
                
                def firstSampleDrawn = new CountDownLatch(1)
                
                when: "pandas take over"
                new Thread({
                    firstSampleDrawn.await()
                    rulers = "pandas"
                }).start()
        
                then: "pandas are the rulers now, but they have records of formally ruling races including the humans"
                Unreliables.retryUntilTrue(2, TimeUnit.SECONDS, {
                    historyOfRulers << rulers
                    firstSampleDrawn.countDown()
                    "humans" in historyOfRulers
                    rulers == "pandas"
                })
            } 
        }
        """)

        then:
        failures.empty
    }

    def "evaluation result of the closure can change to true for retryUntilTrue with retry limit"() {
        when:
        def failures = runTests("""
        import java.util.concurrent.CountDownLatch
        
        @DuctTape
        class TestSpec extends Specification {
            def "pandas will rule the word eventually"() {
                given: "humans ruling the world"
                def rulers = "humans"
                def historyOfRulers = ["dinosaurs"]
                
                def firstSampleDrawn = new CountDownLatch(1)
                
                when: "pandas take over"
                new Thread({
                    firstSampleDrawn.await()
                    rulers = "pandas"
                }).start()
        
                then: "pandas are the rulers now, but they have records of formally ruling races including the humans"
                Unreliables.retryUntilTrue(10, {
                    historyOfRulers << rulers
                    firstSampleDrawn.countDown()
                    "humans" in historyOfRulers
                    rulers == "pandas"
                })
            } 
        }
        """)

        then:
        failures.empty
    }

    def "Annotation can be set at method level"() {
        when:
        def failures = runTests("""
        class TestSpec extends Specification {
            def unmodifiedTest() {
            expect:
                Unreliables.retryUntilTrue(10, TimeUnit.MILLISECONDS, {
                    1 == 1
                    1 == 2
                })
            }
            
            @DuctTape
            def unreliableTest() {
            expect:
                Unreliables.retryUntilTrue(10, TimeUnit.MILLISECONDS, {
                    1 == 1
                    1 == 2
                })
            }
            
        }
        """)

        then:
        failures[0].exception.cause instanceof org.rnorth.ducttape.TimeoutException
        failures[1].exception instanceof SpockComparisonFailure
        failures[1].exception.actual.trim() == "1"
        failures[1].exception.expected.trim() == "2"
    }

}
