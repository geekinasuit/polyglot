package brackets

import (
	"fmt"

	"github.com/emirpasic/gods/stacks/linkedliststack"
)

var openParentheses = map[rune]rune{
	'(': ')',
	'{': '}',
	'[': ']',
}
var closedParentheses = map[rune]rune{
	')': '(',
	'}': '{',
	']': '[',
}

func BalancedBrackets(text string) error {
	if text == "" {
		return nil
	}
	runes := []rune(text)
	stack := linkedliststack.New()

	for i, r := range runes {
		if openParentheses[r] != 0 {
			stack.Push(r)
		} else if closedParentheses[r] != 0 {
			open, ok := stack.Pop()
			if !ok {
				return fmt.Errorf("closing bracket %c with no opening bracket at rune %d", r, i+1)
			}
			if open != closedParentheses[r] {
				return fmt.Errorf("closing bracket %c at rune %d mismatched with last opening bracket %c", r, i+1, open)
			}
		}
	}
	if !stack.Empty() {
		stackStrings := "["
		for i, v := range stack.Values() {
			if i <= 0 {
				stackStrings += fmt.Sprintf("%c", v)
			} else {
				stackStrings += fmt.Sprintf(", %c", v)
			}
		}
		stackStrings += "]"
		return fmt.Errorf("opening brackets without closing brackets found: %s", stackStrings)
	}
	return nil
}
func foo() Something {
	return Something{
		Id:     2,
		Name:   "blah",
		Labels: []string{"Q", "R"},
	}
}
