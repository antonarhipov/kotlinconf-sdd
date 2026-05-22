---
name: kotlin-language-features
description: >
  Authoritative guidance on modern Kotlin language features (2.2.0 through
  2.3.21) — writing idiomatic code, reviewing or modernizing older code, and
  upgrading across the 2.2 → 2.3 line. Make sure to use this skill whenever
  the user mentions Kotlin 2.2, 2.2.20, 2.3, 2.3.0, 2.3.20, or 2.3.21, or any
  of these features: guard conditions in `when`, non-local `break`/`continue`
  in inline lambdas, multi-dollar string interpolation, context parameters,
  context-sensitive resolution, nested type aliases, the `@all` annotation
  use-site target, unused return value checker, explicit backing fields,
  name-based destructuring, data-flow exhaustiveness for `when`, or `return`
  in expression-body functions. Also trigger for compiler flags
  `-Xreturn-value-checker`, `-Xexplicit-backing-fields`,
  `-Xname-based-destructuring`; annotations `@MustUseReturnValues` and
  `@IgnorableReturnValue`; or any question framed as "what's new in Kotlin"
  or "latest Kotlin features."
---

# Modern Kotlin Language Features (2.2 → 2.3.21)

This skill covers the language evolution from **Kotlin 2.2.0 (June 2025)** through
**Kotlin 2.3.21 (April 2026)** — the current stable line at time of writing. It is
oriented around three jobs:

1. **Writing idiomatic modern Kotlin** using the right feature for the situation
2. **Reviewing or modernizing** 2.1.x-era code that predates these features
3. **Upgrading** projects across the 2.2 → 2.3 boundary

> **Release cadence quick map.** JetBrains ships three kinds of releases:
> - **Language releases** (2.x.0) — every six months, where new language features land.
> - **Tooling releases** (2.x.20) — three months later, mostly build/IDE/compiler-plugin work, occasionally with a language feature.
> - **Bug-fix releases** (2.x.yz) — irregular cadence, **no new language features**.
>
> So Kotlin 2.3.21 specifically is a bug-fix release — the language is identical to 2.3.20. The interesting language work is in 2.2.0, 2.2.20, 2.3.0, and 2.3.20.

---

## Feature timeline at a glance

| Feature | Introduced | Status today |
|---|---|---|
| Guard conditions in `when` | 2.2.0 | **Stable** |
| Non-local `break` / `continue` in inline lambdas | 2.2.0 | **Stable** |
| Multi-dollar string interpolation | 2.2.0 | **Stable** |
| Nested type aliases | 2.2.0 Beta | **Stable** in 2.3.0 |
| Context parameters | 2.2.0 preview | Preview; stabilized in 2.4 |
| Context-sensitive resolution | 2.2.0 preview | Experimental (refined in 2.3.0) |
| `@all` annotation use-site target | 2.2.0 preview | Experimental |
| Improved suspend-lambda overload resolution | 2.2.20 | Default in 2.3.0 |
| `return` in expression-body functions (explicit return type) | 2.2.20 | **Default** in 2.3.0 |
| Reified `Throwable` catches | 2.2.20 | Improvements ongoing |
| Data-flow exhaustiveness for `when` | 2.2.20 | **Stable** in 2.3.0 |
| Unused return value checker | 2.3.0 | Experimental |
| Explicit backing fields | 2.3.0 | Experimental |
| Name-based destructuring | 2.3.20 | Experimental |
| Context-parameter overload resolution change | 2.3.20 | **Behavior change** (potentially breaking) |

---

## Kotlin 2.2.0 (June 2025)

### Guard conditions in `when` — Stable

A branch in a `when` with a subject can carry an extra boolean condition after `if`. This makes the destructured-type-test idiom much cleaner.

```kotlin
when (animal) {
    is Dog if animal.weight > 30 -> "big dog"
    is Dog -> "small dog"
    else -> "not a dog"
}
```

The branch matches only when both the type check and the guard succeed; otherwise execution falls through to the next branch. The compiler's exhaustiveness checker is aware of guards.

### Non-local `break` and `continue` — Stable

`return` has always worked across an inline lambda boundary (`forEach { if (cond) return }`). As of 2.2.0, `break` and `continue` do too — closing the parity gap.

```kotlin
for (item in items) {
    item.children.forEach { child ->
        if (child.invalid) continue   // continues the outer `for`
        if (child.terminal) break     // breaks the outer `for`
        process(child)
    }
}
```

Only valid in **inline** lambdas, since the bytecode jump targets have to be available at the call site.

### Multi-dollar string interpolation — Stable

Double-, triple-, or n-dollar string prefixes let you escape `$` cleanly without ugly `\${'$'}` gymnastics. Useful for JSON, shell snippets, JVM signatures, JS templates, etc.

```kotlin
val price = 42
val json = $$"""{ "price": "$$$price USD" }"""   // {"price": "$42 USD"}

val shell = $$"echo $$$HOME"                     // echo $HOME

// Triple-dollar form, when the literal text contains "$$"
val literal = $$$"reference: $$cost (not interpolated)"
```

The number of `$`s in the prefix determines how many `$`s are needed at the call site to actually interpolate. Reach for it any time a string literal contains a lot of `$` that should appear as-is.

### Context parameters — Preview

The replacement design for the old `context(receivers)` mechanism. Declared on functions and properties with `context(...)`, they pass services, loggers, transactions, and similar implicits **without polluting the signature** the way explicit parameters do, and **without conflating them with `this`** the way receivers did.

```kotlin
context(logger: Logger, tx: Transaction)
fun saveUser(user: User) {
    logger.info("Saving ${user.id}")
    tx.execute { /* ... */ }
}

context(Logger(), Transaction.current()) {
    saveUser(user)
}
```

Status:
- **Preview** in 2.2.0 (`-Xcontext-parameters`)
- **Stabilized in Kotlin 2.4** (the next language release)
- Mind the 2.3.20 overload-resolution change below before relying heavily on overloading with context parameters

### Context-sensitive resolution — Preview

Lets you drop enum/sealed prefixes when the expected type is known from context. Familiar from Swift; long-requested in Kotlin.

```kotlin
enum class Status { ACTIVE, INACTIVE, PENDING }

fun update(status: Status) {
    when (status) {
        ACTIVE -> ...     // instead of Status.ACTIVE
        INACTIVE -> ...
        PENDING -> ...
    }
}
```

Also works for sealed hierarchies and constants. In 2.3.0 the scope rules were tightened: only sealed and enclosing supertypes of the expected type contribute, and the compiler warns when resolution becomes ambiguous around type operators or equalities (typically caused by a clashing imported class).

### Nested type aliases — Beta in 2.2.0, Stable in 2.3.0

`typealias` declarations can live inside classes and objects, not just at file top level.

```kotlin
class Graph<N> {
    typealias Edge = Pair<N, N>
    typealias AdjacencyList = Map<N, List<N>>
}
```

### `@all` annotation use-site target — Preview

Applies an annotation to every relevant target on a property at once — typically the property itself, its backing field, the getter, the setter, and the constructor parameter — instead of forcing you to repeat the annotation with separate `@get:`, `@set:`, `@field:` prefixes.

```kotlin
class User(
    @all:JvmField val name: String
)
```

Avoids the common Spring/Jackson/Hibernate footgun where an annotation lands on only the constructor parameter (or only the property) and the framework silently doesn't see it.

### Compiler: `-Xwarning-level`

Per-warning severity control. Lets you treat a specific warning ID as error, warning, or off, without flipping the global `allWarningsAsErrors`.

```kotlin
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xwarning-level=NOTHING_TO_INLINE:disabled")
    }
}
```

### JVM: default method generation tweak

Default method generation for interface functions on the JVM changed slightly. Mostly invisible unless you bytecode-inspect or work on libraries that ship JVM ABI guarantees — but worth knowing about when triaging strange `IncompatibleClassChangeError` after a Kotlin upgrade in a multi-module setup.

---

## Kotlin 2.2.20 (preview window into 2.3)

A staging release: mostly previews of what would land defaulted/stabilized in 2.3.0.

- **Improved overload resolution when passing lambdas to overloads with `suspend` function types.** No more spurious ambiguity errors when you have `fun foo(block: () -> Unit)` next to `fun foo(block: suspend () -> Unit)`.
- **`return` statements in expression bodies with explicit return types** (became default in 2.3.0; see below).
- **Better exhaustiveness checks for `when` expressions** — fewer false positives, more cases the compiler recognizes as exhaustive without an `else` branch.
- **Reified `Throwable` catches** — `inline fun <reified T : Throwable> tryOr(block: () -> Unit, handler: (T) -> Unit)` now works cleanly.
- **Contract improvements** — more `kotlin.contracts.contract { ... }` patterns are recognized by smart-casting.

---

## Kotlin 2.3.0 (December 2025)

### Unused return value checker — Experimental

Warns whenever an expression returns a value other than `Unit` or `Nothing` and the result is silently dropped — not passed to a function, not checked in a condition, not bound to a variable. The Kotlin analogue of `@CheckReturnValue` / `#[must_use]`.

**Enable:**
```kotlin
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xreturn-value-checker=check")
    }
}
```

In `check` mode, the checker only reports ignored results from functions explicitly marked `@MustUseReturnValues`. Most stdlib functions are already marked.

**Marking your own code** — file-level, class-level, or function-level:
```kotlin
@file:MustUseReturnValues
package com.example

@MustUseReturnValues
class Greeter {
    fun greet(name: String): String = "Hello, $name"
}
```

**Whole-project mode** treats every compiled function as if it carried the annotation:
```kotlin
freeCompilerArgs.add("-Xreturn-value-checker=full")
```

**Opt out per function** with `@IgnorableReturnValue` — useful for fluent builders, `MutableList.add`-style methods, etc.

**Discard intentionally** at a single call site with the new underscore-variable form:
```kotlin
val _ = computeValue()   // explicit drop; no warning
```

**Override inheritance is respected.** Overriding a `@MustUseReturnValues` function carries the obligation forward, so something like `override fun hashCode(): Int = ...` will be correctly flagged when used as a top-level statement.

The checker ignores `++` and `--`.

### Explicit backing fields — Experimental

Finally kills the `_city` / `city` double-property pattern that's been a staple of ViewModel and DDD code for years.

**Enable:**
```kotlin
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexplicit-backing-fields")
    }
}
```

**Before:**
```kotlin
private val _city = MutableStateFlow<String>("")
val city: StateFlow<String> get() = _city

fun updateCity(newCity: String) {
    _city.value = newCity
}
```

**After:**
```kotlin
val city: StateFlow<String>
    field = MutableStateFlow("")

fun updateCity(newCity: String) {
    city.value = newCity   // smart-cast to MutableStateFlow inside the class
}
```

Inside the declaring class, the compiler smart-casts to the backing field's concrete type (`MutableStateFlow` here). Outside the class, only the declared public type (`StateFlow`) is visible.

**Limitations worth flagging in 2.3.0:**
- Backing field visibility is fixed at **private** — no `protected field`, no `internal field`
- The property itself must be **`final`** — `open`/`override` interaction is still rough at the time of writing
- Works only with `val` properties on classes (not top-level)

### Context-sensitive resolution refinements

See the 2.2.0 entry for the feature itself. In 2.3.0:
- The contextual scope of the search now includes the **sealed and enclosing supertypes** of the expected type, and **no other supertype scopes**. This fixes a class of surprising candidate matches in deep hierarchies (KT-77823).
- The compiler warns when context-sensitive resolution makes a call ambiguous around type operators or equalities — typically caused by a clashing imported class (KT-77821).

### Enabled by default: `return` in expression bodies with explicit return type

You can mix `return` statements into an expression-body function as long as the return type is declared. No flag required.

```kotlin
fun classify(n: Int): String =
    if (n < 0) return "negative"
    else if (n == 0) "zero"
    else "positive"
```

Without the explicit `: String` declaration, this still doesn't compile — type inference doesn't see through `return`.

### Promoted to Stable

- **Nested type aliases**
- **Data-flow-based exhaustiveness checks for `when`**

Both lose their experimental flags in 2.3.0; no migration needed.

### Platform-side highlights (talk material)

These aren't language features, but worth knowing about for conference content:
- **Kotlin/JVM** — Java 25 bytecode target
- **Kotlin/Native** — Swift export gains native enum classes and variadic function parameters; C/Objective-C library import goes Beta
- **Kotlin/Wasm** — new exception-handling proposal enabled by default; `KClass.qualifiedName` at runtime; Latin-1 compact string storage shrinks binaries
- **Kotlin/JS** — `@JsExport` can now export `suspend` functions directly; `LongArray` represented as `BigInt64Array`; unified companion-object access across module systems
- **Compose compiler** — stack traces for minified Android apps

---

## Kotlin 2.3.20 (March 2026)

### Name-based destructuring — Experimental

Destructures by **property name** rather than position. Kills a whole class of subtle bugs that show up when component order changes on a data class and every destructuring site silently keeps compiling.

```kotlin
data class User(val name: String, val email: String)

val user = User("alice", "alice@example.com")
val (email, name) = user   // matches by name, not position
```

**Enable:**
```kotlin
freeCompilerArgs.add("-Xname-based-destructuring=only-syntax")
```

**Three modes:**
- `only-syntax` — adds an explicit name-based form `(val mail = email, val n = name) = user`; does **not** change behavior of existing positional code.
- `name-mismatch` — adds the form **and** warns when positional destructuring uses variable names that don't match the property names. Excellent CI mode for surfacing latent bugs.
- `complete` — the short form `(name, value)` becomes name-based by default; positional destructuring moves to square-bracket syntax: `val [name, email] = user`.

The trajectory: name-based becomes the default in a future release; positional moves to brackets permanently.

### Context-parameter overload resolution — Behavior change

This is **not** an experimental flag — it's a corrected behavior that may break code on upgrade.

Before 2.3.20, a declaration with `context(...)` parameters was treated as **more specific** than one without, so a call inside a `context(...) { ... }` block would silently prefer the contextual overload. From 2.3.20, the two are treated as **equally specific**, and ambiguous calls fail to compile.

```kotlin
class Logger { fun info(msg: String) = println("INFO: $msg") }

fun saveUser(id: Int) {
    println("Saving user $id (no logger)")
}

context(logger: Logger)
fun saveUser(id: Int) {                   // shadowing warning in 2.3.20
    logger.info("Saving user $id")
}

fun main() {
    context(Logger()) {
        saveUser(1)                       // ❌ ambiguous in 2.3.20+
    }
}
```

**Fix:** rename one of the overloads, or wait for Kotlin 2.4 which introduces explicit context arguments at the call site.

Additionally, the number of `kotlin.context` overloads was reduced from 22 to 6 to clean up code-completion noise.

### Platform-side: SWC transpiler integration

Kotlin/JS transpilation is migrating from the in-tree pipeline to the SWC transpiler — quieter migration target for browser-based codebases, with the longer-term promise of modern JS syntax inside inlined-JS blocks (currently ES5-only).

---

## Kotlin 2.3.21 (April 2026) — bug-fix release

**No language changes.** The language is identical to 2.3.20.

Highlights for users who hit pain in 2.3.20:
- **KMP incremental compilation** — per-symbol fingerprinting in klib metadata. Wasm rebuilds that took 20–45 seconds (or failed) now complete in seconds.
- **Kotlin/Native + Swift Package Manager** — static frameworks (`isStatic = true`) on iOS arm64 no longer require manual `linkerOpts` workarounds.
- Compiler memory usage in parallel CI builds.
- Several K2 IDE / FIR-plugin fixes.

Recommend the upgrade unconditionally for KMP or Wasm projects; low urgency for pure JVM.

---

## Compiler flags reference

| Flag | Feature |
|---|---|
| `-Xwarning-level=ID:level` | Per-warning severity (2.2.0) |
| `-Xcontext-parameters` | Context parameters (2.2.0 preview; default in 2.4) |
| `-Xreturn-value-checker=check` | Unused return value checker, marked-only mode (2.3.0) |
| `-Xreturn-value-checker=full` | Unused return value checker, whole-project mode (2.3.0) |
| `-Xexplicit-backing-fields` | Explicit backing fields (2.3.0) |
| `-Xname-based-destructuring=only-syntax` | Name-based destructuring, additive (2.3.20) |
| `-Xname-based-destructuring=name-mismatch` | Above + warnings on positional name mismatches (2.3.20) |
| `-Xname-based-destructuring=complete` | Name-based default; positional via brackets (2.3.20) |

---

## Demo recommendations

If picking features to demo on the IntelliJ IDEA channel or at a conference, **explicit backing fields** and the **unused return value checker** land hardest with developer audiences:

- **Explicit backing fields** — kills a boilerplate pattern that everyone in the room has written. The audience reaction is "wait, that's it? finally" in roughly five seconds.
- **Unused return value checker** — flip the flag on a real codebase live, find real bugs in five minutes of analysis. Especially effective on backend codebases with a lot of `String.replaceFirstChar`-style transformations that silently get dropped.

Secondary picks that demo well:
- **Guard conditions in `when`** — short, instantly readable, replaces nested `if` inside a `when` branch.
- **Multi-dollar interpolation** — one-line cleanup of every JSON-in-a-Kotlin-string snippet in your build.
- **Name-based destructuring** in `name-mismatch` mode — show how a data class refactor breaks positional destructuring silently, then enable the flag and watch the compiler catch it.

Context parameters are the headline *language design* item across this whole window, but they demo less well in short slots — too much context (no pun intended) needed before the payoff. Save them for talks with a 30+ minute runway.
