package bracketsjava;

import java.util.*;

import com.geekinasuit.polyglot.example.protos.Something;

class Brackets {

  private static final Map<Character, Character> closedParentheses = Map.of(
          ')', '(',
          '}', '{',
          ']', '['
  );
  private static final Set<Character> openParentheses = new HashSet<>(closedParentheses.values());

  public static void balancedBrackets(String text) throws BracketsNotBalancedException {
    if (text.isEmpty()) { return; } // short-circuit
    ArrayDeque<Character> stack = new ArrayDeque<>();
    String[] chars = text.split("");
    for (int i = 0; i < chars.length; i++) {
      char c = chars[i].charAt(0);
      if (openParentheses.contains(c)) {
        stack.push(c);
      } else if (closedParentheses.containsKey(c)) {
        Character open = closedParentheses.get(c);
        Character last = stack.poll();
        if (last == null) {
          throw new BracketsNotBalancedException("closing bracket %c with no opening bracket at char %d", c, i+1);
        }
        if (!last.equals(open))
          throw new BracketsNotBalancedException("closing bracket ] at char %d mismatched with last opening bracket (", i+1);
      }
    }
    if (!stack.isEmpty()) {
      throw new BracketsNotBalancedException("opening brackets without closing brackets found: %s", stack);
    }
  }

  public static Something foo()  {
    return Something.newBuilder()
            .setId(1)
            .setName("Foo")
            .addAllLabels(new HashSet<>(Arrays.asList("A", "B")))
            .build();
  }
}
class BracketsNotBalancedException extends Exception {
  BracketsNotBalancedException(String template, Object... o) {
    super(String.format(template, o));
  }
}

