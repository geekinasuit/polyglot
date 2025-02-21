package bracketskt

import kotlin.collections.ArrayDeque


val closedParentheses = mapOf(
  ')' to '(',
  '}' to '{',
  ']' to '[',
)
val openParentheses = closedParentheses.values.toSet()

@Throws()
public fun balancedBrackets(text: String) {
  if (text.isEmpty()) return // short-circuit

  val stack = ArrayDeque<Char>()
  for ((i, c) in text.withIndex()) {
    when (c) {
      in openParentheses -> {
        stack.push(c)
      }
      in closedParentheses -> {
        val open = closedParentheses[c]
        val last = stack.removeLastOrNull() ?:
          throw BracketsNotBalancedException("closing bracket $c with no opening bracket at char ${i+1}")
        if (last != open)
          throw BracketsNotBalancedException("closing bracket ] at char ${i+1} mismatched with last opening bracket (")
      }
    }
  }
  if (stack.isNotEmpty()) {
    throw BracketsNotBalancedException("opening brackets without closing brackets found: $stack")
  }
}

class BracketsNotBalancedException(msg: String) : Exception(msg)

fun <E> ArrayDeque<E>.push(item: E) = this.addLast(item)
