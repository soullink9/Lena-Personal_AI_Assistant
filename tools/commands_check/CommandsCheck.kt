// Example sentences and what each should do. "-" means it isn't hers (it goes on to Open WebUI or Assist);
// "?" means she answers without setting anything (asks how long, says what's set).
private val c = Context()
private var failed = 0

private fun expect(sentence: String, wanted: String) {
    did.clear()
    val reply = Commands.handle(c, sentence)
    val got = when {
        reply == null -> "-"
        did.isEmpty() -> "?"
        else -> did.joinToString(", ")
    }
    if (got != wanted) {
        failed++
        println("FAIL  \"$sentence\"\n      wanted: $wanted\n      got:    $got  (said: $reply)")
    } else println("ok    \"$sentence\" -> $got${if (reply != null) "  ($reply)" else ""}")
}

fun main() {
    // Timers
    expect("Set a 10 minute timer", "timer 600000")
    expect("set a timer for 10 minutes", "timer 600000")
    expect("Set a 10 minute pasta timer.", "timer 600000 pasta")
    expect("timer for an hour and a half", "timer 5400000")
    expect("set a timer for one and a half hours", "timer 5400000")
    expect("Set a timer for half an hour", "timer 1800000")
    expect("5 min timer", "timer 300000")
    expect("10min timer", "timer 600000")
    expect("set a timer for 1 hour 30 minutes", "timer 5400000")
    expect("Set a timer for twenty five minutes called laundry", "timer 1500000 laundry")
    expect("set a timer for 90 seconds", "timer 90000")
    expect("Hey Lena, can you set a timer for 3 minutes please?", "timer 180000")
    expect("set a timer", "?")
    expect("How long is left?", "?") // names no timer, but some are running
    expect("how much time is left on the pasta timer", "?")
    expect("what timers do I have", "?")
    expect("cancel the pasta timer", "cancel 2")
    expect("cancel the timer", "?") // several left: she asks which
    val timers = Timers.all(c).filter { it.kind == CountdownKind.TIMER }.joinToString(", ") { "cancel ${it.id}" }
    expect("cancel all timers", timers)

    // Alarms
    expect("Wake me up at 6:45", "alarm 06:45")
    expect("Set an alarm for 7 pm", "alarm 19:00")
    expect("set an alarm for 6 45 a.m.", "alarm 06:45")
    expect("set an alarm for seven thirty am", "alarm 07:30")
    expect("alarm at half past six in the morning", "alarm 06:30")
    expect("set an alarm for quarter to seven in the morning", "alarm 06:45")
    expect("set an alarm for noon", "alarm 12:00")
    expect("wake me up at six oh five", "alarm 06:05")
    expect("set an alarm for ten to seven pm", "alarm 18:50")
    expect("Set an alarm for 7 o'clock in the evening", "alarm 19:00")
    expect("set an alarm for 18:30", "alarm 18:30")
    expect("when is my alarm", "?")
    expect("cancel my 6:05 alarm", "cancel 19")
    expect("cancel my alarm", "?") // several: she asks which
    expect("set an alarm", "?")

    // Not hers
    expect("Turn on the alarm system", "-")
    expect("what is a timer circuit", "-")
    expect("How long is left?", "-") // no timers now, so it isn't about one
    expect("tell me a story about a timer that ran out and a dragon who loved alarms in the night", "-")
    expect("what time is it", "-")
    expect("remind me to set an alarm", "-")
    expect("arm the alarm", "-")

    // While one rings
    Ringing.now.value = Countdown("r", CountdownKind.TIMER, 0)
    expect("Stop", "stop")
    Ringing.now.value = Countdown("r", CountdownKind.TIMER, 0)
    expect("stop the timer", "stop")
    Ringing.now.value = Countdown("r", CountdownKind.ALARM, 0)
    expect("snooze", "snooze")
    expect("stop", "-") // nothing ringing any more: "stop" alone is for her

    if (failed > 0) {
        println("\n$failed sentence(s) not understood as expected.")
        kotlin.system.exitProcess(1)
    }
    println("\nEvery sentence understood as expected.")
}
