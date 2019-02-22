# spock-duct-tape [![Follow @MeneDev on Twitter](https://img.shields.io/twitter/follow/MeneDev.svg?style=social&label=%40MeneDev)](https://twitter.com/MeneDev)

Improved test feedback for [Duct Tape](https://github.com/rnorth/duct-tape) in [Spock](http://spockframework.org/).

## Usage

1. Include the dependency in your project (see below)
1. Add the `@DuctTape` Annotation to your Feature or Specification: 
1. Enjoy!

```groovy
@DuctTape
def "pandas will rule the word eventually"() {
    given: "humans ruling the world"
    def rulers = "humans"
    def historyOfRulers = ["dinosaurs"]

    def firstSampleDrawn = new CountDownLatch(3)

    when: "pandas take over"
    new Thread({
        firstSampleDrawn.await()
        rulers = "pandas"
    }).start()

    then: "falsely believe humans are still the rulers"
    Unreliables.retryUntilTrue(2, TimeUnit.SECONDS, {
        historyOfRulers << rulers
        
        // we need to call this 3 times before
        // firstSampleDrawn.await() stops blocking
        // and the pandas take over
        firstSampleDrawn.countDown()
        
        // All conditions are evaluated, just like Spock 
        "humans" in historyOfRulers
        rulers == "humans"
        
        // Beware: do not add logic here (e.g. a sleep)
        // Failing conditions will throw an Exception
        // so nothing below will be executed
    })
}
```

To get implicit test conditions and get informative output as you're used from Spock:

```
Condition not satisfied:

rulers == "humans"
|      |
pandas false
       5 differences (16% similarity)
       (pand)a(-)s
       (hum-)a(n)s
```

Instead of vague output:

```
Condition failed with Exception:

Unreliables.retryUntilTrue(2, TimeUnit.SECONDS, { historyOfRulers << rulers firstSampleDrawn.countDown() "humans" in historyOfRulers rulers == "humans" })

```

## Dependency
```groovy
repositories {
    // ...
    maven { url 'https://jitpack.io' }
}

dependencies {
    testCompile 'com.github.MeneDev:spock-duct-tape:194fdf199f'
}
```

## Compatibility

Should work with Groovy 2.3-2.5 and Spock 1.0-1.3-RC1
