package brackets

import kotlin.collections.ArrayDeque

var openParentheses = mapOf(
  '(' to ')',
  '{' to '}',
  '[' to ']',
)
var closedParentheses = mapOf(
  ')' to '(',
  '}' to '{',
  ']' to '[',
)

@Throws()
fun balancedBrackets(text: String) {
  if (text.isEmpty()) return // short-circuit

  val stack = ArrayDeque<Char>()
  for ((i, c) in text.withIndex()) {
    when {
      c in openParentheses -> {
        stack.push(c)
      }
      c in closedParentheses -> {
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
