# ducttape-spock

Improved test feedback for [Duct Tape](https://github.com/rnorth/duct-tape) in [Spock](http://spockframework.org/).

## Example

### Without
```groovy
def "pandas will rule the word eventually"() {
    given: "humans ruling the world"
    def rulers = "humans"
    def historyOfRulers = ["dinosaurs"]
    
    def firstSampleDrawn = new CountDownLatch(1)
    
    when: "pandas take over"
    new Thread({
        firstSampleDrawn.await()
        rulers = "sloths"
    }).start()
    
    then: "pandas become rulers, humans are history"
    Unreliables.retryUntilTrue(2, TimeUnit.SECONDS, {
        historyOfRulers << rulers
        firstSampleDrawn.countDown()
        
        "humans" in historyOfRulers
        rulers == "pandas"
    })
} 
```
```
Condition failed with Exception:

Unreliables.retryUntilTrue(2, TimeUnit.SECONDS, { historyOfRulers << rulers firstSampleDrawn.countDown() "humans" in historyOfRulers rulers == "humans" })

Condition failed with Exception:

Unreliables.retryUntilTrue(2, TimeUnit.SECONDS, { historyOfRulers << rulers firstSampleDrawn.countDown() "humans" in historyOfRulers rulers == "humans" })

	at de.menedev.spock.ducttape.RealDuctTapeSpec.pandas will rule the word eventually(RealDuctTapeSpec.groovy:24)
Caused by: org.rnorth.ducttape.TimeoutException: Timeout waiting for result with exception
	at org.rnorth.ducttape.unreliables.Unreliables.retryUntilSuccess(Unreliables.java:51)
	at org.rnorth.ducttape.unreliables.Unreliables.retryUntilTrue(Unreliables.java:95)
	... 1 more
Caused by: java.lang.RuntimeException: Not ready yet
	at org.rnorth.ducttape.unreliables.Unreliables.lambda$retryUntilTrue$1(Unreliables.java:97)
	at org.rnorth.ducttape.unreliables.Unreliables.lambda$retryUntilSuccess$0(Unreliables.java:41)
	at java.util.concurrent.FutureTask.run(FutureTask.java:266)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
	at java.lang.Thread.run(Thread.java:748)

```
### With
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
    
    then: "pandas become rulers, humans are history"
    Unreliables.retryUntilTrue(2, TimeUnit.SECONDS, {
        historyOfRulers << rulers
        firstSampleDrawn.countDown()
        
        "humans" in historyOfRulers
        rulers == "pandas"
    })
} 
```
