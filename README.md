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
        
        // All conditions are evaluated, just like Spock 
        "humans" in historyOfRulers
        rulers == "humans"
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
    testCompile 'com.github.MeneDev:spock-duct-tape:6daea6d26b'
}
```

## Compatibility

Should work with Groovy 2.3-2.5 and Spock 1.0-1.3-RC1
