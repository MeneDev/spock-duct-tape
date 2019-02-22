# ducttape-spock

Improved test feedback for [Duct Tape](https://github.com/rnorth/duct-tape) in [Spock](http://spockframework.org/).

## Usage

Just add the `@DuctTape` Annotation to your Feature or Specification: 

```groovy
@DuctTape
def "pandas will rule the word eventually with annotation"() {
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

To get implicit test conditions and get very informative output:

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
